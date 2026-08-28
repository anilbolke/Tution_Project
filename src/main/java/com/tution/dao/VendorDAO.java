package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.Vendor;
import com.tution.util.DBConnection;

/**
 * Vendors, and the money position each one is in.
 *
 * The ordered / paid figures come from one subquery pair reused by every read
 * here, so the list, the vendor card and the work-order screen can never quote
 * three different outstanding balances for the same vendor.
 */
public class VendorDAO {

    /**
     * The value a vendor has been committed. CANCELLED orders are excluded -
     * an order somebody stopped is not a commitment, and counting it would
     * inflate every vendor's outstanding for ever.
     */
    private static final String ORDERED =
          "(SELECT COALESCE(SUM(w.order_value),0) FROM work_orders w "
        + "   WHERE w.vendor_id = v.vendor_id AND w.status <> 'CANCELLED')";

    /** What has actually gone out to this vendor. VOID expenses do not count. */
    private static final String PAID =
          "(SELECT COALESCE(SUM(x.amount),0) FROM expenses x "
        + "   WHERE x.vendor_id = v.vendor_id AND x.status = 'ACTIVE')";

    private static final String ORDER_N =
          "(SELECT COUNT(*) FROM work_orders w "
        + "   WHERE w.vendor_id = v.vendor_id AND w.status <> 'CANCELLED')";

    /** Orders that can still take money: live, and not yet fully covered. */
    private static final String OPEN_N =
          "(SELECT COUNT(*) FROM work_orders w "
        + "   WHERE w.vendor_id = v.vendor_id "
        + "     AND w.status IN ('ISSUED','IN_PROGRESS','COMPLETED') "
        + "     AND w.order_value > (SELECT COALESCE(SUM(x2.amount),0) FROM expenses x2 "
        + "                           WHERE x2.work_order_id = w.work_order_id "
        + "                             AND x2.status = 'ACTIVE'))";

    private static final String SELECT =
          "SELECT v.*, " + ORDERED + " AS ordered, " + PAID + " AS paid, "
        + "       " + ORDER_N + " AS n_orders, " + OPEN_N + " AS n_open "
        + "  FROM vendors v ";

    /** @param q  free text over name, contact, mobile or category; may be null. */
    public List<Vendor> list(String q, boolean activeOnly) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1 ");
        List<String> args = new ArrayList<>();
        if (activeOnly) sql.append(" AND v.is_active = 1 ");
        if (notBlank(q)) {
            sql.append(" AND (v.name LIKE ? OR v.contact_person LIKE ? "
                     + "      OR v.mobile LIKE ? OR v.category LIKE ?) ");
            String like = "%" + q.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        sql.append(" ORDER BY v.is_active DESC, v.name ");

        List<Vendor> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setString(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public Vendor findById(int vendorId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + " WHERE v.vendor_id = ?")) {
            ps.setInt(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Used to catch a duplicate before the unique key does, so the message is human. */
    public Vendor findByName(String name) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT + " WHERE v.name = ?")) {
            ps.setString(1, name == null ? "" : name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public int insert(Vendor v) throws SQLException {
        String sql = "INSERT INTO vendors "
                   + "(name, contact_person, mobile, email, address, gstin, pan, "
                   + " bank_account, bank_ifsc, category, notes, is_active, created_by) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindEditable(ps, v);
            ps.setInt(12, v.isActive() ? 1 : 0);
            setNullable(ps, 13, v.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) { v.setVendorId(keys.getInt(1)); return v.getVendorId(); }
            }
            return 0;
        }
    }

    public boolean update(Vendor v) throws SQLException {
        String sql = "UPDATE vendors SET name=?, contact_person=?, mobile=?, email=?, address=?, "
                   + " gstin=?, pan=?, bank_account=?, bank_ifsc=?, category=?, notes=? "
                   + " WHERE vendor_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindEditable(ps, v);
            ps.setInt(12, v.getVendorId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Retires or restores a vendor.
     *
     * There is no delete. A vendor with work orders and expenses behind them is
     * part of the ledger's history; removing the row would orphan every voucher
     * that names them.
     */
    public boolean setActive(int vendorId, boolean active) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE vendors SET is_active = ? WHERE vendor_id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, vendorId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Distinct categories already in use, for the datalist on the form. */
    public List<String> categories() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT DISTINCT category FROM vendors "
                   + " WHERE category IS NOT NULL AND category <> '' ORDER BY category");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private void bindEditable(PreparedStatement ps, Vendor v) throws SQLException {
        ps.setString(1, v.getName() == null ? "" : v.getName().trim());
        setNullable(ps,  2, v.getContactPerson());
        setNullable(ps,  3, v.getMobile());
        setNullable(ps,  4, v.getEmail());
        setNullable(ps,  5, v.getAddress());
        setNullable(ps,  6, v.getGstin());
        setNullable(ps,  7, v.getPan());
        setNullable(ps,  8, v.getBankAccount());
        setNullable(ps,  9, v.getBankIfsc());
        setNullable(ps, 10, v.getCategory());
        setNullable(ps, 11, v.getNotes());
    }

    private Vendor map(ResultSet rs) throws SQLException {
        Vendor v = new Vendor();
        v.setVendorId(rs.getInt("vendor_id"));
        v.setName(rs.getString("name"));
        v.setContactPerson(rs.getString("contact_person"));
        v.setMobile(rs.getString("mobile"));
        v.setEmail(rs.getString("email"));
        v.setAddress(rs.getString("address"));
        v.setGstin(rs.getString("gstin"));
        v.setPan(rs.getString("pan"));
        v.setBankAccount(rs.getString("bank_account"));
        v.setBankIfsc(rs.getString("bank_ifsc"));
        v.setCategory(rs.getString("category"));
        v.setNotes(rs.getString("notes"));
        v.setActive(rs.getInt("is_active") == 1);
        v.setCreatedBy(rs.getString("created_by"));
        v.setCreatedAt(rs.getString("created_at"));
        v.setOrdered(rs.getBigDecimal("ordered"));
        v.setPaid(rs.getBigDecimal("paid"));
        v.setOrderCount(rs.getInt("n_orders"));
        v.setOpenOrders(rs.getInt("n_open"));
        return v;
    }

    private void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) ps.setNull(idx, Types.VARCHAR);
        else                                     ps.setString(idx, val.trim());
    }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
