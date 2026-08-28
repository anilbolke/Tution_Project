package com.tution.model;

/** A support ticket / concern raised by a student to the management. */
public class Ticket {

    private int    ticketId;
    private int    studentId;
    private String category;
    private String subject;
    private String message;
    private String status;     // OPEN / IN_PROGRESS / RESOLVED / CLOSED
    private String priority;   // LOW / NORMAL / HIGH
    private String createdAt;
    private String updatedAt;

    // joined / computed (for staff list + headers)
    private String studentName;
    private String admissionNo;
    private String className;
    private int    replyCount;

    public int    getTicketId()            { return ticketId; }
    public void   setTicketId(int v)       { this.ticketId = v; }
    public int    getStudentId()           { return studentId; }
    public void   setStudentId(int v)      { this.studentId = v; }
    public String getCategory()            { return category; }
    public void   setCategory(String v)    { this.category = v; }
    public String getSubject()             { return subject; }
    public void   setSubject(String v)     { this.subject = v; }
    public String getMessage()             { return message; }
    public void   setMessage(String v)     { this.message = v; }
    public String getStatus()              { return status; }
    public void   setStatus(String v)      { this.status = v; }
    public String getPriority()            { return priority; }
    public void   setPriority(String v)    { this.priority = v; }
    public String getCreatedAt()           { return createdAt; }
    public void   setCreatedAt(String v)   { this.createdAt = v; }
    public String getUpdatedAt()           { return updatedAt; }
    public void   setUpdatedAt(String v)   { this.updatedAt = v; }

    public String getStudentName()         { return studentName; }
    public void   setStudentName(String v) { this.studentName = v; }
    public String getAdmissionNo()         { return admissionNo; }
    public void   setAdmissionNo(String v) { this.admissionNo = v; }
    public String getClassName()           { return className; }
    public void   setClassName(String v)   { this.className = v; }
    public int    getReplyCount()          { return replyCount; }
    public void   setReplyCount(int v)     { this.replyCount = v; }
}
