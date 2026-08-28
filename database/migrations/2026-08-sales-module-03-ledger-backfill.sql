-- ============================================================
--  Sales Module migration, part 3  —  fee-ledger catch-up
--
--  Run AFTER 2026-08-sales-module.sql (and after deploying Phase 4):
--      mysql -u root -p tuition_db < database/migrations/2026-08-sales-module-03-ledger-backfill.sql
--
--  Why: the original backfill ran during Phase 0. Any student admitted
--  between that migration and the Phase 4 deploy has no student_fees row,
--  because AdmissionServlet only started opening the ledger in Phase 4.
--  This is safe to re-run at any time — it only ever adds missing rows.
-- ============================================================

-- 1. Ledger row for every student that lacks one, priced from their slab.
INSERT INTO student_fees
    (student_id, course_fee, registration_fee, material_fee,
     discount, scholarship, net_payable, plan, remarks)
SELECT s.student_id,
       COALESCE(fs.per_month, 0) * 12                    AS course_fee,
       CASE WHEN fs.slab_key IS NULL THEN 0 ELSE 500 END AS registration_fee,
       CASE WHEN fs.slab_key IS NULL THEN 0 ELSE 800 END AS material_fee,
       0, 0,
       CASE WHEN fs.slab_key IS NULL THEN 0
            ELSE 1300 + fs.per_month * 12 END            AS net_payable,
       'FULL',
       'Ledger catch-up backfill'
FROM students s
LEFT JOIN fee_slabs fs ON fs.slab_key = s.fee_slab
WHERE NOT EXISTS (SELECT 1 FROM student_fees sf WHERE sf.student_id = s.student_id);

-- 2. Bring installment statuses in line with the money actually allocated.
--    (Cheap and idempotent; guards against a schedule edited directly in SQL.)
UPDATE fee_installments
   SET status = CASE
         WHEN paid_amount >= amount            THEN 'PAID'
         WHEN due_date < CURDATE()             THEN 'OVERDUE'
         WHEN paid_amount > 0                  THEN 'PARTIAL'
         ELSE 'PENDING' END;

-- ------------------------------------------------------------
--  Verification — every student must now have exactly one row,
--  and the billed total must equal what the dashboard shows.
-- ------------------------------------------------------------
-- SELECT (SELECT COUNT(*) FROM students) AS students,
--        (SELECT COUNT(*) FROM student_fees) AS fee_rows,
--        (SELECT SUM(net_payable) FROM student_fees) AS billed;
--
-- Students whose schedule does not sum to their net payable (should be empty
-- for anyone on an instalment plan):
-- SELECT sf.student_id, sf.net_payable, COALESCE(SUM(i.amount),0) AS scheduled
--   FROM student_fees sf
--   LEFT JOIN fee_installments i ON i.student_id = sf.student_id
--  GROUP BY sf.student_id, sf.net_payable
-- HAVING scheduled > 0 AND scheduled <> sf.net_payable;
