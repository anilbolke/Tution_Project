package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.FollowupDAO;
import com.tution.dao.InquiryDAO;
import com.tution.dao.LeadStageDAO;
import com.tution.dao.MasterDAO;
import com.tution.model.Inquiry;
import com.tution.model.LeadFollowup;
import com.tution.model.User;

/**
 * The counsellor's daily work queue, and the endpoint that logs a follow-up.
 *
 * GET  /followup[?view=today|overdue|week|all]  the queue
 * POST /followup   action=log                   record a touchpoint on a lead
 */
@WebServlet("/followup")
public class FollowupServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final InquiryDAO  inquiryDAO  = new InquiryDAO();
    private final FollowupDAO followupDAO = new FollowupDAO();
    private final LeadStageDAO stageDAO   = new LeadStageDAO();
    private final MasterDAO   masterDAO   = new MasterDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String view = req.getParameter("view");
        if (view == null || view.isEmpty()) {
            view = "today";
        }
        LocalDate today = LocalDate.now();

        InquiryDAO.Filter f = new InquiryDAO.Filter();
        f.openOnly   = true;
        f.orderByDue = true;
        f.scopeCounsellorId = com.tution.dao.Scope.of(user);   // own / ABM's team / all

        // Narrowing filters, on top of whichever due window the view picked.
        // A counsellor's own scope is applied above and is not one of these —
        // it is not theirs to widen.
        f.q         = req.getParameter("q");
        f.priority  = req.getParameter("priority");
        f.leadStage = req.getParameter("stage");
        f.status    = req.getParameter("status");
        // Picking one counsellor only ever narrows: it is ANDed with the scope
        // above, so an ABM can filter to a team member but never outside the team.
        f.counsellorId = intOrNull(req.getParameter("counsellor"));

        switch (view) {
            case "overdue":
                f.dueTo = today.minusDays(1).toString();
                break;
            case "week":
                f.dueFrom = today.toString();
                f.dueTo   = today.plusDays(7).toString();
                break;
            case "all":
                // every open lead that has a follow-up date at all
                f.dueFrom = "1900-01-01";
                break;
            case "today":
            default:
                f.dueFrom = today.toString();
                f.dueTo   = today.toString();
                view = "today";
                break;
        }

        try {
            req.setAttribute("leads", inquiryDAO.find(f));
            // Counted through the SAME filters as the list. Tiles that ignored
            // them would contradict the rows underneath and quietly send a
            // counsellor looking for work that is not theirs.
            req.setAttribute("counts", loadCounts(f, today));
            req.setAttribute("stages",        stageDAO.stages());
            req.setAttribute("subStagesJson", stageDAO.subStagesJson());
            req.setAttribute("counsellors",   masterDAO.counsellors(f.scopeCounsellorId));
        } catch (SQLException e) {
            getServletContext().log("Follow-up queue failed", e);
            req.setAttribute("error", "Could not load the follow-up list. Please try again.");
        }
        req.setAttribute("view", view);

        // Echoed back so the form keeps its state and the view tabs can carry
        // the filters with them.
        req.setAttribute("fq",          f.q);
        req.setAttribute("fpriority",   f.priority);
        req.setAttribute("fstage",      f.leadStage);
        req.setAttribute("fstatus",     f.status);
        req.setAttribute("fcounsellor", req.getParameter("counsellor"));
        req.getRequestDispatcher("/WEB-INF/views/followups.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        int inquiryId = intParam(req, "inquiryId", 0);
        if (inquiryId <= 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing lead.");
            return;
        }

        try {
            Inquiry lead = inquiryDAO.findById(inquiryId);
            if (lead == null || !mayAccess(user, lead)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your lead.");
                return;
            }

            LeadFollowup f = new LeadFollowup();
            f.setInquiryId(inquiryId);
            f.setCounsellorId(user.getUserId());
            f.setCounsellorName(user.getFullName());
            f.setCommType(p(req, "commType"));
            f.setDiscussion(p(req, "discussion"));
            f.setObjection(p(req, "objection"));
            f.setNextActionDate(p(req, "nextActionDate"));

            // Blank means "leave the lead where it is".
            String newStatus = p(req, "outcomeStatus");
            followupDAO.logAndAdvanceLead(f, newStatus);

            // Lead Stage / Sub Stage can also be moved while logging the call —
            // that is how the counsellor actually works. Blank leaves it alone.
            // The pair is re-checked here because the cascade runs in the browser.
            String stage = p(req, "leadStage");
            String sub   = p(req, "leadSubStage");
            if (stage != null && !stage.isEmpty()) {
                if (!stageDAO.isValidStage(stage) || !stageDAO.isValidPair(stage, sub)) {
                    getServletContext().log("Rejected lead stage pair: " + stage + " / " + sub);
                } else {
                    inquiryDAO.updateStage(inquiryId, stage, sub);
                }
            }

            String back = p(req, "back");
            if ("queue".equals(back)) {
                // Back to the queue the call was logged FROM, filters intact —
                // otherwise every logged call throws the counsellor back to the
                // unfiltered list and they lose their place.
                StringBuilder url = new StringBuilder(req.getContextPath())
                        .append("/followup?view=").append(safeView(p(req, "view")));
                appendFilter(url, req, "q");
                appendFilter(url, req, "priority");
                appendFilter(url, req, "stage");
                appendFilter(url, req, "status");
                appendFilter(url, req, "counsellor");
                resp.sendRedirect(url.append("&msg=logged").toString());
            } else {
                resp.sendRedirect(req.getContextPath() + "/lead?id=" + inquiryId + "&msg=logged");
            }

        } catch (SQLException e) {
            getServletContext().log("Log follow-up failed", e);
            resp.sendRedirect(req.getContextPath() + "/lead?id=" + inquiryId + "&msg=error");
        }
    }

    /**
     * Tile counts for the queue header — today / overdue / next 7 days.
     *
     * @param base the request's filters; each tile re-runs them over its own
     *             due window so the numbers agree with the list below
     */
    private int[] loadCounts(InquiryDAO.Filter base, LocalDate today) throws SQLException {
        return new int[] {
            countDue(base, today.toString(), today.toString()),
            countDue(base, null, today.minusDays(1).toString()),
            countDue(base, today.toString(), today.plusDays(7).toString())
        };
    }

    private int countDue(InquiryDAO.Filter base, String from, String to) throws SQLException {
        InquiryDAO.Filter f = new InquiryDAO.Filter();
        f.openOnly = true;
        f.dueFrom  = from;
        f.dueTo    = to;
        f.scopeCounsellorId = base.scopeCounsellorId;
        f.q            = base.q;
        f.priority     = base.priority;
        f.leadStage    = base.leadStage;
        f.status       = base.status;
        f.counsellorId = base.counsellorId;
        if (from == null) {
            // "overdue" has no lower bound but must still require a date
            f.dueFrom = "1900-01-01";
        }
        List<Inquiry> list = inquiryDAO.find(f);
        return list.size();
    }

    private boolean mayAccess(User user, Inquiry lead) {
        // own lead, or (for an ABM) a lead of someone reporting to them
        return com.tution.dao.Scope.mayAccess(user, lead.getCounsellorId());
    }

    private static String safeView(String v) {
        if ("overdue".equals(v) || "week".equals(v) || "all".equals(v)) {
            return v;
        }
        return "today";
    }

    private static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s == null) ? null : (User) s.getAttribute("user");
    }

    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }

    /**
     * Re-attaches one filter to the redirect URL, encoded.
     *
     * URLEncoder output cannot contain CR or LF, so a crafted filter value
     * cannot break out of the Location header.
     */
    private static void appendFilter(StringBuilder url, HttpServletRequest req, String name) {
        String v = p(req, name);
        if (v == null || v.isEmpty()) {
            return;
        }
        try {
            url.append('&').append(name).append('=')
               .append(java.net.URLEncoder.encode(v, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always available; the filter is simply dropped.
        }
    }

    private static Integer intOrNull(String v) {
        return (v == null || !v.trim().matches("\\d+")) ? null : Integer.valueOf(v.trim());
    }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        String v = req.getParameter(name);
        return (v != null && v.trim().matches("\\d+")) ? Integer.parseInt(v.trim()) : fallback;
    }
}
