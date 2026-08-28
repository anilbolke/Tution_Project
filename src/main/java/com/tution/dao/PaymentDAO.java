package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.tution.model.Payment;
import com.tution.util.DBConnection;

/** Data-access for fee payments. */
public class PaymentDAO {

    /**
     * Inserts a payment, generating a unique receipt number (e.g. RCPT-2627-0481),
     * and allocates the money against the student's open installments — oldest
     * due first — in the SAME transaction.
     *
     * The two must not come apart: a recorded payment that failed to clear its
     * installment would keep chasing the parent for money already collected.
     *
     * Sets the generated id + receipt number on the supplied object and returns the id.
     */
    public int insert(Payment p) throws SQLException {
        String sql = "INSERT INTO payments "
            + "(student_id, receipt_no, amount, payment_mode, payment_date, remarks, collected_by, "
            + " collected_by_id, txn_ref, razorpay_order_id, razorpay_payment_id) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            for (int attempt = 0; attempt < 5; attempt++) {
                String receiptNo = generateReceiptNo();
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, p.getStudentId());
                    ps.setString(2, receiptNo);
                    ps.setInt(3, p.getAmount());
                    ps.setString(4, p.getPaymentMode());
                    ps.setString(5, p.getPaymentDate());
                    if (p.getRemarks() == null || p.getRemarks().trim().isEmpty()) {
                        ps.setNull(6, java.sql.Types.VARCHAR);
                    } else {
                        ps.setString(6, p.getRemarks().trim());
                    }
                    ps.setString(7, p.getCollectedBy());
                    if (p.getCollectedById() == null) {
                        ps.setNull(8, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(8, p.getCollectedById());
                    }
                    setNullable(ps, 9, p.getTxnRef());
                    setNullable(ps, 10, p.getRazorpayOrderId());
                    setNullable(ps, 11, p.getRazorpayPaymentId());
                    ps.executeUpdate();

                    int id;
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        id = keys.next() ? keys.getInt(1) : 0;
                    }
                    p.setPaymentId(id);
                    p.setReceiptNo(receiptNo);

                    Integer firstTouched = allocate(con, p.getStudentId(), p.getAmount(),
                                                    p.getPaymentDate());
                    if (firstTouched != null) {
                        try (PreparedStatement up = con.prepareStatement(
                                 "UPDATE payments SET installment_id = ? WHERE payment_id = ?")) {
                            up.setInt(1, firstTouched);
                            up.setInt(2, id);
                            up.executeUpdate();
                        }
                        p.setInstallmentId(firstTouched);
                    }

                    con.commit();
                    return id;

                } catch (SQLIntegrityConstraintViolationException dup) {
                    // receipt_no collision -> roll back this attempt and retry
                    con.rollback();
                }
            }
            throw new SQLException("Could not generate a unique receipt number.");

        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); } catch (SQLException ignore) { }
                try { con.close(); } catch (SQLException ignore) { }
            }
        }
    }

    /**
     * Spreads an amount across the student's open installments, oldest due first.
     *
     * @return the id of the first installment the money touched (the payment's
     *         primary attribution), or null when the student has no schedule —
     *         a pay-in-full student is perfectly valid and simply has none.
     */
    private Integer allocate(Connection con, int studentId, int amount, String paidDate)
            throws SQLException {
        InstallmentDAO instDAO = new InstallmentDAO();
        List<com.tution.model.FeeInstallment> open = instDAO.openForStudent(con, studentId);
        if (open.isEmpty()) {
            return null;
        }
        Integer first = null;
        int left = amount;
        for (com.tution.model.FeeInstallment inst : open) {
            if (left <= 0) {
                break;
            }
            int need = inst.getBalance();
            if (need <= 0) {
                continue;
            }
            int give = Math.min(need, left);
            instDAO.applyPayment(con, inst.getInstallmentId(), give, paidDate);
            if (first == null) {
                first = inst.getInstallmentId();
            }
            left -= give;
        }
        return first;
    }

    /** Total amount paid by a single student. */
    public int getTotalPaid(int studentId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM payments WHERE student_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** studentId -> total paid, for the whole fee list in one query. */
    public Map<Integer, Integer> getPaidMap() throws SQLException {
        String sql = "SELECT student_id, COALESCE(SUM(amount),0) AS paid FROM payments GROUP BY student_id";
        Map<Integer, Integer> map = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getInt("student_id"), rs.getInt("paid"));
            }
        }
        return map;
    }

    /** Payment history for a student, newest first. */
    public List<Payment> findByStudent(int studentId) throws SQLException {
        String sql = "SELECT payment_id, receipt_no, amount, payment_mode, payment_date, "
                   + "remarks, collected_by, collected_by_id, txn_ref, installment_id, created_at "
                   + "FROM payments WHERE student_id = ? ORDER BY payment_id DESC";
        List<Payment> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Payment p = new Payment();
                    p.setPaymentId(rs.getInt("payment_id"));
                    p.setStudentId(studentId);
                    p.setReceiptNo(rs.getString("receipt_no"));
                    p.setAmount(rs.getInt("amount"));
                    p.setPaymentMode(rs.getString("payment_mode"));
                    p.setPaymentDate(String.valueOf(rs.getDate("payment_date")));
                    p.setRemarks(rs.getString("remarks"));
                    p.setCollectedBy(rs.getString("collected_by"));
                    p.setTxnRef(rs.getString("txn_ref"));
                    int cbid = rs.getInt("collected_by_id");
                    p.setCollectedById(rs.wasNull() ? null : cbid);
                    int inst = rs.getInt("installment_id");
                    p.setInstallmentId(rs.wasNull() ? null : inst);
                    p.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                    list.add(p);
                }
            }
        }
        return list;
    }

    /** Single payment with joined student details (for the receipt). */
    public Payment findById(int paymentId) throws SQLException {
        String sql = "SELECT p.payment_id, p.student_id, p.receipt_no, p.amount, p.payment_mode, "
                   + "p.payment_date, p.remarks, p.collected_by, p.collected_by_id, p.txn_ref, "
                   + "p.installment_id, p.created_at, p.razorpay_order_id, p.razorpay_payment_id, "
                   + "s.full_name, s.student_mobile, s.admission_no, s.class_name, s.fee_slab "
                   + "FROM payments p JOIN students s ON s.student_id = p.student_id "
                   + "WHERE p.payment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Payment p = new Payment();
                p.setPaymentId(rs.getInt("payment_id"));
                p.setStudentId(rs.getInt("student_id"));
                p.setReceiptNo(rs.getString("receipt_no"));
                p.setAmount(rs.getInt("amount"));
                p.setPaymentMode(rs.getString("payment_mode"));
                p.setPaymentDate(String.valueOf(rs.getDate("payment_date")));
                p.setRemarks(rs.getString("remarks"));
                p.setCollectedBy(rs.getString("collected_by"));
                p.setTxnRef(rs.getString("txn_ref"));
                int cbid = rs.getInt("collected_by_id");
                p.setCollectedById(rs.wasNull() ? null : cbid);
                int inst = rs.getInt("installment_id");
                p.setInstallmentId(rs.wasNull() ? null : inst);
                p.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                p.setRazorpayOrderId(rs.getString("razorpay_order_id"));
                p.setRazorpayPaymentId(rs.getString("razorpay_payment_id"));
                p.setStudentName(rs.getString("full_name"));
                p.setStudentMobile(rs.getString("student_mobile"));
                p.setAdmissionNo(rs.getString("admission_no"));
                p.setClassName(rs.getString("class_name"));
                p.setFeeSlab(rs.getString("fee_slab"));
                return p;
            }
        }
    }

    /** Whether the WhatsApp receipt was already sent for this payment (avoids resend on refresh). */
    public boolean isWaSent(int paymentId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT wa_sent FROM payments WHERE payment_id = ?")) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /** Marks the WhatsApp receipt as sent for this payment. */
    public void markWaSent(int paymentId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE payments SET wa_sent = 1 WHERE payment_id = ?")) {
            ps.setInt(1, paymentId);
            ps.executeUpdate();
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, java.sql.Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }

    private String generateReceiptNo() {
        int yy = Year.now().getValue() % 100;
        int nextYy = (yy + 1) % 100;
        int seq = ThreadLocalRandom.current().nextInt(1, 10000);
        return String.format("RCPT-%02d%02d-%04d", yy, nextYy, seq);
    }
}
