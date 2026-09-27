<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal, java.util.*" %>
<%@ page import="com.tution.model.User, com.tution.model.StaffAttendance,
                 com.tution.model.StaffLeave, com.tution.model.Payslip,
                 com.tution.model.FundAccount" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    String tab  = (String) request.getAttribute("tab");
    String date = (String) request.getAttribute("date");
    int y  = (Integer) request.getAttribute("y");
    int m  = (Integer) request.getAttribute("m");
    int monthDays = (Integer) request.getAttribute("monthDays");

    @SuppressWarnings("unchecked") List<User> staff =
        (List<User>) request.getAttribute("staff");
    if (staff == null) staff = new ArrayList<User>();

    @SuppressWarnings("unchecked") List<StaffAttendance> register =
        (List<StaffAttendance>) request.getAttribute("register");
    @SuppressWarnings("unchecked") List<Map<String,Object>> summary =
        (List<Map<String,Object>>) request.getAttribute("summary");
    @SuppressWarnings("unchecked") List<StaffLeave> leaves =
        (List<StaffLeave>) request.getAttribute("leaves");
    @SuppressWarnings("unchecked") Map<Integer,BigDecimal> salaries =
        (Map<Integer,BigDecimal>) request.getAttribute("salaries");
    @SuppressWarnings("unchecked") List<Payslip> payslips =
        (List<Payslip>) request.getAttribute("payslips");
    @SuppressWarnings("unchecked") List<FundAccount> funds =
        (List<FundAccount>) request.getAttribute("funds");
    String fstatus = (String) request.getAttribute("fstatus");

    String error      = (String) request.getAttribute("error");
    String flash      = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");

    String today = java.time.LocalDate.now().toString();
    String[] MONTHS = {"","January","February","March","April","May","June",
                       "July","August","September","October","November","December"};
    String period = "&y=" + y + "&m=" + m;

    int pending = 0;
    if (leaves != null) for (StaffLeave l : leaves) if (l.isPending()) pending++;

    BigDecimal payrollTotal = Money.ZERO, paidTotal = Money.ZERO;
    int nPaid = 0;
    if (payslips != null) for (Payslip p : payslips) {
        payrollTotal = payrollTotal.add(p.getNetPay());
        if (p.isPaid()) { paidTotal = paidTotal.add(p.getNetPay()); nPaid++; }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>HR – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .hr-wrap { max-width:1180px; margin:18px auto; padding:0 14px; }
  .hr-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px;
             flex-wrap:wrap; margin:0 0 14px; }
  .hr-head h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .hr-head .sub { margin:3px 0 0; font-size:12.5px; color:#7b8b93; }

  .tabs { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
  .tabs a { text-decoration:none; font-size:13px; font-weight:600; padding:8px 16px;
            border-radius:20px; border:1px solid #cfd6da; color:#5a6b73; background:#fff; }
  .tabs a.on { background:#0E5C3F; color:#fff; border-color:#0E5C3F; }
  .tabs a .pill { background:#c0392b; color:#fff; border-radius:10px; padding:1px 7px;
                  font-size:11px; margin-left:6px; }

  .card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px;
          margin-bottom:14px; }
  .card.primary { padding:0; overflow:hidden; border:1px solid #bfe0cc; border-top:4px solid #0E5C3F;
                  box-shadow:0 6px 20px rgba(14,92,63,.13); }
  .p-head { display:flex; align-items:baseline; gap:10px; flex-wrap:wrap;
            background:#eaf6ef; border-bottom:1px solid #c9e4d6; padding:13px 18px; }
  .p-head h2 { margin:0; font-size:16.5px; color:#0b4a33; }
  .p-head .lede { font-size:12.5px; color:#3f7a5c; }
  .p-body { padding:16px 18px 18px; }
  .card h2 { margin:0 0 3px; font-size:16px; }
  .card p.hint { color:#5a6b73; font-size:13px; margin:0 0 14px; max-width:82ch; }

  .fgrid { display:grid; grid-template-columns:repeat(6,1fr); gap:14px 15px; align-items:start; }
  @media(max-width:1080px){ .fgrid { grid-template-columns:repeat(4,1fr); } }
  @media(max-width:760px) { .fgrid { grid-template-columns:repeat(2,1fr); } }
  @media(max-width:460px) { .fgrid { grid-template-columns:1fr; } }
  .sp2 { grid-column:span 2; } .sp3 { grid-column:span 3; }
  .frm { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  .frm > .fl { flex:0 0 175px; }
  .fl { display:flex; flex-direction:column; gap:6px; min-width:0; }
  .fl label { font-size:10.5px; font-weight:800; text-transform:uppercase; letter-spacing:.45px;
              color:#3f5b4d; }
  .fl label .opt { font-weight:600; text-transform:none; letter-spacing:0; color:#8fa79a;
                   font-size:11px; }
  .fl input, .fl select { width:100%; height:38px; box-sizing:border-box; padding:0 11px;
      border:1px solid #cfd6da; border-radius:7px; background:#fff; font-size:13.5px;
      font-family:inherit; color:#1f3a2c; }
  .fl input::placeholder { color:#9fb4a8; }
  .fl input:hover, .fl select:hover { border-color:#a9bfb4; }
  .fl input:focus, .fl select:focus { outline:none; border-color:#0E5C3F;
      box-shadow:0 0 0 3px rgba(14,92,63,.15); }
  .fl select { appearance:none; -webkit-appearance:none; -moz-appearance:none;
      padding-right:31px; cursor:pointer;
      background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='11' height='7' viewBox='0 0 11 7'%3E%3Cpath d='M1 1l4.5 4.5L10 1' fill='none' stroke='%230E5C3F' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
      background-repeat:no-repeat; background-position:right 11px center; }
  .amt { font-size:16px; font-weight:700; color:#0E5C3F; font-variant-numeric:tabular-nums; }
  .fact { display:flex; justify-content:flex-end; align-items:center; gap:13px; flex-wrap:wrap;
          margin-top:16px; padding-top:15px; border-top:1px solid #eceff1; }
  .fact .note { font-size:12px; color:#7b8b93; margin-right:auto; max-width:62ch; }

  .btn { background:#0E5C3F; color:#fff; border:0; padding:9px 18px; border-radius:6px;
         font-size:13px; font-weight:600; cursor:pointer; text-decoration:none;
         display:inline-block; font-family:inherit; }
  .btn:hover { background:#0b4a33; }
  .btn.alt { background:#fff; color:#42555e; border:1px solid #cfd6da; }
  .btn.sm { padding:5px 11px; font-size:11.5px; }
  .btn.warn { background:#8c2020; }

  .t-wrap { overflow-x:auto; border:1px solid #eceff1; border-radius:9px; }
  table.t { width:100%; border-collapse:collapse; font-size:13.5px; min-width:760px; }
  table.t th { background:#f4f8f6; text-align:left; padding:11px 12px; font-size:10.5px;
      font-weight:800; letter-spacing:.45px; text-transform:uppercase; color:#3f5b4d;
      border-bottom:1px solid #dbe7e0; white-space:nowrap; }
  table.t td { padding:10px 12px; border-bottom:1px solid #eceff1; vertical-align:middle; }
  table.t tbody tr:last-child td { border-bottom:none; }
  table.t tbody tr:hover td { background:#f7fbf9; }
  table.t tr.sum td { background:#f4f8f6; font-weight:700; border-top:2px solid #dbe7e0; }
  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .nm { font-weight:650; color:#1f3a2c; }
  .muted { color:#7b8b93; }
  .sub2 { font-size:11px; color:#8b9aa1; margin-top:3px; }

  .rl { font-size:10px; font-weight:800; letter-spacing:.4px; text-transform:uppercase;
        padding:3px 9px; border-radius:20px; white-space:nowrap; display:inline-block;
        background:#eef3f0; color:#3f5b4d; }
  .st { font-size:10px; font-weight:800; padding:3px 9px; border-radius:20px; letter-spacing:.4px;
        text-transform:uppercase; display:inline-block; white-space:nowrap; }
  .st-PENDING { background:#fff4d6; color:#8A5E00; }
  .st-APPROVED{ background:#e3f3e9; color:#1b6b39; }
  .st-REJECTED{ background:#f7d4d4; color:#8c2020; }
  .st-CANCELLED{ background:#eceff1; color:#6b7f75; }
  .st-DRAFT   { background:#dde9fb; color:#1B4F9C; }
  .st-PAID    { background:#e3f3e9; color:#1b6b39; }

  /* attendance marking: a compact radio row per person */
  .marks { display:flex; gap:5px; flex-wrap:wrap; }
  .marks label { font-size:11px; font-weight:600; color:#5a6b73; border:1px solid #cfd6da;
                 border-radius:6px; padding:4px 9px; cursor:pointer; background:#fff; }
  .marks input { position:absolute; opacity:0; width:0; height:0; }
  .marks input:checked + span { color:#0E5C3F; }
  .marks label:has(input:checked) { border-color:#0E5C3F; background:#eaf6ef; color:#0b4a33; }
  .marks label:focus-within { box-shadow:0 0 0 3px rgba(14,92,63,.15); }
  .rmk { width:100%; height:32px; box-sizing:border-box; padding:0 9px; border:1px solid #e3e6e8;
         border-radius:6px; font-size:12px; font-family:inherit; }

  .tiles { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
  .tile { flex:1 1 170px; background:#fff; border:1px solid #e3e6e8; border-left:4px solid #0E5C3F;
          border-radius:10px; padding:12px 14px; }
  .tile .lb { font-size:11px; color:#5a6b73; font-weight:700; text-transform:uppercase;
              letter-spacing:.3px; }
  .tile .vl { font-size:21px; font-weight:700; color:#0E5C3F; margin-top:3px;
              font-variant-numeric:tabular-nums; }

  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.ok { background:#e3f3e9; color:#1b6b39; border:1px solid #bfe0cc; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .note-box { background:#fdf1e3; border:1px solid #f0d3ae; border-left:4px solid #8a4b12;
              border-radius:8px; padding:13px 16px; font-size:13px; color:#8a4b12;
              margin-bottom:14px; }
  .note-box b { color:#6b3a0d; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="hr"/>
</jsp:include>

<div class="hr-wrap">

  <div class="hr-head">
    <div>
      <h1>HR</h1>
      <p class="sub">Staff attendance, leave and salary</p>
    </div>
  </div>

  <div class="tabs">
    <%-- each tab is its own activity in the role matrix (HR_ATTENDANCE / HR_LEAVE / HR_SALARY) --%>
    <% if (user.can("HR_ATTENDANCE")) { %>
    <a href="<%= ctx %>/hr?tab=attendance&date=<%= esc(date) %><%= period %>"
       class="<%= "attendance".equals(tab) ? "on" : "" %>">Attendance</a>
    <% } %>
    <% if (user.can("HR_LEAVE")) { %>
    <a href="<%= ctx %>/hr?tab=leave<%= period %>"
       class="<%= "leave".equals(tab) ? "on" : "" %>">Leave<%
       if (pending > 0 && "leave".equals(tab)) { %><span class="pill"><%= pending %></span><% } %></a>
    <% } %>
    <% if (user.can("HR_SALARY")) { %>
    <a href="<%= ctx %>/hr?tab=salary<%= period %>"
       class="<%= "salary".equals(tab) ? "on" : "" %>">Salary</a>
    <% } %>
  </div>

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

<%-- ══════════════════ ATTENDANCE ══════════════════ --%>
<% if ("attendance".equals(tab)) { %>

  <div class="card primary">
    <div class="p-head">
      <h2>Mark <%= esc(date) %></h2>
      <span class="lede">Leave a row blank to leave it unmarked</span>
    </div>
    <div class="p-body">
      <p class="hint">A week off or a declared holiday is a paid day and costs nothing at
         payroll. Absent costs a full day, half day costs half. A day nobody marks is treated
         as worked &mdash; the register having a gap is not the employee's fault.</p>

      <form class="frm" method="get" action="<%= ctx %>/hr" style="margin-bottom:16px">
        <input type="hidden" name="tab" value="attendance">
        <input type="hidden" name="y" value="<%= y %>"><input type="hidden" name="m" value="<%= m %>">
        <div class="fl"><label for="dpick">Date</label>
          <input type="date" id="dpick" name="date" value="<%= esc(date) %>" max="<%= today %>"></div>
        <button class="btn alt" type="submit">Show</button>
      </form>

      <form method="post" action="<%= ctx %>/hr">
        <input type="hidden" name="action" value="markday">
        <input type="hidden" name="tab" value="attendance">
        <input type="hidden" name="date" value="<%= esc(date) %>">
        <input type="hidden" name="y" value="<%= y %>"><input type="hidden" name="m" value="<%= m %>">
        <div class="t-wrap">
          <table class="t">
            <thead><tr>
              <th style="width:200px">Employee</th>
              <th style="width:110px">Role</th>
              <th>Mark</th>
              <th style="width:210px">Remark</th>
            </tr></thead>
            <tbody>
            <% if (register != null) for (StaffAttendance a : register) {
                 String cur = a.getStatus() == null ? "" : a.getStatus();
                 String[][] opts = {
                   {StaffAttendance.PRESENT,"P"}, {StaffAttendance.ABSENT,"A"},
                   {StaffAttendance.HALF_DAY,"½"}, {StaffAttendance.LEAVE,"L"},
                   {StaffAttendance.HOLIDAY,"H"}, {StaffAttendance.WEEK_OFF,"WO"} };
            %>
              <tr>
                <td class="nm"><%= esc(a.getStaffName()) %>
                  <% if (a.getMarkedBy() != null) { %>
                    <div class="sub2">by <%= esc(a.getMarkedBy()) %></div>
                  <% } %></td>
                <td><span class="rl"><%= esc(com.tution.model.Role.labelOf(a.getRole())) %></span></td>
                <td>
                  <div class="marks">
                    <label><input type="radio" name="status_<%= a.getUserId() %>" value=""
                        <%= cur.isEmpty() ? "checked" : "" %>><span>&mdash;</span></label>
                    <% for (String[] o : opts) { %>
                      <label title="<%= o[0] %>"><input type="radio"
                          name="status_<%= a.getUserId() %>" value="<%= o[0] %>"
                          <%= o[0].equals(cur) ? "checked" : "" %>><span><%= o[1] %></span></label>
                    <% } %>
                  </div>
                </td>
                <td><input class="rmk" type="text" name="remark_<%= a.getUserId() %>"
                           maxlength="200" value="<%= esc(a.getRemarks()) %>"></td>
              </tr>
            <% } %>
            <% if (register == null || register.isEmpty()) { %>
              <tr><td colspan="4" class="muted" style="padding:20px 12px">No active staff.</td></tr>
            <% } %>
            </tbody>
          </table>
        </div>
        <div class="fact">
          <span class="note">P present &middot; A absent &middot; ½ half day &middot;
            L leave &middot; H holiday &middot; WO week off</span>
          <button class="btn" type="submit">Save the day</button>
        </div>
      </form>
    </div>
  </div>

  <div class="card">
    <h2><%= MONTHS[m] %> <%= y %> summary</h2>
    <p class="hint">Totals from the marks above, across all <%= monthDays %> days of the month.</p>
    <form class="frm" method="get" action="<%= ctx %>/hr" style="margin-bottom:14px">
      <input type="hidden" name="tab" value="attendance">
      <input type="hidden" name="date" value="<%= esc(date) %>">
      <div class="fl"><label for="msel">Month</label>
        <select id="msel" name="m"><% for (int i=1;i<=12;i++) { %>
          <option value="<%= i %>" <%= i==m?"selected":"" %>><%= MONTHS[i] %></option><% } %>
        </select></div>
      <div class="fl"><label for="ysel">Year</label>
        <select id="ysel" name="y"><% for (int i=y-2;i<=y+1;i++) { %>
          <option value="<%= i %>" <%= i==y?"selected":"" %>><%= i %></option><% } %>
        </select></div>
      <button class="btn alt" type="submit">Show</button>
    </form>
    <div class="t-wrap">
      <table class="t">
        <thead><tr>
          <th style="width:200px">Employee</th><th style="width:110px">Role</th>
          <th class="num">Present</th><th class="num">Absent</th><th class="num">Half</th>
          <th class="num">Leave</th><th class="num">Off</th><th class="num">Marked</th>
        </tr></thead>
        <tbody>
        <% if (summary != null) for (Map<String,Object> r : summary) { %>
          <tr>
            <td class="nm"><%= esc(String.valueOf(r.get("name"))) %></td>
            <td><span class="rl"><%= esc(String.valueOf(r.get("role"))) %></span></td>
            <td class="num"><%= r.get("present") %></td>
            <td class="num"><%= r.get("absent") %></td>
            <td class="num"><%= r.get("half") %></td>
            <td class="num"><%= r.get("leave") %></td>
            <td class="num"><%= r.get("off") %></td>
            <td class="num muted"><%= r.get("marked") %> / <%= monthDays %></td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  </div>

<%-- ══════════════════ LEAVE ══════════════════ --%>
<% } else if ("leave".equals(tab)) { %>

  <div class="card primary">
    <div class="p-head">
      <h2>Record a leave request</h2>
      <span class="lede">Approving it writes the days onto the attendance register</span>
    </div>
    <div class="p-body">
      <p class="hint">Unpaid leave is the only kind that reduces pay. Casual and sick leave are
         paid, so payroll ignores them. For a half day, set the days by hand.</p>
      <form method="post" action="<%= ctx %>/hr">
        <input type="hidden" name="action" value="applyleave">
        <input type="hidden" name="tab" value="leave">
        <div class="fgrid">
          <div class="fl sp2"><label for="lvWho">Employee</label>
            <select name="userId" id="lvWho" required>
              <option value="">&mdash; choose &mdash;</option>
              <% for (User s : staff) { if (!s.isActive()) continue; %>
                <option value="<%= s.getUserId() %>"><%= esc(s.getFullName()) %>
                  &nbsp;&middot;&nbsp;<%= esc(s.getRoleLabel()) %></option>
              <% } %>
            </select></div>
          <div class="fl"><label for="lvType">Type</label>
            <select name="leaveType" id="lvType">
              <option value="CASUAL">Casual (paid)</option>
              <option value="SICK">Sick (paid)</option>
              <option value="UNPAID">Unpaid</option>
              <option value="OTHER">Other</option>
            </select></div>
          <div class="fl"><label for="lvFrom">From</label>
            <input type="date" name="fromDate" id="lvFrom" required value="<%= today %>"></div>
          <div class="fl"><label for="lvTo">To</label>
            <input type="date" name="toDate" id="lvTo" value="<%= today %>"></div>
          <div class="fl"><label for="lvDays">Days <span class="opt">(override)</span></label>
            <input type="text" name="days" id="lvDays" inputmode="decimal" placeholder="auto"></div>
          <div class="fl sp3"><label for="lvWhy">Reason</label>
            <input type="text" name="reason" id="lvWhy" maxlength="255"
                   placeholder="e.g. family function"></div>
        </div>
        <div class="fact">
          <button class="btn" type="submit">Record request</button>
        </div>
      </form>
    </div>
  </div>

  <div class="card">
    <h2>Requests<% if (pending > 0) { %> <span class="muted">&middot; <%= pending %> awaiting a decision</span><% } %></h2>
    <form class="frm" method="get" action="<%= ctx %>/hr" style="margin-bottom:14px">
      <input type="hidden" name="tab" value="leave">
      <div class="fl"><label for="lsel">Status</label>
        <select id="lsel" name="status">
          <option value="">All</option>
          <option value="PENDING"  <%= "PENDING".equals(fstatus)?"selected":"" %>>Pending</option>
          <option value="APPROVED" <%= "APPROVED".equals(fstatus)?"selected":"" %>>Approved</option>
          <option value="REJECTED" <%= "REJECTED".equals(fstatus)?"selected":"" %>>Rejected</option>
        </select></div>
      <button class="btn alt" type="submit">Show</button>
    </form>
    <div class="t-wrap">
      <table class="t">
        <thead><tr>
          <th style="width:180px">Employee</th><th style="width:95px">Type</th>
          <th style="width:180px">Dates</th><th class="num" style="width:70px">Days</th>
          <th>Reason</th><th style="width:110px">Status</th><th style="width:170px">Decision</th>
        </tr></thead>
        <tbody>
        <% if (leaves != null) for (StaffLeave l : leaves) { %>
          <tr>
            <td class="nm"><%= esc(l.getStaffName()) %>
              <div class="sub2"><%= esc(com.tution.model.Role.labelOf(l.getRole())) %></div></td>
            <td><%= esc(l.getTypeLabel()) %><% if (l.isUnpaid()) { %>
              <div class="sub2">reduces pay</div><% } %></td>
            <td><%= esc(l.getFromDate()) %>
              <% if (!l.getFromDate().equals(l.getToDate())) { %>
                <div class="sub2">to <%= esc(l.getToDate()) %></div><% } %></td>
            <td class="num"><%= trimNum(l.getDays()) %></td>
            <td class="muted"><%= l.getReason()==null ? "&mdash;" : esc(l.getReason()) %>
              <% if (l.getDecisionNote()!=null) { %>
                <div class="sub2"><%= esc(l.getDecisionNote()) %></div><% } %></td>
            <td><span class="st st-<%= esc(l.getStatus()) %>"><%= esc(l.getStatusLabel()) %></span>
              <% if (l.getDecidedBy()!=null) { %>
                <div class="sub2">by <%= esc(l.getDecidedBy()) %></div><% } %></td>
            <td>
              <% if (l.isPending()) { %>
                <form method="post" action="<%= ctx %>/hr" style="display:inline">
                  <input type="hidden" name="action" value="decideleave">
                  <input type="hidden" name="tab" value="leave">
                  <input type="hidden" name="leaveId" value="<%= l.getLeaveId() %>">
                  <input type="hidden" name="decision" value="approve">
                  <button class="btn sm" type="submit">Approve</button>
                </form>
                <form method="post" action="<%= ctx %>/hr" style="display:inline"
                      onsubmit="var r=prompt('Why is this being rejected?'); if(!r){return false;} this.note.value=r; return true;">
                  <input type="hidden" name="action" value="decideleave">
                  <input type="hidden" name="tab" value="leave">
                  <input type="hidden" name="leaveId" value="<%= l.getLeaveId() %>">
                  <input type="hidden" name="decision" value="reject">
                  <input type="hidden" name="note" value="">
                  <button class="btn alt sm" type="submit">Reject</button>
                </form>
              <% } else { %><span class="muted">&mdash;</span><% } %>
            </td>
          </tr>
        <% } %>
        <% if (leaves == null || leaves.isEmpty()) { %>
          <tr><td colspan="7" class="muted" style="padding:20px 12px">No leave requests.</td></tr>
        <% } %>
        </tbody>
      </table>
    </div>
  </div>

<%-- ══════════════════ SALARY ══════════════════ --%>
<% } else { %>

  <div class="tiles">
    <div class="tile"><div class="lb">Payroll <%= MONTHS[m] %></div>
      <div class="vl">Rs. <%= Money.fmt(payrollTotal) %></div></div>
    <div class="tile"><div class="lb">Paid</div>
      <div class="vl">Rs. <%= Money.fmt(paidTotal) %></div></div>
    <div class="tile"><div class="lb">Payslips</div>
      <div class="vl"><%= payslips==null?0:payslips.size() %></div></div>
    <div class="tile"><div class="lb">Still to pay</div>
      <div class="vl"><%= (payslips==null?0:payslips.size()) - nPaid %></div></div>
  </div>

  <div class="note-box">
    <b>Paying a payslip debits a fund.</b> Salary is the institute's largest outflow, so it goes
    on the ledger like any expense &mdash; the payslip stores the fund and the entry it created,
    and the two are written together or not at all.
  </div>

  <div class="card primary">
    <div class="p-head">
      <h2>Payroll for <%= MONTHS[m] %> <%= y %></h2>
      <span class="lede"><%= monthDays %> days in the month</span>
    </div>
    <div class="p-body">
      <p class="hint">Generating works out a draft for everyone with a salary on record who does
         not already have one for this month. Payable days start at <%= monthDays %> and come
         down by absences, half days and approved unpaid leave. A slip already paid is never
         recalculated.</p>
      <form class="frm" method="get" action="<%= ctx %>/hr" style="margin-bottom:14px">
        <input type="hidden" name="tab" value="salary">
        <div class="fl"><label for="pm">Month</label>
          <select id="pm" name="m"><% for (int i=1;i<=12;i++) { %>
            <option value="<%= i %>" <%= i==m?"selected":"" %>><%= MONTHS[i] %></option><% } %>
          </select></div>
        <div class="fl"><label for="py">Year</label>
          <select id="py" name="y"><% for (int i=y-2;i<=y+1;i++) { %>
            <option value="<%= i %>" <%= i==y?"selected":"" %>><%= i %></option><% } %>
          </select></div>
        <button class="btn alt" type="submit">Show</button>
      </form>

      <div class="t-wrap">
        <table class="t">
          <thead><tr>
            <th style="width:180px">Employee</th>
            <th class="num" style="width:110px">Monthly</th>
            <th class="num" style="width:95px">Payable</th>
            <th class="num" style="width:85px">Lost</th>
            <th class="num" style="width:115px">Net pay</th>
            <th style="width:100px">Status</th>
            <th style="width:250px">Pay from</th>
          </tr></thead>
          <tbody>
          <% if (payslips != null) for (Payslip p : payslips) { %>
            <tr>
              <td class="nm"><%= esc(p.getStaffName()) %>
                <div class="sub2"><%= esc(com.tution.model.Role.labelOf(p.getRole())) %></div></td>
              <td class="num"><%= Money.fmt(p.getMonthlyCtc()) %></td>
              <td class="num"><%= trimNum(p.getPayableDays()) %> / <%= p.getMonthDays() %></td>
              <td class="num"><%= trimNum(p.getAbsentDays() + p.getUnpaidDays()) %>
                <% if (p.getUnpaidDays() > 0) { %>
                  <div class="sub2"><%= trimNum(p.getUnpaidDays()) %> unpaid</div><% } %></td>
              <td class="num"><strong>Rs. <%= Money.fmt(p.getNetPay()) %></strong></td>
              <td><span class="st st-<%= esc(p.getStatus()) %>"><%= esc(p.getStatusLabel()) %></span></td>
              <td>
                <% if (p.isDraft()) { %>
                  <form method="post" action="<%= ctx %>/hr" style="display:flex;gap:6px"
                        onsubmit="return confirm('Pay Rs. <%= Money.fmt(p.getNetPay()) %> to <%= esc(p.getStaffName()) %>? This debits the fund.');">
                    <input type="hidden" name="action" value="pay">
                    <input type="hidden" name="tab" value="salary">
                    <input type="hidden" name="y" value="<%= y %>">
                    <input type="hidden" name="m" value="<%= m %>">
                    <input type="hidden" name="payslipId" value="<%= p.getPayslipId() %>">
                    <select name="fundId" required
                            style="height:32px;border:1px solid #cfd6da;border-radius:6px;font-size:12px;font-family:inherit;padding:0 8px">
                      <% if (funds != null) for (FundAccount f : funds) { %>
                        <option value="<%= f.getFundId() %>"><%= esc(f.getName()) %></option>
                      <% } %>
                    </select>
                    <button class="btn sm" type="submit">Pay</button>
                  </form>
                <% } else { %>
                  <span class="muted"><%= p.getFundName()==null ? "&mdash;" : esc(p.getFundName()) %></span>
                  <% if (p.getPaidOn()!=null) { %>
                    <div class="sub2"><%= esc(p.getPaidOn()) %><%
                      if (p.getPaidBy()!=null) { %> &middot; <%= esc(p.getPaidBy()) %><% } %></div>
                  <% } %>
                <% } %>
              </td>
            </tr>
          <% } %>
          <% if (payslips == null || payslips.isEmpty()) { %>
            <tr><td colspan="7" class="muted" style="padding:20px 12px">
              No payslips for <%= MONTHS[m] %> <%= y %> yet. Generate them below.</td></tr>
          <% } %>
          </tbody>
          <% if (payslips != null && !payslips.isEmpty()) { %>
          <tfoot><tr class="sum">
            <td colspan="4"><%= payslips.size() %> payslip(s), <%= nPaid %> paid</td>
            <td class="num">Rs. <%= Money.fmt(payrollTotal) %></td>
            <td colspan="2"></td>
          </tr></tfoot>
          <% } %>
        </table>
      </div>

      <form method="post" action="<%= ctx %>/hr">
        <input type="hidden" name="action" value="generate">
        <input type="hidden" name="tab" value="salary">
        <input type="hidden" name="y" value="<%= y %>"><input type="hidden" name="m" value="<%= m %>">
        <div class="fact">
          <span class="note">Safe to run again &mdash; existing payslips are left alone.</span>
          <button class="btn" type="submit">Generate drafts for <%= MONTHS[m] %></button>
        </div>
      </form>
    </div>
  </div>

  <div class="card">
    <h2>Salary on record</h2>
    <p class="hint">Each change is kept with the date it takes effect, so a raise never restates
       what somebody was paid before it. Payroll uses whichever figure was in force.</p>
    <form method="post" action="<%= ctx %>/hr">
      <input type="hidden" name="action" value="setsalary">
      <input type="hidden" name="tab" value="salary">
      <input type="hidden" name="y" value="<%= y %>"><input type="hidden" name="m" value="<%= m %>">
      <div class="fgrid">
        <div class="fl sp2"><label for="slWho">Employee</label>
          <select name="userId" id="slWho" required>
            <option value="">&mdash; choose &mdash;</option>
            <% for (User s : staff) { if (!s.isActive()) continue;
                 BigDecimal cur = salaries==null ? null : salaries.get(s.getUserId()); %>
              <option value="<%= s.getUserId() %>"><%= esc(s.getFullName()) %>
                <%= cur==null ? "&nbsp;&middot;&nbsp;not set"
                              : "&nbsp;&middot;&nbsp;Rs. " + Money.fmt(cur) %></option>
            <% } %>
          </select></div>
        <div class="fl"><label for="slCtc">Monthly salary</label>
          <input type="text" name="ctc" id="slCtc" class="amt" required inputmode="decimal"
                 placeholder="0.00"></div>
        <div class="fl"><label for="slFrom">Effective from</label>
          <input type="date" name="effectiveFrom" id="slFrom"
                 value="<%= today.substring(0,8) %>01"></div>
        <div class="fl sp2"><label for="slNote">Note <span class="opt">(optional)</span></label>
          <input type="text" name="note" id="slNote" maxlength="200"
                 placeholder="e.g. annual revision"></div>
      </div>
      <div class="fact">
        <button class="btn" type="submit">Record salary</button>
      </div>
    </form>

    <div class="t-wrap" style="margin-top:16px">
      <table class="t">
        <thead><tr><th style="width:220px">Employee</th><th style="width:120px">Role</th>
          <th class="num" style="width:140px">Monthly salary</th><th>Status</th></tr></thead>
        <tbody>
        <% for (User s : staff) { if (!s.isActive()) continue;
             BigDecimal cur = salaries==null ? null : salaries.get(s.getUserId()); %>
          <tr>
            <td class="nm"><%= esc(s.getFullName()) %></td>
            <td><span class="rl"><%= esc(s.getRoleLabel()) %></span></td>
            <td class="num"><%= cur==null ? "&mdash;" : "Rs. " + Money.fmt(cur) %></td>
            <td class="muted"><%= cur==null ? "no salary on record &mdash; left out of payroll"
                                            : "in payroll" %></td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  </div>

<% } %>

</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="hr"/></jsp:include>

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

    /** 3.0 reads as "3", 2.5 stays "2.5" — day counts are mostly whole. */
    private String trimNum(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
%>
