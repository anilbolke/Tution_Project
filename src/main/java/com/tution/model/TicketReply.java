package com.tution.model;

/** One message in a ticket conversation (from the student or from staff). */
public class TicketReply {

    private int    replyId;
    private int    ticketId;
    private String sender;       // STUDENT / STAFF
    private String senderName;
    private String message;
    private String createdAt;

    public int    getReplyId()           { return replyId; }
    public void   setReplyId(int v)      { this.replyId = v; }
    public int    getTicketId()          { return ticketId; }
    public void   setTicketId(int v)     { this.ticketId = v; }
    public String getSender()            { return sender; }
    public void   setSender(String v)    { this.sender = v; }
    public String getSenderName()        { return senderName; }
    public void   setSenderName(String v){ this.senderName = v; }
    public String getMessage()           { return message; }
    public void   setMessage(String v)   { this.message = v; }
    public String getCreatedAt()         { return createdAt; }
    public void   setCreatedAt(String v) { this.createdAt = v; }

    public boolean isStaff() { return "STAFF".equalsIgnoreCase(sender); }
}
