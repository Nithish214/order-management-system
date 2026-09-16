package com.learn.orderservice.scheduler;

import com.learn.orderservice.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Idempotency keys exist to survive a client retry, and a real retry (a browser resend, a
// mobile app's exponential backoff after a dropped connection) happens within seconds to
// low minutes of the original attempt, essentially never hours later -- once a key is a day
// old, it has done its job and is never going to be looked up again. Kept for a window, not
// forever: forever would mean this table grows without bound for as long as the store runs,
// for rows that stopped being useful the same day they were written. 24 hours is generous
// slack well beyond any realistic retry delay, without holding onto dead weight for long.
//
// Not kept for zero time either -- a customer who force-quits a slow-loading browser tab and
// reopens it minutes later to resubmit still needs their original key to still be there, or
// this table isn't actually protecting the case it exists for.
@Component
public class IdempotencyKeyCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupTask.class);

    private static final int RETENTION_HOURS = 24;

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyKeyCleanupTask(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    // Once an hour is plenty for a 24-hour retention window -- this doesn't need to be
    // precise to the minute, just needs to keep the table from growing unbounded.
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void deleteExpiredKeys() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
        long deleted = idempotencyKeyRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency key(s) older than {}", deleted, cutoff);
        }
    }
}
