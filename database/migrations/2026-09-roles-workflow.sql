-- Phase 1 of Doc/Mapping/ROLE_ACCESS_PLAN.md — the roles from Work Flow.xlsx.
-- Safe to re-run: MODIFY is idempotent and the column is added only if missing.
-- Keep this list in step with com.tution.model.Role.

ALTER TABLE users MODIFY role ENUM(
    'ADMIN','STAFF','TEACHER','COUNSELLOR','ACCOUNTANT','HR',
    'OFFICE_ADMIN','ABM','ACADEMIC_COORDINATOR','EDP','ACADEMIC_INCHARGE',
    'ACADEMIC_HEAD','BRANCH_MANAGER','BUSINESS_HEAD'
) NOT NULL DEFAULT 'STAFF';

-- Reporting line (counsellor -> ABM -> branch manager ...). Used by phase 3 to
-- scope an ABM's leads to their team. NULL = reports to nobody / not set.
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'reports_to');
SET @sql := IF(@has = 0,
    'ALTER TABLE users ADD COLUMN reports_to INT NULL AFTER role,
         ADD CONSTRAINT fk_users_reports_to FOREIGN KEY (reports_to) REFERENCES users(user_id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
