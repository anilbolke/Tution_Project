package com.tution.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Central fee maths. Mirrors the figures used in the admission form:
 * one-time ₹1,300 (registration + material) + tuition (per-month × 12).
 * e.g. monthly slab ₹3,000 → ₹1,300 + ₹36,000 = ₹37,300.
 */
public class FeeCalculator {

    public static final int REG_FEE      = 500;
    public static final int MATERIAL_FEE = 800;
    public static final int ONE_TIME     = REG_FEE + MATERIAL_FEE; // 1300
    public static final int COURSE_MONTHS = 12;

    private static final NumberFormat INR = NumberFormat.getInstance(new Locale("en", "IN"));

    /** Total course fee for a given per-month tuition rate. */
    public static int totalFee(int perMonth) {
        return ONE_TIME + perMonth * COURSE_MONTHS;
    }

    /** Formats a rupee amount with Indian grouping, e.g. ₹37,300. */
    public static String inr(long amount) {
        return "₹" + INR.format(amount);
    }

    private FeeCalculator() { }
}
