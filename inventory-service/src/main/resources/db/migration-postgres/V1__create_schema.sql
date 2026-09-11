-- Postgres equivalent of db/migration/V1__create_schema.sql (Oracle, used for local dev).
-- No trailing COMMIT: Flyway manages this migration's transaction itself on Postgres.

CREATE TABLE product_stock (
    product_id BIGINT NOT NULL,
    sku VARCHAR(255) NOT NULL,
    available_quantity INTEGER NOT NULL,
    CONSTRAINT pk_product_stock PRIMARY KEY (product_id)
);

CREATE TABLE processed_event (
    event_id VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);

INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (1, 'LAPTOP-01', 10);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (2, 'MOUSE-01', 25);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (3, 'KEYBOARD-01', 15);
