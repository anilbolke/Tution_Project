package com.tution.model;

/** A scheduled demo / trial class for a lead, and the feedback it produced. */
public class LeadDemo {

    private int    demoId;
    private int    inquiryId;
    private String demoDate;
    private String demoTime;
    private String facultyName;
    private String subject;
    private String mode;          // Online / Offline
    private String status;        // SCHEDULED / COMPLETED / NO_SHOW / CANCELLED
    private String feedback;
    private Integer rating;       // 1..5, null until feedback is captured
    private boolean reminderSent;
    private String createdBy;
    private String createdAt;

    // joined from inquiries, for the "today's demos" list
    private String leadName;
    private String leadMobile;
    private String counsellorName;

    public boolean isOpen() {
        return "SCHEDULED".equalsIgnoreCase(status);
    }

    /** True when a scheduled demo's date has already passed without an outcome. */
    public boolean isMissed(String today) {
        return isOpen() && demoDate != null && demoDate.compareTo(today) < 0;
    }

    /** Star string for the rating, e.g. 4 -> "★★★★☆". */
    public String getStars() {
        if (rating == null || rating < 1) return "—";
        int r = Math.min(5, rating);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r; i++)     sb.append('★');
        for (int i = r; i < 5; i++)     sb.append('☆');
        return sb.toString();
    }

    public int    getDemoId()               { return demoId; }
    public void   setDemoId(int v)          { this.demoId = v; }

    public int    getInquiryId()            { return inquiryId; }
    public void   setInquiryId(int v)       { this.inquiryId = v; }

    public String getDemoDate()             { return demoDate; }
    public void   setDemoDate(String v)     { this.demoDate = v; }

    public String getDemoTime()             { return demoTime; }
    public void   setDemoTime(String v)     { this.demoTime = v; }

    public String getFacultyName()          { return facultyName; }
    public void   setFacultyName(String v)  { this.facultyName = v; }

    public String getSubject()              { return subject; }
    public void   setSubject(String v)      { this.subject = v; }

    public String getMode()                 { return mode; }
    public void   setMode(String v)         { this.mode = v; }

    public String getStatus()               { return status; }
    public void   setStatus(String v)       { this.status = v; }

    public String getFeedback()             { return feedback; }
    public void   setFeedback(String v)     { this.feedback = v; }

    public Integer getRating()              { return rating; }
    public void    setRating(Integer v)     { this.rating = v; }

    public boolean isReminderSent()         { return reminderSent; }
    public void    setReminderSent(boolean v) { this.reminderSent = v; }

    public String getCreatedBy()            { return createdBy; }
    public void   setCreatedBy(String v)    { this.createdBy = v; }

    public String getCreatedAt()            { return createdAt; }
    public void   setCreatedAt(String v)    { this.createdAt = v; }

    public String getLeadName()             { return leadName; }
    public void   setLeadName(String v)     { this.leadName = v; }

    public String getLeadMobile()           { return leadMobile; }
    public void   setLeadMobile(String v)   { this.leadMobile = v; }

    public String getCounsellorName()         { return counsellorName; }
    public void   setCounsellorName(String v) { this.counsellorName = v; }
}
