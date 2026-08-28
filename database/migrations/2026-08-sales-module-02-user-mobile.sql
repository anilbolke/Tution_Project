-- ============================================================
--  Sales Module migration, part 2  —  counsellor mobile number
--
--  Run AFTER 2026-08-sales-module.sql:
--      mysql -u root -p tuition_db < database/migrations/2026-08-sales-module-02-user-mobile.sql
--
--  Why: the lead-assignment alert sends the already-approved `inquiry_staff`
--  WhatsApp template to the counsellor who just received the lead, and the
--  users table had no phone number to send it to. Nullable — an alert is
--  simply skipped for a user with no mobile on file.
-- ============================================================

ALTER TABLE users ADD COLUMN mobile VARCHAR(15) NULL AFTER email;
