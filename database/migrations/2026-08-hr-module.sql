-- ============================================================
--  HR module: staff attendance, leave and salary
--
--  Until now "attendance" in this system meant a STUDENT in a class. These are
--  employees, which is a different thing with different rules - a student is
--  present or absent, an employee also takes half-days, paid leave and unpaid
--  leave, and the difference decides what they are paid. Separate tables rather
--  than a "type" column on the student ones: nothing about the two ever wants
--  to be queried together, and sharing the table would put staff salary data
--  one forgotten WHERE clause away from a class register.
--
--  All three point at users(user_id) - the staff login IS the employee record.
--  There is no separate employee table because there is no employee the system
--  knows about who does not have a login.
--
--  WHAT PAYING A SALARY DOES TO THE BOOKS
--
--  A payslip marked PAID debits a fund, exactly like an expense, and stores the
--  fund_id and txn_id it created. Salary is the institute's largest outflow;
--  recording it anywhere other than the ledger would make the fund balance a
--  fiction. The payslip therefore cannot be marked paid without naming a fund,
--  and the two writes commit together.
--
--  RUN ONCE.
-- ============================================================

-- ── 1. staff attendance ──────────────────────────────────────
-- One row per employee per day. HALF_DAY counts 0.5 when payroll works out
-- payable days; WEEK_OFF and HOLIDAY are paid and count as full days.
CREATE TABLE IF NOT EXISTS staff_attendance (
    att_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT  NOT NULL,
    att_date   DATE NOT NULL,
    status     ENUM('PRESENT','ABSENT','HALF_DAY','LEAVE','HOLIDAY','WEEK_OFF')
               NOT NULL DEFAULT 'PRESENT',
    in_time    TIME NULL,
    out_time   TIME NULL,
    remarks    VARCHAR(200),
    marked_by  VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- one mark per person per day; re-marking updates rather than duplicates
    UNIQUE KEY uq_staff_att (user_id, att_date),
    INDEX idx_sa_date (att_date),
    CONSTRAINT fk_sa_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 2. leave ─────────────────────────────────────────────────
-- days is stored rather than derived from the dates because a half-day counts
-- 0.5 and a request can straddle a week-off; the number that payroll uses must
-- be the number somebody approved, not one recomputed later from a changed
-- holiday calendar.
CREATE TABLE IF NOT EXISTS staff_leave (
    leave_id      INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT  NOT NULL,
    leave_type    ENUM('CASUAL','SICK','UNPAID','OTHER') NOT NULL DEFAULT 'CASUAL',
    from_date     DATE NOT NULL,
    to_date       DATE NOT NULL,
    days          DECIMAL(4,1) NOT NULL DEFAULT 1.0,
    reason        VARCHAR(255),
    status        ENUM('PENDING','APPROVED','REJECTED','CANCELLED')
                  NOT NULL DEFAULT 'PENDING',
    decision_note VARCHAR(255),
    decided_by    VARCHAR(100),
    decided_at    TIMESTAMP NULL,
    applied_by    VARCHAR(100),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sl_user (user_id, from_date),
    INDEX idx_sl_status (status, from_date),
    CONSTRAINT fk_sl_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 3. salary structure ──────────────────────────────────────
-- History, not a single figure on users: a raise must not silently restate what
-- somebody was paid last year. The current one is the row with the latest
-- effective_from that is not in the future.
CREATE TABLE IF NOT EXISTS staff_salary (
    salary_id      INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT  NOT NULL,
    monthly_ctc    DECIMAL(12,2) NOT NULL,
    effective_from DATE NOT NULL,
    note           VARCHAR(200),
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_salary_from (user_id, effective_from),
    INDEX idx_ss_user (user_id, effective_from),
    CONSTRAINT fk_ss_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 4. payslips ──────────────────────────────────────────────
-- One per employee per month. The day counts are frozen onto the row when the
-- slip is generated: attendance for a closed month can still be corrected, and
-- a payslip that silently recalculated itself afterwards would stop matching
-- the money that actually went out.
CREATE TABLE IF NOT EXISTS salary_payslip (
    payslip_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    period_year  SMALLINT NOT NULL,
    period_month TINYINT  NOT NULL,          -- 1-12
    monthly_ctc  DECIMAL(12,2) NOT NULL,
    month_days   TINYINT  NOT NULL,
    payable_days DECIMAL(4,1) NOT NULL,
    absent_days  DECIMAL(4,1) NOT NULL DEFAULT 0,
    unpaid_days  DECIMAL(4,1) NOT NULL DEFAULT 0,
    gross        DECIMAL(12,2) NOT NULL,
    deductions   DECIMAL(12,2) NOT NULL DEFAULT 0,
    net_pay      DECIMAL(12,2) NOT NULL,
    note         VARCHAR(200),
    status       ENUM('DRAFT','PAID','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    -- set together when the slip is paid; the ledger row this created
    fund_id      INT NULL,
    txn_id       INT NULL,
    paid_on      DATE NULL,
    paid_by      VARCHAR(100),
    created_by   VARCHAR(100),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_payslip (user_id, period_year, period_month),
    INDEX idx_sp_period (period_year, period_month, status),
    CONSTRAINT fk_sp_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_fund FOREIGN KEY (fund_id)
        REFERENCES fund_accounts(fund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Salary needs its own source type so it is distinguishable from an office
-- expense on the fund statement forever after.
ALTER TABLE fund_transactions
  MODIFY COLUMN source_type
    ENUM('TOPUP','EXPENSE','EXAM_FEE','ADJUSTMENT','REVERSAL','SALARY')
    NOT NULL DEFAULT 'TOPUP';
