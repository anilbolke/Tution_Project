-- ============================================================
--  Lead Stage + Lead Sub Stage (dependent dropdowns)
--
--  The client's reference screens carry a Lead Stage whose Sub Stage list is
--  rebuilt from the chosen Stage. Both become controlled lists here rather than
--  hardcoded <option>s, so the institute can add a stage or reword a sub stage
--  without a code change - the same way lead_sources already works.
--
--  RELATIONSHIP TO THE EXISTING FIELDS
--
--  `priority` (Hot/Warm/Cold) is the same idea as the first three stages, so on
--  the form Lead Stage REPLACES the Lead Priority dropdown. The column is NOT
--  dropped and NOT abandoned: the leads list filter, the counsellor dashboard
--  and the follow-up screen all read it, so saving a lead whose stage is
--  Hot/Warm/Cold keeps `priority` in step. Existing values migrate across
--  unchanged.
--
--  `status` is left alone. The whole sales module is built on it - reminder
--  queues, all eight reports, the conversion funnel and the scholarship-exam
--  handoff (RESULT_DECLARED / CONVERTED) - and Stage is a different axis:
--  status is where the enquiry has got to, stage is how warm it is.
--
--  RUN ONCE.
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

ALTER TABLE inquiries
  ADD COLUMN lead_stage VARCHAR(60) NULL AFTER priority;

CREATE INDEX idx_inq_stage ON inquiries(lead_stage, lead_sub_stage);

-- ── stages, in the order the client's dropdown shows them ──
INSERT INTO lead_stages (name, sort_order) VALUES
  ('Hot', 10), ('Warm', 20), ('Cold', 30), ('Lost', 40), ('CPA Registered', 50),
  ('Junk Lead', 60), ('Direct Enrolled', 70), ('Fresh', 80), ('Enquiry', 90),
  ('After Board', 100)
AS new ON DUPLICATE KEY UPDATE sort_order = new.sort_order;

-- ── sub stages, exactly as shown on the client's screens ──
-- Junk Lead, Direct Enrolled, Fresh, Enquiry and After Board have no sub stages
-- here because no screen was supplied for them. Sub Stage is therefore optional,
-- and adding their lists later is an INSERT, not a code change.
INSERT INTO lead_sub_stages (stage_id, name, sort_order)
SELECT s.stage_id, v.name, v.sort_order FROM lead_stages s JOIN (
  SELECT 'Hot' stage, 'Student Will visit the center within a week' name, 10 sort_order UNION ALL
  SELECT 'Hot', 'Student has confirmed that he will take admission', 20 UNION ALL

  SELECT 'Warm', 'In Conversation', 10 UNION ALL
  SELECT 'Warm', 'Follow Up', 20 UNION ALL

  SELECT 'Cold', 'After Board Exam', 10 UNION ALL
  SELECT 'Cold', 'Not sure', 20 UNION ALL
  SELECT 'Cold', 'Join Later', 30 UNION ALL
  SELECT 'Cold', 'Busy', 40 UNION ALL
  SELECT 'Cold', 'Will Discuss', 50 UNION ALL
  SELECT 'Cold', 'Call Later', 60 UNION ALL
  SELECT 'Cold', 'Not Decided', 70 UNION ALL

  SELECT 'Lost', 'Joined Somewhere Else', 10 UNION ALL
  SELECT 'Lost', 'Dropped The Plan', 20 UNION ALL
  SELECT 'Lost', 'Financial Issues', 30 UNION ALL
  SELECT 'Lost', 'Time Constraint', 40 UNION ALL
  SELECT 'Lost', 'Other Career', 50 UNION ALL
  SELECT 'Lost', 'Not Connected', 60 UNION ALL
  SELECT 'Lost', 'Not Responding', 70 UNION ALL
  SELECT 'Lost', 'Duplicate Leads', 80 UNION ALL

  SELECT 'CPA Registered', 'Absent (CPA Exam)', 10 UNION ALL
  SELECT 'CPA Registered', 'Hot', 20 UNION ALL
  SELECT 'CPA Registered', 'Warm', 30 UNION ALL
  SELECT 'CPA Registered', 'Cold', 40 UNION ALL
  SELECT 'CPA Registered', 'Denied', 50 UNION ALL
  SELECT 'CPA Registered', 'Not Responding/ Not Connected', 60 UNION ALL
  SELECT 'CPA Registered', 'Lost', 70 UNION ALL
  SELECT 'CPA Registered', 'Enrolled', 80
) v ON v.stage = s.name
-- INSERT ... SELECT cannot take a row alias, so the update reads the derived
-- table directly; this keeps the statement re-runnable.
ON DUPLICATE KEY UPDATE sort_order = v.sort_order;

-- ── carry the existing Hot/Warm/Cold priorities onto the new stage ──
UPDATE inquiries SET lead_stage = priority WHERE lead_stage IS NULL;
