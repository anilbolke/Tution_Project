package com.tution.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The geometry of one physical OMR sheet, as fractions of the image width and
 * height so it scales with whatever resolution the sheet was scanned at.
 *
 * This used to be a block of {@code public static} fields on OmrService, which
 * meant the application could only ever read ONE sheet layout, and two staff
 * scanning different exams at the same time shared — and corrupted — the same
 * mutable geometry. It is now per-exam data, loaded from omr_templates.
 */
public class OmrTemplate {

    /** Template built from the values OmrService used to carry as static fields. */
    public static final String LEGACY_180Q = "Havellsson 180Q (legacy)";

    private int    templateId;
    private String name = "";
    private int    blocks = 4;
    private int    rowsPerBlock = 15;
    private int    rollCols = 6;
    private String ringInk = "BLACK";      // RED | BLACK — colour of the PRINTED circle
    private String timingEdge = "BOTH";    // LEFT | RIGHT | BOTH
    private boolean calibrated;

    // ── answer grid ──
    private double[] ax = { 0.15059, 0.35824, 0.56647, 0.77588 };
    private double dx = 0.03819;
    private double row0 = 0.57088;
    private double rowDy = 0.01845;
    private double r = 0.0070;             // sampling radius
    private double ringR = 0.01000;        // printed circle radius
    private double snapX = 0.0055;
    private double snapY = 0.0065;
    private double snapXEdge = 0.026;

    // ── roll-number grid ──
    private double rollCol0 = 0.11882;
    private double rollColDx = 0.02788;
    private double rollRow0 = 0.29184;
    private double rollRowDy = 0.01855;
    private double rollR = 0.0050;

    /** A small bubble group read off the sheet: booklet code, board, exam name. */
    public static class Region {
        public String key;            // booklet / board / exam_name
        public List<String> options = new ArrayList<>();
        public double x;              // column centre (fraction of width)
        public double y0;             // first option's centre (fraction of height)
        public double dy;             // spacing between options
        public Region() { }
        public Region(String key, double x, double y0, double dy, String... opts) {
            this.key = key; this.x = x; this.y0 = y0; this.dy = dy;
            for (String o : opts) options.add(o);
        }
    }
    private final Map<String, Region> regions = new LinkedHashMap<>();

    public int totalQuestions() { return blocks * rowsPerBlock; }

    public boolean isBlackInk() { return !"RED".equalsIgnoreCase(ringInk); }
    public boolean timingLeft()  { return "LEFT".equals(timingEdge)  || "BOTH".equals(timingEdge); }
    public boolean timingRight() { return "RIGHT".equals(timingEdge) || "BOTH".equals(timingEdge); }

    public Region region(String key) { return regions.get(key); }
    public Map<String, Region> regions() { return regions; }
    public void addRegion(Region r) { if (r != null && r.key != null) regions.put(r.key, r); }

    /**
     * The template OmrService used before geometry became data — the 180-question
     * sheet with a 9-digit roll grid, red printed rings and timing marks only on
     * the right edge. Kept so the existing scan flow behaves exactly as it did.
     */
    public static OmrTemplate legacy180() {
        OmrTemplate t = new OmrTemplate();
        t.name = LEGACY_180Q;
        t.blocks = 4;
        t.rowsPerBlock = 45;
        t.rollCols = 9;
        t.ringInk = "RED";
        t.timingEdge = "RIGHT";
        t.calibrated = true;
        t.ax = new double[] { 0.276, 0.471, 0.666, 0.861 };
        t.dx = 0.0300;
        t.row0 = 0.1600;
        t.rowDy = 0.01828;
        t.r = 0.0070;
        t.ringR = 0.0075;
        t.snapX = 0.0055;
        t.snapY = 0.0065;
        t.snapXEdge = 0.026;
        t.rollCol0 = 0.0520;
        t.rollColDx = 0.0182;
        t.rollRow0 = 0.2174;
        t.rollRowDy = 0.0184;
        t.rollR = 0.0050;
        return t;
    }

    /**
     * Reads the stored geometry JSON. Deliberately a small hand-rolled parser:
     * the project carries no JSON library and the document is a flat map of
     * numbers, arrays of numbers, and the regions block.
     *
     * Anything absent keeps its default, so a partial document degrades to the
     * built-in geometry rather than to zeros — a zeroed coordinate would sample
     * the corner of the page and read every bubble as blank.
     */
    public void applyGeometry(String json) {
        if (json == null || json.isEmpty()) return;

        ax        = numArray(json, "AX", ax);
        dx        = num(json, "DX", dx);
        row0      = num(json, "ROW0", row0);
        rowDy     = num(json, "ROWDY", rowDy);
        r         = num(json, "R", r);
        ringR     = num(json, "RING_R", ringR);
        snapX     = num(json, "SNAPX", snapX);
        snapY     = num(json, "SNAPY", snapY);
        snapXEdge = num(json, "SNAPX_EDGE", snapXEdge);
        rollCol0  = num(json, "ROLL_COL0", rollCol0);
        rollColDx = num(json, "ROLL_COLDX", rollColDx);
        rollRow0  = num(json, "ROLL_ROW0", rollRow0);
        rollRowDy = num(json, "ROLL_ROWDY", rollRowDy);
        rollR     = num(json, "ROLL_R", rollR);
        calibrated = json.contains("\"calibrated\":true") || json.contains("\"calibrated\": true");

        for (String key : new String[] { "booklet", "board", "exam_name" }) {
            Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{(.*?)\\}\\s*(?=,\\s*\"|\\})",
                                        Pattern.DOTALL).matcher(json);
            if (!m.find()) continue;
            String body = m.group(1);
            Region reg = new Region();
            reg.key = key;
            reg.x  = num(body, "x", 0);
            reg.y0 = num(body, "y0", 0);
            reg.dy = num(body, "dy", 0);
            Matcher om = Pattern.compile("\"opts\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(body);
            if (om.find()) {
                for (String piece : om.group(1).split(",")) {
                    String v = piece.trim().replaceAll("^\"|\"$", "");
                    if (!v.isEmpty()) reg.options.add(v);
                }
            }
            // A region with no coordinates cannot be sampled; skip it rather than
            // reading the top-left corner of the page and inventing an answer.
            if (reg.x > 0 && reg.y0 > 0 && reg.dy > 0 && !reg.options.isEmpty()) addRegion(reg);
        }
    }

    private static double num(String json, String key, double dflt) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : dflt;
    }

    private static double[] numArray(String json, String key, double[] dflt) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL)
                           .matcher(json);
        if (!m.find()) return dflt;
        String[] parts = m.group(1).split(",");
        List<Double> vals = new ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (v.isEmpty()) continue;
            try { vals.add(Double.parseDouble(v)); } catch (NumberFormatException ignore) { }
        }
        if (vals.isEmpty()) return dflt;
        double[] out = new double[vals.size()];
        for (int i = 0; i < out.length; i++) out[i] = vals.get(i);
        return out;
    }

    /* ─── accessors ─── */

    public int    getTemplateId()          { return templateId; }
    public void   setTemplateId(int v)     { this.templateId = v; }
    public String getName()                { return name; }
    public void   setName(String v)        { this.name = v; }
    public int    getBlocks()              { return blocks; }
    public void   setBlocks(int v)         { this.blocks = v; }
    public int    getRowsPerBlock()        { return rowsPerBlock; }
    public void   setRowsPerBlock(int v)   { this.rowsPerBlock = v; }
    public int    getRollCols()            { return rollCols; }
    public void   setRollCols(int v)       { this.rollCols = v; }
    public String getRingInk()             { return ringInk; }
    public void   setRingInk(String v)     { this.ringInk = v; }
    public String getTimingEdge()          { return timingEdge; }
    public void   setTimingEdge(String v)  { this.timingEdge = v; }
    public boolean isCalibrated()          { return calibrated; }
    public void   setCalibrated(boolean v) { this.calibrated = v; }

    public double[] getAx()   { return ax; }
    public double getDx()     { return dx; }
    public double getRow0()   { return row0; }
    public double getRowDy()  { return rowDy; }
    public double getR()      { return r; }
    public double getRingR()  { return ringR; }
    public double getSnapX()  { return snapX; }
    public double getSnapY()  { return snapY; }
    public double getSnapXEdge() { return snapXEdge; }

    public double getRollCol0()  { return rollCol0; }
    public double getRollColDx() { return rollColDx; }
    public double getRollRow0()  { return rollRow0; }
    public double getRollRowDy() { return rollRowDy; }
    public double getRollR()     { return rollR; }
}
