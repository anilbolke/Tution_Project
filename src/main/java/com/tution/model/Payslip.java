package com.tution.model;

import java.math.BigDecimal;

/** One employee's pay for one month. */
public class Payslip {

    public static final String DRAFT     = "DRAFT";
    public static final String PAID      = "PAID";
    public static final String CANCELLED = "CANCELLED";

    private static final String[] MONTHS = {
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };

    private int        payslipId;
    private int        userId;
    private String     staffName;
    private String     role;
    private int        periodYear;
    private int        periodMonth;
    private BigDecimal monthlyCtc;
    private int        monthDays;
    private double     payableDays;
    private double     absentDays;
    private double     unpaidDays;
    private BigDecimal gross;
    private BigDecimal deductions;
    private BigDecimal netPay;
    private String     note;
    private String     status;
    private Integer    fundId;
    private String     fundName;
    private Integer    txnId;
    private String     paidOn;
    private String     paidBy;
    private String     createdBy;

    public boolean isDraft() { return DRAFT.equals(status); }
    public boolean isPaid()  { return PAID.equals(status); }

    public String getPeriodLabel() {
        if (periodMonth < 1 || periodMonth > 12) return String.valueOf(periodYear);
        return MONTHS[periodMonth] + " " + periodYear;
    }

    public String getStatusLabel() {
        if (status == null) return "";
        switch (status) {
            case DRAFT:     return "Draft";
            case PAID:      return "Paid";
            case CANCELLED: return "Cancelled";
            default:        return status;
        }
    }

    public int        getPayslipId()             { return payslipId; }
    public void       setPayslipId(int v)        { this.payslipId = v; }
    public int        getUserId()                { return userId; }
    public void       setUserId(int v)           { this.userId = v; }
    public String     getStaffName()             { return staffName; }
    public void       setStaffName(String v)     { this.staffName = v; }
    public String     getRole()                  { return role; }
    public void       setRole(String v)          { this.role = v; }
    public int        getPeriodYear()            { return periodYear; }
    public void       setPeriodYear(int v)       { this.periodYear = v; }
    public int        getPeriodMonth()           { return periodMonth; }
    public void       setPeriodMonth(int v)      { this.periodMonth = v; }
    public BigDecimal getMonthlyCtc()            { return monthlyCtc; }
    public void       setMonthlyCtc(BigDecimal v){ this.monthlyCtc = v; }
    public int        getMonthDays()             { return monthDays; }
    public void       setMonthDays(int v)        { this.monthDays = v; }
    public double     getPayableDays()           { return payableDays; }
    public void       setPayableDays(double v)   { this.payableDays = v; }
    public double     getAbsentDays()            { return absentDays; }
    public void       setAbsentDays(double v)    { this.absentDays = v; }
    public double     getUnpaidDays()            { return unpaidDays; }
    public void       setUnpaidDays(double v)    { this.unpaidDays = v; }
    public BigDecimal getGross()                 { return gross; }
    public void       setGross(BigDecimal v)     { this.gross = v; }
    public BigDecimal getDeductions()            { return deductions; }
    public void       setDeductions(BigDecimal v){ this.deductions = v; }
    public BigDecimal getNetPay()                { return netPay; }
    public void       setNetPay(BigDecimal v)    { this.netPay = v; }
    public String     getNote()                  { return note; }
    public void       setNote(String v)          { this.note = v; }
    public String     getStatus()                { return status; }
    public void       setStatus(String v)        { this.status = v; }
    public Integer    getFundId()                { return fundId; }
    public void       setFundId(Integer v)       { this.fundId = v; }
    public String     getFundName()              { return fundName; }
    public void       setFundName(String v)      { this.fundName = v; }
    public Integer    getTxnId()                 { return txnId; }
    public void       setTxnId(Integer v)        { this.txnId = v; }
    public String     getPaidOn()                { return paidOn; }
    public void       setPaidOn(String v)        { this.paidOn = v; }
    public String     getPaidBy()                { return paidBy; }
    public void       setPaidBy(String v)        { this.paidBy = v; }
    public String     getCreatedBy()             { return createdBy; }
    public void       setCreatedBy(String v)     { this.createdBy = v; }
}
