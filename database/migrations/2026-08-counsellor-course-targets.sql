-- ============================================================
--  Per-counsellor targets, broken down by course.
--
--  Distinct from counsellor_targets (one overall number per counsellor) and
--  from course_targets (one number per course, institute-wide) — this is the
--  cross of the two: "how much of THIS counsellor's target should come from
--  THAT course". The UI picks a counsellor first, then shows one row per
--  active course for them, same as counsellor_targets/course_targets do.
--
--  Same institute rules as the other two target tables (cancelled-but-paid
--  still counts, revenue is BOOKED by default, quarterly periods). Attribution
--  is s.counsellor_id = ? AND fee_plans.programme = courses.name, same course
--  match as course_targets.
--
--  RUN ONCE.
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
    -- one target per counsellor+course+period, so a mid-quarter revision edits the row
    UNIQUE KEY uq_counsellor_course_target (counsellor_id, course_id, period_type, period_start),
    INDEX (period_start, period_end),
    CONSTRAINT fk_cct_user FOREIGN KEY (counsellor_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cct_course FOREIGN KEY (course_id)
        REFERENCES courses(course_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
