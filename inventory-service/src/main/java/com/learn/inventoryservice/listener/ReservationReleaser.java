package com.learn.inventoryservice.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import jakarta.persistence.OptimisticLockException;

// Credits back whatever this service actually reserved for an order (via
// OrderReservationItem), then deletes those rows -- they're no longer active
// reservations. Shared by OrderCancelledListener (order.cancelled) and
// PaymentFailedListener (payment.failed): both mean the exact same thing to this service --
// "this order isn't happening any more, give the stock back" -- they just arrive from two
// different sources, so the release logic itself shouldn't be duplicated.
//
// Deliberately NOT @Transactional itself: this method's only job now is retrying
// StockReleaseService.releaseAttempt(), and needs to call it fresh (through that bean's own
// proxy) on each attempt to get a genuinely new transaction each time -- see
// StockReleaseService's comment for why REQUIRES_NEW there is what actually makes that work
// when called from inside an already-transactional caller.
@Component
public class ReservationReleaser {

    private static final Logger log = LoggerFactory.getLogger(ReservationReleaser.class);

    private static final int MAX_ATTEMPTS = 3;

    private final StockReleaseService stockReleaseService;

    public ReservationReleaser(StockReleaseService stockReleaseService) {
        this.stockReleaseService = stockReleaseService;
    }

    public void release(Long orderId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                stockReleaseService.releaseAttempt(orderId);
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    // Unlike OrderCreatedListener, there's no "inventory.failed"-style topic
                    // for a release -- nothing downstream is synchronously waiting on a
                    // release to succeed or fail the way Payment Service waits on a
                    // reservation. So instead of inventing a new failure event, this just
                    // rethrows: the caller (PaymentFailedListener/OrderCancelledListener) is
                    // itself @Transactional, so its processed_event row never gets written
                    // either, and Kafka's at-least-once delivery will redeliver the same
                    // payment.failed/order.cancelled message later, when the contention has
                    // presumably passed -- the existing idempotency guard makes that safe to
                    // just let happen rather than handling it specially here.
                    log.warn("Order {}: still conflicting after {} attempts releasing stock -- " +
                            "giving up for now, will retry when Kafka redelivers this event",
                            orderId, attempt, ex);
                    throw ex;
                }
                log.info("Order {}: optimistic lock conflict releasing stock on attempt {}/{} -- " +
                        "retrying with a fresh read", orderId, attempt, MAX_ATTEMPTS);
            }
        }
    }
}
