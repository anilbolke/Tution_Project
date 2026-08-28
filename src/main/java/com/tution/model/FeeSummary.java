package com.tution.model;

/** Per-student fee position used by the fee list. */
public class FeeSummary {

    // ── added with the fee ledger ──
    private int    concession;   // discount + scholarship
    private String plan;         // FULL / INSTALLMENT / EMI

    public int  getConcession()        { return concession; }
    public void setConcession(int v)   { this.concession = v; }

    public String getPlan()            { return plan; }
    public void   setPlan(String v)    { this.plan = v; }


    private int    studentId;
    private String admissionNo;
    private String fullName;
    private String className;
    private String slabLabel;
    private int    totalFee;
    private int    paid;
    private int    outstanding;
    private String status;   // PAID / PARTIAL / PENDING

    public int    getStudentId()             { return studentId; }
    public void   setStudentId(int v)        { this.studentId = v; }

    public String getAdmissionNo()           { return admissionNo; }
    public void   setAdmissionNo(String v)   { this.admissionNo = v; }

    public String getFullName()              { return fullName; }
    public void   setFullName(String v)      { this.fullName = v; }

    public String getClassName()             { return className; }
    public void   setClassName(String v)     { this.className = v; }

    public String getSlabLabel()             { return slabLabel; }
    public void   setSlabLabel(String v)     { this.slabLabel = v; }

    public int    getTotalFee()              { return totalFee; }
    public void   setTotalFee(int v)         { this.totalFee = v; }

    public int    getPaid()                  { return paid; }
    public void   setPaid(int v)             { this.paid = v; }

    public int    getOutstanding()           { return outstanding; }
    public void   setOutstanding(int v)      { this.outstanding = v; }

    public String getStatus()                { return status; }
    public void   setStatus(String v)        { this.status = v; }
}
