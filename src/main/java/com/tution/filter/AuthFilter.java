package com.tution.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.AccessDAO;
import com.tution.model.User;

/**
 * Single place where "who may see this page" is decided.
 *
 * Before this filter existed the same five-line session check was copy-pasted
 * into every servlet and again into every JSP. Those inline guards are left in
 * place (they are harmless now) - but new rules go HERE only, so adding the
 * COUNSELLOR role did not mean editing 26 servlets.
 *
 * Three kinds of path:
 *   PUBLIC        - no session at all (login pages, the public enquiry form, static files)
 *   STUDENT       - needs the "student" session attribute (student portal)
 *   everything else - needs the "user" session attribute (staff side)
 *
 * On top of that, every staff path is checked against the role/activity matrix
 * (AccessDAO, seeded from Doc/Mapping/Work Flow.xlsx), and {@link #ADMIN_ONLY}
 * paths require the ADMIN role.
 *
 * The logged-in user's role is published as the request attribute "role" so JSPs
 * can branch on it without re-reading the session.
 */
@WebFilter("/*")
public class AuthFilter implements Filter {

    /** Reachable with no login at all. */
    private static final Set<String> PUBLIC_PATHS = new HashSet<>(Arrays.asList(
        "/login", "/login.jsp",
        "/logout",
        "/student-login", "/student-login.jsp",
        "/student-logout",
        "/inquiry", "/inquiry.jsp"          // the public enquiry form
    ));

    /** Static asset folders - served without a session. */
    private static final String[] PUBLIC_PREFIXES = {
        "/css/", "/js/", "/img/", "/uploads/", "/favicon"
    };

    /**
     * Carved OUT of {@code /uploads/}: study material is not a public asset.
     *
     * The rest of /uploads/ is student photos and ID scans referenced from
     * pages that are already behind a login, but study material was being
     * linked to students as a direct URL - which meant one forwarded link put
     * the whole PDF library on the open internet. It is served through
     * /student-material instead, which checks the session and the class.
     */
    private static final String[] PROTECTED_UPLOAD_PREFIXES = {
        "/uploads/materials/"
    };

    /** Student-portal paths - guarded by the "student" session attribute. */
    private static final String STUDENT_PREFIX = "/student-";

    /**
     * Staff paths that only ADMIN may open — not in the matrix at all, because
     * they are about running the system rather than the institute.
     *
     * Everything else a staff member may open is decided by the role/activity
     * matrix (AccessDAO). The per-role deny lists that used to live here
     * (COUNSELLOR_DENIED, TEACHER_DENIED, ACCOUNTANT_DENIED, HR_ALLOWED,
     * FINANCE_ONLY) are gone; the matrix is their replacement.
     */
    private static final Set<String> ADMIN_ONLY = new HashSet<>(Arrays.asList(
        "/role-mapping", "/server-logs"
    ));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String ctx  = req.getContextPath();
        String path = req.getRequestURI().substring(ctx.length());
        if (path.isEmpty()) {
            path = "/";
        }

        // 1. Anonymous paths pass straight through.
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);

        // 2. Student portal - separate session key from the staff side.
        if (path.startsWith(STUDENT_PREFIX)) {
            if (session == null || session.getAttribute("student") == null) {
                redirect(req, resp, "/student-login.jsp");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // 3. Everything else is staff-only.
        Object userObj = (session == null) ? null : session.getAttribute("user");
        if (userObj == null) {
            redirect(req, resp, "/login.jsp");
            return;
        }

        // The matrix check reads ?tab= / ?type=, and reading ANY parameter fixes
        // the body's encoding. Both filters are @WebFilter("/*") with no defined
        // order, so set UTF-8 here too or a POST to /hr could decode as Latin-1.
        if (req.getCharacterEncoding() == null) {
            req.setCharacterEncoding("UTF-8");
        }

        User user = (userObj instanceof User) ? (User) userObj : null;
        String role = user == null ? null : user.getRole();
        req.setAttribute("role", role == null ? "STAFF" : role);

        if (ADMIN_ONLY.contains(path)) {
            if (user == null || !user.isAdmin()) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                               "This page is restricted to administrators.");
                return;
            }
        } else if (user != null && !AccessDAO.mayOpen(user, path, req)) {
            // A wrong turn (stale bookmark, a link from before a mapping change),
            // not an attack: send the user home rather than show a 403. homePath()
            // only ever returns a page the role may open, so this cannot loop —
            // the guard is for a matrix that fails to load.
            String home = user.homePath();
            if (home.equals(path)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                               "Your role does not have access to this page.");
                return;
            }
            resp.sendRedirect(req.getContextPath() + home);
            return;
        }


        chain.doFilter(request, response);
    }

    /** True for the welcome page, static assets and the anonymous entry points. */
    private boolean isPublic(String path) {
        if ("/".equals(path) || PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PROTECTED_UPLOAD_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;       // checked before the general /uploads/ rule
            }
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends the browser to a login page. An AJAX call gets a plain 401 instead,
     * so the caller sees a failure rather than a login page rendered into a
     * JSON/partial response.
     */
    private void redirect(HttpServletRequest req, HttpServletResponse resp, String target)
            throws IOException {
        if ("XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
        } else {
            resp.sendRedirect(req.getContextPath() + target);
        }
    }
}
