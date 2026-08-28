package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import com.tution.util.DBConnection;
import com.tution.util.Dates;

/**
 * The send log that makes reminders idempotent.
 *
 * The unique key is (kind, ref_id, due_date, channel), so the same reminder for
 * the same due date can only be recorded once no matter how often the scheduler
 * runs or how many times someone clicks Send. Nobody gets messaged twice about
 * one thing — the fastest way to lose a parent's trust.
 */
public class ReminderLogDAO {

    /**
     * Records a send attempt.
     *
     * @return true if this was a NEW record (the caller should send), false if
     *         an identical reminder had already been logged.
     */
    public boolean claim(String kind, int refId, String dueDate, String channel)
            throws SQLException {
        String sql = "INSERT IGNORE INTO reminder_log (kind, ref_id, due_date, channel) "
                   + "VALUES (?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, kind);
            ps.setInt(2, refId);
            setNullableDate(ps, 3, dueDate);
            ps.setString(4, (channel == null || channel.isEmpty()) ? "WHATSAPP" : channel);
            return ps.executeUpdate() > 0;
        }
    }

    /** Marks the outcome of a send that was previously claimed. */
    public void complete(String kind, int refId, String dueDate, String channel,
                         boolean ok, String messageId, String detail) throws SQLException {
        String sql = "UPDATE reminder_log SET sent_ok = ?, message_id = ?, detail = ? "
                   + "WHERE kind = ? AND ref_id = ? AND due_date = ? AND channel = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ok ? 1 : 0);
            setNullable(ps, 2, messageId);
            setNullable(ps, 3, detail);
            ps.setString(4, kind);
            ps.setInt(5, refId);
            setNullableDate(ps, 6, dueDate);
            ps.setString(7, (channel == null || channel.isEmpty()) ? "WHATSAPP" : channel);
            ps.executeUpdate();
        }
    }

    /**
     * Removes a claim so the item can be retried.
     *
     * Used when a send fails: leaving the row would suppress the reminder
     * forever, which is worse than sending it a little late.
     */
    public void release(String kind, int refId, String dueDate, String channel)
            throws SQLException {
        String sql = "DELETE FROM reminder_log WHERE kind = ? AND ref_id = ? "
                   + "AND due_date = ? AND channel = ? AND sent_ok = 0";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, kind);
            ps.setInt(2, refId);
            setNullableDate(ps, 3, dueDate);
            ps.setString(4, (channel == null || channel.isEmpty()) ? "WHATSAPP" : channel);
            ps.executeUpdate();
        }
    }

    /** Keys ("KIND:refId:dueDate") already logged, so the queue can grey them out. */
    public Set<String> sentKeys(String fromDate) throws SQLException {
        Set<String> keys = new HashSet<>();
        String sql = "SELECT kind, ref_id, due_date FROM reminder_log WHERE due_date >= ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setNullableDate(ps, 1, fromDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date d = rs.getDate("due_date");
                    keys.add(rs.getString("kind") + ":" + rs.getInt("ref_id")
                             + ":" + (d == null ? "" : d.toString()));
                }
            }
        }
        return keys;
    }

    /** How many reminders went out in the last N days — shown on the queue page. */
    public int sentSince(int days) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT COUNT(*) FROM reminder_log "
               + "WHERE sent_ok = 1 AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, java.sql.Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim().length() > 250 ? val.trim().substring(0, 250) : val.trim());
        }
    }

    /**
     * due_date is a DATE and the unique key is per DAY, but a follow-up's due
     * value now carries a time ("2026-08-13 16:30"). Truncating here — rather
     * than at each call site — keeps every claim/complete/release consistent
     * with the day-shaped keys sentKeys() reads back.
     */
    private static void setNullableDate(PreparedStatement ps, int idx, String val) throws SQLException {
        String day = Dates.dayOf(val);
        if (day == null) {
            ps.setNull(idx, java.sql.Types.DATE);
        } else {
            ps.setDate(idx, java.sql.Date.valueOf(day));
        }
    }
}
