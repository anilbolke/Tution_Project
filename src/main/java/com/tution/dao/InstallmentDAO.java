package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.FeeInstallment;
import com.tution.util.DBConnection;

/** Data-access for the installment / EMI schedule. */
public class InstallmentDAO {

    private static final String COLS =
          "i.installment_id, i.student_id, i.seq, i.kind, i.label, i.amount, i.pct, i.due_date, "
        + "i.paid_amount, i.paid_date, i.status, i.reminder_sent, "
        + "s.full_name AS student_name, s.admission_no, s.student_mobile, s.parent_mobile";

    private static final String FROM =
          "FROM fee_installments i JOIN students s ON s.student_id = i.student_id ";

    /** A student's schedule in order. */
    public List<FeeInstallment> findByStudent(int studentId) throws SQLException {
        String sql = "SELECT " + COLS + " " + FROM + "WHERE i.student_id = ? ORDER BY i.seq";
        List<FeeInstallment> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Installments due in a window and not yet settled — the fee-reminder queue
     * and the defaulter report both read this.
     *
     * @param from   inclusive due date, or null for no lower bound
     * @param to     inclusive due date, or null for no upper bound
     * @param unsentOnly restrict to rows whose reminder has not gone out yet
     */
    public List<FeeInstallment> findDue(String from, String to, boolean unsentOnly)
            throws SQLException {
        return findDue(from, to, unsentOnly, null);
    }

    /**
     * @param scopeCounsellorId when set, only instalments belonging to that
     *                          counsellor's students — so a counsellor's
     *                          reminder queue is their own work, not the
     *                          institute's whole fee book
     */
    public List<FeeInstallment> findDue(String from, String to, boolean unsentOnly,
                                        Integer scopeCounsellorId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS).append(" ").append(FROM)
            .append("WHERE i.status <> 'PAID' AND s.is_active = 1 ");
        List<Object> args = new ArrayList<>();
        if (notBlank(from)) { sql.append("AND i.due_date >= ? "); args.add(from); }
        if (notBlank(to))   { sql.append("AND i.due_date <= ? "); args.add(to); }
        if (unsentOnly)     { sql.append("AND i.reminder_sent = 0 "); }
        if (scopeCounsellorId != null) {
            sql.append("AND ").append(Scope.teamOf("s.counsellor_id")).append(' ');
            args.add(scopeCounsellorId);
        }
        sql.append("ORDER BY i.due_date, s.full_name");

        List<FeeInstallment> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                Object a = args.get(i);
                if (a instanceof Integer) {
                    ps.setInt(i + 1, (Integer) a);
                } else {
                    ps.setString(i + 1, String.valueOf(a));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Replaces a student's whole schedule in one transaction.
     *
     * Regenerating is all-or-nothing on purpose: a half-written schedule would
     * bill the student for a plan that never existed. Money already allocated to
     * the old installments is re-applied afterwards by
     * {@code FeeService.reallocate}, so no payment is lost.
     */
    public void replaceSchedule(int studentId, List<FeeInstallment> rows) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            // Detach payments first — the FK would otherwise block the delete.
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE payments SET installment_id = NULL WHERE installment_id IN "
                   + "(SELECT installment_id FROM fee_installments WHERE student_id = ?)")) {
                ps.setInt(1, studentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM fee_installments WHERE student_id = ?")) {
                ps.setInt(1, studentId);
                ps.executeUpdate();
            }

            if (rows != null && !rows.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO fee_installments "
                       + "(student_id, seq, kind, label, amount, pct, due_date, paid_amount, status) "
                       + "VALUES (?,?,?,?,?,?,?,0,'PENDING')")) {
                    for (FeeInstallment r : rows) {
                        ps.setInt(1, studentId);
                        ps.setInt(2, r.getSeq());
                        ps.setString(3, r.getKind() == null ? "COURSE" : r.getKind());
                        setNullable(ps, 4, r.getLabel());
                        ps.setInt(5, r.getAmount());
                        if (r.getPct() == null) {
                            ps.setNull(6, Types.DECIMAL);
                        } else {
                            ps.setDouble(6, r.getPct());
                        }
                        setNullableDate(ps, 7, r.getDueDate());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /**
     * Applies money to one installment and recomputes its status.
     *
     * Deliberately two statements. Doing both in one UPDATE double-counted the
     * payment: MySQL evaluates assignments left to right, so by the time the
     * status CASE ran, paid_amount had ALREADY been incremented — and the CASE
     * added the same amount again. A half payment therefore came out as PAID,
     * which dropped the installment out of {@link #openForStudent} and the
     * reminder queue, so the rest was never collected or chased.
     *
     * Splitting it also removes the dependence on that left-to-right ordering,
     * which is a MySQL detail rather than something SQL guarantees.
     */
    public void applyPayment(Connection con, int installmentId, int amount, String paidDate)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                 "UPDATE fee_installments SET paid_amount = paid_amount + ?, paid_date = ? "
               + "WHERE installment_id = ?")) {
            ps.setInt(1, amount);
            setNullableDate(ps, 2, paidDate);
            ps.setInt(3, installmentId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                 "UPDATE fee_installments SET status = "
               + "  CASE WHEN paid_amount >= amount        THEN 'PAID' "
               + "       WHEN due_date < CURDATE()         THEN 'OVERDUE' "
               + "       WHEN paid_amount > 0              THEN 'PARTIAL' "
               + "       ELSE 'PENDING' END "
               + "WHERE installment_id = ?")) {
            ps.setInt(1, installmentId);
            ps.executeUpdate();
        }
    }

    /** Unsettled installments for a student, oldest due first — the allocation order. */
    public List<FeeInstallment> openForStudent(Connection con, int studentId) throws SQLException {
        List<FeeInstallment> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                 "SELECT installment_id, student_id, seq, kind, label, amount, due_date, "
               + "paid_amount, paid_date, status, reminder_sent FROM fee_installments "
               + "WHERE student_id = ? AND status <> 'PAID' ORDER BY due_date, seq")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FeeInstallment f = new FeeInstallment();
                    f.setInstallmentId(rs.getInt("installment_id"));
                    f.setStudentId(rs.getInt("student_id"));
                    f.setSeq(rs.getInt("seq"));
                    f.setKind(rs.getString("kind"));
                    f.setLabel(rs.getString("label"));
                    f.setAmount(rs.getInt("amount"));
                    java.sql.Date d = rs.getDate("due_date");
                    f.setDueDate(d == null ? null : d.toString());
                    f.setPaidAmount(rs.getInt("paid_amount"));
                    f.setStatus(rs.getString("status"));
                    list.add(f);
                }
            }
        }
        return list;
    }

    /**
     * Resets the schedule and spreads {@code totalPaid} across it oldest-first.
     *
     * Used after a schedule is regenerated, so money already collected lands on
     * the new rows instead of being asked for again.
     */
    public void reallocate(int studentId, int totalPaid) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE fee_installments SET paid_amount = 0, paid_date = NULL, "
                   + "status = 'PENDING' WHERE student_id = ?")) {
                ps.setInt(1, studentId);
                ps.executeUpdate();
            }

            int left = totalPaid;
            if (left > 0) {
                for (FeeInstallment inst : openForStudent(con, studentId)) {
                    if (left <= 0) {
                        break;
                    }
                    int give = Math.min(inst.getAmount(), left);
                    applyPayment(con, inst.getInstallmentId(), give, null);
                    left -= give;
                }
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /**
     * Flags everything past its due date as OVERDUE. Run nightly; also called
     * lazily when the fee dashboard loads so the status is never stale on screen.
     *
     * @return how many rows changed
     */
    public int markOverdue() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE fee_installments SET status = 'OVERDUE' "
               + "WHERE due_date < CURDATE() AND status IN ('PENDING','PARTIAL')")) {
            return ps.executeUpdate();
        }
    }

    /** Flags a due-reminder as sent so a second scheduler run does not resend. */
    public void markReminderSent(int installmentId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE fee_installments SET reminder_sent = 1 WHERE installment_id = ?")) {
            ps.setInt(1, installmentId);
            ps.executeUpdate();
        }
    }

    private FeeInstallment map(ResultSet rs) throws SQLException {
        FeeInstallment f = new FeeInstallment();
        f.setInstallmentId(rs.getInt("installment_id"));
        f.setStudentId(rs.getInt("student_id"));
        f.setSeq(rs.getInt("seq"));
        f.setKind(rs.getString("kind"));
        f.setLabel(rs.getString("label"));
        f.setAmount(rs.getInt("amount"));
        double pc = rs.getDouble("pct");
        f.setPct(rs.wasNull() ? null : pc);
        java.sql.Date due = rs.getDate("due_date");
        f.setDueDate(due == null ? null : due.toString());
        f.setPaidAmount(rs.getInt("paid_amount"));
        java.sql.Date pd = rs.getDate("paid_date");
        f.setPaidDate(pd == null ? null : pd.toString());
        f.setStatus(rs.getString("status"));
        f.setReminderSent(rs.getInt("reminder_sent") == 1);
        f.setStudentName(rs.getString("student_name"));
        f.setAdmissionNo(rs.getString("admission_no"));
        f.setStudentMobile(rs.getString("student_mobile"));
        f.setParentMobile(rs.getString("parent_mobile"));
        return f;
    }

    private static void close(Connection con) {
        if (con != null) {
            try { con.setAutoCommit(true); } catch (SQLException ignore) { }
            try { con.close(); } catch (SQLException ignore) { }
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }

    private static void setNullableDate(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.DATE);
            return;
        }
        try {
            ps.setDate(idx, java.sql.Date.valueOf(val.trim()));
        } catch (IllegalArgumentException e) {
            ps.setNull(idx, Types.DATE);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
