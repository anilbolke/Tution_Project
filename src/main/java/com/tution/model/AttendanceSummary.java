package com.tution.model;

/** Per-student attendance totals over a date range. */
public class AttendanceSummary {

    private int    studentId;
    private String admissionNo;
    private String fullName;
    private String className;
    private int    present;
    private int    absent;
    private int    late;
    private int    leave;

    public int total()      { return present + absent + late + leave; }
    /** Attendance % counting Present + Late as attended. */
    public int percentage() {
        int t = total();
        return t == 0 ? 0 : Math.round((present + late) * 100f / t);
    }

    public int    getStudentId()           { return studentId; }
    public void   setStudentId(int v)      { this.studentId = v; }

    public String getAdmissionNo()         { return admissionNo; }
    public void   setAdmissionNo(String v) { this.admissionNo = v; }

    public String getFullName()            { return fullName; }
    public void   setFullName(String v)    { this.fullName = v; }

    public String getClassName()           { return className; }
    public void   setClassName(String v)   { this.className = v; }

    public int    getPresent()             { return present; }
    public void   setPresent(int v)        { this.present = v; }

    public int    getAbsent()              { return absent; }
    public void   setAbsent(int v)         { this.absent = v; }

    public int    getLate()                { return late; }
    public void   setLate(int v)           { this.late = v; }

    public int    getLeave()               { return leave; }
    public void   setLeave(int v)          { this.leave = v; }
}
