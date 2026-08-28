package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.Ticket;
import com.tution.model.TicketReply;
import com.tution.util.DBConnection;

/** Data-access for student support tickets + their reply threads. */
public class TicketDAO {

    /* ── create ── */
    public int insert(Ticket t) throws SQLException {
        String sql = "INSERT INTO tickets (student_id, category, subject, message, priority) VALUES (?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getStudentId());
            ps.setString(2, (t.getCategory() == null || t.getCategory().isEmpty()) ? "General" : t.getCategory());
            ps.setString(3, t.getSubject());
            ps.setString(4, t.getMessage());
            ps.setString(5, (t.getPriority() == null || t.getPriority().isEmpty()) ? "NORMAL" : t.getPriority());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /* ── student: own tickets ── */
    public List<Ticket> findByStudent(int studentId) throws SQLException {
        String sql = "SELECT t.*, (SELECT COUNT(*) FROM ticket_replies r WHERE r.ticket_id = t.ticket_id) reply_count "
                   + "FROM tickets t WHERE t.student_id = ? ORDER BY t.ticket_id DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Ticket> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs, true));
                return list;
            }
        }
    }

    /* ── staff: all tickets with student name ── */
    public List<Ticket> findAll() throws SQLException {
        String sql = "SELECT t.*, s.full_name, s.admission_no, s.class_name, "
                   + "(SELECT COUNT(*) FROM ticket_replies r WHERE r.ticket_id = t.ticket_id) reply_count "
                   + "FROM tickets t JOIN students s ON s.student_id = t.student_id "
                   + "ORDER BY (t.status='CLOSED' OR t.status='RESOLVED'), t.updated_at DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Ticket> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs, true));
            return list;
        }
    }

    /** Single ticket with joined student info, or null. */
    public Ticket findById(int ticketId) throws SQLException {
        String sql = "SELECT t.*, s.full_name, s.admission_no, s.class_name "
                   + "FROM tickets t JOIN students s ON s.student_id = t.student_id WHERE t.ticket_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs, false) : null;
            }
        }
    }

    /* ── replies ── */
    public List<TicketReply> findReplies(int ticketId) throws SQLException {
        String sql = "SELECT reply_id, ticket_id, sender, sender_name, message, created_at "
                   + "FROM ticket_replies WHERE ticket_id = ? ORDER BY reply_id ASC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TicketReply> list = new ArrayList<>();
                while (rs.next()) {
                    TicketReply r = new TicketReply();
                    r.setReplyId(rs.getInt("reply_id"));
                    r.setTicketId(rs.getInt("ticket_id"));
                    r.setSender(rs.getString("sender"));
                    r.setSenderName(rs.getString("sender_name"));
                    r.setMessage(rs.getString("message"));
                    r.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                    list.add(r);
                }
                return list;
            }
        }
    }

    public void addReply(TicketReply r) throws SQLException {
        String sql = "INSERT INTO ticket_replies (ticket_id, sender, sender_name, message) VALUES (?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, r.getTicketId());
            ps.setString(2, r.getSender());
            ps.setString(3, r.getSenderName());
            ps.setString(4, r.getMessage());
            ps.executeUpdate();
        }
        touch(r.getTicketId());
    }

    public void updateStatus(int ticketId, String status) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE tickets SET status = ? WHERE ticket_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }
    }

    /** Count of tickets needing attention (OPEN or IN_PROGRESS) — for the dashboard badge. */
    public int countOpen() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT COUNT(*) FROM tickets WHERE status IN ('OPEN','IN_PROGRESS')");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void touch(int ticketId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE tickets SET updated_at = CURRENT_TIMESTAMP WHERE ticket_id = ?")) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }
    }

    private Ticket map(ResultSet rs, boolean joined) throws SQLException {
        Ticket t = new Ticket();
        t.setTicketId(rs.getInt("ticket_id"));
        t.setStudentId(rs.getInt("student_id"));
        t.setCategory(rs.getString("category"));
        t.setSubject(rs.getString("subject"));
        t.setMessage(rs.getString("message"));
        t.setStatus(rs.getString("status"));
        t.setPriority(rs.getString("priority"));
        t.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        t.setUpdatedAt(String.valueOf(rs.getTimestamp("updated_at")));
        if (hasColumn(rs, "full_name"))   t.setStudentName(rs.getString("full_name"));
        if (hasColumn(rs, "admission_no")) t.setAdmissionNo(rs.getString("admission_no"));
        if (hasColumn(rs, "class_name"))   t.setClassName(rs.getString("class_name"));
        if (hasColumn(rs, "reply_count"))  t.setReplyCount(rs.getInt("reply_count"));
        return t;
    }

    private boolean hasColumn(ResultSet rs, String col) throws SQLException {
        java.sql.ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (col.equalsIgnoreCase(md.getColumnLabel(i))) return true;
        }
        return false;
    }
}
