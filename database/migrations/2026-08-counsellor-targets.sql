-- ============================================================
--  Revenue & Admission targets for counsellors
--
--  Decisions taken with the institute:
--    * revenue is BOOKED - the value of admissions closed in the period
--      (student_fees.total_payable), not cash received. A counsellor controls
--      whether the admission closes; they do not control the instalment plan.
--    * periods are QUARTERLY.
--    * attribution is LIVE from students.counsellor_id, so the figures always
--      agree with the existing Counsellor Performance report.
--    * admins get targets too (HR sets them).
--    * a cancelled admission still counts IF the student paid something; an
--      admission that was never paid for at all does not.
--
--  RUN ONCE.
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
