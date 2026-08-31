CREATE TABLE IF NOT EXISTS sessions (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id         VARCHAR(128),
    user_prompt     TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    goal            TEXT         NOT NULL DEFAULT '',
    root_agent_id   VARCHAR(64),
    llm_provider    VARCHAR(64),
    llm_model       VARCHAR(128),
    token_budget    BIGINT       NOT NULL DEFAULT 200000,
    failure_counter INTEGER      NOT NULL DEFAULT 0,
    config_json         TEXT         NOT NULL DEFAULT '{}',
    workspace           TEXT,
    last_upload_index   INTEGER      NOT NULL DEFAULT 0,
    delete_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_sessions_tenant_id ON sessions (tenant_id);
CREATE INDEX IF NOT EXISTS ix_sessions_user_id ON sessions (user_id);
CREATE INDEX IF NOT EXISTS ix_sessions_status ON sessions (status);
CREATE INDEX IF NOT EXISTS ix_sessions_delete_at ON sessions (delete_at);

CREATE TABLE IF NOT EXISTS tasks (
    id                VARCHAR(64) PRIMARY KEY,
    session_id        VARCHAR(64) NOT NULL REFERENCES sessions (id),
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    title             TEXT         NOT NULL DEFAULT '',
    description       TEXT         NOT NULL DEFAULT '',
    user_prompt       TEXT,
    assigned_agent_id VARCHAR(64),
    creator_agent_id  VARCHAR(64),
    is_daemon         BOOLEAN      NOT NULL DEFAULT FALSE,
    outputs_json      TEXT         NOT NULL DEFAULT 'null',
    error             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_tasks_session_id ON tasks (session_id);
CREATE INDEX IF NOT EXISTS ix_tasks_status ON tasks (status);

CREATE TABLE IF NOT EXISTS events (
    id            VARCHAR(64) PRIMARY KEY,
    run_id        VARCHAR(64),
    session_id    VARCHAR(64) NOT NULL,
    task_id       VARCHAR(64),
    agent_id      VARCHAR(64),
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    type          VARCHAR(128) NOT NULL,
    sequence      INTEGER      NOT NULL,
    payload_json  TEXT         NOT NULL DEFAULT '{}',
    metadata_json TEXT         NOT NULL DEFAULT '{}',
    causation_id  VARCHAR(64),
    timestamp     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_events_run_id ON events (run_id);
CREATE INDEX IF NOT EXISTS ix_events_session_id ON events (session_id);
CREATE INDEX IF NOT EXISTS ix_events_type ON events (type);
CREATE INDEX IF NOT EXISTS ix_events_run_seq ON events (run_id, sequence);
CREATE INDEX IF NOT EXISTS ix_events_session_type ON events (session_id, type);

CREATE TABLE IF NOT EXISTS session_sse_events (
    id         SERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    event_json TEXT        NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_session_sse_events_session_id ON session_sse_events (session_id);

CREATE TABLE IF NOT EXISTS snapshots (
    id                    VARCHAR(64) PRIMARY KEY,
    session_id            VARCHAR(64) NOT NULL REFERENCES sessions (id),
    last_event_id         VARCHAR(64) NOT NULL,
    last_event_sequence   INTEGER     NOT NULL,
    state_blob_json       TEXT        NOT NULL,
    snapshot_reason       VARCHAR(128) NOT NULL DEFAULT '',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_snapshots_session_id ON snapshots (session_id);

-- ── Host 对齐表（memory + agent_templates，见 IpMasterCoworkPy models.py）────────

CREATE TABLE IF NOT EXISTS memory_events (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64),
    agent_id        VARCHAR(64),
    layer           VARCHAR(16)  NOT NULL DEFAULT 'task',
    type            VARCHAR(64)  NOT NULL,
    role            VARCHAR(32),
    topic           VARCHAR(256),
    content         TEXT         NOT NULL,
    seq_no          INTEGER      NOT NULL,
    topic_seq_no    INTEGER      NOT NULL DEFAULT 0,
    is_superseded   BOOLEAN      NOT NULL DEFAULT FALSE,
    metadata_json   TEXT         NOT NULL DEFAULT '{}',
    timestamp       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_memory_events_session_id ON memory_events (session_id);
CREATE INDEX IF NOT EXISTS ix_memory_events_layer ON memory_events (layer);
CREATE INDEX IF NOT EXISTS ix_memory_events_type ON memory_events (type);
CREATE INDEX IF NOT EXISTS ix_memory_events_topic ON memory_events (topic);
CREATE INDEX IF NOT EXISTS ix_memory_task ON memory_events (session_id, layer, task_id, type);
CREATE INDEX IF NOT EXISTS ix_memory_agent ON memory_events (session_id, layer, agent_id, type);

CREATE TABLE IF NOT EXISTS memory_subscriptions (
    id          VARCHAR(64) PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL,
    task_id     VARCHAR(64)  NOT NULL DEFAULT '',
    topic       VARCHAR(256) NOT NULL,
    cursor      INTEGER      NOT NULL DEFAULT 0,
    intent      VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_memory_subscriptions_session_id ON memory_subscriptions (session_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_subscriptions_session_task_topic
    ON memory_subscriptions (session_id, task_id, topic);

CREATE TABLE IF NOT EXISTS agent_templates (
    id            VARCHAR(128) PRIMARY KEY,
    name          VARCHAR(256) NOT NULL,
    version       VARCHAR(32)  NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    template_dir  TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_agent_templates_name ON agent_templates (name);

CREATE TABLE IF NOT EXISTS user_profile (
    user_id   VARCHAR(128) PRIMARY KEY,
    username  VARCHAR(256) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_user_profile_username ON user_profile (username);
