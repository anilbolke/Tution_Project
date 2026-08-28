package com.tution.servlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.tution.dao.InquiryDAO;
import com.tution.dao.MasterDAO;
import com.tution.dao.PaymentDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.Inquiry;
import com.tution.model.Payment;
import com.tution.model.Student;
import com.tution.model.User;

/**
 * Student profile and edit screen.
 *
 * Before this existed there was no way to correct a student record at all —
 * StudentDAO had insert but no update, and the students list rows were not even
 * clickable. A mistyped mobile number was permanent.
 *
 * GET  /student?id=N          profile
 * GET  /student?id=N&edit=1   edit form
 * POST /student  action=update|deactivate|activate
 */
@WebServlet("/student")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize       = 4 * 1024 * 1024,
    maxRequestSize    = 10 * 1024 * 1024
)
public class StudentEditServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String UPLOAD_DIR = "uploads";

    private final StudentDAO studentDAO = new StudentDAO();
    private final MasterDAO  masterDAO  = new MasterDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final InquiryDAO inquiryDAO = new InquiryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        int id = intParam(req, "id", 0);
        if (id <= 0) {
            resp.sendRedirect(req.getContextPath() + "/students");
            return;
        }

        try {
            Student s = studentDAO.findById(id);
            if (s == null) {
                resp.sendRedirect(req.getContextPath() + "/students");
                return;
            }
            req.setAttribute("student", s);
            loadDropdowns(req);

            if (req.getParameter("edit") != null) {
                req.getRequestDispatcher("/WEB-INF/views/student_form.jsp").forward(req, resp);
                return;
            }

            // Profile view also shows payment history and the source lead.
            List<Payment> payments = paymentDAO.findByStudent(id);
            req.setAttribute("payments", payments);
            req.setAttribute("paidTotal", paymentDAO.getTotalPaid(id));
            if (s.getInquiryId() != null) {
                Inquiry lead = inquiryDAO.findById(s.getInquiryId());
                req.setAttribute("sourceLead", lead);
            }
            req.getRequestDispatcher("/WEB-INF/views/student_view.jsp").forward(req, resp);

        } catch (SQLException e) {
            getServletContext().log("Student load failed", e);
            req.setAttribute("error", "Could not load the student. Please try again.");
            req.getRequestDispatcher("/WEB-INF/views/student_view.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = currentUser(req);
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        int id = intParam(req, "studentId", 0);
        String action = p(req, "action");
        String ctx = req.getContextPath();

        if (id <= 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing student.");
            return;
        }

        try {
            Student existing = studentDAO.findById(id);
            if (existing == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Student not found.");
                return;
            }

            // Deactivating a student is an ADMIN decision — it hides them from
            // fee and attendance work lists.
            if ("deactivate".equals(action) || "activate".equals(action)) {
                if (!user.isAdmin()) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                                   "Only an administrator can change a student's active status.");
                    return;
                }
                existing.setActive("activate".equals(action));
                studentDAO.update(existing);
                resp.sendRedirect(ctx + "/student?id=" + id + "&msg="
                                  + ("activate".equals(action) ? "activated" : "deactivated"));
                return;
            }

            Student s = bind(req, existing, user);
            String error = validate(s);
            if (error != null) {
                req.setAttribute("error", error);
                req.setAttribute("student", s);
                loadDropdowns(req);
                req.getRequestDispatcher("/WEB-INF/views/student_form.jsp").forward(req, resp);
                return;
            }

            studentDAO.update(s);

            // Replace the photo / ID proof only when a new file was attached,
            // so saving the form without re-picking a file keeps the old one.
            String photo = saveUpload(req, "photo", "stu");
            if (photo != null) {
                studentDAO.updatePhoto(id, photo);
            }
            String idProof = saveUpload(req, "idProof", "id");
            if (idProof != null) {
                studentDAO.updateIdProof(id, idProof);
            }

            resp.sendRedirect(ctx + "/student?id=" + id + "&msg=saved");

        } catch (SQLException e) {
            getServletContext().log("Student save failed", e);
            resp.sendRedirect(ctx + "/student?id=" + id + "&msg=error");
        }
    }

    /**
     * Binds the form onto the existing record — admission number is never editable.
     *
     * Fields use {@link #keep}: a parameter that is ABSENT from the request keeps
     * its stored value, while one that is present but empty is treated as a
     * deliberate clear. Without that distinction a save would silently wipe
     * anything the form did not submit — which is exactly what a disabled
     * &lt;select&gt; does.
     */
    private Student bind(HttpServletRequest req, Student existing, User user) {
        Student s = new Student();
        s.setStudentId(existing.getStudentId());
        s.setAdmissionNo(existing.getAdmissionNo());
        s.setPhotoPath(existing.getPhotoPath());
        s.setIdProofPath(existing.getIdProofPath());
        s.setInquiryId(existing.getInquiryId());
        s.setActive(existing.isActive());

        s.setFullName(keep(req, "fullName", existing.getFullName()));
        s.setDob(keep(req, "dob", existing.getDob()));
        s.setGender(keep(req, "gender", existing.getGender()));
        s.setClassName(keep(req, "className", existing.getClassName()));
        s.setBoard(keep(req, "board", existing.getBoard()));
        s.setPrevSchool(keep(req, "prevSchool", existing.getPrevSchool()));
        s.setPrevMarks(keep(req, "prevMarks", existing.getPrevMarks()));
        s.setStudentMobile(keep(req, "studentMobile", existing.getStudentMobile()));
        s.setAltMobile(keep(req, "altMobile", existing.getAltMobile()));
        s.setStudentEmail(keep(req, "studentEmail", existing.getStudentEmail()));
        s.setParentName(keep(req, "parentName", existing.getParentName()));
        s.setParentMobile(keep(req, "parentMobile", existing.getParentMobile()));
        s.setAddress(keep(req, "address", existing.getAddress()));
        s.setFeeSlab(keep(req, "feeSlab", existing.getFeeSlab()));
        s.setBatchName(keep(req, "batchName", existing.getBatchName()));
        s.setBranch(keep(req, "branch", existing.getBranch()));

        // Only an ADMIN may reassign; for everyone else the field is disabled on
        // the form, so it never arrives and the current owner must be preserved.
        if (user != null && user.isAdmin() && req.getParameter("counsellorId") != null) {
            String cid = p(req, "counsellorId");
            s.setCounsellorId((cid == null || cid.isEmpty()) ? null : Integer.valueOf(cid));
        } else {
            s.setCounsellorId(existing.getCounsellorId());
        }
        return s;
    }

    /** Absent parameter → keep the stored value; present-but-empty → clear it. */
    private static String keep(HttpServletRequest req, String name, String current) {
        String v = req.getParameter(name);
        return (v == null) ? current : v.trim();
    }

    private String validate(Student s) {
        if (isBlank(s.getFullName())) {
            return "Please enter the student name.";
        }
        if (!isMobile(s.getStudentMobile())) {
            return "Please enter a valid 10-digit student mobile number.";
        }
        if (!isBlank(s.getAltMobile()) && !isMobile(s.getAltMobile())) {
            return "Please enter a valid 10-digit alternate mobile number.";
        }
        if (!isBlank(s.getParentMobile()) && !isMobile(s.getParentMobile())) {
            return "Please enter a valid 10-digit parent mobile number.";
        }
        return null;
    }

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

    private void loadDropdowns(HttpServletRequest req) {
        try {
            req.setAttribute("courses",     masterDAO.courses());
            req.setAttribute("batches",     masterDAO.batches());
            req.setAttribute("counsellors", masterDAO.counsellors());
            req.setAttribute("slabs",       new com.tution.dao.FeeSlabDAO().findAllAsMap());
        } catch (SQLException e) {
            getServletContext().log("Master lists failed to load", e);
        }
    }

    private static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s == null) ? null : (User) s.getAttribute("user");
    }

    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }

    private static int intParam(HttpServletRequest req, String name, int fallback) {
        String v = req.getParameter(name);
        return (v != null && v.trim().matches("\\d+")) ? Integer.parseInt(v.trim()) : fallback;
    }

    private static boolean isBlank(String s)  { return s == null || s.isEmpty(); }
    private static boolean isMobile(String s) { return s != null && s.matches("[6-9]\\d{9}"); }
}
