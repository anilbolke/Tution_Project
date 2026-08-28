package com.tution.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.tution.util.DBConnection;

/**
 * Works out what scholarship a candidate has earned, from brochure pages 9-10.
 *
 * Two kinds of rule:
 *   FORMULA  scholarship = min(cap, factor x score percentage)
 *            HAMSE  factor 1.00 cap 100   (equal to the score percentage)
 *            HACKSE factor 0.90 cap  90   (90% of the score percentage)
 *   FIXED    a flat percentage for a named criterion, claimed with proof.
 *
 * THE GOVERNING RULE, printed on page 10: "A student can avail any ONE of the
 * scholarship in Category A & B, whichever is higher." Awards therefore take the
 * MAXIMUM, never the sum. Adding them would discount a course fee by well over
 * 100% for a candidate who qualifies on several routes at once.
 *
 * Only VERIFIED claims can win — page 10 also requires proof to be submitted, so
 * an unverified claim is a request, not an entitlement.
 */
public class ScholarshipService {

    /** One route to a scholarship, and what it is worth for this candidate. */
    public static class Award {
        public int    ruleId;
        public String category;      // A / B
        public String subGroup;      // HOSE / SCHOOL / EXTRAORDINARY / NEET / OTHER
        public String criteria;
        public double pct;
        public boolean isFormula;

        public String label() {
            return criteria + " (" + String.format("%.2f", pct) + "%)";
        }
    }

    /** Everything applicable, plus which one wins. */
    public static class Outcome {
        public final List<Award> applicable = new ArrayList<>();
        public Award winner;
        public double pct() { return winner == null ? 0 : winner.pct; }
        public Integer winningRuleId() { return winner == null ? null : Integer.valueOf(winner.ruleId); }
    }

    /**
     * @param examType    HAMSE / HACKSE / HAT — selects the formula rule
     * @param percentage  the candidate's score percentage, already floored at 0
     * @param inquiryId   the person, for their verified Category A/B claims
     */
    public Outcome evaluate(String examType, double percentage, int inquiryId) throws SQLException {
        Outcome out = new Outcome();
        try (Connection con = DBConnection.getConnection()) {
            addFormula(con, out, examType, percentage);
            addVerifiedClaims(con, out, inquiryId);
        }
        for (Award a : out.applicable) {
            if (out.winner == null || a.pct > out.winner.pct) out.winner = a;
        }
        return out;
    }

    private void addFormula(Connection con, Outcome out, String examType, double percentage)
            throws SQLException {
        if (examType == null) return;
        String sql = "SELECT rule_id, category, sub_group, criteria, factor, cap_pct "
                   + "FROM scholarship_rules "
                   + "WHERE rule_kind = 'FORMULA' AND is_active = 1 AND exam_type = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, examType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Award a = new Award();
                    a.ruleId = rs.getInt("rule_id");
                    a.category = rs.getString("category");
                    a.subGroup = rs.getString("sub_group");
                    a.criteria = rs.getString("criteria");
                    a.isFormula = true;
                    double factor = rs.getDouble("factor");
                    double cap = rs.getDouble("cap_pct");
                    a.pct = round2(Math.min(cap, factor * Math.max(0, percentage)));
                    out.applicable.add(a);
                }
            }
        }
    }

    private void addVerifiedClaims(Connection con, Outcome out, int inquiryId) throws SQLException {
        if (inquiryId <= 0) return;
        String sql = "SELECT r.rule_id, r.category, r.sub_group, r.criteria, r.fixed_pct "
                   + "FROM scholarship_claims c "
                   + "JOIN scholarship_rules r ON r.rule_id = c.rule_id "
                   + "WHERE c.inquiry_id = ? AND c.status = 'VERIFIED' AND r.is_active = 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inquiryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Award a = new Award();
                    a.ruleId = rs.getInt("rule_id");
                    a.category = rs.getString("category");
                    a.subGroup = rs.getString("sub_group");
                    a.criteria = rs.getString("criteria");
                    a.pct = round2(rs.getDouble("fixed_pct"));
                    out.applicable.add(a);
                }
            }
        }
    }

    /** Score percentage on the positive scale — negative marking can drive raw below zero. */
    public static double percentage(int rawScore, int maxScore) {
        if (maxScore <= 0) return 0;
        return round2(Math.max(0, rawScore) * 100.0 / maxScore);
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
