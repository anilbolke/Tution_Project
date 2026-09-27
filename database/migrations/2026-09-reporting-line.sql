-- Phase 3 of Doc/Mapping/ROLE_ACCESS_PLAN.md — reporting line + HR self-service.
-- Needs 2026-09-roles-workflow.sql (users.reports_to) and
-- 2026-09-role-activity-matrix.sql (activity_paths) first. Safe to re-run.

-- Setting who somebody reports to is audited like a role change.
ALTER TABLE user_audit
    MODIFY action ENUM('CREATE','UPDATE','ROLE','PASSWORD','ENABLE','DISABLE','MANAGER') NOT NULL;

-- /my-hr: a person's OWN attendance, leave and payslips. Opens for anyone
-- holding any HR activity; each section inside checks its own.
INSERT IGNORE INTO activity_paths (path, activity_code) VALUES
    ('/my-hr', 'HR_ATTENDANCE'),
    ('/my-hr', 'HR_LEAVE'),
    ('/my-hr', 'HR_SALARY');
