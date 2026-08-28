package com.tution.servlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import com.tution.dao.MaterialDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.Material;
import com.tution.model.Student;
import com.tution.model.User;

/** Staff management of learning materials: upload PDFs, add YouTube links, list, delete. */
@WebServlet("/materials")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 25 * 1024 * 1024, maxRequestSize = 30 * 1024 * 1024)
public class MaterialServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String UPLOAD_DIR = "uploads/materials";
    private final MaterialDAO materialDAO = new MaterialDAO();
    private final StudentDAO  studentDAO  = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;
        loadView(req);
        req.setAttribute("saved", "1".equals(req.getParameter("saved")));
        req.getRequestDispatcher("/WEB-INF/views/materials_admin.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        String action = req.getParameter("action");
        try {
            if ("delete".equals(action)) {
                Integer id = parseInt(req.getParameter("materialId"));
                if (id != null) materialDAO.delete(id);
                resp.sendRedirect(req.getContextPath() + "/materials?saved=1");
                return;
            }

            String title = trim(req.getParameter("title"));
            String cls   = trim(req.getParameter("className"));
            String subj  = trim(req.getParameter("subject"));
            String type  = "VIDEO".equals(req.getParameter("type")) ? "VIDEO" : "PDF";

            String error = null;
            Material m = new Material();
            m.setTitle(title);
            m.setClassName(cls);
            m.setSubject(subj);
            m.setType(type);

            if (title.isEmpty()) {
                error = "Please enter a title.";
            } else if ("VIDEO".equals(type)) {
                String url = trim(req.getParameter("youtubeUrl"));
                m.setYoutubeUrl(url);
                if (m.youtubeId().isEmpty()) error = "Please enter a valid YouTube link.";
            } else {
                String path = savePdf(req);
                if (path == null) error = "Please choose a PDF file to upload.";
                m.setFilePath(path);
            }

            if (error != null) {
                req.setAttribute("error", error);
                loadView(req);
                req.getRequestDispatcher("/WEB-INF/views/materials_admin.jsp").forward(req, resp);
                return;
            }

            User user = (User) req.getSession().getAttribute("user");
            m.setUploadedBy(user == null ? "" : user.getFullName());
            materialDAO.insert(m);
            resp.sendRedirect(req.getContextPath() + "/materials?saved=1");

        } catch (SQLException e) {
            getServletContext().log("Material save failed", e);
            req.setAttribute("error", "Could not save. Please try again.");
            loadView(req);
            req.getRequestDispatcher("/WEB-INF/views/materials_admin.jsp").forward(req, resp);
        }
    }

    private void loadView(HttpServletRequest req) {
        try {
            req.setAttribute("materials", materialDAO.findAll());
            List<Student> all = studentDAO.findAll();
            TreeSet<String> classes = new TreeSet<>();
            for (Student s : all) if (s.getClassName() != null) classes.add(s.getClassName());
            req.setAttribute("classes", classes);
        } catch (SQLException e) {
            getServletContext().log("Load materials failed", e);
        }
    }

    private String savePdf(HttpServletRequest req) throws IOException, ServletException {
        Part part = req.getPart("pdf");
        if (part == null || part.getSize() == 0) return null;
        String submitted = part.getSubmittedFileName();
        String ext = (submitted != null && submitted.contains(".")) ? submitted.substring(submitted.lastIndexOf('.')) : ".pdf";
        String fileName = "mat_" + System.currentTimeMillis() + ext.toLowerCase();
        String base = getServletContext().getRealPath("/") + UPLOAD_DIR;
        File dir = new File(base);
        if (!dir.exists()) dir.mkdirs();
        part.write(Paths.get(base, fileName).toString());
        return UPLOAD_DIR + "/" + fileName;
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
