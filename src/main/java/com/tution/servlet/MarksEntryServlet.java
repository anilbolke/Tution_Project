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
import com.tution.model.Student;

/** Subject-wise marks entry grid for an exam. */
@WebServlet("/exam-marks")
public class MarksEntryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ExamDAO     examDAO     = new ExamDAO();
    private final ExamMarkDAO examMarkDAO = new ExamMarkDAO();
    private final StudentDAO  studentDAO  = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        Integer examId = parseInt(req.getParameter("examId"));
        if (examId == null) { resp.sendRedirect(req.getContextPath() + "/exams"); return; }

        try {
            Exam exam = examDAO.findById(examId);
            if (exam == null) { resp.sendRedirect(req.getContextPath() + "/exams"); return; }
            req.setAttribute("exam", exam);
            req.setAttribute("students", studentsFor(exam));
            req.setAttribute("marks", examMarkDAO.getMarks(examId));
            req.setAttribute("saved", "1".equals(req.getParameter("saved")));
        } catch (SQLException e) {
            getServletContext().log("Load marks entry failed", e);
            req.setAttribute("error", "Could not load the marks sheet. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/marks_entry.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        Integer examId = parseInt(req.getParameter("examId"));
        if (examId == null) { resp.sendRedirect(req.getContextPath() + "/exams"); return; }

        try {
            Exam exam = examDAO.findById(examId);
            if (exam == null) { resp.sendRedirect(req.getContextPath() + "/exams"); return; }

            String[] subjects = exam.subjectList();
            int max = exam.getMaxPerSubject();
            List<Student> students = studentsFor(exam);

            Map<Integer, Map<String, Integer>> toSave = new LinkedHashMap<>();
            for (Student s : students) {
                int sid = s.getStudentId();
                for (int j = 0; j < subjects.length; j++) {
                    Integer val = parseInt(req.getParameter("mark_" + sid + "_" + j));
                    if (val != null) {
                        int clamped = Math.max(0, Math.min(max, val));
                        toSave.computeIfAbsent(sid, k -> new LinkedHashMap<>()).put(subjects[j], clamped);
                    }
                }
            }
            if (!toSave.isEmpty()) examMarkDAO.saveMarks(examId, toSave);
        } catch (SQLException e) {
            getServletContext().log("Save marks failed", e);
        }
        resp.sendRedirect(req.getContextPath() + "/exam-marks?examId=" + examId + "&saved=1");
    }

    /** Students in the exam's class (all students if class is blank). */
    private List<Student> studentsFor(Exam exam) throws SQLException {
        List<Student> all = studentDAO.findAll();
        String cls = exam.getClassName();
        if (cls == null || cls.isEmpty() || "null".equals(cls)) return all;
        List<Student> out = new ArrayList<>();
        for (Student s : all) if (cls.equals(s.getClassName())) out.add(s);
        return out;
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }
    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("-?\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
