package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.tution.model.Inquiry;
import com.tution.util.DBConnection;
import com.tution.util.Dates;

/** Data-access for enquiries / sales leads. */
public class InquiryDAO {

    /**
     * Every lead column plus the two joined display fields. Kept in one place so
     * the several finders below cannot drift apart.
     */
    private static final String COLS =
          "i.inquiry_id, i.full_name, i.mobile, i.parent_name, i.parent_mobile, i.email, "
        + "i.dob, i.gender, i.city, i.address, "
        + "i.current_class, i.prev_qualification, i.school_name, i.board, i.percentage, "
        + "i.class_interest, i.course_name, i.batch_pref, i.learning_mode, i.branch, i.expected_join_date, "
        + "i.source, i.message, i.priority, i.lead_stage, i.lead_sub_stage, i.counsellor_id, i.next_followup_date, i.status, "
        + "i.student_requirements, i.parent_feedback, i.counsellor_remarks, "
        + "i.converted_student_id, i.created_at, i.updated_at, "
        + "u.full_name AS counsellor_name, s.admission_no AS converted_admission_no";

    private static final String FROM =
          "FROM inquiries i "
        + "LEFT JOIN users    u ON u.user_id    = i.counsellor_id "
        + "LEFT JOIN students s ON s.student_id = i.converted_student_id ";

    // ────────────────────────────────────────────────────────────────
    //  Search / list
    // ────────────────────────────────────────────────────────────────

    /**
     * Filter for the lead list. Every field is optional — a null or blank value
     * means "do not filter on this".
     */
    public static class Filter {
        public String  q;             // free text: name / mobile / parent mobile / email
        public String  status;
        public String  priority;
        public String  leadStage;
        public String  source;
        public Integer counsellorId;  // filter by a chosen counsellor
        public String  fromDate;      // enquiry date range (yyyy-MM-dd)
        public String  toDate;
        public boolean overdueOnly;   // next_followup_date < today and still open

        /** Next-follow-up due window — drives the "today / this week" work queue. */
        public String  dueFrom;
        public String  dueTo;
        /** Exclude leads that are finished, so the work queue only shows live ones. */
        public boolean openOnly;
        /** Work-queue ordering: soonest due first, instead of newest lead first. */
        public boolean orderByDue;

        /**
         * Row-level scoping. When set, only this counsellor's leads are returned
         * regardless of the other filters — this is what stops one counsellor
         * reading another's pipeline. ADMIN passes null.
         */
        public Integer scopeCounsellorId;
    }

    /** Leads matching the filter, newest first. */
    public List<Inquiry> find(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLS).append(" ").append(FROM).append("WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (f != null) {
            if (notBlank(f.q)) {
                sql.append("AND (i.full_name LIKE ? OR i.mobile LIKE ? OR i.parent_mobile LIKE ? OR i.email LIKE ?) ");
                String like = "%" + f.q.trim() + "%";
                args.add(like); args.add(like); args.add(like); args.add(like);
            }
            if (notBlank(f.status))   { sql.append("AND i.status = ? ");   args.add(f.status); }
            if (notBlank(f.priority)) { sql.append("AND i.priority = ? "); args.add(f.priority); }
            if (notBlank(f.leadStage)){ sql.append("AND i.lead_stage = ? "); args.add(f.leadStage); }
            if (notBlank(f.source))   { sql.append("AND i.source = ? ");   args.add(f.source); }
            if (f.counsellorId != null) { sql.append("AND i.counsellor_id = ? "); args.add(f.counsellorId); }
            if (notBlank(f.fromDate)) { sql.append("AND DATE(i.created_at) >= ? "); args.add(f.fromDate); }
            if (notBlank(f.toDate))   { sql.append("AND DATE(i.created_at) <= ? "); args.add(f.toDate); }
            if (f.overdueOnly) {
                sql.append("AND i.next_followup_date IS NOT NULL AND i.next_followup_date < CURDATE() ")
                   .append("AND i.status NOT IN ('CONVERTED','NOT_INTERESTED','LOST') ");
            }
            // The due window is a range of DAYS, but the column now carries a
            // time too — compare on the date part so a lead due at 4:30 PM
            // still lands in "today".
            if (notBlank(f.dueFrom)) { sql.append("AND DATE(i.next_followup_date) >= ? "); args.add(f.dueFrom); }
            if (notBlank(f.dueTo))   { sql.append("AND DATE(i.next_followup_date) <= ? "); args.add(f.dueTo); }
            if (f.openOnly) {
                sql.append("AND i.status NOT IN ('CONVERTED','NOT_INTERESTED','LOST') ");
            }
            if (f.scopeCounsellorId != null) {
                sql.append("AND ").append(Scope.teamOf("i.counsellor_id")).append(' ');
                args.add(f.scopeCounsellorId);
            }
        }
        sql.append((f != null && f.orderByDue)
                   ? "ORDER BY i.next_followup_date ASC, i.priority = 'Hot' DESC, i.inquiry_id DESC"
                   : "ORDER BY i.inquiry_id DESC");

        List<Inquiry> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /** All leads, newest first. Unchanged behaviour for existing callers. */
    public List<Inquiry> findAll() throws SQLException {
        return find(null);
    }

    /** Single lead by id (also used to pre-fill the admission form), or null. */
    public Inquiry findById(int id) throws SQLException {
        String sql = "SELECT " + COLS + " " + FROM + "WHERE i.inquiry_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Existing leads reachable on this mobile number — matched against BOTH the
     * student and the parent mobile, because families re-enquire on either.
     * Used for the duplicate-enquiry warning.
     *
     * @param excludeId pass the lead being edited so it does not flag itself; 0 when creating.
     */
    public List<Inquiry> findByMobile(String mobile, int excludeId) throws SQLException {
        if (!notBlank(mobile)) {
            return new ArrayList<>();
        }
        String sql = "SELECT " + COLS + " " + FROM
                   + "WHERE (i.mobile = ? OR i.parent_mobile = ?) AND i.inquiry_id <> ? "
                   + "ORDER BY i.inquiry_id DESC";
        List<Inquiry> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String m = mobile.trim();
            ps.setString(1, m);
            ps.setString(2, m);
            ps.setInt(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    // ────────────────────────────────────────────────────────────────
    //  Write
    // ────────────────────────────────────────────────────────────────

    /**
     * Minimal insert used by the PUBLIC enquiry form. Deliberately unchanged —
     * inquiry.jsp still calls this and must keep working.
     */
    public void insert(Inquiry q) throws SQLException {
        String sql = "INSERT INTO inquiries "
            + "(full_name, mobile, email, class_interest, source, message) "
            + "VALUES (?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, q.getFullName());
            ps.setString(2, q.getMobile());
            setNullable(ps, 3, q.getEmail());
            setNullable(ps, 4, q.getClassInterest());
            setNullable(ps, 5, q.getSource());
            setNullable(ps, 6, q.getMessage());
            ps.executeUpdate();
        }
    }

    /** Full counsellor-side insert. Returns the new inquiry_id. */
    public int insertFull(Inquiry q) throws SQLException {
        String sql = "INSERT INTO inquiries ("
            + "full_name, mobile, parent_name, parent_mobile, email, dob, gender, city, address, "
            + "current_class, prev_qualification, school_name, board, percentage, "
            + "class_interest, course_name, batch_pref, learning_mode, branch, expected_join_date, "
            + "source, message, priority, lead_stage, lead_sub_stage, counsellor_id, next_followup_date, status, "
            + "student_requirements, parent_feedback, counsellor_remarks"
            + ") VALUES (?,?,?,?,?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?, ?,?,?,?,?,?,?,?, ?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindLead(ps, q);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /** Updates every editable lead field. */
    public void update(Inquiry q) throws SQLException {
        String sql = "UPDATE inquiries SET "
            + "full_name=?, mobile=?, parent_name=?, parent_mobile=?, email=?, dob=?, gender=?, city=?, address=?, "
            + "current_class=?, prev_qualification=?, school_name=?, board=?, percentage=?, "
            + "class_interest=?, course_name=?, batch_pref=?, learning_mode=?, branch=?, expected_join_date=?, "
            + "source=?, message=?, priority=?, lead_stage=?, lead_sub_stage=?, counsellor_id=?, next_followup_date=?, status=?, "
            + "student_requirements=?, parent_feedback=?, counsellor_remarks=? "
            + "WHERE inquiry_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int idx = bindLead(ps, q);
            ps.setInt(idx, q.getInquiryId());
            ps.executeUpdate();
        }
    }

    /**
     * Moves a lead's stage and sub stage without touching anything else — used
     * when a counsellor changes it while logging a call.
     *
     * `priority` is kept in step for the three stages that can express it, since
     * the leads list, the counsellor dashboard and the follow-up queue all sort
     * and filter on it.
     */
    public void updateStage(int id, String stage, String subStage) throws SQLException {
        boolean mapsToPriority = stage != null
                && ("Hot".equalsIgnoreCase(stage.trim())
                 || "Warm".equalsIgnoreCase(stage.trim())
                 || "Cold".equalsIgnoreCase(stage.trim()));
        String sql = "UPDATE inquiries SET lead_stage = ?, lead_sub_stage = ?"
                   + (mapsToPriority ? ", priority = ?" : "")
                   + " WHERE inquiry_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            setNullable(ps, i++, stage);
            setNullable(ps, i++, subStage);
            if (mapsToPriority) ps.setString(i++, stage.trim());
            ps.setInt(i, id);
            ps.executeUpdate();
        }
    }

    /** Updates the workflow status (e.g. CONVERTED). */
    public void updateStatus(int id, String status) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE inquiries SET status = ? WHERE inquiry_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Status change that also records why, and optionally the next follow-up date. */
    public void updateStatus(int id, String status, String remarks, String nextFollowup)
            throws SQLException {
        String sql = "UPDATE inquiries SET status = ?, counsellor_remarks = COALESCE(?, counsellor_remarks), "
                   + "next_followup_date = ? WHERE inquiry_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            setNullable(ps, 2, remarks);
            Dates.setNullableDateTime(ps, 3, nextFollowup);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    /** Assigns (or clears, with null) the owning counsellor. */
    public void assign(int inquiryId, Integer counsellorId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE inquiries SET counsellor_id = ? WHERE inquiry_id = ?")) {
            if (counsellorId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, counsellorId);
            }
            ps.setInt(2, inquiryId);
            ps.executeUpdate();
        }
    }

    /** Sets only the next follow-up date and time (called after logging a follow-up). */
    public void setNextFollowup(int inquiryId, String date) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE inquiries SET next_followup_date = ? WHERE inquiry_id = ?")) {
            Dates.setNullableDateTime(ps, 1, date);
            ps.setInt(2, inquiryId);
            ps.executeUpdate();
        }
    }

    /** Marks the lead converted and links it to the student record it became. */
    public void markConverted(int inquiryId, int studentId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE inquiries SET status='CONVERTED', converted_student_id=?, "
               + "next_followup_date=NULL WHERE inquiry_id=?")) {
            ps.setInt(1, studentId);
            ps.setInt(2, inquiryId);
            ps.executeUpdate();
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    /** Binds the 29 lead columns in the order used by insertFull/update. Returns the next index. */
    private int bindLead(PreparedStatement ps, Inquiry q) throws SQLException {
        int i = 1;
        ps.setString(i++, q.getFullName());
        ps.setString(i++, q.getMobile());
        setNullable(ps, i++, q.getParentName());
        setNullable(ps, i++, q.getParentMobile());
        setNullable(ps, i++, q.getEmail());
        setNullableDate(ps, i++, q.getDob());
        setNullable(ps, i++, q.getGender());
        setNullable(ps, i++, q.getCity());
        setNullable(ps, i++, q.getAddress());

        setNullable(ps, i++, q.getCurrentClass());
        setNullable(ps, i++, q.getPrevQualification());
        setNullable(ps, i++, q.getSchoolName());
        setNullable(ps, i++, q.getBoard());
        setNullable(ps, i++, q.getPercentage());

        setNullable(ps, i++, q.getClassInterest());
        setNullable(ps, i++, q.getCourseName());
        setNullable(ps, i++, q.getBatchPref());
        setNullable(ps, i++, q.getLearningMode());
        setNullable(ps, i++, q.getBranch());
        setNullableDate(ps, i++, q.getExpectedJoinDate());

        setNullable(ps, i++, q.getSource());
        setNullable(ps, i++, q.getMessage());
        // Lead Stage replaced Lead Priority on the form, but `priority` still
        // drives the leads-list filter, the counsellor dashboard and the
        // follow-up screen — so keep it in step whenever the stage is one of the
        // three it can express, and leave it alone otherwise.
        ps.setString(i++, blankTo(priorityFor(q), "Warm"));
        setNullable(ps, i++, q.getLeadStage());
        setNullable(ps, i++, q.getLeadSubStage());
        if (q.getCounsellorId() == null) {
            ps.setNull(i++, Types.INTEGER);
        } else {
            ps.setInt(i++, q.getCounsellorId());
        }
        Dates.setNullableDateTime(ps, i++, q.getNextFollowupDate());
        ps.setString(i++, blankTo(q.getStatus(), "NEW"));

        setNullable(ps, i++, q.getStudentRequirements());
        setNullable(ps, i++, q.getParentFeedback());
        setNullable(ps, i++, q.getCounsellorRemarks());
        return i;
    }

    /**
     * The Hot/Warm/Cold value to store alongside the lead stage.
     *
     * The first three stages ARE the old priority, so they map straight across.
     * For any other stage (Lost, CPA Registered, Junk Lead …) priority has no
     * equivalent, so whatever the lead already carried is kept rather than
     * silently reset to Warm — that would quietly reshuffle the leads list.
     */
    private String priorityFor(Inquiry q) {
        String stage = q.getLeadStage();
        if (stage != null) {
            String s = stage.trim();
            if ("Hot".equalsIgnoreCase(s) || "Warm".equalsIgnoreCase(s) || "Cold".equalsIgnoreCase(s)) {
                return s;
            }
        }
        return q.getPriority();
    }

    private Inquiry map(ResultSet rs) throws SQLException {
        Inquiry q = new Inquiry();
        q.setInquiryId(rs.getInt("inquiry_id"));
        q.setFullName(rs.getString("full_name"));
        q.setMobile(rs.getString("mobile"));
        q.setParentName(rs.getString("parent_name"));
        q.setParentMobile(rs.getString("parent_mobile"));
        q.setEmail(rs.getString("email"));
        q.setDob(dateStr(rs, "dob"));
        q.setGender(rs.getString("gender"));
        q.setCity(rs.getString("city"));
        q.setAddress(rs.getString("address"));

        q.setCurrentClass(rs.getString("current_class"));
        q.setPrevQualification(rs.getString("prev_qualification"));
        q.setSchoolName(rs.getString("school_name"));
        q.setBoard(rs.getString("board"));
        q.setPercentage(rs.getString("percentage"));

        q.setClassInterest(rs.getString("class_interest"));
        q.setCourseName(rs.getString("course_name"));
        q.setBatchPref(rs.getString("batch_pref"));
        q.setLearningMode(rs.getString("learning_mode"));
        q.setBranch(rs.getString("branch"));
        q.setExpectedJoinDate(dateStr(rs, "expected_join_date"));

        q.setSource(rs.getString("source"));
        q.setMessage(rs.getString("message"));
        q.setPriority(rs.getString("priority"));
        q.setLeadStage(rs.getString("lead_stage"));
        q.setLeadSubStage(rs.getString("lead_sub_stage"));
        int cid = rs.getInt("counsellor_id");
        q.setCounsellorId(rs.wasNull() ? null : cid);
        q.setCounsellorName(rs.getString("counsellor_name"));
        q.setNextFollowupDate(Dates.read(rs, "next_followup_date"));
        q.setStatus(rs.getString("status"));

        q.setStudentRequirements(rs.getString("student_requirements"));
        q.setParentFeedback(rs.getString("parent_feedback"));
        q.setCounsellorRemarks(rs.getString("counsellor_remarks"));

        int sid = rs.getInt("converted_student_id");
        q.setConvertedStudentId(rs.wasNull() ? null : sid);
        q.setConvertedAdmissionNo(rs.getString("converted_admission_no"));

        q.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        java.sql.Timestamp upd = rs.getTimestamp("updated_at");
        q.setUpdatedAt(upd == null ? null : String.valueOf(upd));
        return q;
    }

    /** Reads a DATE column as "yyyy-MM-dd", or null. Keeps the model String-based. */
    private static String dateStr(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toString();
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object a = args.get(i);
            if (a instanceof Integer) {
                ps.setInt(i + 1, (Integer) a);
            } else {
                ps.setString(i + 1, String.valueOf(a));
            }
        }
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }

    /**
     * Sets a DATE parameter from a "yyyy-MM-dd" string. Anything blank or
     * unparseable becomes SQL NULL rather than throwing — an empty date input
     * on a form is normal, not an error.
     */
    private static void setNullableDate(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, Types.DATE);
            return;
        }
        try {
            ps.setDate(idx, java.sql.Date.valueOf(val.trim()));
        } catch (IllegalArgumentException e) {
            ps.setNull(idx, Types.DATE);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static String blankTo(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }
}
