-- app_user and product were both seeded with explicit IDs (V1), which never advances a
-- sequence (only NEXTVAL does). Both sequences have sat at their starting value ever
-- since -- harmless until something actually asks one for a genuinely new ID, at which
-- point it collides with existing seed data. app_user_seq just hit this for real, the
-- first time anything auto-created a new app_user row; product_seq has the identical
-- latent bug, just never triggered since nothing creates products at runtime yet.
DECLARE
  v_max_user   NUMBER;
  v_max_product NUMBER;
  v_curr       NUMBER;
BEGIN
  SELECT NVL(MAX(id), 0) INTO v_max_user FROM app_user;
  SELECT app_user_seq.NEXTVAL INTO v_curr FROM dual;
  IF v_max_user > v_curr THEN
    EXECUTE IMMEDIATE 'ALTER SEQUENCE app_user_seq INCREMENT BY ' || (v_max_user - v_curr);
    SELECT app_user_seq.NEXTVAL INTO v_curr FROM dual;
    EXECUTE IMMEDIATE 'ALTER SEQUENCE app_user_seq INCREMENT BY 1';
  END IF;

  SELECT NVL(MAX(id), 0) INTO v_max_product FROM product;
  SELECT product_seq.NEXTVAL INTO v_curr FROM dual;
  IF v_max_product > v_curr THEN
    EXECUTE IMMEDIATE 'ALTER SEQUENCE product_seq INCREMENT BY ' || (v_max_product - v_curr);
    SELECT product_seq.NEXTVAL INTO v_curr FROM dual;
    EXECUTE IMMEDIATE 'ALTER SEQUENCE product_seq INCREMENT BY 1';
  END IF;
END;
