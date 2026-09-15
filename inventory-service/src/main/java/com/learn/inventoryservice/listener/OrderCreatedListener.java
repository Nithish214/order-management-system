package com.learn.inventoryservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.inventoryservice.cache.StockCache;
import com.learn.inventoryservice.entity.OrderReservationItem;
import com.learn.inventoryservice.entity.ProcessedEvent;
import com.learn.inventoryservice.entity.ProductStock;
import com.learn.inventoryservice.event.OrderCreatedEvent;
import com.learn.inventoryservice.repository.OrderReservationItemRepository;
import com.learn.inventoryservice.repository.ProcessedEventRepository;
import com.learn.inventoryservice.repository.ProductStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// "groupId = inventory-service" (also set as spring.kafka.consumer.group-id) means all
// instances of this service sharing that id cooperate to split up the topic's partitions --
// scale to 3 instances and each handles a disjoint subset of orders, with Kafka tracking
// per-group offsets so each order.created message is still handled exactly once per group.
@Service
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final ProductStockRepository productStockRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderReservationItemRepository orderReservationItemRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StockCache stockCache;

    public OrderCreatedListener(
            ProductStockRepository productStockRepository,
            ProcessedEventRepository processedEventRepository,
            OrderReservationItemRepository orderReservationItemRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            StockCache stockCache
    ) {
        this.productStockRepository = productStockRepository;
        this.processedEventRepository = processedEventRepository;
        this.orderReservationItemRepository = orderReservationItemRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.stockCache = stockCache;
    }

    @KafkaListener(topics = "order.created", groupId = "inventory-service")
    @Transactional
    public void onOrderCreated(String message) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        String eventId = String.valueOf(event.getEventId());

        // Idempotency guard: Kafka is at-least-once, not exactly-once -- a producer retry or a
        // consumer restarting before it commits an offset can redeliver the same message. Without
        // this check, a redelivered order.created would decrement stock a second time.
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping already-processed event {}", eventId);
            return;
        }

        List<String> shortages = new ArrayList<>();
        Map<Long, ProductStock> reservable = new LinkedHashMap<>();

        for (OrderCreatedEvent.Item item : event.getItems()) {
            ProductStock stock = productStockRepository.findById(item.getProductId()).orElse(null);
            int available = stock == null ? 0 : stock.getAvailableQuantity();
            if (stock == null || available < item.getQuantity()) {
                shortages.add("product " + item.getProductId() + " requested " + item.getQuantity()
                        + " but only " + available + " available");
            } else {
                reservable.put(item.getProductId(), stock);
            }
        }

        // Record this event as handled regardless of outcome (success or shortage) -- either way
        // this exact message should never be reprocessed, and it must land in the same transaction
        // as the stock update below so both commit or roll back together.
        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));

        if (shortages.isEmpty()) {
            for (OrderCreatedEvent.Item item : event.getItems()) {
                ProductStock stock = reservable.get(item.getProductId());
                stock.setAvailableQuantity(stock.getAvailableQuantity() - item.getQuantity());
                // Recorded so a later cancellation (OrderCancelledListener) knows exactly what
                // this service actually reserved for this order, without needing Order Service
                // to repeat the item list back to it.
                orderReservationItemRepository.save(
                        new OrderReservationItem(event.getOrderId(), item.getProductId(), item.getQuantity()));
                // This is a second write path to product_stock beyond the restock endpoint --
                // without this, a cached stock:{id}/stock:list would silently go stale the
                // moment an order actually reserved anything, not just on restock.
                stockCache.delete(StockCache.itemKey(item.getProductId()));
            }
            stockCache.delete(StockCache.LIST_KEY);
            kafkaTemplate.send("inventory.reserved", String.valueOf(event.getOrderId()),
                    reservedPayload(event.getOrderId()));
            log.info("Reserved stock for order {}", event.getOrderId());
        } else {
            String reason = String.join("; ", shortages);
            kafkaTemplate.send("inventory.failed", String.valueOf(event.getOrderId()),
                    failedPayload(event.getOrderId(), reason));
            log.info("Insufficient stock for order {}: {}", event.getOrderId(), reason);
        }
    }

    private String reservedPayload(Long orderId) {
        return """
                {"eventId":"%s","orderId":%d,"status":"RESERVED"}""".formatted(UUID.randomUUID(), orderId);
    }

    private String failedPayload(Long orderId, String reason) {
        return """
                {"eventId":"%s","orderId":%d,"status":"FAILED","reason":"%s"}"""
                .formatted(UUID.randomUUID(), orderId, reason.replace("\"", "'"));
    }
}
