package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.OmrTemplate;
import com.tution.util.DBConnection;

/** Loads OMR sheet layouts. */
public class OmrTemplateDAO {

    /** Qualified with the table alias: `exams` also has a template_id, so an
     *  unqualified list makes the join in {@link #forExam(int)} ambiguous. */
    private static final String COLS =
        "t.template_id, t.name, t.blocks, t.rows_per_block, t.roll_cols, "
      + "t.ring_ink, t.timing_edge, t.geometry_json";

    public OmrTemplate findById(int templateId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT " + COLS + " FROM omr_templates t WHERE t.template_id = ?")) {
            ps.setInt(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * The layout an exam scans with. Falls back to the legacy 180-question
     * template when the exam has none, so the older scan flow keeps working.
     */
    public OmrTemplate forExam(int examId) throws SQLException {
        String sql = "SELECT " + COLS + " FROM omr_templates t "
                   + "JOIN exams e ON e.template_id = t.template_id WHERE e.exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : OmrTemplate.legacy180();
            }
        }
    }

    public List<OmrTemplate> findAll() throws SQLException {
        List<OmrTemplate> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT " + COLS + " FROM omr_templates t WHERE t.is_active = 1 ORDER BY t.template_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    /** Stores tuned geometry back against a template (used by the calibration screen). */
    public int saveGeometry(int templateId, String geometryJson, boolean calibrated) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE omr_templates SET geometry_json = ? WHERE template_id = ?")) {
            ps.setString(1, geometryJson);
            ps.setInt(2, templateId);
            return ps.executeUpdate();
        }
    }

    private OmrTemplate map(ResultSet rs) throws SQLException {
        OmrTemplate t = new OmrTemplate();
        t.setTemplateId(rs.getInt("template_id"));
        t.setName(rs.getString("name"));
        t.setBlocks(rs.getInt("blocks"));
        t.setRowsPerBlock(rs.getInt("rows_per_block"));
        t.setRollCols(rs.getInt("roll_cols"));
        t.setRingInk(rs.getString("ring_ink"));
        t.setTimingEdge(rs.getString("timing_edge"));
        t.applyGeometry(rs.getString("geometry_json"));
        return t;
    }
}
