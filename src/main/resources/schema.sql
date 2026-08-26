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
