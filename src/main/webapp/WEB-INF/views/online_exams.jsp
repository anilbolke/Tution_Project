<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.OnlineExam, com.tution.model.OnlineExamQuestion, com.tution.model.OnlineExamAttempt, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<OnlineExam> exams = (List<OnlineExam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<OnlineExam>();
    @SuppressWarnings("unchecked") TreeSet<String> classes = (TreeSet<String>) request.getAttribute("classes");
    if (classes == null) classes = new TreeSet<String>();
    OnlineExam exam = (OnlineExam) request.getAttribute("exam");
    @SuppressWarnings("unchecked") List<OnlineExamQuestion> questions =
        (List<OnlineExamQuestion>) request.getAttribute("questions");
    if (questions == null) questions = new ArrayList<OnlineExamQuestion>();
    @SuppressWarnings("unchecked") List<OnlineExamAttempt> attempts =
        (List<OnlineExamAttempt>) request.getAttribute("attempts");
    if (attempts == null) attempts = new ArrayList<OnlineExamAttempt>();

    Integer currentPage = (Integer) request.getAttribute("currentPage");
    if (currentPage == null) currentPage = 1;
    Integer totalPages = (Integer) request.getAttribute("totalPages");
    if (totalPages == null) totalPages = 1;
    Integer totalCount = (Integer) request.getAttribute("totalCount");
    if (totalCount == null) totalCount = exams.size();

    String error = (String) request.getAttribute("error");
    String flash = (String) request.getAttribute("flash");
    String flashError = (String) request.getAttribute("flashError");

    // Re-shown after a failed "create" submit, so a bad line in a long paste
    // doesn't mean retyping everything.
    String stickyTitle = (String) request.getAttribute("stickyTitle");
    String stickyClassName = (String) request.getAttribute("stickyClassName");
    Integer stickyDuration = (Integer) request.getAttribute("stickyDuration");
    String stickyQuestionsText = (String) request.getAttribute("stickyQuestionsText");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Online Exams – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .oe-wrap { max-width:1100px; margin:18px auto; padding:0 14px; }
  .oe-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:20px; margin-bottom:16px; }
  .oe-card h2 { margin:0 0 3px; font-size:17px; color:#1b2b23; }
  .oe-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 16px; }

  .oe-primary { border:1.5px solid #1A7A4A; box-shadow:0 2px 10px rgba(26,122,74,0.08); }
  .oe-primary h2 { color:#135C37; }

  .step { display:flex; align-items:center; gap:9px; margin:0 0 12px; }
  .step .num { display:inline-flex; align-items:center; justify-content:center; width:22px; height:22px;
               border-radius:50%; background:#1A7A4A; color:#fff; font-size:12px; font-weight:700; flex:none; }
  .step .lbl { font-size:13.5px; font-weight:700; color:#135C37; }

  .q-section { background:#FFF8E8; border:1.5px solid #F0D9A8; border-left:5px solid #C98A1A;
               border-radius:9px; padding:16px 18px; margin-bottom:4px; }
  .q-section .step .num { background:#C98A1A; }
  .q-section .step .lbl { color:#8A5A0F; font-size:14.5px; }
  .q-section textarea { border-color:#e6cd93; }
  .q-section textarea:focus { border-color:#C98A1A; box-shadow:0 0 0 3px rgba(201,138,26,0.14); }
  .q-section .fmt-box { background:#fff; border-color:#e6cd93; }

  .row { display:flex; gap:14px; flex-wrap:wrap; align-items:flex-end; }
  .row label { display:block; font-size:12px; font-weight:600; color:#42555e; margin-bottom:5px; }
  .row input, .row select { padding:9px 11px; border:1.5px solid #cfd6da; border-radius:7px; font-size:14px;
                             font-family:inherit; box-sizing:border-box; }
  .row input:focus, .row select:focus, textarea:focus { outline:none; border-color:#1A7A4A;
    box-shadow:0 0 0 3px rgba(26,122,74,0.12); }

  textarea { width:100%; padding:10px 11px; border:1.5px solid #cfd6da; border-radius:7px;
             font-family:Consolas,Menlo,monospace; font-size:12.5px; box-sizing:border-box; min-height:170px; }

  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }

  table.el { width:100%; border-collapse:collapse; font-size:13px; margin-bottom:6px; }
  table.el th, table.el td { padding:9px 10px; border-bottom:1px solid #eceff1; text-align:left; }
  table.el th { background:#f6f8f9; font-size:11.5px; color:#42555e; text-transform:uppercase; letter-spacing:.3px; }
  table.el tr.sel td { background:#eef8f1; }
  .muted { color:#8697a0; font-size:12px; }
  .badge { font-size:11px; padding:3px 9px; border-radius:20px; font-weight:700; white-space:nowrap; }
  .b-on  { background:#d8efdf; color:#1b6b39; }
  .b-off { background:#f0f2f3; color:#69777e; }
  .btn-mini { display:inline-block; background:none; border:1px solid #cfd6da; border-radius:6px;
              padding:5px 10px; font-size:12px; cursor:pointer; text-decoration:none; color:#42555e; }
  .btn-mini:hover { background:#f6f8f9; }
  .btn-mini.disabled { color:#c3cacd; cursor:default; pointer-events:none; }
  .pager { display:flex; align-items:center; justify-content:center; gap:16px; margin-top:14px; }
  .fmt-box { background:#f6f8f9; border:1px solid #e3e6e8; border-radius:8px; padding:12px 14px;
             font-size:12.5px; color:#42555e; margin-bottom:12px; line-height:1.6; }
  .fmt-box code { background:#fff; border:1px solid #e3e6e8; border-radius:4px; padding:1px 5px; }
  .scroll { overflow-x:auto; }
  .manage-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:10px; margin-bottom:4px; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="onlineexams"/>
</jsp:include>

<div class="oe-wrap">
  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>
  <% if (flash != null)      { %><div class="alert a-ok"><%= esc(flash) %></div><% } %>

  <!-- ── create: title, class, time and questions together, one form ── -->
  <div class="oe-card oe-primary">
    <h2>Create a new exam</h2>
    <p class="hint">Fill in the exam's details and its questions, then create it in one go — it publishes
      immediately and Class students can take it right after.</p>

    <form method="post" action="<%= ctx %>/online-exams">
      <input type="hidden" name="action" value="create">

      <div class="step"><span class="num">1</span><span class="lbl">Exam details</span></div>
      <div class="row" style="margin-bottom:20px;">
        <div style="flex:2; min-width:220px;">
          <label for="title">Exam title</label>
          <input type="text" name="title" id="title" style="width:100%;" required
                 placeholder="e.g. Algebra Unit Test" value="<%= v(stickyTitle) %>">
        </div>
        <div>
          <label for="className">Class</label>
          <input type="text" name="className" id="className" list="classList" size="16" required
                 placeholder="e.g. 10TH" value="<%= v(stickyClassName) %>">
          <datalist id="classList">
            <% for (String c : classes) { %><option value="<%= esc(c) %>"><% } %>
          </datalist>
        </div>
        <div>
          <label for="durationMinutes">Time (minutes)</label>
          <input type="number" name="durationMinutes" id="durationMinutes" size="6" min="1"
                 value="<%= stickyDuration != null ? stickyDuration : 30 %>">
        </div>
      </div>

      <div class="q-section">
        <div class="step"><span class="num">2</span><span class="lbl">Questions</span></div>
        <div class="fmt-box">
          Paste one question per line, pipe-separated (<code>|</code>), in this exact order:<br>
          <code>CLASS|SUBJECT|CHAPTER|QUESTION_TEXT|OPTION_A|OPTION_B|OPTION_C|OPTION_D|CORRECT_ANSWER|DIFFICULTY|MARKS</code><br>
          CLASS on every line must match the Class typed above. CORRECT_ANSWER is A/B/C/D, DIFFICULTY is
          EASY/MEDIUM/HARD. Example:<br>
          <code>10|Mathematics|Algebra|What is 2+2?|3|4|5|6|B|EASY|1</code>
        </div>
        <textarea name="questionsText" required
                  placeholder="Paste pipe-separated questions here…"><%= v(stickyQuestionsText) %></textarea>
      </div>

      <div style="margin-top:14px;">
        <button class="btn" type="submit">✚ Create &amp; publish exam</button>
      </div>
    </form>
  </div>

  <!-- ── your exams ── -->
  <div class="oe-card">
    <h2>Your exams</h2>
    <p class="hint">Click a title to manage its questions, publish/close it, or see who has taken it.</p>
    <div class="scroll">
      <table class="el">
        <tr><th>Title</th><th>Class</th><th>Questions</th><th>Marks</th><th>Duration</th><th>Status</th><th></th></tr>
        <% if (exams.isEmpty()) { %>
          <tr><td colspan="7" class="muted">No online exams yet — create one above.</td></tr>
        <% } for (OnlineExam e : exams) {
             boolean isSel = exam != null && exam.getOnlineExamId() == e.getOnlineExamId(); %>
          <tr<%= isSel ? " class=\"sel\"" : "" %>>
            <td><a href="<%= ctx %>/online-exams?examId=<%= e.getOnlineExamId() %>&page=<%= currentPage %>"><%= esc(e.getTitle()) %></a></td>
            <td><%= esc(e.getClassName()) %></td>
            <td><%= e.getQuestionCount() %></td>
            <td><%= e.getTotalMarks() %></td>
            <td><%= e.getDurationMinutes() %> min</td>
            <td><span class="badge <%= e.isActive() ? "b-on" : "b-off" %>"><%= e.isActive() ? "Published" : "Closed" %></span></td>
            <td>
              <form method="post" action="<%= ctx %>/online-exams" style="display:inline;">
                <input type="hidden" name="action" value="toggle">
                <input type="hidden" name="examId" value="<%= e.getOnlineExamId() %>">
                <button type="submit" class="btn-mini"><%= e.isActive() ? "Close" : "Publish" %></button>
              </form>
            </td>
          </tr>
        <% } %>
      </table>
    </div>
    <% if (totalPages > 1) { %>
      <div class="pager">
        <% if (currentPage > 1) { %>
          <a class="btn-mini" href="<%= ctx %>/online-exams?page=<%= currentPage - 1 %>">&larr; Previous</a>
        <% } else { %>
          <span class="btn-mini disabled">&larr; Previous</span>
        <% } %>
        <span class="muted">Page <%= currentPage %> of <%= totalPages %> &middot; <%= totalCount %> exam<%= totalCount == 1 ? "" : "s" %></span>
        <% if (currentPage < totalPages) { %>
          <a class="btn-mini" href="<%= ctx %>/online-exams?page=<%= currentPage + 1 %>">Next &rarr;</a>
        <% } else { %>
          <span class="btn-mini disabled">Next &rarr;</span>
        <% } %>
      </div>
    <% } %>
  </div>

  <% if (exam != null) { %>
  <div class="oe-card">
    <div class="manage-head">
      <h2 style="margin:0;">Manage: <%= esc(exam.getTitle()) %></h2>
      <span class="badge <%= exam.isActive() ? "b-on" : "b-off" %>"><%= exam.isActive() ? "Published" : "Closed" %></span>
    </div>
    <p class="hint">
      Class <%= esc(exam.getClassName()) %> &middot; <%= questions.size() %> question(s) &middot;
      <%= exam.getTotalMarks() %> mark(s) total &middot; <%= exam.getDurationMinutes() %> minute duration.
    </p>

    <div class="fmt-box">
      Add more questions to this exam — same format as above, CLASS must still be
      <code><%= esc(exam.getClassName()) %></code>.
    </div>
    <form method="post" action="<%= ctx %>/online-exams">
      <input type="hidden" name="action" value="questions">
      <input type="hidden" name="examId" value="<%= exam.getOnlineExamId() %>">
      <textarea name="questionsText" placeholder="Paste more pipe-separated questions here…"></textarea>
      <div style="margin-top:9px;">
        <button class="btn" type="submit">💾 Add questions</button>
        <span class="muted" style="margin-left:9px;">Appended to the existing question bank below.</span>
      </div>
    </form>

    <% if (!questions.isEmpty()) { %>
    <div class="scroll" style="margin-top:16px;">
      <table class="el">
        <tr><th>#</th><th>Subject / Chapter</th><th>Question</th><th>Correct</th><th>Difficulty</th><th>Marks</th><th></th></tr>
        <% int i = 1; for (OnlineExamQuestion q : questions) { %>
        <tr>
          <td><%= i++ %></td>
          <td><%= esc(q.getSubject()) %><% if (q.getChapter() != null && !q.getChapter().isEmpty()) { %><br><span class="muted"><%= esc(q.getChapter()) %></span><% } %></td>
          <td><%= esc(q.getQuestionText()) %></td>
          <td><%= esc(q.getCorrectAnswer()) %></td>
          <td><%= esc(q.getDifficulty()) %></td>
          <td><%= q.getMarks() %></td>
          <td>
            <form method="post" action="<%= ctx %>/online-exams" style="display:inline;"
                  onsubmit="return confirm('Remove this question?');">
              <input type="hidden" name="action" value="delete-question">
              <input type="hidden" name="questionId" value="<%= q.getQuestionId() %>">
              <input type="hidden" name="examId" value="<%= exam.getOnlineExamId() %>">
              <button type="submit" class="btn-mini">Remove</button>
            </form>
          </td>
        </tr>
        <% } %>
      </table>
    </div>
    <% } %>
  </div>

  <div class="oe-card">
    <h2>Results</h2>
    <% if (attempts.isEmpty()) { %>
      <p class="hint">No student has taken this exam yet.</p>
    <% } else {
         int submittedCount = 0; long scoreSum = 0;
         for (OnlineExamAttempt a : attempts) if (a.isSubmitted()) { submittedCount++; scoreSum += a.getScore(); } %>
      <p class="hint">
        <%= submittedCount %> of <%= attempts.size() %> attempt(s) submitted<%
          if (submittedCount > 0) { %>, average score <%= String.format("%.1f", scoreSum / (double) submittedCount) %> / <%= exam.getTotalMarks() %><% } %>.
      </p>
      <div class="scroll">
        <table class="el">
          <tr><th>Student</th><th>Admission No.</th><th>Score</th><th>%</th><th>Status</th><th>Submitted</th></tr>
          <% for (OnlineExamAttempt a : attempts) { %>
          <tr>
            <td><%= esc(a.getStudentName()) %></td>
            <td><%= esc(a.getAdmissionNo()) %></td>
            <td><%= a.isSubmitted() ? (a.getScore() + " / " + a.getTotalMarks()) : "—" %></td>
            <td><%= a.isSubmitted() ? (a.percentage() + "%") : "—" %></td>
            <td><span class="badge <%= a.isSubmitted() ? "b-on" : "b-off" %>"><%= a.isSubmitted() ? "Submitted" : "In progress" %></span></td>
            <td><%= a.getSubmittedAt() == null ? "—" : esc(a.getSubmittedAt()) %></td>
          </tr>
          <% } %>
        </table>
      </div>
    <% } %>
  </div>
  <% } %>
</div>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String v(String s) { return s == null ? "" : esc(s); }
%>
</body>
</html>
