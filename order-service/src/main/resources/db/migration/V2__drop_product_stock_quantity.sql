-- Order Service no longer tracks stock at all -- Inventory Service's own product_stock
-- table (a separate schema, inventory_user) is now the single source of truth for it.
-- This column was never updated again after that split, so it's removed here rather
-- than left behind as dead, misleading data.
ALTER TABLE product DROP COLUMN stock_quantity;
