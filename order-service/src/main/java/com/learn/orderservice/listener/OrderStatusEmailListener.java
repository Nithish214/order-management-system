package com.learn.orderservice.listener;

import com.learn.orderservice.entity.Order;
import com.learn.orderservice.event.OrderStatusChanged;
import com.learn.orderservice.repository.OrderRepository;
import com.learn.orderservice.service.OrderNotificationEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Same AFTER_COMMIT + @Async reasoning as OutboxEventCreatedListener: raised from inside
// the same transaction that changes an order's status (OrderStatusUpdater, or
// OrderController#cancelOrder), but only actually runs once that transaction has
// genuinely committed -- and off the request/listener thread entirely, so a slow or
// sandboxed SES call can never add latency to (or fail) the status change itself.
//
// Looks the order back up by id, with its user joined, rather than trusting anything
// carried on the event -- same "fresh read of the now-committed row" reasoning as
// OutboxEventCreatedListener.
@Component
public class OrderStatusEmailListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusEmailListener.class);

    private final OrderRepository orderRepository;
    private final OrderNotificationEmailService orderNotificationEmailService;

    public OrderStatusEmailListener(
            OrderRepository orderRepository,
            OrderNotificationEmailService orderNotificationEmailService
    ) {
        this.orderRepository = orderRepository;
        this.orderNotificationEmailService = orderNotificationEmailService;
    }

    @Async("outboxPublisherExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChanged event) {
        Order order = orderRepository.findByIdWithUserAndItems(event.orderId()).orElse(null);
        if (order == null) {
            // Shouldn't happen (the row was just committed moments ago), but logging
            // instead of throwing -- there's nothing this listener could fix by failing
            // loudly, and the order itself is already committed either way.
            log.warn("OrderStatusChanged fired for id {} but no such order exists", event.orderId());
            return;
        }
        orderNotificationEmailService.sendStatusEmail(order);
    }
}
