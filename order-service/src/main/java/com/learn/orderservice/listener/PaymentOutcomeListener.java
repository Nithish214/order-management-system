package com.learn.orderservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.event.PaymentOutcomeEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Closes the loop Payment Service's InventoryReservedListener started: an order only
// reaches CONFIRMED once payment has actually succeeded, not the moment stock was set
// aside for it (see InventoryOutcomeListener's comment for why that shortcut was
// removed). Same OrderStatusUpdater as InventoryOutcomeListener -- "don't clobber a
// customer's own cancellation" and "log unknown order" apply identically here, so that
// logic isn't repeated.
@Service
public class PaymentOutcomeListener {

    // Same principle as InventoryOutcomeListener's CUSTOMER_MESSAGE, extended here too:
    // Payment Service's real reason today is "Simulated payment decline" (see
    // InventoryReservedListener's DECLINE_REASON) -- accurate, but it discloses an internal
    // implementation detail (that payment is simulated at all) that no customer should
    // ever see, real gateway or not.
    private static final String CUSTOMER_MESSAGE = "We couldn't process your payment. Please try again.";

    private final ObjectMapper objectMapper;
    private final OrderStatusUpdater orderStatusUpdater;

    public PaymentOutcomeListener(ObjectMapper objectMapper, OrderStatusUpdater orderStatusUpdater) {
        this.objectMapper = objectMapper;
        this.orderStatusUpdater = orderStatusUpdater;
    }

    @KafkaListener(topics = "payment.completed", groupId = "order-service")
    @Transactional
    public void onPaymentCompleted(String message) throws Exception {
        PaymentOutcomeEvent event = objectMapper.readValue(message, PaymentOutcomeEvent.class);
        orderStatusUpdater.applyStatus(event.getOrderId(), OrderStatus.CONFIRMED, null, null);
    }

    @KafkaListener(topics = "payment.failed", groupId = "order-service")
    @Transactional
    public void onPaymentFailed(String message) throws Exception {
        PaymentOutcomeEvent event = objectMapper.readValue(message, PaymentOutcomeEvent.class);
        orderStatusUpdater.applyStatus(event.getOrderId(), OrderStatus.REJECTED, CUSTOMER_MESSAGE, event.getReason());
    }
}
