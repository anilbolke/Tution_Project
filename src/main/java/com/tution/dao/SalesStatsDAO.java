package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.SalesStats;
import com.tution.util.DBConnection;
import com.tution.util.FeeCalculator;

/**
 * Sales figures for the counsellor and management dashboards.
 *
 * Every query takes an optional counsellor filter, so the same SQL serves one
 * counsellor's tile and the institute-wide strip — no second set of statements
 * to drift out of step.
 */
public class SalesStatsDAO {

    /**
     * Outstanding uses the same ledger-with-fallback rule as FeeService: the
     * student_fees row when there is one, otherwise the legacy slab formula.
     */
    private static final String STUDENT_TOTAL =
          "COALESCE(sf.net_payable, CASE WHEN fs.slab_key IS NULL THEN 0 "
        + "  ELSE " + FeeCalculator.ONE_TIME + " + fs.per_month * "
        + FeeCalculator.COURSE_MONTHS + " END)";

    /** @param counsellorId null for institute-wide figures */
    public SalesStats load(Integer counsellorId) throws SQLException {
        SalesStats s = new SalesStats();
        s.counsellorId = counsellorId;

        try (Connection con = DBConnection.getConnection()) {

            // ── pipeline by status ──
            String pipeline = "SELECT status, COUNT(*) n FROM inquiries "
                            + where(counsellorId, "counsellor_id") + " GROUP BY status";
            try (PreparedStatement ps = con.prepareStatement(pipeline)) {
                bind(ps, counsellorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String st = rs.getString("status");
                        int n = rs.getInt("n");
                        if (st == null) {
                            continue;
                        }
                        switch (st) {
                            case "NEW":               s.leadsNew        = n; break;
                            case "CONTACTED":         s.leadsContacted  = n; break;
                            case "INTERESTED":        s.leadsInterested = n; break;
                            case "DEMO_PENDING":
                            case "DEMO_COMPLETED":    s.leadsDemo      += n; break;
                            case "FOLLOWUP_REQUIRED": s.leadsFollowup   = n; break;
                            case "NOT_INTERESTED":
                            case "LOST":              s.leadsLost      += n; break;
                            default: break;   // CONVERTED counted separately
                        }
                    }
                }
            }
            s.leadsTotal = s.leadsNew + s.leadsContacted + s.leadsInterested
                         + s.leadsDemo + s.leadsFollowup;

            // ── follow-up work queue ──
            s.followupsToday = scalar(con,
                "SELECT COUNT(*) FROM inquiries WHERE DATE(next_followup_date) = CURDATE() "
              + "AND status NOT IN ('CONVERTED','NOT_INTERESTED','LOST') "
              + and(counsellorId, "counsellor_id"), counsellorId);

            s.followupsOverdue = scalar(con,
                "SELECT COUNT(*) FROM inquiries WHERE next_followup_date < CURDATE() "
              + "AND status NOT IN ('CONVERTED','NOT_INTERESTED','LOST') "
              + and(counsellorId, "counsellor_id"), counsellorId);

            // ── demos ──
            s.demosToday = scalar(con,
                "SELECT COUNT(*) FROM lead_demos d JOIN inquiries i ON i.inquiry_id = d.inquiry_id "
              + "WHERE d.demo_date = CURDATE() AND d.status = 'SCHEDULED' "
              + and(counsellorId, "i.counsellor_id"), counsellorId);

            s.demosUpcoming = scalar(con,
                "SELECT COUNT(*) FROM lead_demos d JOIN inquiries i ON i.inquiry_id = d.inquiry_id "
              + "WHERE d.demo_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY) "
              + "AND d.status = 'SCHEDULED' "
              + and(counsellorId, "i.counsellor_id"), counsellorId);

            // ── month to date ──
            s.leadsMtd = scalar(con,
                "SELECT COUNT(*) FROM inquiries WHERE created_at >= DATE_FORMAT(CURDATE(),'%Y-%m-01') "
              + and(counsellorId, "counsellor_id"), counsellorId);

            s.conversionsMtd = scalar(con,
                "SELECT COUNT(*) FROM students WHERE created_at >= DATE_FORMAT(CURDATE(),'%Y-%m-01') "
              + and(counsellorId, "counsellor_id"), counsellorId);

            s.revenueMtd = scalarLong(con,
                "SELECT COALESCE(SUM(p.amount),0) FROM payments p "
              + "JOIN students st ON st.student_id = p.student_id "
              + "WHERE p.payment_date >= DATE_FORMAT(CURDATE(),'%Y-%m-01') "
              + and(counsellorId, "st.counsellor_id"), counsellorId);

            // ── outstanding on this counsellor's students ──
            String due =
                  "SELECT COALESCE(SUM(GREATEST(total - paid, 0)),0) FROM ("
                + "  SELECT " + STUDENT_TOTAL + " AS total, "
                + "         COALESCE((SELECT SUM(pay.amount) FROM payments pay "
                + "                   WHERE pay.student_id = s.student_id),0) AS paid "
                + "  FROM students s "
                + "  LEFT JOIN student_fees sf ON sf.student_id = s.student_id "
                + "  LEFT JOIN fee_slabs  fs ON fs.slab_key   = s.fee_slab "
                + "  WHERE s.is_active = 1 " + and(counsellorId, "s.counsellor_id") + ") t";
            s.pendingFees = scalarLong(con, due, counsellorId);
        }
        return s;
    }

    /**
     * One row per counsellor for the management leaderboard, best conversion
     * rate first. ADMIN accounts are included because small institutes run with
     * the owner doing the counselling.
     */
    public List<SalesStats> leaderboard() throws SQLException {
        List<SalesStats> out = new ArrayList<>();
        String sql = "SELECT user_id, full_name FROM users "
                   + "WHERE is_active = 1 AND role IN ('COUNSELLOR','ADMIN') ORDER BY full_name";
        List<int[]> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(new int[] { rs.getInt("user_id") });
                names.add(rs.getString("full_name"));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            SalesStats st = load(Integer.valueOf(ids.get(i)[0]));
            st.counsellorName = names.get(i);
            out.add(st);
        }
        out.sort((a, b) -> {
            int c = Integer.compare(b.conversionsMtd, a.conversionsMtd);
            if (c != 0) {
                return c;
            }
            return Long.compare(b.revenueMtd, a.revenueMtd);
        });
        return out;
    }

    // ── helpers ──

    private static String where(Integer counsellorId, String col) {
        return (counsellorId == null) ? "" : "WHERE " + col + " = ? ";
    }

    private static String and(Integer counsellorId, String col) {
        return (counsellorId == null) ? "" : "AND " + col + " = ? ";
    }

    private static void bind(PreparedStatement ps, Integer counsellorId) throws SQLException {
        if (counsellorId != null) {
            ps.setInt(1, counsellorId);
        }
    }

    private int scalar(Connection con, String sql, Integer counsellorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, counsellorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private long scalarLong(Connection con, String sql, Integer counsellorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, counsellorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
