package com.tution.model;

/** A single fee payment / receipt. */
public class Payment {

    private int    paymentId;
    private int    studentId;
    private String receiptNo;
    private int    amount;
    private String paymentMode;
    private String paymentDate;   // yyyy-MM-dd
    private String remarks;
    private String collectedBy;
    private String createdAt;
    private String razorpayOrderId;
    private String razorpayPaymentId;

    // ── ledger links (added with the fee ledger) ──
    private Integer installmentId;   // the installment this payment was applied to
    private Integer collectedById;   // real user reference; collectedBy stays for legacy rows
    private String  txnRef;          // cheque no / UPI ref / bank reference

    // joined student fields (for receipt / history display)
    private String studentName;
    private String studentMobile;
    private String admissionNo;
    private String className;
    private String feeSlab;

    public int    getPaymentId()             { return paymentId; }
    public void   setPaymentId(int v)        { this.paymentId = v; }

    public int    getStudentId()             { return studentId; }
    public void   setStudentId(int v)        { this.studentId = v; }

    public String getReceiptNo()             { return receiptNo; }
    public void   setReceiptNo(String v)     { this.receiptNo = v; }

    public int    getAmount()                { return amount; }
    public void   setAmount(int v)           { this.amount = v; }

    public String getPaymentMode()           { return paymentMode; }
    public void   setPaymentMode(String v)   { this.paymentMode = v; }

    public String getPaymentDate()           { return paymentDate; }
    public void   setPaymentDate(String v)   { this.paymentDate = v; }

    public String getRemarks()               { return remarks; }
    public void   setRemarks(String v)       { this.remarks = v; }

    public String getCollectedBy()           { return collectedBy; }
    public void   setCollectedBy(String v)   { this.collectedBy = v; }

    public String getCreatedAt()             { return createdAt; }
    public void   setCreatedAt(String v)     { this.createdAt = v; }

    public String getStudentName()           { return studentName; }
    public void   setStudentName(String v)   { this.studentName = v; }

    public String getStudentMobile()         { return studentMobile; }
    public void   setStudentMobile(String v) { this.studentMobile = v; }

    public String getAdmissionNo()           { return admissionNo; }
    public void   setAdmissionNo(String v)   { this.admissionNo = v; }

    public String getClassName()             { return className; }
    public void   setClassName(String v)     { this.className = v; }

    public String getFeeSlab()               { return feeSlab; }
    public void   setFeeSlab(String v)       { this.feeSlab = v; }

    public String getRazorpayOrderId()           { return razorpayOrderId; }
    public void   setRazorpayOrderId(String v)   { this.razorpayOrderId = v; }

    public String getRazorpayPaymentId()         { return razorpayPaymentId; }
    public void   setRazorpayPaymentId(String v) { this.razorpayPaymentId = v; }

    public Integer getInstallmentId()            { return installmentId; }
    public void    setInstallmentId(Integer v)   { this.installmentId = v; }

    public Integer getCollectedById()            { return collectedById; }
    public void    setCollectedById(Integer v)   { this.collectedById = v; }

    public String getTxnRef()                    { return txnRef; }
    public void   setTxnRef(String v)            { this.txnRef = v; }
}
