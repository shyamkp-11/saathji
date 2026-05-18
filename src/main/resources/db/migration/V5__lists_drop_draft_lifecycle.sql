-- Lists no longer have a DRAFT lifecycle: every list is public from the
-- moment it is created. Dropping both columns is the simplest path —
-- existing DRAFTs become visible (acceptable for v1; the editor was the
-- only one who could see them anyway).

ALTER TABLE lists DROP COLUMN status;
ALTER TABLE lists DROP COLUMN published_at;
DROP INDEX idx_lists_status_published ON lists;
