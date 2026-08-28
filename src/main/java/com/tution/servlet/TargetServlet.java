package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.CounsellorCourseTargetDAO;
import com.tution.dao.TargetDAO;
import com.tution.model.CounsellorCourseTarget;
import com.tution.model.CounsellorTarget;
import com.tution.model.User;

/**
 * Setting quarterly revenue and admission targets, and the management view of
 * how the team is doing against them — by counsellor, and by counsellor+course.
 *
 * The course breakdown is deliberately gated on the counsellor already having
 * an overall target for the quarter: the institute's workflow is "set the
 * headline number first, then decide which courses it should come from" —
 * not the other way round. See [[project_tuition_mgmt]] in project memory.
 *
 * Management only. A counsellor sees their own target on their own dashboard and
 * nothing about anyone else's — that was the institute's explicit instruction.
 */
@WebServlet("/targets")
public class TargetServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String PERIOD = "QUARTER";

    private final TargetDAO dao = new TargetDAO();
    private final CounsellorCourseTargetDAO ccDao = new CounsellorCourseTargetDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) { resp.sendRedirect(req.getContextPath() + "/login.jsp"); return; }
        if (user.isCounsellor()) {           // belt and braces; AuthFilter also blocks this
            resp.sendRedirect(req.getContextPath() + "/my-dashboard");
            return;
        }
        try {
            LocalDate start = periodStart(req.getParameter("q"));
            LocalDate end   = TargetDAO.quarterEnd(start);

            List<CounsellorTarget> rows = dao.forPeriod(PERIOD, start, end);
            req.setAttribute("rows", rows);
            req.setAttribute("periodStart", start.toString());
            req.setAttribute("periodEnd", end.toString());
            req.setAttribute("periodLabel", TargetDAO.quarterLabel(start));
            req.setAttribute("prevQ", start.minusMonths(3).toString());
            req.setAttribute("nextQ", start.plusMonths(3).toString());

            // "copy from last quarter" is offered only when there is something to copy
            req.setAttribute("prevTargets",
                    dao.previousTargets(PERIOD, start.minusMonths(3)));

            long tAdmT = 0, tAdmA = 0, tRevT = 0, tRevA = 0;
            for (CounsellorTarget t : rows) {
                tAdmT += t.getAdmissionsTarget(); tAdmA += t.getAdmissionsActual();
                tRevT += t.getRevenueTarget();    tRevA += t.getRevenueActual();
            }
            req.setAttribute("totals", new long[] { tAdmT, tAdmA, tRevT, tRevA });

            // The course breakdown only opens for a counsellor who already has an
            // overall target this quarter — that is what "assigned" means here.
            Map<Integer, String> counsellorsWithTarget = new LinkedHashMap<>();
            for (CounsellorTarget t : rows) {
                if (!t.isUnset()) counsellorsWithTarget.put(t.getCounsellorId(), t.getCounsellorName());
            }
            req.setAttribute("counsellorOptions", counsellorsWithTarget);

            Integer selCounsellor = intOrNull(req.getParameter("counsellorId"));
            if (selCounsellor != null && !counsellorsWithTarget.containsKey(selCounsellor)) {
                selCounsellor = null;   // no overall target set (or none any more) — nothing to break down
            }
            req.setAttribute("selCounsellorId", selCounsellor);
            if (selCounsellor != null) {
                List<CounsellorCourseTarget> ccRows =
                        ccDao.forCounsellor(selCounsellor, PERIOD, start, end);
                req.setAttribute("ccRows", ccRows);
                req.setAttribute("prevCcTargets",
                        ccDao.previousTargets(selCounsellor, PERIOD, start.minusMonths(3)));

                long ccAdmT = 0, ccAdmA = 0, ccRevT = 0, ccRevA = 0;
                for (CounsellorCourseTarget t : ccRows) {
                    ccAdmT += t.getAdmissionsTarget(); ccAdmA += t.getAdmissionsActual();
                    ccRevT += t.getRevenueTarget();    ccRevA += t.getRevenueActual();
                }
                req.setAttribute("ccTotals", new long[] { ccAdmT, ccAdmA, ccRevT, ccRevA });
            }

            req.getRequestDispatcher("/WEB-INF/views/targets.jsp").forward(req, resp);
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load targets: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/targets.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = (User) req.getSession().getAttribute("user");
        if (user == null || user.isCounsellor()) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        LocalDate start = periodStart(req.getParameter("periodStart"));
        LocalDate end   = TargetDAO.quarterEnd(start);

        if ("counsellor-course".equals(req.getParameter("scope"))) {
            saveCounsellorCourseTargets(req, resp, user, start, end);
            return;
        }

        try {
            Map<Integer, int[]> values = new LinkedHashMap<>();
            Map<Integer, String> notes = new LinkedHashMap<>();

            String[] ids = req.getParameterValues("counsellorId");
            if (ids != null) {
                for (String id : ids) {
                    int uid = parseInt(id, 0);
                    if (uid <= 0) continue;
                    int adm  = parseInt(req.getParameter("adm_" + uid), 0);
                    long rev = parseLong(req.getParameter("rev_" + uid), 0);
                    if (adm < 0 || rev < 0) {
                        fail(req, resp, start, "A target cannot be negative.");
                        return;
                    }
                    values.put(uid, new int[] { adm, (int) Math.min(rev, Integer.MAX_VALUE) });
                    notes.put(uid, req.getParameter("note_" + uid));
                }
            }
            int saved = dao.saveAll(PERIOD, start, end, values, notes, "BOOKED", user.getUsername());
            req.getSession().setAttribute("flash",
                    saved + " target(s) saved for " + TargetDAO.quarterLabel(start)
                  + ". Rows left blank are treated as no target.");
        } catch (SQLException e) {
            req.getSession().setAttribute("flashError", "Could not save: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + "/targets?q=" + start);
    }

    private void saveCounsellorCourseTargets(HttpServletRequest req, HttpServletResponse resp, User user,
                                             LocalDate start, LocalDate end) throws IOException {
        Integer counsellorId = intOrNull(req.getParameter("counsellorId"));
        if (counsellorId == null) {
            resp.sendRedirect(req.getContextPath() + "/targets?q=" + start);
            return;
        }
        String back = req.getContextPath() + "/targets?q=" + start + "&counsellorId=" + counsellorId;
        try {
            Map<Integer, int[]> values = new LinkedHashMap<>();
            Map<Integer, String> notes = new LinkedHashMap<>();

            String[] ids = req.getParameterValues("courseId");
            if (ids != null) {
                for (String id : ids) {
                    int cid = parseInt(id, 0);
                    if (cid <= 0) continue;
                    int adm  = parseInt(req.getParameter("admcc_" + cid), 0);
                    long rev = parseLong(req.getParameter("revcc_" + cid), 0);
                    if (adm < 0 || rev < 0) {
                        req.getSession().setAttribute("flashError", "A target cannot be negative.");
                        resp.sendRedirect(back);
                        return;
                    }
                    values.put(cid, new int[] { adm, (int) Math.min(rev, Integer.MAX_VALUE) });
                    notes.put(cid, req.getParameter("notecc_" + cid));
                }
            }
            int saved = ccDao.saveAll(counsellorId, PERIOD, start, end, values, notes, "BOOKED", user.getUsername());
            req.getSession().setAttribute("flash",
                    saved + " course target(s) saved for " + TargetDAO.quarterLabel(start)
                  + ". Rows left blank are treated as no target.");
        } catch (SQLException e) {
            req.getSession().setAttribute("flashError", "Could not save: " + e.getMessage());
        }
        resp.sendRedirect(back);
    }

    /* ─── helpers ─── */

    /** The quarter containing the given date, defaulting to the current one. */
    private LocalDate periodStart(String iso) {
        LocalDate d = LocalDate.now();
        if (iso != null && iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try { d = LocalDate.parse(iso); } catch (Exception ignore) { }
        }
        return TargetDAO.quarterStart(d);
    }

    private void fail(HttpServletRequest req, HttpServletResponse resp, LocalDate start, String msg)
            throws IOException {
        req.getSession().setAttribute("flashError", msg);
        resp.sendRedirect(req.getContextPath() + "/targets?q=" + start);
    }

    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s.replaceAll("[,\\s]", "").trim()); } catch (Exception e) { return d; }
    }
    private static long parseLong(String s, long d) {
        try { return Long.parseLong(s.replaceAll("[,\\s]", "").trim()); } catch (Exception e) { return d; }
    }
    private static Integer intOrNull(String v) {
        return (v == null || !v.trim().matches("\\d+")) ? null : Integer.valueOf(v.trim());
    }
}
