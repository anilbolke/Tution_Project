package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.AccessDAO;
import com.tution.model.Role;
import com.tution.model.User;

/**
 * The role x activity matrix on screen — Doc/Mapping/Work Flow.xlsx as a grid
 * of checkboxes: activities down, roles across. ADMIN only (AuthFilter).
 *
 *   GET  /role-mapping    the grid + the last saves
 *   POST /role-mapping    saves the whole grid; one checkbox per granted cell,
 *                         named by role, valued by activity code
 *
 * ADMIN is not a column: it holds every activity by definition, so it cannot
 * lock itself out of this screen.
 */
@WebServlet("/role-mapping")
public class RoleMappingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** The columns, in Role order, without ADMIN. */
    static String[] editableRoles() {
        List<String> out = new ArrayList<>();
        for (String r : Role.codes()) if (!"ADMIN".equals(r)) out.add(r);
        return out.toArray(new String[0]);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireAdmin(req, resp);
        if (user == null) return;

        String[] roles = editableRoles();
        Map<String, Set<String>> grants = new LinkedHashMap<>();
        for (String r : roles) grants.put(r, AccessDAO.grantsOf(r));

        req.setAttribute("roles", roles);
        req.setAttribute("activities", AccessDAO.activities());
        req.setAttribute("grants", grants);
        try {
            req.setAttribute("log", AccessDAO.recentLog(8));
        } catch (SQLException e) {
            getServletContext().log("role-mapping log", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/role_mapping.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireAdmin(req, resp);
        if (user == null) return;

        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        for (String r : editableRoles()) {
            String[] v = req.getParameterValues(r);
            matrix.put(r, v == null ? new HashSet<String>() : new HashSet<>(Arrays.asList(v)));
        }
        HttpSession s = req.getSession();
        try {
            int n = AccessDAO.save(matrix, user);
            s.setAttribute("flash", n == 0 ? "Nothing changed."
                    : n + " box" + (n == 1 ? "" : "es") + " changed. It applies to everyone from their next click.");
        } catch (SQLException e) {
            getServletContext().log("role-mapping save", e);
            s.setAttribute("flashError", "Could not save the mapping: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + "/role-mapping");
    }

    /** Repeated here rather than trusted to AuthFilter alone. */
    private User requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        if (!user.isAdmin()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "This page is restricted to administrators.");
            return null;
        }
        return user;
    }
}
