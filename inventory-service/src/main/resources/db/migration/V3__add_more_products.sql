-- Postgres equivalent of db/migration/V3__add_more_products.sql.
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (4, 'MONITOR-01', 8);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (5, 'WEBCAM-01', 20);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (6, 'HEADSET-01', 12);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (7, 'USBHUB-01', 25);
