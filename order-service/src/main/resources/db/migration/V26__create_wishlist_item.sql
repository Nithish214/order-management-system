-- One row per (user, product) a shopper has saved for later. Deliberately shaped like
-- cart_item (see V19), not order_item: only the product reference and when it was saved,
-- never a price/name snapshot -- a wishlist, like a cart, should always reflect the
-- product's CURRENT price and availability, not what it was when saved.
CREATE TABLE wishlist_item (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT pk_wishlist_item PRIMARY KEY (id),
    CONSTRAINT fk_wishlist_item_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_wishlist_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    -- Saving an already-saved product is a no-op (see WishlistController#addItem), not a
    -- second row -- same one-row-per-product-per-user shape as cart_item's own constraint.
    CONSTRAINT uq_wishlist_item_user_product UNIQUE (user_id, product_id)
);

CREATE SEQUENCE wishlist_item_seq START WITH 1 INCREMENT BY 1;

-- Newest-first is the list's natural order (see WishlistItemRepository), so this is the
-- one query pattern worth an index for -- the same reasoning as V24's analytics indexes.
CREATE INDEX idx_wishlist_item_user_created ON wishlist_item (user_id, created_at DESC);
