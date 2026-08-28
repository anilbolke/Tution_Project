package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.AttendanceDAO;
import com.tution.model.Student;

/** The logged-in student's own attendance. */
@WebServlet("/student-attendance")
public class StudentAttendanceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final AttendanceDAO attendanceDAO = new AttendanceDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        Student s = (session == null) ? null : (Student) session.getAttribute("student");
        if (s == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        try {
            req.setAttribute("summary", attendanceDAO.summaryForStudent(s.getStudentId()));
            req.setAttribute("recent", attendanceDAO.recentForStudent(s.getStudentId(), 30));
        } catch (SQLException e) {
            getServletContext().log("Student attendance failed", e);
            req.setAttribute("error", "Could not load your attendance. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/student_attendance.jsp").forward(req, resp);
    }
}
