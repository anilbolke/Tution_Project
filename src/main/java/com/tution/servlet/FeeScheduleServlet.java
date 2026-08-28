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

import com.tution.dao.FeePlanDAO;
import com.tution.dao.FeeSlabDAO;
import com.tution.dao.InstallmentDAO;
import com.tution.dao.SettingsDAO;
import com.tution.dao.StudentDAO;
import com.tution.dao.StudentFeeDAO;
import com.tution.model.FeePlan;
import com.tution.model.FeeSlab;
import com.tution.model.Student;
import com.tution.model.StudentFee;
import com.tution.model.User;
import com.tution.service.FeeService;

/**
 * The fee plan for one student: course fee, concessions, and the installment
 * schedule generated from them.
 *
 * GET  /fee-plan?studentId=N
 * POST /fee-plan  action=save      revise the fee record (and optionally the schedule)
 * POST /fee-plan  action=schedule  regenerate the schedule only
 */
@WebServlet("/fee-plan")
public class FeeScheduleServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final StudentDAO     studentDAO     = new StudentDAO();
    private final StudentFeeDAO  studentFeeDAO  = new StudentFeeDAO();
    private final InstallmentDAO installmentDAO = new InstallmentDAO();
    private final FeeSlabDAO     feeSlabDAO     = new FeeSlabDAO();
    private final FeePlanDAO     planDAO        = new FeePlanDAO();
    private final SettingsDAO    settingsDAO    = new SettingsDAO();
    private final FeeService     feeService     = new FeeService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        int studentId = intParam(req, "studentId", 0);
        if (studentId <= 0) {
            resp.sendRedirect(req.getContextPath() + "/fees");
            return;
        }

        try {
            if (!load(req, studentId)) {
                resp.sendRedirect(req.getContextPath() + "/fees");
                return;
            }
        } catch (SQLException e) {
            getServletContext().log("Fee plan load failed", e);
            req.setAttribute("error", "Could not load the fee plan. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/fee_plan.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        // A concession is money off the institute's revenue — an ADMIN decision.
        if (!user.isAdmin()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "Only an administrator can change a student's fee plan.");
            return;
        }

        int studentId = intParam(req, "studentId", 0);
        String ctx = req.getContextPath();
        if (studentId <= 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing student.");
            return;
        }

        try {
            Student student = studentDAO.findById(studentId);
            if (student == null) {
                resp.sendRedirect(ctx + "/fees");
                return;
            }

            StudentFee existing = studentFeeDAO.findByStudent(studentId);

            StudentFee f = new StudentFee();
            f.setStudentId(studentId);
            f.setPlanCode(blankTo(p(req, "planCode"),
                                  existing == null ? null : existing.getPlanCode()));
            f.setCourseFee(intParam(req, "courseFee", 0));
            f.setRegistrationFee(intParam(req, "registrationFee", 0));
            f.setMaterialFee(intParam(req, "materialFee", 0));
            f.setDiscount(intParam(req, "discount", 0));
            f.setScholarship(intParam(req, "scholarship", 0));
            f.setGstRate(doubleParam(req, "gstRate",
                                     existing == null ? 0 : existing.getGstRate()));
            f.setRegistrationPaid(existing != null && existing.isRegistrationPaid());
            f.setPlan(blankTo(p(req, "plan"), "FULL"));
            f.setApprovedBy(user.getFullName());
            f.setRemarks(p(req, "remarks"));
            f.recalc();

            String error = validate(f, studentId);
            if (error != null) {
                req.setAttribute("error", error);
                load(req, studentId);
                req.getRequestDispatcher("/WEB-INF/views/fee_plan.jsp").forward(req, resp);
                return;
            }

            feeService.saveLedger(f);

            // Regenerate the schedule when asked, or whenever the plan is not
            // "pay in full" — otherwise a revised fee would leave a stale schedule.
            boolean regen = "1".equals(p(req, "regenerate")) || "schedule".equals(p(req, "action"));
            if (regen) {
                // The percentage pattern and due months come from the brochure
                // plan, so a regenerated schedule always matches what was sold.
                FeePlan plan = planDAO.findByCode(f.getPlanCode());
                if (plan != null) {
                    String start = blankTo(p(req, "startDate"), LocalDate.now().toString());
                    installmentDAO.replaceSchedule(studentId,
                        feeService.buildSchedule(plan, f, start));
                    // Money already collected is re-applied oldest-first so a
                    // regenerated schedule never asks for it twice.
                    feeService.reallocate(studentId);
                }
            }

            resp.sendRedirect(ctx + "/fee-plan?studentId=" + studentId + "&msg=saved");

        } catch (SQLException e) {
            getServletContext().log("Fee plan save failed", e);
            resp.sendRedirect(ctx + "/fee-plan?studentId=" + studentId + "&msg=error");
        }
    }

    /** Loads student, ledger, slab and schedule into request scope. */
    private boolean load(HttpServletRequest req, int studentId) throws SQLException {
        Student student = studentDAO.findById(studentId);
        if (student == null) {
            return false;
        }
        installmentDAO.markOverdue();

        StudentFee ledger = studentFeeDAO.findByStudent(studentId);
        Map<String, FeeSlab> slabs = feeSlabDAO.findAllAsMap();
        FeeSlab slab = (student.getFeeSlab() == null) ? null : slabs.get(student.getFeeSlab());

        req.setAttribute("student", student);
        req.setAttribute("ledger", ledger);
        req.setAttribute("slab", slab);
        req.setAttribute("position", feeService.position(studentId, student.getFeeSlab()));
        req.setAttribute("schedule", installmentDAO.findByStudent(studentId));
        req.setAttribute("plans", planDAO.findAll());
        req.setAttribute("feePlan", planDAO.findByCode(
            ledger != null ? ledger.getPlanCode() : student.getPlanCode()));
        req.setAttribute("gstRate", Double.valueOf(settingsDAO.gstRate()));
        req.setAttribute("today", LocalDate.now().toString());
        return true;
    }

    /**
     * A concession cannot exceed the gross fee, and the net payable must not
     * drop below what has already been collected — that would imply a refund the
     * system has no way to make.
     */
    private String validate(StudentFee f, int studentId) throws SQLException {
        if (f.getCourseFee() < 0 || f.getRegistrationFee() < 0 || f.getMaterialFee() < 0
                || f.getDiscount() < 0 || f.getScholarship() < 0) {
            return "Amounts cannot be negative.";
        }
        if (f.getTotalConcession() > f.getGross()) {
            return "Discount and scholarship together cannot exceed the total fee of Rs. "
                 + String.format("%,d", f.getGross()) + ".";
        }
        int paid = new com.tution.dao.PaymentDAO().getTotalPaid(studentId);
        if (f.getNetPayable() < paid) {
            return "The new payable amount (Rs. " + String.format("%,d", f.getNetPayable())
                 + ") is less than the Rs. " + String.format("%,d", paid)
                 + " already collected. Refunds are not handled by the system — "
                 + "reduce the concession or raise the fee.";
        }
        return null;
    }

    private static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s == null) ? null : (User) s.getAttribute("user");
    }

    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        String v = req.getParameter(name);
        if (v == null) {
            return fallback;
        }
        v = v.trim().replace(",", "");
        return v.matches("-?\\d+") ? Integer.parseInt(v) : fallback;
    }

    private static String blankTo(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private static double doubleParam(HttpServletRequest req, String name, double fallback) {
        String v = req.getParameter(name);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
