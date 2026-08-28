package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.CandidateDAO;
import com.tution.dao.ExamDAO;
import com.tution.model.Exam;
import com.tution.model.ExamCandidate;

/**
 * Print output for an exam: the invigilator's roll list, and hall tickets for
 * the students.
 *
 * Rendered as print-styled HTML rather than PDF on purpose. ReceiptPdf is a
 * minimal ASCII-only writer — it strips anything outside 32..126, so it cannot
 * set a student's name in a non-Latin script and has no table or multi-up
 * layout. A print stylesheet gives the browser's own PDF export, four tickets to
 * a page, correct page breaks, and no new dependency.
 */
@WebServlet({ "/roll-list", "/hall-tickets" })
public class HallTicketServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final CandidateDAO dao = new CandidateDAO();
    private final ExamDAO examDAO = new ExamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        boolean tickets = req.getServletPath().endsWith("hall-tickets");
        int examId = parseInt(req.getParameter("examId"), 0);
        if (examId <= 0) {
            resp.sendRedirect(req.getContextPath() + "/candidates");
            return;
        }
        try {
            Exam exam = examDAO.findById(examId);
            List<ExamCandidate> list = dao.find(examId,
                    req.getParameter("school"), req.getParameter("centre"),
                    req.getParameter("status"), req.getParameter("q"));

            req.setAttribute("exam", exam);
            req.setAttribute("candidates", list);
            req.setAttribute("centre", req.getParameter("centre"));
            req.setAttribute("school", req.getParameter("school"));
            req.getRequestDispatcher(tickets ? "/WEB-INF/views/hall_tickets.jsp"
                                             : "/WEB-INF/views/roll_list.jsp")
               .forward(req, resp);
        } catch (SQLException e) {
            req.setAttribute("error", "Database error: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/candidates.jsp").forward(req, resp);
        }
    }

    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
}
