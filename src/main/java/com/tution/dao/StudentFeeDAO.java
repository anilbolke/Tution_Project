package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.tution.model.StudentFee;
import com.tution.util.DBConnection;

/** Data-access for the per-student fee ledger. */
public class StudentFeeDAO {

    private static final String COLS =
          "fee_id, student_id, plan_code, course_fee, registration_fee, material_fee, "
        + "discount, scholarship, net_payable, gst_rate, gst_amount, total_payable, "
        + "registration_paid, plan, approved_by, remarks, updated_at";

    /** The student's fee record, or null when none has been created yet. */
    public StudentFee findByStudent(int studentId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT " + COLS + " FROM student_fees WHERE student_id = ?")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Every ledger row keyed by student_id — one query for the fee dashboard. */
    public Map<Integer, StudentFee> findAllAsMap() throws SQLException {
        Map<Integer, StudentFee> map = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLS + " FROM student_fees");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                StudentFee f = map(rs);
                map.put(f.getStudentId(), f);
            }
        }
        return map;
    }

    /**
     * Creates the ledger row if the student has none.
     *
     * ON DUPLICATE KEY is a no-op update so two concurrent callers cannot create
     * two rows (student_id is UNIQUE).
     */
    private static final String INSERT_SQL = "INSERT INTO student_fees "
        + "(student_id, plan_code, course_fee, registration_fee, material_fee, discount, "
        + " scholarship, net_payable, gst_rate, gst_amount, total_payable, "
        + " registration_paid, plan, approved_by, remarks) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";

    public void insertIfMissing(StudentFee f) throws SQLException {
        String sql = INSERT_SQL
            + "ON DUPLICATE KEY UPDATE student_id = student_fees.student_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, f);
            ps.executeUpdate();
        }
    }

    /** Inserts or overwrites the student's fee record. */
    public void save(StudentFee f) throws SQLException {
        String sql = INSERT_SQL
            + "ON DUPLICATE KEY UPDATE "
            + " plan_code=VALUES(plan_code), course_fee=VALUES(course_fee), "
            + " registration_fee=VALUES(registration_fee), material_fee=VALUES(material_fee), "
            + " discount=VALUES(discount), scholarship=VALUES(scholarship), "
            + " net_payable=VALUES(net_payable), gst_rate=VALUES(gst_rate), "
            + " gst_amount=VALUES(gst_amount), total_payable=VALUES(total_payable), "
            + " registration_paid=VALUES(registration_paid), plan=VALUES(plan), "
            + " approved_by=VALUES(approved_by), remarks=VALUES(remarks)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, f);
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, StudentFee f) throws SQLException {
        int i = 1;
        ps.setInt(i++, f.getStudentId());
        setNullable(ps, i++, f.getPlanCode());
        ps.setInt(i++, f.getCourseFee());
        ps.setInt(i++, f.getRegistrationFee());
        ps.setInt(i++, f.getMaterialFee());
        ps.setInt(i++, f.getDiscount());
        ps.setInt(i++, f.getScholarship());
        ps.setInt(i++, f.getNetPayable());
        ps.setDouble(i++, f.getGstRate());
        ps.setInt(i++, f.getGstAmount());
        ps.setInt(i++, f.getTotalPayable());
        ps.setInt(i++, f.isRegistrationPaid() ? 1 : 0);
        ps.setString(i++, (f.getPlan() == null || f.getPlan().isEmpty()) ? "FULL" : f.getPlan());
        setNullable(ps, i++, f.getApprovedBy());
        setNullable(ps, i, f.getRemarks());
    }

    private StudentFee map(ResultSet rs) throws SQLException {
        StudentFee f = new StudentFee();
        f.setFeeId(rs.getInt("fee_id"));
        f.setStudentId(rs.getInt("student_id"));
        f.setPlanCode(rs.getString("plan_code"));
        f.setCourseFee(rs.getInt("course_fee"));
        f.setRegistrationFee(rs.getInt("registration_fee"));
        f.setMaterialFee(rs.getInt("material_fee"));
        f.setDiscount(rs.getInt("discount"));
        f.setScholarship(rs.getInt("scholarship"));
        f.setNetPayable(rs.getInt("net_payable"));
        f.setGstRate(rs.getDouble("gst_rate"));
        f.setGstAmount(rs.getInt("gst_amount"));
        f.setTotalPayable(rs.getInt("total_payable"));
        f.setRegistrationPaid(rs.getInt("registration_paid") == 1);
        f.setPlan(rs.getString("plan"));
        f.setApprovedBy(rs.getString("approved_by"));
        f.setRemarks(rs.getString("remarks"));
        java.sql.Timestamp t = rs.getTimestamp("updated_at");
        f.setUpdatedAt(t == null ? null : String.valueOf(t));
        return f;
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, java.sql.Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }
}
