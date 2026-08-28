package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.OnlineExamDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.OnlineExam;
import com.tution.model.Student;
import com.tution.model.User;
import com.tution.service.OnlineExamService;

/**
 * Staff screen for online MCQ exams. Creating an exam is ONE step — title,
 * class, duration and its question bank are all submitted together at the
 * top of the page, rather than an empty exam first and questions added in a
 * separate trip — since a paper isn't really usable until it has questions
 * anyway. More questions can still be added later from the exam's own panel
 * further down the page. Students of that class see and take it from
 * {@code /student-exams} once it is published.
 */
@WebServlet("/online-exams")
public class OnlineExamServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 10;

    private final OnlineExamDAO examDAO = new OnlineExamDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        try {
            int page = Math.max(1, parseInt(req.getParameter("page"), 1));
            int totalCount = examDAO.countAll();
            int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
            if (page > totalPages) page = totalPages;

            req.setAttribute("exams", examDAO.findPage((page - 1) * PAGE_SIZE, PAGE_SIZE));
            req.setAttribute("currentPage", page);
            req.setAttribute("totalPages", totalPages);
            req.setAttribute("totalCount", totalCount);
            req.setAttribute("classes", distinctClasses());

            Integer examId = intOrNull(req.getParameter("examId"));
            if (examId != null) {
                OnlineExam exam = examDAO.findById(examId);
                if (exam != null) {
                    req.setAttribute("exam", exam);
                    req.setAttribute("questions", examDAO.findQuestions(examId));
                    req.setAttribute("attempts", examDAO.findAttemptsForExam(examId));
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Load online exams failed", e);
            req.setAttribute("error", "Could not load online exams. Please try again.");
        }

        HttpSession session = req.getSession();
        req.setAttribute("flash", session.getAttribute("flash"));
        req.setAttribute("flashError", session.getAttribute("flashError"));
        session.removeAttribute("flash");
        session.removeAttribute("flashError");

        // What was typed into the "create a new exam" form when it failed
        // validation, so a bad line in a 40-question paste doesn't mean
        // retyping the title and re-pasting everything from scratch.
        req.setAttribute("stickyTitle", session.getAttribute("stickyTitle"));
        req.setAttribute("stickyClassName", session.getAttribute("stickyClassName"));
        req.setAttribute("stickyDuration", session.getAttribute("stickyDuration"));
        req.setAttribute("stickyQuestionsText", session.getAttribute("stickyQuestionsText"));
        session.removeAttribute("stickyTitle");
        session.removeAttribute("stickyClassName");
        session.removeAttribute("stickyDuration");
        session.removeAttribute("stickyQuestionsText");

        req.getRequestDispatcher("/WEB-INF/views/online_exams.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        String action = req.getParameter("action");
        try {
            if ("create".equals(action)) {
                createExam(req, resp);
            } else if ("questions".equals(action)) {
                addQuestions(req, resp);
            } else if ("toggle".equals(action)) {
                toggleExam(req, resp);
            } else if ("delete-question".equals(action)) {
                deleteQuestion(req, resp);
            } else {
                resp.sendRedirect(req.getContextPath() + "/online-exams");
            }
        } catch (SQLException e) {
            getServletContext().log("Online exam save failed", e);
            req.getSession().setAttribute("flashError", "Database error: " + e.getMessage());
            resp.sendRedirect(req.getContextPath() + "/online-exams");
        }
    }

    private void createExam(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        String title = trim(req.getParameter("title"));
        String className = trim(req.getParameter("className"));
        int duration = parseInt(req.getParameter("durationMinutes"), 30);
        if (duration < 1) duration = 30;
        String questionsText = req.getParameter("questionsText");

        String problem = null;
        OnlineExamService.Check check = null;
        if (title.isEmpty() || className.isEmpty()) {
            problem = "Give the exam a title and a class.";
        } else {
            check = OnlineExamService.parseQuestions(questionsText, className);
            if (!check.ok()) problem = check.problemText();
        }

        if (problem != null) {
            HttpSession session = req.getSession();
            session.setAttribute("flashError", problem);
            session.setAttribute("stickyTitle", title);
            session.setAttribute("stickyClassName", className);
            session.setAttribute("stickyDuration", duration);
            session.setAttribute("stickyQuestionsText", questionsText);
            resp.sendRedirect(req.getContextPath() + "/online-exams");
            return;
        }

        OnlineExam e = new OnlineExam();
        e.setTitle(title);
        e.setClassName(className);
        e.setDurationMinutes(duration);
        User u = (User) req.getSession().getAttribute("user");
        e.setCreatedBy(u == null ? "system" : u.getUsername());

        int id = examDAO.createExam(e);
        examDAO.insertQuestions(id, check.value);
        req.getSession().setAttribute("flash",
                "Exam created with " + check.value.size() + " question(s) — published, Class "
              + className + " can take it now.");
        resp.sendRedirect(req.getContextPath() + "/online-exams?examId=" + id);
    }

    private void addQuestions(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        Integer examId = intOrNull(req.getParameter("examId"));
        OnlineExam exam = examId == null ? null : examDAO.findById(examId);
        if (exam == null) {
            req.getSession().setAttribute("flashError", "That exam no longer exists.");
            resp.sendRedirect(req.getContextPath() + "/online-exams");
            return;
        }

        String raw = req.getParameter("questionsText");
        OnlineExamService.Check check = OnlineExamService.parseQuestions(raw, exam.getClassName());
        if (!check.ok()) {
            req.getSession().setAttribute("flashError", check.problemText());
            resp.sendRedirect(req.getContextPath() + "/online-exams?examId=" + examId);
            return;
        }

        int added = examDAO.insertQuestions(examId, check.value);
        req.getSession().setAttribute("flash", added + " question(s) added.");
        resp.sendRedirect(req.getContextPath() + "/online-exams?examId=" + examId);
    }

    private void toggleExam(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        Integer examId = intOrNull(req.getParameter("examId"));
        if (examId == null) { resp.sendRedirect(req.getContextPath() + "/online-exams"); return; }
        OnlineExam exam = examDAO.findById(examId);
        if (exam == null) { resp.sendRedirect(req.getContextPath() + "/online-exams"); return; }

        examDAO.setActive(examId, !exam.isActive());
        req.getSession().setAttribute("flash",
                exam.isActive() ? "Exam closed to students." : "Exam published — students can now take it.");
        resp.sendRedirect(req.getContextPath() + "/online-exams?examId=" + examId);
    }

    private void deleteQuestion(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        Integer questionId = intOrNull(req.getParameter("questionId"));
        Integer examId = intOrNull(req.getParameter("examId"));
        if (questionId != null) examDAO.deleteQuestion(questionId);
        resp.sendRedirect(req.getContextPath() + "/online-exams" + (examId == null ? "" : "?examId=" + examId));
    }

    private TreeSet<String> distinctClasses() throws SQLException {
        List<Student> all = studentDAO.findAll();
        TreeSet<String> classes = new TreeSet<>();
        for (Student s : all) if (s.getClassName() != null && !s.getClassName().trim().isEmpty()) {
            classes.add(s.getClassName());
        }
        return classes;
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static Integer intOrNull(String v) {
        return (v == null || !v.trim().matches("\\d+")) ? null : Integer.valueOf(v.trim());
    }
}
