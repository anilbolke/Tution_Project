package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.ExamDAO;
import com.tution.dao.ExamMarkDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.Exam;
import com.tution.model.ResultRow;
import com.tution.model.Student;

/** Computes results for an exam (table) or a single report card. */
@WebServlet("/exam-results")
public class ResultServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ExamDAO     examDAO     = new ExamDAO();
    private final ExamMarkDAO examMarkDAO = new ExamMarkDAO();
    private final StudentDAO  studentDAO  = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        Integer examId = parseInt(req.getParameter("examId"));
        if (examId == null) { resp.sendRedirect(req.getContextPath() + "/exams"); return; }
        Integer studentId = parseInt(req.getParameter("studentId"));

        try {
            Exam exam = examDAO.findById(examId);
            if (exam == null) { resp.sendRedirect(req.getContextPath() + "/exams"); return; }

            List<ResultRow> rows = compute(exam);
            req.setAttribute("exam", exam);

            if (studentId != null) {
                ResultRow card = null;
                for (ResultRow r : rows) if (r.getStudentId() == studentId) { card = r; break; }
                if (card == null) { resp.sendRedirect(req.getContextPath() + "/exam-results?examId=" + examId); return; }
                req.setAttribute("card", card);
                req.setAttribute("totalStudents", appearedCount(rows));
                req.getRequestDispatcher("/WEB-INF/views/report_card.jsp").forward(req, resp);
            } else {
                req.setAttribute("rows", rows);
                req.getRequestDispatcher("/WEB-INF/views/results.jsp").forward(req, resp);
            }
        } catch (SQLException e) {
            getServletContext().log("Compute results failed", e);
            req.setAttribute("error", "Could not compute results. Please try again.");
            req.getRequestDispatcher("/WEB-INF/views/results.jsp").forward(req, resp);
        }
    }

    /** Builds ranked result rows for every student in the exam's class. */
    private List<ResultRow> compute(Exam exam) throws SQLException {
        String[] subjects = exam.subjectList();
        int maxTotal = exam.totalMax();
        Map<Integer, Map<String, Integer>> allMarks = examMarkDAO.getMarks(exam.getExamId());

        List<Student> students = studentsFor(exam);
        List<ResultRow> rows = new ArrayList<>();
        for (Student s : students) {
            Map<String, Integer> m = allMarks.get(s.getStudentId());
            ResultRow r = new ResultRow();
            r.setStudentId(s.getStudentId());
            r.setAdmissionNo(s.getAdmissionNo());
            r.setFullName(s.getFullName());
            r.setMaxTotal(maxTotal);
            int total = 0;
            boolean appeared = false;
            Map<String, Integer> ordered = new LinkedHashMap<>();
            for (String sub : subjects) {
                Integer v = (m == null) ? null : m.get(sub);
                if (v != null) { appeared = true; total += v; ordered.put(sub, v); }
                else ordered.put(sub, null);
            }
            r.setMarks(ordered);
            r.setTotal(total);
            r.setAppeared(appeared);
            rows.add(r);
        }

        // rank appeared students by total desc (ties share a rank)
        List<ResultRow> appeared = new ArrayList<>();
        for (ResultRow r : rows) if (r.isAppeared()) appeared.add(r);
        appeared.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        int rank = 0, prevTotal = Integer.MIN_VALUE, seen = 0;
        for (ResultRow r : appeared) {
            seen++;
            if (r.getTotal() != prevTotal) { rank = seen; prevTotal = r.getTotal(); }
            r.setRank(rank);
        }

        // order output: appeared (by rank) first, then non-appeared (by name)
        List<ResultRow> out = new ArrayList<>(appeared);
        List<ResultRow> rest = new ArrayList<>();
        for (ResultRow r : rows) if (!r.isAppeared()) rest.add(r);
        rest.sort((a, b) -> safe(a.getFullName()).compareToIgnoreCase(safe(b.getFullName())));
        out.addAll(rest);
        return out;
    }

    private int appearedCount(List<ResultRow> rows) {
        int n = 0; for (ResultRow r : rows) if (r.isAppeared()) n++; return n;
    }

    private List<Student> studentsFor(Exam exam) throws SQLException {
        List<Student> all = studentDAO.findAll();
        String cls = exam.getClassName();
        if (cls == null || cls.isEmpty() || "null".equals(cls)) return all;
        List<Student> out = new ArrayList<>();
        for (Student s : all) if (cls.equals(s.getClassName())) out.add(s);
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
