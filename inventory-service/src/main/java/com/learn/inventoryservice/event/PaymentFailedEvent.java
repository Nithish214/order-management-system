package com.learn.inventoryservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Mirrors the JSON shape Payment Service's InventoryReservedListener builds for
// payment.failed -- no shared library between the two services, just an agreed-upon wire
// format, same as every other cross-service event in this system. eventId is a plain
// number (Payment Service's own outbox row id), unlike this service's own OrderCreatedEvent/
// OrderCancelledEvent counterparts on the Order Service side, which use Order Service's ids
// -- each producer mints ids its own way.
@Getter
@Setter
@NoArgsConstructor
public class PaymentFailedEvent {
    private Long eventId;
    private Long orderId;
    private String status;
    private String reason;
}
