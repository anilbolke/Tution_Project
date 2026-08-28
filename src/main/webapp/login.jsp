<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    // If already logged in, go straight to dashboard
    if (session.getAttribute("user") != null) {
        response.sendRedirect(request.getContextPath() + "/dashboard.jsp");
        return;
    }
    String error    = (String) request.getAttribute("error");
    String username = (String) request.getAttribute("username");
    if (username == null) username = "";
    boolean loggedOut = "1".equals(request.getParameter("logout"));
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Login – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
</head>
<body>

<header>
  <a class="logo" href="<%= request.getContextPath() %>/login.jsp">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Tuition Management System</span>
    </div>
  </a>
  <nav>
    <a href="<%= request.getContextPath() %>/inquiry.jsp">Inquiry</a>
    <a href="<%= request.getContextPath() %>/student-login.jsp">Student Login</a>
  </nav>
</header>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<%-- <div class="banner-strip"><img src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson" width="250" height="50"></div>
 --%><div class="login-wrap">
  <div class="login-card">

    <div class="login-hero">
      <div class="eyebrow">Staff Portal</div>
      <h1>Welcome Back</h1>
      <p>Sign in to manage admissions, fees &amp; results</p>
    </div>

    <form class="login-body" action="<%= request.getContextPath() %>/login" method="post">

      <% if (error != null) { %>
        <div class="alert error"><%= error %></div>
      <% } else if (loggedOut) { %>
        <div class="alert success">You have been logged out successfully.</div>
      <% } %>

      <div class="field">
        <label for="username">Username</label>
        <input type="text" id="username" name="username" placeholder="e.g. admin"
               value="<%= username %>" autocomplete="username" autofocus required>
      </div>

      <div class="field">
        <label for="password">Password</label>
        <input type="password" id="password" name="password" placeholder="Enter your password"
               autocomplete="current-password" required>
      </div>

      <button type="submit" class="btn-submit">Sign In →</button>
    </form>

    <div class="login-foot">
      <a href="<%= request.getContextPath() %>/inquiry.jsp"
         style="display:block;background:var(--accent);color:var(--green-dark);font-weight:800;font-size:16px;
                text-decoration:none;padding:14px 18px;border-radius:9px;margin-bottom:14px;
                box-shadow:0 3px 10px rgba(244,197,66,0.45);letter-spacing:0.3px;">
        📝 New Student? Submit an Inquiry →
      </a>
      Student? <a href="<%= request.getContextPath() %>/student-login.jsp" style="color:var(--green);font-weight:600;text-decoration:none;">Login to the Student Portal →</a>
      <br><br>
      Demo login &nbsp;·&nbsp; <code>admin</code> / <code>admin123</code>
    </div>

  </div>
</div>

<!-- Floating WhatsApp contact button -->
<a class="wa-float" href="<%= com.tution.util.WhatsAppConfig.supportLink() %>" target="_blank" rel="noopener" aria-label="Chat with us on WhatsApp">
  <svg viewBox="0 0 24 24" fill="#fff" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
  </svg>
  <span class="wa-label">Chat with us</span>
</a>

</body>
</html>
