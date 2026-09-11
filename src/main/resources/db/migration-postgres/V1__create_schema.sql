-- Postgres equivalent of db/migration/V1-V3 (Oracle, used for local dev), consolidated into
-- one clean migration since a fresh RDS database has no historical rows to migrate through --
-- see DEPLOYMENT.md for the full list of Oracle -> Postgres syntax differences.
-- No trailing COMMIT: Flyway manages this migration's transaction itself on Postgres.

CREATE TABLE app_user (
    id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE SEQUENCE app_user_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE product (
    id BIGINT NOT NULL,
    sku VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uq_product_sku UNIQUE (sku)
);

CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE SEQUENCE orders_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE order_item (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    line_total NUMERIC(19,4) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE SEQUENCE order_item_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE outbox_event (
    id BIGINT NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE SEQUENCE outbox_event_seq START WITH 1 INCREMENT BY 1;

INSERT INTO app_user (id, email, name) VALUES (1, 'alice@example.com', 'Alice');
INSERT INTO app_user (id, email, name) VALUES (2, 'bob@example.com', 'Bob');

INSERT INTO product (id, sku, name, unit_price) VALUES (1, 'LAPTOP-01', 'Laptop', 1200.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (2, 'MOUSE-01', 'Wireless Mouse', 35.50);
INSERT INTO product (id, sku, name, unit_price) VALUES (3, 'KEYBOARD-01', 'Mechanical Keyboard', 85.00);
