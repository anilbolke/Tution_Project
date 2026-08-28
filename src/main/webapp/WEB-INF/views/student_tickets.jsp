<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.Ticket" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<Ticket> tickets = (List<Ticket>) request.getAttribute("tickets");
    String error = (String) request.getAttribute("error");
    String fSubject = (String) request.getAttribute("formSubject");
    String fMessage = (String) request.getAttribute("formMessage");
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private String badge(String st){
        if (st==null) return "st-new";
        switch(st){ case "IN_PROGRESS": return "st-contacted"; case "RESOLVED": return "st-converted";
                    case "CLOSED": return "st-closed"; default: return "st-new"; }
    }
    private String label(String st){ return st==null? "OPEN" : st.replace("_"," "); }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>Support – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
  <style>
    h2.t { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:4px; }
    .sub-t { font-size:13px; color:var(--muted); margin-bottom:18px; }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:20px; margin-bottom:20px; }
    .card h3 { font-size:16px; color:var(--green-dark); margin-bottom:14px; }
    .field { margin-bottom:14px; }
    .field label { display:block; font-size:12px; font-weight:600; color:var(--text); margin-bottom:6px; }
    .field input, .field select, .field textarea { width:100%; padding:11px 12px; font-size:14px; font-family:'Inter',sans-serif;
      border:1.5px solid var(--border); border-radius:8px; background:var(--green-pale); }
    .field textarea { min-height:96px; resize:vertical; }
    .row2 { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
    .btn-submit { background:var(--green); color:#fff; border:none; padding:12px 22px; border-radius:8px; font-size:14px;
      font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }
    .tk-list { display:flex; flex-direction:column; gap:12px; }
    .tk { display:block; background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:15px 17px;
      text-decoration:none; color:var(--text); border-left:4px solid var(--green); transition:transform .12s, box-shadow .12s; }
    .tk:hover { transform:translateY(-2px); box-shadow:0 8px 24px rgba(26,122,74,0.14); }
    .tk-top { display:flex; justify-content:space-between; align-items:center; gap:10px; margin-bottom:6px; }
    .tk-subj { font-weight:700; color:var(--green-dark); font-size:15px; }
    .tk-meta { font-size:12px; color:var(--muted); display:flex; gap:12px; flex-wrap:wrap; }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 10px; border-radius:12px; text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; } .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-converted { background:var(--green-light); color:var(--success); } .st-closed { background:#EEE; color:#666; }
    .empty { text-align:center; padding:36px 20px; color:var(--muted); background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); }
    .empty .ic { font-size:40px; margin-bottom:10px; }
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
      <a href="<%= ctx %>/student-attendance">Attendance</a>
      <a href="<%= ctx %>/student-resources?type=PDF">Study</a>
      <a href="<%= ctx %>/student-resources?type=VIDEO">Videos</a>
      <a href="<%= ctx %>/student-exams">Exams</a>
      <a href="<%= ctx %>/student-tickets" class="active">Support</a>
    </span>
    <span class="user sp-username">🎓 <%= esc(student.getFullName()) %></span>
    <a class="logout" href="<%= ctx %>/student-logout">Logout</a>
  </nav>
</header>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <h2 class="t">🎫 Raise a Concern</h2>
  <p class="sub-t">Have a question or an issue? Send it to the management — we'll respond here.</p>

  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <div class="card">
    <h3>New Concern</h3>
    <form action="<%= ctx %>/student-tickets" method="post">
      <div class="field">
        <label>Subject <span style="color:#C0392B">*</span></label>
        <input type="text" name="subject" maxlength="160" placeholder="Briefly, what is this about?" value="<%= esc(fSubject) %>" required>
      </div>
      <div class="row2">
        <div class="field">
          <label>Category</label>
          <select name="category">
            <option>General</option><option>Fees &amp; Payment</option><option>Attendance</option>
            <option>Study Material</option><option>Exam / Result</option><option>Timetable / Batch</option>
            <option>Facilities</option><option>Other</option>
          </select>
        </div>
        <div class="field">
          <label>Priority</label>
          <select name="priority"><option value="NORMAL">Normal</option><option value="HIGH">High</option><option value="LOW">Low</option></select>
        </div>
      </div>
      <div class="field">
        <label>Describe your concern <span style="color:#C0392B">*</span></label>
        <textarea name="message" placeholder="Tell us the details…" required><%= esc(fMessage) %></textarea>
      </div>
      <button type="submit" class="btn-submit">📨 Submit Concern</button>
    </form>
  </div>

  <h2 class="t" style="font-size:20px;">My Concerns</h2>
  <p class="sub-t">Tap a concern to view the conversation.</p>

  <% if (tickets == null || tickets.isEmpty()) { %>
    <div class="empty"><div class="ic">🗒️</div><p>You haven't raised any concerns yet.</p></div>
  <% } else { %>
    <div class="tk-list">
      <% for (Ticket t : tickets) { %>
        <a class="tk" href="<%= ctx %>/student-tickets?id=<%= t.getTicketId() %>">
          <div class="tk-top">
            <span class="tk-subj">#<%= t.getTicketId() %> · <%= esc(t.getSubject()) %></span>
            <span class="badge <%= badge(t.getStatus()) %>"><%= label(t.getStatus()) %></span>
          </div>
          <div class="tk-meta">
            <span>🏷️ <%= esc(t.getCategory()) %></span>
            <% if (t.getReplyCount() > 0) { %><span>💬 <%= t.getReplyCount() %> repl<%= t.getReplyCount()==1?"y":"ies" %></span><% } %>
            <span>🕒 <%= t.getUpdatedAt()!=null && t.getUpdatedAt().length()>=16 ? t.getUpdatedAt().substring(0,16) : t.getUpdatedAt() %></span>
          </div>
        </a>
      <% } %>
    </div>
  <% } %>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<nav class="tabbar">
  <a href="<%= ctx %>/student-dashboard.jsp"><span class="ti">🏠</span>Home</a>
  <a href="<%= ctx %>/student-attendance"><span class="ti">📅</span>Attend</a>
  <a href="<%= ctx %>/student-resources?type=PDF"><span class="ti">📄</span>Study</a>
  <a href="<%= ctx %>/student-resources?type=VIDEO"><span class="ti">▶️</span>Videos</a>
  <a href="<%= ctx %>/student-exams"><span class="ti">📝</span>Exams</a>
  <a href="<%= ctx %>/student-tickets" class="active"><span class="ti">🎫</span>Support</a>
</nav>
</body>
</html>
