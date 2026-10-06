-- Move device images to the upload-metadata endpoint and invalidate legacy reset tokens.
-- The old token format used a fast, unsalted digest and cannot be safely upgraded in place.

DELETE FROM password_reset_tokens;

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS selector VARCHAR(36);

ALTER TABLE password_reset_tokens
    ALTER COLUMN selector SET NOT NULL;

ALTER TABLE password_reset_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS password_reset_tokens_selector_idx
    ON password_reset_tokens(selector);

UPDATE devices d
SET image_url = '/api/uploads/' || u.id::text
FROM uploads u
WHERE d.user_id = u.user_id
  AND u.purpose = 'devices'
  AND d.image_url LIKE '/api/devices/files/%'
  AND right(u.storage_path, length(substring(d.image_url FROM '[^/]+$')))
      = substring(d.image_url FROM '[^/]+$');
