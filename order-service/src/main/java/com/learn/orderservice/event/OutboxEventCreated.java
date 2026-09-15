package com.learn.orderservice.event;

// An internal, in-process Spring application event -- not a Kafka message, and never
// leaves this JVM. It carries just enough to look the row back up (its own database id)
// rather than the row's full content, since by the time this is actually handled
// (see OutboxEventCreatedListener), the transaction that created it has already committed
// and the row is durably in Postgres -- there's no reason to smuggle a copy of its data
// through the event itself when a fresh read gets the authoritative version for free.
public record OutboxEventCreated(Long outboxEventId) {
}
