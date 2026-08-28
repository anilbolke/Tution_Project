package com.tution.model;

/** A student admission record. */
public class Student {

    private int    studentId;
    private String admissionNo;
    private String fullName;
    private String dob;            // yyyy-MM-dd
    private String gender;
    private String className;
    private String board;
    private String prevSchool;
    private String prevMarks;
    private String studentMobile;
    private String altMobile;
    private String studentEmail;
    private String parentName;
    private String parentMobile;
    private String address;
    private String photoPath;
    private String feeSlab;      // legacy slab key, kept for students admitted before the re-price
    private String planCode;     // fee_plans.code — the brochure plan the student was sold

    // ── sales dimension (added with the Sales Module) ──
    private Integer counsellorId;    // who closed the admission
    private String  counsellorName;  // joined from users, not a column
    private Integer inquiryId;       // the lead this admission came from
    private String  batchName;
    private String  branch;
    private String  idProofPath;
    private boolean active = true;
    private String  createdAt;

    public int    getStudentId()              { return studentId; }
    public void   setStudentId(int v)         { this.studentId = v; }

    public String getAdmissionNo()            { return admissionNo; }
    public void   setAdmissionNo(String v)    { this.admissionNo = v; }

    public String getFullName()               { return fullName; }
    public void   setFullName(String v)       { this.fullName = v; }

    public String getDob()                    { return dob; }
    public void   setDob(String v)            { this.dob = v; }

    public String getGender()                 { return gender; }
    public void   setGender(String v)         { this.gender = v; }

    public String getClassName()              { return className; }
    public void   setClassName(String v)      { this.className = v; }

    public String getBoard()                  { return board; }
    public void   setBoard(String v)          { this.board = v; }

    public String getPrevSchool()             { return prevSchool; }
    public void   setPrevSchool(String v)     { this.prevSchool = v; }

    public String getPrevMarks()              { return prevMarks; }
    public void   setPrevMarks(String v)      { this.prevMarks = v; }

    public String getStudentMobile()          { return studentMobile; }
    public void   setStudentMobile(String v)  { this.studentMobile = v; }

    public String getAltMobile()              { return altMobile; }
    public void   setAltMobile(String v)      { this.altMobile = v; }

    public String getStudentEmail()           { return studentEmail; }
    public void   setStudentEmail(String v)   { this.studentEmail = v; }

    public String getParentName()             { return parentName; }
    public void   setParentName(String v)     { this.parentName = v; }

    public String getParentMobile()           { return parentMobile; }
    public void   setParentMobile(String v)   { this.parentMobile = v; }

    public String getAddress()                { return address; }
    public void   setAddress(String v)        { this.address = v; }

    public String getPhotoPath()              { return photoPath; }
    public void   setPhotoPath(String v)      { this.photoPath = v; }

    public String getFeeSlab()                { return feeSlab; }
    public void   setFeeSlab(String v)        { this.feeSlab = v; }

    public String getPlanCode()               { return planCode; }
    public void   setPlanCode(String v)       { this.planCode = v; }

    public Integer getCounsellorId()           { return counsellorId; }
    public void    setCounsellorId(Integer v)  { this.counsellorId = v; }

    public String getCounsellorName()          { return counsellorName; }
    public void   setCounsellorName(String v)  { this.counsellorName = v; }

    public Integer getInquiryId()              { return inquiryId; }
    public void    setInquiryId(Integer v)     { this.inquiryId = v; }

    public String getBatchName()               { return batchName; }
    public void   setBatchName(String v)       { this.batchName = v; }

    public String getBranch()                  { return branch; }
    public void   setBranch(String v)          { this.branch = v; }

    public String getIdProofPath()             { return idProofPath; }
    public void   setIdProofPath(String v)     { this.idProofPath = v; }

    private String doc1Path, doc2Path, doc3Path, doc4Path;
    public String getDoc1Path()                { return doc1Path; }
    public void   setDoc1Path(String v)        { this.doc1Path = v; }
    public String getDoc2Path()                { return doc2Path; }
    public void   setDoc2Path(String v)        { this.doc2Path = v; }
    public String getDoc3Path()                { return doc3Path; }
    public void   setDoc3Path(String v)        { this.doc3Path = v; }
    public String getDoc4Path()                { return doc4Path; }
    public void   setDoc4Path(String v)        { this.doc4Path = v; }

    public boolean isActive()                  { return active; }
    public void    setActive(boolean v)        { this.active = v; }

    public String getCreatedAt()               { return createdAt; }
    public void   setCreatedAt(String v)       { this.createdAt = v; }
}
