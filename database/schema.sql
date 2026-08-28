-- ============================================================
--  Tuition Management System  -  MySQL Schema
--  Target: MySQL 8.x
--  Run:    mysql -u root -p < database/schema.sql
--
--  This file is the FULL schema for a fresh install. Existing
--  databases are upgraded with the scripts in database/migrations/.
--  Keep the two in sync - a fresh install and a migrated install
--  must end up identical.
-- ============================================================

CREATE DATABASE IF NOT EXISTS tuition_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE tuition_db;

-- ------------------------------------------------------------
--  Users (login accounts: admin / staff / teacher / counsellor)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id      INT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password     CHAR(64)     NOT NULL,                       -- SHA-256 hex
    full_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(120),
    mobile       VARCHAR(15),                                 -- for counsellor WhatsApp alerts
    role         ENUM('ADMIN','STAFF','TEACHER','COUNSELLOR') NOT NULL DEFAULT 'STAFF',
    is_active    TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login   TIMESTAMP    NULL
) ENGINE=InnoDB;

-- Default admin login -> username: admin   password: admin123
-- Password stored as SHA-256 hash so it matches the Java login check.
INSERT INTO users (username, password, full_name, email, role)
VALUES ('admin', SHA2('admin123', 256), 'System Administrator', 'admin@havellsson.com', 'ADMIN')
ON DUPLICATE KEY UPDATE username = username;

-- ------------------------------------------------------------
--  Students (admission records)
--  inquiry_id back-links to the lead this admission came from; its
--  foreign key is added after `inquiries` exists (circular reference).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    student_id     INT AUTO_INCREMENT PRIMARY KEY,
    admission_no   VARCHAR(30)  NOT NULL UNIQUE,
    full_name      VARCHAR(100) NOT NULL,
    dob            DATE,
    gender         ENUM('Male','Female','Other'),
    class_name     VARCHAR(40),
    board          VARCHAR(40),
    prev_school    VARCHAR(120),
    prev_marks     VARCHAR(20),
    student_mobile VARCHAR(15),
    alt_mobile     VARCHAR(15),
    student_email  VARCHAR(120),
    parent_name    VARCHAR(100),
    parent_mobile  VARCHAR(15),
    address        VARCHAR(255),
    photo_path     VARCHAR(255),
    fee_slab       VARCHAR(30),
    plan_code      VARCHAR(40),                      -- fee_plans.code the student was sold
    -- sales dimension
    counsellor_id  INT NULL,
    inquiry_id     INT NULL,
    batch_name     VARCHAR(60),
    branch         VARCHAR(60),
    id_proof_path  VARCHAR(255),
    doc1_path      VARCHAR(255),
    doc2_path      VARCHAR(255),
    doc3_path      VARCHAR(255),
    doc4_path      VARCHAR(255),
    is_active      TINYINT(1) NOT NULL DEFAULT 1,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_stu_mobile (student_mobile),
    INDEX idx_stu_parent (parent_mobile),
    INDEX idx_stu_name   (full_name),
    CONSTRAINT fk_stu_counsellor FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
--  Fee slabs (payment plans)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_slabs (
    slab_id    INT AUTO_INCREMENT PRIMARY KEY,
    slab_key   VARCHAR(30) NOT NULL UNIQUE,
    label      VARCHAR(40) NOT NULL,
    months     INT NOT NULL,
    per_month  INT NOT NULL
) ENGINE=InnoDB;

INSERT INTO fee_slabs (slab_key, label, months, per_month) VALUES
    ('monthly',    'Monthly',     1,  3000),
    ('quarterly',  'Quarterly',   3,  2700),
    ('halfyearly', 'Half-Yearly', 6,  2500),
    ('yearly',     'Full Year',   12, 2300)
ON DUPLICATE KEY UPDATE label = VALUES(label);

-- ------------------------------------------------------------
--  Inquiries / Leads  (the sales pipeline - Step 1)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inquiries (
    inquiry_id     INT AUTO_INCREMENT PRIMARY KEY,
    -- student details
    full_name      VARCHAR(100) NOT NULL,
    mobile         VARCHAR(15)  NOT NULL,
    parent_name    VARCHAR(100),
    parent_mobile  VARCHAR(15),
    email          VARCHAR(120),
    dob            DATE,
    gender         ENUM('Male','Female','Other'),
    city           VARCHAR(60),
    address        VARCHAR(255),
    -- the school Excel splits parents; parent_name/parent_mobile stay the primary contact
    father_name    VARCHAR(100),
    father_mobile  VARCHAR(15),
    mother_name    VARCHAR(100),
    mother_mobile  VARCHAR(15),
    district       VARCHAR(60),
    state          VARCHAR(60),
    -- academic details
    current_class      VARCHAR(40),
    prev_qualification VARCHAR(80),
    school_name        VARCHAR(120),
    board              VARCHAR(40),
    percentage         VARCHAR(20),
    prev_class_pct     VARCHAR(20),
    current_tutor      VARCHAR(150),
    student_type       VARCHAR(40),
    caste_category     VARCHAR(40),
    sibling_detail     VARCHAR(255),
    -- course interest
    class_interest VARCHAR(40),                  -- legacy field, still written by inquiry.jsp
    course_name    VARCHAR(80),
    batch_pref     VARCHAR(60),
    learning_mode  ENUM('Online','Offline','Hybrid'),
    branch         VARCHAR(60),
    expected_join_date DATE,
    academic_term    VARCHAR(40),
    preferred_centre VARCHAR(80),
    stream           VARCHAR(40),
    -- enquiry / ownership
    source         VARCHAR(40),
    heard_from     VARCHAR(120),                 -- "How they came to know about Havellsson?"
    lead_sub_stage VARCHAR(60),
    message        VARCHAR(500),
    -- exam fee is collected OUTSIDE this system; these two are reference only
    utr_number     VARCHAR(60),
    payment_status VARCHAR(30),
    -- scholarship carried onto the LEAD at publication, so the admission form can
    -- pre-fill a concession without knowing anything about exams
    scholarship_pct  DECIMAL(5,2),
    scholarship_note VARCHAR(200),
    priority       ENUM('Hot','Warm','Cold') NOT NULL DEFAULT 'Warm',
    counsellor_id  INT NULL,
    -- date AND time: counsellors promise a specific moment for the next call
    next_followup_date DATETIME NULL,
    status         ENUM('NEW','CONTACTED','INTERESTED','DEMO_PENDING','DEMO_COMPLETED',
                        'FOLLOWUP_REQUIRED','RESULT_DECLARED','CONVERTED','NOT_INTERESTED','LOST')
                   NOT NULL DEFAULT 'NEW',
    -- follow-up information
    student_requirements VARCHAR(500),
    parent_feedback      VARCHAR(500),
    counsellor_remarks   VARCHAR(500),
    -- conversion
    converted_student_id INT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_inq_mobile        (mobile),
    INDEX idx_inq_parent_mobile (parent_mobile),
    INDEX idx_inq_name          (full_name),
    INDEX idx_inq_school        (school_name),
    INDEX idx_inq_counsellor    (counsellor_id, status),
    INDEX idx_inq_followup      (next_followup_date, status),
    CONSTRAINT fk_inq_counsellor FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_inq_student FOREIGN KEY (converted_student_id)
        REFERENCES students(student_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Now that `inquiries` exists, close the circular reference from students.
-- (No IF NOT EXISTS for constraints in MySQL - safe to ignore a duplicate
--  error here when re-running this file over an existing database.)
ALTER TABLE students
    ADD CONSTRAINT fk_stu_inquiry FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(inquiry_id) ON DELETE SET NULL;

-- ------------------------------------------------------------
--  Follow-up history (one row per counselling touchpoint)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lead_followups (
    followup_id      INT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id       INT NOT NULL,
    counsellor_id    INT NULL,
    counsellor_name  VARCHAR(120),
    comm_type        ENUM('Call','WhatsApp','Email','Visit','Demo Discussion')
                     NOT NULL DEFAULT 'Call',
    discussion       TEXT,
    objection        VARCHAR(500),
    outcome_status   VARCHAR(30),
    next_action_date DATETIME NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fu_inquiry (inquiry_id),
    INDEX idx_fu_next    (next_action_date),
    CONSTRAINT fk_fu_inq FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(inquiry_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
--  Demo / trial class scheduling
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
    rating        TINYINT NULL,
    reminder_sent TINYINT(1) NOT NULL DEFAULT 0,
    created_by    VARCHAR(120),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_demo_inquiry (inquiry_id),
    INDEX idx_demo_date    (demo_date, status),
    CONSTRAINT fk_demo_inq FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(inquiry_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
--  Fee ledger: one negotiated fee record per student
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_fees (
    fee_id           INT AUTO_INCREMENT PRIMARY KEY,
    student_id       INT NOT NULL UNIQUE,
    plan_code        VARCHAR(40),
    course_fee       DECIMAL(10,2) NOT NULL DEFAULT 0,
    registration_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    material_fee     DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount         DECIMAL(10,2) NOT NULL DEFAULT 0,
    scholarship      DECIMAL(10,2) NOT NULL DEFAULT 0,
    net_payable      DECIMAL(10,2) NOT NULL DEFAULT 0,   -- COURSE fee after concession
    gst_rate         DECIMAL(5,2)  NOT NULL DEFAULT 0,
    gst_amount       DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_payable    DECIMAL(10,2) NOT NULL DEFAULT 0,   -- registration + net + GST
    registration_paid TINYINT(1)   NOT NULL DEFAULT 0,
    plan             ENUM('FULL','INSTALLMENT','EMI') NOT NULL DEFAULT 'FULL',
    approved_by      VARCHAR(120),
    remarks          VARCHAR(255),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sf_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
--  Installment / EMI schedule
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_installments (
    installment_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id     INT NOT NULL,
    seq            INT NOT NULL,
    kind           ENUM('COURSE','REGISTRATION') NOT NULL DEFAULT 'COURSE',
    label          VARCHAR(60),
    amount         DECIMAL(10,2) NOT NULL,
    pct            DECIMAL(5,2) NULL,                 -- part-payment % this slice carries
    due_date       DATE NOT NULL,
    paid_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    paid_date      DATE NULL,
    status         ENUM('PENDING','PARTIAL','PAID','OVERDUE') NOT NULL DEFAULT 'PENDING',
    reminder_sent  TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_student_seq (student_id, seq),
    INDEX idx_inst_due (due_date, status),
    CONSTRAINT fk_fi_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
--  Payments (fee collection / receipts)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    payment_id    INT AUTO_INCREMENT PRIMARY KEY,
    student_id    INT NOT NULL,
    receipt_no    VARCHAR(30)  NOT NULL UNIQUE,
    amount        DECIMAL(10,2) NOT NULL,
    payment_mode  ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online') NOT NULL DEFAULT 'Cash',
    payment_date  DATE NOT NULL,
    remarks       VARCHAR(255),
    collected_by  VARCHAR(100),                       -- display name (kept for legacy rows)
    collected_by_id INT NULL,                         -- real user reference
    installment_id  INT NULL,
    txn_ref       VARCHAR(60),                        -- cheque no / UPI ref / bank ref
    razorpay_order_id   VARCHAR(40) NULL,
    razorpay_payment_id VARCHAR(40) NULL,
    wa_sent       TINYINT(1) NOT NULL DEFAULT 0,      -- receipt already WhatsApped?
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pay_date (payment_date),
    CONSTRAINT fk_payments_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_pay_inst FOREIGN KEY (installment_id)
        REFERENCES fee_installments(installment_id) ON DELETE SET NULL,
    CONSTRAINT fk_pay_user FOREIGN KEY (collected_by_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
--  Masters (replace the hardcoded <option> lists in the JSPs)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lead_sources (
    source_id  INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    is_active  TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO lead_sources (name, sort_order) VALUES
    ('Website Enquiry',        10),
    ('Walk-in',                20),
    ('Phone Call',             30),
    ('WhatsApp Enquiry',       40),
    ('Social Media',           50),
    ('Advertisement Campaign', 60),
    ('Student Reference',      70),
    ('Counsellor Entry',       80),
    ('Friend / Referral',      90),
    ('Newspaper / Pamphlet',  100),
    ('Other',                 110)
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order);

-- ------------------------------------------------------------
--  Institute settings (GST rate, re-admission charge)
--  The brochure charges GST "in addition to the final course fee" but
--  never states the rate, so it is configurable rather than hardcoded.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS settings (
    skey        VARCHAR(50)  PRIMARY KEY,
    svalue      VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_at  TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO settings (skey, svalue, description) VALUES
    ('gst_rate',       '18.00', 'GST % added on top of the net course fee'),
    ('readmission_fee','500',   'Charged when a struck-off student re-admits after non-payment'),
    ('institute_name', 'Havellsson NEET Samrat', 'Shown on receipts and reports')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ------------------------------------------------------------
--  Courses — the programmes from the institute brochure
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS courses (
    course_id       INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(80) NOT NULL UNIQUE,
    code            VARCHAR(20),
    mode            ENUM('CLASSROOM','HYBRID','DISTANCE','TEST_SERIES','SHORT'),
    duration_months INT NULL,
    eligibility     VARCHAR(120),
    is_active       TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO courses (name, code, mode, duration_months, eligibility) VALUES
 ('11th NEET : ANKUR',                   'TYM',      'CLASSROOM',  24,'Class 10th moving to 11th'),
 ('12th NEET : BLOSSOM',                 'OYM-XII',  'CLASSROOM',  12,'Class 11th passed'),
 ('Repeater / Dropper : GROWER',         'OYM-RM',   'CLASSROOM',  12,'Class 12th passed'),
 ('2 Year Integrated Hybrid (TYM-H)',    'TYM-H',    'HYBRID',     24,'Class 10th moving to 11th'),
 ('12th Cum Medical Hybrid (OYM-XII H)', 'OYM-XII H','HYBRID',     12,'Class 11th moving to 12th'),
 ('Repeater / Dropper Hybrid (OYM-RMH)', 'OYM-RMH',  'HYBRID',     12,'Class 12th passed'),
 ('2 Year Integrated Distance (TYM-D)',  'TYM-D',    'DISTANCE',   24,'Class 10th moving to 11th'),
 ('One Year Distance (OYM-D)',           'OYM-D',    'DISTANCE',   12,'Class 12th / 12th passed'),
 ('2 Year Integrated HEATS (TYM-HEATS)', 'TYM-HEATS','TEST_SERIES',24,'Class 10th moving to 11th'),
 ('1 Year HEATS (OYM-HEATS)',            'OYM-HEATS','TEST_SERIES',12,'Class 12th / 12th passed'),
 ('Havellsson Fast Track Series (HFTS)', 'HFTS',     'SHORT',       3,'NEET appearing')
ON DUPLICATE KEY UPDATE code = VALUES(code), mode = VALUES(mode),
                        duration_months = VALUES(duration_months), eligibility = VALUES(eligibility);

-- ------------------------------------------------------------
--  Fee plans — one row per sellable programme + batch + duration.
--
--  pp_pattern / due_months encode the brochure's instalment plan:
--    one-year  40,35,25          due START,JUN,SEP
--    two-year  25,20,15,15,15,10 due START,JUN,SEP,JAN,APR,JUL
--  Short courses (distance, HEATS, HFTS) are full payment at admission.
--  registration_fee is NON-REFUNDABLE and never part of the instalments.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_plans (
    plan_id          INT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(40) NOT NULL UNIQUE,
    programme        VARCHAR(90) NOT NULL,
    programme_code   VARCHAR(20),
    mode             ENUM('CLASSROOM','HYBRID','DISTANCE','TEST_SERIES','SHORT') NOT NULL,
    batch_type       VARCHAR(40),
    eligibility      VARCHAR(120),
    duration_years   INT NOT NULL DEFAULT 1,
    registration_fee INT NOT NULL DEFAULT 0,
    course_fee       INT NOT NULL,
    gst_inclusive    TINYINT(1) NOT NULL DEFAULT 0,
    installments     INT NOT NULL DEFAULT 1,
    pp_pattern       VARCHAR(60) NOT NULL DEFAULT '100',
    due_months       VARCHAR(60) NOT NULL DEFAULT 'START',
    is_active        TINYINT(1) NOT NULL DEFAULT 1,
    sort_order       INT NOT NULL DEFAULT 0,
    INDEX idx_plan_mode (mode, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fee_plans
 (code, programme, programme_code, mode, batch_type, eligibility, duration_years,
  registration_fee, course_fee, gst_inclusive, installments, pp_pattern, due_months, sort_order) VALUES
 ('ANKUR-SURE-1Y',  '11th NEET : ANKUR',           'TYM',      'CLASSROOM',  'HAGL SURE (13 hrs/day)',    'Class 10th moving to 11th',1, 30000,104000,0,3,'40,35,25','START,JUN,SEP',10),
 ('ANKUR-SURE-2Y',  '11th NEET : ANKUR',           'TYM',      'CLASSROOM',  'HAGL SURE (13 hrs/day)',    'Class 10th moving to 11th',2, 30000,185000,0,6,'25,20,15,15,15,10','START,JUN,SEP,JAN,APR,JUL',11),
 ('ANKUR-AMB-1Y',   '11th NEET : ANKUR',           'TYM',      'CLASSROOM',  'HAGS AMBITION (4 hrs/day)', 'Class 10th moving to 11th',1, 12000, 75000,0,3,'40,35,25','START,JUN,SEP',12),
 ('ANKUR-AMB-2Y',   '11th NEET : ANKUR',           'TYM',      'CLASSROOM',  'HAGS AMBITION (4 hrs/day)', 'Class 10th moving to 11th',2, 12000,140000,0,6,'25,20,15,15,15,10','START,JUN,SEP,JAN,APR,JUL',13),
 ('BLOSSOM-SURE-1Y','12th NEET : BLOSSOM',         'OYM-XII',  'CLASSROOM',  'HAGL SURE (13 hrs/day)',    'Class 11th passed',        1, 30000,104000,0,3,'40,35,25','START,JUN,SEP',20),
 ('BLOSSOM-AMB-1Y', '12th NEET : BLOSSOM',         'OYM-XII',  'CLASSROOM',  'HAGS AMBITION (4 hrs/day)', 'Class 11th passed',        1, 12000, 75000,0,3,'40,35,25','START,JUN,SEP',21),
 ('GROWER-SURE-1Y', 'Repeater / Dropper : GROWER', 'OYM-RM',   'CLASSROOM',  'HAGL SURE (13 hrs/day)',    'Class 12th passed',        1, 30000,104000,0,3,'40,35,25','START,JUN,SEP',30),
 ('GROWER-AMB-1Y',  'Repeater / Dropper : GROWER', 'OYM-RM',   'CLASSROOM',  'HAGS AMBITION (4 hrs/day)', 'Class 12th passed',        1, 12000, 75000,0,3,'40,35,25','START,JUN,SEP',31),
 ('TYM-H-1Y',       '2 Year Integrated Hybrid (TYM-H)',    'TYM-H',    'HYBRID',NULL,'Class 10th moving to 11th',1, 5000, 41000,0,3,'40,35,25','START,JUN,SEP',40),
 ('TYM-H-2Y',       '2 Year Integrated Hybrid (TYM-H)',    'TYM-H',    'HYBRID',NULL,'Class 10th moving to 11th',2, 5000, 77000,0,6,'25,20,15,15,15,10','START,JUN,SEP,JAN,APR,JUL',41),
 ('OYM-XII-H',      '12th Cum Medical Hybrid (OYM-XII H)', 'OYM-XII H','HYBRID',NULL,'Class 11th moving to 12th',1, 5000, 41000,0,3,'40,35,25','START,JUN,SEP',42),
 ('OYM-RMH',        'Repeater / Dropper Hybrid (OYM-RMH)', 'OYM-RMH',  'HYBRID',NULL,'Class 12th passed',        1, 5000, 41000,0,3,'40,35,25','START,JUN,SEP',43),
 ('TYM-D-2Y',       '2 Year Integrated Distance (TYM-D)',  'TYM-D','DISTANCE',NULL,'Class 10th moving to 11th',2, 3000, 23000,0,1,'100','START',50),
 ('TYM-D-1Y',       '2 Year Integrated Distance (TYM-D)',  'TYM-D','DISTANCE',NULL,'Class 10th moving to 11th',1, 3000, 14000,0,1,'100','START',51),
 ('OYM-D',          'One Year Distance (OYM-D)',           'OYM-D','DISTANCE',NULL,'Class 12th / 12th passed', 1, 3000, 14000,0,1,'100','START',52),
 ('TYM-HEATS',      '2 Year Integrated HEATS (TYM-HEATS)','TYM-HEATS','TEST_SERIES',NULL,'Class 10th moving to 11th',2, 0, 21000,1,1,'100','START',60),
 ('OYM-HEATS',      '1 Year HEATS (OYM-HEATS)',           'OYM-HEATS','TEST_SERIES',NULL,'Class 12th / 12th passed', 1, 0, 12000,1,1,'100','START',61),
 ('HFTS',           'Havellsson Fast Track Series (HFTS)','HFTS',     'SHORT',      NULL,'NEET appearing',           1, 0,  3000,1,1,'100','START',62)
ON DUPLICATE KEY UPDATE course_fee = VALUES(course_fee), registration_fee = VALUES(registration_fee);

CREATE TABLE IF NOT EXISTS batches (
    batch_id     INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(60) NOT NULL UNIQUE,
    course_id    INT NULL,
    start_date   DATE NULL,
    timing       VARCHAR(40),
    faculty_name VARCHAR(120),
    branch       VARCHAR(60),
    is_active    TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_batch_course (course_id),
    CONSTRAINT fk_batch_course FOREIGN KEY (course_id)
        REFERENCES courses(course_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The two engagement levels the brochure sells against
INSERT INTO batches (name, timing) VALUES
    ('HAGL SURE (13 hrs/day)',    '13 hours per day'),
    ('HAGS AMBITION (4 hrs/day)', '4 hours per day')
ON DUPLICATE KEY UPDATE timing = VALUES(timing);

-- ------------------------------------------------------------
--  Reminder log (idempotency for the auto-send scheduler)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reminder_log (
    log_id      INT AUTO_INCREMENT PRIMARY KEY,
    kind        ENUM('FOLLOWUP','DEMO','FEE_DUE') NOT NULL,
    ref_id      INT NOT NULL,
    due_date    DATE NOT NULL,
    channel     VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    sent_ok     TINYINT(1) NOT NULL DEFAULT 0,
    message_id  VARCHAR(80),
    detail      VARCHAR(255),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_reminder (kind, ref_id, due_date, channel),
    INDEX idx_rem_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
--  Attendance (one row per student per day)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id   INT AUTO_INCREMENT PRIMARY KEY,
    student_id      INT NOT NULL,
    attendance_date DATE NOT NULL,
    status          ENUM('Present','Absent','Late','Leave') NOT NULL DEFAULT 'Present',
    marked_by       VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_student_date (student_id, attendance_date),
    CONSTRAINT fk_att_student FOREIGN KEY (student_id)
        REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
--  OMR sheet layouts
--
--  One row per physical answer sheet. The reader's geometry used to live in
--  `public static` fields on OmrService, so only ONE layout could ever work
--  and two concurrent scans shared the same mutable state.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS omr_templates (
    template_id     INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(80) NOT NULL,
    blocks          INT NOT NULL,                    -- answer columns across the page
    rows_per_block  INT NOT NULL,                    -- questions down each column
    total_questions INT AS (blocks * rows_per_block) STORED,
    roll_cols       INT NOT NULL,                    -- digits in the bubbled roll grid
    ring_ink        ENUM('RED','BLACK')        NOT NULL DEFAULT 'BLACK',
    timing_edge     ENUM('LEFT','RIGHT','BOTH') NOT NULL DEFAULT 'BOTH',
    geometry_json   MEDIUMTEXT NOT NULL,             -- fractional coordinates
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_tpl_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Geometry is fractions of image width/height, so it scales with scan
-- resolution. The 60Q values are a starting estimate taken off the blank PDF
-- and are marked "calibrated": false until they are tuned against real
-- scanned sheets.
INSERT INTO omr_templates (name, blocks, rows_per_block, roll_cols, ring_ink, timing_edge, geometry_json)
VALUES
('Havellsson 60Q (HOSE)', 4, 15, 6, 'BLACK', 'BOTH',
 '{"AX":[0.276,0.471,0.666,0.861],"DX":0.0300,"ROW0":0.1600,"ROWDY":0.01828,'
 '"R":0.0070,"RING_R":0.0075,"SNAPX":0.0055,"SNAPY":0.0065,"SNAPX_EDGE":0.026,'
 '"ROLL_COL0":0.0520,"ROLL_COLDX":0.0182,"ROLL_ROW0":0.2174,"ROLL_ROWDY":0.0184,"ROLL_R":0.0050,'
 '"regions":{"booklet":{"opts":["A","B","C","D"]},"board":{"opts":["ICSE","CBSE","STATE","OTHER"]},'
 '"exam_name":{"opts":["HAMSE","HACKSE"]}},"calibrated":false}'),
('Havellsson 180Q (legacy)', 4, 45, 9, 'RED', 'RIGHT',
 '{"AX":[0.276,0.471,0.666,0.861],"DX":0.0300,"ROW0":0.1600,"ROWDY":0.01828,'
 '"R":0.0070,"RING_R":0.0075,"SNAPX":0.0055,"SNAPY":0.0065,"SNAPX_EDGE":0.026,'
 '"ROLL_COL0":0.0520,"ROLL_COLDX":0.0182,"ROLL_ROW0":0.2174,"ROLL_ROWDY":0.0184,"ROLL_R":0.0050,'
 '"regions":{},"calibrated":true}')
AS new ON DUPLICATE KEY UPDATE name = new.name;

-- ------------------------------------------------------------
--  Examinations + subject-wise marks
--
--  Two kinds live here: INTERNAL class tests (marks typed in via exam_marks)
--  and the HOSE scholarship exams (HAMSE / HACKSE / HAT), which are separate
--  exams sat on an OMR sheet and scored from a scan.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exams (
    exam_id         INT AUTO_INCREMENT PRIMARY KEY,
    exam_name       VARCHAR(120) NOT NULL,
    exam_date       DATE,
    class_name      VARCHAR(40),
    subjects        VARCHAR(255) NOT NULL,           -- CSV e.g. "Physics,Chemistry,Biology"
    max_per_subject INT NOT NULL DEFAULT 100,
    -- scholarship-exam settings (unused by INTERNAL exams)
    exam_type       ENUM('INTERNAL','HAMSE','HACKSE','HAT') NOT NULL DEFAULT 'INTERNAL',
    template_id     INT NULL,
    total_questions INT NOT NULL DEFAULT 60,
    mark_correct    INT NOT NULL DEFAULT 4,
    mark_wrong      INT NOT NULL DEFAULT -1,         -- negative marking, per the sheet
    max_score       INT NOT NULL DEFAULT 240,
    roll_block_from INT NULL,                        -- inclusive 5-digit sequence block
    roll_block_to   INT NULL,
    roll_next       INT NULL,                        -- next unallocated sequence
    result_published TINYINT(1) NOT NULL DEFAULT 0,
    published_at    TIMESTAMP NULL,
    published_by    VARCHAR(100),
    created_by      VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exam_type (exam_type, exam_date),
    CONSTRAINT fk_exam_tpl FOREIGN KEY (template_id) REFERENCES omr_templates(template_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exam_marks (
    mark_id     INT AUTO_INCREMENT PRIMARY KEY,
    exam_id     INT NOT NULL,
    student_id  INT NOT NULL,
    subject     VARCHAR(40) NOT NULL,
    marks       INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_exam_student_subject (exam_id, student_id, subject),
    CONSTRAINT fk_em_exam    FOREIGN KEY (exam_id)    REFERENCES exams(exam_id)       ON DELETE CASCADE,
    CONSTRAINT fk_em_student FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
--  Learning materials: study material (PDF) + e-content (YouTube)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS materials (
    material_id  INT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(150) NOT NULL,
    class_name   VARCHAR(40),                       -- NULL/blank = visible to all classes
    subject      VARCHAR(40),
    type         ENUM('PDF','VIDEO') NOT NULL,
    file_path    VARCHAR(255),                      -- for PDF
    youtube_url  VARCHAR(255),                      -- for VIDEO
    uploaded_by  VARCHAR(100),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
--  OMR answer-sheet scans
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS omr_scans (
    scan_id      INT AUTO_INCREMENT PRIMARY KEY,
    student_id   INT NULL,
    title        VARCHAR(120),
    page_no      INT,
    start_q      INT,
    end_q        INT,
    total        INT,
    attempted    INT,
    blank        INT,
    ambiguous    INT,
    correct      INT NULL,
    wrong        INT NULL,
    score        INT NULL,
    result_json  MEDIUMTEXT,
    scanned_by   VARCHAR(100),
    -- scholarship-exam scanning. candidate_id stays NULLABLE on purpose: an
    -- unmatched sheet must still be stored so it can be reviewed and
    -- re-matched by hand, never lost.
    exam_id      INT NULL,
    candidate_id INT NULL,                          -- FK added after exam_candidates exists
    roll_read    VARCHAR(10) NULL,
    booklet_read CHAR(1) NULL,
    match_status ENUM('MATCHED','NO_ROLL','BAD_CHECKSUM','NOT_IN_EXAM','DUPLICATE','NO_BOOKLET') NULL,
    low_registration TINYINT(1) NOT NULL DEFAULT 0, -- timing/column detection fell back
    image_path   VARCHAR(255) NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scan_exam (exam_id, match_status),
    CONSTRAINT fk_os_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
--  Scholarship exam (HOSE): candidates, results, scholarship rules
--
--  Flow: school student list (Excel) -> candidates with a generated 6-digit
--  roll number -> offline 60-question OMR paper -> scan -> score ->
--  scholarship -> lead -> admission.
--
--  COLLATION: pinned to utf8mb4_unicode_ci to match the core tables. The
--  DATABASE default is utf8mb4_0900_ai_ci, and a table created without an
--  explicit COLLATE picks that up instead, after which any string/ENUM join
--  back to exams or inquiries dies with
--      ERROR 1267 Illegal mix of collations ... for operation '='
-- ------------------------------------------------------------

-- Question ranges -> subject. Fully configurable per exam: staff choose the
-- split (1-10 one subject, 11-30 another, ...). Nothing is hardcoded, so a
-- Medical paper (Phy/Chem/Bio) and an Engineering one (Phy/Chem/Maths) are
-- just different rows.
CREATE TABLE IF NOT EXISTS exam_subject_map (
    exam_id    INT NOT NULL,
    subject    VARCHAR(40) NOT NULL,
    q_from     INT NOT NULL,
    q_to       INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (exam_id, subject),
    CONSTRAINT fk_esm_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The paper is shuffled into booklets A-D, so an exam has FOUR keys and the
-- right one is chosen from the booklet bubble read off each sheet.
CREATE TABLE IF NOT EXISTS exam_answer_keys (
    exam_id      INT NOT NULL,
    booklet_code CHAR(1) NOT NULL,
    q_no         INT NOT NULL,
    correct_opt  CHAR(1) NOT NULL,
    PRIMARY KEY (exam_id, booklet_code, q_no),
    CONSTRAINT fk_eak_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audit trail and undo for each Excel upload.
CREATE TABLE IF NOT EXISTS import_batches (
    batch_id       INT AUTO_INCREMENT PRIMARY KEY,
    exam_id        INT NOT NULL,
    file_name      VARCHAR(255),
    school_name    VARCHAR(150),
    rows_total     INT NOT NULL DEFAULT 0,
    rows_imported  INT NOT NULL DEFAULT 0,
    rows_duplicate INT NOT NULL DEFAULT 0,
    rows_rejected  INT NOT NULL DEFAULT 0,
    uploaded_by    VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX (exam_id, created_at),
    CONSTRAINT fk_ib_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per SITTING, not per person. The Excel's "EXAM Attempt" column
-- confirms students re-sit: someone taking HAMSE in December and HACKSE in
-- March needs two roll numbers and two results against ONE inquiries row.
CREATE TABLE IF NOT EXISTS exam_candidates (
    candidate_id    INT AUTO_INCREMENT PRIMARY KEY,
    exam_id         INT NOT NULL,
    inquiry_id      INT NOT NULL,
    roll_no         CHAR(6) NOT NULL,               -- 5-digit sequence + Luhn check digit
    roll_kind       ENUM('GENERATED','LEGACY') NOT NULL DEFAULT 'GENERATED',
    booklet_code    CHAR(1) NULL,                   -- expected; actual is read off the sheet
    exam_centre     VARCHAR(120),
    attempt_no      TINYINT NOT NULL DEFAULT 1,
    status          ENUM('REGISTERED','APPEARED','ABSENT','RESULT_READY') NOT NULL DEFAULT 'REGISTERED',
    import_batch_id INT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Globally unique and never reused, so a sheet fed into the wrong exam is
    -- DETECTED rather than silently scored against whoever holds that number.
    UNIQUE KEY uq_roll (roll_no),
    UNIQUE KEY uq_exam_person (exam_id, inquiry_id),
    INDEX (exam_id, status),
    INDEX (import_batch_id),
    CONSTRAINT fk_ec_exam  FOREIGN KEY (exam_id)         REFERENCES exams(exam_id),
    CONSTRAINT fk_ec_inq   FOREIGN KEY (inquiry_id)      REFERENCES inquiries(inquiry_id),
    CONSTRAINT fk_ec_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Scholarship rules, from brochure pages 9-10.
--   FORMULA: scholarship = LEAST(cap_pct, factor * score percentage)
--            HAMSE  factor 1.00 cap 100   (equal to score %)
--            HACKSE factor 0.90 cap  90   (90% of score %)
--   FIXED:   a flat percentage for a named criterion, claimed with proof.
-- p10: "A student can avail any ONE of the scholarship in Category A & B,
-- whichever is higher." Awards take the MAXIMUM, never the sum.
CREATE TABLE IF NOT EXISTS scholarship_rules (
    rule_id     INT AUTO_INCREMENT PRIMARY KEY,
    category    ENUM('A','B') NOT NULL,
    sub_group   VARCHAR(60)  NOT NULL,              -- HOSE / SCHOOL / EXTRAORDINARY / NEET / OTHER
    criteria    VARCHAR(200) NOT NULL,
    rule_kind   ENUM('FORMULA','FIXED') NOT NULL DEFAULT 'FIXED',
    exam_type   ENUM('HAMSE','HACKSE','HAT') NULL,  -- FORMULA rules only
    factor      DECIMAL(5,4) NULL,                  -- FORMULA: multiplier on score %
    cap_pct     DECIMAL(5,2) NULL,                  -- FORMULA: upper bound
    fixed_pct   DECIMAL(5,2) NULL,                  -- FIXED: the flat award
    needs_proof TINYINT(1) NOT NULL DEFAULT 1,
    is_active   TINYINT(1) NOT NULL DEFAULT 1,
    sort_order  INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_rule (category, sub_group, criteria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO scholarship_rules
  (category, sub_group, criteria, rule_kind, exam_type, factor, cap_pct, fixed_pct, needs_proof, sort_order)
VALUES
-- Category A1 - HOSE performance. THE exam-driven rule, computed automatically.
('A','HOSE','HAMSE score percentage',  'FORMULA','HAMSE', 1.0000, 100.00, NULL, 0, 10),
('A','HOSE','HACKSE score percentage', 'FORMULA','HACKSE',0.9000,  90.00, NULL, 0, 11),
-- Category A2 - school exam score
('A','SCHOOL','Board score above 97% or State/Science/National topper','FIXED',NULL,NULL,NULL,100.00,1,20),
('A','SCHOOL','A1 grade or above 95% in board',                        'FIXED',NULL,NULL,NULL, 40.00,1,21),
('A','SCHOOL','A1 in Science, Maths and English or above 90% in same',  'FIXED',NULL,NULL,NULL, 30.00,1,22),
('A','SCHOOL','A1 in Maths and A2 in Science',                          'FIXED',NULL,NULL,NULL, 25.00,1,23),
('A','SCHOOL','A2 in all subjects',                                     'FIXED',NULL,NULL,NULL, 20.00,1,24),
-- Category A3 - extraordinary performance
('A','EXTRAORDINARY','International olympiad or merit exam (rank 1-10)',    'FIXED',NULL,NULL,NULL,100.00,1,30),
('A','EXTRAORDINARY','International olympiad or merit exam (below rank 10)','FIXED',NULL,NULL,NULL, 50.00,1,31),
('A','EXTRAORDINARY','State govt merit scholarship qualified',              'FIXED',NULL,NULL,NULL, 50.00,1,32),
('A','EXTRAORDINARY','National level olympiad or merit test qualified',     'FIXED',NULL,NULL,NULL, 50.00,1,33),
-- Category A4 - previous year NEET score
('A','NEET','Within 50 marks of cutoff',     'FIXED',NULL,NULL,NULL, 80.00,1,40),
('A','NEET','51 to 100 marks below cutoff',  'FIXED',NULL,NULL,NULL, 70.00,1,41),
('A','NEET','101 to 150 marks below cutoff', 'FIXED',NULL,NULL,NULL, 50.00,1,42),
('A','NEET','151 to 200 marks below cutoff', 'FIXED',NULL,NULL,NULL, 30.00,1,43),
('A','NEET','201 to 250 marks below cutoff', 'FIXED',NULL,NULL,NULL, 20.00,1,44),
-- Category B - other than academic performance
('B','OTHER','Ex-Havellsson student (HA Gurukul)',        'FIXED',NULL,NULL,NULL,100.00,1,50),
('B','OTHER','Ex-Havellsson student (HA Generous)',       'FIXED',NULL,NULL,NULL, 50.00,1,51),
('B','OTHER','Ex-Havellsson student (HHC, HDLP, HEATS)',  'FIXED',NULL,NULL,NULL, 25.00,1,52),
('B','OTHER','Single parent / paralysed parents',         'FIXED',NULL,NULL,NULL, 50.00,1,53),
('B','OTHER','Sibling (brother / sister)',                'FIXED',NULL,NULL,NULL, 30.00,1,54),
('B','OTHER','Defence (Indian army & paramilitary force)','FIXED',NULL,NULL,NULL, 50.00,1,55),
('B','OTHER','Police directorate',                        'FIXED',NULL,NULL,NULL, 30.00,1,56),
('B','OTHER','Martyr (lost their father & mother)',       'FIXED',NULL,NULL,NULL,100.00,1,57)
AS new ON DUPLICATE KEY UPDATE
  rule_kind = new.rule_kind, factor = new.factor, cap_pct = new.cap_pct, fixed_pct = new.fixed_pct;

CREATE TABLE IF NOT EXISTS exam_results (
    result_id       INT AUTO_INCREMENT PRIMARY KEY,
    candidate_id    INT NOT NULL,
    scan_id         INT NULL,
    booklet_read    CHAR(1) NULL,
    attempted       INT NOT NULL DEFAULT 0,
    correct         INT NOT NULL DEFAULT 0,
    wrong           INT NOT NULL DEFAULT 0,
    blank           INT NOT NULL DEFAULT 0,
    raw_score       INT NOT NULL DEFAULT 0,          -- signed: negative marking can go below 0
    max_score       INT NOT NULL DEFAULT 240,
    percentage      DECIMAL(5,2) NOT NULL DEFAULT 0, -- max(0, raw) / max * 100
    scholarship_pct DECIMAL(5,2) NULL,
    awarded_rule_id INT NULL,                        -- WHICH rule won, so the figure is explainable
    rank_overall    INT NULL,
    verified_by     VARCHAR(100),
    verified_at     TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_res_candidate (candidate_id),
    CONSTRAINT fk_er_cand FOREIGN KEY (candidate_id)    REFERENCES exam_candidates(candidate_id) ON DELETE CASCADE,
    CONSTRAINT fk_er_rule FOREIGN KEY (awarded_rule_id) REFERENCES scholarship_rules(rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_result_subjects (
    result_id INT NOT NULL,
    subject   VARCHAR(40) NOT NULL,
    q_from    INT NOT NULL,
    q_to      INT NOT NULL,
    attempted INT NOT NULL DEFAULT 0,
    correct   INT NOT NULL DEFAULT 0,
    wrong     INT NOT NULL DEFAULT 0,
    score     INT NOT NULL DEFAULT 0,
    PRIMARY KEY (result_id, subject),
    CONSTRAINT fk_ers_res FOREIGN KEY (result_id) REFERENCES exam_results(result_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A claim against a FIXED rule. p10: "proper proof in the above criteria will
-- have to be submitted", so a claim must be VERIFIED before it can win.
CREATE TABLE IF NOT EXISTS scholarship_claims (
    claim_id    INT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id  INT NOT NULL,
    rule_id     INT NOT NULL,
    status      ENUM('CLAIMED','VERIFIED','REJECTED') NOT NULL DEFAULT 'CLAIMED',
    proof_path  VARCHAR(255),
    remarks     VARCHAR(255),
    verified_by VARCHAR(100),
    verified_at TIMESTAMP NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_claim (inquiry_id, rule_id),
    INDEX (inquiry_id, status),
    CONSTRAINT fk_sc_inq  FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_rule FOREIGN KEY (rule_id)    REFERENCES scholarship_rules(rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Now that exam_candidates exists, close the reference from omr_scans.
-- (No IF NOT EXISTS for constraints in MySQL - safe to ignore a duplicate
--  error here when re-running this file over an existing database.)
ALTER TABLE omr_scans
    ADD CONSTRAINT fk_os_cand FOREIGN KEY (candidate_id)
        REFERENCES exam_candidates(candidate_id);

-- ------------------------------------------------------------
--  Support tickets (student raises a concern → management acts)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tickets (
    ticket_id   INT AUTO_INCREMENT PRIMARY KEY,
    student_id  INT NOT NULL,
    category    VARCHAR(40)  NOT NULL DEFAULT 'General',
    subject     VARCHAR(160) NOT NULL,
    message     TEXT NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',     -- OPEN / IN_PROGRESS / RESOLVED / CLOSED
    priority    VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',   -- LOW / NORMAL / HIGH
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX (student_id), INDEX (status),
    CONSTRAINT fk_ticket_student FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_replies (
    reply_id    INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   INT NOT NULL,
    sender      VARCHAR(10)  NOT NULL,                    -- STUDENT / STAFF
    sender_name VARCHAR(120),
    message     TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (ticket_id),
    CONSTRAINT fk_reply_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================
--  COUNSELLOR TARGETS
--  (migration: 2026-08-counsellor-targets.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS counsellor_targets (
    target_id         INT AUTO_INCREMENT PRIMARY KEY,
    counsellor_id     INT NOT NULL,
    -- QUARTER is the working period; MONTH and YEAR are here so a different
    -- cadence later is new rows rather than a migration.
    period_type       ENUM('MONTH','QUARTER','YEAR') NOT NULL DEFAULT 'QUARTER',
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    admissions_target INT NOT NULL DEFAULT 0,
    revenue_target    DECIMAL(12,2) NOT NULL DEFAULT 0,
    -- BOOKED    = value of admissions closed in the period
    -- COLLECTED = cash received in the period
    revenue_basis     ENUM('BOOKED','COLLECTED') NOT NULL DEFAULT 'BOOKED',
    notes             VARCHAR(255),
    set_by            VARCHAR(100),
    set_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- one target per person per period, so a mid-quarter revision edits the row
    -- rather than stacking a second one nobody notices
    UNIQUE KEY uq_target (counsellor_id, period_type, period_start),
    INDEX (period_start, period_end),
    CONSTRAINT fk_target_user FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
--  COURSE TARGETS
--  (migration: 2026-08-course-targets.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS course_targets (
    target_id         INT AUTO_INCREMENT PRIMARY KEY,
    course_id         INT NOT NULL,
    period_type       ENUM('MONTH','QUARTER','YEAR') NOT NULL DEFAULT 'QUARTER',
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    admissions_target INT NOT NULL DEFAULT 0,
    revenue_target    DECIMAL(12,2) NOT NULL DEFAULT 0,
    revenue_basis     ENUM('BOOKED','COLLECTED') NOT NULL DEFAULT 'BOOKED',
    notes             VARCHAR(255),
    set_by            VARCHAR(100),
    set_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_course_target (course_id, period_type, period_start),
    INDEX (period_start, period_end),
    CONSTRAINT fk_course_target_course FOREIGN KEY (course_id)
        REFERENCES courses(course_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
--  COUNSELLOR × COURSE TARGETS
--  (migration: 2026-08-counsellor-course-targets.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS counsellor_course_targets (
    target_id         INT AUTO_INCREMENT PRIMARY KEY,
    counsellor_id     INT NOT NULL,
    course_id         INT NOT NULL,
    period_type       ENUM('MONTH','QUARTER','YEAR') NOT NULL DEFAULT 'QUARTER',
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    admissions_target INT NOT NULL DEFAULT 0,
    revenue_target    DECIMAL(12,2) NOT NULL DEFAULT 0,
    revenue_basis     ENUM('BOOKED','COLLECTED') NOT NULL DEFAULT 'BOOKED',
    notes             VARCHAR(255),
    set_by            VARCHAR(100),
    set_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_counsellor_course_target (counsellor_id, course_id, period_type, period_start),
    INDEX (period_start, period_end),
    CONSTRAINT fk_cct_user FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cct_course FOREIGN KEY (course_id)
        REFERENCES courses(course_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  LEAD STAGES / SUB STAGES
--  (migration: 2026-08-lead-stage-substage.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS lead_stages (
    stage_id   INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    is_active  TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_stage_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lead_sub_stages (
    sub_stage_id INT AUTO_INCREMENT PRIMARY KEY,
    stage_id     INT NOT NULL,
    name         VARCHAR(120) NOT NULL,
    is_active    TINYINT(1) NOT NULL DEFAULT 1,
    sort_order   INT NOT NULL DEFAULT 0,
    -- the same wording may legitimately appear under two stages
    -- (e.g. "Lost" is both a stage and a CPA Registered sub stage)
    UNIQUE KEY uq_sub_stage (stage_id, name),
    CONSTRAINT fk_sub_stage_stage FOREIGN KEY (stage_id)
        REFERENCES lead_stages(stage_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
--  FINANCE MODULE  -  exam fees in, expense fund out
--  (migration: 2026-08-finance-01.sql)
--
--  Paid state is never stored: a candidate's position and a work
--  order's payment progress are both derived from the receipts and
--  vouchers, so they cannot drift from the money actually moved.
-- ============================================================

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

-- Who created, closed, reopened or deleted a fund, and why.
-- (migration: 2026-08-fund-audit.sql)
--
-- fund_id is deliberately NOT a foreign key: the audit row for a DELETE has to
-- outlive the fund it describes. The snapshot columns exist for the same
-- reason - once the fund row is gone they are the only record of what it was
-- called and what it held. Only an empty fund can ever be deleted; one with a
-- statement behind it is CLOSEd instead.
CREATE TABLE IF NOT EXISTS fund_audit (
    audit_id    INT AUTO_INCREMENT PRIMARY KEY,
    fund_id     INT NOT NULL,
    action      ENUM('CREATE','RENAME','CLOSE','REOPEN','DELETE') NOT NULL,
    fund_name   VARCHAR(80)   NOT NULL,
    -- RENAME only: what it was called before. Other actions leave this NULL and
    -- keep their snapshot in fund_name.
    old_name    VARCHAR(80)   NULL,
    balance     DECIMAL(12,2) NOT NULL DEFAULT 0,
    txn_count   INT           NOT NULL DEFAULT 0,
    reason      VARCHAR(255),
    acted_by    VARCHAR(100),
    acted_by_id INT NULL,
    acted_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fa_fund (fund_id, acted_at),
    INDEX idx_fa_when (acted_at),
    CONSTRAINT fk_fa_user FOREIGN KEY (acted_by_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

-- ── Fee columns the finance module adds to the exam tables ──
-- Part of the same migration; kept here so a fresh install matches a
-- migrated one exactly, which is what the header of this file promises.
ALTER TABLE exams
    ADD COLUMN exam_fee DECIMAL(10,2) NOT NULL DEFAULT 0;

ALTER TABLE exam_candidates
    ADD COLUMN fee_amount    DECIMAL(10,2) NULL,        -- NULL = use the exam's list price
    ADD COLUMN fee_waived    TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN waiver_reason VARCHAR(160) NULL;

-- ── Seed: one fund and the expense categories ──
INSERT INTO fund_accounts (name, description, opening_balance, opening_date, created_by)
VALUES ('Main Office Fund',
        'Primary operating fund. Topped up by management; all expenses draw it down.',
        0, CURDATE(), 'system')
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO expense_categories (name, sort_order) VALUES
    ('Printing', 10), ('Stationery', 20), ('Rent', 30), ('Electricity', 40),
    ('Internet & Phone', 50), ('Salaries', 60), ('Housekeeping', 70),
    ('Repairs & Maintenance', 80), ('Travel', 90), ('Marketing', 100),
    ('Exam Expenses', 110), ('Refreshments', 120), ('Miscellaneous', 999)
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
--  ONLINE MCQ EXAMS
--  (migration: 2026-08-online-exams.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS online_exams (
    online_exam_id   INT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(150) NOT NULL,
    class_name       VARCHAR(40)  NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 30,
    is_active        TINYINT(1) NOT NULL DEFAULT 1,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_oe_class (class_name, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS online_exam_questions (
    question_id      INT AUTO_INCREMENT PRIMARY KEY,
    online_exam_id   INT NOT NULL,
    subject          VARCHAR(60),
    chapter          VARCHAR(100),
    question_text    VARCHAR(1000) NOT NULL,
    option_a         VARCHAR(300) NOT NULL,
    option_b         VARCHAR(300) NOT NULL,
    option_c         VARCHAR(300) NOT NULL,
    option_d         VARCHAR(300) NOT NULL,
    correct_answer   CHAR(1) NOT NULL,
    difficulty       ENUM('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'MEDIUM',
    marks            INT NOT NULL DEFAULT 1,
    sort_order       INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_oeq_exam (online_exam_id, sort_order),
    CONSTRAINT fk_oeq_exam FOREIGN KEY (online_exam_id)
        REFERENCES online_exams(online_exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS online_exam_attempts (
    attempt_id       INT AUTO_INCREMENT PRIMARY KEY,
    online_exam_id   INT NOT NULL,
    student_id       INT NOT NULL,
    started_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at     TIMESTAMP NULL,
    total_marks      INT NOT NULL DEFAULT 0,
    score            INT NOT NULL DEFAULT 0,
    status           ENUM('IN_PROGRESS','SUBMITTED') NOT NULL DEFAULT 'IN_PROGRESS',
    UNIQUE KEY uq_oea_student (online_exam_id, student_id),
    CONSTRAINT fk_oea_exam    FOREIGN KEY (online_exam_id) REFERENCES online_exams(online_exam_id) ON DELETE CASCADE,
    CONSTRAINT fk_oea_student FOREIGN KEY (student_id)     REFERENCES students(student_id)         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS online_exam_answers (
    answer_id        INT AUTO_INCREMENT PRIMARY KEY,
    attempt_id       INT NOT NULL,
    question_id      INT NOT NULL,
    selected_answer  CHAR(1) NULL,
    is_correct       TINYINT(1) NOT NULL DEFAULT 0,
    marks_awarded    INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_oea2_question (attempt_id, question_id),
    CONSTRAINT fk_oea2_attempt  FOREIGN KEY (attempt_id)  REFERENCES online_exam_attempts(attempt_id)  ON DELETE CASCADE,
    CONSTRAINT fk_oea2_question FOREIGN KEY (question_id) REFERENCES online_exam_questions(question_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
