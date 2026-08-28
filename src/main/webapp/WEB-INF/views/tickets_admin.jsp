<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Ticket, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<Ticket> tickets = (List<Ticket>) request.getAttribute("tickets");
    String error = (String) request.getAttribute("error");
    int total = (tickets == null) ? 0 : tickets.size();
    int open = 0; if (tickets != null) for (Ticket t : tickets) if ("OPEN".equals(t.getStatus())||"IN_PROGRESS".equals(t.getStatus())) open++;
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
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Support Tickets – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:20px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700; padding:4px 12px; border-radius:20px; border:1px solid var(--border); }
    .count-pill.amber { background:#FFF4D6; color:#9A6B00; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:880px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:12px 14px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:11px 14px; border-bottom:1px solid var(--border); color:var(--text); vertical-align:middle; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 10px; border-radius:12px; text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; } .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-converted { background:var(--green-light); color:var(--success); } .st-closed { background:#EEE; color:#666; }
    .prio-HIGH { color:#C0392B; font-weight:700; } .prio-LOW { color:#888; }
    .btn-convert { display:inline-block; background:var(--green); color:#fff; text-decoration:none; font-size:12px; font-weight:700; padding:7px 13px; border-radius:6px; white-space:nowrap; }
    .btn-convert:hover { background:var(--green-dark); }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
  </style>
</head>
<body>
<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="tickets"/></jsp:include>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <div class="list-head">
    <h2>🎫 Support Tickets</h2>
    <div style="display:flex;gap:8px;">
      <span class="count-pill amber"><%= open %> open</span>
      <span class="count-pill"><%= total %> total</span>
    </div>
  </div>

  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <% if (tickets == null || tickets.isEmpty()) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">🗒️</div><p>No tickets raised yet.</p></div></div>
  <% } else { %>
    <div class="filter-bar">
      <input type="text" id="fSearch" placeholder="Search subject, student, category…" oninput="applyFilters()">
      <select data-filter data-col="5" onchange="applyFilters()">
        <option value="">All statuses</option>
        <option value="OPEN">Open</option><option value="IN PROGRESS">In Progress</option>
        <option value="RESOLVED">Resolved</option><option value="CLOSED">Closed</option>
      </select>
      <span class="fcount"><b id="fCount"></b> shown</span>
    </div>
    <div class="table-wrap">
      <table class="lst" id="fTable">
        <thead>
          <tr><th>#</th><th>Subject</th><th>Student</th><th>Category</th><th>Priority</th><th>Status</th><th>Replies</th><th>Updated</th><th>Action</th></tr>
        </thead>
        <tbody>
          <% for (Ticket t : tickets) { %>
          <tr>
            <td><%= t.getTicketId() %></td>
            <td class="nm"><%= esc(t.getSubject()) %></td>
            <td><%= esc(t.getStudentName()) %><br><span style="font-size:11px;color:var(--muted);"><%= esc(t.getAdmissionNo()) %></span></td>
            <td><%= esc(t.getCategory()) %></td>
            <td class="prio-<%= esc(t.getPriority()) %>"><%= esc(t.getPriority()) %></td>
            <td><span class="badge <%= badge(t.getStatus()) %>"><%= label(t.getStatus()) %></span></td>
            <td style="text-align:center;"><%= t.getReplyCount() %></td>
            <td><%= when(t.getUpdatedAt()) %></td>
            <td><a class="btn-convert" href="<%= ctx %>/manage-tickets?id=<%= t.getTicketId() %>">Open →</a></td>
          </tr>
          <% } %>
        </tbody>
      </table>
    </div>
  <% } %>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>
<script src="<%= ctx %>/js/filter.js"></script>
</body>
</html>
