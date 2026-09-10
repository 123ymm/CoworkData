-- tasks：存量库补 DEFAULT，避免 MyBatis 省略/写入 null 时撞 NOT NULL
-- （保留 NOT NULL；有 DEFAULT 后省略列即可插入成功）
ALTER TABLE tasks ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE tasks ALTER COLUMN title SET DEFAULT '';
ALTER TABLE tasks ALTER COLUMN description SET DEFAULT '';
ALTER TABLE tasks ALTER COLUMN is_daemon SET DEFAULT FALSE;
ALTER TABLE tasks ALTER COLUMN outputs_json SET DEFAULT 'null';

UPDATE tasks SET title = '' WHERE title IS NULL;
UPDATE tasks SET description = '' WHERE description IS NULL;
UPDATE tasks SET status = 'PENDING' WHERE status IS NULL;
UPDATE tasks SET outputs_json = 'null' WHERE outputs_json IS NULL;
UPDATE tasks SET is_daemon = FALSE WHERE is_daemon IS NULL;
