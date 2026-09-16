-- Postgres equivalent of db/migration/V10__add_product_image_table.sql -- see that file for
-- why this table exists and why there's no separate ordering/primary-flag column.
CREATE TABLE product_image (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE SEQUENCE product_image_seq START WITH 1 INCREMENT BY 1;

INSERT INTO product_image (id, product_id, image_url)
SELECT nextval('product_image_seq'), id, image_url FROM product WHERE image_url IS NOT NULL;

ALTER TABLE product DROP COLUMN image_url;
