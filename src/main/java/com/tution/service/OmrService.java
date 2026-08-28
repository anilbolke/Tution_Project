package com.tution.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tution.model.OmrResult;
import com.tution.model.OmrTemplate;
import com.tution.model.SubjectScore;

/**
 * FREE, fully local OMR reader — pure JDK (java.awt / javax.imageio). No paid
 * API, no network, no JARs.
 *
 * How it works: the sheet has a fixed grid of question rows, each with printed
 * option circles. We sample a small disk at each bubble's location and measure
 * how dark it is. For each question the darkest bubble that stands clear of its
 * row's baseline is the answer; nothing standing out means blank; two close
 * together means ambiguous.
 *
 * GEOMETRY IS PER-SHEET DATA, not constants. It used to live in {@code public
 * static} fields here, which meant the app could read exactly one sheet layout
 * and two people scanning different exams at once would corrupt each other's
 * reads. It now arrives as an {@link OmrTemplate}.
 *
 * REGISTRATION. Both page edges carry a ladder of black timing bars at a uniform
 * pitch — 45 of them on the 60-question sheet, not one per answer row. We fit a
 * line through each edge's ladder and read a row's true Y off the fitted rung,
 * interpolating between the two edges across the page so a rotated scan is
 * corrected rather than merely tolerated. When a ladder cannot be found we fall
 * back to the flat template AND raise {@link #isLowRegistration()}, because a
 * silent fallback is exactly the case a human needs to look at.
 */
public class OmrService {

    /* ─── detection thresholds (independent of sheet layout) ─── */

    /** A mark is "filled" when its darkness exceeds the row's empty baseline by this. */
    public static double SIGNAL_MIN = 0.14;
    /** If the 2nd-strongest mark reaches AMBIG x the strongest signal, it's ambiguous. */
    public static double AMBIG = 0.62;
    /** Grey value below which a pixel is "dark" (0=black..255=white). */
    public static int DARK = 150;
    /** Winner must ALSO be this dark in absolute terms — rejects all-blank rows. */
    public static double FILL_ABS_MIN = 0.14;
    /** A pixel is ignored as a mark when it is RED-dominant. Only meaningful on a
     *  sheet whose rings are printed red; a black-printed sheet cannot colour-
     *  discriminate and relies on the sampling disk sitting inside the ring. */
    public static int RED_MARGIN = 35;
    /** Roll digit: minimum fill for a digit to count, and the ambiguity guard. */
    public static double ROLL_FILL_MIN = 0.22;
    public static double ROLL_AMBIG = 0.70;

    /* ─── timing ladder search ─── */
    private static final double LEFT_X0 = 0.010, LEFT_X1 = 0.062;
    private static final double RIGHT_X0 = 0.940, RIGHT_X1 = 0.992;
    private static final int TIMING_MIN_H = 6, TIMING_MAX_H = 60;

    private static final String[] OPT = { "A", "B", "C", "D" };

    private final OmrTemplate tpl;
    private boolean lowRegistration;

    /** Legacy behaviour: the 180-question sheet this class was originally written for. */
    public OmrService() { this(OmrTemplate.legacy180()); }

    public OmrService(OmrTemplate template) {
        this.tpl = template == null ? OmrTemplate.legacy180() : template;
    }

    public OmrTemplate template() { return tpl; }

    /**
     * True when the last read could not lock onto the timing ladder or the
     * printed columns and fell back to the flat template. The read still
     * produced answers; they just deserve a human glance.
     */
    public boolean isLowRegistration() { return lowRegistration; }

    /* ─── answers ─── */

    /** Reads every answer on the sheet. */
    public OmrResult read(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        boolean[][] mark = toMark(img);
        Ladder ladder = registration(img, W, H);
        int rad = (int) Math.round(tpl.getR() * W);
        boolean[][] ring = toRing(img);
        int[] colShift = colShifts(ring, ladder, W, H, (int) Math.round(tpl.getRingR() * W));

        OmrResult r = new OmrResult();
        r.setPage(1);
        r.setStartQ(1);
        r.setEndQ(tpl.totalQuestions());

        int sy = (int) Math.round(tpl.getSnapY() * H);
        for (int block = 0; block < tpl.getBlocks(); block++) {
            for (int row = 0; row < tpl.getRowsPerBlock(); row++) {
                int q = block * tpl.getRowsPerBlock() + row + 1;
                double[] ratio = new double[4];
                for (int opt = 0; opt < 4; opt++) {
                    double fx = tpl.getAx()[block] + opt * tpl.getDx();
                    int cx = (int) Math.round(fx * W) + colShift[block];
                    int cy = rowCenterY(ladder, row, fx, H);
                    int[] ex = snapExtents(opt, block, W);
                    ratio[opt] = maxDarkRatio(mark, W, H, cx, cy, rad, ex[0], ex[1], sy);
                }
                decide(r, q, ratio);
            }
        }
        for (Map.Entry<Integer, String> e : r.getConfidence().entrySet()) {
            if ("ambiguous".equals(e.getValue())) r.getAmbiguous().add(e.getKey());
            else if (r.getAnswers().get(e.getKey()) == null) r.getBlanks().add(e.getKey());
        }
        return r;
    }

    private void decide(OmrResult r, int q, double[] ratio) {
        int best = 0, second = -1;
        double baseline = ratio[0];
        for (int i = 1; i < 4; i++) {
            if (ratio[i] > ratio[best]) best = i;
            if (ratio[i] < baseline) baseline = ratio[i];
        }
        for (int i = 0; i < 4; i++) if (i != best && (second < 0 || ratio[i] > ratio[second])) second = i;

        double sig = ratio[best] - baseline;
        double sigSec = ratio[second] - baseline;

        if (sig < SIGNAL_MIN || ratio[best] < FILL_ABS_MIN) {
            r.getAnswers().put(q, null);
            r.getConfidence().put(q, "blank");
        } else if (sigSec >= AMBIG * sig) {
            r.getAnswers().put(q, null);
            r.getConfidence().put(q, "ambiguous");
        } else {
            r.getAnswers().put(q, OPT[best]);
            r.getConfidence().put(q, sig > 0.32 ? "high" : "medium");
        }
    }

    /* ─── roll number ─── */

    /**
     * Reads the bubbled roll number. Each column takes the darkest of its ten
     * digits; a blank or ambiguous column contributes nothing, so a partial read
     * comes back SHORT rather than wrong — and a short roll fails the length
     * check upstream instead of quietly matching some other candidate.
     */
    public String readRoll(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        boolean[][] mark = toMark(img);
        int rad = (int) Math.round(tpl.getRollR() * W);
        int sx = (int) Math.round(tpl.getSnapX() * W), sy = (int) Math.round(tpl.getSnapY() * H);
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < tpl.getRollCols(); col++) {
            int cx = (int) Math.round((tpl.getRollCol0() + col * tpl.getRollColDx()) * W);
            int bestD = -1;
            double bestV = 0, second = 0;
            for (int d = 0; d < 10; d++) {
                int cy = (int) Math.round((tpl.getRollRow0() + d * tpl.getRollRowDy()) * H);
                double v = maxDarkRatio(mark, W, H, cx, cy, rad, sx, sx, sy);
                if (v > bestV) { second = bestV; bestV = v; bestD = d; }
                else if (v > second) second = v;
            }
            if (bestV >= ROLL_FILL_MIN && second < ROLL_AMBIG * bestV) sb.append((char) ('0' + bestD));
        }
        return sb.toString();
    }

    /* ─── small bubble groups: booklet code, board, exam name ─── */

    /**
     * Reads one of the sheet's option groups, e.g. {@code "booklet"}.
     * Returns null when nothing is filled or two options tie — the caller must
     * send that sheet for review rather than assume a default, because guessing
     * the booklet scores the paper against the wrong answer key.
     */
    public String readRegion(BufferedImage img, String key) {
        OmrTemplate.Region reg = tpl.region(key);
        if (reg == null || reg.options.isEmpty()) return null;
        int W = img.getWidth(), H = img.getHeight();
        boolean[][] mark = toMark(img);
        int rad = (int) Math.round(tpl.getRollR() * W);
        int sx = (int) Math.round(tpl.getSnapX() * W), sy = (int) Math.round(tpl.getSnapY() * H);

        int cx = (int) Math.round(reg.x * W);
        double[] v = new double[reg.options.size()];
        for (int i = 0; i < v.length; i++) {
            int cy = (int) Math.round((reg.y0 + i * reg.dy) * H);
            v[i] = maxDarkRatio(mark, W, H, cx, cy, rad, sx, sx, sy);
        }
        int best = 0;
        double second = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[best]) best = i;
        for (int i = 0; i < v.length; i++) if (i != best && v[i] > second) second = v[i];
        if (v[best] < ROLL_FILL_MIN || second >= ROLL_AMBIG * v[best]) return null;
        return reg.options.get(best);
    }

    /* ─── registration: the timing ladders ─── */

    /** A straight-line fit through one edge's timing bars: y = a + b*k. */
    private static class Fit {
        double a, b;
        int count;
        boolean ok() { return count >= 6 && b > 0; }
        double y(double k) { return a + b * k; }
        double k(double y) { return (y - a) / b; }
    }

    /** Both edges' fits, or whatever could be found. */
    private static class Ladder {
        Fit left, right;
        boolean any() { return (left != null && left.ok()) || (right != null && right.ok()); }
    }

    private Ladder registration(BufferedImage img, int W, int H) {
        lowRegistration = false;
        Ladder l = new Ladder();
        if (tpl.timingLeft())  l.left  = fitEdge(img, W, H, LEFT_X0, LEFT_X1);
        if (tpl.timingRight()) l.right = fitEdge(img, W, H, RIGHT_X0, RIGHT_X1);

        if (!l.any()) {
            lowRegistration = true;               // nothing to lock onto; flat template
            return l;
        }
        // Both edges present: they should agree on pitch. A real disagreement
        // means a torn, folded or badly skewed sheet - still readable, but a
        // human should see it.
        if (l.left != null && l.left.ok() && l.right != null && l.right.ok()) {
            double rel = Math.abs(l.left.b - l.right.b) / Math.max(l.left.b, l.right.b);
            if (rel > 0.02) lowRegistration = true;
        } else if (tpl.timingLeft() && tpl.timingRight()) {
            lowRegistration = true;               // one edge missing on a both-edge sheet
        }
        return l;
    }

    /** Finds the timing strip in a margin and least-squares fits its bar centres. */
    private Fit fitEdge(BufferedImage img, int W, int H, double x0f, double x1f) {
        int x0 = (int) Math.round(x0f * W), x1 = Math.min(W - 1, (int) Math.round(x1f * W));
        if (x1 <= x0) return null;
        int bestX = -1, bestCnt = -1;
        for (int x = x0; x <= x1; x++) {
            int c = 0;
            for (int y = 0; y < H; y++) if (isBlack(img.getRGB(x, y))) c++;
            if (c > bestCnt) { bestCnt = c; bestX = x; }
        }
        if (bestX < 0 || bestCnt < 20) return null;

        int half = Math.max(3, (int) Math.round(0.004 * W));
        int xa = Math.max(0, bestX - half), xb = Math.min(W - 1, bestX + half);
        List<Double> centres = new ArrayList<>();
        int s = -1;
        for (int y = 0; y < H; y++) {
            int c = 0, t = 0;
            for (int x = xa; x <= xb; x++) { t++; if (isBlack(img.getRGB(x, y))) c++; }
            boolean dark = t > 0 && (double) c / t >= 0.5;
            if (dark) { if (s < 0) s = y; }
            else if (s >= 0) { addBand(centres, s, y - 1); s = -1; }
        }
        if (s >= 0) addBand(centres, s, H - 1);
        if (centres.size() < 6) return null;

        // Least squares over (index, y). The bars are evenly pitched, so the fit
        // recovers both the true pitch (scan scaling) and the phase (offset).
        int n = centres.size();
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += i; sy += centres.get(i); sxx += (double) i * i; sxy += i * centres.get(i);
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) return null;
        Fit f = new Fit();
        f.b = (n * sxy - sx * sy) / denom;
        f.a = (sy - f.b * sx) / n;
        f.count = n;
        return f.ok() ? f : null;
    }

    private void addBand(List<Double> centres, int a, int b) {
        int h = b - a + 1;
        if (h >= TIMING_MIN_H && h <= TIMING_MAX_H) centres.add((a + b) / 2.0);
    }

    /**
     * True Y of an answer row at horizontal position {@code fx} (fraction of
     * width). The template gives the nominal Y; we snap it to the nearest ladder
     * rung, then interpolate between the two edges so a rotated page is
     * straightened rather than merely tolerated.
     */
    private int rowCenterY(Ladder l, int row, double fx, int H) {
        double nominal = (tpl.getRow0() + row * tpl.getRowDy()) * H;
        if (l == null || !l.any()) return (int) Math.round(nominal);

        Double yl = null, yr = null;
        if (l.left  != null && l.left.ok())  yl = l.left.y(Math.round(l.left.k(nominal)));
        if (l.right != null && l.right.ok()) yr = l.right.y(Math.round(l.right.k(nominal)));

        if (yl != null && yr != null) return (int) Math.round(yl + fx * (yr - yl));
        return (int) Math.round(yl != null ? yl : yr);
    }

    /* ─── X registration: line each block up with its printed circles ─── */

    private int[] colShifts(boolean[][] ring, Ladder l, int W, int H, int ringR) {
        int[] sh = new int[tpl.getBlocks()];
        int found = 0;
        for (int b = 0; b < tpl.getBlocks(); b++) {
            sh[b] = detectColShift(ring, l, W, H, b, ringR);
            if (sh[b] != Integer.MIN_VALUE) found++;
            else sh[b] = 0;
        }
        if (found < tpl.getBlocks()) lowRegistration = true;
        return sh;
    }

    /** Best horizontal shift so a block's option columns sit on the printed circles. */
    private int detectColShift(boolean[][] ring, Ladder l, int W, int H, int block, int ringR) {
        int span = Math.max(10, (int) Math.round(tpl.getDx() * W * 0.6));
        int best = -1, bestDx = 0;
        for (int dx = -span; dx <= span; dx += 2) {
            int s = 0;
            for (int row = 0; row < tpl.getRowsPerBlock(); row += Math.max(1, tpl.getRowsPerBlock() / 8)) {
                for (int opt = 0; opt < 4; opt++) {
                    double fx = tpl.getAx()[block] + opt * tpl.getDx();
                    int cx = (int) Math.round(fx * W) + dx;
                    int cy = rowCenterY(l, row, fx, H);
                    if (ringScore(ring, W, H, cx, cy, ringR) >= 13) s++;
                }
            }
            if (s > best) { best = s; bestDx = dx; }
        }
        return best >= 8 ? bestDx : Integer.MIN_VALUE;
    }

    /* ─── pixel helpers ─── */

    private boolean isBlack(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return r < 110 && g < 110 && b < 110;
    }

    /**
     * Pixels that are a genuine pen or pencil fill.
     *
     * On a RED-printed sheet the form's own rings and rules are red, so they are
     * excluded by hue and blank bubbles read as empty. On a BLACK-printed sheet
     * no such discrimination is possible; there the sampling disk is smaller than
     * the printed ring and sits inside it, and {@link #decide} compares each
     * option against its own row's baseline, so ring ink that clips all four
     * options equally cancels out instead of inventing an answer.
     */
    private boolean[][] toMark(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        boolean excludeRed = !tpl.isBlackInk();
        int leftGuard  = (int) Math.round(guardLeft() * W);
        int rightGuard = (int) Math.round(guardRight() * W);
        boolean[][] m = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x < leftGuard || x >= rightGuard) continue;
                int rgb = img.getRGB(x, y);
                int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
                int lum = (rr * 299 + gg * 587 + bb * 114) / 1000;
                if (excludeRed && rr > gg + RED_MARGIN && rr > bb + RED_MARGIN) continue;
                m[y][x] = lum < DARK;
            }
        }
        return m;
    }

    /**
     * Margins that must never be sampled as ink — they hold the timing ladders,
     * whose solid black bars would otherwise read as enormous marks.
     *
     * Derived from the template rather than fixed, because the two sheets put
     * their content in different places: the 180-question sheet's last option
     * column sits at x=0.951, well inside what would be a sensible fixed guard
     * for the 60-question sheet, and a fixed guard would blank that whole column.
     * The bounds therefore sit just outside the outermost thing we actually read.
     */
    private double guardLeft() {
        double leftmost = tpl.getRollCol0();
        for (double a : tpl.getAx()) leftmost = Math.min(leftmost, a);
        for (OmrTemplate.Region r : tpl.regions().values()) leftmost = Math.min(leftmost, r.x);
        return Math.max(0, leftmost - 2 * tpl.getRingR());
    }

    private double guardRight() {
        double rightmost = 0;
        for (double a : tpl.getAx()) rightmost = Math.max(rightmost, a + 3 * tpl.getDx());
        rightmost = Math.max(rightmost, tpl.getRollCol0() + (tpl.getRollCols() - 1) * tpl.getRollColDx());
        for (OmrTemplate.Region r : tpl.regions().values()) rightmost = Math.max(rightmost, r.x);
        // leave room for the D-bubble's outward snap search
        return Math.min(1.0, rightmost + tpl.getSnapXEdge() + 2 * tpl.getRingR());
    }

    /** The printed circle outlines, by whichever ink this sheet uses. */
    private boolean[][] toRing(BufferedImage img) {
        int W = img.getWidth(), H = img.getHeight();
        boolean black = tpl.isBlackInk();
        boolean[][] m = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = img.getRGB(x, y);
                int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
                m[y][x] = black
                        ? ((rr * 299 + gg * 587 + bb * 114) / 1000) < DARK
                        : (rr > 90 && rr - gg > 35 && rr - bb > 35);
            }
        }
        return m;
    }

    private double maxDarkRatio(boolean[][] mark, int W, int H, int cx, int cy, int rad,
                                int sxL, int sxR, int sy) {
        double best = 0;
        int step = Math.max(2, rad / 3);
        for (int oy = -sy; oy <= sy; oy += step) {
            for (int ox = -sxL; ox <= sxR; ox += step) {
                double v = darkRatio(mark, W, H, cx + ox, cy + oy, rad);
                if (v > best) best = v;
            }
        }
        return best;
    }

    /**
     * Outward search extents. Only the D bubble of a non-final block reaches
     * further right, into the empty gutter, to absorb an off-grid last column;
     * everything else stays tight so a search never wanders into its neighbour.
     */
    private int[] snapExtents(int opt, int block, int W) {
        int sx = (int) Math.round(tpl.getSnapX() * W);
        int edge = (int) Math.round(tpl.getSnapXEdge() * W);
        int sxR = (opt == 3 && block < tpl.getBlocks() - 1) ? edge : sx;
        return new int[] { sx, sxR };
    }

    private int ringScore(boolean[][] ring, int W, int H, int cx, int cy, int ringR) {
        int hit = 0;
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12.0, ca = Math.cos(a), sa = Math.sin(a);
            boolean any = false;
            for (int dr = -3; dr <= 3 && !any; dr++) {
                int x = cx + (int) Math.round((ringR + dr) * ca);
                int y = cy + (int) Math.round((ringR + dr) * sa);
                if (x >= 0 && y >= 0 && x < W && y < H && ring[y][x]) any = true;
            }
            if (any) hit++;
        }
        return hit;
    }

    private double darkRatio(boolean[][] mark, int W, int H, int cx, int cy, int rad) {
        int dark = 0, total = 0, r2 = rad * rad;
        for (int dy = -rad; dy <= rad; dy++) {
            int y = cy + dy;
            if (y < 0 || y >= H) continue;
            for (int dx = -rad; dx <= rad; dx++) {
                if (dx * dx + dy * dy > r2) continue;
                int x = cx + dx;
                if (x < 0 || x >= W) continue;
                total++;
                if (mark[y][x]) dark++;
            }
        }
        return total == 0 ? 0 : (double) dark / total;
    }

    /* ─── calibration overlay ─── */

    /** Draws where every bubble was sampled, so a human can check the fit. */
    public BufferedImage overlay(BufferedImage src, OmrResult r) {
        int W = src.getWidth(), H = src.getHeight();
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setStroke(new BasicStroke(Math.max(2, W / 500)));
        int ringR = (int) Math.round(tpl.getRingR() * W);
        Ladder l = registration(src, W, H);
        int[] colShift = colShifts(toRing(src), l, W, H, ringR);

        for (int block = 0; block < tpl.getBlocks(); block++) {
            for (int row = 0; row < tpl.getRowsPerBlock(); row++) {
                int q = block * tpl.getRowsPerBlock() + row + 1;
                String ans = r == null ? null : r.getAnswers().get(q);
                String conf = r == null ? null : r.getConfidence().get(q);
                for (int opt = 0; opt < 4; opt++) {
                    double fx = tpl.getAx()[block] + opt * tpl.getDx();
                    int cx = (int) Math.round(fx * W) + colShift[block];
                    int cy = rowCenterY(l, row, fx, H);
                    if (ans != null && OPT[opt].equals(ans))      g.setColor(Color.GREEN);
                    else if ("ambiguous".equals(conf))            g.setColor(Color.ORANGE);
                    else                                          g.setColor(new Color(0, 120, 255));
                    g.drawOval(cx - ringR, cy - ringR, ringR * 2, ringR * 2);
                }
            }
        }
        // roll grid
        g.setColor(Color.MAGENTA);
        int rr = (int) Math.round(tpl.getRollR() * W);
        for (int col = 0; col < tpl.getRollCols(); col++) {
            int cx = (int) Math.round((tpl.getRollCol0() + col * tpl.getRollColDx()) * W);
            for (int d = 0; d < 10; d++) {
                int cy = (int) Math.round((tpl.getRollRow0() + d * tpl.getRollRowDy()) * H);
                g.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
            }
        }
        // option groups
        g.setColor(new Color(200, 0, 200));
        for (OmrTemplate.Region reg : tpl.regions().values()) {
            int cx = (int) Math.round(reg.x * W);
            for (int i = 0; i < reg.options.size(); i++) {
                int cy = (int) Math.round((reg.y0 + i * reg.dy) * H);
                g.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
            }
        }
        g.dispose();
        return out;
    }

    /* ─── scoring (unchanged behaviour) ─── */

    public void score(OmrResult r, Map<Integer, String> key, int markCorrect, int markWrong) {
        if (key == null || key.isEmpty()) return;
        int correct = 0, wrong = 0, score = 0;
        for (Map.Entry<Integer, String> k : key.entrySet()) {
            r.getCorrectKey().put(k.getKey(), k.getValue());
            String ans = r.getAnswers().get(k.getKey());
            if (ans == null) continue;
            if (ans.equalsIgnoreCase(k.getValue())) { correct++; score += markCorrect; }
            else { wrong++; score += markWrong; }
        }
        r.setCorrect(correct);
        r.setWrong(wrong);
        r.setScore(score);
        r.setScored(true);
    }

    public void subjectBreakdown(OmrResult r, Map<Integer, String> key, int markCorrect, int markWrong,
                                 List<SubjectScore> ranges) {
        r.getSubjects().clear();
        for (SubjectScore in : ranges) {
            if (in == null || in.from <= 0 || in.to < in.from) continue;
            SubjectScore s = new SubjectScore(in.name, in.from, in.to);
            for (int q = s.from; q <= s.to; q++) {
                String ans = r.getAnswers().get(q);
                if (ans != null) s.attempted++;
                if (key != null && key.containsKey(q)) {
                    s.scored = true;
                    if (ans == null) continue;
                    if (ans.equalsIgnoreCase(key.get(q))) { s.correct++; s.score += markCorrect; }
                    else { s.wrong++; s.score += markWrong; }
                }
            }
            r.getSubjects().add(s);
        }
    }

    /** Accepts "1:A 2:B …" or a plain "A,B,C,…" sequence (use '-' to skip a question). */
    public Map<Integer, String> parseAnswerKey(String raw) {
        Map<Integer, String> key = new LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return key;
        String[] tokens = raw.trim().split("[\\s,;]+");
        int seq = 1;
        Pattern pair = Pattern.compile("^(\\d+)[:.)\\-]?([ABCDabcd])$");
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            Matcher pm = pair.matcher(t);
            if (pm.matches()) key.put(Integer.parseInt(pm.group(1)), pm.group(2).toUpperCase());
            else if (t.length() == 1 && "ABCDabcd".contains(t)) key.put(seq++, t.toUpperCase());
            else seq++;
        }
        return key;
    }
}
