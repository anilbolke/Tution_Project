<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.*, com.tution.model.Student, com.tution.model.OnlineExam, com.tution.model.OnlineExamAttempt" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<OnlineExam> exams = (List<OnlineExam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<OnlineExam>();
    @SuppressWarnings("unchecked") Map<Integer,OnlineExamAttempt> attempts =
        (Map<Integer,OnlineExamAttempt>) request.getAttribute("attempts");
    if (attempts == null) attempts = new HashMap<Integer,OnlineExamAttempt>();
    String error = (String) request.getAttribute("error");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flashError");
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>Exams – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
  <style>
    h2.t { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:4px; }
    .sub-t { font-size:13px; color:var(--muted); margin-bottom:18px; }
    .alert { padding:11px 14px; border-radius:8px; margin-bottom:16px; font-size:14px; }
    .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
    .ex-list { display:flex; flex-direction:column; gap:12px; }
    .ex { display:flex; align-items:center; justify-content:space-between; gap:14px; flex-wrap:wrap;
          background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 18px; }
    .ex h3 { font-size:15px; color:var(--green-dark); margin:0 0 4px; }
    .ex .meta { font-size:12.5px; color:var(--muted); }
    .badge { font-size:11px; font-weight:700; padding:3px 10px; border-radius:20px; white-space:nowrap; text-transform:uppercase; }
    .b-new  { background:#FFF4D6; color:#9A6B00; }
    .b-prog { background:#DDEBFF; color:#1B4F9C; }
    .b-done { background:var(--green-light); color:var(--success); }
    .btn-go { display:inline-block; background:var(--green); color:#fff; text-decoration:none; font-size:13px;
              font-weight:700; padding:9px 16px; border-radius:8px; white-space:nowrap; }
    .btn-go:hover { background:var(--green-dark); }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
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

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <h2 class="t">Exams</h2>
  <p class="sub-t">Online tests for Class <%= esc(student.getClassName()) %>. One attempt each.</p>

  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>

  <% if (exams.isEmpty()) { %>
    <div class="empty">
      <div class="ic">📝</div>
      <p>No exams available for your class right now.</p>
    </div>
  <% } else { %>
    <div class="ex-list">
      <% for (OnlineExam e : exams) {
           OnlineExamAttempt a = attempts.get(e.getOnlineExamId());
           boolean submitted = a != null && a.isSubmitted();
           boolean inProgress = a != null && !a.isSubmitted();
      %>
      <div class="ex">
        <div>
          <h3><%= esc(e.getTitle()) %></h3>
          <div class="meta">
            <%= e.getQuestionCount() %> question(s) &middot; <%= e.getTotalMarks() %> mark(s)
            &middot; <%= e.getDurationMinutes() %> min
          </div>
        </div>
        <div style="display:flex; align-items:center; gap:12px;">
          <% if (submitted) { %>
            <span class="badge b-done">Score: <%= a.getScore() %> / <%= a.getTotalMarks() %></span>
            <a class="btn-go" href="<%= ctx %>/student-exam-take?examId=<%= e.getOnlineExamId() %>">View result</a>
          <% } else if (inProgress) { %>
            <span class="badge b-prog">In progress</span>
            <a class="btn-go" href="<%= ctx %>/student-exam-take?examId=<%= e.getOnlineExamId() %>">Resume</a>
          <% } else { %>
            <span class="badge b-new">Not started</span>
            <a class="btn-go" href="<%= ctx %>/student-exam-take?examId=<%= e.getOnlineExamId() %>">Start exam</a>
          <% } %>
        </div>
      </div>
      <% } %>
    </div>
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
