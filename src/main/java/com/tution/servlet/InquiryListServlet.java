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

import com.tution.dao.InquiryDAO;
import com.tution.dao.MasterDAO;
import com.tution.model.Inquiry;
import com.tution.model.User;
import com.tution.service.WhatsAppService;

/**
 * The lead pipeline list.
 *
 * Filtering moved server-side here: the page used to render every row and let
 * js/filter.js hide some in the browser, which stops being workable once a
 * counsellor has thousands of leads.
 */
@WebServlet("/inquiries")
public class InquiryListServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final InquiryDAO inquiryDAO = new InquiryDAO();
    private final MasterDAO  masterDAO  = new MasterDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        InquiryDAO.Filter f = new InquiryDAO.Filter();
        f.q            = req.getParameter("q");
        f.status       = req.getParameter("status");
        f.priority     = req.getParameter("priority");
        f.source       = req.getParameter("source");
        f.fromDate     = req.getParameter("from");
        f.toDate       = req.getParameter("to");
        f.overdueOnly  = "1".equals(req.getParameter("overdue"));
        f.counsellorId = intOrNull(req.getParameter("counsellor"));

        // A counsellor only ever sees their own pipeline.
        if (user.isCounsellor()) {
            f.scopeCounsellorId = Integer.valueOf(user.getUserId());
        }

        try {
            List<Inquiry> inquiries = inquiryDAO.find(f);
            req.setAttribute("inquiries", inquiries);
            req.setAttribute("counsellors", masterDAO.counsellors());
            req.setAttribute("sources", masterDAO.leadSources());
        } catch (SQLException e) {
            getServletContext().log("Load inquiries failed", e);
            req.setAttribute("error", "Could not load inquiries. Please try again.");
        }

        // Echo the filters back so the form keeps its state.
        req.setAttribute("fq", f.q);
        req.setAttribute("fstatus", f.status);
        req.setAttribute("fpriority", f.priority);
        req.setAttribute("fsource", f.source);
        req.setAttribute("ffrom", f.fromDate);
        req.setAttribute("fto", f.toDate);
        req.setAttribute("fcounsellor", req.getParameter("counsellor"));
        req.setAttribute("foverdue", f.overdueOnly);

        req.getRequestDispatcher("/WEB-INF/views/inquiries.jsp").forward(req, resp);
    }

    /**
     * Bulk-assign a counsellor to a batch of unassigned leads at once.
     * Re-assigning an already-owned lead stays a single-lead ADMIN action in
     * LeadServlet — the UI here only offers checkboxes for leads with no
     * counsellor yet, but the server re-checks that too, since a stale page
     * (opened before someone else claimed a lead) could otherwise post an id
     * that already has an owner.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String ctx = req.getContextPath();
        String action = req.getParameter("action");
        String qs = req.getParameter("qs");
        String back = ctx + "/inquiries" + (qs == null || qs.isEmpty() ? "" : "?" + qs);

        if (!"bulk-assign".equals(action)) {
            resp.sendRedirect(back);
            return;
        }

        if (!user.isAdmin()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "Only an administrator can bulk-assign leads.");
            return;
        }

        String[] ids = req.getParameterValues("ids");
        Integer counsellorId = intOrNull(req.getParameter("counsellorId"));
        int assigned = 0;

        if (ids != null && counsellorId != null) {
            try {
                for (String idStr : ids) {
                    Integer id = intOrNull(idStr);
                    if (id == null) {
                        continue;
                    }
                    Inquiry lead = inquiryDAO.findById(id);
                    if (lead == null || lead.getCounsellorId() != null) {
                        continue;   // already claimed since the page was loaded — skip it
                    }
                    inquiryDAO.assign(id, counsellorId);
                    notifyCounsellor(counsellorId, lead);
                    assigned++;
                }
            } catch (SQLException e) {
                getServletContext().log("Bulk-assign counsellor failed", e);
            }
        }

        String sep = back.contains("?") ? "&" : "?";
        resp.sendRedirect(back + sep + "bulkMsg=" + assigned);
    }

    /**
     * Best-effort WhatsApp nudge to the counsellor, mirroring LeadServlet's
     * single-assign notification. A messaging failure must never block the
     * assignment itself.
     */
    private void notifyCounsellor(int counsellorId, Inquiry lead) {
        try {
            String mobile = masterDAO.counsellorMobile(counsellorId);
            if (mobile == null) {
                return;
            }
            String course = (lead.getCourseName() != null && !lead.getCourseName().isEmpty())
                          ? lead.getCourseName() : lead.getClassInterest();
            new WhatsAppService().sendInquiryStaff(
                mobile,
                java.time.LocalDate.now().toString(),
                lead.getFullName(),
                course,
                lead.getMobile(),
                lead.getParentMobile());
        } catch (Exception e) {
            getServletContext().log("Counsellor assignment alert failed", e);
        }
    }

    private static Integer intOrNull(String v) {
        return (v == null || !v.trim().matches("\\d+")) ? null : Integer.valueOf(v.trim());
    }
}
