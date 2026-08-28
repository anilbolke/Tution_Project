package com.tution.model;

/**
 * A prospective-student enquiry — the lead record that drives the whole sales
 * cycle (enquiry → follow-up → demo → admission).
 *
 * The first five fields plus source/message/status are what the public form at
 * inquiry.jsp has always captured. Everything after that was added for the Sales
 * Module and is optional, so an enquiry created from the public form still saves
 * with exactly the same code path.
 */
public class Inquiry {

    // ── identity ──
    private int    inquiryId;

    // ── student details ──
    private String fullName;
    private String mobile;
    private String parentName;
    private String parentMobile;
    private String email;
    private String dob;
    private String gender;
    private String city;
    private String address;

    // ── academic details ──
    private String currentClass;
    private String prevQualification;
    private String schoolName;
    private String board;
    private String percentage;

    // ── course interest ──
    private String classInterest;      // legacy field, still written by the public form
    private String courseName;
    private String batchPref;
    private String learningMode;       // Online / Offline / Hybrid
    private String branch;
    private String expectedJoinDate;

    // ── enquiry / ownership ──
    private String source;
    private String message;
    private String priority;           // Hot / Warm / Cold — kept in step with leadStage
    /** Controlled list (lead_stages). Replaces Lead Priority on the form; the
     *  first three values are the same Hot / Warm / Cold. */
    private String leadStage;
    /** Controlled list, dependent on {@link #leadStage} (lead_sub_stages). */
    private String leadSubStage;
    private Integer counsellorId;      // null = unassigned
    private String  counsellorName;    // joined from users, not a column
    private String  nextFollowupDate;
    private String  status;

    // ── follow-up information ──
    private String studentRequirements;
    private String parentFeedback;
    private String counsellorRemarks;

    // ── conversion ──
    private Integer convertedStudentId;
    private String  convertedAdmissionNo;   // joined from students, not a column

    private String createdAt;
    private String updatedAt;

    // ── derived, filled by the DAO where useful ──
    private int followupCount;

    /** True once this lead has become a student. */
    public boolean isConverted() { return "CONVERTED".equalsIgnoreCase(status); }

    /** True when the next follow-up date has passed and the lead is still open. */
    public boolean isOverdue(String today) {
        return nextFollowupDate != null && !nextFollowupDate.isEmpty()
            && nextFollowupDate.compareTo(today) < 0
            && !isConverted()
            && !"NOT_INTERESTED".equalsIgnoreCase(status)
            && !"LOST".equalsIgnoreCase(status);
    }

    public int    getInquiryId()             { return inquiryId; }
    public void   setInquiryId(int v)        { this.inquiryId = v; }

    public String getFullName()              { return fullName; }
    public void   setFullName(String v)      { this.fullName = v; }

    public String getMobile()                { return mobile; }
    public void   setMobile(String v)        { this.mobile = v; }

    public String getParentName()            { return parentName; }
    public void   setParentName(String v)    { this.parentName = v; }

    public String getParentMobile()          { return parentMobile; }
    public void   setParentMobile(String v)  { this.parentMobile = v; }

    public String getEmail()                 { return email; }
    public void   setEmail(String v)         { this.email = v; }

    public String getDob()                   { return dob; }
    public void   setDob(String v)           { this.dob = v; }

    public String getGender()                { return gender; }
    public void   setGender(String v)        { this.gender = v; }

    public String getCity()                  { return city; }
    public void   setCity(String v)          { this.city = v; }

    public String getAddress()               { return address; }
    public void   setAddress(String v)       { this.address = v; }

    public String getCurrentClass()          { return currentClass; }
    public void   setCurrentClass(String v)  { this.currentClass = v; }

    public String getPrevQualification()         { return prevQualification; }
    public void   setPrevQualification(String v) { this.prevQualification = v; }

    public String getSchoolName()            { return schoolName; }
    public void   setSchoolName(String v)    { this.schoolName = v; }

    public String getBoard()                 { return board; }
    public void   setBoard(String v)         { this.board = v; }

    public String getPercentage()            { return percentage; }
    public void   setPercentage(String v)    { this.percentage = v; }

    public String getClassInterest()         { return classInterest; }
    public void   setClassInterest(String v) { this.classInterest = v; }

    public String getCourseName()            { return courseName; }
    public void   setCourseName(String v)    { this.courseName = v; }

    public String getBatchPref()             { return batchPref; }
    public void   setBatchPref(String v)     { this.batchPref = v; }

    public String getLearningMode()          { return learningMode; }
    public void   setLearningMode(String v)  { this.learningMode = v; }

    public String getBranch()                { return branch; }
    public void   setBranch(String v)        { this.branch = v; }

    public String getExpectedJoinDate()          { return expectedJoinDate; }
    public void   setExpectedJoinDate(String v)  { this.expectedJoinDate = v; }

    public String getSource()                { return source; }
    public void   setSource(String v)        { this.source = v; }

    public String getMessage()               { return message; }
    public void   setMessage(String v)       { this.message = v; }

    public String getPriority()              { return priority; }
    public void   setPriority(String v)      { this.priority = v; }

    public String getLeadStage()             { return leadStage; }
    public void   setLeadStage(String v)     { this.leadStage = v; }

    public String getLeadSubStage()          { return leadSubStage; }
    public void   setLeadSubStage(String v)  { this.leadSubStage = v; }

    public Integer getCounsellorId()             { return counsellorId; }
    public void    setCounsellorId(Integer v)    { this.counsellorId = v; }

    public String getCounsellorName()         { return counsellorName; }
    public void   setCounsellorName(String v) { this.counsellorName = v; }

    public String getNextFollowupDate()          { return nextFollowupDate; }
    public void   setNextFollowupDate(String v)  { this.nextFollowupDate = v; }

    public String getStatus()                { return status; }
    public void   setStatus(String v)        { this.status = v; }

    public String getStudentRequirements()         { return studentRequirements; }
    public void   setStudentRequirements(String v) { this.studentRequirements = v; }

    public String getParentFeedback()         { return parentFeedback; }
    public void   setParentFeedback(String v) { this.parentFeedback = v; }

    public String getCounsellorRemarks()         { return counsellorRemarks; }
    public void   setCounsellorRemarks(String v) { this.counsellorRemarks = v; }

    public Integer getConvertedStudentId()          { return convertedStudentId; }
    public void    setConvertedStudentId(Integer v) { this.convertedStudentId = v; }

    public String getConvertedAdmissionNo()         { return convertedAdmissionNo; }
    public void   setConvertedAdmissionNo(String v) { this.convertedAdmissionNo = v; }

    public String getCreatedAt()             { return createdAt; }
    public void   setCreatedAt(String v)     { this.createdAt = v; }

    public String getUpdatedAt()             { return updatedAt; }
    public void   setUpdatedAt(String v)     { this.updatedAt = v; }

    public int  getFollowupCount()           { return followupCount; }
    public void setFollowupCount(int v)      { this.followupCount = v; }
}
