package com.tution.model;

/** One page's result inside a multi-page PDF batch scan. */
public class OmrBatchRow {
    public int     page;
    public int     scanId;
    public int     attempted;
    public int     blank;
    public int     ambiguous;
    public Integer correct;
    public Integer wrong;
    public Integer score;
    public String  overlayUrl;
    public java.util.List<SubjectScore> subjects;   // per-subject breakdown for this sheet
    public String  rollNo;        // bubbled roll number read from the sheet
    public String  studentName;   // matched student (null if no/ambiguous match)
}
