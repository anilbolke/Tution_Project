package com.tution.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal single-page A4 PDF generator — pure JDK, no external JARs (matches the
 * project's "no extra libraries" rule). Good enough for a fee receipt: a title plus
 * label/value lines in Helvetica. ASCII only (callers should pass "Rs." not the ₹ glyph).
 */
public final class ReceiptPdf {

    private ReceiptPdf() { }

    /** Builds the receipt PDF for one payment and returns the raw bytes. */
    public static byte[] receipt(String brand, String subtitle, String receiptNo,
                                 String[][] rows, String footer) {
        return document(brand, subtitle, "Fee Payment Receipt", "No: " + receiptNo, rows, footer);
    }

    /**
     * A single-page label/value document: brand, a heading, an optional reference
     * in the top right, then rows of label and value.
     *
     * Receipts and exam result slips are the same shape, so they share this
     * rather than carrying two PDF writers. Row spacing tightens automatically
     * once there are enough rows to run off the page — a result slip lists every
     * subject, which a fixed 30pt step could not fit.
     *
     * ASCII ONLY: {@link #safe(String)} strips anything outside 32..126, because
     * the built-in Helvetica encoding here cannot render it. Pass "Rs. ", never
     * the rupee sign.
     */
    public static byte[] document(String brand, String subtitle, String heading,
                                  String reference, String[][] rows, String footer) {
        StringBuilder c = new StringBuilder();
        c.append(text(50, 800, 20, brand));
        if (subtitle != null && !subtitle.isEmpty()) c.append(text(50, 778, 11, subtitle));
        c.append(line(50, 766, 545, 766));
        if (heading != null && !heading.isEmpty()) c.append(text(50, 740, 15, heading));
        if (reference != null && !reference.isEmpty()) c.append(text(360, 740, 12, reference));

        int top = 706, bottom = 90;
        int n = rows == null ? 0 : rows.length;
        int step = 30;
        if (n > 0) {
            int fit = (top - bottom) / n;
            step = Math.max(15, Math.min(30, fit));
        }
        int y = top;
        for (int i = 0; i < n; i++) {
            String[] row = rows[i];
            boolean rule = row.length > 0 && "-".equals(row[0]);
            if (rule) {
                c.append(line(60, y + 8, 535, y + 8));
            } else {
                c.append(text(60, y, 12, safe(row[0])));
                c.append(text(300, y, 12, safe(row.length > 1 ? row[1] : "")));
            }
            y -= step;
        }
        c.append(line(50, y + 6, 545, y + 6));
        if (footer != null && !footer.isEmpty()) c.append(text(50, y - 18, 10, footer));
        return assemble(c.toString());
    }

    /* ── PDF content operators ── */
    private static String text(int x, int y, int size, String s) {
        return "BT /F1 " + size + " Tf 0 0 0 rg " + x + " " + y + " Td (" + escPdf(s) + ") Tj ET\n";
    }
    private static String line(int x1, int y1, int x2, int y2) {
        return "0.6 0.1 0.1 RG 1 w " + x1 + " " + y1 + " m " + x2 + " " + y2 + " l S\n";
    }

    /* ── assemble objects + xref into a valid PDF ── */
    private static byte[] assemble(String content) {
        byte[] cb = content.getBytes(StandardCharsets.ISO_8859_1);
        List<String> objs = new ArrayList<>();
        objs.add("<</Type/Catalog/Pages 2 0 R>>");
        objs.add("<</Type/Pages/Kids[3 0 R]/Count 1>>");
        objs.add("<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]"
               + "/Resources<</Font<</F1 5 0 R>>>>/Contents 4 0 R>>");
        objs.add("<</Length " + cb.length + ">>\nstream\n" + content + "\nendstream");
        objs.add("<</Type/Font/Subtype/Type1/BaseFont/Helvetica/Encoding/WinAnsiEncoding>>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> off = new ArrayList<>();
        put(out, "%PDF-1.4\n%âãÏÓ\n");
        for (int i = 0; i < objs.size(); i++) {
            off.add(out.size());
            put(out, (i + 1) + " 0 obj\n" + objs.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder();
        x.append("xref\n0 ").append(objs.size() + 1).append("\n0000000000 65535 f \n");
        for (int o : off) x.append(String.format("%010d 00000 n \n", o));
        x.append("trailer\n<</Size ").append(objs.size() + 1).append("/Root 1 0 R>>\n")
         .append("startxref\n").append(xref).append("\n%%EOF");
        put(out, x.toString());
        return out.toByteArray();
    }

    private static void put(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }
    /** Escape PDF string delimiters. */
    private static String escPdf(String s) {
        return safe(s).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
    /** Strip to printable Latin-1 (drop ₹, en-dash, emoji, etc.). */
    private static String safe(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= 32 && ch < 127 ? ch : ' ');
        return b.toString().trim();
    }
}
