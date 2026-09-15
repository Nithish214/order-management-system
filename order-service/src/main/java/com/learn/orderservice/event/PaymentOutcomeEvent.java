package com.learn.orderservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Mirrors the JSON shape Payment Service's InventoryReservedListener builds for both
// payment.completed and payment.failed -- no shared library between the two services,
// just an agreed-upon wire format, same as InventoryOutcomeEvent for inventory.failed.
// eventId is a plain number here (Payment Service's own outbox row id), unlike
// InventoryOutcomeEvent's String eventId (Inventory Service instead mints a random UUID
// per message) -- two independently-evolved services, two different id schemes for the
// same underlying concept.
@Getter
@Setter
@NoArgsConstructor
public class PaymentOutcomeEvent {

    private Long eventId;
    private Long orderId;
    private String status;
    private String reason;
}
