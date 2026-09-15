package com.learn.inventoryservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.inventoryservice.entity.ProcessedEvent;
import com.learn.inventoryservice.event.PaymentFailedEvent;
import com.learn.inventoryservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// A declined payment means the order isn't happening -- whatever stock was reserved for
// it needs to go back, exactly like a customer cancellation (see ReservationReleaser,
// shared with OrderCancelledListener). Same idempotency requirement as every other
// consumer here too: Kafka is at-least-once, and a redelivered payment.failed must not
// credit stock back twice for the same order -- yes, this needs its own processed_event
// guard, the same as the other three listeners in this system already have, not something
// safe to skip just because it's "only" restoring stock rather than deducting it.
//
// Unlike OrderCancelledListener, this one does NOT share that listener's ordering
// limitation. payment.failed can only ever be produced as a causal consequence of this
// exact service having already published inventory.reserved for this order -- and
// OrderReservationItem is written in the very same transaction as that publish (see
// OrderCreatedListener). So by the time Payment Service could possibly see
// inventory.reserved, let alone act on it and produce payment.failed, the reservation row
// this listener looks up is already guaranteed to exist. There's no independent race here
// the way there is between order.created and order.cancelled, which have no such shared
// origin.
@Service
public class PaymentFailedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ReservationReleaser reservationReleaser;
    private final ObjectMapper objectMapper;

    public PaymentFailedListener(
            ProcessedEventRepository processedEventRepository,
            ReservationReleaser reservationReleaser,
            ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.reservationReleaser = reservationReleaser;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.failed", groupId = "inventory-service")
    @Transactional
    public void onPaymentFailed(String message) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
        // Prefixed, not a bare String.valueOf(...): processed_event is one shared table
        // fed by THREE independently-numbered id sequences in this service now --
        // order.created/order.cancelled both use Order Service's own outbox id (one
        // sequence, since they share a single outbox_event table there), and payment.failed
        // uses Payment Service's own, completely separate outbox id, which also starts
        // counting from 1 in its own database. Found live: Payment Service's outbox id 2
        // collided with an order.created event's id "2" already sitting in this table from
        // September 11th, days before Payment Service even existed -- every payment.failed
        // in this test run was silently skipped as "already processed", and no stock was
        // ever released. A bare integer id is only unique within the sequence that
        // generated it, never across independently-numbered sequences sharing one table.
        String eventId = "payment-failed:" + event.getEventId();

        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping already-processed event {}", eventId);
            return;
        }

        reservationReleaser.release(event.getOrderId());
        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));
    }
}
