package com.tution.model;

/** One student's sitting of one online exam. */
public class OnlineExamAttempt {

    private int    attemptId;
    private int    onlineExamId;
    private int    studentId;
    private String startedAt;
    private String submittedAt;
    private int    totalMarks;
    private int    score;
    private String status = "IN_PROGRESS";

    // ── joined, for the student's exam list ──
    private String examTitle;
    private String className;
    private int    durationMinutes;

    // ── joined, for the staff-side results list ──
    private String studentName;
    private String admissionNo;

    public boolean isSubmitted() { return "SUBMITTED".equals(status); }

    public double percentage() {
        return totalMarks <= 0 ? 0 : Math.round(score * 1000.0 / totalMarks) / 10.0;
    }

    public int    getAttemptId()              { return attemptId; }
    public void   setAttemptId(int v)         { this.attemptId = v; }

    public int    getOnlineExamId()           { return onlineExamId; }
    public void   setOnlineExamId(int v)      { this.onlineExamId = v; }

    public int    getStudentId()              { return studentId; }
    public void   setStudentId(int v)         { this.studentId = v; }

    public String getStartedAt()              { return startedAt; }
    public void   setStartedAt(String v)      { this.startedAt = v; }

    public String getSubmittedAt()            { return submittedAt; }
    public void   setSubmittedAt(String v)    { this.submittedAt = v; }

    public int    getTotalMarks()             { return totalMarks; }
    public void   setTotalMarks(int v)        { this.totalMarks = v; }

    public int    getScore()                  { return score; }
    public void   setScore(int v)             { this.score = v; }

    public String getStatus()                 { return status; }
    public void   setStatus(String v)         { this.status = v; }

    public String getExamTitle()              { return examTitle; }
    public void   setExamTitle(String v)      { this.examTitle = v; }

    public String getClassName()              { return className; }
    public void   setClassName(String v)      { this.className = v; }

    public int    getDurationMinutes()        { return durationMinutes; }
    public void   setDurationMinutes(int v)   { this.durationMinutes = v; }

    public String getStudentName()            { return studentName; }
    public void   setStudentName(String v)    { this.studentName = v; }

    public String getAdmissionNo()            { return admissionNo; }
    public void   setAdmissionNo(String v)    { this.admissionNo = v; }
}
