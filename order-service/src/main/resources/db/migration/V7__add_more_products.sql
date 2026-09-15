INSERT INTO product (id, sku, name, unit_price) VALUES (4, 'MONITOR-01', '27-inch Monitor', 250.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (5, 'WEBCAM-01', 'HD Webcam', 45.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (6, 'HEADSET-01', 'Wireless Headset', 60.00);
INSERT INTO product (id, sku, name, unit_price) VALUES (7, 'USBHUB-01', 'USB-C Hub', 30.00);

-- Explicit IDs above never advance product_seq (only NEXTVAL does) -- same fix as
-- V5__fix_sequences_after_seed_data.sql, needed again since this migration reintroduces
-- the identical latent bug for the new rows.
DECLARE
  v_max_product NUMBER;
  v_curr        NUMBER;
BEGIN
  SELECT NVL(MAX(id), 0) INTO v_max_product FROM product;
  SELECT product_seq.NEXTVAL INTO v_curr FROM dual;
  IF v_max_product > v_curr THEN
    EXECUTE IMMEDIATE 'ALTER SEQUENCE product_seq INCREMENT BY ' || (v_max_product - v_curr);
    SELECT product_seq.NEXTVAL INTO v_curr FROM dual;
    EXECUTE IMMEDIATE 'ALTER SEQUENCE product_seq INCREMENT BY 1';
  END IF;
END;

COMMIT;
