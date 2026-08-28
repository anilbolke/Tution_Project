# Finance Module — Exam Fees, Expense Fund, Vendors & Work Orders

**Status: COMPLETE.** All 7 phases are built, deployed and tested. All three of the client's
original asks work end to end: exam fees collected by roll number with fully-paid tracking; an
expense fund that expenses debit; and vendors with work orders where every expense records
which order it paid.

`database/schema.sql` now builds a fresh install identical to the migrated database
(42 tables, verified by building one).
**Date:** 2026-08-11 · last updated 2026-08-13

---

## 1. What was asked for

Three things, in the client's words:

1. **"All exams paid (Hamse and all) — track fully-paid exam candidates."** A candidate pays the tuition for their exam; the money is taken **against that candidate's roll number**; the tuition needs to see who has paid in full and who has not.
2. **"They have a specific expense amount for tuition which is credited, and in Add Expense the amount will be debited and minus from that amount — keep maintaining this."** A fund is topped up, expenses draw it down, and a running balance is maintained.
3. **"Tuition creates a work order to pay a vendor. If one vendor has 3 work orders — ₹1,00,000, ₹1,00,000 and ₹20,000 — the Expenses module must have the option for which vendor, which work order the payment was done."**

### These are one module, not three

They are the two halves of a cash book plus the thing that connects them:

```
                    MONEY IN                    MONEY OUT
              ┌──────────────────┐        ┌──────────────────┐
              │  Exam fees       │        │  Expenses        │
              │  per candidate   │        │  ├─ general      │
              │  (roll number)   │        │  └─ vendor work  │
              └────────┬─────────┘        │     order        │
                       │                  └────────┬─────────┘
                    credit                      debit
                       └──────────┬───────────────┘
                          ┌───────▼────────┐
                          │  FUND LEDGER   │
                          │  balance =     │
                          │  Σcr − Σdr     │
                          └────────────────┘
```

Building them as three unrelated screens is the main risk here: you end up with three numbers that disagree and no way to tell which is right. One ledger, three entry points.

---

## 2. What exists today (verified, not assumed)

Checked against all 42 servlets, 30 DAOs, `database/schema.sql` and the 9 migration files.

| Thing | Status |
|---|---|
| Expenses | **Nothing.** Zero matches for expense/vendor/work_order anywhere in `src/main` or `database/` |
| Vendors | **Nothing** |
| Fund / petty cash | **Nothing** |
| Exam fee | **Nothing.** `exams` has no fee column; `exam_candidates` has no payment state |
| Student fee collection | Exists and works — `payments`, `fee_installments`, `PaymentDAO`, receipts, PDF, WhatsApp |

So this module is greenfield. Nothing needs migrating, and nothing existing needs to change except three additive columns.

### Two facts that shape the whole design

**(a) An exam candidate is a lead, not a student.**

```
exam_candidates.inquiry_id  →  inquiries(inquiry_id)     NOT NULL
payments.student_id         →  students(student_id)      NOT NULL
```

A scholarship candidate is a school child who has not enrolled. They have **no `students` row**, so their exam fee **cannot** go into `payments` as it stands.

**(b) `URL patterns are unique or the whole context dies.`** Two `@WebServlet`s on one pattern failed the entire deployment earlier in this project. The five patterns below are confirmed free: `/exam-fees`, `/vendors`, `/work-orders`, `/expenses`, `/fund`.

---

## 3. Design decisions

### D1 — Exam fees go in their own table, **not** in `payments`

The tempting shortcut is to make `payments.student_id` nullable and add `candidate_id`. **Reject it.** Every money figure in the app is a `SUM` over `payments` grouped by student:

- `FeeService.outstanding(studentId)`
- `StatsDAO` — dashboard Collected / Outstanding tiles
- `ReportDAO` — daily/monthly collection register, pending-fee aging, and the new Target vs Achievement report
- `TargetDAO` — counsellor revenue achievement

Exam fees dropped into that table would silently inflate tuition collection, counsellor revenue targets and the outstanding calculation. The failure is quiet, appears only in month-end totals, and is very hard to unpick afterwards.

`exam_payments` keyed on `candidate_id` keeps the two books separate and leaves every existing figure untouched. Receipt numbering is reused with a different prefix so the two series never collide: `RCPT-…` for tuition, `EXM-…` for exams.

### D2 — The balance is **derived**, never stored

No `fund_accounts.balance` column. Balance is `SUM(credits) − SUM(debits)` over the ledger.

A stored balance drifts the first time two people post at once, or an expense is edited, or a transaction is rolled back after the column was already updated — and once it has drifted there is no way to know what the right number was. At this volume (hundreds of rows a year) the `SUM` is free.

### D3 — A work order cannot be over-paid

The client's own example is the test case: vendor with work orders of ₹1,00,000 + ₹1,00,000 + ₹20,000. Paying ₹60,000 against the ₹20,000 order must be refused.

Check-then-insert is not enough — two clerks posting ₹15,000 each against that ₹20,000 order at the same moment would both see "₹20,000 remaining" and both succeed. The work order row is locked with `SELECT … FOR UPDATE` inside the transaction, the same pattern already proven in this project for roll-number allocation (19 concurrent threads, 19 unique rolls, 0 errors).

### D4 — Expenses are voided by reversal, never deleted

A ledger you can `DELETE` from is not a ledger. Voiding an expense writes a **contra entry** that cancels it, and both rows stay visible. The auditor's question — "why did the balance change on the 14th?" — has to be answerable.

### D5 — Money is `BigDecimal`, not `int`

`Payment.amount` is an `int` today (`ps.setInt(3, p.getAmount())`) against a `DECIMAL(10,2)` column. It works for whole-rupee tuition fees. It will not do here — vendor invoices carry GST at 18% and produce paise. New models use `BigDecimal`; the existing `Payment` is left alone.

### D6 — Finance is not a counsellor's or a teacher's screen

`AuthFilter` uses **deny lists** — `COUNSELLOR_DENIED` and `TEACHER_DENIED`. Anything not listed is **allowed**. A new module is therefore visible to every role until it is explicitly denied. All five new paths must be added to both lists, and `/vendors`, `/work-orders`, `/expenses`, `/fund` additionally to `ADMIN_ONLY` — institute payables are management money.

`/exam-fees` stays open to ADMIN + STAFF, because taking exam fees at the counter is front-desk work.

---

## 4. Schema

One migration: `database/migrations/2026-08-finance-01.sql`.

Every table pinned `COLLATE=utf8mb4_unicode_ci` — the DB default is `utf8mb4_0900_ai_ci` and the core tables are `utf8mb4_unicode_ci`. Getting this wrong produces `ERROR 1267 Illegal mix of collations` on the first join, exactly as it did on the scholarship-exam tables.

### 4.1 Exam fees (money in)

```sql
ALTER TABLE exams
  ADD COLUMN exam_fee DECIMAL(10,2) NOT NULL DEFAULT 0;   -- list price for this exam

ALTER TABLE exam_candidates
  ADD COLUMN fee_amount   DECIMAL(10,2) NULL,             -- per-candidate override; NULL = use exams.exam_fee
  ADD COLUMN fee_waived   TINYINT(1) NOT NULL DEFAULT 0,  -- sponsored / free seat
  ADD COLUMN waiver_reason VARCHAR(160) NULL;

CREATE TABLE exam_payments (
  exam_payment_id INT AUTO_INCREMENT PRIMARY KEY,
  candidate_id  INT NOT NULL,
  receipt_no    VARCHAR(30) NOT NULL UNIQUE,              -- EXM-2627-0481
  amount        DECIMAL(10,2) NOT NULL,
  payment_mode  ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online') NOT NULL DEFAULT 'Cash',
  payment_date  DATE NOT NULL,
  txn_ref       VARCHAR(60),
  school_receipt_id INT NULL,                             -- set when part of a bulk school payment
  remarks       VARCHAR(255),
  collected_by_id INT NULL,
  status        ENUM('ACTIVE','VOID') NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX (candidate_id), INDEX (payment_date), INDEX (school_receipt_id),
  CONSTRAINT fk_ep_cand FOREIGN KEY (candidate_id) REFERENCES exam_candidates(candidate_id),
  CONSTRAINT fk_ep_user FOREIGN KEY (collected_by_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One cheque from a school covering many candidates.
CREATE TABLE school_receipts (
  school_receipt_id INT AUTO_INCREMENT PRIMARY KEY,
  exam_id       INT NOT NULL,
  school_name   VARCHAR(160) NOT NULL,
  receipt_no    VARCHAR(30) NOT NULL UNIQUE,              -- SCH-2627-0007
  total_amount  DECIMAL(12,2) NOT NULL,
  payment_mode  ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online') NOT NULL DEFAULT 'Cheque',
  payment_date  DATE NOT NULL,
  txn_ref       VARCHAR(60),
  collected_by_id INT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sr_exam FOREIGN KEY (exam_id) REFERENCES exams(exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Why `school_receipts` is in the plan.** The whole scholarship pipeline is school-driven — schools send the student list and the tuition bulk-imports it. A school paying for 60 candidates with one cheque is the *likely* case, not the exception. Supporting it now costs one table; retrofitting it later means splitting receipts that have already been handed out.

Paid status is **derived**, never stored:

```
payable = fee_waived ? 0 : COALESCE(exam_candidates.fee_amount, exams.exam_fee)
paid    = COALESCE(SUM(exam_payments.amount WHERE status='ACTIVE'), 0)
state   = payable = 0 ? WAIVED
        : paid >= payable ? PAID
        : paid > 0 ? PARTIAL
        : UNPAID
```

### 4.2 Fund ledger (the running balance)

```sql
CREATE TABLE fund_accounts (
  fund_id     INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80) NOT NULL UNIQUE,               -- "Main Office Fund", "Branch 2 Petty Cash"
  opening_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
  is_active   TINYINT(1) NOT NULL DEFAULT 1,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fund_transactions (
  txn_id      INT AUTO_INCREMENT PRIMARY KEY,
  fund_id     INT NOT NULL,
  txn_date    DATE NOT NULL,
  direction   ENUM('CREDIT','DEBIT') NOT NULL,
  amount      DECIMAL(12,2) NOT NULL,
  source_type ENUM('TOPUP','EXPENSE','EXAM_FEE','ADJUSTMENT','REVERSAL') NOT NULL,
  source_id   INT NULL,                                  -- expense_id / exam_payment_id
  narration   VARCHAR(255),
  created_by_id INT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX (fund_id, txn_date), INDEX (source_type, source_id),
  CONSTRAINT fk_ft_fund FOREIGN KEY (fund_id) REFERENCES fund_accounts(fund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`balance = opening_balance + Σ CREDIT − Σ DEBIT`. Every credit and every debit is a row; nothing is updated in place.

### 4.3 Vendors and work orders

```sql
CREATE TABLE vendors (
  vendor_id   INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(160) NOT NULL,
  contact_person VARCHAR(120),
  mobile      VARCHAR(15),
  email       VARCHAR(120),
  address     VARCHAR(255),
  gstin       VARCHAR(20),
  pan         VARCHAR(12),
  bank_account VARCHAR(40),
  bank_ifsc   VARCHAR(15),
  category    VARCHAR(60),                               -- Printing, Stationery, Housekeeping…
  is_active   TINYINT(1) NOT NULL DEFAULT 1,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_vendor_name (name),
  INDEX (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE work_orders (
  work_order_id INT AUTO_INCREMENT PRIMARY KEY,
  vendor_id   INT NOT NULL,
  wo_no       VARCHAR(30) NOT NULL UNIQUE,               -- WO-2627-0003
  title       VARCHAR(200) NOT NULL,                     -- "OMR sheet printing - HAMSE Sept"
  description TEXT,
  order_value DECIMAL(12,2) NOT NULL,                    -- 100000 / 100000 / 20000
  order_date  DATE NOT NULL,
  expected_date DATE NULL,
  status      ENUM('DRAFT','ISSUED','IN_PROGRESS','COMPLETED','CANCELLED')
              NOT NULL DEFAULT 'DRAFT',
  created_by_id  INT NULL,
  approved_by_id INT NULL,
  approved_at TIMESTAMP NULL,
  remarks     VARCHAR(255),
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX (vendor_id, status), INDEX (order_date),
  CONSTRAINT fk_wo_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`status` is the **lifecycle** (stored — someone decides a work order is cancelled). Payment progress is **derived**:

```
paid      = Σ expenses.amount WHERE work_order_id = X AND status = 'ACTIVE'
remaining = order_value − paid
payment_status = paid = 0 ? UNPAID : paid < order_value ? PARTIALLY_PAID : FULLY_PAID
```

### 4.4 Expenses

```sql
CREATE TABLE expense_categories (
  category_id INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80) NOT NULL UNIQUE,
  is_active   TINYINT(1) NOT NULL DEFAULT 1,
  sort_order  INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE expenses (
  expense_id  INT AUTO_INCREMENT PRIMARY KEY,
  fund_id     INT NOT NULL,
  voucher_no  VARCHAR(30) NOT NULL UNIQUE,               -- EXP-2627-0142
  expense_date DATE NOT NULL,
  category_id INT NULL,
  vendor_id      INT NULL,                               -- NULL = general expense, no vendor
  work_order_id  INT NULL,                               -- NULL = vendor expense outside any WO
  amount      DECIMAL(12,2) NOT NULL,
  tax_amount  DECIMAL(12,2) NOT NULL DEFAULT 0,
  payment_mode ENUM('Cash','UPI','Bank Transfer','Card','Cheque','Online') NOT NULL DEFAULT 'Cash',
  txn_ref     VARCHAR(60),
  invoice_no  VARCHAR(60),
  bill_path   VARCHAR(255),                              -- uploaded bill/invoice scan
  description VARCHAR(255),
  status      ENUM('ACTIVE','VOID') NOT NULL DEFAULT 'ACTIVE',
  voided_by_id INT NULL, voided_at TIMESTAMP NULL, void_reason VARCHAR(255),
  created_by_id INT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX (expense_date), INDEX (vendor_id), INDEX (work_order_id), INDEX (fund_id, status),
  CONSTRAINT fk_exp_fund   FOREIGN KEY (fund_id)     REFERENCES fund_accounts(fund_id),
  CONSTRAINT fk_exp_cat    FOREIGN KEY (category_id) REFERENCES expense_categories(category_id),
  CONSTRAINT fk_exp_vendor FOREIGN KEY (vendor_id)   REFERENCES vendors(vendor_id),
  CONSTRAINT fk_exp_wo     FOREIGN KEY (work_order_id) REFERENCES work_orders(work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Integrity rule the DB cannot express and the service must:** if `work_order_id` is set, `vendor_id` must equal that work order's vendor. Otherwise a payment can be booked to Vendor A against Vendor B's work order. Enforced in `ExpenseService`, and the UI makes it structurally impossible by loading the work-order dropdown *from* the chosen vendor.

Seed categories: Printing, Stationery, Rent, Electricity, Salary, Marketing, Travel, Housekeeping, Repairs & Maintenance, Internet & Phone, Refreshments, Exam Expenses, Miscellaneous.

---

## 5. Application layer

Follows the existing shape exactly: plain-JDBC DAOs with `map(ResultSet)` + `setNullable()`, `@WebServlet` annotations, scriptlet JSPs, no JSTL, no new JARs.

### 5.1 New classes

| Layer | Classes |
|---|---|
| Model | `ExamPayment`, `SchoolReceipt`, `CandidateFeeRow`, `FundAccount`, `FundTransaction`, `Vendor`, `WorkOrder`, `Expense`, `ExpenseCategory` |
| DAO | `ExamPaymentDAO`, `FundDAO`, `VendorDAO`, `WorkOrderDAO`, `ExpenseDAO` |
| Service | `ExamFeeService`, `FundService`, `ExpenseService` |
| Servlet | `ExamFeeServlet` `/exam-fees`, `VendorServlet` `/vendors`, `WorkOrderServlet` `/work-orders`, `ExpenseServlet` `/expenses`, `FundServlet` `/fund` |
| JSP | `exam_fees.jsp`, `vendors.jsp`, `vendor_view.jsp`, `work_orders.jsp`, `work_order_view.jsp`, `expenses.jsp`, `expense_form.jsp`, `fund.jsp` |

### 5.2 Screens

**`/exam-fees` — All Exams Paid**
Exam picker, then every candidate with roll no, name, school, payable, paid, balance and a state chip (PAID / PARTIAL / UNPAID / WAIVED). Filter by state, school and roll number; totals strip on top (expected / collected / outstanding). Excel export.

**Collect by roll number** — the counter types or scans the roll number. `RollNumber.isValid()` (Damm) rejects a mistyped number **before** the candidate is looked up, so money is never booked against the wrong child because of a transposed digit. Then: candidate card, payable, balance, amount, mode, ref → receipt.

**Bulk school payment** — pick exam + school, tick candidates, enter one total; the amount is allocated across the ticked candidates and one `school_receipts` row plus N `exam_payments` rows are written in a single transaction.

**`/vendors`** — list, add/edit, and a vendor view showing all work orders with ordered / paid / remaining and the vendor's total outstanding.

**`/work-orders`** — list with vendor, value, paid, remaining, lifecycle status and payment status; create/edit/approve; work-order view listing every expense booked against it. PDF via the existing `ReceiptPdf.document(...)` — it already builds a generic label/value document and is reused rather than adding a second PDF writer. **ASCII only — pass `"Rs. "`, never `₹`.**

**`/expenses`** — the register, and the Add Expense form:

```
Fund        [Main Office Fund ▾]   Balance available: Rs. 1,84,000
Date        [11-08-2026]
Category    [Printing ▾]
Vendor      [Sai Printers ▾]            ← optional
Work Order  [WO-2627-0003 · OMR printing · Rs. 20,000 (Rs. 5,000 paid, Rs. 15,000 left) ▾]
Amount      [__________]                ← blocked above Rs. 15,000
```

The work-order dropdown is **populated from the selected vendor** and shows remaining value inline, which is the client's exact requirement — the clerk sees which of the three work orders still has money on it at the moment of choosing.

**`/fund`** — accounts with balances, top-up, and a statement view: date, particulars, credit, debit, running balance. Excel export.

### 5.3 Reports

Four added to the existing `ReportServlet` (reports 10–13), inheriting the date picker and `XlsxWriter` export for free:

| Report | Content |
|---|---|
| Exam Fee Collection | per exam: expected, collected, outstanding, candidate counts by state |
| Expense Register | every expense in range by category and vendor |
| Vendor Outstanding | per vendor: ordered, paid, remaining, across all work orders |
| Fund Statement | credits, debits, closing balance per fund |

---

## 6. Phases

| Phase | Scope | Days |
|---|---|---|
| **1** | Migration + fund ledger core: `fund_accounts`, `fund_transactions`, `FundDAO`, `FundService`, `/fund` with top-up and statement | 3 |
| **2** | Exam fees: schema, `ExamPaymentDAO`, `ExamFeeService`, `/exam-fees` list + collect-by-roll + receipt PDF | 4 |
| **3** | Bulk school payment + waivers/concessions | 2 |
| **4** | Vendors + work orders: DAOs, `/vendors`, `/work-orders`, approval, WO PDF | 4 |
| **5** | Expenses: `/expenses`, add/edit/void, vendor→work-order cascade, over-payment guard, fund debit in one transaction | 4 |
| **6** | Reports 10–13 + Excel export; finance tiles on the admin dashboard | 3 |
| **7** | Role scoping (`AuthFilter` + header), full regression, doc update | 2 |
| | **Total** | **~22 dev-days ≈ 4.5 weeks** |

Phases 1–2 are the first demoable milestone (exam money in, fund visible). Phases 4–5 close the vendor half.

---

## 7. Verification

1. **Over-payment guard.** The client's own numbers: vendor with work orders of ₹1,00,000 / ₹1,00,000 / ₹20,000. Pay ₹15,000 against the third → accepted, ₹5,000 remaining. Pay ₹6,000 → **refused**. Then two threads posting ₹15,000 and ₹10,000 simultaneously against the same order → exactly one succeeds. (Same concurrency harness used for roll-number allocation.)
2. **Balance arithmetic.** Credit ₹2,00,000, post expenses of ₹35,000 + ₹12,500 + ₹1,800, void the ₹12,500 → balance must read ₹1,63,200 and the statement must show four rows plus a reversal, not three rows.
3. **Cross-vendor block.** Attempt an expense with Vendor A and Vendor B's work order via a hand-crafted POST → rejected server-side, not just hidden in the UI.
4. **Exam fee states.** Candidates who are unpaid / part-paid / fully paid / waived all classify correctly, and the exam totals equal `SUM(exam_payments)` for that exam.
5. **Roll-number entry.** A transposed roll number (e.g. `09` → `90`) is rejected by the Damm check digit before any candidate is loaded.
6. **Bulk school payment.** One ₹60,000 cheque across 30 candidates at ₹2,000 → 30 candidates PAID, one school receipt, and `SUM(exam_payments) = 60,000` exactly.
7. **No contamination of tuition figures — the regression that matters most.** Record the dashboard's Collected / Outstanding, the collection report total and every counsellor's revenue achievement *before* the migration; post exam fees and expenses; confirm **all of them are byte-identical afterwards**.
8. **Role scoping.** Log in as COUNSELLOR and as TEACHER and request all five new URLs directly — each must redirect, not render.
9. **Regression sweep.** The existing 29-path admin regression, 6 role-scoped checks and the student portal, all still at 0 failures.

---

## 8. Open questions

These change the build, so they are worth answering before Phase 1 starts. Sensible defaults are proposed so nothing blocks.

| # | Question | Proposed default |
|---|---|---|
| 1 | Does exam fee income **automatically credit** the expense fund, or are they separate books? | **Separate.** Exam fees are institute income; the fund is a spending float topped up deliberately. Auto-crediting makes the float untraceable. A manual "transfer to fund" is one click. |
| 2 | Should an **unpaid candidate be blocked** from a hall ticket or from having their sheet scanned? | **Warn, don't block.** Show a red UNPAID banner on the hall ticket screen and flag it in the list — but let it print. Blocking on exam morning because a cash payment had not been keyed in yet would stop a child sitting the exam. Make it an admin-settable toggle if the client wants hard blocking. |
| 3 | Is the exam fee **the same for everyone**, or per school / per exam type? | Per exam (`exams.exam_fee`), with a per-candidate override and a waiver flag. Covers all three without a pricing engine. |
| 4 | Does a work order need **approval before** expenses can be booked against it? | Yes — only `ISSUED` or later accepts payments; `DRAFT` and `CANCELLED` do not. `approved_by_id` is recorded. |
| 5 | **GST / TDS** on vendor payments — is a tax breakdown needed on the work order and expense? | `tax_amount` is captured on the expense and `gstin` on the vendor, but no tax computation or return-filing logic. Say so now if a GST report is expected. |
| 6 | Can an expense be booked when the **fund balance is insufficient**? | Warn and allow, with the negative balance shown in red. A hard block would stop a genuine urgent payment because a top-up had not been entered yet. |
| 7 | Multiple **funds/branches**, or one? | Table supports many; seed one ("Main Office Fund"). No extra work either way. |

---

## 9. Note on the existing open items

Unchanged by this plan, still outstanding:

- **~20 filled OMR sheets** remain the blocker before any live scholarship exam (`omr_templates."calibrated": false`).
- Missing **Lead Sub Stage** lists for Junk Lead, Direct Enrolled, Fresh, Enquiry and After Board; whether Lost and CPA Registered are complete at 8 each; and what **CPA** stands for.
- Whether the Excel importer should **reject or store** unrecognised Lead Stage / Sub Stage values.
- Pre-production cleanup: synthetic test data, 4 tables still on the wrong collation, test accounts, Razorpay `MOCK_MODE`.
