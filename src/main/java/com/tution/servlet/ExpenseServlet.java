package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.ExpenseDAO;
import com.tution.dao.FundDAO;
import com.tution.dao.VendorDAO;
import com.tution.dao.WorkOrderDAO;
import com.tution.model.Expense;
import com.tution.model.User;
import com.tution.service.ExpenseService;

/**
 * Expenses: money leaving a fund, and what it settled.
 *
 * ADMIN only, matching {@code AuthFilter.ADMIN_ONLY}.
 */
@WebServlet("/expenses")
public class ExpenseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ExpenseDAO     dao       = new ExpenseDAO();
    private final FundDAO        fundDao   = new FundDAO();
    private final VendorDAO      vendorDao = new VendorDAO();
    private final WorkOrderDAO   woDao     = new WorkOrderDAO();
    private final ExpenseService service   = new ExpenseService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireAdmin(req, resp);
        if (user == null) return;

        try {
            int    fundId   = parseInt(req.getParameter("fund"), 0);
            int    vendorId = parseInt(req.getParameter("vendor"), 0);
            int    woId     = parseInt(req.getParameter("wo"), 0);
            int    catId    = parseInt(req.getParameter("cat"), 0);
            String status   = trim(req.getParameter("status"));
            String q        = trim(req.getParameter("q"));

            // Default window: this month. An unbounded expense register is
            // unreadable within a term and slow within a year.
            LocalDate today = LocalDate.now();
            String from = orDefault(req.getParameter("from"), today.withDayOfMonth(1).toString());
            String to   = orDefault(req.getParameter("to"),   today.toString());

            req.setAttribute("rows",    dao.list(fundId, vendorId, woId, catId, status, from, to, q));
            req.setAttribute("totals",  dao.totals(fundId, vendorId, woId, catId, from, to));
            req.setAttribute("byCat",   dao.byCategory(from, to));
            req.setAttribute("funds",   fundDao.all(true));
            req.setAttribute("vendors", vendorDao.list(null, true));
            req.setAttribute("orders",  woDao.payableAll());
            req.setAttribute("cats",    dao.categories());

            req.setAttribute("fundId",   Integer.valueOf(fundId));
            req.setAttribute("vendorId", Integer.valueOf(vendorId));
            req.setAttribute("woId",     Integer.valueOf(woId));
            req.setAttribute("catId",    Integer.valueOf(catId));
            req.setAttribute("status",   status == null ? "" : status);
            req.setAttribute("q",        q      == null ? "" : q);
            req.setAttribute("from",     from);
            req.setAttribute("to",       to);
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load expenses: " + e.getMessage());
            req.setAttribute("rows", new ArrayList<Expense>());
        }
        req.getRequestDispatcher("/WEB-INF/views/expenses.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireAdmin(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");
        String back   = "/expenses";

        try {
            switch (action) {
                case "add": {
                    ExpenseService.Result r = service.record(
                            parseInt(req.getParameter("fundId"), 0),
                            req.getParameter("amount"),
                            req.getParameter("tax"),
                            req.getParameter("date"),
                            parseInt(req.getParameter("categoryId"), 0),
                            parseInt(req.getParameter("vendorId"), 0),
                            parseInt(req.getParameter("workOrderId"), 0),
                            req.getParameter("mode"),
                            req.getParameter("txnRef"),
                            req.getParameter("invoiceNo"),
                            req.getParameter("description"),
                            user);
                    if (r.overdrawn) flashError(req, service.savedMessage(r));
                    else             flash(req, service.savedMessage(r));
                    break;
                }
                case "void": {
                    Expense x = service.voidExpense(parseInt(req.getParameter("expenseId"), 0),
                                                    req.getParameter("reason"), user);
                    flash(req, x.getVoucherNo() + " cancelled. It stays on the register marked "
                             + "cancelled, and the amount has been credited back to the fund.");
                    break;
                }
                case "addcat": {
                    String name = trim(req.getParameter("name"));
                    if (name == null) flashError(req, "Give the category a name.");
                    else { dao.addCategory(name); flash(req, "Category \"" + name + "\" added."); }
                    break;
                }
                default:
                    flashError(req, "Unknown action.");
            }
        } catch (ExpenseService.ExpenseException e) {
            flashError(req, e.getMessage());
        } catch (SQLException e) {
            flashError(req, "Could not save: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + back);
    }

    /* ───────────────────────── helpers ───────────────────────── */

    private User requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        if (!user.canSeeFinance()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "Expenses are managed by the administrator and the accountant.");
            return null;
        }
        return user;
    }

    private void flash(HttpServletRequest req, String msg) {
        req.getSession().setAttribute("flash", msg);
    }

    private void flashError(HttpServletRequest req, String msg) {
        req.getSession().setAttribute("flashError", msg);
    }

    private static String trim(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private static String orDefault(String v, String dflt) {
        return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
    }

    private static int parseInt(String v, int dflt) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return dflt; }
    }
}
