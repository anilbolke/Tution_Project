package com.tution.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tution.util.Money;

/**
 * One payment from a school covering many candidates.
 *
 * Schools send the candidate list and then pay for the whole list with a single
 * cheque, so the money arrives as one instrument and has to end up as N separate
 * candidate receipts - otherwise no individual child ever shows as paid. This row
 * is the cheque; the {@link ExamPayment} rows pointing back at it are how it was
 * split.
 */
public class SchoolReceipt {

    private int        schoolReceiptId;
    private int        examId;
    private String     schoolName;
    private String     receiptNo;          // SCH-2627-0007
    private BigDecimal totalAmount = Money.ZERO;
    private String     paymentMode = "Cheque";
    private String     paymentDate;
    private String     txnRef;
    private String     remarks;
    private String     collectedBy;
    private Integer    collectedById;
    private String     createdAt;

    // Joined / derived for display - never stored.
    private String            examName;
    private int               candidateCount;
    private BigDecimal        allocated = Money.ZERO;   // sum of the LIVE splits
    private List<ExamPayment> splits    = new ArrayList<>();

    /**
     * What is left of this cheque after cancelled splits are taken off.
     *
     * Voiding one candidate's split does not send money back to the school - it
     * frees it up, and the difference between the cheque and the live splits is
     * exactly how much is sitting unallocated. Showing it stops that money
     * quietly disappearing.
     */
    public BigDecimal getUnallocated() {
        BigDecimal left = Money.of(totalAmount).subtract(Money.of(allocated));
        return left.signum() < 0 ? Money.ZERO : left;
    }

    public boolean isFullyAllocated() { return getUnallocated().signum() == 0; }

    public int getSchoolReceiptId()            { return schoolReceiptId; }
    public void setSchoolReceiptId(int v)      { this.schoolReceiptId = v; }

    public int getExamId()                     { return examId; }
    public void setExamId(int v)               { this.examId = v; }

    public String getSchoolName()              { return schoolName; }
    public void setSchoolName(String v)        { this.schoolName = v; }

    public String getReceiptNo()               { return receiptNo; }
    public void setReceiptNo(String v)         { this.receiptNo = v; }

    public BigDecimal getTotalAmount()         { return totalAmount; }
    public void setTotalAmount(BigDecimal v)   { this.totalAmount = Money.of(v); }

    public String getPaymentMode()             { return paymentMode; }
    public void setPaymentMode(String v)       { this.paymentMode = v; }

    public String getPaymentDate()             { return paymentDate; }
    public void setPaymentDate(String v)       { this.paymentDate = v; }

    public String getTxnRef()                  { return txnRef; }
    public void setTxnRef(String v)            { this.txnRef = v; }

    public String getRemarks()                 { return remarks; }
    public void setRemarks(String v)           { this.remarks = v; }

    public String getCollectedBy()             { return collectedBy; }
    public void setCollectedBy(String v)       { this.collectedBy = v; }

    public Integer getCollectedById()          { return collectedById; }
    public void setCollectedById(Integer v)    { this.collectedById = v; }

    public String getCreatedAt()               { return createdAt; }
    public void setCreatedAt(String v)         { this.createdAt = v; }

    public String getExamName()                { return examName; }
    public void setExamName(String v)          { this.examName = v; }

    public int getCandidateCount()             { return candidateCount; }
    public void setCandidateCount(int v)       { this.candidateCount = v; }

    public BigDecimal getAllocated()           { return allocated; }
    public void setAllocated(BigDecimal v)     { this.allocated = Money.of(v); }

    public List<ExamPayment> getSplits()       { return splits; }
    public void setSplits(List<ExamPayment> v) { this.splits = v == null ? new ArrayList<ExamPayment>() : v; }
}
