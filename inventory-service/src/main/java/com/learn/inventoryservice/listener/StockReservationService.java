package com.learn.inventoryservice.listener;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// One reservation "attempt" for one order.created event -- everything OrderCreatedListener
// used to do inline, before it needed to be retried as a whole unit. Pulled out into its
// own @Transactional method (rather than left on the @KafkaListener method itself) for
// exactly one reason: Spring's @Transactional only takes effect on a call that goes through
// this bean's proxy. OrderCreatedListener calls attemptReservation(...) fresh on every retry,
// so each attempt gets a brand-new transaction and a brand-new persistence context --
// which is what actually makes "re-read the row fresh" true on a retry. Calling a method
// annotated @Transactional from *within the same class* (self-invocation) would bypass the
// proxy entirely and silently run everything in one transaction, defeating the whole point.
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final ProductStockRepository productStockRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderReservationItemRepository orderReservationItemRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StockCache stockCache;

    public StockReservationService(
            ProductStockRepository productStockRepository,
            ProcessedEventRepository processedEventRepository,
            OrderReservationItemRepository orderReservationItemRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            StockCache stockCache
    ) {
        this.productStockRepository = productStockRepository;
        this.processedEventRepository = processedEventRepository;
        this.orderReservationItemRepository = orderReservationItemRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.stockCache = stockCache;
    }

    // Everything in this method either all commits or all rolls back together, including
    // the processed_event row -- so if a version conflict rolls this transaction back, the
    // event is NOT marked processed, and OrderCreatedListener's next attempt starts from
    // scratch with fresh reads of every row involved, not just the one that conflicted.
    @Transactional
    public void attemptReservation(OrderCreatedEvent event) {
        String eventId = String.valueOf(event.getEventId());

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

        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));

        if (shortages.isEmpty()) {
            for (OrderCreatedEvent.Item item : event.getItems()) {
                ProductStock stock = reservable.get(item.getProductId());
                // The plain setter -- Hibernate does the version bookkeeping on flush/commit,
                // nothing here needs to touch stock.getVersion() at all. If some other
                // transaction already updated this exact row since our findById() above, the
                // UPDATE this produces at commit matches zero rows and this whole method
                // throws ObjectOptimisticLockingFailureException instead of returning
                // normally -- see OrderCreatedListener for what happens next.
                stock.setAvailableQuantity(stock.getAvailableQuantity() - item.getQuantity());
                orderReservationItemRepository.save(
                        new OrderReservationItem(event.getOrderId(), item.getProductId(), item.getQuantity()));
                stockCache.delete(StockCache.itemKey(item.getProductId()));
            }
            stockCache.delete(StockCache.LIST_KEY);
            kafkaTemplate.send("inventory.reserved", String.valueOf(event.getOrderId()),
                    reservedPayload(event.getOrderId(), event.getTotalAmount()));
            log.info("Reserved stock for order {}", event.getOrderId());
        } else {
            String reason = String.join("; ", shortages);
            kafkaTemplate.send("inventory.failed", String.valueOf(event.getOrderId()),
                    failedPayload(event.getOrderId(), reason));
            log.info("Insufficient stock for order {}: {}", event.getOrderId(), reason);
        }
    }

    // Called once OrderCreatedListener has retried attemptReservation() the maximum number
    // of times and every attempt still hit a version conflict -- genuinely unusual (it means
    // this exact row was under sustained write contention across every single retry), but
    // the order still needs a definitive answer one way or the other, since Payment Service
    // is waiting on either inventory.reserved or inventory.failed and has no other way to
    // find out what happened. Its own small transaction: doesn't touch product_stock at all,
    // so it can't itself hit another version conflict.
    @Transactional
    public void recordConcurrencyFailure(OrderCreatedEvent event) {
        String eventId = String.valueOf(event.getEventId());
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping already-processed event {}", eventId);
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));
        // Deliberately generic -- never leak internal contention details to whatever
        // eventually surfaces this reason to a customer, same reasoning as every other
        // inventory.failed reason in this service already follows.
        kafkaTemplate.send("inventory.failed", String.valueOf(event.getOrderId()),
                failedPayload(event.getOrderId(), "unable to reserve stock right now, please try again"));
        log.warn("Gave up reserving stock for order {} after repeated concurrent updates", event.getOrderId());
    }

    private String reservedPayload(Long orderId, BigDecimal totalAmount) {
        return """
                {"eventId":"%s","orderId":%d,"status":"RESERVED","totalAmount":%s}"""
                .formatted(UUID.randomUUID(), orderId, totalAmount);
    }

    private String failedPayload(Long orderId, String reason) {
        return """
                {"eventId":"%s","orderId":%d,"status":"FAILED","reason":"%s"}"""
                .formatted(UUID.randomUUID(), orderId, reason.replace("\"", "'"));
    }
}
