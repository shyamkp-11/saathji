-- Lists no longer have a DRAFT lifecycle: every list is public from the
-- moment it is created. Dropping both columns is the simplest path —
-- existing DRAFTs become visible (acceptable for v1; the editor was the
-- only one who could see them anyway).
--
-- Wrapped in a conditional via INFORMATION_SCHEMA because an earlier
-- partial-apply of this file may have already dropped the columns on
-- some environments. We need this to work on both fresh databases
-- (where the columns exist) and recovering databases (where they don't).
-- MySQL < 8.0 has no `DROP COLUMN IF EXISTS`, so prepared statements
-- pick the right path at runtime.

SET @db = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'lists' AND COLUMN_NAME = 'status') > 0,
  'ALTER TABLE lists DROP COLUMN status',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'lists' AND COLUMN_NAME = 'published_at') > 0,
  'ALTER TABLE lists DROP COLUMN published_at',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
