-- Flyway V9: Performance indexes for paginated queries
CREATE INDEX IF NOT EXISTS notifications_user_created_at_idx 
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS notifications_user_read_idx 
    ON notifications (user_id, is_read);

CREATE INDEX IF NOT EXISTS audit_logs_created_at_desc_idx 
    ON audit_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS reward_ledger_user_created_at_idx 
    ON reward_ledger (user_id, created_at DESC);
