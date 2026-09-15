package com.learn.paymentservice.scheduler;

import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.entity.OutboxStatus;
import com.learn.paymentservice.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Safety net, not the primary publishing path -- identical role to Order Service's
// OutboxPoller. Built in from the start here (rather than starting with a 3-second poll
// and only later replacing it), since the event-driven pattern is already proven and
// there's no reason to reintroduce the bug it fixed just to relive that history.
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
