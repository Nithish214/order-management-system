-- Postgres equivalent of db/migration/V11__add_idempotency_key_table.sql -- see that file
-- for why this table exists and why `key` alone being the primary key is load-bearing.
CREATE TABLE idempotency_key (
    key VARCHAR(255) NOT NULL,
    order_id BIGINT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (key),
    CONSTRAINT fk_idempotency_key_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
