package com.tution.model;

/**
 * Every login role, in the order the staff directory lists them.
 *
 * The codes are the values of the users.role ENUM column — add a role here AND
 * there, never one without the other.
 *
 * {@link #family} is the older role whose screen rules a newer role borrows
 * until the role/activity matrix (Doc/Mapping/ROLE_ACCESS_PLAN.md, phase 2)
 * takes over. Borrowing is deliberately on the narrow side: an ABM is scoped to
 * their own leads like a counsellor, and the academic roles get the teacher's
 * screens, so no new role can see more than the sheet gives it before the
 * matrix is switched on.
 */
public enum Role {

    ADMIN               ("Admin",                 "ADMIN"),
    BUSINESS_HEAD       ("Business Head",         "STAFF"),
    BRANCH_MANAGER      ("Branch Manager",        "STAFF"),
    ACADEMIC_HEAD       ("Academic Head",         "TEACHER"),
    OFFICE_ADMIN        ("Office Admin",          "STAFF"),
    ACCOUNTANT          ("Accountant",            "ACCOUNTANT"),
    HR                  ("HR",                    "HR"),
    ABM                 ("ABM",                   "COUNSELLOR"),
    COUNSELLOR          ("Counsellor",            "COUNSELLOR"),
    ACADEMIC_INCHARGE   ("Academic Incharge",     "TEACHER"),
    ACADEMIC_COORDINATOR("Academic Coordinator",  "TEACHER"),
    EDP                 ("EDP",                   "TEACHER"),
    TEACHER             ("Faculty",               "TEACHER"),
    STAFF               ("Staff",                 "STAFF");

    private final String label;
    private final String family;

    Role(String label, String family) {
        this.label  = label;
        this.family = family;
    }

    public String code()   { return name(); }
    public String label()  { return label; }
    public String family() { return family; }

    /** The role for a code, or null for null/unknown — never throws. */
    public static Role of(String code) {
        if (code == null) return null;
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The legacy role whose rules apply; an unknown code is returned unchanged. */
    public static String familyOf(String code) {
        Role r = of(code);
        return r == null ? code : r.family;
    }

    /** Screen label — "Faculty", "Branch Manager"; an unknown code as-is. */
    public static String labelOf(String code) {
        Role r = of(code);
        return r == null ? (code == null ? "—" : code) : r.label;
    }

    /** All codes, in display order. */
    public static String[] codes() {
        Role[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) out[i] = all[i].name();
        return out;
    }
}
