package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tution.util.DBConnection;

/** Data-access for subject-wise exam marks. */
public class ExamMarkDAO {

    /**
     * Upserts marks for an exam.
     * @param marks studentId -> (subject -> marks)
     */
    public void saveMarks(int examId, Map<Integer, Map<String, Integer>> marks) throws SQLException {
        String sql = "INSERT INTO exam_marks (exam_id, student_id, subject, marks) VALUES (?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE marks = VALUES(marks)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (Map.Entry<Integer, Map<String, Integer>> stu : marks.entrySet()) {
                for (Map.Entry<String, Integer> sub : stu.getValue().entrySet()) {
                    ps.setInt(1, examId);
                    ps.setInt(2, stu.getKey());
                    ps.setString(3, sub.getKey());
                    ps.setInt(4, sub.getValue());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    /** All marks for an exam: studentId -> (subject -> marks). */
    public Map<Integer, Map<String, Integer>> getMarks(int examId) throws SQLException {
        String sql = "SELECT student_id, subject, marks FROM exam_marks WHERE exam_id = ?";
        Map<Integer, Map<String, Integer>> map = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sid = rs.getInt("student_id");
                    map.computeIfAbsent(sid, k -> new LinkedHashMap<>())
                       .put(rs.getString("subject"), rs.getInt("marks"));
                }
            }
        }
        return map;
    }
}
