-- ============================================================
--  Sales Module migration  —  Coaching ERP
--  Covers: Requirement/ERP_Sales_Module_Requirement_1.docx
--          Requirement/Coaching_ERP_Sales_Module_Requirement.docx
--
--  Target: MySQL 8.x, database tuition_db
--  Run:    mysql -u root -p tuition_db < database/migrations/2026-08-sales-module.sql
--
--  RUN ONCE. MySQL has no "ADD COLUMN IF NOT EXISTS" / "CREATE INDEX IF NOT
--  EXISTS", so re-running raises duplicate errors. Take a dump first:
--      mysqldump -u root -p tuition_db > backup_before_sales_module.sql
-- ============================================================

-- No "USE" statement on purpose: the target database comes from the command
-- line, so this same file can be dry-run against a scratch copy first.

-- ------------------------------------------------------------
--  0. Fix existing schema drift
--     payments.wa_sent was added by hand to the live DB (PaymentDAO
--     reads/writes it) but never committed to schema.sql. A fresh DB
--     built from schema.sql throws on every receipt view.
--     Safe to skip if the column already exists.
-- ------------------------------------------------------------
-- ALTER TABLE payments ADD COLUMN wa_sent TINYINT(1) NOT NULL DEFAULT 0;

-- ------------------------------------------------------------
--  1. Lead fields on inquiries
--     The enquiry form in Requirement 1 has ~30 fields across 5
--     sections; the table had 9 columns.
-- ------------------------------------------------------------
ALTER TABLE inquiries
    -- Student details
    ADD COLUMN parent_name         VARCHAR(100)  NULL AFTER mobile,
    ADD COLUMN parent_mobile       VARCHAR(15)   NULL AFTER parent_name,
    ADD COLUMN dob                 DATE          NULL,
    ADD COLUMN gender              ENUM('Male','Female','Other') NULL,
    ADD COLUMN city                VARCHAR(60)   NULL,
    ADD COLUMN address            VARCHAR(255)  NULL,
    -- Academic details
    ADD COLUMN current_class       VARCHAR(40)   NULL,
    ADD COLUMN prev_qualification  VARCHAR(80)   NULL,
    ADD COLUMN school_name         VARCHAR(120)  NULL,
    ADD COLUMN board               VARCHAR(40)   NULL,
    ADD COLUMN percentage          VARCHAR(20)   NULL,
    -- Course interest
    ADD COLUMN course_name         VARCHAR(80)   NULL,
    ADD COLUMN batch_pref          VARCHAR(60)   NULL,
    ADD COLUMN learning_mode       ENUM('Online','Offline','Hybrid') NULL,
    ADD COLUMN branch              VARCHAR(60)   NULL,
    ADD COLUMN expected_join_date  DATE          NULL,
    -- Enquiry / ownership
    ADD COLUMN priority            ENUM('Hot','Warm','Cold') NOT NULL DEFAULT 'Warm',
    ADD COLUMN counsellor_id       INT           NULL,
    ADD COLUMN next_followup_date  DATE          NULL,
    -- Follow-up information
    ADD COLUMN student_requirements VARCHAR(500) NULL,
    ADD COLUMN parent_feedback     VARCHAR(500)  NULL,
    ADD COLUMN counsellor_remarks  VARCHAR(500)  NULL,
    -- Conversion link
    ADD COLUMN converted_student_id INT          NULL,
    ADD COLUMN updated_at          TIMESTAMP     NULL
                                   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 9 lead statuses per the client document.
-- NEW and CONVERTED are carried over; CONTACTED is kept. CLOSED is dropped —
-- it was unreachable in the app (no UI ever set it) and no row uses it.
ALTER TABLE inquiries MODIFY COLUMN status
    ENUM('NEW','CONTACTED','INTERESTED','DEMO_PENDING','DEMO_COMPLETED',
         'FOLLOWUP_REQUIRED','CONVERTED','NOT_INTERESTED','LOST')
    NOT NULL DEFAULT 'NEW';

ALTER TABLE inquiries
    ADD CONSTRAINT fk_inq_counsellor FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_inq_student FOREIGN KEY (converted_student_id)
        REFERENCES students(student_id) ON DELETE SET NULL;

-- Search + duplicate-detection + reminder indexes
CREATE INDEX idx_inq_mobile        ON inquiries (mobile);
CREATE INDEX idx_inq_parent_mobile ON inquiries (parent_mobile);
CREATE INDEX idx_inq_name          ON inquiries (full_name);
CREATE INDEX idx_inq_counsellor    ON inquiries (counsellor_id, status);
CREATE INDEX idx_inq_followup      ON inquiries (next_followup_date, status);

-- ------------------------------------------------------------
--  2. Follow-up history  (Requirement 2 §2)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lead_followups (
    followup_id      INT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id       INT NOT NULL,
    counsellor_id    INT NULL,
    counsellor_name  VARCHAR(120),                     -- denormalised for display
    comm_type        ENUM('Call','WhatsApp','Email','Visit','Demo Discussion')
                     NOT NULL DEFAULT 'Call',
    discussion       TEXT,                             -- discussion summary / call notes
    objection        VARCHAR(500),                     -- objection handling notes
    outcome_status   VARCHAR(30),                      -- status the lead moved to
    next_action_date DATE NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fu_inquiry (inquiry_id),
    INDEX idx_fu_next    (next_action_date),
    CONSTRAINT fk_fu_inq FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(inquiry_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  3. Demo scheduling  (Requirement 2 §2 / sales flow)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lead_demos (
    demo_id       INT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id    INT NOT NULL,
    demo_date     DATE NOT NULL,
    demo_time     VARCHAR(20),
    faculty_name  VARCHAR(120),
    subject       VARCHAR(60),
    mode          ENUM('Online','Offline') NOT NULL DEFAULT 'Offline',
    status        ENUM('SCHEDULED','COMPLETED','NO_SHOW','CANCELLED')
                  NOT NULL DEFAULT 'SCHEDULED',
    feedback      VARCHAR(500),
    rating        TINYINT NULL,                        -- 1..5
    reminder_sent TINYINT(1) NOT NULL DEFAULT 0,
    created_by    VARCHAR(120),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_demo_inquiry (inquiry_id),
    INDEX idx_demo_date    (demo_date, status),
    CONSTRAINT fk_demo_inq FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(inquiry_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  4. Counsellor role + student sales dimension
-- ------------------------------------------------------------
ALTER TABLE users MODIFY COLUMN role
    ENUM('ADMIN','STAFF','TEACHER','COUNSELLOR') NOT NULL DEFAULT 'STAFF';

ALTER TABLE students
    ADD COLUMN counsellor_id INT          NULL,
    ADD COLUMN inquiry_id    INT          NULL,        -- back-link to the source lead
    ADD COLUMN batch_name    VARCHAR(60)  NULL,
    ADD COLUMN branch        VARCHAR(60)  NULL,
    ADD COLUMN id_proof_path VARCHAR(255) NULL,
    ADD COLUMN is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    ADD COLUMN updated_at    TIMESTAMP    NULL
                             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE students
    ADD CONSTRAINT fk_stu_counsellor FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_stu_inquiry FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(inquiry_id) ON DELETE SET NULL;

-- Global search indexes (Requirement 1: search by student / parent mobile)
CREATE INDEX idx_stu_mobile ON students (student_mobile);
CREATE INDEX idx_stu_parent ON students (parent_mobile);
CREATE INDEX idx_stu_name   ON students (full_name);

-- ------------------------------------------------------------
--  5. Fee ledger  (Requirement 2 §4)
--     Replaces the hardcoded FeeCalculator formula with a real
--     per-student record that can carry discount and scholarship.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_fees (
    fee_id           INT AUTO_INCREMENT PRIMARY KEY,
    student_id       INT NOT NULL UNIQUE,
    course_fee       DECIMAL(10,2) NOT NULL DEFAULT 0,
    registration_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    material_fee     DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount         DECIMAL(10,2) NOT NULL DEFAULT 0,
    scholarship      DECIMAL(10,2) NOT NULL DEFAULT 0,
    net_payable      DECIMAL(10,2) NOT NULL DEFAULT 0,
    plan             ENUM('FULL','INSTALLMENT','EMI') NOT NULL DEFAULT 'FULL',
    approved_by      VARCHAR(120),                     -- who authorised the concession
    remarks          VARCHAR(255),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NULL
                     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sf_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fee_installments (
    installment_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id     INT NOT NULL,
    seq            INT NOT NULL,
    label          VARCHAR(60),                        -- "Instalment 2 of 4"
    amount         DECIMAL(10,2) NOT NULL,
    due_date       DATE NOT NULL,
    paid_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    paid_date      DATE NULL,
    status         ENUM('PENDING','PARTIAL','PAID','OVERDUE')
                   NOT NULL DEFAULT 'PENDING',
    reminder_sent  TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_student_seq (student_id, seq),
    INDEX idx_inst_due (due_date, status),
    CONSTRAINT fk_fi_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Link payments to an installment + capture a real collector id and txn reference.
-- collected_by (name string) is kept so existing rows keep displaying.
ALTER TABLE payments
    ADD COLUMN installment_id  INT         NULL,
    ADD COLUMN collected_by_id INT         NULL,
    ADD COLUMN txn_ref         VARCHAR(60) NULL;       -- cheque no / UPI ref / bank ref

ALTER TABLE payments
    ADD CONSTRAINT fk_pay_inst FOREIGN KEY (installment_id)
        REFERENCES fee_installments(installment_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_pay_user FOREIGN KEY (collected_by_id)
        REFERENCES users(user_id) ON DELETE SET NULL;

CREATE INDEX idx_pay_date ON payments (payment_date);

-- ------------------------------------------------------------
--  6. Masters — replace the hardcoded <option> lists in
--     inquiry.jsp / admission.jsp
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lead_sources (
    source_id  INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    is_active  TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS courses (
    course_id       INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(80) NOT NULL UNIQUE,
    duration_months INT NULL,
    is_active       TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS batches (
    batch_id     INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(60) NOT NULL,
    course_id    INT NULL,
    start_date   DATE NULL,
    timing       VARCHAR(40),
    faculty_name VARCHAR(120),
    branch       VARCHAR(60),
    is_active    TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_batch_course (course_id),
    CONSTRAINT fk_batch_course FOREIGN KEY (course_id)
        REFERENCES courses(course_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The 8 lead sources from the client document, plus the 5 already hardcoded in
-- inquiry.jsp so existing free-text values in inquiries.source still resolve.
INSERT INTO lead_sources (name, sort_order) VALUES
    ('Website Enquiry',        10),
    ('Walk-in',                20),
    ('Phone Call',             30),
    ('WhatsApp Enquiry',       40),
    ('Social Media',           50),
    ('Advertisement Campaign', 60),
    ('Student Reference',      70),
    ('Counsellor Entry',       80),
    ('Friend / Referral',      90),   -- legacy value from inquiry.jsp
    ('Newspaper / Pamphlet',  100),   -- legacy value from inquiry.jsp
    ('Other',                 110)    -- legacy value from inquiry.jsp
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order);

-- Courses currently hardcoded in inquiry.jsp / admission.jsp
INSERT INTO courses (name, duration_months) VALUES
    ('11th - Science (PCB)', 12),
    ('11th - Science (PCM)', 12),
    ('12th - Science (PCB)', 12),
    ('12th - Science (PCM)', 12),
    ('NEET Repeater',        12)
ON DUPLICATE KEY UPDATE duration_months = VALUES(duration_months);

-- ------------------------------------------------------------
--  7. Reminder log  (Requirement 2 §6 — idempotent auto-send)
--     lead_demos.reminder_sent / fee_installments.reminder_sent cover
--     those two; follow-ups have no own row to flag, so they dedupe here.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reminder_log (
    log_id      INT AUTO_INCREMENT PRIMARY KEY,
    kind        ENUM('FOLLOWUP','DEMO','FEE_DUE') NOT NULL,
    ref_id      INT NOT NULL,                          -- inquiry_id / demo_id / installment_id
    due_date    DATE NOT NULL,
    channel     VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    sent_ok     TINYINT(1) NOT NULL DEFAULT 0,
    message_id  VARCHAR(80),
    detail      VARCHAR(255),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_reminder (kind, ref_id, due_date, channel),
    INDEX idx_rem_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  8. Backfill student_fees from the current FeeCalculator formula
--     (REG_FEE 500 + MATERIAL_FEE 800 + per_month x 12).
--
--     LEFT JOIN on purpose: students with no matching fee_slab get a
--     zero row, which is exactly how StatsDAO counts them today. This
--     keeps Total Billed / Collected / Outstanding identical before and
--     after the migration.
-- ------------------------------------------------------------
INSERT INTO student_fees
    (student_id, course_fee, registration_fee, material_fee,
     discount, scholarship, net_payable, plan, remarks)
SELECT s.student_id,
       COALESCE(fs.per_month, 0) * 12                         AS course_fee,
       CASE WHEN fs.slab_key IS NULL THEN 0 ELSE 500 END      AS registration_fee,
       CASE WHEN fs.slab_key IS NULL THEN 0 ELSE 800 END      AS material_fee,
       0, 0,
       CASE WHEN fs.slab_key IS NULL THEN 0
            ELSE 1300 + fs.per_month * 12 END                 AS net_payable,
       'FULL',
       'Backfilled from fee_slabs during sales-module migration'
FROM students s
LEFT JOIN fee_slabs fs ON fs.slab_key = s.fee_slab
ON DUPLICATE KEY UPDATE student_id = student_fees.student_id;

-- ------------------------------------------------------------
--  9. Post-migration verification — run these and compare against
--     the values recorded before the migration.
-- ------------------------------------------------------------
-- Every student must have exactly one ledger row:
--   SELECT (SELECT COUNT(*) FROM students) AS students,
--          (SELECT COUNT(*) FROM student_fees) AS fee_rows;
--
-- Total Billed must be unchanged (compare with the dashboard tile):
--   SELECT SUM(net_payable) AS billed_new FROM student_fees;
--   SELECT COUNT(*) * 1300 + SUM(fs.per_month) * 12 AS billed_old
--     FROM students s JOIN fee_slabs fs ON fs.slab_key = s.fee_slab;
--
-- No lead should have lost its status:
--   SELECT status, COUNT(*) FROM inquiries GROUP BY status;
