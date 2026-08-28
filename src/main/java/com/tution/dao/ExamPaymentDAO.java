package com.tution.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.CandidateFee;
import com.tution.model.ExamPayment;
import com.tution.model.SchoolReceipt;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * Exam fee receipts, and the fee position they add up to.
 *
 * SEPARATE FROM PaymentDAO ON PURPOSE. Exam candidates are leads, not students,
 * and every tuition money figure in this app is a SUM over `payments` grouped by
 * student. Putting exam receipts in that table would inflate the dashboard's
 * collected total, the collection register, the pending-fee aging and every
 * counsellor's revenue achievement - quietly, and only visibly at month end.
 *
 * PAID STATE IS NEVER STORED. {@link #PAYABLE} and {@link #PAID_SUM} below are
 * the only definitions of "what is owed" and "what came in", and they are
 * reused by every query here so the list, the search and the totals strip can
 * never tell three different stories.
 */
public class ExamPaymentDAO {

    /**
     * What a candidate owes: nothing if waived, otherwise their own override if
     * one is set, otherwise the exam's list price.
     */
    private static final String PAYABLE =
          "(CASE WHEN c.fee_waived = 1 THEN 0 ELSE COALESCE(c.fee_amount, e.exam_fee) END)";

    /** What has actually been taken. VOID receipts are excluded, never deleted. */
    private static final String PAID_SUM =
          "(SELECT COALESCE(SUM(p.amount), 0) FROM exam_payments p "
        + "   WHERE p.candidate_id = c.candidate_id AND p.status = 'ACTIVE')";

    private static final String RECEIPTS =
          "(SELECT COUNT(*) FROM exam_payments p "
        + "   WHERE p.candidate_id = c.candidate_id AND p.status = 'ACTIVE')";

    private static final String FEE_SELECT =
          "SELECT c.candidate_id, c.exam_id, c.roll_no, c.status AS cand_status, "
        + "       c.fee_amount, c.fee_waived, c.waiver_reason, "
        + "       i.full_name, i.mobile, i.parent_mobile, i.school_name, i.current_class, "
        + "       e.exam_name, e.exam_type, e.exam_date, e.exam_fee, "
        + "       " + PAYABLE  + " AS payable, "
        + "       " + PAID_SUM + " AS paid, "
        + "       " + RECEIPTS + " AS receipts "
        + "  FROM exam_candidates c "
        + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
        + "  JOIN exams     e ON e.exam_id    = c.exam_id ";

    /* ─────────────────────── fee positions ─────────────────────── */

    /**
     * The candidate list for one exam, filtered.
     *
     * {@code state} filters on the DERIVED position, which is why it is a HAVING
     * and not a WHERE - `payable` and `paid` are computed columns and do not
     * exist yet at WHERE time.
     */
    public List<CandidateFee> list(int examId, String state, String school, String q)
            throws SQLException {

        StringBuilder sql = new StringBuilder(FEE_SELECT).append(" WHERE c.exam_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(examId);

        if (notBlank(school)) {
            sql.append(" AND i.school_name = ? ");
            args.add(school.trim());
        }
        if (notBlank(q)) {
            sql.append(" AND (c.roll_no LIKE ? OR i.full_name LIKE ? "
                     + "      OR i.mobile LIKE ? OR i.parent_mobile LIKE ?) ");
            String like = "%" + q.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }

        if (notBlank(state)) {
            switch (state) {
                case CandidateFee.PAID:
                    sql.append(" HAVING payable > 0 AND paid >= payable AND c.fee_waived = 0 "); break;
                case CandidateFee.PARTIAL:
                    sql.append(" HAVING paid > 0 AND paid < payable "); break;
                case CandidateFee.UNPAID:
                    sql.append(" HAVING payable > 0 AND paid = 0 AND c.fee_waived = 0 "); break;
                case CandidateFee.WAIVED:
                    sql.append(" HAVING c.fee_waived = 1 "); break;
                case CandidateFee.NO_FEE:
                    sql.append(" HAVING c.fee_waived = 0 AND payable = 0 "); break;
                default: /* unknown filter = no filter */ break;
            }
        }
        sql.append(" ORDER BY c.roll_no ");

        List<CandidateFee> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapFee(rs));
            }
        }
        return out;
    }

    /**
     * Look a candidate up by roll number ACROSS ALL EXAMS.
     *
     * Roll numbers are globally unique and never reused (uq_roll), so the person
     * at the counter can just type the number without first choosing the exam -
     * and a roll number from a different exam is found and shown rather than
     * silently missed.
     */
    public CandidateFee findByRoll(String rollNo) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(FEE_SELECT + " WHERE c.roll_no = ?")) {
            ps.setString(1, rollNo == null ? "" : rollNo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFee(rs) : null;
            }
        }
    }

    public CandidateFee findByCandidate(int candidateId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(FEE_SELECT + " WHERE c.candidate_id = ?")) {
            ps.setInt(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFee(rs) : null;
            }
        }
    }

    /** Totals for one exam's fee screen. */
    public Totals totals(int examId) throws SQLException {
        String sql =
              "SELECT COUNT(*) AS n, "
            + "       COALESCE(SUM(" + PAYABLE + "), 0) AS expected, "
            + "       COALESCE(SUM(" + PAID_SUM + "), 0) AS collected, "
            + "       COALESCE(SUM(CASE WHEN c.fee_waived = 1 THEN 1 ELSE 0 END), 0) AS waived "
            + "  FROM exam_candidates c "
            + "  JOIN exams e ON e.exam_id = c.exam_id "
            + " WHERE c.exam_id = ?";
        Totals t = new Totals();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    t.candidates = rs.getInt("n");
                    t.expected   = Money.of(rs.getBigDecimal("expected"));
                    t.collected  = Money.of(rs.getBigDecimal("collected"));
                    t.waived     = rs.getInt("waived");
                }
            }
        }
        // Counted separately so each candidate's shortfall is floored at zero:
        // one candidate's over-payment must not cancel out another's arrears.
        String due = "SELECT COALESCE(SUM(GREATEST(" + PAYABLE + " - " + PAID_SUM + ", 0)), 0) AS d, "
                   + "       COALESCE(SUM(CASE WHEN " + PAYABLE + " > " + PAID_SUM
                   + "                    THEN 1 ELSE 0 END), 0) AS n "
                   + "  FROM exam_candidates c JOIN exams e ON e.exam_id = c.exam_id "
                   + " WHERE c.exam_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(due)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    t.outstanding   = Money.of(rs.getBigDecimal("d"));
                    t.candidatesDue = rs.getInt("n");
                }
            }
        }
        return t;
    }

    /** Totals strip for one exam. */
    public static class Totals {
        public int        candidates;
        public int        candidatesDue;
        public int        waived;
        public BigDecimal expected    = Money.ZERO;
        public BigDecimal collected   = Money.ZERO;
        public BigDecimal outstanding = Money.ZERO;
    }

    /* ─────────────────────── receipts ─────────────────────── */

    public List<ExamPayment> paymentsFor(int candidateId) throws SQLException {
        String sql = "SELECT p.*, c.roll_no, i.full_name, e.exam_name "
                   + "  FROM exam_payments p "
                   + "  JOIN exam_candidates c ON c.candidate_id = p.candidate_id "
                   + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                   + "  JOIN exams e ON e.exam_id = c.exam_id "
                   + " WHERE p.candidate_id = ? ORDER BY p.payment_date, p.exam_payment_id";
        List<ExamPayment> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapPayment(rs));
            }
        }
        return out;
    }

    public ExamPayment findPayment(int examPaymentId) throws SQLException {
        String sql = "SELECT p.*, c.roll_no, i.full_name, e.exam_name, i.school_name "
                   + "  FROM exam_payments p "
                   + "  JOIN exam_candidates c ON c.candidate_id = p.candidate_id "
                   + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                   + "  JOIN exams e ON e.exam_id = c.exam_id "
                   + " WHERE p.exam_payment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examPaymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapPayment(rs) : null;
            }
        }
    }

    /** Recent receipts across every exam - the "today's collection" strip. */
    public List<ExamPayment> recent(int limit) throws SQLException {
        String sql = "SELECT p.*, c.roll_no, i.full_name, e.exam_name, i.school_name "
                   + "  FROM exam_payments p "
                   + "  JOIN exam_candidates c ON c.candidate_id = p.candidate_id "
                   + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                   + "  JOIN exams e ON e.exam_id = c.exam_id "
                   + " ORDER BY p.created_at DESC LIMIT ?";
        List<ExamPayment> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapPayment(rs));
            }
        }
        return out;
    }

    /**
     * Records a receipt, allocating the next number in the EXM series.
     *
     * The number is derived from the current maximum rather than picked at
     * random, so the series is sequential and a missing number is a question
     * somebody can answer. Two counters saving at the same instant would both
     * compute the same next number; the UNIQUE key catches that and the loop
     * simply tries again, which is cheaper and safer than locking the table.
     */
    public int insert(ExamPayment p) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return insert(con, p);
        }
    }

    /**
     * Same, but on the caller's connection so it joins their transaction.
     *
     * A bulk school payment writes one cheque row and N receipts that must all
     * land or none of them; and the numbering MUST run on this same connection,
     * because {@link #nextReceiptNo(Connection)} reads the maximum issued so far
     * and only this connection can see the rows the transaction has just written.
     * Numbering on a separate connection would hand every receipt in the batch
     * the same number.
     */
    public int insert(Connection con, ExamPayment p) throws SQLException {
        String sql = "INSERT INTO exam_payments "
                   + "(candidate_id, receipt_no, amount, payment_mode, payment_date, txn_ref, "
                   + " school_receipt_id, remarks, collected_by, collected_by_id) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?)";

        SQLException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            String receiptNo = nextReceiptNo(con);
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, p.getCandidateId());
                ps.setString(2, receiptNo);
                ps.setBigDecimal(3, Money.of(p.getAmount()));
                ps.setString(4, p.getPaymentMode() == null ? "Cash" : p.getPaymentMode());
                ps.setString(5, p.getPaymentDate());
                setNullable(ps, 6, p.getTxnRef());
                if (p.getSchoolReceiptId() == null) ps.setNull(7, Types.INTEGER);
                else                                ps.setInt(7, p.getSchoolReceiptId());
                setNullable(ps, 8, p.getRemarks());
                setNullable(ps, 9, p.getCollectedBy());
                if (p.getCollectedById() == null) ps.setNull(10, Types.INTEGER);
                else                              ps.setInt(10, p.getCollectedById());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        p.setExamPaymentId(keys.getInt(1));
                        p.setReceiptNo(receiptNo);
                        return p.getExamPaymentId();
                    }
                }
                return 0;
            } catch (SQLIntegrityConstraintViolationException e) {
                // Almost certainly the receipt number; try the next one. InnoDB
                // rolls back only the failed statement, so the surrounding
                // transaction is still good to carry on with.
                last = e;
            }
        }
        throw last != null ? last : new SQLException("Could not allocate a receipt number.");
    }

    /**
     * Cancels a receipt without deleting it. The row stays, marked VOID, and
     * drops out of every paid figure because {@link #PAID_SUM} counts ACTIVE
     * rows only - so the candidate's balance corrects itself.
     */
    public boolean voidPayment(int examPaymentId, String reason, Integer byUserId)
            throws SQLException {
        String sql = "UPDATE exam_payments "
                   + "   SET status = 'VOID', void_reason = ?, voided_at = NOW(), voided_by_id = ? "
                   + " WHERE exam_payment_id = ? AND status = 'ACTIVE'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setNullable(ps, 1, reason);
            if (byUserId == null) ps.setNull(2, Types.INTEGER);
            else                  ps.setInt(2, byUserId);
            ps.setInt(3, examPaymentId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Sets the list price for an exam. */
    public boolean setExamFee(int examId, BigDecimal fee) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE exams SET exam_fee = ? WHERE exam_id = ?")) {
            ps.setBigDecimal(1, Money.of(fee));
            ps.setInt(2, examId);
            return ps.executeUpdate() > 0;
        }
    }

    /* ─────────────────── waivers and concessions ─────────────────── */

    /**
     * Waives (or un-waives) a candidate's fee.
     *
     * The reason is kept even when the waiver is lifted, so the history of why a
     * free seat was given does not vanish the moment somebody changes their mind.
     */
    public boolean setWaiver(int candidateId, boolean waived, String reason) throws SQLException {
        String sql = waived
                ? "UPDATE exam_candidates SET fee_waived = 1, waiver_reason = ? WHERE candidate_id = ?"
                : "UPDATE exam_candidates SET fee_waived = 0 WHERE candidate_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (waived) { setNullable(ps, 1, reason); ps.setInt(2, candidateId); }
            else        { ps.setInt(1, candidateId); }
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Sets or clears a candidate's own fee, which overrides the exam's price.
     * A null clears it, putting the candidate back on the list price.
     */
    public boolean setCandidateFee(int candidateId, BigDecimal fee) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE exam_candidates SET fee_amount = ? WHERE candidate_id = ?")) {
            if (fee == null) ps.setNull(1, Types.DECIMAL);
            else             ps.setBigDecimal(1, Money.of(fee));
            ps.setInt(2, candidateId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * The fee position of specific candidates, in roll order.
     *
     * Used by the bulk school payment, which allocates a cheque across a ticked
     * list. Roll order is not cosmetic: it is the order the money is handed out
     * in, so a short cheque always leaves the same candidate short, and re-running
     * the same numbers produces the same answer.
     */
    public List<CandidateFee> findByIds(List<Integer> candidateIds) throws SQLException {
        List<CandidateFee> out = new ArrayList<>();
        if (candidateIds == null || candidateIds.isEmpty()) return out;

        StringBuilder in = new StringBuilder();
        for (int i = 0; i < candidateIds.size(); i++) in.append(i == 0 ? "?" : ",?");

        String sql = FEE_SELECT + " WHERE c.candidate_id IN (" + in + ") ORDER BY c.roll_no";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < candidateIds.size(); i++) ps.setInt(i + 1, candidateIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapFee(rs));
            }
        }
        return out;
    }

    /* ─────────────────── school (bulk) receipts ─────────────────── */

    /** Writes the cheque row on the caller's connection, inside their transaction. */
    public int insertSchoolReceipt(Connection con, SchoolReceipt r) throws SQLException {
        String sql = "INSERT INTO school_receipts "
                   + "(exam_id, school_name, receipt_no, total_amount, payment_mode, "
                   + " payment_date, txn_ref, remarks, collected_by, collected_by_id) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?)";

        SQLException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            String receiptNo = nextSchoolReceiptNo(con);
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, r.getExamId());
                ps.setString(2, r.getSchoolName());
                ps.setString(3, receiptNo);
                ps.setBigDecimal(4, Money.of(r.getTotalAmount()));
                ps.setString(5, r.getPaymentMode() == null ? "Cheque" : r.getPaymentMode());
                ps.setString(6, r.getPaymentDate());
                setNullable(ps, 7, r.getTxnRef());
                setNullable(ps, 8, r.getRemarks());
                setNullable(ps, 9, r.getCollectedBy());
                if (r.getCollectedById() == null) ps.setNull(10, Types.INTEGER);
                else                              ps.setInt(10, r.getCollectedById());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        r.setSchoolReceiptId(keys.getInt(1));
                        r.setReceiptNo(receiptNo);
                        return r.getSchoolReceiptId();
                    }
                }
                return 0;
            } catch (SQLIntegrityConstraintViolationException e) {
                last = e;
            }
        }
        throw last != null ? last : new SQLException("Could not allocate a school receipt number.");
    }

    /** School cheques for one exam, newest first, with their live allocation. */
    public List<SchoolReceipt> schoolReceipts(int examId) throws SQLException {
        String sql = "SELECT r.*, e.exam_name, "
                   + "  (SELECT COALESCE(SUM(p.amount),0) FROM exam_payments p "
                   + "    WHERE p.school_receipt_id = r.school_receipt_id AND p.status='ACTIVE') AS alloc, "
                   + "  (SELECT COUNT(*) FROM exam_payments p "
                   + "    WHERE p.school_receipt_id = r.school_receipt_id AND p.status='ACTIVE') AS n "
                   + "  FROM school_receipts r JOIN exams e ON e.exam_id = r.exam_id "
                   + " WHERE r.exam_id = ? ORDER BY r.payment_date DESC, r.school_receipt_id DESC";
        List<SchoolReceipt> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapSchoolReceipt(rs));
            }
        }
        return out;
    }

    /** One school cheque with the individual receipts it was split into. */
    public SchoolReceipt findSchoolReceipt(int schoolReceiptId) throws SQLException {
        String sql = "SELECT r.*, e.exam_name, "
                   + "  (SELECT COALESCE(SUM(p.amount),0) FROM exam_payments p "
                   + "    WHERE p.school_receipt_id = r.school_receipt_id AND p.status='ACTIVE') AS alloc, "
                   + "  (SELECT COUNT(*) FROM exam_payments p "
                   + "    WHERE p.school_receipt_id = r.school_receipt_id AND p.status='ACTIVE') AS n "
                   + "  FROM school_receipts r JOIN exams e ON e.exam_id = r.exam_id "
                   + " WHERE r.school_receipt_id = ?";
        SchoolReceipt r;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, schoolReceiptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                r = mapSchoolReceipt(rs);
            }
        }

        String splits = "SELECT p.*, c.roll_no, i.full_name, e.exam_name, i.school_name "
                      + "  FROM exam_payments p "
                      + "  JOIN exam_candidates c ON c.candidate_id = p.candidate_id "
                      + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                      + "  JOIN exams e ON e.exam_id = c.exam_id "
                      + " WHERE p.school_receipt_id = ? ORDER BY c.roll_no";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(splits)) {
            ps.setInt(1, schoolReceiptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.getSplits().add(mapPayment(rs));
            }
        }
        return r;
    }

    private SchoolReceipt mapSchoolReceipt(ResultSet rs) throws SQLException {
        SchoolReceipt r = new SchoolReceipt();
        r.setSchoolReceiptId(rs.getInt("school_receipt_id"));
        r.setExamId(rs.getInt("exam_id"));
        r.setSchoolName(rs.getString("school_name"));
        r.setReceiptNo(rs.getString("receipt_no"));
        r.setTotalAmount(rs.getBigDecimal("total_amount"));
        r.setPaymentMode(rs.getString("payment_mode"));
        r.setPaymentDate(rs.getString("payment_date"));
        r.setTxnRef(rs.getString("txn_ref"));
        r.setRemarks(rs.getString("remarks"));
        r.setCollectedBy(rs.getString("collected_by"));
        int uid = rs.getInt("collected_by_id");
        r.setCollectedById(rs.wasNull() ? null : uid);
        r.setCreatedAt(rs.getString("created_at"));
        r.setExamName(rs.getString("exam_name"));
        r.setAllocated(rs.getBigDecimal("alloc"));
        r.setCandidateCount(rs.getInt("n"));
        return r;
    }

    /** "SCH-2627-0007" - same shape as the candidate series, separate sequence. */
    private String nextSchoolReceiptNo(Connection con) throws SQLException {
        int yy     = Year.now().getValue() % 100;
        int nextYy = (yy + 1) % 100;
        String prefix = String.format("SCH-%02d%02d-", yy, nextYy);

        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(receipt_no, ?) AS UNSIGNED)), 0) + 1 AS nxt "
                   + "  FROM school_receipts WHERE receipt_no LIKE ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, prefix.length() + 1);
            ps.setString(2, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int next = rs.next() ? rs.getInt("nxt") : 1;
                return prefix + String.format("%04d", next);
            }
        }
    }

    /** Distinct schools among an exam's candidates, for the filter. */
    public List<String> schools(int examId) throws SQLException {
        String sql = "SELECT DISTINCT i.school_name FROM exam_candidates c "
                   + "  JOIN inquiries i ON i.inquiry_id = c.inquiry_id "
                   + " WHERE c.exam_id = ? AND i.school_name IS NOT NULL AND i.school_name <> '' "
                   + " ORDER BY i.school_name";
        List<String> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    /* ─────────────────────── internals ─────────────────────── */

    /** "EXM-2627-0004" - financial-year pair, then a sequence within it. */
    private String nextReceiptNo(Connection con) throws SQLException {
        int yy     = Year.now().getValue() % 100;
        int nextYy = (yy + 1) % 100;
        String prefix = String.format("EXM-%02d%02d-", yy, nextYy);

        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(receipt_no, ?) AS UNSIGNED)), 0) + 1 AS nxt "
                   + "  FROM exam_payments WHERE receipt_no LIKE ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, prefix.length() + 1);       // SUBSTRING is 1-based
            ps.setString(2, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                int next = rs.next() ? rs.getInt("nxt") : 1;
                return prefix + String.format("%04d", next);
            }
        }
    }

    private CandidateFee mapFee(ResultSet rs) throws SQLException {
        CandidateFee f = new CandidateFee();
        f.setCandidateId(rs.getInt("candidate_id"));
        f.setExamId(rs.getInt("exam_id"));
        f.setRollNo(rs.getString("roll_no"));
        f.setCandidateStatus(rs.getString("cand_status"));
        f.setCandidateName(rs.getString("full_name"));
        f.setMobile(rs.getString("mobile"));
        f.setParentMobile(rs.getString("parent_mobile"));
        f.setSchoolName(rs.getString("school_name"));
        f.setClassName(rs.getString("current_class"));
        f.setExamName(rs.getString("exam_name"));
        f.setExamType(rs.getString("exam_type"));
        f.setExamDate(rs.getString("exam_date"));
        f.setExamFee(rs.getBigDecimal("exam_fee"));
        BigDecimal override = rs.getBigDecimal("fee_amount");
        f.setFeeAmount(rs.wasNull() ? null : override);
        f.setFeeWaived(rs.getInt("fee_waived") == 1);
        f.setWaiverReason(rs.getString("waiver_reason"));
        f.setPaid(rs.getBigDecimal("paid"));
        f.setReceiptCount(rs.getInt("receipts"));
        return f;
    }

    private ExamPayment mapPayment(ResultSet rs) throws SQLException {
        ExamPayment p = new ExamPayment();
        p.setExamPaymentId(rs.getInt("exam_payment_id"));
        p.setCandidateId(rs.getInt("candidate_id"));
        p.setReceiptNo(rs.getString("receipt_no"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setPaymentMode(rs.getString("payment_mode"));
        p.setPaymentDate(rs.getString("payment_date"));
        p.setTxnRef(rs.getString("txn_ref"));
        int sr = rs.getInt("school_receipt_id");
        p.setSchoolReceiptId(rs.wasNull() ? null : sr);
        p.setRemarks(rs.getString("remarks"));
        p.setStatus(rs.getString("status"));
        p.setVoidReason(rs.getString("void_reason"));
        p.setVoidedAt(rs.getString("voided_at"));
        p.setCollectedBy(rs.getString("collected_by"));
        int uid = rs.getInt("collected_by_id");
        p.setCollectedById(rs.wasNull() ? null : uid);
        p.setCreatedAt(rs.getString("created_at"));
        p.setRollNo(rs.getString("roll_no"));
        p.setCandidateName(rs.getString("full_name"));
        p.setExamName(rs.getString("exam_name"));
        try { p.setSchoolName(rs.getString("school_name")); }
        catch (SQLException ignore) { /* not selected by every query */ }
        return p;
    }

    private void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof Integer) ps.setInt(i + 1, (Integer) a);
            else                      ps.setString(i + 1, String.valueOf(a));
        }
    }

    private void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(idx, Types.VARCHAR);
        else                                 ps.setString(idx, v.trim());
    }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
