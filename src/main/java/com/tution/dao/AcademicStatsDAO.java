package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.util.DBConnection;

/**
 * The numbers behind the teacher's dashboard: today's class register, exams
 * waiting on marks, and what the month's attendance looks like.
 *
 * Everything is institute-wide. The schema has no teacher-to-class mapping —
 * nothing records which teacher owns which class — so a "my classes" view would
 * have to invent an association that does not exist. Rather than guess, this
 * shows every class and lets the teacher pick; if you later tell the system who
 * teaches what, the queries here take a user_id and nothing else changes.
 */
public class AcademicStatsDAO {

    /** One row per class: how many students, and how many marked today. */
    public List<Map<String,Object>> todayByClass(String date) throws SQLException {
        String sql = "SELECT s.class_name, COUNT(*) AS students, "
                   + "  COUNT(a.attendance_id) AS marked, "
                   + "  SUM(a.status = 'Present') AS present, "
                   + "  SUM(a.status = 'Absent')  AS absent, "
                   + "  SUM(a.status = 'Late')    AS late, "
                   + "  SUM(a.status = 'Leave')   AS onleave "
                   + "  FROM students s "
                   + "  LEFT JOIN attendance a ON a.student_id = s.student_id "
                   + "        AND a.attendance_date = ? "
                   + " WHERE s.is_active = 1 "
                   + " GROUP BY s.class_name ORDER BY s.class_name";
        List<Map<String,Object>> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    String cn = rs.getString("class_name");
                    m.put("className", cn == null || cn.isEmpty() ? "(no class set)" : cn);
                    m.put("students", rs.getInt("students"));
                    m.put("marked",   rs.getInt("marked"));
                    m.put("present",  rs.getInt("present"));
                    m.put("absent",   rs.getInt("absent"));
                    m.put("late",     rs.getInt("late"));
                    m.put("onleave",  rs.getInt("onleave"));
                    out.add(m);
                }
            }
        }
        return out;
    }

    /**
     * Exams with their marks progress.
     *
     * "Expected" is students in the exam's class times its subject count — the
     * subjects are a comma-separated string on the exam, so the count comes from
     * the commas. Crude, but it is the shape the exam module already stores, and
     * a dashboard that said "marks entered: 12" without a denominator would not
     * tell a teacher whether they were finished.
     */
    public List<Map<String,Object>> examProgress(int limit) throws SQLException {
        String sql = "SELECT e.exam_id, e.exam_name, e.exam_date, e.class_name, e.subjects, "
                   + "  (LENGTH(e.subjects) - LENGTH(REPLACE(e.subjects, ',', '')) + 1) AS n_sub, "
                   + "  (SELECT COUNT(*) FROM exam_marks m WHERE m.exam_id = e.exam_id) AS entered, "
                   + "  (SELECT COUNT(*) FROM students s WHERE s.is_active = 1 "
                   + "     AND (e.class_name IS NULL OR e.class_name = '' "
                   + "          OR s.class_name = e.class_name)) AS n_stud "
                   + "  FROM exams e WHERE e.exam_type = 'INTERNAL' "
                   + " ORDER BY e.exam_date IS NULL, e.exam_date DESC, e.exam_id DESC LIMIT ?";
        List<Map<String,Object>> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("examId",   rs.getInt("exam_id"));
                    m.put("name",     rs.getString("exam_name"));
                    m.put("date",     rs.getString("exam_date"));
                    m.put("className",rs.getString("class_name"));
                    int expected = rs.getInt("n_sub") * rs.getInt("n_stud");
                    int entered  = rs.getInt("entered");
                    m.put("expected", expected);
                    m.put("entered",  entered);
                    m.put("pct", expected == 0 ? 0
                                 : Math.min(100, entered * 100 / expected));
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** The tiles across the top of the teacher's dashboard. */
    public Map<String,Object> teacherTiles(String date, int year, int month) throws SQLException {
        Map<String,Object> d = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection()) {
            d.put("students", scalar(con,
                "SELECT COUNT(*) FROM students WHERE is_active = 1"));
            d.put("classes", scalar(con,
                "SELECT COUNT(DISTINCT class_name) FROM students WHERE is_active = 1"));
            d.put("markedToday", scalar(con,
                "SELECT COUNT(*) FROM attendance WHERE attendance_date = ?", date));
            d.put("absentToday", scalar(con,
                "SELECT COUNT(*) FROM attendance WHERE attendance_date = ? AND status = 'Absent'",
                date));
            // Attendance rate for the month, over marks actually taken.
            d.put("monthMarks", scalar(con,
                "SELECT COUNT(*) FROM attendance WHERE YEAR(attendance_date) = ? "
              + "AND MONTH(attendance_date) = ?", year, month));
            d.put("monthPresent", scalar(con,
                "SELECT COUNT(*) FROM attendance WHERE YEAR(attendance_date) = ? "
              + "AND MONTH(attendance_date) = ? AND status IN ('Present','Late')", year, month));
            d.put("exams", scalar(con,
                "SELECT COUNT(*) FROM exams WHERE exam_type = 'INTERNAL'"));
            d.put("upcoming", scalar(con,
                "SELECT COUNT(*) FROM exams WHERE exam_type = 'INTERNAL' AND exam_date >= ?", date));
            d.put("materials", scalar(con, "SELECT COUNT(*) FROM materials"));
            d.put("openTickets", scalar(con,
                "SELECT COUNT(*) FROM tickets WHERE status IN ('OPEN','IN_PROGRESS')"));
        }
        return d;
    }

    private int scalar(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Integer) ps.setInt(i + 1, (Integer) args[i]);
                else                            ps.setString(i + 1, String.valueOf(args[i]));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
