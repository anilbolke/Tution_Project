package com.tution.model;

/** Login account / user of the Tuition Management System. */
public class User {

    private int    userId;
    private String username;
    private String fullName;
    private String email;
    private String role;     // ADMIN / STAFF / TEACHER / COUNSELLOR / ACCOUNTANT / HR
    private String mobile;
    private boolean active = true;
    private String createdAt;
    private String lastLogin;

    /** True for the sales roles that own leads (a counsellor sees only own leads). */
    public boolean isCounsellor() { return "COUNSELLOR".equalsIgnoreCase(role); }

    /** True for ADMIN, who sees every counsellor's data. */
    public boolean isAdmin()      { return "ADMIN".equalsIgnoreCase(role); }

    /** True for a teacher — academic screens only, no money and no sales pipeline. */
    public boolean isTeacher()    { return "TEACHER".equalsIgnoreCase(role); }

    /** True for back-office staff: everything operational, minus admin-only settings. */
    public boolean isStaff()      { return "STAFF".equalsIgnoreCase(role); }

    /** True for the accountant — the money roles' counterpart to ADMIN. */
    public boolean isAccountant() { return "ACCOUNTANT".equalsIgnoreCase(role); }

    /** True for HR. Owns the staff directory and nothing else yet. */
    public boolean isHr()         { return "HR".equalsIgnoreCase(role); }

    /*
     * The two questions below USED to be written as !isTeacher(), which was
     * correct while there were four roles and is a trap with six: a negative
     * test hands every role added later the answer "yes" by default, so HR would
     * silently have inherited the fee screens the day the role was created.
     * Both are allow-lists now. The answers for the original four roles are
     * unchanged.
     */

    /**
     * True when this role may see money — fee screens, receipts, collection
     * figures.
     */
    public boolean canSeeFees() {
        return isAdmin() || isStaff() || isCounsellor() || isAccountant() || isHr();
    }

    /**
     * True when this role works the whole sales pipeline — follow-up queue,
     * demo diary, reminder sending.
     *
     * HR is NOT here. HR was given enquiries and admissions, which is the
     * front of the pipeline; chasing calls and sending reminders is the
     * counsellor's daily work, not theirs.
     */
    public boolean canSeeSales() {
        return isAdmin() || isStaff() || isCounsellor();
    }

    /** True for the enquiry list and a lead's record — the pipeline's front end. */
    public boolean canSeeLeads() {
        return canSeeSales() || isHr();
    }

    /** True for setting and tracking quarterly targets. */
    public boolean canSeeTargets() {
        return isAdmin() || isStaff() || isHr();
    }

    /** True for the academic and back-office modules: attendance, exams, materials. */
    public boolean canSeeAcademic() {
        return isAdmin() || isStaff() || isTeacher();
    }

    /** True for institute-wide management figures: reports and targets. */
    public boolean canSeeManagement() {
        return isAdmin() || isStaff();
    }

    /** True for the institute's own money: the fund, expenses, vendors, payables. */
    public boolean canSeeFinance() {
        return isAdmin() || isAccountant();
    }

    /** True for the staff directory. */
    public boolean canSeePeople() {
        return isAdmin() || isHr();
    }

    /**
     * True for the student list and a student's profile.
     *
     * Every role but HR, which has no reason to hold a child's record — HR deals
     * with employees, and the system has no employee data yet.
     */
    public boolean canSeeStudents() {
        return isAdmin() || isStaff() || isTeacher() || isCounsellor() || isAccountant() || isHr();
    }

    /**
     * Where this user belongs when they log in or click the logo.
     *
     * Every role's home must be a page that role is actually allowed to open —
     * AuthFilter sends a denied user here, so a home the role cannot reach is an
     * infinite redirect.
     */
    public String homePath() {
        if (isCounsellor()) return "/my-dashboard";
        if (isTeacher())    return "/teacher-dashboard";
        if (isAccountant()) return "/finance-dashboard";
        if (isHr())         return "/hr-dashboard";
        return "/dashboard.jsp";
    }

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

    public String getLastLogin()         { return lastLogin; }
    public void   setLastLogin(String v) { this.lastLogin = v; }

    /** Sentence-case role for the screen — "Accountant", not "ACCOUNTANT". */
    public String getRoleLabel() {
        if (role == null || role.isEmpty()) return "—";
        String r = role.trim();
        if ("HR".equalsIgnoreCase(r)) return "HR";
        return r.substring(0, 1).toUpperCase() + r.substring(1).toLowerCase();
    }

    public String getFullName()          { return fullName; }
    public void   setFullName(String v)  { this.fullName = v; }

    public String getEmail()             { return email; }
    public void   setEmail(String v)     { this.email = v; }

    public String getRole()              { return role; }
    public void   setRole(String v)      { this.role = v; }
}
