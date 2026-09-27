package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.ReminderLogDAO;
import com.tution.model.Reminder;
import com.tution.model.User;
import com.tution.service.ReminderService;
import com.tution.util.ReminderConfig;

/**
 * The reminder queue: everything due, with a Send button per row and a
 * "send all" for the whole list.
 *
 * This page is what makes Phase 6 usable before the WhatsApp templates are
 * approved — the queue is computed and actionable today, and the scheduler
 * takes over unattended once {@link ReminderConfig#AUTO_SEND} is switched on.
 *
 * GET  /reminders[?kind=FOLLOWUP|DEMO|FEE_DUE]
 * POST /reminders  action=send      kind + refId
 * POST /reminders  action=sendall   [kind]
 */
@WebServlet("/reminders")
public class ReminderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ReminderService service = new ReminderService();
    private final ReminderLogDAO  logDAO  = new ReminderLogDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String kind = req.getParameter("kind");
        // A counsellor chases their own leads and students (an ABM, their team's), not the institute's.
        Integer scope = com.tution.dao.Scope.of(user);
        try {
            List<Reminder> all = service.queue(scope);
            List<Reminder> shown = new ArrayList<>();
            int nFollowup = 0, nDemo = 0, nFee = 0, nPending = 0;
            for (Reminder r : all) {
                if ("FOLLOWUP".equals(r.getKind()))    nFollowup++;
                else if ("DEMO".equals(r.getKind()))   nDemo++;
                else                                   nFee++;
                if (!r.isAlreadySent()) {
                    nPending++;
                }
                if (kind == null || kind.isEmpty() || kind.equals(r.getKind())) {
                    shown.add(r);
                }
            }
            req.setAttribute("reminders", shown);
            req.setAttribute("nFollowup", nFollowup);
            req.setAttribute("nDemo", nDemo);
            req.setAttribute("nFee", nFee);
            req.setAttribute("nPending", nPending);
            req.setAttribute("sentWeek", logDAO.sentSince(7));
        } catch (SQLException e) {
            getServletContext().log("Reminder queue failed", e);
            req.setAttribute("error", "Could not build the reminder list. Please try again.");
        }

        req.setAttribute("kind", kind);
        req.setAttribute("autoSend", Boolean.valueOf(ReminderConfig.AUTO_SEND));
        req.setAttribute("dryRun", Boolean.valueOf(ReminderConfig.DRY_RUN));
        req.setAttribute("runHour", String.format("%02d:%02d",
            ReminderConfig.RUN_HOUR, ReminderConfig.RUN_MINUTE));
        req.setAttribute("today", LocalDate.now().toString());
        req.getRequestDispatcher("/WEB-INF/views/reminders.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String action = req.getParameter("action");
        String kind   = req.getParameter("kind");
        String ctx    = req.getContextPath();
        Integer scope = com.tution.dao.Scope.of(user);
        String back   = ctx + "/reminders" + (kind == null || kind.isEmpty() ? "" : "?kind=" + kind);

        try {
            if ("sendall".equals(action)) {
                // Sending a batch means one gateway call per item, so it goes to
                // the background and the operator gets the page straight back.
                ReminderService.RunResult res = service.sendAll(
                    (kind == null || kind.isEmpty()) ? null : kind, scope);
                resp.sendRedirect(back + (back.contains("?") ? "&" : "?")
                    + "msg=batch&sent=" + res.sent + "&failed=" + res.failed
                    + "&skipped=" + res.skipped + "&nomob=" + res.noMobile
                    + "&dry=" + res.dryRun);
                return;
            }

            if ("send".equals(action)) {
                int refId = intParam(req, "refId", 0);
                String k  = req.getParameter("itemKind");
                if (refId <= 0 || k == null || k.isEmpty()) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing reminder.");
                    return;
                }
                ReminderService.Outcome out = service.sendOne(k, refId, scope);
                resp.sendRedirect(back + (back.contains("?") ? "&" : "?")
                    + "msg=" + out.name().toLowerCase());
                return;
            }

            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action.");

        } catch (SQLException e) {
            getServletContext().log("Reminder send failed", e);
            resp.sendRedirect(back + (back.contains("?") ? "&" : "?") + "msg=error");
        }
    }

    private static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s == null) ? null : (User) s.getAttribute("user");
    }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        String v = req.getParameter(name);
        return (v != null && v.trim().matches("\\d+")) ? Integer.parseInt(v.trim()) : fallback;
    }
}
