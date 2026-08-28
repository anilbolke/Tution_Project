<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.Set, java.time.LocalDate, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") Set<String> classes = (Set<String>) request.getAttribute("classes");
    String error = (String) request.getAttribute("error");
    String examName = (String) request.getAttribute("examName");
    String className = (String) request.getAttribute("className");
    if (examName == null) examName = "";
    if (className == null) className = "";
    String today = LocalDate.now().toString();
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>New Exam – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .form-wrap { max-width:560px; margin:0 auto; }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:24px 26px; }
    .card h2 { font-family:'Playfair Display',serif; font-size:22px; color:var(--green-dark); margin-bottom:18px; }
    .field { margin-bottom:15px; }
    .field label { display:block; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .req { color:#E05A2B; }
    .field input, .field select { width:100%; padding:11px 13px; font-size:15px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:7px; background:var(--green-pale); outline:none; }
    .field input:focus, .field select:focus { border-color:var(--green); box-shadow:0 0 0 3px rgba(26,122,74,0.13); background:#fff; }
    .field-row { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
    .subj-grid { display:flex; flex-wrap:wrap; gap:8px; }
    .subj-grid label { display:flex; align-items:center; gap:7px; background:var(--green-pale); border:1.5px solid var(--border); border-radius:8px; padding:9px 13px; font-size:13px; font-weight:600; color:var(--green-dark); cursor:pointer; }
    .subj-grid input { width:17px; height:17px; accent-color:var(--green); }
    .btn-submit { width:100%; padding:13px; background:var(--green); color:#fff; border:none; border-radius:8px; font-size:16px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; margin-top:6px; }
    .btn-submit:hover { background:var(--green-dark); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
    .back-link { display:inline-block; margin-bottom:14px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="exams"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <div class="form-wrap">
    <a class="back-link" href="<%= ctx %>/exams">← Back to Exams</a>
    <div class="card">
      <h2>Create Exam</h2>
      <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

      <form action="<%= ctx %>/exam-new" method="post">
        <div class="field">
          <label>Exam Name <span class="req">*</span></label>
          <input type="text" name="examName" placeholder="e.g. Weekly Test 1" value="<%= esc(examName) %>" required/>
        </div>
        <div class="field-row">
          <div class="field">
            <label>Exam Date</label>
            <input type="date" name="examDate" value="<%= today %>"/>
          </div>
          <div class="field">
            <label>Class</label>
            <select name="className">
              <option value="">All classes</option>
              <% if (classes != null) for (String c : classes) { %>
                <option value="<%= esc(c) %>" <%= c.equals(className)?"selected":"" %>><%= esc(c) %></option>
              <% } %>
            </select>
          </div>
        </div>
        <div class="field">
          <label>Subjects <span class="req">*</span></label>
          <div class="subj-grid">
            <% for (String s : new String[]{"Physics","Chemistry","Biology","Maths","English"}) { %>
              <label><input type="checkbox" name="subjects" value="<%= s %>"
                <%= ("Physics".equals(s)||"Chemistry".equals(s)||"Biology".equals(s))?"checked":"" %>/> <%= s %></label>
            <% } %>
          </div>
        </div>
        <div class="field">
          <label>Max Marks per Subject <span class="req">*</span></label>
          <input type="number" name="maxPerSubject" min="1" value="100" required/>
        </div>
        <button type="submit" class="btn-submit">Create &amp; Enter Marks →</button>
      </form>
    </div>
  </div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="exams"/></jsp:include>

</body>
</html>
