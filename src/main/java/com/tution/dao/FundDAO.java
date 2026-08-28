package com.tution.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.FundAccount;
import com.tution.model.FundAudit;
import com.tution.model.FundTransaction;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * The fund ledger: money credited into a fund, money debited out of it, and the
 * balance that falls out of the two.
 *
 * THE BALANCE IS ALWAYS COMPUTED, NEVER STORED. Every read here derives it from
 * fund_transactions. That costs one aggregate query on a table that will hold a
 * few hundred rows a year - free at this size - and in exchange the balance
 * cannot drift, because there is no second copy of it to disagree with the
 * rows.
 *
 * TWO FLAVOURS OF post(). The no-argument-connection version opens and commits
 * its own connection and is what a top-up screen wants. The version taking a
 * {@link Connection} joins a transaction the caller already has open, which is
 * how an expense debits the fund in the SAME transaction that inserts the
 * expense row. Those two writes must never come apart: an expense recorded
 * without its debit would overstate the balance and let the money be spent
 * twice.
 */
public class FundDAO {

    private static final String FUND_COLS =
          "f.fund_id, f.name, f.description, f.opening_balance, f.opening_date, "
        + "f.is_active, f.created_by, f.created_at";

    /**
     * Credits and debits per fund in one pass.
     *
     * Deliberately a LEFT JOIN onto an aggregate sub-select rather than a join
     * straight onto fund_transactions: joining the rows directly and then using
     * SUM would multiply nothing here, but a fund with no transactions at all
     * would still need the LEFT JOIN to appear, and doing it this way keeps the
     * shape identical whether or not there are rows.
     */
    private static final String TOTALS_JOIN =
          " LEFT JOIN (SELECT fund_id, "
        + "                   COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount END), 0) AS cr, "
        + "                   COALESCE(SUM(CASE WHEN direction = 'DEBIT'  THEN amount END), 0) AS dr, "
        + "                   COUNT(*) AS n "
        + "              FROM fund_transactions GROUP BY fund_id) t ON t.fund_id = f.fund_id ";

    /* ───────────────────────── funds ───────────────────────── */

    /** Every fund, balances included. Inactive ones last. */
    public List<FundAccount> all(boolean activeOnly) throws SQLException {
        String sql = "SELECT " + FUND_COLS + ", t.cr, t.dr, t.n FROM fund_accounts f"
                   + TOTALS_JOIN
                   + (activeOnly ? " WHERE f.is_active = 1 " : "")
                   + " ORDER BY f.is_active DESC, f.name";

        List<FundAccount> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapFund(rs));
        }
        return out;
    }

    /** One fund with its balance, or null. */
    public FundAccount find(int fundId) throws SQLException {
        String sql = "SELECT " + FUND_COLS + ", t.cr, t.dr, t.n FROM fund_accounts f"
                   + TOTALS_JOIN + " WHERE f.fund_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, fundId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFund(rs) : null;
            }
        }
    }

    /** Current balance of one fund. Zero if the fund does not exist. */
    public BigDecimal balance(int fundId) throws SQLException {
        FundAccount f = find(fundId);
        return f == null ? Money.ZERO : f.getBalance();
    }

    /**
     * Balance immediately BEFORE {@code date} - the opening figure a statement
     * for that period starts from. Without this the running balance on a filtered
     * statement would start at zero and every line would be wrong.
     */
    public BigDecimal balanceBefore(int fundId, String date) throws SQLException {
        String sql = "SELECT f.opening_balance "
                   + "     + COALESCE((SELECT SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END) "
                   + "                   FROM fund_transactions "
                   + "                  WHERE fund_id = f.fund_id AND txn_date < ?), 0) AS bal "
                   + "  FROM fund_accounts f WHERE f.fund_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, date);
            ps.setInt(2, fundId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.of(rs.getBigDecimal("bal")) : Money.ZERO;
            }
        }
    }

    public int insertFund(FundAccount f) throws SQLException {
        String sql = "INSERT INTO fund_accounts "
                   + "(name, description, opening_balance, opening_date, is_active, created_by) "
                   + "VALUES (?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.getName());
            setNullable(ps, 2, f.getDescription());
            ps.setBigDecimal(3, Money.of(f.getOpeningBalance()));
            setNullable(ps, 4, f.getOpeningDate());
            ps.setInt(5, f.isActive() ? 1 : 0);
            setNullable(ps, 6, f.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    f.setFundId(keys.getInt(1));
                    return f.getFundId();
                }
            }
        }
        return 0;
    }

    public boolean updateFund(FundAccount f) throws SQLException {
        String sql = "UPDATE fund_accounts SET name = ?, description = ?, is_active = ? "
                   + " WHERE fund_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, f.getName());
            setNullable(ps, 2, f.getDescription());
            ps.setInt(3, f.isActive() ? 1 : 0);
            ps.setInt(4, f.getFundId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Opens or closes a fund without touching its name or description. */
    public boolean setActive(int fundId, boolean active) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE fund_accounts SET is_active = ? WHERE fund_id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, fundId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * How many expense vouchers point at this fund.
     *
     * Counted separately from the ledger rows because the two can differ: a
     * VOID expense keeps its row here but its debit has been reversed, so the
     * transaction count alone would not reveal that a voucher still references
     * the fund. Either one being non-zero means the fund has history and must
     * be closed rather than deleted.
     */
    public int expenseCount(int fundId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT COUNT(*) FROM expenses WHERE fund_id = ?")) {
            ps.setInt(1, fundId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Deletes an EMPTY fund and records the deletion, both or neither.
     *
     * The audit row is written FIRST and inside the same transaction. Written
     * afterwards it could be lost to a crash between the two statements, and
     * the one deletion with no trail would be the one nobody could explain.
     *
     * Callers must have established that the fund is empty; if they have not,
     * the foreign keys on fund_transactions and expenses refuse the delete and
     * the whole transaction rolls back — including the audit row, which would
     * otherwise claim a deletion that never happened.
     *
     * @return true if a fund row was actually removed
     */
    public boolean deleteFund(FundAccount f, String reason, String by, Integer byId)
            throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            insertAudit(con, f.getFundId(), FundAudit.DELETE, f.getName(), null,
                        f.getBalance(), f.getTxnCount(), reason, by, byId);

            boolean gone;
            try (PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM fund_accounts WHERE fund_id = ?")) {
                ps.setInt(1, f.getFundId());
                gone = ps.executeUpdate() > 0;
            }

            if (!gone) {
                // Someone else deleted it first; do not leave a trail entry for
                // a deletion this request did not perform.
                con.rollback();
                return false;
            }
            con.commit();
            return true;
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

    /* ───────────────────────── audit trail ───────────────────────── */

    /** Records a lifecycle event on its own connection. */
    public void audit(int fundId, String action, String name, BigDecimal balance,
                      int txnCount, String reason, String by, Integer byId)
            throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            insertAudit(con, fundId, action, name, null, balance, txnCount, reason, by, byId);
        }
    }

    private void insertAudit(Connection con, int fundId, String action, String name,
                             String oldName, BigDecimal balance, int txnCount, String reason,
                             String by, Integer byId) throws SQLException {
        String sql = "INSERT INTO fund_audit "
                   + "(fund_id, action, fund_name, old_name, balance, txn_count, reason, "
                   + " acted_by, acted_by_id) "
                   + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, fundId);
            ps.setString(2, action);
            ps.setString(3, name);
            setNullable(ps, 4, oldName);
            ps.setBigDecimal(5, Money.of(balance));
            ps.setInt(6, txnCount);
            setNullable(ps, 7, reason);
            setNullable(ps, 8, by);
            if (byId == null) ps.setNull(9, Types.INTEGER);
            else              ps.setInt(9, byId);
            ps.executeUpdate();
        }
    }

    /**
     * Renames a fund and records both names, together or not at all.
     *
     * Same ordering argument as {@link #deleteFund}: the trail entry goes in
     * first and inside the transaction, so a duplicate-name rejection rolls the
     * audit row back with it rather than leaving a record of a rename that did
     * not happen.
     *
     * @return true if a fund row was actually renamed
     */
    public boolean renameFund(FundAccount f, String newName, String reason,
                              String by, Integer byId) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            insertAudit(con, f.getFundId(), FundAudit.RENAME, newName, f.getName(),
                        f.getBalance(), f.getTxnCount(), reason, by, byId);

            boolean done;
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE fund_accounts SET name = ? WHERE fund_id = ?")) {
                ps.setString(1, newName);
                ps.setInt(2, f.getFundId());
                done = ps.executeUpdate() > 0;
            }

            if (!done) {
                con.rollback();
                return false;
            }
            con.commit();
            return true;
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
     * The lifecycle history, newest first, across every fund — including funds
     * that have since been deleted, which is the whole point of keeping it.
     */
    public List<FundAudit> auditTrail(int limit) throws SQLException {
        String sql = "SELECT * FROM fund_audit ORDER BY acted_at DESC, audit_id DESC LIMIT ?";
        List<FundAudit> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapAudit(rs));
            }
        }
        return out;
    }

    /* ─────────────────────── transactions ─────────────────────── */

    /**
     * Posts one ledger row on a connection the caller already owns, so it joins
     * the caller's transaction. Does NOT commit - that is the caller's job.
     *
     * This is the entry point an expense uses: the expense insert and its fund
     * debit commit together or not at all.
     */
    public int post(Connection con, FundTransaction t) throws SQLException {
        String sql = "INSERT INTO fund_transactions "
                   + "(fund_id, txn_date, direction, amount, source_type, source_id, "
                   + " narration, payment_mode, txn_ref, created_by, created_by_id) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getFundId());
            ps.setString(2, t.getTxnDate());
            ps.setString(3, t.getDirection());
            ps.setBigDecimal(4, Money.of(t.getAmount()));
            ps.setString(5, t.getSourceType() == null ? FundTransaction.SRC_TOPUP : t.getSourceType());
            if (t.getSourceId() == null) ps.setNull(6, Types.INTEGER);
            else                         ps.setInt(6, t.getSourceId());
            setNullable(ps, 7, t.getNarration());
            setNullable(ps, 8, t.getPaymentMode());
            setNullable(ps, 9, t.getTxnRef());
            setNullable(ps, 10, t.getCreatedBy());
            if (t.getCreatedById() == null) ps.setNull(11, Types.INTEGER);
            else                            ps.setInt(11, t.getCreatedById());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    t.setTxnId(keys.getInt(1));
                    return t.getTxnId();
                }
            }
        }
        return 0;
    }

    /** Posts one ledger row in its own transaction. For top-ups and adjustments. */
    public int post(FundTransaction t) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int id = post(con, t);
                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * Cancels an existing row by posting its mirror image, leaving both on the
     * statement. Nothing is deleted and nothing is edited, so the audit trail
     * survives.
     */
    public int reverse(int txnId, String onDate, String reason, String by, Integer byId)
            throws SQLException {
        FundTransaction orig = findTxn(txnId);
        if (orig == null) return 0;

        FundTransaction r = new FundTransaction();
        r.setFundId(orig.getFundId());
        r.setTxnDate(onDate);
        r.setDirection(orig.isCredit() ? FundTransaction.DEBIT : FundTransaction.CREDIT);
        r.setAmount(orig.getAmount());
        r.setSourceType(FundTransaction.SRC_REVERSAL);
        r.setSourceId(orig.getTxnId());
        r.setNarration("Reversal of txn #" + orig.getTxnId()
                     + (reason == null || reason.trim().isEmpty() ? "" : " - " + reason.trim()));
        r.setCreatedBy(by);
        r.setCreatedById(byId);
        return post(r);
    }

    public FundTransaction findTxn(int txnId) throws SQLException {
        String sql = "SELECT x.*, f.name AS fund_name FROM fund_transactions x "
                   + "  JOIN fund_accounts f ON f.fund_id = x.fund_id "
                   + " WHERE x.txn_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, txnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapTxn(rs) : null;
            }
        }
    }

    /**
     * The statement for one fund over a date range, each row carrying the
     * balance after it.
     *
     * ORDERING IS (txn_date, txn_id) AND NOT DATE ALONE. Several rows commonly
     * share a date; ordering only by date lets MySQL return them in any order it
     * likes, so the running balance would reshuffle between two loads of the
     * same page and the statement would look wrong without being wrong.
     */
    public List<FundTransaction> statement(int fundId, String from, String to) throws SQLException {
        String sql = "SELECT x.*, f.name AS fund_name FROM fund_transactions x "
                   + "  JOIN fund_accounts f ON f.fund_id = x.fund_id "
                   + " WHERE x.fund_id = ? AND x.txn_date BETWEEN ? AND ? "
                   + " ORDER BY x.txn_date, x.txn_id";

        BigDecimal running = balanceBefore(fundId, from);

        List<FundTransaction> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, fundId);
            ps.setString(2, from);
            ps.setString(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FundTransaction t = mapTxn(rs);
                    running = running.add(t.getSignedAmount());
                    t.setRunningBalance(running);
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Most recent rows across every fund - the "recent activity" strip. */
    public List<FundTransaction> recent(int limit) throws SQLException {
        String sql = "SELECT x.*, f.name AS fund_name FROM fund_transactions x "
                   + "  JOIN fund_accounts f ON f.fund_id = x.fund_id "
                   + " ORDER BY x.txn_date DESC, x.txn_id DESC LIMIT ?";
        List<FundTransaction> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapTxn(rs));
            }
        }
        return out;
    }

    /* ───────────────────────── mapping ───────────────────────── */

    private FundAccount mapFund(ResultSet rs) throws SQLException {
        FundAccount f = new FundAccount();
        f.setFundId(rs.getInt("fund_id"));
        f.setName(rs.getString("name"));
        f.setDescription(rs.getString("description"));
        f.setOpeningBalance(rs.getBigDecimal("opening_balance"));
        f.setOpeningDate(rs.getString("opening_date"));
        f.setActive(rs.getInt("is_active") == 1);
        f.setCreatedBy(rs.getString("created_by"));
        f.setCreatedAt(rs.getString("created_at"));
        f.setCredits(rs.getBigDecimal("cr"));   // null for a fund with no rows
        f.setDebits(rs.getBigDecimal("dr"));
        f.setTxnCount(rs.getInt("n"));
        return f;
    }

    private FundTransaction mapTxn(ResultSet rs) throws SQLException {
        FundTransaction t = new FundTransaction();
        t.setTxnId(rs.getInt("txn_id"));
        t.setFundId(rs.getInt("fund_id"));
        t.setFundName(rs.getString("fund_name"));
        t.setTxnDate(rs.getString("txn_date"));
        t.setDirection(rs.getString("direction"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setSourceType(rs.getString("source_type"));
        int sid = rs.getInt("source_id");
        t.setSourceId(rs.wasNull() ? null : sid);
        t.setNarration(rs.getString("narration"));
        t.setPaymentMode(rs.getString("payment_mode"));
        t.setTxnRef(rs.getString("txn_ref"));
        t.setCreatedBy(rs.getString("created_by"));
        int uid = rs.getInt("created_by_id");
        t.setCreatedById(rs.wasNull() ? null : uid);
        t.setCreatedAt(rs.getString("created_at"));
        return t;
    }

    private FundAudit mapAudit(ResultSet rs) throws SQLException {
        FundAudit a = new FundAudit();
        a.setAuditId(rs.getInt("audit_id"));
        a.setFundId(rs.getInt("fund_id"));
        a.setAction(rs.getString("action"));
        a.setFundName(rs.getString("fund_name"));
        a.setOldName(rs.getString("old_name"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setTxnCount(rs.getInt("txn_count"));
        a.setReason(rs.getString("reason"));
        a.setActedBy(rs.getString("acted_by"));
        int uid = rs.getInt("acted_by_id");
        a.setActedById(rs.wasNull() ? null : uid);
        a.setActedAt(rs.getString("acted_at"));
        return a;
    }

    private void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(idx, Types.VARCHAR);
        else                                 ps.setString(idx, v.trim());
    }
}
