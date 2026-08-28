package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.AttendanceDAO;
import com.tution.model.AttendanceSummary;

/** Per-student attendance report over a date range. */
@WebServlet("/attendance-report")
public class AttendanceReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final AttendanceDAO attendanceDAO = new AttendanceDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        // default range = current month to today
        String from = trim(req.getParameter("from"));
        String to   = trim(req.getParameter("to"));
        if (from.isEmpty() && to.isEmpty()) {
            LocalDate now = LocalDate.now();
            from = now.withDayOfMonth(1).toString();
            to   = now.toString();
        }
        if (!from.matches("\\d{4}-\\d{2}-\\d{2}")) from = "";
        if (!to.matches("\\d{4}-\\d{2}-\\d{2}"))   to   = "";

        try {
            List<AttendanceSummary> rows = attendanceDAO.report(from, to);
            req.setAttribute("rows", rows);
        } catch (SQLException e) {
            getServletContext().log("Attendance report failed", e);
            req.setAttribute("error", "Could not load the report. Please try again.");
        }
        req.setAttribute("from", from);
        req.setAttribute("to", to);
        req.getRequestDispatcher("/WEB-INF/views/attendance_report.jsp").forward(req, resp);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
