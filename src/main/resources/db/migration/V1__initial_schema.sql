-- Initial schema for Ruhani backend.
-- Charset: utf8mb4 across the board to handle Devanagari + Gujarati text.

CREATE TABLE users (
  id         VARCHAR(36)  NOT NULL,
  email      VARCHAR(255) NOT NULL,
  handle     VARCHAR(64),
  bio        VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uniq_users_email  (email),
  UNIQUE KEY uniq_users_handle (handle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE posts (
  id            VARCHAR(36)  NOT NULL,
  author_id     VARCHAR(36)  NOT NULL,
  poet_id       VARCHAR(36),
  language_code VARCHAR(16)  NOT NULL,
  form          VARCHAR(32)  NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  published_at  TIMESTAMP(6),
  summary       VARCHAR(1000),
  edits_locked  BOOLEAN      NOT NULL DEFAULT FALSE,
  deleted_at    TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
  CONSTRAINT fk_posts_poet   FOREIGN KEY (poet_id)   REFERENCES users (id),
  KEY idx_posts_status_created (status, created_at),
  KEY idx_posts_author         (author_id),
  KEY idx_posts_deleted        (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_tags (
  post_id VARCHAR(36) NOT NULL,
  tag     VARCHAR(64) NOT NULL,
  PRIMARY KEY (post_id, tag),
  CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_lines (
  id      VARCHAR(36) NOT NULL,
  post_id VARCHAR(36) NOT NULL,
  ordinal INT         NOT NULL,
  text    TEXT        NOT NULL,
  summary VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT fk_lines_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
  KEY idx_lines_post_ordinal (post_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE line_transliterations (
  line_id VARCHAR(36) NOT NULL,
  script  VARCHAR(16) NOT NULL,
  text    TEXT        NOT NULL,
  PRIMARY KEY (line_id, script),
  CONSTRAINT fk_translit_line FOREIGN KEY (line_id) REFERENCES post_lines (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE word_entries (
  id              VARCHAR(36)  NOT NULL,
  normalized_form VARCHAR(255) NOT NULL,
  language_code   VARCHAR(16)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_word_lang (normalized_form, language_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_tokens (
  id            VARCHAR(36)  NOT NULL,
  line_id       VARCHAR(36)  NOT NULL,
  ordinal       INT          NOT NULL,
  text          VARCHAR(255) NOT NULL,
  word_entry_id VARCHAR(36)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_tokens_line  FOREIGN KEY (line_id)       REFERENCES post_lines   (id) ON DELETE CASCADE,
  CONSTRAINT fk_tokens_entry FOREIGN KEY (word_entry_id) REFERENCES word_entries (id),
  KEY idx_tokens_line_ordinal (line_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE word_meanings (
  id            VARCHAR(36)   NOT NULL,
  word_entry_id VARCHAR(36)   NOT NULL,
  text          VARCHAR(1000) NOT NULL,
  author_id     VARCHAR(36)   NOT NULL,
  created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  upvote_count  INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_meanings_entry  FOREIGN KEY (word_entry_id) REFERENCES word_entries (id) ON DELETE CASCADE,
  CONSTRAINT fk_meanings_author FOREIGN KEY (author_id)     REFERENCES users        (id),
  KEY idx_meanings_entry_count (word_entry_id, upvote_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meaning_upvotes (
  meaning_id VARCHAR(36) NOT NULL,
  user_id    VARCHAR(36) NOT NULL,
  PRIMARY KEY (meaning_id, user_id),
  CONSTRAINT fk_upvote_meaning FOREIGN KEY (meaning_id) REFERENCES word_meanings (id) ON DELETE CASCADE,
  CONSTRAINT fk_upvote_user    FOREIGN KEY (user_id)    REFERENCES users         (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bookmarks (
  user_id    VARCHAR(36)  NOT NULL,
  post_id    VARCHAR(36)  NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, post_id),
  CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_bookmarks_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
  KEY idx_bookmarks_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
  token      VARCHAR(64) NOT NULL,
  user_id    VARCHAR(36) NOT NULL,
  status     VARCHAR(16) NOT NULL DEFAULT 'VALID',   -- VALID | ROTATED | REVOKED
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (token),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  KEY idx_refresh_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
