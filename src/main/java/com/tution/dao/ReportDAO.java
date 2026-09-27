package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.CounsellorTarget;
import com.tution.model.ReportResult;
import com.tution.util.DBConnection;
import com.tution.util.FeeCalculator;

/**
 * The nine sales reports: the eight from the client requirement, plus Target vs
 * Achievement.
 *
 * Every method returns a {@link ReportResult}, so one JSP renders them all and
 * one servlet exports them all to Excel.
 */
public class ReportDAO {

    /** Report ids, in the order they appear in the picker. */
    public static final String[][] TYPES = {
        { "lead-source",    "Lead Source Analysis" },
        { "counsellor",     "Counsellor Performance" },
        { "funnel",         "Conversion Funnel" },
        { "lost-lead",      "Lost Lead Analysis" },
        { "course-wise",    "Course-wise Admissions" },
        { "collection",     "Fee Collection Register" },
        { "pending-fee",    "Pending Fees & Ageing" },
        { "discount",       "Discount & Scholarship Register" },
        { "target",         "Target vs Achievement" },
        // ── finance (money the institute takes in and pays out) ──
        { "exam-fee",          "Exam Fee Collection" },
        { "expense-register",  "Expense Register" },
        { "vendor-outstanding","Vendor Outstanding" },
        { "fund-statement",    "Fund Statement" }
    };

    /**
     * Per-student payable: the ledger row when there is one, otherwise the
     * legacy slab formula. Same rule as FeeService, so reports and screens
     * cannot disagree.
     */
    private static final String STUDENT_TOTAL =
          "COALESCE(sf.net_payable, CASE WHEN fs.slab_key IS NULL THEN 0 ELSE "
        + FeeCalculator.ONE_TIME + " + fs.per_month * " + FeeCalculator.COURSE_MONTHS + " END)";

    /**
     * Row scope (Scope.of(user)): null = the whole institute; else the lead- and
     * student-based reports only count that user and their direct reports.
     * Final and per instance - the servlets share one ReportDAO across requests,
     * so a scoped run gets its own instance rather than a field set on theirs.
     */
    private final Integer scope;

    public ReportDAO() { this(null); }

    private ReportDAO(Integer scope) { this.scope = scope; }

    /** As {@link #run(String, String, String)}, limited to a counsellor's / ABM's own team. */
    public ReportResult run(String type, String from, String to, Integer scope) throws SQLException {
        if (scope == null) return run(type, from, to);
        ReportResult r = new ReportDAO(scope).run(type, from, to);
        if (SCOPED_TYPES.contains(r.getType())) {
            String d = r.getDescription();
            r.setDescription((d == null ? "" : d + " ") + "Showing your own leads and students"
                    + " (and your team's, if anyone reports to you) - not the whole institute.");
        }
        return r;
    }

    /** Reports built on leads/students, which a scoped user sees only their share of. */
    private static final java.util.Set<String> SCOPED_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            "lead-source", "counsellor", "funnel", "lost-lead", "course-wise",
            "collection", "pending-fee", "discount", "target"));

    /** " AND col IN (team) " when scoped, else "". The id is an int from the session, so inlining is safe. */
    private String sc(String col) {
        return scope == null ? "" : " AND " + Scope.teamOf(col, scope) + " ";
    }

    public ReportResult run(String type, String from, String to) throws SQLException {
        if (type == null) {
            type = "lead-source";
        }
        switch (type) {
            case "counsellor":  return counsellorPerformance(from, to);
            case "funnel":      return conversionFunnel(from, to);
            case "lost-lead":   return lostLeads(from, to);
            case "course-wise": return courseWise(from, to);
            case "collection":  return collection(from, to);
            case "pending-fee": return pendingFees();
            case "discount":    return discountRegister();
            case "target":      return targetVsAchievement(from, to);
            case "exam-fee":           return examFeeCollection(from, to);
            case "expense-register":   return expenseRegister(from, to);
            case "vendor-outstanding": return vendorOutstanding();
            case "fund-statement":     return fundStatement(from, to);
            case "lead-source":
            default:            return leadSource(from, to);
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  1. Lead source
    // ────────────────────────────────────────────────────────────────
    private ReportResult leadSource(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("lead-source", "Lead Source Analysis");
        r.setDescription("Where enquiries came from, and how many of each source became admissions.");
        r.setColumns("Source", "Enquiries", "Converted", "Conversion %", "Revenue Collected");
        r.setNumeric(false, true, true, true, true);
        r.setFromDate(from); r.setToDate(to);

        String sql =
              "SELECT COALESCE(NULLIF(i.source,''),'(not recorded)') AS src, "
            + "       COUNT(*) AS leads, "
            + "       SUM(i.status = 'CONVERTED') AS conv, "
            + "       COALESCE(SUM((SELECT COALESCE(SUM(p.amount),0) FROM payments p "
            + "                     WHERE p.student_id = i.converted_student_id)),0) AS revenue "
            + "FROM inquiries i WHERE 1=1 " + range("DATE(i.created_at)", from, to)
            + sc("i.counsellor_id")
            + " GROUP BY src ORDER BY leads DESC";

        long tLeads = 0, tConv = 0, tRev = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long leads = rs.getLong("leads");
                    long conv  = rs.getLong("conv");
                    long rev   = rs.getLong("revenue");
                    r.addRow(rs.getString("src"), String.valueOf(leads), String.valueOf(conv),
                             pct(conv, leads), money(rev));
                    tLeads += leads; tConv += conv; tRev += rev;
                }
            }
        }
        r.setTotals("Total", String.valueOf(tLeads), String.valueOf(tConv),
                    pct(tConv, tLeads), money(tRev));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  2. Counsellor performance
    // ────────────────────────────────────────────────────────────────
    private ReportResult counsellorPerformance(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("counsellor", "Counsellor Performance");
        r.setDescription("Leads handled, follow-ups logged, demos run and admissions closed per counsellor.");
        r.setColumns("Counsellor", "Leads", "Follow-ups Logged", "Demos", "Admissions",
                     "Conversion %", "Fees Collected");
        r.setNumeric(false, true, true, true, true, true, true);
        r.setFromDate(from); r.setToDate(to);

        String sql =
              "SELECT u.user_id, u.full_name, "
            + " (SELECT COUNT(*) FROM inquiries i WHERE i.counsellor_id = u.user_id "
            + "   " + range("DATE(i.created_at)", from, to) + ") AS leads, "
            + " (SELECT COUNT(*) FROM lead_followups f WHERE f.counsellor_id = u.user_id "
            + "   " + range("DATE(f.created_at)", from, to) + ") AS fups, "
            + " (SELECT COUNT(*) FROM lead_demos d JOIN inquiries i2 ON i2.inquiry_id = d.inquiry_id "
            + "   WHERE i2.counsellor_id = u.user_id " + range("d.demo_date", from, to) + ") AS demos, "
            + " (SELECT COUNT(*) FROM students s WHERE s.counsellor_id = u.user_id "
            + "   " + range("DATE(s.created_at)", from, to) + ") AS adms, "
            + " (SELECT COALESCE(SUM(p.amount),0) FROM payments p "
            + "   JOIN students s2 ON s2.student_id = p.student_id "
            + "   WHERE s2.counsellor_id = u.user_id " + range("p.payment_date", from, to) + ") AS collected "
            + "FROM users u WHERE u.is_active = 1 AND u.role IN ('COUNSELLOR','ABM','ADMIN') "
            + sc("u.user_id")
            + "ORDER BY adms DESC, collected DESC";

        long tL = 0, tF = 0, tD = 0, tA = 0, tC = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            // five independent sub-queries, each with its own range pair
            int idx = 1;
            for (int i = 0; i < 5; i++) {
                idx = bindRange(ps, idx, from, to);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long leads = rs.getLong("leads"), fups = rs.getLong("fups");
                    long demos = rs.getLong("demos"), adms = rs.getLong("adms");
                    long coll  = rs.getLong("collected");
                    r.addRow(rs.getString("full_name"), String.valueOf(leads), String.valueOf(fups),
                             String.valueOf(demos), String.valueOf(adms), pct(adms, leads), money(coll));
                    tL += leads; tF += fups; tD += demos; tA += adms; tC += coll;
                }
            }
        }
        r.setTotals("Total", String.valueOf(tL), String.valueOf(tF), String.valueOf(tD),
                    String.valueOf(tA), pct(tA, tL), money(tC));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  3. Conversion funnel
    // ────────────────────────────────────────────────────────────────
    private ReportResult conversionFunnel(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("funnel", "Conversion Funnel");
        r.setDescription("How enquiries are distributed across the pipeline, and where they stall.");
        r.setColumns("Stage", "Leads", "Share of Total");
        r.setNumeric(false, true, true);
        r.setFromDate(from); r.setToDate(to);

        // Ordered so the funnel reads top to bottom in pipeline order.
        String[][] stages = {
            { "NEW",               "New" },
            { "CONTACTED",         "Contacted" },
            { "INTERESTED",        "Interested" },
            { "DEMO_PENDING",      "Demo Pending" },
            { "DEMO_COMPLETED",    "Demo Completed" },
            { "FOLLOWUP_REQUIRED", "Follow-up Required" },
            { "CONVERTED",         "Converted (Admitted)" },
            { "NOT_INTERESTED",    "Not Interested" },
            { "LOST",              "Lost" }
        };

        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        String sql = "SELECT status, COUNT(*) n FROM inquiries WHERE 1=1 "
                   + range("DATE(created_at)", from, to) + sc("counsellor_id") + " GROUP BY status";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long n = rs.getLong("n");
                    counts.put(rs.getString("status"), n);
                    total += n;
                }
            }
        }
        for (String[] s : stages) {
            long n = counts.getOrDefault(s[0], 0L);
            r.addRow(s[1], String.valueOf(n), pct(n, total));
        }
        r.setTotals("Total enquiries", String.valueOf(total), "100%");
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  4. Lost leads
    // ────────────────────────────────────────────────────────────────
    private ReportResult lostLeads(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("lost-lead", "Lost Lead Analysis");
        r.setDescription("Enquiries marked Lost or Not Interested, with the last objection recorded "
                       + "against them — the raw material for fixing the pitch.");
        r.setColumns("Enquiry Date", "Name", "Mobile", "Course Interest", "Source",
                     "Counsellor", "Status", "Last Objection / Remark");
        r.setFromDate(from); r.setToDate(to);

        String sql =
              "SELECT DATE(i.created_at) AS d, i.full_name, i.mobile, "
            + "       COALESCE(NULLIF(i.course_name,''), i.class_interest) AS course, "
            + "       i.source, u.full_name AS counsellor, i.status, "
            + "       COALESCE(NULLIF((SELECT f.objection FROM lead_followups f "
            + "          WHERE f.inquiry_id = i.inquiry_id AND f.objection IS NOT NULL AND f.objection <> '' "
            + "          ORDER BY f.followup_id DESC LIMIT 1),''), i.counsellor_remarks) AS why "
            + "FROM inquiries i LEFT JOIN users u ON u.user_id = i.counsellor_id "
            + "WHERE i.status IN ('LOST','NOT_INTERESTED') " + range("DATE(i.created_at)", from, to)
            + sc("i.counsellor_id")
            + " ORDER BY i.inquiry_id DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.addRow(str(rs, "d"), str(rs, "full_name"), str(rs, "mobile"),
                             str(rs, "course"), str(rs, "source"), str(rs, "counsellor"),
                             str(rs, "status"), str(rs, "why"));
                }
            }
        }
        r.setTotals("Total lost", String.valueOf(r.size()), "", "", "", "", "", "");
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  5. Course-wise admissions
    // ────────────────────────────────────────────────────────────────
    private ReportResult courseWise(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("course-wise", "Course-wise Admissions");
        r.setDescription("Admissions, billing and collection broken down by course.");
        r.setColumns("Course / Class", "Admissions", "Billed", "Collected", "Outstanding");
        r.setNumeric(false, true, true, true, true);
        r.setFromDate(from); r.setToDate(to);

        String sql =
              "SELECT COALESCE(NULLIF(s.class_name,''),'(not set)') AS course, "
            + "       COUNT(*) AS n, "
            + "       COALESCE(SUM(" + STUDENT_TOTAL + "),0) AS billed, "
            + "       COALESCE(SUM((SELECT COALESCE(SUM(p.amount),0) FROM payments p "
            + "                     WHERE p.student_id = s.student_id)),0) AS paid "
            + "FROM students s "
            + "LEFT JOIN student_fees sf ON sf.student_id = s.student_id "
            + "LEFT JOIN fee_slabs  fs ON fs.slab_key   = s.fee_slab "
            + "WHERE 1=1 " + range("DATE(s.created_at)", from, to)
            + sc("s.counsellor_id")
            + " GROUP BY course ORDER BY n DESC";

        long tN = 0, tB = 0, tP = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long n = rs.getLong("n"), billed = rs.getLong("billed"), paid = rs.getLong("paid");
                    r.addRow(rs.getString("course"), String.valueOf(n), money(billed), money(paid),
                             money(Math.max(0, billed - paid)));
                    tN += n; tB += billed; tP += paid;
                }
            }
        }
        r.setTotals("Total", String.valueOf(tN), money(tB), money(tP), money(Math.max(0, tB - tP)));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  6. Collection register
    // ────────────────────────────────────────────────────────────────
    private ReportResult collection(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("collection", "Fee Collection Register");
        r.setDescription("Every payment received in the period, with mode and collector.");
        r.setColumns("Date", "Receipt No", "Student", "Admission No", "Mode",
                     "Reference", "Collected By", "Amount");
        r.setNumeric(false, false, false, false, false, false, false, true);
        r.setFromDate(from); r.setToDate(to);

        String sql =
              "SELECT p.payment_date, p.receipt_no, s.full_name, s.admission_no, p.payment_mode, "
            + "       p.txn_ref, p.collected_by, p.amount "
            + "FROM payments p JOIN students s ON s.student_id = p.student_id "
            + "WHERE 1=1 " + range("p.payment_date", from, to)
            + sc("s.counsellor_id")
            + " ORDER BY p.payment_date DESC, p.payment_id DESC";

        long total = 0;
        Map<String, Long> byMode = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long amt = rs.getLong("amount");
                    String mode = str(rs, "payment_mode");
                    r.addRow(str(rs, "payment_date"), str(rs, "receipt_no"), str(rs, "full_name"),
                             str(rs, "admission_no"), mode, str(rs, "txn_ref"),
                             str(rs, "collected_by"), money(amt));
                    total += amt;
                    byMode.merge(mode, amt, Long::sum);
                }
            }
        }
        // Mode split appended as its own block, so the register doubles as a
        // day-book without needing a second report.
        if (!byMode.isEmpty()) {
            r.addRow("", "", "", "", "", "", "", "");
            r.addRow("— Mode split —", "", "", "", "", "", "", "");
            for (Map.Entry<String, Long> e : byMode.entrySet()) {
                r.addRow("", "", "", "", e.getKey(), "", "", money(e.getValue()));
            }
        }
        r.setTotals("Total collected", "", "", "", "", "", "", money(total));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  7. Pending fees + ageing
    // ────────────────────────────────────────────────────────────────
    private ReportResult pendingFees() throws SQLException {
        ReportResult r = new ReportResult("pending-fee", "Pending Fees & Ageing");
        r.setDescription("Students with money outstanding, aged by how long their oldest unpaid "
                       + "instalment has been due.");
        r.setColumns("Admission No", "Student", "Class", "Parent Mobile", "Counsellor",
                     "Payable", "Paid", "Outstanding", "Oldest Due", "Days", "Ageing");
        r.setNumeric(false, false, false, false, false, true, true, true, false, true, false);
        r.setDateRanged(false);

        // Wrapped in a derived table so the outstanding filter is a plain WHERE
        // on a computed column, rather than a HAVING without GROUP BY.
        String sql =
              "SELECT * FROM ("
            + "  SELECT s.admission_no, s.full_name, s.class_name, s.parent_mobile, "
            + "         u.full_name AS counsellor, "
            + "         " + STUDENT_TOTAL + " AS payable, "
            + "         COALESCE((SELECT SUM(p.amount) FROM payments p "
            + "                   WHERE p.student_id = s.student_id),0) AS paid, "
            + "         (SELECT MIN(i.due_date) FROM fee_installments i "
            + "           WHERE i.student_id = s.student_id AND i.status <> 'PAID') AS oldest_due "
            + "  FROM students s "
            + "  LEFT JOIN student_fees sf ON sf.student_id = s.student_id "
            + "  LEFT JOIN fee_slabs  fs ON fs.slab_key   = s.fee_slab "
            + "  LEFT JOIN users u ON u.user_id = s.counsellor_id "
            + "  WHERE s.is_active = 1" + sc("s.counsellor_id")
            + ") t WHERE (payable - paid) > 0 "
            + "ORDER BY oldest_due IS NULL, oldest_due";

        long tPayable = 0, tPaid = 0, tDue = 0;
        long[] buckets = new long[4];   // current, 1-30, 31-60, 61-90+, see bucketIndex
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long payable = rs.getLong("payable");
                long paid    = rs.getLong("paid");
                long due     = Math.max(0, payable - paid);
                java.sql.Date oldest = rs.getDate("oldest_due");

                long days = 0;
                String ageing;
                if (oldest == null) {
                    ageing = "No schedule";
                } else {
                    days = java.time.temporal.ChronoUnit.DAYS.between(
                        oldest.toLocalDate(), java.time.LocalDate.now());
                    ageing = bucket(days);
                    int bi = bucketIndex(days);
                    buckets[bi] += due;
                }
                r.addRow(str(rs, "admission_no"), str(rs, "full_name"), str(rs, "class_name"),
                         str(rs, "parent_mobile"), str(rs, "counsellor"),
                         money(payable), money(paid), money(due),
                         oldest == null ? "—" : oldest.toString(),
                         days > 0 ? String.valueOf(days) : "0", ageing);
                tPayable += payable; tPaid += paid; tDue += due;
            }
        }
        if (r.size() > 0) {
            r.addRow("", "", "", "", "", "", "", "", "", "", "");
            r.addRow("— Ageing split —", "", "", "", "", "", "", "", "", "", "");
            String[] labels = { "Not yet due", "1-30 days", "31-60 days", "61+ days" };
            for (int i = 0; i < labels.length; i++) {
                r.addRow("", "", "", "", "", "", "", money(buckets[i]), "", "", labels[i]);
            }
        }
        r.setTotals("Total", "", "", "", "", money(tPayable), money(tPaid), money(tDue), "", "", "");
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  8. Discount register
    // ────────────────────────────────────────────────────────────────
    /**
     * Quarterly targets against what was actually achieved.
     *
     * The date range picks the QUARTER to report on rather than filtering rows —
     * a target belongs to a period, so a half-quarter slice of one would be a
     * meaningless number. Whatever quarter the "from" date falls in is reported
     * whole, and the heading says which.
     *
     * Figures come from {@link TargetDAO}, the same source as the targets screen
     * and the counsellor's own dashboard, so all three always agree.
     */
    private ReportResult targetVsAchievement(String from, String to) throws SQLException {
        java.time.LocalDate basis = java.time.LocalDate.now();
        if (from != null && from.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try { basis = java.time.LocalDate.parse(from); } catch (Exception ignore) { }
        }
        java.time.LocalDate qs = TargetDAO.quarterStart(basis);
        java.time.LocalDate qe = TargetDAO.quarterEnd(qs);

        ReportResult r = new ReportResult("target", "Target vs Achievement");
        r.setDescription("Quarterly admission and revenue targets against what each counsellor "
                       + "actually closed — " + TargetDAO.quarterLabel(qs)
                       + ". Revenue is booked (the value of admissions closed in the quarter), "
                       + "not cash received; cash is shown separately.");
        r.setColumns("Counsellor", "Role", "Adm. Target", "Adm. Actual", "Adm. %",
                     "Revenue Target", "Revenue Booked", "Revenue %", "Collected", "Pace", "Status");
        r.setNumeric(false, false, true, true, true, true, true, true, true, false, false);
        r.setFromDate(qs.toString());
        r.setToDate(qe.toString());

        long tAdmT = 0, tAdmA = 0, tRevT = 0, tRevA = 0, tColl = 0;
        java.util.Set<Integer> team = scope == null ? null : Scope.teamIds(scope);
        for (CounsellorTarget t : new TargetDAO().forPeriod("QUARTER", qs, qe)) {
            if (team != null && !team.contains(t.getCounsellorId())) continue;
            // People with no target still appear: "not set" is a finding in its
            // own right on a management report, not a row to hide.
            String pace = String.format("%.0f", t.expectedPct()) + "% elapsed";
            r.addRow(t.getCounsellorName(),
                     t.getRole(),
                     t.isUnset() ? "—" : String.valueOf(t.getAdmissionsTarget()),
                     String.valueOf(t.getAdmissionsActual()),
                     t.isUnset() ? "—" : String.format("%.1f", t.admissionsPct()),
                     t.isUnset() ? "—" : money(t.getRevenueTarget()),
                     money(t.getRevenueActual()),
                     t.isUnset() ? "—" : String.format("%.1f", t.revenuePct()),
                     money(t.getCollectedActual()),
                     pace,
                     t.isUnset() ? "No target set" : t.revenueStatus());
            tAdmT += t.getAdmissionsTarget(); tAdmA += t.getAdmissionsActual();
            tRevT += t.getRevenueTarget();    tRevA += t.getRevenueActual();
            tColl += t.getCollectedActual();
        }
        r.setTotals("Team", "", String.valueOf(tAdmT), String.valueOf(tAdmA),
                    tAdmT > 0 ? String.format("%.1f", tAdmA * 100.0 / tAdmT) : "—",
                    money(tRevT), money(tRevA),
                    tRevT > 0 ? String.format("%.1f", tRevA * 100.0 / tRevT) : "—",
                    money(tColl), "", "");
        return r;
    }

    private ReportResult discountRegister() throws SQLException {
        ReportResult r = new ReportResult("discount", "Discount & Scholarship Register");
        r.setDescription("Every concession granted, who approved it and why — the audit trail for "
                       + "money the institute chose not to charge.");
        r.setColumns("Admission No", "Student", "Class", "Gross Fee", "Discount",
                     "Scholarship", "Net Payable", "Approved By", "Reason", "Granted On");
        r.setNumeric(false, false, false, true, true, true, true, false, false, false);
        r.setDateRanged(false);

        String sql =
              "SELECT s.admission_no, s.full_name, s.class_name, "
            + "       (sf.course_fee + sf.registration_fee + sf.material_fee) AS gross, "
            + "       sf.discount, sf.scholarship, sf.net_payable, sf.approved_by, sf.remarks, "
            + "       sf.updated_at "
            + "FROM student_fees sf JOIN students s ON s.student_id = sf.student_id "
            + "WHERE (sf.discount > 0 OR sf.scholarship > 0) " + sc("s.counsellor_id")
            + "ORDER BY (sf.discount + sf.scholarship) DESC";

        long tGross = 0, tDisc = 0, tSch = 0, tNet = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long gross = rs.getLong("gross"), disc = rs.getLong("discount");
                long sch   = rs.getLong("scholarship"), net = rs.getLong("net_payable");
                java.sql.Timestamp up = rs.getTimestamp("updated_at");
                r.addRow(str(rs, "admission_no"), str(rs, "full_name"), str(rs, "class_name"),
                         money(gross), money(disc), money(sch), money(net),
                         str(rs, "approved_by"), str(rs, "remarks"),
                         up == null ? "" : up.toString().substring(0, 10));
                tGross += gross; tDisc += disc; tSch += sch; tNet += net;
            }
        }
        r.setTotals("Total", "", "", money(tGross), money(tDisc), money(tSch), money(tNet), "", "", "");
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    // ════════════════════════════════════════════════════════════════
    //  FINANCE REPORTS (10-13)
    //
    //  These read the finance tables only. They never touch `payments`,
    //  `students` or `student_fees`, so nothing here can move a tuition
    //  figure - the same separation the exam_payments table exists for.
    // ════════════════════════════════════════════════════════════════

    // ────────────────────────────────────────────────────────────────
    //  10. Exam fee collection
    // ────────────────────────────────────────────────────────────────
    private ReportResult examFeeCollection(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("exam-fee", "Exam Fee Collection");
        r.setDescription("Per scholarship exam: what was billable, what came in, and who still owes. "
                       + "The range filters on the exam date, not the payment date, so an exam's "
                       + "figures are always complete.");
        r.setColumns("Exam", "Date", "Fee", "Candidates", "Paid", "Part paid", "Unpaid",
                     "Waived", "Expected", "Collected", "Outstanding", "Collected %");
        r.setNumeric(false, false, true, true, true, true, true, true, true, true, true, true);
        r.setFromDate(from); r.setToDate(to);

        // Payable and paid are defined exactly as in ExamPaymentDAO; if that
        // definition ever changes, this report must change with it.
        String payable = "(CASE WHEN c.fee_waived = 1 THEN 0 "
                       + "      ELSE COALESCE(c.fee_amount, e.exam_fee) END)";
        String paid    = "(SELECT COALESCE(SUM(p.amount),0) FROM exam_payments p "
                       + "   WHERE p.candidate_id = c.candidate_id AND p.status = 'ACTIVE')";

        String sql =
              "SELECT e.exam_id, e.exam_name, e.exam_date, e.exam_fee, "
            + "  COUNT(c.candidate_id) AS n, "
            + "  COALESCE(SUM(" + payable + "),0) AS expected, "
            + "  COALESCE(SUM(" + paid    + "),0) AS collected, "
            + "  COALESCE(SUM(GREATEST(" + payable + " - " + paid + ", 0)),0) AS due, "
            + "  SUM(c.fee_waived = 1) AS waived, "
            + "  SUM(c.fee_waived = 0 AND " + payable + " > 0 AND " + paid + " >= " + payable + ") AS full_paid, "
            + "  SUM(c.fee_waived = 0 AND " + paid + " > 0 AND " + paid + " < " + payable + ") AS part_paid, "
            + "  SUM(c.fee_waived = 0 AND " + payable + " > 0 AND " + paid + " = 0) AS unpaid "
            + "FROM exams e LEFT JOIN exam_candidates c ON c.exam_id = e.exam_id "
            + "WHERE e.exam_type <> 'INTERNAL' " + range("e.exam_date", from, to)
            + " GROUP BY e.exam_id, e.exam_name, e.exam_date, e.exam_fee "
            + " ORDER BY e.exam_date DESC, e.exam_id DESC";

        long tN = 0, tPaid = 0, tPart = 0, tUnpaid = 0, tWaived = 0;
        java.math.BigDecimal tExp = java.math.BigDecimal.ZERO,
                             tCol = java.math.BigDecimal.ZERO,
                             tDue = java.math.BigDecimal.ZERO;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.math.BigDecimal exp = nz(rs.getBigDecimal("expected"));
                    java.math.BigDecimal col = nz(rs.getBigDecimal("collected"));
                    java.math.BigDecimal due = nz(rs.getBigDecimal("due"));
                    long n = rs.getLong("n");
                    r.addRow(str(rs, "exam_name"), str(rs, "exam_date"),
                             rupees(nz(rs.getBigDecimal("exam_fee"))),
                             String.valueOf(n),
                             String.valueOf(rs.getLong("full_paid")),
                             String.valueOf(rs.getLong("part_paid")),
                             String.valueOf(rs.getLong("unpaid")),
                             String.valueOf(rs.getLong("waived")),
                             rupees(exp), rupees(col), rupees(due), share(col, exp));
                    tN += n; tPaid += rs.getLong("full_paid"); tPart += rs.getLong("part_paid");
                    tUnpaid += rs.getLong("unpaid"); tWaived += rs.getLong("waived");
                    tExp = tExp.add(exp); tCol = tCol.add(col); tDue = tDue.add(due);
                }
            }
        }
        r.setTotals("Total", "", "", String.valueOf(tN), String.valueOf(tPaid),
                    String.valueOf(tPart), String.valueOf(tUnpaid), String.valueOf(tWaived),
                    rupees(tExp), rupees(tCol), rupees(tDue), share(tCol, tExp));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  11. Expense register
    // ────────────────────────────────────────────────────────────────
    private ReportResult expenseRegister(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("expense-register", "Expense Register");
        r.setDescription("Every live voucher in the range, with the vendor and the work order it "
                       + "settled. Cancelled vouchers are excluded - they no longer represent money "
                       + "spent.");
        r.setColumns("Date", "Voucher", "Category", "Vendor", "Work Order", "What for",
                     "Mode", "Reference", "Amount", "Of which tax");
        r.setNumeric(false, false, false, false, false, false, false, false, true, true);
        r.setFromDate(from); r.setToDate(to);

        String sql =
              "SELECT x.expense_date, x.voucher_no, x.amount, x.tax_amount, x.payment_mode, "
            + "       COALESCE(x.txn_ref, x.invoice_no, '') AS ref, "
            + "       COALESCE(x.description,'') AS descr, "
            + "       COALESCE(c.name,'(uncategorised)') AS cat, "
            + "       COALESCE(v.name,'—') AS vendor, COALESCE(w.wo_no,'—') AS wo "
            + "  FROM expenses x "
            + "  LEFT JOIN expense_categories c ON c.category_id   = x.category_id "
            + "  LEFT JOIN vendors           v ON v.vendor_id     = x.vendor_id "
            + "  LEFT JOIN work_orders       w ON w.work_order_id = x.work_order_id "
            + " WHERE x.status = 'ACTIVE' " + range("x.expense_date", from, to)
            + " ORDER BY x.expense_date, x.expense_id";

        java.math.BigDecimal tAmt = java.math.BigDecimal.ZERO, tTax = java.math.BigDecimal.ZERO;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindRange(ps, 1, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.math.BigDecimal amt = nz(rs.getBigDecimal("amount"));
                    java.math.BigDecimal tax = nz(rs.getBigDecimal("tax_amount"));
                    r.addRow(str(rs, "expense_date"), str(rs, "voucher_no"), str(rs, "cat"),
                             str(rs, "vendor"), str(rs, "wo"), str(rs, "descr"),
                             str(rs, "payment_mode"), str(rs, "ref"),
                             rupees(amt), tax.signum() == 0 ? "" : rupees(tax));
                    tAmt = tAmt.add(amt); tTax = tTax.add(tax);
                }
            }
        }
        r.setTotals("Total", "", "", "", "", "", "", "", rupees(tAmt), rupees(tTax));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  12. Vendor outstanding
    // ────────────────────────────────────────────────────────────────
    private ReportResult vendorOutstanding() throws SQLException {
        ReportResult r = new ReportResult("vendor-outstanding", "Vendor Outstanding");
        r.setDescription("What each vendor has been committed and what is still owed across all "
                       + "their work orders. This is a position as it stands today, not a period, "
                       + "so the date range does not apply.");
        r.setDateRanged(false);
        r.setColumns("Vendor", "Category", "Contact", "Work Orders", "Open", "Ordered",
                     "Paid", "Outstanding");
        r.setNumeric(false, false, false, true, true, true, true, true);

        String sql =
              "SELECT v.name, COALESCE(v.category,'—') AS cat, "
            + "  TRIM(CONCAT(COALESCE(v.contact_person,''), ' ', COALESCE(v.mobile,''))) AS contact, "
            + "  v.is_active, "
            + "  (SELECT COUNT(*) FROM work_orders w "
            + "     WHERE w.vendor_id = v.vendor_id AND w.status <> 'CANCELLED') AS n_orders, "
            + "  (SELECT COALESCE(SUM(w.order_value),0) FROM work_orders w "
            + "     WHERE w.vendor_id = v.vendor_id AND w.status <> 'CANCELLED') AS ordered, "
            + "  (SELECT COALESCE(SUM(x.amount),0) FROM expenses x "
            + "     WHERE x.vendor_id = v.vendor_id AND x.status = 'ACTIVE') AS paid, "
            + "  (SELECT COUNT(*) FROM work_orders w "
            + "     WHERE w.vendor_id = v.vendor_id "
            + "       AND w.status IN ('ISSUED','IN_PROGRESS','COMPLETED') "
            + "       AND w.order_value > (SELECT COALESCE(SUM(x2.amount),0) FROM expenses x2 "
            + "                             WHERE x2.work_order_id = w.work_order_id "
            + "                               AND x2.status = 'ACTIVE')) AS n_open "
            + "FROM vendors v ORDER BY 8 DESC, v.name";

        long tOrders = 0, tOpen = 0;
        java.math.BigDecimal tOrdered = java.math.BigDecimal.ZERO,
                             tPaid    = java.math.BigDecimal.ZERO,
                             tDue     = java.math.BigDecimal.ZERO;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.math.BigDecimal ordered = nz(rs.getBigDecimal("ordered"));
                java.math.BigDecimal paid    = nz(rs.getBigDecimal("paid"));
                java.math.BigDecimal due     = ordered.subtract(paid);
                if (due.signum() < 0) due = java.math.BigDecimal.ZERO;

                String name = str(rs, "name") + (rs.getInt("is_active") == 1 ? "" : " (retired)");
                long n    = rs.getLong("n_orders");
                long open = rs.getLong("n_open");
                r.addRow(name, str(rs, "cat"), str(rs, "contact"),
                         String.valueOf(n), String.valueOf(open),
                         rupees(ordered), rupees(paid), rupees(due));
                tOrders += n; tOpen += open;
                tOrdered = tOrdered.add(ordered); tPaid = tPaid.add(paid); tDue = tDue.add(due);
            }
        }
        r.setTotals("Total", "", "", String.valueOf(tOrders), String.valueOf(tOpen),
                    rupees(tOrdered), rupees(tPaid), rupees(tDue));
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    //  13. Fund statement
    // ────────────────────────────────────────────────────────────────
    private ReportResult fundStatement(String from, String to) throws SQLException {
        ReportResult r = new ReportResult("fund-statement", "Fund Statement");
        r.setDescription("Per fund: what it held before the range, what came in and went out "
                       + "during it, and what it holds now. Reversals appear as ordinary "
                       + "credits - a cancelled expense is corrected, never erased.");
        r.setColumns("Fund", "Opening", "Top-ups", "Reversals & Adjustments", "Credits",
                     "Expenses", "Debits", "Closing");
        r.setNumeric(false, true, true, true, true, true, true, true);
        r.setFromDate(from); r.setToDate(to);

        // Opening = the fund's own opening balance plus everything posted BEFORE
        // the window. Without it the closing figure would not tie to the screen.
        String sql =
              "SELECT f.fund_id, f.name, f.opening_balance, "
            + " (SELECT COALESCE(SUM(CASE WHEN t.direction='CREDIT' THEN t.amount "
            + "                           ELSE -t.amount END),0) "
            + "    FROM fund_transactions t WHERE t.fund_id = f.fund_id "
            + (notBlank(from) ? "     AND t.txn_date < ? " : "     AND 1=0 ")
            + " ) AS before_window, "
            + " (SELECT COALESCE(SUM(t.amount),0) FROM fund_transactions t "
            + "    WHERE t.fund_id=f.fund_id AND t.direction='CREDIT' "
            + "      AND t.source_type='TOPUP' " + range("t.txn_date", from, to) + ") AS topups, "
            + " (SELECT COALESCE(SUM(t.amount),0) FROM fund_transactions t "
            + "    WHERE t.fund_id=f.fund_id AND t.direction='CREDIT' "
            + "      AND t.source_type<>'TOPUP' " + range("t.txn_date", from, to) + ") AS other_cr, "
            + " (SELECT COALESCE(SUM(t.amount),0) FROM fund_transactions t "
            + "    WHERE t.fund_id=f.fund_id AND t.direction='DEBIT' "
            + "      AND t.source_type='EXPENSE' " + range("t.txn_date", from, to) + ") AS exp_dr, "
            + " (SELECT COALESCE(SUM(t.amount),0) FROM fund_transactions t "
            + "    WHERE t.fund_id=f.fund_id AND t.direction='DEBIT' "
            + "      AND t.source_type<>'EXPENSE' " + range("t.txn_date", from, to) + ") AS other_dr "
            + "FROM fund_accounts f ORDER BY f.name";

        java.math.BigDecimal tOpen = java.math.BigDecimal.ZERO, tTop = java.math.BigDecimal.ZERO,
                             tOth  = java.math.BigDecimal.ZERO, tExp = java.math.BigDecimal.ZERO,
                             tOdr  = java.math.BigDecimal.ZERO, tClose = java.math.BigDecimal.ZERO;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (notBlank(from)) ps.setString(i++, from.trim());   // before_window
            for (int k = 0; k < 4; k++) i = bindRange(ps, i, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.math.BigDecimal open = nz(rs.getBigDecimal("opening_balance"))
                                                  .add(nz(rs.getBigDecimal("before_window")));
                    java.math.BigDecimal top   = nz(rs.getBigDecimal("topups"));
                    java.math.BigDecimal oth   = nz(rs.getBigDecimal("other_cr"));
                    java.math.BigDecimal exp   = nz(rs.getBigDecimal("exp_dr"));
                    java.math.BigDecimal odr   = nz(rs.getBigDecimal("other_dr"));
                    java.math.BigDecimal close = open.add(top).add(oth).subtract(exp).subtract(odr);

                    r.addRow(str(rs, "name"), rupees(open), rupees(top), rupees(oth),
                             rupees(top.add(oth)), rupees(exp), rupees(exp.add(odr)),
                             rupees(close));
                    tOpen = tOpen.add(open); tTop = tTop.add(top); tOth = tOth.add(oth);
                    tExp = tExp.add(exp);    tOdr = tOdr.add(odr); tClose = tClose.add(close);
                }
            }
        }
        r.setTotals("Total", rupees(tOpen), rupees(tTop), rupees(tOth),
                    rupees(tTop.add(tOth)), rupees(tExp), rupees(tExp.add(tOdr)), rupees(tClose));
        return r;
    }

    /** Money on a finance report: Indian grouping with paise, matching the screens. */
    private static String rupees(java.math.BigDecimal v) {
        return com.tution.util.Money.fmt(v);
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    private static String share(java.math.BigDecimal part, java.math.BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return "—";
        return Math.round(part.doubleValue() * 100.0 / whole.doubleValue()) + "%";
    }

    /** Adds "AND col BETWEEN ? AND ?" clauses only for the bounds supplied. */
    private static String range(String col, String from, String to) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(from)) sb.append(" AND ").append(col).append(" >= ? ");
        if (notBlank(to))   sb.append(" AND ").append(col).append(" <= ? ");
        return sb.toString();
    }

    /** Binds whichever range bounds were supplied. Returns the next free index. */
    private static int bindRange(PreparedStatement ps, int idx, String from, String to)
            throws SQLException {
        if (notBlank(from)) ps.setString(idx++, from.trim());
        if (notBlank(to))   ps.setString(idx++, to.trim());
        return idx;
    }

    private static String bucket(long days) {
        if (days <= 0)  return "Not yet due";
        if (days <= 30) return "1-30 days";
        if (days <= 60) return "31-60 days";
        return "61+ days";
    }

    private static int bucketIndex(long days) {
        if (days <= 0)  return 0;
        if (days <= 30) return 1;
        if (days <= 60) return 2;
        return 3;
    }

    private static String pct(long part, long whole) {
        if (whole <= 0) return "0%";
        return Math.round(part * 100.0 / whole) + "%";
    }

    /**
     * Plain digits with grouping — no currency symbol, so Excel keeps it readable.
     *
     * Indian grouping, same as every screen in the application. It used to be
     * {@code String.format("%,d")}, which meant a report and the screen it came
     * from printed the same amount two different ways.
     */
    private static String money(long v) {
        return com.tution.util.Money.whole(v);
    }

    private static String str(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v == null ? "" : v;
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    /** Every report id paired with its title. */
    public static List<String[]> types() {
        return new ArrayList<>(java.util.Arrays.asList(TYPES));
    }

    /** The reports this user's role holds (RPT_* activities), in picker order. */
    public static List<String[]> types(com.tution.model.User u) {
        List<String[]> out = new ArrayList<>();
        for (String[] t : TYPES) {
            if (u != null && u.can(AccessDAO.reportActivity(t[0]))) out.add(t);
        }
        return out;
    }

}
