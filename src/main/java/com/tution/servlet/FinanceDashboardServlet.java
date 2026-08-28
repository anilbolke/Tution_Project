package com.tution.servlet;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.ExpenseDAO;
import com.tution.dao.FundDAO;
import com.tution.dao.SalesStatsDAO;
import com.tution.dao.WorkOrderDAO;
import com.tution.model.FundAccount;
import com.tution.model.User;
import com.tution.util.Money;

/**
 * The accountant's landing screen: what the institute holds, what went out this
 * month, and what is still owed in both directions.
 *
 * The accountant used to land on /fund, which is one fund's statement — fine
 * once you know which fund you want, and no use at all as an opening view when
 * there are several. This leads with every balance, then the month, then the
 * two "still outstanding" figures that decide what to chase: fees owed TO the
 * institute, and work orders owed BY it.
 *
 * ADMIN and ACCOUNTANT only, matching {@code User.canSeeFinance()}.
 */
@WebServlet("/finance-dashboard")
public class FinanceDashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final FundDAO       fundDAO  = new FundDAO();
    private final ExpenseDAO    expDAO   = new ExpenseDAO();
    private final WorkOrderDAO  woDAO    = new WorkOrderDAO();
    private final SalesStatsDAO statsDAO = new SalesStatsDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        // A hard stop rather than a redirect: this role's home IS a finance
        // screen, so bouncing a denied user to homePath() could loop.
        if (!user.canSeeFinance()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "This page is restricted to the administrator and the accountant.");
            return;
        }

        LocalDate today = LocalDate.now();
        String from = today.withDayOfMonth(1).toString();
        String to   = today.toString();

        try {
            java.util.List<FundAccount> funds = fundDAO.all(false);
            BigDecimal held = Money.ZERO;
            int overdrawn = 0;
            for (FundAccount f : funds) {
                held = held.add(f.getBalance());
                if (f.isOverdrawn()) overdrawn++;
            }
            req.setAttribute("funds", funds);
            req.setAttribute("held", held);
            req.setAttribute("overdrawn", Integer.valueOf(overdrawn));

            req.setAttribute("spend",  expDAO.totals(0, 0, 0, 0, from, to));
            req.setAttribute("recent", fundDAO.recent(8));
            req.setAttribute("payable", woDAO.totals(0, null));
            req.setAttribute("stats",   statsDAO.load(null));
        } catch (SQLException e) {
            getServletContext().log("Finance dashboard failed", e);
            req.setAttribute("error", "Could not load the dashboard: " + e.getMessage());
        }

        req.setAttribute("from", from);
        req.setAttribute("to", to);
        req.getRequestDispatcher("/WEB-INF/views/finance_dashboard.jsp").forward(req, resp);
    }
}
