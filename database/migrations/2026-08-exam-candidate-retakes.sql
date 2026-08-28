-- ============================================================
--  Allow a student to sit the SAME exam more than once (a retake).
--
--  Previously exam_candidates had UNIQUE KEY uq_exam_person (exam_id,
--  inquiry_id) — one registration per person per exam, full stop. The
--  attempt_no column already existed but was pointless: any second row for
--  the same exam+person was rejected before attempt_no was even looked at
--  (ExamImportDAO.isAlreadyRegistered checked exam_id+inquiry_id only).
--
--  This widens the unique key to (exam_id, inquiry_id, attempt_no), so a
--  repeat import row for the same student gets its own roll number and its
--  own result as long as its "EXAM Attempt" number differs from any attempt
--  already on file — an exact repeat of the same attempt number is still
--  rejected as a genuine duplicate.
--
--  RUN ONCE.
-- ============================================================

ALTER TABLE exam_candidates
  DROP INDEX uq_exam_person,
  ADD UNIQUE KEY uq_exam_person_attempt (exam_id, inquiry_id, attempt_no);
