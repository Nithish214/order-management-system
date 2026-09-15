-- Postgres equivalent of db/migration/V7__add_more_products.sql.
INSERT INTO product (id, sku, name, unit_price) VALUES (4, 'MONITOR-01', '27-inch Monitor', 250.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (5, 'WEBCAM-01', 'HD Webcam', 45.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (6, 'HEADSET-01', 'Wireless Headset', 60.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (7, 'USBHUB-01', 'USB-C Hub', 30.00);

-- Same one-liner fix as V3, needed again for the same reason: explicit IDs above don't
-- advance product_seq on their own.
SELECT setval('product_seq', (SELECT COALESCE(MAX(id), 1) FROM product));
