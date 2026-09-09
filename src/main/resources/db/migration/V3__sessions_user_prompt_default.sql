-- sessions.user_prompt：存量库补 DEFAULT，避免 INSERT 省略该列时违反 NOT NULL
ALTER TABLE sessions
    ALTER COLUMN user_prompt SET DEFAULT '';

UPDATE sessions
SET user_prompt = ''
WHERE user_prompt IS NULL;
