<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.AttendanceSummary, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<AttendanceSummary> rows = (List<AttendanceSummary>) request.getAttribute("rows");
    String from = (String) request.getAttribute("from");
    String to   = (String) request.getAttribute("to");
    String error = (String) request.getAttribute("error");
    int count = (rows==null)?0:rows.size();
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private String pctClass(int p){ if(p>=75)return "pc-good"; if(p>=50)return "pc-mid"; return "pc-low"; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Attendance Report – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700; padding:4px 12px; border-radius:20px; border:1px solid var(--border); }
    .toolbar { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 18px; margin-bottom:18px; display:flex; flex-wrap:wrap; align-items:flex-end; gap:14px; }
    .toolbar .fld label { display:block; font-size:12px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .toolbar input { padding:9px 11px; font-size:14px; border:1.5px solid var(--border); border-radius:7px; background:var(--green-pale); font-family:'Inter',sans-serif; }
    .btn { padding:9px 16px; border:none; border-radius:7px; font-size:13px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; text-decoration:none; display:inline-block; }
    .btn-go { background:var(--green-dark); color:#fff; }
    .btn-mark { background:transparent; color:var(--green-dark); border:1.5px solid var(--green-dark); margin-left:auto; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.rep { width:100%; border-collapse:collapse; font-size:13px; min-width:720px; }
    table.rep th { background:var(--green); color:#fff; text-align:left; padding:11px 14px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.rep td { padding:10px 14px; border-bottom:1px solid var(--border); }
    table.rep tr:last-child td { border-bottom:none; }
    table.rep tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .bar { position:relative; height:18px; background:#eee; border-radius:10px; overflow:hidden; min-width:90px; }
    .bar > span { position:absolute; left:0; top:0; bottom:0; border-radius:10px; }
    .pct { font-weight:800; font-size:13px; margin-left:8px; }
    .pc-good { color:var(--success); } .pc-good.fill { background:var(--green); }
    .pc-mid  { color:#B8860B; }        .pc-mid.fill  { background:#E0A800; }
    .pc-low  { color:#C0392B; }        .pc-low.fill  { background:#C0392B; }
    .pcell { display:flex; align-items:center; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
    .empty { text-align:center; padding:42px 20px; color:var(--muted); }
    .empty .ic { font-size:40px; margin-bottom:10px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="attendance"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Attendance Report</h2>
    <span class="count-pill"><%= count %> students</span>
  </div>

  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <form class="toolbar" method="get" action="<%= ctx %>/attendance-report">
    <div class="fld"><label>From</label><input type="date" name="from" value="<%= esc(from) %>"/></div>
    <div class="fld"><label>To</label><input type="date" name="to" value="<%= esc(to) %>"/></div>
    <button type="submit" class="btn btn-go">Apply</button>
    <a class="btn btn-mark" href="<%= ctx %>/attendance">✏ Mark Attendance</a>
  </form>

  <% if (rows == null || rows.isEmpty()) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">📊</div><p>No data for this range.</p></div></div>
  <% } else { %>
    <div class="table-wrap">
      <table class="rep">
        <thead>
          <tr><th>#</th><th>Name</th><th>Class</th><th>Present</th><th>Absent</th>
              <th>Late</th><th>Leave</th><th>Total</th><th>Attendance %</th></tr>
        </thead>
        <tbody>
          <% int i=1; for (AttendanceSummary a : rows) { int p=a.percentage(); String cl=pctClass(p); %>
          <tr>
            <td><%= i++ %></td>
            <td class="nm"><%= esc(a.getFullName()) %></td>
            <td><%= a.getClassName()==null?"—":esc(a.getClassName()) %></td>
            <td><%= a.getPresent() %></td>
            <td><%= a.getAbsent() %></td>
            <td><%= a.getLate() %></td>
            <td><%= a.getLeave() %></td>
            <td><%= a.total() %></td>
            <td>
              <div class="pcell">
                <div class="bar"><span class="<%= cl %> fill" style="width:<%= p %>%"></span></div>
                <span class="pct <%= cl %>"><%= a.total()==0?"—":(p+"%") %></span>
              </div>
            </td>
          </tr>
          <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="attendance"/></jsp:include>

</body>
</html>
