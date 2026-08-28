# Revenue & Admission Targets for Counsellors — Development Plan

**Requirement 5.** Give each counsellor a revenue target and an admission target, and show
performance against them — to the counsellor on their own dashboard, and to management across the
team.

---

## 1. What already exists

This is the useful news: **the measuring half is largely built.** The sales module already
attributes work to a counsellor and totals it up.

| Already working | Where |
|---|---|
| Admissions per counsellor | `students.counsellor_id`, counted by `students.created_at` |
| Fees collected per counsellor | `payments` joined to `students` on `counsellor_id`, by `payment_date` |
| Leads, follow-ups, demos, conversion % | `ReportDAO.counsellorPerformance()` — any date range, Excel export |
| Month-to-date figures per counsellor | `SalesStatsDAO.load(counsellorId)` — leads MTD, admissions MTD, revenue MTD, outstanding |
| Counsellor leaderboard | `SalesStatsDAO.leaderboard()` — already on the admin dashboard |
| Booked value of an admission | `student_fees.total_payable` (course + registration + material − concessions + GST) |

**What is missing is only the target side**: somewhere to record what each counsellor is *supposed*
to do, and the comparison against what they actually did. No screen, no table, no achievement
percentage, no sense of whether someone is ahead or behind with a week to go.

---

## 2. The decision that shapes everything: what counts as "revenue"

There are two defensible answers and they produce very different numbers.

| Basis | Meaning | Example |
|---|---|---|
| **Booked** | The value of admissions the counsellor closed in the period — `student_fees.total_payable` | Closes a ₹1,58,900 admission on 12 August → **₹1,58,900 counts in August** |
| **Collected** | Cash actually received in the period — `SUM(payments.amount)` | Family pays ₹50,000 now, the rest over four instalments → **₹50,000 counts in August**, the rest lands in later months |

With instalment plans the gap is large and permanent, not a rounding difference.

> **My recommendation: target on BOOKED revenue, and display collected alongside it.**
>
> A counsellor controls whether the admission closes. They do not control the instalment schedule
> the institute agreed to, nor whether a family pays on the 3rd or the 28th. Targeting them on cash
> makes their number a function of somebody else's decision, and the predictable result is
> counsellors pushing families towards full payment when an instalment plan would have won the
> admission.
>
> Collection still matters — it is just an **accounts** target, not a sales one.

Because institutes disagree on this, the target row will carry a `revenue_basis` of `BOOKED` or
`COLLECTED`. Setting it is a choice, not a code change. **I need your answer on the default.**

---

## 3. Design decisions

**Period.** Monthly, since that is how the existing dashboards already think (`revenueMtd`,
`admissionsMtd`). The table stores an explicit `period_start` / `period_end` with a `period_type`,
so quarterly or annual targets later are new rows, not a migration.

**Attribution.** `students.counsellor_id` — the counsellor who owns the student. This is what every
existing report and dashboard already uses, so targets will agree with the Counsellor Performance
report rather than quietly disagreeing with it. Note `payments.collected_by_id` records who took the
money at the desk; that is a cashier record, not a sales credit, and is deliberately not used here.

**Pacing, not just the final number.** A counsellor at 55% on the 20th of a 31-day month is behind;
on the 5th they are well ahead. Every figure is shown against an *expected-by-today* line
(`target × days elapsed ÷ days in period`) and labelled **Ahead / On track / Behind**. Without this
the number is only useful on the last day of the month, which is too late to act on.

**Zero and missing targets.** A counsellor with no target row is shown as "no target set" rather
than 0% — those are different statements, and 0% of nothing reads as failure.

**Visibility.** A counsellor sees their own target and achievement. Management sees everyone.
Whether the *leaderboard* exposes colleagues' targets to each other is a policy question — see §7.

---

## 4. Schema

```sql
CREATE TABLE counsellor_targets (
  target_id        INT AUTO_INCREMENT PRIMARY KEY,
  counsellor_id    INT NOT NULL,
  period_type      ENUM('MONTH','QUARTER','YEAR') NOT NULL DEFAULT 'MONTH',
  period_start     DATE NOT NULL,
  period_end       DATE NOT NULL,
  admissions_target INT NOT NULL DEFAULT 0,
  revenue_target   DECIMAL(12,2) NOT NULL DEFAULT 0,
  -- BOOKED  = value of admissions closed in the period (student_fees.total_payable)
  -- COLLECTED = cash received in the period (payments.amount)
  revenue_basis    ENUM('BOOKED','COLLECTED') NOT NULL DEFAULT 'BOOKED',
  notes            VARCHAR(255),
  set_by           VARCHAR(100),
  set_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- one target per counsellor per period, so a revision edits rather than stacks
  UNIQUE KEY uq_target (counsellor_id, period_type, period_start),
  INDEX (period_start, period_end),
  CONSTRAINT fk_target_user FOREIGN KEY (counsellor_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Collation pinned to `utf8mb4_unicode_ci` deliberately — the database default is
`utf8mb4_0900_ai_ci`, and a table created without it cannot be joined to `users` on a string column.

**Optional, if you want a team number:** a row with `counsellor_id` pointing at a dedicated
"institute" user, or a nullable `counsellor_id` meaning institute-wide. I would leave this out of
the first cut and simply sum the individual targets — a team target that disagrees with the sum of
its parts is a support call waiting to happen.

---

## 5. What gets built

### 5.1 Setting targets — `/targets` (admin only)

A month picker and a grid of counsellors, editable in place:

```
August 2026                                    [ Copy from July ]  [ Save all ]

Counsellor        Admissions   Revenue (Rs.)   Basis      Notes
Meera Joshi       [   12   ]   [  6,00,000 ]   Booked     [ ................ ]
Vikas Patil       [   10   ]   [  5,00,000 ]   Booked     [ ................ ]
                  ---------    -----------
Team              22           11,00,000
```

- **Copy from last month** — because targets usually change by a little, not from scratch.
- Saving is one transaction for the whole grid.
- Editing an existing target updates it (the unique key guarantees one row per period), keeping
  `set_by` / `updated_at` so a mid-month revision is visible.

### 5.2 The achievement engine — `TargetService`

One place that answers "how is this counsellor doing", used by every screen so they cannot drift
apart:

```
admissions:  actual  = COUNT(students WHERE counsellor_id = ? AND created_at IN period)
revenue:     BOOKED  = SUM(student_fees.total_payable) for those same students
             COLLECTED = SUM(payments.amount WHERE payment_date IN period
                             AND student's counsellor_id = ?)
achieved %   = actual / target
expected %   = days elapsed / days in period
status       = Ahead (actual >= expected + 10%) / On track / Behind
```

### 5.3 Counsellor dashboard card — `/my-dashboard`

Sits above the existing pipeline tiles:

```
YOUR AUGUST TARGET                                       12 days left

Admissions    7 / 12          58%   ▓▓▓▓▓▓░░░░   expected 61%   On track
Revenue     3,45,000 / 6,00,000  58%   ▓▓▓▓▓▓░░░░   expected 61%   On track
                                        collected so far: 1,92,400
```

Both bars carry the expected-by-today marker, so "behind" is visible on the 12th rather than the 31st.

### 5.4 Management view

- The existing **counsellor leaderboard** on the admin dashboard gains Target, Achieved and % columns.
- A **team roll-up**: total target vs total actual, and who is behind pace.

### 5.5 Report — "Target vs Achievement"

A ninth entry in the existing reports screen, so it inherits the date-range picker and one-click
Excel export already built: counsellor, admissions target/actual/%, revenue target/actual/%, basis,
status.

---

## 6. Risks and edge cases

| Case | Handling |
|---|---|
| **A student is reassigned between counsellors** | Actuals are computed live from current ownership, so a past month's figure can move after the fact. This is the one genuinely awkward case — see the question in §7 |
| **Admission cancelled / student deactivated** | `students.is_active = 0`. Decide whether the admission still counts toward the month it was made. Default: it still counts, because the counsellor did close it |
| **Refunds** | `payments` holds no negative rows today, so a refund is invisible to a COLLECTED target. Worth knowing before choosing that basis |
| **Counsellor joins or leaves mid-month** | The target is whatever was set; a pro-rated target is a manual decision, not an automatic one |
| **Admins appear in the leaderboard** (`role IN ('COUNSELLOR','ADMIN')`) | Targets are optional per person, so an admin simply has none unless one is set |
| **Two people editing the grid at once** | Last write wins per row; the unique key prevents duplicates |
| **Target of zero vs no target** | Stored and displayed differently — "no target set" is not 0% |

---

## 7. Questions I need answered

1. **Booked or collected revenue?** (§2) My recommendation is **booked**, with collected shown
   alongside. This is the one that changes what the feature means.
2. **Monthly targets to start with?** Or do you need quarterly / annual from day one?
3. **Reassigned students** — if a lead moves from Meera to Vikas in September, should August's
   figures still credit Meera? *Live attribution* is simpler and always agrees with the current
   reports; *snapshot at month end* is historically stable but adds a monthly close step. My
   recommendation: live, and revisit only if reassignment turns out to be common.
4. **Can counsellors see each other's targets** on the leaderboard, or only their own?
5. **Do admins get targets** too, or counsellors only?
6. **Does a cancelled admission** reduce the counsellor's count for that month?

---

## 8. Phases

| Phase | Scope | Days |
|---|---|---|
| **1** | Migration + `TargetDAO` + the `/targets` admin grid with copy-from-last-month | 2 |
| **2** | `TargetService` achievement engine + the counsellor dashboard card with pacing | 2 |
| **3** | Leaderboard target columns + management team roll-up | 1 |
| **4** | "Target vs Achievement" report with Excel export | 1 |
| | **Total** | **~6 dev-days** |

Phase 1 and 2 together are the usable product — someone can set targets and counsellors can see
where they stand. Phases 3 and 4 are the management reporting on top.
