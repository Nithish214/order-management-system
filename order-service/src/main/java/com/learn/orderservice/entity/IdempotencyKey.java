package com.learn.orderservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// One row per client-supplied Idempotency-Key that has successfully resulted in an order.
// Written in the exact same transaction as the order + outbox event it records (see
// OrderCreationService) -- same atomicity guarantee OutboxEvent already relies on: either
// all three rows exist, or none of them do. `key` being the primary key is what makes two
// concurrent requests for the same key structurally unable to both succeed -- see
// OrderController for how the resulting unique-constraint violation is turned into "return
// the winner's response" instead of a raw 500.
@Entity
@Table(name = "idempotency_key")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(name = "key", length = 255)
    private String key;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    // The exact bytes returned the first time, replayed byte-for-byte on a retry rather
    // than reconstructed from the order's current state -- so a retry sees precisely what
    // the original call saw, even if OrderResponse's shape changes in a later deploy
    // between the original call and the retry.
    @Column(name = "response_body", columnDefinition = "CLOB", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public IdempotencyKey(String key, Long orderId, Integer responseStatus, String responseBody) {
        this.key = key;
        this.orderId = orderId;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }
}
