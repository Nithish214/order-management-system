package com.learn.orderservice.scheduler;

import com.learn.orderservice.entity.OutboxEvent;
import com.learn.orderservice.event.OutboxEventCreated;
import com.learn.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// The primary publishing path now: OrderController raises an OutboxEventCreated event the
// moment it writes an outbox row (still inside its own @Transactional method), and this
// listener does the actual publish -- but only once that transaction has genuinely
// committed, not the instant the event is raised.
//
// phase = AFTER_COMMIT is the whole point here, not the default (synchronous, immediate)
// listener behavior. A plain @EventListener would run inside the SAME transaction, before
// it commits -- meaning if the transaction rolled back afterward for any reason, Kafka
// would already have an event for an order that was never actually saved. That's the exact
// dual-write problem the outbox pattern exists to prevent, and it doesn't matter whether
// the write to Kafka happens directly in the request thread or indirectly via an event
// listener -- what matters is *when*, relative to commit. @TransactionalEventListener
// doesn't invoke this method itself when publishEvent() is called; it registers a callback
// with Spring's transaction synchronization machinery, which then invokes it once the
// transaction resolves, and only for the AFTER_COMMIT phase specifically (as opposed to
// BEFORE_COMMIT, AFTER_ROLLBACK, or AFTER_COMPLETION, the other phases this same annotation
// supports).
//
// One sharp edge worth knowing: if publishEvent() is ever called with NO transaction active
// at all, this listener is silently never invoked by default (fallbackExecution = false is
// the default) -- not queued, not run later, just skipped. That's never actually a risk
// here, since OrderController only ever publishes this event from inside its own
// @Transactional methods, so a transaction is always active at the point of publishing.
//
// Looks the row up by id rather than trusting any data carried on the event itself --
// by the time this runs, the transaction that wrote it has already committed, so a fresh
// read is both simple and guaranteed to see it.
//
// @Async matters here as much as phase = AFTER_COMMIT does. Without it, this method runs
// SYNCHRONOUSLY on the exact same thread that just committed the transaction -- which, for
// a request handled by OrderController, is the same thread that's about to write the HTTP
// response. That means the customer's own POST /orders call would sit blocked on the
// Kafka round-trip completing (confirmed directly: request latency jumped to ~600-1200ms
// once this listener was wired in without @Async) before they ever got their response --
// exactly the coupling between "my request finishing" and "Kafka's own latency/
// availability" that the outbox pattern exists to avoid in the first place. @Async
// dispatches the actual method body to a separate thread pool (see AsyncConfig) the
// moment AFTER_COMMIT fires, letting the original request thread return immediately.
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
            // Shouldn't happen (the row was just committed moments ago), but logging instead
            // of throwing -- there's nothing this listener could do to fix a missing row, and
            // failing loudly here wouldn't undo the (already-committed) order either.
            log.warn("OutboxEventCreated fired for id {} but no such row exists", event.outboxEventId());
            return;
        }
        outboxPublisher.publish(outboxEvent);
    }
}
