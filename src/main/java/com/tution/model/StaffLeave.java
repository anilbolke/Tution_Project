package com.tution.model;

/** A leave request and what was decided about it. */
public class StaffLeave {

    public static final String PENDING   = "PENDING";
    public static final String APPROVED  = "APPROVED";
    public static final String REJECTED  = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    public static final String UNPAID = "UNPAID";

    private int    leaveId;
    private int    userId;
    private String staffName;
    private String role;
    private String leaveType;
    private String fromDate;
    private String toDate;
    private double days;
    private String reason;
    private String status;
    private String decisionNote;
    private String decidedBy;
    private String decidedAt;
    private String appliedBy;
    private String createdAt;

    public boolean isPending()  { return PENDING.equals(status); }
    public boolean isApproved() { return APPROVED.equals(status); }

    /** Approved UNPAID leave is the only kind that reduces pay. */
    public boolean isUnpaid()   { return UNPAID.equals(leaveType); }

    public String getTypeLabel() {
        if (leaveType == null) return "";
        switch (leaveType) {
            case "CASUAL": return "Casual";
            case "SICK":   return "Sick";
            case UNPAID:   return "Unpaid";
            default:       return "Other";
        }
    }

    public String getStatusLabel() {
        if (status == null) return "";
        switch (status) {
            case PENDING:   return "Pending";
            case APPROVED:  return "Approved";
            case REJECTED:  return "Rejected";
            case CANCELLED: return "Cancelled";
            default:        return status;
        }
    }

    public int    getLeaveId()              { return leaveId; }
    public void   setLeaveId(int v)         { this.leaveId = v; }
    public int    getUserId()               { return userId; }
    public void   setUserId(int v)          { this.userId = v; }
    public String getStaffName()            { return staffName; }
    public void   setStaffName(String v)    { this.staffName = v; }
    public String getRole()                 { return role; }
    public void   setRole(String v)         { this.role = v; }
    public String getLeaveType()            { return leaveType; }
    public void   setLeaveType(String v)    { this.leaveType = v; }
    public String getFromDate()             { return fromDate; }
    public void   setFromDate(String v)     { this.fromDate = v; }
    public String getToDate()               { return toDate; }
    public void   setToDate(String v)       { this.toDate = v; }
    public double getDays()                 { return days; }
    public void   setDays(double v)         { this.days = v; }
    public String getReason()               { return reason; }
    public void   setReason(String v)       { this.reason = v; }
    public String getStatus()               { return status; }
    public void   setStatus(String v)       { this.status = v; }
    public String getDecisionNote()         { return decisionNote; }
    public void   setDecisionNote(String v) { this.decisionNote = v; }
    public String getDecidedBy()            { return decidedBy; }
    public void   setDecidedBy(String v)    { this.decidedBy = v; }
    public String getDecidedAt()            { return decidedAt; }
    public void   setDecidedAt(String v)    { this.decidedAt = v; }
    public String getAppliedBy()            { return appliedBy; }
    public void   setAppliedBy(String v)    { this.appliedBy = v; }
    public String getCreatedAt()            { return createdAt; }
    public void   setCreatedAt(String v)    { this.createdAt = v; }
}
