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

import com.tution.dao.CandidateDAO;
import com.tution.dao.ExamDAO;
import com.tution.dao.ExamSetupDAO;
import com.tution.model.Exam;
import com.tution.model.SubjectScore;
import com.tution.model.User;
import com.tution.service.ExamSetupService;

/**
 * Everything an exam needs before a single sheet can be scored: the sheet
 * layout, how many questions and what they are worth, which questions belong to
 * which subject, the four booklet answer keys, and the block of roll numbers it
 * may issue.
 *
 * Replaces having to write SQL to create a scholarship exam.
 */
@WebServlet("/exam-setup")
public class ExamSetupServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ExamSetupDAO setupDAO = new ExamSetupDAO();
    private final ExamDAO examDAO = new ExamDAO();
    private final CandidateDAO candidateDAO = new CandidateDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            req.setAttribute("exams", examDAO.findScholarshipExams());
            req.setAttribute("templates", setupDAO.templates());

            int examId = parseInt(req.getParameter("examId"), 0);
            if (examId > 0) {
                Exam exam = examDAO.findById(examId);
                if (exam == null) {
                    req.setAttribute("error", "That exam no longer exists.");
                } else {
                    req.setAttribute("exam", exam);
                    req.setAttribute("subjectMap", setupDAO.subjectMap(examId));
                    req.setAttribute("keyCounts", setupDAO.keyCounts(examId));
                    req.setAttribute("keyText",   keyTexts(examId, exam.getTotalQuestions()));
                    req.setAttribute("issuedMax", setupDAO.highestIssuedSequence(examId));
                    req.setAttribute("registered", total(candidateDAO.statusCounts(examId)));
                    req.setAttribute("readiness", readiness(exam));
                }
            } else {
                req.setAttribute("suggestFrom", setupDAO.suggestBlockStart());
            }
            req.getRequestDispatcher("/WEB-INF/views/exam_setup.jsp").forward(req, resp);
        } catch (SQLException e) {
            req.setAttribute("error", "Database error: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/exam_setup.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        int examId = parseInt(req.getParameter("examId"), 0);
        try {
            if ("settings".equals(action) || "create".equals(action)) {
                saveSettings(req, resp, examId, "create".equals(action));
            } else if ("subjects".equals(action)) {
                saveSubjects(req, resp, examId);
            } else if ("key".equals(action)) {
                saveKey(req, resp, examId);
            } else {
                fail(req, resp, examId, "Unknown action.");
            }
        } catch (SQLException e) {
            fail(req, resp, examId, "Database error: " + e.getMessage());
        }
    }

    /* ─── settings ─── */

    private void saveSettings(HttpServletRequest req, HttpServletResponse resp, int examId, boolean creating)
            throws SQLException, ServletException, IOException {

        String name = trim(req.getParameter("examName"));
        if (name.isEmpty()) { fail(req, resp, examId, "Give the exam a name."); return; }

        ExamSetupService.Check<int[]> marking = ExamSetupService.marking(
                req.getParameter("markCorrect"), req.getParameter("markWrong"),
                req.getParameter("totalQuestions"));
        if (!marking.ok()) { fail(req, resp, examId, marking.problemText()); return; }
        int questions = marking.value[0], correct = marking.value[1], wrong = marking.value[2];

        Integer templateId = null;
        int tpl = parseInt(req.getParameter("templateId"), 0);
        if (tpl > 0) {
            templateId = tpl;
            int sheetQuestions = setupDAO.templateQuestions(tpl);
            // The paper cannot ask more questions than the sheet has bubble rows;
            // those answers would have nowhere to be marked.
            if (sheetQuestions > 0 && questions > sheetQuestions) {
                fail(req, resp, examId, "The chosen answer sheet has room for " + sheetQuestions
                   + " questions, but this exam is set to " + questions + ".");
                return;
            }
        }

        int from = parseInt(req.getParameter("rollFrom"), 0);
        int to   = parseInt(req.getParameter("rollTo"), 0);
        String conflict = setupDAO.blockConflict(examId, from, to);
        if (conflict != null) { fail(req, resp, examId, conflict); return; }

        Exam e = new Exam();
        e.setExamId(examId);
        e.setExamName(name);
        e.setExamDate(trim(req.getParameter("examDate")));
        e.setClassName(trim(req.getParameter("className")));
        e.setExamType(trim(req.getParameter("examType")));
        e.setTemplateId(templateId);
        e.setTotalQuestions(questions);
        e.setMarkCorrect(correct);
        e.setMarkWrong(wrong);
        e.setMaxScore(ExamSetupService.maxScore(questions, correct));
        e.setRollBlockFrom(from);
        e.setRollBlockTo(to);
        e.setMaxPerSubject(parseInt(req.getParameter("maxPerSubject"), 100));
        User u = (User) req.getSession().getAttribute("user");
        e.setCreatedBy(u == null ? "system" : u.getUsername());

        if (creating) {
            e.setSubjects("");
            examId = setupDAO.create(e);
            flash(req, "Exam created. Now set the subject split and the booklet answer keys.");
        } else {
            Exam existing = examDAO.findById(examId);
            e.setSubjects(existing == null ? "" : existing.getSubjects());
            setupDAO.updateSettings(e);
            flash(req, "Settings saved. Maximum score is now " + e.getMaxScore() + ".");
        }
        resp.sendRedirect(req.getContextPath() + "/exam-setup?examId=" + examId);
    }

    /* ─── subject split ─── */

    private void saveSubjects(HttpServletRequest req, HttpServletResponse resp, int examId)
            throws SQLException, ServletException, IOException {
        Exam exam = examDAO.findById(examId);
        if (exam == null) { fail(req, resp, examId, "That exam no longer exists."); return; }

        ExamSetupService.Check<List<SubjectScore>> check = ExamSetupService.subjectMap(
                req.getParameterValues("subjectName"),
                req.getParameterValues("qFrom"),
                req.getParameterValues("qTo"),
                exam.getTotalQuestions());

        if (!check.ok()) { fail(req, resp, examId, check.problemText()); return; }

        // The UI offers up to 6 subject rows; belt-and-braces check in case a
        // request is crafted with more.
        if (check.value.size() > 6) {
            fail(req, resp, examId, "At most 6 subjects are supported (this has "
               + check.value.size() + ").");
            return;
        }

        setupDAO.saveSubjectMap(examId, check.value);

        StringBuilder sb = new StringBuilder("Subject split saved: ");
        for (int i = 0; i < check.value.size(); i++) {
            SubjectScore r = check.value.get(i);
            if (i > 0) sb.append(", ");
            sb.append(r.name).append(' ').append(r.from).append('-').append(r.to);
        }
        flash(req, sb.toString() + ".");
        resp.sendRedirect(req.getContextPath() + "/exam-setup?examId=" + examId);
    }

    /* ─── answer keys ─── */

    private void saveKey(HttpServletRequest req, HttpServletResponse resp, int examId)
            throws SQLException, ServletException, IOException {
        Exam exam = examDAO.findById(examId);
        if (exam == null) { fail(req, resp, examId, "That exam no longer exists."); return; }

        String booklet = trim(req.getParameter("booklet")).toUpperCase();
        if (!ExamSetupService.booklets().contains(booklet)) {
            fail(req, resp, examId, "Booklet must be A, B, C or D.");
            return;
        }
        String raw = req.getParameter("keyText");
        if (raw != null && raw.trim().isEmpty()) {
            setupDAO.saveAnswerKey(examId, booklet, new LinkedHashMap<Integer, String>());
            flash(req, "Answer key for booklet " + booklet + " cleared.");
            resp.sendRedirect(req.getContextPath() + "/exam-setup?examId=" + examId);
            return;
        }
        ExamSetupService.Check<Map<Integer, String>> check =
                ExamSetupService.answerKey(raw, exam.getTotalQuestions());
        if (!check.ok()) {
            fail(req, resp, examId, "Booklet " + booklet + ": " + check.problemText());
            return;
        }
        setupDAO.saveAnswerKey(examId, booklet, check.value);
        flash(req, "Answer key for booklet " + booklet + " saved - "
                 + check.value.size() + " of " + exam.getTotalQuestions() + " questions.");
        resp.sendRedirect(req.getContextPath() + "/exam-setup?examId=" + examId);
    }

    /* ─── helpers ─── */

    /** Plain-English list of what still stands between this exam and scanning. */
    private List<String> readiness(Exam exam) throws SQLException {
        List<String> todo = new ArrayList<>();
        if (exam.getTemplateId() == null) {
            todo.add("Choose which answer sheet this exam uses.");
        }
        if (setupDAO.subjectMap(exam.getExamId()).isEmpty()) {
            todo.add("Set the subject split, so results break down by subject.");
        }
        Map<String, Integer> counts = setupDAO.keyCounts(exam.getExamId());
        List<String> missing = new ArrayList<>(), partial = new ArrayList<>();
        for (String b : ExamSetupService.booklets()) {
            Integer n = counts.get(b);
            if (n == null || n == 0) missing.add(b);
            else if (n < exam.getTotalQuestions()) partial.add(b + " (" + n + "/" + exam.getTotalQuestions() + ")");
        }
        if (!missing.isEmpty()) {
            todo.add("No answer key for booklet " + String.join(", ", missing)
                   + ". A sheet bubbled with one of those cannot be scored.");
        }
        if (!partial.isEmpty()) {
            todo.add("Incomplete answer key for booklet " + String.join(", ", partial) + ".");
        }
        if (exam.getRollBlockFrom() <= 0 || exam.getRollBlockTo() <= 0) {
            todo.add("Assign a roll-number block before importing students.");
        } else if (exam.rollsRemaining() == 0) {
            todo.add("The roll-number block is exhausted - widen it before importing more students.");
        }
        return todo;
    }

    private Map<String, String> keyTexts(int examId, int totalQuestions) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String b : ExamSetupService.booklets()) {
            out.put(b, ExamSetupService.keyToText(setupDAO.answerKey(examId, b), totalQuestions));
        }
        return out;
    }

    private int total(Map<String, Integer> counts) {
        int n = 0;
        for (int v : counts.values()) n += v;
        return n;
    }

    private void fail(HttpServletRequest req, HttpServletResponse resp, int examId, String message)
            throws ServletException, IOException {
        req.getSession().setAttribute("flashError", message);
        resp.sendRedirect(req.getContextPath() + "/exam-setup"
                        + (examId > 0 ? "?examId=" + examId : ""));
    }
    private void flash(HttpServletRequest req, String message) {
        req.getSession().setAttribute("flash", message);
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
}
