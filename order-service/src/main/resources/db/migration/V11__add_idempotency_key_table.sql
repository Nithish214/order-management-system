-- One row per client-supplied Idempotency-Key that has successfully resulted in an order --
-- written in the SAME transaction as the order + outbox event it recorded (see
-- OrderCreationService), so either all three rows exist or none of them do. `key` itself
-- being the primary key is what makes two concurrent requests for the same key impossible
-- to both succeed: the database itself rejects the second INSERT, not application logic.
CREATE TABLE idempotency_key (
    key VARCHAR2(255 CHAR) NOT NULL,
    order_id NUMBER(19) NOT NULL,
    response_status NUMBER(3) NOT NULL,
    response_body CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (key),
    CONSTRAINT fk_idempotency_key_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

COMMIT;
