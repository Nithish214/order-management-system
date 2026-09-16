package com.learn.paymentservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.paymentservice.config.CorrelationIdConstants;
import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.entity.OutboxStatus;
import com.learn.paymentservice.entity.Payment;
import com.learn.paymentservice.entity.PaymentStatus;
import com.learn.paymentservice.entity.ProcessedEvent;
import com.learn.paymentservice.event.InventoryReservedEvent;
import com.learn.paymentservice.event.OutboxEventCreated;
import com.learn.paymentservice.repository.OutboxEventRepository;
import com.learn.paymentservice.repository.PaymentRepository;
import com.learn.paymentservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

// "groupId = payment-service" -- same cooperative-consumption reasoning as the other
// services' listeners: every instance of this service sharing that id splits up
// inventory.reserved's partitions between them, each message still handled exactly once
// per group.
@Service
public class InventoryReservedListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservedListener.class);
    private static final String DECLINE_REASON = "Simulated payment decline";

    private final ProcessedEventRepository processedEventRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryReservedListener(
            ProcessedEventRepository processedEventRepository,
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory.reserved", groupId = "payment-service")
    @Transactional
    public void onInventoryReserved(
            String message,
            @Header(value = CorrelationIdConstants.CORRELATION_ID_HEADER, required = false) String correlationId
    ) throws Exception {
        if (correlationId != null) {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        }
        try {
        InventoryReservedEvent event = objectMapper.readValue(message, InventoryReservedEvent.class);
        String eventId = event.getEventId();

        // Idempotency guard: Kafka is at-least-once, not exactly-once -- a redelivered
        // inventory.reserved would otherwise charge (simulate) the same order twice.
        // Identical pattern to Inventory Service's OrderCreatedListener.
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping already-processed event {}", eventId);
            return;
        }

        // Simulated payment gateway: ~90% success, not tied to amount at all. nextInt(100)
        // returns a uniform value in [0, 100) -- true for values 0..89 (90 of the 100
        // possible outcomes), so this is exactly a 90% chance, not an off-by-one 89% or 91%.
        boolean success = ThreadLocalRandom.current().nextInt(100) < 90;

        Payment payment = new Payment();
        payment.setOrderId(event.getOrderId());
        payment.setAmount(event.getTotalAmount());
        payment.setStatus(success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        payment.setFailureReason(success ? null : DECLINE_REASON);
        payment = paymentRepository.save(payment);

        // Recorded regardless of outcome (success or decline) -- either way this exact
        // message should never be reprocessed, and it must land in the same transaction as
        // the payment row so both commit or roll back together.
        processedEventRepository.save(new ProcessedEvent(eventId, LocalDateTime.now()));

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Payment");
        outboxEvent.setAggregateId(String.valueOf(payment.getPaymentId()));
        outboxEvent.setEventType(success ? "PaymentCompleted" : "PaymentFailed");
        outboxEvent.setStatus(OutboxStatus.PENDING);
        // Placeholder: the payload column is NOT NULL, and this row's generated id (only
        // allocated by save()) needs to be embedded in its own payload as the idempotency
        // key downstream consumers will use. Overwritten below once that id exists --
        // identical two-step pattern to Order Service's OrderController.
        outboxEvent.setPayload("{}");
        // Same reasoning as Order Service's OrderCreationService -- captured once, here,
        // from whatever's in MDC right now (fed by the incoming inventory.reserved
        // message's own header above), since OutboxPublisher.publish() may run on a
        // different thread or minutes later via the poller.
        outboxEvent.setCorrelationId(correlationId);
        outboxEvent = outboxEventRepository.save(outboxEvent);
        outboxEvent.setPayload(success
                ? completedPayload(outboxEvent.getId(), event.getOrderId())
                : failedPayload(outboxEvent.getId(), event.getOrderId(), DECLINE_REASON));

        // Synchronous and returns immediately -- registers OutboxEventCreatedListener's
        // callback with Spring's transaction synchronization machinery; the actual publish
        // is deferred until AFTER_COMMIT (see that listener's comment).
        applicationEventPublisher.publishEvent(new OutboxEventCreated(outboxEvent.getId()));

        log.info("Payment {} for order {}: payment {}",
                success ? "completed" : "failed", event.getOrderId(), payment.getPaymentId());
        } finally {
            if (correlationId != null) {
                MDC.remove(CorrelationIdConstants.MDC_KEY);
            }
        }
    }

    private String completedPayload(Long eventId, Long orderId) {
        return """
                {"eventId":%d,"orderId":%d,"status":"COMPLETED"}""".formatted(eventId, orderId);
    }

    // Carries "reason" the same way Inventory Service's failedPayload does -- Order
    // Service's orders table has no column to persist it (this is purely a log-line
    // detail on the consuming side, same as inventory.failed's reason today), but it costs
    // nothing to include and matches the existing asymmetry (RESERVED/COMPLETED carry no
    // reason; FAILED does).
    private String failedPayload(Long eventId, Long orderId, String reason) {
        return """
                {"eventId":%d,"orderId":%d,"status":"FAILED","reason":"%s"}"""
                .formatted(eventId, orderId, reason);
    }
}
