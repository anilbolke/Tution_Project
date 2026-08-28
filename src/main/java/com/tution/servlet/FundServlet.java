package com.tution.servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.FundDAO;
import com.tution.model.FundAccount;
import com.tution.model.FundTransaction;
import com.tution.model.User;
import com.tution.service.FundService;
import com.tution.util.Money;

/**
 * The expense fund: what is in it, what went in and out, and topping it up.
 *
 * Management only. This is institute money rather than anything to do with a
 * counsellor's pipeline or a teacher's classes, so {@code AuthFilter} keeps both
 * roles out; the check is repeated here because a servlet should not depend on a
 * filter's allow-list to stay correct.
 */
@WebServlet("/fund")
public class FundServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final FundDAO      dao     = new FundDAO();
    private final FundService  service = new FundService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireManagement(req, resp);
        if (user == null) return;

        try {
            List<FundAccount> funds = dao.all(false);
            req.setAttribute("funds", funds);

            // Shown on the page rather than kept for a report nobody runs: an
            // audit trail only deters anything if it is visible where the
            // action is taken. Includes funds that have since been deleted.
            req.setAttribute("auditTrail", dao.auditTrail(25));

            // Which fund's statement to show: the one asked for, else the first.
            int fundId = parseInt(req.getParameter("id"), 0);
            if (fundId <= 0 && !funds.isEmpty()) fundId = funds.get(0).getFundId();

            if (fundId > 0) {
                FundAccount sel = dao.find(fundId);
                if (sel != null) {
                    // Drives whether Delete is offered at all: a fund with any
                    // history can only be closed.
                    req.setAttribute("selExpenses", Integer.valueOf(dao.expenseCount(fundId)));
                    LocalDate today = LocalDate.now();
                    String from = orDefault(req.getParameter("from"),
                                            today.withDayOfMonth(1).toString());
                    String to   = orDefault(req.getParameter("to"), today.toString());

                    List<FundTransaction> rows = dao.statement(fundId, from, to);

                    // The opening figure the statement starts from. Without it the
                    // running balance on a filtered range would start at zero.
                    BigDecimal opening = dao.balanceBefore(fundId, from);
                    BigDecimal periodCr = Money.ZERO, periodDr = Money.ZERO;
                    for (FundTransaction t : rows) {
                        if (t.isCredit()) periodCr = periodCr.add(t.getAmount());
                        else              periodDr = periodDr.add(t.getAmount());
                    }

                    req.setAttribute("selected", sel);
                    req.setAttribute("rows", rows);
                    req.setAttribute("from", from);
                    req.setAttribute("to", to);
                    req.setAttribute("opening",  opening);
                    req.setAttribute("periodCr", periodCr);
                    req.setAttribute("periodDr", periodDr);
                    req.setAttribute("closing",  opening.add(periodCr).subtract(periodDr));
                }
            }
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load the fund ledger: " + e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/fund.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireManagement(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");
        int fundId = parseInt(req.getParameter("fundId"), 0);

        try {
            switch (action) {
                case "topup": {
                    BigDecimal bal = service.topUp(
                            fundId,
                            req.getParameter("amount"),
                            req.getParameter("date"),
                            req.getParameter("mode"),
                            req.getParameter("ref"),
                            req.getParameter("narration"),
                            user);
                    flash(req, "Fund topped up. Balance is now Rs. " + Money.fmt(bal) + ".");
                    break;
                }
                case "adjust": {
                    BigDecimal bal = service.adjust(
                            fundId,
                            req.getParameter("amount"),
                            req.getParameter("direction"),
                            req.getParameter("date"),
                            req.getParameter("narration"),
                            user);
                    flash(req, "Adjustment posted. Balance is now Rs. " + Money.fmt(bal) + ".");
                    break;
                }
                case "create": {
                    String name = req.getParameter("name");
                    if (name == null || name.trim().isEmpty()) {
                        flashError(req, "A fund needs a name.");
                        break;
                    }
                    BigDecimal opening = Money.parseOrZero(req.getParameter("opening"));
                    FundAccount f = new FundAccount();
                    f.setName(name.trim());
                    f.setDescription(req.getParameter("description"));
                    f.setOpeningBalance(opening);
                    f.setOpeningDate(LocalDate.now().toString());
                    f.setCreatedBy(user.getFullName());
                    fundId = dao.insertFund(f);
                    service.auditCreate(f, user);
                    flash(req, "Fund \"" + f.getName() + "\" created.");
                    break;
                }
                case "rename": {
                    String newName = req.getParameter("name");
                    String was = service.renameFund(fundId, newName,
                                                    req.getParameter("reason"), user);
                    flash(req, "\"" + was + "\" is now \"" + newName.trim() + "\".");
                    break;
                }
                case "close":
                case "reopen": {
                    boolean open = "reopen".equals(action);
                    FundAccount f = service.setActive(fundId, open,
                                                      req.getParameter("reason"), user);
                    flash(req, "\"" + f.getName() + "\" is now "
                             + (open ? "open. It can be topped up and spent from again."
                                     : "closed, and has left the fund list — use \"Show "
                                     + "closed\" to reach it. Its statement stays readable; "
                                     + "nothing new can be posted to it."));
                    break;
                }
                case "delete": {
                    String name = service.deleteFund(fundId, req.getParameter("reason"), user);
                    // The fund is gone, so there is nothing left to redirect to.
                    fundId = 0;
                    flash(req, "Fund \"" + name + "\" was deleted. The deletion is on the "
                             + "fund history below.");
                    break;
                }
                case "reverse": {
                    int txnId = parseInt(req.getParameter("txnId"), 0);
                    int made = dao.reverse(txnId, LocalDate.now().toString(),
                                           req.getParameter("reason"),
                                           user.getFullName(), user.getUserId());
                    if (made > 0) {
                        flash(req, "Entry reversed. Both the original and the reversal "
                                 + "stay on the statement.");
                    } else {
                        flashError(req, "That entry could not be found.");
                    }
                    break;
                }
                default:
                    flashError(req, "Unknown action.");
            }
        } catch (FundService.FundException e) {
            flashError(req, e.getMessage());
        } catch (SQLException e) {
            // A duplicate fund name is the one failure a person can act on.
            String msg = e.getMessage() != null && e.getMessage().contains("uq_fund_name")
                       ? "A fund with that name already exists."
                       : "Could not save: " + e.getMessage();
            flashError(req, msg);
        }

        // Carry "showing closed funds" across the redirect, so acting on a
        // closed fund does not silently drop it out of view mid-task.
        StringBuilder back = new StringBuilder(req.getContextPath()).append("/fund");
        String sep = "?";
        if (fundId > 0) {
            back.append(sep).append("id=").append(fundId);
            sep = "&";
        }
        if ("1".equals(req.getParameter("closed"))) {
            back.append(sep).append("closed=1");
        }
        resp.sendRedirect(back.toString());
    }

    /* ───────────────────────── helpers ───────────────────────── */

    /**
     * Returns the logged-in admin, or null having already redirected.
     *
     * ADMIN ONLY, matching {@code AuthFilter.ADMIN_ONLY}. The institute's float
     * is management money: topping it up and posting adjustments changes what
     * the books say is available to spend. Loosening this to STAFF is a
     * one-line change in both places if the office should record it directly.
     */
    private User requireManagement(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        // NOT redirected to homePath(): the accountant's home IS /fund, so
        // bouncing a denied user there would loop forever the moment a role
        // whose home is a finance screen fails this check.
        if (!user.canSeeFinance()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "The fund is managed by the administrator and the accountant.");
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

    private static String orDefault(String v, String dflt) {
        return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
    }

    private static int parseInt(String v, int dflt) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return dflt; }
    }
}
