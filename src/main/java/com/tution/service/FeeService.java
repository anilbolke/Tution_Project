package com.tution.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tution.dao.FeePlanDAO;
import com.tution.dao.FeeSlabDAO;
import com.tution.dao.InstallmentDAO;
import com.tution.dao.PaymentDAO;
import com.tution.dao.SettingsDAO;
import com.tution.dao.StudentFeeDAO;
import com.tution.model.FeeInstallment;
import com.tution.model.FeePlan;
import com.tution.model.FeeSlab;
import com.tution.model.Student;
import com.tution.model.StudentFee;
import com.tution.util.FeeCalculator;

/**
 * The single place that answers "what does this student owe?".
 *
 * Pricing comes from the institute brochure via {@code fee_plans}:
 *   • registration is non-refundable and NOT part of the course fee
 *   • the course fee is split by a percentage pattern (40/35/25 for a one-year
 *     course, 25/20/15/15/15/10 for two years) on fixed calendar months
 *   • GST is added on top of the course fee at a configurable rate
 *
 * Students admitted before the brochure pricing landed have no plan, so their
 * figures still come from the ledger row (or, failing that, the legacy slab
 * formula) — nothing about their balance changes.
 */
public class FeeService {

    private final StudentFeeDAO  feeDAO         = new StudentFeeDAO();
    private final PaymentDAO     paymentDAO     = new PaymentDAO();
    private final FeeSlabDAO     slabDAO        = new FeeSlabDAO();
    private final InstallmentDAO installmentDAO = new InstallmentDAO();
    private final FeePlanDAO     planDAO        = new FeePlanDAO();
    private final SettingsDAO    settingsDAO    = new SettingsDAO();

    /** A student's complete fee position. */
    public static class Position {
        public int total;            // TOTAL payable: registration + course after concession + GST
        public int paid;
        public int outstanding;

        public int courseFee;        // list price of the course
        public int registrationFee;  // non-refundable, collected at admission
        public int materialFee;      // legacy students only; brochure folds this into the course fee
        public int discount;
        public int scholarship;
        public int netCourse;        // course fee after concession — what GST and the PP split apply to
        public int gstAmount;
        public double gstRate;

        public String plan = "FULL";
        public String planCode;
        /** false when the figures came from the legacy slab formula, not a ledger row. */
        public boolean fromLedger;

        public boolean hasConcession() { return discount + scholarship > 0; }

        /** Gross before any concession. */
        public int gross() { return courseFee + materialFee; }

        /** PENDING / PARTIAL / PAID — the tri-state the fee dashboard filters on. */
        public String status() {
            if (paid <= 0)      return "PENDING";
            if (paid >= total)  return "PAID";
            return "PARTIAL";
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Reads
    // ────────────────────────────────────────────────────────────────

    /** The fee position of one student. */
    public Position position(int studentId, String feeSlabKey) throws SQLException {
        StudentFee ledger = feeDAO.findByStudent(studentId);
        int paid = paymentDAO.getTotalPaid(studentId);
        return build(ledger, paid, feeSlabKey, slabDAO.findAllAsMap());
    }

    /**
     * Positions for every student in one pass — a handful of queries rather than
     * three per student, which is what the fee dashboard needs.
     */
    public Map<Integer, Position> positions(List<Student> students) throws SQLException {
        Map<Integer, StudentFee> ledgers = feeDAO.findAllAsMap();
        Map<Integer, Integer>    paidMap = paymentDAO.getPaidMap();
        Map<String, FeeSlab>     slabs   = slabDAO.findAllAsMap();

        Map<Integer, Position> out = new HashMap<>();
        for (Student s : students) {
            out.put(s.getStudentId(),
                    build(ledgers.get(s.getStudentId()),
                          paidMap.getOrDefault(s.getStudentId(), 0),
                          s.getFeeSlab(), slabs));
        }
        return out;
    }

    private Position build(StudentFee ledger, int paid, String feeSlabKey,
                           Map<String, FeeSlab> slabs) {
        Position p = new Position();
        p.paid = paid;

        if (ledger != null) {
            p.fromLedger      = true;
            p.planCode        = ledger.getPlanCode();
            p.courseFee       = ledger.getCourseFee();
            p.registrationFee = ledger.getRegistrationFee();
            p.materialFee     = ledger.getMaterialFee();
            p.discount        = ledger.getDiscount();
            p.scholarship     = ledger.getScholarship();
            p.netCourse       = ledger.getNetPayable();
            p.gstRate         = ledger.getGstRate();
            p.gstAmount       = ledger.getGstAmount();
            p.total           = ledger.getTotalPayable() > 0
                              ? ledger.getTotalPayable()
                              : p.registrationFee + p.netCourse + p.gstAmount;
            p.plan            = ledger.getPlan() == null ? "FULL" : ledger.getPlan();
        } else {
            // Legacy path: derive from the slab exactly as the app always has.
            FeeSlab slab = (feeSlabKey == null) ? null : slabs.get(feeSlabKey);
            p.courseFee       = (slab == null) ? 0 : slab.getPerMonth() * FeeCalculator.COURSE_MONTHS;
            p.registrationFee = (slab == null) ? 0 : FeeCalculator.REG_FEE;
            p.materialFee     = (slab == null) ? 0 : FeeCalculator.MATERIAL_FEE;
            p.netCourse       = p.courseFee + p.materialFee;
            p.total           = p.registrationFee + p.netCourse;
        }
        p.outstanding = Math.max(0, p.total - p.paid);
        return p;
    }

    // ────────────────────────────────────────────────────────────────
    //  Writes
    // ────────────────────────────────────────────────────────────────

    /**
     * Opens the fee ledger for a newly admitted student from the brochure plan
     * they were sold, and lays down the instalment schedule.
     *
     * @param startDate the batch start — the first instalment falls on it
     */
    public void applyPlan(int studentId, String planCode, String startDate) throws SQLException {
        FeePlan plan = planDAO.findByCode(planCode);
        if (plan == null) {
            return;
        }
        double gstRate = plan.isGstInclusive() ? 0 : settingsDAO.gstRate();

        StudentFee f = new StudentFee();
        f.setStudentId(studentId);
        f.setPlanCode(plan.getCode());
        f.setCourseFee(plan.getCourseFee());
        f.setRegistrationFee(plan.getRegistrationFee());
        f.setMaterialFee(0);            // the brochure folds materials into the course fee
        f.setDiscount(0);
        f.setScholarship(0);
        f.setGstRate(gstRate);
        f.setPlan(plan.isFullPaymentOnly() ? "FULL" : "INSTALLMENT");
        f.recalc();
        feeDAO.save(f);

        installmentDAO.replaceSchedule(studentId, buildSchedule(plan, f, startDate));
    }

    /**
     * Saves a revised fee record, recomputing GST and the total.
     *
     * The maths lives on {@link StudentFee#recalc()} alone — having a second
     * copy here let the two drift apart and double-counted the registration fee.
     */
    public void saveLedger(StudentFee f) throws SQLException {
        f.recalc();
        feeDAO.save(f);
    }

    /**
     * Re-applies everything the student has already paid across their current
     * schedule, oldest instalment first.
     *
     * Called after a schedule is regenerated: the new rows start at zero paid,
     * so without this a student who had settled two instalments would be asked
     * for that money a second time.
     */
    public void reallocate(int studentId) throws SQLException {
        int paid = paymentDAO.getTotalPaid(studentId);
        installmentDAO.reallocate(studentId, paid);
    }

    // ────────────────────────────────────────────────────────────────
    //  Schedule generation
    // ────────────────────────────────────────────────────────────────

    /**
     * Builds the schedule for a plan: one non-refundable registration row due at
     * admission, then the course fee split by the plan's percentage pattern on
     * its fixed calendar months.
     */
    public List<FeeInstallment> buildSchedule(FeePlan plan, StudentFee fee, String startDate) {
        List<FeeInstallment> rows = new ArrayList<>();
        LocalDate start = parseOrToday(startDate);
        int seq = 1;

        if (fee.getRegistrationFee() > 0) {
            FeeInstallment r = new FeeInstallment();
            r.setSeq(seq++);
            r.setKind("REGISTRATION");
            r.setLabel("Registration (non-refundable)");
            r.setAmount(fee.getRegistrationFee());
            r.setDueDate(start.toString());
            r.setStatus("PENDING");
            rows.add(r);
        }

        // The percentages apply to the course fee plus its GST — that is the
        // amount actually payable for the course.
        int payable = fee.getNetPayable() + fee.getGstAmount();
        if (payable <= 0) {
            return rows;
        }

        double[] pct = plan.percentages();
        String[] months = plan.dueMonthTokens();
        List<LocalDate> dates = dueDates(start, months, pct.length);

        int[] amounts = split(payable, pct);
        for (int i = 0; i < amounts.length; i++) {
            FeeInstallment r = new FeeInstallment();
            r.setSeq(seq++);
            r.setKind("COURSE");
            r.setLabel("Part payment " + (i + 1) + " of " + amounts.length
                       + " (" + trimPct(pct[i]) + "%)");
            r.setAmount(amounts[i]);
            r.setPct(pct[i]);
            r.setDueDate(dates.get(i).toString());
            r.setStatus("PENDING");
            rows.add(r);
        }
        return rows;
    }

    /**
     * Splits an amount by percentages into whole rupees.
     *
     * Rounding remainder goes on the FIRST instalment so the schedule always
     * sums exactly to the payable amount and any shortfall is collected
     * earliest rather than drifting to the end.
     */
    static int[] split(int payable, double[] pct) {
        int[] out = new int[pct.length];
        int running = 0;
        for (int i = 0; i < pct.length; i++) {
            out[i] = (int) Math.floor(payable * pct[i] / 100.0);
            running += out[i];
        }
        out[0] += payable - running;
        return out;
    }

    /**
     * Turns the plan's month tokens into real dates.
     *
     * "START" is the batch start date itself. Every later token is the NEXT
     * occurrence of that month strictly after the previous instalment, so a
     * batch starting in March runs Mar → Jun → Sep, while one starting in
     * August runs Aug → next Jun → next Sep. The day of month follows the batch
     * start, clamped to the length of the target month.
     */
    static List<LocalDate> dueDates(LocalDate start, String[] tokens, int count) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate prev = start;
        for (int i = 0; i < count; i++) {
            String tok = (i < tokens.length) ? tokens[i] : "";
            if (i == 0 || "START".equals(tok)) {
                out.add(start);
                prev = start;
                continue;
            }
            int month = monthOf(tok);
            if (month <= 0) {
                // Unknown token — fall back to three months after the previous one.
                prev = prev.plusMonths(3);
                out.add(prev);
                continue;
            }
            LocalDate next = nextOccurrence(prev, month, start.getDayOfMonth());
            out.add(next);
            prev = next;
        }
        return out;
    }

    /** First date in the given month strictly after {@code after}. */
    private static LocalDate nextOccurrence(LocalDate after, int month, int preferredDay) {
        int year = after.getYear();
        for (int guard = 0; guard < 4; guard++) {
            YearMonth ym = YearMonth.of(year, month);
            LocalDate candidate = ym.atDay(Math.min(preferredDay, ym.lengthOfMonth()));
            if (candidate.isAfter(after)) {
                return candidate;
            }
            year++;
        }
        return after.plusMonths(3);
    }

    private static int monthOf(String tok) {
        String[] names = { "JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC" };
        return Arrays.asList(names).indexOf(tok) + 1;
    }

    private static LocalDate parseOrToday(String s) {
        if (s == null || s.trim().isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private static String trimPct(double d) {
        return (d == Math.rint(d)) ? String.valueOf((int) d) : String.valueOf(d);
    }
}
