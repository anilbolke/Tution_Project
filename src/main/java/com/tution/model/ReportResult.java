package com.tution.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The output of any report — a title, a set of columns and rows of strings.
 *
 * Deliberately generic: one JSP renders every report and one servlet exports
 * every report to Excel. Eight bespoke pages would have been eight places to
 * fix the next time a column changes.
 */
public class ReportResult {

    private String type;
    private String title;
    private String description;
    private String[] columns = new String[0];
    private final List<String[]> rows = new ArrayList<>();
    /** Optional footer row, aligned to the columns. Null cells render blank. */
    private String[] totals;
    /** Column indexes that hold money/numbers, so the view can right-align them. */
    private boolean[] numeric;

    private String fromDate;
    private String toDate;
    /** False for point-in-time reports where a date range makes no sense. */
    private boolean dateRanged = true;

    public ReportResult(String type, String title) {
        this.type = type;
        this.title = title;
    }

    public void addRow(String... cells) {
        rows.add(cells);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int size() {
        return rows.size();
    }

    /** True when the column at this index should be right-aligned. */
    public boolean isNumericCol(int i) {
        return numeric != null && i < numeric.length && numeric[i];
    }

    public String getType()                  { return type; }
    public void   setType(String v)          { this.type = v; }

    public String getTitle()                 { return title; }
    public void   setTitle(String v)         { this.title = v; }

    public String getDescription()           { return description; }
    public void   setDescription(String v)   { this.description = v; }

    public String[] getColumns()             { return columns; }
    public void     setColumns(String... v)  { this.columns = v; }

    public List<String[]> getRows()          { return rows; }

    public String[] getTotals()              { return totals; }
    public void     setTotals(String... v)   { this.totals = v; }

    public void setNumeric(boolean... v)     { this.numeric = v; }

    public String getFromDate()              { return fromDate; }
    public void   setFromDate(String v)      { this.fromDate = v; }

    public String getToDate()                { return toDate; }
    public void   setToDate(String v)        { this.toDate = v; }

    public boolean isDateRanged()            { return dateRanged; }
    public void    setDateRanged(boolean v)  { this.dateRanged = v; }
}
