package com.tution.model;

/** One employee's attendance mark for one day. */
public class StaffAttendance {

    public static final String PRESENT  = "PRESENT";
    public static final String ABSENT   = "ABSENT";
    public static final String HALF_DAY = "HALF_DAY";
    public static final String LEAVE    = "LEAVE";
    public static final String HOLIDAY  = "HOLIDAY";
    public static final String WEEK_OFF = "WEEK_OFF";

    private int    attId;
    private int    userId;
    private String staffName;
    private String role;
    private String attDate;
    private String status;
    private String remarks;
    private String markedBy;

    /**
     * What this day is worth in pay.
     *
     * A week off and a declared holiday count a full day rather than being left
     * out: nobody is docked for a Sunday. Absent is worth nothing, a half day
     * half. Unpaid leave is deducted from the leave record, not from here.
     */
    public double payableValue() {
        if (ABSENT.equals(status))   return 0.0;
        if (HALF_DAY.equals(status)) return 0.5;
        return 1.0;
    }

    public String getLabel() {
        if (status == null) return "";
        switch (status) {
            case PRESENT:  return "Present";
            case ABSENT:   return "Absent";
            case HALF_DAY: return "Half day";
            case LEAVE:    return "Leave";
            case HOLIDAY:  return "Holiday";
            case WEEK_OFF: return "Week off";
            default:       return status;
        }
    }

    public int    getAttId()             { return attId; }
    public void   setAttId(int v)        { this.attId = v; }
    public int    getUserId()            { return userId; }
    public void   setUserId(int v)       { this.userId = v; }
    public String getStaffName()         { return staffName; }
    public void   setStaffName(String v) { this.staffName = v; }
    public String getRole()              { return role; }
    public void   setRole(String v)      { this.role = v; }
    public String getAttDate()           { return attDate; }
    public void   setAttDate(String v)   { this.attDate = v; }
    public String getStatus()            { return status; }
    public void   setStatus(String v)    { this.status = v; }
    public String getRemarks()           { return remarks; }
    public void   setRemarks(String v)   { this.remarks = v; }
    public String getMarkedBy()          { return markedBy; }
    public void   setMarkedBy(String v)  { this.markedBy = v; }
}
