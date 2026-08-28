-- ============================================================
--  Revert of 2026-08-exam-candidate-retakes.sql.
--
--  Turns out retakes-via-re-import were never the actual requirement: each
--  student is uploaded once, one roll number is generated, and scoring at
--  scan time is keyed off that roll number. Restoring the original rule —
--  one exam_candidates row per (exam, person) — so a stray duplicate row is
--  rejected again instead of silently accepted as attempt 2.
--
--  RUN ONCE.
-- ============================================================

ALTER TABLE exam_candidates
  DROP INDEX uq_exam_person_attempt,
  ADD UNIQUE KEY uq_exam_person (exam_id, inquiry_id);
