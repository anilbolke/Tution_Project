package com.tution.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One row of an uploaded school student list, after parsing and validation.
 *
 * Carries both the mapped values and the verdict, so the preview screen can show
 * exactly what will happen before anything is written.
 */
public class ImportRow {

    public enum Verdict { NEW, DUPLICATE, REJECTED }

    /** 1-based spreadsheet row number, so a rejection can name the row the user sees. */
    public int rowNo;
    public Verdict verdict = Verdict.NEW;
    public final List<String> problems = new ArrayList<>();

    /** field name -> value, in template order. */
    public final Map<String, String> values = new LinkedHashMap<>();

    /** Set when the row matches an existing lead. */
    public Integer existingInquiryId;
    public String  existingMatchedOn;      // "student mobile" / "parent mobile"

    /** Roll number allocated at commit time (preview leaves it null). */
    public String rollNo;

    public String get(String field) {
        String v = values.get(field);
        return v == null ? "" : v;
    }
    public void set(String field, String value) {
        values.put(field, value == null ? "" : value.trim());
    }
    public void reject(String why) {
        problems.add(why);
        verdict = Verdict.REJECTED;
    }
    public String problemText() {
        return String.join("; ", problems);
    }
    public String getName()   { return get("full_name"); }
    public String getMobile() { return get("mobile"); }
    public String getSchool() { return get("school_name"); }
}
