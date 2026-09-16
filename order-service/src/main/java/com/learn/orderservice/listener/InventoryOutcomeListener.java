package com.learn.orderservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.orderservice.config.CorrelationIdFilter;
import com.learn.orderservice.entity.OrderStatus;
import com.learn.orderservice.event.InventoryOutcomeEvent;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
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

    // Deliberately generic -- Inventory Service's real reason (event.getReason(), e.g.
    // "product 7 requested 3 but only 1 available") is genuinely useful for debugging and
    // still gets logged in full (see the call below), but it is never what gets persisted
    // or returned by the API. Exposing exact available_quantity figures to any
    // authenticated caller who orders enough of something to trigger this message would
    // let them enumerate this business's live stock levels for free -- a real
    // information leak, not just a rough-edges wording choice.
    private static final String CUSTOMER_MESSAGE = "One or more items in your order are currently out of stock.";

    private final ObjectMapper objectMapper;
    private final OrderStatusUpdater orderStatusUpdater;

    public InventoryOutcomeListener(ObjectMapper objectMapper, OrderStatusUpdater orderStatusUpdater) {
        this.objectMapper = objectMapper;
        this.orderStatusUpdater = orderStatusUpdater;
    }

    @KafkaListener(topics = "inventory.failed", groupId = "order-service")
    @Transactional
    public void onInventoryFailed(
            String message,
            // required = false: covers a message published before this feature existed,
            // or sent by some other producer that doesn't set this header -- falls back
            // to processing normally, just without a correlation id attached to the logs.
            @Header(value = CorrelationIdFilter.CORRELATION_ID_HEADER, required = false) String correlationId
    ) throws Exception {
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            InventoryOutcomeEvent event = objectMapper.readValue(message, InventoryOutcomeEvent.class);
            orderStatusUpdater.applyStatus(event.getOrderId(), OrderStatus.REJECTED, CUSTOMER_MESSAGE, event.getReason());
        } finally {
            if (correlationId != null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            }
        }
    }
}
