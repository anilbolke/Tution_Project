package com.tution.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * The finance strip on the admin dashboard.
 *
 * Deliberately separate from {@link StatsDAO}: that one drives the operational
 * tiles every user sees, and a slow or broken finance query must not be able to
 * take it down. It also reads only the finance tables, so nothing here can move
 * a tuition figure.
 */
public class FinanceStatsDAO {

    /** One snapshot, all derived. Nothing on here is a stored total. */
    public static class FinanceStats {
        public BigDecimal fundBalance    = Money.ZERO;
        public boolean    overdrawn;
        public BigDecimal spentThisMonth = Money.ZERO;
        public int        vouchersMonth;
        public BigDecimal vendorOutstanding = Money.ZERO;
        public int        openOrders;
        public BigDecimal examExpected  = Money.ZERO;
        public BigDecimal examCollected = Money.ZERO;
        public BigDecimal examOutstanding = Money.ZERO;
        public int        candidatesDue;

        public String examShare() {
            if (examExpected.signum() == 0) return "—";
            return Math.round(examCollected.doubleValue() * 100.0
                            / examExpected.doubleValue()) + "%";
        }
    }

    /**
     * Loads the whole strip.
     *
     * Each block is independent, so one empty table (no vendors yet, say) leaves
     * the rest of the strip intact rather than blanking the row.
     */
    public FinanceStats load() throws SQLException {
        FinanceStats s = new FinanceStats();
        LocalDate today = LocalDate.now();
        String monthStart = today.withDayOfMonth(1).toString();

        try (Connection con = DBConnection.getConnection()) {

            // Fund balance across every open fund.
            String funds =
                  "SELECT COALESCE(SUM(f.opening_balance),0) + "
                + "       COALESCE((SELECT SUM(CASE WHEN t.direction='CREDIT' THEN t.amount "
                + "                                 ELSE -t.amount END) "
                + "                   FROM fund_transactions t "
                + "                   JOIN fund_accounts g ON g.fund_id = t.fund_id "
                + "                  WHERE g.is_active = 1),0) AS bal "
                + "  FROM fund_accounts f WHERE f.is_active = 1";
            try (PreparedStatement ps = con.prepareStatement(funds);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) s.fundBalance = Money.of(rs.getBigDecimal("bal"));
            }
            s.overdrawn = s.fundBalance.signum() < 0;

            // Spend so far this month. VOID vouchers are not spend.
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(SUM(amount),0) AS spent, COUNT(*) AS n FROM expenses "
                  + " WHERE status='ACTIVE' AND expense_date >= ? AND expense_date <= ?")) {
                ps.setString(1, monthStart);
                ps.setString(2, today.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        s.spentThisMonth = Money.of(rs.getBigDecimal("spent"));
                        s.vouchersMonth  = rs.getInt("n");
                    }
                }
            }

            // Owed to vendors, floored per order so one over-paid order cannot
            // hide what is still owed on another.
            String vendors =
                  "SELECT COALESCE(SUM(GREATEST(w.order_value - "
                + "   (SELECT COALESCE(SUM(x.amount),0) FROM expenses x "
                + "     WHERE x.work_order_id = w.work_order_id AND x.status='ACTIVE'), 0)),0) AS due, "
                + " SUM(w.status IN ('ISSUED','IN_PROGRESS','COMPLETED') AND w.order_value > "
                + "   (SELECT COALESCE(SUM(x2.amount),0) FROM expenses x2 "
                + "     WHERE x2.work_order_id = w.work_order_id AND x2.status='ACTIVE')) AS n_open "
                + "FROM work_orders w WHERE w.status <> 'CANCELLED'";
            try (PreparedStatement ps = con.prepareStatement(vendors);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.vendorOutstanding = Money.of(rs.getBigDecimal("due"));
                    s.openOrders        = rs.getInt("n_open");
                }
            }

            // Exam fees. Same payable/paid definition as ExamPaymentDAO.
            String payable = "(CASE WHEN c.fee_waived = 1 THEN 0 "
                           + "      ELSE COALESCE(c.fee_amount, e.exam_fee) END)";
            String paid    = "(SELECT COALESCE(SUM(p.amount),0) FROM exam_payments p "
                           + "   WHERE p.candidate_id = c.candidate_id AND p.status='ACTIVE')";
            String exams =
                  "SELECT COALESCE(SUM(" + payable + "),0) AS expected, "
                + "       COALESCE(SUM(" + paid    + "),0) AS collected, "
                + "       COALESCE(SUM(GREATEST(" + payable + " - " + paid + ", 0)),0) AS due, "
                + "       SUM(" + payable + " > " + paid + ") AS n_due "
                + "  FROM exam_candidates c JOIN exams e ON e.exam_id = c.exam_id";
            try (PreparedStatement ps = con.prepareStatement(exams);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.examExpected    = Money.of(rs.getBigDecimal("expected"));
                    s.examCollected   = Money.of(rs.getBigDecimal("collected"));
                    s.examOutstanding = Money.of(rs.getBigDecimal("due"));
                    s.candidatesDue   = rs.getInt("n_due");
                }
            }
        }
        return s;
    }
}
