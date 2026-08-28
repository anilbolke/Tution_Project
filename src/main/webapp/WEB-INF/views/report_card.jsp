<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.tution.model.Exam, com.tution.model.ResultRow, com.tution.model.User, com.tution.util.ExamUtil" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Exam exam = (Exam) request.getAttribute("exam");
    ResultRow r = (ResultRow) request.getAttribute("card");
    if (exam == null || r == null) { response.sendRedirect(ctx + "/exams"); return; }
    int totalStudents = request.getAttribute("totalStudents")==null?0:(Integer)request.getAttribute("totalStudents");
    String[] subjects = exam.subjectList();
    int max = exam.getMaxPerSubject();
    double pct = r.percentage();
    String grade = ExamUtil.grade(pct);
    boolean pass = ExamUtil.isPass(pct);
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Report Card – <%= esc(r.getFullName()) %></title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .toolbar { max-width:660px; margin:18px auto 0; display:flex; justify-content:space-between; align-items:center; padding:0 1rem; }
    .toolbar a { font-size:13px; color:var(--green); font-weight:600; text-decoration:none; }
    .btn-print { background:var(--green); color:#fff !important; border:none; border-radius:7px; padding:9px 18px; font-size:13px; font-weight:700; text-decoration:none; }
    .card { max-width:660px; margin:16px auto 50px; background:#fff; border-radius:var(--radius); box-shadow:var(--shadow); overflow:hidden; }
    .rc-head { background:var(--green-dark); color:#fff; padding:22px 26px; display:flex; align-items:center; gap:14px; }
    .rc-logo { width:48px; height:48px; min-width:48px; background:var(--accent); border-radius:9px; display:flex; align-items:center; justify-content:center; font-family:'Playfair Display',serif; font-size:24px; font-weight:900; color:var(--green-dark); }
    .rc-head .brand { font-family:'Playfair Display',serif; font-size:20px; }
    .rc-head .sub { font-size:12px; color:#A8D9BC; margin-top:2px; }
    .rc-title { text-align:center; padding:12px; background:var(--accent); color:var(--green-dark); font-weight:800; letter-spacing:1px; text-transform:uppercase; font-size:13px; }
    .rc-body { padding:22px 26px; }
    .meta { display:grid; grid-template-columns:1fr 1fr; gap:6px 18px; font-size:13px; margin-bottom:18px; }
    .meta div span { color:var(--muted); } .meta div strong { color:var(--text); }
    table.sc { width:100%; border-collapse:collapse; font-size:13px; margin-bottom:18px; }
    table.sc th { background:var(--green); color:#fff; text-align:left; padding:10px 12px; font-size:11px; text-transform:uppercase; letter-spacing:0.4px; }
    table.sc td { padding:10px 12px; border-bottom:1px solid var(--border); }
    table.sc tr:last-child td { border-bottom:none; }
    .sub-pass { color:var(--success); font-weight:600; } .sub-fail { color:#C0392B; font-weight:600; }
    .totrow td { font-weight:800; color:var(--green-dark); background:var(--green-pale); }
    .summary { display:grid; grid-template-columns:repeat(4,1fr); gap:10px; text-align:center; }
    .summary div { background:var(--green-pale); border:1px solid var(--border); border-radius:9px; padding:12px 6px; }
    .summary .v { font-size:20px; font-weight:900; color:var(--green-dark); line-height:1; }
    .summary .l { font-size:10px; color:var(--muted); text-transform:uppercase; letter-spacing:0.4px; margin-top:5px; }
    .result-tag { display:inline-block; margin-top:4px; font-size:13px; font-weight:800; padding:4px 14px; border-radius:14px; }
    .result-tag.pass { background:var(--green-light); color:var(--success); }
    .result-tag.fail { background:#C0392B; color:#fff; }
    .sign { display:flex; justify-content:space-between; margin-top:34px; font-size:12px; color:var(--muted); }
    .sign .line { border-top:1.5px solid var(--text); padding-top:6px; width:150px; text-align:center; }
    .rc-foot { text-align:center; font-size:11px; color:var(--muted); padding:14px; border-top:1px solid var(--border); }
    @media print { header, .neet-ribbon, .toolbar, .footer, .tabbar { display:none !important; } body{background:#fff;} .card{box-shadow:none;margin:0;max-width:100%;border-radius:0;} }
  </style>
</head>
<body class="sp-body">

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="exams"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="toolbar">
  <a href="<%= ctx %>/exam-results?examId=<%= exam.getExamId() %>">← Back to results</a>
  <a class="btn-print" href="javascript:window.print()">🖨 Print</a>
</div>

<div class="card">
  <div class="rc-head">
    <div class="rc-logo">H</div>
    <div><div class="brand">Havellsson NEET Samrat</div><div class="sub">Expert Coaching · 11th &amp; 12th Science</div></div>
  </div>
  <div class="rc-title">Student Report Card</div>

  <div class="rc-body">
    <div class="meta">
      <div><span>Student:</span> <strong><%= esc(r.getFullName()) %></strong></div>
      <div><span>Admission No:</span> <strong><%= esc(r.getAdmissionNo()) %></strong></div>
      <div><span>Exam:</span> <strong><%= esc(exam.getExamName()) %></strong></div>
      <div><span>Date:</span> <strong><%= esc(exam.getExamDate()) %></strong></div>
      <div><span>Class:</span> <strong><%= exam.getClassName()==null||"null".equals(exam.getClassName())?"-":esc(exam.getClassName()) %></strong></div>
      <div><span>Rank:</span> <strong><%= r.getRank() %> of <%= totalStudents %></strong></div>
    </div>

    <table class="sc">
      <thead><tr><th>Subject</th><th>Marks</th><th>Max</th><th>Result</th></tr></thead>
      <tbody>
        <% for (String s : subjects) {
              Integer v = r.getMarks().get(s);
              boolean sp = v != null && ExamUtil.isPass(v * 100.0 / max); %>
        <tr>
          <td><%= esc(s) %></td>
          <td><%= v==null?"-":v %></td>
          <td><%= max %></td>
          <td><% if (v==null) { %>-<% } else { %><span class="<%= sp?"sub-pass":"sub-fail" %>"><%= sp?"Pass":"Fail" %></span><% } %></td>
        </tr>
        <% } %>
        <tr class="totrow"><td>Total</td><td><%= r.getTotal() %></td><td><%= r.getMaxTotal() %></td><td><%= pct %>%</td></tr>
      </tbody>
    </table>

    <div class="summary">
      <div><div class="v"><%= r.getTotal() %></div><div class="l">Total</div></div>
      <div><div class="v"><%= pct %>%</div><div class="l">Percentage</div></div>
      <div><div class="v"><%= grade %></div><div class="l">Grade</div></div>
      <div><div class="v">#<%= r.getRank() %></div><div class="l">Rank</div></div>
    </div>

    <div style="text-align:center;margin-top:16px;">
      <span class="result-tag <%= pass?"pass":"fail" %>"><%= pass?"PASS":"FAIL" %></span>
    </div>

    <div class="sign">
      <div class="line">Class Teacher</div>
      <div class="line">Principal</div>
    </div>
  </div>

  <div class="rc-foot">Computer-generated report card · Grading: A+ ≥90, A ≥75, B ≥60, C ≥45, D ≥33, F &lt;33%.</div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="exams"/></jsp:include>

</body>
</html>
