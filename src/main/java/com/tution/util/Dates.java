package com.tution.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Helpers for the follow-up "next action" fields, which carry a date AND a time.
 *
 * The models stay String-based like the rest of the app, so one canonical text
 * form is used everywhere: "yyyy-MM-dd HH:mm". Every read and write of
 * next_followup_date / next_action_date goes through here so the browser's
 * datetime-local format, the SQL DATETIME and the on-screen text cannot drift
 * apart.
 *
 * Rows written before those columns grew a time sit at 00:00, so midnight is
 * displayed as a plain date rather than a misleading "12:00 AM".
 */
public final class Dates {

    /** Canonical form carried in the models, and what the JSPs compare against. */
    private static final DateTimeFormatter MODEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** What <input type="datetime-local"> sends and expects back. */
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    /** The literal MySQL accepts for a DATETIME column. */
    private static final DateTimeFormatter SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private Dates() { }

    /**
     * Parses whatever the app might hand us — the browser's "yyyy-MM-ddTHH:mm",
     * our own "yyyy-MM-dd HH:mm", a JDBC "yyyy-MM-dd HH:mm:ss[.f]" or a bare
     * "yyyy-MM-dd" (taken as midnight). Blank or unparseable gives null: an
     * empty date box on a form is normal input, not an error.
     */
    public static LocalDateTime parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        try {
            if (s.length() == 10) {
                return LocalDate.parse(s).atStartOfDay();
            }
            if (s.length() >= 16) {
                return LocalDateTime.parse(s.substring(0, 16), MODEL);
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Canonical "yyyy-MM-dd HH:mm", or null. */
    public static String normalize(String raw) {
        LocalDateTime t = parse(raw);
        return t == null ? null : t.format(MODEL);
    }

    /**
     * The date part alone, "yyyy-MM-dd", or null.
     *
     * The reminder log is keyed on the day, not the minute, so one lead cannot
     * generate two WhatsApp reminders just because its time was edited.
     */
    public static String dayOf(String raw) {
        LocalDateTime t = parse(raw);
        return t == null ? null : t.toLocalDate().toString();
    }

    /**
     * The time alone, "4:30 PM", or "" when unset or at midnight.
     *
     * Lets a narrow table column stack the time under the date instead of
     * widening to fit both on one line.
     */
    public static String timeOf(String raw) {
        LocalDateTime t = parse(raw);
        if (t == null || (t.getHour() == 0 && t.getMinute() == 0)) {
            return "";
        }
        return t.format(CLOCK);
    }

    /** Value for an &lt;input type="datetime-local"&gt;; "" when unset. */
    public static String forInput(String raw) {
        LocalDateTime t = parse(raw);
        return t == null ? "" : t.format(INPUT);
    }

    /**
     * On-screen text: "2026-08-13 &middot; 4:30 PM", or just the date at
     * midnight. The middle dot is built from its code point so this file stays
     * pure ASCII - the project has been bitten by encoding drift before.
     */
    public static String display(String raw) {
        LocalDateTime t = parse(raw);
        if (t == null) {
            return "";
        }
        String day = t.toLocalDate().toString();
        return (t.getHour() == 0 && t.getMinute() == 0)
             ? day
             : day + " " + ((char) 0xB7) + " " + t.format(CLOCK);
    }

    /**
     * Binds a DATETIME parameter; blank or unparseable becomes SQL NULL.
     *
     * Bound as TEXT, not a Timestamp, on purpose. The connection runs with
     * serverTimezone=UTC, so the driver would shift a Timestamp out of the
     * JVM's zone and store 4:30 PM as 11:00 — a MySQL DATETIME is a plain wall
     * clock with no zone, and "4:30 PM" must stay 4:30 PM for the DATE(...)
     * comparisons the work queue is built on. Read() is the same trade in
     * reverse.
     */
    public static void setNullableDateTime(PreparedStatement ps, int idx, String raw)
            throws SQLException {
        LocalDateTime t = parse(raw);
        if (t == null) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setString(idx, t.format(SQL));
        }
    }

    /** Reads a DATETIME column into the canonical form, or null. */
    public static String read(ResultSet rs, String col) throws SQLException {
        return normalize(rs.getString(col));
    }
}
