package com.tution.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.tution.util.DBConnection;

/**
 * Hands published results back to the sales pipeline.
 *
 * The result lives against a SITTING (exam_candidates -> exam_results); the
 * admission form works from the LEAD. Publication copies the winning scholarship
 * onto the lead and moves it to RESULT_DECLARED, which is what lets the
 * counsellor's existing follow-up and conversion flow pick these candidates up
 * with no knowledge of exams, and lets the fee plan pre-fill a concession.
 */
public class ResultPublishService {

    public static class Summary {
        public int imported;   // leads updated
        public int skipped;    // already converted to a student
    }

    /**
     * Publishes every scored result for an exam.
     *
     * Runs in one transaction so a half-published exam cannot exist — some
     * counsellors seeing a scholarship and others not would be worse than none
     * of them seeing it.
     *
     * A lead already CONVERTED is left alone: that student has an admission and
     * very likely a fee ledger built on the concession recorded at the time.
     * Silently rewriting it here would change what they owe.
     */
    public Summary publish(int examId, String who) throws SQLException {
        Summary s = new Summary();
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String sql = "SELECT c.inquiry_id, r.scholarship_pct, i.status, sr.criteria "
                       + "FROM exam_results r "
                       + "JOIN exam_candidates c ON c.candidate_id = r.candidate_id "
                       + "JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                       + "LEFT JOIN scholarship_rules sr ON sr.rule_id = r.awarded_rule_id "
                       + "WHERE c.exam_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql);
                 PreparedStatement up = con.prepareStatement(
                     "UPDATE inquiries SET scholarship_pct = ?, scholarship_note = ?, "
                   + "status = 'RESULT_DECLARED' WHERE inquiry_id = ?")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if ("CONVERTED".equals(rs.getString("status"))) { s.skipped++; continue; }
                        up.setDouble(1, rs.getDouble("scholarship_pct"));
                        String note = rs.getString("criteria");
                        if (note == null) up.setNull(2, java.sql.Types.VARCHAR);
                        else up.setString(2, note);
                        up.setInt(3, rs.getInt("inquiry_id"));
                        up.addBatch();
                        s.imported++;
                    }
                }
                up.executeBatch();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE exams SET result_published = 1, published_at = NOW(), published_by = ? "
                  + "WHERE exam_id = ?")) {
                ps.setString(1, who);
                ps.setInt(2, examId);
                ps.executeUpdate();
            }
            con.commit();
            return s;
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }
}
