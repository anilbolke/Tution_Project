package com.tution.model;

/**
 * A student's negotiated fee — the record that replaced the hardcoded
 * {@code FeeCalculator} formula.
 *
 * Before this existed, every student on the same slab was billed an identical
 * amount computed in Java, so there was nowhere to record a concession. Amounts
 * are whole rupees (int) to match the rest of the app; the column is
 * DECIMAL(10,2) only so partial payments cannot round oddly in SQL sums.
 */
public class StudentFee {

    private int feeId;
    private int studentId;
    private String planCode;         // fee_plans.code the student was sold
    private int courseFee;
    private int registrationFee;
    private int materialFee;
    private int discount;
    private int scholarship;
    private int netPayable;       // COURSE fee after concession — GST and the PP split apply to this
    private double gstRate;
    private int gstAmount;
    private int totalPayable;     // registration + net course + GST
    private boolean registrationPaid;
    private String plan;          // FULL / INSTALLMENT / EMI
    private String approvedBy;
    private String remarks;
    private String updatedAt;

    /**
     * The COURSE fee at list price, before any concession.
     *
     * Registration is deliberately excluded: the brochure states it is not part
     * of the course fee, it is never discounted, and no GST is charged on it.
     * Including it here would both double-count it against the schedule and tax
     * it.
     */
    public int getGross() {
        return courseFee + materialFee;
    }

    /** Discount + scholarship combined. */
    public int getTotalConcession() {
        return discount + scholarship;
    }

    /**
     * Recomputes netPayable, GST and the total from the components.
     *
     * Registration is added to the total but never discounted — the brochure
     * makes it non-refundable and excluded from the course fee.
     */
    public void recalc() {
        this.netPayable = Math.max(0, getGross() - getTotalConcession());
        this.gstAmount  = (int) Math.round(netPayable * gstRate / 100.0);
        this.totalPayable = registrationFee + netPayable + gstAmount;
    }

    public String getPlanCode()            { return planCode; }
    public void   setPlanCode(String v)    { this.planCode = v; }

    public double getGstRate()             { return gstRate; }
    public void   setGstRate(double v)     { this.gstRate = v; }

    public int  getGstAmount()             { return gstAmount; }
    public void setGstAmount(int v)        { this.gstAmount = v; }

    public int  getTotalPayable()          { return totalPayable; }
    public void setTotalPayable(int v)     { this.totalPayable = v; }

    public boolean isRegistrationPaid()      { return registrationPaid; }
    public void    setRegistrationPaid(boolean v) { this.registrationPaid = v; }

    public int  getFeeId()                 { return feeId; }
    public void setFeeId(int v)            { this.feeId = v; }

    public int  getStudentId()             { return studentId; }
    public void setStudentId(int v)        { this.studentId = v; }

    public int  getCourseFee()             { return courseFee; }
    public void setCourseFee(int v)        { this.courseFee = v; }

    public int  getRegistrationFee()       { return registrationFee; }
    public void setRegistrationFee(int v)  { this.registrationFee = v; }

    public int  getMaterialFee()           { return materialFee; }
    public void setMaterialFee(int v)      { this.materialFee = v; }

    public int  getDiscount()              { return discount; }
    public void setDiscount(int v)         { this.discount = v; }

    public int  getScholarship()           { return scholarship; }
    public void setScholarship(int v)      { this.scholarship = v; }

    public int  getNetPayable()            { return netPayable; }
    public void setNetPayable(int v)       { this.netPayable = v; }

    public String getPlan()                { return plan; }
    public void   setPlan(String v)        { this.plan = v; }

    public String getApprovedBy()          { return approvedBy; }
    public void   setApprovedBy(String v)  { this.approvedBy = v; }

    public String getRemarks()             { return remarks; }
    public void   setRemarks(String v)     { this.remarks = v; }

    public String getUpdatedAt()           { return updatedAt; }
    public void   setUpdatedAt(String v)   { this.updatedAt = v; }
}
