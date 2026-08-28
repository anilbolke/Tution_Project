-- ============================================================
--  Finance module: exam fees (money in), expense fund, vendors
--  and work orders (money out).
--
--  Decisions taken with the institute (see
--  Module_Details/Finance_Module_Plan.md for the reasoning):
--
--    * Exam fees live in their OWN table, not in `payments`. An exam
--      candidate is a LEAD (exam_candidates.inquiry_id -> inquiries),
--      not a student, and payments.student_id is NOT NULL. Mixing the
--      two would silently inflate every tuition figure in the app -
--      dashboard Collected/Outstanding, the collection register,
--      pending-fee aging and counsellor revenue targets.
--
--    * Balances are DERIVED, never stored. There is deliberately no
--      `balance` column on fund_accounts: a stored balance drifts the
--      first time two people post at once, and once it has drifted
--      there is no way to recover the correct figure.
--
--    * Nothing is deleted. An expense is voided by a contra entry and
--      both rows stay visible, so "why did the balance change on the
--      14th?" is always answerable.
--
--  COLLATION: every table is pinned to utf8mb4_unicode_ci. The database
--  default is utf8mb4_0900_ai_ci but the core tables (students,
--  inquiries, exam_candidates) are utf8mb4_unicode_ci; leaving it to the
--  default produces "ERROR 1267 Illegal mix of collations" on the first
--  join, exactly as it did on the scholarship-exam tables.
--
--  RUN ONCE. MySQL has no ADD COLUMN IF NOT EXISTS, so the ALTER
--  statements below will error on a second run. The CREATE TABLE
--  statements are guarded and are safe either way.
-- ============================================================

-- ------------------------------------------------------------
--  1. Fund accounts and the ledger behind them
--
--     balance = opening_balance + SUM(CREDIT) - SUM(DEBIT)
--     computed from fund_transactions. Every top-up and every
--     expense is one row here; nothing is ever updated in place.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fund_accounts (
    fund_id         INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(80)   NOT NULL,
    description     VARCHAR(255),
    -- The balance carried in on the day the institute started using this
    -- module. Kept separate from the ledger so it is never mistaken for a
    -- transaction somebody entered.
    opening_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    opening_date    DATE          NULL,
    is_active       TINYINT(1)    NOT NULL DEFAULT 1,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_fund_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fund_transactions (
    txn_id       INT AUTO_INCREMENT PRIMARY KEY,
    fund_id      INT  NOT NULL,
    txn_date     DATE NOT NULL,
    direction    ENUM('CREDIT','DEBIT') NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    -- What put this row here. source_id points at the expense / exam
    -- payment that caused it, so a ledger line can always be traced back
    -- to the document behind it.
    source_type  ENUM('TOPUP','EXPENSE','EXAM_FEE','ADJUSTMENT','REVERSAL')
                 NOT NULL DEFAULT 'TOPUP',
    source_id    INT NULL,
    narration    VARCHAR(255),
    payment_mode ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online') NULL,
    txn_ref      VARCHAR(60),
    created_by   VARCHAR(100),
    created_by_id INT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ft_fund_date (fund_id, txn_date),
    INDEX idx_ft_source (source_type, source_id),
    CONSTRAINT fk_ft_fund FOREIGN KEY (fund_id)
        REFERENCES fund_accounts(fund_id),
    CONSTRAINT fk_ft_user FOREIGN KEY (created_by_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  2. Vendors
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vendors (
    vendor_id      INT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(160) NOT NULL,
    contact_person VARCHAR(120),
    mobile         VARCHAR(15),
    email          VARCHAR(120),
    address        VARCHAR(255),
    gstin          VARCHAR(20),
    pan            VARCHAR(12),
    bank_account   VARCHAR(40),
    bank_ifsc      VARCHAR(15),
    category       VARCHAR(60),                  -- Printing, Stationery, Housekeeping...
    notes          VARCHAR(255),
    is_active      TINYINT(1) NOT NULL DEFAULT 1,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_vendor_name (name),
    INDEX idx_vendor_mobile (mobile),
    INDEX idx_vendor_active (is_active, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  3. Work orders
--
--     `status` is the LIFECYCLE and is stored - somebody decides a work
--     order is cancelled. Payment progress (UNPAID / PARTIALLY_PAID /
--     FULLY_PAID) is DERIVED from the expenses booked against it and is
--     deliberately not a column.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS work_orders (
    work_order_id  INT AUTO_INCREMENT PRIMARY KEY,
    vendor_id      INT NOT NULL,
    wo_no          VARCHAR(30)  NOT NULL,        -- WO-2627-0003
    title          VARCHAR(200) NOT NULL,        -- "OMR sheet printing - HAMSE Sept"
    description    TEXT,
    order_value    DECIMAL(12,2) NOT NULL,
    order_date     DATE NOT NULL,
    expected_date  DATE NULL,
    status         ENUM('DRAFT','ISSUED','IN_PROGRESS','COMPLETED','CANCELLED')
                   NOT NULL DEFAULT 'DRAFT',
    created_by     VARCHAR(100),
    created_by_id  INT NULL,
    approved_by_id INT NULL,
    approved_at    TIMESTAMP NULL,
    remarks        VARCHAR(255),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_wo_no (wo_no),
    INDEX idx_wo_vendor (vendor_id, status),
    INDEX idx_wo_date (order_date),
    CONSTRAINT fk_wo_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id),
    CONSTRAINT fk_wo_creator  FOREIGN KEY (created_by_id)  REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_wo_approver FOREIGN KEY (approved_by_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  4. Expenses
--
--     vendor_id NULL      = general expense, no vendor involved
--     work_order_id NULL  = vendor expense outside any work order
--
--     INTEGRITY RULE THE DATABASE CANNOT EXPRESS: when work_order_id is
--     set, vendor_id must match that work order's vendor. Enforced in
--     ExpenseService - without it a payment can be booked to Vendor A
--     against Vendor B's work order.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS expense_categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80) NOT NULL,
    is_active   TINYINT(1)  NOT NULL DEFAULT 1,
    sort_order  INT         NOT NULL DEFAULT 0,
    UNIQUE KEY uq_expcat_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS expenses (
    expense_id    INT AUTO_INCREMENT PRIMARY KEY,
    fund_id       INT NOT NULL,
    voucher_no    VARCHAR(30) NOT NULL,          -- EXP-2627-0142
    expense_date  DATE NOT NULL,
    category_id   INT NULL,
    vendor_id     INT NULL,
    work_order_id INT NULL,
    amount        DECIMAL(12,2) NOT NULL,
    tax_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
    payment_mode  ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online')
                  NOT NULL DEFAULT 'Cash',
    txn_ref       VARCHAR(60),
    invoice_no    VARCHAR(60),
    bill_path     VARCHAR(255),                  -- uploaded bill / invoice scan
    description   VARCHAR(255),
    -- Voided, never deleted: a ledger you can DELETE from is not a ledger.
    status        ENUM('ACTIVE','VOID') NOT NULL DEFAULT 'ACTIVE',
    voided_by_id  INT NULL,
    voided_at     TIMESTAMP NULL,
    void_reason   VARCHAR(255),
    created_by    VARCHAR(100),
    created_by_id INT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_voucher_no (voucher_no),
    INDEX idx_exp_date (expense_date),
    INDEX idx_exp_vendor (vendor_id),
    INDEX idx_exp_wo (work_order_id, status),
    INDEX idx_exp_fund (fund_id, status),
    CONSTRAINT fk_exp_fund   FOREIGN KEY (fund_id)       REFERENCES fund_accounts(fund_id),
    CONSTRAINT fk_exp_cat    FOREIGN KEY (category_id)   REFERENCES expense_categories(category_id),
    CONSTRAINT fk_exp_vendor FOREIGN KEY (vendor_id)     REFERENCES vendors(vendor_id),
    CONSTRAINT fk_exp_wo     FOREIGN KEY (work_order_id) REFERENCES work_orders(work_order_id),
    CONSTRAINT fk_exp_user   FOREIGN KEY (created_by_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_exp_voider FOREIGN KEY (voided_by_id)  REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  5. Exam fees (money in)
--
--     Paid state is DERIVED, never stored:
--       payable = fee_waived ? 0 : COALESCE(candidate.fee_amount, exam.exam_fee)
--       paid    = SUM(exam_payments.amount WHERE status='ACTIVE')
-- ------------------------------------------------------------
ALTER TABLE exams
    ADD COLUMN exam_fee DECIMAL(10,2) NOT NULL DEFAULT 0;

ALTER TABLE exam_candidates
    ADD COLUMN fee_amount    DECIMAL(10,2) NULL,        -- NULL = use the exam's list price
    ADD COLUMN fee_waived    TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN waiver_reason VARCHAR(160) NULL;

-- One cheque from a school covering many candidates. The exam pipeline is
-- school-driven, so a school paying for 60 candidates at once is the likely
-- case rather than the exception.
CREATE TABLE IF NOT EXISTS school_receipts (
    school_receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    exam_id       INT NOT NULL,
    school_name   VARCHAR(160) NOT NULL,
    receipt_no    VARCHAR(30)  NOT NULL,        -- SCH-2627-0007
    total_amount  DECIMAL(12,2) NOT NULL,
    payment_mode  ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online')
                  NOT NULL DEFAULT 'Cheque',
    payment_date  DATE NOT NULL,
    txn_ref       VARCHAR(60),
    remarks       VARCHAR(255),
    collected_by  VARCHAR(100),
    collected_by_id INT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_school_receipt_no (receipt_no),
    INDEX idx_sr_exam (exam_id, payment_date),
    CONSTRAINT fk_sr_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id),
    CONSTRAINT fk_sr_user FOREIGN KEY (collected_by_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_payments (
    exam_payment_id INT AUTO_INCREMENT PRIMARY KEY,
    candidate_id  INT NOT NULL,
    receipt_no    VARCHAR(30) NOT NULL,          -- EXM-2627-0481
    amount        DECIMAL(10,2) NOT NULL,
    payment_mode  ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online')
                  NOT NULL DEFAULT 'Cash',
    payment_date  DATE NOT NULL,
    txn_ref       VARCHAR(60),
    -- set when this row is one candidate's share of a bulk school payment
    school_receipt_id INT NULL,
    remarks       VARCHAR(255),
    status        ENUM('ACTIVE','VOID') NOT NULL DEFAULT 'ACTIVE',
    voided_by_id  INT NULL,
    voided_at     TIMESTAMP NULL,
    void_reason   VARCHAR(255),
    collected_by  VARCHAR(100),
    collected_by_id INT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_exam_receipt_no (receipt_no),
    INDEX idx_ep_candidate (candidate_id, status),
    INDEX idx_ep_date (payment_date),
    INDEX idx_ep_school (school_receipt_id),
    CONSTRAINT fk_ep_cand   FOREIGN KEY (candidate_id) REFERENCES exam_candidates(candidate_id),
    CONSTRAINT fk_ep_school FOREIGN KEY (school_receipt_id)
        REFERENCES school_receipts(school_receipt_id) ON DELETE SET NULL,
    CONSTRAINT fk_ep_user   FOREIGN KEY (collected_by_id) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_ep_voider FOREIGN KEY (voided_by_id)    REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  6. Seed data
-- ------------------------------------------------------------
INSERT INTO fund_accounts (name, description, opening_balance, opening_date, created_by)
VALUES ('Main Office Fund',
        'Primary operating fund. Topped up by management; all expenses draw it down.',
        0, CURDATE(), 'system')
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO expense_categories (name, sort_order) VALUES
    ('Printing',                10),
    ('Stationery',              20),
    ('Rent',                    30),
    ('Electricity',             40),
    ('Salary',                  50),
    ('Marketing',               60),
    ('Travel',                  70),
    ('Housekeeping',            80),
    ('Repairs & Maintenance',   90),
    ('Internet & Phone',       100),
    ('Refreshments',           110),
    ('Exam Expenses',          120),
    ('Miscellaneous',          130)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order);
