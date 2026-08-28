package com.tution.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.tution.dao.ExamDAO;
import com.tution.dao.ExamImportDAO;
import com.tution.model.Exam;
import com.tution.model.ImportRow;
import com.tution.model.User;
import com.tution.service.LeadImportService;
import com.tution.util.XlsxWriter;

/**
 * Uploads a school's student list and registers each student for a scholarship
 * exam with a generated roll number.
 *
 * Deliberately a TWO-STEP flow: upload parses and validates but writes nothing,
 * showing exactly what would happen; a second, explicit confirm commits. A
 * school list is bulk data arriving from outside the institute, and finding out
 * it was malformed after five thousand leads already exist is not a recoverable
 * position.
 *
 * The parsed rows are held in the session between the two steps rather than
 * re-uploaded, so the file is read once.
 */
@WebServlet("/exam-import")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 20 * 1024 * 1024,
                 maxRequestSize = 25 * 1024 * 1024)
public class ExamImportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_KEY = "examImportPreview";

    private final LeadImportService importer = new LeadImportService();
    private final ExamImportDAO dao = new ExamImportDAO();
    private final ExamDAO examDAO = new ExamDAO();

    /** What the preview screen needs, parked in the session between the two steps. */
    public static class Preview implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public int examId;
        public String examName;
        public String fileName;
        public String schoolName;
        public List<ImportRow> rows = new ArrayList<>();
        public int newCount, duplicateCount, rejectedCount;
        public List<String> unknownHeaders = new ArrayList<>();
        public int total() { return rows.size(); }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if ("rejected".equals(req.getParameter("download"))) {
            downloadRejected(req, resp);
            return;
        }
        try {
            req.setAttribute("exams", examDAO.findScholarshipExams());
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load exams: " + e.getMessage());
        }
        req.setAttribute("preview", req.getSession().getAttribute(SESSION_KEY));
        req.getRequestDispatcher("/WEB-INF/views/exam_import.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        try {
            if ("commit".equals(action))      commit(req, resp);
            else if ("cancel".equals(action)) { req.getSession().removeAttribute(SESSION_KEY);
                                                resp.sendRedirect(req.getContextPath() + "/exam-import"); }
            else                               preview(req, resp);
        } catch (SQLException e) {
            req.setAttribute("error", "Database error: " + e.getMessage());
            doGet(req, resp);
        }
    }

    /* ─── step 1: parse and show, write nothing ─── */

    private void preview(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException, SQLException {
        int examId = parseInt(req.getParameter("examId"), 0);
        if (examId <= 0) {
            req.setAttribute("error", "Choose which exam these students are sitting.");
            doGet(req, resp);
            return;
        }
        Part part = req.getPart("file");
        if (part == null || part.getSize() == 0) {
            req.setAttribute("error", "Choose an .xlsx file to upload.");
            doGet(req, resp);
            return;
        }
        String fileName = submittedFileName(part);
        if (!fileName.toLowerCase().endsWith(".xlsx")) {
            req.setAttribute("error", "Only .xlsx files are supported. "
                           + "Save the sheet as 'Excel Workbook (.xlsx)' and upload again.");
            doGet(req, resp);
            return;
        }

        LeadImportService.Parsed parsed;
        try (InputStream in = part.getInputStream()) {
            parsed = importer.parse(readAll(in));
        } catch (IOException e) {
            req.setAttribute("error", "Could not read the file: " + e.getMessage());
            doGet(req, resp);
            return;
        }
        if (!parsed.missingHeaders.isEmpty()) {
            req.setAttribute("error", "The file is missing required column(s): "
                           + String.join(", ", parsed.missingHeaders)
                           + ". Nothing was imported.");
            doGet(req, resp);
            return;
        }
        if (parsed.rows.isEmpty()) {
            req.setAttribute("error", "The file has a header row but no student rows.");
            doGet(req, resp);
            return;
        }

        // one bulk lookup for the whole file, not one query per row
        List<Map<String, Integer>> existing = dao.findExistingByMobiles(importer.mobilesIn(parsed));
        importer.markDuplicates(parsed, existing.get(0), existing.get(1));

        Exam exam = examDAO.findById(examId);
        Preview p = new Preview();
        p.examId     = examId;
        p.examName   = exam == null ? ("Exam #" + examId) : exam.getExamName();
        p.fileName   = fileName;
        p.schoolName = trim(req.getParameter("schoolName"));
        p.rows       = parsed.rows;
        p.newCount   = parsed.newCount;
        p.duplicateCount = parsed.duplicateCount;
        p.rejectedCount  = parsed.rejectedCount;
        p.unknownHeaders = parsed.unknownHeaders;

        req.getSession().setAttribute(SESSION_KEY, p);
        resp.sendRedirect(req.getContextPath() + "/exam-import");
    }

    /* ─── step 2: commit what was shown ─── */

    private void commit(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException, SQLException {
        HttpSession session = req.getSession();
        Preview p = (Preview) session.getAttribute(SESSION_KEY);
        if (p == null) {
            req.setAttribute("error", "That preview has expired. Please upload the file again.");
            doGet(req, resp);
            return;
        }
        User user = (User) session.getAttribute("user");
        String who = user == null ? "system" : user.getUsername();

        ExamImportDAO.Result r;
        try {
            r = dao.commit(p.examId, p.fileName, p.schoolName, who, p.rows);
        } catch (SQLException e) {
            // Roll-block exhaustion and the like are the user's to fix, not a crash.
            req.setAttribute("error", e.getMessage());
            doGet(req, resp);
            return;
        }
        session.removeAttribute(SESSION_KEY);

        StringBuilder msg = new StringBuilder();
        msg.append(r.imported).append(" new student(s) added");
        if (r.linked > 0)   msg.append(", ").append(r.linked).append(" matched to an existing lead");
        if (r.skipped > 0)  msg.append(", ").append(r.skipped).append(" already registered for this exam");
        if (r.rejected > 0) msg.append(", ").append(r.rejected).append(" rejected");
        msg.append(". Roll numbers have been generated.");

        session.setAttribute("flash", msg.toString());
        if (!r.notes.isEmpty()) session.setAttribute("flashNotes", r.notes);
        resp.sendRedirect(req.getContextPath() + "/exam-import");
    }

    /* ─── rejected rows, as a sheet the school can fix and re-send ─── */

    private void downloadRejected(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Preview p = (Preview) req.getSession().getAttribute(SESSION_KEY);
        if (p == null) {
            resp.sendRedirect(req.getContextPath() + "/exam-import");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Row", "Name", "Student's Contact", "School", "Class", "Problem" });
        for (ImportRow r : p.rows) {
            if (r.verdict != ImportRow.Verdict.REJECTED) continue;
            rows.add(new String[] { String.valueOf(r.rowNo), r.getName(), r.getMobile(),
                                    r.getSchool(), r.get("current_class"), r.problemText() });
        }
        byte[] xlsx = XlsxWriter.sheet("Rejected", rows);
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition", "attachment; filename=\"rejected-rows.xlsx\"");
        resp.setContentLength(xlsx.length);
        resp.getOutputStream().write(xlsx);
    }

    /* ─── helpers ─── */

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
    private static String submittedFileName(Part part) {
        String n = part.getSubmittedFileName();
        if (n == null) return "";
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        return slash >= 0 ? n.substring(slash + 1) : n;
    }
    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
