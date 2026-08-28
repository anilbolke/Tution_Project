# OMR Answer-Sheet Scanner — Flow

Free, fully local reading. No paid API, no external service, no extra JARs — pure JDK
image processing (`java.awt` / `ImageIO`). Sheet: Havellsson NEET Samrat, 180 questions
per side, 4 options (A,B,C,D), 4 column blocks (Q1-45 / 46-90 / 91-135 / 136-180).

---

## 1. User flow (staff)

```
Dashboard → OMR  (/omr)
      │
      ▼
Upload sheet scan (JPG/PNG)   or   tick "Use sample sheet"
   + optional Answer Key   (e.g. "1:A 2:C 3:D …"  or  "A,C,D,B,…", '-' to skip)
   + marks per correct (+4) / per wrong (-1)
      │
      ▼
Click "Read Sheet"
      │
      ▼
Results page:
   • Attempted / Blank / Ambiguous   (+ Correct / Wrong / Score if a key was given)
   • 180-question answer grid (color-coded: answered / ambiguous "?" / blank "-")
   • Detection overlay  (green circle = bubble read as filled)
   • Saved to scan history
```

---

## 2. Request flow (components)

```
Browser ──POST /omr (multipart: image + answer key + marks)──▶ OmrScanServlet
                                                                   │
  1. load image   (uploaded Part  OR  webapp/uploads/omr/sample_sheet.jpg) ─▶ BufferedImage
                                                                   │
  2. OmrService.read(img) ─────────────────────────────────────▶ OmrResult
                                                                   │   (answers, blanks, ambiguous)
  3. OmrService.parseAnswerKey(text) + score(+4/-1)   [only if a key was provided]
                                                                   │
  4. OmrService.overlay(img, result) ─▶ writes uploads/omr/overlay_<timestamp>.jpg
                                                                   │
  5. OmrScanDAO.insert(...) ───────────────────────────────────▶ MySQL: omr_scans
                                                                   │
  6. forward ──────────────────────────────────────────────────▶ WEB-INF/views/omr_scan.jsp
                                                                   │
Browser ◀── results page (grid + overlay + history) ──────────────┘
```

Files involved:
- `servlet/OmrScanServlet.java`  — `/omr` (staff only, @MultipartConfig)
- `service/OmrService.java`      — the reader (template, detection, overlay, scoring)
- `model/OmrResult.java`         — one scan's parsed answers + scoring
- `dao/OmrScanDAO.java` + table `omr_scans` — history
- `WEB-INF/views/omr_scan.jsp`   — upload form + results

---

## 3. Detection algorithm (inside OmrService.read)

```
grayscale the image (luminance = 0.299R + 0.587G + 0.114B)
      │
for each of 4 column blocks  (block → Q1-45 / 46-90 / 91-135 / 136-180):
  for each of 45 rows:
      bubble centres from TEMPLATE (fractions of image W/H):
          cx = (AX[block] + option*DX) * W
          cy = (ROW0 + row*ROWDY) * H
      for each option A,B,C,D:
          dark-ratio of a small disk (radius R*W),
          taking the BEST over a tiny SNAP window  ← absorbs minor scan offset
                                                  │
          ┌──────────────── decide(ratio[A..D]) ─┴───────────────┐
          │ baseline = darkest EMPTY sibling                       │
          │ signal    = darkest  − baseline                        │
          │ signalSec = 2nd dark − baseline                        │
          │                                                        │
          │ signal < SIGNAL_MIN ............... → BLANK   (–)       │
          │ signalSec ≥ AMBIG × signal ........ → AMBIGUOUS (?)     │
          │ else .............................. → that option      │
          └────────────────────────────────────────────────────────┘
      → answers[q] = A/B/C/D or null,  confidence[q] = high/medium/blank/ambiguous
```

Key idea: a filled bubble is far darker than its three EMPTY siblings, so the
**relative** comparison cancels the printed red-ring baseline — robust without ML
or any paid vision API.

Template constants (top of `OmrService`, tune to your scanner):
```
AX[4]  = bubble-A centre x for each column block   (fraction of width)
DX     = x spacing A→B→C→D
ROW0   = first row centre y                        (fraction of height)
ROWDY  = row spacing
R      = sample disk radius
SNAPX/SNAPY = local search half-window
SIGNAL_MIN  = darkness a mark must exceed the empty baseline by
AMBIG       = 2nd/1st signal ratio above which a question is ambiguous
DARK        = grey value below which a pixel counts as "dark"
```
Verify/tune by reading the **detection overlay** the scan produces (green = filled).

---

## 4. Scoring flow (optional)

```
answer key (per question)   ×   detected answers
   match    → +markCorrect (default +4)
   mismatch → +markWrong   (default -1)
   blank    →  0
   ────────────────────────────────────────
   Correct, Wrong, Score   (stored in omr_scans)
```

---

## 5. Calibration / one-time setup

```
PDF of scanned sheets ──(embedded JPEGs extracted directly; no poppler/Ghostscript needed)──▶ page images
        │
   pick ONE reference page ──▶ Doc/omr_sample_sheet.jpg  +  webapp/uploads/omr/sample_sheet.jpg
        │
   run scan → overlay → check green circles land on the filled bubbles
        │
   nudge AX / DX / ROW0 / ROWDY / R until aligned   (top, middle, bottom rows)
```
