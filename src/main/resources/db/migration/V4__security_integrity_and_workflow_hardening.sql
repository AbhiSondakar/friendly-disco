-- V4__security_integrity_and_workflow_hardening.sql
-- Hardening constraints, indexes, tables, and schema cleanliness for EcoLoop.

-- 1. PRECONDITION CHECKS: Fail fast if duplicate data exists
DO $$
BEGIN
    -- Check for ambiguous duplicate normalized emails
    IF EXISTS (
        SELECT lower(trim(email))
        FROM users
        GROUP BY lower(trim(email))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Flyway V4 Precondition Failed: Duplicate normalized lower(email) exists in users table. Resolve manually before migrating.';
    END IF;

    -- Check for duplicate active pickups for the same device
    IF EXISTS (
        SELECT device_id
        FROM pickup_requests
        WHERE device_id IS NOT NULL AND status NOT IN ('completed', 'cancelled')
        GROUP BY device_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Flyway V4 Precondition Failed: Multiple active pickups exist for the same device. Resolve manually before migrating.';
    END IF;

    -- Check for duplicate routing offers for the same (pickup_id, partner_id)
    IF EXISTS (
        SELECT pickup_id, partner_id
        FROM routing_offers
        GROUP BY pickup_id, partner_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Flyway V4 Precondition Failed: Multiple routing offers exist for the same (pickup_id, partner_id). Resolve manually before migrating.';
    END IF;

    -- Check for duplicate reward ledger entries for (user_id, reference_id)
    IF EXISTS (
        SELECT user_id, reference_id
        FROM reward_ledger
        WHERE reference_id IS NOT NULL
        GROUP BY user_id, reference_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Flyway V4 Precondition Failed: Multiple reward ledger entries exist for the same (user_id, reference_id). Resolve manually before migrating.';
    END IF;
END $$;

-- 2. EMAIL NORMALIZATION & UNIQUE INDEX
UPDATE users SET email = lower(trim(email));
CREATE UNIQUE INDEX IF NOT EXISTS users_lower_email_idx ON users (lower(email));

-- 3. REMOVE DENORMALIZED STALE FIELDS
ALTER TABLE users DROP COLUMN IF EXISTS points_balance;
ALTER TABLE partners DROP COLUMN IF EXISTS active_job_count;

-- 4. CONSTRAINTS & WORKFLOW STATUS VALIDATION
ALTER TABLE users ADD CONSTRAINT chk_users_role 
    CHECK (role IN ('HOUSEHOLD', 'PARTNER', 'ADMIN'));

ALTER TABLE devices ADD CONSTRAINT chk_devices_ai_status 
    CHECK (ai_status IN ('pending', 'completed', 'failed', 'manual'));

ALTER TABLE partners ADD CONSTRAINT chk_partners_status 
    CHECK (status IN ('pending', 'approved', 'rejected', 'suspended'));

ALTER TABLE pickup_requests ADD CONSTRAINT chk_pickup_requests_status 
    CHECK (status IN ('pending', 'accepted', 'in_progress', 'verified', 'completed', 'cancelled'));

ALTER TABLE routing_offers ADD CONSTRAINT chk_routing_offers_status 
    CHECK (status IN ('offered', 'accepted', 'rejected', 'superseded', 'cancelled', 'expired'));

ALTER TABLE reward_ledger ADD CONSTRAINT chk_reward_ledger_type 
    CHECK (type IN ('earn', 'redeem', 'bonus', 'adjustment', 'pickup_completion'));

-- 5. CONCURRENCY & INTEGRITY INDEXES
CREATE UNIQUE INDEX IF NOT EXISTS uq_reward_ledger_user_reference 
    ON reward_ledger (user_id, reference_id) 
    WHERE reference_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pickup_device_active 
    ON pickup_requests (device_id) 
    WHERE device_id IS NOT NULL AND status NOT IN ('completed', 'cancelled');

CREATE UNIQUE INDEX IF NOT EXISTS uq_routing_offers_pickup_partner 
    ON routing_offers (pickup_id, partner_id);

-- 6. NEW TABLES AND FIELDS FOR WORKFLOWS, UPLOADS, AND VERIFICATION
CREATE TABLE IF NOT EXISTS uploads (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    purpose VARCHAR(50) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS uploads_user_idx ON uploads(user_id);
CREATE INDEX IF NOT EXISTS uploads_purpose_idx ON uploads(purpose);

ALTER TABLE partners ADD COLUMN IF NOT EXISTS license_upload_id UUID REFERENCES uploads(id);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS password_reset_tokens_user_idx ON password_reset_tokens(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS password_reset_tokens_hash_idx ON password_reset_tokens(token_hash);

ALTER TABLE pickup_requests ADD COLUMN IF NOT EXISTS verified_category VARCHAR(50);
ALTER TABLE pickup_requests ADD COLUMN IF NOT EXISTS verified_condition VARCHAR(50);
ALTER TABLE pickup_requests ADD COLUMN IF NOT EXISTS verification_notes TEXT;
ALTER TABLE pickup_requests ADD COLUMN IF NOT EXISTS verification_evidence_url VARCHAR(1024);
ALTER TABLE pickup_requests ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE pickup_requests ADD COLUMN IF NOT EXISTS verified_by UUID REFERENCES users(id);

ALTER TABLE routing_offers ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION;
ALTER TABLE routing_offers ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
