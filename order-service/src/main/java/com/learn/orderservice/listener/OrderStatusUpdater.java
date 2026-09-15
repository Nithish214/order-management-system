package com.learn.orderservice.listener;

import com.learn.orderservice.entity.Order;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public OrderStatusUpdater(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void applyStatus(Long orderId, OrderStatus status, String reason) {
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
            log.info("Order {} rejected: {}", orderId, reason);
        } else {
            log.info("Order {} confirmed", orderId);
        }
    }
}
