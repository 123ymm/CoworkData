-- V8: user_profile.user_id = 工号；username = 展示姓名（张三/李四）
-- 旧口径：user_id=surrogate、username=工号 → 迁成 user_id=工号、username=''（姓名待地端回填）

CREATE TABLE IF NOT EXISTS user_profile_v8 (
    user_id   VARCHAR(128) PRIMARY KEY,
    username  VARCHAR(256) NOT NULL DEFAULT ''
);

-- 旧表 username 列存的是工号
INSERT INTO user_profile_v8 (user_id, username)
SELECT DISTINCT TRIM(username), ''
FROM user_profile
WHERE username IS NOT NULL AND TRIM(username) <> ''
ON CONFLICT (user_id) DO NOTHING;

-- 已按新口径写入的 sessions.user_id（工号）也纳入字典
INSERT INTO user_profile_v8 (user_id, username)
SELECT DISTINCT TRIM(user_id), ''
FROM sessions
WHERE user_id IS NOT NULL
  AND TRIM(user_id) <> ''
  AND (surrogate_id IS NULL OR user_id <> surrogate_id)
ON CONFLICT (user_id) DO NOTHING;

DROP TABLE IF EXISTS user_profile;
ALTER TABLE user_profile_v8 RENAME TO user_profile;

CREATE INDEX IF NOT EXISTS ix_user_profile_username ON user_profile (username);
