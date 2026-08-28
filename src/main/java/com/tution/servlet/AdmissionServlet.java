package com.tution.servlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.tution.dao.FeePlanDAO;
import com.tution.dao.InquiryDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.FeePlan;
import com.tution.model.Inquiry;
import com.tution.model.Student;
import com.tution.model.User;
import com.tution.service.FeeService;

/**
 * Handles the admission form POST. Requires an authenticated session.
 * Accepts a multipart form (so the student photo can be uploaded).
 */
@WebServlet("/admission")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,        // 1 MB
    maxFileSize       = 4 * 1024 * 1024,    // 4 MB per file (ID-proof scans run larger than photos)
    maxRequestSize    = 30 * 1024 * 1024    // 30 MB total — photo + ID proof + 4 extra documents
)
public class AdmissionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String UPLOAD_DIR = "uploads";
    private final StudentDAO     studentDAO     = new StudentDAO();
    private final InquiryDAO     inquiryDAO     = new InquiryDAO();
    private final FeePlanDAO     feePlanDAO     = new FeePlanDAO();
    private final FeeService     feeService     = new FeeService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendRedirect(req.getContextPath() + "/admission.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Must be logged in
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        User staff = (User) session.getAttribute("user");
        Student s = bind(req);

        // Class and batch come from the chosen fee plan, so what the student is
        // recorded as studying can never drift from what they were billed for.
        try {
            FeePlan plan = feePlanDAO.findByCode(s.getPlanCode());
            if (plan != null) {
                s.setClassName(plan.getProgramme());
                if (plan.getBatchType() != null && !plan.getBatchType().isEmpty()) {
                    s.setBatchName(plan.getBatchType());
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Could not resolve fee plan " + s.getPlanCode(), e);
        }

        // Server-side validation (mirrors the client-side checks)
        String error = validate(s, req);
        if (error != null) {
            forwardError(req, resp, error);
            return;
        }

        try {
            // Duplicate-admission guard. A family often re-applies for a student
            // who is already enrolled; block it unless the operator confirms.
            if (!"1".equals(p(req, "confirmDuplicate"))) {
                String clash = duplicateWarning(s);
                if (clash != null) {
                    req.setAttribute("duplicateWarning", clash);
                    forwardError(req, resp, clash);
                    return;
                }
            }

            s.setPhotoPath(saveUpload(req, "photo", "stu"));
            s.setIdProofPath(saveUpload(req, "idProof", "id"));
            s.setDoc1Path(saveUpload(req, "doc1", "doc1"));
            s.setDoc2Path(saveUpload(req, "doc2", "doc2"));
            s.setDoc3Path(saveUpload(req, "doc3", "doc3"));
            s.setDoc4Path(saveUpload(req, "doc4", "doc4"));

            // Sales dimension: who closed it, and which lead it came from.
            String inquiryId = p(req, "inquiryId");
            Inquiry sourceLead = null;
            if (inquiryId != null && inquiryId.matches("\\d+")) {
                sourceLead = inquiryDAO.findById(Integer.parseInt(inquiryId));
                if (sourceLead != null) {
                    s.setInquiryId(Integer.valueOf(sourceLead.getInquiryId()));
                    // Credit the lead's own counsellor; fall back to whoever is
                    // filling in the form, so the admission is never unattributed.
                    s.setCounsellorId(sourceLead.getCounsellorId() != null
                        ? sourceLead.getCounsellorId()
                        : (staff == null ? null : Integer.valueOf(staff.getUserId())));
                }
            }
            if (s.getCounsellorId() == null && staff != null) {
                s.setCounsellorId(Integer.valueOf(staff.getUserId()));
            }

            String admissionNo = studentDAO.insert(s);

            // Open the fee ledger from the brochure plan the student was sold and
            // lay down the payment schedule. Without this the student would have
            // no ledger row and no due dates to remind against.
            try {
                String startDate = p(req, "batchStartDate");
                feeService.applyPlan(s.getStudentId(), s.getPlanCode(), startDate);
            } catch (SQLException e) {
                // The admission itself is saved; a ledger problem must not lose it.
                // But it must not go unnoticed either - silently leaving a student
                // with no fee ledger means no instalment schedule and no due-date
                // reminders, with nothing on screen to prompt anyone to fix it.
                getServletContext().log("Could not open the fee ledger for student "
                                        + s.getStudentId(), e);
                req.setAttribute("ledgerWarning", true);
            }

            // Close the loop on the source lead: CONVERTED plus a hard link to
            // the student record, which is what the conversion report joins on.
            if (sourceLead != null) {
                try {
                    inquiryDAO.markConverted(sourceLead.getInquiryId(), s.getStudentId());
                } catch (SQLException e) {
                    getServletContext().log("Could not mark inquiry " + inquiryId + " converted", e);
                }
            }

            req.setAttribute("admissionNo", admissionNo);
            req.setAttribute("savedName", s.getFullName());
            req.setAttribute("savedMobile", s.getStudentMobile());
            req.setAttribute("savedClass", s.getClassName());
            req.setAttribute("savedSlab", s.getFeeSlab());
            req.getRequestDispatcher("/admission.jsp").forward(req, resp);

        } catch (SQLException e) {
            getServletContext().log("Admission save failed", e);
            forwardError(req, resp, "Could not save the admission. Please try again.");
        }
    }

    private Student bind(HttpServletRequest req) {
        Student s = new Student();
        s.setFullName(p(req, "fullName"));
        s.setDob(p(req, "dob"));
        s.setGender(p(req, "gender"));
        // Overwritten by the chosen fee plan's programme; the free-text field is
        // the school class the student is coming FROM.
        s.setClassName(p(req, "currentClass"));
        s.setBoard(p(req, "admBoard"));
        s.setPrevSchool(p(req, "school"));
        s.setPrevMarks(p(req, "marks"));
        s.setStudentMobile(p(req, "stuMobile"));
        s.setAltMobile(p(req, "altMobile"));
        s.setStudentEmail(p(req, "stuEmail"));
        s.setParentName(p(req, "parentName"));
        s.setParentMobile(p(req, "parentMobile"));
        s.setAddress(p(req, "address"));
        s.setPlanCode(p(req, "planCode"));
        s.setBatchName(p(req, "batchName"));
        s.setBranch(p(req, "branch"));
        return s;
    }

    private String validate(Student s, HttpServletRequest req) {
        if (isBlank(s.getFullName()))                 return "Please enter the student full name.";
        if (isBlank(s.getDob()))                      return "Please enter date of birth.";
        if (isBlank(s.getGender()))                   return "Please select gender.";
        if (isBlank(s.getBoard()))                    return "Please select the board.";
        if (!isMobile(s.getStudentMobile()))          return "Please enter a valid student mobile number.";
        if (!isMobile(s.getAltMobile()))              return "Please enter a valid alternate mobile number.";
        if (isBlank(s.getParentName()))               return "Please enter parent/guardian name.";
        if (!isMobile(s.getParentMobile()))           return "Please enter a valid parent mobile number.";
        if (isBlank(s.getPlanCode()))                 return "Please select a course and fee plan.";
        if (!"on".equals(p(req, "declare")))          return "Please read and accept the declaration.";
        return null;
    }

    /**
     * Returns a human-readable warning when an existing student already uses the
     * student or parent mobile being admitted, else null. Names the clashing
     * student so the operator can tell a genuine sibling from a re-entry.
     */
    private String duplicateWarning(Student s) throws SQLException {
        List<Student> clashes = new ArrayList<>();
        clashes.addAll(studentDAO.findByMobile(s.getStudentMobile()));
        for (Student p : studentDAO.findByMobile(s.getParentMobile())) {
            boolean seen = false;
            for (Student c : clashes) {
                if (c.getStudentId() == p.getStudentId()) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                clashes.add(p);
            }
        }
        if (clashes.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("A student with this mobile number already exists: ");
        for (int i = 0; i < clashes.size(); i++) {
            Student c = clashes.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(c.getFullName()).append(" (").append(c.getAdmissionNo()).append(')');
        }
        sb.append(". Tick “Admit anyway” below if this is a different student, e.g. a sibling.");
        return sb.toString();
    }

    /**
     * Saves an optional uploaded file (student photo, ID proof) and returns a
     * web-relative path, or null when nothing was attached.
     *
     * @param field  the multipart field name
     * @param prefix filename prefix, so uploads stay tellable apart on disk
     */
    private String saveUpload(HttpServletRequest req, String field, String prefix)
            throws IOException, ServletException {
        Part part = req.getPart(field);
        if (part == null || part.getSize() == 0) {
            return null;
        }
        String submitted = part.getSubmittedFileName();
        String ext = "";
        if (submitted != null && submitted.contains(".")) {
            ext = submitted.substring(submitted.lastIndexOf('.')).toLowerCase();
        }
        String fileName = prefix + "_" + System.currentTimeMillis() + ext;

        String uploadBase = getServletContext().getRealPath("/") + UPLOAD_DIR;
        File dir = new File(uploadBase);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        part.write(Paths.get(uploadBase, fileName).toString());
        return UPLOAD_DIR + "/" + fileName;
    }

    private void forwardError(HttpServletRequest req, HttpServletResponse resp, String msg)
            throws ServletException, IOException {
        req.setAttribute("error", msg);
        req.getRequestDispatcher("/admission.jsp").forward(req, resp);
    }

    // ── helpers ──
    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }
    private static boolean isBlank(String s) { return s == null || s.isEmpty(); }
    private static boolean isMobile(String s) { return s != null && s.matches("[6-9]\\d{9}"); }
}
