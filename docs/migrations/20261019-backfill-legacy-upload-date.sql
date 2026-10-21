-- Run manually ONCE against the intended database, after session_upload_daily exists.
-- Business rule: untracked sessions present at the first run belong to "before
-- 2026-10-19". 2026-10-18 is an assigned bucket, NOT an observed upload date.
-- No business data is copied or modified. Existing upload records are preserved.
BEGIN;

-- Serialize with upload-summary writers while capturing the initial population.
LOCK TABLE session_upload_daily IN SHARE ROW EXCLUSIVE MODE;

-- This persistent baseline freezes the first run's population. Re-running this
-- script must NOT capture newly created/untracked sessions as historical data.
-- Do not drop this table after completion: it is also the provenance record.
CREATE TABLE IF NOT EXISTS session_upload_legacy_baseline AS
SELECT s.id AS session_id,
       DATE '2026-10-18' AS assigned_date,
       CURRENT_TIMESTAMP AS captured_at
FROM sessions s
WHERE NOT EXISTS (
    SELECT 1 FROM session_upload_daily d WHERE d.session_id = s.id
);

CREATE UNIQUE INDEX IF NOT EXISTS ix_session_upload_legacy_baseline_session
    ON session_upload_legacy_baseline (session_id);

INSERT INTO session_upload_daily
    (session_id, upload_date, first_uploaded_at, last_uploaded_at)
SELECT b.session_id, b.assigned_date,
       TIMESTAMPTZ '2026-10-18 23:59:59+08',
       TIMESTAMPTZ '2026-10-18 23:59:59+08'
FROM session_upload_legacy_baseline b
WHERE EXISTS (SELECT 1 FROM sessions s WHERE s.id = b.session_id)
-- The table lock above serializes writers, so this is also safe on openGauss
-- versions without ON CONFLICT. Preserve existing real or legacy activity.
AND NOT EXISTS (
    SELECT 1 FROM session_upload_daily d
    WHERE d.session_id = b.session_id AND d.upload_date = b.assigned_date
);

COMMIT;

-- Review how many sessions were classified. captured_at is the real migration
-- time; the daily table's 2026-10-18 timestamps are synthetic bucket markers.
SELECT assigned_date, COUNT(*) AS legacy_session_count,
       MIN(captured_at) AS baseline_captured_at
FROM session_upload_legacy_baseline
GROUP BY assigned_date;
