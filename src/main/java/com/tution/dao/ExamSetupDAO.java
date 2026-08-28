package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.Exam;
import com.tution.model.SubjectScore;
import com.tution.util.DBConnection;

/** Persistence for exam setup: settings, subject split, booklet answer keys. */
public class ExamSetupDAO {

    /* ─── the exam row ─── */

    /** Creates a scholarship exam. Returns the new exam_id. */
    public int create(Exam e) throws SQLException {
        String sql = "INSERT INTO exams (exam_name, exam_date, class_name, subjects, max_per_subject, "
                   + "exam_type, template_id, total_questions, mark_correct, mark_wrong, max_score, "
                   + "roll_block_from, roll_block_to, roll_next, created_by) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindSettings(ps, e);
            ps.setInt(14, e.getRollBlockFrom());          // a new exam starts at the block start
            ps.setString(15, e.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Saves settings on an existing exam. roll_next is never moved backwards here. */
    public int updateSettings(Exam e) throws SQLException {
        String sql = "UPDATE exams SET exam_name=?, exam_date=?, class_name=?, subjects=?, max_per_subject=?, "
                   + "exam_type=?, template_id=?, total_questions=?, mark_correct=?, mark_wrong=?, max_score=?, "
                   + "roll_block_from=?, roll_block_to=?, "
                   + "roll_next = GREATEST(COALESCE(roll_next, ?), ?) "
                   + "WHERE exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindSettings(ps, e);
            ps.setInt(14, e.getRollBlockFrom());
            ps.setInt(15, e.getRollBlockFrom());
            ps.setInt(16, e.getExamId());
            return ps.executeUpdate();
        }
    }

    private void bindSettings(PreparedStatement ps, Exam e) throws SQLException {
        ps.setString(1, e.getExamName());
        if (e.getExamDate() == null || !e.getExamDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
            ps.setNull(2, java.sql.Types.DATE);
        } else {
            ps.setString(2, e.getExamDate());
        }
        ps.setString(3, e.getClassName());
        ps.setString(4, e.getSubjects() == null ? "" : e.getSubjects());
        ps.setInt(5, e.getMaxPerSubject());
        ps.setString(6, e.getExamType());
        if (e.getTemplateId() == null) ps.setNull(7, java.sql.Types.INTEGER);
        else ps.setInt(7, e.getTemplateId());
        ps.setInt(8,  e.getTotalQuestions());
        ps.setInt(9,  e.getMarkCorrect());
        ps.setInt(10, e.getMarkWrong());
        ps.setInt(11, e.getMaxScore());
        ps.setInt(12, e.getRollBlockFrom());
        ps.setInt(13, e.getRollBlockTo());
    }

    /* ─── roll block safety ─── */

    /**
     * Why this exists: roll numbers are globally unique and never reused, which is
     * what lets a sheet fed into the wrong exam be DETECTED rather than scored
     * against a stranger. Two exams with overlapping blocks would break that
     * guarantee, and the collision would only surface halfway through an import.
     *
     * Returns a human-readable reason, or null when the block is safe.
     */
    public String blockConflict(int examId, int from, int to) throws SQLException {
        if (from < 1 || to < 1)   return "Roll block start and end must both be given.";
        if (to < from)            return "Roll block end (" + to + ") is before its start (" + from + ").";
        if (to > 99999)           return "Roll numbers only go up to 99999 - the sheet has six bubble columns.";

        String sql = "SELECT exam_id, exam_name, roll_block_from, roll_block_to FROM exams "
                   + "WHERE exam_id <> ? AND roll_block_from IS NOT NULL AND roll_block_to IS NOT NULL "
                   + "AND roll_block_from <= ? AND roll_block_to >= ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            ps.setInt(2, to);
            ps.setInt(3, from);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "Roll block " + from + "-" + to + " overlaps '" + rs.getString("exam_name")
                         + "' (" + rs.getInt("roll_block_from") + "-" + rs.getInt("roll_block_to")
                         + "). Roll numbers are never reused, so blocks must not overlap.";
                }
            }
        }
        // Never strand roll numbers that have already been printed on hall tickets.
        int issued = highestIssuedSequence(examId);
        if (issued > 0 && (from > issued || to < issued)) {
            return "Roll numbers up to sequence " + issued + " have already been issued for this exam, "
                 + "so the block must still contain them.";
        }
        return null;
    }

    /** Highest 5-digit sequence already handed out for this exam, or 0. */
    public int highestIssuedSequence(int examId) throws SQLException {
        String sql = "SELECT MAX(CAST(LEFT(roll_no, 5) AS UNSIGNED)) FROM exam_candidates "
                   + "WHERE exam_id = ? AND roll_kind = 'GENERATED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** The next free sequence across every exam, as a suggestion for a new block. */
    public int suggestBlockStart() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(MAX(roll_block_to), 0) + 1 FROM exams");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Math.max(1, rs.getInt(1)) : 1;
        }
    }

    /* ─── subject split ─── */

    public List<SubjectScore> subjectMap(int examId) throws SQLException {
        List<SubjectScore> out = new ArrayList<>();
        String sql = "SELECT subject, q_from, q_to FROM exam_subject_map "
                   + "WHERE exam_id = ? ORDER BY sort_order, q_from";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SubjectScore(rs.getString("subject"), rs.getInt("q_from"), rs.getInt("q_to")));
                }
            }
        }
        return out;
    }

    /** Replaces the whole split in one transaction — a half-saved map is worse than none. */
    public void saveSubjectMap(int examId, List<SubjectScore> ranges) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM exam_subject_map WHERE exam_id = ?")) {
                del.setInt(1, examId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO exam_subject_map (exam_id, subject, q_from, q_to, sort_order) "
                  + "VALUES (?,?,?,?,?)")) {
                int order = 0;
                for (SubjectScore r : ranges) {
                    ps.setInt(1, examId);
                    ps.setString(2, r.name);
                    ps.setInt(3, r.from);
                    ps.setInt(4, r.to);
                    ps.setInt(5, order++);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            // Keep the legacy CSV column in step: exam_marks and the older results
            // screens still read it.
            StringBuilder csv = new StringBuilder();
            for (SubjectScore r : ranges) {
                if (csv.length() > 0) csv.append(',');
                csv.append(r.name);
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE exams SET subjects = ? WHERE exam_id = ?")) {
                ps.setString(1, csv.toString());
                ps.setInt(2, examId);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    /* ─── answer keys ─── */

    public Map<Integer, String> answerKey(int examId, String booklet) throws SQLException {
        Map<Integer, String> key = new LinkedHashMap<>();
        String sql = "SELECT q_no, correct_opt FROM exam_answer_keys "
                   + "WHERE exam_id = ? AND booklet_code = ? ORDER BY q_no";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            ps.setString(2, booklet);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) key.put(rs.getInt("q_no"), rs.getString("correct_opt"));
            }
        }
        return key;
    }

    /** How many answers each booklet has on file — drives the "ready to scan" check. */
    public Map<String, Integer> keyCounts(int examId) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        String sql = "SELECT booklet_code, COUNT(*) n FROM exam_answer_keys "
                   + "WHERE exam_id = ? GROUP BY booklet_code ORDER BY booklet_code";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString("booklet_code"), rs.getInt("n"));
            }
        }
        return out;
    }

    /** Replaces one booklet's key. An empty map clears it. */
    public void saveAnswerKey(int examId, String booklet, Map<Integer, String> key) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM exam_answer_keys WHERE exam_id = ? AND booklet_code = ?")) {
                del.setInt(1, examId);
                del.setString(2, booklet);
                del.executeUpdate();
            }
            if (key != null && !key.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO exam_answer_keys (exam_id, booklet_code, q_no, correct_opt) "
                      + "VALUES (?,?,?,?)")) {
                    for (Map.Entry<Integer, String> e : key.entrySet()) {
                        ps.setInt(1, examId);
                        ps.setString(2, booklet);
                        ps.setInt(3, e.getKey());
                        ps.setString(4, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    /* ─── sheet templates ─── */

    /** template_id -> display name, for the dropdown. */
    public Map<Integer, String> templates() throws SQLException {
        Map<Integer, String> out = new LinkedHashMap<>();
        String sql = "SELECT template_id, name, total_questions, roll_cols FROM omr_templates "
                   + "WHERE is_active = 1 ORDER BY template_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getInt("template_id"),
                        rs.getString("name") + " - " + rs.getInt("total_questions") + "Q, "
                      + rs.getInt("roll_cols") + "-digit roll");
            }
        }
        return out;
    }

    /** Questions the chosen sheet layout physically has, or 0 when unknown. */
    public int templateQuestions(int templateId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT total_questions FROM omr_templates WHERE template_id = ?")) {
            ps.setInt(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
