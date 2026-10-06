ALTER TABLE uploads
    ALTER COLUMN storage_path DROP NOT NULL;

CREATE TABLE upload_contents (
    upload_id UUID PRIMARY KEY REFERENCES uploads(id) ON DELETE CASCADE,
    content BYTEA NOT NULL
);
