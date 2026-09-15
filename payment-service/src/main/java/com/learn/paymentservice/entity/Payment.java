package com.learn.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    // No foreign key -- orderId refers to a row in a genuinely different database
    // (orderdb), which Postgres cannot enforce a constraint across. Same reasoning as
    // order_reservation_item.orderId in Inventory Service.
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Never actually touched again after creation today -- a payment row is decided once,
    // synchronously, and never revisited (see PaymentStatus's comment). Set alongside
    // createdAt now so the column is never null; a real update path would need to also
    // refresh this on write.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
