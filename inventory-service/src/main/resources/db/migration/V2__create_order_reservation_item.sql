-- Backs OrderReservationItem: one row per (order, product) this service has actually
-- reserved stock for, so a later cancellation knows exactly how much to credit back.
-- See OrderCreatedListener (writes these) and OrderCancelledListener (reads/deletes them).
CREATE TABLE order_reservation_item (
    id NUMBER(19) NOT NULL,
    order_id NUMBER(19) NOT NULL,
    product_id NUMBER(19) NOT NULL,
    quantity NUMBER(10) NOT NULL,
    CONSTRAINT pk_order_reservation_item PRIMARY KEY (id)
);

CREATE SEQUENCE order_reservation_item_seq START WITH 1 INCREMENT BY 1;

-- Every lookup this table ever does is "give me all rows for this order" (release on
-- cancellation) -- this is the access pattern that actually needs to be fast.
CREATE INDEX idx_order_reservation_item_order_id ON order_reservation_item (order_id);

COMMIT;
