package com.tution.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.tution.model.Student;
import com.tution.util.DBConnection;

/** Data-access for student admissions. */
public class StudentDAO {

    /** Student portal login: admission number + registered mobile. Returns the student or null. */
    public Student authenticate(String admissionNo, String mobile) throws SQLException {
        String sql = "SELECT student_id, admission_no, full_name, class_name, board, "
                   + "student_mobile, parent_mobile, fee_slab "
                   + "FROM students WHERE admission_no = ? AND student_mobile = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, admissionNo);
            ps.setString(2, mobile);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Student s = new Student();
                s.setStudentId(rs.getInt("student_id"));
                s.setAdmissionNo(rs.getString("admission_no"));
                s.setFullName(rs.getString("full_name"));
                s.setClassName(rs.getString("class_name"));
                s.setBoard(rs.getString("board"));
                s.setStudentMobile(rs.getString("student_mobile"));
                s.setParentMobile(rs.getString("parent_mobile"));
                s.setFeeSlab(rs.getString("fee_slab"));
                return s;
            }
        }
    }

    /** Single student by id, or null. */
    /**
     * Finds a student by the roll number bubbled on an OMR sheet, matched against the digits of
     * the admission number (non-digits stripped). Tries an exact digit match first, then a
     * suffix match (sheet often carries only the trailing digits). Returns null if none / ambiguous.
     */
    public Student findByRoll(String rollDigits) throws SQLException {
        String digits = (rollDigits == null) ? "" : rollDigits.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        String sql = "SELECT student_id, admission_no, full_name, class_name, board, "
                   + "student_mobile, parent_mobile, fee_slab FROM students "
                   + "WHERE REGEXP_REPLACE(admission_no,'[^0-9]','') = ? "
                   + "   OR REGEXP_REPLACE(admission_no,'[^0-9]','') LIKE CONCAT('%', ?) "
                   + "ORDER BY (REGEXP_REPLACE(admission_no,'[^0-9]','') = ?) DESC LIMIT 2";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, digits);
            ps.setString(2, digits);
            ps.setString(3, digits);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Student s = new Student();
                s.setStudentId(rs.getInt("student_id"));
                s.setAdmissionNo(rs.getString("admission_no"));
                s.setFullName(rs.getString("full_name"));
                s.setClassName(rs.getString("class_name"));
                s.setStudentMobile(rs.getString("student_mobile"));
                s.setFeeSlab(rs.getString("fee_slab"));
                if (rs.next()) return null;   // more than one match → ambiguous, don't guess
                return s;
            }
        }
    }

    /**
     * The complete student record, including the fields the profile/edit screen
     * needs (DOB, address, photo, sales dimension). Existing callers that only
     * read a handful of fields are unaffected — this returns a superset.
     */
    public Student findById(int studentId) throws SQLException {
        String sql = "SELECT " + FULL_COLS + " FROM students s "
                   + "LEFT JOIN users u ON u.user_id = s.counsellor_id "
                   + "WHERE s.student_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFull(rs) : null;
            }
        }
    }

    /** Every student column plus the joined counsellor name. */
    private static final String FULL_COLS =
          "s.student_id, s.admission_no, s.full_name, s.dob, s.gender, s.class_name, s.board, "
        + "s.prev_school, s.prev_marks, s.student_mobile, s.alt_mobile, s.student_email, "
        + "s.parent_name, s.parent_mobile, s.address, s.photo_path, s.fee_slab, "
        + "s.plan_code, s.counsellor_id, s.inquiry_id, s.batch_name, s.branch, s.id_proof_path, "
        + "s.doc1_path, s.doc2_path, s.doc3_path, s.doc4_path, "
        + "s.is_active, s.created_at, u.full_name AS counsellor_name";

    private Student mapFull(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getInt("student_id"));
        s.setAdmissionNo(rs.getString("admission_no"));
        s.setFullName(rs.getString("full_name"));
        java.sql.Date dob = rs.getDate("dob");
        s.setDob(dob == null ? null : dob.toString());
        s.setGender(rs.getString("gender"));
        s.setClassName(rs.getString("class_name"));
        s.setBoard(rs.getString("board"));
        s.setPrevSchool(rs.getString("prev_school"));
        s.setPrevMarks(rs.getString("prev_marks"));
        s.setStudentMobile(rs.getString("student_mobile"));
        s.setAltMobile(rs.getString("alt_mobile"));
        s.setStudentEmail(rs.getString("student_email"));
        s.setParentName(rs.getString("parent_name"));
        s.setParentMobile(rs.getString("parent_mobile"));
        s.setAddress(rs.getString("address"));
        s.setPhotoPath(rs.getString("photo_path"));
        s.setFeeSlab(rs.getString("fee_slab"));
        s.setPlanCode(rs.getString("plan_code"));
        int cid = rs.getInt("counsellor_id");
        s.setCounsellorId(rs.wasNull() ? null : cid);
        int iid = rs.getInt("inquiry_id");
        s.setInquiryId(rs.wasNull() ? null : iid);
        s.setBatchName(rs.getString("batch_name"));
        s.setBranch(rs.getString("branch"));
        s.setIdProofPath(rs.getString("id_proof_path"));
        s.setDoc1Path(rs.getString("doc1_path"));
        s.setDoc2Path(rs.getString("doc2_path"));
        s.setDoc3Path(rs.getString("doc3_path"));
        s.setDoc4Path(rs.getString("doc4_path"));
        s.setActive(rs.getInt("is_active") == 1);
        s.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        s.setCounsellorName(rs.getString("counsellor_name"));
        return s;
    }

    /**
     * Updates an existing student. The admission number is deliberately NOT
     * editable — it is printed on receipts and is the student-portal login, so
     * changing it would orphan both.
     */
    public void update(Student s) throws SQLException {
        String sql = "UPDATE students SET "
            + "full_name=?, dob=?, gender=?, class_name=?, board=?, prev_school=?, prev_marks=?, "
            + "student_mobile=?, alt_mobile=?, student_email=?, parent_name=?, parent_mobile=?, "
            + "address=?, fee_slab=?, plan_code=?, batch_name=?, branch=?, counsellor_id=?, is_active=? "
            + "WHERE student_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, s.getFullName());
            setNullableDate(ps, i++, s.getDob());
            setNullable(ps, i++, s.getGender());
            setNullable(ps, i++, s.getClassName());
            setNullable(ps, i++, s.getBoard());
            setNullable(ps, i++, s.getPrevSchool());
            setNullable(ps, i++, s.getPrevMarks());
            setNullable(ps, i++, s.getStudentMobile());
            setNullable(ps, i++, s.getAltMobile());
            setNullable(ps, i++, s.getStudentEmail());
            setNullable(ps, i++, s.getParentName());
            setNullable(ps, i++, s.getParentMobile());
            setNullable(ps, i++, s.getAddress());
            setNullable(ps, i++, s.getFeeSlab());
            setNullable(ps, i++, s.getPlanCode());
            setNullable(ps, i++, s.getBatchName());
            setNullable(ps, i++, s.getBranch());
            if (s.getCounsellorId() == null) {
                ps.setNull(i++, java.sql.Types.INTEGER);
            } else {
                ps.setInt(i++, s.getCounsellorId());
            }
            ps.setInt(i++, s.isActive() ? 1 : 0);
            ps.setInt(i, s.getStudentId());
            ps.executeUpdate();
        }
    }

    /** Replaces the photo path only — the edit form uploads separately from the field save. */
    public void updatePhoto(int studentId, String photoPath) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE students SET photo_path = ? WHERE student_id = ?")) {
            ps.setString(1, photoPath);
            ps.setInt(2, studentId);
            ps.executeUpdate();
        }
    }

    /** Replaces the ID-proof path only. */
    public void updateIdProof(int studentId, String path) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE students SET id_proof_path = ? WHERE student_id = ?")) {
            ps.setString(1, path);
            ps.setInt(2, studentId);
            ps.executeUpdate();
        }
    }

    /** Returns all admissions, newest first, with batch/branch and the closing counsellor. */
    public List<Student> findAll() throws SQLException {
        return findAll(null);
    }

    /**
     * Every student, or only one counsellor's.
     *
     * @param scopeCounsellorId null for management (all students); a user id to
     *        restrict the list to the students that counsellor owns.
     *
     * A counsellor has no business browsing a colleague's admissions — the row
     * carries the family's contact numbers and fee position. Leads were already
     * scoped this way; the student list was not, so it was showing everyone's.
     */
    public List<Student> findAll(Integer scopeCounsellorId) throws SQLException {
        String sql = "SELECT " + FULL_COLS + " FROM students s "
                   + "LEFT JOIN users u ON u.user_id = s.counsellor_id "
                   + (scopeCounsellorId == null ? "" : "WHERE s.counsellor_id = ? ")
                   + "ORDER BY s.student_id DESC";
        List<Student> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (scopeCounsellorId != null) ps.setInt(1, scopeCounsellorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFull(rs));
                }
            }
        }
        return list;
    }

    /**
     * Students already reachable on this mobile — checked against the student,
     * parent and alternate numbers, since a family often gives the same number
     * for all three. Used to stop a second admission being created for someone
     * who is already enrolled.
     */
    public List<Student> findByMobile(String mobile) throws SQLException {
        List<Student> list = new ArrayList<>();
        if (mobile == null || mobile.trim().isEmpty()) {
            return list;
        }
        String sql = "SELECT student_id, admission_no, full_name, class_name, board, "
                   + "student_mobile, parent_mobile, fee_slab FROM students "
                   + "WHERE student_mobile = ? OR parent_mobile = ? OR alt_mobile = ? "
                   + "ORDER BY student_id DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            String m = mobile.trim();
            ps.setString(1, m);
            ps.setString(2, m);
            ps.setString(3, m);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Student s = new Student();
                    s.setStudentId(rs.getInt("student_id"));
                    s.setAdmissionNo(rs.getString("admission_no"));
                    s.setFullName(rs.getString("full_name"));
                    s.setClassName(rs.getString("class_name"));
                    s.setBoard(rs.getString("board"));
                    s.setStudentMobile(rs.getString("student_mobile"));
                    s.setParentMobile(rs.getString("parent_mobile"));
                    s.setFeeSlab(rs.getString("fee_slab"));
                    list.add(s);
                }
            }
        }
        return list;
    }

    /**
     * Inserts a new admission, generating a unique admission number
     * (e.g. HNS-2627-0421). Returns the generated number.
     *
     * The generated student_id is written back onto the passed Student, so the
     * caller can link the source lead to the student it became.
     */
    public String insert(Student s) throws SQLException {
        String sql = "INSERT INTO students "
            + "(admission_no, full_name, dob, gender, class_name, board, prev_school, "
            + " prev_marks, student_mobile, alt_mobile, student_email, parent_name, "
            + " parent_mobile, address, photo_path, fee_slab, "
            + " counsellor_id, inquiry_id, batch_name, branch, id_proof_path, plan_code, "
            + " doc1_path, doc2_path, doc3_path, doc4_path) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection con = DBConnection.getConnection()) {
            // Retry a few times in case of an admission-number collision.
            for (int attempt = 0; attempt < 5; attempt++) {
                String admissionNo = generateAdmissionNo();
                try (PreparedStatement ps = con.prepareStatement(sql,
                         PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1,  admissionNo);
                    ps.setString(2,  s.getFullName());
                    setNullableDate(ps, 3, s.getDob());
                    setNullable(ps, 4,  s.getGender());
                    setNullable(ps, 5,  s.getClassName());
                    setNullable(ps, 6,  s.getBoard());
                    setNullable(ps, 7,  s.getPrevSchool());
                    setNullable(ps, 8,  s.getPrevMarks());
                    setNullable(ps, 9,  s.getStudentMobile());
                    setNullable(ps, 10, s.getAltMobile());
                    setNullable(ps, 11, s.getStudentEmail());
                    setNullable(ps, 12, s.getParentName());
                    setNullable(ps, 13, s.getParentMobile());
                    setNullable(ps, 14, s.getAddress());
                    setNullable(ps, 15, s.getPhotoPath());
                    setNullable(ps, 16, s.getFeeSlab());
                    if (s.getCounsellorId() == null) {
                        ps.setNull(17, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(17, s.getCounsellorId());
                    }
                    if (s.getInquiryId() == null) {
                        ps.setNull(18, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(18, s.getInquiryId());
                    }
                    setNullable(ps, 19, s.getBatchName());
                    setNullable(ps, 20, s.getBranch());
                    setNullable(ps, 21, s.getIdProofPath());
                    setNullable(ps, 22, s.getPlanCode());
                    setNullable(ps, 23, s.getDoc1Path());
                    setNullable(ps, 24, s.getDoc2Path());
                    setNullable(ps, 25, s.getDoc3Path());
                    setNullable(ps, 26, s.getDoc4Path());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            s.setStudentId(keys.getInt(1));
                        }
                    }
                    s.setAdmissionNo(admissionNo);
                    return admissionNo;
                } catch (SQLIntegrityConstraintViolationException dup) {
                    // collision on admission_no -> try a different number
                }
            }
        }
        throw new SQLException("Could not generate a unique admission number.");
    }

    private String generateAdmissionNo() {
        int yy = Year.now().getValue() % 100;          // e.g. 26
        int nextYy = (yy + 1) % 100;                   // e.g. 27
        int seq = ThreadLocalRandom.current().nextInt(1, 10000);
        return String.format("HNS-%02d%02d-%04d", yy, nextYy, seq);
    }

    private static void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, java.sql.Types.VARCHAR);
        } else {
            ps.setString(idx, val.trim());
        }
    }

    /** Blank or unparseable dates become SQL NULL rather than throwing. */
    private static void setNullableDate(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.trim().isEmpty()) {
            ps.setNull(idx, java.sql.Types.DATE);
            return;
        }
        try {
            ps.setDate(idx, java.sql.Date.valueOf(val.trim()));
        } catch (IllegalArgumentException e) {
            ps.setNull(idx, java.sql.Types.DATE);
        }
    }
}
