-- Optimistic locking column for product_stock. Every UPDATE Hibernate issues against a row
-- mapped with @Version now carries "AND version = ?" in its WHERE clause, and bumps the
-- column by 1 on success. Two concurrent transactions that both read the same row before
-- either commits will have one of them's UPDATE match zero rows -- Hibernate detects that
-- and throws instead of silently letting the second write clobber the first (a lost
-- update). DEFAULT 0 backfills every existing row so this ships without a separate manual
-- data-fix step.
ALTER TABLE product_stock ADD (version NUMBER(19) DEFAULT 0 NOT NULL);

COMMIT;
