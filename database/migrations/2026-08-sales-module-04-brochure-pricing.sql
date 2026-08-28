-- ============================================================
--  Sales Module migration, part 4  —  real pricing from the brochure
--
--  Source: Requirement/Broucher/Broucher.pdf
--          p4  classroom courses offered
--          p5  classroom batches & fee structure
--          p6  instalment plan + terms & conditions
--          p7  hybrid courses (HHC)
--          p8  distance learning (HDLP) + test series (HEATS)
--          p9  scholarship scheme (HOSE)
--
--  Run AFTER 2026-08-sales-module-03-ledger-backfill.sql:
--      mysql -u root -p tuition_db < database/migrations/2026-08-sales-module-04-brochure-pricing.sql
--
--  Replaces the placeholder pricing the app shipped with
--  (Rs.500 registration + Rs.800 material + per_month x 12) with the
--  institute's actual fee plans.
-- ============================================================

-- ------------------------------------------------------------
--  1. Institute settings (GST rate, re-admission charge)
--
--  The brochure states GST is charged "in addition to the final course
--  fee" but never gives a rate, so it lives here as an editable value
--  rather than being baked into code. 18% is the standard Indian rate
--  for coaching services.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS settings (
    skey        VARCHAR(50)  PRIMARY KEY,
    svalue      VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_at  TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO settings (skey, svalue, description) VALUES
    ('gst_rate',       '18.00', 'GST % added on top of the net course fee (brochure: "GST will be applicable in addition to the final course fee")'),
    ('readmission_fee','500',   'Charged when a struck-off student re-admits after non-payment (brochure terms)'),
    ('institute_name', 'Havellsson NEET Samrat', 'Shown on receipts and reports')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ------------------------------------------------------------
--  2. Fee plans — one row per sellable programme + batch + duration
--
--  pp_pattern / due_months encode the instalment plan from p6:
--      one-year  40,35,25            due START,JUN,SEP
--      two-year  25,20,15,15,15,10   due START,JUN,SEP,JAN,APR,JUL
--  Short courses (HDLP, HEATS, HFTS) are full payment at admission —
--  the brochure states "no PP will be available" — so they carry a
--  single 100% instalment due at the start.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_plans (
    plan_id          INT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(40) NOT NULL UNIQUE,   -- ANKUR-SURE-1Y
    programme        VARCHAR(90) NOT NULL,          -- 11th NEET : ANKUR
    programme_code   VARCHAR(20),                   -- TYM / OYM-XII / OYM-RM / TYM-H …
    mode             ENUM('CLASSROOM','HYBRID','DISTANCE','TEST_SERIES','SHORT') NOT NULL,
    batch_type       VARCHAR(40),                   -- HAGL SURE / HAGS AMBITION / NULL
    eligibility      VARCHAR(120),
    duration_years   INT NOT NULL DEFAULT 1,
    registration_fee INT NOT NULL DEFAULT 0,        -- non-refundable, NOT part of course fee
    course_fee       INT NOT NULL,
    gst_inclusive    TINYINT(1) NOT NULL DEFAULT 0, -- HEATS fees are quoted GST-inclusive
    installments     INT NOT NULL DEFAULT 1,
    pp_pattern       VARCHAR(60) NOT NULL DEFAULT '100',
    due_months       VARCHAR(60) NOT NULL DEFAULT 'START',
    is_active        TINYINT(1) NOT NULL DEFAULT 1,
    sort_order       INT NOT NULL DEFAULT 0,
    INDEX idx_plan_mode (mode, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Classroom (p5) ──
-- Registration by batch type (p6 terms): HAGL SURE Rs.30,000 · HAGS AMBITION Rs.12,000
INSERT INTO fee_plans
 (code, programme, programme_code, mode, batch_type, eligibility, duration_years,
  registration_fee, course_fee, installments, pp_pattern, due_months, sort_order) VALUES
 ('ANKUR-SURE-1Y',  '11th NEET : ANKUR',            'TYM',     'CLASSROOM','HAGL SURE (13 hrs/day)',     'Class 10th moving to 11th',1, 30000,104000, 3,'40,35,25','START,JUN,SEP',10),
 ('ANKUR-SURE-2Y',  '11th NEET : ANKUR',            'TYM',     'CLASSROOM','HAGL SURE (13 hrs/day)',     'Class 10th moving to 11th',2, 30000,185000, 6,'25,20,15,15,15,10','START,JUN,SEP,JAN,APR,JUL',11),
 ('ANKUR-AMB-1Y',   '11th NEET : ANKUR',            'TYM',     'CLASSROOM','HAGS AMBITION (4 hrs/day)',  'Class 10th moving to 11th',1, 12000, 75000, 3,'40,35,25','START,JUN,SEP',12),
 ('ANKUR-AMB-2Y',   '11th NEET : ANKUR',            'TYM',     'CLASSROOM','HAGS AMBITION (4 hrs/day)',  'Class 10th moving to 11th',2, 12000,140000, 6,'25,20,15,15,15,10','START,JUN,SEP,JAN,APR,JUL',13),
 ('BLOSSOM-SURE-1Y','12th NEET : BLOSSOM',          'OYM-XII', 'CLASSROOM','HAGL SURE (13 hrs/day)',     'Class 11th passed',        1, 30000,104000, 3,'40,35,25','START,JUN,SEP',20),
 ('BLOSSOM-AMB-1Y', '12th NEET : BLOSSOM',          'OYM-XII', 'CLASSROOM','HAGS AMBITION (4 hrs/day)',  'Class 11th passed',        1, 12000, 75000, 3,'40,35,25','START,JUN,SEP',21),
 ('GROWER-SURE-1Y', 'Repeater / Dropper : GROWER',  'OYM-RM',  'CLASSROOM','HAGL SURE (13 hrs/day)',     'Class 12th passed',        1, 30000,104000, 3,'40,35,25','START,JUN,SEP',30),
 ('GROWER-AMB-1Y',  'Repeater / Dropper : GROWER',  'OYM-RM',  'CLASSROOM','HAGS AMBITION (4 hrs/day)',  'Class 12th passed',        1, 12000, 75000, 3,'40,35,25','START,JUN,SEP',31)
ON DUPLICATE KEY UPDATE course_fee = VALUES(course_fee), registration_fee = VALUES(registration_fee);

-- ── Hybrid, HHC (p7) — registration Rs.5,000 ──
INSERT INTO fee_plans
 (code, programme, programme_code, mode, batch_type, eligibility, duration_years,
  registration_fee, course_fee, installments, pp_pattern, due_months, sort_order) VALUES
 ('TYM-H-1Y',    '2 Year Integrated Hybrid (TYM-H)',        'TYM-H',    'HYBRID', NULL,'Class 10th moving to 11th',1, 5000, 41000, 3,'40,35,25','START,JUN,SEP',40),
 ('TYM-H-2Y',    '2 Year Integrated Hybrid (TYM-H)',        'TYM-H',    'HYBRID', NULL,'Class 10th moving to 11th',2, 5000, 77000, 6,'25,20,15,15,15,10','START,JUN,SEP,JAN,APR,JUL',41),
 ('OYM-XII-H',   '12th Cum Medical Hybrid (OYM-XII H)',     'OYM-XII H','HYBRID', NULL,'Class 11th moving to 12th',1, 5000, 41000, 3,'40,35,25','START,JUN,SEP',42),
 ('OYM-RMH',     'Repeater / Dropper Hybrid (OYM-RMH)',     'OYM-RMH',  'HYBRID', NULL,'Class 12th passed',        1, 5000, 41000, 3,'40,35,25','START,JUN,SEP',43)
ON DUPLICATE KEY UPDATE course_fee = VALUES(course_fee), registration_fee = VALUES(registration_fee);

-- ── Distance learning, HDLP (p8) — registration Rs.3,000, full payment at admission ──
INSERT INTO fee_plans
 (code, programme, programme_code, mode, batch_type, eligibility, duration_years,
  registration_fee, course_fee, installments, pp_pattern, due_months, sort_order) VALUES
 ('TYM-D-2Y', '2 Year Integrated Distance (TYM-D)', 'TYM-D','DISTANCE',NULL,'Class 10th moving to 11th',2, 3000, 23000, 1,'100','START',50),
 ('TYM-D-1Y', '2 Year Integrated Distance (TYM-D)', 'TYM-D','DISTANCE',NULL,'Class 10th moving to 11th',1, 3000, 14000, 1,'100','START',51),
 ('OYM-D',    'One Year Distance (OYM-D)',          'OYM-D','DISTANCE',NULL,'Class 12th / 12th passed', 1, 3000, 14000, 1,'100','START',52)
ON DUPLICATE KEY UPDATE course_fee = VALUES(course_fee), registration_fee = VALUES(registration_fee);

-- ── Test series & short courses (p4, p8) — fees quoted GST-inclusive, full payment ──
INSERT INTO fee_plans
 (code, programme, programme_code, mode, batch_type, eligibility, duration_years,
  registration_fee, course_fee, gst_inclusive, installments, pp_pattern, due_months, sort_order) VALUES
 ('TYM-HEATS','2 Year Integrated HEATS (TYM-HEATS)','TYM-HEATS','TEST_SERIES',NULL,'Class 10th moving to 11th',2, 0, 21000, 1, 1,'100','START',60),
 ('OYM-HEATS','1 Year HEATS (OYM-HEATS)',           'OYM-HEATS','TEST_SERIES',NULL,'Class 12th / 12th passed', 1, 0, 12000, 1, 1,'100','START',61),
 ('HFTS',     'Havellsson Fast Track Series (HFTS)', 'HFTS',     'SHORT',      NULL,'NEET appearing',           1, 0,  3000, 1, 1,'100','START',62)
ON DUPLICATE KEY UPDATE course_fee = VALUES(course_fee);

-- ------------------------------------------------------------
--  3. Course master rebuilt from the brochure programmes
-- ------------------------------------------------------------
ALTER TABLE courses
    ADD COLUMN code VARCHAR(20)  NULL AFTER name,
    ADD COLUMN mode ENUM('CLASSROOM','HYBRID','DISTANCE','TEST_SERIES','SHORT') NULL AFTER code,
    ADD COLUMN eligibility VARCHAR(120) NULL;

-- The five placeholder rows seeded in migration 01 are not real programmes.
DELETE FROM courses WHERE name IN
    ('11th - Science (PCB)','11th - Science (PCM)','12th - Science (PCB)','12th - Science (PCM)','NEET Repeater');

INSERT INTO courses (name, code, mode, duration_months, eligibility) VALUES
 ('11th NEET : ANKUR',                       'TYM',      'CLASSROOM',  24,'Class 10th moving to 11th'),
 ('12th NEET : BLOSSOM',                     'OYM-XII',  'CLASSROOM',  12,'Class 11th passed'),
 ('Repeater / Dropper : GROWER',             'OYM-RM',   'CLASSROOM',  12,'Class 12th passed'),
 ('2 Year Integrated Hybrid (TYM-H)',        'TYM-H',    'HYBRID',     24,'Class 10th moving to 11th'),
 ('12th Cum Medical Hybrid (OYM-XII H)',     'OYM-XII H','HYBRID',     12,'Class 11th moving to 12th'),
 ('Repeater / Dropper Hybrid (OYM-RMH)',     'OYM-RMH',  'HYBRID',     12,'Class 12th passed'),
 ('2 Year Integrated Distance (TYM-D)',      'TYM-D',    'DISTANCE',   24,'Class 10th moving to 11th'),
 ('One Year Distance (OYM-D)',               'OYM-D',    'DISTANCE',   12,'Class 12th / 12th passed'),
 ('2 Year Integrated HEATS (TYM-HEATS)',     'TYM-HEATS','TEST_SERIES',24,'Class 10th moving to 11th'),
 ('1 Year HEATS (OYM-HEATS)',                'OYM-HEATS','TEST_SERIES',12,'Class 12th / 12th passed'),
 ('Havellsson Fast Track Series (HFTS)',     'HFTS',     'SHORT',       3,'NEET appearing')
ON DUPLICATE KEY UPDATE code = VALUES(code), mode = VALUES(mode),
                        duration_months = VALUES(duration_months), eligibility = VALUES(eligibility);

-- ------------------------------------------------------------
--  4. Batch master — the two engagement levels from p5
--
--  batches.name had no unique key, so the upsert below would have
--  inserted duplicates on a re-run. Add the constraint first.
-- ------------------------------------------------------------
ALTER TABLE batches ADD UNIQUE KEY uq_batch_name (name);

INSERT INTO batches (name, timing, branch) VALUES
 ('HAGL SURE (13 hrs/day)',    '13 hours per day', NULL),
 ('HAGS AMBITION (4 hrs/day)', '4 hours per day',  NULL)
ON DUPLICATE KEY UPDATE timing = VALUES(timing);

-- ------------------------------------------------------------
--  5. Fee ledger gains GST and a plan reference
--
--  net_payable stays the COURSE fee after concession. Registration is a
--  separate non-refundable charge (brochure: "Registration is not
--  included in Course Fee") and is never part of the instalment split.
-- ------------------------------------------------------------
ALTER TABLE student_fees
    ADD COLUMN plan_code     VARCHAR(40)   NULL AFTER student_id,
    ADD COLUMN gst_rate      DECIMAL(5,2)  NOT NULL DEFAULT 0,
    ADD COLUMN gst_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN total_payable DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN registration_paid TINYINT(1) NOT NULL DEFAULT 0;

-- Existing rows: no GST was ever charged, so total = what they already owed.
UPDATE student_fees SET total_payable = net_payable WHERE total_payable = 0;

-- ------------------------------------------------------------
--  6. Instalments record which slice they are and what % they carry
-- ------------------------------------------------------------
ALTER TABLE fee_installments
    ADD COLUMN kind ENUM('COURSE','REGISTRATION') NOT NULL DEFAULT 'COURSE' AFTER seq,
    ADD COLUMN pct  DECIMAL(5,2) NULL AFTER amount;

-- ------------------------------------------------------------
--  7. Students carry the plan they were sold
-- ------------------------------------------------------------
ALTER TABLE students
    ADD COLUMN plan_code VARCHAR(40) NULL AFTER fee_slab;

-- ------------------------------------------------------------
--  Verification
-- ------------------------------------------------------------
-- SELECT mode, COUNT(*) FROM fee_plans GROUP BY mode;
-- SELECT code, programme, batch_type, duration_years, registration_fee, course_fee,
--        installments, pp_pattern, due_months FROM fee_plans ORDER BY sort_order;
-- Every pp_pattern must sum to 100:
-- SELECT code, pp_pattern FROM fee_plans;
