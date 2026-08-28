package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.SubjectScore;
import com.tution.service.ExamScanService;
import com.tution.util.DBConnection;

/** Persists scanned sheets and their results. */
public class ExamResultDAO {

    /** One row of the results table, for the results screen. */
    public static class Row {
        public int candidateId, resultId;
        public String rollNo, name, school, booklet, status;
        public int attempted, correct, wrong, blank, rawScore, maxScore;
        public double percentage, scholarshipPct;
        public String awardCriteria;
        public Integer rank;
        public final List<SubjectScore> subjects = new ArrayList<>();
    }

    /**
     * Writes one processed sheet in a single transaction: the scan row, and — if
     * it matched — the result, its subject breakdown, and the candidate's status.
     *
     * An UNMATCHED sheet is still stored. It has to be: the review queue works
     * from the scan row and its image, and a sheet that vanished because nobody
     * could identify it is a student with no marks and no trace.
     */
    public void save(int examId, ExamScanService.Sheet s, String imagePath, String scannedBy)
            throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int scanId = insertScan(con, examId, s, imagePath, scannedBy);

            if (s.match == ExamScanService.Match.MATCHED && s.candidate != null) {
                int resultId = upsertResult(con, s, scanId);
                saveSubjects(con, resultId, s.subjects);
                setStatus(con, s.candidate.getCandidateId(), "RESULT_READY");
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    private int insertScan(Connection con, int examId, ExamScanService.Sheet s,
                           String imagePath, String scannedBy) throws SQLException {
        String sql = "INSERT INTO omr_scans (student_id, title, page_no, start_q, end_q, total, "
                   + "attempted, blank, ambiguous, correct, wrong, score, result_json, scanned_by, "
                   + "exam_id, candidate_id, roll_read, booklet_read, match_status, low_registration, image_path) "
                   + "VALUES (NULL,?,1,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, s.fileName);
            ps.setInt(i++, s.omr == null ? 0 : s.omr.getEndQ());
            ps.setInt(i++, s.omr == null ? 0 : s.omr.total());
            ps.setInt(i++, s.attempted);
            ps.setInt(i++, s.blank);
            ps.setInt(i++, s.omr == null ? 0 : s.omr.getAmbiguous().size());
            setIntOrNull(ps, i++, s.scored() ? s.correct : null);
            setIntOrNull(ps, i++, s.scored() ? s.wrong : null);
            setIntOrNull(ps, i++, s.scored() ? s.rawScore : null);
            ps.setString(i++, answersJson(s));
            ps.setString(i++, scannedBy);
            ps.setInt(i++, examId);
            if (s.candidate == null) ps.setNull(i++, java.sql.Types.INTEGER);
            else ps.setInt(i++, s.candidate.getCandidateId());
            ps.setString(i++, s.rollRead);
            if (s.bookletRead == null) ps.setNull(i++, java.sql.Types.CHAR);
            else ps.setString(i++, s.bookletRead);
            ps.setString(i++, s.match == null ? null : s.match.name());
            ps.setBoolean(i++, s.lowRegistration);
            if (imagePath == null) ps.setNull(i++, java.sql.Types.VARCHAR);
            else ps.setString(i++, imagePath);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Re-scanning a candidate replaces their result rather than adding a second. */
    private int upsertResult(Connection con, ExamScanService.Sheet s, int scanId) throws SQLException {
        String sql = "INSERT INTO exam_results (candidate_id, scan_id, booklet_read, attempted, correct, "
                   + "wrong, blank, raw_score, max_score, percentage, scholarship_pct, awarded_rule_id) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE scan_id=VALUES(scan_id), booklet_read=VALUES(booklet_read), "
                   + "attempted=VALUES(attempted), correct=VALUES(correct), wrong=VALUES(wrong), "
                   + "blank=VALUES(blank), raw_score=VALUES(raw_score), max_score=VALUES(max_score), "
                   + "percentage=VALUES(percentage), scholarship_pct=VALUES(scholarship_pct), "
                   + "awarded_rule_id=VALUES(awarded_rule_id)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, s.candidate.getCandidateId());
            ps.setInt(i++, scanId);
            if (s.bookletRead == null) ps.setNull(i++, java.sql.Types.CHAR);
            else ps.setString(i++, s.bookletRead);
            ps.setInt(i++, s.attempted);
            ps.setInt(i++, s.correct);
            ps.setInt(i++, s.wrong);
            ps.setInt(i++, s.blank);
            ps.setInt(i++, s.rawScore);
            ps.setInt(i++, s.maxScore);
            ps.setDouble(i++, s.percentage);
            ps.setDouble(i++, s.scholarshipPct);
            if (s.awardedRuleId == null) ps.setNull(i++, java.sql.Types.INTEGER);
            else ps.setInt(i++, s.awardedRuleId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next() && rs.getInt(1) > 0) return rs.getInt(1);
            }
        }
        // ON DUPLICATE KEY UPDATE returns no key when it updated; look it up.
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT result_id FROM exam_results WHERE candidate_id = ?")) {
            ps.setInt(1, s.candidate.getCandidateId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void saveSubjects(Connection con, int resultId, List<SubjectScore> subjects) throws SQLException {
        try (PreparedStatement del = con.prepareStatement(
                "DELETE FROM exam_result_subjects WHERE result_id = ?")) {
            del.setInt(1, resultId);
            del.executeUpdate();
        }
        if (subjects == null || subjects.isEmpty()) return;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO exam_result_subjects (result_id, subject, q_from, q_to, attempted, correct, wrong, score) "
              + "VALUES (?,?,?,?,?,?,?,?)")) {
            for (SubjectScore s : subjects) {
                ps.setInt(1, resultId);
                ps.setString(2, s.name);
                ps.setInt(3, s.from);
                ps.setInt(4, s.to);
                ps.setInt(5, s.attempted);
                ps.setInt(6, s.correct);
                ps.setInt(7, s.wrong);
                ps.setInt(8, s.score);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void setStatus(Connection con, int candidateId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE exam_candidates SET status = ? WHERE candidate_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, candidateId);
            ps.executeUpdate();
        }
    }

    /* ─── reading back ─── */

    /** Results for an exam, ranked by raw score. */
    public List<Row> results(int examId) throws SQLException {
        String sql = "SELECT r.result_id, r.candidate_id, c.roll_no, i.full_name, i.school_name, "
                   + "r.booklet_read, c.status, r.attempted, r.correct, r.wrong, r.blank, "
                   + "r.raw_score, r.max_score, r.percentage, r.scholarship_pct, sr.criteria "
                   + "FROM exam_results r "
                   + "JOIN exam_candidates c ON c.candidate_id = r.candidate_id "
                   + "JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                   + "LEFT JOIN scholarship_rules sr ON sr.rule_id = r.awarded_rule_id "
                   + "WHERE c.exam_id = ? ORDER BY r.raw_score DESC, i.full_name";
        List<Row> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 0, lastScore = Integer.MIN_VALUE, seen = 0;
                while (rs.next()) {
                    Row r = new Row();
                    r.resultId = rs.getInt("result_id");
                    r.candidateId = rs.getInt("candidate_id");
                    r.rollNo = rs.getString("roll_no");
                    r.name = rs.getString("full_name");
                    r.school = rs.getString("school_name");
                    r.booklet = rs.getString("booklet_read");
                    r.status = rs.getString("status");
                    r.attempted = rs.getInt("attempted");
                    r.correct = rs.getInt("correct");
                    r.wrong = rs.getInt("wrong");
                    r.blank = rs.getInt("blank");
                    r.rawScore = rs.getInt("raw_score");
                    r.maxScore = rs.getInt("max_score");
                    r.percentage = rs.getDouble("percentage");
                    r.scholarshipPct = rs.getDouble("scholarship_pct");
                    r.awardCriteria = rs.getString("criteria");
                    seen++;
                    // equal scores share a rank; the next distinct score skips ahead
                    if (r.rawScore != lastScore) { rank = seen; lastScore = r.rawScore; }
                    r.rank = rank;
                    out.add(r);
                }
            }
        }
        if (!out.isEmpty()) loadSubjects(out);
        return out;
    }

    private void loadSubjects(List<Row> rows) throws SQLException {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) in.append(i == 0 ? "?" : ",?");
        String sql = "SELECT result_id, subject, q_from, q_to, attempted, correct, wrong, score "
                   + "FROM exam_result_subjects WHERE result_id IN (" + in + ") ORDER BY q_from";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < rows.size(); i++) ps.setInt(i + 1, rows.get(i).resultId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("result_id");
                    for (Row r : rows) {
                        if (r.resultId != id) continue;
                        SubjectScore s = new SubjectScore(rs.getString("subject"),
                                rs.getInt("q_from"), rs.getInt("q_to"));
                        s.attempted = rs.getInt("attempted");
                        s.correct = rs.getInt("correct");
                        s.wrong = rs.getInt("wrong");
                        s.score = rs.getInt("score");
                        r.subjects.add(s);
                        break;
                    }
                }
            }
        }
    }

    /** Scans that did not match, for the review queue. */
    public List<Object[]> reviewQueue(int examId) throws SQLException {
        String sql = "SELECT scan_id, title, roll_read, booklet_read, match_status, low_registration, "
                   + "image_path, created_at FROM omr_scans "
                   + "WHERE exam_id = ? AND match_status <> 'MATCHED' ORDER BY scan_id DESC";
        List<Object[]> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Object[] { rs.getInt("scan_id"), rs.getString("title"),
                            rs.getString("roll_read"), rs.getString("booklet_read"),
                            rs.getString("match_status"), rs.getBoolean("low_registration"),
                            rs.getString("image_path"), String.valueOf(rs.getTimestamp("created_at")) });
                }
            }
        }
        return out;
    }

    /**
     * Points a reviewed scan at the candidate a human identified it as.
     * The original match_status is kept alongside, so the audit trail still
     * shows what the reader made of the sheet before anyone intervened.
     */
    public void linkScanToCandidate(int scanId, int candidateId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE omr_scans SET candidate_id = ? WHERE scan_id = ?")) {
            ps.setInt(1, candidateId);
            ps.setInt(2, scanId);
            ps.executeUpdate();
        }
    }

    /**
     * Removes one review-queue sheet permanently — for a scan that was never
     * going to match (wrong exam selected, a blank page pulled in with the
     * batch, a duplicate upload). The match_status guard means this can never
     * touch a sheet that already scored, even if a stale scanId is replayed.
     *
     * @return the sheet's archived image path (to also delete the file), or
     *         null if there was nothing to delete or it wasn't a review item.
     */
    public String deleteScan(int examId, int scanId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            String img;
            try (PreparedStatement sel = con.prepareStatement(
                    "SELECT image_path FROM omr_scans WHERE scan_id = ? AND exam_id = ? AND match_status <> 'MATCHED'")) {
                sel.setInt(1, scanId);
                sel.setInt(2, examId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) return null;
                    img = rs.getString("image_path");
                }
            }
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM omr_scans WHERE scan_id = ?")) {
                del.setInt(1, scanId);
                del.executeUpdate();
            }
            return img;
        }
    }

    /** Empties the whole review queue for one exam. Returns the archived image paths removed. */
    public List<String> deleteAllReview(int examId) throws SQLException {
        List<String> imgs = new ArrayList<>();
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement sel = con.prepareStatement(
                    "SELECT image_path FROM omr_scans WHERE exam_id = ? AND match_status <> 'MATCHED'")) {
                sel.setInt(1, examId);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) imgs.add(rs.getString("image_path"));
                }
            }
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM omr_scans WHERE exam_id = ? AND match_status <> 'MATCHED'")) {
                del.setInt(1, examId);
                del.executeUpdate();
            }
        }
        return imgs;
    }

    /** Deletes a candidate's result, so a sheet can be re-scanned cleanly. */
    public void deleteResult(int candidateId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM exam_results WHERE candidate_id = ?")) {
                ps.setInt(1, candidateId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE exam_candidates SET status = 'REGISTERED' WHERE candidate_id = ?")) {
                ps.setInt(1, candidateId);
                ps.executeUpdate();
            }
        }
    }

    private String answersJson(ExamScanService.Sheet s) {
        if (s.omr == null) return "{}";
        StringBuilder sb = new StringBuilder("{\"answers\":{");
        boolean first = true;
        for (java.util.Map.Entry<Integer, String> e : s.omr.getAnswers().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":")
              .append(e.getValue() == null ? "null" : "\"" + e.getValue() + "\"");
        }
        sb.append("},\"roll\":\"").append(s.rollRead == null ? "" : s.rollRead).append('"');
        sb.append(",\"booklet\":").append(s.bookletRead == null ? "null" : "\"" + s.bookletRead + "\"");
        sb.append(",\"board\":").append(s.boardRead == null ? "null" : "\"" + s.boardRead + "\"");
        sb.append(",\"examName\":").append(s.examNameRead == null ? "null" : "\"" + s.examNameRead + "\"");
        sb.append('}');
        return sb.toString();
    }

    private void setIntOrNull(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.INTEGER);
        else ps.setInt(idx, v);
    }
}
