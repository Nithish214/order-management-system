-- One review per (product, user) -- the unique constraint below is what makes a second
-- POST from the same buyer for the same product a clean, expected conflict rather than a
-- silent duplicate review, the same way idempotency_key's own primary key already forces
-- "the second attempt gets told about the first" instead of just succeeding twice.
CREATE TABLE review (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_review PRIMARY KEY (id),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT uq_review_product_user UNIQUE (product_id, user_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE SEQUENCE review_seq START WITH 1 INCREMENT BY 1;

-- Every lookup this feature actually does is "reviews for product X, newest first" -- this
-- is the one index that lookup needs. product_id alone (not product_id+created_at) is
-- enough: Postgres can sort the small per-product result set after the index narrows it
-- down, no composite index needed for what's realistically a handful to a few dozen rows
-- per product.
CREATE INDEX idx_review_product ON review(product_id);
