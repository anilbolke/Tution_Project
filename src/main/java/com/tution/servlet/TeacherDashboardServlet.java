package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.AcademicStatsDAO;
import com.tution.model.User;

/**
 * The teacher's landing screen.
 *
 * A teacher used to land straight on the attendance form, which is one job on
 * one date — fine as a task, useless as an opening view, and it said nothing
 * about the exam whose marks were half entered. This leads with the two things
 * that are actually outstanding: classes not yet marked today, and exams still
 * waiting on marks.
 *
 * ADMIN, STAFF and TEACHER, matching {@code User.canSeeAcademic()}.
 */
@WebServlet("/teacher-dashboard")
public class TeacherDashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final AcademicStatsDAO statsDAO = new AcademicStatsDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        // A hard stop rather than a redirect: a teacher's home IS this page, so
        // bouncing a denied user to homePath() could loop.
        if (!user.canSeeAcademic()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "This page is for teaching staff.");
            return;
        }

        LocalDate today = LocalDate.now();
        try {
            req.setAttribute("d", statsDAO.teacherTiles(today.toString(),
                                                        today.getYear(), today.getMonthValue()));
            req.setAttribute("classes", statsDAO.todayByClass(today.toString()));
            req.setAttribute("exams",   statsDAO.examProgress(6));
        } catch (SQLException e) {
            getServletContext().log("Teacher dashboard failed", e);
            req.setAttribute("error", "Could not load the dashboard: " + e.getMessage());
        }
        req.setAttribute("today", today.toString());
        req.setAttribute("m", Integer.valueOf(today.getMonthValue()));
        req.getRequestDispatcher("/WEB-INF/views/teacher_dashboard.jsp").forward(req, resp);
    }
}
