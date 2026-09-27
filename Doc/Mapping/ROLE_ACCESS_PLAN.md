# Role & Activity Mapping — Project Plan

Source: `Doc/Mapping/Work Flow.xlsx` (Sheet1) · Prepared 2026-09-27

---

## 1. What the sheet asks for

### 1.1 Roles (12 login roles + MD)

Reporting hierarchy (drawn on the right side of the sheet):

```
MD Sir
 └─ Business Head
     └─ Branch Manager
         ├─ ABM Sales ──────────── Counsellor
         ├─ Branch Account
         ├─ Branch HR
         └─ Academic Incharge ─┬─ Academic Coordinator
                               └─ EDP
Also in the matrix: Admin, Faculty, Academic Head
```

| # | Sheet column | Proposed role code | Exists today? |
|---|---|---|---|
| 1 | Counsellor | `COUNSELLOR` | ✅ yes |
| 2 | Account | `ACCOUNTANT` | ✅ yes |
| 3 | Branch HR | `HR` | ✅ yes |
| 4 | Admin (office admin) | `OFFICE_ADMIN` | ❌ new *(see decision D1)* |
| 5 | ABM (Asst. Branch Manager – Sales) | `ABM` | ❌ new |
| 6 | Academic Coordinator | `ACADEMIC_COORDINATOR` | ❌ new |
| 7 | EDP | `EDP` | ❌ new |
| 8 | Academic Incharge | `ACADEMIC_INCHARGE` | ❌ new |
| 9 | Faculty | `TEACHER` (label "Faculty") | ✅ yes (rename label only) |
| 10 | Academic Head | `ACADEMIC_HEAD` | ❌ new |
| 11 | Branch Manager | `BRANCH_MANAGER` | ❌ new |
| 12 | Business Head | `BUSINESS_HEAD` | ❌ new |
| — | MD Sir / system owner | `ADMIN` (super admin, everything + mapping screen) | ✅ yes |
| — | (legacy) | `STAFF` | ✅ exists — to migrate *(D1)* |

**Today:** `users.role` ENUM = ADMIN, STAFF, TEACHER, COUNSELLOR, ACCOUNTANT, HR (live DB: 7 users).
**Gap:** 8 new roles.

### 1.2 Activities (43, in 6 ERP tabs) → existing screens

| ERP tab | Activity | URL(s) it guards |
|---|---|---|
| Dashboard | Dashboard | `/dashboard.jsp` (+ role dashboards) |
| My Sales | My Sales | `/my-dashboard` |
| Student | Student | `/students`, `/student`, `/admission` |
| Fees | Fees | `/fees`, `/collect`, `/receipt`, `/fee-plan`, `/pay-online`, `/pay-verify` |
| Sales | Lead | `/inquiries`, `/lead`, `/search` |
| | Follow Up | `/followup`, `/demo` |
| | Counsellor | `/demo` (already labelled "Counsellors" in the menu) — D5 ✅ |
| | Reminder | `/reminders` |
| | Target | `/targets` |
| Academics | Attendance | `/attendance`, `/attendance-report` |
| | Exam | `/exams`, `/exam-new`, `/exam-marks`, `/exam-results` |
| | Import | `/exam-import` |
| | Exam Setup | `/exam-setup` |
| | Candidate | `/candidates`, `/roll-list`, `/hall-tickets` |
| | Scan Sheet | `/exam-scan` |
| | Result | `/scholarship-results` |
| | Online Exam | `/online-exams` |
| | Online Result | `/online-exam-results` |
| | Material | `/materials` |
| | OMR | `/omr`, `/omr-export` |
| | Tickets | `/manage-tickets` |
| Finance | Fund | `/fund` |
| | Vendor | `/vendors` |
| | Work Order | `/work-orders` |
| | Expenses | `/expenses` |
| | Exam Fees | `/exam-fees` |
| People-HR | Attendance / Leave / Salary / Staff | `/hr` (per tab), `/staff`, `/hr-dashboard` |
| Reports | 13 reports (Lead Source, Counsellor Perf, Funnel, Lost Lead, Course-wise Adm, Fee Register, Pending & Ageing, Discount & Scholarship, TGT vs Ach, Exam Fee Collection, Expense Register, Vendor Outstanding, Fund Statement) | `/reports?type=…`, `/report-export?type=…` |

The full YES/NO grid (43 × 12) is in the sheet and will be seeded verbatim into the DB (Phase 2).

---

## 2. How access works today (and why it must change)

- `User.java` has hand-written `canSeeX()` methods per role.
- `AuthFilter.java` has hard-coded `COUNSELLOR_DENIED`, `TEACHER_DENIED`, `ACCOUNTANT_DENIED`, `HR_ALLOWED`, `FINANCE_ONLY` URL lists.
- Menus in JSPs call `user.canSeeX()`.

With 14 roles × 43 activities that becomes ~600 hand-maintained decisions spread over three places. **Proposal:** one permission table, one check.

---

## 3. Target design

### 3.1 Database

```sql
-- 1. extend role enum (Phase 1)
ALTER TABLE users MODIFY role ENUM(
  'ADMIN','STAFF','TEACHER','COUNSELLOR','ACCOUNTANT','HR',
  'OFFICE_ADMIN','ABM','ACADEMIC_COORDINATOR','EDP','ACADEMIC_INCHARGE',
  'ACADEMIC_HEAD','BRANCH_MANAGER','BUSINESS_HEAD') NOT NULL DEFAULT 'STAFF';
ALTER TABLE users ADD COLUMN reports_to INT NULL,          -- hierarchy (Phase 3)
                  ADD CONSTRAINT fk_users_reports_to FOREIGN KEY (reports_to) REFERENCES users(user_id);

-- 2. role master (label, home page, level)
CREATE TABLE roles (
  role_code  VARCHAR(30) PRIMARY KEY,
  role_label VARCHAR(60) NOT NULL,
  home_path  VARCHAR(80) NOT NULL,
  level      TINYINT     NOT NULL,        -- 1 = MD ... 6 = Counsellor/Faculty
  sort_order TINYINT     NOT NULL
);

-- 3. activity master (the 43 rows of the sheet)
CREATE TABLE activities (
  activity_code VARCHAR(40) PRIMARY KEY,  -- e.g. SALES_LEAD, RPT_FUNNEL
  erp_tab       VARCHAR(20) NOT NULL,     -- Dashboard / Sales / Academics / ...
  label         VARCHAR(60) NOT NULL,
  sort_order    SMALLINT    NOT NULL
);

-- 4. which URL belongs to which activity
CREATE TABLE activity_paths (
  path          VARCHAR(80) PRIMARY KEY,  -- '/followup'
  activity_code VARCHAR(40) NOT NULL REFERENCES activities(activity_code)
);

-- 5. THE MATRIX (seeded from Work Flow.xlsx)
CREATE TABLE role_activity (
  role_code     VARCHAR(30) NOT NULL,
  activity_code VARCHAR(40) NOT NULL,
  PRIMARY KEY (role_code, activity_code)
);
```

### 3.2 Java

| Piece | Change |
|---|---|
| `AccessDAO` / `AccessCache` | Loads the matrix once into memory (`Map<role, Set<activity>>`, `Map<path, activity>`); reloaded when the admin saves the mapping screen. |
| `User` | Add `can(String activityCode)`; existing `canSeeX()` methods become thin wrappers over `can()` so current JSPs keep working, then get removed gradually. `homePath()` read from `roles`. |
| `AuthFilter` | Replace the four deny/allow lists with: `path → activity → role allowed?`. Paths not in `activity_paths` keep today's behaviour (logged-in = OK) so nothing breaks while mapping. `ADMIN` always passes. |
| `ReportDAO.types()` | Filter report list by `RPT_*` activity instead of `isAdminOnly`. |
| Nav / `staff_header.jsp` / `staff_tabbar.jsp` | Build menu from the user's allowed activities, grouped by ERP tab. |
| `StaffDirectoryServlet` | New roles in the create/assign dropdown, "Reports to" picker, who-may-assign-whom rules. |
| **New:** `/role-mapping` (ADMIN only) | Grid screen = the Excel sheet on screen: roles as columns, activities as rows, checkboxes; Save. |

### 3.3 Data scope (row-level) — separate from screen access

Screen access says *which page*; scope says *which rows on it*.

| Role | Leads / students / sales figures |
|---|---|
| Counsellor | own only (sheet: "Only Counsellor") |
| ABM | own + counsellors reporting to them (sheet: "Both Counsellor & ABM") |
| Branch Manager, Business Head, Admin | all |
| Others | all rows of the screens they can open |

Implemented via `reports_to` + a `UserDAO.teamIds(user)` helper used where `!user.isAdmin()` checks exist today (LeadServlet, InquiryListServlet, CounsellorDashboardServlet, StudentEditServlet, FeeScheduleServlet).

---

## 4. Phases

### Phase 1 — Create roles  ✅ *done & login-tested 2026-09-27*

> **Built:** `model/Role.java` (single registry: code, label, interim family) · `users.role` ENUM +8 roles and `users.reports_to` (migration `database/migrations/2026-09-roles-workflow.sql`, applied to live DB; `schema.sql` updated) · `User` predicates + `AuthFilter` rules resolve by family · staff directory lists/creates new roles with labels (HR may assign ABM / Academic Incharge / Academic Coordinator / EDP; managers, Office Admin only by ADMIN) · ABM counts as a lead owner in counsellor pickers/reports · header & HR pages show labels ("Faculty").
> **Test logins** (password `test123`): `t_officeadmin`, `t_abm`, `t_acoord`, `t_edp`, `t_aincharge`, `t_ahead`, `t_bm`, `t_bh`.
> **Deferred:** the `reports_to` picker moves to Phase 3 (it is only used for data scope). No `roles` DB table yet — it's created in Phase 2 with the matrix.

1. `ALTER` role enum + `roles` master table + seed 14 rows; update `database/schema.sql`.
2. `User.java`: `isAbm()`, `isBranchManager()`… + `getRoleLabel()` from master ("Faculty", "ABM", "EDP").
3. Home page per new role (use existing dashboards: sales roles → `/my-dashboard`, academic → `/teacher-dashboard`, managers → `/dashboard.jsp`).
4. `StaffDirectoryServlet` + `staff` JSP: create/change users to new roles; `reports_to` picker.
5. Create one test login per new role.
6. **Interim access:** until Phase 2, each new role maps to the closest existing rule set so logins don't dead-end.
   ✅ *Done when:* every role can log in and lands on its home page.

### Phase 2 — Activity mapping (the matrix)  ✅ *done & tested 2026-09-27*

> **Built**
> - **DB:** `activities` (43), `activity_paths` (61 URL→activity rows), `role_activity` (277 grants), `role_activity_log`. Generated from the sheet by `database/tools/workflow_to_sql.py` → `database/migrations/2026-09-role-activity-matrix.sql` (applied to live DB) **and** a marked block at the end of `schema.sql` (fresh installs). Re-running the script **resets** the matrix to the sheet.
> - **`dao/AccessDAO`:** loads the matrix into memory; `mayOpen(user, path, req)` is the only access rule. Fails **closed** (only ADMIN gets in) if the tables can't be read. Unmapped staff URLs are ADMIN-only — a new screen is hidden until it's mapped.
> - **`User.can(activity)` / `canAny(prefix)` / `mayOpen(path)`:** old `canSeeX()` now read from the matrix. `homePath()` = the first dashboard the role may open.
> - **`AuthFilter`:** four hand-kept role lists removed → one matrix check. `/role-mapping`, `/server-logs` = ADMIN only.
> - **Menus:** header, mobile tab bar, main dashboard tiles/strips, and the Academic / My Sales / Finance / HR dashboards all show only what `can()` allows.
> - **Reports:** picker + export filtered per report (`RPT_*`); default report = first one the role has.
> - **`/role-mapping`** (ADMIN): sheet-style checkbox grid, all/none per row & column, unsaved changes highlighted, change log. A save applies on everyone's next click (no re-login).
>
> **Rules the sheet can't express (in code, on purpose)**
> - `/hr` is everyone's attendance/leave/**salary** register → needs `HR_STAFF` **plus** the tab's activity. The sheet's "Attendance/Leave = YES for all" means *own* → Phase 3 self-service. `HrServlet` also checks the posted *action*, not just the tab.
> - `/targets` (where management sets every counsellor's target) stays shut for Counsellor/ABM although the sheet says YES; they see their own target on My Sales. Phase 3.
> - Institute-wide sales/target strips on the main dashboard are hidden from Counsellor/ABM (they're scoped to own leads).
>
> **Tested:** `python database/tools/access_matrix_test.py` — 13 roles × 63 URLs against the sheet → **PASS**, no server errors. Mapping save → takes effect in the same session → restore identical. HR salary-by-wrong-tab POST → 403.
>
> **Behaviour changes existing users will notice (the sheet asks for them):** **Faculty** loses Attendance, Exams, OMR, Students (keeps Online Exam, Material, Tickets). **HR** loses Leads and Fees, gains My Sales, Tickets and 2 reports (Counsellor Performance, TGT vs Ach). **Accountant** gains Tickets, the main dashboard and the 4 finance reports, loses the sales reports. **Counsellor** gains the main dashboard, Exam Fees, Tickets and 6 sales reports. Legacy **STAFF** keeps exactly what it had (seeded, not on the sheet).

### Phase 3 — Hierarchy & data scope  ✅ *done & tested 2026-09-27*

> **Built**
> - **Reporting line:** Staff directory → *Reports to* on every row and on *Add someone*. Audited (`user_audit` action `MANAGER`). Refuses self and two-person loops. Migration `database/migrations/2026-09-reporting-line.sql`.
> - **`dao/Scope`:** one rule for whose rows you see — counsellor = own, **ABM = own + direct reports**, everyone else = all. The old single `counsellor_id = ?` became `Scope.teamOf(col)` (same one bind), so every DAO kept its signature: leads, follow-ups, counsellor classes, reminders, search, students, instalments, My Sales figures. The three copied "own lead only" checks → `Scope.mayAccess`.
> - **Reports:** lead/student-based reports (9) are scoped for counsellor/ABM, with a note on the report saying so. Fund, vendor, expense and exam-fee reports have no owner and stay institute-wide.
> - **My Sales:** ABM gets a picker for self + team (their own view totals the team); management picks any counsellor; a counsellor sees only themself.
> - **Targets:** Counsellor/ABM now open `/targets` **read-only**, own/team rows only; saving stays with management.
> - **My HR (`/my-hr`):** own attendance by month, own leave requests + *Apply for leave* (always for yourself), own **paid** payslips — each section follows HR_ATTENDANCE / HR_LEAVE / HR_SALARY. Linked as **My HR** in the header.
>
> **Tested:** matrix test 13 roles × 64 URLs → PASS (no exceptions left). Scope test 32/32: counsellor vs ABM vs out-of-team counsellor across lead list, lead open (403), search, reports, My Sales picker, targets (read-only, POST refused), My HR (leave filed for self even with a forged `userId`), reporting-line loop refused.
>
> **Notes:** scope is one level (an ABM's direct reports). Only an ADMIN can move a lead between counsellors — an ABM reassigning within the team would be a small follow-up if wanted.

### Phase 4 — Test & roll-out
1. Automated check: log in as each of 14 roles, hit all ~60 URLs, compare to matrix → pass/fail table.
2. Migrate existing users (STAFF → per D1).
3. Deploy (schema script + WTP publish + context reload), update memory/docs.

---

## 5. Decisions needed before / during build

| # | Question | My recommendation |
|---|---|---|
| **D1 ✅** | Sheet's **"Admin"** column has limited access (no sales, no fees, but finance YES). Is that an *office admin* separate from the system `ADMIN` (MD, full access)? | Yes — new `OFFICE_ADMIN`; keep `ADMIN` = MD/super admin. Migrate the 1 existing `STAFF` user to whichever role fits. |
| **D2 ✅** | Matrix stored in **DB with an edit screen**, or hard-coded in Java? | DB + `/role-mapping` screen — the business will change it again. |
| D3 | People-HR **Attendance/Leave = YES for all roles** — means each person sees/applies for their **own**? Faculty **Salary = YES** — own payslip? | Yes, self-service; only HR / Business Head see everyone. |
| D4 | Academic roles have **Dashboard = NO** — what is their landing page? | `/teacher-dashboard` (academic dashboard), not the admin dashboard. |
| D5 ✅ | Sales › **"Counsellor"** activity (Counsellor, BM, BH = YES) — what screen is it? | `/demo` — the existing menu already calls it "Counsellors". |
| D6 | Sheet quirks to confirm: Branch Manager **Fund Statement = NO** and **Staff/Salary = NO** while Business Head YES; stray "YES" in row 6 col W; Faculty **Online Exam = YES** but Online Result NO. | Take the sheet literally; client can adjust later on the mapping screen. |
| D7 | Multi-branch (a `branches` table) — needed now? "Branch Manager / Business Head" suggests several branches. | Out of scope now; design keeps a slot for `branch_id` later. |

---

## 6. Files expected to change

`database/schema.sql`, new `database/migrations/2026-09_roles_mapping.sql`,
`model/User.java`, new `model/Role.java`, new `dao/AccessDAO.java`, `dao/UserDAO.java`, `dao/ReportDAO.java`,
`filter/AuthFilter.java`, `servlet/StaffDirectoryServlet.java`, new `servlet/RoleMappingServlet.java`,
`servlet/LeadServlet.java`, `InquiryListServlet.java`, `CounsellorDashboardServlet.java`, `StudentEditServlet.java`, `FeeScheduleServlet.java`,
`WEB-INF/views/staff_header.jsp`, `staff_tabbar.jsp`, `dashboard.jsp`, staff directory JSP, new `role_mapping.jsp`.
