package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.StudentDAO;
import com.tution.model.Student;
import com.tution.service.FeeService;
import com.tution.service.RazorpayService;
import com.tution.util.FeeCalculator;
import com.tution.util.RazorpayConfig;

/** Creates a Razorpay order and shows the checkout page. */
@WebServlet("/pay-online")
public class PayOnlineServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final StudentDAO studentDAO = new StudentDAO();
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
        if (studentId == null) {
            resp.sendRedirect(req.getContextPath() + "/fees");
            return;
        }

        try {
            Student student = studentDAO.findById(studentId);
            if (student == null) {
                resp.sendRedirect(req.getContextPath() + "/fees");
                return;
            }
            int outstanding = feeService.position(studentId, student.getFeeSlab()).outstanding;

            if (amount == null || amount <= 0 || amount > outstanding) {
                session.setAttribute("flashError",
                    "Enter a valid amount up to the outstanding " + FeeCalculator.inr(outstanding) + ".");
                resp.sendRedirect(req.getContextPath() + "/collect?studentId=" + studentId);
                return;
            }

            String receipt = "stu" + studentId + "-" + System.currentTimeMillis();
            String orderId = razorpay.createOrder(amount, receipt);

            req.setAttribute("student", student);
            req.setAttribute("amount", amount);
            req.setAttribute("orderId", orderId);
            req.setAttribute("mock", RazorpayConfig.isMock());
            req.setAttribute("keyId", RazorpayConfig.KEY_ID);
            req.setAttribute("mockPaymentId", "pay_TEST" + System.currentTimeMillis());
            req.getRequestDispatcher("/WEB-INF/views/checkout.jsp").forward(req, resp);

        } catch (SQLException e) {
            getServletContext().log("pay-online DB error", e);
            session.setAttribute("flashError", "Could not start the payment. Please try again.");
            resp.sendRedirect(req.getContextPath() + "/collect?studentId=" + studentId);
        } catch (IOException e) {
            getServletContext().log("Razorpay order error", e);
            session.setAttribute("flashError", "Payment gateway error: " + e.getMessage());
            resp.sendRedirect(req.getContextPath() + "/collect?studentId=" + studentId);
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
