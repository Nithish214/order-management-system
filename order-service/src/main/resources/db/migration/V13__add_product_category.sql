-- Nullable at first, deliberately -- adding a NOT NULL column directly to a table that
-- already has rows would fail outright (Postgres has nothing to put in the existing 7
-- rows' new column until the UPDATE below runs). Backfilled as a separate step, then
-- locked down to NOT NULL once every row genuinely has a value, not before.
ALTER TABLE product ADD COLUMN category VARCHAR(50);

-- All 7 original products (Laptop, Mouse, Keyboard, Monitor, Webcam, Headset, USB Hub)
-- are unambiguously Electronics -- there was no category concept at all when they were
-- first seeded (V1/V5), so this is a one-time backfill, not an ongoing pattern.
UPDATE product SET category = 'Electronics' WHERE id IN (1, 2, 3, 4, 5, 6, 7);

ALTER TABLE product ALTER COLUMN category SET NOT NULL;
