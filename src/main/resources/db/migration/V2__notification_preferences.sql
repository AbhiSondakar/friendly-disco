-- Notification preferences for users (push/SMS/email topic toggles).
CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    pickup_updates BOOLEAN NOT NULL DEFAULT TRUE,
    points_updates BOOLEAN NOT NULL DEFAULT TRUE,
    offer_alerts BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX notification_preferences_user_idx ON notification_preferences(user_id);
