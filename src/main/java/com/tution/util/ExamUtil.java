package com.tution.util;

/** Grade / pass logic shared across results and report cards. */
public class ExamUtil {

    /** Letter grade from a percentage. */
    public static String grade(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 75) return "A";
        if (pct >= 60) return "B";
        if (pct >= 45) return "C";
        if (pct >= 33) return "D";
        return "F";
    }

    /** A subject is a pass at >= 33% of its max. */
    public static boolean isPass(double pct) {
        return pct >= 33;
    }

    private ExamUtil() { }
}
