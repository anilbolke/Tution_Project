-- ============================================================
--  Next Action / Next Follow-up: a date AND a time
--
--  Counsellors book the next touchpoint for a specific moment ("call back at
--  4:30"), but both columns were DATE, so the time was thrown away and the
--  work queue could only say "sometime today". Widening them to DATETIME lets
--  the queue be worked in the order the calls were actually promised.
--
--  Existing rows keep their date and land on 00:00 - MySQL fills the time in
--  itself. The UI shows midnight as a plain date, so those rows look exactly
--  as they did before rather than claiming a made-up 12:00 AM.
--
--  Everything that FILTERS on these columns compares the date part
--  (DATE(next_followup_date) BETWEEN ...), so the today / overdue / next-7-days
--  tiles keep counting whole days. reminder_log.due_date stays DATE on
--  purpose: one lead should still generate at most one WhatsApp reminder a
--  day, whatever time it is booked for.
--
--  RUN ONCE.
-- ============================================================

ALTER TABLE inquiries
  MODIFY COLUMN next_followup_date DATETIME NULL;

ALTER TABLE lead_followups
  MODIFY COLUMN next_action_date DATETIME NULL;
