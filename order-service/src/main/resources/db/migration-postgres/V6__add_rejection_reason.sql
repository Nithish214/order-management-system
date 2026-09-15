-- Nullable: only ever set for a REJECTED order going forward. Existing REJECTED rows
-- from before this column existed simply have no reason on record -- there was never
-- anywhere that value was kept until now (OrderStatusUpdater only logged it).
ALTER TABLE orders ADD rejection_reason VARCHAR(255) NULL;
