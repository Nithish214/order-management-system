-- Same root cause as the Oracle equivalent (V5): app_user and product were seeded with
-- explicit IDs, which never advances a sequence. Postgres makes the fix a one-liner.
SELECT setval('app_user_seq', (SELECT COALESCE(MAX(id), 1) FROM app_user));
SELECT setval('product_seq', (SELECT COALESCE(MAX(id), 1) FROM product));
