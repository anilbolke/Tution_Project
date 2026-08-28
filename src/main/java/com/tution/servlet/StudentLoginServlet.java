package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.StudentDAO;
import com.tution.model.Student;

/** Student portal login: admission number + registered mobile. */
@WebServlet("/student-login")
public class StudentLoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendRedirect(req.getContextPath() + "/student-login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String admissionNo = trim(req.getParameter("admissionNo"));
        String mobile      = trim(req.getParameter("mobile"));

        if (admissionNo.isEmpty() || mobile.isEmpty()) {
            fail(req, resp, "Please enter your admission number and mobile.", admissionNo);
            return;
        }
        try {
            Student s = studentDAO.authenticate(admissionNo, mobile);
            if (s != null) {
                HttpSession session = req.getSession(true);
                session.setAttribute("student", s);
                session.setMaxInactiveInterval(30 * 60);
                resp.sendRedirect(req.getContextPath() + "/student-dashboard.jsp");
            } else {
                fail(req, resp, "Invalid admission number or mobile number.", admissionNo);
            }
        } catch (SQLException e) {
            getServletContext().log("Student login error", e);
            fail(req, resp, "Server error. Please try again later.", admissionNo);
        }
    }

    private void fail(HttpServletRequest req, HttpServletResponse resp, String msg, String adm)
            throws ServletException, IOException {
        req.setAttribute("error", msg);
        req.setAttribute("admissionNo", adm);
        req.getRequestDispatcher("/student-login.jsp").forward(req, resp);
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
