package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.FeeSlabDAO;
import com.tution.dao.InstallmentDAO;
import com.tution.dao.PaymentDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.FeeInstallment;
import com.tution.model.FeeSlab;
import com.tution.model.Payment;
import com.tution.model.Student;
import com.tution.model.User;
import com.tution.service.FeeService;
import com.tution.util.FeeCalculator;

/** Shows the collection form for a student and records a payment. */
@WebServlet("/collect")
public class CollectPaymentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final StudentDAO     studentDAO     = new StudentDAO();
    private final PaymentDAO     paymentDAO     = new PaymentDAO();
    private final FeeSlabDAO     feeSlabDAO     = new FeeSlabDAO();
    private final InstallmentDAO installmentDAO = new InstallmentDAO();
    private final FeeService     feeService     = new FeeService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        Integer studentId = parseInt(req.getParameter("studentId"));
        if (studentId == null) {
            resp.sendRedirect(req.getContextPath() + "/fees");
            return;
        }
        try {
            if (!loadStudentContext(req, studentId)) {
                resp.sendRedirect(req.getContextPath() + "/fees");
                return;
            }
        } catch (SQLException e) {
            getServletContext().log("Load collect form failed", e);
            req.setAttribute("error", "Could not load the student. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/collect.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        Integer studentId = parseInt(req.getParameter("studentId"));
        if (studentId == null) {
            resp.sendRedirect(req.getContextPath() + "/fees");
            return;
        }

        Integer amount = parseInt(req.getParameter("amount"));
        String  mode   = trim(req.getParameter("mode"));
        String  date   = trim(req.getParameter("paymentDate"));
        String  remarks = trim(req.getParameter("remarks"));
        if (date.isEmpty()) date = LocalDate.now().toString();

        try {
            if (!loadStudentContext(req, studentId)) {
                resp.sendRedirect(req.getContextPath() + "/fees");
                return;
            }
            int outstanding = (int) req.getAttribute("outstanding");

            String error = null;
            if (amount == null || amount <= 0)      error = "Please enter a valid amount.";
            else if (mode.isEmpty())                error = "Please select a payment mode.";
            else if (amount > outstanding)          error = "Amount exceeds the outstanding balance of "
                                                            + FeeCalculator.inr(outstanding) + ".";
            if (error != null) {
                req.setAttribute("error", error);
                req.setAttribute("formAmount", amount);
                req.getRequestDispatcher("/WEB-INF/views/collect.jsp").forward(req, resp);
                return;
            }

            User user = (User) req.getSession().getAttribute("user");
            Payment p = new Payment();
            p.setStudentId(studentId);
            p.setAmount(amount);
            p.setPaymentMode(mode);
            p.setPaymentDate(date);
            p.setRemarks(remarks);
            p.setTxnRef(trim(req.getParameter("txnRef")));
            p.setCollectedBy(user == null ? "" : user.getFullName());
            p.setCollectedById(user == null ? null : Integer.valueOf(user.getUserId()));

            // insert() also allocates the money across open installments.
            int paymentId = paymentDAO.insert(p);
            resp.sendRedirect(req.getContextPath() + "/receipt?paymentId=" + paymentId);

        } catch (SQLException e) {
            getServletContext().log("Save payment failed", e);
            req.setAttribute("error", "Could not record the payment. Please try again.");
            req.getRequestDispatcher("/WEB-INF/views/collect.jsp").forward(req, resp);
        }
    }

    /** Loads the student + fee position into request attributes. Returns false if student not found. */
    private boolean loadStudentContext(HttpServletRequest req, int studentId) throws SQLException {
        Student student = studentDAO.findById(studentId);
        if (student == null) return false;

        installmentDAO.markOverdue();

        Map<String, FeeSlab> slabs = feeSlabDAO.findAllAsMap();
        FeeSlab slab = (student.getFeeSlab() == null) ? null : slabs.get(student.getFeeSlab());
        FeeService.Position pos = feeService.position(studentId, student.getFeeSlab());
        List<Payment> history = paymentDAO.findByStudent(studentId);
        List<FeeInstallment> schedule = installmentDAO.findByStudent(studentId);

        // The next unsettled installment — offered as the default amount, since
        // that is what the parent has actually come in to pay.
        FeeInstallment nextDue = null;
        for (FeeInstallment i : schedule) {
            if (!i.isSettled()) { nextDue = i; break; }
        }

        req.setAttribute("student", student);
        req.setAttribute("slabLabel", slab == null ? "—" : slab.getLabel());
        req.setAttribute("totalFee", pos.total);
        req.setAttribute("paid", pos.paid);
        req.setAttribute("outstanding", pos.outstanding);
        req.setAttribute("position", pos);
        req.setAttribute("history", history);
        req.setAttribute("schedule", schedule);
        req.setAttribute("nextDue", nextDue);
        return true;
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }

    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
