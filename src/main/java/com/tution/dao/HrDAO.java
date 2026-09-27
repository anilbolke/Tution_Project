package com.tution.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.model.Payslip;
import com.tution.model.StaffAttendance;
import com.tution.model.StaffLeave;
import com.tution.model.User;
import com.tution.util.DBConnection;
import com.tution.util.Money;

/**
 * Data-access for the HR module: staff attendance, leave and payroll.
 *
 * The three sit together because payroll reads the other two — a payslip's
 * payable days come from the attendance marks for that month, less any approved
 * unpaid leave. Splitting them across three DAOs would mean the one calculation
 * that matters had to reach into all of them anyway.
 */
public class HrDAO {

    /* ─────────────────────── staff attendance ─────────────────────── */

    /**
     * One day's marks for everyone with an active login, whether or not they
     * have been marked yet.
     *
     * The LEFT JOIN is the point: the marking screen has to list every employee
     * so an unmarked one is visible as a gap, rather than quietly missing from a
     * page that only shows rows that already exist.
     */
    public List<StaffAttendance> dayRegister(String date) throws SQLException {
        String sql = "SELECT u.user_id, u.full_name, u.role, "
                   + "       a.att_id, a.status, a.remarks, a.marked_by "
                   + "  FROM users u "
                   + "  LEFT JOIN staff_attendance a "
                   + "         ON a.user_id = u.user_id AND a.att_date = ? "
                   + " WHERE u.is_active = 1 "
                   + " ORDER BY FIELD(u.role,'ADMIN','ACCOUNTANT','HR','STAFF','COUNSELLOR',"
                   + "          'TEACHER'), u.full_name";
        List<StaffAttendance> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StaffAttendance a = new StaffAttendance();
                    a.setUserId(rs.getInt("user_id"));
                    a.setStaffName(rs.getString("full_name"));
                    a.setRole(rs.getString("role"));
                    a.setAttDate(date);
                    int id = rs.getInt("att_id");
                    a.setAttId(rs.wasNull() ? 0 : id);
                    a.setStatus(rs.getString("status"));   // null when unmarked
                    a.setRemarks(rs.getString("remarks"));
                    a.setMarkedBy(rs.getString("marked_by"));
                    out.add(a);
                }
            }
        }
        return out;
    }

    /**
     * Saves a whole day in one transaction.
     *
     * Upsert rather than delete-then-insert: correcting one person's mark must
     * not momentarily leave the rest of the day blank, and the unique key on
     * (user_id, att_date) is what makes re-marking idempotent.
     */
    public int saveDay(String date, Map<Integer,String> marks, Map<Integer,String> remarks,
                       String markedBy) throws SQLException {
        String sql = "INSERT INTO staff_attendance (user_id, att_date, status, remarks, marked_by) "
                   + "VALUES (?,?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE status=VALUES(status), remarks=VALUES(remarks), "
                   + "                        marked_by=VALUES(marked_by)";
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            int n = 0;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (Map.Entry<Integer,String> e : marks.entrySet()) {
                    if (e.getValue() == null || e.getValue().isEmpty()) {
                        continue;               // left blank = not marked today
                    }
                    ps.setInt(1, e.getKey());
                    ps.setString(2, date);
                    ps.setString(3, e.getValue());
                    String r = remarks == null ? null : remarks.get(e.getKey());
                    setNullable(ps, 4, r);
                    setNullable(ps, 5, markedBy);
                    ps.addBatch();
                    n++;
                }
                ps.executeBatch();
            }
            con.commit();
            return n;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /** Per-employee totals for a month — the attendance summary screen. */
    public List<Map<String,Object>> monthSummary(int year, int month) throws SQLException {
        String sql = "SELECT u.user_id, u.full_name, u.role, "
                   + "  SUM(a.status='PRESENT')  AS present_d, "
                   + "  SUM(a.status='ABSENT')   AS absent_d, "
                   + "  SUM(a.status='HALF_DAY') AS half_d, "
                   + "  SUM(a.status='LEAVE')    AS leave_d, "
                   + "  SUM(a.status IN ('HOLIDAY','WEEK_OFF')) AS off_d, "
                   + "  COUNT(a.att_id) AS marked_d "
                   + "  FROM users u "
                   + "  LEFT JOIN staff_attendance a ON a.user_id = u.user_id "
                   + "        AND YEAR(a.att_date) = ? AND MONTH(a.att_date) = ? "
                   + " WHERE u.is_active = 1 "
                   + " GROUP BY u.user_id, u.full_name, u.role "
                   + " ORDER BY u.full_name";
        List<Map<String,Object>> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("userId",  rs.getInt("user_id"));
                    m.put("name",    rs.getString("full_name"));
                    m.put("role",    rs.getString("role"));
                    m.put("present", rs.getInt("present_d"));
                    m.put("absent",  rs.getInt("absent_d"));
                    m.put("half",    rs.getInt("half_d"));
                    m.put("leave",   rs.getInt("leave_d"));
                    m.put("off",     rs.getInt("off_d"));
                    m.put("marked",  rs.getInt("marked_d"));
                    out.add(m);
                }
            }
        }
        return out;
    }

    /* ─────────────────────────── leave ─────────────────────────── */

    private static final String LEAVE_COLS =
          "l.leave_id, l.user_id, l.leave_type, l.from_date, l.to_date, l.days, l.reason, "
        + "l.status, l.decision_note, l.decided_by, l.decided_at, l.applied_by, l.created_at, "
        + "u.full_name, u.role";

    /** Leave requests, newest first. Pass null for status to get every one. */
    public List<StaffLeave> leaves(String status) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(LEAVE_COLS)
            .append(" FROM staff_leave l JOIN users u ON u.user_id = l.user_id WHERE 1=1 ");
        if (status != null && !status.isEmpty()) {
            sql.append("AND l.status = ? ");
        }
        // Pending first whatever the filter: an undecided request is the only
        // kind that needs somebody to do something.
        sql.append("ORDER BY l.status='PENDING' DESC, l.from_date DESC, l.leave_id DESC");

        List<StaffLeave> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (status != null && !status.isEmpty()) {
                ps.setString(1, status);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapLeave(rs));
            }
        }
        return out;
    }

    /* ───────────── self-service (/my-hr): one person's own records ───────────── */

    /** One person's marks for a month, oldest first. Unmarked days are simply absent from the list. */
    public List<StaffAttendance> myAttendance(int userId, int year, int month) throws SQLException {
        String sql = "SELECT att_id, att_date, status, remarks, marked_by FROM staff_attendance "
                   + " WHERE user_id = ? AND YEAR(att_date) = ? AND MONTH(att_date) = ? "
                   + " ORDER BY att_date";
        List<StaffAttendance> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, year);
            ps.setInt(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StaffAttendance a = new StaffAttendance();
                    a.setAttId(rs.getInt("att_id"));
                    a.setUserId(userId);
                    a.setAttDate(rs.getString("att_date"));
                    a.setStatus(rs.getString("status"));
                    a.setRemarks(rs.getString("remarks"));
                    a.setMarkedBy(rs.getString("marked_by"));
                    out.add(a);
                }
            }
        }
        return out;
    }

    /** One person's leave requests, newest first. */
    public List<StaffLeave> myLeaves(int userId) throws SQLException {
        String sql = "SELECT " + LEAVE_COLS
                   + " FROM staff_leave l JOIN users u ON u.user_id = l.user_id "
                   + "WHERE l.user_id = ? ORDER BY l.from_date DESC, l.leave_id DESC LIMIT 50";
        List<StaffLeave> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapLeave(rs));
            }
        }
        return out;
    }

    /**
     * One person's payslips, newest first. Drafts are left out: a draft is HR's
     * working figure and can still change, so it is not the person's pay yet.
     */
    public List<Payslip> myPayslips(int userId) throws SQLException {
        String sql = "SELECT " + SLIP_COLS
                   + "  FROM salary_payslip p "
                   + "  JOIN users u ON u.user_id = p.user_id "
                   + "  LEFT JOIN fund_accounts f ON f.fund_id = p.fund_id "
                   + " WHERE p.user_id = ? AND p.status = 'PAID' "
                   + " ORDER BY p.period_year DESC, p.period_month DESC LIMIT 24";
        List<Payslip> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapSlip(rs));
            }
        }
        return out;
    }

    public int applyLeave(StaffLeave l) throws SQLException {
        String sql = "INSERT INTO staff_leave "
                   + "(user_id, leave_type, from_date, to_date, days, reason, status, applied_by) "
                   + "VALUES (?,?,?,?,?,?,'PENDING',?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, l.getUserId());
            ps.setString(2, l.getLeaveType());
            ps.setString(3, l.getFromDate());
            ps.setString(4, l.getToDate());
            ps.setBigDecimal(5, BigDecimal.valueOf(l.getDays()));
            setNullable(ps, 6, l.getReason());
            setNullable(ps, 7, l.getAppliedBy());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                return k.next() ? k.getInt(1) : 0;
            }
        }
    }

    /**
     * Approves or rejects a request, and on approval writes the LEAVE marks onto
     * the attendance register for the days covered.
     *
     * Both in one transaction. An approved leave that did not reach the register
     * would be counted as an absence by payroll and quietly dock the person's
     * pay — the whole reason the two tables have to agree.
     *
     * Existing marks are not overwritten: if somebody was already marked present
     * on a day later covered by leave, that is a conflict a human should look
     * at, not something to silently rewrite.
     */
    public boolean decideLeave(int leaveId, String decision, String note, String by)
            throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            StaffLeave l;
            try (PreparedStatement ps = con.prepareStatement(
                     "SELECT " + LEAVE_COLS + " FROM staff_leave l "
                   + "  JOIN users u ON u.user_id = l.user_id WHERE l.leave_id = ?")) {
                ps.setInt(1, leaveId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { con.rollback(); return false; }
                    l = mapLeave(rs);
                }
            }
            if (!StaffLeave.PENDING.equals(l.getStatus())) {
                con.rollback();
                return false;                    // already decided
            }

            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE staff_leave SET status = ?, decision_note = ?, decided_by = ?, "
                   + "       decided_at = CURRENT_TIMESTAMP WHERE leave_id = ?")) {
                ps.setString(1, decision);
                setNullable(ps, 2, note);
                setNullable(ps, 3, by);
                ps.setInt(4, leaveId);
                ps.executeUpdate();
            }

            if (StaffLeave.APPROVED.equals(decision)) {
                String ins = "INSERT IGNORE INTO staff_attendance "
                           + "(user_id, att_date, status, remarks, marked_by) "
                           + "VALUES (?,?, 'LEAVE', ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(ins)) {
                    LocalDate d  = LocalDate.parse(l.getFromDate());
                    LocalDate to = LocalDate.parse(l.getToDate());
                    String tag = l.getTypeLabel() + " leave #" + leaveId;
                    while (!d.isAfter(to)) {
                        ps.setInt(1, l.getUserId());
                        ps.setString(2, d.toString());
                        ps.setString(3, tag);
                        setNullable(ps, 4, by);
                        ps.addBatch();
                        d = d.plusDays(1);
                    }
                    ps.executeBatch();
                }
            }

            con.commit();
            return true;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /* ────────────────────── salary structure ────────────────────── */

    /**
     * The salary in force for each employee, as a user_id → monthly CTC map.
     *
     * "In force" means the latest effective_from that is not in the future — a
     * raise dated next month must not change this month's payroll.
     */
    public Map<Integer,BigDecimal> currentSalaries() throws SQLException {
        String sql = "SELECT s.user_id, s.monthly_ctc "
                   + "  FROM staff_salary s "
                   + "  JOIN (SELECT user_id, MAX(effective_from) AS eff "
                   + "          FROM staff_salary WHERE effective_from <= CURDATE() "
                   + "         GROUP BY user_id) cur "
                   + "    ON cur.user_id = s.user_id AND cur.eff = s.effective_from";
        Map<Integer,BigDecimal> out = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getInt("user_id"), rs.getBigDecimal("monthly_ctc"));
        }
        return out;
    }

    /** Records a salary, or restates one already dated the same day. */
    public void setSalary(int userId, BigDecimal ctc, String effectiveFrom,
                          String note, String by) throws SQLException {
        String sql = "INSERT INTO staff_salary (user_id, monthly_ctc, effective_from, note, created_by) "
                   + "VALUES (?,?,?,?,?) "
                   + "ON DUPLICATE KEY UPDATE monthly_ctc=VALUES(monthly_ctc), note=VALUES(note), "
                   + "                        created_by=VALUES(created_by)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setBigDecimal(2, Money.of(ctc));
            ps.setString(3, effectiveFrom);
            setNullable(ps, 4, note);
            setNullable(ps, 5, by);
            ps.executeUpdate();
        }
    }

    /* ───────────────────────── payroll ───────────────────────── */

    private static final String SLIP_COLS =
          "p.payslip_id, p.user_id, p.period_year, p.period_month, p.monthly_ctc, p.month_days, "
        + "p.payable_days, p.absent_days, p.unpaid_days, p.gross, p.deductions, p.net_pay, "
        + "p.note, p.status, p.fund_id, p.txn_id, p.paid_on, p.paid_by, p.created_by, "
        + "u.full_name, u.role, f.name AS fund_name";

    public List<Payslip> payslips(int year, int month) throws SQLException {
        String sql = "SELECT " + SLIP_COLS
                   + "  FROM salary_payslip p "
                   + "  JOIN users u ON u.user_id = p.user_id "
                   + "  LEFT JOIN fund_accounts f ON f.fund_id = p.fund_id "
                   + " WHERE p.period_year = ? AND p.period_month = ? "
                   + " ORDER BY u.full_name";
        List<Payslip> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapSlip(rs));
            }
        }
        return out;
    }

    /**
     * Works out and stores a draft payslip for every employee who has a salary
     * on record and does not already have a slip for the month.
     *
     * THE CALCULATION. Payable days start at the number of days in the month and
     * come down: an absence costs a day, a half day costs half, and approved
     * UNPAID leave costs a day each. Paid leave, holidays and week-offs cost
     * nothing. Days nobody marked at all are treated as WORKED rather than
     * absent — an unmarked register is a gap in record-keeping, and defaulting
     * it the other way would dock people's pay for the office forgetting to
     * mark a day.
     *
     * Existing slips are skipped rather than recalculated: once a slip is paid
     * it is a record of money that moved, and re-running the month must not
     * touch it.
     *
     * @return how many drafts were created
     */
    public int generatePayslips(int year, int month, String by) throws SQLException {
        YearMonth ym = YearMonth.of(year, month);
        int monthDays = ym.lengthOfMonth();

        Map<Integer,BigDecimal> salaries = currentSalaries();
        if (salaries.isEmpty()) {
            return 0;
        }

        Map<Integer,double[]> deductibles = monthDeductions(year, month);

        String ins = "INSERT IGNORE INTO salary_payslip "
                   + "(user_id, period_year, period_month, monthly_ctc, month_days, payable_days, "
                   + " absent_days, unpaid_days, gross, deductions, net_pay, status, created_by) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?,?, 'DRAFT', ?)";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            int made = 0;
            try (PreparedStatement ps = con.prepareStatement(ins)) {
                for (Map.Entry<Integer,BigDecimal> e : salaries.entrySet()) {
                    int userId = e.getKey();
                    BigDecimal ctc = Money.of(e.getValue());

                    double[] d = deductibles.get(userId);
                    double absent = d == null ? 0 : d[0];
                    double unpaid = d == null ? 0 : d[1];
                    double payable = monthDays - absent - unpaid;
                    if (payable < 0) payable = 0;

                    // per-day rate × payable days, to the paisa
                    BigDecimal gross = ctc
                        .multiply(BigDecimal.valueOf(payable))
                        .divide(BigDecimal.valueOf(monthDays), 2, RoundingMode.HALF_UP);

                    ps.setInt(1, userId);
                    ps.setInt(2, year);
                    ps.setInt(3, month);
                    ps.setBigDecimal(4, ctc);
                    ps.setInt(5, monthDays);
                    ps.setBigDecimal(6, BigDecimal.valueOf(payable));
                    ps.setBigDecimal(7, BigDecimal.valueOf(absent));
                    ps.setBigDecimal(8, BigDecimal.valueOf(unpaid));
                    ps.setBigDecimal(9, gross);
                    ps.setBigDecimal(10, Money.ZERO);
                    ps.setBigDecimal(11, gross);
                    setNullable(ps, 12, by);
                    ps.addBatch();
                    made++;
                }
                int[] res = ps.executeBatch();
                made = 0;
                for (int r : res) {
                    if (r > 0) made++;      // INSERT IGNORE returns 0 for a skip
                }
            }
            con.commit();
            return made;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /**
     * Days that cost pay in a month, as user_id → {absent, unpaidLeave}.
     *
     * Unpaid leave is counted from the LEAVE table rather than the register,
     * because the register cannot tell paid leave from unpaid — both are marked
     * LEAVE. Counting it from the marks would make every sick day unpaid.
     */
    private Map<Integer,double[]> monthDeductions(int year, int month) throws SQLException {
        Map<Integer,double[]> out = new LinkedHashMap<>();

        String att = "SELECT user_id, "
                   + "       SUM(CASE WHEN status='ABSENT' THEN 1 "
                   + "                WHEN status='HALF_DAY' THEN 0.5 ELSE 0 END) AS lost "
                   + "  FROM staff_attendance "
                   + " WHERE YEAR(att_date) = ? AND MONTH(att_date) = ? "
                   + " GROUP BY user_id";
        String unpaid = "SELECT l.user_id, SUM(l.days) AS d "
                      + "  FROM staff_leave l "
                      + " WHERE l.status = 'APPROVED' AND l.leave_type = 'UNPAID' "
                      + "   AND YEAR(l.from_date) = ? AND MONTH(l.from_date) = ? "
                      + " GROUP BY l.user_id";

        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(att)) {
                ps.setInt(1, year);
                ps.setInt(2, month);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.computeIfAbsent(rs.getInt("user_id"), k -> new double[2])[0] =
                            rs.getDouble("lost");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(unpaid)) {
                ps.setInt(1, year);
                ps.setInt(2, month);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.computeIfAbsent(rs.getInt("user_id"), k -> new double[2])[1] =
                            rs.getDouble("d");
                    }
                }
            }
        }
        return out;
    }

    /**
     * Marks a payslip paid AND debits the fund, together or not at all.
     *
     * Salary is the institute's largest outflow. A slip marked paid without the
     * matching ledger row would overstate the fund balance and let the same
     * money be spent again, which is the exact failure the expense module was
     * built to prevent — so this reuses the same pattern: one connection, both
     * writes, commit or roll back as a pair.
     *
     * @return false if the slip does not exist or has already been paid
     */
    public boolean payPayslip(int payslipId, int fundId, String onDate, User by)
            throws SQLException {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            BigDecimal net;
            int userId;
            String name;
            int year, month;
            try (PreparedStatement ps = con.prepareStatement(
                     "SELECT p.net_pay, p.status, p.user_id, p.period_year, p.period_month, "
                   + "       u.full_name FROM salary_payslip p "
                   + "  JOIN users u ON u.user_id = p.user_id WHERE p.payslip_id = ?")) {
                ps.setInt(1, payslipId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { con.rollback(); return false; }
                    if (!Payslip.DRAFT.equals(rs.getString("status"))) {
                        con.rollback();
                        return false;            // already paid or cancelled
                    }
                    net    = rs.getBigDecimal("net_pay");
                    userId = rs.getInt("user_id");
                    year   = rs.getInt("period_year");
                    month  = rs.getInt("period_month");
                    name   = rs.getString("full_name");
                }
            }

            int txnId;
            String narration = "Salary " + month + "/" + year + " - " + name;
            String debit = "INSERT INTO fund_transactions "
                         + "(fund_id, txn_date, direction, amount, source_type, source_id, "
                         + " narration, created_by, created_by_id) "
                         + "VALUES (?,?, 'DEBIT', ?, 'SALARY', ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(debit, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, fundId);
                ps.setString(2, onDate);
                ps.setBigDecimal(3, Money.of(net));
                ps.setInt(4, payslipId);
                ps.setString(5, narration);
                setNullable(ps, 6, by == null ? null : by.getFullName());
                if (by == null) ps.setNull(7, Types.INTEGER);
                else            ps.setInt(7, by.getUserId());
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) {
                    txnId = k.next() ? k.getInt(1) : 0;
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                     "UPDATE salary_payslip SET status='PAID', fund_id=?, txn_id=?, paid_on=?, "
                   + "       paid_by=? WHERE payslip_id = ? AND status='DRAFT'")) {
                ps.setInt(1, fundId);
                ps.setInt(2, txnId);
                ps.setString(3, onDate);
                setNullable(ps, 4, by == null ? null : by.getFullName());
                ps.setInt(5, payslipId);
                if (ps.executeUpdate() == 0) {
                    // Somebody paid it between the read and the write. Roll the
                    // debit back rather than paying twice.
                    con.rollback();
                    return false;
                }
            }

            con.commit();
            return userId > 0;
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ignore) { }
            }
            throw e;
        } finally {
            close(con);
        }
    }

    /* ───────────────────────── mapping ───────────────────────── */

    /**
     * The numbers the HR dashboard opens with, in one round trip.
     *
     * One method on one connection rather than a dozen callers each opening
     * their own: a landing page that costs a dozen connections per load is how
     * the first screen somebody sees becomes the slowest one in the system.
     */
    public Map<String,Object> dashboard(String date, int year, int month) throws SQLException {
        Map<String,Object> d = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection()) {
            d.put("staffActive",  scalar(con, "SELECT COUNT(*) FROM users WHERE is_active = 1"));
            d.put("staffOff",     scalar(con, "SELECT COUNT(*) FROM users WHERE is_active = 0"));

            d.put("markedToday",  scalar(con,
                "SELECT COUNT(*) FROM staff_attendance WHERE att_date = ?", date));
            d.put("presentToday", scalar(con,
                "SELECT COUNT(*) FROM staff_attendance WHERE att_date = ? "
              + "AND status IN ('PRESENT','HALF_DAY')", date));
            d.put("absentToday",  scalar(con,
                "SELECT COUNT(*) FROM staff_attendance WHERE att_date = ? AND status = 'ABSENT'", date));
            d.put("onLeaveToday", scalar(con,
                "SELECT COUNT(*) FROM staff_attendance WHERE att_date = ? AND status = 'LEAVE'", date));

            d.put("leavePending", scalar(con,
                "SELECT COUNT(*) FROM staff_leave WHERE status = 'PENDING'"));
            d.put("leaveUpcoming", scalar(con,
                "SELECT COUNT(*) FROM staff_leave WHERE status = 'APPROVED' AND from_date >= ?", date));

            d.put("slipsTotal",  scalar(con,
                "SELECT COUNT(*) FROM salary_payslip WHERE period_year = ? AND period_month = ?",
                year, month));
            d.put("slipsPaid",   scalar(con,
                "SELECT COUNT(*) FROM salary_payslip WHERE period_year = ? AND period_month = ? "
              + "AND status = 'PAID'", year, month));
            d.put("payrollDue",  money(con,
                "SELECT COALESCE(SUM(net_pay),0) FROM salary_payslip "
              + "WHERE period_year = ? AND period_month = ? AND status = 'DRAFT'", year, month));
            d.put("payrollPaid", money(con,
                "SELECT COALESCE(SUM(net_pay),0) FROM salary_payslip "
              + "WHERE period_year = ? AND period_month = ? AND status = 'PAID'", year, month));
            // Somebody with no salary on record is silently left out of payroll,
            // so the dashboard has to say how many there are.
            d.put("noSalary", scalar(con,
                "SELECT COUNT(*) FROM users u WHERE u.is_active = 1 AND NOT EXISTS ("
              + "  SELECT 1 FROM staff_salary s WHERE s.user_id = u.user_id "
              + "   AND s.effective_from <= CURDATE())"));
        }
        return d;
    }

    private int scalar(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindAll(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private BigDecimal money(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindAll(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.of(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    private static void bindAll(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Integer) ps.setInt(i + 1, (Integer) args[i]);
            else                            ps.setString(i + 1, String.valueOf(args[i]));
        }
    }

    private StaffLeave mapLeave(ResultSet rs) throws SQLException {
        StaffLeave l = new StaffLeave();
        l.setLeaveId(rs.getInt("leave_id"));
        l.setUserId(rs.getInt("user_id"));
        l.setStaffName(rs.getString("full_name"));
        l.setRole(rs.getString("role"));
        l.setLeaveType(rs.getString("leave_type"));
        l.setFromDate(rs.getString("from_date"));
        l.setToDate(rs.getString("to_date"));
        l.setDays(rs.getDouble("days"));
        l.setReason(rs.getString("reason"));
        l.setStatus(rs.getString("status"));
        l.setDecisionNote(rs.getString("decision_note"));
        l.setDecidedBy(rs.getString("decided_by"));
        java.sql.Timestamp t = rs.getTimestamp("decided_at");
        l.setDecidedAt(t == null ? null : t.toString().substring(0, 16));
        l.setAppliedBy(rs.getString("applied_by"));
        java.sql.Timestamp c = rs.getTimestamp("created_at");
        l.setCreatedAt(c == null ? null : c.toString().substring(0, 10));
        return l;
    }

    private Payslip mapSlip(ResultSet rs) throws SQLException {
        Payslip p = new Payslip();
        p.setPayslipId(rs.getInt("payslip_id"));
        p.setUserId(rs.getInt("user_id"));
        p.setStaffName(rs.getString("full_name"));
        p.setRole(rs.getString("role"));
        p.setPeriodYear(rs.getInt("period_year"));
        p.setPeriodMonth(rs.getInt("period_month"));
        p.setMonthlyCtc(rs.getBigDecimal("monthly_ctc"));
        p.setMonthDays(rs.getInt("month_days"));
        p.setPayableDays(rs.getDouble("payable_days"));
        p.setAbsentDays(rs.getDouble("absent_days"));
        p.setUnpaidDays(rs.getDouble("unpaid_days"));
        p.setGross(rs.getBigDecimal("gross"));
        p.setDeductions(rs.getBigDecimal("deductions"));
        p.setNetPay(rs.getBigDecimal("net_pay"));
        p.setNote(rs.getString("note"));
        p.setStatus(rs.getString("status"));
        int f = rs.getInt("fund_id");
        p.setFundId(rs.wasNull() ? null : f);
        p.setFundName(rs.getString("fund_name"));
        int t = rs.getInt("txn_id");
        p.setTxnId(rs.wasNull() ? null : t);
        p.setPaidOn(rs.getString("paid_on"));
        p.setPaidBy(rs.getString("paid_by"));
        p.setCreatedBy(rs.getString("created_by"));
        return p;
    }

    private static void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.trim().isEmpty()) ps.setNull(idx, Types.VARCHAR);
        else                                 ps.setString(idx, v.trim());
    }

    private static void close(Connection con) {
        if (con != null) {
            try { con.setAutoCommit(true); } catch (SQLException ignore) { }
            try { con.close(); } catch (SQLException ignore) { }
        }
    }
}
