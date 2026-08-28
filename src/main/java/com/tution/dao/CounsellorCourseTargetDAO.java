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

import com.tution.model.CounsellorCourseTarget;
import com.tution.util.DBConnection;

/**
 * One counsellor's targets, broken down by course — the cross of
 * {@link TargetDAO} (counsellor only) and {@link CourseTargetDAO} (course
 * only). The page picks a counsellor first, so every method here is scoped to
 * one counsellor_id and returns one row per active course.
 *
 * Same institute rules and the same fee_plans.programme → courses.name
 * attribution as CourseTargetDAO, with an added s.counsellor_id filter. See
 * that class for the collation note (fee_plans is utf8mb4_0900_ai_ci, courses
 * and students are utf8mb4_unicode_ci).
 */
public class CounsellorCourseTargetDAO {

    private static final String COUNTS =
          "(s.is_active = 1 OR EXISTS (SELECT 1 FROM payments p2 WHERE p2.student_id = s.student_id))";

    private static final String FP_JOIN =
          "JOIN fee_plans fp ON fp.code COLLATE utf8mb4_unicode_ci = s.plan_code ";

    private static final String FP_MATCH =
          "fp.programme COLLATE utf8mb4_unicode_ci = c.name ";

    /* ─── read ─── */

    /** Every active course for one counsellor and period, target if set, actuals either way. */
    public List<CounsellorCourseTarget> forCounsellor(int counsellorId, String periodType,
                                                       LocalDate start, LocalDate end) throws SQLException {
        String sql =
              "SELECT c.course_id, c.name AS course_name, "
            + "       t.target_id, t.admissions_target, t.revenue_target, t.revenue_basis, "
            + "       t.notes, t.set_by, "
            + "       (SELECT COUNT(*) FROM students s " + FP_JOIN
            + "          WHERE " + FP_MATCH
            + "            AND s.counsellor_id = ? "
            + "            AND DATE(s.created_at) BETWEEN ? AND ? AND " + COUNTS + ") AS adm, "
            + "       (SELECT COALESCE(SUM(sf.total_payable),0) FROM students s " + FP_JOIN
            + "          JOIN student_fees sf ON sf.student_id = s.student_id "
            + "          WHERE " + FP_MATCH
            + "            AND s.counsellor_id = ? "
            + "            AND DATE(s.created_at) BETWEEN ? AND ? AND " + COUNTS + ") AS booked, "
            + "       (SELECT COALESCE(SUM(p.amount),0) FROM payments p "
            + "          JOIN students s ON s.student_id = p.student_id " + FP_JOIN
            + "          WHERE " + FP_MATCH
            + "            AND s.counsellor_id = ? "
            + "            AND p.payment_date BETWEEN ? AND ?) AS collected "
            + "  FROM courses c "
            + "  LEFT JOIN counsellor_course_targets t "
            + "         ON t.course_id = c.course_id AND t.counsellor_id = ? "
            + "        AND t.period_type = ? AND t.period_start = ? "
            + " WHERE c.is_active = 1 "
            + " ORDER BY c.name";

        List<CounsellorCourseTarget> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String s = start.toString(), e = end.toString();
            int i = 1;
            ps.setInt(i++, counsellorId);    ps.setString(i++, s); ps.setString(i++, e);   // admissions
            ps.setInt(i++, counsellorId);    ps.setString(i++, s); ps.setString(i++, e);   // booked
            ps.setInt(i++, counsellorId);    ps.setString(i++, s); ps.setString(i++, e);   // collected
            ps.setInt(i++, counsellorId);
            ps.setString(i++, periodType);
            ps.setString(i++, s);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs, counsellorId, periodType, s, e));
            }
        }
        return out;
    }

    private CounsellorCourseTarget map(ResultSet rs, int counsellorId, String periodType,
                                       String start, String end) throws SQLException {
        CounsellorCourseTarget t = new CounsellorCourseTarget();
        t.setCounsellorId(counsellorId);
        t.setCourseId(rs.getInt("course_id"));
        t.setCourseName(rs.getString("course_name"));
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

    /** Saves one counsellor's whole course grid in one transaction. */
    public int saveAll(int counsellorId, String periodType, LocalDate start, LocalDate end,
                       Map<Integer, int[]> admissionsAndRevenue,
                       Map<Integer, String> notes, String basis, String setBy) throws SQLException {
        Connection con = null;
        int saved = 0;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String ins = "INSERT INTO counsellor_course_targets "
                       + "(counsellor_id, course_id, period_type, period_start, period_end, "
                       + " admissions_target, revenue_target, revenue_basis, notes, set_by) "
                       + "VALUES (?,?,?,?,?,?,?,?,?,?) "
                       + "ON DUPLICATE KEY UPDATE period_end = VALUES(period_end), "
                       + " admissions_target = VALUES(admissions_target), "
                       + " revenue_target = VALUES(revenue_target), "
                       + " revenue_basis = VALUES(revenue_basis), "
                       + " notes = VALUES(notes), set_by = VALUES(set_by)";
            String del = "DELETE FROM counsellor_course_targets "
                       + "WHERE counsellor_id = ? AND course_id = ? AND period_type = ? AND period_start = ?";

            try (PreparedStatement psIns = con.prepareStatement(ins);
                 PreparedStatement psDel = con.prepareStatement(del)) {
                for (Map.Entry<Integer, int[]> e : admissionsAndRevenue.entrySet()) {
                    int cid = e.getKey();
                    int adm = e.getValue()[0];
                    long rev = e.getValue()[1];
                    String note = notes == null ? null : notes.get(cid);
                    boolean blank = adm <= 0 && rev <= 0 && (note == null || note.trim().isEmpty());

                    if (blank) {
                        psDel.setInt(1, counsellorId);
                        psDel.setInt(2, cid);
                        psDel.setString(3, periodType);
                        psDel.setString(4, start.toString());
                        psDel.addBatch();
                        continue;
                    }
                    int i = 1;
                    psIns.setInt(i++, counsellorId);
                    psIns.setInt(i++, cid);
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

    /** The previous period's targets for this counsellor, keyed by course — for "copy from last quarter". */
    public Map<Integer, int[]> previousTargets(int counsellorId, String periodType, LocalDate prevStart)
            throws SQLException {
        Map<Integer, int[]> out = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT course_id, admissions_target, revenue_target "
                   + "FROM counsellor_course_targets "
                   + "WHERE counsellor_id = ? AND period_type = ? AND period_start = ?")) {
            ps.setInt(1, counsellorId);
            ps.setString(2, periodType);
            ps.setString(3, prevStart.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("course_id"),
                            new int[] { rs.getInt("admissions_target"), (int) rs.getLong("revenue_target") });
                }
            }
        }
        return out;
    }
}
