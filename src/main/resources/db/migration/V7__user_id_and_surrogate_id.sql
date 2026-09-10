-- V7: sessions 双身份 + 全表易漏写 NOT NULL 文本列补 DEFAULT
-- user_id = W3 工号；surrogate_id = substrate JWT sub
-- tenant_id 仍建议 "{surrogate_id}:{cowork_id}"

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS surrogate_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS ix_sessions_surrogate_id ON sessions (surrogate_id);

-- 存量：旧 user_id 实为 surrogate → 迁到 surrogate_id
UPDATE sessions
SET surrogate_id = user_id
WHERE (surrogate_id IS NULL OR TRIM(surrogate_id) = '')
  AND user_id IS NOT NULL
  AND TRIM(user_id) <> '';

-- 能从 user_profile 反查时，把 sessions.user_id 改回工号
UPDATE sessions s
SET user_id = up.username
FROM user_profile up
WHERE s.surrogate_id IS NOT NULL
  AND TRIM(s.surrogate_id) <> ''
  AND s.surrogate_id = up.user_id
  AND up.username IS NOT NULL
  AND TRIM(up.username) <> '';

-- 仍等于 surrogate 的 user_id（未能反查工号）先清空，避免工号列残留 UUID
UPDATE sessions
SET user_id = NULL
WHERE surrogate_id IS NOT NULL
  AND TRIM(surrogate_id) <> ''
  AND user_id = surrogate_id;

-- ── NOT NULL 文本列 DEFAULT（显式 INSERT null 仍需应用层 nz；省略列时靠 DEFAULT）──
ALTER TABLE sessions ALTER COLUMN user_prompt SET DEFAULT '';
ALTER TABLE sessions ALTER COLUMN status SET DEFAULT 'RUNNING';
ALTER TABLE sessions ALTER COLUMN title SET DEFAULT '';
ALTER TABLE sessions ALTER COLUMN goal SET DEFAULT '';
ALTER TABLE sessions ALTER COLUMN config_json SET DEFAULT '{}';
ALTER TABLE sessions ALTER COLUMN source SET DEFAULT 'local';
ALTER TABLE sessions ALTER COLUMN tenant_id SET DEFAULT 'default';

ALTER TABLE tasks ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE tasks ALTER COLUMN title SET DEFAULT '';
ALTER TABLE tasks ALTER COLUMN description SET DEFAULT '';
ALTER TABLE tasks ALTER COLUMN outputs_json SET DEFAULT 'null';

ALTER TABLE events ALTER COLUMN tenant_id SET DEFAULT 'default';
ALTER TABLE events ALTER COLUMN type SET DEFAULT '';
ALTER TABLE events ALTER COLUMN payload_json SET DEFAULT '{}';
ALTER TABLE events ALTER COLUMN metadata_json SET DEFAULT '{}';

ALTER TABLE memory_events ALTER COLUMN layer SET DEFAULT 'task';
ALTER TABLE memory_events ALTER COLUMN type SET DEFAULT '';
ALTER TABLE memory_events ALTER COLUMN content SET DEFAULT '';
ALTER TABLE memory_events ALTER COLUMN metadata_json SET DEFAULT '{}';

ALTER TABLE memory_subscriptions ALTER COLUMN topic SET DEFAULT '';
ALTER TABLE memory_subscriptions ALTER COLUMN intent SET DEFAULT '';
ALTER TABLE memory_subscriptions ALTER COLUMN task_id SET DEFAULT '';

ALTER TABLE session_sse_events ALTER COLUMN event_json SET DEFAULT '';

ALTER TABLE snapshots ALTER COLUMN last_event_id SET DEFAULT '';
ALTER TABLE snapshots ALTER COLUMN state_blob_json SET DEFAULT '{}';
ALTER TABLE snapshots ALTER COLUMN snapshot_reason SET DEFAULT '';

ALTER TABLE agent_templates ALTER COLUMN name SET DEFAULT '';
ALTER TABLE agent_templates ALTER COLUMN version SET DEFAULT '';
ALTER TABLE agent_templates ALTER COLUMN template_dir SET DEFAULT '';
ALTER TABLE agent_templates ALTER COLUMN description SET DEFAULT '';

ALTER TABLE user_profile ALTER COLUMN username SET DEFAULT '';
ALTER TABLE cowork ALTER COLUMN name SET DEFAULT '';
ALTER TABLE cowork_permission ALTER COLUMN llm SET DEFAULT '[]';
