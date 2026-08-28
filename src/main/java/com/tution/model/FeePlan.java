package com.tution.model;

/**
 * A sellable programme + batch + duration, priced exactly as the institute
 * brochure states.
 *
 * Replaces the old {@code fee_slabs} idea, where every student on a slab paid
 * the same per-month rate. The real pricing is per programme, and the
 * instalment split is a percentage pattern on fixed calendar months rather than
 * an equal division.
 */
public class FeePlan {

    private int    planId;
    private String code;             // ANKUR-SURE-1Y
    private String programme;        // 11th NEET : ANKUR
    private String programmeCode;    // TYM / OYM-XII / …
    private String mode;             // CLASSROOM / HYBRID / DISTANCE / TEST_SERIES / SHORT
    private String batchType;        // HAGL SURE (13 hrs/day) — null for non-classroom
    private String eligibility;
    private int    durationYears;
    private int    registrationFee;  // non-refundable, NOT part of the course fee
    private int    courseFee;
    private boolean gstInclusive;    // HEATS fees are quoted with GST already in
    private int    installments;
    private String ppPattern;        // "40,35,25"
    private String dueMonths;        // "START,JUN,SEP"
    private boolean active = true;
    private int    sortOrder;

    /** Label for a dropdown: programme, batch and duration in one line. */
    public String getLabel() {
        StringBuilder sb = new StringBuilder(programme == null ? "" : programme);
        if (batchType != null && !batchType.isEmpty()) {
            sb.append(" — ").append(batchType);
        }
        sb.append(" · ").append(durationYears).append(durationYears == 1 ? " year" : " years");
        return sb.toString();
    }

    /** True when the brochure allows no part payment (distance, HEATS, HFTS). */
    public boolean isFullPaymentOnly() {
        return installments <= 1;
    }

    /** The percentages as numbers, in order. */
    public double[] percentages() {
        String p = (ppPattern == null || ppPattern.isEmpty()) ? "100" : ppPattern;
        String[] parts = p.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** The due-month tokens, in order: "START" or a three-letter month. */
    public String[] dueMonthTokens() {
        String d = (dueMonths == null || dueMonths.isEmpty()) ? "START" : dueMonths;
        String[] parts = d.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim().toUpperCase();
        }
        return parts;
    }

    public int    getPlanId()               { return planId; }
    public void   setPlanId(int v)          { this.planId = v; }

    public String getCode()                 { return code; }
    public void   setCode(String v)         { this.code = v; }

    public String getProgramme()            { return programme; }
    public void   setProgramme(String v)    { this.programme = v; }

    public String getProgrammeCode()        { return programmeCode; }
    public void   setProgrammeCode(String v){ this.programmeCode = v; }

    public String getMode()                 { return mode; }
    public void   setMode(String v)         { this.mode = v; }

    public String getBatchType()            { return batchType; }
    public void   setBatchType(String v)    { this.batchType = v; }

    public String getEligibility()          { return eligibility; }
    public void   setEligibility(String v)  { this.eligibility = v; }

    public int    getDurationYears()        { return durationYears; }
    public void   setDurationYears(int v)   { this.durationYears = v; }

    public int    getRegistrationFee()      { return registrationFee; }
    public void   setRegistrationFee(int v) { this.registrationFee = v; }

    public int    getCourseFee()            { return courseFee; }
    public void   setCourseFee(int v)       { this.courseFee = v; }

    public boolean isGstInclusive()         { return gstInclusive; }
    public void    setGstInclusive(boolean v) { this.gstInclusive = v; }

    public int    getInstallments()         { return installments; }
    public void   setInstallments(int v)    { this.installments = v; }

    public String getPpPattern()            { return ppPattern; }
    public void   setPpPattern(String v)    { this.ppPattern = v; }

    public String getDueMonths()            { return dueMonths; }
    public void   setDueMonths(String v)    { this.dueMonths = v; }

    public boolean isActive()               { return active; }
    public void    setActive(boolean v)     { this.active = v; }

    public int    getSortOrder()            { return sortOrder; }
    public void   setSortOrder(int v)       { this.sortOrder = v; }
}
