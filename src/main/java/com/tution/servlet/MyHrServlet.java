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

import com.tution.dao.HrDAO;
import com.tution.model.StaffLeave;
import com.tution.model.User;

/**
 * Self-service HR: a person's OWN attendance, leave and payslips.
 *
 *   GET  /my-hr[?y=&m=]          the three sections the role holds
 *   POST /my-hr action=applyleave  a leave request for yourself
 *
 * The sheet gives every role "Attendance" and "Leave" — that means this page,
 * not the /hr register of everyone. Each section is its own activity
 * (HR_ATTENDANCE / HR_LEAVE / HR_SALARY); AuthFilter lets the page open for any
 * of the three. Nothing here takes a user id from the request: it is always the
 * signed-in user's own record.
 */
@WebServlet("/my-hr")
public class MyHrServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final HrDAO hrDAO = new HrDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = currentUser(req, resp);
        if (user == null) return;

        LocalDate today = LocalDate.now();
        int y = intParam(req, "y", today.getYear());
        int m = intParam(req, "m", today.getMonthValue());
        if (m < 1 || m > 12) { m = today.getMonthValue(); y = today.getYear(); }

        try {
            if (user.can("HR_ATTENDANCE")) {
                req.setAttribute("attendance", hrDAO.myAttendance(user.getUserId(), y, m));
            }
            if (user.can("HR_LEAVE")) {
                req.setAttribute("leaves", hrDAO.myLeaves(user.getUserId()));
            }
            if (user.can("HR_SALARY")) {
                req.setAttribute("payslips", hrDAO.myPayslips(user.getUserId()));
            }
        } catch (SQLException e) {
            getServletContext().log("my-hr load", e);
            req.setAttribute("error", "Could not load your records. Please try again.");
        }
        req.setAttribute("y", Integer.valueOf(y));
        req.setAttribute("m", Integer.valueOf(m));
        req.setAttribute("today", today.toString());
        req.getRequestDispatcher("/WEB-INF/views/my_hr.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = currentUser(req, resp);
        if (user == null) return;
        HttpSession s = req.getSession();

        if (!"applyleave".equals(req.getParameter("action")) || !user.can("HR_LEAVE")) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Your role cannot do that.");
            return;
        }
        StaffLeave l = new StaffLeave();
        l.setUserId(user.getUserId());                       // always yourself
        String type = req.getParameter("leaveType");
        l.setLeaveType(type == null || type.isEmpty() ? "CASUAL" : type);
        l.setFromDate(req.getParameter("fromDate"));
        String to = req.getParameter("toDate");
        l.setToDate(to == null || to.isEmpty() ? l.getFromDate() : to);
        l.setReason(req.getParameter("reason"));
        l.setAppliedBy(user.getFullName());

        if (l.getFromDate() == null || l.getFromDate().isEmpty()) {
            s.setAttribute("flashError", "Choose when the leave starts.");
        } else {
            l.setDays(HrServlet.daysBetween(l.getFromDate(), l.getToDate(), req.getParameter("days")));
            if (l.getDays() <= 0) {
                s.setAttribute("flashError", "The end date cannot be before the start date.");
            } else {
                try {
                    hrDAO.applyLeave(l);
                    s.setAttribute("flash", "Leave request sent. HR will approve or reject it.");
                } catch (SQLException e) {
                    getServletContext().log("my-hr applyleave", e);
                    s.setAttribute("flashError", "Could not send the request: " + e.getMessage());
                }
            }
        }
        resp.sendRedirect(req.getContextPath() + "/my-hr#leave");
    }

    private User currentUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) resp.sendRedirect(req.getContextPath() + "/login.jsp");
        return user;
    }

    private static int intParam(HttpServletRequest req, String n, int dflt) {
        try {
            return Integer.parseInt(req.getParameter(n).trim());
        } catch (RuntimeException e) {
            return dflt;
        }
    }
}
