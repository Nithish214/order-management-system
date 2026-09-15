-- Matches order-service/src/main/resources/db/migration/V7__add_more_products.sql --
-- product_stock has no @GeneratedValue (its id mirrors Order Service's product id), so
-- there's no sequence to fix here, unlike that migration.
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (4, 'MONITOR-01', 8);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (5, 'WEBCAM-01', 20);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (6, 'HEADSET-01', 12);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (7, 'USBHUB-01', 25);

COMMIT;
