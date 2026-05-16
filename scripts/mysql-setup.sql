-- One-time MySQL setup for ruhani-backend.
-- Edit the password below, then run as root:
--   mysql -h 10.0.0.83 -u root -p < scripts/mysql-setup.sql
--
-- The Flyway migration owns the schema; this file only creates the
-- database + user.

CREATE DATABASE IF NOT EXISTS ruhani
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Replace 'change-me' with a real password; this same value goes in
-- the MYSQL_PASSWORD env var when running the backend.
CREATE USER IF NOT EXISTS 'ruhani'@'%' IDENTIFIED BY 'change-me';

GRANT ALL PRIVILEGES ON ruhani.* TO 'ruhani'@'%';
FLUSH PRIVILEGES;
