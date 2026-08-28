<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Reminder, com.tution.model.User,
                 com.tution.util.FeeCalculator, com.tution.util.Dates" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<Reminder> reminders = (List<Reminder>) request.getAttribute("reminders");
    Integer nFollowup = (Integer) request.getAttribute("nFollowup");
    Integer nDemo     = (Integer) request.getAttribute("nDemo");
    Integer nFee      = (Integer) request.getAttribute("nFee");
    Integer nPending  = (Integer) request.getAttribute("nPending");
    Integer sentWeek  = (Integer) request.getAttribute("sentWeek");
    Boolean autoSend  = (Boolean) request.getAttribute("autoSend");
    Boolean dryRun    = (Boolean) request.getAttribute("dryRun");
    String runHour    = (String) request.getAttribute("runHour");
    String kind       = (String) request.getAttribute("kind");
    String today      = (String) request.getAttribute("today");
    String error      = (String) request.getAttribute("error");
    String msg        = request.getParameter("msg");
    if (today == null) today = java.time.LocalDate.now().toString();
    if (kind == null) kind = "";
    int total = (reminders == null) ? 0 : reminders.size();
%>
<%!
    private String esc(String x) {
        if (x == null) return "";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String d(String x) { return (x == null || x.isEmpty()) ? "—" : esc(x); }
    private String tabCls(String cur, String want) { return cur.equals(want) ? " on" : ""; }
    private int nz(Integer i) { return i == null ? 0 : i; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reminders – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap;
      gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .head-right { display:flex; gap:9px; align-items:center; flex-wrap:wrap; }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700;
      padding:5px 12px; border-radius:20px; border:1px solid var(--border); }
    .count-pill.warn { background:#FFF4D6; color:#9A6B00; border-color:#F0D89A; }

    .btn { display:inline-block; text-decoration:none; font-size:13px; font-weight:700; padding:9px 16px;
      border-radius:8px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-primary[disabled] { background:#B9C4BD; cursor:not-allowed; }
    .btn-sm { font-size:11.5px; padding:6px 12px; }

    .tabs { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
    .tabs a { text-decoration:none; font-size:12.5px; font-weight:700; padding:8px 14px;
      border-radius:20px; border:1.5px solid var(--border); color:var(--muted); background:var(--white); }
    .tabs a.on { background:var(--green); color:#fff; border-color:var(--green); }

    .status-box { border-radius:var(--radius); padding:14px 16px; font-size:13px; margin-bottom:18px; }
    .status-box.off { background:#FFF6E0; border:1.5px solid #F0D89A; color:#7A5200; }
    .status-box.on { background:var(--green-light); border:1.5px solid #BFE3CE; color:var(--success); }
    .status-box.dry { background:#EFE7FF; border:2px solid #A98CE8; color:#3F2A75; }
    .status-box b { display:block; margin-bottom:3px; }
    .status-box code { background:rgba(0,0,0,.06); padding:1px 6px; border-radius:4px; font-size:12px; }

    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:900px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:11px 13px; font-size:11px;
      font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:10px 13px; border-bottom:1px solid var(--border); vertical-align:middle; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    table.lst tr.late td { background:#FFF5F4; }
    table.lst tr.done td { opacity:0.55; }
    .nm { font-weight:700; color:var(--green-dark); text-decoration:none; }
    .nm:hover { text-decoration:underline; }
    .kind { display:inline-block; font-size:10px; font-weight:800; padding:3px 9px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .k-FOLLOWUP { background:#DDEBFF; color:#1B4F9C; }
    .k-DEMO { background:#D9F2F7; color:#0F6C7E; }
    .k-FEE_DUE { background:#FFF4D6; color:#9A6B00; }
    .due-late { color:#C0392B; font-weight:700; }
    .sent-tag { font-size:11.5px; color:var(--success); font-weight:700; }
    .nomob { font-size:11.5px; color:#C0392B; }

    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid #BFE3CE; }
    .alert.info { background:#E6F0FF; color:#1B4F9C; border:1px solid #BBD3F5; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="reminders"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Reminder Queue</h2>
    <div class="head-right">
      <span class="count-pill"><%= total %> shown</span>
      <% if (nz(nPending) > 0) { %><span class="count-pill warn"><%= nz(nPending) %> not yet sent</span><% } %>
      <span class="count-pill"><%= nz(sentWeek) %> sent this week</span>
    </div>
  </div>

  <% if (msg != null) {
       String cls = "ok", text;
       if ("batch".equals(msg)) {
         String dry = request.getParameter("dry");
         if (dry != null && !"0".equals(dry)) {
           text = "Dry run — " + dry + " reminder(s) WOULD have been sent. Nothing was actually "
                + "messaged and nothing was marked as sent.";
           cls = "info";
         } else {
           text = "Sent " + request.getParameter("sent") + " reminder(s)."
                + " Skipped " + request.getParameter("skipped") + " already sent,"
                + " " + request.getParameter("failed") + " failed,"
                + " " + request.getParameter("nomob") + " had no mobile number.";
           if (!"0".equals(request.getParameter("failed"))) cls = "info";
         }
       } else if ("dry_run".equals(msg))   { text = "Dry run — this reminder was NOT sent. "
                                                  + "Nothing reached WhatsApp."; cls = "info";
       } else if ("sent".equals(msg))      { text = "Reminder sent.";
       } else if ("duplicate".equals(msg)) { text = "Already sent — nothing was re-sent."; cls = "info";
       } else if ("no_mobile".equals(msg)) { text = "No mobile number on file for that person."; cls = "error";
       } else if ("not_found".equals(msg)) { text = "That reminder is no longer due."; cls = "info";
       } else                              { text = "Send failed. The item stays in the queue and will be retried."; cls = "error"; }
  %>
    <div class="alert <%= cls %>"><%= text %></div>
  <% } %>

  <%-- Dry run is the loudest thing on the page on purpose: staff must never
       believe a parent was messaged when nothing left the building. --%>
  <% if (Boolean.TRUE.equals(dryRun)) { %>
    <div class="status-box dry">
      <b>🧪 DRY RUN — no messages are actually being sent</b>
      Every Send button below only <i>reports</i> what would go out; nothing reaches WhatsApp and
      nothing is marked as sent. This guard exists so test data and placeholder phone numbers
      cannot reach real families. Set <code>ReminderConfig.DRY_RUN = false</code> when you are
      ready to send for real.
    </div>
  <% } %>
  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <%-- The scheduler runs regardless; only the SENDING is gated on template
       approval. Say so plainly rather than letting staff wonder. --%>
  <% if (Boolean.TRUE.equals(autoSend)) { %>
    <div class="status-box on">
      <b>✅ Automatic sending is ON</b>
      The scheduler runs daily at <code><%= esc(runHour) %></code> and sends everything in this queue.
      You can still send an individual reminder early with the buttons below.
    </div>
  <% } else { %>
    <div class="status-box off">
      <b>⏸ Automatic sending is OFF — send by hand for now</b>
      The daily job runs at <code><%= esc(runHour) %></code> and keeps this queue and the overdue
      flags up to date, but it will not message anyone until the three WhatsApp templates
      (<code>followup_reminder</code>, <code>demo_reminder</code>, <code>fee_due_reminder</code>)
      are approved by Meta. Once they are, set
      <code>ReminderConfig.AUTO_SEND = true</code> — nothing else needs changing.
    </div>
  <% } %>

  <div class="tabs">
    <a class="<%= "".equals(kind) ? "on" : "" %>" href="<%= ctx %>/reminders">All</a>
    <a class="<%= tabCls(kind,"FOLLOWUP") %>" href="<%= ctx %>/reminders?kind=FOLLOWUP">📞 Follow-ups (<%= nz(nFollowup) %>)</a>
    <a class="<%= tabCls(kind,"DEMO") %>" href="<%= ctx %>/reminders?kind=DEMO">🎓 Counsellors (<%= nz(nDemo) %>)</a>
    <a class="<%= tabCls(kind,"FEE_DUE") %>" href="<%= ctx %>/reminders?kind=FEE_DUE">💰 Fees Due (<%= nz(nFee) %>)</a>
  </div>

  <% if (reminders == null || reminders.isEmpty()) { %>
    <div class="table-wrap">
      <div class="empty">
        <div class="ic">🔔</div>
        <p>Nothing due. No reminders to send right now.</p>
      </div>
    </div>
  <% } else { %>
    <form method="post" action="<%= ctx %>/reminders" style="margin-bottom:14px;"
          onsubmit="return confirm('Send every unsent reminder in this list?');">
      <input type="hidden" name="action" value="sendall">
      <input type="hidden" name="kind" value="<%= esc(kind) %>">
      <button type="submit" class="btn btn-primary" <%= nz(nPending) == 0 ? "disabled" : "" %>>
        📤 Send all unsent<%= "".equals(kind) ? "" : " in this tab" %>
      </button>
      <span style="font-size:12px;color:var(--muted);margin-left:10px;">
        Already-sent items are skipped automatically.</span>
    </form>

    <div class="table-wrap">
      <table class="lst">
        <thead>
          <tr><th>Type</th><th>Due</th><th>Name</th><th>Sends To</th>
              <th>Detail</th><th>Counsellor</th><th>Status</th><th>Action</th></tr>
        </thead>
        <tbody>
        <% for (Reminder r : reminders) {
             boolean late = r.isOverdue(today);
             String target = r.targetMobile();
             boolean hasMobile = target != null && !target.isEmpty(); %>
          <tr class="<%= r.isAlreadySent() ? "done" : (late ? "late" : "") %>">
            <td><span class="kind k-<%= esc(r.getKind()) %>"><%= r.getIcon() %> <%= r.getKindLabel() %></span></td>
            <td class="<%= late ? "due-late" : "" %>"><%= d(Dates.display(r.getDueDate())) %></td>
            <td>
              <% if (r.getInquiryId() != null) { %>
                <a class="nm" href="<%= ctx %>/lead?id=<%= r.getInquiryId() %>"><%= esc(r.getName()) %></a>
              <% } else if (r.getStudentId() != null) { %>
                <a class="nm" href="<%= ctx %>/student?id=<%= r.getStudentId() %>"><%= esc(r.getName()) %></a>
              <% } else { %><%= esc(r.getName()) %><% } %>
            </td>
            <td>
              <% if (hasMobile) { %><%= esc(target) %>
                <% if (r.getParentMobile() != null && !r.getParentMobile().isEmpty()) { %>
                  <span style="font-size:11px;color:var(--muted);">(parent)</span>
                <% } %>
              <% } else { %><span class="nomob">no mobile</span><% } %>
            </td>
            <td>
              <%= d(r.getDetail()) %>
              <% if ("FEE_DUE".equals(r.getKind())) { %>
                <b> · <%= FeeCalculator.inr(r.getAmount()) %></b>
              <% } %>
            </td>
            <td><%= d(r.getCounsellorName()) %></td>
            <td>
              <% if (r.isAlreadySent()) { %><span class="sent-tag">✓ sent</span>
              <% } else if (late) { %><span class="due-late">overdue</span>
              <% } else { %>pending<% } %>
            </td>
            <td>
              <% if (!r.isAlreadySent() && hasMobile) { %>
                <form method="post" action="<%= ctx %>/reminders" style="margin:0;">
                  <input type="hidden" name="action" value="send">
                  <input type="hidden" name="itemKind" value="<%= esc(r.getKind()) %>">
                  <input type="hidden" name="refId" value="<%= r.getRefId() %>">
                  <input type="hidden" name="kind" value="<%= esc(kind) %>">
                  <button type="submit" class="btn btn-primary btn-sm">Send</button>
                </form>
              <% } else if (!hasMobile) { %>
                <span style="font-size:11.5px;color:var(--muted);">—</span>
              <% } else { %>
                <span style="font-size:11.5px;color:var(--muted);">done</span>
              <% } %>
            </td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
