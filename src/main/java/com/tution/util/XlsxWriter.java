package com.tution.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal Office Open XML (.xlsx) writer — pure JDK (java.util.zip), no external JARs.
 * Writes one worksheet from a list of string rows; cells that look numeric are written as
 * numbers (so Excel can sum/sort them), everything else as inline strings.
 */
public final class XlsxWriter {

    private XlsxWriter() { }

    public static byte[] sheet(String sheetName, List<String[]> rows) {
        StringBuilder sd = new StringBuilder();
        int rn = 1;
        for (String[] row : rows) {
            sd.append("<row r=\"").append(rn).append("\">");
            for (int c = 0; c < row.length; c++) {
                String ref = colRef(c) + rn;
                String v = (row[c] == null) ? "" : row[c];
                if (isNumber(v)) {
                    sd.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
                } else {
                    sd.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                      .append(esc(v)).append("</t></is></c>");
                }
            }
            sd.append("</row>");
            rn++;
        }
        String sheetXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>" + sd + "</sheetData></worksheet>";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>");
            put(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
            put(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + esc(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>");
            put(zip, "xl/worksheets/sheet1.xml", sheetXml);
        } catch (IOException e) {
            throw new RuntimeException("xlsx build failed", e);
        }
        return bos.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static String colRef(int c) {
        StringBuilder s = new StringBuilder();
        c++;
        while (c > 0) { int r = (c - 1) % 26; s.insert(0, (char) ('A' + r)); c = (c - 1) / 26; }
        return s.toString();
    }
    /**
     * True only for values that should become real numeric cells.
     *
     * Digit strings are NOT automatically numbers: a mobile number, roll number
     * or cheque number written as numeric loses its leading zeros in Excel and
     * can drift once it passes 15 digits. Anything with a leading zero, or 10+
     * digits long, is therefore kept as text — that covers phones, admission
     * digits and reference numbers, while genuine report figures (counts,
     * amounts, marks) are all well under that length.
     */
    private static boolean isNumber(String v) {
        if (v == null || !v.matches("-?\\d+(\\.\\d+)?")) {
            return false;
        }
        String digits = v.startsWith("-") ? v.substring(1) : v;
        if (digits.length() > 1 && digits.startsWith("0") && !digits.startsWith("0.")) {
            return false;   // leading zero is meaningful → identifier, not a number
        }
        int intLen = digits.indexOf('.') >= 0 ? digits.indexOf('.') : digits.length();
        return intLen < 10;
    }
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;");  break;
                case '>': b.append("&gt;");  break;
                case '"': b.append("&quot;"); break;
                default: b.append(ch < 32 && ch != 9 && ch != 10 ? ' ' : ch);
            }
        }
        return b.toString();
    }
}
