package com.tution.model;

/**
 * One student's SITTING of one exam — the row that owns the roll number.
 *
 * Deliberately not a property of the person: the Excel template's "EXAM Attempt"
 * column exists because students re-sit, and someone taking HAMSE in December
 * and HACKSE in March needs two roll numbers and two results against a single
 * lead.
 *
 * The lead's own details (name, school, contacts) are carried here read-only for
 * display, so a roll list or hall ticket is one query rather than one per row.
 */
public class ExamCandidate {

    private int    candidateId;
    private int    examId;
    private int    inquiryId;
    private String rollNo;
    private String rollKind;        // GENERATED / LEGACY
    private String bookletCode;
    private String examCentre;
    private int    attemptNo = 1;
    private String status;          // REGISTERED / APPEARED / ABSENT / RESULT_READY
    private Integer importBatchId;
    private String createdAt;

    // ── from the joined lead, for display only ──
    private String fullName;
    private String mobile;
    private String parentMobile;
    private String schoolName;
    private String className;
    private String board;
    private String city;

    // ── from the joined exam, for hall tickets ──
    private String examName;
    private String examDate;
    private String examType;

    /** A roll issued by hand before this system existed; exempt from the check digit. */
    public boolean isLegacyRoll() { return "LEGACY".equals(rollKind); }

    public boolean hasSat() { return "APPEARED".equals(status) || "RESULT_READY".equals(status); }

    public int    getCandidateId()        { return candidateId; }
    public void   setCandidateId(int v)   { this.candidateId = v; }

    public int    getExamId()             { return examId; }
    public void   setExamId(int v)        { this.examId = v; }

    public int    getInquiryId()          { return inquiryId; }
    public void   setInquiryId(int v)     { this.inquiryId = v; }

    public String getRollNo()             { return rollNo; }
    public void   setRollNo(String v)     { this.rollNo = v; }

    public String getRollKind()           { return rollKind; }
    public void   setRollKind(String v)   { this.rollKind = v; }

    public String getBookletCode()        { return bookletCode; }
    public void   setBookletCode(String v){ this.bookletCode = v; }

    public String getExamCentre()         { return examCentre; }
    public void   setExamCentre(String v) { this.examCentre = v; }

    public int    getAttemptNo()          { return attemptNo; }
    public void   setAttemptNo(int v)     { this.attemptNo = v; }

    public String getStatus()             { return status; }
    public void   setStatus(String v)     { this.status = v; }

    public Integer getImportBatchId()        { return importBatchId; }
    public void    setImportBatchId(Integer v) { this.importBatchId = v; }

    public String getCreatedAt()          { return createdAt; }
    public void   setCreatedAt(String v)  { this.createdAt = v; }

    public String getFullName()           { return fullName; }
    public void   setFullName(String v)   { this.fullName = v; }

    public String getMobile()             { return mobile; }
    public void   setMobile(String v)     { this.mobile = v; }

    public String getParentMobile()          { return parentMobile; }
    public void   setParentMobile(String v)  { this.parentMobile = v; }

    public String getSchoolName()         { return schoolName; }
    public void   setSchoolName(String v) { this.schoolName = v; }

    public String getClassName()          { return className; }
    public void   setClassName(String v)  { this.className = v; }

    public String getBoard()              { return board; }
    public void   setBoard(String v)      { this.board = v; }

    public String getCity()               { return city; }
    public void   setCity(String v)       { this.city = v; }

    public String getExamName()           { return examName; }
    public void   setExamName(String v)   { this.examName = v; }

    public String getExamDate()           { return examDate; }
    public void   setExamDate(String v)   { this.examDate = v; }

    public String getExamType()           { return examType; }
    public void   setExamType(String v)   { this.examType = v; }
}
