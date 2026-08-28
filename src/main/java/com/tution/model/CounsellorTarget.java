package com.tution.model;

import java.time.LocalDate;

/**
 * One counsellor's target for one period, and how they are doing against it.
 *
 * The target fields are stored; the actuals and everything derived from them are
 * computed live, so these figures always agree with the Counsellor Performance
 * report rather than drifting from it.
 */
public class CounsellorTarget {

    private int    targetId;
    private int    counsellorId;
    private String counsellorName;
    private String role;
    private String periodType = "QUARTER";
    private String periodStart;      // ISO
    private String periodEnd;        // ISO
    private int    admissionsTarget;
    private long   revenueTarget;
    private String revenueBasis = "BOOKED";
    private String notes;
    private String setBy;

    /** True when nothing has been set for this person and period. */
    private boolean unset = true;

    // ── live actuals ──
    private int  admissionsActual;
    private long revenueActual;      // on the basis above
    private long collectedActual;    // cash in the period, shown alongside BOOKED

    /* ─── derived ─── */

    public double admissionsPct() { return pct(admissionsActual, admissionsTarget); }
    public double revenuePct()    { return pct(revenueActual, revenueTarget); }

    /**
     * How far through the period we are, as a percentage.
     *
     * This is what makes the numbers usable before the last day: 55% on day 20
     * of a quarter is ahead, on day 80 it is behind. Clamped to 0..100 so a
     * finished period reads 100 and a future one reads 0.
     */
    public double expectedPct() {
        LocalDate start = parse(periodStart), end = parse(periodEnd);
        if (start == null || end == null) return 0;
        LocalDate today = LocalDate.now();
        if (today.isBefore(start)) return 0;
        if (!today.isBefore(end))  return 100;
        double total   = end.toEpochDay() - start.toEpochDay() + 1;
        double elapsed = today.toEpochDay() - start.toEpochDay() + 1;
        return total <= 0 ? 0 : Math.min(100, Math.max(0, elapsed * 100.0 / total));
    }

    /** Days left in the period, 0 once it has ended. */
    public long daysLeft() {
        LocalDate end = parse(periodEnd);
        if (end == null) return 0;
        long d = end.toEpochDay() - LocalDate.now().toEpochDay();
        return Math.max(0, d);
    }

    /** Ahead / On track / Behind, judged against the pace, not the finish line. */
    public String status(double achievedPct) {
        if (unset) return "No target";
        double expected = expectedPct();
        if (achievedPct >= expected + 10) return "Ahead";
        if (achievedPct >= expected - 10) return "On track";
        return "Behind";
    }
    public String admissionsStatus() { return status(admissionsPct()); }
    public String revenueStatus()    { return status(revenuePct()); }

    /** What still has to be closed to hit the target. */
    public int  admissionsShortfall() { return Math.max(0, admissionsTarget - admissionsActual); }
    public long revenueShortfall()    { return Math.max(0, revenueTarget - revenueActual); }

    private static double pct(double actual, double target) {
        if (target <= 0) return 0;
        return Math.round(actual * 1000.0 / target) / 10.0;   // one decimal
    }
    private static LocalDate parse(String iso) {
        try { return (iso == null || iso.isEmpty()) ? null : LocalDate.parse(iso.substring(0, 10)); }
        catch (Exception e) { return null; }
    }

    /* ─── accessors ─── */

    public int    getTargetId()             { return targetId; }
    public void   setTargetId(int v)        { this.targetId = v; }
    public int    getCounsellorId()         { return counsellorId; }
    public void   setCounsellorId(int v)    { this.counsellorId = v; }
    public String getCounsellorName()       { return counsellorName; }
    public void   setCounsellorName(String v) { this.counsellorName = v; }
    public String getRole()                 { return role; }
    public void   setRole(String v)         { this.role = v; }
    public String getPeriodType()           { return periodType; }
    public void   setPeriodType(String v)   { this.periodType = v; }
    public String getPeriodStart()          { return periodStart; }
    public void   setPeriodStart(String v)  { this.periodStart = v; }
    public String getPeriodEnd()            { return periodEnd; }
    public void   setPeriodEnd(String v)    { this.periodEnd = v; }
    public int    getAdmissionsTarget()     { return admissionsTarget; }
    public void   setAdmissionsTarget(int v){ this.admissionsTarget = v; }
    public long   getRevenueTarget()        { return revenueTarget; }
    public void   setRevenueTarget(long v)  { this.revenueTarget = v; }
    public String getRevenueBasis()         { return revenueBasis; }
    public void   setRevenueBasis(String v) { this.revenueBasis = v; }
    public String getNotes()                { return notes; }
    public void   setNotes(String v)        { this.notes = v; }
    public String getSetBy()                { return setBy; }
    public void   setSetBy(String v)        { this.setBy = v; }
    public boolean isUnset()                { return unset; }
    public void   setUnset(boolean v)       { this.unset = v; }
    public int    getAdmissionsActual()     { return admissionsActual; }
    public void   setAdmissionsActual(int v){ this.admissionsActual = v; }
    public long   getRevenueActual()        { return revenueActual; }
    public void   setRevenueActual(long v)  { this.revenueActual = v; }
    public long   getCollectedActual()      { return collectedActual; }
    public void   setCollectedActual(long v){ this.collectedActual = v; }
}
