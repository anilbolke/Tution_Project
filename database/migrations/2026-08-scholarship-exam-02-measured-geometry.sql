-- ============================================================
--  Scholarship Exam module - Phase 4
--  Real measured geometry for the 60-question HOSE answer sheet.
--
--  The geometry seeded in migration 01 was a placeholder copied from the
--  180-question sheet. It was wrong: the two sheets share almost nothing.
--  Measured off a 1700x2402 render of the blank PDF by detecting the printed
--  rings themselves - 240 answer bubbles (15 rows x 16 columns) and 60 roll
--  bubbles (10 rows x 6 columns) found exactly, with no strays.
--
--      answer grid   AX  0.15059 0.35824 0.56647 0.77588   (was 0.276 ... 0.861)
--                    DX  0.03819                            (was 0.0300)
--                    ROW0  0.57088  ROWDY 0.01845           (was 0.1600 / 0.01828)
--      roll grid     COL0 0.11882  COLDX 0.02788
--                    ROW0 0.29184  ROWDY 0.01855
--      printed ring radius 0.01000 of width (34 px diameter at 1700 wide)
--
--  Still "calibrated": false - these come from clean vector artwork, not from
--  scanned paper. Thresholds must be tuned against real filled sheets before
--  this is trusted on a live exam.
--
--  RUN ONCE (it is an UPDATE, so re-running is harmless).
-- ============================================================

UPDATE omr_templates
   SET geometry_json = CONCAT(
        '{"AX":[0.15059,0.35824,0.56647,0.77588],"DX":0.03819,',
        '"ROW0":0.57088,"ROWDY":0.01845,',
        '"R":0.0060,"RING_R":0.01000,"SNAPX":0.0055,"SNAPY":0.0060,"SNAPX_EDGE":0.012,',
        '"ROLL_COL0":0.11882,"ROLL_COLDX":0.02788,',
        '"ROLL_ROW0":0.29184,"ROLL_ROWDY":0.01855,"ROLL_R":0.0055,',
        '"regions":{',
          '"booklet":{"x":0.55353,"y0":0.32931,"dy":0.01860,"opts":["A","B","C","D"]},',
          '"board":{"x":0.40137,"y0":0.32931,"dy":0.01860,"opts":["ICSE","CBSE","STATE","OTHER"]},',
          '"exam_name":{"x":0.40137,"y0":0.44047,"dy":0.01831,"opts":["HAMSE","HACKSE"]}',
        '},"calibrated":false}')
 WHERE roll_cols = 6;
