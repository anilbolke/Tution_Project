<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.LeadDemo, com.tution.model.User, com.tution.util.Dates" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<LeadDemo> demos  = (List<LeadDemo>) request.getAttribute("demos");
    @SuppressWarnings("unchecked")
    List<LeadDemo> missed = (List<LeadDemo>) request.getAttribute("missed");
    String ffrom   = (String) request.getAttribute("ffrom");
    String fto     = (String) request.getAttribute("fto");
    String fstatus = (String) request.getAttribute("fstatus");
    String error   = (String) request.getAttribute("error");
    String msg     = request.getParameter("msg");

    String today = java.time.LocalDate.now().toString();
    int total = (demos == null) ? 0 : demos.size();
    int missedCount = (missed == null) ? 0 : missed.size();
    int todayCount = 0;
    if (demos != null) {
        for (LeadDemo d : demos) { if (today.equals(d.getDemoDate()) && d.isOpen()) todayCount++; }
    }
%>
<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String v(String s) { return s == null ? "" : esc(s); }
    private String d(String s) { return (s == null || s.isEmpty()) ? "—" : esc(s); }
    private String sel(String current, String option) {
        return option.equals(current == null ? "" : current) ? " selected" : "";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Counsellor – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .head-right { display:flex; gap:9px; align-items:center; flex-wrap:wrap; }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700;
      padding:5px 12px; border-radius:20px; border:1px solid var(--border); }
    .count-pill.warn { background:#FDE2E0; color:#C0392B; border-color:#F5C6CB; }

    .filters { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:16px 18px; margin-bottom:16px; display:grid; grid-template-columns:repeat(4,1fr) auto;
      gap:10px 12px; align-items:end; }
    @media(max-width:800px){ .filters { grid-template-columns:repeat(2,1fr); } }
    .filters .f { display:flex; flex-direction:column; }
    .filters label { font-size:10.5px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--muted); margin-bottom:4px; }
    .filters input, .filters select { padding:8px 10px; border:1.5px solid var(--border);
      border-radius:7px; font-size:13px; font-family:inherit; }

    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      overflow-x:auto; margin-bottom:20px; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:980px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:11px 13px; font-size:11px;
      font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:10px 13px; border-bottom:1px solid var(--border); vertical-align:middle; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    table.lst tr.miss td { background:#FFF5F4; }
    table.lst tr.istoday td { background:#FFFBEA; }
    .nm { font-weight:700; color:var(--green-dark); text-decoration:none; }
    .nm:hover { text-decoration:underline; }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 9px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .dm-SCHEDULED { background:#D9F2F7; color:#0F6C7E; }
    .dm-COMPLETED { background:var(--green-light); color:var(--success); }
    .dm-NO_SHOW { background:#FDE2E0; color:#C0392B; }
    .dm-CANCELLED { background:#EEE; color:#666; }
    .stars { color:#D69E00; letter-spacing:1px; }
    .btn { display:inline-block; text-decoration:none; font-size:12px; font-weight:700; padding:6px 12px;
      border-radius:6px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }

    .fbrow td { background:#F7FCF9 !important; padding:14px 16px; }
    .fbform { display:grid; grid-template-columns:160px 150px 1fr 160px auto; gap:10px; align-items:end; }
    @media(max-width:900px){ .fbform { grid-template-columns:1fr 1fr; } }
    .fbform .fld { display:flex; flex-direction:column; }
    .fbform label { font-size:10.5px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--muted); margin-bottom:4px; }
    .fbform input, .fbform select, .fbform textarea { padding:8px 10px; border:1.5px solid var(--border);
      border-radius:7px; font-size:13px; font-family:inherit; }
    .fbform textarea { resize:vertical; min-height:38px; }
    details.q > summary { cursor:pointer; list-style:none; }
    details.q > summary::-webkit-details-marker { display:none; }

    .sec-title { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:#C0392B;
      font-weight:800; margin:4px 0 10px; }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid #BFE3CE; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="demos"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Demo Schedule</h2>
    <div class="head-right">
      <span class="count-pill"><%= todayCount %> today</span>
      <span class="count-pill"><%= total %> in range</span>
      <% if (missedCount > 0) { %>
        <span class="count-pill warn"><%= missedCount %> awaiting outcome</span>
      <% } %>
    </div>
  </div>

  <% if (msg != null) { %>
    <div class="alert <%= "error".equals(msg) ? "error" : "ok" %>"><%=
        "saved".equals(msg) ? "Demo updated." :
        "error".equals(msg) ? "Something went wrong. Please try again." : "Done." %></div>
  <% } %>
  <% if (error != null) { %>
    <div class="alert error"><%= esc(error) %></div>
  <% } %>

  <form class="filters" method="get" action="<%= ctx %>/demo">
    <div class="f"><label>From</label><input type="date" name="from" value="<%= v(ffrom) %>"></div>
    <div class="f"><label>To</label><input type="date" name="to" value="<%= v(fto) %>"></div>
    <div class="f">
      <label>Status</label>
      <select name="status">
        <option value=""<%= sel(fstatus,"") %>>All</option>
        <option value="SCHEDULED"<%= sel(fstatus,"SCHEDULED") %>>Scheduled</option>
        <option value="COMPLETED"<%= sel(fstatus,"COMPLETED") %>>Attended</option>
        <option value="NO_SHOW"<%= sel(fstatus,"NO_SHOW") %>>Did not attend</option>
        <option value="CANCELLED"<%= sel(fstatus,"CANCELLED") %>>Cancelled</option>
      </select>
    </div>
    <div class="f"><label>&nbsp;</label><button type="submit" class="btn btn-primary" style="padding:9px 18px;">Apply</button></div>
    <div class="f"><label>&nbsp;</label><a href="<%= ctx %>/demo" style="font-size:12.5px;color:var(--muted);">Reset</a></div>
  </form>

  <%-- Demos whose date has passed while still SCHEDULED. These are the ones
       that quietly rot, so they get their own block above the diary. --%>
  <% if (missedCount > 0) { %>
    <div class="sec-title">⚠ Past Counsellors with no outcome recorded</div>
    <div class="table-wrap">
      <table class="lst">
        <thead>
          <tr><th>Date</th><th>Student</th><th>Mobile</th><th>Subject</th>
              <th>Faculty</th><th>Counsellor</th><th>Action</th></tr>
        </thead>
        <tbody>
        <% for (LeadDemo dm : missed) { %>
          <tr class="miss">
            <td><b><%= d(dm.getDemoDate()) %></b> <%= v(dm.getDemoTime()) %></td>
            <td><a class="nm" href="<%= ctx %>/lead?id=<%= dm.getInquiryId() %>"><%= esc(dm.getLeadName()) %></a></td>
            <td><%= d(dm.getLeadMobile()) %></td>
            <td><%= d(dm.getSubject()) %></td>
            <td><%= d(dm.getFacultyName()) %></td>
            <td><%= d(dm.getCounsellorName()) %></td>
            <td>
              <details class="q"><summary><span class="btn btn-primary">Record outcome</span></summary></details>
            </td>
          </tr>
          <tr class="fbrow" style="display:none;">
            <td colspan="7">
              <form method="post" action="<%= ctx %>/demo" class="fbform">
                <input type="hidden" name="action" value="feedback">
                <input type="hidden" name="demoId" value="<%= dm.getDemoId() %>">
                <input type="hidden" name="back" value="diary">
                <div class="fld">
                  <label>Outcome</label>
                  <select name="demoStatus">
                    <option value="COMPLETED">Attended</option>
                    <option value="NO_SHOW">Did not attend</option>
                    <option value="CANCELLED">Cancelled</option>
                  </select>
                </div>
                <div class="fld">
                  <label>Rating</label>
                  <select name="rating">
                    <option value="">—</option>
                    <option value="5">★★★★★</option><option value="4">★★★★☆</option>
                    <option value="3">★★★☆☆</option><option value="2">★★☆☆☆</option>
                    <option value="1">★☆☆☆☆</option>
                  </select>
                </div>
                <div class="fld"><label>Feedback</label><textarea name="feedback" maxlength="500"></textarea></div>
                <div class="fld"><label>Next Follow-up</label><input type="datetime-local" name="nextFollowupDate"></div>
                <div class="fld"><button type="submit" class="btn btn-primary" style="padding:9px 16px;">Save</button></div>
              </form>
            </td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

  <% if (demos == null || demos.isEmpty()) { %>
    <div class="table-wrap">
      <div class="empty">
        <div class="ic">🎓</div>
        <p>No Counsellors in this date range.</p>
        <p style="margin-top:8px;font-size:12.5px;">Book one from any lead's page.</p>
      </div>
    </div>
  <% } else { %>
    <div class="table-wrap">
      <table class="lst">
        <thead>
          <tr><th>Date</th><th>Time</th><th>Student</th><th>Mobile</th><th>Subject</th>
              <th>Faculty</th><th>Mode</th><th>Status</th><th>Rating</th><th>Action</th></tr>
        </thead>
        <tbody>
        <% for (LeadDemo dm : demos) {
             boolean isToday = today.equals(dm.getDemoDate());
             boolean miss = dm.isMissed(today); %>
          <tr class="<%= miss ? "miss" : (isToday ? "istoday" : "") %>">
            <td><b><%= d(dm.getDemoDate()) %></b></td>
            <td><%= d(dm.getDemoTime()) %></td>
            <td><a class="nm" href="<%= ctx %>/lead?id=<%= dm.getInquiryId() %>"><%= esc(dm.getLeadName()) %></a></td>
            <td><%= d(dm.getLeadMobile()) %></td>
            <td><%= d(dm.getSubject()) %></td>
            <td><%= d(dm.getFacultyName()) %></td>
            <td><%= d(dm.getMode()) %></td>
            <td><span class="badge dm-<%= esc(dm.getStatus()) %>"><%= esc(dm.getStatus()) %></span></td>
            <td class="stars"><%= dm.getRating() == null ? "—" : dm.getStars() %></td>
            <td>
              <% if (dm.isOpen()) { %>
                <details class="q"><summary><span class="btn btn-primary">Record outcome</span></summary></details>
              <% } else { %>
                <a class="nm" style="font-size:12px;" href="<%= ctx %>/lead?id=<%= dm.getInquiryId() %>">Open lead →</a>
              <% } %>
            </td>
          </tr>
          <% if (dm.isOpen()) { %>
          <tr class="fbrow" style="display:none;">
            <td colspan="10">
              <form method="post" action="<%= ctx %>/demo" class="fbform">
                <input type="hidden" name="action" value="feedback">
                <input type="hidden" name="demoId" value="<%= dm.getDemoId() %>">
                <input type="hidden" name="back" value="diary">
                <div class="fld">
                  <label>Outcome</label>
                  <select name="demoStatus">
                    <option value="COMPLETED">Attended</option>
                    <option value="NO_SHOW">Did not attend</option>
                    <option value="CANCELLED">Cancelled</option>
                  </select>
                </div>
                <div class="fld">
                  <label>Rating</label>
                  <select name="rating">
                    <option value="">—</option>
                    <option value="5">★★★★★</option><option value="4">★★★★☆</option>
                    <option value="3">★★★☆☆</option><option value="2">★★☆☆☆</option>
                    <option value="1">★☆☆☆☆</option>
                  </select>
                </div>
                <div class="fld"><label>Feedback</label><textarea name="feedback" maxlength="500"></textarea></div>
                <div class="fld"><label>Next Follow-up</label><input type="datetime-local" name="nextFollowupDate"></div>
                <div class="fld"><button type="submit" class="btn btn-primary" style="padding:9px 16px;">Save</button></div>
              </form>
            </td>
          </tr>
          <% } %>
        <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

<script>
  document.querySelectorAll('details.q').forEach(function (dt) {
    dt.addEventListener('toggle', function () {
      var row = dt.closest('tr').nextElementSibling;
      if (row && row.classList.contains('fbrow')) {
        row.style.display = dt.open ? 'table-row' : 'none';
      }
    });
  });
</script>
</body>
</html>
