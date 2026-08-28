package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.UserDAO;
import com.tution.model.User;

/** Handles the login form POST and creates the user session. */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final UserDAO userDAO = new UserDAO();

    /** A GET on /login just shows the login page. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendRedirect(req.getContextPath() + "/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String username = trim(req.getParameter("username"));
        String password = req.getParameter("password");

        if (username.isEmpty() || password == null || password.isEmpty()) {
            fail(req, resp, "Please enter both username and password.");
            return;
        }

        try {
            User user = userDAO.authenticate(username, password);
            if (user != null) {
                HttpSession session = req.getSession(true);
                session.setAttribute("user", user);
                session.setMaxInactiveInterval(30 * 60); // 30 minutes
                // Each role starts where its work is: a counsellor on their own
                // pipeline, a teacher on attendance, everyone else on the
                // institute dashboard.
                resp.sendRedirect(req.getContextPath() + user.homePath());
            } else {
                fail(req, resp, "Invalid username or password.");
            }
        } catch (SQLException e) {
            getServletContext().log("Login DB error", e);
            fail(req, resp, "Server error. Please try again later.");
        }
    }

    private void fail(HttpServletRequest req, HttpServletResponse resp, String msg)
            throws ServletException, IOException {
        req.setAttribute("error", msg);
        req.setAttribute("username", req.getParameter("username"));
        req.getRequestDispatcher("/login.jsp").forward(req, resp);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
