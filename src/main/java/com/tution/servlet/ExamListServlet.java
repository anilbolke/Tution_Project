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

import com.tution.dao.ExamDAO;
import com.tution.model.Exam;

/** Lists all exams. */
@WebServlet("/exams")
public class ExamListServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ExamDAO examDAO = new ExamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        try {
            List<Exam> exams = examDAO.findAll();
            req.setAttribute("exams", exams);
        } catch (SQLException e) {
            getServletContext().log("Load exams failed", e);
            req.setAttribute("error", "Could not load exams. Please try again.");
        }
        req.setAttribute("saved", "1".equals(req.getParameter("saved")));
        req.getRequestDispatcher("/WEB-INF/views/exams.jsp").forward(req, resp);
    }
}
