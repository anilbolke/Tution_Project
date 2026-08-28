package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.PaymentDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.Payment;
import com.tution.model.Student;
import com.tution.model.User;
import com.tution.service.FeeService;
import com.tution.service.RazorpayService;
import com.tution.util.RazorpayConfig;

/** Verifies the Razorpay callback signature and records the payment. */
@WebServlet("/pay-verify")
public class PayVerifyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final StudentDAO studentDAO = new StudentDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final FeeService feeService = new FeeService();
    private final RazorpayService razorpay = new RazorpayService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        Integer studentId = parseInt(req.getParameter("studentId"));
        Integer amount    = parseInt(req.getParameter("amount"));
        String orderId    = trim(req.getParameter("razorpay_order_id"));
        String paymentId  = trim(req.getParameter("razorpay_payment_id"));
        String signature  = trim(req.getParameter("razorpay_signature"));

        if (studentId == null) {
            resp.sendRedirect(req.getContextPath() + "/fees");
            return;
        }

        // 1) Authenticity check (skipped in mock mode)
        if (!razorpay.verify(orderId, paymentId, signature)) {
            session.setAttribute("flashError", "Payment verification failed. Nothing was charged twice — please retry.");
            resp.sendRedirect(req.getContextPath() + "/collect?studentId=" + studentId);
            return;
        }

        try {
            // 2) Re-check the outstanding server-side (never trust the posted amount blindly)
            Student student = studentDAO.findById(studentId);
            if (student == null) {
                resp.sendRedirect(req.getContextPath() + "/fees");
                return;
            }
            int outstanding = feeService.position(studentId, student.getFeeSlab()).outstanding;

            if (amount == null || amount <= 0 || amount > outstanding) {
                session.setAttribute("flashError", "Payment amount is invalid against the current balance.");
                resp.sendRedirect(req.getContextPath() + "/collect?studentId=" + studentId);
                return;
            }

            // 3) Record it
            User user = (User) session.getAttribute("user");
            Payment p = new Payment();
            p.setStudentId(studentId);
            p.setAmount(amount);
            p.setPaymentMode("Online");
            p.setPaymentDate(LocalDate.now().toString());
            p.setRemarks(RazorpayConfig.isMock() ? "Razorpay (TEST)" : "Razorpay");
            p.setCollectedBy((user == null ? "" : user.getFullName()) + " (Online)");
            p.setCollectedById(user == null ? null : Integer.valueOf(user.getUserId()));
            p.setTxnRef(paymentId);
            p.setRazorpayOrderId(orderId);
            p.setRazorpayPaymentId(paymentId);

            int newId = paymentDAO.insert(p);
            resp.sendRedirect(req.getContextPath() + "/receipt?paymentId=" + newId);

        } catch (SQLException e) {
            getServletContext().log("pay-verify save failed", e);
            session.setAttribute("flashError", "Payment succeeded but saving failed. Please contact admin with id " + paymentId + ".");
            resp.sendRedirect(req.getContextPath() + "/collect?studentId=" + studentId);
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
