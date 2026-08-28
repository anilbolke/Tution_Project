<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("student") != null) {
        response.sendRedirect(request.getContextPath() + "/student-dashboard.jsp");
        return;
    }
    String ctx = request.getContextPath();
    String error = (String) request.getAttribute("error");
    String adm = (String) request.getAttribute("admissionNo");
    if (adm == null) adm = "";
    boolean loggedOut = "1".equals(request.getParameter("logout"));
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>Student Login – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
</head>
<body>

<header>
  <a class="logo" href="<%= ctx %>/student-login.jsp">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Student Portal</span>
    </div>
  </a>
  <nav><a href="<%= ctx %>/login.jsp">Staff Login</a></nav>
</header>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="banner-strip"><img src="<%= ctx %>/img/havellsson-banner.webp" alt="Havellsson" width="250" height="50"></div>
<div class="login-wrap">
  <div class="login-card">
    <div class="login-hero">
      <div class="eyebrow">Student Portal</div>
      <h1>Student Login</h1>
      <p>Access your attendance, study material &amp; e-content</p>
    </div>

    <form class="login-body" action="<%= ctx %>/student-login" method="post">
      <% if (error != null) { %>
        <div class="alert error"><%= error %></div>
      <% } else if (loggedOut) { %>
        <div class="alert success">You have been logged out.</div>
      <% } %>

      <div class="field">
        <label for="admissionNo">Admission Number</label>
        <input type="text" id="admissionNo" name="admissionNo" placeholder="e.g. HNS-2627-0481"
               value="<%= adm %>" autofocus required>
      </div>
      <div class="field">
        <label for="mobile">Registered Mobile</label>
        <input type="tel" id="mobile" name="mobile" placeholder="10-digit mobile" maxlength="10"
               inputmode="numeric" pattern="[6-9][0-9]{9}" required>
      </div>
      <button type="submit" class="btn-submit">Login →</button>
    </form>

    <div class="login-foot">
      Use your <strong>admission number</strong> and the <strong>mobile number</strong> registered at admission.
    </div>
  </div>
</div>

</body>
</html>
