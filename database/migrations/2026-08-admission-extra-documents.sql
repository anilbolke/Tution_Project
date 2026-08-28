-- ============================================================
--  Admission form: 4 extra document upload slots.
--
--  students already had photo_path + id_proof_path (one file each).
--  These are 4 more, generic slots (doc1..doc4) for whatever else
--  the family brings at admission — no fixed label, so counsellors
--  are not blocked waiting on a specific document type to be added.
--
--  Run once:
--    mysql -u root -p tuition_db < 2026-08-admission-extra-documents.sql
-- ============================================================

ALTER TABLE students
    ADD COLUMN doc1_path VARCHAR(255) AFTER id_proof_path,
    ADD COLUMN doc2_path VARCHAR(255) AFTER doc1_path,
    ADD COLUMN doc3_path VARCHAR(255) AFTER doc2_path,
    ADD COLUMN doc4_path VARCHAR(255) AFTER doc3_path;
