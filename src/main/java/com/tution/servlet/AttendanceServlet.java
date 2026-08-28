package com.tution.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.AttendanceDAO;
import com.tution.dao.StudentDAO;
import com.tution.model.Student;
import com.tution.model.User;

/** Daily attendance marking. */
@WebServlet("/attendance")
public class AttendanceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Set<String> VALID = new LinkedHashSet<>(
        java.util.Arrays.asList("Present", "Absent", "Late", "Leave"));

    private final StudentDAO    studentDAO    = new StudentDAO();
    private final AttendanceDAO attendanceDAO = new AttendanceDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        String date = trim(req.getParameter("date"));
        if (date.isEmpty() || !date.matches("\\d{4}-\\d{2}-\\d{2}")) date = LocalDate.now().toString();
        String classFilter = trim(req.getParameter("class"));

        try {
            List<Student> all = studentDAO.findAll();

            Set<String> classes = new TreeSet<>();
            for (Student s : all) if (s.getClassName() != null) classes.add(s.getClassName());

            List<Student> students = new ArrayList<>();
            for (Student s : all) {
                if (classFilter.isEmpty() || classFilter.equals(s.getClassName())) students.add(s);
            }

            req.setAttribute("students", students);
            req.setAttribute("classes", classes);
            req.setAttribute("statusMap", attendanceDAO.getStatusForDate(date));
            req.setAttribute("date", date);
            req.setAttribute("classFilter", classFilter);
            req.setAttribute("saved", "1".equals(req.getParameter("saved")));
        } catch (SQLException e) {
            getServletContext().log("Load attendance failed", e);
            req.setAttribute("error", "Could not load attendance. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/attendance.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        String date = trim(req.getParameter("date"));
        if (date.isEmpty() || !date.matches("\\d{4}-\\d{2}-\\d{2}")) date = LocalDate.now().toString();
        String classFilter = trim(req.getParameter("class"));

        Map<Integer, String> toMark = new LinkedHashMap<>();
        Enumeration<String> names = req.getParameterNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            if (n.startsWith("status_")) {
                String idPart = n.substring("status_".length());
                String status = req.getParameter(n);
                if (idPart.matches("\\d+") && status != null && VALID.contains(status)) {
                    toMark.put(Integer.valueOf(idPart), status);
                }
            }
        }

        try {
            User user = (User) req.getSession().getAttribute("user");
            if (!toMark.isEmpty()) {
                attendanceDAO.markBatch(date, toMark, user == null ? "" : user.getFullName());
            }
        } catch (SQLException e) {
            getServletContext().log("Save attendance failed", e);
        }

        String url = req.getContextPath() + "/attendance?date=" + date + "&saved=1";
        if (!classFilter.isEmpty()) url += "&class=" + URLEncoder.encode(classFilter, StandardCharsets.UTF_8.name());
        resp.sendRedirect(url);
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
}
