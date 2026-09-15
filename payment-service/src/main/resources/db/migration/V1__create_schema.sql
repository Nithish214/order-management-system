-- Postgres only -- this service has no Oracle-era local-dev history to keep in sync
-- (unlike Order/Inventory Service's separate db/migration vs db/migration-postgres split),
-- so ids just use Postgres's own IDENTITY columns directly instead of a manual SEQUENCE.

CREATE TABLE payment (
    payment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- No foreign key to Order Service's orders.id -- this is a genuinely separate database
    -- (paymentdb), and Postgres can't enforce a constraint across two databases at all.
    -- Same reasoning already applied to order_reservation_item.order_id in Inventory Service.
    order_id BIGINT NOT NULL,
    amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Every lookup here is "give me the payment(s) for this order" -- there's no other access
-- pattern this table ever serves.
CREATE INDEX idx_payment_order ON payment(order_id);

-- Idempotency ledger -- identical shape and purpose to Inventory Service's processed_event:
-- one row per Kafka event this service has already handled, so a redelivered
-- inventory.reserved (or payment.failed, for the future Inventory Service consumer) is a
-- no-op instead of double-processing.
CREATE TABLE processed_event (
    event_id VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);

-- Outbox -- identical shape and purpose to Order Service's outbox_event: this service
-- writes here (payment + outbox row, in the same transaction as the idempotency record)
-- instead of calling KafkaTemplate directly from inside the listener, so a crash between
-- "payment decided" and "event actually reached Kafka" can never lose the outcome --
-- the row just sits PENDING until published (event-driven, with the same poller-as-a-
-- safety-net pattern Order Service uses, covered in Step 2).
CREATE TABLE outbox_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
