package com.learn.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// One row per Kafka event this service has successfully handled -- the idempotency
// ledger that makes InventoryReservedListener safe against redelivery/duplicates.
// Identical shape and purpose to Inventory Service's ProcessedEvent.
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
