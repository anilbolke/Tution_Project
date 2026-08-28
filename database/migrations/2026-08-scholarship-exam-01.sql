-- ============================================================
--  Scholarship Exam module (HOSE: HAMSE / HACKSE / HAT)
--  Phase 0 — schema
--
--  Flow: school student list (Excel) -> exam candidates with a generated
--  6-digit roll number -> offline 60-question OMR paper -> scan -> score ->
--  scholarship -> lead -> admission.
--
--  RUN ONCE. MySQL 8 has no "ALTER TABLE ... ADD COLUMN IF NOT EXISTS", so a
--  second run aborts on the very first statement with
--      ERROR 1060 Duplicate column name 'father_name'
--  before touching anything else. That failure IS the guard against double
--  application - it is safe, not destructive. The CREATE TABLE and INSERT
--  statements below are individually idempotent, so a run that failed partway
--  can be resumed by deleting the ALTERs that already succeeded.
--
--  Verify it has been applied:
--      SELECT COUNT(*) FROM information_schema.columns
--       WHERE table_schema = DATABASE() AND table_name = 'inquiries'
--         AND column_name = 'father_name';        -- 1 = applied
-- ============================================================

-- COLLATION: every table below is pinned to utf8mb4_unicode_ci to match the
-- core tables (exams, inquiries, students, omr_scans). The DATABASE default is
-- utf8mb4_0900_ai_ci, so a table created without an explicit COLLATE picks that
-- up instead, and any string/ENUM join back to a core table then dies with
--     ERROR 1267 Illegal mix of collations ... for operation '='
-- Do not drop the COLLATE clauses.

-- ------------------------------------------------------------
--  0. Lead columns the Excel template carries and we don't
--     (the 49-column "LEAD TEMPLATE FOR NEW ERP.xlsx")
-- ------------------------------------------------------------
-- The sheet splits parents into father/mother; inquiries has a single
-- parent_name/parent_mobile pair, which stays as the primary contact.
ALTER TABLE inquiries
  ADD COLUMN father_name     VARCHAR(100) NULL,
  ADD COLUMN father_mobile   VARCHAR(15)  NULL,
  ADD COLUMN mother_name     VARCHAR(100) NULL,
  ADD COLUMN mother_mobile   VARCHAR(15)  NULL,
  ADD COLUMN district        VARCHAR(60)  NULL,
  ADD COLUMN state           VARCHAR(60)  NULL,
  ADD COLUMN academic_term   VARCHAR(40)  NULL,
  ADD COLUMN preferred_centre VARCHAR(80) NULL,
  ADD COLUMN stream          VARCHAR(40)  NULL,
  ADD COLUMN student_type    VARCHAR(40)  NULL,
  ADD COLUMN caste_category  VARCHAR(40)  NULL,
  ADD COLUMN sibling_detail  VARCHAR(255) NULL,
  ADD COLUMN current_tutor   VARCHAR(150) NULL,
  ADD COLUMN prev_class_pct  VARCHAR(20)  NULL,
  -- Exam fee is collected OUTSIDE this system; these two are reference only.
  ADD COLUMN utr_number      VARCHAR(60)  NULL,
  ADD COLUMN payment_status  VARCHAR(30)  NULL,
  ADD COLUMN heard_from      VARCHAR(120) NULL,
  ADD COLUMN lead_sub_stage  VARCHAR(60)  NULL;

CREATE INDEX idx_inq_school ON inquiries(school_name);

-- ------------------------------------------------------------
--  1. OMR sheet layouts
--
--  The reader's geometry used to live in `public static` fields on
--  OmrService, so only ONE sheet could ever work and two concurrent scans
--  shared the same mutable state. Layouts now live here, one row per
--  physical sheet, and are loaded per exam.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS omr_templates (
    template_id    INT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(80)  NOT NULL,
    blocks         INT          NOT NULL,            -- answer columns across the page
    rows_per_block INT          NOT NULL,            -- questions down each column
    total_questions INT AS (blocks * rows_per_block) STORED,
    roll_cols      INT          NOT NULL,            -- digits in the bubbled roll grid
    ring_ink       ENUM('RED','BLACK') NOT NULL DEFAULT 'BLACK',
    timing_edge    ENUM('LEFT','RIGHT','BOTH')       NOT NULL DEFAULT 'BOTH',
    geometry_json  MEDIUMTEXT   NOT NULL,            -- all fractional coordinates
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_tpl_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  2. Exam setup
-- ------------------------------------------------------------
ALTER TABLE exams
  ADD COLUMN exam_type       ENUM('INTERNAL','HAMSE','HACKSE','HAT') NOT NULL DEFAULT 'INTERNAL',
  ADD COLUMN template_id     INT NULL,
  ADD COLUMN total_questions INT NOT NULL DEFAULT 60,
  ADD COLUMN mark_correct    INT NOT NULL DEFAULT 4,
  ADD COLUMN mark_wrong      INT NOT NULL DEFAULT -1,   -- negative marking, per the sheet
  ADD COLUMN max_score       INT NOT NULL DEFAULT 240,
  ADD COLUMN roll_block_from INT NULL,                  -- inclusive 5-digit sequence block
  ADD COLUMN roll_block_to   INT NULL,
  ADD COLUMN roll_next       INT NULL,                  -- next unallocated sequence
  ADD COLUMN result_published TINYINT(1) NOT NULL DEFAULT 0,
  ADD CONSTRAINT fk_exam_tpl FOREIGN KEY (template_id) REFERENCES omr_templates(template_id);

CREATE INDEX idx_exam_type ON exams(exam_type, exam_date);

-- Question ranges -> subject. Fully configurable per exam: staff choose the
-- split (1-10 one subject, 11-30 another, ...). Nothing is hardcoded, so a
-- Medical paper (Phy/Chem/Bio) and an Engineering one (Phy/Chem/Maths) are
-- just different rows.
CREATE TABLE IF NOT EXISTS exam_subject_map (
    exam_id  INT         NOT NULL,
    subject  VARCHAR(40) NOT NULL,
    q_from   INT         NOT NULL,
    q_to     INT         NOT NULL,
    sort_order INT       NOT NULL DEFAULT 0,
    PRIMARY KEY (exam_id, subject),
    CONSTRAINT fk_esm_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The paper is shuffled into booklets A-D, so an exam has FOUR keys and the
-- right one is chosen from the booklet bubble read off each sheet.
CREATE TABLE IF NOT EXISTS exam_answer_keys (
    exam_id      INT     NOT NULL,
    booklet_code CHAR(1) NOT NULL,
    q_no         INT     NOT NULL,
    correct_opt  CHAR(1) NOT NULL,
    PRIMARY KEY (exam_id, booklet_code, q_no),
    CONSTRAINT fk_eak_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  3. Import batches — audit trail and undo for each Excel upload
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS import_batches (
    batch_id       INT AUTO_INCREMENT PRIMARY KEY,
    exam_id        INT          NOT NULL,
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

-- ------------------------------------------------------------
--  4. Candidates — one row per SITTING, not per person
--
--  The Excel's "EXAM Attempt" column confirms students re-sit: someone who
--  takes HAMSE in December and HACKSE in March needs two roll numbers and
--  two results against ONE inquiries row. So the roll number belongs here,
--  not on the person.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_candidates (
    candidate_id  INT AUTO_INCREMENT PRIMARY KEY,
    exam_id       INT      NOT NULL,
    inquiry_id    INT      NOT NULL,
    roll_no       CHAR(6)  NOT NULL,                 -- 5-digit sequence + Luhn check digit
    roll_kind     ENUM('GENERATED','LEGACY') NOT NULL DEFAULT 'GENERATED',
    booklet_code  CHAR(1)  NULL,                     -- expected; actual is read off the sheet
    exam_centre   VARCHAR(120),
    attempt_no    TINYINT  NOT NULL DEFAULT 1,
    status        ENUM('REGISTERED','APPEARED','ABSENT','RESULT_READY') NOT NULL DEFAULT 'REGISTERED',
    import_batch_id INT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Globally unique and never reused, so a sheet fed into the wrong exam is
    -- DETECTED rather than silently scored against whoever holds that number.
    UNIQUE KEY uq_roll (roll_no),
    UNIQUE KEY uq_exam_person (exam_id, inquiry_id),
    INDEX (exam_id, status),
    INDEX (import_batch_id),
    CONSTRAINT fk_ec_exam  FOREIGN KEY (exam_id)    REFERENCES exams(exam_id),
    CONSTRAINT fk_ec_inq   FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id),
    CONSTRAINT fk_ec_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  5. Results
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_results (
    result_id       INT AUTO_INCREMENT PRIMARY KEY,
    candidate_id    INT NOT NULL,
    scan_id         INT NULL,
    booklet_read    CHAR(1) NULL,
    attempted       INT NOT NULL DEFAULT 0,
    correct         INT NOT NULL DEFAULT 0,
    wrong           INT NOT NULL DEFAULT 0,
    blank           INT NOT NULL DEFAULT 0,
    raw_score       INT NOT NULL DEFAULT 0,           -- signed: negative marking can go below 0
    max_score       INT NOT NULL DEFAULT 240,
    percentage      DECIMAL(5,2) NOT NULL DEFAULT 0,  -- max(0, raw) / max * 100
    scholarship_pct DECIMAL(5,2) NULL,
    awarded_rule_id INT NULL,                         -- WHICH rule won, so the figure is explainable
    rank_overall    INT NULL,
    verified_by     VARCHAR(100),
    verified_at     TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_res_candidate (candidate_id),
    CONSTRAINT fk_er_cand FOREIGN KEY (candidate_id) REFERENCES exam_candidates(candidate_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_result_subjects (
    result_id INT         NOT NULL,
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

-- ------------------------------------------------------------
--  6. Scholarship rules (brochure pages 9-10)
--
--  FORMULA: scholarship = LEAST(cap_pct, factor * score percentage)
--           HAMSE  factor 1.00 cap 100   (equal to score %)
--           HACKSE factor 0.90 cap  90   (90% of score %)
--  FIXED:   a flat percentage for a named criterion, claimed with proof.
--
--  p10: "A student can avail any ONE of the scholarship in Category A & B,
--  whichever is higher." Awards take the MAXIMUM, never the sum.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship_rules (
    rule_id     INT AUTO_INCREMENT PRIMARY KEY,
    category    ENUM('A','B') NOT NULL,
    sub_group   VARCHAR(60)  NOT NULL,               -- HOSE / SCHOOL / EXTRAORDINARY / NEET / OTHER
    criteria    VARCHAR(200) NOT NULL,
    rule_kind   ENUM('FORMULA','FIXED') NOT NULL DEFAULT 'FIXED',
    exam_type   ENUM('HAMSE','HACKSE','HAT') NULL,   -- FORMULA rules only
    factor      DECIMAL(5,4) NULL,                   -- FORMULA: multiplier on score %
    cap_pct     DECIMAL(5,2) NULL,                   -- FORMULA: upper bound
    fixed_pct   DECIMAL(5,2) NULL,                   -- FIXED: the flat award
    needs_proof TINYINT(1) NOT NULL DEFAULT 1,
    is_active   TINYINT(1) NOT NULL DEFAULT 1,
    sort_order  INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_rule (category, sub_group, criteria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A claim against a FIXED rule. Proof must be verified before it can win.
CREATE TABLE IF NOT EXISTS scholarship_claims (
    claim_id     INT AUTO_INCREMENT PRIMARY KEY,
    inquiry_id   INT NOT NULL,
    rule_id      INT NOT NULL,
    status       ENUM('CLAIMED','VERIFIED','REJECTED') NOT NULL DEFAULT 'CLAIMED',
    proof_path   VARCHAR(255),
    remarks      VARCHAR(255),
    verified_by  VARCHAR(100),
    verified_at  TIMESTAMP NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_claim (inquiry_id, rule_id),
    INDEX (inquiry_id, status),
    CONSTRAINT fk_sc_inq  FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_rule FOREIGN KEY (rule_id)    REFERENCES scholarship_rules(rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE exam_results
  ADD CONSTRAINT fk_er_rule FOREIGN KEY (awarded_rule_id) REFERENCES scholarship_rules(rule_id);

-- ------------------------------------------------------------
--  7. Link the existing scan log to an exam and a candidate
--
--  omr_scans.candidate_id stays NULLABLE on purpose: an unmatched sheet must
--  still be stored so it can be reviewed and re-matched, never lost.
-- ------------------------------------------------------------
ALTER TABLE omr_scans
  ADD COLUMN exam_id      INT NULL,
  ADD COLUMN candidate_id INT NULL,
  ADD COLUMN roll_read    VARCHAR(10) NULL,
  ADD COLUMN booklet_read CHAR(1) NULL,
  ADD COLUMN match_status ENUM('MATCHED','NO_ROLL','BAD_CHECKSUM','NOT_IN_EXAM','DUPLICATE','NO_BOOKLET') NULL,
  ADD COLUMN low_registration TINYINT(1) NOT NULL DEFAULT 0,  -- timing/column detection fell back
  ADD COLUMN image_path   VARCHAR(255) NULL,
  ADD INDEX idx_scan_exam (exam_id, match_status),
  ADD CONSTRAINT fk_os_exam FOREIGN KEY (exam_id)      REFERENCES exams(exam_id),
  ADD CONSTRAINT fk_os_cand FOREIGN KEY (candidate_id) REFERENCES exam_candidates(candidate_id);

-- ============================================================
--  SEED DATA
-- ============================================================

-- The two sheet layouts. Geometry is fractions of image width/height so it
-- scales with scan resolution.
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

-- Category A1 — HOSE performance. THE exam-driven rule.
INSERT INTO scholarship_rules
  (category, sub_group, criteria, rule_kind, exam_type, factor, cap_pct, fixed_pct, needs_proof, sort_order)
VALUES
('A','HOSE','HAMSE score percentage',  'FORMULA','HAMSE', 1.0000, 100.00, NULL, 0, 10),
('A','HOSE','HACKSE score percentage', 'FORMULA','HACKSE',0.9000,  90.00, NULL, 0, 11),

-- Category A2 — school exam score
('A','SCHOOL','Board score above 97% or State/Science/National topper','FIXED',NULL,NULL,NULL,100.00,1,20),
('A','SCHOOL','A1 grade or above 95% in board',                        'FIXED',NULL,NULL,NULL, 40.00,1,21),
('A','SCHOOL','A1 in Science, Maths and English or above 90% in same',  'FIXED',NULL,NULL,NULL, 30.00,1,22),
('A','SCHOOL','A1 in Maths and A2 in Science',                          'FIXED',NULL,NULL,NULL, 25.00,1,23),
('A','SCHOOL','A2 in all subjects',                                     'FIXED',NULL,NULL,NULL, 20.00,1,24),

-- Category A3 — extraordinary performance
('A','EXTRAORDINARY','International olympiad or merit exam (rank 1-10)','FIXED',NULL,NULL,NULL,100.00,1,30),
('A','EXTRAORDINARY','International olympiad or merit exam (below rank 10)','FIXED',NULL,NULL,NULL,50.00,1,31),
('A','EXTRAORDINARY','State govt merit scholarship qualified',          'FIXED',NULL,NULL,NULL, 50.00,1,32),
('A','EXTRAORDINARY','National level olympiad or merit test qualified', 'FIXED',NULL,NULL,NULL, 50.00,1,33),

-- Category A4 — previous year NEET score
('A','NEET','Within 50 marks of cutoff',      'FIXED',NULL,NULL,NULL, 80.00,1,40),
('A','NEET','51 to 100 marks below cutoff',   'FIXED',NULL,NULL,NULL, 70.00,1,41),
('A','NEET','101 to 150 marks below cutoff',  'FIXED',NULL,NULL,NULL, 50.00,1,42),
('A','NEET','151 to 200 marks below cutoff',  'FIXED',NULL,NULL,NULL, 30.00,1,43),
('A','NEET','201 to 250 marks below cutoff',  'FIXED',NULL,NULL,NULL, 20.00,1,44),

-- Category B — other than academic performance
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
