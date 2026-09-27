<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*, com.tution.model.User, com.tution.model.StaffAttendance,
                 com.tution.model.StaffLeave, com.tution.model.Payslip, com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<StaffAttendance> att = (List<StaffAttendance>) request.getAttribute("attendance");
    @SuppressWarnings("unchecked") List<StaffLeave> leaves   = (List<StaffLeave>) request.getAttribute("leaves");
    @SuppressWarnings("unchecked") List<Payslip> slips       = (List<Payslip>) request.getAttribute("payslips");
    int y = (Integer) request.getAttribute("y"), m = (Integer) request.getAttribute("m");
    String today = (String) request.getAttribute("today");
    String error = (String) request.getAttribute("error");
    String flash      = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");

    String[] MONTHS = { "", "January", "February", "March", "April", "May", "June", "July",
                        "August", "September", "October", "November", "December" };
    int py = m == 1 ? y - 1 : y, pm = m == 1 ? 12 : m - 1;
    int ny = m == 12 ? y + 1 : y, nm = m == 12 ? 1 : m + 1;

    // month tally, by status
    Map<String, Integer> tally = new LinkedHashMap<String, Integer>();
    if (att != null) for (StaffAttendance a : att) {
        Integer n = tally.get(a.getLabel());
        tally.put(a.getLabel(), n == null ? 1 : n + 1);
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>My HR – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .mh-wrap { max-width:980px; margin:18px auto; padding:0 14px 30px; }
  .mh-wrap h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .mh-wrap .sub { margin:3px 0 16px; font-size:12.5px; color:#7b8b93; }
  .card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px 18px; margin-bottom:14px; }
  .card h2 { margin:0 0 10px; font-size:16px; color:#0b4a33; display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
  .card h2 .nav { margin-left:auto; font-size:12.5px; font-weight:600; }
  .card h2 .nav a { color:#0E5C3F; text-decoration:none; padding:0 6px; }
  .hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }
  .chips { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:12px; }
  .chips span { font-size:12px; font-weight:700; padding:5px 12px; border-radius:20px; background:#eef3f0; color:#3f5b4d; }
  .t-wrap { overflow-x:auto; }
  table.t { width:100%; border-collapse:collapse; font-size:13.5px; }
  table.t th { background:#f4f8f6; text-align:left; padding:9px 10px; font-size:10.5px; font-weight:800;
      letter-spacing:.45px; text-transform:uppercase; color:#3f5b4d; border-bottom:1px solid #dbe7e0; white-space:nowrap; }
  table.t td { padding:9px 10px; border-bottom:1px solid #eceff1; vertical-align:top; }
  table.t td.num, table.t th.num { text-align:right; font-variant-numeric:tabular-nums; }
  .st { font-size:10.5px; font-weight:800; padding:2px 9px; border-radius:20px; text-transform:uppercase; letter-spacing:.3px; }
  .st-PENDING { background:#fbeecd; color:#8A5E00; } .st-APPROVED { background:#d9ece1; color:#0b4a33; }
  .st-REJECTED, .st-CANCELLED { background:#f7d4d4; color:#8c2020; }
  .muted { color:#7b8b93; }
  .fgrid { display:grid; grid-template-columns:repeat(4, 1fr); gap:12px; align-items:end; }
  @media (max-width:700px) { .fgrid { grid-template-columns:1fr 1fr; } }
  .fl { display:flex; flex-direction:column; gap:5px; }
  .fl.wide { grid-column:1 / -1; }
  .fl label { font-size:10.5px; font-weight:800; text-transform:uppercase; letter-spacing:.45px; color:#3f5b4d; }
  .fl input, .fl select { height:38px; box-sizing:border-box; padding:0 10px; border:1px solid #cfd6da;
      border-radius:7px; font:inherit; font-size:13.5px; background:#fff; }
  .btn { background:#0E5C3F; color:#fff; border:0; padding:9px 18px; border-radius:6px; font-size:13px;
      font-weight:600; cursor:pointer; font-family:inherit; }
  .btn:hover { background:#0b4a33; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="myhr"/>
</jsp:include>

<div class="mh-wrap">
  <h1>My HR</h1>
  <p class="sub"><%= esc(user.getFullName()) %> &middot; <%= esc(user.getRoleLabel()) %> &middot; your own records only</p>

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <% if (att != null) { %>
  <div class="card" id="attendance">
    <h2>My attendance &middot; <%= MONTHS[m] %> <%= y %>
      <span class="nav"><a href="<%= ctx %>/my-hr?y=<%= py %>&m=<%= pm %>#attendance">&larr; <%= MONTHS[pm].substring(0,3) %></a>
        <a href="<%= ctx %>/my-hr?y=<%= ny %>&m=<%= nm %>#attendance"><%= MONTHS[nm].substring(0,3) %> &rarr;</a></span></h2>
    <% if (att.isEmpty()) { %>
      <p class="hint">Nothing marked for you this month yet. A day nobody marks counts as worked for pay.</p>
    <% } else { %>
      <div class="chips"><% for (Map.Entry<String,Integer> e : tally.entrySet()) { %>
        <span><%= esc(e.getKey()) %>: <b><%= e.getValue() %></b></span><% } %></div>
      <div class="t-wrap"><table class="t">
        <tr><th>Date</th><th>Status</th><th>Remarks</th><th>Marked by</th></tr>
        <% for (StaffAttendance a : att) { %>
          <tr><td><%= esc(a.getAttDate()) %></td><td><%= esc(a.getLabel()) %></td>
              <td><%= esc(a.getRemarks()) %></td><td class="muted"><%= esc(a.getMarkedBy()) %></td></tr>
        <% } %>
      </table></div>
    <% } %>
  </div>
  <% } %>

  <% if (leaves != null) { %>
  <div class="card" id="leave">
    <h2>Apply for leave</h2>
    <form method="post" action="<%= ctx %>/my-hr">
      <input type="hidden" name="action" value="applyleave">
      <div class="fgrid">
        <div class="fl"><label for="lt">Type</label>
          <select name="leaveType" id="lt">
            <option value="CASUAL">Casual</option><option value="SICK">Sick</option>
            <option value="UNPAID">Unpaid</option><option value="OTHER">Other</option>
          </select></div>
        <div class="fl"><label for="lf">From</label>
          <input type="date" name="fromDate" id="lf" required min="<%= today %>"></div>
        <div class="fl"><label for="lto">To</label>
          <input type="date" name="toDate" id="lto" min="<%= today %>"></div>
        <div class="fl"><label for="ld">Days <span class="muted">(half day: 0.5)</span></label>
          <input type="number" name="days" id="ld" step="0.5" min="0.5" placeholder="auto"></div>
        <div class="fl wide"><label for="lr">Reason</label>
          <input type="text" name="reason" id="lr" maxlength="255"></div>
      </div>
      <p style="margin:12px 0 0"><button class="btn" type="submit">Send request</button></p>
    </form>
  </div>

  <div class="card">
    <h2>My leave requests</h2>
    <% if (leaves.isEmpty()) { %><p class="hint">None yet.</p><% } else { %>
    <div class="t-wrap"><table class="t">
      <tr><th>From</th><th>To</th><th class="num">Days</th><th>Type</th><th>Status</th><th>Decision note</th></tr>
      <% for (StaffLeave l : leaves) { %>
        <tr><td><%= esc(l.getFromDate()) %></td><td><%= esc(l.getToDate()) %></td>
            <td class="num"><%= l.getDays() %></td><td><%= esc(l.getTypeLabel()) %></td>
            <td><span class="st st-<%= esc(l.getStatus()) %>"><%= esc(l.getStatusLabel()) %></span></td>
            <td class="muted"><%= esc(l.getDecisionNote()) %><% if (l.getDecidedBy() != null) { %>
              <br><small>by <%= esc(l.getDecidedBy()) %></small><% } %></td></tr>
      <% } %>
    </table></div>
    <% } %>
  </div>
  <% } %>

  <% if (slips != null) { %>
  <div class="card" id="salary">
    <h2>My payslips</h2>
    <p class="hint">Paid payslips only &mdash; a month appears here once HR has paid it.</p>
    <% if (slips.isEmpty()) { %><p class="hint">None yet.</p><% } else { %>
    <div class="t-wrap"><table class="t">
      <tr><th>Month</th><th class="num">Monthly CTC</th><th class="num">Payable days</th>
          <th class="num">Gross</th><th class="num">Deductions</th><th class="num">Net pay</th><th>Paid on</th></tr>
      <% for (Payslip p : slips) { %>
        <tr><td><%= esc(p.getPeriodLabel()) %></td>
            <td class="num">Rs. <%= Money.fmt(p.getMonthlyCtc()) %></td>
            <td class="num"><%= p.getPayableDays() %> / <%= p.getMonthDays() %></td>
            <td class="num">Rs. <%= Money.fmt(p.getGross()) %></td>
            <td class="num">Rs. <%= Money.fmt(p.getDeductions()) %></td>
            <td class="num"><b>Rs. <%= Money.fmt(p.getNetPay()) %></b></td>
            <td class="muted"><%= esc(p.getPaidOn()) %></td></tr>
      <% } %>
    </table></div>
    <% } %>
  </div>
  <% } %>
</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="myhr"/></jsp:include>
</body>
</html>

<%!
    private String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  b.append("&amp;");  break;
                case '<':  b.append("&lt;");   break;
                case '>':  b.append("&gt;");   break;
                case '"':  b.append("&quot;"); break;
                case '\'': b.append("&#39;");  break;
                default:   b.append(c);
            }
        }
        return b.toString();
    }
%>
