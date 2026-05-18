-- Lists no longer have a DRAFT lifecycle: every list is public from the
-- moment it is created. Dropping both columns is the simplest path —
-- existing DRAFTs become visible (acceptable for v1; the editor was the
-- only one who could see them anyway).

-- MySQL automatically drops any index that references a dropped column,
-- so the composite `idx_lists_status_published` is gone implicitly.
--
-- Guarded with IF EXISTS because an earlier partial-apply of this file
-- (before the DROP INDEX was removed) may have already dropped the columns
-- on some environments. `IF EXISTS` is supported on MySQL 8.0+.
ALTER TABLE lists DROP COLUMN IF EXISTS status;
ALTER TABLE lists DROP COLUMN IF EXISTS published_at;
