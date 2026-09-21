-- One row per (user, product) a shopper currently has in their cart -- server-side now,
-- replacing what used to live only in the browser's localStorage, specifically so a cart
-- follows an account across devices/browsers rather than staying stuck in the one browser
-- it was built up in. Deliberately stores only user_id/product_id/quantity, never a price
-- or name snapshot: unlike order_item (which snapshots price permanently at purchase
-- time), a cart should always reflect the product's CURRENT price right up until checkout.
CREATE TABLE cart_item (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT pk_cart_item PRIMARY KEY (id),
    CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    -- One row per product per user -- adding the same product again increments this row's
    -- quantity (see CartController#addItem) rather than creating a duplicate.
    CONSTRAINT uq_cart_item_user_product UNIQUE (user_id, product_id)
);

CREATE SEQUENCE cart_item_seq START WITH 1 INCREMENT BY 1;
