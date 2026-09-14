package com.learn.inventoryservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Deliberately minimal (unlike OrderCreatedEvent) -- no item list. This service looks up
// what it actually reserved for this order itself (see OrderReservationItem) rather than
// trusting a repeated item list from Order Service. See OrderController#buildOrderCancelledPayload
// on the producing side for why.
@Getter
@Setter
@NoArgsConstructor
public class OrderCancelledEvent {
    private Long eventId;
    private Long orderId;
}
