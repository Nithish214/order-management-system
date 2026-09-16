-- Moves from one image_url column per product to many: a product can now have any number
-- of images (capped at 6, enforced in ProductImageUploadService), shown in the order they
-- were uploaded -- the lowest id is simply whichever image was added first, and that IS the
-- cover/thumbnail image the product list and cart show (see ProductImage entity's comment).
-- No separate "isPrimary" flag or display-order column: upload order already gives an
-- unambiguous, always-consistent ordering for free.
CREATE TABLE product_image (
    id NUMBER(19) NOT NULL,
    product_id NUMBER(19) NOT NULL,
    image_url VARCHAR2(500 CHAR) NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE SEQUENCE product_image_seq START WITH 1 INCREMENT BY 1;

-- Carry forward whatever single image a product already had (V9) as that product's first
-- (and, for now, only) image -- nothing already uploaded is lost in this move.
INSERT INTO product_image (id, product_id, image_url)
SELECT product_image_seq.NEXTVAL, id, image_url FROM product WHERE image_url IS NOT NULL;

-- Fully superseded by product_image now that every existing value has been carried over.
ALTER TABLE product DROP COLUMN image_url;

COMMIT;
