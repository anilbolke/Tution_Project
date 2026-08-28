package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.OnlineExam;
import com.tution.model.OnlineExamAttempt;
import com.tution.model.OnlineExamQuestion;
import com.tution.util.DBConnection;

/** Data access for online MCQ exams: the paper, its questions, and student attempts. */
public class OnlineExamDAO {

    private static final int BATCH = 500;

    /* ─── exams (staff side) ─── */

    public List<OnlineExam> findAll() throws SQLException {
        String sql =
              "SELECT e.online_exam_id, e.title, e.class_name, e.duration_minutes, e.is_active, "
            + "       e.created_by, e.created_at, "
            + "       (SELECT COUNT(*) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qcount, "
            + "       (SELECT COALESCE(SUM(q.marks),0) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qmarks "
            + "  FROM online_exams e ORDER BY e.created_at DESC";
        List<OnlineExam> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapExam(rs));
        }
        return out;
    }

    /** One page of exams, newest first, for the "Your exams" list. */
    public List<OnlineExam> findPage(int offset, int limit) throws SQLException {
        String sql =
              "SELECT e.online_exam_id, e.title, e.class_name, e.duration_minutes, e.is_active, "
            + "       e.created_by, e.created_at, "
            + "       (SELECT COUNT(*) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qcount, "
            + "       (SELECT COALESCE(SUM(q.marks),0) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qmarks "
            + "  FROM online_exams e ORDER BY e.created_at DESC LIMIT ? OFFSET ?";
        List<OnlineExam> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapExam(rs));
            }
        }
        return out;
    }

    public int countAll() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM online_exams");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public OnlineExam findById(int examId) throws SQLException {
        String sql =
              "SELECT e.online_exam_id, e.title, e.class_name, e.duration_minutes, e.is_active, "
            + "       e.created_by, e.created_at, "
            + "       (SELECT COUNT(*) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qcount, "
            + "       (SELECT COALESCE(SUM(q.marks),0) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qmarks "
            + "  FROM online_exams e WHERE e.online_exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapExam(rs) : null;
            }
        }
    }

    private OnlineExam mapExam(ResultSet rs) throws SQLException {
        OnlineExam e = new OnlineExam();
        e.setOnlineExamId(rs.getInt("online_exam_id"));
        e.setTitle(rs.getString("title"));
        e.setClassName(rs.getString("class_name"));
        e.setDurationMinutes(rs.getInt("duration_minutes"));
        e.setActive(rs.getInt("is_active") == 1);
        e.setCreatedBy(rs.getString("created_by"));
        e.setCreatedAt(rs.getString("created_at"));
        e.setQuestionCount(rs.getInt("qcount"));
        e.setTotalMarks(rs.getInt("qmarks"));
        return e;
    }

    public int createExam(OnlineExam e) throws SQLException {
        String sql = "INSERT INTO online_exams (title, class_name, duration_minutes, created_by) "
                   + "VALUES (?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getTitle());
            ps.setString(2, e.getClassName());
            ps.setInt(3, e.getDurationMinutes());
            ps.setString(4, e.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void setActive(int examId, boolean active) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE online_exams SET is_active = ? WHERE online_exam_id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, examId);
            ps.executeUpdate();
        }
    }

    /* ─── questions ─── */

    public List<OnlineExamQuestion> findQuestions(int examId) throws SQLException {
        String sql = "SELECT question_id, online_exam_id, subject, chapter, question_text, "
                   + "       option_a, option_b, option_c, option_d, correct_answer, difficulty, marks, sort_order "
                   + "  FROM online_exam_questions WHERE online_exam_id = ? ORDER BY sort_order, question_id";
        List<OnlineExamQuestion> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapQuestion(rs));
            }
        }
        return out;
    }

    private OnlineExamQuestion mapQuestion(ResultSet rs) throws SQLException {
        OnlineExamQuestion q = new OnlineExamQuestion();
        q.setQuestionId(rs.getInt("question_id"));
        q.setOnlineExamId(rs.getInt("online_exam_id"));
        q.setSubject(rs.getString("subject"));
        q.setChapter(rs.getString("chapter"));
        q.setQuestionText(rs.getString("question_text"));
        q.setOptionA(rs.getString("option_a"));
        q.setOptionB(rs.getString("option_b"));
        q.setOptionC(rs.getString("option_c"));
        q.setOptionD(rs.getString("option_d"));
        q.setCorrectAnswer(rs.getString("correct_answer"));
        q.setDifficulty(rs.getString("difficulty"));
        q.setMarks(rs.getInt("marks"));
        q.setSortOrder(rs.getInt("sort_order"));
        return q;
    }

    /** Appends questions to an exam's paper — existing questions are untouched. */
    public int insertQuestions(int examId, List<OnlineExamQuestion> qs) throws SQLException {
        if (qs.isEmpty()) return 0;
        String sql = "INSERT INTO online_exam_questions "
                   + "(online_exam_id, subject, chapter, question_text, option_a, option_b, option_c, option_d, "
                   + " correct_answer, difficulty, marks, sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            int nextOrder = nextSortOrder(con, examId);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int n = 0;
                for (OnlineExamQuestion q : qs) {
                    ps.setInt(1, examId);
                    setNullable(ps, 2, q.getSubject());
                    setNullable(ps, 3, q.getChapter());
                    ps.setString(4, q.getQuestionText());
                    ps.setString(5, q.getOptionA());
                    ps.setString(6, q.getOptionB());
                    ps.setString(7, q.getOptionC());
                    ps.setString(8, q.getOptionD());
                    ps.setString(9, q.getCorrectAnswer());
                    ps.setString(10, q.getDifficulty());
                    ps.setInt(11, q.getMarks());
                    ps.setInt(12, nextOrder++);
                    ps.addBatch();
                    if (++n % BATCH == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            con.commit();
            return qs.size();
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    private int nextSortOrder(Connection con, int examId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(MAX(sort_order),-1) + 1 FROM online_exam_questions WHERE online_exam_id = ?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void deleteQuestion(int questionId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "DELETE FROM online_exam_questions WHERE question_id = ?")) {
            ps.setInt(1, questionId);
            ps.executeUpdate();
        }
    }

    /* ─── student side ─── */

    /** Active exams for a class, newest first. */
    public List<OnlineExam> findForClass(String className) throws SQLException {
        String sql =
              "SELECT e.online_exam_id, e.title, e.class_name, e.duration_minutes, e.is_active, "
            + "       e.created_by, e.created_at, "
            + "       (SELECT COUNT(*) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qcount, "
            + "       (SELECT COALESCE(SUM(q.marks),0) FROM online_exam_questions q WHERE q.online_exam_id = e.online_exam_id) AS qmarks "
            + "  FROM online_exams e "
            + " WHERE e.is_active = 1 AND e.class_name = ? "
            + " ORDER BY e.created_at DESC";
        List<OnlineExam> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, className);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapExam(rs));
            }
        }
        return out;
    }

    /** Every attempt this student has made, keyed by online_exam_id. */
    public Map<Integer, OnlineExamAttempt> attemptsForStudent(int studentId) throws SQLException {
        String sql = "SELECT attempt_id, online_exam_id, student_id, started_at, submitted_at, "
                   + "       total_marks, score, status FROM online_exam_attempts WHERE student_id = ?";
        Map<Integer, OnlineExamAttempt> out = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OnlineExamAttempt a = mapAttempt(rs);
                    out.put(a.getOnlineExamId(), a);
                }
            }
        }
        return out;
    }

    /**
     * One page of attempts across every online exam, for the staff-side
     * results tab. {@code examId} and {@code className} are optional filters
     * — either or both may be null/blank to mean "all". Newest submission
     * first.
     */
    public List<OnlineExamAttempt> findAllAttempts(Integer examId, String className, int offset, int limit)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
              "SELECT a.attempt_id, a.online_exam_id, a.student_id, a.started_at, a.submitted_at, "
            + "       a.total_marks, a.score, a.status, s.full_name, s.admission_no, "
            + "       e.title AS exam_title, e.class_name, e.duration_minutes "
            + "  FROM online_exam_attempts a "
            + "  JOIN students s ON s.student_id = a.student_id "
            + "  JOIN online_exams e ON e.online_exam_id = a.online_exam_id "
            + " WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (examId != null) { sql.append(" AND a.online_exam_id = ? "); args.add(examId); }
        if (className != null && !className.trim().isEmpty()) {
            sql.append(" AND e.class_name = ? "); args.add(className.trim());
        }
        sql.append(" ORDER BY (a.status = 'SUBMITTED') DESC, a.submitted_at DESC, a.started_at DESC");
        sql.append(" LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        List<OnlineExamAttempt> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OnlineExamAttempt a = mapAttempt(rs);
                    a.setStudentName(rs.getString("full_name"));
                    a.setAdmissionNo(rs.getString("admission_no"));
                    a.setExamTitle(rs.getString("exam_title"));
                    a.setClassName(rs.getString("class_name"));
                    a.setDurationMinutes(rs.getInt("duration_minutes"));
                    out.add(a);
                }
            }
        }
        return out;
    }

    public int countAttempts(Integer examId, String className) throws SQLException {
        StringBuilder sql = new StringBuilder(
              "SELECT COUNT(*) FROM online_exam_attempts a "
            + "JOIN online_exams e ON e.online_exam_id = a.online_exam_id WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (examId != null) { sql.append(" AND a.online_exam_id = ? "); args.add(examId); }
        if (className != null && !className.trim().isEmpty()) {
            sql.append(" AND e.class_name = ? "); args.add(className.trim());
        }
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * {@code [submittedCount, scoreSum, totalMarksSum]} across every attempt
     * matching the filters — not just the current page — so the summary
     * line above a paginated table stays accurate regardless of page size.
     */
    public int[] attemptStats(Integer examId, String className) throws SQLException {
        StringBuilder sql = new StringBuilder(
              "SELECT COUNT(*) AS submitted, "
            + "       COALESCE(SUM(a.score),0) AS score_sum, COALESCE(SUM(a.total_marks),0) AS total_sum "
            + "  FROM online_exam_attempts a "
            + "  JOIN online_exams e ON e.online_exam_id = a.online_exam_id "
            + " WHERE a.status = 'SUBMITTED' ");
        List<Object> args = new ArrayList<>();
        if (examId != null) { sql.append(" AND a.online_exam_id = ? "); args.add(examId); }
        if (className != null && !className.trim().isEmpty()) {
            sql.append(" AND e.class_name = ? "); args.add(className.trim());
        }
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[] { rs.getInt("submitted"), rs.getInt("score_sum"), rs.getInt("total_sum") };
                }
                return new int[] { 0, 0, 0 };
            }
        }
    }

    /**
     * Every student attempt at one exam, for the staff-side results view.
     * Submitted attempts are ranked highest score first; anyone still
     * IN_PROGRESS is listed after, oldest start first.
     */
    public List<OnlineExamAttempt> findAttemptsForExam(int examId) throws SQLException {
        String sql = "SELECT a.attempt_id, a.online_exam_id, a.student_id, a.started_at, a.submitted_at, "
                   + "       a.total_marks, a.score, a.status, s.full_name, s.admission_no "
                   + "  FROM online_exam_attempts a "
                   + "  JOIN students s ON s.student_id = a.student_id "
                   + " WHERE a.online_exam_id = ? "
                   + " ORDER BY (a.status = 'SUBMITTED') DESC, a.score DESC, a.started_at";
        List<OnlineExamAttempt> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OnlineExamAttempt a = mapAttempt(rs);
                    a.setStudentName(rs.getString("full_name"));
                    a.setAdmissionNo(rs.getString("admission_no"));
                    out.add(a);
                }
            }
        }
        return out;
    }

    public OnlineExamAttempt findAttempt(int examId, int studentId) throws SQLException {
        String sql = "SELECT attempt_id, online_exam_id, student_id, started_at, submitted_at, "
                   + "       total_marks, score, status FROM online_exam_attempts "
                   + " WHERE online_exam_id = ? AND student_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapAttempt(rs) : null;
            }
        }
    }

    private OnlineExamAttempt mapAttempt(ResultSet rs) throws SQLException {
        OnlineExamAttempt a = new OnlineExamAttempt();
        a.setAttemptId(rs.getInt("attempt_id"));
        a.setOnlineExamId(rs.getInt("online_exam_id"));
        a.setStudentId(rs.getInt("student_id"));
        a.setStartedAt(rs.getString("started_at"));
        a.setSubmittedAt(rs.getString("submitted_at"));
        a.setTotalMarks(rs.getInt("total_marks"));
        a.setScore(rs.getInt("score"));
        a.setStatus(rs.getString("status"));
        return a;
    }

    /**
     * Returns the student's existing attempt at this exam, creating an
     * IN_PROGRESS one if this is their first time opening it. Never creates a
     * second row — the unique key (online_exam_id, student_id) is the same
     * guarantee, this just avoids relying on catching that exception.
     */
    public OnlineExamAttempt startOrResumeAttempt(int examId, int studentId, int totalMarks) throws SQLException {
        OnlineExamAttempt existing = findAttempt(examId, studentId);
        if (existing != null) return existing;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO online_exam_attempts (online_exam_id, student_id, total_marks) VALUES (?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            ps.setInt(3, totalMarks);
            ps.executeUpdate();
        }
        return findAttempt(examId, studentId);
    }

    /**
     * Scores and closes an attempt in one transaction. {@code answers} maps
     * question_id to the selected letter (A-D), or is absent for a skipped
     * question.
     */
    public void submitAttempt(int attemptId, List<OnlineExamQuestion> questions,
                              Map<Integer, String> answers) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int score = 0;
            String ansSql = "INSERT INTO online_exam_answers "
                          + "(attempt_id, question_id, selected_answer, is_correct, marks_awarded) "
                          + "VALUES (?,?,?,?,?) "
                          + "ON DUPLICATE KEY UPDATE selected_answer = VALUES(selected_answer), "
                          + " is_correct = VALUES(is_correct), marks_awarded = VALUES(marks_awarded)";
            try (PreparedStatement ps = con.prepareStatement(ansSql)) {
                for (OnlineExamQuestion q : questions) {
                    String selected = answers.get(q.getQuestionId());
                    boolean correct = selected != null && selected.equalsIgnoreCase(q.getCorrectAnswer());
                    int awarded = correct ? q.getMarks() : 0;
                    score += awarded;

                    ps.setInt(1, attemptId);
                    ps.setInt(2, q.getQuestionId());
                    if (selected == null) ps.setNull(3, Types.CHAR);
                    else ps.setString(3, selected.toUpperCase());
                    ps.setInt(4, correct ? 1 : 0);
                    ps.setInt(5, awarded);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE online_exam_attempts SET score = ?, status = 'SUBMITTED', "
                  + "submitted_at = CURRENT_TIMESTAMP WHERE attempt_id = ? AND status = 'IN_PROGRESS'")) {
                ps.setInt(1, score);
                ps.setInt(2, attemptId);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    /** The student's saved answers for a submitted attempt, keyed by question_id. */
    public Map<Integer, String> findAnswers(int attemptId) throws SQLException {
        Map<Integer, String> out = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT question_id, selected_answer FROM online_exam_answers WHERE attempt_id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getInt("question_id"), rs.getString("selected_answer"));
            }
        }
        return out;
    }

    /* ─── helpers ─── */

    private static void setNullable(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(i, Types.VARCHAR);
        else ps.setString(i, v.trim());
    }
}
