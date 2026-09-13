package com.learn.orderservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Shape shared by both inventory.reserved and inventory.failed (Inventory Service's
// OrderCreatedListener#reservedPayload / #failedPayload) -- "reason" is simply absent
// (null after deserialization) on a reserved message.
@Getter
@Setter
@NoArgsConstructor
public class InventoryOutcomeEvent {

    private String eventId;
    private Long orderId;
    private String status;
    private String reason;
}
