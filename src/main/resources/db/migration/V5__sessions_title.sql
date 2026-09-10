-- sessions.title：用户手动标题（与 goal 分离，对齐地端）
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS title TEXT NOT NULL DEFAULT '';
