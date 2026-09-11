package com.learn.inventoryservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// Mirrors the JSON shape OrderController builds into the outbox payload
// (order-service's OrderController#buildOrderCreatedPayload). Jackson deserializes
// into this via the no-arg constructor + setters -- no shared library between the
// two services, just an agreed-upon wire format, which is normal for Kafka events.
@Getter
@Setter
@NoArgsConstructor
public class OrderCreatedEvent {

    private Long eventId;
    private Long orderId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long productId;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
