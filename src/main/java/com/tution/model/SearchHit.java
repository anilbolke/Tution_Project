package com.tution.model;

/**
 * One row of the global search result — deliberately flat, because a lead and a
 * student are shown side by side in the same list and the JSP should not care
 * which table a row came from.
 */
public class SearchHit {

    /** "LEAD" or "STUDENT". */
    private String type;
    private int    id;
    private String name;
    private String mobile;
    private String parentMobile;
    private String courseOrClass;
    private String status;          // lead status, or "ADMITTED" for a student
    private String reference;       // admission no for a student, source for a lead
    private String counsellorName;
    private String createdAt;

    public boolean isLead()    { return "LEAD".equals(type); }
    public boolean isStudent() { return "STUDENT".equals(type); }

    public String getType()               { return type; }
    public void   setType(String v)       { this.type = v; }

    public int    getId()                 { return id; }
    public void   setId(int v)            { this.id = v; }

    public String getName()               { return name; }
    public void   setName(String v)       { this.name = v; }

    public String getMobile()             { return mobile; }
    public void   setMobile(String v)     { this.mobile = v; }

    public String getParentMobile()       { return parentMobile; }
    public void   setParentMobile(String v) { this.parentMobile = v; }

    public String getCourseOrClass()      { return courseOrClass; }
    public void   setCourseOrClass(String v) { this.courseOrClass = v; }

    public String getStatus()             { return status; }
    public void   setStatus(String v)     { this.status = v; }

    public String getReference()          { return reference; }
    public void   setReference(String v)  { this.reference = v; }

    public String getCounsellorName()     { return counsellorName; }
    public void   setCounsellorName(String v) { this.counsellorName = v; }

    public String getCreatedAt()          { return createdAt; }
    public void   setCreatedAt(String v)  { this.createdAt = v; }
}
