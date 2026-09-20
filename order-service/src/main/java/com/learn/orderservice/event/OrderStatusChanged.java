package com.learn.orderservice.event;

// Same shape and reasoning as OutboxEventCreated: an internal, in-process Spring
// application event carrying just the order's id, not a copy of its data. By the time
// this is actually handled (see OrderStatusEmailListener), the transaction that changed
// the status has already committed, so looking the order back up fresh is both simpler
// and guaranteed to see the real, final state -- there's nothing to gain from smuggling
// a possibly-stale status/email through the event itself.
public record OrderStatusChanged(Long orderId) {
}
