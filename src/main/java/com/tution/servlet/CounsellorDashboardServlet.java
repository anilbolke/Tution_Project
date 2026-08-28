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

import com.tution.dao.DemoDAO;
import com.tution.dao.InquiryDAO;
import com.tution.dao.SalesStatsDAO;
import com.tution.dao.TargetDAO;
import com.tution.model.User;

/**
 * The counsellor's own dashboard: their pipeline, their work due today, and
 * their month-to-date performance.
 *
 * ADMIN can inspect any counsellor's view with {@code ?counsellorId=N}, which is
 * what makes the leaderboard rows clickable.
 */
@WebServlet("/my-dashboard")
public class CounsellorDashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final SalesStatsDAO salesDAO   = new SalesStatsDAO();
    private final InquiryDAO    inquiryDAO = new InquiryDAO();
    private final DemoDAO       demoDAO    = new DemoDAO();
    private final TargetDAO     targetDAO  = new TargetDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        // A counsellor only ever sees their own numbers; ADMIN may pick a
        // counsellor, and defaults to their own.
        Integer target = Integer.valueOf(user.getUserId());
        if (user.isAdmin()) {
            String q = req.getParameter("counsellorId");
            if (q != null && q.trim().matches("\\d+")) {
                target = Integer.valueOf(q.trim());
            }
        }

        LocalDate today = LocalDate.now();
        try {
            req.setAttribute("stats", salesDAO.load(target));

            // Today's and overdue follow-ups, soonest first.
            InquiryDAO.Filter due = new InquiryDAO.Filter();
            due.openOnly   = true;
            due.orderByDue = true;
            due.dueFrom    = "1900-01-01";
            due.dueTo      = today.toString();
            due.scopeCounsellorId = target;
            req.setAttribute("dueLeads", inquiryDAO.find(due));

            // Hot leads that are still open — the ones worth calling first.
            InquiryDAO.Filter hot = new InquiryDAO.Filter();
            hot.openOnly = true;
            hot.priority = "Hot";
            hot.scopeCounsellorId = target;
            req.setAttribute("hotLeads", inquiryDAO.find(hot));

            req.setAttribute("todayDemos",
                demoDAO.find(today.toString(), today.plusDays(7).toString(), "SCHEDULED", target));

            req.setAttribute("viewingOther",
                Boolean.valueOf(user.isAdmin() && target.intValue() != user.getUserId()));
            req.setAttribute("counsellors", new com.tution.dao.MasterDAO().counsellors());
            req.setAttribute("targetId", target);

            // The counsellor's own quarterly target. Their own only - the
            // institute was explicit that counsellors must not see each other's.
            if (target != null) {
                java.time.LocalDate qs = TargetDAO.quarterStart(java.time.LocalDate.now());
                req.setAttribute("myTarget",
                        targetDAO.forCounsellor(target.intValue(), "QUARTER", qs, TargetDAO.quarterEnd(qs)));
                req.setAttribute("quarterLabel", TargetDAO.quarterLabel(qs));
            }

        } catch (SQLException e) {
            getServletContext().log("Counsellor dashboard failed", e);
            req.setAttribute("error", "Could not load your dashboard. Please try again.");
        }

        req.setAttribute("today", today.toString());
        req.getRequestDispatcher("/WEB-INF/views/my_dashboard.jsp").forward(req, resp);
    }
}
