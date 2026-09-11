-- OrderStatus.CREATED was renamed to PENDING when Order Service stopped tracking stock
-- itself and started waiting on Inventory Service's async verdict instead. That code
-- change alone does nothing for rows already written with the old value -- this migrates
-- them, since a Java enum rename must be paired with a data migration whenever existing
-- rows might already hold the old value.
UPDATE orders SET status = 'PENDING' WHERE status = 'CREATED';

COMMIT;
