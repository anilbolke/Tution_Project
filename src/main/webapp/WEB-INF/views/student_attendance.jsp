<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.AttendanceSummary" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();
    AttendanceSummary sum = (AttendanceSummary) request.getAttribute("summary");
    @SuppressWarnings("unchecked") List<String[]> recent = (List<String[]>) request.getAttribute("recent");
    String error = (String) request.getAttribute("error");
    int pct = (sum==null)?0:sum.percentage();
    String pc = pct>=75?"#15803D":(pct>=50?"#B8860B":"#C0392B");
%>
<%! private String stColor(String s){ if("Present".equals(s))return "#15803D"; if("Absent".equals(s))return "#C0392B"; if("Late".equals(s))return "#B8860B"; return "#5A7364"; } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>My Attendance – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
  <style>
    .back-link { display:inline-block; margin-bottom:14px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
    h2.t { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:18px; }
    .stat-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(130px,1fr)); gap:14px; margin-bottom:24px; }
    .sc { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 18px; text-align:center; }
    .sc .v { font-size:26px; font-weight:800; line-height:1; }
    .sc .l { font-size:11px; color:var(--muted); text-transform:uppercase; letter-spacing:0.5px; margin-top:6px; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.a { width:100%; border-collapse:collapse; font-size:13px; }
    table.a th { background:var(--green); color:#fff; text-align:left; padding:11px 14px; font-size:11px; text-transform:uppercase; letter-spacing:0.4px; }
    table.a td { padding:10px 14px; border-bottom:1px solid var(--border); }
    table.a tr:nth-child(even) td { background:var(--green-pale); }
    .tag { font-weight:700; }
    .empty { text-align:center; padding:36px; color:var(--muted); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
  </style>
</head>
<body>
<header>
  <a class="logo" href="<%= ctx %>/student-dashboard.jsp">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Student Portal</span>
    </div>
  </a>
  <nav>
    <span class="sp-topnav">
      <a href="<%= ctx %>/student-dashboard.jsp">Home</a>
      <a href="<%= ctx %>/student-attendance" class="active">Attendance</a>
      <a href="<%= ctx %>/student-resources?type=PDF">Study</a>
      <a href="<%= ctx %>/student-resources?type=VIDEO">Videos</a>
      <a href="<%= ctx %>/student-exams">Exams</a>
      <a href="<%= ctx %>/student-tickets">Support</a>
    </span>
    <span class="user sp-username">🎓 <%= student.getFullName() %></span>
    <a class="logout" href="<%= ctx %>/student-logout">Logout</a>
  </nav>
</header>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <h2 class="t">My Attendance</h2>

  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <div class="stat-grid">
    <div class="sc"><div class="v" style="color:<%=pc%>"><%= sum==null||sum.total()==0?"—":(pct+"%") %></div><div class="l">Attendance</div></div>
    <div class="sc"><div class="v" style="color:#15803D"><%= sum==null?0:sum.getPresent() %></div><div class="l">Present</div></div>
    <div class="sc"><div class="v" style="color:#C0392B"><%= sum==null?0:sum.getAbsent() %></div><div class="l">Absent</div></div>
    <div class="sc"><div class="v" style="color:#B8860B"><%= sum==null?0:sum.getLate() %></div><div class="l">Late</div></div>
    <div class="sc"><div class="v" style="color:#5A7364"><%= sum==null?0:sum.getLeave() %></div><div class="l">Leave</div></div>
  </div>

  <div class="table-wrap">
    <% if (recent == null || recent.isEmpty()) { %>
      <div class="empty">📅 No attendance recorded yet.</div>
    <% } else { %>
      <table class="a">
        <thead><tr><th>Date</th><th>Status</th></tr></thead>
        <tbody>
          <% for (String[] r : recent) { %>
            <tr><td><%= r[0] %></td><td class="tag" style="color:<%= stColor(r[1]) %>"><%= r[1] %></td></tr>
          <% } %>
        </tbody>
      </table>
    <% } %>
  </div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<nav class="tabbar">
  <a href="<%= ctx %>/student-dashboard.jsp"><span class="ti">🏠</span>Home</a>
  <a href="<%= ctx %>/student-attendance" class="active"><span class="ti">📅</span>Attend</a>
  <a href="<%= ctx %>/student-resources?type=PDF"><span class="ti">📄</span>Study</a>
  <a href="<%= ctx %>/student-resources?type=VIDEO"><span class="ti">▶️</span>Videos</a>
  <a href="<%= ctx %>/student-exams"><span class="ti">📝</span>Exams</a>
  <a href="<%= ctx %>/student-tickets"><span class="ti">🎫</span>Support</a>
</nav>
</body>
</html>
