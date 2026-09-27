package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.LeadDemo;
import com.tution.util.DBConnection;
import com.tution.util.Dates;

/** Data-access for demo / trial-class scheduling. */
public class DemoDAO {

    private static final String COLS =
          "d.demo_id, d.inquiry_id, d.demo_date, d.demo_time, d.faculty_name, d.subject, "
        + "d.mode, d.status, d.feedback, d.rating, d.reminder_sent, d.created_by, d.created_at, "
        + "i.full_name AS lead_name, i.mobile AS lead_mobile, u.full_name AS counsellor_name";

    private static final String FROM =
          "FROM lead_demos d "
        + "JOIN inquiries i ON i.inquiry_id = d.inquiry_id "
        + "LEFT JOIN users u ON u.user_id = i.counsellor_id ";

    /** Demos booked for a lead, soonest first. */
    public List<LeadDemo> findByLead(int inquiryId) throws SQLException {
        String sql = "SELECT " + COLS + " " + FROM
                   + "WHERE d.inquiry_id = ? ORDER BY d.demo_date DESC, d.demo_id DESC";
        return query(sql, inquiryId);
    }

    /** Single demo by id, or null. */
    public LeadDemo findById(int demoId) throws SQLException {
        String sql = "SELECT " + COLS + " " + FROM + "WHERE d.demo_id = ?";
        List<LeadDemo> list = query(sql, demoId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * The demo diary.
     *
     * @param from   inclusive date, or null for no lower bound
     * @param to     inclusive date, or null for no upper bound
     * @param status one of SCHEDULED/COMPLETED/NO_SHOW/CANCELLED, or null for all
     * @param scopeCounsellorId when set, only demos on that counsellor's leads
     */
    public List<LeadDemo> find(String from, String to, String status, Integer scopeCounsellorId)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS).append(" ").append(FROM)
                                                        .append("WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (notBlank(from))   { sql.append("AND d.demo_date >= ? "); args.add(from); }
        if (notBlank(to))     { sql.append("AND d.demo_date <= ? "); args.add(to); }
        if (notBlank(status)) { sql.append("AND d.status = ? ");     args.add(status); }
        if (scopeCounsellorId != null) {
            sql.append("AND ").append(Scope.teamOf("i.counsellor_id")).append(' ');
            args.add(scopeCounsellorId);
        }
        sql.append("ORDER BY d.demo_date, d.demo_time, d.demo_id");

        List<LeadDemo> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                Object a = args.get(i);
                if (a instanceof Integer) {
                    ps.setInt(i + 1, (Integer) a);
                } else {
                    ps.setString(i + 1, String.valueOf(a));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Books a demo and moves the lead to DEMO_PENDING in one transaction — a
     * booked demo that left the lead in its old status would not show up in the
     * pipeline as awaiting a demo.
     */
    public int schedule(LeadDemo d) throws SQLException {
        String insert = "INSERT INTO lead_demos "
            + "(inquiry_id, demo_date, demo_time, faculty_name, subject, mode, status, created_by) "
            + "VALUES (?,?,?,?,?,?, 'SCHEDULED', ?)";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            int newId;

            try (PreparedStatement ps = con.prepareStatement(insert,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, d.getInquiryId());
                setNullableDate(ps, 2, d.getDemoDate());
                setNullable(ps, 3, d.getDemoTime());
                setNullable(ps, 4, d.getFacultyName());
                setNullable(ps, 5, d.getSubject());
                ps.setString(6, blankTo(d.getMode(), "Offline"));
                setNullable(ps, 7, d.getCreatedBy());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    newId = keys.next() ? keys.getInt(1) : 0;
                }
            }

            // The demo date doubles as the lead's next touchpoint.
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE inquiries SET status='DEMO_PENDING', next_followup_date=? "
                   + "WHERE inquiry_id=? AND status <> 'CONVERTED'")) {
                Dates.setNullableDateTime(ps, 1, d.getDemoDate());
                ps.setInt(2, d.getInquiryId());
                ps.executeUpdate();
            }

            con.commit();
            return newId;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /**
     * Records the outcome of a demo and moves the lead accordingly:
     * COMPLETED → DEMO_COMPLETED, NO_SHOW/CANCELLED → FOLLOWUP_REQUIRED.
     */
    public void recordOutcome(int demoId, String status, String feedback, Integer rating,
                              String nextFollowup) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int inquiryId;
            try (PreparedStatement ps = con.prepareStatement(
                     "SELECT inquiry_id FROM lead_demos WHERE demo_id = ?")) {
                ps.setInt(1, demoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return;
                    }
                    inquiryId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE lead_demos SET status=?, feedback=?, rating=? WHERE demo_id=?")) {
                ps.setString(1, status);
                setNullable(ps, 2, feedback);
                if (rating == null) {
                    ps.setNull(3, Types.TINYINT);
                } else {
                    ps.setInt(3, rating);
                }
                ps.setInt(4, demoId);
                ps.executeUpdate();
            }

            String leadStatus = "COMPLETED".equals(status) ? "DEMO_COMPLETED" : "FOLLOWUP_REQUIRED";
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE inquiries SET status=?, next_followup_date=? "
                   + "WHERE inquiry_id=? AND status <> 'CONVERTED'")) {
                ps.setString(1, leadStatus);
                Dates.setNullableDateTime(ps, 2, nextFollowup);
                ps.setInt(3, inquiryId);
                ps.executeUpdate();
            }

            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /** Demos still scheduled on a given date — the Phase 6 reminder queue. */
    public List<LeadDemo> scheduledOn(String date) throws SQLException {
        return find(date, date, "SCHEDULED", null);
    }

    /** Flags a demo's reminder as sent so a second scheduler run does not resend. */
    public void markReminderSent(int demoId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE lead_demos SET reminder_sent = 1 WHERE demo_id = ?")) {
            ps.setInt(1, demoId);
            ps.executeUpdate();
        }
    }

    // ── helpers ──

    private List<LeadDemo> query(String sql, int arg) throws SQLException {
        List<LeadDemo> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private LeadDemo map(ResultSet rs) throws SQLException {
        LeadDemo d = new LeadDemo();
        d.setDemoId(rs.getInt("demo_id"));
        d.setInquiryId(rs.getInt("inquiry_id"));
        java.sql.Date dt = rs.getDate("demo_date");
        d.setDemoDate(dt == null ? null : dt.toString());
        d.setDemoTime(rs.getString("demo_time"));
        d.setFacultyName(rs.getString("faculty_name"));
        d.setSubject(rs.getString("subject"));
        d.setMode(rs.getString("mode"));
        d.setStatus(rs.getString("status"));
        d.setFeedback(rs.getString("feedback"));
        int r = rs.getInt("rating");
        d.setRating(rs.wasNull() ? null : r);
        d.setReminderSent(rs.getInt("reminder_sent") == 1);
        d.setCreatedBy(rs.getString("created_by"));
        d.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        d.setLeadName(rs.getString("lead_name"));
        d.setLeadMobile(rs.getString("lead_mobile"));
        d.setCounsellorName(rs.getString("counsellor_name"));
        return d;
    }

    private static void close(Connection con) {
        if (con != null) {
            try { con.setAutoCommit(true); } catch (SQLException ignore) { }
            try { con.close(); } catch (SQLException ignore) { }
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }

    private static void setNullableDate(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.DATE);
            return;
        }
        try {
            ps.setDate(idx, java.sql.Date.valueOf(val.trim()));
        } catch (IllegalArgumentException e) {
            ps.setNull(idx, Types.DATE);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static String blankTo(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }
}
