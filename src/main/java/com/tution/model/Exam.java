package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/** An examination definition (subject-wise). */
public class Exam {

    private int    examId;
    private String examName;
    private String examDate;
    private String className;
    private String subjects;      // CSV, e.g. "Physics,Chemistry,Biology"
    private int    maxPerSubject;
    private String createdBy;
    private String createdAt;

    // ── scholarship-exam settings (all unused by an INTERNAL class test) ──
    private String examType = "INTERNAL";   // INTERNAL / HAMSE / HACKSE / HAT
    private Integer templateId;             // which OMR sheet layout
    private int    totalQuestions = 60;
    private int    markCorrect    = 4;
    private int    markWrong      = -1;     // negative marking, per the sheet
    private int    maxScore       = 240;
    private int    rollBlockFrom;           // inclusive 5-digit sequence block
    private int    rollBlockTo;
    private int    rollNext;                // next unallocated sequence
    private boolean resultPublished;

    /**
     * The list price for sitting this exam. Zero means "no fee set yet" — which is
     * why {@link com.tution.model.CandidateFee} reports NO_FEE rather than treating
     * an unpriced exam as fully paid.
     */
    private BigDecimal examFee = Money.ZERO;

    /** True for HAMSE / HACKSE / HAT — i.e. an OMR-scored scholarship exam. */
    public boolean isScholarshipExam() {
        return examType != null && !"INTERNAL".equals(examType);
    }

    /** How many roll numbers this exam still has left to hand out. */
    public int rollsRemaining() {
        if (rollBlockFrom <= 0 || rollBlockTo <= 0) return 0;
        int next = rollNext < rollBlockFrom ? rollBlockFrom : rollNext;
        return Math.max(0, rollBlockTo - next + 1);
    }

    /** Subjects as a trimmed array (empty-safe). */
    public String[] subjectList() {
        if (subjects == null || subjects.trim().isEmpty()) return new String[0];
        String[] parts = subjects.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    public int totalMax() { return maxPerSubject * subjectList().length; }

    public int    getExamId()            { return examId; }
    public void   setExamId(int v)       { this.examId = v; }

    public String getExamName()          { return examName; }
    public void   setExamName(String v)  { this.examName = v; }

    public String getExamDate()          { return examDate; }
    public void   setExamDate(String v)  { this.examDate = v; }

    public String getClassName()         { return className; }
    public void   setClassName(String v) { this.className = v; }

    public String getSubjects()          { return subjects; }
    public void   setSubjects(String v)  { this.subjects = v; }

    public int    getMaxPerSubject()         { return maxPerSubject; }
    public void   setMaxPerSubject(int v)    { this.maxPerSubject = v; }

    public String getCreatedBy()         { return createdBy; }
    public void   setCreatedBy(String v) { this.createdBy = v; }

    public String getCreatedAt()         { return createdAt; }
    public void   setCreatedAt(String v) { this.createdAt = v; }

    public String  getExamType()          { return examType; }
    public void    setExamType(String v)  { this.examType = v; }

    public Integer getTemplateId()          { return templateId; }
    public void    setTemplateId(Integer v) { this.templateId = v; }

    public int  getTotalQuestions()      { return totalQuestions; }
    public void setTotalQuestions(int v) { this.totalQuestions = v; }

    public int  getMarkCorrect()         { return markCorrect; }
    public void setMarkCorrect(int v)    { this.markCorrect = v; }

    public int  getMarkWrong()           { return markWrong; }
    public void setMarkWrong(int v)      { this.markWrong = v; }

    public int  getMaxScore()            { return maxScore; }
    public void setMaxScore(int v)       { this.maxScore = v; }

    public int  getRollBlockFrom()       { return rollBlockFrom; }
    public void setRollBlockFrom(int v)  { this.rollBlockFrom = v; }

    public int  getRollBlockTo()         { return rollBlockTo; }
    public void setRollBlockTo(int v)    { this.rollBlockTo = v; }

    public int  getRollNext()            { return rollNext; }
    public void setRollNext(int v)       { this.rollNext = v; }

    public boolean isResultPublished()        { return resultPublished; }
    public void    setResultPublished(boolean v) { this.resultPublished = v; }

    public BigDecimal getExamFee()          { return examFee; }
    public void       setExamFee(BigDecimal v) { this.examFee = Money.of(v); }
}
