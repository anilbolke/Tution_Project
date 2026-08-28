package com.tution.servlet;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.tution.dao.CandidateDAO;
import com.tution.dao.ExamDAO;
import com.tution.dao.ExamResultDAO;
import com.tution.dao.OmrTemplateDAO;
import com.tution.model.Exam;
import com.tution.model.ExamCandidate;
import com.tution.model.OmrTemplate;
import com.tution.model.User;
import com.tution.service.ExamScanService;
import com.tution.util.PdfJpegExtractor;

/**
 * Scans a batch of answer sheets for one exam: reads each, scores the ones it
 * can identify with certainty, and sends everything else to a review queue with
 * its image attached.
 *
 * Sheets are processed one at a time and released immediately. A scanned page at
 * full resolution is tens of megabytes as an int-RGB raster, so holding a whole
 * batch in memory is how this falls over on a real exam day.
 */
@WebServlet("/exam-scan")
// A whole hall's sheets arrive as one scanner PDF, so the per-FILE cap has to
// hold a multi-page document rather than a single JPEG. 30 MB would reject a
// 60-page scan; the request cap already allowed for a batch.
@MultipartConfig(fileSizeThreshold = 2 * 1024 * 1024, maxFileSize = 200 * 1024 * 1024,
                 maxRequestSize = 400 * 1024 * 1024)
public class ExamScanServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String UPLOAD_DIR = "uploads";
    /** Stored sheet images are downscaled to this width — enough to re-read a
     *  roll number by eye during review, small enough that 2,500 of them do not
     *  fill the disk. */
    private static final int ARCHIVE_WIDTH = 1200;
    /** Pages taken from PDFs in one upload. A cap, so a mis-picked 900-page
     *  file cannot tie up the request thread and fill the archive folder. */
    private static final int MAX_PDF_PAGES = 250;

    private final ExamDAO examDAO = new ExamDAO();
    private final OmrTemplateDAO templateDAO = new OmrTemplateDAO();
    private final CandidateDAO candidateDAO = new CandidateDAO();
    private final ExamResultDAO resultDAO = new ExamResultDAO();
    private final ExamScanService scanner = new ExamScanService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            List<Exam> exams = examDAO.findScholarshipExams();
            req.setAttribute("exams", exams);
            int examId = parseInt(req.getParameter("examId"), 0);
            if (examId <= 0 && !exams.isEmpty()) examId = exams.get(0).getExamId();

            if (examId > 0) {
                Exam exam = examDAO.findById(examId);
                req.setAttribute("exam", exam);
                req.setAttribute("examId", examId);
                req.setAttribute("results", resultDAO.results(examId));
                req.setAttribute("review", resultDAO.reviewQueue(examId));
                req.setAttribute("counts", candidateDAO.statusCounts(examId));
                req.setAttribute("blockers", blockers(exam));
            }
            req.getRequestDispatcher("/WEB-INF/views/exam_scan.jsp").forward(req, resp);
        } catch (SQLException e) {
            req.setAttribute("error", "Database error: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/exam_scan.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        int examId = parseInt(req.getParameter("examId"), 0);
        try {
            if ("rematch".equals(action))      { rematch(req, examId); }
            else if ("clear".equals(action))   { resultDAO.deleteResult(parseInt(req.getParameter("candidateId"), 0));
                                                 flash(req, "Result cleared. That candidate can be re-scanned."); }
            else if ("deleteScan".equals(action)) {
                String img = resultDAO.deleteScan(examId, parseInt(req.getParameter("scanId"), 0));
                deleteArchiveFile(img);
                flash(req, "Sheet removed from the review queue.");
            } else if ("deleteAllReview".equals(action)) {
                List<String> imgs = resultDAO.deleteAllReview(examId);
                for (String img : imgs) deleteArchiveFile(img);
                flash(req, imgs.size() + " sheet(s) removed from the review queue.");
            } else if ("absentees".equals(action)) {
                int n = candidateDAO.markAbsentees(examId);
                flash(req, n + " candidate(s) with no scanned sheet marked ABSENT.");
            } else {
                scanBatch(req, examId);
            }
        } catch (SQLException e) {
            req.getSession().setAttribute("flashError", "Database error: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + "/exam-scan?examId=" + examId);
    }

    /* ─── the batch ─── */

    private void scanBatch(HttpServletRequest req, int examId) throws IOException, SQLException, ServletException {
        Exam exam = examDAO.findById(examId);
        if (exam == null) { req.getSession().setAttribute("flashError", "Choose an exam first."); return; }

        List<String> blockers = blockers(exam);
        if (!blockers.isEmpty()) {
            req.getSession().setAttribute("flashError",
                "This exam is not ready to scan: " + String.join(" ", blockers));
            return;
        }
        OmrTemplate tpl = templateDAO.forExam(examId);
        ExamScanService.Context ctx = scanner.load(exam, tpl);

        User u = (User) req.getSession().getAttribute("user");
        String who = u == null ? "system" : u.getUsername();

        Collection<Part> parts = req.getParts();
        Set<String> seen = new HashSet<>();
        int matched = 0, review = 0, unreadable = 0, lowReg = 0;
        List<String> notes = new ArrayList<>();

        int pdfPages = 0;
        for (Part p : parts) {
            if (!"sheets".equals(p.getName()) || p.getSize() == 0) continue;
            String fileName = submittedFileName(p);

            byte[] data = readAll(p);

            // A scanner hands back one multi-page PDF far more often than a
            // folder of JPEGs, so the whole batch usually arrives as a single
            // file. Each page is pulled out and scored exactly as a loose image
            // would be - same reader, same rules, same review queue.
            if (PdfJpegExtractor.isPdf(data)) {
                List<byte[]> pages = PdfJpegExtractor.extract(data);
                if (pages.isEmpty()) {
                    unreadable++;
                    notes.add(fileName + ": no page images inside that PDF. It needs to be a "
                            + "SCANNED pdf (pages stored as images), not one exported from Word.");
                    continue;
                }
                int pageNo = 0;
                for (byte[] page : pages) {
                    if (pdfPages >= MAX_PDF_PAGES) {
                        notes.add(fileName + ": stopped at " + MAX_PDF_PAGES
                                + " pages. Split the file and upload the rest.");
                        break;
                    }
                    pageNo++; pdfPages++;
                    // Named per page so the review queue says which sheet in the
                    // stack it was - "scan.pdf" alone is useless with 60 in a pile.
                    String pageName = fileName + " p" + pageNo;
                    int[] tally = scanOne(ctx, examId, page, pageName, seen, who, notes);
                    matched += tally[0]; review += tally[1]; unreadable += tally[2]; lowReg += tally[3];
                }
                continue;
            }

            int[] tally = scanOne(ctx, examId, data, fileName, seen, who, notes);
            matched += tally[0]; review += tally[1]; unreadable += tally[2]; lowReg += tally[3];
        }

        StringBuilder msg = new StringBuilder();
        msg.append(matched).append(" sheet(s) scored");
        if (review > 0)     msg.append(", ").append(review).append(" sent for review");
        if (unreadable > 0) msg.append(", ").append(unreadable).append(" unreadable");
        if (lowReg > 0)     msg.append(". ").append(lowReg)
                               .append(" had weak page registration and deserve a check");
        msg.append('.');
        flash(req, msg.toString());
        if (!notes.isEmpty()) req.getSession().setAttribute("flashNotes", notes);
    }

    /**
     * Reads and scores one sheet, whether it came in loose or out of a PDF.
     *
     * @return {matched, review, unreadable, lowRegistration} to add to the batch tally.
     */
    private int[] scanOne(ExamScanService.Context ctx, int examId, byte[] data, String label,
                          Set<String> seen, String who, List<String> notes)
            throws IOException, SQLException {

        BufferedImage img;
        try (InputStream in = new ByteArrayInputStream(data)) {
            img = ImageIO.read(in);
        } catch (IOException e) {
            img = null;
        }
        if (img == null) {
            notes.add(label + ": not a readable image.");
            return new int[] { 0, 0, 1, 0 };
        }

        ExamScanService.Sheet s;
        String path;
        try {
            s = scanner.process(ctx, img, label, seen);
            path = archive(img, examId, label);
        } finally {
            img.flush();               // release the raster before the next page
        }
        resultDAO.save(examId, s, path, who);

        int matched = 0, review = 0;
        if (s.match == ExamScanService.Match.MATCHED) matched = 1;
        else { review = 1; if (s.note != null) notes.add(label + ": " + s.note); }
        return new int[] { matched, review, 0, s.lowRegistration ? 1 : 0 };
    }

    /** Reads a part fully; needed because a PDF must be inspected before decoding. */
    private static byte[] readAll(Part p) throws IOException {
        try (InputStream in = p.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /**
     * Assigns a reviewed sheet to a candidate by hand. The sheet keeps its
     * original scan row; only the link and the result are created, so the audit
     * trail still shows what the reader originally made of it.
     */
    private void rematch(HttpServletRequest req, int examId) throws SQLException {
        int scanId = parseInt(req.getParameter("scanId"), 0);
        String roll = trim(req.getParameter("rollNo"));
        if (scanId <= 0 || roll.isEmpty()) {
            req.getSession().setAttribute("flashError", "Give the roll number this sheet belongs to.");
            return;
        }
        ExamCandidate c = candidateDAO.findByRoll(examId, roll);
        if (c == null) {
            req.getSession().setAttribute("flashError",
                "No candidate in this exam holds roll " + roll + ".");
            return;
        }
        // Deliberately does NOT re-score from the stored image: the archive copy
        // is downscaled for review, and scoring from it could differ from the
        // original scan. Re-scan the paper sheet to produce marks.
        resultDAO.linkScanToCandidate(scanId, c.getCandidateId());
        flash(req, "Sheet linked to " + c.getFullName() + " (roll " + roll + "). "
                 + "Re-scan the paper sheet to score it.");
    }

    /* ─── helpers ─── */

    /** What still stands between this exam and a scan. */
    private List<String> blockers(Exam exam) throws SQLException {
        List<String> out = new ArrayList<>();
        if (exam == null) return out;
        if (exam.getTemplateId() == null) out.add("No answer sheet layout chosen.");
        com.tution.dao.ExamSetupDAO setup = new com.tution.dao.ExamSetupDAO();
        if (setup.subjectMap(exam.getExamId()).isEmpty()) out.add("No subject split set.");
        if (setup.keyCounts(exam.getExamId()).isEmpty()) out.add("No answer keys set.");
        return out;
    }

    /** Stores a downscaled JPEG copy, returning a web-relative path. */
    private String archive(BufferedImage img, int examId, String fileName) {
        try {
            String base = getServletContext().getRealPath("/") + UPLOAD_DIR + File.separator
                        + "omr" + File.separator + examId;
            File dir = new File(base);
            if (!dir.exists() && !dir.mkdirs()) return null;

            int w = img.getWidth(), h = img.getHeight();
            double scale = w > ARCHIVE_WIDTH ? (double) ARCHIVE_WIDTH / w : 1.0;
            int nw = (int) Math.round(w * scale), nh = (int) Math.round(h * scale);
            BufferedImage small = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();

            String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            String out = System.currentTimeMillis() + "_" + safe.replaceAll("\\.[^.]+$", "") + ".jpg";
            ImageIO.write(small, "jpg", new File(dir, out));
            small.flush();
            return UPLOAD_DIR + "/omr/" + examId + "/" + out;
        } catch (Exception e) {
            return null;                    // an archive failure must not lose the result
        }
    }

    /** Best-effort cleanup of an archived thumbnail; a leftover file is clutter, not a bug. */
    private void deleteArchiveFile(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) return;
        try {
            File f = new File(getServletContext().getRealPath("/"), imagePath.replace('/', File.separatorChar));
            f.delete();
        } catch (Exception ignore) { }
    }

    private static String submittedFileName(Part part) {
        String n = part.getSubmittedFileName();
        if (n == null) return "sheet";
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        return slash >= 0 ? n.substring(slash + 1) : n;
    }
    private void flash(HttpServletRequest req, String m) { req.getSession().setAttribute("flash", m); }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; }
    }
}
