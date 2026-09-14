-- Postgres equivalent of db/migration/V6__rename_cancelled_to_rejected.sql -- see that
-- file's comment for why this exists.
UPDATE orders SET status = 'REJECTED' WHERE status = 'CANCELLED';
