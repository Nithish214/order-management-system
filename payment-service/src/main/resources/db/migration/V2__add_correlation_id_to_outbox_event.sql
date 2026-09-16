-- Same reasoning as Order Service's identical migration -- nullable, stamped once when
-- InventoryReservedListener creates the row (from the incoming inventory.reserved
-- message's own correlation header), read back by OutboxPublisher at send time.
ALTER TABLE outbox_event ADD correlation_id VARCHAR(64) NULL;
