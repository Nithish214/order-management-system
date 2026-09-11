-- Owned entirely by Inventory Service, in its own Oracle user/schema (inventory_user --
-- see docker/oracle-init). It never reads or writes Order Service's `product` table.

CREATE TABLE product_stock (
    product_id NUMBER(19) NOT NULL,
    sku VARCHAR2(255 CHAR) NOT NULL,
    available_quantity NUMBER(10) NOT NULL,
    CONSTRAINT pk_product_stock PRIMARY KEY (product_id)
);

-- Idempotency ledger: one row per Kafka event this service has already handled, so a
-- redelivered/duplicate message is a no-op instead of double-deducting stock.
CREATE TABLE processed_event (
    event_id VARCHAR2(255 CHAR) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);

-- Mirrors Order Service's seed data (order-service/src/main/resources/db/migration/V1__create_schema.sql)
-- so productIds line up for this learning project. A real system would source both
-- from one shared Product Catalog Service instead of duplicating IDs by hand.
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (1, 'LAPTOP-01', 10);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (2, 'MOUSE-01', 25);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (3, 'KEYBOARD-01', 15);

COMMIT;
