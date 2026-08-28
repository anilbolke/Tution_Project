package com.tution.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.Expense;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/** Expense vouchers, and the categories they are filed under. */
public class ExpenseDAO {

    private static final String SELECT =
          "SELECT x.*, f.name AS fund_name, c.name AS category_name, "
        + "       v.name AS vendor_name, w.wo_no, w.title AS wo_title "
        + "  FROM expenses x "
        + "  JOIN fund_accounts f ON f.fund_id = x.fund_id "
        + "  LEFT JOIN expense_categories c ON c.category_id   = x.category_id "
        + "  LEFT JOIN vendors           v ON v.vendor_id     = x.vendor_id "
        + "  LEFT JOIN work_orders       w ON w.work_order_id = x.work_order_id ";

    /**
     * @param vendorId  0 for any
     * @param woId      0 for any
     * @param catId     0 for any
     * @param status    ACTIVE / VOID, or null for both
     */
    public List<Expense> list(int fundId, int vendorId, int woId, int catId,
                              String status, String from, String to, String q)
            throws SQLException {

        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (fundId   > 0) { sql.append(" AND x.fund_id = ? ");       args.add(fundId); }
        if (vendorId > 0) { sql.append(" AND x.vendor_id = ? ");     args.add(vendorId); }
        if (woId     > 0) { sql.append(" AND x.work_order_id = ? "); args.add(woId); }
        if (catId    > 0) { sql.append(" AND x.category_id = ? ");   args.add(catId); }
        if (notBlank(status)) { sql.append(" AND x.status = ? ");    args.add(status.trim()); }
        if (notBlank(from))   { sql.append(" AND x.expense_date >= ? "); args.add(from.trim()); }
        if (notBlank(to))     { sql.append(" AND x.expense_date <= ? "); args.add(to.trim()); }
        if (notBlank(q)) {
            sql.append(" AND (x.voucher_no LIKE ? OR x.description LIKE ? "
                     + "      OR x.invoice_no LIKE ? OR x.txn_ref LIKE ? OR v.name LIKE ? "
                     + "      OR w.wo_no LIKE ?) ");
            String like = "%" + q.trim() + "%";
            for (int i = 0; i < 6; i++) args.add(like);
        }
        sql.append(" ORDER BY x.expense_date DESC, x.expense_id DESC ");

        List<Expense> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public Expense findById(int expenseId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + " WHERE x.expense_id = ?")) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * What has been paid against a work order, read INSIDE the caller's
     * transaction and behind a lock on the order row.
     *
     * This is the whole over-payment guard. Reading the total on a separate
     * connection would let two people each see the same remaining balance and
     * both be told there was room; the {@code FOR UPDATE} on work_orders makes
     * the second one wait until the first has committed, so it sees the money
     * that just went out.
     */
    public BigDecimal paidOnOrderForUpdate(Connection con, int workOrderId) throws SQLException {
        try (PreparedStatement lock = con.prepareStatement(
                "SELECT work_order_id FROM work_orders WHERE work_order_id = ? FOR UPDATE")) {
            lock.setInt(1, workOrderId);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next()) return null;    // caller reports "no such order"
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(amount),0) FROM expenses "
              + " WHERE work_order_id = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, workOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.of(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    /** Writes the voucher on the caller's connection, inside their transaction. */
    public int insert(Connection con, Expense x) throws SQLException {
        String sql = "INSERT INTO expenses "
                   + "(fund_id, voucher_no, expense_date, category_id, vendor_id, work_order_id, "
                   + " amount, tax_amount, payment_mode, txn_ref, invoice_no, bill_path, "
                   + " description, created_by, created_by_id) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        SQLException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            String voucher = nextVoucherNo(con);
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, x.getFundId());
                ps.setString(2, voucher);
                ps.setString(3, x.getExpenseDate());
                setNullableInt(ps, 4, x.getCategoryId());
                setNullableInt(ps, 5, x.getVendorId());
                setNullableInt(ps, 6, x.getWorkOrderId());
                ps.setBigDecimal(7, Money.of(x.getAmount()));
                ps.setBigDecimal(8, Money.of(x.getTaxAmount()));
                ps.setString(9, x.getPaymentMode() == null ? "Cash" : x.getPaymentMode());
                setNullable(ps, 10, x.getTxnRef());
                setNullable(ps, 11, x.getInvoiceNo());
                setNullable(ps, 12, x.getBillPath());
                setNullable(ps, 13, x.getDescription());
                setNullable(ps, 14, x.getCreatedBy());
                setNullableInt(ps, 15, x.getCreatedById());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        x.setExpenseId(keys.getInt(1));
                        x.setVoucherNo(voucher);
                        return x.getExpenseId();
                    }
                }
                return 0;
            } catch (SQLIntegrityConstraintViolationException e) {
                last = e;   // almost certainly the voucher number; take the next
            }
        }
        throw last != null ? last : new SQLException("Could not allocate a voucher number.");
    }

    /**
     * Marks a voucher cancelled inside the caller's transaction.
     *
     * Returns false if it was already cancelled, which is what makes the void
     * safe to race: only the caller that actually flips the row goes on to post
     * the reversing credit, so a double-click cannot credit the fund twice.
     */
    public boolean voidExpense(Connection con, int expenseId, String reason, Integer byUserId)
            throws SQLException {
        String sql = "UPDATE expenses SET status='VOID', void_reason=?, voided_at=NOW(), "
                   + "       voided_by_id=? WHERE expense_id=? AND status='ACTIVE'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            setNullable(ps, 1, reason);
            setNullableInt(ps, 2, byUserId);
            ps.setInt(3, expenseId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Totals for the filtered view. VOID vouchers never count towards spend. */
    public Totals totals(int fundId, int vendorId, int woId, int catId,
                         String from, String to) throws SQLException {
        StringBuilder sql = new StringBuilder(
              "SELECT COUNT(*) AS n, COALESCE(SUM(x.amount),0) AS spent, "
            + "       COALESCE(SUM(x.tax_amount),0) AS tax "
            + "  FROM expenses x WHERE x.status = 'ACTIVE' ");
        List<Object> args = new ArrayList<>();
        if (fundId   > 0) { sql.append(" AND x.fund_id = ? ");       args.add(fundId); }
        if (vendorId > 0) { sql.append(" AND x.vendor_id = ? ");     args.add(vendorId); }
        if (woId     > 0) { sql.append(" AND x.work_order_id = ? "); args.add(woId); }
        if (catId    > 0) { sql.append(" AND x.category_id = ? ");   args.add(catId); }
        if (notBlank(from)) { sql.append(" AND x.expense_date >= ? "); args.add(from.trim()); }
        if (notBlank(to))   { sql.append(" AND x.expense_date <= ? "); args.add(to.trim()); }

        Totals t = new Totals();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    t.vouchers = rs.getInt("n");
                    t.spent    = Money.of(rs.getBigDecimal("spent"));
                    t.tax      = Money.of(rs.getBigDecimal("tax"));
                }
            }
        }
        return t;
    }

    public static class Totals {
        public int        vouchers;
        public BigDecimal spent = Money.ZERO;
        public BigDecimal tax   = Money.ZERO;
    }

    /** Spend by category for the filtered period - the "where did it go" strip. */
    public List<Object[]> byCategory(String from, String to) throws SQLException {
        StringBuilder sql = new StringBuilder(
              "SELECT COALESCE(c.name,'Uncategorised') AS nm, COALESCE(SUM(x.amount),0) AS spent, "
            + "       COUNT(*) AS n "
            + "  FROM expenses x LEFT JOIN expense_categories c ON c.category_id = x.category_id "
            + " WHERE x.status = 'ACTIVE' ");
        List<Object> args = new ArrayList<>();
        if (notBlank(from)) { sql.append(" AND x.expense_date >= ? "); args.add(from.trim()); }
        if (notBlank(to))   { sql.append(" AND x.expense_date <= ? "); args.add(to.trim()); }
        sql.append(" GROUP BY nm ORDER BY spent DESC");

        List<Object[]> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Object[] { rs.getString("nm"),
                                           Money.of(rs.getBigDecimal("spent")),
                                           Integer.valueOf(rs.getInt("n")) });
                }
            }
        }
        return out;
    }

    /* ─────────────────────── categories ─────────────────────── */

    public List<Object[]> categories() throws SQLException {
        List<Object[]> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT category_id, name FROM expense_categories "
                   + " WHERE is_active = 1 ORDER BY sort_order, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Object[] { Integer.valueOf(rs.getInt(1)), rs.getString(2) });
            }
        }
        return out;
    }

    public int addCategory(String name) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO expense_categories (name, sort_order) VALUES (?, 900)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /* ─────────────────────── internals ─────────────────────── */

    /**
     * "EXP-2627-0142" - financial-year pair, then a sequence within it.
     *
     * Runs on the caller's connection so that inside a transaction it sees the
     * vouchers that transaction has already written.
     */
    private String nextVoucherNo(Connection con) throws SQLException {
        int yy     = Year.now().getValue() % 100;
        int nextYy = (yy + 1) % 100;
        String prefix = String.format("EXP-%02d%02d-", yy, nextYy);

        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(voucher_no, ?) AS UNSIGNED)), 0) + 1 AS nxt "
                   + "  FROM expenses WHERE voucher_no LIKE ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, prefix.length() + 1);
            ps.setString(2, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int next = rs.next() ? rs.getInt("nxt") : 1;
                return prefix + String.format("%04d", next);
            }
        }
    }

    private Expense map(ResultSet rs) throws SQLException {
        Expense x = new Expense();
        x.setExpenseId(rs.getInt("expense_id"));
        x.setFundId(rs.getInt("fund_id"));
        x.setVoucherNo(rs.getString("voucher_no"));
        x.setExpenseDate(rs.getString("expense_date"));
        int cat = rs.getInt("category_id");   x.setCategoryId(rs.wasNull()  ? null : cat);
        int ven = rs.getInt("vendor_id");     x.setVendorId(rs.wasNull()    ? null : ven);
        int wo  = rs.getInt("work_order_id"); x.setWorkOrderId(rs.wasNull() ? null : wo);
        x.setAmount(rs.getBigDecimal("amount"));
        x.setTaxAmount(rs.getBigDecimal("tax_amount"));
        x.setPaymentMode(rs.getString("payment_mode"));
        x.setTxnRef(rs.getString("txn_ref"));
        x.setInvoiceNo(rs.getString("invoice_no"));
        x.setBillPath(rs.getString("bill_path"));
        x.setDescription(rs.getString("description"));
        x.setStatus(rs.getString("status"));
        int vb = rs.getInt("voided_by_id");   x.setVoidedById(rs.wasNull() ? null : vb);
        x.setVoidedAt(rs.getString("voided_at"));
        x.setVoidReason(rs.getString("void_reason"));
        x.setCreatedBy(rs.getString("created_by"));
        int cb = rs.getInt("created_by_id");  x.setCreatedById(rs.wasNull() ? null : cb);
        x.setCreatedAt(rs.getString("created_at"));
        x.setFundName(rs.getString("fund_name"));
        x.setCategoryName(rs.getString("category_name"));
        x.setVendorName(rs.getString("vendor_name"));
        x.setWoNo(rs.getString("wo_no"));
        x.setWoTitle(rs.getString("wo_title"));
        return x;
    }

    private void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof Integer) ps.setInt(i + 1, (Integer) a);
            else                      ps.setString(i + 1, String.valueOf(a));
        }
    }

    private void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(idx, Types.VARCHAR);
        else                                 ps.setString(idx, v.trim());
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null || v <= 0) ps.setNull(idx, Types.INTEGER);
        else                     ps.setInt(idx, v);
    }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
