package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * One exam fee receipt: money a candidate paid towards sitting an exam.
 *
 * DELIBERATELY NOT A {@link Payment}. A {@code Payment} belongs to a student and
 * settles tuition fees; this belongs to an exam CANDIDATE, who is a lead
 * (exam_candidates -> inquiries) and usually has no students row at all. Keeping
 * the two apart is what stops exam income leaking into the tuition collection
 * figures, the outstanding calculation and counsellor revenue targets.
 *
 * Receipt series are separate for the same reason: EXM-xxxx here, RCPT-xxxx for
 * tuition, so the two books never share a number.
 */
public class ExamPayment {

    public static final String ACTIVE = "ACTIVE";
    public static final String VOID   = "VOID";

    private int        examPaymentId;
    private int        candidateId;
    private String     receiptNo;
    private BigDecimal amount = Money.ZERO;
    private String     paymentMode;
    private String     paymentDate;
    private String     txnRef;
    private Integer    schoolReceiptId;      // set when part of a bulk school payment
    private String     remarks;
    private String     status = ACTIVE;
    private String     voidReason;
    private String     voidedAt;
    private String     collectedBy;
    private Integer    collectedById;
    private String     createdAt;

    /* ── joined, for display on a receipt or list ── */
    private String rollNo;
    private String candidateName;
    private String examName;
    private String schoolName;

    public boolean isVoid() { return VOID.equals(status); }

    public int getExamPaymentId()             { return examPaymentId; }
    public void setExamPaymentId(int v)       { this.examPaymentId = v; }

    public int getCandidateId()               { return candidateId; }
    public void setCandidateId(int v)         { this.candidateId = v; }

    public String getReceiptNo()              { return receiptNo; }
    public void setReceiptNo(String v)        { this.receiptNo = v; }

    public BigDecimal getAmount()             { return amount; }
    public void setAmount(BigDecimal v)       { this.amount = Money.of(v); }

    public String getPaymentMode()            { return paymentMode; }
    public void setPaymentMode(String v)      { this.paymentMode = v; }

    public String getPaymentDate()            { return paymentDate; }
    public void setPaymentDate(String v)      { this.paymentDate = v; }

    public String getTxnRef()                 { return txnRef; }
    public void setTxnRef(String v)           { this.txnRef = v; }

    public Integer getSchoolReceiptId()       { return schoolReceiptId; }
    public void setSchoolReceiptId(Integer v) { this.schoolReceiptId = v; }

    public String getRemarks()                { return remarks; }
    public void setRemarks(String v)          { this.remarks = v; }

    public String getStatus()                 { return status; }
    public void setStatus(String v)           { this.status = v; }

    public String getVoidReason()             { return voidReason; }
    public void setVoidReason(String v)       { this.voidReason = v; }

    public String getVoidedAt()               { return voidedAt; }
    public void setVoidedAt(String v)         { this.voidedAt = v; }

    public String getCollectedBy()            { return collectedBy; }
    public void setCollectedBy(String v)      { this.collectedBy = v; }

    public Integer getCollectedById()         { return collectedById; }
    public void setCollectedById(Integer v)   { this.collectedById = v; }

    public String getCreatedAt()              { return createdAt; }
    public void setCreatedAt(String v)        { this.createdAt = v; }

    public String getRollNo()                 { return rollNo; }
    public void setRollNo(String v)           { this.rollNo = v; }

    public String getCandidateName()          { return candidateName; }
    public void setCandidateName(String v)    { this.candidateName = v; }

    public String getExamName()               { return examName; }
    public void setExamName(String v)         { this.examName = v; }

    public String getSchoolName()             { return schoolName; }
    public void setSchoolName(String v)       { this.schoolName = v; }
}
