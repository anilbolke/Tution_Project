package com.tution.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.tution.model.Stats;
import com.tution.util.DBConnection;
import com.tution.util.FeeCalculator;

/** Computes the dashboard summary in one connection. */
public class StatsDAO {

    public Stats load() throws SQLException {
        Stats s = new Stats();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {

            s.students       = scalar(st, "SELECT COUNT(*) FROM students");
            s.inquiriesNew   = scalar(st, "SELECT COUNT(*) FROM inquiries WHERE status='NEW'");
            s.inquiriesTotal = scalar(st, "SELECT COUNT(*) FROM inquiries");
            s.exams          = scalar(st, "SELECT COUNT(*) FROM exams");
            s.collected      = scalarLong(st, "SELECT COALESCE(SUM(amount),0) FROM payments");
            s.collectedToday = scalarLong(st, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE payment_date = CURDATE()");

            // today's attendance
            try (ResultSet rs = st.executeQuery(
                    "SELECT COALESCE(SUM(status IN ('Present','Late')),0) AS present, COUNT(*) AS marked "
                  + "FROM attendance WHERE attendance_date = CURDATE()")) {
                if (rs.next()) { s.presentToday = rs.getInt("present"); s.markedToday = rs.getInt("marked"); }
            }

            // ── Billed / outstanding / status split ──
            //
            // Reads the student_fees ledger, falling back to the legacy slab
            // formula for any student who has no ledger row yet. Both branches
            // live in ONE query so the dashboard cannot drift from /fees, which
            // is what happened when this figure was computed in six places.
            String perStudentTotal =
                  "COALESCE(sf.net_payable, "
                + "         CASE WHEN fs.slab_key IS NULL THEN 0 "
                + "              ELSE " + FeeCalculator.ONE_TIME
                + "                   + fs.per_month * " + FeeCalculator.COURSE_MONTHS + " END)";

            String ledger =
                  "SELECT COALESCE(SUM(total),0) AS billed, "
                + "       COALESCE(SUM(paid >= total AND total > 0),0) AS p, "
                + "       COALESCE(SUM(paid > 0 AND paid < total),0)   AS pt, "
                + "       COALESCE(SUM(paid = 0 AND total > 0),0)      AS pe FROM ("
                + "  SELECT " + perStudentTotal + " AS total, "
                + "         COALESCE((SELECT SUM(pay.amount) FROM payments pay "
                + "                   WHERE pay.student_id = s.student_id),0) AS paid "
                + "  FROM students s "
                + "  LEFT JOIN student_fees sf ON sf.student_id = s.student_id "
                + "  LEFT JOIN fee_slabs  fs ON fs.slab_key   = s.fee_slab) t";
            try (ResultSet rs = st.executeQuery(ledger)) {
                if (rs.next()) {
                    s.billed     = rs.getLong("billed");
                    s.feePaid    = rs.getInt("p");
                    s.feePartial = rs.getInt("pt");
                    s.feePending = rs.getInt("pe");
                }
            }
            s.outstanding = Math.max(0, s.billed - s.collected);
        }
        return s;
    }

    private int scalar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getInt(1) : 0; }
    }
    private long scalarLong(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getLong(1) : 0L; }
    }
}
