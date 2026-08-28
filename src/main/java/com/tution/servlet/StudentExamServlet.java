package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
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
import com.tution.model.Student;

/**
 * The student's list of online exams for their own class, with their
 * attempt status against each — Not started / In progress / a score once
 * submitted. Guarded by {@code AuthFilter}'s "/student-" prefix rule.
 */
@WebServlet("/student-exams")
public class StudentExamServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final OnlineExamDAO examDAO = new OnlineExamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Student student = student(req);
        if (student == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        try {
            String className = student.getClassName();
            List<OnlineExam> exams = (className == null || className.trim().isEmpty())
                    ? java.util.Collections.emptyList()
                    : examDAO.findForClass(className);
            Map<Integer, OnlineExamAttempt> attempts = examDAO.attemptsForStudent(student.getStudentId());
            req.setAttribute("exams", exams);
            req.setAttribute("attempts", attempts);
        } catch (SQLException e) {
            getServletContext().log("Load student exams failed", e);
            req.setAttribute("error", "Could not load exams. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/student_exams.jsp").forward(req, resp);
    }

    private Student student(HttpServletRequest req) {
        HttpSession sn = req.getSession(false);
        return (sn == null) ? null : (Student) sn.getAttribute("student");
    }
}
