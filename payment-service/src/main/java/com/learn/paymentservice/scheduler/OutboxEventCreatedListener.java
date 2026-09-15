package com.learn.paymentservice.scheduler;

import com.learn.paymentservice.entity.OutboxEvent;
import com.learn.paymentservice.event.OutboxEventCreated;
import com.learn.paymentservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// The primary publishing path: InventoryReservedListener raises an OutboxEventCreated
// event the moment it writes an outbox row (still inside its own @Transactional method),
// and this listener does the actual publish -- but only once that transaction has
// genuinely committed. Identical role, and identical AFTER_COMMIT + @Async reasoning, to
// Order Service's OutboxEventCreatedListener -- see its comment for the full explanation
// (the transaction-visibility bug REQUIRES_NEW fixes, and the thread-blocking @Async
// fixes). The only difference here: the thread this would otherwise block is the Kafka
// consumer thread handling inventory.reserved, not an HTTP request thread -- blocking it
// would delay every message behind it in the same partition, not just slow one customer's
// response.
@Component
public class OutboxEventCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventCreatedListener.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisher outboxPublisher;

    public OutboxEventCreatedListener(OutboxEventRepository outboxEventRepository, OutboxPublisher outboxPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Async("outboxPublisherExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventCreated(OutboxEventCreated event) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(event.outboxEventId()).orElse(null);
        if (outboxEvent == null) {
            log.warn("OutboxEventCreated fired for id {} but no such row exists", event.outboxEventId());
            return;
        }
        outboxPublisher.publish(outboxEvent);
    }
}
