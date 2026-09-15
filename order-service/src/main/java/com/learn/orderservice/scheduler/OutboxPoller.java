package com.learn.orderservice.scheduler;

import com.learn.orderservice.entity.OutboxEvent;
import com.learn.orderservice.entity.OutboxStatus;
import com.learn.orderservice.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Now a SAFETY NET, not the primary publishing path -- OutboxEventCreatedListener handles
// the normal case (a new row gets published within milliseconds, right after its
// transaction commits). This still has to exist alongside it, for exactly the cases an
// in-process event can't cover:
//   - The app crashes/is killed between the transaction committing and the AFTER_COMMIT
//     listener finishing its publish -- there's no persistent record that an in-process
//     event was ever raised, so nothing else will ever retry it except this poller
//     independently re-scanning the table from scratch.
//   - The AFTER_COMMIT listener's own Kafka send fails (broker briefly unreachable, etc.)
//     -- see OutboxPublisher, which leaves the row PENDING on any failure. That one-shot
//     in-process event doesn't retry itself, so recovery for that specific row depends
//     entirely on this poller finding it on its next sweep.
// 30s instead of the old 3s: acceptable now that it's a fallback rather than what every
// order waits on, and it means 10x fewer no-op queries against Postgres in the overwhelmingly
// common case where the event-driven path already handled everything.
@Component
public class OutboxPoller {

    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisher outboxPublisher;

    public OutboxPoller(OutboxEventRepository outboxEventRepository, OutboxPublisher outboxPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(fixedDelay = 30000)
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE, Sort.by("createdAt").ascending())
        );

        for (OutboxEvent event : batch) {
            outboxPublisher.publish(event);
        }
    }
}
