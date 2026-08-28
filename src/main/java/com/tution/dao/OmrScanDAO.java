package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.OmrScan;
import com.tution.util.DBConnection;

/** Data-access for OMR scans. */
public class OmrScanDAO {

    public int insert(String title, int page, int startQ, int endQ, int total, int attempted,
                      int blank, int ambiguous, Integer correct, Integer wrong, Integer score,
                      String resultJson, String scannedBy) throws SQLException {
        String sql = "INSERT INTO omr_scans (title, page_no, start_q, end_q, total, attempted, blank, "
                   + "ambiguous, correct, wrong, score, result_json, scanned_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setInt(2, page); ps.setInt(3, startQ); ps.setInt(4, endQ);
            ps.setInt(5, total); ps.setInt(6, attempted); ps.setInt(7, blank); ps.setInt(8, ambiguous);
            setInt(ps, 9, correct); setInt(ps, 10, wrong); setInt(ps, 11, score);
            ps.setString(12, resultJson); ps.setString(13, scannedBy);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { return k.next() ? k.getInt(1) : 0; }
        }
    }

    public List<OmrScan> findRecent(int limit) throws SQLException {
        String sql = "SELECT scan_id, title, total, attempted, blank, ambiguous, correct, wrong, score, "
                   + "scanned_by, created_at FROM omr_scans ORDER BY scan_id DESC LIMIT ?";
        List<OmrScan> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OmrScan s = new OmrScan();
                    s.setScanId(rs.getInt("scan_id"));
                    s.setTitle(rs.getString("title"));
                    s.setTotal(rs.getInt("total"));
                    s.setAttempted(rs.getInt("attempted"));
                    s.setBlank(rs.getInt("blank"));
                    s.setAmbiguous(rs.getInt("ambiguous"));
                    int c = rs.getInt("correct"); s.setCorrect(rs.wasNull() ? null : c);
                    int w = rs.getInt("wrong");   s.setWrong(rs.wasNull() ? null : w);
                    int sc = rs.getInt("score");  s.setScore(rs.wasNull() ? null : sc);
                    s.setScannedBy(rs.getString("scanned_by"));
                    s.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                    list.add(s);
                }
            }
        }
        return list;
    }

    private static void setInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.INTEGER); else ps.setInt(idx, v);
    }
}
