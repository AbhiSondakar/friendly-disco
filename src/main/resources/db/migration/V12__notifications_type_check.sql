-- Flyway V12: CHECK constraint enumerating canonical notification.type values.
-- Prevents silent garbage types; NOT NULL not enforced (NULLs allowed per existing schema).

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type
    CHECK (type IS NULL OR type IN (
        'offer_received',
        'offer_superseded',
        'pickup_accepted',
        'pickup_reoffered',
        'pickup_cancelled',
        'pickup_verified',
        'pickup_completed',
        'pickup_reassigned',
        'pickup_routing_escalated',
        'pickup_routing_delayed',
        'partner_approved',
        'partner_suspended',
        'partner_changed'
    ));
