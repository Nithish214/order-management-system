package com.learn.inventoryservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.inventoryservice.entity.ProcessedEvent;
import com.learn.inventoryservice.event.OrderCancelledEvent;
import com.learn.inventoryservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Mirrors OrderCreatedListener's structure -- same idempotency ledger, same
// "record-then-act" transactional shape. The actual release logic lives in
// ReservationReleaser, shared with the new PaymentFailedListener.
//
// KNOWN LIMITATION, worth being honest about rather than pretending it's handled: this
// listens on a *separate* topic (order.cancelled) from OrderCreatedListener's
// (order.created). Kafka only guarantees ordering within a single topic-partition, so in
// principle, if this service's order.cancelled consumer got ahead of its order.created
// consumer (e.g. after a rebalance or restart introduces lag on just one of the two), a
// cancellation could theoretically be processed before the reservation it's meant to
// release even exists -- this listener would correctly find nothing to release (since
// OrderReservationItem has no rows for that order yet), mark the event processed, and then
// OrderCreatedListener would go on to reserve stock moments later for an order the customer
// already cancelled, with nothing left to ever release it. The realistic window for this is
// narrow (the outbox poller runs every 3s and always creates the OrderCreated row first),
// but it isn't zero. The textbook fix is putting both event types on one topic, partitioned
// by orderId, so Kafka's real ordering guarantee actually applies -- left as a follow-up
// rather than restructuring the existing order.created pipeline for this pass.
// PaymentFailedListener does NOT share this limitation -- see its own comment for why.
@Service
public class OrderCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ReservationReleaser reservationReleaser;
    private final ObjectMapper objectMapper;

    public OrderCancelledListener(
            ProcessedEventRepository processedEventRepository,
            ReservationReleaser reservationReleaser,
            ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.reservationReleaser = reservationReleaser;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.cancelled", groupId = "inventory-service")
    @Transactional
    public void onOrderCancelled(String message) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(message, OrderCancelledEvent.class);
        String eventId = String.valueOf(event.getEventId());

        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping already-processed event {}", eventId);
            return;
        }

        reservationReleaser.release(event.getOrderId());
        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));
    }
}
