<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.tution.model.Student" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>Student Dashboard – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
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
      <a href="<%= ctx %>/student-dashboard.jsp" class="active">Home</a>
      <a href="<%= ctx %>/student-attendance">Attendance</a>
      <a href="<%= ctx %>/student-resources?type=PDF">Study</a>
      <a href="<%= ctx %>/student-resources?type=VIDEO">Videos</a>
      <a href="<%= ctx %>/student-exams">Exams</a>
      <a href="<%= ctx %>/student-tickets">Support</a>
    </span>
    <a class="logout" href="<%= ctx %>/student-logout">Logout</a>
  </nav>
</header>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="sp-hello">
    <h2>Hi, <%= student.getFullName() %> 👋</h2>
    <p>Welcome to your student portal.</p>
    <div class="chips">
      <span class="chip">🆔 <%= student.getAdmissionNo() %></span>
      <span class="chip">🎓 <%= student.getClassName()==null?"-":student.getClassName() %></span>
    </div>
  </div>

  <div class="sp-tiles">
    <a class="sp-tile" href="<%= ctx %>/student-attendance">
      <span class="ic">📅</span><h3>Attendance</h3><p>Your record &amp; %</p>
    </a>
    <a class="sp-tile" href="<%= ctx %>/student-resources?type=PDF">
      <span class="ic">📄</span><h3>Study Material</h3><p>Notes &amp; PDFs</p>
    </a>
    <a class="sp-tile" href="<%= ctx %>/student-resources?type=VIDEO">
      <span class="ic">▶️</span><h3>E-Content</h3><p>Lecture videos</p>
    </a>
    <a class="sp-tile" href="<%= ctx %>/student-exams">
      <span class="ic">📝</span><h3>Exams</h3><p>Take an online test</p>
    </a>
    <a class="sp-tile" href="<%= ctx %>/student-tickets">
      <span class="ic">🎫</span><h3>Raise a Concern</h3><p>Support &amp; queries</p>
    </a>
  </div>

</div>

<!-- mobile bottom tab bar -->
<nav class="tabbar">
  <a href="<%= ctx %>/student-dashboard.jsp" class="active"><span class="ti">🏠</span>Home</a>
  <a href="<%= ctx %>/student-attendance"><span class="ti">📅</span>Attend</a>
  <a href="<%= ctx %>/student-resources?type=PDF"><span class="ti">📄</span>Study</a>
  <a href="<%= ctx %>/student-resources?type=VIDEO"><span class="ti">▶️</span>Videos</a>
  <a href="<%= ctx %>/student-exams"><span class="ti">📝</span>Exams</a>
  <a href="<%= ctx %>/student-tickets"><span class="ti">🎫</span>Support</a>
</nav>

</body>
</html>
