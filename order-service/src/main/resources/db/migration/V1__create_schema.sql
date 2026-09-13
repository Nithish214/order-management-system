-- Oracle sequence naming and ID generation are explicit here because Oracle does not auto-increment like MySQL.
-- The application uses SEQUENCE-backed IDs to keep JPA and Oracle happy together.

CREATE TABLE app_user (
    id NUMBER(19) NOT NULL,
    email VARCHAR2(255 CHAR) NOT NULL,
    name VARCHAR2(255 CHAR) NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE SEQUENCE app_user_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE product (
    id NUMBER(19) NOT NULL,
    sku VARCHAR2(255 CHAR) NOT NULL,
    name VARCHAR2(255 CHAR) NOT NULL,
    stock_quantity NUMBER(10) NOT NULL,
    unit_price NUMBER(19,4) NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uq_product_sku UNIQUE (sku)
);

CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    status VARCHAR2(50 CHAR) NOT NULL,
    total_amount NUMBER(19,4) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE SEQUENCE orders_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE order_item (
    id NUMBER(19) NOT NULL,
    order_id NUMBER(19) NOT NULL,
    product_id NUMBER(19) NOT NULL,
    quantity NUMBER(10) NOT NULL,
    unit_price NUMBER(19,4) NOT NULL,
    line_total NUMBER(19,4) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE SEQUENCE order_item_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE outbox_event (
    id NUMBER(19) NOT NULL,
    aggregate_type VARCHAR2(255 CHAR) NOT NULL,
    aggregate_id VARCHAR2(255 CHAR) NOT NULL,
    event_type VARCHAR2(255 CHAR) NOT NULL,
    payload CLOB NOT NULL,
    status VARCHAR2(50 CHAR) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE SEQUENCE outbox_event_seq START WITH 1 INCREMENT BY 1;

INSERT INTO app_user (id, email, name) VALUES (1, 'alice@example.com', 'Alice');
INSERT INTO app_user (id, email, name) VALUES (2, 'bob@example.com', 'Bob');

INSERT INTO product (id, sku, name, stock_quantity, unit_price) VALUES (1, 'LAPTOP-01', 'Laptop', 10, 1200.00);
INSERT INTO product (id, sku, name, stock_quantity, unit_price) VALUES (2, 'MOUSE-01', 'Wireless Mouse', 25, 35.50);
INSERT INTO product (id, sku, name, stock_quantity, unit_price) VALUES (3, 'KEYBOARD-01', 'Mechanical Keyboard', 15, 85.00);

COMMIT;
