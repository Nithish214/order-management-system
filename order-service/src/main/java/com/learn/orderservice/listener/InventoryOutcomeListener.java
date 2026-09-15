package com.learn.orderservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.event.InventoryOutcomeEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Only inventory.failed is handled here now -- stock that can never be fulfilled means
// there's nothing to pay for, so this order is rejected immediately, the same way it
// always was. inventory.reserved used to also be consumed here (straight to CONFIRMED,
// the moment stock was set aside), but now that Payment Service sits between "stock
// reserved" and "order confirmed", that would confirm an order before it's actually been
// paid for -- CONFIRMED now only ever comes from PaymentOutcomeListener's
// payment.completed handler instead. Order Service doesn't need to react to
// inventory.reserved at all any more; the order simply stays PENDING until Payment
// Service decides its fate.
@Service
public class InventoryOutcomeListener {

    private final ObjectMapper objectMapper;
    private final OrderStatusUpdater orderStatusUpdater;

    public InventoryOutcomeListener(ObjectMapper objectMapper, OrderStatusUpdater orderStatusUpdater) {
        this.objectMapper = objectMapper;
        this.orderStatusUpdater = orderStatusUpdater;
    }

    @KafkaListener(topics = "inventory.failed", groupId = "order-service")
    @Transactional
    public void onInventoryFailed(String message) throws Exception {
        InventoryOutcomeEvent event = objectMapper.readValue(message, InventoryOutcomeEvent.class);
        orderStatusUpdater.applyStatus(event.getOrderId(), OrderStatus.REJECTED, event.getReason());
    }
}
