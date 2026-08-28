package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * One candidate's fee position: what they owe for the exam, what they have paid,
 * and therefore where they stand.
 *
 * NOTHING HERE IS STORED. There is no paid_status column on exam_candidates.
 * Payable comes from the exam's fee (or a per-candidate override, or zero if
 * waived) and paid is the sum of that candidate's live receipts, so this can
 * never disagree with the money actually taken - which a status column would,
 * the first time a receipt was voided.
 */
public class CandidateFee {

    /** Where a candidate stands. */
    public static final String PAID    = "PAID";
    public static final String PARTIAL = "PARTIAL";
    public static final String UNPAID  = "UNPAID";
    public static final String WAIVED  = "WAIVED";
    /** The exam has no fee set yet - a different statement from "waived". */
    public static final String NO_FEE  = "NO_FEE";

    private int     candidateId;
    private int     examId;
    private String  rollNo;
    private String  candidateName;
    private String  mobile;
    private String  parentMobile;
    private String  schoolName;
    private String  className;
    private String  examName;
    private String  examType;
    private String  examDate;
    private String  candidateStatus;      // REGISTERED / APPEARED / ...

    private BigDecimal examFee    = Money.ZERO;   // the exam's list price
    private BigDecimal feeAmount;                 // per-candidate override, or null
    private boolean    feeWaived;
    private String     waiverReason;

    private BigDecimal paid = Money.ZERO;         // sum of ACTIVE receipts
    private int        receiptCount;

    /** What this candidate is actually due to pay. */
    public BigDecimal getPayable() {
        if (feeWaived) return Money.ZERO;
        return feeAmount != null ? Money.of(feeAmount) : Money.of(examFee);
    }

    /** Never negative: an over-payment is not a negative balance. */
    public BigDecimal getBalance() {
        BigDecimal b = getPayable().subtract(Money.of(paid));
        return b.signum() < 0 ? Money.ZERO : b;
    }

    /** True when more was taken than was due - worth showing, never hiding. */
    public boolean isOverPaid() {
        return getPayable().subtract(Money.of(paid)).signum() < 0;
    }

    /**
     * The single place a fee state is decided.
     *
     * WAIVED and NO_FEE are kept apart on purpose. Both leave nothing to pay,
     * but "this child was given a free seat" and "nobody has set a price for
     * this exam yet" need completely different follow-up, and collapsing them
     * would quietly report an unpriced exam as fully settled.
     */
    public String getState() {
        if (feeWaived)                    return WAIVED;
        if (getPayable().signum() == 0)   return NO_FEE;
        if (Money.of(paid).compareTo(getPayable()) >= 0) return PAID;
        if (Money.of(paid).signum() > 0)  return PARTIAL;
        return UNPAID;
    }

    public String getStateLabel() {
        switch (getState()) {
            case PAID:    return "Paid";
            case PARTIAL: return "Part paid";
            case UNPAID:  return "Unpaid";
            case WAIVED:  return "Waived";
            case NO_FEE:  return "No fee set";
            default:      return getState();
        }
    }

    /** True when this candidate still owes money. */
    public boolean isDue() {
        String s = getState();
        return UNPAID.equals(s) || PARTIAL.equals(s);
    }

    public int getCandidateId()               { return candidateId; }
    public void setCandidateId(int v)         { this.candidateId = v; }

    public int getExamId()                    { return examId; }
    public void setExamId(int v)              { this.examId = v; }

    public String getRollNo()                 { return rollNo; }
    public void setRollNo(String v)           { this.rollNo = v; }

    public String getCandidateName()          { return candidateName; }
    public void setCandidateName(String v)    { this.candidateName = v; }

    public String getMobile()                 { return mobile; }
    public void setMobile(String v)           { this.mobile = v; }

    public String getParentMobile()           { return parentMobile; }
    public void setParentMobile(String v)     { this.parentMobile = v; }

    public String getSchoolName()             { return schoolName; }
    public void setSchoolName(String v)       { this.schoolName = v; }

    public String getClassName()              { return className; }
    public void setClassName(String v)        { this.className = v; }

    public String getExamName()               { return examName; }
    public void setExamName(String v)         { this.examName = v; }

    public String getExamType()               { return examType; }
    public void setExamType(String v)         { this.examType = v; }

    public String getExamDate()               { return examDate; }
    public void setExamDate(String v)         { this.examDate = v; }

    public String getCandidateStatus()        { return candidateStatus; }
    public void setCandidateStatus(String v)  { this.candidateStatus = v; }

    public BigDecimal getExamFee()            { return examFee; }
    public void setExamFee(BigDecimal v)      { this.examFee = Money.of(v); }

    public BigDecimal getFeeAmount()          { return feeAmount; }
    public void setFeeAmount(BigDecimal v)    { this.feeAmount = v == null ? null : Money.of(v); }

    public boolean isFeeWaived()              { return feeWaived; }
    public void setFeeWaived(boolean v)       { this.feeWaived = v; }

    public String getWaiverReason()           { return waiverReason; }
    public void setWaiverReason(String v)     { this.waiverReason = v; }

    public BigDecimal getPaid()               { return paid; }
    public void setPaid(BigDecimal v)         { this.paid = Money.of(v); }

    public int getReceiptCount()              { return receiptCount; }
    public void setReceiptCount(int v)        { this.receiptCount = v; }
}
