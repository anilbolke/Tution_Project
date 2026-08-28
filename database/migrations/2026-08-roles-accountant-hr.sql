-- ============================================================
--  Two new staff roles: ACCOUNTANT and HR
--
--  users.role is an ENUM, so a new role is a schema change, not just a row.
--  Inserting a user with a value outside the list fails (or, on a permissive
--  sql_mode, silently stores the empty string and locks that person out of
--  everything) - so the column has to be widened before anyone can be given
--  either role.
--
--  ACCOUNTANT  the money roles' counterpart to ADMIN: the fund, expenses,
--              vendors, work orders, the fee register, collection, receipts and
--              exam fees. No sales pipeline, no academics, no targets. This is
--              why /fund and friends stopped being ADMIN-only in AuthFilter and
--              became "finance roles only".
--
--  HR          added to the list here, but HR owns exactly one screen today
--              (the staff directory) because the system has no HR module yet:
--              no employee records, no payroll, no leave. The role exists and
--              is safe rather than useful, and its access is written as an
--              ALLOW-list in AuthFilter for that reason - a deny-list would
--              silently hand HR each new module somebody adds later.
--
--  RUN ONCE.
-- ============================================================

ALTER TABLE users
  MODIFY COLUMN role
    ENUM('ADMIN','STAFF','TEACHER','COUNSELLOR','ACCOUNTANT','HR')
    NOT NULL DEFAULT 'STAFF';
