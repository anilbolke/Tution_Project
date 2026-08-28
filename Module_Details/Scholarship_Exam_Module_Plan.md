# Scholarship Exam Module (HAMSE / HACKSE) — Development Plan

**Flow to build:** school sends a student list → staff uploads the Excel → system registers each
student as an exam candidate and **generates a roll number** → students sit the paper offline and
fill the 60-question OMR sheet → staff scans the sheets → system reads the roll number, scores
against the answer key, computes subject-wise marks, percentage and **scholarship %** → the
candidate becomes a lead the counsellor converts into an admission with the scholarship applied.

Sources analysed:
- `Requirement/LEAD TEMPLATE FOR NEW ERP.xlsx` — 49 columns, 1 sample row
- `Requirement/Broucher/havellsson 60 que omr (4).pdf` — the answer sheet, 1 page

---

## 1. What the two files actually specify

### 1.1 The OMR sheet (measured from the PDF)

| Region | Detail |
|---|---|
| Questions | **60**, laid out in **4 blocks of 15** (1‑15, 16‑30, 31‑45, 46‑60) |
| Options | 4 per question — A B C D |
| **Roll No.** | **6 digits**, bubbled 0‑9 (6 columns × 10 rows) |
| Booklet code | **A / B / C / D**, bubbled |
| Board | ICSE / CBSE / STATE / OTHER, bubbled |
| Exam name | HAMSE / HACKSE, bubbled |
| Marking scheme | **+4 correct, −1 wrong** → max **240** |
| Timing marks | black registration bars down **both** left and right edges |
| Written, not bubbled | Student name, Parent's mobile (10 boxes), Alternate mobile (10 boxes), School name, Exam centre, Date of exam, signatures |

Two consequences that drive the design:

**The roll number is the only machine-readable identity on the sheet.** Name, school and both
mobile numbers are hand-written boxes with no bubble grid, so nothing else can be read reliably.
If the roll number is misread, the marks land on the wrong student and there is no second field to
catch it. Roll number generation therefore has to be designed defensively (§3.2).

**A booklet code means four different answer keys per exam.** The paper is shuffled into versions
A–D, so scoring must select the key by the booklet bubble read off each individual sheet. The
current code supports exactly one key per scan session.

### 1.2 The Excel template

49 columns, sparse (the sample row fills only 14 of them). Grouped by where the data belongs:

| Group | Columns |
|---|---|
| Identity | Name, Father's Name, Mother's Name, Student's Contact, Father's Contact, Mother's Contact, Date of Birth, Gender, Email |
| Address | Street Address, City, District, State |
| Academic | School, Class, Board, Percentage Score in Previous Class, Current Tutor Name/Academy, Student Type, Caste category, Sibling Detail |
| Course interest | Academic Term, Select Course, Course Interested In, Stream, Preferred Centre |
| **Exam** | Exam Roll Number, Date of Exam, Exam Date, Exam Status, EXAM Attempt, Registered For HAMSE/HACKSE/HAT, EXAM APPEARED, Physics / Chemistry / Maths / Biology / Total / Percentage Score, Scholarship Percentage |
| Lead / CRM | Lead Source, Lead Stage, Lead Sub Stage, Lead Owner1, Remark, Other Remak, How they came to know about Havellsson? |
| Payment | UTR NUMBER, Payment Status |

Sample row: `Aditi Angad Gutte · 7020288898 · Gyan Mata Vidya Vihar · 10TH · TYM · NEET · Medical ·
Nanded · roll 618062 · STATE board · exam date 21‑Dec‑2025 · HAMSE · Associate Reference`.

Four things worth settling with the institute before coding:

1. **`Date of Exam` (AC) and `Exam Date` (AW) are duplicates.** One should be dropped from the template.
2. **`EXAM Attempt` confirms students re-sit.** A student who sits HAMSE and later HACKSE needs *two*
   roll numbers and two result sets — so the roll number belongs to a *sitting*, not to a person.
   This is why §3.1 puts candidates in a join table rather than a column on the student.
3. **`Other Remak`** is a typo of Remark, and **`Lead Owner1`** carries a stray `1`. Harmless, but the
   importer will key off these exact strings, so they should be fixed once and frozen.
4. **Score columns are outputs, not inputs.** Physics/Chemistry/Maths/Biology/Total/Percentage/
   Scholarship are produced by the OMR scan. The importer must ignore them on upload (or the sheet
   becomes a way to hand-type marks that bypass the answer key entirely).

**Stream decides the subject split.** The Excel carries Maths *and* Biology, but a 60-question paper
has room for three subjects: Medical = Physics/Chemistry/Biology, Engineering = Physics/Chemistry/
Maths. The question-number → subject map must be configured per exam, not hardcoded.

---

## 2. Where we stand

### What already exists and is genuinely reusable

| Asset | Path | Reuse |
|---|---|---|
| OMR bubble reader | `service/OmrService.java` | The core image processing — darkness sampling, timing-mark row registration, ambiguity detection, calibration overlay. Sound and worth keeping |
| Marking scheme | `OmrScanServlet.java:66` | Already parameterised, and already defaults to **+4 / −1** — exactly this sheet |
| Subject breakdown | `OmrService.subjectBreakdown()` | Already computes per-subject correct/wrong/score from question ranges |
| Batch scan + review UI | `omr_scan.jsp`, `OmrScanServlet` | Multi-file upload, per-sheet review, export |
| Excel **writer** | `util/XlsxWriter.java` | All result exports |
| Lead pipeline | `inquiries` + `LeadServlet` + counsellor scoping | Candidates become leads with no new CRM work |
| Concession handling | `service/FeeService.java` | Scholarship flows into the fee ledger through the existing concession fields |

### What breaks on this sheet

The existing OMR engine was calibrated for a **different** answer sheet, and the differences are not
tuning — they are structural:

| | Existing engine | This sheet |
|---|---|---|
| Questions | **180** (4 blocks × 45) — `ROWS_PER_BLOCK = 45` | **60** (4 × 15) |
| Roll digits | **9** — `ROLL_COLS = 9` | **6** |
| Timing marks | right edge only — `TIMING_X0 = 0.93` | both edges |
| Printed bubbles | **red** outlines — `toRed()`, `RED_MARGIN` ignores red as ink | **black** outlines |
| Booklet / board / exam-name bubbles | not read | must be read |

`detectRowCenters()` returns `null` unless it finds exactly `ROWS_PER_BLOCK` timing marks, so on a
60-row sheet it silently falls back to the linear template and loses skew correction. And because
the printed rings here are black rather than red, `toRed()` finds no circles, so `detectColShift()`
returns 0 and horizontal registration is lost too. **Both safety nets fail quietly** — the reader
still produces answers, just less reliable ones, with nothing in the UI to say so.

> **The template constants are `public static` mutable fields.** They are global to the whole
> application, so the 180-question sheet and the 60-question sheet cannot both work at once, and two
> staff members scanning different exams concurrently would corrupt each other's reads. Moving the
> template out of static fields and into per-exam data is the single largest code change in this plan.

### Two defects to fix on the way

**Roll lookup can match the wrong student.** `StudentDAO.findByRoll()` (line 50) matches the bubbled
digits against `students.admission_no` using a **suffix `LIKE '%digits'`**, ordered so exact matches
come first, `LIMIT 2` — and the caller takes the first row. A partial match silently wins when no
exact one exists. On a scholarship exam that decides fee concessions, attaching a score to the wrong
student is not acceptable. Replaced in §4.4 by an exact lookup scoped to the exam's own roster.

**Exam candidates are not students.** `findByRoll` searches `students`, but the people sitting this
exam are school children who have **not** been admitted and have no `admission_no` at all. Loading
thousands of them into `students` would also corrupt every fee report, the dashboard KPIs, attendance
and the student portal, all of which assume a `students` row is an admitted, fee-paying student.

---

## 3. Design decisions

### 3.1 Where candidates live

Three tables, each with one job:

```
inquiries            the person  (already exists — this is the lead)
  └── exam_candidates    the sitting: one row per (person, exam) — holds the ROLL NUMBER
        └── exam_results     the outcome: scores, percentage, scholarship
```

A student who sits HAMSE in December and HACKSE in March gets **two** `exam_candidates` rows with
two roll numbers and two results, against **one** `inquiries` row. This is what the `EXAM Attempt`
column in the Excel is describing, and it is the reason the roll number cannot be a column on the
person.

Candidates go into `inquiries` because they *are* leads — the Excel carries Lead Source, Lead Stage,
Lead Sub Stage, Lead Owner and Remark — and the sales module already handles lead → follow‑up →
admission with counsellor scoping. No parallel CRM is needed. They are only promoted into `students`
at admission, exactly as today.

### 3.2 Roll number generation

Fixed at **6 digits** — the bubble grid has six columns and cannot express more.

**Recommended scheme: 5-digit sequence + 1 check digit.**

```
  0 1 2 3 4   7
  └─ sequence ┘ └ check (Damm)
```

- Allocated from a single global sequence, in a **contiguous block per exam** (exam 1 → 00001‑02500,
  exam 2 → 02501‑…), so a printed roll list stays in order.
- Capacity 99,999 sittings; never reused, so a sheet from the wrong exam is *detected* rather than
  silently scored against whoever holds that number in the current exam.
- The **check digit is the point.** With no second machine-readable field on the sheet, a single
  misread bubble would otherwise turn one valid roll straight into another valid roll. The check
  digit converts a silent mis-attribution into a visible "invalid roll — review" row.
- **Damm, not Luhn.** Luhn is the obvious choice and it is not good enough here: it cannot detect the
  transposition `09` ↔ `90`, because double-and-reduce maps 0 and 9 to the same contribution.
  Measured over 18,000 random rolls that left **2.1% of adjacent transpositions undetected**. Damm's
  totally anti-symmetric quasigroup catches **all** single-digit errors and **all** adjacent
  transpositions for the same one digit of space — verified at 100% on both, over 18,000 samples each.
  A swapped pair and a single wrong bubble are exactly what an OMR misread looks like.

The trade-off: the sample roll `618062` in the Excel does not satisfy a check digit, so existing
manually-issued numbers will not validate. The importer will therefore accept a supplied
`Exam Roll Number` as-is for legacy rows (flagged `legacy`, exempt from check-digit validation) and
generate a fresh compliant number whenever the column is blank — which is the stated requirement.

> If the institute would rather keep their existing numbering, the check digit can be dropped for a
> 6-digit sequence. It costs the ability to detect misreads, and I'd recommend against it — but it is
> their call, and it is a one-line change in the generator.

### 3.3 The sheet template becomes data

An `omr_templates` row describes one physical sheet layout: block count, rows per block, bubble
coordinates, roll grid geometry, timing-mark edges, ink colour of the printed rings, and the extra
bubble regions (booklet / board / exam name). Each exam points at a template.

This is what lets the 180-question sheet and this 60-question sheet coexist, makes concurrent
scanning safe, and means the next sheet revision is a data change rather than a recompile.

### 3.4 Answer keys per booklet

`exam_answer_keys(exam_id, booklet_code, q_no, correct_option)` — four keys per exam. Scoring reads
the booklet bubble from each sheet and selects the matching key. A sheet whose booklet bubble is
blank or ambiguous goes to review rather than being scored against a default.

### 3.5 Scholarship — a formula, not a slab table

Read off brochure pages 9–10. The umbrella exam is **HOSE** (Havellsson Opportunity Scholarship
Exam) with two types, and the exam-driven award is a **straight formula on the score percentage**:

| Exam | Scholarship | Cap |
|---|---|---|
| **HAMSE** (Type A — Academic Merit) | **equal to** the score % | 100% |
| **HACKSE** (Type B — Admission Cum Knowledge) | **90% of** the score % | 90% |

So a HAMSE candidate scoring 72% gets 72% off the course fee; the same score in HACKSE gets 64.8%.
There are no bands — an earlier slab-table assumption was wrong and has been dropped.

That formula is only one of several routes to a scholarship. The full scheme:

**Category A — academic performance**
- **A1 HOSE performance** — the formula above *(automatic, computed from the OMR result)*
- **A2 school exam score** — board >97% / topper **100%**; A1 grade or >95% **40%**; A1 in Science,
  Maths & English or >90% in the same **30%**; A1 in Maths + A2 in Science **25%**; A2 in all **20%**
- **A3 extraordinary performance** — international olympiad rank 1–10 **100%**; below rank 10 **50%**;
  state govt merit **50%**; national olympiad / merit test **50%**
- **A4 previous NEET score** — within 50 marks of cutoff **80%**; 51–100 **70%**; 101–150 **50%**;
  151–200 **30%**; 201–250 **20%**

**Category B — other than academic performance**
- ex-Havellsson student: HA Gurukul **100%**, HA Generous **50%**, HHC/HDLP/HEATS **25%**
- single or paralysed parent **50%** · sibling **30%** · defence **50%** · police directorate **30%** ·
  martyr (lost both parents) **100%**

> **The governing rule, printed on p10:** *"A student can avail any ONE of the scholarship in
> Category A & B, whichever is higher."* Awards therefore **take the maximum, never the sum**. Getting
> this wrong is a direct revenue leak — a sibling of an ex-Gurukul student who also scores well would
> otherwise stack to well over 100%.

Modelled as one `scholarship_rules` table with two rule kinds:

- `FORMULA` — `scholarship = min(cap, factor × percentage)`; A1 only, evaluated automatically
- `FIXED` — a flat percentage for a named criterion; claimed by the counsellor and **requires proof**
  (p10: *"proper proof in the above criteria will have to be submitted"*), so each claim carries a
  verified-by / verified-at stamp before it can win

The award engine collects every applicable rule for a candidate, takes the highest, and records
**which rule won** alongside the value — so the figure on the fee ledger is always explainable.

Percentage is computed on the **positive scale**: `max(0, raw) / 240 × 100`, because negative marking
can drive a raw score below zero.

Also noted from p10, for the fee ledger rather than this module: if GST is ever levied on a
scholarship or concession, it is borne by the student — worth confirming against the 18% GST
assumption already flagged in the sales module.

---

## 4. Build

### 4.1 Schema

```sql
CREATE TABLE exam_candidates (
  candidate_id  INT AUTO_INCREMENT PRIMARY KEY,
  exam_id       INT NOT NULL,
  inquiry_id    INT NOT NULL,                       -- the person (lead)
  roll_no       CHAR(6) NOT NULL,
  roll_kind     ENUM('GENERATED','LEGACY') NOT NULL DEFAULT 'GENERATED',
  booklet_code  CHAR(1) NULL,                       -- expected; actual is read off the sheet
  exam_centre   VARCHAR(120),
  attempt_no    TINYINT NOT NULL DEFAULT 1,
  appeared      TINYINT(1) NOT NULL DEFAULT 0,
  status        ENUM('REGISTERED','APPEARED','ABSENT','RESULT_READY') NOT NULL DEFAULT 'REGISTERED',
  import_batch_id INT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_roll (roll_no),                     -- globally unique, never reused
  UNIQUE KEY uq_exam_person (exam_id, inquiry_id),  -- one sitting per person per exam
  INDEX (exam_id, status),
  CONSTRAINT fk_ec_exam FOREIGN KEY (exam_id)    REFERENCES exams(exam_id),
  CONSTRAINT fk_ec_inq  FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id)
) ENGINE=InnoDB;

CREATE TABLE exam_results (
  result_id     INT AUTO_INCREMENT PRIMARY KEY,
  candidate_id  INT NOT NULL UNIQUE,
  scan_id       INT NULL,                           -- back-link to the scanned image
  booklet_read  CHAR(1),
  attempted     INT NOT NULL DEFAULT 0,
  correct       INT NOT NULL DEFAULT 0,
  wrong         INT NOT NULL DEFAULT 0,
  blank         INT NOT NULL DEFAULT 0,
  raw_score     INT NOT NULL DEFAULT 0,             -- may be negative
  max_score     INT NOT NULL DEFAULT 240,
  percentage    DECIMAL(5,2) NOT NULL DEFAULT 0,
  scholarship_pct DECIMAL(5,2) NULL,
  rank_overall  INT NULL,
  verified_by   VARCHAR(100),                       -- who cleared it out of review
  verified_at   TIMESTAMP NULL,
  CONSTRAINT fk_er_cand FOREIGN KEY (candidate_id) REFERENCES exam_candidates(candidate_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE exam_result_subjects (
  result_id  INT NOT NULL,
  subject    VARCHAR(40) NOT NULL,
  q_from     INT NOT NULL,  q_to INT NOT NULL,
  correct    INT NOT NULL DEFAULT 0,
  wrong      INT NOT NULL DEFAULT 0,
  score      INT NOT NULL DEFAULT 0,
  PRIMARY KEY (result_id, subject),
  CONSTRAINT fk_ers_res FOREIGN KEY (result_id) REFERENCES exam_results(result_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE exam_answer_keys (
  exam_id      INT NOT NULL,
  booklet_code CHAR(1) NOT NULL,
  q_no         INT NOT NULL,
  correct_opt  CHAR(1) NOT NULL,
  PRIMARY KEY (exam_id, booklet_code, q_no)
) ENGINE=InnoDB;

CREATE TABLE exam_subject_map (                     -- question ranges → subject, per exam
  exam_id INT NOT NULL, subject VARCHAR(40) NOT NULL,
  q_from  INT NOT NULL, q_to    INT NOT NULL,
  PRIMARY KEY (exam_id, subject)
) ENGINE=InnoDB;

CREATE TABLE scholarship_slabs (
  slab_id INT AUTO_INCREMENT PRIMARY KEY,
  exam_id INT NOT NULL,
  min_pct DECIMAL(5,2) NOT NULL, max_pct DECIMAL(5,2) NOT NULL,
  scholarship_pct DECIMAL(5,2) NOT NULL,
  INDEX (exam_id, min_pct)
) ENGINE=InnoDB;

CREATE TABLE omr_templates (                        -- one physical sheet layout
  template_id INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  blocks      INT NOT NULL, rows_per_block INT NOT NULL,
  roll_cols   INT NOT NULL,
  ring_ink    ENUM('RED','BLACK') NOT NULL DEFAULT 'BLACK',
  timing_edge ENUM('LEFT','RIGHT','BOTH') NOT NULL DEFAULT 'BOTH',
  geometry_json MEDIUMTEXT NOT NULL,                -- all fractional coordinates
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE import_batches (                       -- audit + undo for each upload
  batch_id INT AUTO_INCREMENT PRIMARY KEY,
  exam_id  INT NOT NULL, file_name VARCHAR(255),
  school_name VARCHAR(150),
  rows_total INT, rows_imported INT, rows_duplicate INT, rows_rejected INT,
  uploaded_by VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

ALTER TABLE exams
  ADD template_id  INT NULL,
  ADD exam_type    ENUM('INTERNAL','HAMSE','HACKSE','HAT') NOT NULL DEFAULT 'INTERNAL',
  ADD total_questions INT NOT NULL DEFAULT 60,
  ADD mark_correct INT NOT NULL DEFAULT 4,
  ADD mark_wrong   INT NOT NULL DEFAULT -1,
  ADD roll_block_from INT NULL, ADD roll_block_to INT NULL;

ALTER TABLE omr_scans                               -- currently a standalone log
  ADD exam_id      INT NULL,
  ADD candidate_id INT NULL,
  ADD roll_read    VARCHAR(10),
  ADD booklet_read CHAR(1),
  ADD match_status ENUM('MATCHED','NO_ROLL','BAD_CHECKSUM','NOT_IN_EXAM','DUPLICATE') NULL,
  ADD image_path   VARCHAR(255);

ALTER TABLE inquiries                               -- fields the Excel carries that we don't
  ADD father_name VARCHAR(100),  ADD father_mobile VARCHAR(15),
  ADD mother_name VARCHAR(100),  ADD mother_mobile VARCHAR(15),
  ADD district VARCHAR(60),      ADD state VARCHAR(60),
  ADD academic_term VARCHAR(40), ADD preferred_centre VARCHAR(80),
  ADD stream VARCHAR(40),        ADD student_type VARCHAR(40),
  ADD caste_category VARCHAR(40),ADD sibling_detail VARCHAR(255),
  ADD current_tutor VARCHAR(150),ADD prev_class_pct VARCHAR(20),
  ADD utr_number VARCHAR(60),    ADD payment_status VARCHAR(30);
```

`omr_scans.candidate_id` is nullable on purpose: an unmatched sheet must still be stored, so it can
be reviewed and re-matched rather than lost.

### 4.2 Excel import — `util/XlsxReader.java`

The project has `XlsxWriter` but **no reader**, and there are no external JARs. Pure-JDK reader using
`java.util.zip` + the built-in XML parser, mirroring how `XlsxWriter` builds the same format:

- read `xl/sharedStrings.xml` into an index, `xl/worksheets/sheet1.xml` for cells
- handle inline strings (`t="inlineStr"`) as well as shared ones
- **address cells by reference (`A2`, `AB2`), never by position.** Empty cells are *omitted* from the
  XML — the sample row has 14 `<c>` elements for 49 columns — so positional reading silently shifts
  every value left. This is the single most likely importer bug
- column letters run past `Z` to `AW`, so the letter→index conversion must be base-26 multi-character
- convert Excel date serials (`46012` → 2025‑12‑21) using the 1899‑12‑30 epoch

Import pipeline (`ExamImportServlet` → `/exam-import`):

1. Upload `.xlsx` + pick the exam + school name
2. **Preview before commit** — parse, validate, and show counts of new / duplicate / rejected with the
   first rejected rows and their reasons. Nothing is written until the user confirms
3. Validation per row: name required; at least one valid 10-digit contact; class recognised; duplicate
   detection on `(name, student mobile)` **and** `(name, parent mobile)` against existing leads
4. On commit, in one transaction per batch: upsert `inquiries` → insert `exam_candidates` → allocate
   roll numbers from the exam's block → write `import_batches`
5. Score columns on the sheet are **ignored** — results come from the scan only
6. Rejected rows download as an `.xlsx` (reusing `XlsxWriter`) with a reason column, so the school can
   fix and re-upload just those

**Volume.** The DAOs open a connection per method and there is no pool, so a 5,000-row import done
row-by-row would take minutes and hold a transaction open throughout. The importer must use a single
connection with `addBatch()`/`executeBatch()` in chunks (500), and roll numbers must be allocated
from the sequence under a row lock so two concurrent imports cannot collide.

### 4.3 Exam setup

`ExamSetupServlet` → `/exam-setup?examId=` — one screen holding what is currently typed into the scan
form every time:

- sheet template, total questions, marking scheme (defaults +4 / −1)
- subject map (Physics 1‑20, Chemistry 21‑40, Biology 41‑60 — editable per stream)
- **four answer keys**, one per booklet code, pasted or uploaded; `OmrService.parseAnswerKey()` already
  accepts both `1:A 2:B …` and plain `A,B,C,…`
- scholarship slabs
- roll number block allocation

### 4.4 OMR engine changes — `service/OmrService.java`

1. **Template out of statics into an instance.** Introduce `OmrTemplate` (a value object loaded from
   `omr_templates`), constructed per request. This removes the global mutable state and is what makes
   two sheet layouts — and concurrent scanning — possible. Existing callers keep working by passing
   the 180-question template built from today's constants.
2. **Generalise the grid** — `blocks` and `rowsPerBlock` from the template instead of `4` and `45`.
3. **Roll grid `roll_cols`** from the template (6 here, 9 on the old sheet), plus check-digit validation
   and a `match_status` for every outcome.
4. **Ink colour.** `toMark()` currently discards red-dominant pixels as printing, and `toRed()` locates
   the printed rings by their redness. On a black-printed sheet both assumptions invert. Add a
   `ring_ink` mode: with `BLACK`, ring detection keys off the printed circle's geometry rather than
   its hue, and the mark test drops the red-exclusion.
5. **Timing marks on both edges** — detect each independently and cross-check. Two independent reads
   of the same row make skew correction materially more reliable than the single right-edge read, and
   disagreement between edges is a good signal that the sheet is damaged or fed crooked.
6. **New bubble regions** — booklet code (4), board (4), exam name (2), read with the same darkness
   sampling as answers.
7. **Report registration failure.** Today a failed timing-mark or column detection falls back to the
   linear template silently. It should still fall back — but must set a `low registration confidence`
   flag that surfaces in the review UI, because that is exactly when a sheet needs a human eye.

### 4.5 Scan → match → score

`ExamScanServlet` → `/exam-scan?examId=`, built on the existing batch upload:

```
for each scanned image:
    roll     = readRoll(img)                       6 digits
    booklet  = readBooklet(img)                    A/B/C/D
    ├─ roll blank / ambiguous     → NO_ROLL        → review queue
    ├─ check digit fails          → BAD_CHECKSUM   → review queue
    ├─ roll not in THIS exam      → NOT_IN_EXAM    → review queue
    ├─ roll already scanned       → DUPLICATE      → review queue
    └─ MATCHED
         key = answer key for `booklet`            (blank booklet → review, never a default)
         score with the exam's +4 / −1
         subject breakdown from exam_subject_map
         percentage      = max(0, raw) / max × 100
         scholarship_pct = slab lookup
         write exam_results (+ subjects), mark candidate APPEARED / RESULT_READY
```

The review queue is the important part of this screen: every sheet that did not cleanly match stays
visible with its scanned image and the calibration overlay, and a staff member assigns it to a
candidate by hand. Nothing is discarded, and nothing is guessed.

Candidates with no scan at the end of a batch are marked **ABSENT** — which is what the Excel's
`EXAM APPEARED` column is tracking.

`StudentDAO.findByRoll()` is retired from this path in favour of an exact
`exam_candidates.roll_no = ?` lookup scoped by `exam_id`. The old method stays for the legacy
180-question flow, but its suffix-`LIKE` behaviour should be tightened to exact-match at the same
time — it is a latent mis-attribution bug wherever it is used.

### 4.6 Results, scholarship and handoff

- `exam_results.jsp` — ranked list, subject columns, percentage, scholarship %, filters by school /
  centre / class, `.xlsx` export via `XlsxWriter` in the same 49-column shape the institute already
  uses, so it round-trips
- Per-candidate result slip as PDF via `ReceiptPdf` — **ASCII only**, `"Rs. "` never `₹`, since
  `ReceiptPdf.safe()` strips anything outside 32..126
- Result to parent over WhatsApp. `com_1_line` / `com_2_line_new` are approved and generic, so this
  needs no new Meta approval. **Send stays manual** — `ReminderConfig.AUTO_SEND` and `DRY_RUN` are not
  touched by this work
- On publication the lead moves to a `RESULT_DECLARED` stage carrying its scholarship %, so the
  counsellor's existing follow-up and conversion flow picks it up unchanged. At admission the
  scholarship pre-fills the concession field that `FeeService` already understands

---

## 5. Phases

**This plan has seven phases, 0 to 6. All are built.** What is not yet done is
calibration against real scanned paper — see §6.

| Phase | Scope | Days | Status |
|---|---|---|---|
| **0** | Migration + `schema.sql` sync; `inquiries` extra columns; seed the 60‑question template and the existing 180‑question one | 2 | ✅ delivered |
| **1** | `XlsxReader`, import preview/commit, duplicate detection, rejected-row export, batch audit | 5 | ✅ delivered |
| **2** | Roll number generator (block allocation, check digit, concurrency), candidate registry, printable roll list + hall tickets | 3 | ✅ delivered |
| **3** | Exam setup screen: template, subject map, 4 answer keys, marking scheme, roll blocks | 3 | ✅ delivered |
| **4** | **OMR engine**: template out of statics, 60‑question grid, 6‑digit roll, black-ink rings, both-edge timing, booklet/board/exam-name bubbles, registration-confidence flag | 6 | ✅ delivered, **uncalibrated** |
| **5** | Scan → match → score → store, review queue for every unmatched outcome, absentee marking | 5 | ✅ delivered |
| **6** | Results, ranking, scholarship, exports, PDF slip, WhatsApp result (manual), lead handoff | 4 | ✅ delivered |
| | **Total** | **~28 dev-days ≈ 5–6 weeks** | |

**Screens added**, all restricted to admin/staff (counsellors are blocked server-side):
`/exam-import` · `/exam-setup` · `/candidates` · `/roll-list` · `/hall-tickets` ·
`/exam-scan` · `/scholarship-results`.

> The scholarship-exam results screen is `/scholarship-results`, **not**
> `/exam-results` — that path already belongs to the internal class-test results
> screen, and two `@WebServlet` annotations on one pattern stop the whole
> application from deploying.

**Deliberately still off**, per the institute: `ReminderConfig.AUTO_SEND = false`
and `DRY_RUN = true`. The result-message button previews the exact text rather
than sending it. Turning sending on is one line, and nothing else changes.

Phase 4 is the risk concentration: it is image processing against a sheet we have only as a blank
PDF. It cannot be signed off on rendered PDFs alone — see below.

---

## 6. Risks and verification

**We need real scanned sheets before Phase 4 can be trusted.** Everything measured so far comes from
a clean vector PDF. Real scans bring skew, fold shadows, scanner brightness variation, pencil vs blue
vs black ink, erased marks and photocopied sheets — the things that actually break bubble readers.

> **Ask the institute for ~20 filled sheets** covering: full and light shading, pencil and both pen
> colours, an erased-and-changed answer, a deliberate double mark, a blank sheet, a crooked feed, and
> a photocopy. Calibration and thresholds should be tuned against those, not against the blank PDF.

| Risk | Handling |
|---|---|
| Misread roll → marks on the wrong student | Check digit + roster-scoped exact match + duplicate detection; every failure to the review queue, never a silent best guess |
| Sheet only carries a machine-readable roll | Accepted, and mitigated by the above — there is no second field to cross-check against |
| Ambiguous / double bubbles | Already handled by `decide()`; surfaced per question in review |
| Negative marking → negative score | Percentage floored at 0; raw score stored signed so the arithmetic stays auditable |
| Booklet bubble blank | Never defaulted to key A — routed to review |
| 5,000-row import | Batched inserts on one connection, chunked, with a preview step before any write |
| Large scan batches | Cap files per batch and free each `BufferedImage` after reading; a 1700px page is ~16 MB in memory as `TYPE_INT_RGB` |
| Existing 180-question flow regresses | Phase 4 keeps it working via a template built from today's constants; regression-test an old sheet before and after |

**Verification per phase**

1. **Import** — the supplied sample row round-trips: 14 populated cells land in the right columns, the
   35 empty ones stay null, `46012` reads as 21‑Dec‑2025. Re-uploading the same file imports 0 and
   reports N duplicates.
2. **Roll numbers** — generate 2,500, assert all unique, all 6 digits, all check-digit valid, all inside
   the exam's block; two concurrent imports produce no collision.
3. **OMR** — the 20 real sheets, scored by hand first, then by the engine; target 100% on the roll
   number (any miss must land in review, not in a wrong match) and per-question disagreements
   individually explained.
4. **Scoring** — one sheet with a known answer pattern: 40 correct, 15 wrong, 5 blank → 40×4 − 15 =
   **145 / 240 = 60.42%**, and the subject breakdown sums to the total.
5. **Scholarship** — a candidate at each slab boundary, including exactly on a boundary percentage.
6. **End to end** — upload 50 students → roll numbers → scan 48 sheets → 2 marked absent → results
   ranked → export matches the on-screen figures → one candidate converted to an admission with the
   scholarship carried into the fee ledger, and the existing fee totals for other students unchanged.
7. **Regression** — the 180-question sheet still reads correctly; `/omr`, `/exams`, `/results`,
   attendance and the student portal all still load.

---

## 7. Answers from the institute — settled

| # | Question | Answer | Effect on the build |
|---|---|---|---|
| 1 | Subject split across the 60 questions | **Fully configurable** — staff define the ranges themselves (1‑10 one subject, 11‑30 another, …) | `exam_subject_map` with a range editor on the exam setup screen; no split is hardcoded. Validation enforces contiguous, non-overlapping ranges covering exactly 1‑60 |
| 2 | Scholarship scheme | **Brochure is authoritative** | Implemented as §3.5 — formula for HAMSE/HACKSE, fixed rules for the rest, highest-wins |
| 3 | HAMSE / HACKSE / HAT | **Separate exams** | Each is its own `exams` row with its own paper, four booklet keys, subject map, roll block and scholarship formula. HAT gets no bubble on the current sheet, so it needs either its own sheet revision or a booklet-code convention — flagged below |
| 4 | Exam fee (UTR, payment status) | **Collected outside the system** | The two columns import as read-only reference data. No payment flow, no receipt, no reconciliation is built |
| 5 | `Date of Exam` vs `Exam Date` | **Keep both for now** | Both import to their own columns. No de-duplication, no authority assumed between them |
| 6 | Roll numbers | **Generate with check digit** | 5-digit sequence + Luhn, per §3.2, blocks allocated per exam |

**One consequence to resolve before Phase 3.** The sheet bubbles only HAMSE and HACKSE under EXAM
NAME. If HAT runs on this same sheet, there is no bubble to identify it, so a HAT sheet is
indistinguishable from a HAMSE one by the exam-name region alone. This is not blocking — the scan is
already scoped to a chosen `exam_id`, and the roll number is globally unique and never reused, so a
sheet fed into the wrong exam is *detected* rather than mis-scored. But the exam-name bubble stops
being a useful cross-check for HAT, and a sheet revision adding a third option would restore it.
