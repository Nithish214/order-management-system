package com.learn.inventoryservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.inventoryservice.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import jakarta.persistence.OptimisticLockException;

// "groupId = inventory-service" (also set as spring.kafka.consumer.group-id) means all
// instances of this service sharing that id cooperate to split up the topic's partitions --
// scale to 3 instances and each handles a disjoint subset of orders, with Kafka tracking
// per-group offsets so each order.created message is still handled exactly once per group.
//
// Deliberately NOT @Transactional itself any more, and holds no repositories of its own --
// the actual read-check-write work now lives in StockReservationService.attemptReservation(),
// one full transaction per call. This method's only job is deciding *how many times* to call
// it: on a version conflict (see the catch block), call it again, which -- because it's a
// fresh call through StockReservationService's Spring proxy -- gets a brand-new transaction
// and persistence context, so the retry's reads of product_stock are genuinely fresh from the
// database, not whatever this method's own (now-rolled-back) attempt last saw.
@Service
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    // "A small, bounded number" per the spec -- enough to ride out a brief burst of
    // contention on one popular product without retrying forever. 1 initial attempt + 2
    // retries if the first two both lose the race.
    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final StockReservationService stockReservationService;

    public OrderCreatedListener(ObjectMapper objectMapper, StockReservationService stockReservationService) {
        this.objectMapper = objectMapper;
        this.stockReservationService = stockReservationService;
    }

    @KafkaListener(topics = "order.created", groupId = "inventory-service")
    public void onOrderCreated(String message) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                stockReservationService.attemptReservation(event);
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
                // ObjectOptimisticLockingFailureException is Spring's translated form -- what
                // actually surfaces here in practice, since the version-mismatch UPDATE is
                // usually only detected when this call's transaction commits (at the end of
                // attemptReservation), and Spring's JpaTransactionManager translates the
                // jakarta.persistence.OptimisticLockException Hibernate throws at that point
                // into this Spring exception before it propagates out to us. The plain JPA
                // exception is caught too as a fallback, in case a future change makes
                // attemptReservation flush mid-method instead (which throws the untranslated
                // exception directly, since it happens inside the Spring Data proxy's own
                // call rather than at commit).
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Order {}: still conflicting after {} attempts -- treating as a genuine failure",
                            event.getOrderId(), attempt, ex);
                    stockReservationService.recordConcurrencyFailure(event);
                    return;
                }
                log.info("Order {}: optimistic lock conflict on attempt {}/{} -- retrying with a fresh read",
                        event.getOrderId(), attempt, MAX_ATTEMPTS);
            }
        }
    }
}
