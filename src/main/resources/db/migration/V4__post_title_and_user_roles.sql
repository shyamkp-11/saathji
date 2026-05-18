-- Two unrelated changes folded into one migration for v1:
--
-- 1) Optional `title` on posts. Surfaced on PostCard / PostDetail / search.
-- 2) Replace the `is_moderator` boolean on users with a 3-valued `role`
--    (USER | EDITOR | ADMIN). ADMIN is reserved — no extra permissions yet;
--    EDITOR retains the old moderator powers (curate lists).

ALTER TABLE posts
  ADD COLUMN title VARCHAR(255) NULL AFTER form;

ALTER TABLE users
  ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER bio;

UPDATE users SET role = CASE WHEN is_moderator = TRUE THEN 'EDITOR' ELSE 'USER' END;

ALTER TABLE users
  DROP COLUMN is_moderator;
