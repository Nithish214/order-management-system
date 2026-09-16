package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@SequenceGenerator(name = "outbox_event_seq", sequenceName = "outbox_event_seq", allocationSize = 1)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_event_seq")
    private Long id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // Stamped once, at creation, from whatever's in MDC at that moment (the incoming
    // request's X-Correlation-Id, set by CorrelationIdFilter) -- deliberately NOT read
    // fresh from MDC at publish time, since this row's publish can happen on a
    // completely different thread (the async outbox listener) or minutes later (the
    // scheduled poller safety net, with no HTTP request/MDC context at all by then).
    // Persisting it here is what makes OutboxPublisher able to attach the right Kafka
    // header regardless of which of those two paths actually sends this row.
    @Column(name = "correlation_id")
    private String correlationId;
}
