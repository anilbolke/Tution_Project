package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tution.model.FeeSlab;
import com.tution.util.DBConnection;

/** Data-access for fee slabs. */
public class FeeSlabDAO {

    /** All slabs keyed by slab_key (preserves table order). */
    public Map<String, FeeSlab> findAllAsMap() throws SQLException {
        String sql = "SELECT slab_key, label, months, per_month FROM fee_slabs ORDER BY per_month DESC";
        Map<String, FeeSlab> map = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                FeeSlab s = new FeeSlab();
                s.setSlabKey(rs.getString("slab_key"));
                s.setLabel(rs.getString("label"));
                s.setMonths(rs.getInt("months"));
                s.setPerMonth(rs.getInt("per_month"));
                map.put(s.getSlabKey(), s);
            }
        }
        return map;
    }
}
