-- ============================================================
--  Creating and managing logins, and the trail for it
--
--  This is the most privilege-sensitive screen in the system: whoever can
--  create a login and pick its role can grant themselves anything. So every
--  change is recorded, and the recording is not optional.
--
--  WHY THERE IS NO DELETE
--
--  staff_attendance, staff_leave, staff_salary and salary_payslip all reference
--  users(user_id) ON DELETE CASCADE. Deleting an employee would therefore take
--  their attendance history, their approved leave and every payslip - including
--  paid ones that match money already out of the fund - with them, silently.
--  Accounts are switched OFF instead. is_active = 0 blocks the login and leaves
--  the history intact, which is the same reasoning the expense module uses for
--  voiding rather than deleting a voucher.
--
--  target_user_id is deliberately NOT a foreign key, matching fund_audit: if a
--  row is ever removed by hand the trail describing it has to survive.
--
--  RUN ONCE.
-- ============================================================

CREATE TABLE IF NOT EXISTS user_audit (
    audit_id        INT AUTO_INCREMENT PRIMARY KEY,
    target_user_id  INT NOT NULL,
    target_username VARCHAR(50)  NOT NULL,
    target_name     VARCHAR(100) NOT NULL,
    action          ENUM('CREATE','UPDATE','ROLE','PASSWORD','ENABLE','DISABLE') NOT NULL,
    -- what changed, in words: "COUNSELLOR to STAFF", "mobile updated"
    detail          VARCHAR(255),
    acted_by        VARCHAR(100),
    acted_by_id     INT NULL,
    acted_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ua_target (target_user_id, acted_at),
    INDEX idx_ua_when (acted_at),
    CONSTRAINT fk_ua_actor FOREIGN KEY (acted_by_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Accounts that already exist pre-date the trail; seed a CREATE for each so the
-- history does not open with an unexplained gap.
INSERT INTO user_audit (target_user_id, target_username, target_name, action, detail,
                        acted_by, acted_at)
SELECT u.user_id, u.username, u.full_name, 'CREATE',
       CONCAT('Recorded when the trail was added; role ', u.role),
       'system', u.created_at
  FROM users u
 WHERE NOT EXISTS (SELECT 1 FROM user_audit a
                    WHERE a.target_user_id = u.user_id AND a.action = 'CREATE');
