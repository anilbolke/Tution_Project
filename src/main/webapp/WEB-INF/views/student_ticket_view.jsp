<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.Ticket, com.tution.model.TicketReply" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();
    Ticket t = (Ticket) request.getAttribute("ticket");
    if (t == null) { response.sendRedirect(ctx + "/student-tickets"); return; }
    @SuppressWarnings("unchecked") List<TicketReply> replies = (List<TicketReply>) request.getAttribute("replies");
    boolean created = "1".equals(request.getParameter("created"));
    boolean closed = "CLOSED".equals(t.getStatus());
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private String badge(String st){ if(st==null)return "st-new"; switch(st){ case "IN_PROGRESS": return "st-contacted";
        case "RESOLVED": return "st-converted"; case "CLOSED": return "st-closed"; default: return "st-new"; } }
    private String label(String st){ return st==null? "OPEN" : st.replace("_"," "); }
    private String when(String s){ return s!=null && s.length()>=16 ? s.substring(0,16) : s; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>Concern #<%= t.getTicketId() %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
  <style>
    .back-link { display:inline-block; margin-bottom:14px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:20px; margin-bottom:18px; }
    .t-head { display:flex; justify-content:space-between; align-items:flex-start; gap:12px; margin-bottom:8px; }
    .t-subj { font-family:'Playfair Display',serif; font-size:21px; color:var(--green-dark); }
    .t-meta { font-size:12px; color:var(--muted); display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
    .t-desc { font-size:14px; line-height:1.6; white-space:pre-wrap; color:var(--text); }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 10px; border-radius:12px; text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; } .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-converted { background:var(--green-light); color:var(--success); } .st-closed { background:#EEE; color:#666; }
    .thread { display:flex; flex-direction:column; gap:12px; margin-bottom:18px; }
    .bub { max-width:84%; padding:11px 14px; border-radius:14px; font-size:14px; line-height:1.5; white-space:pre-wrap; box-shadow:var(--shadow); }
    .bub .who { font-size:11px; font-weight:700; margin-bottom:3px; opacity:.85; }
    .bub .at { font-size:10px; opacity:.7; margin-top:5px; }
    .bub.staff { align-self:flex-start; background:#fff; border:1px solid var(--border); border-top-left-radius:3px; }
    .bub.staff .who { color:var(--green-dark); }
    .bub.me { align-self:flex-end; background:var(--green-light); border-top-right-radius:3px; }
    .bub.me .who { color:var(--success); }
    .field textarea { width:100%; padding:11px 12px; font-size:14px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:8px; background:var(--green-pale); min-height:80px; resize:vertical; }
    .btn-submit { background:var(--green); color:#fff; border:none; padding:11px 20px; border-radius:8px; font-size:14px; font-weight:700; cursor:pointer; margin-top:10px; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }
    .note { background:#EAF7F0; color:var(--green-dark); border:1px solid var(--green-light); padding:10px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
    .closed-note { text-align:center; color:var(--muted); font-size:13px; padding:14px; background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); }
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
    <a class="logout" href="<%= ctx %>/student-logout">Logout</a>
  </nav>
</header>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <a class="back-link" href="<%= ctx %>/student-tickets">← All my concerns</a>

  <% if (created) { %><div class="note">✅ Your concern has been submitted. The management will respond here.</div><% } %>

  <div class="card">
    <div class="t-head">
      <div class="t-subj">#<%= t.getTicketId() %> · <%= esc(t.getSubject()) %></div>
      <span class="badge <%= badge(t.getStatus()) %>"><%= label(t.getStatus()) %></span>
    </div>
    <div class="t-meta">
      <span>🏷️ <%= esc(t.getCategory()) %></span>
      <span>⚑ <%= esc(t.getPriority()) %></span>
      <span>🕒 <%= when(t.getCreatedAt()) %></span>
    </div>
    <div class="t-desc"><%= esc(t.getMessage()) %></div>
  </div>

  <% if (replies != null && !replies.isEmpty()) { %>
    <div class="thread">
      <% for (TicketReply r : replies) { %>
        <div class="bub <%= r.isStaff() ? "staff" : "me" %>">
          <div class="who"><%= r.isStaff() ? "👩‍🏫 " + esc(r.getSenderName()) + " (Management)" : "🙋 You" %></div>
          <%= esc(r.getMessage()) %>
          <div class="at"><%= when(r.getCreatedAt()) %></div>
        </div>
      <% } %>
    </div>
  <% } %>

  <% if (closed) { %>
    <div class="closed-note">🔒 This concern is closed. Reply to re-open it if you still need help.</div>
  <% } %>

  <div class="card">
    <form action="<%= ctx %>/student-tickets" method="post">
      <input type="hidden" name="action" value="reply">
      <input type="hidden" name="ticketId" value="<%= t.getTicketId() %>">
      <div class="field">
        <label style="display:block;font-size:12px;font-weight:600;margin-bottom:6px;">Add a reply</label>
        <textarea name="message" placeholder="Type your message…" required></textarea>
      </div>
      <button type="submit" class="btn-submit">💬 Send Reply</button>
    </form>
  </div>
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
