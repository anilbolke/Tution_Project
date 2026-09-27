package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.util.DBConnection;

/**
 * Lookup lists that used to be hardcoded as &lt;option&gt; tags in inquiry.jsp and
 * admission.jsp — lead sources, courses, batches — plus the counsellor list.
 *
 * Keeping them here means adding a course is a row in a table, not an edit in
 * two JSPs that were already drifting apart.
 */
public class MasterDAO {

    /** Active lead sources, in display order. */
    public List<String> leadSources() throws SQLException {
        return names("SELECT name FROM lead_sources WHERE is_active = 1 ORDER BY sort_order, name");
    }

    /** Active course names. */
    public List<String> courses() throws SQLException {
        return names("SELECT name FROM courses WHERE is_active = 1 ORDER BY name");
    }

    /** Active batch names. */
    public List<String> batches() throws SQLException {
        return names("SELECT name FROM batches WHERE is_active = 1 ORDER BY name");
    }

    /** Distinct branch names currently in use on batches. */
    public List<String> branches() throws SQLException {
        return names("SELECT DISTINCT branch FROM batches "
                   + "WHERE is_active = 1 AND branch IS NOT NULL AND branch <> '' ORDER BY branch");
    }

    /**
     * user_id → full name for everyone who can own a lead (ADMIN and COUNSELLOR).
     * ADMIN is included because small institutes run with the owner doing the
     * counselling and no separate counsellor account.
     */
    public Map<Integer, String> counsellors() throws SQLException {
        return counsellors(null);
    }

    /**
     * As {@link #counsellors()}, limited to a scope (Scope.of(user)): a
     * counsellor gets themself, an ABM their team, null gets everyone.
     */
    public Map<Integer, String> counsellors(Integer scope) throws SQLException {
        Map<Integer, String> map = new LinkedHashMap<>();
        String sql = "SELECT user_id, full_name FROM users "
                   + "WHERE is_active = 1 AND role IN ('COUNSELLOR','ABM','ADMIN') "
                   + (scope == null ? "" : "AND " + Scope.teamOf("user_id") + " ")
                   + "ORDER BY role IN ('COUNSELLOR','ABM') DESC, full_name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (scope != null) ps.setInt(1, scope);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("user_id"), rs.getString("full_name"));
                }
            }
        }
        return map;
    }

    /**
     * A counsellor's WhatsApp number, or null when none is on file. Used to
     * alert them that a lead has just landed in their queue.
     */
    public String counsellorMobile(int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT mobile FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String m = rs.getString("mobile");
                    return (m == null || m.trim().isEmpty()) ? null : m.trim();
                }
            }
        }
        return null;
    }

    private List<String> names(String sql) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        }
        return list;
    }
}
