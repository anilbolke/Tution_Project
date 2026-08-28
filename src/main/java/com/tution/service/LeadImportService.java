package com.tution.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tution.model.ImportRow;
import com.tution.util.XlsxReader;

/**
 * Parses and validates an uploaded school student list against the institute's
 * 49-column template ("LEAD TEMPLATE FOR NEW ERP.xlsx").
 *
 * Columns are located by their HEADER TEXT, not by position, for two reasons:
 * schools reorder and delete columns, and Excel omits empty cells from the file
 * altogether — the institute's own sample row has 14 cells for 49 columns, so
 * positional reading shifts every value left of its true column.
 *
 * Score columns (Physics/Chemistry/Maths/Biology/Total/Percentage/Scholarship)
 * are deliberately NOT imported. Results come from the OMR scan and the answer
 * key; accepting them here would be a way to hand-type marks that never touch a
 * scanned sheet.
 */
public class LeadImportService {

    /** field -> the header text it is found under (normalised by XlsxReader). */
    private static final Map<String, String> FIELD_HEADERS = new LinkedHashMap<>();
    static {
        FIELD_HEADERS.put("full_name",        "name");
        FIELD_HEADERS.put("father_name",      "father s name");
        FIELD_HEADERS.put("mother_name",      "mother s name");
        FIELD_HEADERS.put("mobile",           "student s contact");
        FIELD_HEADERS.put("father_mobile",    "father s contact");
        FIELD_HEADERS.put("mother_mobile",    "mother s contact");
        FIELD_HEADERS.put("academic_term",    "academic term");
        FIELD_HEADERS.put("dob",              "date of birth");
        FIELD_HEADERS.put("gender",           "gender");
        FIELD_HEADERS.put("email",            "email");
        FIELD_HEADERS.put("school_name",      "school");
        FIELD_HEADERS.put("current_class",    "class");
        FIELD_HEADERS.put("course_name",      "select course");
        FIELD_HEADERS.put("course_interest",  "course interested in");
        FIELD_HEADERS.put("stream",           "stream");
        FIELD_HEADERS.put("preferred_centre", "preferred centre");
        FIELD_HEADERS.put("city",             "city");
        FIELD_HEADERS.put("district",         "district");
        FIELD_HEADERS.put("state",            "state");
        FIELD_HEADERS.put("roll_no",          "exam roll number");
        FIELD_HEADERS.put("board",            "board");
        FIELD_HEADERS.put("exam_date",        "date of exam");
        FIELD_HEADERS.put("exam_date_2",      "exam date");        // duplicate column, kept per the institute
        FIELD_HEADERS.put("exam_status",      "exam status");
        FIELD_HEADERS.put("attempt_no",       "exam attempt");
        FIELD_HEADERS.put("registered_for",   "registered for hamse hackse hat");
        FIELD_HEADERS.put("appeared",         "exam appeared");
        FIELD_HEADERS.put("source",           "lead source");
        FIELD_HEADERS.put("address",          "street address");
        FIELD_HEADERS.put("student_type",     "student type");
        FIELD_HEADERS.put("caste_category",   "caste category");
        FIELD_HEADERS.put("prev_class_pct",   "percentage score in previous class");
        FIELD_HEADERS.put("current_tutor",    "current tutor name academy");
        FIELD_HEADERS.put("sibling_detail",   "sibling detail");
        FIELD_HEADERS.put("heard_from",       "how they came to know about havellsson");
        FIELD_HEADERS.put("lead_stage",       "lead stage");
        FIELD_HEADERS.put("lead_sub_stage",   "lead sub stage");
        FIELD_HEADERS.put("lead_owner",       "lead owner1");      // stray digit is in the template
        FIELD_HEADERS.put("counsellor_remarks", "remark");
        FIELD_HEADERS.put("other_remark",     "other remak");      // typo is in the template
        FIELD_HEADERS.put("utr_number",       "utr number");
        FIELD_HEADERS.put("payment_status",   "payment status");
    }

    /** Header text the template carries that we intentionally ignore. */
    private static final Set<String> IGNORED_HEADERS = new HashSet<>();
    static {
        // results come from the scan, never from the upload
        IGNORED_HEADERS.add("physics score");
        IGNORED_HEADERS.add("chemistry score");
        IGNORED_HEADERS.add("maths score");
        IGNORED_HEADERS.add("biology score");
        IGNORED_HEADERS.add("total score");
        IGNORED_HEADERS.add("percentage score");
        IGNORED_HEADERS.add("scholarship percentage");
    }

    private static final Set<String> DATE_FIELDS = new HashSet<>();
    static {
        DATE_FIELDS.add("dob");
        DATE_FIELDS.add("exam_date");
        DATE_FIELDS.add("exam_date_2");
    }

    /** Outcome of parsing a file: the rows plus what the header told us. */
    public static class Parsed {
        public final List<ImportRow> rows = new ArrayList<>();
        public final List<String> missingHeaders = new ArrayList<>();
        public final List<String> unknownHeaders = new ArrayList<>();
        public int newCount, duplicateCount, rejectedCount;

        public int total() { return rows.size(); }
        public void tally() {
            newCount = duplicateCount = rejectedCount = 0;
            for (ImportRow r : rows) {
                switch (r.verdict) {
                    case NEW:       newCount++;       break;
                    case DUPLICATE: duplicateCount++; break;
                    default:        rejectedCount++;  break;
                }
            }
        }
    }

    /**
     * Parses and validates, without touching the database. Duplicate detection
     * against existing leads is applied by the caller via
     * {@link #markDuplicates(Parsed, Map, Map)}.
     */
    public Parsed parse(byte[] xlsx) throws IOException {
        Parsed out = new Parsed();
        List<XlsxReader.Row> rows = XlsxReader.read(xlsx);
        if (rows.isEmpty()) throw new IOException("The file has no rows at all.");

        XlsxReader.Row header = rows.get(0);
        Map<String, String> idx = XlsxReader.headerIndex(header);

        // field -> column letter, for the headers this file actually has
        Map<String, String> colOf = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : FIELD_HEADERS.entrySet()) {
            String col = idx.get(e.getValue());
            if (col != null) colOf.put(e.getKey(), col);
            else if (isRequiredHeader(e.getKey())) out.missingHeaders.add(e.getValue());
        }
        for (String h : idx.keySet()) {
            if (!FIELD_HEADERS.containsValue(h) && !IGNORED_HEADERS.contains(h)) out.unknownHeaders.add(h);
        }
        if (!out.missingHeaders.isEmpty()) return out;   // caller shows the error, imports nothing

        // in-file duplicates: the same mobile twice in one upload
        Set<String> seenMobiles = new HashSet<>();

        for (int i = 1; i < rows.size(); i++) {
            XlsxReader.Row src = rows.get(i);
            if (src.isEmpty()) continue;                 // trailing blank rows are not errors

            ImportRow r = new ImportRow();
            r.rowNo = src.number;
            for (Map.Entry<String, String> e : colOf.entrySet()) {
                String raw = src.get(e.getValue());
                if (DATE_FIELDS.contains(e.getKey())) {
                    String iso = XlsxReader.toIsoDate(raw);
                    raw = iso.isEmpty() ? raw : iso;     // keep the original if it isn't a date
                }
                r.set(e.getKey(), raw);
            }
            validate(r);
            if (r.verdict != ImportRow.Verdict.REJECTED) {
                String m = r.get("mobile");
                if (!m.isEmpty() && !seenMobiles.add(m)) {
                    r.verdict = ImportRow.Verdict.DUPLICATE;
                    r.existingMatchedOn = "repeated in this file";
                }
            }
            out.rows.add(r);
        }
        out.tally();
        return out;
    }

    /**
     * Flags rows that already exist as leads. Callers pass the mobile -> inquiry_id
     * maps loaded once for the whole file, so this stays a single query rather
     * than one per row.
     */
    public void markDuplicates(Parsed parsed,
                               Map<String, Integer> byStudentMobile,
                               Map<String, Integer> byParentMobile) {
        for (ImportRow r : parsed.rows) {
            if (r.verdict == ImportRow.Verdict.REJECTED) continue;
            if (r.existingInquiryId != null) continue;

            Integer id = byStudentMobile.get(r.get("mobile"));
            String on = "student mobile";
            if (id == null) { id = byParentMobile.get(r.get("mobile")); on = "parent mobile"; }
            if (id == null && !r.get("father_mobile").isEmpty()) {
                id = byStudentMobile.get(r.get("father_mobile"));
                if (id == null) id = byParentMobile.get(r.get("father_mobile"));
                on = "father's contact";
            }
            if (id != null) {
                r.existingInquiryId = id;
                r.existingMatchedOn = on;
                if (r.verdict == ImportRow.Verdict.NEW) r.verdict = ImportRow.Verdict.DUPLICATE;
            }
        }
        parsed.tally();
    }

    /** Every mobile in the file, for a single bulk duplicate lookup. */
    public Set<String> mobilesIn(Parsed parsed) {
        Set<String> out = new HashSet<>();
        for (ImportRow r : parsed.rows) {
            if (r.verdict == ImportRow.Verdict.REJECTED) continue;
            if (!r.get("mobile").isEmpty())        out.add(r.get("mobile"));
            if (!r.get("father_mobile").isEmpty()) out.add(r.get("father_mobile"));
        }
        return out;
    }

    /* ─── validation ─── */

    private void validate(ImportRow r) {
        if (r.get("full_name").isEmpty()) r.reject("Name is blank");

        // Normalise every mobile to 10 digits before checking; sheets arrive with
        // +91 prefixes, spaces and hyphens, and rejecting those would be wrong.
        for (String f : new String[] { "mobile", "father_mobile", "mother_mobile" }) {
            String norm = normaliseMobile(r.get(f));
            r.set(f, norm);
        }
        if (r.get("mobile").isEmpty()
            && r.get("father_mobile").isEmpty()
            && r.get("mother_mobile").isEmpty()) {
            r.reject("No usable 10-digit contact number");
        }
        // The student's own contact is what the lead is keyed on; fall back to a
        // parent's rather than dropping an otherwise complete row.
        if (r.get("mobile").isEmpty()) {
            if (!r.get("father_mobile").isEmpty())      r.set("mobile", r.get("father_mobile"));
            else if (!r.get("mother_mobile").isEmpty()) r.set("mobile", r.get("mother_mobile"));
        }

        String email = r.get("email");
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            r.set("email", "");                       // not worth rejecting a row over
        }
        String g = r.get("gender").toUpperCase();
        r.set("gender", g.startsWith("M") ? "Male" : g.startsWith("F") ? "Female" : g.isEmpty() ? "" : "Other");

        String attempt = r.get("attempt_no").replaceAll("[^0-9]", "");
        r.set("attempt_no", attempt.isEmpty() ? "1" : attempt);

        String roll = r.get("roll_no").replaceAll("[^0-9]", "");
        if (!roll.isEmpty() && roll.length() != 6) {
            r.reject("Exam Roll Number must be 6 digits (found '" + r.get("roll_no") + "')");
        } else {
            r.set("roll_no", roll);
        }
    }

    /** Digits only, dropping a 91/+91 country prefix; "" when it isn't a usable Indian mobile. */
    public static String normaliseMobile(String raw) {
        if (raw == null) return "";
        String d = raw.replaceAll("[^0-9]", "");
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0"))  d = d.substring(1);
        return d.matches("[6-9]\\d{9}") ? d : "";
    }

    private boolean isRequiredHeader(String field) {
        return "full_name".equals(field) || "mobile".equals(field);
    }

    /** Rejected rows as a spreadsheet the school can correct and re-upload. */
    public List<String[]> rejectionSheet(Parsed parsed) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] { "Row", "Name", "Student's Contact", "School", "Class", "Problem" });
        for (ImportRow r : parsed.rows) {
            if (r.verdict != ImportRow.Verdict.REJECTED) continue;
            out.add(new String[] {
                String.valueOf(r.rowNo), r.getName(), r.getMobile(),
                r.getSchool(), r.get("current_class"), r.problemText()
            });
        }
        return out;
    }

    /** Field names in template order — used by the preview table. */
    public static List<String> fields() {
        return new ArrayList<>(FIELD_HEADERS.keySet());
    }

    /** Header text expected for a field, for error messages. */
    public static Map<String, String> fieldHeaders() {
        return new HashMap<>(FIELD_HEADERS);
    }
}
