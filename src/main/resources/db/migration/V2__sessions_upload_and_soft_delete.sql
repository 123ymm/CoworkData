-- sessions: 用户归属 + 上传水位 + 软删（幂等增量）
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(128);

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS last_upload_index INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS delete_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS ix_sessions_user_id ON sessions (user_id);
CREATE INDEX IF NOT EXISTS ix_sessions_delete_at ON sessions (delete_at);
