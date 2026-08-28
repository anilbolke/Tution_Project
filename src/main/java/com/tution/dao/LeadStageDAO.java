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
 * Lead Stage and its dependent Lead Sub Stage.
 *
 * Both are controlled lists in the database rather than hardcoded options, so
 * the institute can add a stage or reword a sub stage without a code change —
 * five of the ten stages have no sub stages yet, and filling those in should be
 * an INSERT, not a release.
 */
public class LeadStageDAO {

    /** Active stages in display order. */
    public List<String> stages() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name FROM lead_stages WHERE is_active = 1 ORDER BY sort_order, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    /**
     * stage → its sub stages, in display order. Loaded whole because the form
     * needs every list at once to switch between them without a round trip;
     * it is a few dozen short strings.
     */
    public Map<String, List<String>> subStagesByStage() throws SQLException {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String sql = "SELECT s.name AS stage, v.name AS sub "
                   + "FROM lead_stages s "
                   + "LEFT JOIN lead_sub_stages v ON v.stage_id = s.stage_id AND v.is_active = 1 "
                   + "WHERE s.is_active = 1 "
                   + "ORDER BY s.sort_order, s.name, v.sort_order, v.name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String stage = rs.getString("stage");
                String sub = rs.getString("sub");
                List<String> list = out.get(stage);
                if (list == null) { list = new ArrayList<>(); out.put(stage, list); }
                if (sub != null) list.add(sub);      // LEFT JOIN: stage with no sub stages
            }
        }
        return out;
    }

    /**
     * True when this sub stage genuinely belongs to this stage.
     *
     * The cascade is done in the browser, so without this a hand-built POST — or
     * a stale form left open while the master list changed — could store a pair
     * that never appears together in the UI, e.g. stage "Hot" with sub stage
     * "Financial Issues". A blank sub stage is always allowed: five stages have
     * no list yet.
     */
    public boolean isValidPair(String stage, String subStage) throws SQLException {
        if (subStage == null || subStage.trim().isEmpty()) return true;
        if (stage == null || stage.trim().isEmpty()) return false;   // sub stage without a stage
        String sql = "SELECT 1 FROM lead_sub_stages v JOIN lead_stages s ON s.stage_id = v.stage_id "
                   + "WHERE s.name = ? AND v.name = ? AND v.is_active = 1 AND s.is_active = 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, stage.trim());
            ps.setString(2, subStage.trim());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** True when the stage itself is a known, active one. Blank is allowed. */
    public boolean isValidStage(String stage) throws SQLException {
        if (stage == null || stage.trim().isEmpty()) return true;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT 1 FROM lead_stages WHERE name = ? AND is_active = 1")) {
            ps.setString(1, stage.trim());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /**
     * The stage → sub stage map as a JSON literal for the page's cascade script.
     * Hand-rolled: the project carries no JSON library and this is a flat map of
     * string arrays.
     */
    public String subStagesJson() throws SQLException {
        StringBuilder sb = new StringBuilder("{");
        boolean firstStage = true;
        for (Map.Entry<String, List<String>> e : subStagesByStage().entrySet()) {
            if (!firstStage) sb.append(',');
            firstStage = false;
            sb.append(js(e.getKey())).append(":[");
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(js(e.getValue().get(i)));
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

    private static String js(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '<':  b.append("\\u003c"); break;   // never break out of <script>
                case '>':  b.append("\\u003e"); break;
                case '&':  b.append("\\u0026"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                default:   b.append(c);
            }
        }
        return b.append('"').toString();
    }
}
