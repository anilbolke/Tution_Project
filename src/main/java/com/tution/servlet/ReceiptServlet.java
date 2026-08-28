package com.tution.servlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.InstallmentDAO;
import com.tution.dao.PaymentDAO;
import com.tution.model.Payment;
import com.tution.service.FeeService;
import com.tution.service.ReminderScheduler;
import com.tution.service.WhatsAppService;
import com.tution.util.ReceiptPdf;
import com.tution.util.WhatsAppConfig;

/** Renders a printable receipt for a payment. */
@WebServlet("/receipt")
public class ReceiptServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final PaymentDAO     paymentDAO     = new PaymentDAO();
    private final InstallmentDAO installmentDAO = new InstallmentDAO();
    private final FeeService     feeService     = new FeeService();
    private final WhatsAppService whatsApp      = new WhatsAppService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        Integer paymentId = parseInt(req.getParameter("paymentId"));
        if (paymentId == null) {
            resp.sendRedirect(req.getContextPath() + "/fees");
            return;
        }

        try {
            Payment p = paymentDAO.findById(paymentId);
            if (p == null) {
                resp.sendRedirect(req.getContextPath() + "/fees");
                return;
            }
            // total / paid-to-date / outstanding as of this receipt
            FeeService.Position pos = feeService.position(p.getStudentId(), p.getFeeSlab());
            int total       = pos.total;
            int paidToDate  = pos.paid;
            int outstanding = pos.outstanding;

            req.setAttribute("payment", p);
            req.setAttribute("totalFee", total);
            req.setAttribute("paidToDate", paidToDate);
            req.setAttribute("outstanding", outstanding);
            req.setAttribute("position", pos);
            req.setAttribute("schedule", installmentDAO.findByStudent(p.getStudentId()));

            // ── receipt PDF + WhatsApp send (once per payment) ──
            String pdfRel = writeReceiptPdf(p, pos);                  // /uploads/receipts/receipt_<id>.pdf
            req.setAttribute("pdfUrl", req.getContextPath() + pdfRel);

            String mobile = p.getStudentMobile();
            String amt = "Rs. " + String.format("%,d", p.getAmount());
            String bal = "Rs. " + String.format("%,d", outstanding);
            req.setAttribute("waLink",
                    whatsApp.link(mobile, whatsApp.paymentMessage(p.getStudentName(), p.getReceiptNo(),
                            amt, p.getPaymentDate(), p.getPaymentMode(), bal)));   // tap-to-send fallback

            if (mobile != null && !mobile.isEmpty()) {
                if (paymentDAO.isWaSent(paymentId)) {
                    req.setAttribute("waAlready", Boolean.TRUE);
                } else {
                    // Hand the send to the background pool. It used to run inline,
                    // so a slow gateway held the receipt page for up to 30s of
                    // connect+read timeout while the operator stared at a blank tab.
                    final String docUrl = publicBase(req) + pdfRel;
                    final String fName  = p.getStudentName();
                    final String fRcpt  = p.getReceiptNo();
                    final String fDate  = p.getPaymentDate();
                    final String fMode  = p.getPaymentMode();
                    final int    fPayId = paymentId.intValue();
                    ReminderScheduler.submit(() -> {
                        try {
                            boolean sent = new WhatsAppService().sendPayment(
                                mobile, fName, fRcpt, amt, fDate, fMode, bal, docUrl);
                            if (sent) {
                                new PaymentDAO().markWaSent(fPayId);
                            }
                        } catch (Exception ex) {
                            getServletContext().log("Receipt WhatsApp send failed for payment "
                                                    + fPayId, ex);
                        }
                    });
                    req.setAttribute("waQueued", Boolean.TRUE);
                }
            }

            req.getRequestDispatcher("/WEB-INF/views/receipt.jsp").forward(req, resp);

        } catch (SQLException e) {
            getServletContext().log("Load receipt failed", e);
            resp.sendRedirect(req.getContextPath() + "/fees");
        }
    }

    /**
     * Generates the receipt PDF and writes it under /uploads/receipts/.
     *
     * The fee is itemised now — registration, material, tuition and any
     * concession are shown separately, because a parent given a discount needs
     * to see it on the receipt. Every string stays ASCII-only: ReceiptPdf.safe()
     * strips anything outside 32..126, so "Rs. " is used, never the rupee sign.
     */
    private String writeReceiptPdf(Payment p, FeeService.Position pos) throws IOException {
        List<String[]> rowList = new ArrayList<>();
        rowList.add(new String[] { "Student Name", p.getStudentName() });
        rowList.add(new String[] { "Admission No", p.getAdmissionNo() });
        rowList.add(new String[] { "Class",        p.getClassName() });
        rowList.add(new String[] { "Payment Date", p.getPaymentDate() });
        rowList.add(new String[] { "Payment Mode", p.getPaymentMode() });
        if (p.getTxnRef() != null && !p.getTxnRef().isEmpty()) {
            rowList.add(new String[] { "Reference", p.getTxnRef() });
        }
        rowList.add(new String[] { "", "" });
        rowList.add(new String[] { "Registration Fee", money(pos.registrationFee) });
        rowList.add(new String[] { "Study Material",   money(pos.materialFee) });
        rowList.add(new String[] { "Tuition Fee",      money(pos.courseFee) });
        if (pos.discount > 0) {
            rowList.add(new String[] { "Discount",    "- " + money(pos.discount) });
        }
        if (pos.scholarship > 0) {
            rowList.add(new String[] { "Scholarship", "- " + money(pos.scholarship) });
        }
        rowList.add(new String[] { "Total Payable",  money(pos.total) });
        rowList.add(new String[] { "", "" });
        rowList.add(new String[] { "Amount Paid",   money(p.getAmount()) });
        rowList.add(new String[] { "Paid To Date",  money(pos.paid) });
        rowList.add(new String[] { "Balance Due",   money(pos.outstanding) });
        rowList.add(new String[] { "Collected By",  p.getCollectedBy() });
        String[][] rows = rowList.toArray(new String[0][]);
        byte[] pdf = ReceiptPdf.receipt("Havellsson NEET Samrat", "Expert NEET Coaching - 11th & 12th Science",
                p.getReceiptNo(), rows, "This is a computer-generated receipt. Thank you!");
        String dir = getServletContext().getRealPath("/") + "uploads/receipts";
        new File(dir).mkdirs();
        String name = "receipt_" + p.getPaymentId() + ".pdf";
        Files.write(new File(dir, name).toPath(), pdf);
        return "/uploads/receipts/" + name;
    }

    /** Public base URL for the receipt link (configured PUBLIC_BASE_URL, else derived from request). */
    private String publicBase(HttpServletRequest req) {
        String base = WhatsAppConfig.PUBLIC_BASE_URL;
        if (base != null && !base.isEmpty()) return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        int port = req.getServerPort();
        String hostPort = req.getServerName() + ((port == 80 || port == 443) ? "" : ":" + port);
        return req.getScheme() + "://" + hostPort + req.getContextPath();
    }

    /** ASCII-safe rupee formatting for the PDF — never the Unicode rupee sign. */
    private static String money(int amount) {
        return "Rs. " + String.format("%,d", amount);
    }

    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
