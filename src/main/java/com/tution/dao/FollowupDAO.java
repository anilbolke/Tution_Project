package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.LeadFollowup;
import com.tution.util.DBConnection;
import com.tution.util.Dates;

/** Data-access for the counselling follow-up history. */
public class FollowupDAO {

    private static final String COLS =
          "f.followup_id, f.inquiry_id, f.counsellor_id, f.counsellor_name, f.comm_type, "
        + "f.discussion, f.objection, f.outcome_status, f.next_action_date, f.created_at";

    /** Every touchpoint on a lead, newest first. */
    public List<LeadFollowup> findByLead(int inquiryId) throws SQLException {
        String sql = "SELECT " + COLS + " FROM lead_followups f "
                   + "WHERE f.inquiry_id = ? ORDER BY f.followup_id DESC";
        List<LeadFollowup> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inquiryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** How many touchpoints a lead has had — shown as a badge on the timeline. */
    public int countByLead(int inquiryId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT COUNT(*) FROM lead_followups WHERE inquiry_id = ?")) {
            ps.setInt(1, inquiryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Records a follow-up AND moves the lead in one transaction.
     *
     * These two writes must not come apart: a logged call that did not update
     * next_followup_date would silently drop out of the reminder queue, and a
     * moved lead with no history entry loses the audit trail the counsellor
     * performance report is built on.
     *
     * @param newStatus null to leave the lead's status untouched
     */
    public void logAndAdvanceLead(LeadFollowup f, String newStatus) throws SQLException {
        String insert = "INSERT INTO lead_followups "
            + "(inquiry_id, counsellor_id, counsellor_name, comm_type, discussion, "
            + " objection, outcome_status, next_action_date) VALUES (?,?,?,?,?,?,?,?)";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(insert)) {
                ps.setInt(1, f.getInquiryId());
                if (f.getCounsellorId() == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, f.getCounsellorId());
                }
                setNullable(ps, 3, f.getCounsellorName());
                ps.setString(4, blankTo(f.getCommType(), "Call"));
                setNullable(ps, 5, f.getDiscussion());
                setNullable(ps, 6, f.getObjection());
                setNullable(ps, 7, newStatus);
                Dates.setNullableDateTime(ps, 8, f.getNextActionDate());
                ps.executeUpdate();
            }

            String update = (newStatus == null || newStatus.isEmpty())
                ? "UPDATE inquiries SET next_followup_date = ? WHERE inquiry_id = ?"
                : "UPDATE inquiries SET next_followup_date = ?, status = ? WHERE inquiry_id = ?";
            try (PreparedStatement ps = con.prepareStatement(update)) {
                Dates.setNullableDateTime(ps, 1, f.getNextActionDate());
                if (newStatus == null || newStatus.isEmpty()) {
                    ps.setInt(2, f.getInquiryId());
                } else {
                    ps.setString(2, newStatus);
                    ps.setInt(3, f.getInquiryId());
                }
                ps.executeUpdate();
            }

            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); } catch (SQLException ignore) { }
                try { con.close(); } catch (SQLException ignore) { }
            }
        }
    }

    private LeadFollowup map(ResultSet rs) throws SQLException {
        LeadFollowup f = new LeadFollowup();
        f.setFollowupId(rs.getInt("followup_id"));
        f.setInquiryId(rs.getInt("inquiry_id"));
        int cid = rs.getInt("counsellor_id");
        f.setCounsellorId(rs.wasNull() ? null : cid);
        f.setCounsellorName(rs.getString("counsellor_name"));
        f.setCommType(rs.getString("comm_type"));
        f.setDiscussion(rs.getString("discussion"));
        f.setObjection(rs.getString("objection"));
        f.setOutcomeStatus(rs.getString("outcome_status"));
        f.setNextActionDate(Dates.read(rs, "next_action_date"));
        f.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        return f;
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }

    private static String blankTo(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }
}
