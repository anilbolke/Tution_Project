package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.UserDAO;
import com.tution.model.Role;
import com.tution.model.User;

/**
 * The staff directory, and creating and managing the logins behind it.
 *
 * WHO MAY DO WHAT, AND WHY IT IS NOT SIMPLY "HR MANAGES PEOPLE".
 *
 * Whoever can create an account and choose its role can grant themselves
 * anything. So HR — who does the onboarding, and should not have to ask an
 * administrator to add a new teacher — may only ever create or touch an account
 * in one of the {@link #HR_ASSIGNABLE} roles. HR cannot create an ADMIN, cannot
 * promote anyone into a privileged role, and cannot reset the password of a
 * privileged account, because any of the three is a way to become an
 * administrator. ADMIN has no such limit.
 *
 * Two further rules protect against locking everybody out:
 *   - nobody can switch off or demote their own account
 *   - the last active ADMIN cannot be switched off or demoted
 *
 * NOTHING IS EVER DELETED. Four tables reference users ON DELETE CASCADE, so a
 * delete would silently take somebody's attendance, leave and paid payslips with
 * it. Accounts are switched off instead.
 *
 *   GET  /staff                        the directory
 *   POST /staff  action=create|update|role|password|enable|disable
 */
@WebServlet("/staff")
public class StaffDirectoryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Every role a login can hold. Order is the order shown on screen. */
    static final String[] ALL_ROLES = Role.codes();

    /**
     * The roles HR may create, edit, reset or move somebody between.
     *
     * Deliberately excludes ADMIN, ACCOUNTANT, HR, OFFICE_ADMIN and the three
     * heads/managers: those reach the institute's money, its people or its
     * permissions, and handing out that access is the administrator's decision,
     * not an onboarding step.
     */
    private static final Set<String> HR_ASSIGNABLE =
        new HashSet<>(Arrays.asList("STAFF", "COUNSELLOR", "TEACHER",
                                    "ABM", "ACADEMIC_INCHARGE", "ACADEMIC_COORDINATOR", "EDP"));

    private static final int MIN_PASSWORD = 6;

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requirePeople(req, resp);
        if (user == null) return;

        try {
            req.setAttribute("staff", userDAO.allStaff());
            req.setAttribute("trail", userDAO.auditTrail(30));
            req.setAttribute("adminCount", Integer.valueOf(userDAO.activeAdminCount()));
        } catch (SQLException e) {
            getServletContext().log("Staff directory failed", e);
            req.setAttribute("error", "Could not load the staff list. Please try again.");
        }
        req.setAttribute("allRoles", ALL_ROLES);
        req.setAttribute("assignable", assignableBy(user));
        req.getRequestDispatcher("/WEB-INF/views/staff_directory.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requirePeople(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");

        try {
            switch (action) {
                case "create":   doCreate(req, user);              break;
                case "update":   doUpdate(req, user);              break;
                case "role":     doRole(req, user);                break;
                case "manager":  doManager(req, user);             break;
                case "password": doPassword(req, user);            break;
                case "enable":   doActive(req, user, true);        break;
                case "disable":  doActive(req, user, false);       break;
                default:         flashError(req, "Unknown action.");
            }
        } catch (SQLException e) {
            getServletContext().log("Staff action failed", e);
            String msg = e.getMessage() != null && e.getMessage().contains("username")
                       ? "That username is already taken."
                       : "Could not save: " + e.getMessage();
            flashError(req, msg);
        }

        resp.sendRedirect(req.getContextPath() + "/staff");
    }

    /* ─────────────────────────── actions ─────────────────────────── */

    private void doCreate(HttpServletRequest req, User by) throws SQLException {
        String username = lower(req.getParameter("username"));
        String fullName = trim(req.getParameter("fullName"));
        String role     = upper(req.getParameter("role"));
        String pw       = req.getParameter("password");

        if (username == null || fullName == null || role == null) {
            flashError(req, "A login needs a username, a name and a role.");
            return;
        }
        if (!username.matches("[a-z0-9._-]{3,50}")) {
            flashError(req, "The username must be 3 to 50 characters, using only letters, "
                          + "numbers, dot, underscore or hyphen.");
            return;
        }
        if (!isRole(role)) {
            flashError(req, "That is not a role.");
            return;
        }
        if (!assignableBy(by).contains(role)) {
            flashError(req, "You cannot create " + a(role) + " account. Ask an administrator.");
            return;
        }
        if (pw == null || pw.length() < MIN_PASSWORD) {
            flashError(req, "Set a starting password of at least " + MIN_PASSWORD + " characters.");
            return;
        }
        if (userDAO.usernameTaken(username)) {
            flashError(req, "The username \"" + username + "\" is already taken.");
            return;
        }

        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(req.getParameter("email"));
        u.setMobile(req.getParameter("mobile"));
        u.setRole(role);
        u.setActive(true);

        // Optional reporting line, checked BEFORE the account exists so a bad
        // pick does not leave a half-made login behind.
        User manager = null;
        int mgrId = parseInt(req.getParameter("reportsTo"), 0);
        if (mgrId > 0) {
            manager = userDAO.findById(mgrId);
            if (manager == null || !manager.isActive()) {
                flashError(req, "Pick an active person for \"Reports to\", or leave it blank.");
                return;
            }
        }

        int id = userDAO.createUser(u, pw, by);
        if (id > 0 && manager != null) {
            User created = userDAO.findById(id);
            if (created != null) userDAO.setReportsTo(created, manager, by);
        }
        if (id > 0) {
            flash(req, "Login created for " + fullName + " (" + username + ") as "
                     + Role.labelOf(role)
                     + (manager == null ? "" : ", reporting to " + manager.getFullName())
                     + ". Tell them the starting password and ask them to change it.");
        } else {
            flashError(req, "Could not create that login.");
        }
    }

    private void doUpdate(HttpServletRequest req, User by) throws SQLException {
        User target = target(req, by);
        if (target == null) return;

        String fullName = trim(req.getParameter("fullName"));
        if (fullName == null) {
            flashError(req, "A name cannot be blank.");
            return;
        }
        target.setFullName(fullName);
        target.setEmail(req.getParameter("email"));
        target.setMobile(req.getParameter("mobile"));
        if (userDAO.updateDetails(target, by)) {
            flash(req, "Details updated for " + fullName + ".");
        } else {
            flashError(req, "That account no longer exists.");
        }
    }

    private void doRole(HttpServletRequest req, User by) throws SQLException {
        User target = target(req, by);
        if (target == null) return;

        String role = upper(req.getParameter("role"));
        if (!isRole(role)) {
            flashError(req, "That is not a role.");
            return;
        }
        if (role.equals(target.getRole())) {
            flashError(req, target.getFullName() + " is already " + Role.labelOf(role) + ".");
            return;
        }
        // The NEW role must also be one this person may hand out — otherwise HR
        // could move a teacher into ADMIN, which is the escalation this blocks.
        if (!assignableBy(by).contains(role)) {
            flashError(req, "You cannot move anybody into " + Role.labelOf(role) + ". Ask an administrator.");
            return;
        }
        if (target.getUserId() == by.getUserId()) {
            flashError(req, "You cannot change your own role.");
            return;
        }
        if ("ADMIN".equals(target.getRole()) && userDAO.activeAdminCount() <= 1) {
            flashError(req, "This is the last active administrator. Make somebody else an "
                          + "administrator first, or there would be nobody left who can.");
            return;
        }
        if (userDAO.setRole(target, role, by)) {
            flash(req, target.getFullName() + " is now " + Role.labelOf(role) + ".");
        } else {
            flashError(req, "That account no longer exists.");
        }
    }

    /**
     * Sets who somebody reports to. This is what scopes an ABM: every account
     * reporting to them is "their team" (dao/Scope). One level, no loops.
     */
    private void doManager(HttpServletRequest req, User by) throws SQLException {
        User target = target(req, by);
        if (target == null) return;

        int mgrId = parseInt(req.getParameter("reportsTo"), 0);
        User manager = null;
        if (mgrId > 0) {
            manager = userDAO.findById(mgrId);
            if (manager == null || !manager.isActive()) {
                flashError(req, "That person is not an active login.");
                return;
            }
            if (manager.getUserId() == target.getUserId()) {
                flashError(req, "Nobody can report to themselves.");
                return;
            }
            if (manager.getReportsTo() != null && manager.getReportsTo().intValue() == target.getUserId()) {
                flashError(req, manager.getFullName() + " already reports to " + target.getFullName()
                              + ". Change that first - two people cannot report to each other.");
                return;
            }
        }
        Integer now = target.getReportsTo();
        if ((now == null && manager == null) || (now != null && manager != null && now.intValue() == mgrId)) {
            flashError(req, "Nothing changed.");
            return;
        }
        if (userDAO.setReportsTo(target, manager, by)) {
            flash(req, target.getFullName() + (manager == null ? " now reports to nobody."
                                                               : " now reports to " + manager.getFullName() + "."));
        } else {
            flashError(req, "That account no longer exists.");
        }
    }

    private void doPassword(HttpServletRequest req, User by) throws SQLException {
        User target = target(req, by);
        if (target == null) return;

        String pw = req.getParameter("password");
        if (pw == null || pw.length() < MIN_PASSWORD) {
            flashError(req, "The new password must be at least " + MIN_PASSWORD + " characters.");
            return;
        }
        if (userDAO.resetPassword(target, pw, by)) {
            flash(req, "Password reset for " + target.getFullName()
                     + ". Tell them the new one and ask them to change it.");
        } else {
            flashError(req, "That account no longer exists.");
        }
    }

    private void doActive(HttpServletRequest req, User by, boolean active) throws SQLException {
        User target = target(req, by);
        if (target == null) return;

        if (!active && target.getUserId() == by.getUserId()) {
            flashError(req, "You cannot switch off your own account — you would be locked out.");
            return;
        }
        if (!active && "ADMIN".equals(target.getRole()) && userDAO.activeAdminCount() <= 1) {
            flashError(req, "This is the last active administrator and cannot be switched off.");
            return;
        }
        if (target.isActive() == active) {
            flashError(req, target.getFullName() + " is already "
                          + (active ? "switched on." : "switched off."));
            return;
        }
        if (userDAO.setActive(target, active, by)) {
            flash(req, target.getFullName() + " is now "
                     + (active ? "switched on and can sign in."
                               : "switched off and cannot sign in. Their records are kept."));
        } else {
            flashError(req, "That account no longer exists.");
        }
    }

    /* ─────────────────────────── helpers ─────────────────────────── */

    /**
     * Loads the account being acted on, having checked the caller is allowed to
     * touch it at all. Flashes the reason and returns null when not.
     */
    private User target(HttpServletRequest req, User by) throws SQLException {
        int id = parseInt(req.getParameter("userId"), 0);
        User t = id <= 0 ? null : userDAO.findById(id);
        if (t == null) {
            flashError(req, "That account no longer exists.");
            return null;
        }
        if (!assignableBy(by).contains(t.getRole())) {
            flashError(req, cap(a(t.getRole())) + " account can only be changed by an "
                          + "administrator.");
            return null;
        }
        return t;
    }

    /** The set of roles this user may create, edit or move somebody into. */
    private Set<String> assignableBy(User by) {
        if (by != null && by.isAdmin()) {
            return new HashSet<>(Arrays.asList(ALL_ROLES));
        }
        return HR_ASSIGNABLE;
    }

    /** "an ADMIN" / "a TEACHER" — the messages read as English either way. */
    private static String a(String role) {
        if (role == null || role.isEmpty()) return "a role";
        role = Role.labelOf(role);
        boolean vowel = "AEIOU".indexOf(Character.toUpperCase(role.charAt(0))) >= 0;
        return (vowel ? "an " : "a ") + role;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isRole(String r) {
        if (r == null) return false;
        for (String x : ALL_ROLES) {
            if (x.equals(r)) return true;
        }
        return false;
    }

    private User requirePeople(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession s = req.getSession(false);
        User user = (s == null) ? null : (User) s.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        // Repeated here rather than trusted to AuthFilter: a servlet should not
        // depend on a filter's allow-list to stay correct.
        if (!user.canSeePeople()) {
            resp.sendRedirect(req.getContextPath() + user.homePath());
            return null;
        }
        return user;
    }

    private void flash(HttpServletRequest req, String m) {
        req.getSession().setAttribute("flash", m);
    }

    private void flashError(HttpServletRequest req, String m) {
        req.getSession().setAttribute("flashError", m);
    }

    private static String orDefault(String v, String d) {
        return (v == null || v.trim().isEmpty()) ? d : v.trim();
    }

    private static String trim(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private static String lower(String v) {
        String t = trim(v);
        return t == null ? null : t.toLowerCase();
    }

    private static String upper(String v) {
        String t = trim(v);
        return t == null ? null : t.toUpperCase();
    }

    private static int parseInt(String v, int d) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return d; }
    }
}
