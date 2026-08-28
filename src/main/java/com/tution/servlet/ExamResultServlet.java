package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.CandidateDAO;
import com.tution.dao.ExamDAO;
import com.tution.dao.ExamResultDAO;
import com.tution.model.Exam;
import com.tution.model.ExamCandidate;
import com.tution.model.SubjectScore;
import com.tution.model.User;
import com.tution.service.ResultPublishService;
import com.tution.service.WhatsAppService;
import com.tution.util.ReceiptPdf;
import com.tution.util.ReminderConfig;
import com.tution.util.XlsxWriter;

/**
 * Published exam results: the ranked list, the export the institute can feed
 * back into its own template, a printable slip per candidate, the result message
 * to the parent, and the handoff that turns a candidate back into a live lead.
 */
@WebServlet("/scholarship-results")
public class ExamResultServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String BRAND = "Havellsson NEET Samrat";

    private final ExamDAO examDAO = new ExamDAO();
    private final ExamResultDAO resultDAO = new ExamResultDAO();
    private final CandidateDAO candidateDAO = new CandidateDAO();
    private final ResultPublishService publisher = new ResultPublishService();
    private final WhatsAppService whatsApp = new WhatsAppService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            List<Exam> exams = examDAO.findScholarshipExams();
            req.setAttribute("exams", exams);

            int examId = parseInt(req.getParameter("examId"), 0);
            if (examId <= 0 && !exams.isEmpty()) examId = exams.get(0).getExamId();
            if (examId <= 0) {
                req.getRequestDispatcher("/WEB-INF/views/exam_results.jsp").forward(req, resp);
                return;
            }
            Exam exam = examDAO.findById(examId);
            String school = req.getParameter("school");
            List<ExamResultDAO.Row> rows = filter(resultDAO.results(examId), school);

            if ("xlsx".equals(req.getParameter("export"))) { exportXlsx(resp, exam, rows); return; }
            if ("slip".equals(req.getParameter("action")))  { slip(req, resp, exam); return; }

            req.setAttribute("exam", exam);
            req.setAttribute("examId", examId);
            req.setAttribute("results", rows);
            req.setAttribute("schools", candidateDAO.schools(examId));
            req.setAttribute("fSchool", school);
            req.setAttribute("counts", candidateDAO.statusCounts(examId));
            req.setAttribute("dryRun", Boolean.valueOf(ReminderConfig.DRY_RUN));
            req.getRequestDispatcher("/WEB-INF/views/exam_results.jsp").forward(req, resp);

        } catch (SQLException e) {
            req.setAttribute("error", "Database error: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/exam_results.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int examId = parseInt(req.getParameter("examId"), 0);
        String action = req.getParameter("action");
        try {
            if ("publish".equals(action)) {
                User u = (User) req.getSession().getAttribute("user");
                ResultPublishService.Summary sum = publisher.publish(examId,
                        u == null ? "system" : u.getUsername());
                flash(req, sum.imported + " candidate(s) handed back to the sales pipeline as "
                         + "RESULT_DECLARED with their scholarship attached"
                         + (sum.skipped > 0 ? "; " + sum.skipped + " already admitted and left alone" : "")
                         + ".");
            } else if ("whatsapp".equals(action)) {
                sendResult(req, examId);
            } else {
                req.getSession().setAttribute("flashError", "Unknown action.");
            }
        } catch (SQLException e) {
            req.getSession().setAttribute("flashError", "Database error: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + "/scholarship-results?examId=" + examId);
    }

    /* ─── result message to the parent ─── */

    private void sendResult(HttpServletRequest req, int examId) throws SQLException {
        int candidateId = parseInt(req.getParameter("candidateId"), 0);
        ExamCandidate c = candidateDAO.findById(candidateId);
        if (c == null) { req.getSession().setAttribute("flashError", "Candidate not found."); return; }

        ExamResultDAO.Row row = null;
        for (ExamResultDAO.Row r : resultDAO.results(examId)) {
            if (r.candidateId == candidateId) { row = r; break; }
        }
        if (row == null) { req.getSession().setAttribute("flashError", "That candidate has no result."); return; }

        String mobile = c.getParentMobile() == null || c.getParentMobile().isEmpty()
                      ? c.getMobile() : c.getParentMobile();
        if (mobile == null || mobile.isEmpty()) {
            req.getSession().setAttribute("flashError",
                "No contact number on file for " + c.getFullName() + ".");
            return;
        }
        String message = resultMessage(c, row);

        // ReminderConfig.DRY_RUN ships true: the institute asked for sending to
        // stay manual, and this respects the same switch as every other send.
        if (ReminderConfig.DRY_RUN) {
            flash(req, "Sending is switched off (DRY_RUN). This is what would go to " + mobile
                     + ": \"" + message + "\"");
            return;
        }
        boolean ok = whatsApp.sendTemplate(mobile, ReminderConfig.TPL_GENERIC,
                ReminderConfig.LANG_GENERIC, c.getFullName(), message);
        if (ok) flash(req, "Result sent to " + mobile + ".");
        else req.getSession().setAttribute("flashError",
                "The gateway did not accept the message for " + mobile + ".");
    }

    /** One line, because the approved generic template carries one. */
    static String resultMessage(ExamCandidate c, ExamResultDAO.Row r) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getExamName()).append(" result - Roll ").append(r.rollNo)
          .append(": ").append(r.rawScore).append(" out of ").append(r.maxScore)
          .append(" (").append(String.format("%.2f", r.percentage)).append("%).");
        if (r.scholarshipPct > 0) {
            sb.append(" Scholarship earned: ").append(String.format("%.2f", r.scholarshipPct))
              .append("% on course fee.");
        }
        sb.append(" Please contact us to confirm your admission.");
        return sb.toString();
    }

    /* ─── printable slip ─── */

    private void slip(HttpServletRequest req, HttpServletResponse resp, Exam exam)
            throws SQLException, IOException {
        int candidateId = parseInt(req.getParameter("candidateId"), 0);
        ExamCandidate c = candidateDAO.findById(candidateId);
        ExamResultDAO.Row row = null;
        for (ExamResultDAO.Row r : resultDAO.results(exam.getExamId())) {
            if (r.candidateId == candidateId) { row = r; break; }
        }
        if (c == null || row == null) {
            resp.sendRedirect(req.getContextPath() + "/scholarship-results?examId=" + exam.getExamId());
            return;
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Name", c.getFullName() });
        rows.add(new String[] { "Roll Number", row.rollNo });
        rows.add(new String[] { "School", nn(c.getSchoolName()) });
        rows.add(new String[] { "Class / Board", nn(c.getClassName())
                              + (c.getBoard() == null || c.getBoard().isEmpty() ? "" : " / " + c.getBoard()) });
        rows.add(new String[] { "Exam", exam.getExamName() + " (" + exam.getExamType() + ")" });
        rows.add(new String[] { "Date", nn(exam.getExamDate()) });
        rows.add(new String[] { "Booklet", nn(row.booklet) });
        rows.add(new String[] { "-", "" });
        for (SubjectScore s : row.subjects) {
            rows.add(new String[] { s.name + "  (Q" + s.from + "-" + s.to + ")",
                                    s.correct + " right, " + s.wrong + " wrong  =  " + s.score });
        }
        rows.add(new String[] { "-", "" });
        rows.add(new String[] { "Attempted", row.attempted + " of " + exam.getTotalQuestions() });
        rows.add(new String[] { "Correct / Wrong", row.correct + " / " + row.wrong });
        rows.add(new String[] { "Total Score", row.rawScore + " out of " + row.maxScore });
        rows.add(new String[] { "Percentage", String.format("%.2f", row.percentage) + " %" });
        rows.add(new String[] { "Rank", String.valueOf(row.rank) });
        rows.add(new String[] { "Scholarship", String.format("%.2f", row.scholarshipPct)
                              + " % on course fee" });
        if (row.awardCriteria != null && !row.awardCriteria.isEmpty()) {
            rows.add(new String[] { "Awarded on", row.awardCriteria });
        }
        // ASCII only - ReceiptPdf strips anything outside 32..126.
        byte[] pdf = ReceiptPdf.document(BRAND, "Scholarship Exam Result",
                "Result Slip", "Roll: " + row.rollNo, rows.toArray(new String[0][]),
                "Marking: +" + exam.getMarkCorrect() + " correct, " + exam.getMarkWrong()
              + " wrong. Percentage is floored at zero. Scholarship is the single highest "
              + "route the candidate qualifies for.");
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition",
                "inline; filename=\"result-" + row.rollNo + ".pdf\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
    }

    /* ─── export in the institute's own 49-column shape ─── */

    /**
     * The columns of "LEAD TEMPLATE FOR NEW ERP.xlsx", in its own order, so the
     * export drops straight back into the sheet the schools already use.
     */
    private static final String[] TEMPLATE_HEADERS = {
        "Name", "Father's Name", "Mother's Name", "Student's Contact", "Father's Contact",
        "Mother's Contact", "Academic Term", "Date of Birth", "Gender", "Email", "School",
        "Class", "Select Course", "Course Interested In", "Stream", "Preferred Centre", "City",
        "District", "State", "Exam Roll Number", "Physics Score", "Chemistry Score", "Maths Score",
        "Biology Score", "Total Score", "Percentage Score", "Scholarship Percentage", "Board",
        "Date of Exam", "Exam Status", "EXAM Attempt", "Registered For HAMSE/HACKSE/HAT",
        "EXAM APPEARED", "Lead Source", "Street Address", "Student Type", "Caste category",
        "  Percentage Score in Previous Class", "Current Tutor Name/ Academy", "Sibling Detail",
        "How they came to know about Havellsson?", "Lead Stage", "Lead Sub Stage", "Lead Owner1",
        "Remark", "Other Remak", "UTR NUMBER", "Payment Status", "Exam Date"
    };

    private void exportXlsx(HttpServletResponse resp, Exam exam, List<ExamResultDAO.Row> rows)
            throws SQLException, IOException {
        List<String[]> out = new ArrayList<>();
        out.add(TEMPLATE_HEADERS);
        for (ExamResultDAO.Row r : rows) {
            ExamCandidate c = candidateDAO.findById(r.candidateId);
            String[] line = new String[TEMPLATE_HEADERS.length];
            for (int i = 0; i < line.length; i++) line[i] = "";
            line[0]  = nn(r.name);
            line[3]  = c == null ? "" : nn(c.getMobile());
            line[4]  = c == null ? "" : nn(c.getParentMobile());
            line[10] = nn(r.school);
            line[11] = c == null ? "" : nn(c.getClassName());
            line[16] = c == null ? "" : nn(c.getCity());
            line[19] = nn(r.rollNo);
            // subject scores land in their named columns; anything the institute
            // does not have a column for simply does not go out.
            for (SubjectScore s : r.subjects) {
                int col = subjectColumn(s.name);
                if (col >= 0) line[col] = String.valueOf(s.score);
            }
            line[24] = String.valueOf(r.rawScore);
            line[25] = String.format("%.2f", r.percentage);
            line[26] = String.format("%.2f", r.scholarshipPct);
            line[27] = c == null ? "" : nn(c.getBoard());
            line[28] = nn(exam.getExamDate());
            line[29] = nn(r.status);
            line[30] = c == null ? "1" : String.valueOf(c.getAttemptNo());
            line[31] = nn(exam.getExamType());
            line[32] = "YES";
            line[41] = "RESULT_DECLARED";
            line[48] = nn(exam.getExamDate());
            out.add(line);
        }
        byte[] xlsx = XlsxWriter.sheet("Results", out);
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition", "attachment; filename=\"exam-results.xlsx\"");
        resp.setContentLength(xlsx.length);
        resp.getOutputStream().write(xlsx);
    }

    private static int subjectColumn(String subject) {
        if (subject == null) return -1;
        String s = subject.trim().toLowerCase();
        if (s.startsWith("phys")) return 20;
        if (s.startsWith("chem")) return 21;
        if (s.startsWith("math")) return 22;
        if (s.startsWith("bio"))  return 23;
        return -1;
    }

    /* ─── helpers ─── */

    private List<ExamResultDAO.Row> filter(List<ExamResultDAO.Row> rows, String school) {
        if (school == null || school.trim().isEmpty()) return rows;
        List<ExamResultDAO.Row> out = new ArrayList<>();
        for (ExamResultDAO.Row r : rows) if (school.equals(r.school)) out.add(r);
        return out;
    }
    private void flash(HttpServletRequest req, String m) { req.getSession().setAttribute("flash", m); }
    private static String nn(String s) { return s == null ? "" : s; }
    private static int parseInt(String s, int d) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; }
    }
}
