package com.tution.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.tution.dao.ExamPaymentDAO;
import com.tution.model.CandidateFee;
import com.tution.model.ExamPayment;
import com.tution.model.SchoolReceipt;
import com.tution.model.User;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * Splitting one school cheque across many candidates.
 *
 * The scholarship pipeline is school-driven: a school sends its candidate list,
 * the tuition imports it, and then the school pays for the whole list with a
 * single instrument. That money has to become one receipt per child or no
 * individual candidate ever reads as paid - and it has to happen all at once,
 * because a half-applied cheque is worse than an unapplied one.
 */
public class SchoolPaymentService {

    private final ExamPaymentDAO dao = new ExamPaymentDAO();

    /** What a proposed split would do, before anything is written. */
    public static class Allocation {
        public final CandidateFee candidate;
        public final BigDecimal   amount;
        Allocation(CandidateFee c, BigDecimal a) { this.candidate = c; this.amount = a; }
    }

    /** The outcome of a completed split, for the confirmation message. */
    public static class Result {
        public SchoolReceipt    receipt;
        public List<Allocation> allocations = new ArrayList<>();
        public List<CandidateFee> skipped   = new ArrayList<>();  // already settled
        public int        settled;                                 // now fully paid
        public BigDecimal shortfall = Money.ZERO;                  // owed but uncovered
    }

    /**
     * Takes a school payment and splits it across the ticked candidates.
     *
     * ALLOCATION IS SEQUENTIAL BY ROLL NUMBER, NOT PRO-RATA. Each candidate is
     * filled to their full balance in turn until the money runs out. A cheque
     * that is short by one fee therefore leaves 29 children settled and one to
     * chase, which the office can act on - where spreading the shortfall evenly
     * would leave all 30 part-paid and nobody settled.
     *
     * Over-payment is refused for the same reason it is at the counter: there is
     * no such thing as exam-fee credit, so money beyond the total owed has
     * nowhere to sit.
     */
    public Result collect(int examId, String school, List<Integer> candidateIds, String rawTotal,
                          String date, String mode, String ref, String remarks, User by)
            throws ExamFeeService.FeeException, SQLException {

        if (candidateIds == null || candidateIds.isEmpty()) {
            throw new ExamFeeService.FeeException("Tick at least one candidate to pay for.");
        }
        BigDecimal total = Money.parse(rawTotal);
        if (total == null) {
            throw new ExamFeeService.FeeException("\"" + (rawTotal == null ? "" : rawTotal.trim())
                                                + "\" is not a valid amount.");
        }
        if (total.signum() <= 0) {
            throw new ExamFeeService.FeeException("The amount must be more than zero.");
        }
        String on = requireDate(date);

        List<CandidateFee> picked = dao.findByIds(candidateIds);   // already in roll order
        if (picked.isEmpty()) {
            throw new ExamFeeService.FeeException("None of those candidates could be found.");
        }

        Result result = new Result();
        List<CandidateFee> owing = new ArrayList<>();
        BigDecimal capacity = Money.ZERO;

        for (CandidateFee c : picked) {
            // A candidate from another exam would put the cheque against the
            // wrong exam's totals, so it is refused rather than quietly skipped.
            if (c.getExamId() != examId) {
                throw new ExamFeeService.FeeException("Roll " + c.getRollNo() + " belongs to "
                        + c.getExamName() + ", not the exam being paid for.");
            }
            if (c.getBalance().signum() == 0) { result.skipped.add(c); continue; }
            owing.add(c);
            capacity = capacity.add(c.getBalance());
        }

        if (owing.isEmpty()) {
            throw new ExamFeeService.FeeException("Every candidate ticked is already settled or "
                    + "waived, so there is nothing to allocate this payment to.");
        }
        if (total.compareTo(capacity) > 0) {
            throw new ExamFeeService.FeeException("That is " + Money.rs(total.subtract(capacity))
                    + " more than the " + Money.rs(capacity) + " owed by the "
                    + owing.size() + " candidate(s) ticked. Reduce the amount or tick more candidates.");
        }

        BigDecimal left = total;
        for (CandidateFee c : owing) {
            if (left.signum() == 0) {
                result.shortfall = result.shortfall.add(c.getBalance());
                continue;
            }
            BigDecimal give = c.getBalance().compareTo(left) <= 0 ? c.getBalance() : left;
            result.allocations.add(new Allocation(c, give));
            if (give.compareTo(c.getBalance()) == 0) result.settled++;
            else result.shortfall = result.shortfall.add(c.getBalance().subtract(give));
            left = left.subtract(give);
        }

        SchoolReceipt r = new SchoolReceipt();
        r.setExamId(examId);
        r.setSchoolName(blank(school) ? "(not stated)" : school.trim());
        r.setTotalAmount(total);
        r.setPaymentMode(blank(mode) ? "Cheque" : mode.trim());
        r.setPaymentDate(on);
        r.setTxnRef(blank(ref) ? null : ref.trim());
        r.setRemarks(blank(remarks) ? null : remarks.trim());
        if (by != null) {
            r.setCollectedBy(by.getFullName());
            r.setCollectedById(by.getUserId());
        }

        // One transaction: the cheque and every split it became, or nothing.
        Connection con = null;
        boolean auto = true;
        try {
            con = DBConnection.getConnection();
            auto = con.getAutoCommit();
            con.setAutoCommit(false);

            dao.insertSchoolReceipt(con, r);

            for (Allocation a : result.allocations) {
                ExamPayment p = new ExamPayment();
                p.setCandidateId(a.candidate.getCandidateId());
                p.setAmount(a.amount);
                p.setPaymentDate(on);
                p.setPaymentMode(r.getPaymentMode());
                p.setTxnRef(r.getTxnRef());
                p.setSchoolReceiptId(r.getSchoolReceiptId());
                p.setRemarks("School payment " + r.getReceiptNo()
                           + (blank(remarks) ? "" : " - " + remarks.trim()));
                p.setCollectedBy(r.getCollectedBy());
                p.setCollectedById(r.getCollectedById());
                dao.insert(con, p);
            }
            con.commit();
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(auto); } catch (SQLException ignore) { }
                try { con.close(); }            catch (SQLException ignore) { }
            }
        }

        r.setAllocated(total);
        r.setCandidateCount(result.allocations.size());
        result.receipt = r;
        return result;
    }

    private String requireDate(String date) throws ExamFeeService.FeeException {
        if (blank(date)) return LocalDate.now().toString();
        try {
            return LocalDate.parse(date.trim()).toString();
        } catch (RuntimeException e) {
            throw new ExamFeeService.FeeException("\"" + date.trim() + "\" is not a valid date.");
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
