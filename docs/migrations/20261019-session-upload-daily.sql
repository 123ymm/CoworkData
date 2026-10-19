-- Server-observed upload activity, one row per session per Shanghai calendar day.
-- Deliberately no backfill from client creation timestamps.
CREATE TABLE IF NOT EXISTS session_upload_daily (
    session_id VARCHAR(64) NOT NULL,
    upload_date DATE NOT NULL,
    first_uploaded_at TIMESTAMPTZ NOT NULL,
    last_uploaded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, upload_date)
);
CREATE INDEX IF NOT EXISTS ix_session_upload_daily_date_session
    ON session_upload_daily (upload_date, session_id);
