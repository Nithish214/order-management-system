-- Nullable: existing outbox_event rows from before this column existed were never part
-- of a traced request. Stamped once, at the same moment the row is created (see
-- OrderCreationService/OrderController), so it survives regardless of whether the row
-- actually gets published moments later (the normal event-driven path) or minutes later
-- by the scheduled poller safety net -- either way, OutboxPublisher reads it straight
-- from this column rather than needing any live thread-local context at publish time.
ALTER TABLE outbox_event ADD correlation_id VARCHAR(64) NULL;
