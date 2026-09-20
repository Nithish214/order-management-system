package com.learn.orderservice.listener;

import com.learn.orderservice.entity.Order;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.event.OrderStatusChanged;
import com.learn.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// Shared by every listener that reacts to some other service's verdict on an order --
// today that's InventoryOutcomeListener (inventory.failed) and PaymentOutcomeListener
// (payment.completed/payment.failed). Extracted out rather than duplicated in each,
// since all three cases share the exact same two rules: don't clobber a customer's own
// cancellation, and there's nothing to do if the order somehow doesn't exist.
//
// Unlike Inventory Service's stock decrement, setting an order's status is naturally
// idempotent -- applying the same outcome twice leaves the order in the same state, so no
// processed_event-style dedup table is needed here.
@Component
public class OrderStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusUpdater.class);

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderStatusUpdater(OrderRepository orderRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.orderRepository = orderRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    // customerReason is what actually gets persisted and returned by the API -- plain
    // language, safe for any authenticated caller to read back. internalDetail is the
    // real, technical reason (exact stock figures, which payment gateway said what),
    // logged here for debugging/ops visibility but never persisted or exposed -- pass
    // null when status isn't REJECTED, or when customerReason already is the full story.
    //
    // These two are deliberately not the same value: Inventory Service's real rejection
    // reason looks like "product 7 requested 3 but only 1 available", which is exactly
    // the kind of thing that should never reach a customer verbatim -- not because it's
    // poorly worded, but because it discloses this business's exact live stock levels to
    // anyone willing to place a large-enough order and read the error. See the two
    // callers' CUSTOMER_MESSAGE constants for what actually gets shown instead.
    public void applyStatus(Long orderId, OrderStatus status, String customerReason, String internalDetail) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received outcome for unknown order {}", orderId);
            return;
        }
        // Don't clobber a status the customer already set themselves -- if they cancelled
        // while this outcome was in flight, their cancellation should stick, not get
        // silently overwritten by an outcome that's now moot.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Ignoring outcome for order {} -- already cancelled by customer", orderId);
            return;
        }
        order.setStatus(status);
        if (status == OrderStatus.REJECTED) {
            order.setRejectionReason(customerReason);
            log.info("Order {} rejected: {}", orderId, internalDetail != null ? internalDetail : customerReason);
        } else {
            log.info("Order {} confirmed", orderId);
        }
        // Same AFTER_COMMIT-deferred pattern as OutboxEventCreated -- see
        // OrderStatusEmailListener. Raised here, inside the same transaction that's about
        // to commit the status change, but the actual email send only happens once that
        // commit has genuinely gone through.
        applicationEventPublisher.publishEvent(new OrderStatusChanged(orderId));
    }
}
