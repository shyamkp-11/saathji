-- Post versioning: edits to a published post create a new `posts` row
-- pointing back at the previous version. On publish, the previous version
-- transitions to status 'SUPERSEDED' (alongside the existing DRAFT and
-- PUBLISHED states — no enum constraint to alter, status is VARCHAR(16)).

ALTER TABLE posts
  ADD COLUMN parent_post_id VARCHAR(36) NULL AFTER poet_id;

ALTER TABLE posts
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER parent_post_id;

ALTER TABLE posts
  ADD CONSTRAINT fk_posts_parent FOREIGN KEY (parent_post_id) REFERENCES posts (id);

CREATE INDEX idx_posts_parent ON posts (parent_post_id);
