package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.CandidateDAO;
import com.tution.dao.ExamDAO;
import com.tution.model.Exam;
import com.tution.model.ExamCandidate;
import com.tution.util.XlsxWriter;

/**
 * The exam candidate registry: who is sitting, under which roll number, at which
 * centre, and whether they turned up.
 *
 * Also where roll numbers become usable on paper — the roll list and hall
 * tickets are printed from here.
 */
@WebServlet("/candidates")
public class CandidateServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final CandidateDAO dao = new CandidateDAO();
    private final ExamDAO examDAO = new ExamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            List<Exam> exams = examDAO.findScholarshipExams();
            req.setAttribute("exams", exams);

            int examId = parseInt(req.getParameter("examId"), 0);
            if (examId <= 0 && !exams.isEmpty()) examId = exams.get(0).getExamId();
            if (examId <= 0) {
                req.getRequestDispatcher("/WEB-INF/views/candidates.jsp").forward(req, resp);
                return;
            }

            String school = req.getParameter("school");
            String centre = req.getParameter("centre");
            String status = req.getParameter("status");
            String q      = req.getParameter("q");

            List<ExamCandidate> list = dao.find(examId, school, centre, status, q);

            if ("xlsx".equals(req.getParameter("export"))) {
                exportXlsx(resp, list);
                return;
            }

            Exam exam = examDAO.findById(examId);
            Map<String, Integer> counts = dao.statusCounts(examId);
            int total = 0;
            for (int n : counts.values()) total += n;

            req.setAttribute("examId",     examId);
            req.setAttribute("exam",       exam);
            req.setAttribute("candidates", list);
            req.setAttribute("counts",     counts);
            req.setAttribute("totalRegistered", total);
            req.setAttribute("schools",    dao.schools(examId));
            req.setAttribute("centres",    dao.centres(examId));
            req.setAttribute("fSchool", school);
            req.setAttribute("fCentre", centre);
            req.setAttribute("fStatus", status);
            req.setAttribute("fQ",      q);
            req.getRequestDispatcher("/WEB-INF/views/candidates.jsp").forward(req, resp);

        } catch (SQLException e) {
            req.setAttribute("error", "Database error: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/candidates.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        int examId = parseInt(req.getParameter("examId"), 0);
        String msg = null, err = null;
        try {
            if ("booklets".equals(action)) {
                int n = dao.autoAssignBooklets(examId);
                msg = "Booklet codes A-D assigned across " + n + " candidate(s) in roll order.";

            } else if ("absentees".equals(action)) {
                int n = dao.markAbsentees(examId);
                msg = n + " candidate(s) with no scanned sheet marked ABSENT.";

            } else if ("delete".equals(action)) {
                int cid = parseInt(req.getParameter("candidateId"), 0);
                msg = dao.delete(cid)
                    ? "Candidate removed. The roll number is not reissued."
                    : null;
                if (msg == null) {
                    err = "That candidate already has a result and cannot be removed. "
                        + "Delete the result first if this really is a mistake.";
                }
            } else {
                err = "Unknown action.";
            }
        } catch (SQLException e) {
            err = "Database error: " + e.getMessage();
        }
        if (msg != null) req.getSession().setAttribute("flash", msg);
        if (err != null) req.getSession().setAttribute("flashError", err);
        resp.sendRedirect(req.getContextPath() + "/candidates?examId=" + examId);
    }

    /** The registry as a spreadsheet — same shape as the printed roll list. */
    private void exportXlsx(HttpServletResponse resp, List<ExamCandidate> list) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Roll No", "Name", "School", "Class", "Board",
                                "Student Contact", "Parent Contact", "Centre",
                                "Booklet", "Attempt", "Status" });
        for (ExamCandidate c : list) {
            rows.add(new String[] {
                c.getRollNo(), nn(c.getFullName()), nn(c.getSchoolName()), nn(c.getClassName()),
                nn(c.getBoard()), nn(c.getMobile()), nn(c.getParentMobile()), nn(c.getExamCentre()),
                nn(c.getBookletCode()), String.valueOf(c.getAttemptNo()), nn(c.getStatus())
            });
        }
        byte[] xlsx = XlsxWriter.sheet("Candidates", rows);
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition", "attachment; filename=\"exam-candidates.xlsx\"");
        resp.setContentLength(xlsx.length);
        resp.getOutputStream().write(xlsx);
    }

    private static String nn(String s) { return s == null ? "" : s; }
    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
}
