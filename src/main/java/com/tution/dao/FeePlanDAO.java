package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.FeePlan;
import com.tution.util.DBConnection;

/** Data-access for the brochure fee plans. */
public class FeePlanDAO {

    private static final String COLS =
          "plan_id, code, programme, programme_code, mode, batch_type, eligibility, "
        + "duration_years, registration_fee, course_fee, gst_inclusive, installments, "
        + "pp_pattern, due_months, is_active, sort_order";

    /** Active plans in brochure order. */
    public List<FeePlan> findAll() throws SQLException {
        List<FeePlan> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT " + COLS + " FROM fee_plans WHERE is_active = 1 ORDER BY sort_order, code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /** Active plans grouped by mode, so the picker can show CLASSROOM / HYBRID / … sections. */
    public Map<String, List<FeePlan>> findGroupedByMode() throws SQLException {
        Map<String, List<FeePlan>> out = new LinkedHashMap<>();
        for (FeePlan p : findAll()) {
            out.computeIfAbsent(p.getMode(), k -> new ArrayList<>()).add(p);
        }
        return out;
    }

    /** One plan by its code, or null. */
    public FeePlan findByCode(String code) throws SQLException {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT " + COLS + " FROM fee_plans WHERE code = ?")) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private FeePlan map(ResultSet rs) throws SQLException {
        FeePlan p = new FeePlan();
        p.setPlanId(rs.getInt("plan_id"));
        p.setCode(rs.getString("code"));
        p.setProgramme(rs.getString("programme"));
        p.setProgrammeCode(rs.getString("programme_code"));
        p.setMode(rs.getString("mode"));
        p.setBatchType(rs.getString("batch_type"));
        p.setEligibility(rs.getString("eligibility"));
        p.setDurationYears(rs.getInt("duration_years"));
        p.setRegistrationFee(rs.getInt("registration_fee"));
        p.setCourseFee(rs.getInt("course_fee"));
        p.setGstInclusive(rs.getInt("gst_inclusive") == 1);
        p.setInstallments(rs.getInt("installments"));
        p.setPpPattern(rs.getString("pp_pattern"));
        p.setDueMonths(rs.getString("due_months"));
        p.setActive(rs.getInt("is_active") == 1);
        p.setSortOrder(rs.getInt("sort_order"));
        return p;
    }
}
