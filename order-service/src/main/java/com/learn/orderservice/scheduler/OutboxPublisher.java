package com.learn.orderservice.scheduler;

import com.learn.orderservice.config.CorrelationIdFilter;
import com.learn.orderservice.entity.OutboxEvent;
import com.learn.orderservice.entity.OutboxStatus;
import com.learn.orderservice.repository.OutboxEventRepository;
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

// The actual "send this outbox row to Kafka" logic, extracted out of OutboxPoller so both
// it and OutboxEventCreatedListener (the new event-driven path) call exactly the same
// code -- there is only one implementation of "how a row gets published" in this service,
// regardless of which of the two triggers noticed it needed publishing.
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    // Keeps the domain event name (what the outbox row calls itself) decoupled from the
    // Kafka topic name (a separate, infrastructure-level naming convention).
    private static final Map<String, String> TOPICS_BY_EVENT_TYPE = Map.of(
            "OrderCreated", "order.created",
            "OrderCancelled", "order.cancelled"
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // REQUIRES_NEW, not the default REQUIRED, and not a coincidence: this method is called
    // both from a plain @Scheduled method (no transaction active at all) and from
    // OutboxEventCreatedListener's AFTER_COMMIT callback -- and that second caller is the
    // real reason this matters. At the exact moment an AFTER_COMMIT callback runs, the
    // original transaction has already physically committed, but Spring's own
    // TransactionSynchronizationManager bookkeeping for it hasn't been fully cleared yet
    // (that happens right after all afterCommit callbacks return). A REQUIRED-propagation
    // call made from inside that window can mistakenly try to "join" that
    // already-finishing transaction instead of starting a genuinely new one -- and the
    // write silently gets swept into a transaction that's already being torn down,
    // updating nothing, without throwing any exception. That's exactly what was observed
    // here: the Kafka send succeeded and logged success, but the subsequent save()
    // marking the row PUBLISHED never actually persisted, so the (now-infrequent)
    // scheduled poller found the same row still PENDING a full cycle later and published
    // it a second time. REQUIRES_NEW forces a genuinely independent transaction every
    // time, regardless of whatever transactional context (real or already-finishing) the
    // caller happens to be in.
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

        // Read from the row itself, not MDC -- this method runs on whichever thread/path
        // happened to notice the row needed publishing (the async event listener's own
        // executor thread, or the scheduled poller with no HTTP context at all), neither
        // of which reliably has the original request's MDC still attached. Setting it here
        // means this method's OWN log line below also carries the id, not just whatever
        // ends up in the Kafka header.
        String correlationId = event.getCorrelationId();
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            List<org.apache.kafka.common.header.Header> headers = correlationId == null
                    ? List.of()
                    : List.of(new RecordHeader(
                            CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic, null, event.getAggregateId(), event.getPayload(), headers);

            // .get() blocks until the broker acknowledges the send (or throws), which is what
            // makes it safe to flip the row to PUBLISHED right after -- a fire-and-forget send()
            // would let this method move on before knowing whether the message actually landed.
            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
            LocalDateTime now = LocalDateTime.now();
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(now);
            outboxEventRepository.save(event);
            // The number to actually watch when checking whether the event-driven path is
            // working: this gap should now read low milliseconds for anything published via
            // OutboxEventCreatedListener, versus up to the poller's own interval (previously
            // 3s, now the safety-net interval) for anything that fell through to it instead.
            log.info("Published outbox event {} ({} -> {}), {}ms after it was created",
                    event.getId(), event.getEventType(), topic,
                    Duration.between(event.getCreatedAt(), now).toMillis());
        } catch (Exception e) {
            // Leave the row PENDING on any failure (timeout, broker unavailable, interrupted wait) --
            // whichever path notices it next (the scheduled poller, always; another event-driven
            // attempt only if some other row's publish happens to sweep this one up too) will
            // simply try it again. See OutboxEventCreatedListener's class comment for why this
            // specific row won't automatically get a second event-driven attempt of its own.
            log.warn("Failed to publish outbox event {} to topic {}: {}", event.getId(), topic, e.getMessage());
        } finally {
            if (correlationId != null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            }
        }
    }
}
