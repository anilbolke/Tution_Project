package com.tution.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.MaterialDAO;
import com.tution.model.Material;
import com.tution.model.Student;

/**
 * Streams one study-material PDF to the student it belongs to, for reading on
 * screen rather than keeping.
 *
 * WHAT THIS DOES AND DOES NOT DO. It stops the file being handed out as a URL
 * anyone can pass on, and it serves the PDF inline so the browser renders it
 * instead of saving it. It CANNOT stop a determined student keeping a copy:
 * once a browser can display a PDF the bytes are already on their machine, and
 * a screenshot needs no bytes at all. Treat this as "not offered", not as
 * "prevented".
 *
 * The real gain is the access check. Before this, material lived at a public
 * /uploads/materials/ path with no session required, so one shared link reached
 * anybody on the internet.
 */
@WebServlet("/student-material")
public class StudentMaterialServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final MaterialDAO materialDAO = new MaterialDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        Student s = (session == null) ? null : (Student) session.getAttribute("student");
        if (s == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in again.");
            return;
        }

        int id = parseInt(req.getParameter("id"), 0);
        Material m;
        try {
            m = materialDAO.findById(id);
        } catch (SQLException e) {
            getServletContext().log("Material lookup failed", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not open that file.");
            return;
        }
        if (m == null || !"PDF".equals(m.getType()) || m.getFilePath() == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such study material.");
            return;
        }

        // Same rule the list uses: material for this student's class, or for
        // everyone. Without it, ?id=N would walk the whole library.
        if (!isForClass(m, s.getClassName())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "That material is not for your class.");
            return;
        }

        File file = resolve(m.getFilePath());
        if (file == null || !file.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "That file is missing.");
            return;
        }

        resp.setContentType("application/pdf");
        resp.setContentLengthLong(file.length());
        // inline = render in the viewer. A browser will still let somebody save
        // it; this only stops the download being the default action.
        resp.setHeader("Content-Disposition",
                       "inline; filename=\"" + asciiName(m.getTitle()) + ".pdf\"");
        // Never let a mislabelled upload be sniffed into something executable.
        resp.setHeader("X-Content-Type-Options", "nosniff");
        // Private to this student's browser, and not left in a shared cache.
        resp.setHeader("Cache-Control", "private, max-age=0, must-revalidate");

        try (InputStream in = Files.newInputStream(file.toPath());
             OutputStream out = resp.getOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** Material with no class set is for everyone. */
    private boolean isForClass(Material m, String studentClass) {
        String c = m.getClassName();
        if (c == null || c.trim().isEmpty()) return true;
        return c.equals(studentClass);
    }

    /**
     * Turns the stored path into a file inside the webapp.
     *
     * PATH TRAVERSAL GUARD. file_path comes from a row an administrator wrote,
     * but a stored "../../conf/server.xml" would otherwise be served happily, so
     * the resolved file must still sit under the uploads folder.
     */
    private File resolve(String stored) {
        String real = getServletContext().getRealPath("/");
        if (real == null) return null;
        try {
            File root = new File(real, "uploads").getCanonicalFile();
            File f    = new File(real, stored).getCanonicalFile();
            return f.getPath().startsWith(root.getPath()) ? f : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** A filename header must be plain ASCII or browsers mangle it. */
    private String asciiName(String title) {
        if (title == null || title.trim().isEmpty()) return "study-material";
        StringBuilder b = new StringBuilder();
        for (char c : title.trim().toCharArray()) {
            if (c >= 32 && c <= 126 && c != '"' && c != '\\' && c != '/') b.append(c);
            else if (Character.isWhitespace(c)) b.append(' ');
        }
        String out = b.toString().trim();
        return out.isEmpty() ? "study-material" : out;
    }

    private static int parseInt(String v, int dflt) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return dflt; }
    }
}
