package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.ExamCandidate;
import com.tution.util.DBConnection;
import com.tution.util.RollNumber;

/** Data-access for exam candidates — the roll-number registry. */
public class CandidateDAO {

    /**
     * Candidate plus the lead and exam details a roll list or hall ticket needs,
     * so those screens stay one query instead of one per row.
     */
    private static final String SELECT =
          "SELECT c.candidate_id, c.exam_id, c.inquiry_id, c.roll_no, c.roll_kind, c.booklet_code, "
        + "       c.exam_centre, c.attempt_no, c.status, c.import_batch_id, c.created_at, "
        + "       i.full_name, i.mobile, i.parent_mobile, i.school_name, i.current_class, i.board, i.city, "
        + "       e.exam_name, e.exam_date, e.exam_type "
        + "  FROM exam_candidates c "
        + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
        + "  JOIN exams     e ON e.exam_id    = c.exam_id ";

    /**
     * The registry, filtered. Every filter is optional; blank means "all".
     * {@code q} matches the roll number, the student's name, or either mobile.
     */
    public List<ExamCandidate> find(int examId, String school, String centre,
                                    String status, String q) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE c.exam_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(examId);

        if (notBlank(school)) { sql.append(" AND i.school_name = ? ");  args.add(school.trim()); }
        if (notBlank(centre)) { sql.append(" AND c.exam_centre = ? ");  args.add(centre.trim()); }
        if (notBlank(status)) { sql.append(" AND c.status = ? ");       args.add(status.trim()); }
        if (notBlank(q)) {
            sql.append(" AND (c.roll_no = ? OR i.full_name LIKE ? OR i.mobile = ? OR i.parent_mobile = ?) ");
            String term = q.trim();
            args.add(term);
            args.add("%" + term + "%");
            args.add(term);
            args.add(term);
        }
        // Roll number is the printing and invigilation order, so sort by it.
        sql.append(" ORDER BY c.roll_no");

        List<ExamCandidate> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** A single candidate, or null. */
    public ExamCandidate findById(int candidateId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + " WHERE c.candidate_id = ?")) {
            ps.setInt(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Looks a roll number up WITHIN one exam. This is the lookup the scanner will
     * use, and it is deliberately an exact match scoped to the exam — unlike
     * StudentDAO.findByRoll, which matches a suffix against admission numbers and
     * can silently settle on the wrong student.
     */
    public ExamCandidate findByRoll(int examId, String rollNo) throws SQLException {
        if (rollNo == null || !rollNo.matches("\\d{6}")) return null;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     SELECT + " WHERE c.exam_id = ? AND c.roll_no = ?")) {
            ps.setInt(1, examId);
            ps.setString(2, rollNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Status counts for one exam, e.g. {REGISTERED=248, APPEARED=241, ABSENT=7}. */
    public Map<String, Integer> statusCounts(int examId) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        String sql = "SELECT status, COUNT(*) n FROM exam_candidates WHERE exam_id = ? GROUP BY status";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString("status"), rs.getInt("n"));
            }
        }
        return out;
    }

    /** Distinct schools with a candidate in this exam, for the filter dropdown. */
    public List<String> schools(int examId) throws SQLException {
        return distinct("SELECT DISTINCT i.school_name FROM exam_candidates c "
                      + "JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                      + "WHERE c.exam_id = ? AND i.school_name IS NOT NULL AND i.school_name <> '' "
                      + "ORDER BY i.school_name", examId);
    }

    /** Distinct exam centres in this exam, for the filter dropdown. */
    public List<String> centres(int examId) throws SQLException {
        return distinct("SELECT DISTINCT exam_centre FROM exam_candidates "
                      + "WHERE exam_id = ? AND exam_centre IS NOT NULL AND exam_centre <> '' "
                      + "ORDER BY exam_centre", examId);
    }

    /** Sets the booklet a candidate is to be given (the sheet still records what they actually bubbled). */
    public int assignBooklet(int candidateId, String booklet) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE exam_candidates SET booklet_code = ? WHERE candidate_id = ?")) {
            if (booklet == null || booklet.trim().isEmpty()) ps.setNull(1, java.sql.Types.CHAR);
            else ps.setString(1, booklet.trim().toUpperCase());
            ps.setInt(2, candidateId);
            return ps.executeUpdate();
        }
    }

    /**
     * Spreads booklets A-D across the roll order, so neighbours in a hall get
     * different papers. Returns how many rows were set.
     */
    public int autoAssignBooklets(int examId) throws SQLException {
        String[] codes = { "A", "B", "C", "D" };
        int changed = 0;
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            List<Integer> ids = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT candidate_id FROM exam_candidates WHERE exam_id = ? ORDER BY roll_no")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(rs.getInt(1));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE exam_candidates SET booklet_code = ? WHERE candidate_id = ?")) {
                for (int i = 0; i < ids.size(); i++) {
                    ps.setString(1, codes[i % codes.length]);
                    ps.setInt(2, ids.get(i));
                    ps.addBatch();
                    if ((i + 1) % 500 == 0) ps.executeBatch();
                }
                for (int n : ps.executeBatch()) changed += Math.max(n, 0);
            }
            con.commit();
            con.setAutoCommit(true);
        }
        return changed;
    }

    /** Marks everyone who has no result yet as ABSENT — run once a scanning batch is finished. */
    public int markAbsentees(int examId) throws SQLException {
        String sql = "UPDATE exam_candidates c "
                   + "LEFT JOIN exam_results r ON r.candidate_id = c.candidate_id "
                   + "SET c.status = 'ABSENT' "
                   + "WHERE c.exam_id = ? AND r.result_id IS NULL AND c.status = 'REGISTERED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            return ps.executeUpdate();
        }
    }

    /**
     * Removes a candidate — only while nothing has been scored against them, so a
     * result can never be orphaned from the sitting that produced it. Returns
     * false when a result already exists.
     */
    public boolean delete(int candidateId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 FROM exam_results WHERE candidate_id = ?")) {
                ps.setInt(1, candidateId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return false;
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM exam_candidates WHERE candidate_id = ?")) {
                ps.setInt(1, candidateId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /**
     * Registers one existing lead for an exam, allocating the next roll number
     * from the exam's block under the same row lock the bulk importer uses.
     * Returns the roll number issued.
     */
    public String addCandidate(int examId, int inquiryId, String centre, int attemptNo)
            throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int seq;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT roll_block_from, roll_block_to, roll_next FROM exams "
                  + "WHERE exam_id = ? FOR UPDATE")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Exam " + examId + " not found.");
                    int from = rs.getInt("roll_block_from");
                    int to   = rs.getInt("roll_block_to");
                    int next = rs.getInt("roll_next");
                    if (rs.wasNull() || next < from) next = from;
                    if (from <= 0 || to <= 0) {
                        throw new SQLException("This exam has no roll-number block assigned.");
                    }
                    if (next > to) {
                        throw new SQLException("The exam's roll-number block (" + from + "-" + to
                                             + ") is exhausted. Widen it before adding candidates.");
                    }
                    seq = next;
                }
            }
            String roll = RollNumber.forSequence(seq);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO exam_candidates (exam_id, inquiry_id, roll_no, roll_kind, exam_centre, attempt_no) "
                  + "VALUES (?,?,?,'GENERATED',?,?)")) {
                ps.setInt(1, examId);
                ps.setInt(2, inquiryId);
                ps.setString(3, roll);
                if (centre == null || centre.trim().isEmpty()) ps.setNull(4, java.sql.Types.VARCHAR);
                else ps.setString(4, centre.trim());
                ps.setInt(5, attemptNo <= 0 ? 1 : attemptNo);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE exams SET roll_next = ? WHERE exam_id = ?")) {
                ps.setInt(1, seq + 1);
                ps.setInt(2, examId);
                ps.executeUpdate();
            }
            con.commit();
            return roll;
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    /* ─── helpers ─── */

    private List<String> distinct(String sql, int examId) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    private void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof Integer) ps.setInt(i + 1, (Integer) a);
            else ps.setString(i + 1, String.valueOf(a));
        }
    }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private ExamCandidate map(ResultSet rs) throws SQLException {
        ExamCandidate c = new ExamCandidate();
        c.setCandidateId(rs.getInt("candidate_id"));
        c.setExamId(rs.getInt("exam_id"));
        c.setInquiryId(rs.getInt("inquiry_id"));
        c.setRollNo(rs.getString("roll_no"));
        c.setRollKind(rs.getString("roll_kind"));
        c.setBookletCode(rs.getString("booklet_code"));
        c.setExamCentre(rs.getString("exam_centre"));
        c.setAttemptNo(rs.getInt("attempt_no"));
        c.setStatus(rs.getString("status"));
        int b = rs.getInt("import_batch_id");
        c.setImportBatchId(rs.wasNull() ? null : b);
        c.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        c.setFullName(rs.getString("full_name"));
        c.setMobile(rs.getString("mobile"));
        c.setParentMobile(rs.getString("parent_mobile"));
        c.setSchoolName(rs.getString("school_name"));
        c.setClassName(rs.getString("current_class"));
        c.setBoard(rs.getString("board"));
        c.setCity(rs.getString("city"));
        c.setExamName(rs.getString("exam_name"));
        java.sql.Date d = rs.getDate("exam_date");
        c.setExamDate(d == null ? "" : d.toString());
        c.setExamType(rs.getString("exam_type"));
        return c;
    }
}
