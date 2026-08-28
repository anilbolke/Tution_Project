package com.tution.model;

/**
 * One counselling touchpoint on a lead — a call, a WhatsApp message, a visit.
 *
 * Follow-ups are append-only: the lead row carries the CURRENT next-follow-up
 * date and status, while this table keeps the history of how it got there. That
 * separation is what makes counsellor-performance reporting possible later.
 */
public class LeadFollowup {

    private int    followupId;
    private int    inquiryId;
    private Integer counsellorId;
    private String counsellorName;
    private String commType;        // Call / WhatsApp / Email / Visit / Demo Discussion
    private String discussion;      // call notes / discussion summary
    private String objection;       // objection handling notes
    private String outcomeStatus;   // status the lead moved to on this touchpoint
    private String nextActionDate;
    private String createdAt;

    /** Leads the timeline icon off the communication type. */
    public String getIcon() {
        if (commType == null) return "📝";
        switch (commType) {
            case "Call":            return "📞";
            case "WhatsApp":        return "💬";
            case "Email":           return "✉️";
            case "Visit":           return "🏫";
            case "Demo Discussion": return "🎓";
            default:                return "📝";
        }
    }

    public int    getFollowupId()            { return followupId; }
    public void   setFollowupId(int v)       { this.followupId = v; }

    public int    getInquiryId()             { return inquiryId; }
    public void   setInquiryId(int v)        { this.inquiryId = v; }

    public Integer getCounsellorId()          { return counsellorId; }
    public void    setCounsellorId(Integer v) { this.counsellorId = v; }

    public String getCounsellorName()         { return counsellorName; }
    public void   setCounsellorName(String v) { this.counsellorName = v; }

    public String getCommType()              { return commType; }
    public void   setCommType(String v)      { this.commType = v; }

    public String getDiscussion()            { return discussion; }
    public void   setDiscussion(String v)    { this.discussion = v; }

    public String getObjection()             { return objection; }
    public void   setObjection(String v)     { this.objection = v; }

    public String getOutcomeStatus()         { return outcomeStatus; }
    public void   setOutcomeStatus(String v) { this.outcomeStatus = v; }

    public String getNextActionDate()        { return nextActionDate; }
    public void   setNextActionDate(String v){ this.nextActionDate = v; }

    public String getCreatedAt()             { return createdAt; }
    public void   setCreatedAt(String v)     { this.createdAt = v; }
}
