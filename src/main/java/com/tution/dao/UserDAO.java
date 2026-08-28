package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import com.tution.model.User;
import com.tution.model.UserAudit;
import com.tution.util.DBConnection;
import com.tution.util.PasswordUtil;

/** Data-access for the users (login) table. */
public class UserDAO {

    /**
     * Validates credentials. Returns the matching {@link User} or {@code null}.
     * Uses a parameterised query (no SQL injection) and SHA-256 hash compare.
     */
    public User authenticate(String username, String rawPassword) throws SQLException {
        String hash = PasswordUtil.sha256(rawPassword);
        String sql  = "SELECT user_id, username, full_name, email, role "
                    + "FROM users WHERE username = ? AND password = ? AND is_active = 1";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, hash);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setUserId(rs.getInt("user_id"));
                    u.setUsername(rs.getString("username"));
                    u.setFullName(rs.getString("full_name"));
                    u.setEmail(rs.getString("email"));
                    u.setRole(rs.getString("role"));
                    updateLastLogin(con, u.getUserId());
                    return u;
                }
            }
        }
        return null;
    }

    /**
     * Everyone with a login, for the staff directory. Active accounts first,
     * then by role and name.
     *
     * The password column is not selected. There is no reason for a hash to
     * travel to a JSP, and a directory that never loads one cannot leak one.
     */
    public java.util.List<User> allStaff() throws SQLException {
        String sql = "SELECT user_id, username, full_name, email, mobile, role, is_active, "
                   + "       created_at, last_login "
                   + "  FROM users "
                   + " ORDER BY is_active DESC, FIELD(role,'ADMIN','ACCOUNTANT','HR','STAFF',"
                   + "         'COUNSELLOR','TEACHER'), full_name";
        java.util.List<User> out = new java.util.ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setUserId(rs.getInt("user_id"));
                u.setUsername(rs.getString("username"));
                u.setFullName(rs.getString("full_name"));
                u.setEmail(rs.getString("email"));
                u.setMobile(rs.getString("mobile"));
                u.setRole(rs.getString("role"));
                u.setActive(rs.getInt("is_active") == 1);
                Timestamp created = rs.getTimestamp("created_at");
                u.setCreatedAt(created == null ? null : created.toString().substring(0, 10));
                Timestamp seen = rs.getTimestamp("last_login");
                u.setLastLogin(seen == null ? null : seen.toString().substring(0, 16));
                out.add(u);
            }
        }
        return out;
    }

    /* ─────────────────── creating and managing logins ─────────────────── */

    /** One account by id, or null. Never selects the password. */
    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, username, full_name, email, mobile, role, is_active "
                   + "  FROM users WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                User u = new User();
                u.setUserId(rs.getInt("user_id"));
                u.setUsername(rs.getString("username"));
                u.setFullName(rs.getString("full_name"));
                u.setEmail(rs.getString("email"));
                u.setMobile(rs.getString("mobile"));
                u.setRole(rs.getString("role"));
                u.setActive(rs.getInt("is_active") == 1);
                return u;
            }
        }
    }

    public boolean usernameTaken(String username) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * How many ADMIN accounts are still switched on.
     *
     * Guards the lockout case: demoting or disabling the last active admin would
     * leave nobody able to manage roles, and no way back in through the UI.
     */
    public int activeAdminCount() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND is_active = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Creates a login, and records that it was created, together or not at all.
     *
     * The audit row goes in first and inside the transaction, the same ordering
     * the fund and expense modules use: an account that exists with no record of
     * who made it is the one case nobody could later explain.
     *
     * @return the new user_id, or 0 if the username was taken
     */
    public int createUser(User u, String rawPassword, User by) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int newId;
            String sql = "INSERT INTO users (username, password, full_name, email, mobile, "
                       + "role, is_active) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.getUsername());
                ps.setString(2, PasswordUtil.sha256(rawPassword));
                ps.setString(3, u.getFullName());
                setNullable(ps, 4, u.getEmail());
                setNullable(ps, 5, u.getMobile());
                ps.setString(6, u.getRole());
                ps.setInt(7, u.isActive() ? 1 : 0);
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) {
                    newId = k.next() ? k.getInt(1) : 0;
                }
            }
            if (newId == 0) {
                con.rollback();
                return 0;
            }
            insertAudit(con, newId, u.getUsername(), u.getFullName(), UserAudit.CREATE,
                        "Created as " + u.getRole(), by);
            con.commit();
            return newId;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            closeQuietly(con);
        }
    }

    /** Changes name, email and mobile. Not the role, and not the password. */
    public boolean updateDetails(User u, User by) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            boolean done;
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET full_name = ?, email = ?, mobile = ? WHERE user_id = ?")) {
                ps.setString(1, u.getFullName());
                setNullable(ps, 2, u.getEmail());
                setNullable(ps, 3, u.getMobile());
                ps.setInt(4, u.getUserId());
                done = ps.executeUpdate() > 0;
            }
            if (!done) { con.rollback(); return false; }
            insertAudit(con, u.getUserId(), u.getUsername(), u.getFullName(),
                        UserAudit.UPDATE, "Name, email or mobile updated", by);
            con.commit();
            return true;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            closeQuietly(con);
        }
    }

    /** Moves an account to a different role. */
    public boolean setRole(User target, String newRole, User by) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            boolean done;
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET role = ? WHERE user_id = ?")) {
                ps.setString(1, newRole);
                ps.setInt(2, target.getUserId());
                done = ps.executeUpdate() > 0;
            }
            if (!done) { con.rollback(); return false; }
            insertAudit(con, target.getUserId(), target.getUsername(), target.getFullName(),
                        UserAudit.ROLE, target.getRole() + " to " + newRole, by);
            con.commit();
            return true;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            closeQuietly(con);
        }
    }

    /** Switches an account on or off. Nothing is ever deleted. */
    public boolean setActive(User target, boolean active, User by) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            boolean done;
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET is_active = ? WHERE user_id = ?")) {
                ps.setInt(1, active ? 1 : 0);
                ps.setInt(2, target.getUserId());
                done = ps.executeUpdate() > 0;
            }
            if (!done) { con.rollback(); return false; }
            insertAudit(con, target.getUserId(), target.getUsername(), target.getFullName(),
                        active ? UserAudit.ENABLE : UserAudit.DISABLE,
                        active ? "Account switched on" : "Account switched off", by);
            con.commit();
            return true;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            closeQuietly(con);
        }
    }

    /**
     * Sets a new password.
     *
     * The old one is neither read nor compared — this is an administrator
     * resetting a forgotten password, not a user changing their own, and there
     * is nothing to verify against. The audit row records that it happened and
     * deliberately records nothing about the value.
     */
    public boolean resetPassword(User target, String rawPassword, User by) throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            boolean done;
            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET password = ? WHERE user_id = ?")) {
                ps.setString(1, PasswordUtil.sha256(rawPassword));
                ps.setInt(2, target.getUserId());
                done = ps.executeUpdate() > 0;
            }
            if (!done) { con.rollback(); return false; }
            insertAudit(con, target.getUserId(), target.getUsername(), target.getFullName(),
                        UserAudit.PASSWORD, "Password reset by an administrator", by);
            con.commit();
            return true;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            closeQuietly(con);
        }
    }

    /** The change history, newest first, across every account. */
    public java.util.List<UserAudit> auditTrail(int limit) throws SQLException {
        String sql = "SELECT * FROM user_audit ORDER BY acted_at DESC, audit_id DESC LIMIT ?";
        java.util.List<UserAudit> out = new java.util.ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserAudit a = new UserAudit();
                    a.setAuditId(rs.getInt("audit_id"));
                    a.setTargetUserId(rs.getInt("target_user_id"));
                    a.setTargetUsername(rs.getString("target_username"));
                    a.setTargetName(rs.getString("target_name"));
                    a.setAction(rs.getString("action"));
                    a.setDetail(rs.getString("detail"));
                    a.setActedBy(rs.getString("acted_by"));
                    int id = rs.getInt("acted_by_id");
                    a.setActedById(rs.wasNull() ? null : id);
                    Timestamp t = rs.getTimestamp("acted_at");
                    a.setActedAt(t == null ? null : t.toString().substring(0, 16));
                    out.add(a);
                }
            }
        }
        return out;
    }

    private void insertAudit(Connection con, int targetId, String username, String name,
                             String action, String detail, User by) throws SQLException {
        String sql = "INSERT INTO user_audit (target_user_id, target_username, target_name, "
                   + "action, detail, acted_by, acted_by_id) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, targetId);
            ps.setString(2, username);
            ps.setString(3, name);
            ps.setString(4, action);
            setNullable(ps, 5, detail);
            setNullable(ps, 6, by == null ? null : by.getFullName());
            if (by == null) ps.setNull(7, java.sql.Types.INTEGER);
            else            ps.setInt(7, by.getUserId());
            ps.executeUpdate();
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(idx, java.sql.Types.VARCHAR);
        else                                 ps.setString(idx, v.trim());
    }

    private static void closeQuietly(Connection con) {
        if (con != null) {
            try { con.setAutoCommit(true); } catch (SQLException ignore) { }
            try { con.close(); } catch (SQLException ignore) { }
        }
    }

    private void updateLastLogin(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps =
                 con.prepareStatement("UPDATE users SET last_login = ? WHERE user_id = ?")) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }
}
