package com.tution.model;

/**
 * One item in the reminder queue — a follow-up, a demo or a fee instalment that
 * is due and has not been messaged yet.
 *
 * Flat by design: the queue page shows all three kinds in one list and should
 * not care which table a row came from.
 */
public class Reminder {

    /** FOLLOWUP / DEMO / FEE_DUE — matches the reminder_log.kind enum. */
    private String  kind;
    /** inquiry_id / demo_id / installment_id, depending on kind. */
    private int     refId;
    private Integer inquiryId;      // for the "open the lead" link, when there is one
    private Integer studentId;      // for the "open the student" link, when there is one

    private String name;
    private String mobile;
    private String parentMobile;
    private String dueDate;
    private String detail;          // course / faculty / amount, shown in the list
    private String counsellorName;

    private int    amount;          // FEE_DUE only
    private boolean alreadySent;

    /** The number a reminder goes to: parent first, since they pay the fees. */
    public String targetMobile() {
        if (parentMobile != null && !parentMobile.trim().isEmpty()) {
            return parentMobile.trim();
        }
        return (mobile == null) ? "" : mobile.trim();
    }

    public boolean isOverdue(String today) {
        return dueDate != null && dueDate.compareTo(today) < 0;
    }

    public String getIcon() {
        if ("DEMO".equals(kind))    return "🎓";
        if ("FEE_DUE".equals(kind)) return "💰";
        return "📞";
    }

    public String getKindLabel() {
        if ("DEMO".equals(kind))    return "Demo";
        if ("FEE_DUE".equals(kind)) return "Fee due";
        return "Follow-up";
    }

    public String getKind()                 { return kind; }
    public void   setKind(String v)         { this.kind = v; }

    public int    getRefId()                { return refId; }
    public void   setRefId(int v)           { this.refId = v; }

    public Integer getInquiryId()           { return inquiryId; }
    public void    setInquiryId(Integer v)  { this.inquiryId = v; }

    public Integer getStudentId()           { return studentId; }
    public void    setStudentId(Integer v)  { this.studentId = v; }

    public String getName()                 { return name; }
    public void   setName(String v)         { this.name = v; }

    public String getMobile()               { return mobile; }
    public void   setMobile(String v)       { this.mobile = v; }

    public String getParentMobile()         { return parentMobile; }
    public void   setParentMobile(String v) { this.parentMobile = v; }

    public String getDueDate()              { return dueDate; }
    public void   setDueDate(String v)      { this.dueDate = v; }

    public String getDetail()               { return detail; }
    public void   setDetail(String v)       { this.detail = v; }

    public String getCounsellorName()         { return counsellorName; }
    public void   setCounsellorName(String v) { this.counsellorName = v; }

    public int    getAmount()               { return amount; }
    public void   setAmount(int v)          { this.amount = v; }

    public boolean isAlreadySent()          { return alreadySent; }
    public void    setAlreadySent(boolean v){ this.alreadySent = v; }
}
