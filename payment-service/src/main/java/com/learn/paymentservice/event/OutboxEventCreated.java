package com.learn.paymentservice.event;

// An internal, in-process Spring application event -- not a Kafka message, never leaves
// this JVM. Carries just the row's own id; see OutboxEventCreatedListener for why a fresh
// read by id is preferred over smuggling the row's data through the event itself.
// Identical role to Order Service's OutboxEventCreated.
public record OutboxEventCreated(Long outboxEventId) {
}
