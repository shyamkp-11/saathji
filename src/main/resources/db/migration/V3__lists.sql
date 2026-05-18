-- Editor-curated lists of posts.
--
-- A `list` is a public-facing collection of existing posts, curated and
-- ordered by an editor (any user with users.is_moderator = TRUE). Lists
-- have a DRAFT → PUBLISHED lifecycle similar to posts; only PUBLISHED
-- lists appear in the public browse endpoint.

-- Editor role lives as a flag on the user. Toggled out-of-band for v1:
--   UPDATE users SET is_moderator = TRUE WHERE email = 'editor@example.com';
ALTER TABLE users
  ADD COLUMN is_moderator BOOLEAN NOT NULL DEFAULT FALSE AFTER bio;

CREATE TABLE lists (
  id            VARCHAR(36)   NOT NULL,
  slug          VARCHAR(128)  NOT NULL,
  title         VARCHAR(255)  NOT NULL,
  description   VARCHAR(1000),
  status        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT | PUBLISHED
  editor_id     VARCHAR(36)   NOT NULL,
  created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  published_at  TIMESTAMP(6),
  updated_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uniq_lists_slug (slug),
  CONSTRAINT fk_lists_editor FOREIGN KEY (editor_id) REFERENCES users (id),
  KEY idx_lists_status_published (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Composite PK (list_id, post_id) enforces "a post appears at most once
-- per list". Ordering lives in `ordinal` with its own index; reordering
-- is "replace all ordinals" atomically server-side.
CREATE TABLE list_items (
  list_id   VARCHAR(36)  NOT NULL,
  post_id   VARCHAR(36)  NOT NULL,
  ordinal   INT          NOT NULL,
  added_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (list_id, post_id),
  CONSTRAINT fk_list_items_list FOREIGN KEY (list_id) REFERENCES lists (id) ON DELETE CASCADE,
  CONSTRAINT fk_list_items_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
  KEY idx_list_items_list_ordinal (list_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
