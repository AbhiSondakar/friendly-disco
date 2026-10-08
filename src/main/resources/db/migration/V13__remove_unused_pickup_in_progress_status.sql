-- No service transition ever placed a pickup in in_progress.  Normalize the
-- legacy value before tightening the database constraint to the canonical flow.
UPDATE pickup_requests
SET status = 'accepted'
WHERE status = 'in_progress';

ALTER TABLE pickup_requests
    DROP CONSTRAINT IF EXISTS chk_pickup_requests_status;

ALTER TABLE pickup_requests
    ADD CONSTRAINT chk_pickup_requests_status
    CHECK (status IN ('pending', 'accepted', 'verified', 'completed', 'cancelled'));
