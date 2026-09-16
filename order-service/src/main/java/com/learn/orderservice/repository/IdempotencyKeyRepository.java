package com.learn.orderservice.repository;

import com.learn.orderservice.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    // Used by IdempotencyKeyCleanupTask -- rows older than the retention window are safe to
    // forget entirely, since no genuinely-delayed retry realistically arrives that late.
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
