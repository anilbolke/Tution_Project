package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.SearchHit;
import com.tution.util.DBConnection;

/**
 * Global search across the whole sales pipeline — enquiries/leads AND admitted
 * students — in one query, so the counsellor never has to guess which screen a
 * person might be on.
 *
 * A 10-digit input is treated as a phone number and matched exactly against
 * every mobile column we hold (student, parent and alternate). Anything else is
 * a name/admission-number fragment and matched with LIKE.
 */
public class SearchDAO {

    /** Hard cap so a one-letter search cannot drag the whole table into memory. */
    private static final int MAX_HITS = 100;

    /**
     * @param q                 mobile number, name fragment, or admission number
     * @param scopeCounsellorId when non-null, LEAD rows are limited to this
     *                          counsellor's own leads (students stay visible to
     *                          everyone — front desk needs to find any student)
     */
    public List<SearchHit> search(String q, Integer scopeCounsellorId) throws SQLException {
        List<SearchHit> hits = new ArrayList<>();
        if (q == null || q.trim().isEmpty()) {
            return hits;
        }
        String term = q.trim();
        boolean phone = isPhone(term);
        String like = "%" + term + "%";
        // Mobiles are stored as bare 10 digits, so strip whatever the user pasted
        // ("+91 98765 43210", "091-9876543210") down to the last 10 digits.
        String phoneTerm = phone ? last10(term) : term;

        // ── leads ──
        StringBuilder leadSql = new StringBuilder(
              "SELECT i.inquiry_id, i.full_name, i.mobile, i.parent_mobile, "
            + "       COALESCE(NULLIF(i.course_name,''), i.class_interest) AS course, "
            + "       i.status, i.source, i.created_at, u.full_name AS counsellor_name "
            + "FROM inquiries i LEFT JOIN users u ON u.user_id = i.counsellor_id WHERE ");
        leadSql.append(phone
            ? "(i.mobile = ? OR i.parent_mobile = ?) "
            : "(i.full_name LIKE ? OR i.email LIKE ?) ");
        if (scopeCounsellorId != null) {
            leadSql.append("AND i.counsellor_id = ? ");
        }
        leadSql.append("ORDER BY i.inquiry_id DESC LIMIT ").append(MAX_HITS);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(leadSql.toString())) {
            ps.setString(1, phone ? phoneTerm : like);
            ps.setString(2, phone ? phoneTerm : like);
            if (scopeCounsellorId != null) {
                ps.setInt(3, scopeCounsellorId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SearchHit h = new SearchHit();
                    h.setType("LEAD");
                    h.setId(rs.getInt("inquiry_id"));
                    h.setName(rs.getString("full_name"));
                    h.setMobile(rs.getString("mobile"));
                    h.setParentMobile(rs.getString("parent_mobile"));
                    h.setCourseOrClass(rs.getString("course"));
                    h.setStatus(rs.getString("status"));
                    h.setReference(rs.getString("source"));
                    h.setCounsellorName(rs.getString("counsellor_name"));
                    h.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                    hits.add(h);
                }
            }
        }

        // ── admitted students ──
        String stuSql =
              "SELECT s.student_id, s.admission_no, s.full_name, s.student_mobile, s.parent_mobile, "
            + "       s.class_name, s.created_at, u.full_name AS counsellor_name "
            + "FROM students s LEFT JOIN users u ON u.user_id = s.counsellor_id WHERE "
            + (phone
                ? "(s.student_mobile = ? OR s.parent_mobile = ? OR s.alt_mobile = ?) "
                : "(s.full_name LIKE ? OR s.admission_no LIKE ? OR s.student_email LIKE ?) ")
            + "ORDER BY s.student_id DESC LIMIT " + MAX_HITS;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(stuSql)) {
            String v = phone ? phoneTerm : like;
            ps.setString(1, v);
            ps.setString(2, v);
            ps.setString(3, v);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SearchHit h = new SearchHit();
                    h.setType("STUDENT");
                    h.setId(rs.getInt("student_id"));
                    h.setName(rs.getString("full_name"));
                    h.setMobile(rs.getString("student_mobile"));
                    h.setParentMobile(rs.getString("parent_mobile"));
                    h.setCourseOrClass(rs.getString("class_name"));
                    h.setStatus("ADMITTED");
                    h.setReference(rs.getString("admission_no"));
                    h.setCounsellorName(rs.getString("counsellor_name"));
                    h.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
                    hits.add(h);
                }
            }
        }
        return hits;
    }

    /** 10+ digits and nothing but phone punctuation — e.g. "+91 98765 43210". */
    public static boolean isPhone(String s) {
        String digits = s.replaceAll("[^0-9]", "");
        return digits.length() >= 10 && s.replaceAll("[0-9+\\-() ]", "").isEmpty();
    }

    /** The last 10 digits of a phone-like string — how mobiles are stored. */
    public static String last10(String s) {
        String digits = s.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
}
