package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tution.model.ImportRow;
import com.tution.util.DBConnection;
import com.tution.util.RollNumber;

/**
 * Persistence for the school student-list import.
 *
 * Unlike the other DAOs in this project, which open a connection per method,
 * everything here runs on ONE connection inside ONE transaction and uses JDBC
 * batching. A school list can be thousands of rows: at a connection and a
 * round-trip per row it would take minutes, and a half-finished import would
 * leave candidates without leads.
 */
public class ExamImportDAO {

    /** How many statements to accumulate before hitting the server. */
    private static final int BATCH = 500;

    /** Outcome of a commit. */
    public static class Result {
        public int batchId;
        public int imported;        // new leads created
        public int linked;          // existing leads registered for the exam
        public int skipped;         // already registered for THIS exam
        public int rejected;
        public final List<String> notes = new ArrayList<>();
    }

    /**
     * Existing leads keyed by mobile, for bulk duplicate detection.
     * Returns two maps: [0] by student mobile, [1] by parent mobile.
     */
    public List<Map<String, Integer>> findExistingByMobiles(Set<String> mobiles) throws SQLException {
        Map<String, Integer> byStudent = new HashMap<>();
        Map<String, Integer> byParent  = new HashMap<>();
        List<Map<String, Integer>> out = new ArrayList<>();
        out.add(byStudent);
        out.add(byParent);
        if (mobiles == null || mobiles.isEmpty()) return out;

        StringBuilder in = new StringBuilder();
        for (int i = 0; i < mobiles.size(); i++) in.append(i == 0 ? "?" : ",?");
        String sql = "SELECT inquiry_id, mobile, parent_mobile FROM inquiries "
                   + "WHERE mobile IN (" + in + ") OR parent_mobile IN (" + in + ")";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            for (String m : mobiles) ps.setString(i++, m);
            for (String m : mobiles) ps.setString(i++, m);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("inquiry_id");
                    String sm = rs.getString("mobile");
                    String pm = rs.getString("parent_mobile");
                    if (sm != null && !sm.isEmpty()) byStudent.putIfAbsent(sm, id);
                    if (pm != null && !pm.isEmpty()) byParent.putIfAbsent(pm, id);
                }
            }
        }
        return out;
    }

    /**
     * Writes the whole import in one transaction: lead rows, candidate rows with
     * freshly allocated roll numbers, and the batch audit record.
     *
     * Rows already REJECTED are counted and skipped. Rows matching an existing
     * lead reuse it rather than creating a second person — the same child sent by
     * two schools, or sitting a second exam, must stay one lead.
     */
    public Result commit(int examId, String fileName, String schoolName,
                         String uploadedBy, List<ImportRow> rows) throws SQLException {
        Result res = new Result();
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            res.batchId = insertBatch(con, examId, fileName, schoolName, uploadedBy, rows);

            // Reserve the whole block of roll numbers up front, under a row lock,
            // so two staff importing at the same time cannot be handed the same
            // sequence. SELECT ... FOR UPDATE holds exams until we commit.
            int needed = 0;
            for (ImportRow r : rows) if (r.verdict != ImportRow.Verdict.REJECTED) needed++;
            int[] block = reserveRollBlock(con, examId, needed);
            int nextSeq = block[0];

            // Leads created earlier in THIS file, keyed by mobile. Without it a
            // student listed twice in one upload becomes two people with two roll
            // numbers: the row is flagged DUPLICATE in the preview but carries no
            // existing inquiry_id, so it would fall through to "insert a new lead",
            // and because that mints a fresh id the uq_exam_person constraint never
            // fires. The preview would have promised one thing and done another.
            Map<String, Integer> createdInThisFile = new HashMap<>();
            /** People already queued for a candidate row in this transaction. */
            Set<Integer> queuedPeople = new HashSet<>();

            List<ImportRow> toInsert = new ArrayList<>();
            for (ImportRow r : rows) {
                if (r.verdict == ImportRow.Verdict.REJECTED) { res.rejected++; continue; }

                Integer inquiryId = r.existingInquiryId;
                if (inquiryId == null) inquiryId = createdInThisFile.get(r.get("mobile"));

                if (inquiryId == null) {
                    inquiryId = insertLead(con, r, schoolName);
                    if (!r.get("mobile").isEmpty()) createdInThisFile.put(r.get("mobile"), inquiryId);
                    res.imported++;
                } else {
                    res.linked++;
                }
                r.existingInquiryId = inquiryId;

                // Registered already, or queued a moment ago by an earlier row of
                // this same file. The in-memory half matters: candidate inserts are
                // batched to the end of the transaction, so a second row for the
                // same person would pass the database check and only fail at
                // executeBatch on uq_exam_person, taking the whole import with it.
                if (!queuedPeople.add(inquiryId) || isAlreadyRegistered(con, examId, inquiryId)) {
                    res.skipped++;
                    res.notes.add("Row " + r.rowNo + " (" + r.getName()
                                + ") is already registered for this exam - left as it was.");
                    continue;
                }
                // A 6-digit number supplied by the school predates this system and
                // will not satisfy the check digit, so it is kept as LEGACY rather
                // than being rewritten or rejected.
                if (r.get("roll_no").length() == 6) {
                    r.rollNo = r.get("roll_no");
                } else {
                    r.rollNo = RollNumber.forSequence(nextSeq++);
                }
                toInsert.add(r);
            }

            insertCandidates(con, examId, res.batchId, toInsert);
            saveRollNext(con, examId, nextSeq);
            updateBatchCounts(con, res);

            con.commit();
            return res;
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    /* ─── internals ─── */

    private int insertBatch(Connection con, int examId, String fileName, String schoolName,
                            String uploadedBy, List<ImportRow> rows) throws SQLException {
        String sql = "INSERT INTO import_batches (exam_id, file_name, school_name, rows_total, uploaded_by) "
                   + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, examId);
            ps.setString(2, fileName);
            ps.setString(3, schoolName);
            ps.setInt(4, rows.size());
            ps.setString(5, uploadedBy);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Locks the exam row and hands back [firstSequence, lastAllowed], failing
     * loudly if the exam's block cannot cover the import rather than silently
     * running past it into another exam's numbers.
     */
    private int[] reserveRollBlock(Connection con, int examId, int needed) throws SQLException {
        String sql = "SELECT roll_block_from, roll_block_to, roll_next FROM exams WHERE exam_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Exam " + examId + " not found.");
                int from = rs.getInt("roll_block_from");
                int to   = rs.getInt("roll_block_to");
                int next = rs.getInt("roll_next");
                if (rs.wasNull() || next < from) next = from;
                if (from <= 0 || to <= 0) {
                    throw new SQLException("This exam has no roll-number block assigned. "
                                         + "Set one on the exam before importing students.");
                }
                if (next + needed - 1 > to) {
                    throw new SQLException("The exam's roll-number block (" + from + "-" + to + ") has only "
                                         + Math.max(0, to - next + 1) + " numbers left, but this file needs "
                                         + needed + ". Widen the block and upload again.");
                }
                return new int[] { next, to };
            }
        }
    }

    private void saveRollNext(Connection con, int examId, int nextSeq) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE exams SET roll_next = ? WHERE exam_id = ?")) {
            ps.setInt(1, nextSeq);
            ps.setInt(2, examId);
            ps.executeUpdate();
        }
    }

    private boolean isAlreadyRegistered(Connection con, int examId, int inquiryId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM exam_candidates WHERE exam_id = ? AND inquiry_id = ?")) {
            ps.setInt(1, examId);
            ps.setInt(2, inquiryId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private int insertLead(Connection con, ImportRow r, String schoolFallback) throws SQLException {
        String sql = "INSERT INTO inquiries ("
            + "full_name, mobile, parent_name, parent_mobile, father_name, father_mobile, "
            + "mother_name, mother_mobile, email, dob, gender, city, district, state, address, "
            + "current_class, school_name, board, prev_class_pct, current_tutor, student_type, "
            + "caste_category, sibling_detail, academic_term, preferred_centre, stream, "
            + "course_name, source, heard_from, lead_sub_stage, counsellor_remarks, "
            + "utr_number, payment_status, status, priority"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEW','Warm')";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, r.get("full_name"));
            ps.setString(i++, r.get("mobile"));
            // parent_name/parent_mobile stay the primary parent contact; the sheet's
            // father column is the usual source, mother is the fallback.
            ps.setString(i++, first(r.get("father_name"), r.get("mother_name")));
            ps.setString(i++, first(r.get("father_mobile"), r.get("mother_mobile")));
            setNullable(ps, i++, r.get("father_name"));
            setNullable(ps, i++, r.get("father_mobile"));
            setNullable(ps, i++, r.get("mother_name"));
            setNullable(ps, i++, r.get("mother_mobile"));
            setNullable(ps, i++, r.get("email"));
            setDate(ps,     i++, r.get("dob"));
            setNullable(ps, i++, r.get("gender"));
            setNullable(ps, i++, r.get("city"));
            setNullable(ps, i++, r.get("district"));
            setNullable(ps, i++, r.get("state"));
            setNullable(ps, i++, r.get("address"));
            setNullable(ps, i++, r.get("current_class"));
            setNullable(ps, i++, first(r.get("school_name"), schoolFallback));
            setNullable(ps, i++, r.get("board"));
            setNullable(ps, i++, r.get("prev_class_pct"));
            setNullable(ps, i++, r.get("current_tutor"));
            setNullable(ps, i++, r.get("student_type"));
            setNullable(ps, i++, r.get("caste_category"));
            setNullable(ps, i++, r.get("sibling_detail"));
            setNullable(ps, i++, r.get("academic_term"));
            setNullable(ps, i++, r.get("preferred_centre"));
            setNullable(ps, i++, r.get("stream"));
            setNullable(ps, i++, first(r.get("course_interest"), r.get("course_name")));
            setNullable(ps, i++, r.get("source"));
            setNullable(ps, i++, r.get("heard_from"));
            setNullable(ps, i++, r.get("lead_sub_stage"));
            setNullable(ps, i++, joinRemarks(r));
            setNullable(ps, i++, r.get("utr_number"));
            setNullable(ps, i++, r.get("payment_status"));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void insertCandidates(Connection con, int examId, int batchId, List<ImportRow> rows) throws SQLException {
        if (rows.isEmpty()) return;
        String sql = "INSERT INTO exam_candidates "
                   + "(exam_id, inquiry_id, roll_no, roll_kind, exam_centre, attempt_no, import_batch_id) "
                   + "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int n = 0;
            for (ImportRow r : rows) {
                ps.setInt(1, examId);
                ps.setInt(2, r.existingInquiryId);
                ps.setString(3, r.rollNo);
                ps.setString(4, RollNumber.isValid(r.rollNo) ? "GENERATED" : "LEGACY");
                setNullable(ps, 5, r.get("preferred_centre"));
                ps.setInt(6, parseIntOr(r.get("attempt_no"), 1));
                ps.setInt(7, batchId);
                ps.addBatch();
                if (++n % BATCH == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    private void updateBatchCounts(Connection con, Result res) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE import_batches SET rows_imported = ?, rows_duplicate = ?, rows_rejected = ? "
              + "WHERE batch_id = ?")) {
            ps.setInt(1, res.imported);
            ps.setInt(2, res.linked);
            ps.setInt(3, res.rejected);
            ps.setInt(4, res.batchId);
            ps.executeUpdate();
        }
    }

    /* ─── helpers, matching the shape used by the other DAOs ─── */

    private void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(idx, java.sql.Types.VARCHAR);
        else ps.setString(idx, v.trim());
    }
    private void setDate(PreparedStatement ps, int idx, String iso) throws SQLException {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) ps.setNull(idx, java.sql.Types.DATE);
        else ps.setString(idx, iso);
    }
    private String first(String a, String b) {
        return (a != null && !a.trim().isEmpty()) ? a.trim() : (b == null ? "" : b.trim());
    }
    private String joinRemarks(ImportRow r) {
        String a = r.get("counsellor_remarks"), b = r.get("other_remark");
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " | " + b;
    }
    private int parseIntOr(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
}
