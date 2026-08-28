<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.*, com.tution.model.Student, com.tution.model.OnlineExam, com.tution.model.OnlineExamAttempt, com.tution.model.OnlineExamQuestion" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();

    OnlineExam exam = (OnlineExam) request.getAttribute("exam");
    OnlineExamAttempt attempt = (OnlineExamAttempt) request.getAttribute("attempt");
    @SuppressWarnings("unchecked") List<OnlineExamQuestion> questions =
        (List<OnlineExamQuestion>) request.getAttribute("questions");
    if (questions == null) questions = new ArrayList<OnlineExamQuestion>();
    @SuppressWarnings("unchecked") Map<Integer,String> myAnswers =
        (Map<Integer,String>) request.getAttribute("myAnswers");
    if (myAnswers == null) myAnswers = new HashMap<Integer,String>();
    String error = (String) request.getAttribute("error");

    boolean submitted = attempt != null && attempt.isSubmitted();
    long remainingSec = 0;
    if (exam != null && attempt != null && !submitted) {
        long totalSec = (long) exam.getDurationMinutes() * 60;
        long elapsedSec = 0;
        try {
            java.sql.Timestamp started = java.sql.Timestamp.valueOf(attempt.getStartedAt());
            elapsedSec = (System.currentTimeMillis() - started.getTime()) / 1000;
        } catch (Exception ignore) { }
        remainingSec = Math.max(0, totalSec - elapsedSec);
    }
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title><%= exam == null ? "Exam" : esc(exam.getTitle()) %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
  <style>
    h2.t { font-family:'Playfair Display',serif; font-size:22px; color:var(--green-dark); margin-bottom:4px; }
    .sub-t { font-size:13px; color:var(--muted); margin-bottom:16px; }
    .alert { padding:11px 14px; border-radius:8px; margin-bottom:16px; font-size:14px; }
    .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }

    .timer-bar { position:sticky; top:0; z-index:50; display:flex; align-items:center; justify-content:space-between;
      background:var(--white); box-shadow:var(--shadow); border-radius:var(--radius); padding:12px 18px; margin-bottom:16px; }
    .timer-bar .clock { font-size:20px; font-weight:800; color:var(--green-dark); font-variant-numeric:tabular-nums; }
    .timer-bar .clock.low { color:#C0392B; }
    .timer-bar .meta { font-size:12.5px; color:var(--muted); }

    .q-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 18px; margin-bottom:12px; }
    .q-card .q-head { display:flex; justify-content:space-between; gap:10px; margin-bottom:8px; }
    .q-card .q-no { font-size:12px; font-weight:700; color:var(--green-dark); }
    .q-card .q-meta { font-size:11px; color:var(--muted); }
    .q-card .q-text { font-size:14.5px; color:var(--text); margin-bottom:12px; }
    .opt { display:flex; align-items:center; gap:9px; padding:9px 11px; border:1.5px solid var(--border);
           border-radius:8px; margin-bottom:7px; cursor:pointer; font-size:13.5px; }
    .opt:hover { background:var(--green-pale); }
    .opt input { accent-color: var(--green); }
    .opt.correct { border-color:var(--success); background:var(--green-light); }
    .opt.wrong   { border-color:#C0392B; background:#FDECEA; }
    .opt-tag { font-size:11px; font-weight:700; margin-left:auto; }
    .opt-tag.ok  { color:var(--success); }
    .opt-tag.no  { color:#C0392B; }

    .submit-bar { position:sticky; bottom:0; background:var(--white); box-shadow:0 -2px 14px rgba(0,0,0,0.08);
      border-radius:var(--radius) var(--radius) 0 0; padding:14px 18px; text-align:center; margin-top:10px; }
    .btn-submit { background:var(--green); color:#fff; border:none; padding:13px 30px; border-radius:8px;
      font-size:15px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }

    .result-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:26px 22px; text-align:center; margin-bottom:18px; }
    .result-card .score { font-size:38px; font-weight:900; color:var(--green-dark); font-family:'Playfair Display',serif; }
    .result-card .pct { font-size:14px; color:var(--muted); margin-top:4px; }
  </style>
</head>
<body>

<header>
  <a class="logo" href="<%= ctx %>/student-dashboard.jsp">
    <img class="logo-mark" src="<%= ctx %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text"><span class="sub">Student Portal</span></div>
  </a>
  <nav>
    <span class="sp-topnav">
      <a href="<%= ctx %>/student-dashboard.jsp">Home</a>
      <a href="<%= ctx %>/student-attendance">Attendance</a>
      <a href="<%= ctx %>/student-resources?type=PDF">Study</a>
      <a href="<%= ctx %>/student-resources?type=VIDEO">Videos</a>
      <a href="<%= ctx %>/student-exams" class="active">Exams</a>
      <a href="<%= ctx %>/student-tickets">Support</a>
    </span>
    <span class="user sp-username">🎓 <%= esc(student.getFullName()) %></span>
    <a class="logout" href="<%= ctx %>/student-logout">Logout</a>
  </nav>
</header>

<div class="page-body sp-body" style="max-width:760px; margin:0 auto;">

  <% if (error != null || exam == null) { %>
    <div class="alert a-err"><%= error != null ? esc(error) : "This exam is not available." %></div>
    <p><a class="btn-go" href="<%= ctx %>/student-exams">&larr; Back to exams</a></p>
  <% } else { %>

    <h2 class="t"><%= esc(exam.getTitle()) %></h2>
    <p class="sub-t">Class <%= esc(exam.getClassName()) %> &middot; <%= questions.size() %> question(s)
      &middot; <%= exam.getTotalMarks() %> mark(s)</p>

    <% if (submitted) { %>
      <div class="result-card">
        <div class="score"><%= attempt.getScore() %> / <%= attempt.getTotalMarks() %></div>
        <div class="pct"><%= attempt.percentage() %>% &middot; submitted</div>
      </div>

      <% int i = 1; for (OnlineExamQuestion q : questions) {
           String my = myAnswers.get(q.getQuestionId());
           boolean answered = my != null; %>
      <div class="q-card">
        <div class="q-head">
          <span class="q-no">Q<%= i++ %><% if (q.getSubject() != null && !q.getSubject().isEmpty()) { %> &middot; <%= esc(q.getSubject()) %><% } %></span>
          <span class="q-meta"><%= q.getMarks() %> mark<%= q.getMarks()==1?"":"s" %><%= answered ? "" : " · not answered" %></span>
        </div>
        <div class="q-text"><%= esc(q.getQuestionText()) %></div>
        <% for (String letter : new String[]{"A","B","C","D"}) {
             boolean isCorrect = letter.equals(q.getCorrectAnswer());
             boolean isMine    = letter.equals(my);
             String cls = isCorrect ? "correct" : (isMine ? "wrong" : ""); %>
          <div class="opt <%= cls %>">
            <span><%= letter %>. <%= esc(q.optionFor(letter)) %></span>
            <% if (isCorrect) { %><span class="opt-tag ok">Correct</span>
            <% } else if (isMine) { %><span class="opt-tag no">Your answer</span><% } %>
          </div>
        <% } %>
      </div>
      <% } %>
      <p style="text-align:center; margin-top:8px;"><a class="btn-go" href="<%= ctx %>/student-exams">&larr; Back to exams</a></p>

    <% } else { %>

      <div class="timer-bar">
        <div>
          <div class="clock" id="clock">--:--</div>
          <div class="meta">Time remaining</div>
        </div>
        <div class="meta">Answer every question, then submit. You get one attempt.</div>
      </div>

      <form method="post" action="<%= ctx %>/student-exam-take" id="examForm">
        <input type="hidden" name="examId" value="<%= exam.getOnlineExamId() %>">

        <% int i = 1; for (OnlineExamQuestion q : questions) { %>
        <div class="q-card">
          <div class="q-head">
            <span class="q-no">Q<%= i++ %><% if (q.getSubject() != null && !q.getSubject().isEmpty()) { %> &middot; <%= esc(q.getSubject()) %><% } %></span>
            <span class="q-meta"><%= q.getMarks() %> mark<%= q.getMarks()==1?"":"s" %></span>
          </div>
          <div class="q-text"><%= esc(q.getQuestionText()) %></div>
          <% for (String letter : new String[]{"A","B","C","D"}) { %>
            <label class="opt">
              <input type="radio" name="q_<%= q.getQuestionId() %>" value="<%= letter %>">
              <span><%= letter %>. <%= esc(q.optionFor(letter)) %></span>
            </label>
          <% } %>
        </div>
        <% } %>

        <div class="submit-bar">
          <button type="submit" class="btn-submit" id="submitBtn">Submit exam</button>
        </div>
      </form>

      <script>
        (function () {
          var remaining = <%= remainingSec %>;
          var clockEl = document.getElementById('clock');
          var form = document.getElementById('examForm');
          var submitBtn = document.getElementById('submitBtn');
          var autoSubmitted = false;

          function render() {
            var m = Math.floor(remaining / 60), s = remaining % 60;
            clockEl.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
            clockEl.classList.toggle('low', remaining <= 60);
          }
          function tick() {
            if (remaining <= 0) {
              if (!autoSubmitted) {
                autoSubmitted = true;
                clockEl.textContent = "00:00";
                form.submit();
              }
              return;
            }
            remaining--;
            render();
          }
          render();
          if (remaining <= 0) { form.submit(); }
          else { setInterval(tick, 1000); }

          submitBtn.addEventListener('click', function (e) {
            if (!confirm('Submit the exam? You will not be able to change your answers after this.')) {
              e.preventDefault();
            }
          });
        })();
      </script>

    <% } %>
  <% } %>

</div>

<!-- mobile bottom tab bar -->
<nav class="tabbar">
  <a href="<%= ctx %>/student-dashboard.jsp"><span class="ti">🏠</span>Home</a>
  <a href="<%= ctx %>/student-attendance"><span class="ti">📅</span>Attend</a>
  <a href="<%= ctx %>/student-resources?type=PDF"><span class="ti">📄</span>Study</a>
  <a href="<%= ctx %>/student-resources?type=VIDEO"><span class="ti">▶️</span>Videos</a>
  <a href="<%= ctx %>/student-exams" class="active"><span class="ti">📝</span>Exams</a>
  <a href="<%= ctx %>/student-tickets"><span class="ti">🎫</span>Support</a>
</nav>

</body>
</html>
