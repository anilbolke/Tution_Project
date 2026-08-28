package com.tution.servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.FundDAO;
import com.tution.dao.HrDAO;
import com.tution.dao.UserDAO;
import com.tution.model.StaffLeave;
import com.tution.model.User;
import com.tution.util.Money;

/**
 * The HR module: staff attendance, leave and payroll.
 *
 * One servlet with a {@code tab} parameter rather than three, because the three
 * are one job — a month's attendance decides that month's leave balance and both
 * decide the payslip — and splitting them would mean three copies of the same
 * period handling and the same guard.
 *
 *   GET  /hr?tab=attendance[&date=]      the day's register
 *   GET  /hr?tab=leave[&status=]         leave requests
 *   GET  /hr?tab=salary[&y=&m=]          salary structure and the month's payroll
 *   POST /hr  action=markday|applyleave|decideleave|setsalary|generate|pay
 *
 * ADMIN and HR only, matching {@code User.canSeePeople()}.
 */
@WebServlet("/hr")
public class HrServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final HrDAO   hrDAO   = new HrDAO();
    private final UserDAO userDAO = new UserDAO();
    private final FundDAO fundDAO = new FundDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requirePeople(req, resp);
        if (user == null) return;

        String tab = orDefault(req.getParameter("tab"), "attendance");
        if (!"leave".equals(tab) && !"salary".equals(tab)) {
            tab = "attendance";
        }

        LocalDate today = LocalDate.now();
        int year  = intParam(req, "y", today.getYear());
        int month = intParam(req, "m", today.getMonthValue());
        if (month < 1 || month > 12) month = today.getMonthValue();
        String date = orDefault(req.getParameter("date"), today.toString());

        try {
            req.setAttribute("staff", userDAO.allStaff());

            if ("attendance".equals(tab)) {
                req.setAttribute("register", hrDAO.dayRegister(date));
                req.setAttribute("summary",  hrDAO.monthSummary(year, month));
            } else if ("leave".equals(tab)) {
                req.setAttribute("leaves", hrDAO.leaves(req.getParameter("status")));
                req.setAttribute("fstatus", req.getParameter("status"));
            } else {
                req.setAttribute("salaries", hrDAO.currentSalaries());
                req.setAttribute("payslips", hrDAO.payslips(year, month));
                req.setAttribute("funds",    fundDAO.all(true));
            }
        } catch (SQLException e) {
            getServletContext().log("HR screen failed", e);
            req.setAttribute("error", "Could not load the HR data: " + e.getMessage());
        }

        req.setAttribute("tab", tab);
        req.setAttribute("date", date);
        req.setAttribute("y", Integer.valueOf(year));
        req.setAttribute("m", Integer.valueOf(month));
        req.setAttribute("monthDays", Integer.valueOf(YearMonth.of(year, month).lengthOfMonth()));
        req.getRequestDispatcher("/WEB-INF/views/hr.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requirePeople(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");
        String tab    = orDefault(req.getParameter("tab"), "attendance");
        LocalDate today = LocalDate.now();
        int year  = intParam(req, "y", today.getYear());
        int month = intParam(req, "m", today.getMonthValue());
        String date = orDefault(req.getParameter("date"), today.toString());

        try {
            switch (action) {
                case "markday": {
                    // Every listed employee posts a status_<id> field; a blank
                    // one means "not marked", which is not the same as absent.
                    Map<Integer,String> marks   = new LinkedHashMap<>();
                    Map<Integer,String> remarks = new LinkedHashMap<>();
                    java.util.Enumeration<String> names = req.getParameterNames();
                    while (names.hasMoreElements()) {
                        String n = names.nextElement();
                        if (n.startsWith("status_")) {
                            int id = parseInt(n.substring(7), 0);
                            if (id > 0) {
                                marks.put(id, req.getParameter(n));
                                remarks.put(id, req.getParameter("remark_" + id));
                            }
                        }
                    }
                    int n = hrDAO.saveDay(date, marks, remarks, user.getFullName());
                    flash(req, n + " staff marked for " + date + ".");
                    break;
                }
                case "applyleave": {
                    StaffLeave l = new StaffLeave();
                    l.setUserId(parseInt(req.getParameter("userId"), 0));
                    l.setLeaveType(orDefault(req.getParameter("leaveType"), "CASUAL"));
                    l.setFromDate(req.getParameter("fromDate"));
                    l.setToDate(orDefault(req.getParameter("toDate"),
                                          req.getParameter("fromDate")));
                    l.setReason(req.getParameter("reason"));
                    l.setAppliedBy(user.getFullName());
                    if (l.getUserId() <= 0 || l.getFromDate() == null || l.getFromDate().isEmpty()) {
                        flashError(req, "Choose who the leave is for and when it starts.");
                        break;
                    }
                    l.setDays(daysBetween(l.getFromDate(), l.getToDate(),
                                          req.getParameter("days")));
                    if (l.getDays() <= 0) {
                        flashError(req, "The end date cannot be before the start date.");
                        break;
                    }
                    hrDAO.applyLeave(l);
                    flash(req, "Leave request recorded, awaiting a decision.");
                    tab = "leave";
                    break;
                }
                case "decideleave": {
                    int id = parseInt(req.getParameter("leaveId"), 0);
                    String d = "approve".equals(req.getParameter("decision"))
                             ? StaffLeave.APPROVED : StaffLeave.REJECTED;
                    boolean ok = hrDAO.decideLeave(id, d, req.getParameter("note"),
                                                   user.getFullName());
                    if (ok) {
                        flash(req, StaffLeave.APPROVED.equals(d)
                              ? "Leave approved, and the days are on the attendance register."
                              : "Leave rejected.");
                    } else {
                        flashError(req, "That request has already been decided.");
                    }
                    tab = "leave";
                    break;
                }
                case "setsalary": {
                    int id = parseInt(req.getParameter("userId"), 0);
                    BigDecimal ctc = Money.parse(req.getParameter("ctc"));
                    if (id <= 0 || ctc == null || ctc.signum() < 0) {
                        flashError(req, "Choose an employee and a valid monthly amount.");
                        break;
                    }
                    hrDAO.setSalary(id, ctc,
                                    orDefault(req.getParameter("effectiveFrom"),
                                              today.withDayOfMonth(1).toString()),
                                    req.getParameter("note"), user.getFullName());
                    flash(req, "Salary recorded.");
                    tab = "salary";
                    break;
                }
                case "generate": {
                    int n = hrDAO.generatePayslips(year, month, user.getFullName());
                    if (n > 0) {
                        flash(req, n + " draft payslip(s) worked out for "
                                 + month + "/" + year + ".");
                    } else {
                        flashError(req, "Nothing to generate — either every payslip for this "
                                      + "month already exists, or no one has a salary on record.");
                    }
                    tab = "salary";
                    break;
                }
                case "pay": {
                    int slip   = parseInt(req.getParameter("payslipId"), 0);
                    int fundId = parseInt(req.getParameter("fundId"), 0);
                    if (fundId <= 0) {
                        flashError(req, "Choose which fund the salary is paid from.");
                        tab = "salary";
                        break;
                    }
                    boolean ok = hrDAO.payPayslip(slip, fundId,
                                                  orDefault(req.getParameter("paidOn"),
                                                            today.toString()), user);
                    if (ok) {
                        flash(req, "Salary paid, and the fund has been debited.");
                    } else {
                        flashError(req, "That payslip has already been paid.");
                    }
                    tab = "salary";
                    break;
                }
                default:
                    flashError(req, "Unknown action.");
            }
        } catch (SQLException e) {
            getServletContext().log("HR action failed", e);
            flashError(req, "Could not save: " + e.getMessage());
        }

        resp.sendRedirect(req.getContextPath() + "/hr?tab=" + tab
                        + "&date=" + date + "&y=" + year + "&m=" + month);
    }

    /* ───────────────────────── helpers ───────────────────────── */

    /**
     * Inclusive day count, or the override the form supplied.
     *
     * The override exists for the half-day case: two dates cannot express
     * "0.5", and payroll must use the number a human approved.
     */
    private static double daysBetween(String from, String to, String override) {
        if (override != null && !override.trim().isEmpty()) {
            try {
                double d = Double.parseDouble(override.trim());
                if (d > 0) return d;
            } catch (NumberFormatException ignore) { /* fall through to the dates */ }
        }
        try {
            LocalDate a = LocalDate.parse(from);
            LocalDate b = LocalDate.parse(to);
            return b.isBefore(a) ? 0 : (b.toEpochDay() - a.toEpochDay() + 1);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private User requirePeople(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        // Repeated here rather than trusted to AuthFilter: a servlet should not
        // depend on a filter's allow-list to stay correct.
        if (!user.canSeePeople()) {
            resp.sendRedirect(req.getContextPath() + user.homePath());
            return null;
        }
        return user;
    }

    private void flash(HttpServletRequest req, String m) {
        req.getSession().setAttribute("flash", m);
    }

    private void flashError(HttpServletRequest req, String m) {
        req.getSession().setAttribute("flashError", m);
    }

    private static String orDefault(String v, String dflt) {
        return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
    }

    private static int parseInt(String v, int dflt) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return dflt; }
    }

    private static int intParam(HttpServletRequest req, String n, int dflt) {
        return parseInt(req.getParameter(n), dflt);
    }
}
