package com.tution.servlet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.tution.dao.OmrScanDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.OmrBatchRow;
import com.tution.model.OmrResult;
import com.tution.model.Student;
import com.tution.model.SubjectScore;
import com.tution.model.User;
import com.tution.service.OmrService;
import com.tution.util.PdfJpegExtractor;

/** OMR answer-sheet scanner — upload a sheet, read the bubbles locally, optionally score. */
@WebServlet("/omr")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 80 * 1024 * 1024, maxRequestSize = 90 * 1024 * 1024)
public class OmrScanServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final OmrService  omr        = new OmrService();
    private final OmrScanDAO  scanDAO    = new OmrScanDAO();
    private final StudentDAO  studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;
        loadRecent(req);
        req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        try {
            byte[] data = loadBytes(req);
            if (data == null || data.length == 0) {
                req.setAttribute("error", "Please choose an answer-sheet image or PDF (or tick “Use sample sheet”).");
                loadRecent(req);
                req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
                return;
            }

            int markCorrect = parseInt(req.getParameter("markCorrect"), 4);
            int markWrong   = parseInt(req.getParameter("markWrong"), -1);
            Map<Integer, String> key = omr.parseAnswerKey(req.getParameter("answerKey"));
            String title = trim(req.getParameter("title"));
            if (title.isEmpty()) title = "Scan";
            User user = (User) req.getSession().getAttribute("user");
            String by = (user == null) ? "" : user.getFullName();
            List<SubjectScore> ranges = buildRanges(req);
            Map<Integer, String> keyOrNull = key.isEmpty() ? null : key;

            // ── PDF: scan every page ──
            if (PdfJpegExtractor.isPdf(data)) {
                List<byte[]> pages = PdfJpegExtractor.extract(data);
                if (pages.isEmpty()) {
                    req.setAttribute("error", "No page images found in that PDF — use a scanned (image) PDF of the sheets.");
                    loadRecent(req);
                    req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
                    return;
                }
                List<OmrBatchRow> batch = new ArrayList<>();
                int pno = 0;
                for (byte[] pg : pages) {
                    if (pno >= 50) break;                          // safety cap
                    BufferedImage img;
                    try (InputStream in = new ByteArrayInputStream(pg)) { img = ImageIO.read(in); }
                    if (img == null) continue;
                    pno++;
                    OmrResult result = omr.read(img);
                    if (!key.isEmpty()) omr.score(result, key, markCorrect, markWrong);
                    omr.subjectBreakdown(result, keyOrNull, markCorrect, markWrong, ranges);
                    String overlayUrl = saveOverlay(req, img, result);
                    int id = scanDAO.insert(title + " — p" + pno, result.getPage(), result.getStartQ(), result.getEndQ(),
                            result.total(), result.attempted(), result.blankCount(), result.getAmbiguous().size(),
                            result.isScored() ? result.getCorrect() : null,
                            result.isScored() ? result.getWrong() : null,
                            result.isScored() ? result.getScore() : null,
                            answersJson(result), by);
                    OmrBatchRow row = new OmrBatchRow();
                    row.page = pno; row.scanId = id;
                    row.attempted = result.attempted(); row.blank = result.blankCount();
                    row.ambiguous = result.getAmbiguous().size();
                    if (result.isScored()) { row.correct = result.getCorrect(); row.wrong = result.getWrong(); row.score = result.getScore(); }
                    row.overlayUrl = overlayUrl;
                    row.subjects = result.getSubjects();
                    row.rollNo = omr.readRoll(img);
                    if (!row.rollNo.isEmpty()) {
                        Student st = studentDAO.findByRoll(row.rollNo);
                        if (st != null) row.studentName = st.getFullName();
                    }
                    batch.add(row);
                }
                req.setAttribute("batch", batch);
                req.setAttribute("batchTitle", title);
                req.setAttribute("scored", !key.isEmpty());
                storeExport(req, batch, !key.isEmpty(), title);
                loadRecent(req);
                req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
                return;
            }

            // ── single image ──
            BufferedImage img;
            try (InputStream in = new ByteArrayInputStream(data)) { img = ImageIO.read(in); }
            if (img == null) {
                req.setAttribute("error", "Could not read the file. Use a clear JPG/PNG image or a scanned PDF.");
                loadRecent(req);
                req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
                return;
            }
            OmrResult result = omr.read(img);
            if (!key.isEmpty()) omr.score(result, key, markCorrect, markWrong);
            omr.subjectBreakdown(result, keyOrNull, markCorrect, markWrong, ranges);
            String overlayUrl = saveOverlay(req, img, result);
            int id = scanDAO.insert(title, result.getPage(), result.getStartQ(), result.getEndQ(),
                    result.total(), result.attempted(), result.blankCount(), result.getAmbiguous().size(),
                    result.isScored() ? result.getCorrect() : null,
                    result.isScored() ? result.getWrong() : null,
                    result.isScored() ? result.getScore() : null,
                    answersJson(result), by);

            String rollNo = omr.readRoll(img);
            Student rollStudent = rollNo.isEmpty() ? null : studentDAO.findByRoll(rollNo);
            req.setAttribute("rollNo", rollNo);
            req.setAttribute("rollStudent", rollStudent);

            // make this single result exportable to Excel (one row)
            OmrBatchRow exRow = new OmrBatchRow();
            exRow.page = 1;
            exRow.attempted = result.attempted();
            exRow.blank = result.blankCount();
            exRow.ambiguous = result.getAmbiguous().size();
            if (result.isScored()) { exRow.correct = result.getCorrect(); exRow.wrong = result.getWrong(); exRow.score = result.getScore(); }
            exRow.subjects = result.getSubjects();
            exRow.rollNo = rollNo;
            exRow.studentName = (rollStudent == null) ? null : rollStudent.getFullName();
            List<OmrBatchRow> exList = new ArrayList<>();
            exList.add(exRow);
            storeExport(req, exList, result.isScored(), title);

            req.setAttribute("result", result);
            req.setAttribute("overlayUrl", overlayUrl);
            req.setAttribute("title", title);
            req.setAttribute("scanId", id);
            req.setAttribute("markCorrect", markCorrect);
            req.setAttribute("markWrong", markWrong);
            loadRecent(req);
            req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);

        } catch (SQLException e) {
            getServletContext().log("OMR save failed", e);
            req.setAttribute("error", "Scan succeeded but saving failed. Please try again.");
            loadRecent(req);
            req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
        } catch (Exception e) {
            getServletContext().log("OMR scan failed", e);
            req.setAttribute("error", "Could not read the image. Use a clear JPG/PNG scan of the answer sheet.");
            loadRecent(req);
            req.getRequestDispatcher("/WEB-INF/views/omr_scan.jsp").forward(req, resp);
        }
    }

    private byte[] loadBytes(HttpServletRequest req) throws IOException, ServletException {
        if ("1".equals(req.getParameter("useSample"))) {
            File f = new File(getServletContext().getRealPath("/uploads/omr/sample_sheet.jpg"));
            return f.exists() ? Files.readAllBytes(f.toPath()) : null;
        }
        Part part = req.getPart("sheet");
        if (part == null || part.getSize() == 0) return null;
        try (InputStream in = part.getInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            return bos.toByteArray();
        }
    }

    private String saveOverlay(HttpServletRequest req, BufferedImage img, OmrResult result) {
        try {
            BufferedImage ov = omr.overlay(img, result);
            String dir = getServletContext().getRealPath("/") + "uploads/omr";
            new File(dir).mkdirs();
            String name = "overlay_" + System.currentTimeMillis() + ".jpg";
            ImageIO.write(ov, "jpg", new File(dir, name));
            return req.getContextPath() + "/uploads/omr/" + name;
        } catch (Exception e) {
            getServletContext().log("overlay write failed", e);
            return null;
        }
    }

    private String answersJson(OmrResult r) {
        StringBuilder sb = new StringBuilder("{\"page\":").append(r.getPage()).append(",\"answers\":{");
        boolean first = true;
        for (Map.Entry<Integer, String> e : r.getAnswers().entrySet()) {
            if (!first) sb.append(","); first = false;
            sb.append("\"").append(e.getKey()).append("\":")
              .append(e.getValue() == null ? "null" : "\"" + e.getValue() + "\"");
        }
        return sb.append("}}").toString();
    }

    private void loadRecent(HttpServletRequest req) {
        try { req.setAttribute("recent", scanDAO.findRecent(10)); }
        catch (SQLException e) { getServletContext().log("load recent omr failed", e); }
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }
    /** Reads the subject question ranges from the form (defaults to NEET 45/45/45/45). */
    private List<SubjectScore> buildRanges(HttpServletRequest req) {
        String[] names = { "Physics", "Chemistry", "Botany", "Zoology" };
        int[][] def = { {1, 45}, {46, 90}, {91, 135}, {136, 180} };
        List<SubjectScore> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int from = parseInt(req.getParameter("from" + (i + 1)), def[i][0]);
            int to   = parseInt(req.getParameter("to" + (i + 1)),   def[i][1]);
            String nm = trim(req.getParameter("subj" + (i + 1)));
            if (nm.isEmpty()) nm = names[i];
            if (from > 0 && to >= from) list.add(new SubjectScore(nm, from, to));
        }
        return list;
    }

    /** Stashes the latest scan results in the session so /omr-export can build the Excel file. */
    private void storeExport(HttpServletRequest req, List<OmrBatchRow> rows, boolean scored, String title) {
        req.getSession().setAttribute("omrExportRows", rows);
        req.getSession().setAttribute("omrExportScored", scored);
        req.getSession().setAttribute("omrExportTitle", title);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static int parseInt(String s, int def) {
        try { return s == null || s.trim().isEmpty() ? def : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
