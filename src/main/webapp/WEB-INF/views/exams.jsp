<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Exam, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<Exam> exams = (List<Exam>) request.getAttribute("exams");
    String error = (String) request.getAttribute("error");
    boolean saved = Boolean.TRUE.equals(request.getAttribute("saved"));
    int count = (exams==null)?0:exams.size();
%>
<%! private String esc(String s){ return s==null||"null".equals(s)? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Exams – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:18px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .btn-add { background:var(--green); color:#fff; text-decoration:none; font-size:13px; font-weight:700; padding:9px 16px; border-radius:7px; }
    .btn-add:hover { background:var(--green-dark); }
    .alert { padding:11px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; font-weight:500; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid var(--border); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:760px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:12px 14px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:11px 14px; border-bottom:1px solid var(--border); vertical-align:middle; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .subj-tag { display:inline-block; background:var(--green-light); color:var(--green-dark); font-size:11px; font-weight:600; padding:2px 8px; border-radius:10px; margin:1px; }
    .btn-sm { display:inline-block; font-size:12px; font-weight:700; padding:6px 11px; border-radius:6px; text-decoration:none; white-space:nowrap; }
    .btn-marks { background:var(--green); color:#fff; }
    .btn-res { background:transparent; color:var(--green-dark); border:1.5px solid var(--green-dark); margin-left:5px; }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="exams"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Examinations</h2>
    <a class="btn-add" href="<%= ctx %>/exam-new">+ New Exam</a>
  </div>

  <% if (saved) { %><div class="alert ok">✓ Exam saved.</div><% } %>
  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <% if (exams == null || exams.isEmpty()) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">🧪</div><p>No exams yet.</p>
      <p style="margin-top:6px;"><a href="<%= ctx %>/exam-new" style="color:var(--green);font-weight:600;">Create the first exam →</a></p></div></div>
  <% } else { %>
    <div class="table-wrap">
      <table class="lst">
        <thead><tr><th>#</th><th>Exam</th><th>Date</th><th>Class</th><th>Subjects</th><th>Max/Subj</th><th>Actions</th></tr></thead>
        <tbody>
        <% int i=1; for (Exam e : exams) { %>
          <tr>
            <td><%= i++ %></td>
            <td class="nm"><%= esc(e.getExamName()) %></td>
            <td><%= esc(e.getExamDate()) %></td>
            <td><%= e.getClassName()==null||"null".equals(e.getClassName())?"All":esc(e.getClassName()) %></td>
            <td><% for (String s : e.subjectList()) { %><span class="subj-tag"><%= esc(s) %></span><% } %></td>
            <td><%= e.getMaxPerSubject() %></td>
            <td>
              <a class="btn-sm btn-marks" href="<%= ctx %>/exam-marks?examId=<%= e.getExamId() %>">Enter Marks</a>
              <a class="btn-sm btn-res" href="<%= ctx %>/exam-results?examId=<%= e.getExamId() %>">Results</a>
            </td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="exams"/></jsp:include>

</body>
</html>
