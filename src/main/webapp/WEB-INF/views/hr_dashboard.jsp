<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal, java.util.*" %>
<%@ page import="com.tution.model.User, com.tution.model.SalesStats" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") Map<String,Object> d =
        (Map<String,Object>) request.getAttribute("d");
    if (d == null) d = new HashMap<String,Object>();
    SalesStats st = (SalesStats) request.getAttribute("stats");
    String today  = (String) request.getAttribute("today");
    String error  = (String) request.getAttribute("error");

    String[] MONTHS = {"","January","February","March","April","May","June",
                       "July","August","September","October","November","December"};
    int m = (Integer) request.getAttribute("m");

    int staffActive  = i(d.get("staffActive"));
    int markedToday  = i(d.get("markedToday"));
    int presentToday = i(d.get("presentToday"));
    int absentToday  = i(d.get("absentToday"));
    int onLeaveToday = i(d.get("onLeaveToday"));
    int leavePending = i(d.get("leavePending"));
    int slipsTotal   = i(d.get("slipsTotal"));
    int slipsPaid    = i(d.get("slipsPaid"));
    int noSalary     = i(d.get("noSalary"));
    int unmarked     = staffActive - markedToday;
    if (unmarked < 0) unmarked = 0;

    BigDecimal payrollDue  = bd(d.get("payrollDue"));
    BigDecimal payrollPaid = bd(d.get("payrollPaid"));
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>HR Dashboard – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .dw { max-width:1180px; margin:18px auto; padding:0 14px; }
  .dh { margin:0 0 16px; }
  .dh h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .dh .sub { margin:3px 0 0; font-size:12.5px; color:#7b8b93; }

  .todo { display:grid; grid-template-columns:repeat(auto-fit,minmax(270px,1fr)); gap:12px;
          margin-bottom:18px; }
  .todo a { display:block; text-decoration:none; color:inherit; background:#fff;
            border:1px solid #e3e6e8; border-left:4px solid #0E5C3F; border-radius:10px;
            padding:14px 16px; }
  .todo a:hover { box-shadow:0 4px 14px rgba(14,92,63,.10); }
  .todo a.act { border-left-color:#c0392b; background:#fffaf9; }
  .todo a.done { border-left-color:#bfe0cc; }
  .todo .n { font-size:24px; font-weight:700; color:#0E5C3F; line-height:1.1;
             font-variant-numeric:tabular-nums; }
  .todo a.act .n { color:#c0392b; }
  .todo .l { font-size:13px; font-weight:650; color:#1f3a2c; margin-top:4px; }
  .todo .s { font-size:11.5px; color:#7b8b93; margin-top:3px; }

  .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:14px; align-items:start; }
  @media(max-width:900px){ .grid2 { grid-template-columns:1fr; } }

  .card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px;
          margin-bottom:14px; }
  .card h2 { margin:0 0 3px; font-size:15px; }
  .card p.hint { color:#5a6b73; font-size:12.5px; margin:0 0 13px; }
  .card h2 a { float:right; font-size:11.5px; font-weight:600; color:#0E5C3F;
               text-decoration:none; }

  .kv { display:grid; grid-template-columns:1fr auto; gap:9px 12px; font-size:13.5px; }
  .kv .k { color:#5a6b73; }
  .kv .v { font-weight:700; color:#1f3a2c; font-variant-numeric:tabular-nums; text-align:right; }
  .kv .v.warn { color:#c0392b; }
  .kv .row { display:contents; }
  .kv hr { grid-column:1/-1; border:0; border-top:1px solid #eceff1; margin:2px 0; }

  .bar { height:9px; border-radius:5px; background:#eef3f0; overflow:hidden; margin-top:9px; }
  .bar i { display:block; height:100%; background:linear-gradient(90deg,#1b8a5c,#0E5C3F);
           border-radius:5px; }
  .barlab { display:flex; justify-content:space-between; font-size:11.5px; color:#7b8b93;
            margin-top:6px; }

  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .note-box { background:#fdf1e3; border:1px solid #f0d3ae; border-left:4px solid #8a4b12;
              border-radius:8px; padding:12px 15px; font-size:12.5px; color:#8a4b12;
              margin-bottom:14px; }
  .note-box a { color:#6b3a0d; font-weight:700; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="home"/>
</jsp:include>

<div class="dw">

  <div class="dh">
    <h1>Good day, <%= esc(first(user.getFullName())) %></h1>
    <p class="sub"><%= esc(today) %> &middot; <%= staffActive %> people on the books</p>
  </div>

  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <%-- What needs doing, before anything that is merely interesting. --%>
  <div class="todo">
    <a class="<%= unmarked > 0 ? "act" : "done" %>" href="<%= ctx %>/hr?tab=attendance">
      <div class="n"><%= unmarked %></div>
      <div class="l"><%= unmarked == 0 ? "Register is complete" : "Not marked today" %></div>
      <div class="s"><%= unmarked == 0
          ? presentToday + " in, " + absentToday + " absent, " + onLeaveToday + " on leave"
          : "of " + staffActive + " &mdash; mark the day &rarr;" %></div>
    </a>
    <a class="<%= leavePending > 0 ? "act" : "done" %>" href="<%= ctx %>/hr?tab=leave&status=PENDING">
      <div class="n"><%= leavePending %></div>
      <div class="l"><%= leavePending == 0 ? "No leave to decide" : "Leave awaiting a decision" %></div>
      <div class="s"><%= leavePending == 0 ? "nothing pending"
                                           : "approve or reject &rarr;" %></div>
    </a>
    <a class="<%= (slipsTotal - slipsPaid) > 0 ? "act" : "done" %>" href="<%= ctx %>/hr?tab=salary">
      <div class="n"><%= slipsTotal - slipsPaid %></div>
      <div class="l"><%= slipsTotal == 0 ? "Payroll not started" : "Payslips still to pay" %></div>
      <div class="s"><%= slipsTotal == 0
          ? MONTHS[m] + " &mdash; generate drafts &rarr;"
          : "Rs. " + Money.fmt(payrollDue) + " outstanding" %></div>
    </a>
  </div>

  <% if (noSalary > 0) { %>
    <div class="note-box">
      <b><%= noSalary %></b> active
      <%= noSalary == 1 ? "person has" : "people have" %> no salary on record, so
      <%= noSalary == 1 ? "they are" : "they are" %> left out of payroll entirely.
      <a href="<%= ctx %>/hr?tab=salary">Set it &rarr;</a>
    </div>
  <% } %>

  <div class="grid2">
    <div>
      <div class="card">
        <h2>Today &middot; <%= esc(today) %><a href="<%= ctx %>/hr?tab=attendance">Register &rarr;</a></h2>
        <p class="hint">Marks recorded so far.</p>
        <div class="kv">
          <div class="k">Present (incl. half days)</div><div class="v"><%= presentToday %></div>
          <div class="k">Absent</div><div class="v <%= absentToday>0?"warn":"" %>"><%= absentToday %></div>
          <div class="k">On leave</div><div class="v"><%= onLeaveToday %></div>
          <hr>
          <div class="k">Marked</div><div class="v"><%= markedToday %> / <%= staffActive %></div>
        </div>
        <% int pct = staffActive == 0 ? 0 : (markedToday * 100 / staffActive); %>
        <div class="bar"><i style="width:<%= pct %>%"></i></div>
        <div class="barlab"><span>Register complete</span><span><%= pct %>%</span></div>
      </div>

      <div class="card">
        <h2>Payroll &middot; <%= MONTHS[m] %><a href="<%= ctx %>/hr?tab=salary">Open &rarr;</a></h2>
        <p class="hint">Paying a payslip debits a fund and shows on the accountant's statement.</p>
        <div class="kv">
          <div class="k">Payslips</div><div class="v"><%= slipsTotal %></div>
          <div class="k">Paid</div><div class="v"><%= slipsPaid %></div>
          <div class="k">Paid out</div><div class="v">Rs. <%= Money.fmt(payrollPaid) %></div>
          <hr>
          <div class="k">Still to pay</div>
          <div class="v <%= payrollDue.signum()>0?"warn":"" %>">Rs. <%= Money.fmt(payrollDue) %></div>
        </div>
      </div>
    </div>

    <div>
      <div class="card">
        <h2>Enquiries &amp; admissions<a href="<%= ctx %>/inquiries">Leads &rarr;</a></h2>
        <p class="hint">This month, across the whole institute.</p>
        <div class="kv">
          <div class="k">New enquiries</div><div class="v"><%= st==null?0:st.leadsMtd %></div>
          <div class="k">Admissions</div><div class="v"><%= st==null?0:st.conversionsMtd %></div>
          <div class="k">Open pipeline</div><div class="v"><%= st==null?0:st.leadsTotal %></div>
          <hr>
          <div class="k">Conversion</div><div class="v"><%= st==null?0:st.conversionRate() %>%</div>
        </div>
      </div>

      <div class="card">
        <h2>Fees<a href="<%= ctx %>/fees">Register &rarr;</a></h2>
        <p class="hint">What has come in this month, and what is still owed.</p>
        <div class="kv">
          <div class="k">Collected this month</div>
          <div class="v">Rs. <%= st==null?"0":Money.fmt(BigDecimal.valueOf(st.revenueMtd)) %></div>
          <hr>
          <div class="k">Outstanding dues</div>
          <div class="v <%= (st!=null && st.pendingFees>0)?"warn":"" %>">Rs. <%=
            st==null?"0":Money.fmt(BigDecimal.valueOf(st.pendingFees)) %></div>
        </div>
      </div>

      <div class="card">
        <h2>People<a href="<%= ctx %>/staff">Directory &rarr;</a></h2>
        <div class="kv">
          <div class="k">Active logins</div><div class="v"><%= staffActive %></div>
          <div class="k">Switched off</div><div class="v"><%= i(d.get("staffOff")) %></div>
          <div class="k">Approved leave upcoming</div><div class="v"><%= i(d.get("leaveUpcoming")) %></div>
        </div>
      </div>
    </div>
  </div>

</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

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
    private String first(String name) {
        if (name == null || name.isEmpty()) return "there";
        int sp = name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : name;
    }
    private int i(Object o) { return (o instanceof Integer) ? (Integer) o : 0; }
    private java.math.BigDecimal bd(Object o) {
        return (o instanceof java.math.BigDecimal) ? (java.math.BigDecimal) o
                                                   : java.math.BigDecimal.ZERO;
    }
%>
