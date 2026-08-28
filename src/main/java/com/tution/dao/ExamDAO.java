package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.Exam;
import com.tution.util.DBConnection;

/** Data-access for exams. */
public class ExamDAO {

    public int insert(Exam e) throws SQLException {
        String sql = "INSERT INTO exams (exam_name, exam_date, class_name, subjects, max_per_subject, created_by) "
                   + "VALUES (?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getExamName());
            setNullable(ps, 2, e.getExamDate());
            setNullable(ps, 3, e.getClassName());
            ps.setString(4, e.getSubjects());
            ps.setInt(5, e.getMaxPerSubject());
            ps.setString(6, e.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /** Every column the model carries — keep map() and these SELECTs in step. */
    private static final String COLS =
          "exam_id, exam_name, exam_date, class_name, subjects, max_per_subject, "
        + "created_by, created_at, exam_type, template_id, total_questions, "
        + "mark_correct, mark_wrong, max_score, roll_block_from, roll_block_to, "
        + "roll_next, result_published, exam_fee";

    public List<Exam> findAll() throws SQLException {
        String sql = "SELECT " + COLS + " FROM exams ORDER BY exam_id DESC";
        List<Exam> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** HAMSE / HACKSE / HAT only — the OMR-scored exams students are imported for. */
    public List<Exam> findScholarshipExams() throws SQLException {
        String sql = "SELECT " + COLS + " FROM exams WHERE exam_type <> 'INTERNAL' "
                   + "ORDER BY exam_date DESC, exam_id DESC";
        List<Exam> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Exam findById(int examId) throws SQLException {
        String sql = "SELECT " + COLS + " FROM exams WHERE exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private Exam map(ResultSet rs) throws SQLException {
        Exam e = new Exam();
        e.setExamId(rs.getInt("exam_id"));
        e.setExamName(rs.getString("exam_name"));
        e.setExamDate(String.valueOf(rs.getDate("exam_date")));
        e.setClassName(rs.getString("class_name"));
        e.setSubjects(rs.getString("subjects"));
        e.setMaxPerSubject(rs.getInt("max_per_subject"));
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        e.setExamType(rs.getString("exam_type"));
        int tpl = rs.getInt("template_id");
        e.setTemplateId(rs.wasNull() ? null : tpl);
        e.setTotalQuestions(rs.getInt("total_questions"));
        e.setMarkCorrect(rs.getInt("mark_correct"));
        e.setMarkWrong(rs.getInt("mark_wrong"));
        e.setMaxScore(rs.getInt("max_score"));
        e.setRollBlockFrom(rs.getInt("roll_block_from"));
        e.setRollBlockTo(rs.getInt("roll_block_to"));
        e.setRollNext(rs.getInt("roll_next"));
        e.setResultPublished(rs.getBoolean("result_published"));
        e.setExamFee(rs.getBigDecimal("exam_fee"));
        return e;
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty() || "null".equals(val)) ps.setNull(idx, java.sql.Types.VARCHAR);
        else ps.setString(idx, val.trim());
    }
}
