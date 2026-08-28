-- ============================================================
--  Revenue & Admission targets, sliced by COURSE instead of by counsellor.
--
--  Same rules as counsellor_targets (see 2026-08-counsellor-targets.sql):
--    * revenue is BOOKED by default (student_fees.total_payable), COLLECTED
--      shown alongside.
--    * periods are QUARTERLY.
--    * a cancelled admission still counts IF the student paid something.
--
--  ATTRIBUTION differs from counsellor_targets: a student's course is their
--  fee_plans.programme (what they were actually sold via plan_code), which is
--  matched to courses.name — the same text the admission form's course
--  dropdown and this table both use. Verified 1:1 against the live DB.
--
--  RUN ONCE.
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
    -- one target per course per period, so a mid-quarter revision edits the row
    UNIQUE KEY uq_course_target (course_id, period_type, period_start),
    INDEX (period_start, period_end),
    CONSTRAINT fk_course_target_course FOREIGN KEY (course_id)
        REFERENCES courses(course_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
