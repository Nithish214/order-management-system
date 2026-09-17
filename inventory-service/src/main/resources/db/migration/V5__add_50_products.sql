-- Mirrors order-service's V14__add_50_products.sql -- same product_id values (8-57),
-- same skus, matching that migration row-for-row. No sequence fix needed here (unlike
-- order-service's product table): product_stock.product_id has no @GeneratedValue at
-- all (see ProductStock.java) -- it's always assigned explicitly to mirror Order
-- Service's own id, so there's no sequence to ever fall behind in the first place.
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (8, 'ELEC-08', 30);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (9, 'ELEC-09', 15);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (10, 'ELEC-10', 50);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (11, 'ELEC-11', 20);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (12, 'ELEC-12', 25);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (13, 'ELEC-13', 60);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (14, 'HOME-14', 18);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (15, 'HOME-15', 22);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (16, 'HOME-16', 35);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (17, 'HOME-17', 40);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (18, 'HOME-18', 45);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (19, 'HOME-19', 10);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (20, 'HOME-20', 28);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (21, 'SPRT-21', 35);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (22, 'SPRT-22', 12);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (23, 'SPRT-23', 50);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (24, 'SPRT-24', 15);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (25, 'SPRT-25', 30);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (26, 'SPRT-26', 40);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (27, 'BOOK-27', 60);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (28, 'BOOK-28', 80);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (29, 'BOOK-29', 45);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (30, 'BOOK-30', 35);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (31, 'BOOK-31', 55);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (32, 'BOOK-32', 70);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (33, 'BOOK-33', 50);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (34, 'FASH-34', 25);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (35, 'FASH-35', 15);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (36, 'FASH-36', 20);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (37, 'FASH-37', 30);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (38, 'FASH-38', 45);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (39, 'FASH-39', 35);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (40, 'FASH-40', 28);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (41, 'TOYS-41', 40);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (42, 'TOYS-42', 22);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (43, 'TOYS-43', 18);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (44, 'TOYS-44', 30);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (45, 'TOYS-45', 35);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (46, 'TOYS-46', 60);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (47, 'BEAU-47', 32);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (48, 'BEAU-48', 40);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (49, 'BEAU-49', 15);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (50, 'BEAU-50', 28);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (51, 'BEAU-51', 35);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (52, 'BEAU-52', 50);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (53, 'OFFC-53', 8);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (54, 'OFFC-54', 5);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (55, 'OFFC-55', 25);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (56, 'OFFC-56', 30);
INSERT INTO product_stock (product_id, sku, available_quantity) VALUES (57, 'OFFC-57', 20);
