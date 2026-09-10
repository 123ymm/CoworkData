CREATE TABLE IF NOT EXISTS sessions (
    id              VARCHAR(64) PRIMARY KEY,
    -- tenant_id = "{surrogate_id}:{cowork_id}"；缺一侧时可为 default
    tenant_id       VARCHAR(256) NOT NULL DEFAULT 'default',
    -- user_id = W3 工号（username）
    user_id         VARCHAR(128),
    -- surrogate_id = substrate JWT sub
    surrogate_id    VARCHAR(128),
    cowork_id       VARCHAR(128),
    -- local = 地端上传；cloud = 云端原生
    source          VARCHAR(16)  NOT NULL DEFAULT 'local',
    -- Electron 安装 UUID（主机名/安装 id）
    install_id      VARCHAR(64),
    -- DEFAULT ''：省略列时仍能 INSERT；显式 null 须靠 Entity setter 收成 ''
    user_prompt     TEXT         NOT NULL DEFAULT '',
    status          VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    -- 用户手动标题；与事件投影的 goal 分离
    title           TEXT         NOT NULL DEFAULT '',
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
CREATE INDEX IF NOT EXISTS ix_sessions_surrogate_id ON sessions (surrogate_id);
CREATE INDEX IF NOT EXISTS ix_sessions_cowork_id ON sessions (cowork_id);
CREATE INDEX IF NOT EXISTS ix_sessions_source ON sessions (source);
CREATE INDEX IF NOT EXISTS ix_sessions_install_id ON sessions (install_id);
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
    tenant_id     VARCHAR(256) NOT NULL DEFAULT 'default',
    type          VARCHAR(128) NOT NULL DEFAULT '',
    sequence      INTEGER      NOT NULL DEFAULT 0,
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
    session_id VARCHAR(64) NOT NULL DEFAULT '',
    event_json TEXT        NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS ix_session_sse_events_session_id ON session_sse_events (session_id);

CREATE TABLE IF NOT EXISTS snapshots (
    id                    VARCHAR(64) PRIMARY KEY,
    session_id            VARCHAR(64) NOT NULL REFERENCES sessions (id),
    last_event_id         VARCHAR(64) NOT NULL DEFAULT '',
    last_event_sequence   INTEGER     NOT NULL DEFAULT 0,
    state_blob_json       TEXT        NOT NULL DEFAULT '{}',
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
    type            VARCHAR(64)  NOT NULL DEFAULT '',
    role            VARCHAR(32),
    topic           VARCHAR(256),
    content         TEXT         NOT NULL DEFAULT '',
    seq_no          INTEGER      NOT NULL DEFAULT 0,
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
    topic       VARCHAR(256) NOT NULL DEFAULT '',
    cursor      INTEGER      NOT NULL DEFAULT 0,
    intent      VARCHAR(64)  NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_memory_subscriptions_session_id ON memory_subscriptions (session_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_subscriptions_session_task_topic
    ON memory_subscriptions (session_id, task_id, topic);

CREATE TABLE IF NOT EXISTS agent_templates (
    id            VARCHAR(128) PRIMARY KEY,
    name          VARCHAR(256) NOT NULL DEFAULT '',
    version       VARCHAR(32)  NOT NULL DEFAULT '',
    description   TEXT         NOT NULL DEFAULT '',
    template_dir  TEXT         NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_agent_templates_name ON agent_templates (name);

-- user_profile.user_id = W3 工号；username = 展示姓名（张三/李四）
CREATE TABLE IF NOT EXISTS user_profile (
    user_id   VARCHAR(128) PRIMARY KEY,
    username  VARCHAR(256) NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS ix_user_profile_username ON user_profile (username);

CREATE TABLE IF NOT EXISTS cowork (
    cowork_id VARCHAR(128) PRIMARY KEY,
    name      VARCHAR(256) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cowork_permission (
    cowork_id VARCHAR(128) PRIMARY KEY REFERENCES cowork (cowork_id),
    llm       TEXT         NOT NULL DEFAULT '[]'
);
