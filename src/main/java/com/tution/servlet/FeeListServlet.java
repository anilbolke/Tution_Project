package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
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
import com.tution.dao.StudentDAO;
import com.tution.model.FeeSlab;
import com.tution.model.FeeSummary;
import com.tution.model.Student;
import com.tution.service.FeeService;

/** Fee dashboard: every student's total / paid / outstanding position. */
@WebServlet("/fees")
public class FeeListServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final StudentDAO     studentDAO     = new StudentDAO();
    private final FeeSlabDAO     feeSlabDAO     = new FeeSlabDAO();
    private final InstallmentDAO installmentDAO = new InstallmentDAO();
    private final FeeService     feeService     = new FeeService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        try {
            // Refresh overdue flags before drawing the page, so a due date that
            // passed overnight is not still shown as PENDING.
            installmentDAO.markOverdue();

            List<Student> students     = studentDAO.findAll();
            Map<String, FeeSlab> slabs = feeSlabDAO.findAllAsMap();
            Map<Integer, FeeService.Position> positions = feeService.positions(students);

            List<FeeSummary> rows = new ArrayList<>();
            long totBilled = 0, totPaid = 0, totDue = 0, totConcession = 0;

            for (Student s : students) {
                FeeService.Position pos = positions.get(s.getStudentId());
                FeeSlab slab = (s.getFeeSlab() == null) ? null : slabs.get(s.getFeeSlab());

                FeeSummary fs = new FeeSummary();
                fs.setStudentId(s.getStudentId());
                fs.setAdmissionNo(s.getAdmissionNo());
                fs.setFullName(s.getFullName());
                fs.setClassName(s.getClassName());
                fs.setSlabLabel(slab == null ? "—" : slab.getLabel());
                fs.setTotalFee(pos.total);
                fs.setPaid(pos.paid);
                fs.setOutstanding(pos.outstanding);
                fs.setStatus(pos.status());
                fs.setConcession(pos.discount + pos.scholarship);
                fs.setPlan(pos.plan);
                rows.add(fs);

                totBilled     += pos.total;
                totPaid       += pos.paid;
                totDue        += pos.outstanding;
                totConcession += pos.discount + pos.scholarship;
            }

            req.setAttribute("rows", rows);
            req.setAttribute("totBilled", totBilled);
            req.setAttribute("totPaid", totPaid);
            req.setAttribute("totDue", totDue);
            req.setAttribute("totConcession", totConcession);
        } catch (SQLException e) {
            getServletContext().log("Load fees failed", e);
            req.setAttribute("error", "Could not load fee data. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/fees.jsp").forward(req, resp);
    }
}
