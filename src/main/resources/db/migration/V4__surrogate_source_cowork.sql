-- sessions: surrogate 身份字段 + 云地来源 + 安装 id；tenant_id 扩长
ALTER TABLE sessions
    ALTER COLUMN tenant_id TYPE VARCHAR(256);

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS cowork_id VARCHAR(128);

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'local';

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS install_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_sessions_cowork_id ON sessions (cowork_id);
CREATE INDEX IF NOT EXISTS ix_sessions_source ON sessions (source);
CREATE INDEX IF NOT EXISTS ix_sessions_install_id ON sessions (install_id);

ALTER TABLE events
    ALTER COLUMN tenant_id TYPE VARCHAR(256);

CREATE TABLE IF NOT EXISTS cowork (
    cowork_id VARCHAR(128) PRIMARY KEY,
    name      VARCHAR(256) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cowork_permission (
    cowork_id VARCHAR(128) PRIMARY KEY REFERENCES cowork (cowork_id),
    llm       TEXT         NOT NULL DEFAULT '[]'
);
