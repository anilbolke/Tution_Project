package com.tution.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tution.dao.ExpenseDAO;
import com.tution.dao.FundDAO;
import com.tution.dao.VendorDAO;
import com.tution.dao.WorkOrderDAO;
import com.tution.model.Expense;
import com.tution.model.FundAccount;
import com.tution.model.FundTransaction;
import com.tution.model.User;
import com.tution.model.Vendor;
import com.tution.model.WorkOrder;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * Paying money out: the rules that keep the fund, the vendor and the work order
 * telling the same story.
 *
 * Three guards matter here, and all three are enforced on the server rather than
 * in the form, because a hand-crafted POST bypasses a dropdown:
 *
 *   1. A work order can only be paid by the vendor it belongs to.
 *   2. A work order cannot be paid more than it is worth.
 *   3. The voucher and the fund debit commit together, or neither does.
 */
public class ExpenseService {

    /** Thrown for something a person needs to fix; the message is shown as-is. */
    public static class ExpenseException extends Exception {
        private static final long serialVersionUID = 1L;
        public ExpenseException(String msg) { super(msg); }
    }

    private final ExpenseDAO   dao       = new ExpenseDAO();
    private final FundDAO      fundDao   = new FundDAO();
    private final VendorDAO    vendorDao = new VendorDAO();
    private final WorkOrderDAO woDao     = new WorkOrderDAO();
    private final FundService  funds     = new FundService();

    /** What a saved voucher did, so the caller can say something useful. */
    public static class Result {
        public Expense    expense;
        public BigDecimal fundBalance = Money.ZERO;
        public boolean    overdrawn;
        public WorkOrder  order;              // null when no work order was named
        public BigDecimal orderRemaining = Money.ZERO;
        public boolean    orderSettled;
    }

    /**
     * Records an expense and takes the money out of the fund.
     *
     * OVER-PAYMENT IS REFUSED, AND THE CHECK IS TAKEN UNDER A LOCK. The client's
     * own numbers: an order of Rs. 20,000 with Rs. 15,000 paid has Rs. 5,000 left,
     * so Rs. 6,000 is refused. Two people trying at the same moment would both
     * read Rs. 5,000 remaining if the balance were read on a separate connection,
     * and both would be allowed. The read happens inside this transaction behind
     * {@code SELECT ... FOR UPDATE} on the order, so the second one waits and
     * then sees the money that has just gone out.
     */
    public Result record(int fundId, String rawAmount, String rawTax, String date,
                         int categoryId, int vendorId, int workOrderId, String mode,
                         String txnRef, String invoiceNo, String description, User by)
            throws ExpenseException, SQLException {

        FundAccount fund = requireFund(fundId);

        BigDecimal amount = Money.parse(rawAmount);
        if (amount == null) {
            throw new ExpenseException("\"" + (rawAmount == null ? "" : rawAmount.trim())
                                     + "\" is not a valid amount.");
        }
        if (amount.signum() <= 0) throw new ExpenseException("The amount must be more than zero.");

        BigDecimal tax = blank(rawTax) ? Money.ZERO : Money.parse(rawTax);
        if (tax == null) {
            throw new ExpenseException("\"" + rawTax.trim() + "\" is not a valid tax amount.");
        }
        if (tax.signum() < 0) throw new ExpenseException("Tax cannot be negative.");
        if (tax.compareTo(amount) > 0) {
            // Tax is the portion inside the amount, not an addition to it.
            throw new ExpenseException("The tax of " + Money.rs(tax) + " is more than the "
                    + "expense itself. Tax is the part of the amount that is tax, not an "
                    + "extra on top - enter the full amount paid in Amount.");
        }
        String on = requireDate(date);

        Vendor    vendor = null;
        WorkOrder order  = null;

        if (workOrderId > 0) {
            order = woDao.findById(workOrderId);
            if (order == null) throw new ExpenseException("That work order no longer exists.");

            // THE CROSS-VENDOR BLOCK. Without this, a hand-crafted POST can book
            // Vendor A's payment against Vendor B's order and both vendors'
            // balances become fiction.
            if (vendorId <= 0) vendorId = order.getVendorId();
            else if (vendorId != order.getVendorId()) {
                Vendor named = vendorDao.findById(vendorId);
                throw new ExpenseException(order.getWoNo() + " belongs to "
                        + order.getVendorName() + ", not "
                        + (named == null ? "that vendor" : named.getName())
                        + ". An expense can only settle a work order raised on the same vendor.");
            }
            if (!order.canAcceptPayment()) {
                throw new ExpenseException("Nothing can be paid against " + order.getWoNo()
                        + " while it is " + order.getStatusLabel().toLowerCase()
                        + (order.isDraft() ? " - issue it first, which is the approval step."
                                           : "."));
            }
        }

        if (vendorId > 0) {
            vendor = vendorDao.findById(vendorId);
            if (vendor == null) throw new ExpenseException("That vendor no longer exists.");
        }

        Expense x = new Expense();
        x.setFundId(fundId);
        x.setExpenseDate(on);
        x.setCategoryId(categoryId > 0 ? Integer.valueOf(categoryId) : null);
        x.setVendorId(vendorId    > 0 ? Integer.valueOf(vendorId)    : null);
        x.setWorkOrderId(workOrderId > 0 ? Integer.valueOf(workOrderId) : null);
        x.setAmount(amount);
        x.setTaxAmount(tax);
        x.setPaymentMode(blank(mode) ? "Cash" : mode.trim());
        x.setTxnRef(blank(txnRef) ? null : txnRef.trim());
        x.setInvoiceNo(blank(invoiceNo) ? null : invoiceNo.trim());
        x.setDescription(blank(description) ? null : description.trim());
        if (by != null) {
            x.setCreatedBy(by.getFullName());
            x.setCreatedById(by.getUserId());
        }

        Result result = new Result();
        Connection con = null;
        boolean auto = true;
        try {
            con  = DBConnection.getConnection();
            auto = con.getAutoCommit();
            con.setAutoCommit(false);

            if (order != null) {
                BigDecimal paid = dao.paidOnOrderForUpdate(con, workOrderId);
                if (paid == null) throw new ExpenseException("That work order no longer exists.");
                BigDecimal room = Money.of(order.getOrderValue()).subtract(paid);
                if (room.signum() <= 0) {
                    throw new ExpenseException(order.getWoNo() + " is already fully paid ("
                            + Money.rs(paid) + " of " + Money.rs(order.getOrderValue()) + ").");
                }
                if (amount.compareTo(room) > 0) {
                    throw new ExpenseException("That is more than " + order.getWoNo()
                            + " has left. " + Money.rs(paid) + " of "
                            + Money.rs(order.getOrderValue()) + " is already paid, so "
                            + Money.rs(room) + " remains - enter that or less.");
                }
                result.orderRemaining = room.subtract(amount);
                result.orderSettled   = result.orderRemaining.signum() == 0;
            }

            dao.insert(con, x);

            funds.debit(con, fundId, amount, on, FundTransaction.SRC_EXPENSE,
                        Integer.valueOf(x.getExpenseId()), narration(x, vendor, order), by);

            con.commit();
        } catch (ExpenseException | SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(auto); } catch (SQLException ignore) { }
                try { con.close(); }            catch (SQLException ignore) { }
            }
        }

        if (vendor != null) x.setVendorName(vendor.getName());
        if (order  != null) { x.setWoNo(order.getWoNo()); x.setWoTitle(order.getTitle()); }

        FundAccount after = fundDao.find(fundId);
        result.expense     = x;
        result.order       = order;
        result.fundBalance = after == null ? fund.getBalance() : after.getBalance();
        result.overdrawn   = result.fundBalance.signum() < 0;
        return result;
    }

    /**
     * Cancels a voucher and puts the money back.
     *
     * The row is kept and marked VOID, and a REVERSAL credit goes onto the fund -
     * the balance corrects itself while both the payment and its reversal stay
     * visible on the statement. Deleting the row would make the money vanish from
     * the record while still having left the building.
     *
     * The flip to VOID is what decides the race: only the caller whose UPDATE
     * actually changed the row posts the credit, so a double submission cannot
     * refund the fund twice.
     */
    public Expense voidExpense(int expenseId, String reason, User by)
            throws ExpenseException, SQLException {

        if (blank(reason)) {
            // An unexplained reversal is indistinguishable from money going missing.
            throw new ExpenseException("Please give a reason for cancelling this voucher.");
        }
        Expense x = dao.findById(expenseId);
        if (x == null)   throw new ExpenseException("That voucher no longer exists.");
        if (x.isVoid())  throw new ExpenseException(x.getVoucherNo() + " is already cancelled.");

        Connection con = null;
        boolean auto = true;
        try {
            con  = DBConnection.getConnection();
            auto = con.getAutoCommit();
            con.setAutoCommit(false);

            if (!dao.voidExpense(con, expenseId, reason.trim(),
                                 by == null ? null : by.getUserId())) {
                con.rollback();
                throw new ExpenseException(x.getVoucherNo()
                        + " has already been cancelled by somebody else.");
            }

            funds.credit(con, x.getFundId(), x.getAmount(), LocalDate.now().toString(),
                         FundTransaction.SRC_REVERSAL, Integer.valueOf(expenseId),
                         "Reversal of " + x.getVoucherNo()
                             + (blank(x.getVendorName()) ? "" : " to " + x.getVendorName())
                             + " - " + reason.trim(),
                         by);
            con.commit();
        } catch (ExpenseException | SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(auto); } catch (SQLException ignore) { }
                try { con.close(); }            catch (SQLException ignore) { }
            }
        }
        x.setStatus(Expense.VOID);
        x.setVoidReason(reason.trim());
        return x;
    }

    /** The sentence shown after a voucher is saved - it says what it settled. */
    public String savedMessage(Result r) {
        Expense x = r.expense;
        StringBuilder sb = new StringBuilder();
        sb.append(x.getVoucherNo()).append(": ").append(Money.rs(x.getAmount())).append(" paid");
        if (x.getVendorName() != null) sb.append(" to ").append(x.getVendorName());
        if (r.order != null) {
            sb.append(" against ").append(r.order.getWoNo());
            sb.append(r.orderSettled
                    ? ", which is now fully paid."
                    : " — " + Money.rs(r.orderRemaining) + " of it still to pay.");
        } else {
            sb.append('.');
        }
        sb.append(" Fund balance is now ").append(Money.rs(r.fundBalance)).append('.');
        if (r.overdrawn) {
            sb.append(" THE FUND IS OVERDRAWN — it needs a top-up.");
        }
        return sb.toString();
    }

    /* ─────────────────────── helpers ─────────────────────── */

    /** What the fund statement will read. It has to stand alone months later. */
    private String narration(Expense x, Vendor v, WorkOrder w) {
        StringBuilder sb = new StringBuilder();
        if (v != null) sb.append(v.getName());
        if (w != null) sb.append(sb.length() > 0 ? " - " : "").append(w.getWoNo());
        if (x.getDescription() != null) sb.append(sb.length() > 0 ? " - " : "").append(x.getDescription());
        if (sb.length() == 0) sb.append("Expense ").append(x.getVoucherNo());
        return sb.length() > 240 ? sb.substring(0, 240) : sb.toString();
    }

    private FundAccount requireFund(int fundId) throws ExpenseException, SQLException {
        if (fundId <= 0) throw new ExpenseException("Choose the fund this is paid from.");
        FundAccount f = fundDao.find(fundId);
        if (f == null)     throw new ExpenseException("That fund no longer exists.");
        if (!f.isActive()) throw new ExpenseException("\"" + f.getName() + "\" is closed.");
        return f;
    }

    private String requireDate(String date) throws ExpenseException {
        if (blank(date)) return LocalDate.now().toString();
        try {
            return LocalDate.parse(date.trim()).toString();
        } catch (RuntimeException e) {
            throw new ExpenseException("\"" + date.trim() + "\" is not a valid date.");
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
