package com.learn.orderservice.scheduler;

import com.learn.orderservice.entity.OutboxEvent;
import com.learn.orderservice.entity.OutboxStatus;
import com.learn.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Polling implementation of the outbox relay: periodically asks the database for
// unpublished rows and republishes them to Kafka. See the README for why this
// project starts with polling instead of a CDC/Debezium-based outbox.
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    // Keeps the domain event name (what the outbox row calls itself) decoupled from the
    // Kafka topic name (a separate, infrastructure-level naming convention).
    private static final Map<String, String> TOPICS_BY_EVENT_TYPE = Map.of(
            "OrderCreated", "order.created"
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE, Sort.by("createdAt").ascending())
        );

        for (OutboxEvent event : batch) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        String topic = TOPICS_BY_EVENT_TYPE.get(event.getEventType());
        if (topic == null) {
            log.warn("No topic mapping for event type '{}' (outbox id {}); marking FAILED",
                    event.getEventType(), event.getId());
            event.setStatus(OutboxStatus.FAILED);
            outboxEventRepository.save(event);
            return;
        }

        try {
            // .get() blocks until the broker acknowledges the send (or throws), which is what
            // makes it safe to flip the row to PUBLISHED right after -- a fire-and-forget send()
            // would let this method move on before knowing whether the message actually landed.
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
        } catch (Exception e) {
            // Leave the row PENDING on any failure (timeout, broker unavailable, interrupted wait) --
            // the next scheduled run will simply try it again.
            log.warn("Failed to publish outbox event {} to topic {}: {}", event.getId(), topic, e.getMessage());
        }
    }
}
