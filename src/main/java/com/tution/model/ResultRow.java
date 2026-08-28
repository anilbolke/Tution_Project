package com.tution.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One student's computed result for an exam. */
public class ResultRow {

    private int    studentId;
    private String admissionNo;
    private String fullName;
    private Map<String, Integer> marks = new LinkedHashMap<>(); // subject -> marks
    private int    total;
    private int    maxTotal;
    private boolean appeared;   // false if no marks entered
    private int    rank;

    public double percentage() {
        return maxTotal == 0 ? 0 : Math.round((total * 10000.0 / maxTotal)) / 100.0;
    }

    public int    getStudentId()             { return studentId; }
    public void   setStudentId(int v)        { this.studentId = v; }

    public String getAdmissionNo()           { return admissionNo; }
    public void   setAdmissionNo(String v)   { this.admissionNo = v; }

    public String getFullName()              { return fullName; }
    public void   setFullName(String v)      { this.fullName = v; }

    public Map<String, Integer> getMarks()   { return marks; }
    public void   setMarks(Map<String,Integer> v) { this.marks = v; }

    public int    getTotal()                 { return total; }
    public void   setTotal(int v)            { this.total = v; }

    public int    getMaxTotal()              { return maxTotal; }
    public void   setMaxTotal(int v)         { this.maxTotal = v; }

    public boolean isAppeared()              { return appeared; }
    public void    setAppeared(boolean v)    { this.appeared = v; }

    public int    getRank()                  { return rank; }
    public void   setRank(int v)             { this.rank = v; }
}
