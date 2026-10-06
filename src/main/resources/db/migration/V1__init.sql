CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'HOUSEHOLD',
    name VARCHAR(255) NOT NULL,
    address TEXT,
    points_balance INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX users_email_idx ON users(email);
CREATE INDEX users_role_idx ON users(role);
CREATE INDEX users_created_at_idx ON users(created_at);

CREATE TABLE push_tokens (
    user_id UUID NOT NULL REFERENCES users(id),
    token VARCHAR(512) NOT NULL,
    platform VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, token)
);
CREATE INDEX push_tokens_user_idx ON push_tokens(user_id);
CREATE INDEX push_tokens_created_at_idx ON push_tokens(created_at);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    category VARCHAR(50),
    condition VARCHAR(50),
    image_url VARCHAR(1024),
    ai_category VARCHAR(50),
    ai_confidence DECIMAL(5,4),
    ai_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    ai_provider VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX devices_user_idx ON devices(user_id);
CREATE INDEX devices_created_at_idx ON devices(created_at);

CREATE TABLE predictions (
    id UUID PRIMARY KEY,
    request_id UUID,
    image_url VARCHAR(1024),
    category VARCHAR(50) NOT NULL,
    confidence DECIMAL(5,4),
    provider VARCHAR(100),
    model VARCHAR(255),
    latency_ms INTEGER,
    raw_response TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX predictions_created_at_idx ON predictions(created_at);
CREATE INDEX predictions_request_id_idx ON predictions(request_id);

CREATE TABLE partners (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(id),
    org_name VARCHAR(255) NOT NULL,
    type VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    license_no VARCHAR(255),
    service_areas TEXT,
    capabilities TEXT,
    capacity INTEGER NOT NULL DEFAULT 10,
    rating DECIMAL(3,2) DEFAULT 0.0,
    active_job_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX partners_status_idx ON partners(status);
CREATE INDEX partners_created_at_idx ON partners(created_at);

CREATE TABLE pickup_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    device_id UUID REFERENCES devices(id),
    partner_id UUID REFERENCES partners(id),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    address TEXT,
    scheduled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX pickup_requests_user_idx ON pickup_requests(user_id);
CREATE INDEX pickup_requests_status_idx ON pickup_requests(status);
CREATE INDEX pickup_requests_partner_idx ON pickup_requests(partner_id);
CREATE INDEX pickup_requests_created_at_idx ON pickup_requests(created_at);

CREATE TABLE routing_offers (
    id UUID PRIMARY KEY,
    pickup_id UUID NOT NULL REFERENCES pickup_requests(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    status VARCHAR(20) NOT NULL DEFAULT 'offered',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX routing_offers_pickup_idx ON routing_offers(pickup_id);
CREATE INDEX routing_offers_partner_idx ON routing_offers(partner_id);
CREATE INDEX routing_offers_created_at_idx ON routing_offers(created_at);

CREATE TABLE reward_catalog (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    points_cost INTEGER NOT NULL,
    image_url VARCHAR(1024),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX reward_catalog_active_idx ON reward_catalog(is_active);
CREATE INDEX reward_catalog_created_at_idx ON reward_catalog(created_at);

CREATE TABLE reward_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    points INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX reward_ledger_user_idx ON reward_ledger(user_id);
CREATE INDEX reward_ledger_type_idx ON reward_ledger(type);
CREATE INDEX reward_ledger_created_at_idx ON reward_ledger(created_at);

CREATE TABLE redemptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    catalog_item_id UUID NOT NULL REFERENCES reward_catalog(id),
    points_cost INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX redemptions_user_idx ON redemptions(user_id);
CREATE INDEX redemptions_created_at_idx ON redemptions(created_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    type VARCHAR(50),
    reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX notifications_user_idx ON notifications(user_id);
CREATE INDEX notifications_is_read_idx ON notifications(is_read);
CREATE INDEX notifications_created_at_idx ON notifications(created_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_role VARCHAR(20),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id UUID,
    result VARCHAR(50),
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX audit_logs_actor_idx ON audit_logs(actor_id);
CREATE INDEX audit_logs_action_idx ON audit_logs(action);
CREATE INDEX audit_logs_created_at_idx ON audit_logs(created_at);
