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
import com.tution.model.OnlineExamAttempt;
import com.tution.model.Student;

/**
 * The staff-side results tab: every student's attempt across every online
 * exam in one place, filterable by exam or by class — the aggregate view
 * that {@code /online-exams}'s per-exam Results card doesn't give you
 * without opening each exam one at a time.
 */
@WebServlet("/online-exam-results")
public class OnlineExamResultsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 20;

    private final OnlineExamDAO examDAO = new OnlineExamDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        try {
            List<OnlineExam> exams = examDAO.findAll();
            req.setAttribute("exams", exams);
            req.setAttribute("classes", distinctClasses());

            Integer examId = intOrNull(req.getParameter("examId"));
            String className = req.getParameter("className");
            req.setAttribute("fExamId", examId);
            req.setAttribute("fClassName", className);

            int totalCount = examDAO.countAttempts(examId, className);
            int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
            int page = Math.max(1, parseInt(req.getParameter("page"), 1));
            if (page > totalPages) page = totalPages;

            List<OnlineExamAttempt> attempts =
                    examDAO.findAllAttempts(examId, className, (page - 1) * PAGE_SIZE, PAGE_SIZE);
            req.setAttribute("attempts", attempts);
            req.setAttribute("currentPage", page);
            req.setAttribute("totalPages", totalPages);
            req.setAttribute("totalCount", totalCount);

            int[] stats = examDAO.attemptStats(examId, className);
            int submitted = stats[0], scoreSum = stats[1], totalSum = stats[2];
            req.setAttribute("submittedCount", submitted);
            req.setAttribute("avgPct", (submitted > 0 && totalSum > 0)
                    ? Math.round(scoreSum * 1000.0 / totalSum) / 10.0 : 0.0);
        } catch (SQLException e) {
            getServletContext().log("Load online exam results failed", e);
            req.setAttribute("error", "Could not load results. Please try again.");
        }

        req.getRequestDispatcher("/WEB-INF/views/online_exam_results.jsp").forward(req, resp);
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

    private static Integer intOrNull(String v) {
        return (v == null || !v.trim().matches("\\d+")) ? null : Integer.valueOf(v.trim());
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
