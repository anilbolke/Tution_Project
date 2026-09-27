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

import com.tution.dao.ReportDAO;
import com.tution.model.User;

/**
 * The sales reports — all eight from the client requirement, rendered through
 * one generic view.
 *
 * GET /reports?type=...&from=yyyy-MM-dd&to=yyyy-MM-dd
 */
@WebServlet("/reports")
public class ReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ReportDAO reportDAO = new ReportDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String type = req.getParameter("type");
        if (type == null || type.isEmpty()) {
            // The first report this role holds - not a fixed one it may not have.
            java.util.List<String[]> mine = ReportDAO.types(user);
            type = mine.isEmpty() ? "lead-source" : mine.get(0)[0];
        }
        // The finance reports carry what /fund, /vendors and /expenses are locked
        // down for, so the same lock has to apply here - a report picker is not
        // a way round a permission.
        if (!user.can(com.tution.dao.AccessDAO.reportActivity(type))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "Your role does not have this report.");
            return;
        }
        String from = req.getParameter("from");
        String to   = req.getParameter("to");

        // Default to the current month on a FIRST visit only — that is what a
        // manager asks for first. Presence of the parameter (even empty) means
        // the user chose a range, so an explicitly blank one means "all time"
        // and must not be silently replaced by this month's dates.
        boolean rangeChosen = req.getParameter("from") != null || req.getParameter("to") != null;
        if (!rangeChosen) {
            LocalDate today = LocalDate.now();
            from = today.withDayOfMonth(1).toString();
            to   = today.toString();
        }
        if (from == null) {
            from = "";
        }
        if (to == null) {
            to = "";
        }

        try {
            req.setAttribute("report", reportDAO.run(type, from, to, com.tution.dao.Scope.of(user)));
        } catch (SQLException e) {
            getServletContext().log("Report '" + type + "' failed", e);
            req.setAttribute("error", "Could not build that report. Please try again.");
        }

        req.setAttribute("types", ReportDAO.types(user));
        req.setAttribute("type", type);
        req.setAttribute("from", from);
        req.setAttribute("to", to);
        req.getRequestDispatcher("/WEB-INF/views/reports.jsp").forward(req, resp);
    }
}
