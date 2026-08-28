<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Ticket, com.tution.model.TicketReply, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Ticket t = (Ticket) request.getAttribute("ticket");
    if (t == null) { response.sendRedirect(ctx + "/manage-tickets"); return; }
    @SuppressWarnings("unchecked") List<TicketReply> replies = (List<TicketReply>) request.getAttribute("replies");
    String cur = t.getStatus() == null ? "OPEN" : t.getStatus();
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private String badge(String st){ if(st==null)return "st-new"; switch(st){ case "IN_PROGRESS": return "st-contacted";
        case "RESOLVED": return "st-converted"; case "CLOSED": return "st-closed"; default: return "st-new"; } }
    private String label(String st){ return st==null? "OPEN" : st.replace("_"," "); }
    private String when(String s){ return s!=null && s.length()>=16 ? s.substring(0,16) : s; }
    private String opt(String v,String cur){ return ("<option value=\""+v+"\""+(v.equals(cur)?" selected":"")+">"+v.replace("_"," ")+"</option>"); }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Ticket #<%= t.getTicketId() %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .back-link { display:inline-block; margin-bottom:14px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
    .grid { display:grid; grid-template-columns:1fr 300px; gap:18px; align-items:start; }
    @media(max-width:780px){ .grid { grid-template-columns:1fr; } }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:20px; margin-bottom:18px; }
    .t-head { display:flex; justify-content:space-between; align-items:flex-start; gap:12px; margin-bottom:8px; }
    .t-subj { font-family:'Playfair Display',serif; font-size:21px; color:var(--green-dark); }
    .t-meta { font-size:12px; color:var(--muted); display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
    .t-desc { font-size:14px; line-height:1.6; white-space:pre-wrap; color:var(--text); }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 10px; border-radius:12px; text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; } .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-converted { background:var(--green-light); color:var(--success); } .st-closed { background:#EEE; color:#666; }
    .thread { display:flex; flex-direction:column; gap:12px; margin-bottom:18px; }
    .bub { max-width:88%; padding:11px 14px; border-radius:14px; font-size:14px; line-height:1.5; white-space:pre-wrap; box-shadow:var(--shadow); }
    .bub .who { font-size:11px; font-weight:700; margin-bottom:3px; opacity:.85; }
    .bub .at { font-size:10px; opacity:.7; margin-top:5px; }
    .bub.student { align-self:flex-start; background:#fff; border:1px solid var(--border); border-top-left-radius:3px; }
    .bub.student .who { color:#1B4F9C; }
    .bub.me { align-self:flex-end; background:var(--green-light); border-top-right-radius:3px; }
    .bub.me .who { color:var(--success); }
    label.lbl { display:block; font-size:12px; font-weight:600; margin-bottom:6px; }
    select, textarea { width:100%; padding:10px 12px; font-size:14px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:8px; background:var(--green-pale); }
    textarea { min-height:90px; resize:vertical; }
    .btn-submit { background:var(--green); color:#fff; border:none; padding:11px 20px; border-radius:8px; font-size:14px; font-weight:700; cursor:pointer; margin-top:10px; width:100%; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }
    .info-row { display:flex; justify-content:space-between; font-size:13px; padding:7px 0; border-bottom:1px solid var(--border); }
    .info-row:last-child { border-bottom:none; }
    .info-row span:first-child { color:var(--muted); }
  </style>
</head>
<body>
<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="tickets"/></jsp:include>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <a class="back-link" href="<%= ctx %>/manage-tickets">← All tickets</a>

  <div class="grid">
    <div>
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
            <div class="bub <%= r.isStaff() ? "me" : "student" %>">
              <div class="who"><%= r.isStaff() ? "👩‍🏫 " + esc(r.getSenderName()) : "🙋 " + esc(r.getSenderName()) + " (Student)" %></div>
              <%= esc(r.getMessage()) %>
              <div class="at"><%= when(r.getCreatedAt()) %></div>
            </div>
          <% } %>
        </div>
      <% } %>

      <div class="card">
        <form action="<%= ctx %>/manage-tickets" method="post">
          <input type="hidden" name="action" value="reply">
          <input type="hidden" name="ticketId" value="<%= t.getTicketId() %>">
          <label class="lbl">Reply to the student</label>
          <textarea name="message" placeholder="Type your response…" required></textarea>
          <button type="submit" class="btn-submit">💬 Send Reply</button>
        </form>
      </div>
    </div>

    <div>
      <div class="card">
        <label class="lbl">Update status</label>
        <form action="<%= ctx %>/manage-tickets" method="post">
          <input type="hidden" name="action" value="status">
          <input type="hidden" name="ticketId" value="<%= t.getTicketId() %>">
          <select name="status">
            <%= opt("OPEN", cur) %><%= opt("IN_PROGRESS", cur) %><%= opt("RESOLVED", cur) %><%= opt("CLOSED", cur) %>
          </select>
          <button type="submit" class="btn-submit">✔ Update Status</button>
        </form>
      </div>
      <div class="card">
        <label class="lbl" style="margin-bottom:10px;">Student</label>
        <div class="info-row"><span>Name</span><span><%= esc(t.getStudentName()) %></span></div>
        <div class="info-row"><span>Admission</span><span><%= esc(t.getAdmissionNo()) %></span></div>
        <div class="info-row"><span>Class</span><span><%= t.getClassName()==null?"—":esc(t.getClassName()) %></span></div>
        <div class="info-row"><span>Raised</span><span><%= when(t.getCreatedAt()) %></span></div>
      </div>
    </div>
  </div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>
</body>
</html>
