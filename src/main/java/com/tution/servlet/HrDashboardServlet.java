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

import com.tution.dao.HrDAO;
import com.tution.dao.SalesStatsDAO;
import com.tution.model.User;

/**
 * The HR landing screen.
 *
 * Deliberately not the institute dashboard. That one leads with collection and
 * admissions figures, which is management's business; this one leads with the
 * two things that need somebody to act today — an unmarked attendance register
 * and an undecided leave request — and then the rest of what HR was given.
 *
 * ADMIN and HR only, matching {@code User.canSeePeople()}.
 */
@WebServlet("/hr-dashboard")
public class HrDashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final HrDAO         hrDAO    = new HrDAO();
    private final SalesStatsDAO statsDAO = new SalesStatsDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        if (!user.canSeePeople()) {
            resp.sendRedirect(req.getContextPath() + user.homePath());
            return;
        }

        LocalDate today = LocalDate.now();
        try {
            req.setAttribute("d", hrDAO.dashboard(today.toString(),
                                                  today.getYear(), today.getMonthValue()));
            // The enquiry / admission / fee side HR was given. Institute-wide,
            // so no counsellor scope.
            req.setAttribute("stats", statsDAO.load(null));
        } catch (SQLException e) {
            getServletContext().log("HR dashboard failed", e);
            req.setAttribute("error", "Could not load the dashboard: " + e.getMessage());
        }
        req.setAttribute("today", today.toString());
        req.setAttribute("y", Integer.valueOf(today.getYear()));
        req.setAttribute("m", Integer.valueOf(today.getMonthValue()));
        req.getRequestDispatcher("/WEB-INF/views/hr_dashboard.jsp").forward(req, resp);
    }
}
