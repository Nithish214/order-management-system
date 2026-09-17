-- Postgres equivalent of db/migration/V4__add_product_stock_version.sql -- see that file
-- for why this column exists.
ALTER TABLE product_stock ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
