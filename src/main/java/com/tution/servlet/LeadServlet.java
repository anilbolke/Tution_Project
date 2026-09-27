package com.tution.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.DemoDAO;
import com.tution.dao.FollowupDAO;
import com.tution.dao.InquiryDAO;
import com.tution.dao.LeadStageDAO;
import com.tution.dao.MasterDAO;
import com.tution.model.Inquiry;
import com.tution.model.User;
import com.tution.service.WhatsAppService;

/**
 * The counsellor-side lead screen: create, view, edit, re-assign and move a lead
 * through the sales pipeline.
 *
 * Routes
 *   GET  /lead?new=1[&mobile=&name=]   blank form, optionally pre-filled from a search
 *   GET  /lead?id=N                    lead detail
 *   GET  /lead?id=N&edit=1             edit form
 *   GET  /lead?action=checkdup&mobile= JSON duplicate check (called on mobile blur)
 *   POST /lead  action=create|update|status|assign
 */
@WebServlet("/lead")
public class LeadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final InquiryDAO  inquiryDAO  = new InquiryDAO();
    private final MasterDAO   masterDAO   = new MasterDAO();
    private final LeadStageDAO stageDAO  = new LeadStageDAO();
    private final FollowupDAO followupDAO = new FollowupDAO();
    private final DemoDAO     demoDAO     = new DemoDAO();

    // ────────────────────────────────────────────────────────────────
    //  GET
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String action = req.getParameter("action");
        if ("checkdup".equals(action)) {
            writeDuplicateJson(req, resp);
            return;
        }

        try {
            if (req.getParameter("new") != null) {
                Inquiry blank = new Inquiry();
                blank.setMobile(req.getParameter("mobile"));
                blank.setFullName(req.getParameter("name"));
                blank.setStatus("NEW");
                blank.setPriority("Warm");
                // A counsellor creating a lead owns it by default.
                if (user.isCounsellor()) {
                    blank.setCounsellorId(user.getUserId());
                }
                req.setAttribute("lead", blank);
                forwardForm(req, resp);
                return;
            }

            int id = intParam(req, "id", 0);
            if (id <= 0) {
                resp.sendRedirect(req.getContextPath() + "/inquiries");
                return;
            }

            Inquiry lead = inquiryDAO.findById(id);
            if (lead == null) {
                resp.sendRedirect(req.getContextPath() + "/inquiries");
                return;
            }
            if (!mayAccess(user, lead)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                               "This lead belongs to another counsellor.");
                return;
            }

            req.setAttribute("lead", lead);
            if (req.getParameter("edit") != null) {
                forwardForm(req, resp);
            } else {
                loadDropdowns(req);
                req.setAttribute("followups", followupDAO.findByLead(id));
                req.setAttribute("demos", demoDAO.findByLead(id));
                req.getRequestDispatcher("/WEB-INF/views/lead_view.jsp").forward(req, resp);
            }

        } catch (SQLException e) {
            getServletContext().log("Lead load failed", e);
            req.setAttribute("error", "Could not load the lead. Please try again.");
            req.getRequestDispatcher("/WEB-INF/views/lead_view.jsp").forward(req, resp);
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  POST
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String action = req.getParameter("action");
        String ctx = req.getContextPath();

        try {
            if ("status".equals(action)) {
                int id = intParam(req, "inquiryId", 0);
                Inquiry lead = inquiryDAO.findById(id);
                if (lead == null || !mayAccess(user, lead)) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your lead.");
                    return;
                }
                inquiryDAO.updateStatus(id, p(req, "status"),
                                        p(req, "remarks"), p(req, "nextFollowupDate"));
                resp.sendRedirect(ctx + "/lead?id=" + id + "&msg=status");
                return;
            }

            if ("assign".equals(action)) {
                // Re-assigning someone else's lead is an ADMIN action.
                if (!user.isAdmin()) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                                   "Only an administrator can re-assign a lead.");
                    return;
                }
                int id = intParam(req, "inquiryId", 0);
                String cid = p(req, "counsellorId");
                Integer counsellorId = (cid == null || cid.isEmpty()) ? null : Integer.valueOf(cid);
                inquiryDAO.assign(id, counsellorId);
                if (counsellorId != null) {
                    notifyCounsellor(counsellorId, inquiryDAO.findById(id));
                }
                resp.sendRedirect(ctx + "/lead?id=" + id + "&msg=assigned");
                return;
            }

            // create / update
            Inquiry lead = bind(req);
            String error = validate(lead);
            if (error != null) {
                req.setAttribute("error", error);
                req.setAttribute("lead", lead);
                forwardForm(req, resp);
                return;
            }

            if ("update".equals(action)) {
                Inquiry existing = inquiryDAO.findById(lead.getInquiryId());
                if (existing == null || !mayAccess(user, existing)) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your lead.");
                    return;
                }
                // Only an ADMIN may move a lead to a different counsellor here.
                if (!user.isAdmin()) {
                    lead.setCounsellorId(existing.getCounsellorId());
                }
                inquiryDAO.update(lead);
                resp.sendRedirect(ctx + "/lead?id=" + lead.getInquiryId() + "&msg=saved");
            } else {
                if (lead.getCounsellorId() == null && user.isCounsellor()) {
                    lead.setCounsellorId(user.getUserId());
                }
                int newId = inquiryDAO.insertFull(lead);
                resp.sendRedirect(ctx + "/lead?id=" + newId + "&msg=created");
            }

        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid counsellor.");
        } catch (SQLException e) {
            getServletContext().log("Lead save failed", e);
            req.setAttribute("error", "Could not save the lead. Please try again.");
            req.setAttribute("lead", bind(req));
            forwardForm(req, resp);
        }
    }

    /**
     * WhatsApps the counsellor that a lead has landed in their queue, using the
     * already-approved `inquiry_staff` template (so no new Meta approval was
     * needed for this).
     *
     * Best-effort by design: a messaging failure must never block the
     * assignment, which is the actual business action.
     */
    private void notifyCounsellor(int counsellorId, Inquiry lead) {
        if (lead == null) {
            return;
        }
        try {
            String mobile = masterDAO.counsellorMobile(counsellorId);
            if (mobile == null) {
                return;   // no number on file — nothing to send to
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

    // ────────────────────────────────────────────────────────────────
    //  Duplicate check (AJAX)
    // ────────────────────────────────────────────────────────────────

    /**
     * Returns the leads already on this mobile so the form can warn before a
     * second enquiry is created for the same family. Hand-built JSON — this
     * project deliberately carries no JSON library.
     */
    private void writeDuplicateJson(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String mobile = p(req, "mobile");
        int excludeId = intParam(req, "excludeId", 0);

        StringBuilder json = new StringBuilder("{\"duplicates\":[");
        try {
            List<Inquiry> dups = inquiryDAO.findByMobile(mobile, excludeId);
            for (int i = 0; i < dups.size(); i++) {
                Inquiry d = dups.get(i);
                if (i > 0) {
                    json.append(',');
                }
                json.append("{\"id\":").append(d.getInquiryId())
                    .append(",\"name\":\"").append(jsonEscape(d.getFullName())).append('"')
                    .append(",\"status\":\"").append(jsonEscape(d.getStatus())).append('"')
                    .append(",\"counsellor\":\"").append(jsonEscape(d.getCounsellorName())).append('"')
                    .append('}');
            }
        } catch (SQLException e) {
            getServletContext().log("Duplicate check failed", e);
        }
        json.append("]}");

        try (PrintWriter out = resp.getWriter()) {
            out.write(json.toString());
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private Inquiry bind(HttpServletRequest req) {
        Inquiry q = new Inquiry();
        q.setInquiryId(intParam(req, "inquiryId", 0));
        q.setFullName(p(req, "fullName"));
        q.setMobile(p(req, "mobile"));
        q.setParentName(p(req, "parentName"));
        q.setParentMobile(p(req, "parentMobile"));
        q.setEmail(p(req, "email"));
        q.setDob(p(req, "dob"));
        q.setGender(p(req, "gender"));
        q.setCity(p(req, "city"));
        q.setAddress(p(req, "address"));

        q.setCurrentClass(p(req, "currentClass"));
        q.setPrevQualification(p(req, "prevQualification"));
        q.setSchoolName(p(req, "schoolName"));
        q.setBoard(p(req, "board"));
        q.setPercentage(p(req, "percentage"));

        q.setCourseName(p(req, "courseName"));
        q.setClassInterest(p(req, "courseName"));   // keep the legacy column in step
        q.setBatchPref(p(req, "batchPref"));
        q.setLearningMode(p(req, "learningMode"));
        q.setBranch(p(req, "branch"));
        q.setExpectedJoinDate(p(req, "expectedJoinDate"));

        q.setSource(p(req, "source"));
        q.setMessage(p(req, "message"));
        // Lead Stage replaced Lead Priority on the form. `priority` is still
        // read straight from the request when present (older forms and the quick
        // create path), and InquiryDAO derives it from the stage otherwise.
        q.setPriority(p(req, "priority"));
        q.setLeadStage(p(req, "leadStage"));
        q.setLeadSubStage(p(req, "leadSubStage"));
        String cid = p(req, "counsellorId");
        q.setCounsellorId((cid == null || cid.isEmpty()) ? null : Integer.valueOf(cid));
        q.setNextFollowupDate(p(req, "nextFollowupDate"));
        q.setStatus(p(req, "status"));

        q.setStudentRequirements(p(req, "studentRequirements"));
        q.setParentFeedback(p(req, "parentFeedback"));
        q.setCounsellorRemarks(p(req, "counsellorRemarks"));
        return q;
    }

    /** Mirrors the client-side checks. Mobile is the one hard requirement. */
    private String validate(Inquiry q) throws SQLException {
        if (isBlank(q.getFullName())) {
            return "Please enter the student name.";
        }
        if (!isMobile(q.getMobile())) {
            return "Please enter a valid 10-digit student mobile number.";
        }
        if (!isBlank(q.getParentMobile()) && !isMobile(q.getParentMobile())) {
            return "Please enter a valid 10-digit parent mobile number.";
        }
        // The cascade runs in the browser, so the pair has to be re-checked here:
        // a hand-built POST, or a form left open while the master lists changed,
        // could otherwise store a sub stage that belongs to a different stage.
        if (!stageDAO.isValidStage(q.getLeadStage())) {
            return "That lead stage is not recognised. Please choose one from the list.";
        }
        if (!stageDAO.isValidPair(q.getLeadStage(), q.getLeadSubStage())) {
            return "\"" + q.getLeadSubStage() + "\" is not a sub stage of \"" + q.getLeadStage()
                 + "\". Please choose the stage first, then its sub stage.";
        }
        return null;
    }

    /** A counsellor may only touch their own leads, an ABM their team's; everyone else all. */
    private boolean mayAccess(User user, Inquiry lead) {
        // own lead, or (for an ABM) a lead of someone reporting to them
        return com.tution.dao.Scope.mayAccess(user, lead.getCounsellorId());
    }

    private void forwardForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        loadDropdowns(req);
        req.getRequestDispatcher("/WEB-INF/views/lead_form.jsp").forward(req, resp);
    }

    private void loadDropdowns(HttpServletRequest req) {
        try {
            req.setAttribute("sources",     masterDAO.leadSources());
            req.setAttribute("courses",     masterDAO.courses());
            req.setAttribute("batches",     masterDAO.batches());
            req.setAttribute("counsellors", masterDAO.counsellors());
            req.setAttribute("stages",        stageDAO.stages());
            req.setAttribute("subStagesJson", stageDAO.subStagesJson());
        } catch (SQLException e) {
            getServletContext().log("Master lists failed to load", e);
        }
    }

    private static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s == null) ? null : (User) s.getAttribute("user");
    }

    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        String v = req.getParameter(name);
        if (v == null || !v.trim().matches("\\d+")) {
            return fallback;
        }
        return Integer.parseInt(v.trim());
    }

    private static boolean isBlank(String s)  { return s == null || s.isEmpty(); }
    private static boolean isMobile(String s) { return s != null && s.matches("[6-9]\\d{9}"); }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
