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
 * On top of that, {@link #ADMIN_ONLY} paths additionally require an ADMIN role.
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
     * Staff paths that only an ADMIN may open.
     *
     * The finance screens are here because they are institute money rather than
     * anything to do with running a class or a pipeline: the fund balance says
     * what is available to spend, and vendor work orders are payables. Exam fee
     * collection (/exam-fees) is deliberately NOT in this list — taking a fee at
     * the counter is front-desk work.
     */
    private static final Set<String> ADMIN_ONLY = new HashSet<>(Arrays.asList(
        "/users"                            // user management (built in a later phase)
    ));

    /**
     * The institute's own money. ADMIN or ACCOUNTANT.
     *
     * These were ADMIN_ONLY until the accountant role existed. They are still a
     * hard 403 for everyone else rather than a redirect: unlike a stale bookmark
     * into the wrong module, an attempt to open the ledger from a role that has
     * no business with money is worth failing loudly.
     */
    private static final Set<String> FINANCE_ONLY = new HashSet<>(Arrays.asList(
        "/fund", "/expenses", "/vendors", "/work-orders", "/finance-dashboard"
    ));

    /**
     * Paths a COUNSELLOR has no business on — the institute-wide operational
     * dashboard (it shows every student's fees and the whole collection figure)
     * and the academic / back-office modules.
     *
     * Hiding the links is not enough on its own: anyone can type a URL, and a
     * stale bookmark would otherwise drop a counsellor straight into the admin
     * dashboard. They are sent to their own dashboard instead of being shown a
     * 403, because this is a wrong turn rather than an attack.
     */
    private static final Set<String> COUNSELLOR_DENIED = new HashSet<>(Arrays.asList(
        "/dashboard.jsp",
        "/attendance", "/attendance-report",
        "/exams", "/exam-new", "/exam-marks", "/exam-results",
        "/materials",
        "/omr", "/omr-export",
        "/exam-import", "/exam-setup", "/exam-scan", "/scholarship-results", "/candidates", "/roll-list", "/hall-tickets",
        "/online-exams", "/online-exam-results",
        "/teacher-dashboard",
        "/manage-tickets",
        // Management reporting: every report is institute-wide, including other
        // counsellors' conversion rates and the full collection register. A
        // counsellor's own numbers live on /my-dashboard.
        "/reports", "/report-export", "/targets",
        // Finance: the institute's float, its payables and its exam receipts.
        "/fund", "/expenses", "/vendors", "/work-orders", "/exam-fees",
        "/finance-dashboard",
        // HR: colleagues' salaries are nobody else's business.
        "/staff", "/hr", "/hr-dashboard"
    ));

    /**
     * Paths a TEACHER has no business on — anything to do with money, the sales
     * pipeline, or institute-wide management figures. A teacher's job is
     * attendance, exams, materials and OMR.
     */
    private static final Set<String> TEACHER_DENIED = new HashSet<>(Arrays.asList(
        // management
        "/dashboard.jsp", "/my-dashboard", "/reports", "/report-export", "/targets",
        // money
        "/fees", "/collect", "/receipt", "/fee-plan", "/pay-online", "/pay-verify",
        "/fund", "/expenses", "/vendors", "/work-orders", "/exam-fees",
        // sales pipeline
        "/search", "/inquiries", "/lead", "/followup", "/demo", "/reminders",
        "/admission", "/admission.jsp",
        // Bulk lead creation: an import writes hundreds of leads and issues the
        // roll numbers an exam is scored against. That is admin/office work, not
        // a teacher's, even though the exam it feeds is academic.
        "/exam-import",
        "/staff", "/hr", "/hr-dashboard", "/finance-dashboard"
    ));

    /**
     * Paths an ACCOUNTANT has no business on — the sales pipeline, the academic
     * modules, and institute-wide targets.
     *
     * The accountant is the money role: the fund, expenses, vendors, work
     * orders, the fee register, collection, receipts and exam fees are all
     * theirs, and so are the reports, because most of them are financial. What
     * is denied is everything that is somebody else's job.
     */
    private static final Set<String> ACCOUNTANT_DENIED = new HashSet<>(Arrays.asList(
        // sales pipeline
        "/my-dashboard", "/inquiries", "/lead", "/followup", "/demo", "/reminders",
        "/admission", "/admission.jsp",
        // academic + back office
        "/attendance", "/attendance-report",
        "/exams", "/exam-new", "/exam-marks", "/exam-results",
        "/materials", "/omr", "/omr-export",
        "/exam-import", "/exam-setup", "/exam-scan", "/scholarship-results",
        "/candidates", "/roll-list", "/hall-tickets", "/teacher-dashboard",
        "/online-exams", "/online-exam-results",
        "/manage-tickets",
        // setting revenue targets is a management decision, not a bookkeeping one
        "/targets",
        // Payroll is HR's; the accountant sees the resulting debit on the fund
        // statement, which is the part that concerns the books.
        "/staff", "/hr", "/hr-dashboard"
    ));

    /*
     * Note on what the accountant KEEPS: /dashboard.jsp, /search, /students,
     * /student, /fees, /collect, /receipt, /fee-plan, /pay-online, /pay-verify,
     * /exam-fees, /reports, /report-export and the four FINANCE_ONLY screens.
     * The student list stays reachable because fee work starts from a student.
     */

    /**
     * HR is an ALLOW-list, not a deny-list, and deliberately so.
     *
     * Written as a deny-list it would have to name every other path in the
     * system, and would then silently grant HR each new module anybody adds
     * later. Naming what HR CAN reach fails closed instead: a new screen is
     * invisible to HR until somebody puts it here on purpose.
     *
     * HR owns its own module (staff attendance, leave, payroll) plus the people
     * side of the business the institute asked for: the enquiry pipeline,
     * admissions, targets, and the fee register with what is outstanding.
     *
     * Note what is NOT here. Taking money at the counter (/collect, /receipt,
     * /pay-online) is the office's and the accountant's job — HR sees what is
     * owed, and does not handle cash. And the four FINANCE_ONLY screens stay
     * out: paying a salary debits a fund from inside the payroll screen, which
     * is a specific, audited action, and is not the same as handing HR the
     * institute's whole ledger.
     */
    private static final Set<String> HR_ALLOWED = new HashSet<>(Arrays.asList(
        // people
        "/staff", "/hr", "/hr-dashboard",
        // enquiry and admission
        "/inquiries", "/lead", "/admission", "/admission.jsp", "/search",
        // targets
        "/targets",
        // fees: what is owed, and the plan behind it — read, not collect
        "/fees", "/fee-plan",
        // a student's record, reachable from a fee row
        "/students", "/student"
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

        String role = (userObj instanceof User) ? ((User) userObj).getRole() : null;
        req.setAttribute("role", role == null ? "STAFF" : role);

        // Wrong turns, not attacks — send the user to their own home rather than
        // showing a 403 for a link they should never have been offered.
        //
        // This runs BEFORE the hard checks on purpose. Several paths are in more
        // than one list (the finance screens are finance-only AND denied to
        // counsellors and teachers); checking the role lists first means those
        // roles get sent home, which is what a stale bookmark deserves, while
        // anyone else without the right role still gets the 403 below.
        if (isDeniedByRole(role, path)) {
            // homeFor() must never return a path that role is itself denied, or
            // this redirect loops. Verified per role above.
            resp.sendRedirect(req.getContextPath() + homeFor(role));
            return;
        }

        if (ADMIN_ONLY.contains(path) && !"ADMIN".equalsIgnoreCase(role)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "This page is restricted to administrators.");
            return;
        }

        if (FINANCE_ONLY.contains(path)
                && !"ADMIN".equalsIgnoreCase(role) && !"ACCOUNTANT".equalsIgnoreCase(role)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "This page is restricted to the administrator and the accountant.");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Whether this role is barred from this path and should be sent home.
     *
     * Five roles are described by what they may NOT reach; HR is described by
     * what it MAY, because its surface is one screen and a deny-list would grow
     * a hole every time a module is added. STAFF appears in no list — it is the
     * general office role and is limited only by the hard checks above.
     */
    private boolean isDeniedByRole(String role, String path) {
        if (role == null) {
            return false;
        }
        if ("COUNSELLOR".equalsIgnoreCase(role)) return COUNSELLOR_DENIED.contains(path);
        if ("TEACHER".equalsIgnoreCase(role))    return TEACHER_DENIED.contains(path);
        if ("ACCOUNTANT".equalsIgnoreCase(role)) return ACCOUNTANT_DENIED.contains(path);
        if ("HR".equalsIgnoreCase(role))         return !HR_ALLOWED.contains(path);
        return false;
    }

    /** Where a denied user is sent. Mirrors {@code User.homePath()}. */
    private String homeFor(String role) {
        if ("COUNSELLOR".equalsIgnoreCase(role)) return "/my-dashboard";
        if ("TEACHER".equalsIgnoreCase(role))    return "/teacher-dashboard";
        if ("ACCOUNTANT".equalsIgnoreCase(role)) return "/finance-dashboard";
        if ("HR".equalsIgnoreCase(role))         return "/hr-dashboard";
        return "/dashboard.jsp";
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
