package com.tution.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal Office Open XML (.xlsx) reader — pure JDK (java.util.zip + regex), no
 * external JARs. The mirror image of {@link XlsxWriter}.
 *
 * Reads the first worksheet into rows of cells keyed by COLUMN LETTER ("A",
 * "AB"), not by position.
 *
 * That distinction is the whole point. Excel OMITS empty cells from the XML
 * entirely — the sample row of the institute's 49-column lead template contains
 * just 14 &lt;c&gt; elements. Anything that reads cells positionally therefore
 * shifts every value left of its true column and silently files the school name
 * under "Date of Birth". Always ask for a value by its letter.
 *
 * Handles: shared strings, inline strings, plain numbers, booleans, and Excel
 * date serials. Formulas are read as their last cached value.
 */
public final class XlsxReader {

    /** Excel's day 0. Serial 1 is 1900-01-01, and the epoch is offset by two days
     *  because Excel keeps Lotus 1-2-3's non-existent 29 Feb 1900. */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private static final Pattern ROW_PAT  = Pattern.compile("<row[^>]*>(.*?)</row>", Pattern.DOTALL);
    private static final Pattern CELL_PAT = Pattern.compile("<c\\s([^>]*?)/>|<c\\s([^>]*?)>(.*?)</c>", Pattern.DOTALL);
    private static final Pattern REF_PAT  = Pattern.compile("r=\"([A-Z]+)\\d+\"");
    private static final Pattern TYPE_PAT = Pattern.compile("t=\"([^\"]+)\"");
    private static final Pattern V_PAT    = Pattern.compile("<v[^>]*>(.*?)</v>", Pattern.DOTALL);
    private static final Pattern T_PAT    = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
    private static final Pattern SI_PAT   = Pattern.compile("<si[^>]*>(.*?)</si>", Pattern.DOTALL);

    private XlsxReader() { }

    /** One spreadsheet row: column letter -> trimmed cell text. */
    public static class Row {
        public final int number;                                     // 1-based, as shown in Excel
        public final Map<String, String> cells = new LinkedHashMap<>();
        Row(int number) { this.number = number; }

        /** Value at a column letter, never null — missing cells read as "". */
        public String get(String colLetter) {
            String v = cells.get(colLetter);
            return v == null ? "" : v;
        }
        public boolean isEmpty() {
            for (String v : cells.values()) if (!v.isEmpty()) return false;
            return true;
        }
    }

    /**
     * Reads the first worksheet. Rows come back in sheet order including the
     * header row, so the caller can map headers to columns itself.
     */
    public static List<Row> read(byte[] xlsx) throws IOException {
        Map<String, byte[]> parts = unzip(xlsx);

        byte[] sheet = parts.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {                                  // some writers name it differently
            for (Map.Entry<String, byte[]> e : parts.entrySet()) {
                if (e.getKey().startsWith("xl/worksheets/") && e.getKey().endsWith(".xml")) {
                    sheet = e.getValue();
                    break;
                }
            }
        }
        if (sheet == null) throw new IOException("No worksheet found — is this really an .xlsx file?");

        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        return parseSheet(new String(sheet, StandardCharsets.UTF_8), shared);
    }

    /** Header row -> {header text (lower-cased, squeezed) : column letter}. */
    public static Map<String, String> headerIndex(Row header) {
        Map<String, String> idx = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : header.cells.entrySet()) {
            String key = normalise(e.getValue());
            if (!key.isEmpty() && !idx.containsKey(key)) idx.put(key, e.getKey());
        }
        return idx;
    }

    /**
     * Header text reduced to a stable lookup key: lower-cased, punctuation and
     * runs of whitespace collapsed. The institute's template carries stray
     * spaces ("  Percentage Score in Previous Class"), a typo ("Other Remak")
     * and a stray digit ("Lead Owner1"), so exact matching is too brittle.
     */
    public static String normalise(String header) {
        if (header == null) return "";
        return header.toLowerCase()
                     .replaceAll("[^a-z0-9]+", " ")
                     .trim();
    }

    /** An Excel date serial (e.g. 46012) as an ISO date, or "" if it isn't one. */
    public static String toIsoDate(String cell) {
        if (cell == null) return "";
        String s = cell.trim();
        if (s.isEmpty()) return "";
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return s;              // already ISO
        if (!s.matches("\\d+(\\.\\d+)?")) return "";
        double d = Double.parseDouble(s);
        // Excel serials for plausible dates: 1 (1900-01-01) .. ~73050 (2099-12-31).
        if (d < 1 || d > 73050) return "";
        return EXCEL_EPOCH.plusDays((long) d).toString();
    }

    /* ─── internals ─── */

    private static Map<String, byte[]> unzip(byte[] data) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                copy(zin, bos);
                out.put(e.getName().replace('\\', '/'), bos.toByteArray());
                zin.closeEntry();
            }
        }
        return out;
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static List<String> sharedStrings(byte[] xml) {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = SI_PAT.matcher(new String(xml, StandardCharsets.UTF_8));
        while (m.find()) {
            // A shared string may be split across several <t> runs (rich text).
            StringBuilder sb = new StringBuilder();
            Matcher t = T_PAT.matcher(m.group(1));
            while (t.find()) sb.append(unescape(t.group(1)));
            out.add(sb.toString());
        }
        return out;
    }

    private static List<Row> parseSheet(String xml, List<String> shared) {
        List<Row> rows = new ArrayList<>();
        Matcher rm = ROW_PAT.matcher(xml);
        int implicit = 0;
        while (rm.find()) {
            String rowXml = rm.group(0);
            int number = attrInt(rowXml, "r", ++implicit);
            implicit = number;
            Row row = new Row(number);

            Matcher cm = CELL_PAT.matcher(rm.group(1));
            while (cm.find()) {
                boolean selfClosing = cm.group(1) != null;
                String attrs = selfClosing ? cm.group(1) : cm.group(2);
                String body  = selfClosing ? "" : cm.group(3);

                Matcher ref = REF_PAT.matcher(attrs);
                if (!ref.find()) continue;                            // no address → unusable
                String col = ref.group(1);

                Matcher tp = TYPE_PAT.matcher(attrs);
                String type = tp.find() ? tp.group(1) : "n";

                row.cells.put(col, cellValue(type, body, shared));
            }
            rows.add(row);
        }
        return rows;
    }

    private static String cellValue(String type, String body, List<String> shared) {
        if ("inlineStr".equals(type)) {
            StringBuilder sb = new StringBuilder();
            Matcher t = T_PAT.matcher(body);
            while (t.find()) sb.append(unescape(t.group(1)));
            return sb.toString().trim();
        }
        Matcher v = V_PAT.matcher(body);
        if (!v.find()) return "";
        String raw = unescape(v.group(1)).trim();

        if ("s".equals(type)) {                                       // shared string index
            try {
                int i = Integer.parseInt(raw);
                return (i >= 0 && i < shared.size()) ? shared.get(i).trim() : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("b".equals(type)) return "1".equals(raw) ? "TRUE" : "FALSE";
        if ("str".equals(type) || "e".equals(type)) return raw;       // formula result / error

        // Plain number. Trim a trailing ".0" so IDs and counts read cleanly.
        if (raw.matches("-?\\d+\\.0+")) raw = raw.substring(0, raw.indexOf('.'));
        return raw;
    }

    private static int attrInt(String tag, String name, int fallback) {
        Matcher m = Pattern.compile(name + "=\"(\\d+)\"").matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    private static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        return s.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#10;", "\n").replace("&#13;", "")
                .replace("&amp;", "&");                                // last, or it double-unescapes
    }
}
