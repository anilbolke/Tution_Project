-- ============================================================
--  HAMSE 10-08-2026 — the ten candidates from the scanned
--  answer-sheet PDF (Requirement/Broucher/10-08-2026_0001.pdf)
--
--  Names, parent mobiles, board and booklet code are transcribed
--  from the sheets themselves. School name and exam centre were
--  left blank on every sheet, so they stay NULL rather than being
--  invented.
--
--  roll_kind = 'LEGACY' ON PURPOSE. Nine of the ten roll numbers
--  fail the Damm check digit (only 123459 passes) because they were
--  filled in by hand rather than issued by this system. LEGACY is
--  exactly the exemption for hand-issued numbers, and without it the
--  scanner refuses every sheet as BAD_CHECKSUM. A real exam prints
--  the roll on the hall ticket, so this cannot arise there.
--
--  The rolls also sit outside exam 5's block (1-2400). That is fine:
--  the block only governs numbers this system ISSUES, and roll_next
--  is deliberately not moved.
--
--  Run once:
--    mysql -u root -p tuition_db < 2026-08-hamse-specimen-candidates.sql
-- ============================================================

START TRANSACTION;

-- One lead per candidate. mobile is NOT NULL and the sheets carry only
-- the parent's number, so it goes in both columns - that is the number
-- the tuition actually has for the family.
INSERT INTO inquiries (full_name, mobile, parent_mobile, current_class, source, status)
VALUES
  ('Adarsh Balasaheb Mane',      '8767419903', '8767419903', '10TH', 'Scholarship Exam', 'NEW'),
  ('Prajwal Khushalrao Shimple', '9665112043', '9665112043', '10TH', 'Scholarship Exam', 'NEW'),
  ('Vishal Pralhad Chame',       '9767400534', '9767400534', '10TH', 'Scholarship Exam', 'NEW'),
  ('Anjali Navnath Pawar',       '8208111060', '8208111060', '10TH', 'Scholarship Exam', 'NEW'),
  ('Sanskruti Ganesh Dhule',     '7798400699', '7798400699', '10TH', 'Scholarship Exam', 'NEW'),
  ('Shakib Mehboob Shaikh',      '9881090038', '9881090038', '10TH', 'Scholarship Exam', 'NEW'),
  ('Aditya Gangaram Shinde',     '9689031260', '9689031260', '10TH', 'Scholarship Exam', 'NEW'),
  ('Pranjali Anil Puramwar',     '9405111146', '9405111146', '10TH', 'Scholarship Exam', 'NEW'),
  ('Smita Rameshwar Shinde',     '8530821693', '8530821693', '10TH', 'Scholarship Exam', 'NEW'),
  ('Samruddhi Vijay Gode',       '9421479881', '9421479881', '10TH', 'Scholarship Exam', 'NEW');

-- Each candidate is joined to its lead by the mobile number, which is
-- unique within this batch. Matching on name would break on the two
-- Shindes.
INSERT INTO exam_candidates (exam_id, inquiry_id, roll_no, roll_kind, booklet_code, status)
SELECT 5, i.inquiry_id, v.roll_no, 'LEGACY', v.booklet, 'REGISTERED'
  FROM (
    SELECT '123453' AS roll_no, 'A' AS booklet, '8767419903' AS mob UNION ALL
    SELECT '123452', 'A', '9665112043' UNION ALL
    SELECT '123451', 'B', '9767400534' UNION ALL
    SELECT '123450', 'B', '8208111060' UNION ALL
    SELECT '123454', 'C', '7798400699' UNION ALL
    SELECT '023456', 'C', '9881090038' UNION ALL
    SELECT '123457', 'C', '9689031260' UNION ALL
    SELECT '123458', 'D', '9405111146' UNION ALL
    SELECT '123459', 'D', '8530821693' UNION ALL
    SELECT '123456', 'A', '9421479881'
  ) v
  JOIN inquiries i ON i.mobile = v.mob AND i.source = 'Scholarship Exam';

COMMIT;

SELECT c.roll_no, c.roll_kind, c.booklet_code, c.status, i.full_name, i.parent_mobile
  FROM exam_candidates c JOIN inquiries i ON i.inquiry_id = c.inquiry_id
 WHERE c.exam_id = 5 AND c.roll_kind = 'LEGACY'
 ORDER BY c.roll_no;
