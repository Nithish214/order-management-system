package com.learn.paymentservice.scheduler;

import com.learn.paymentservice.config.CorrelationIdConstants;
import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.entity.OutboxStatus;
import com.learn.paymentservice.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// The actual "send this outbox row to Kafka" logic, shared by both OutboxPoller and
// OutboxEventCreatedListener -- identical role and REQUIRES_NEW reasoning to Order
// Service's OutboxPublisher (see its own comment for the full explanation of why
// REQUIRES_NEW specifically matters for a row written from an AFTER_COMMIT callback).
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final Map<String, String> TOPICS_BY_EVENT_TYPE = Map.of(
            "PaymentCompleted", "payment.completed",
            "PaymentFailed", "payment.failed"
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(OutboxEvent event) {
        String topic = TOPICS_BY_EVENT_TYPE.get(event.getEventType());
        if (topic == null) {
            log.warn("No topic mapping for event type '{}' (outbox id {}); marking FAILED",
                    event.getEventType(), event.getId());
            event.setStatus(OutboxStatus.FAILED);
            outboxEventRepository.save(event);
            return;
        }

        // Same reasoning as Order Service's identical OutboxPublisher -- read from the
        // row, not MDC, since this can run on the async listener's own executor thread
        // or minutes later via the scheduled poller with no live request context at all.
        String correlationId = event.getCorrelationId();
        if (correlationId != null) {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        }
        try {
            List<org.apache.kafka.common.header.Header> headers = correlationId == null
                    ? List.of()
                    : List.of(new RecordHeader(
                            CorrelationIdConstants.CORRELATION_ID_HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic, null, event.getAggregateId(), event.getPayload(), headers);

            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
            LocalDateTime now = LocalDateTime.now();
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(now);
            outboxEventRepository.save(event);
            log.info("Published outbox event {} ({} -> {}), {}ms after it was created",
                    event.getId(), event.getEventType(), topic,
                    Duration.between(event.getCreatedAt(), now).toMillis());
        } catch (Exception e) {
            // Leave the row PENDING on any failure -- whichever path notices it next (the
            // scheduled poller, always) will simply try it again.
            log.warn("Failed to publish outbox event {} to topic {}: {}", event.getId(), topic, e.getMessage());
        } finally {
            if (correlationId != null) {
                MDC.remove(CorrelationIdConstants.MDC_KEY);
            }
        }
    }
}
