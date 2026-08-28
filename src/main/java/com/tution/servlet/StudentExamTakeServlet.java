package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.OnlineExamDAO;
import com.tution.model.OnlineExam;
import com.tution.model.OnlineExamAttempt;
import com.tution.model.OnlineExamQuestion;
import com.tution.model.Student;

/**
 * Taking one online exam: starts (or resumes) the student's single attempt,
 * renders the paper with a client-side countdown, and scores the submission.
 *
 * One attempt per student per exam (the DB unique key enforces it); once
 * SUBMITTED, this page only ever shows the result and the answer review.
 */
@WebServlet("/student-exam-take")
public class StudentExamTakeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final OnlineExamDAO examDAO = new OnlineExamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Student student = student(req);
        if (student == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        try {
            OnlineExam exam = loadEligibleExam(req, resp, student);
            if (exam == null) return;   // loadEligibleExam already redirected

            List<OnlineExamQuestion> questions = examDAO.findQuestions(exam.getOnlineExamId());
            OnlineExamAttempt attempt =
                    examDAO.startOrResumeAttempt(exam.getOnlineExamId(), student.getStudentId(), exam.getTotalMarks());

            req.setAttribute("exam", exam);
            req.setAttribute("questions", questions);
            req.setAttribute("attempt", attempt);
            if (attempt.isSubmitted()) {
                req.setAttribute("myAnswers", examDAO.findAnswers(attempt.getAttemptId()));
            }
        } catch (SQLException e) {
            getServletContext().log("Load exam-take failed", e);
            req.setAttribute("error", "Could not load this exam. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/student_exam_take.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Student student = student(req);
        if (student == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        try {
            OnlineExam exam = loadEligibleExam(req, resp, student);
            if (exam == null) return;

            OnlineExamAttempt attempt = examDAO.findAttempt(exam.getOnlineExamId(), student.getStudentId());
            if (attempt == null || attempt.isSubmitted()) {
                // Already submitted (a double-click, or the back button) — just show the result.
                resp.sendRedirect(req.getContextPath() + "/student-exam-take?examId=" + exam.getOnlineExamId());
                return;
            }

            List<OnlineExamQuestion> questions = examDAO.findQuestions(exam.getOnlineExamId());
            Map<Integer, String> answers = new HashMap<>();
            for (OnlineExamQuestion q : questions) {
                String v = req.getParameter("q_" + q.getQuestionId());
                if (v != null && !v.trim().isEmpty()) answers.put(q.getQuestionId(), v.trim());
            }
            examDAO.submitAttempt(attempt.getAttemptId(), questions, answers);
            resp.sendRedirect(req.getContextPath() + "/student-exam-take?examId=" + exam.getOnlineExamId());
        } catch (SQLException e) {
            getServletContext().log("Submit exam failed", e);
            req.setAttribute("error", "Could not submit. Please try again.");
            req.getRequestDispatcher("/WEB-INF/views/student_exam_take.jsp").forward(req, resp);
        }
    }

    /**
     * Loads the requested exam, checked against this student: must exist, be
     * active, and be set for the student's own class. On any failure this
     * redirects to the exam list with a message and returns null — callers
     * must stop when that happens.
     */
    private OnlineExam loadEligibleExam(HttpServletRequest req, HttpServletResponse resp, Student student)
            throws SQLException, IOException {
        Integer examId = intOrNull(req.getParameter("examId"));
        OnlineExam exam = examId == null ? null : examDAO.findById(examId);

        String className = student.getClassName();
        boolean classMatches = exam != null && className != null
                && className.equalsIgnoreCase(exam.getClassName());

        if (exam == null || !exam.isActive() || !classMatches) {
            req.getSession().setAttribute("flashError",
                    "That exam is not available to you.");
            resp.sendRedirect(req.getContextPath() + "/student-exams");
            return null;
        }
        return exam;
    }

    private Student student(HttpServletRequest req) {
        HttpSession sn = req.getSession(false);
        return (sn == null) ? null : (Student) sn.getAttribute("student");
    }

    private static Integer intOrNull(String v) {
        return (v == null || !v.trim().matches("\\d+")) ? null : Integer.valueOf(v.trim());
    }
}
