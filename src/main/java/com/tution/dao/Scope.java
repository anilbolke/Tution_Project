package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.tution.model.User;
import com.tution.util.DBConnection;

/**
 * Row-level scope: whose leads, students and sales figures a user sees.
 *
 *   counsellor   their own
 *   ABM          their own + everyone whose users.reports_to is the ABM
 *   everyone else  everything (management, office, academic roles)
 *
 * The scope is carried around as one Integer, exactly as before this class
 * existed — null for "everything", else the user id whose team bounds the view.
 * What changed is the SQL: {@link #teamOf(String)} matches the user AND their
 * direct reports, so a counsellor (nobody reports to them) sees exactly what
 * they always saw and an ABM sees their team, with no DAO signature changing.
 *
 * One level only, on purpose: the managers above an ABM are not scoped at all.
 */
public final class Scope {

    private Scope() {}

    /** null = sees everything; else the id whose team bounds what this user sees. */
    public static Integer of(User u) {
        return (u != null && u.isCounsellor() && !u.isAdmin()) ? Integer.valueOf(u.getUserId()) : null;
    }

    /**
     * SQL condition: {@code col} is the user or one of their direct reports.
     * Binds ONE parameter — the user id — where {@code col = ?} used to.
     */
    public static String teamOf(String col) {
        return col + " IN (SELECT su.user_id FROM users su WHERE ? IN (su.user_id, su.reports_to))";
    }

    /** As {@link #teamOf(String)} with the id inlined — for queries whose binds are positional. */
    public static String teamOf(String col, int userId) {
        return col + " IN (SELECT su.user_id FROM users su WHERE " + userId
             + " IN (su.user_id, su.reports_to))";
    }

    /** The user and their direct reports. */
    public static Set<Integer> teamIds(int userId) throws SQLException {
        Set<Integer> out = new LinkedHashSet<>();
        out.add(userId);
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT user_id FROM users WHERE reports_to = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getInt(1));
            }
        }
        return out;
    }

    /**
     * May this user open/edit a record owned by {@code ownerId}? Unscoped users
     * always may; an unowned record is open to all (it is waiting to be picked
     * up); otherwise the owner must be in the user's team.
     */
    public static boolean mayAccess(User u, Integer ownerId) {
        Integer scope = of(u);
        if (scope == null || ownerId == null || ownerId.intValue() == scope.intValue()) return true;
        try {
            return teamIds(scope).contains(ownerId);
        } catch (SQLException e) {
            return false;           // fail closed
        }
    }
}
