package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.StudentDAO;
import com.tution.model.Student;

/** Loads all admissions and forwards to the student list page. Requires login. */
@WebServlet("/students")
public class StudentListServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        try {
            // A counsellor sees only their own students, the same rule the lead
            // pipeline already follows. Everyone else sees the whole institute.
            com.tution.model.User user = (com.tution.model.User) session.getAttribute("user");
            Integer scope = (user != null && user.isCounsellor())
                          ? Integer.valueOf(user.getUserId()) : null;
            List<Student> students = studentDAO.findAll(scope);
            req.setAttribute("students", students);
            req.setAttribute("scopedToOwn", Boolean.valueOf(scope != null));
        } catch (SQLException e) {
            getServletContext().log("Load students failed", e);
            req.setAttribute("error", "Could not load students. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/students.jsp").forward(req, resp);
    }
}
