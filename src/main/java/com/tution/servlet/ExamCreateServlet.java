package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.ExamDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.Exam;
import com.tution.model.Student;
import com.tution.model.User;

/** Create-exam form (GET) and submission (POST). */
@WebServlet("/exam-new")
public class ExamCreateServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ExamDAO examDAO = new ExamDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;
        loadClasses(req);
        req.getRequestDispatcher("/WEB-INF/views/exam_new.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        String name = trim(req.getParameter("examName"));
        String date = trim(req.getParameter("examDate"));
        String cls  = trim(req.getParameter("className"));
        String[] subs = req.getParameterValues("subjects");
        Integer maxPer = parseInt(req.getParameter("maxPerSubject"));

        String error = null;
        if (name.isEmpty())                 error = "Please enter an exam name.";
        else if (subs == null || subs.length == 0) error = "Please select at least one subject.";
        else if (maxPer == null || maxPer <= 0)    error = "Please enter a valid max marks per subject.";

        if (error != null) {
            req.setAttribute("error", error);
            req.setAttribute("examName", name);
            req.setAttribute("className", cls);
            loadClasses(req);
            req.getRequestDispatcher("/WEB-INF/views/exam_new.jsp").forward(req, resp);
            return;
        }

        try {
            User user = (User) req.getSession().getAttribute("user");
            Exam e = new Exam();
            e.setExamName(name);
            e.setExamDate(date);
            e.setClassName(cls);
            e.setSubjects(String.join(",", subs));
            e.setMaxPerSubject(maxPer);
            e.setCreatedBy(user == null ? "" : user.getFullName());
            int id = examDAO.insert(e);
            // straight to marks entry for the new exam
            resp.sendRedirect(req.getContextPath() + "/exam-marks?examId=" + id);
        } catch (SQLException ex) {
            getServletContext().log("Create exam failed", ex);
            req.setAttribute("error", "Could not create the exam. Please try again.");
            loadClasses(req);
            req.getRequestDispatcher("/WEB-INF/views/exam_new.jsp").forward(req, resp);
        }
    }

    private void loadClasses(HttpServletRequest req) {
        try {
            List<Student> all = studentDAO.findAll();
            TreeSet<String> classes = new TreeSet<>();
            for (Student s : all) if (s.getClassName() != null) classes.add(s.getClassName());
            req.setAttribute("classes", classes);
        } catch (SQLException e) {
            getServletContext().log("Load classes failed", e);
        }
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
