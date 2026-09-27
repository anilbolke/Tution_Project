package com.tution.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.ReportDAO;
import com.tution.model.ReportResult;
import com.tution.model.User;
import com.tution.util.XlsxWriter;

/**
 * Excel export for every report.
 *
 * One servlet handles all eight because {@link ReportResult} is generic. The
 * report is recomputed from the same parameters rather than stashed in the
 * session, so a bookmarked export link always returns current data — and two
 * browser tabs cannot hand each other the wrong sheet.
 *
 * Reuses {@link XlsxWriter}, the pure-JDK .xlsx writer already in the project
 * (previously used only by the OMR export). No library was added.
 *
 * GET /report-export?type=...&from=...&to=...
 */
@WebServlet("/report-export")
public class ReportExportServlet extends HttpServlet {

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
        // Same lock as the on-screen report. An export route that skipped it
        // would be the easier way in, not the harder one.
        if (!user.can(com.tution.dao.AccessDAO.reportActivity(type))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "Your role does not have this report.");
            return;
        }
        String from = req.getParameter("from");
        String to   = req.getParameter("to");

        try {
            ReportResult r = reportDAO.run(type, from, to, com.tution.dao.Scope.of(user));

            List<String[]> rows = new ArrayList<>();
            // A title block, so a downloaded sheet still says what it is and
            // over what period once it is off the screen.
            rows.add(new String[] { r.getTitle() });
            if (r.isDateRanged() && (notBlank(from) || notBlank(to))) {
                rows.add(new String[] { "Period: " + nb(from, "start") + " to " + nb(to, "today") });
            }
            rows.add(new String[] { "Generated: " + java.time.LocalDate.now()
                                    + " by " + safe(user.getFullName()) });
            rows.add(new String[] { "" });

            rows.add(r.getColumns());
            for (String[] row : r.getRows()) {
                rows.add(forExcel(row));
            }
            if (r.getTotals() != null) {
                rows.add(new String[] { "" });
                rows.add(forExcel(r.getTotals()));
            }

            byte[] xlsx = XlsxWriter.sheet(sheetName(r.getTitle()), rows);

            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName(type) + "\"");
            resp.setContentLength(xlsx.length);
            try (OutputStream out = resp.getOutputStream()) {
                out.write(xlsx);
            }

        } catch (SQLException e) {
            getServletContext().log("Report export '" + type + "' failed", e);
            resp.sendRedirect(req.getContextPath() + "/reports?type=" + type + "&msg=exporterror");
        }
    }

    /**
     * Strips the thousands separators the screen uses, so amounts land in Excel
     * as real numbers the client can sum and pivot rather than as text.
     *
     * Only touches cells that are purely digits and commas — a name or a remark
     * containing a comma is left exactly as it is.
     */
    private static String[] forExcel(String[] row) {
        if (row == null) {
            return new String[0];
        }
        String[] out = new String[row.length];
        for (int i = 0; i < row.length; i++) {
            String c = row[i];
            if (c != null && c.matches("-?\\d{1,3}(,\\d{3})+")) {
                out[i] = c.replace(",", "");
            } else {
                out[i] = c;
            }
        }
        return out;
    }

    /** Excel sheet names cannot exceed 31 chars or contain : \ / ? * [ ] */
    private static String sheetName(String title) {
        String s = (title == null ? "Report" : title).replaceAll("[:\\\\/?*\\[\\]]", "-");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private static String fileName(String type) {
        return type.replaceAll("[^a-zA-Z0-9-]", "") + "-" + java.time.LocalDate.now() + ".xlsx";
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
    private static String nb(String s, String fallback) { return notBlank(s) ? s.trim() : fallback; }
    private static String safe(String s) { return s == null ? "" : s; }
}
