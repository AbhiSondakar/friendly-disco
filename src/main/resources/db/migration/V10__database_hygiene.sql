-- V10__database_hygiene.sql
-- Database hygiene: supporting indexes, foreign key ON DELETE policies,
-- soft delete column, and partial unique constraints.

-- 1. PRECONDITION & DATA CLEANUP (with counts and abort threshold)
DO $$
DECLARE
    orphaned_count INTEGER;
    duplicate_token_count INTEGER;
    phone_duplicate_count INTEGER;
    abort_threshold CONSTANT INTEGER := 10000;
BEGIN
    -- Check for orphaned actor_id in audit_logs before FK creation
    SELECT count(*) INTO orphaned_count
    FROM audit_logs
    WHERE actor_id IS NOT NULL AND actor_id NOT IN (SELECT id FROM users);

    IF orphaned_count > abort_threshold THEN
        RAISE EXCEPTION 'Flyway V10 Precondition Aborted: % orphaned audit_logs.actor_id rows exceed threshold of %', 
            orphaned_count, abort_threshold;
    END IF;

    IF orphaned_count > 0 THEN
        UPDATE audit_logs 
        SET actor_id = NULL 
        WHERE actor_id IS NOT NULL AND actor_id NOT IN (SELECT id FROM users);
        RAISE WARNING 'Flyway V10 Precondition: Nullified % orphaned actor_id entries in audit_logs', orphaned_count;
    ELSE
        RAISE WARNING 'Flyway V10 Precondition: No orphaned actor_id entries found in audit_logs';
    END IF;

    -- Check for duplicate push tokens across users
    -- Resolution policy: newest created_at wins. Older duplicates are deleted.
    SELECT count(*) INTO duplicate_token_count
    FROM push_tokens p1
    WHERE EXISTS (
        SELECT 1 FROM push_tokens p2
        WHERE p1.token = p2.token
          AND (p1.created_at < p2.created_at OR (p1.created_at = p2.created_at AND p1.user_id < p2.user_id))
    );

    IF duplicate_token_count > abort_threshold THEN
        RAISE EXCEPTION 'Flyway V10 Precondition Aborted: % duplicate push_tokens exceed threshold of %', 
            duplicate_token_count, abort_threshold;
    END IF;

    IF duplicate_token_count > 0 THEN
        DELETE FROM push_tokens p1
        WHERE EXISTS (
            SELECT 1 FROM push_tokens p2
            WHERE p1.token = p2.token
              AND (p1.created_at < p2.created_at OR (p1.created_at = p2.created_at AND p1.user_id < p2.user_id))
        );
        RAISE WARNING 'Flyway V10 Precondition: Removed % duplicate push_tokens (newest created_at retained)', duplicate_token_count;
    ELSE
        RAISE WARNING 'Flyway V10 Precondition: No duplicate push_tokens found';
    END IF;

    -- Fail fast if duplicate non-null phone numbers exist in users
    SELECT count(*) INTO phone_duplicate_count
    FROM (
        SELECT phone
        WHERE phone IS NOT NULL AND trim(phone) <> ''
        GROUP BY phone
        HAVING count(*) > 1
    ) sub;

    IF phone_duplicate_count > 0 THEN
        RAISE EXCEPTION 'Flyway V10 Precondition Failed: Duplicate phone numbers exist in users table (% duplicate groups). Resolve manually before migrating.', phone_duplicate_count;
    ELSE
        RAISE WARNING 'Flyway V10 Precondition: Phone uniqueness verified successfully';
    END IF;
END $$;

-- 2. SUPPORTING QUERY INDEXES
-- Index for routing offers expiration sweep and status checks
CREATE INDEX IF NOT EXISTS idx_routing_offers_status_expires_at 
    ON routing_offers (status, expires_at);

-- Index for partner active job lookups and status filtering
CREATE INDEX IF NOT EXISTS idx_pickup_requests_partner_status 
    ON pickup_requests (partner_id, status);

-- Indexes for password reset token sweeps
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at 
    ON password_reset_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_used_at 
    ON password_reset_tokens (used_at) 
    WHERE used_at IS NOT NULL;

-- 3. SOFT DELETION COLUMN
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

-- 4. PARTIAL UNIQUE INDEXES
-- Enforce uniqueness of user phone numbers where non-null, permitting multiple null values
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone 
    ON users (phone) 
    WHERE phone IS NOT NULL;

-- Enforce uniqueness of push notification tokens across all users
CREATE UNIQUE INDEX IF NOT EXISTS uq_push_tokens_token 
    ON push_tokens (token);

-- 5. FOREIGN KEY ON DELETE POLICIES (Wrapped in Postgres DO $$ IF NOT EXISTS ... END IF; $$ blocks)
-- audit_logs.actor_id -> SET NULL (preserves security audit history on user deletion)
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_actor_id_fkey;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_audit_logs_actor') THEN
        ALTER TABLE audit_logs 
            ADD CONSTRAINT fk_audit_logs_actor 
            FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

-- uploads.user_id -> CASCADE (uploads cascade when user account is removed)
ALTER TABLE uploads DROP CONSTRAINT IF EXISTS uploads_user_id_fkey;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_uploads_user') THEN
        ALTER TABLE uploads 
            ADD CONSTRAINT fk_uploads_user 
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

-- upload_contents.upload_id -> CASCADE
ALTER TABLE upload_contents DROP CONSTRAINT IF EXISTS upload_contents_upload_id_fkey;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_upload_contents_upload') THEN
        ALTER TABLE upload_contents 
            ADD CONSTRAINT fk_upload_contents_upload 
            FOREIGN KEY (upload_id) REFERENCES uploads(id) ON DELETE CASCADE;
    END IF;
END $$;

-- reward_ledger.user_id -> RESTRICT (prevents deleting user with existing points ledger entries)
ALTER TABLE reward_ledger DROP CONSTRAINT IF EXISTS reward_ledger_user_id_fkey;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reward_ledger_user') THEN
        ALTER TABLE reward_ledger 
            ADD CONSTRAINT fk_reward_ledger_user 
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- redemptions.user_id -> RESTRICT (prevents deleting user with existing redemption records)
ALTER TABLE redemptions DROP CONSTRAINT IF EXISTS redemptions_user_id_fkey;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_redemptions_user') THEN
        ALTER TABLE redemptions 
            ADD CONSTRAINT fk_redemptions_user 
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- pickup_requests.user_id -> RESTRICT (prevents deleting user with logistics jobs history)
ALTER TABLE pickup_requests DROP CONSTRAINT IF EXISTS pickup_requests_user_id_fkey;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pickup_requests_user') THEN
        ALTER TABLE pickup_requests 
            ADD CONSTRAINT fk_pickup_requests_user 
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
    END IF;
END $$;
