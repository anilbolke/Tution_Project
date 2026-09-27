package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.tution.model.User;
import com.tution.util.DBConnection;

/**
 * The role x activity matrix (Doc/Mapping/Work Flow.xlsx), and the one place
 * that answers "may this role open this page".
 *
 * Tables: activities, activity_paths, role_activity, role_activity_log — see
 * database/migrations/2026-09-role-activity-matrix.sql.
 *
 * The matrix is read once into memory and swapped whole on {@link #reload()},
 * which the /role-mapping screen calls after a save — so a change applies to
 * everybody on their next click, no re-login needed.
 *
 * If it cannot be loaded (tables missing, DB down) every check says NO for
 * everyone but ADMIN: failing closed means a broken deploy locks staff out of
 * pages rather than opening every page to every role.
 *
 * ADMIN is never in the table — it passes every check in {@link User#can}.
 */
public final class AccessDAO {

    /** One row of the activities table. */
    public static final class Activity {
        public final String code, tab, label;
        Activity(String code, String tab, String label) {
            this.code = code; this.tab = tab; this.label = label;
        }
    }

    /** An immutable copy of the three tables. */
    private static final class Snapshot {
        final List<Activity> activities;
        final Map<String, Set<String>> grants;      // role -> activities
        final Map<String, Set<String>> pathActs;    // path -> activities (any of)
        Snapshot(List<Activity> a, Map<String, Set<String>> g, Map<String, Set<String>> p) {
            activities = a; grants = g; pathActs = p;
        }
    }

    private static volatile Snapshot snap;
    private static volatile long lastFailure;

    private AccessDAO() {}

    /* ─────────────────────────── the checks ─────────────────────────── */

    /** True when the role holds the activity. ADMIN is decided by the caller. */
    public static boolean allowed(String role, String activity) {
        Snapshot s = snapshot();
        if (s == null || role == null || activity == null) return false;
        Set<String> g = s.grants.get(role.toUpperCase());
        return g != null && g.contains(activity);
    }

    /** True when the role holds any activity whose code starts with the prefix. */
    public static boolean allowedAny(String role, String prefix) {
        Snapshot s = snapshot();
        if (s == null || role == null) return false;
        Set<String> g = s.grants.get(role.toUpperCase());
        if (g == null) return false;
        for (String a : g) if (a.startsWith(prefix)) return true;
        return false;
    }

    /**
     * May this user open this path with these parameters.
     *
     * Three paths are decided by a parameter rather than by the table:
     *   /hr                       its tab (attendance/leave/salary) — and HR_STAFF,
     *                             because /hr is the register of EVERYONE's days,
     *                             leave and pay. The sheet's "Attendance: YES" for
     *                             every role means their OWN (phase 3 self-service),
     *                             not a colleague's salary.
     *   /reports, /report-export  the report type; with none, any report
     *
     * Anything else must be in activity_paths. A path in no list is closed to
     * everyone but ADMIN, so a screen added later is invisible until it is
     * mapped rather than open to every role until somebody notices.
     */
    public static boolean mayOpen(User u, String path, HttpServletRequest req) {
        if (u == null) return false;
        if (u.isAdmin()) return true;

        if ("/hr".equals(path)) {
            String tab = req == null ? null : req.getParameter("tab");
            String act = "leave".equals(tab) ? "HR_LEAVE"
                       : "salary".equals(tab) ? "HR_SALARY" : "HR_ATTENDANCE";
            return u.can("HR_STAFF") && u.can(act);
        }
        if ("/reports".equals(path) || "/report-export".equals(path)) {
            String type = req == null ? null : req.getParameter("type");
            if (type == null || type.isEmpty()) return u.canAny("RPT_");
            return u.can(reportActivity(type));
        }
        return mayOpenPath(u, path);
    }

    /** The table-only half of {@link #mayOpen}: no parameters looked at. */
    public static boolean mayOpenPath(User u, String path) {
        if (u == null) return false;
        if (u.isAdmin()) return true;
        Snapshot s = snapshot();
        if (s == null) return false;
        Set<String> acts = s.pathActs.get(path);
        if (acts == null) return false;
        for (String a : acts) if (u.can(a)) return true;
        return false;
    }

    /** "fund-statement" -> "RPT_FUND_STATEMENT" — the naming the seed script uses. */
    public static String reportActivity(String reportType) {
        return "RPT_" + reportType.trim().toUpperCase().replace('-', '_');
    }

    /* ─────────────────────────── the mapping screen ─────────────────────────── */

    /** Every activity, in sheet order. Empty if the matrix is not loaded. */
    public static List<Activity> activities() {
        Snapshot s = snapshot();
        return s == null ? Collections.<Activity>emptyList() : s.activities;
    }

    /** The activities a role holds (a copy). */
    public static Set<String> grantsOf(String role) {
        Snapshot s = snapshot();
        Set<String> g = (s == null || role == null) ? null : s.grants.get(role.toUpperCase());
        return g == null ? new HashSet<String>() : new HashSet<String>(g);
    }

    /**
     * Replaces the whole matrix for the given roles, logs what changed in words,
     * and reloads. Roles not in {@code matrix} are left untouched.
     *
     * @return the number of cells that changed
     */
    public static int save(Map<String, Set<String>> matrix, User by) throws SQLException {
        Snapshot before = snapshot();
        if (before == null) throw new SQLException("The access matrix is not loaded.");

        Set<String> known = new HashSet<>();
        for (Activity a : before.activities) known.add(a.code);

        List<String> changes = new ArrayList<>();
        int changed = 0;
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement del = con.prepareStatement(
                         "DELETE FROM role_activity WHERE role_code = ?");
                 PreparedStatement ins = con.prepareStatement(
                         "INSERT INTO role_activity (role_code, activity_code) VALUES (?, ?)")) {
                for (Map.Entry<String, Set<String>> e : matrix.entrySet()) {
                    String role = e.getKey().toUpperCase();
                    if ("ADMIN".equals(role)) continue;          // never stored
                    Set<String> want = new LinkedHashSet<>();
                    for (String a : e.getValue()) if (known.contains(a)) want.add(a);

                    Set<String> had = before.grants.containsKey(role)
                            ? before.grants.get(role) : Collections.<String>emptySet();
                    List<String> added = new ArrayList<>(), removed = new ArrayList<>();
                    for (String a : want) if (!had.contains(a)) added.add(a);
                    for (String a : had)  if (!want.contains(a)) removed.add(a);
                    if (added.isEmpty() && removed.isEmpty()) continue;

                    changed += added.size() + removed.size();
                    StringBuilder sb = new StringBuilder(role).append(':');
                    if (!added.isEmpty())   sb.append(" +").append(String.join(" +", added));
                    if (!removed.isEmpty()) sb.append(" -").append(String.join(" -", removed));
                    changes.add(sb.toString());

                    del.setString(1, role);
                    del.executeUpdate();
                    for (String a : want) {
                        ins.setString(1, role);
                        ins.setString(2, a);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                if (!changes.isEmpty()) {
                    try (PreparedStatement log = con.prepareStatement(
                            "INSERT INTO role_activity_log (detail, acted_by, acted_by_id) VALUES (?, ?, ?)")) {
                        log.setString(1, String.join("\n", changes));
                        log.setString(2, by == null ? null : by.getFullName());
                        if (by == null) log.setNull(3, java.sql.Types.INTEGER);
                        else            log.setInt(3, by.getUserId());
                        log.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        }
        reload();
        return changed;
    }

    /** The last few saves: {when, who, detail}. */
    public static List<String[]> recentLog(int limit) throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT DATE_FORMAT(acted_at, '%d %b %Y %H:%i'), acted_by, detail "
                   + "FROM role_activity_log ORDER BY log_id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new String[] { rs.getString(1), rs.getString(2), rs.getString(3) });
            }
        }
        return out;
    }

    /* ─────────────────────────── loading ─────────────────────────── */

    /** Re-reads the tables. On failure the previous snapshot is kept. */
    public static synchronized void reload() {
        try {
            snap = load();
        } catch (SQLException e) {
            lastFailure = System.currentTimeMillis();
            System.err.println("[AccessDAO] could not load the role/activity matrix: " + e.getMessage());
        }
    }

    private static Snapshot snapshot() {
        Snapshot s = snap;
        // Retry a failed load at most every 30 s, not on every request.
        if (s == null && System.currentTimeMillis() - lastFailure > 30_000L) {
            reload();
            s = snap;
        }
        return s;
    }

    private static Snapshot load() throws SQLException {
        List<Activity> acts = new ArrayList<>();
        Map<String, Set<String>> grants = new HashMap<>();
        Map<String, Set<String>> paths = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT activity_code, erp_tab, label FROM activities ORDER BY sort_order")) {
                while (rs.next()) acts.add(new Activity(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
            try (ResultSet rs = st.executeQuery("SELECT role_code, activity_code FROM role_activity")) {
                while (rs.next()) {
                    grants.computeIfAbsent(rs.getString(1).toUpperCase(), k -> new HashSet<>())
                          .add(rs.getString(2));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT path, activity_code FROM activity_paths")) {
                while (rs.next()) {
                    paths.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
                }
            }
        }
        if (acts.isEmpty()) throw new SQLException("activities table is empty");
        for (Map.Entry<String, Set<String>> e : grants.entrySet()) e.setValue(Collections.unmodifiableSet(e.getValue()));
        for (Map.Entry<String, Set<String>> e : paths.entrySet())  e.setValue(Collections.unmodifiableSet(e.getValue()));
        return new Snapshot(Collections.unmodifiableList(acts), grants, paths);
    }
}
