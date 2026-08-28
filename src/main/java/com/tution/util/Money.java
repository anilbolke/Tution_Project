package com.tution.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money formatting and parsing for the finance module.
 *
 * WHY BigDecimal AND NOT int. The tuition-fee side of this app carries amounts
 * as {@code int} ({@code Payment.amount}), which is fine for whole-rupee fees.
 * It will not do here: vendor invoices carry GST at 18% and produce paise, and
 * a bulk school payment split across 60 candidates rarely divides evenly. Any
 * rounding has to be visible and deliberate, not a silent truncation.
 *
 * WHY ALWAYS TWO DECIMALS. In a ledger the amount column is read down, not
 * across. "1,84,000" above "12,500.50" does not line up and invites a misread,
 * so every amount is rendered with both decimal places whether or not they are
 * significant.
 *
 * DIGIT GROUPING IS INDIAN HERE (1,84,000.00), which is what accounting software
 * in this market shows and how the institute writes these figures. Note that the
 * rest of the app formats money with {@code String.format("%,d")} - Western
 * grouping, whole rupees - so the same amount reads as "152,720" on the
 * dashboard and "1,52,720.00" in the expense register. That is a deliberate
 * choice for the finance screens, not an oversight; if the two should match,
 * change {@link #group} to plain {@code %,d} grouping and every finance screen
 * follows, since they all format through here.
 *
 * ASCII ONLY on anything bound for a PDF - {@link ReceiptPdf} strips characters
 * outside 32..126, so pass "Rs. " and never the rupee sign.
 */
public final class Money {

    /** Scale every stored amount uses: paise. */
    public static final int SCALE = 2;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() { }

    /** Normalises to 2dp, rounding half-up. Null becomes zero. */
    public static BigDecimal of(BigDecimal v) {
        return v == null ? ZERO : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long v) {
        return BigDecimal.valueOf(v).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Reads an amount typed by a person. Tolerates thousands separators, stray
     * spaces, a leading "Rs." and the rupee sign, because all of those get
     * pasted in from invoices.
     *
     * Returns null when the text is not a number at all - the caller decides
     * whether that is an error or an empty field. It deliberately does NOT fall
     * back to zero: silently reading "1O,000" (letter O) as 0 would post a
     * blank expense and hide the typo.
     */
    public static BigDecimal parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim()
                      .replace("₹", "")      // rupee sign
                      .replace("Rs.", "")
                      .replace("Rs", "")
                      .replace(",", "")
                      .replace(" ", "");
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s).setScale(SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Same as {@link #parse}, but a blank or unreadable value becomes zero. */
    public static BigDecimal parseOrZero(String raw) {
        BigDecimal v = parse(raw);
        return v == null ? ZERO : v;
    }

    /** "1,84,000.00" - Indian digit grouping, no currency symbol. */
    public static String fmt(BigDecimal v) {
        BigDecimal n = of(v);
        boolean neg = n.signum() < 0;
        String plain = n.abs().toPlainString();

        int dot = plain.indexOf('.');
        String whole = dot < 0 ? plain : plain.substring(0, dot);
        String frac  = dot < 0 ? "00"  : plain.substring(dot + 1);

        return (neg ? "-" : "") + group(whole) + "." + frac;
    }

    /** "Rs. 1,84,000.00" - ASCII, safe for the PDF writer. */
    public static String rs(BigDecimal v) {
        return "Rs. " + fmt(v);
    }

    /**
     * "1,84,000" - Indian grouping, whole rupees, no paise.
     *
     * For places that only ever deal in whole rupees, chiefly the older reports.
     * It exists so there is ONE grouping implementation in the application: the
     * reports used to call {@code String.format("%,d")}, which groups in
     * thousands and put "3,06,800" on the expense screen next to "306,800" in
     * the expense report for the very same money.
     */
    public static String whole(long v) {
        boolean neg = v < 0;
        return (neg ? "-" : "") + group(Long.toString(Math.abs(v)));
    }

    /**
     * Indian grouping: the last three digits, then pairs.
     * 184000 -> 1,84,000 and 12500 -> 12,500.
     */
    private static String group(String whole) {
        int n = whole.length();
        if (n <= 3) return whole;

        String last3 = whole.substring(n - 3);
        String rest  = whole.substring(0, n - 3);

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = rest.length() - 1; i >= 0; i--) {
            sb.append(rest.charAt(i));
            if (++count % 2 == 0 && i > 0) sb.append(',');
        }
        return sb.reverse().append(',').append(last3).toString();
    }

    /** True when the amount is present and strictly greater than zero. */
    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
