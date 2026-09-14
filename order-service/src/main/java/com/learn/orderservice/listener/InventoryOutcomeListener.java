package com.learn.orderservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.orderservice.entity.Order;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.event.InventoryOutcomeEvent;
import com.learn.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Closes the loop Inventory Service's OrderCreatedListener started: this order was created
// PENDING with no idea whether stock was actually available, and these two listeners are
// what update it once Inventory Service has decided.
//
// Unlike Inventory Service's stock decrement, setting an order's status is naturally
// idempotent -- applying the same outcome twice leaves the order in the same state, so no
// processed_event-style dedup table is needed here.
@Service
public class InventoryOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutcomeListener.class);

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public InventoryOutcomeListener(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.reserved", groupId = "order-service")
    @Transactional
    public void onInventoryReserved(String message) throws Exception {
        InventoryOutcomeEvent event = objectMapper.readValue(message, InventoryOutcomeEvent.class);
        applyStatus(event.getOrderId(), OrderStatus.CONFIRMED, null);
    }

    @KafkaListener(topics = "inventory.failed", groupId = "order-service")
    @Transactional
    public void onInventoryFailed(String message) throws Exception {
        InventoryOutcomeEvent event = objectMapper.readValue(message, InventoryOutcomeEvent.class);
        applyStatus(event.getOrderId(), OrderStatus.REJECTED, event.getReason());
    }

    private void applyStatus(Long orderId, OrderStatus status, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received inventory outcome for unknown order {}", orderId);
            return;
        }
        // Don't clobber a status the customer already set themselves -- if they cancelled
        // while this outcome was in flight, their cancellation should stick, not get
        // silently overwritten by an outcome that's now moot.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Ignoring inventory outcome for order {} -- already cancelled by customer", orderId);
            return;
        }
        order.setStatus(status);
        if (status == OrderStatus.REJECTED) {
            log.info("Order {} rejected: {}", orderId, reason);
        } else {
            log.info("Order {} confirmed", orderId);
        }
    }
}
