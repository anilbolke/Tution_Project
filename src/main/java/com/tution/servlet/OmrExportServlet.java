package com.tution.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.model.OmrBatchRow;
import com.tution.model.SubjectScore;
import com.tution.util.XlsxWriter;

/** Downloads the latest OMR scan results (in session) as an .xlsx — roll, student, subjects. */
@WebServlet("/omr-export")
public class OmrExportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp"); return;
        }
        @SuppressWarnings("unchecked")
        List<OmrBatchRow> rows = (List<OmrBatchRow>) s.getAttribute("omrExportRows");
        if (rows == null || rows.isEmpty()) { resp.sendRedirect(req.getContextPath() + "/omr"); return; }

        boolean scored = Boolean.TRUE.equals(s.getAttribute("omrExportScored"));
        List<SubjectScore> subs = rows.get(0).subjects;

        List<String[]> table = new ArrayList<>();
        // header row
        List<String> hdr = new ArrayList<>(Arrays.asList("#", "Roll No", "Student", "Attempted", "Blank", "Ambiguous"));
        if (scored) hdr.addAll(Arrays.asList("Correct", "Wrong", "Total"));
        if (subs != null) for (SubjectScore ss : subs) hdr.add(ss.name + (scored ? " (Score)" : " (Att.)"));
        table.add(hdr.toArray(new String[0]));
        // data rows (one per student / sheet)
        for (OmrBatchRow r : rows) {
            List<String> row = new ArrayList<>();
            row.add(String.valueOf(r.page));
            row.add(r.rollNo == null ? "" : r.rollNo);
            row.add(r.studentName == null ? "" : r.studentName);
            row.add(String.valueOf(r.attempted));
            row.add(String.valueOf(r.blank));
            row.add(String.valueOf(r.ambiguous));
            if (scored) { row.add(str(r.correct)); row.add(str(r.wrong)); row.add(str(r.score)); }
            if (r.subjects != null) for (SubjectScore ss : r.subjects)
                row.add(scored ? String.valueOf(ss.score) : (ss.attempted + "/" + ss.questions));
            table.add(row.toArray(new String[0]));
        }

        byte[] xlsx = XlsxWriter.sheet("OMR Results", table);
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition", "attachment; filename=\"omr_results.xlsx\"");
        resp.setContentLength(xlsx.length);
        try (OutputStream os = resp.getOutputStream()) { os.write(xlsx); }
    }

    private static String str(Integer v) { return v == null ? "" : String.valueOf(v); }
}
