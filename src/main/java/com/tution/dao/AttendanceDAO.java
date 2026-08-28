package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.AttendanceSummary;
import com.tution.util.DBConnection;

/** Data-access for attendance. */
public class AttendanceDAO {

    /** Inserts or updates the status for a student on a given date. */
    public void mark(int studentId, String date, String status, String markedBy) throws SQLException {
        String sql = "INSERT INTO attendance (student_id, attendance_date, status, marked_by) "
                   + "VALUES (?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE status = VALUES(status), marked_by = VALUES(marked_by)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setString(2, date);
            ps.setString(3, status);
            ps.setString(4, markedBy);
            ps.executeUpdate();
        }
    }

    /** Marks many students for one date in a single batch. */
    public void markBatch(String date, Map<Integer, String> statusByStudent, String markedBy) throws SQLException {
        String sql = "INSERT INTO attendance (student_id, attendance_date, status, marked_by) "
                   + "VALUES (?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE status = VALUES(status), marked_by = VALUES(marked_by)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (Map.Entry<Integer, String> e : statusByStudent.entrySet()) {
                ps.setInt(1, e.getKey());
                ps.setString(2, date);
                ps.setString(3, e.getValue());
                ps.setString(4, markedBy);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Attendance totals for one student (for the student portal). */
    public AttendanceSummary summaryForStudent(int studentId) throws SQLException {
        String sql = "SELECT SUM(status='Present') AS present, SUM(status='Absent') AS absent, "
                   + "SUM(status='Late') AS late, SUM(status='Leave') AS leave_cnt "
                   + "FROM attendance WHERE student_id = ?";
        AttendanceSummary a = new AttendanceSummary();
        a.setStudentId(studentId);
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    a.setPresent(rs.getInt("present"));
                    a.setAbsent(rs.getInt("absent"));
                    a.setLate(rs.getInt("late"));
                    a.setLeave(rs.getInt("leave_cnt"));
                }
            }
        }
        return a;
    }

    /** Recent attendance records for one student: [date, status], newest first. */
    public List<String[]> recentForStudent(int studentId, int limit) throws SQLException {
        String sql = "SELECT attendance_date, status FROM attendance WHERE student_id = ? "
                   + "ORDER BY attendance_date DESC LIMIT ?";
        List<String[]> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{ String.valueOf(rs.getDate("attendance_date")), rs.getString("status") });
                }
            }
        }
        return list;
    }

    /** studentId -> status already recorded for the given date (for pre-selection). */
    public Map<Integer, String> getStatusForDate(String date) throws SQLException {
        String sql = "SELECT student_id, status FROM attendance WHERE attendance_date = ?";
        Map<Integer, String> map = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getInt("student_id"), rs.getString("status"));
            }
        }
        return map;
    }

    /**
     * Per-student attendance report. {@code from}/{@code to} are optional
     * (null/blank = all dates). Includes students with zero records.
     */
    public List<AttendanceSummary> report(String from, String to) throws SQLException {
        boolean hasFrom = from != null && !from.isEmpty();
        boolean hasTo   = to   != null && !to.isEmpty();

        StringBuilder sql = new StringBuilder(
            "SELECT s.student_id, s.admission_no, s.full_name, s.class_name, "
          + " SUM(a.status='Present') AS present, SUM(a.status='Absent') AS absent, "
          + " SUM(a.status='Late') AS late, SUM(a.status='Leave') AS leave_cnt "
          + "FROM students s LEFT JOIN attendance a ON a.student_id = s.student_id");
        if (hasFrom || hasTo) {
            sql.append(" AND a.attendance_date");
            if (hasFrom && hasTo) sql.append(" BETWEEN ? AND ?");
            else if (hasFrom)     sql.append(" >= ?");
            else                  sql.append(" <= ?");
        }
        sql.append(" GROUP BY s.student_id, s.admission_no, s.full_name, s.class_name "
                 + "ORDER BY s.full_name");

        List<AttendanceSummary> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasFrom) ps.setString(idx++, from);
            if (hasTo)   ps.setString(idx++, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AttendanceSummary a = new AttendanceSummary();
                    a.setStudentId(rs.getInt("student_id"));
                    a.setAdmissionNo(rs.getString("admission_no"));
                    a.setFullName(rs.getString("full_name"));
                    a.setClassName(rs.getString("class_name"));
                    a.setPresent(rs.getInt("present"));
                    a.setAbsent(rs.getInt("absent"));
                    a.setLate(rs.getInt("late"));
                    a.setLeave(rs.getInt("leave_cnt"));
                    list.add(a);
                }
            }
        }
        return list;
    }
}
