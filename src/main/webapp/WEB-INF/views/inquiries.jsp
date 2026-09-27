<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.Inquiry, com.tution.model.User, com.tution.util.Dates" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<Inquiry> inquiries = (List<Inquiry>) request.getAttribute("inquiries");
    @SuppressWarnings("unchecked")
    Map<Integer,String> counsellors = (Map<Integer,String>) request.getAttribute("counsellors");
    @SuppressWarnings("unchecked")
    List<String> sources = (List<String>) request.getAttribute("sources");
    String error = (String) request.getAttribute("error");
    int total = (inquiries == null) ? 0 : inquiries.size();

    // echoed filter state
    String fq          = (String) request.getAttribute("fq");
    String fstatus     = (String) request.getAttribute("fstatus");
    String fpriority   = (String) request.getAttribute("fpriority");
    String fsource     = (String) request.getAttribute("fsource");
    String ffrom       = (String) request.getAttribute("ffrom");
    String fto         = (String) request.getAttribute("fto");
    String fcounsellor = (String) request.getAttribute("fcounsellor");
    Boolean foverdue   = (Boolean) request.getAttribute("foverdue");
    boolean overdueOn  = Boolean.TRUE.equals(foverdue);
    String bulkMsg     = request.getParameter("bulkMsg");
    boolean canBulkAssign = user.isAdmin();
    String returnQs    = request.getQueryString();

    String today = java.time.LocalDate.now().toString();
    int overdueCount = 0;
    if (inquiries != null) {
        for (Inquiry q : inquiries) { if (q.isOverdue(today)) overdueCount++; }
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
    private String badgeClass(String status) {
        if (status == null) return "st-new";
        switch (status) {
            case "CONVERTED":         return "st-converted";
            case "CONTACTED":         return "st-contacted";
            case "INTERESTED":        return "st-interested";
            case "DEMO_PENDING":
            case "DEMO_COMPLETED":    return "st-demo";
            case "FOLLOWUP_REQUIRED": return "st-followup";
            case "NOT_INTERESTED":
            case "LOST":              return "st-lost";
            default:                  return "st-new";
        }
    }
    private String prioClass(String p) {
        if ("Hot".equals(p))  return "pr-hot";
        if ("Cold".equals(p)) return "pr-cold";
        return "pr-warm";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Leads – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .head-right { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700;
      padding:5px 12px; border-radius:20px; border:1px solid var(--border); }
    .count-pill.warn { background:#FDE2E0; color:#C0392B; border-color:#F5C6CB; }
    .btn { display:inline-block; text-decoration:none; font-size:13px; font-weight:700; padding:10px 18px;
      border-radius:8px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }

    .filters { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:16px 18px; margin-bottom:16px; }
    .filters .row { display:grid; grid-template-columns:repeat(6,1fr); gap:10px 12px; }
    @media(max-width:1000px){ .filters .row { grid-template-columns:repeat(3,1fr); } }
    @media(max-width:640px){ .filters .row { grid-template-columns:repeat(2,1fr); } }
    .filters .f { display:flex; flex-direction:column; }
    .filters label { font-size:10.5px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--muted); margin-bottom:4px; }
    .filters input, .filters select { padding:8px 10px; border:1.5px solid var(--border); border-radius:7px;
      font-size:13px; font-family:inherit; }
    .filters .acts { display:flex; gap:9px; align-items:center; margin-top:12px; flex-wrap:wrap; }
    .chk { display:flex; align-items:center; gap:6px; font-size:12.5px; color:var(--text); }
    .link-clear { font-size:12.5px; color:var(--muted); text-decoration:none; }

    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:1180px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:11px 13px; font-size:11px;
      font-weight:700; text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    table.lst td { padding:10px 13px; border-bottom:1px solid var(--border); color:var(--text); vertical-align:middle;
      white-space:nowrap; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    table.lst tr.overdue td { background:#FFF5F4; }

    /* Eleven columns never fit a screen, so this table scrolls sideways. Pin the
       Action cell to the right edge so Convert stays reachable without scrolling
       there first. The row backgrounds are repeated per state because a sticky
       cell slides over its neighbours and would otherwise be transparent. */
    table.lst thead th:last-child,
    table.lst tbody td:last-child { position:sticky; right:0; }
    table.lst thead th:last-child { background:var(--green); z-index:3; }
    table.lst tbody td:last-child { background:var(--white); z-index:2;
      box-shadow:-7px 0 7px -7px rgba(0,0,0,0.22); }
    table.lst tbody tr:nth-child(even) td:last-child { background:var(--green-pale); }
    table.lst tbody tr.overdue td:last-child { background:#FFF5F4; }
    .nm { font-weight:700; color:var(--green-dark); text-decoration:none; }
    .nm:hover { text-decoration:underline; }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 9px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; }
    .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-interested { background:#E4DDFF; color:#4B2E9C; }
    .st-demo { background:#D9F2F7; color:#0F6C7E; }
    .st-followup { background:#FFE6D6; color:#9C4A16; }
    .st-converted { background:var(--green-light); color:var(--success); }
    .st-lost { background:#EEE; color:#666; }
    .pr-hot { background:#FDE2E0; color:#C0392B; }
    .pr-warm { background:#FFF4D6; color:#9A6B00; }
    .pr-cold { background:#E6F0FF; color:#1B4F9C; }
    .due-late { color:#C0392B; font-weight:700; }
    /* Due date over due time — this table is already wide enough. */
    .due-tm { font-size:11px; font-weight:600; color:var(--muted); margin-top:2px; }
    .due-late .due-tm { color:#C0392B; }
    .btn-convert { display:inline-block; background:var(--green); color:#fff; text-decoration:none; font-size:12px;
      font-weight:700; padding:6px 12px; border-radius:6px; white-space:nowrap; }
    .btn-convert:hover { background:var(--green-dark); }
    .done-txt { font-size:12px; color:var(--success); font-weight:600; }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px;
      border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.success { background:var(--green-light); color:var(--success); border:1px solid var(--border);
      padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }

    .bulk-bar { display:flex; align-items:center; gap:10px; flex-wrap:wrap; background:var(--white);
      border-radius:var(--radius); box-shadow:var(--shadow); padding:12px 16px; margin-bottom:12px; }
    .bulk-bar .lbl { font-size:12.5px; font-weight:700; color:var(--text); }
    .bulk-bar select { padding:8px 10px; border:1.5px solid var(--border); border-radius:7px;
      font-size:13px; font-family:inherit; }
    .bulk-bar .count { font-size:12.5px; color:var(--muted); }
    .bulk-bar button:disabled { opacity:0.5; cursor:not-allowed; }
    td.chk-col, th.chk-col { width:32px; text-align:center; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="leads"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Leads &amp; Enquiries</h2>
    <div class="head-right">
      <span class="count-pill"><%= total %> shown</span>
      <% if (overdueCount > 0) { %>
        <span class="count-pill warn"><%= overdueCount %> overdue</span>
      <% } %>
      <a class="btn btn-primary" href="<%= ctx %>/lead?new=1">➕ New Enquiry</a>
    </div>
  </div>

  <% if (error != null) { %>
    <div class="alert error"><%= esc(error) %></div>
  <% } %>
  <% if (bulkMsg != null && bulkMsg.matches("\\d+")) {
       int n = Integer.parseInt(bulkMsg); %>
    <div class="alert success"><%= n %> lead<%= n == 1 ? "" : "s" %> assigned to the counsellor.</div>
  <% } %>

  <form class="filters" method="get" action="<%= ctx %>/inquiries">
    <div class="row">
      <div class="f">
        <label>Search</label>
        <input type="text" name="q" value="<%= v(fq) %>" placeholder="Name, mobile, email">
      </div>
      <div class="f">
        <label>Status</label>
        <select name="status">
          <option value=""<%= sel(fstatus,"") %>>All</option>
          <option value="NEW"<%= sel(fstatus,"NEW") %>>New</option>
          <option value="CONTACTED"<%= sel(fstatus,"CONTACTED") %>>Contacted</option>
          <option value="INTERESTED"<%= sel(fstatus,"INTERESTED") %>>Interested</option>
          <option value="DEMO_PENDING"<%= sel(fstatus,"DEMO_PENDING") %>>Counsellor Pending</option>
          <option value="DEMO_COMPLETED"<%= sel(fstatus,"DEMO_COMPLETED") %>>Counsellor Completed</option>
          <option value="FOLLOWUP_REQUIRED"<%= sel(fstatus,"FOLLOWUP_REQUIRED") %>>Follow-up Required</option>
          <option value="CONVERTED"<%= sel(fstatus,"CONVERTED") %>>Converted</option>
          <option value="NOT_INTERESTED"<%= sel(fstatus,"NOT_INTERESTED") %>>Not Interested</option>
          <option value="LOST"<%= sel(fstatus,"LOST") %>>Lost</option>
        </select>
      </div>
      <div class="f">
        <label>Priority</label>
        <select name="priority">
          <option value=""<%= sel(fpriority,"") %>>All</option>
          <option value="Hot"<%= sel(fpriority,"Hot") %>>Hot</option>
          <option value="Warm"<%= sel(fpriority,"Warm") %>>Warm</option>
          <option value="Cold"<%= sel(fpriority,"Cold") %>>Cold</option>
        </select>
      </div>
      <div class="f">
        <label>Source</label>
        <select name="source">
          <option value=""<%= sel(fsource,"") %>>All</option>
          <% if (sources != null) for (String s : sources) { %>
            <option value="<%= esc(s) %>"<%= sel(fsource, s) %>><%= esc(s) %></option>
          <% } %>
        </select>
      </div>
      <div class="f">
        <label>From</label>
        <input type="date" name="from" value="<%= v(ffrom) %>">
      </div>
      <div class="f">
        <label>To</label>
        <input type="date" name="to" value="<%= v(fto) %>">
      </div>
      <% if (counsellors != null && counsellors.size() > 1) { %>
      <div class="f">
        <label>Counsellor</label>
        <select name="counsellor">
          <option value="">All</option>
          <% if (counsellors != null) for (Map.Entry<Integer,String> e : counsellors.entrySet()) {
               boolean s = String.valueOf(e.getKey()).equals(fcounsellor); %>
            <option value="<%= e.getKey() %>"<%= s ? " selected" : "" %>><%= esc(e.getValue()) %></option>
          <% } %>
        </select>
      </div>
      <% } %>
    </div>
    <div class="acts">
      <label class="chk"><input type="checkbox" name="overdue" value="1"<%= overdueOn ? " checked" : "" %>> Overdue follow-ups only</label>
      <button type="submit" class="btn btn-primary">Apply</button>
      <a class="link-clear" href="<%= ctx %>/inquiries">Clear filters</a>
    </div>
  </form>

  <% if (inquiries == null || inquiries.isEmpty()) { %>
    <div class="table-wrap">
      <div class="empty">
        <div class="ic">📭</div>
        <p>No leads match these filters.</p>
        <p style="margin-top:12px;"><a class="btn btn-primary" href="<%= ctx %>/lead?new=1">➕ Create the first enquiry</a></p>
      </div>
    </div>
  <% } else { %>
    <% if (canBulkAssign) { %>
    <form id="bulkForm" method="post" action="<%= ctx %>/inquiries">
      <input type="hidden" name="action" value="bulk-assign">
      <input type="hidden" name="qs" value="<%= v(returnQs) %>">
      <div class="bulk-bar">
        <span class="lbl">Bulk assign counsellor:</span>
        <select name="counsellorId" id="bulkCounsellor">
          <option value="">Choose counsellor…</option>
          <% if (counsellors != null) for (Map.Entry<Integer,String> e : counsellors.entrySet()) { %>
            <option value="<%= e.getKey() %>"><%= esc(e.getValue()) %></option>
          <% } %>
        </select>
        <button type="submit" class="btn btn-primary" id="bulkAssignBtn" disabled>Assign Selected</button>
        <span class="count" id="bulkCount">0 selected</span>
      </div>
    <% } %>
    <div class="table-wrap">
      <table class="lst">
        <thead>
          <tr>
            <% if (canBulkAssign) { %><th class="chk-col"><input type="checkbox" id="chkAll"></th><% } %>
            <th>#</th><th>Name</th><th>Mobile</th><th>Course Interest</th>
            <th>Priority</th><th>Status</th><th>Counsellor</th>
            <th>Next Follow-up</th><th>Source</th><th>Received</th><th>Action</th>
          </tr>
        </thead>
        <tbody>
          <% int i = 1; for (Inquiry q : inquiries) {
                boolean converted = q.isConverted();
                boolean late = q.isOverdue(today);
                boolean hasCounsellor = q.getCounsellorId() != null;
                String course = (q.getCourseName() != null && !q.getCourseName().isEmpty())
                                ? q.getCourseName() : q.getClassInterest(); %>
          <tr<%= late ? " class=\"overdue\"" : "" %>>
            <% if (canBulkAssign) { %>
            <td class="chk-col">
              <input type="checkbox" class="rowChk" name="ids" value="<%= q.getInquiryId() %>"
                     form="bulkForm"<%= hasCounsellor ? " disabled" : "" %>
                     title="<%= hasCounsellor ? "Already assigned to " + esc(q.getCounsellorName()) : "" %>">
            </td>
            <% } %>
            <td><%= i++ %></td>
            <td><a class="nm" href="<%= ctx %>/lead?id=<%= q.getInquiryId() %>"><%= esc(q.getFullName()) %></a></td>
            <td><%= d(q.getMobile()) %></td>
            <td><%= d(course) %></td>
            <td><span class="badge <%= prioClass(q.getPriority()) %>"><%= d(q.getPriority()) %></span></td>
            <td><span class="badge <%= badgeClass(q.getStatus()) %>"><%= q.getStatus() == null ? "NEW" : esc(q.getStatus()) %></span></td>
            <td><%= d(q.getCounsellorName()) %></td>
            <td<%= late ? " class=\"due-late\"" : "" %>><%= d(Dates.dayOf(q.getNextFollowupDate())) %><%
                 String dueTime = Dates.timeOf(q.getNextFollowupDate());
                 if (!dueTime.isEmpty()) { %><div class="due-tm"><%= esc(dueTime) %></div><% } %></td>
            <td><%= d(q.getSource()) %></td>
            <td><%= q.getCreatedAt() == null ? "" : esc(q.getCreatedAt()) %></td>
            <td>
              <% if (converted) { %>
                <span class="done-txt">✓ Admitted</span>
              <% } else { %>
                <a class="btn-convert" href="<%= ctx %>/admission.jsp?inquiryId=<%= q.getInquiryId() %>">Convert →</a>
              <% } %>
            </td>
          </tr>
          <% } %>
        </tbody>
      </table>
    </div>
    <% if (canBulkAssign) { %>
    <script>
      (function () {
        var chkAll   = document.getElementById('chkAll');
        var rowChks  = Array.prototype.slice.call(document.querySelectorAll('.rowChk'));
        var selectable = rowChks.filter(function (c) { return !c.disabled; });
        var counsellor = document.getElementById('bulkCounsellor');
        var assignBtn = document.getElementById('bulkAssignBtn');
        var countLbl  = document.getElementById('bulkCount');
        var form      = document.getElementById('bulkForm');

        function refresh() {
          var checked = rowChks.filter(function (c) { return c.checked; }).length;
          countLbl.textContent = checked + ' selected';
          assignBtn.disabled = checked === 0 || !counsellor.value;
        }
        rowChks.forEach(function (c) { c.addEventListener('change', refresh); });
        counsellor.addEventListener('change', refresh);
        if (chkAll) {
          chkAll.addEventListener('change', function () {
            selectable.forEach(function (c) { c.checked = chkAll.checked; });
            refresh();
          });
        }
        form.addEventListener('submit', function (e) {
          var checked = rowChks.filter(function (c) { return c.checked; }).length;
          if (checked === 0 || !counsellor.value) {
            e.preventDefault();
          }
        });
        refresh();
      })();
    </script>
    <% } %>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
