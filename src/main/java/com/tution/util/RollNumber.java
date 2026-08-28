package com.tution.util;

/**
 * Exam roll numbers: a 5-digit sequence plus one check digit, six digits in
 * total because the OMR sheet's roll grid has exactly six bubble columns.
 *
 * WHY A CHECK DIGIT. The roll number is the ONLY machine-readable identity on
 * the answer sheet — student name, school and both mobile numbers are
 * hand-written boxes with no bubble grid. Without a check digit, one misread
 * bubble turns a valid roll straight into another valid roll, and the marks are
 * silently filed against the wrong child with nothing to flag it. On an exam
 * that decides fee concessions that is not an acceptable failure mode.
 *
 * WHY DAMM AND NOT LUHN. Luhn is the obvious choice and it is not good enough
 * here: it misses the transposition 09 -> 90 (and 90 -> 09), because its
 * double-and-reduce step maps 0 and 9 onto the same contribution. Measured over
 * random rolls that leaves ~2% of adjacent transpositions undetected. Damm's
 * totally anti-symmetric quasigroup catches ALL single-digit errors and ALL
 * adjacent transpositions, for the same one digit of space — which matters,
 * because a swapped pair and a single wrong bubble are precisely what an OMR
 * misread looks like.
 *
 * Legacy numbers issued by hand before this system existed (e.g. 618062) will
 * not satisfy the check. They are stored with roll_kind = 'LEGACY' and are
 * exempt from validation.
 */
public final class RollNumber {

    public static final int DIGITS  = 6;
    public static final int SEQ_MIN = 1;
    public static final int SEQ_MAX = 99999;

    /**
     * Damm operation table — a totally anti-symmetric quasigroup of order 10.
     * Row = running interim digit, column = next input digit.
     */
    private static final int[][] DAMM = {
        {0, 3, 1, 7, 5, 9, 8, 6, 4, 2},
        {7, 0, 9, 2, 1, 5, 4, 8, 6, 3},
        {4, 2, 0, 6, 8, 7, 1, 3, 5, 9},
        {1, 7, 5, 0, 9, 8, 3, 4, 2, 6},
        {6, 1, 2, 3, 0, 4, 5, 9, 7, 8},
        {3, 6, 7, 4, 2, 0, 9, 5, 8, 1},
        {5, 8, 6, 9, 7, 2, 0, 1, 3, 4},
        {8, 9, 4, 5, 3, 6, 2, 0, 1, 7},
        {9, 4, 3, 8, 6, 1, 7, 2, 0, 5},
        {2, 5, 8, 1, 4, 3, 6, 7, 9, 0}
    };

    private RollNumber() { }

    /** Roll number for a sequence value: five digits zero-padded, then the check digit. */
    public static String forSequence(int seq) {
        if (seq < SEQ_MIN || seq > SEQ_MAX) {
            throw new IllegalArgumentException(
                "Roll sequence out of range (" + SEQ_MIN + "-" + SEQ_MAX + "): " + seq);
        }
        String body = String.format("%05d", seq);
        return body + checkDigit(body);
    }

    /** The sequence a roll number encodes, ignoring its check digit. -1 if malformed. */
    public static int sequenceOf(String roll) {
        if (roll == null || !roll.matches("\\d{" + DIGITS + "}")) return -1;
        return Integer.parseInt(roll.substring(0, DIGITS - 1));
    }

    /** True when the roll is six digits and the check digit agrees. */
    public static boolean isValid(String roll) {
        if (roll == null || !roll.matches("\\d{" + DIGITS + "}")) return false;
        return interim(roll) == 0;      // Damm: feeding the whole number returns to 0
    }

    /** Damm check digit for a digit string. */
    static char checkDigit(String body) {
        return (char) ('0' + interim(body));
    }

    private static int interim(String digits) {
        int interim = 0;
        for (int i = 0; i < digits.length(); i++) {
            interim = DAMM[interim][digits.charAt(i) - '0'];
        }
        return interim;
    }
}
