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

import com.tution.model.WorkOrder;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * Work orders and how much of each has been paid.
 *
 * {@link #PAID} is the one definition of "paid against this order" in the whole
 * application. The list, the vendor card, the over-payment guard in
 * ExpenseService and the work-order PDF all read it, so none of them can drift
 * apart from the others.
 */
public class WorkOrderDAO {

    /**
     * What has been paid against an order.
     *
     * `amount` ONLY - not amount + tax_amount. An expense's `amount` is the money
     * that left the fund, and `tax_amount` is the tax component already inside
     * it, kept apart for a future GST report. Adding the two would count the tax
     * twice and let a vendor look over-paid on an order that is not.
     */
    private static final String PAID =
          "(SELECT COALESCE(SUM(x.amount),0) FROM expenses x "
        + "   WHERE x.work_order_id = w.work_order_id AND x.status = 'ACTIVE')";

    private static final String EXP_N =
          "(SELECT COUNT(*) FROM expenses x "
        + "   WHERE x.work_order_id = w.work_order_id AND x.status = 'ACTIVE')";

    private static final String SELECT =
          "SELECT w.*, v.name AS vendor_name, "
        + "       " + PAID  + " AS paid, "
        + "       " + EXP_N + " AS n_exp "
        + "  FROM work_orders w JOIN vendors v ON v.vendor_id = w.vendor_id ";

    /**
     * @param vendorId  0 for every vendor
     * @param status    lifecycle filter, or null
     * @param payState  UNPAID / PARTIALLY_PAID / FULLY_PAID, or null. Filtered
     *                  with HAVING because paid is computed and does not exist
     *                  yet at WHERE time.
     */
    public List<WorkOrder> list(int vendorId, String status, String payState, String q)
            throws SQLException {

        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (vendorId > 0)        { sql.append(" AND w.vendor_id = ? ");  args.add(vendorId); }
        if (notBlank(status))    { sql.append(" AND w.status = ? ");     args.add(status.trim()); }
        if (notBlank(q)) {
            sql.append(" AND (w.wo_no LIKE ? OR w.title LIKE ? OR v.name LIKE ?) ");
            String like = "%" + q.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }

        if (notBlank(payState)) {
            switch (payState) {
                case WorkOrder.UNPAID:
                    sql.append(" HAVING paid = 0 "); break;
                case WorkOrder.PARTIALLY_PAID:
                    sql.append(" HAVING paid > 0 AND paid < w.order_value "); break;
                case WorkOrder.FULLY_PAID:
                    sql.append(" HAVING paid >= w.order_value AND paid > 0 "); break;
                default: break;
            }
        }
        sql.append(" ORDER BY w.order_date DESC, w.work_order_id DESC ");

        List<WorkOrder> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public WorkOrder findById(int workOrderId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + " WHERE w.work_order_id = ?")) {
            ps.setInt(1, workOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * The orders an expense may legitimately be booked against for one vendor.
     *
     * Restricted to live orders that still have room, because those are the only
     * ones a payment can go to - offering a cancelled or fully-paid order in a
     * dropdown is an invitation to book money that will then be refused.
     */
    public List<WorkOrder> payableFor(int vendorId) throws SQLException {
        String sql = SELECT
                   + " WHERE w.vendor_id = ? "
                   + "   AND w.status IN ('ISSUED','IN_PROGRESS','COMPLETED') "
                   + " HAVING paid < w.order_value "
                   + " ORDER BY w.order_date, w.work_order_id";
        List<WorkOrder> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /**
     * Every order that can still take money, for every vendor.
     *
     * The expense form embeds these so choosing a vendor narrows the work-order
     * list without a round trip. It is the same rule as {@link #payableFor(int)},
     * so the dropdown can never offer an order the server would then refuse.
     */
    public List<WorkOrder> payableAll() throws SQLException {
        String sql = SELECT
                   + " WHERE w.status IN ('ISSUED','IN_PROGRESS','COMPLETED') "
                   + " HAVING paid < w.order_value "
                   + " ORDER BY v.name, w.order_date, w.work_order_id";
        List<WorkOrder> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    /** Totals strip for the work-order screen. */
    public Totals totals(int vendorId, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
              "SELECT COUNT(*) AS n, COALESCE(SUM(w.order_value),0) AS ordered, "
            + "       COALESCE(SUM(" + PAID + "),0) AS paid, "
            + "       COALESCE(SUM(GREATEST(w.order_value - " + PAID + ", 0)),0) AS due "
            + "  FROM work_orders w WHERE w.status <> 'CANCELLED' ");
        List<Object> args = new ArrayList<>();
        if (vendorId > 0)     { sql.append(" AND w.vendor_id = ? "); args.add(vendorId); }
        if (notBlank(status)) { sql.append(" AND w.status = ? ");    args.add(status.trim()); }

        Totals t = new Totals();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    t.orders    = rs.getInt("n");
                    t.ordered   = Money.of(rs.getBigDecimal("ordered"));
                    t.paid      = Money.of(rs.getBigDecimal("paid"));
                    // Floored per order, so one over-paid order cannot cancel
                    // out what is still owed on another.
                    t.remaining = Money.of(rs.getBigDecimal("due"));
                }
            }
        }
        return t;
    }

    public static class Totals {
        public int        orders;
        public BigDecimal ordered   = Money.ZERO;
        public BigDecimal paid      = Money.ZERO;
        public BigDecimal remaining = Money.ZERO;
    }

    public int insert(WorkOrder w) throws SQLException {
        String sql = "INSERT INTO work_orders "
                   + "(vendor_id, wo_no, title, description, order_value, order_date, "
                   + " expected_date, status, created_by, created_by_id, remarks) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        SQLException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            String woNo = nextWoNo();
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, w.getVendorId());
                ps.setString(2, woNo);
                ps.setString(3, w.getTitle());
                setNullable(ps, 4, w.getDescription());
                ps.setBigDecimal(5, Money.of(w.getOrderValue()));
                ps.setString(6, w.getOrderDate());
                setNullable(ps, 7, w.getExpectedDate());
                ps.setString(8, w.getStatus() == null ? WorkOrder.DRAFT : w.getStatus());
                setNullable(ps, 9, w.getCreatedBy());
                if (w.getCreatedById() == null) ps.setNull(10, Types.INTEGER);
                else                            ps.setInt(10, w.getCreatedById());
                setNullable(ps, 11, w.getRemarks());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        w.setWorkOrderId(keys.getInt(1));
                        w.setWoNo(woNo);
                        return w.getWorkOrderId();
                    }
                }
                return 0;
            } catch (SQLIntegrityConstraintViolationException e) {
                last = e;   // almost certainly the WO number; take the next one
            }
        }
        throw last != null ? last : new SQLException("Could not allocate a work order number.");
    }

    /** Edits the agreed terms. The number, vendor and lifecycle are not touched here. */
    public boolean update(WorkOrder w) throws SQLException {
        String sql = "UPDATE work_orders SET title=?, description=?, order_value=?, "
                   + " order_date=?, expected_date=?, remarks=? WHERE work_order_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, w.getTitle());
            setNullable(ps, 2, w.getDescription());
            ps.setBigDecimal(3, Money.of(w.getOrderValue()));
            ps.setString(4, w.getOrderDate());
            setNullable(ps, 5, w.getExpectedDate());
            setNullable(ps, 6, w.getRemarks());
            ps.setInt(7, w.getWorkOrderId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Moves an order along its lifecycle.
     *
     * The approver is stamped the first time the order becomes payable and is
     * never overwritten, so the record of who authorised the spend survives every
     * later status change.
     *
     * CANCELLING DOES NOT COUNT AS APPROVING. A draft that somebody killed was
     * never authorised, and writing the canceller into approved_by_id would leave
     * an audit trail claiming they signed off on a spend they in fact stopped.
     */
    public boolean setStatus(int workOrderId, String status, Integer byUserId) throws SQLException {
        boolean payable = !WorkOrder.DRAFT.equals(status) && !WorkOrder.CANCELLED.equals(status);
        String sql = "UPDATE work_orders SET status = ?, "
                   + (payable ? "       approved_by_id = COALESCE(approved_by_id, ?), "
                              + "       approved_at    = COALESCE(approved_at, NOW()) "
                              : "       approved_by_id = approved_by_id ")
                   + " WHERE work_order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, status);
            if (payable) {
                if (byUserId == null) ps.setNull(i++, Types.INTEGER);
                else                  ps.setInt(i++, byUserId);
            }
            ps.setInt(i, workOrderId);
            return ps.executeUpdate() > 0;
        }
    }

    /* ─────────────────────── internals ─────────────────────── */

    /** "WO-2627-0003" - financial-year pair, then a sequence within it. */
    private String nextWoNo() throws SQLException {
        int yy     = Year.now().getValue() % 100;
        int nextYy = (yy + 1) % 100;
        String prefix = String.format("WO-%02d%02d-", yy, nextYy);

        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(wo_no, ?) AS UNSIGNED)), 0) + 1 AS nxt "
                   + "  FROM work_orders WHERE wo_no LIKE ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, prefix.length() + 1);
            ps.setString(2, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int next = rs.next() ? rs.getInt("nxt") : 1;
                return prefix + String.format("%04d", next);
            }
        }
    }

    private WorkOrder map(ResultSet rs) throws SQLException {
        WorkOrder w = new WorkOrder();
        w.setWorkOrderId(rs.getInt("work_order_id"));
        w.setVendorId(rs.getInt("vendor_id"));
        w.setWoNo(rs.getString("wo_no"));
        w.setTitle(rs.getString("title"));
        w.setDescription(rs.getString("description"));
        w.setOrderValue(rs.getBigDecimal("order_value"));
        w.setOrderDate(rs.getString("order_date"));
        w.setExpectedDate(rs.getString("expected_date"));
        w.setStatus(rs.getString("status"));
        w.setCreatedBy(rs.getString("created_by"));
        int cid = rs.getInt("created_by_id");
        w.setCreatedById(rs.wasNull() ? null : cid);
        int aid = rs.getInt("approved_by_id");
        w.setApprovedById(rs.wasNull() ? null : aid);
        w.setApprovedAt(rs.getString("approved_at"));
        w.setRemarks(rs.getString("remarks"));
        w.setCreatedAt(rs.getString("created_at"));
        w.setVendorName(rs.getString("vendor_name"));
        w.setPaid(rs.getBigDecimal("paid"));
        w.setExpenseCount(rs.getInt("n_exp"));
        return w;
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

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
