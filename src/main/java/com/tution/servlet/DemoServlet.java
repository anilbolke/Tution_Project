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
import com.tution.model.Inquiry;
import com.tution.model.LeadDemo;
import com.tution.model.User;

/**
 * Demo / trial-class diary.
 *
 * GET  /demo[?from=&to=&status=]        the diary
 * POST /demo  action=schedule           book a demo for a lead
 * POST /demo  action=feedback           record the outcome + rating
 * POST /demo  action=cancel             cancel a booking
 */
@WebServlet("/demo")
public class DemoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final DemoDAO    demoDAO    = new DemoDAO();
    private final InquiryDAO inquiryDAO = new InquiryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        LocalDate today = LocalDate.now();
        String from   = p(req, "from");
        String to     = p(req, "to");
        String status = p(req, "status");

        // Default view: today plus the coming fortnight — the window a
        // counsellor actually acts on.
        if (isBlank(from) && isBlank(to)) {
            from = today.toString();
            to   = today.plusDays(14).toString();
        }
        Integer scope = user.isCounsellor() ? Integer.valueOf(user.getUserId()) : null;

        try {
            req.setAttribute("demos", demoDAO.find(from, to, status, scope));
            // Anything still SCHEDULED with a past date needs chasing.
            req.setAttribute("missed",
                demoDAO.find(null, today.minusDays(1).toString(), "SCHEDULED", scope));
        } catch (SQLException e) {
            getServletContext().log("Demo diary failed", e);
            req.setAttribute("error", "Could not load the demo list. Please try again.");
        }

        req.setAttribute("ffrom", from);
        req.setAttribute("fto", to);
        req.setAttribute("fstatus", status);
        req.getRequestDispatcher("/WEB-INF/views/demos.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String action = p(req, "action");
        String ctx = req.getContextPath();

        try {
            if ("schedule".equals(action)) {
                int inquiryId = intParam(req, "inquiryId", 0);
                Inquiry lead = inquiryDAO.findById(inquiryId);
                if (lead == null || !mayAccess(user, lead)) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your lead.");
                    return;
                }
                if (isBlank(p(req, "demoDate"))) {
                    resp.sendRedirect(ctx + "/lead?id=" + inquiryId + "&msg=demodate");
                    return;
                }

                LeadDemo d = new LeadDemo();
                d.setInquiryId(inquiryId);
                d.setDemoDate(p(req, "demoDate"));
                d.setDemoTime(p(req, "demoTime"));
                d.setFacultyName(p(req, "facultyName"));
                d.setSubject(p(req, "subject"));
                d.setMode(p(req, "mode"));
                d.setCreatedBy(user.getFullName());
                demoDAO.schedule(d);

                resp.sendRedirect(ctx + "/lead?id=" + inquiryId + "&msg=demo");
                return;
            }

            // feedback / cancel both act on an existing demo
            int demoId = intParam(req, "demoId", 0);
            LeadDemo existing = demoDAO.findById(demoId);
            if (existing == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Demo not found.");
                return;
            }
            Inquiry lead = inquiryDAO.findById(existing.getInquiryId());
            if (lead == null || !mayAccess(user, lead)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your lead.");
                return;
            }

            if ("cancel".equals(action)) {
                demoDAO.recordOutcome(demoId, "CANCELLED", p(req, "feedback"), null,
                                      p(req, "nextFollowupDate"));
            } else if ("feedback".equals(action)) {
                String st = p(req, "demoStatus");
                if (isBlank(st)) {
                    st = "COMPLETED";
                }
                demoDAO.recordOutcome(demoId, st, p(req, "feedback"),
                                      intOrNull(p(req, "rating")), p(req, "nextFollowupDate"));
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action.");
                return;
            }

            String back = p(req, "back");
            resp.sendRedirect("diary".equals(back)
                ? ctx + "/demo?msg=saved"
                : ctx + "/lead?id=" + existing.getInquiryId() + "&msg=demo");

        } catch (SQLException e) {
            getServletContext().log("Demo action failed", e);
            resp.sendRedirect(ctx + "/demo?msg=error");
        }
    }

    private boolean mayAccess(User user, Inquiry lead) {
        if (!user.isCounsellor()) {
            return true;
        }
        Integer owner = lead.getCounsellorId();
        return owner == null || owner.intValue() == user.getUserId();
    }

    private static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s == null) ? null : (User) s.getAttribute("user");
    }

    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }

    private static boolean isBlank(String s) { return s == null || s.isEmpty(); }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        String v = req.getParameter(name);
        return (v != null && v.trim().matches("\\d+")) ? Integer.parseInt(v.trim()) : fallback;
    }

    private static Integer intOrNull(String v) {
        return (v != null && v.matches("\\d+")) ? Integer.valueOf(v) : null;
    }
}
