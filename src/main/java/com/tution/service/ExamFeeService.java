package com.tution.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tution.dao.ExamPaymentDAO;
import com.tution.model.CandidateFee;
import com.tution.model.ExamPayment;
import com.tution.model.User;
import com.tution.util.Money;
import com.tution.util.RollNumber;

/**
 * The rules around taking an exam fee at the counter.
 *
 * The money itself is trivial; the risk is booking it against the wrong child.
 * A roll number is six digits with no name attached, and the counter reads it
 * off a printed hall ticket, so a transposed pair is the realistic mistake -
 * hence the check-digit handling in {@link #lookup(String)}.
 */
public class ExamFeeService {

    /** Thrown for something a person needs to fix; the message is shown as-is. */
    public static class FeeException extends Exception {
        private static final long serialVersionUID = 1L;
        public FeeException(String msg) { super(msg); }
    }

    private final ExamPaymentDAO dao = new ExamPaymentDAO();

    /**
     * Finds the candidate behind a roll number.
     *
     * THE CHECK DIGIT IS A DIAGNOSTIC, NOT A GATE. Roll numbers issued by this
     * system carry a Damm check digit, so a transposed pair fails arithmetic
     * before it can ever reach a candidate. But exam_candidates also holds
     * LEGACY roll numbers imported from the previous system, and those were
     * never built to satisfy it. Rejecting on the checksum alone would make
     * every legacy candidate unpayable.
     *
     * So the lookup is always attempted first, and the checksum is used only to
     * explain a miss: a number that fails BOTH the lookup and the check digit is
     * almost certainly a typo, and saying so is far more useful than "not
     * found".
     */
    public CandidateFee lookup(String rollNo) throws FeeException, SQLException {
        String roll = rollNo == null ? "" : rollNo.trim();
        if (roll.isEmpty()) {
            throw new FeeException("Enter a roll number.");
        }

        CandidateFee found = dao.findByRoll(roll);
        if (found != null) return found;

        if (!RollNumber.isValid(roll)) {
            throw new FeeException("Roll number " + roll + " is not valid - please check for a "
                                 + "mistyped or swapped digit. No payment has been recorded.");
        }
        throw new FeeException("No candidate is registered with roll number " + roll + ".");
    }

    /**
     * Records a fee payment against a candidate and returns the saved receipt.
     *
     * OVER-PAYMENT IS REFUSED. There is no such thing as exam-fee credit: money
     * taken beyond the balance has nowhere to go and would show as a candidate
     * who is somehow more than fully paid. Refusing it at the counter, while the
     * family is still standing there, is the only cheap moment to fix it.
     */
    public ExamPayment collect(int candidateId, String rawAmount, String date, String mode,
                               String ref, String remarks, User by)
            throws FeeException, SQLException {

        CandidateFee cf = dao.findByCandidate(candidateId);
        if (cf == null) throw new FeeException("That candidate no longer exists.");

        BigDecimal amount = requireAmount(rawAmount);
        String on = requireDate(date);

        if (cf.isFeeWaived()) {
            throw new FeeException(cf.getCandidateName() + "'s fee is waived"
                    + (cf.getWaiverReason() == null ? "" : " (" + cf.getWaiverReason() + ")")
                    + ", so there is nothing to collect.");
        }
        if (cf.getPayable().signum() == 0) {
            throw new FeeException("No fee has been set for " + cf.getExamName()
                    + " yet, so there is nothing to collect. Set the exam fee first.");
        }
        if (cf.getBalance().signum() == 0) {
            throw new FeeException(cf.getCandidateName() + " (roll " + cf.getRollNo()
                    + ") has already paid in full.");
        }
        if (amount.compareTo(cf.getBalance()) > 0) {
            throw new FeeException("That is more than is owed. The balance on roll "
                    + cf.getRollNo() + " is " + Money.rs(cf.getBalance())
                    + " - enter that or less.");
        }

        ExamPayment p = new ExamPayment();
        p.setCandidateId(candidateId);
        p.setAmount(amount);
        p.setPaymentDate(on);
        p.setPaymentMode(blank(mode) ? "Cash" : mode.trim());
        p.setTxnRef(blank(ref) ? null : ref.trim());
        p.setRemarks(blank(remarks) ? null : remarks.trim());
        if (by != null) {
            p.setCollectedBy(by.getFullName());
            p.setCollectedById(by.getUserId());
        }

        dao.insert(p);

        // Carried for the receipt so the caller does not have to re-read them.
        p.setRollNo(cf.getRollNo());
        p.setCandidateName(cf.getCandidateName());
        p.setExamName(cf.getExamName());
        p.setSchoolName(cf.getSchoolName());
        return p;
    }

    /** Sets an exam's list price. Zero is allowed - it means "free". */
    public void setExamFee(int examId, String rawFee) throws FeeException, SQLException {
        BigDecimal fee = Money.parse(rawFee);
        if (fee == null) {
            throw new FeeException("\"" + (rawFee == null ? "" : rawFee.trim())
                                 + "\" is not a valid fee.");
        }
        if (fee.signum() < 0) throw new FeeException("A fee cannot be negative.");
        dao.setExamFee(examId, fee);
    }

    /**
     * Gives a candidate a free seat, or takes one back.
     *
     * A WAIVER CANNOT BE APPLIED OVER MONEY ALREADY TAKEN. Waiving a candidate who
     * has paid would drop their payable to zero while their receipts stay live,
     * turning them into an over-payment nobody asked for and no report can
     * explain. Cancel the receipts first, deliberately, or give a concession
     * instead.
     */
    public void waive(int candidateId, String reason, boolean waived)
            throws FeeException, SQLException {

        CandidateFee cf = dao.findByCandidate(candidateId);
        if (cf == null) throw new FeeException("That candidate no longer exists.");

        if (waived) {
            if (blank(reason)) {
                // A free seat with no stated reason is indistinguishable from
                // fee income going missing.
                throw new FeeException("Please give a reason for waiving this fee.");
            }
            if (Money.of(cf.getPaid()).signum() > 0) {
                throw new FeeException(cf.getCandidateName() + " (roll " + cf.getRollNo() + ") has "
                        + "already paid " + Money.rs(cf.getPaid()) + ". Cancel that receipt first "
                        + "if the fee is genuinely being waived, or set a concession amount instead.");
            }
        }
        dao.setWaiver(candidateId, waived, waived ? reason.trim() : null);
    }

    /**
     * Sets one candidate's own fee, overriding the exam price - the discounted
     * seat, the sibling rate, the school that negotiated a rate for its list.
     *
     * A blank amount clears the override and puts them back on the list price.
     * The new figure may not be below what they have already paid, which would
     * manufacture an over-payment out of an edit.
     */
    public void setConcession(int candidateId, String rawFee) throws FeeException, SQLException {
        CandidateFee cf = dao.findByCandidate(candidateId);
        if (cf == null) throw new FeeException("That candidate no longer exists.");

        if (blank(rawFee)) { dao.setCandidateFee(candidateId, null); return; }

        BigDecimal fee = Money.parse(rawFee);
        if (fee == null) {
            throw new FeeException("\"" + rawFee.trim() + "\" is not a valid amount.");
        }
        if (fee.signum() < 0) throw new FeeException("A fee cannot be negative.");
        if (fee.compareTo(Money.of(cf.getPaid())) < 0) {
            throw new FeeException("Roll " + cf.getRollNo() + " has already paid "
                    + Money.rs(cf.getPaid()) + ", so the fee cannot be set below that. "
                    + "Cancel a receipt first if money needs to be given back.");
        }
        dao.setCandidateFee(candidateId, fee);
    }

    /**
     * Cancels a receipt. The row is kept and marked VOID, so the money drops out
     * of the candidate's paid total while the record of it having been taken -
     * and by whom - survives.
     */
    public void voidReceipt(int examPaymentId, String reason, User by)
            throws FeeException, SQLException {
        if (blank(reason)) {
            // An unexplained void is indistinguishable from money going missing.
            throw new FeeException("Please give a reason for cancelling this receipt.");
        }
        boolean done = dao.voidPayment(examPaymentId, reason.trim(),
                                       by == null ? null : by.getUserId());
        if (!done) {
            throw new FeeException("That receipt was not found, or has already been cancelled.");
        }
    }

    /* ─────────────────────── validation ─────────────────────── */

    private BigDecimal requireAmount(String raw) throws FeeException {
        BigDecimal v = Money.parse(raw);
        if (v == null) {
            throw new FeeException("\"" + (raw == null ? "" : raw.trim())
                                 + "\" is not a valid amount.");
        }
        if (v.signum() <= 0) throw new FeeException("The amount must be more than zero.");
        return v;
    }

    private String requireDate(String date) throws FeeException {
        if (blank(date)) return LocalDate.now().toString();
        try {
            return LocalDate.parse(date.trim()).toString();
        } catch (RuntimeException e) {
            throw new FeeException("\"" + date.trim() + "\" is not a valid date.");
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
