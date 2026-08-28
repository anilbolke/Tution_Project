package com.tution.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tution.dao.FundDAO;
import com.tution.model.FundAccount;
import com.tution.model.FundAudit;
import com.tution.model.FundTransaction;
import com.tution.model.User;
import com.tution.util.Money;

/**
 * The rules around moving money in and out of a fund. The DAO knows how to
 * write a row; this decides whether it should be written at all.
 *
 * WHY A SPENDING FUND DOES NOT HARD-BLOCK AT ZERO. Posting an expense that
 * takes the fund negative is allowed and shown in red rather than refused. The
 * balance here trails reality: a top-up handed over in cash this morning may not
 * be keyed in until the afternoon, and refusing a genuine, already-paid expense
 * because of that lag would push people into recording it somewhere outside the
 * system - which is exactly the outcome this module exists to prevent. An
 * overdrawn fund is a reporting signal, not an error.
 *
 * What IS refused is arithmetic that cannot be right at all: a zero or negative
 * amount, an unreadable amount, a missing date, or a fund that does not exist.
 */
public class FundService {

    /** Thrown for input a human needs to fix; the message is shown as-is. */
    public static class FundException extends Exception {
        private static final long serialVersionUID = 1L;
        public FundException(String msg) { super(msg); }
    }

    private final FundDAO dao = new FundDAO();

    /* ───────────────────────── top-up ───────────────────────── */

    /**
     * Credits a fund. Returns the new balance.
     *
     * @param rawAmount the amount exactly as typed, so "1,00,000" and "Rs 5000"
     *                  both work and an unreadable value is reported rather than
     *                  silently treated as zero.
     */
    public BigDecimal topUp(int fundId, String rawAmount, String date, String mode,
                            String ref, String narration, User by)
            throws FundException, SQLException {

        FundAccount fund = requireFund(fundId);
        BigDecimal amount = requireAmount(rawAmount);
        String on = requireDate(date);

        FundTransaction t = new FundTransaction();
        t.setFundId(fund.getFundId());
        t.setTxnDate(on);
        t.setDirection(FundTransaction.CREDIT);
        t.setAmount(amount);
        t.setSourceType(FundTransaction.SRC_TOPUP);
        t.setNarration(blankToNull(narration) == null ? "Fund top-up" : narration.trim());
        t.setPaymentMode(blankToNull(mode));
        t.setTxnRef(blankToNull(ref));
        stamp(t, by);

        dao.post(t);
        return dao.balance(fundId);
    }

    /**
     * A correction that is not a top-up and not an expense - a counting error in
     * the cash box, an opening figure that was keyed in wrong. Kept separate
     * from TOPUP so the two are distinguishable on a statement forever after.
     */
    public BigDecimal adjust(int fundId, String rawAmount, String direction, String date,
                             String narration, User by)
            throws FundException, SQLException {

        FundAccount fund = requireFund(fundId);
        BigDecimal amount = requireAmount(rawAmount);
        String on = requireDate(date);

        if (!FundTransaction.CREDIT.equals(direction) && !FundTransaction.DEBIT.equals(direction)) {
            throw new FundException("An adjustment must be either a credit or a debit.");
        }
        if (blankToNull(narration) == null) {
            // An unexplained adjustment is indistinguishable from a mistake.
            throw new FundException("Please give a reason for the adjustment.");
        }

        FundTransaction t = new FundTransaction();
        t.setFundId(fund.getFundId());
        t.setTxnDate(on);
        t.setDirection(direction);
        t.setAmount(amount);
        t.setSourceType(FundTransaction.SRC_ADJUSTMENT);
        t.setNarration(narration.trim());
        stamp(t, by);

        dao.post(t);
        return dao.balance(fundId);
    }

    /* ─────────────────── the fund's own lifecycle ─────────────────── */

    /**
     * Deletes a fund, but ONLY one that has never been used.
     *
     * A fund with a single credit, debit or expense voucher against it is not
     * deletable and must not be: removing it would take its whole statement
     * with it, and this module's premise is that money once recorded stays
     * recorded. The database enforces that too — the foreign keys on
     * fund_transactions and expenses would refuse — but failing here means the
     * person gets a sentence explaining what to do instead of a constraint
     * violation.
     *
     * The realistic case for deleting is the one this allows: a fund typed in
     * by mistake a minute ago, with nothing behind it.
     *
     * @return the name of the fund that was removed
     */
    public String deleteFund(int fundId, String reason, User by)
            throws FundException, SQLException {

        if (fundId <= 0) throw new FundException("Please choose a fund.");
        FundAccount f = dao.find(fundId);
        if (f == null)  throw new FundException("That fund no longer exists.");

        int txns     = f.getTxnCount();
        int vouchers = dao.expenseCount(fundId);
        if (txns > 0 || vouchers > 0) {
            throw new FundException(
                "\"" + f.getName() + "\" cannot be deleted: it has "
              + countPhrase(txns, "ledger entry", "ledger entries")
              + " and " + countPhrase(vouchers, "expense voucher", "expense vouchers")
              + " against it. Deleting it would take that history with it. "
              + "Close the fund instead — the statement stays readable and "
              + "nothing new can be posted to it.");
        }

        boolean gone = dao.deleteFund(f, blankToNull(reason), name(by), id(by));
        if (!gone) throw new FundException("That fund no longer exists.");
        return f.getName();
    }

    /**
     * Renames a fund. Allowed at any time, history or not.
     *
     * Nothing references a fund by name — every ledger row, voucher and report
     * joins on fund_id — so this changes no figure anywhere. That is precisely
     * why a mis-named fund should be renamed rather than deleted and recreated:
     * recreating would strand its statement on a fund nobody can reach.
     *
     * A closed fund can still be renamed. Correcting the label on something you
     * have stopped using is a reasonable thing to want, and it moves no money.
     *
     * @return the name it had before
     */
    public String renameFund(int fundId, String newName, String reason, User by)
            throws FundException, SQLException {

        if (fundId <= 0) throw new FundException("Please choose a fund.");
        FundAccount f = dao.find(fundId);
        if (f == null)  throw new FundException("That fund no longer exists.");

        String name = blankToNull(newName);
        if (name == null)          throw new FundException("A fund needs a name.");
        if (name.length() > 80)    throw new FundException("That name is too long — 80 characters at most.");
        if (name.equals(f.getName())) {
            throw new FundException("\"" + name + "\" is already its name.");
        }

        boolean done = dao.renameFund(f, name, blankToNull(reason), name(by), id(by));
        if (!done) throw new FundException("That fund no longer exists.");
        return f.getName();
    }

    /**
     * Closes a fund (or reopens it), which is what "getting rid of" a fund with
     * history actually means: the statement stays, nothing new can be posted.
     *
     * @return the fund, as it was before the change
     */
    public FundAccount setActive(int fundId, boolean active, String reason, User by)
            throws FundException, SQLException {

        if (fundId <= 0) throw new FundException("Please choose a fund.");
        FundAccount f = dao.find(fundId);
        if (f == null)  throw new FundException("That fund no longer exists.");
        if (f.isActive() == active) {
            throw new FundException("\"" + f.getName() + "\" is already "
                                  + (active ? "open." : "closed."));
        }

        dao.setActive(fundId, active);
        dao.audit(fundId, active ? FundAudit.REOPEN : FundAudit.CLOSE, f.getName(),
                  f.getBalance(), f.getTxnCount(), blankToNull(reason), name(by), id(by));
        return f;
    }

    /** Records a newly created fund on the trail. */
    public void auditCreate(FundAccount f, User by) throws SQLException {
        dao.audit(f.getFundId(), FundAudit.CREATE, f.getName(),
                  Money.of(f.getOpeningBalance()), 0, null, name(by), id(by));
    }

    /* ──────────────── used by other modules ──────────────── */

    /**
     * Debits a fund inside a transaction the caller already owns - how an
     * expense takes money out.
     *
     * Takes the caller's {@link Connection} on purpose: the expense row and this
     * debit must commit together or not at all. An expense written without its
     * debit leaves the balance overstated and the money available to spend
     * twice.
     */
    public int debit(Connection con, int fundId, BigDecimal amount, String date,
                     String sourceType, Integer sourceId, String narration, User by)
            throws SQLException {
        FundTransaction t = new FundTransaction();
        t.setFundId(fundId);
        t.setTxnDate(date);
        t.setDirection(FundTransaction.DEBIT);
        t.setAmount(amount);
        t.setSourceType(sourceType);
        t.setSourceId(sourceId);
        t.setNarration(narration);
        stamp(t, by);
        return dao.post(con, t);
    }

    /** Credits a fund inside the caller's transaction. Mirror of {@link #debit}. */
    public int credit(Connection con, int fundId, BigDecimal amount, String date,
                      String sourceType, Integer sourceId, String narration, User by)
            throws SQLException {
        FundTransaction t = new FundTransaction();
        t.setFundId(fundId);
        t.setTxnDate(date);
        t.setDirection(FundTransaction.CREDIT);
        t.setAmount(amount);
        t.setSourceType(sourceType);
        t.setSourceId(sourceId);
        t.setNarration(narration);
        stamp(t, by);
        return dao.post(con, t);
    }

    /* ───────────────────────── validation ───────────────────────── */

    private FundAccount requireFund(int fundId) throws FundException, SQLException {
        if (fundId <= 0) throw new FundException("Please choose a fund.");
        FundAccount f = dao.find(fundId);
        if (f == null)     throw new FundException("That fund no longer exists.");
        if (!f.isActive()) throw new FundException("\"" + f.getName() + "\" is closed and cannot be used.");
        return f;
    }

    private BigDecimal requireAmount(String raw) throws FundException {
        BigDecimal v = Money.parse(raw);
        if (v == null) {
            throw new FundException("\"" + (raw == null ? "" : raw.trim())
                                  + "\" is not a valid amount.");
        }
        if (v.signum() <= 0) {
            throw new FundException("The amount must be more than zero.");
        }
        return v;
    }

    /** Blank means today; anything unparseable is an error rather than a guess. */
    private String requireDate(String date) throws FundException {
        if (blankToNull(date) == null) return LocalDate.now().toString();
        try {
            return LocalDate.parse(date.trim()).toString();
        } catch (RuntimeException e) {
            throw new FundException("\"" + date.trim() + "\" is not a valid date.");
        }
    }

    private void stamp(FundTransaction t, User by) {
        if (by == null) return;
        t.setCreatedBy(by.getFullName());
        t.setCreatedById(by.getUserId());
    }

    private String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    /** "no ledger entries" / "1 ledger entry" / "4 ledger entries". */
    private static String countPhrase(int n, String one, String many) {
        if (n == 0) return "no " + many;
        return n + " " + (n == 1 ? one : many);
    }

    private static String  name(User u) { return u == null ? null : u.getFullName(); }
    private static Integer id(User u)   { return u == null ? null : Integer.valueOf(u.getUserId()); }
}
