-- Postgres equivalent of db/migration/V2__create_order_reservation_item.sql -- see that
-- file's comment for what this table is for.
CREATE TABLE order_reservation_item (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT pk_order_reservation_item PRIMARY KEY (id)
);

CREATE SEQUENCE order_reservation_item_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_order_reservation_item_order_id ON order_reservation_item (order_id);
