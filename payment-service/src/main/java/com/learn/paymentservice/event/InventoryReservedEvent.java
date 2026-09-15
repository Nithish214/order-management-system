package com.learn.paymentservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Mirrors the JSON shape Inventory Service's OrderCreatedListener#reservedPayload builds --
// no shared library between the two services, just an agreed-upon wire format, same as
// every other cross-service event in this system. Only the RESERVED shape is modelled
// here (unlike Order Service's InventoryOutcomeEvent, which also covers FAILED) because
// this service only ever subscribes to inventory.reserved, never inventory.failed.
@Getter
@Setter
@NoArgsConstructor
public class InventoryReservedEvent {

    private String eventId;
    private Long orderId;
    private String status;
    private BigDecimal totalAmount;
}
