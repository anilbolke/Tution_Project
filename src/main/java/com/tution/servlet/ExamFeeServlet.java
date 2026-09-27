package com.tution.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.ExamDAO;
import com.tution.dao.ExamPaymentDAO;
import com.tution.model.CandidateFee;
import com.tution.model.Exam;
import com.tution.model.ExamPayment;
import com.tution.model.User;
import com.tution.service.ExamFeeService;
import com.tution.service.SchoolPaymentService;
import com.tution.util.Money;
import com.tution.util.ReceiptPdf;

/**
 * Exam fees: who has paid, who has not, and taking money at the counter.
 *
 * Open to ADMIN and STAFF. Collecting a fee at the front desk is office work, so
 * this is deliberately NOT in {@code AuthFilter.ADMIN_ONLY} - unlike the fund and
 * the vendor screens, which are institute payables. Counsellors and teachers are
 * kept out entirely.
 */
@WebServlet("/exam-fees")
public class ExamFeeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ExamPaymentDAO       dao     = new ExamPaymentDAO();
    private final ExamFeeService       service = new ExamFeeService();
    private final SchoolPaymentService bulk    = new SchoolPaymentService();
    private final ExamDAO              examDao = new ExamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireOffice(req, resp);
        if (user == null) return;

        // A receipt download short-circuits the page entirely.
        int receiptId = parseInt(req.getParameter("receipt"), 0);
        if (receiptId > 0) { streamReceipt(receiptId, resp); return; }

        try {
            List<Exam> exams = examDao.findScholarshipExams();
            req.setAttribute("exams", exams);

            int examId = parseInt(req.getParameter("exam"), 0);
            if (examId <= 0 && !exams.isEmpty()) examId = exams.get(0).getExamId();

            // The bulk screen is its own page - ticking sixty candidates needs
            // the room, and mixing it into the counter screen would bury the
            // roll-number box that gets used a hundred times a day.
            if (req.getParameter("bulk") != null) { showBulk(req, resp, examId); return; }

            // Counter lookup: a roll number typed into the search box.
            String roll = trim(req.getParameter("roll"));
            if (roll != null) {
                try {
                    CandidateFee cf = service.lookup(roll);
                    req.setAttribute("lookup", cf);
                    req.setAttribute("lookupPayments", dao.paymentsFor(cf.getCandidateId()));
                    // Jump to the exam this roll actually belongs to, which may not
                    // be the one currently on screen - that mismatch is worth seeing.
                    examId = cf.getExamId();
                } catch (ExamFeeService.FeeException e) {
                    req.setAttribute("lookupError", e.getMessage());
                }
            }

            if (examId > 0) {
                Exam exam = examDao.findById(examId);
                req.setAttribute("exam", exam);
                req.setAttribute("examId", examId);

                String state  = trim(req.getParameter("state"));
                String school = trim(req.getParameter("school"));
                String q      = trim(req.getParameter("q"));

                req.setAttribute("rows",    dao.list(examId, state, school, q));
                req.setAttribute("totals",  dao.totals(examId));
                req.setAttribute("schools", dao.schools(examId));
                req.setAttribute("state",  state  == null ? "" : state);
                req.setAttribute("school", school == null ? "" : school);
                req.setAttribute("q",      q      == null ? "" : q);
            } else {
                req.setAttribute("rows", new ArrayList<CandidateFee>());
            }
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load exam fees: " + e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/exam_fees.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireOffice(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");
        int examId    = parseInt(req.getParameter("examId"), 0);
        String back   = "/exam-fees" + (examId > 0 ? "?exam=" + examId : "");

        try {
            switch (action) {
                case "collect": {
                    ExamPayment p = service.collect(
                            parseInt(req.getParameter("candidateId"), 0),
                            req.getParameter("amount"),
                            req.getParameter("date"),
                            req.getParameter("mode"),
                            req.getParameter("ref"),
                            req.getParameter("remarks"),
                            user);
                    flash(req, "Received " + Money.rs(p.getAmount()) + " from "
                             + p.getCandidateName() + " (roll " + p.getRollNo()
                             + "). Receipt " + p.getReceiptNo() + ".");
                    // Land back on the candidate so the receipt can be printed.
                    back = "/exam-fees?roll=" + p.getRollNo();
                    break;
                }
                case "setfee": {
                    if (!requireManagement(req, resp, user, "change what an exam costs")) return;
                    service.setExamFee(examId, req.getParameter("fee"));
                    flash(req, "Exam fee updated. It applies to every candidate who does not "
                             + "have their own amount set.");
                    break;
                }
                case "bulk": {
                    SchoolPaymentService.Result r = bulk.collect(
                            examId,
                            req.getParameter("school"),
                            intList(req.getParameterValues("candidateId")),
                            req.getParameter("total"),
                            req.getParameter("date"),
                            req.getParameter("mode"),
                            req.getParameter("ref"),
                            req.getParameter("remarks"),
                            user);
                    flash(req, bulkMessage(r));
                    back = "/exam-fees?bulk=1&exam=" + examId
                         + "&cheque=" + r.receipt.getSchoolReceiptId();
                    break;
                }
                case "waive": {
                    if (!requireManagement(req, resp, user, "waive a fee")) return;
                    boolean on = !"0".equals(req.getParameter("waived"));
                    service.waive(parseInt(req.getParameter("candidateId"), 0),
                                  req.getParameter("reason"), on);
                    flash(req, on ? "Fee waived. This candidate is no longer counted as owing, "
                                  + "and the reason is kept on their record."
                                  : "Waiver removed. The exam fee applies to this candidate again.");
                    String roll = trim(req.getParameter("roll"));
                    if (roll != null) back = "/exam-fees?roll=" + roll;
                    break;
                }
                case "concession": {
                    if (!requireManagement(req, resp, user, "set a candidate's own amount")) return;
                    service.setConcession(parseInt(req.getParameter("candidateId"), 0),
                                          req.getParameter("fee"));
                    flash(req, trim(req.getParameter("fee")) == null
                            ? "Own amount cleared. This candidate is back on the exam's fee."
                            : "Own amount saved. It overrides the exam fee for this candidate only.");
                    String roll = trim(req.getParameter("roll"));
                    if (roll != null) back = "/exam-fees?roll=" + roll;
                    break;
                }
                case "void": {
                    service.voidReceipt(parseInt(req.getParameter("paymentId"), 0),
                                        req.getParameter("reason"), user);
                    flash(req, "Receipt cancelled. It stays on the candidate's history, "
                             + "marked cancelled, and no longer counts towards what they have paid.");
                    String roll = trim(req.getParameter("roll"));
                    if (roll != null) back = "/exam-fees?roll=" + roll;
                    break;
                }
                default:
                    flashError(req, "Unknown action.");
            }
        } catch (ExamFeeService.FeeException e) {
            flashError(req, e.getMessage());
            // Send them back to the screen they were on, so the refusal is
            // readable next to the thing that caused it.
            if ("bulk".equals(action)) {
                back = "/exam-fees?bulk=1&exam=" + examId
                     + "&school=" + urlEncode(req.getParameter("school"));
            } else {
                String roll = trim(req.getParameter("roll"));
                if (roll != null) back = "/exam-fees?roll=" + roll;
            }
        } catch (SQLException e) {
            flashError(req, "Could not save: " + e.getMessage());
            if ("bulk".equals(action)) back = "/exam-fees?bulk=1&exam=" + examId;
        }
        resp.sendRedirect(req.getContextPath() + back);
    }

    /**
     * The bulk school payment screen: one exam, one school, a tick list of who
     * still owes, and one total.
     */
    private void showBulk(HttpServletRequest req, HttpServletResponse resp, int examId)
            throws ServletException, IOException {
        try {
            String school = trim(req.getParameter("school"));
            req.setAttribute("examId", examId);
            req.setAttribute("school", school == null ? "" : school);
            if (examId > 0) {
                req.setAttribute("exam",    examDao.findById(examId));
                req.setAttribute("schools", dao.schools(examId));
                req.setAttribute("totals",  dao.totals(examId));
                req.setAttribute("cheques", dao.schoolReceipts(examId));
                // Only candidates who still owe: paid and waived ones cannot take
                // any of the money, so offering them invites a mis-tick.
                List<CandidateFee> due = new ArrayList<>();
                for (CandidateFee c : dao.list(examId, null, school, null)) {
                    if (c.isDue()) due.add(c);
                }
                req.setAttribute("rows", due);
            } else {
                req.setAttribute("rows", new ArrayList<CandidateFee>());
            }

            int chequeId = parseInt(req.getParameter("cheque"), 0);
            if (chequeId > 0) req.setAttribute("cheque", dao.findSchoolReceipt(chequeId));
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load the school payment screen: " + e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/exam_bulk.jsp").forward(req, resp);
    }

    /* ───────────────────────── receipt ───────────────────────── */

    /**
     * Builds the fee receipt as a PDF and streams it.
     *
     * ASCII ONLY - {@link ReceiptPdf} strips anything outside 32..126, so every
     * amount goes through {@link Money#rs} ("Rs. ") and never the rupee sign,
     * which would otherwise vanish and leave a bare number.
     */
    private void streamReceipt(int paymentId, HttpServletResponse resp) throws IOException {
        ExamPayment p;
        CandidateFee cf;
        try {
            p = dao.findPayment(paymentId);
            if (p == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such receipt."); return; }
            cf = dao.findByCandidate(p.getCandidateId());
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not build the receipt.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Roll Number",  p.getRollNo() });
        rows.add(new String[] { "Candidate",    p.getCandidateName() });
        if (p.getSchoolName() != null) rows.add(new String[] { "School", p.getSchoolName() });
        rows.add(new String[] { "Exam",         p.getExamName() });
        rows.add(new String[] { "Payment Date", p.getPaymentDate() });
        rows.add(new String[] { "Payment Mode", p.getPaymentMode() });
        if (p.getTxnRef() != null) rows.add(new String[] { "Reference", p.getTxnRef() });
        rows.add(new String[] { "-", "" });
        if (cf != null) {
            rows.add(new String[] { "Exam Fee",     Money.rs(cf.getPayable()) });
            rows.add(new String[] { "Amount Paid",  Money.rs(p.getAmount()) });
            rows.add(new String[] { "Paid To Date", Money.rs(cf.getPaid()) });
            rows.add(new String[] { "Balance Due",  Money.rs(cf.getBalance()) });
        } else {
            rows.add(new String[] { "Amount Paid",  Money.rs(p.getAmount()) });
        }
        if (p.getCollectedBy() != null) {
            rows.add(new String[] { "Received By", p.getCollectedBy() });
        }
        if (p.isVoid()) {
            rows.add(new String[] { "-", "" });
            rows.add(new String[] { "CANCELLED", p.getVoidReason() == null ? "" : p.getVoidReason() });
        }

        byte[] pdf = ReceiptPdf.receipt(
                "Havellsson NEET Samrat",
                "Scholarship Exam - Fee Receipt",
                p.getReceiptNo(),
                rows.toArray(new String[0][]),
                "This is a computer-generated receipt. Thank you!");

        resp.setContentType("application/pdf");
        resp.setContentLength(pdf.length);
        resp.setHeader("Content-Disposition",
                       "inline; filename=\"" + p.getReceiptNo() + ".pdf\"");
        resp.getOutputStream().write(pdf);
    }

    /* ───────────────────────── helpers ───────────────────────── */

    /**
     * Guards the actions that decide what money is owed rather than take it.
     *
     * TAKING A FEE AND PRICING ONE ARE DIFFERENT JOBS. The counter needs to
     * collect, print a receipt and correct a mis-keyed one on the spot, so STAFF
     * can do all of that. But setting the exam fee changes what every candidate
     * owes, and a waiver or an own-amount gives revenue away for one of them -
     * those are management decisions, and without this check the front desk could
     * quietly reprice the whole exam.
     *
     * @return true to carry on; false having already sent a 403.
     */
    private boolean requireManagement(HttpServletRequest req, HttpServletResponse resp,
                                      User user, String what) throws IOException {
        if (user.isAdmin()) return true;
        resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                       "Only the administrator can " + what + ". You can still take payments.");
        return false;
    }

    /**
     * Returns the logged-in office user, or null having already redirected.
     * Who may take a fee is the FIN_EXAM_FEES activity in the role matrix.
     */
    private User requireOffice(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        if (!user.can("FIN_EXAM_FEES")) {
            resp.sendRedirect(req.getContextPath() + user.homePath());
            return null;
        }
        return user;
    }

    private void flash(HttpServletRequest req, String msg) {
        req.getSession().setAttribute("flash", msg);
    }

    private void flashError(HttpServletRequest req, String msg) {
        req.getSession().setAttribute("flashError", msg);
    }

    private static String trim(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private static String orDefault(String v, String dflt) {
        return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
    }

    private static int parseInt(String v, int dflt) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return dflt; }
    }

    /** Checkbox values to ids, quietly dropping anything that is not a number. */
    private static List<Integer> intList(String[] vals) {
        List<Integer> out = new ArrayList<>();
        if (vals == null) return out;
        for (String v : vals) {
            int id = parseInt(v, 0);
            if (id > 0 && !out.contains(id)) out.add(id);
        }
        return out;
    }

    private static String urlEncode(String v) {
        try { return v == null ? "" : URLEncoder.encode(v, "UTF-8"); }
        catch (IOException e) { return ""; }
    }

    /**
     * Says exactly what the cheque did. A bulk payment touches dozens of
     * candidates at once, so "saved" is not good enough - the person who keyed
     * it needs to see the split, and any shortfall, while they still have the
     * cheque in their hand.
     */
    private static String bulkMessage(SchoolPaymentService.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("School payment ").append(r.receipt.getReceiptNo()).append(" of ")
          .append(Money.rs(r.receipt.getTotalAmount())).append(" split across ")
          .append(r.allocations.size()).append(" candidate(s) — ")
          .append(r.settled).append(" now fully paid.");
        if (!r.skipped.isEmpty()) {
            sb.append(" ").append(r.skipped.size())
              .append(" ticked candidate(s) were already settled and were left out.");
        }
        if (r.shortfall.signum() > 0) {
            sb.append(" ").append(Money.rs(r.shortfall))
              .append(" is still owed — the cheque did not cover everyone ticked.");
        }
        return sb.toString();
    }
}
