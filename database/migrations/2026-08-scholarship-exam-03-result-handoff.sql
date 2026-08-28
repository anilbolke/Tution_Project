-- ============================================================
--  Scholarship Exam module - Phase 6
--  Publishing a result hands the candidate back to the sales pipeline.
--
--  Two additions to `inquiries`:
--
--  1. A RESULT_DECLARED stage. A candidate who has sat the exam and has a
--     scholarship on offer is a different sales proposition from a cold lead,
--     and the counsellor needs to see that at a glance in the existing pipeline
--     rather than in a separate screen.
--
--  2. scholarship_pct, carried on the lead itself. The exam result lives against
--     a SITTING (exam_candidates -> exam_results); the admission form works from
--     the LEAD. Copying the winning percentage onto the lead at publication is
--     what lets the fee plan pre-fill a concession without the admission flow
--     needing to know anything about exams.
--
--  RUN ONCE. A second run fails on the duplicate column, before anything else.
-- ============================================================

ALTER TABLE inquiries
  MODIFY status ENUM('NEW','CONTACTED','INTERESTED','DEMO_PENDING','DEMO_COMPLETED',
                     'FOLLOWUP_REQUIRED','RESULT_DECLARED','CONVERTED','NOT_INTERESTED','LOST')
                NOT NULL DEFAULT 'NEW';

ALTER TABLE inquiries
  ADD COLUMN scholarship_pct  DECIMAL(5,2) NULL,
  ADD COLUMN scholarship_note VARCHAR(200) NULL;   -- which rule won, so the figure is explainable

-- Results are published in one action per exam; this records that it happened.
ALTER TABLE exams
  ADD COLUMN published_at TIMESTAMP NULL,
  ADD COLUMN published_by VARCHAR(100) NULL;
