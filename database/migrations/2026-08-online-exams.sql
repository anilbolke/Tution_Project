-- ============================================================
--  Online MCQ exams — staff create an exam for one class, bulk-paste
--  questions in a pipe-separated format, and students of that class take
--  it from their own login (auto-scored, one attempt each).
--
--  Deliberately a SEPARATE table set from `exams`/`exam_candidates`, which
--  model the printed-sheet scholarship exams (roll blocks, booklets, OMR
--  templates) — none of that applies here. class_name is free text
--  matching students.class_name exactly, same convention as `materials`.
--
--  RUN ONCE.
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
    correct_answer   CHAR(1) NOT NULL,                 -- A / B / C / D
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
    total_marks      INT NOT NULL DEFAULT 0,           -- snapshot of the paper's max at submit time
    score            INT NOT NULL DEFAULT 0,
    status           ENUM('IN_PROGRESS','SUBMITTED') NOT NULL DEFAULT 'IN_PROGRESS',
    -- One attempt per student per exam — a re-sit is a new exam, not a second
    -- attempt at this one (same rule the institute chose for the scholarship
    -- exams' exam_candidates table).
    UNIQUE KEY uq_oea_student (online_exam_id, student_id),
    CONSTRAINT fk_oea_exam    FOREIGN KEY (online_exam_id) REFERENCES online_exams(online_exam_id) ON DELETE CASCADE,
    CONSTRAINT fk_oea_student FOREIGN KEY (student_id)     REFERENCES students(student_id)         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS online_exam_answers (
    answer_id        INT AUTO_INCREMENT PRIMARY KEY,
    attempt_id       INT NOT NULL,
    question_id      INT NOT NULL,
    selected_answer  CHAR(1) NULL,                     -- NULL = left blank
    is_correct       TINYINT(1) NOT NULL DEFAULT 0,
    marks_awarded    INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_oea2_question (attempt_id, question_id),
    CONSTRAINT fk_oea2_attempt  FOREIGN KEY (attempt_id)  REFERENCES online_exam_attempts(attempt_id)  ON DELETE CASCADE,
    CONSTRAINT fk_oea2_question FOREIGN KEY (question_id) REFERENCES online_exam_questions(question_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
