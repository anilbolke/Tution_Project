package com.tution.model;

/** Login account / user of the Tuition Management System. */
public class User {

    private int    userId;
    private String username;
    private String fullName;
    private String email;
    private String role;     // a Role code — see Role.java for the full list
    private String mobile;
    private boolean active = true;
    private String createdAt;
    private String lastLogin;
    private Integer reportsTo;       // users.reports_to — the reporting line (Scope uses it)
    private String reportsToName;    // directory only

    /*
     * The predicates below answer for the role's FAMILY (Role.family), so the
     * roles added from Work Flow.xlsx borrow an existing role's rules: an ABM
     * is a counsellor here, an EDP a teacher, a branch manager staff. isAdmin()
     * is the exception — it is always the exact ADMIN role and nobody else.
     */
    private boolean inFamily(String f) { return f.equalsIgnoreCase(Role.familyOf(role)); }

    /** True for the sales roles that own leads (a counsellor sees only own leads). */
    public boolean isCounsellor() { return inFamily("COUNSELLOR"); }

    /** True for ADMIN, who sees every counsellor's data. */
    public boolean isAdmin()      { return "ADMIN".equalsIgnoreCase(role); }

    /** True for a teacher — academic screens only, no money and no sales pipeline. */
    public boolean isTeacher()    { return inFamily("TEACHER"); }

    /** True for back-office staff: everything operational, minus admin-only settings. */
    public boolean isStaff()      { return inFamily("STAFF"); }

    /** True for the accountant — the money roles' counterpart to ADMIN. */
    public boolean isAccountant() { return inFamily("ACCOUNTANT"); }

    /** True for HR. Owns the staff directory and nothing else yet. */
    public boolean isHr()         { return inFamily("HR"); }

    /* ───────────── the role/activity matrix (Doc/Mapping/Work Flow.xlsx) ───────────── */

    /** True when this user's role holds the activity. ADMIN holds every activity. */
    public boolean can(String activity) {
        return isAdmin() || com.tution.dao.AccessDAO.allowed(role, activity);
    }

    /** True when the role holds any activity with this prefix — "FIN_", "RPT_", "ACAD_". */
    public boolean canAny(String prefix) {
        return isAdmin() || com.tution.dao.AccessDAO.allowedAny(role, prefix);
    }

    /** True when the role may open this page (no parameters looked at). */
    public boolean mayOpen(String path) {
        return com.tution.dao.AccessDAO.mayOpenPath(this, path);
    }

    /*
     * The canSeeX() questions pre-date the matrix and are asked all over the
     * JSPs and servlets; each now just names the activities that answer it.
     */

    /** Fee screens, receipts, a student's fee card. */
    public boolean canSeeFees()       { return can("FEES"); }

    /** The counsellor's daily chase — follow-up queue, counsellor classes, reminders. */
    public boolean canSeeSales()      { return can("SALES_FOLLOWUP") || can("SALES_COUNSELLOR") || can("SALES_REMINDER"); }

    /** The enquiry list and a lead's record. */
    public boolean canSeeLeads()      { return can("SALES_LEAD"); }

    /** Setting and tracking targets. */
    public boolean canSeeTargets()    { return can("SALES_TARGET"); }

    /** Any academic screen — also opens the academic dashboard. */
    public boolean canSeeAcademic()   { return canAny("ACAD_"); }

    /** Any report. */
    public boolean canSeeManagement() { return canAny("RPT_"); }

    /** The institute's own money: fund, vendors, work orders, expenses (not exam fees). */
    public boolean canSeeFinance() {
        return can("FIN_FUND") || can("FIN_VENDOR") || can("FIN_WORK_ORDER") || can("FIN_EXPENSE");
    }

    /** The staff directory, the HR register and the HR dashboard. */
    public boolean canSeePeople()     { return can("HR_STAFF"); }

    /** The student list and a student's profile. */
    public boolean canSeeStudents()   { return can("STUDENT"); }

    /**
     * Where this user belongs when they log in or click the logo: the first
     * dashboard their role prefers AND may open, else any dashboard they may
     * open, else the first screen they hold at all.
     *
     * AuthFilter sends a denied user here, so this must only ever return a page
     * the role can open — or /logout, for a role whose every box is unticked.
     */
    public String homePath() {
        String f = Role.familyOf(role);
        String[] prefer =
              "COUNSELLOR".equals(f) ? new String[] { "/my-dashboard", "/dashboard.jsp" }
            : "TEACHER".equals(f)    ? new String[] { "/teacher-dashboard" }
            : "ACCOUNTANT".equals(f) ? new String[] { "/finance-dashboard", "/dashboard.jsp" }
            : "HR".equals(f)         ? new String[] { "/hr-dashboard", "/dashboard.jsp" }
            :                          new String[] { "/dashboard.jsp" };
        for (String p : prefer) if (mayOpen(p)) return p;
        for (String p : DASHBOARDS) if (mayOpen(p)) return p;
        for (String[] s : FIRST_SCREENS) if (can(s[0])) return s[1];
        return "/logout";
    }

    private static final String[] DASHBOARDS = {
        "/dashboard.jsp", "/my-dashboard", "/teacher-dashboard", "/finance-dashboard", "/hr-dashboard"
    };

    /** Last resort for homePath(): an activity and a page it opens. */
    private static final String[][] FIRST_SCREENS = {
        { "STUDENT", "/students" }, { "FEES", "/fees" }, { "SALES_LEAD", "/inquiries" },
        { "SALES_FOLLOWUP", "/followup" }, { "FIN_EXAM_FEES", "/exam-fees" },
        { "ACAD_TICKETS", "/manage-tickets" },
    };

    public int    getUserId()            { return userId; }
    public void   setUserId(int v)       { this.userId = v; }

    public String getUsername()          { return username; }
    public void   setUsername(String v)  { this.username = v; }

    /* Directory-only fields — populated by UserDAO.allStaff(), not by login. */
    public String getMobile()            { return mobile; }
    public void   setMobile(String v)    { this.mobile = v; }

    public boolean isActive()            { return active; }
    public void   setActive(boolean v)   { this.active = v; }

    public String getCreatedAt()         { return createdAt; }
    public void   setCreatedAt(String v) { this.createdAt = v; }

    public Integer getReportsTo()           { return reportsTo; }
    public void    setReportsTo(Integer v)  { this.reportsTo = v; }
    public String  getReportsToName()       { return reportsToName; }
    public void    setReportsToName(String v) { this.reportsToName = v; }

    public String getLastLogin()         { return lastLogin; }
    public void   setLastLogin(String v) { this.lastLogin = v; }

    /** Role name for the screen — "Faculty", "Branch Manager", not the code. */
    public String getRoleLabel() {
        if (role == null || role.isEmpty()) return "—";
        return Role.labelOf(role);
    }

    public String getFullName()          { return fullName; }
    public void   setFullName(String v)  { this.fullName = v; }

    public String getEmail()             { return email; }
    public void   setEmail(String v)     { this.email = v; }

    public String getRole()              { return role; }
    public void   setRole(String v)      { this.role = v; }
}
