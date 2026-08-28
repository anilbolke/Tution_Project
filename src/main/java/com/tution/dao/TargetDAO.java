package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.CounsellorTarget;
import com.tution.util.DBConnection;

/**
 * Counsellor targets, and the live achievement figures behind them.
 *
 * WHAT COUNTS AS AN ADMISSION. A cancelled admission still counts if the family
 * paid something — the counsellor did close it — but an admission that was
 * deactivated without a single payment does not, because it was never really an
 * admission. That is the institute's rule and it is applied identically to the
 * count and to the booked revenue, so the two can never disagree.
 *
 * ATTRIBUTION is live from students.counsellor_id, matching every existing
 * report; reassigning a student moves their history with them.
 */
public class TargetDAO {

    /** Admissions that count toward a target: active, or cancelled but paid for. */
    private static final String COUNTS =
          "(s.is_active = 1 OR EXISTS (SELECT 1 FROM payments p2 WHERE p2.student_id = s.student_id))";

    /* ─── periods ─── */

    /** First day of the quarter containing {@code d}. */
    public static LocalDate quarterStart(LocalDate d) {
        int m = ((d.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(d.getYear(), m, 1);
    }
    public static LocalDate quarterEnd(LocalDate d) {
        return quarterStart(d).plusMonths(3).minusDays(1);
    }
    /** "Q3 2026 (Jul-Sep)" for a period start. */
    public static String quarterLabel(LocalDate start) {
        int q = (start.getMonthValue() - 1) / 3 + 1;
        String[] spans = { "Jan-Mar", "Apr-Jun", "Jul-Sep", "Oct-Dec" };
        return "Q" + q + " " + start.getYear() + " (" + spans[q - 1] + ")";
    }

    /* ─── read ─── */

    /**
     * Every target-holder (counsellors and admins) for one period, with their
     * target if one is set and their live actuals either way. People with no
     * target still appear — "no target set" is a different statement from 0%.
     */
    public List<CounsellorTarget> forPeriod(String periodType, LocalDate start, LocalDate end)
            throws SQLException {
        String sql =
              "SELECT u.user_id, u.full_name, u.role, "
            + "       t.target_id, t.admissions_target, t.revenue_target, t.revenue_basis, "
            + "       t.notes, t.set_by, "
            + "       (SELECT COUNT(*) FROM students s "
            + "          WHERE s.counsellor_id = u.user_id "
            + "            AND DATE(s.created_at) BETWEEN ? AND ? AND " + COUNTS + ") AS adm, "
            + "       (SELECT COALESCE(SUM(sf.total_payable),0) FROM students s "
            + "          JOIN student_fees sf ON sf.student_id = s.student_id "
            + "          WHERE s.counsellor_id = u.user_id "
            + "            AND DATE(s.created_at) BETWEEN ? AND ? AND " + COUNTS + ") AS booked, "
            + "       (SELECT COALESCE(SUM(p.amount),0) FROM payments p "
            + "          JOIN students s ON s.student_id = p.student_id "
            + "          WHERE s.counsellor_id = u.user_id "
            + "            AND p.payment_date BETWEEN ? AND ?) AS collected "
            + "  FROM users u "
            + "  LEFT JOIN counsellor_targets t "
            + "         ON t.counsellor_id = u.user_id "
            + "        AND t.period_type = ? AND t.period_start = ? "
            + " WHERE u.is_active = 1 AND u.role IN ('COUNSELLOR','ADMIN') "
            + " ORDER BY u.role = 'COUNSELLOR' DESC, u.full_name";

        List<CounsellorTarget> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String s = start.toString(), e = end.toString();
            int i = 1;
            ps.setString(i++, s); ps.setString(i++, e);   // admissions
            ps.setString(i++, s); ps.setString(i++, e);   // booked
            ps.setString(i++, s); ps.setString(i++, e);   // collected
            ps.setString(i++, periodType);
            ps.setString(i++, s);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs, periodType, s, e));
            }
        }
        return out;
    }

    /** One person's target for a period, actuals included. Never null. */
    public CounsellorTarget forCounsellor(int counsellorId, String periodType,
                                          LocalDate start, LocalDate end) throws SQLException {
        for (CounsellorTarget t : forPeriod(periodType, start, end)) {
            if (t.getCounsellorId() == counsellorId) return t;
        }
        CounsellorTarget empty = new CounsellorTarget();
        empty.setCounsellorId(counsellorId);
        empty.setPeriodType(periodType);
        empty.setPeriodStart(start.toString());
        empty.setPeriodEnd(end.toString());
        return empty;
    }

    private CounsellorTarget map(ResultSet rs, String periodType, String start, String end)
            throws SQLException {
        CounsellorTarget t = new CounsellorTarget();
        t.setCounsellorId(rs.getInt("user_id"));
        t.setCounsellorName(rs.getString("full_name"));
        t.setRole(rs.getString("role"));
        t.setPeriodType(periodType);
        t.setPeriodStart(start);
        t.setPeriodEnd(end);

        int id = rs.getInt("target_id");
        t.setUnset(rs.wasNull());
        t.setTargetId(id);
        t.setAdmissionsTarget(rs.getInt("admissions_target"));
        t.setRevenueTarget(rs.getLong("revenue_target"));
        String basis = rs.getString("revenue_basis");
        t.setRevenueBasis(basis == null ? "BOOKED" : basis);
        t.setNotes(rs.getString("notes"));
        t.setSetBy(rs.getString("set_by"));

        t.setAdmissionsActual(rs.getInt("adm"));
        long booked = rs.getLong("booked");
        long collected = rs.getLong("collected");
        t.setCollectedActual(collected);
        t.setRevenueActual("COLLECTED".equals(t.getRevenueBasis()) ? collected : booked);
        return t;
    }

    /* ─── write ─── */

    /**
     * Saves the whole grid in one transaction — a half-saved set of targets
     * would have some counsellors measured against a new number and others
     * against the old one.
     *
     * A row with both figures at zero and no note is treated as "no target" and
     * removed, so clearing a target is possible without a separate button.
     */
    public int saveAll(String periodType, LocalDate start, LocalDate end,
                       Map<Integer, int[]> admissionsAndRevenue,
                       Map<Integer, String> notes, String basis, String setBy) throws SQLException {
        Connection con = null;
        int saved = 0;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String ins = "INSERT INTO counsellor_targets "
                       + "(counsellor_id, period_type, period_start, period_end, "
                       + " admissions_target, revenue_target, revenue_basis, notes, set_by) "
                       + "VALUES (?,?,?,?,?,?,?,?,?) "
                       + "ON DUPLICATE KEY UPDATE period_end = VALUES(period_end), "
                       + " admissions_target = VALUES(admissions_target), "
                       + " revenue_target = VALUES(revenue_target), "
                       + " revenue_basis = VALUES(revenue_basis), "
                       + " notes = VALUES(notes), set_by = VALUES(set_by)";
            String del = "DELETE FROM counsellor_targets "
                       + "WHERE counsellor_id = ? AND period_type = ? AND period_start = ?";

            try (PreparedStatement psIns = con.prepareStatement(ins);
                 PreparedStatement psDel = con.prepareStatement(del)) {
                for (Map.Entry<Integer, int[]> e : admissionsAndRevenue.entrySet()) {
                    int uid = e.getKey();
                    int adm = e.getValue()[0];
                    long rev = e.getValue()[1];
                    String note = notes == null ? null : notes.get(uid);
                    boolean blank = adm <= 0 && rev <= 0 && (note == null || note.trim().isEmpty());

                    if (blank) {
                        psDel.setInt(1, uid);
                        psDel.setString(2, periodType);
                        psDel.setString(3, start.toString());
                        psDel.addBatch();
                        continue;
                    }
                    int i = 1;
                    psIns.setInt(i++, uid);
                    psIns.setString(i++, periodType);
                    psIns.setString(i++, start.toString());
                    psIns.setString(i++, end.toString());
                    psIns.setInt(i++, adm);
                    psIns.setLong(i++, rev);
                    psIns.setString(i++, basis);
                    if (note == null || note.trim().isEmpty()) psIns.setNull(i++, java.sql.Types.VARCHAR);
                    else psIns.setString(i++, note.trim());
                    psIns.setString(i++, setBy);
                    psIns.addBatch();
                    saved++;
                }
                psDel.executeBatch();
                psIns.executeBatch();
            }
            con.commit();
            return saved;
        } catch (SQLException e) {
            if (con != null) try { con.rollback(); } catch (SQLException ignore) { }
            throw e;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) { }
        }
    }

    /** The previous period's targets, keyed by user — for "copy from last quarter". */
    public Map<Integer, int[]> previousTargets(String periodType, LocalDate prevStart)
            throws SQLException {
        Map<Integer, int[]> out = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT counsellor_id, admissions_target, revenue_target "
                   + "FROM counsellor_targets WHERE period_type = ? AND period_start = ?")) {
            ps.setString(1, periodType);
            ps.setString(2, prevStart.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("counsellor_id"),
                            new int[] { rs.getInt("admissions_target"), (int) rs.getLong("revenue_target") });
                }
            }
        }
        return out;
    }
}
