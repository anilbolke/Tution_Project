package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.MaterialDAO;
import com.tution.model.Student;

/** Study material (PDF) or e-content (VIDEO) for the student's class. */
@WebServlet("/student-resources")
public class StudentResourcesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final MaterialDAO materialDAO = new MaterialDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        Student s = (session == null) ? null : (Student) session.getAttribute("student");
        if (s == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        String type = "VIDEO".equalsIgnoreCase(req.getParameter("type")) ? "VIDEO" : "PDF";
        try {
            req.setAttribute("materials", materialDAO.findForClass(s.getClassName(), type));
        } catch (SQLException e) {
            getServletContext().log("Student resources failed", e);
            req.setAttribute("error", "Could not load resources. Please try again.");
        }
        req.setAttribute("type", type);
        req.getRequestDispatcher("/WEB-INF/views/student_resources.jsp").forward(req, resp);
    }
}
