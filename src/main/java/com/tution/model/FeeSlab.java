package com.tution.model;

/** A fee payment plan / slab. */
public class FeeSlab {

    private String slabKey;
    private String label;
    private int    months;
    private int    perMonth;

    public String getSlabKey()           { return slabKey; }
    public void   setSlabKey(String v)   { this.slabKey = v; }

    public String getLabel()             { return label; }
    public void   setLabel(String v)     { this.label = v; }

    public int    getMonths()            { return months; }
    public void   setMonths(int v)       { this.months = v; }

    public int    getPerMonth()          { return perMonth; }
    public void   setPerMonth(int v)     { this.perMonth = v; }
}
