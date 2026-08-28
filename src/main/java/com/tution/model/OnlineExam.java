package com.tution.model;

/** An online MCQ exam for one class — the paper's settings, not its questions. */
public class OnlineExam {

    private int     onlineExamId;
    private String  title;
    private String  className;
    private int     durationMinutes = 30;
    private boolean active = true;
    private String  createdBy;
    private String  createdAt;

    // ── joined / derived, for the management and student list screens ──
    private int questionCount;
    private int totalMarks;

    public int     getOnlineExamId()          { return onlineExamId; }
    public void    setOnlineExamId(int v)     { this.onlineExamId = v; }

    public String  getTitle()                 { return title; }
    public void    setTitle(String v)         { this.title = v; }

    public String  getClassName()             { return className; }
    public void    setClassName(String v)     { this.className = v; }

    public int     getDurationMinutes()       { return durationMinutes; }
    public void    setDurationMinutes(int v)  { this.durationMinutes = v; }

    public boolean isActive()                 { return active; }
    public void    setActive(boolean v)       { this.active = v; }

    public String  getCreatedBy()             { return createdBy; }
    public void    setCreatedBy(String v)     { this.createdBy = v; }

    public String  getCreatedAt()             { return createdAt; }
    public void    setCreatedAt(String v)     { this.createdAt = v; }

    public int     getQuestionCount()         { return questionCount; }
    public void    setQuestionCount(int v)    { this.questionCount = v; }

    public int     getTotalMarks()            { return totalMarks; }
    public void    setTotalMarks(int v)       { this.totalMarks = v; }
}
