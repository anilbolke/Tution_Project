package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.Material;
import com.tution.util.DBConnection;

/** Data-access for learning materials. */
public class MaterialDAO {

    public int insert(Material m) throws SQLException {
        String sql = "INSERT INTO materials (title, class_name, subject, type, file_path, youtube_url, uploaded_by) "
                   + "VALUES (?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getTitle());
            setNullable(ps, 2, m.getClassName());
            setNullable(ps, 3, m.getSubject());
            ps.setString(4, m.getType());
            setNullable(ps, 5, m.getFilePath());
            setNullable(ps, 6, m.getYoutubeUrl());
            ps.setString(7, m.getUploadedBy());
            return ps.executeUpdate();
        }
    }

    /** All materials, newest first (staff view). */
    public List<Material> findAll() throws SQLException {
        return query("SELECT material_id, title, class_name, subject, type, file_path, youtube_url, "
                   + "uploaded_by, created_at FROM materials ORDER BY material_id DESC", null, null);
    }

    /**
     * Materials of a type visible to a student's class
     * (the class matches, or the material is untagged = all classes).
     */
    public List<Material> findForClass(String className, String type) throws SQLException {
        String sql = "SELECT material_id, title, class_name, subject, type, file_path, youtube_url, "
                   + "uploaded_by, created_at FROM materials "
                   + "WHERE type = ? AND (class_name IS NULL OR class_name = '' OR class_name = ?) "
                   + "ORDER BY material_id DESC";
        return query(sql, type, className);
    }

    public Material findById(int id) throws SQLException {
        List<Material> l = query("SELECT material_id, title, class_name, subject, type, file_path, youtube_url, "
                   + "uploaded_by, created_at FROM materials WHERE material_id = ?", null, null, id);
        return l.isEmpty() ? null : l.get(0);
    }

    public void delete(int id) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM materials WHERE material_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private List<Material> query(String sql, String p1, String p2, int... idParam) throws SQLException {
        List<Material> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (p1 != null) ps.setString(i++, p1);
            if (p2 != null) ps.setString(i++, p2);
            for (int id : idParam) ps.setInt(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Material m = new Material();
                    m.setMaterialId(rs.getInt("material_id"));
                    m.setTitle(rs.getString("title"));
                    m.setClassName(rs.getString("class_name"));
                    m.setSubject(rs.getString("subject"));
                    m.setType(rs.getString("type"));
                    m.setFilePath(rs.getString("file_path"));
                    m.setYoutubeUrl(rs.getString("youtube_url"));
                    m.setUploadedBy(rs.getString("uploaded_by"));
                    m.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                    list.add(m);
                }
            }
        }
        return list;
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) ps.setNull(idx, java.sql.Types.VARCHAR);
        else ps.setString(idx, val.trim());
    }
}
