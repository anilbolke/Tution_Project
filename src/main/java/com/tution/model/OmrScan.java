package com.tution.model;

/** A stored OMR scan summary (for the scan history list). */
public class OmrScan {
    private int    scanId;
    private String title;
    private int    total;
    private int    attempted;
    private int    blank;
    private int    ambiguous;
    private Integer correct;
    private Integer wrong;
    private Integer score;
    private String scannedBy;
    private String createdAt;

    public int     getScanId()            { return scanId; }
    public void    setScanId(int v)       { this.scanId = v; }
    public String  getTitle()             { return title; }
    public void    setTitle(String v)     { this.title = v; }
    public int     getTotal()             { return total; }
    public void    setTotal(int v)        { this.total = v; }
    public int     getAttempted()         { return attempted; }
    public void    setAttempted(int v)    { this.attempted = v; }
    public int     getBlank()             { return blank; }
    public void    setBlank(int v)        { this.blank = v; }
    public int     getAmbiguous()         { return ambiguous; }
    public void    setAmbiguous(int v)    { this.ambiguous = v; }
    public Integer getCorrect()           { return correct; }
    public void    setCorrect(Integer v)  { this.correct = v; }
    public Integer getWrong()             { return wrong; }
    public void    setWrong(Integer v)    { this.wrong = v; }
    public Integer getScore()             { return score; }
    public void    setScore(Integer v)    { this.score = v; }
    public String  getScannedBy()         { return scannedBy; }
    public void    setScannedBy(String v) { this.scannedBy = v; }
    public String  getCreatedAt()         { return createdAt; }
    public void    setCreatedAt(String v) { this.createdAt = v; }
}
