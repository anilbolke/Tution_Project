package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tution.util.DBConnection;

/**
 * Institute settings held as editable rows rather than constants.
 *
 * The GST rate lives here because the brochure charges GST "in addition to the
 * final course fee" without ever stating a rate — so it has to be changeable
 * without a code change.
 */
public class SettingsDAO {

    public static final String GST_RATE       = "gst_rate";
    public static final String READMISSION_FEE = "readmission_fee";
    public static final String INSTITUTE_NAME  = "institute_name";

    /** Single value, or the fallback when the key is missing. */
    public String get(String key, String fallback) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT svalue FROM settings WHERE skey = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString("svalue");
                    if (v != null && !v.trim().isEmpty()) {
                        return v.trim();
                    }
                }
            }
        }
        return fallback;
    }

    /** GST percentage, e.g. 18.00. Zero when unset or unparseable. */
    public double gstRate() throws SQLException {
        try {
            return Double.parseDouble(get(GST_RATE, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int readmissionFee() throws SQLException {
        try {
            return Integer.parseInt(get(READMISSION_FEE, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** All settings, for an admin screen. */
    public Map<String, String> all() throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT skey, svalue FROM settings ORDER BY skey");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("skey"), rs.getString("svalue"));
            }
        }
        return out;
    }

    public void set(String key, String value) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "INSERT INTO settings (skey, svalue) VALUES (?,?) "
               + "ON DUPLICATE KEY UPDATE svalue = VALUES(svalue)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
