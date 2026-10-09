ALTER TABLE partners ADD COLUMN warehouse_id VARCHAR(100);

ALTER TABLE pickup_requests ADD COLUMN assigned_at TIMESTAMP;
ALTER TABLE pickup_requests ADD COLUMN in_transit_at TIMESTAMP;
ALTER TABLE pickup_requests ADD COLUMN collected_at TIMESTAMP;
ALTER TABLE pickup_requests ADD COLUMN delivered_at TIMESTAMP;
