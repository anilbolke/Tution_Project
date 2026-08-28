<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Exam, com.tution.model.ResultRow, com.tution.model.User, com.tution.util.ExamUtil" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Exam exam = (Exam) request.getAttribute("exam");
    if (exam == null) { response.sendRedirect(ctx + "/exams"); return; }
    @SuppressWarnings("unchecked") List<ResultRow> rows = (List<ResultRow>) request.getAttribute("rows");
    String error = (String) request.getAttribute("error");
    String[] subjects = exam.subjectList();
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    private String gradeClass(String g){ if("A+".equals(g)||"A".equals(g))return "g-a"; if("B".equals(g)||"C".equals(g))return "g-b"; if("F".equals(g))return "g-f"; return "g-d"; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Results – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { margin-bottom:14px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:23px; color:var(--green-dark); }
    .list-head p { font-size:13px; color:var(--muted); margin-top:3px; }
    .back-link { display:inline-block; margin-bottom:12px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.res { width:100%; border-collapse:collapse; font-size:13px; min-width:680px; }
    table.res th { background:var(--green); color:#fff; text-align:left; padding:11px 12px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.res td { padding:10px 12px; border-bottom:1px solid var(--border); }
    table.res tr:last-child td { border-bottom:none; }
    table.res tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .rank { font-weight:800; color:var(--green-dark); text-align:center; }
    .rank.top { color:#fff; background:var(--accent); border-radius:50%; width:24px; height:24px; display:inline-flex; align-items:center; justify-content:center; }
    .tot { font-weight:700; }
    .grade { display:inline-block; font-size:11px; font-weight:800; padding:2px 9px; border-radius:10px; }
    .g-a { background:var(--green-light); color:var(--success); }
    .g-b { background:#FFF4D6; color:#9A6B00; }
    .g-d { background:#FDE7E0; color:#C0392B; }
    .g-f { background:#C0392B; color:#fff; }
    .absent { color:var(--muted); font-style:italic; }
    .rc { color:var(--green); font-weight:600; text-decoration:none; font-size:12px; white-space:nowrap; }
    .empty { text-align:center; padding:42px 20px; color:var(--muted); }
    .empty .ic { font-size:40px; margin-bottom:10px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="exams"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <a class="back-link" href="<%= ctx %>/exams">← Back to Exams</a>
  <div class="list-head">
    <h2>Results — <%= esc(exam.getExamName()) %></h2>
    <p><%= exam.getClassName()==null||"null".equals(exam.getClassName())?"All classes":esc(exam.getClassName()) %> · total <%= exam.totalMax() %> marks · <%= esc(exam.getExamDate()) %></p>
  </div>

  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <% if (rows == null || rows.isEmpty()) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">📊</div><p>No students for this exam.</p></div></div>
  <% } else { %>
    <div class="table-wrap">
      <table class="res">
        <thead>
          <tr><th>Rank</th><th>Student</th><% for (String s : subjects) { %><th><%= esc(s) %></th><% } %>
              <th>Total</th><th>%</th><th>Grade</th><th>Report</th></tr>
        </thead>
        <tbody>
        <% for (ResultRow r : rows) {
              if (r.isAppeared()) {
                double pct = r.percentage();
                String g = ExamUtil.grade(pct); %>
          <tr>
            <td class="rank"><% if (r.getRank()==1) { %><span class="rank top">1</span><% } else { %><%= r.getRank() %><% } %></td>
            <td class="nm"><%= esc(r.getFullName()) %></td>
            <% for (String s : subjects) { Integer v=r.getMarks().get(s); %>
              <td><%= v==null?"-":v %></td>
            <% } %>
            <td class="tot"><%= r.getTotal() %>/<%= r.getMaxTotal() %></td>
            <td><%= pct %>%</td>
            <td><span class="grade <%= gradeClass(g) %>"><%= g %></span></td>
            <td><a class="rc" href="<%= ctx %>/exam-results?examId=<%= exam.getExamId() %>&studentId=<%= r.getStudentId() %>">📄 Card</a></td>
          </tr>
        <% } else { %>
          <tr>
            <td class="rank">—</td>
            <td class="nm"><%= esc(r.getFullName()) %></td>
            <% for (String s : subjects) { %><td class="absent">-</td><% } %>
            <td colspan="3" class="absent">Not appeared</td>
            <td></td>
          </tr>
        <% } } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="exams"/></jsp:include>

</body>
</html>
