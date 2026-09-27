<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal, java.util.*" %>
<%@ page import="com.tution.dao.ExpenseDAO, com.tution.dao.WorkOrderDAO" %>
<%@ page import="com.tution.model.User, com.tution.model.SalesStats,
                 com.tution.model.FundAccount, com.tution.model.FundTransaction" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<FundAccount> funds =
        (List<FundAccount>) request.getAttribute("funds");
    if (funds == null) funds = new ArrayList<FundAccount>();
    @SuppressWarnings("unchecked") List<FundTransaction> recent =
        (List<FundTransaction>) request.getAttribute("recent");
    if (recent == null) recent = new ArrayList<FundTransaction>();

    BigDecimal held = (BigDecimal) request.getAttribute("held");
    if (held == null) held = Money.ZERO;
    Integer overdrawn = (Integer) request.getAttribute("overdrawn");
    ExpenseDAO.Totals spend   = (ExpenseDAO.Totals) request.getAttribute("spend");
    WorkOrderDAO.Totals payable = (WorkOrderDAO.Totals) request.getAttribute("payable");
    SalesStats st = (SalesStats) request.getAttribute("stats");
    String from = (String) request.getAttribute("from");
    String to   = (String) request.getAttribute("to");
    String error = (String) request.getAttribute("error");

    BigDecimal biggest = Money.ZERO;
    for (FundAccount f : funds) {
        BigDecimal b = f.getBalance().abs();
        if (b.compareTo(biggest) > 0) biggest = b;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Finance Dashboard – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .dw { max-width:1180px; margin:18px auto; padding:0 14px; }
  .dh { margin:0 0 16px; }
  .dh h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .dh .sub { margin:3px 0 0; font-size:12.5px; color:#7b8b93; }

  .tiles { display:grid; grid-template-columns:repeat(auto-fit,minmax(210px,1fr)); gap:12px;
           margin-bottom:18px; }
  .tile { background:#fff; border:1px solid #e3e6e8; border-left:4px solid #0E5C3F;
          border-radius:10px; padding:14px 16px; text-decoration:none; color:inherit; display:block; }
  .tile:hover { box-shadow:0 4px 14px rgba(14,92,63,.10); }
  .tile.red { border-left-color:#c0392b; }
  .tile.blue { border-left-color:#1B4F9C; }
  .tile .lb { font-size:11px; color:#5a6b73; font-weight:700; text-transform:uppercase;
              letter-spacing:.35px; }
  .tile .vl { font-size:23px; font-weight:700; color:#0E5C3F; margin-top:4px;
              font-variant-numeric:tabular-nums; line-height:1.1; }
  .tile.red .vl { color:#c0392b; }
  .tile .sb { font-size:11.5px; color:#7b8b93; margin-top:3px; }

  .grid2 { display:grid; grid-template-columns:1.15fr 1fr; gap:14px; align-items:start; }
  @media(max-width:900px){ .grid2 { grid-template-columns:1fr; } }

  .card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px;
          margin-bottom:14px; }
  .card h2 { margin:0 0 3px; font-size:15px; }
  .card h2 a { float:right; font-size:11.5px; font-weight:600; color:#0E5C3F;
               text-decoration:none; }
  .card p.hint { color:#5a6b73; font-size:12.5px; margin:0 0 13px; }

  .fbar { display:flex; align-items:center; gap:11px; font-size:13px; padding:7px 0; }
  .fbar + .fbar { border-top:1px solid #f2f6f4; }
  .fbar .nm { width:150px; color:#2f4a3c; font-weight:600; overflow:hidden;
              text-overflow:ellipsis; white-space:nowrap; }
  .fbar .track { flex:1; min-width:50px; height:10px; background:#eef3f0; border-radius:6px;
                 overflow:hidden; }
  .fbar .track i { display:block; height:100%; background:linear-gradient(90deg,#1b8a5c,#0E5C3F);
                   border-radius:6px; }
  .fbar.neg .track i { background:linear-gradient(90deg,#d4614f,#c0392b); }
  .fbar .vl { width:120px; text-align:right; font-variant-numeric:tabular-nums; font-weight:700;
              color:#1f3a2c; }
  .fbar.neg .vl { color:#c0392b; }
  .fbar .closed { font-size:10px; background:#eceff1; color:#5a6b73; padding:1px 7px;
                  border-radius:10px; }

  .kv { display:grid; grid-template-columns:1fr auto; gap:9px 12px; font-size:13.5px; }
  .kv .k { color:#5a6b73; }
  .kv .v { font-weight:700; color:#1f3a2c; font-variant-numeric:tabular-nums; text-align:right; }
  .kv .v.warn { color:#c0392b; }
  .kv hr { grid-column:1/-1; border:0; border-top:1px solid #eceff1; margin:2px 0; }

  table.t { width:100%; border-collapse:collapse; font-size:12.5px; }
  table.t td { padding:7px 6px; border-bottom:1px solid #f2f6f4; vertical-align:top; }
  table.t tr:last-child td { border-bottom:none; }
  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .cr { color:#1b6b39; } .dr { color:#8c2020; }
  .muted { color:#7b8b93; }
  .tag { font-size:10px; font-weight:800; padding:2px 7px; border-radius:10px; letter-spacing:.3px;
         text-transform:uppercase; background:#e9eef0; color:#42555e; white-space:nowrap; }

  /* Quick links as proper targets. A bare column of blue text reads as leftover
     scaffolding; these are the size of something you actually tap. */
  .goto { display:grid; grid-template-columns:repeat(auto-fit,minmax(158px,1fr)); gap:10px; }
  .goto a { display:flex; align-items:center; gap:10px; text-decoration:none; color:inherit;
            border:1px solid #dfe7e2; border-radius:9px; padding:11px 13px; background:#fbfdfc; }
  .goto a:hover { border-color:#0E5C3F; background:#f2f9f5; }
  .goto .ic { width:30px; height:30px; flex:none; border-radius:8px; background:#eaf6ef;
              display:flex; align-items:center; justify-content:center; font-size:15px; }
  .goto .tx { min-width:0; }
  .goto .t1 { font-size:13px; font-weight:650; color:#1f3a2c; display:block; }
  .goto .t2 { font-size:11px; color:#8b9aa1; margin-top:1px; display:block; }

  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .warnbar { background:#fdf1e3; border:1px solid #f0d3ae; border-left:4px solid #8a4b12;
             border-radius:8px; padding:12px 15px; font-size:12.5px; color:#8a4b12;
             margin-bottom:14px; }
  .warnbar a { color:#6b3a0d; font-weight:700; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="home"/>
</jsp:include>

<div class="dw">

  <div class="dh">
    <h1>Good day, <%= esc(first(user.getFullName())) %></h1>
    <p class="sub"><%= esc(from) %> to <%= esc(to) %> &middot; <%= funds.size() %>
       fund<%= funds.size()==1?"":"s" %></p>
  </div>

  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <% if (overdrawn != null && overdrawn > 0) { %>
    <div class="warnbar">
      <b><%= overdrawn %></b> fund<%= overdrawn==1?" is":"s are" %> overdrawn &mdash; more has been
      spent than credited. Usually a top-up that has not been entered yet.
      <% if (user.mayOpen("/fund")) { %><a href="<%= ctx %>/fund">Check the statement &rarr;</a><% } %>
    </div>
  <% } %>

  <div class="tiles">
    <% if (user.mayOpen("/fund")) { %><a class="tile <%= held.signum()<0 ? "red" : "" %>" href="<%= ctx %>/fund">
      <div class="lb">Held across all funds</div>
      <div class="vl">Rs. <%= Money.fmt(held) %></div>
      <div class="sb">the money available to spend</div>
    </a><% } %>
    <% if (user.mayOpen("/expenses")) { %><a class="tile red" href="<%= ctx %>/expenses">
      <div class="lb">Spent this month</div>
      <div class="vl">Rs. <%= spend==null?"0.00":Money.fmt(spend.spent) %></div>
      <div class="sb"><%= spend==null?0:spend.vouchers %> voucher<%=
        (spend!=null&&spend.vouchers==1)?"":"s" %>, live only</div>
    </a><% } %>
    <% if (user.mayOpen("/fees")) { %><a class="tile blue" href="<%= ctx %>/fees">
      <div class="lb">Fees collected</div>
      <div class="vl">Rs. <%= st==null?"0":Money.fmt(BigDecimal.valueOf(st.revenueMtd)) %></div>
      <div class="sb">this month</div>
    </a><% } %>
    <% if (user.mayOpen("/fees")) { %><a class="tile <%= (st!=null && st.pendingFees>0) ? "red" : "" %>" href="<%= ctx %>/fees">
      <div class="lb">Fees outstanding</div>
      <div class="vl">Rs. <%= st==null?"0":Money.fmt(BigDecimal.valueOf(st.pendingFees)) %></div>
      <div class="sb">owed to the institute</div>
    </a><% } %>
    <% if (user.mayOpen("/work-orders")) { %><a class="tile <%= (payable!=null && payable.remaining.signum()>0) ? "red" : "" %>"
       href="<%= ctx %>/work-orders">
      <div class="lb">Payables</div>
      <div class="vl">Rs. <%= payable==null?"0.00":Money.fmt(payable.remaining) %></div>
      <div class="sb">owed on <%= payable==null?0:payable.orders %> work order<%=
        (payable!=null&&payable.orders==1)?"":"s" %></div>
    </a><% } %>
  </div>

  <div class="grid2">
    <div>
      <div class="card">
        <h2>Where the money sits<% if (user.mayOpen("/fund")) { %><a href="<%= ctx %>/fund">Statements &rarr;</a><% } %></h2>
        <p class="hint">Balance of each fund. Bars are relative to the largest.</p>
        <% for (FundAccount f : funds) {
             boolean neg = f.isOverdrawn();
             int w = biggest.signum()==0 ? 0
                   : (int) Math.round(f.getBalance().abs().doubleValue() * 100.0
                                      / biggest.doubleValue()); %>
          <div class="fbar <%= neg ? "neg" : "" %>">
            <span class="nm"><%= esc(f.getName()) %>
              <% if (!f.isActive()) { %><span class="closed">closed</span><% } %></span>
            <span class="track"><i style="width:<%= w %>%"></i></span>
            <span class="vl">Rs. <%= Money.fmt(f.getBalance()) %></span>
          </div>
        <% } %>
        <% if (funds.isEmpty()) { %>
          <p class="muted" style="font-size:13px;margin:0">No fund yet.
             <% if (user.mayOpen("/fund")) { %><a href="<%= ctx %>/fund?new=1">Create one &rarr;</a><% } %></p>
        <% } %>
      </div>

      <div class="card">
        <h2>Latest movements<% if (user.mayOpen("/fund")) { %><a href="<%= ctx %>/fund">All &rarr;</a><% } %></h2>
        <p class="hint">The most recent entries across every fund.</p>
        <table class="t">
          <% for (FundTransaction t : recent) { %>
            <tr>
              <td class="muted" style="width:80px;white-space:nowrap"><%= esc(t.getTxnDate()) %></td>
              <td><%= esc(t.getNarration()==null ? t.getSourceLabel() : t.getNarration()) %>
                <div class="muted" style="font-size:11px"><%= esc(t.getFundName()) %></div></td>
              <td style="width:82px"><span class="tag"><%= esc(t.getSourceLabel()) %></span></td>
              <td class="num <%= t.isCredit()?"cr":"dr" %>" style="width:100px"><%=
                (t.isCredit()?"+":"−") %> <%= Money.fmt(t.getAmount()) %></td>
            </tr>
          <% } %>
          <% if (recent.isEmpty()) { %>
            <tr><td class="muted" style="padding:14px 6px">Nothing posted yet.</td></tr>
          <% } %>
        </table>
      </div>
    </div>

    <div>
      <div class="card">
        <h2>This month<% if (user.mayOpen("/expenses")) { %><a href="<%= ctx %>/expenses">Expenses &rarr;</a><% } %></h2>
        <div class="kv">
          <div class="k">Spent</div>
          <div class="v">Rs. <%= spend==null?"0.00":Money.fmt(spend.spent) %></div>
          <div class="k">Of which tax</div>
          <div class="v">Rs. <%= spend==null?"0.00":Money.fmt(spend.tax) %></div>
          <div class="k">Vouchers</div><div class="v"><%= spend==null?0:spend.vouchers %></div>
          <hr>
          <div class="k">Fees collected</div>
          <div class="v">Rs. <%= st==null?"0":Money.fmt(BigDecimal.valueOf(st.revenueMtd)) %></div>
        </div>
      </div>

      <div class="card">
        <h2>Owed both ways</h2>
        <p class="hint">What to chase, and what to settle.</p>
        <div class="kv">
          <div class="k">Fees owed to the institute</div>
          <div class="v <%= (st!=null && st.pendingFees>0)?"warn":"" %>">Rs. <%=
            st==null?"0":Money.fmt(BigDecimal.valueOf(st.pendingFees)) %></div>
          <hr>
          <div class="k">Ordered from vendors</div>
          <div class="v">Rs. <%= payable==null?"0.00":Money.fmt(payable.ordered) %></div>
          <div class="k">Already settled</div>
          <div class="v">Rs. <%= payable==null?"0.00":Money.fmt(payable.paid) %></div>
          <div class="k">Still payable</div>
          <div class="v <%= (payable!=null && payable.remaining.signum()>0)?"warn":"" %>">Rs. <%=
            payable==null?"0.00":Money.fmt(payable.remaining) %></div>
        </div>
      </div>

      <div class="card">
        <h2>Go to</h2>
        <p class="hint">The screens behind the figures above.</p>
        <div class="goto">
          <% if (user.mayOpen("/fund")) { %><a href="<%= ctx %>/fund"><span class="ic">🏦</span>
            <span class="tx"><span class="t1">Fund statements</span>
              <span class="t2"><%= funds.size() %> fund<%= funds.size()==1?"":"s" %></span></span></a><% } %>
          <% if (user.mayOpen("/expenses")) { %><a href="<%= ctx %>/expenses"><span class="ic">🧾</span>
            <span class="tx"><span class="t1">Record an expense</span>
              <span class="t2"><%= spend==null?0:spend.vouchers %> this month</span></span></a><% } %>
          <% if (user.mayOpen("/vendors")) { %><a href="<%= ctx %>/vendors"><span class="ic">🏬</span>
            <span class="tx"><span class="t1">Vendors</span>
              <span class="t2">Who we pay</span></span></a><% } %>
          <% if (user.mayOpen("/work-orders")) { %><a href="<%= ctx %>/work-orders"><span class="ic">📋</span>
            <span class="tx"><span class="t1">Work orders</span>
              <span class="t2"><%= payable==null?0:payable.orders %> on the books</span></span></a><% } %>
          <% if (user.mayOpen("/exam-fees")) { %><a href="<%= ctx %>/exam-fees"><span class="ic">🎟️</span>
            <span class="tx"><span class="t1">Exam fees</span>
              <span class="t2">Take at the counter</span></span></a><% } %>
          <% if (user.canSeeManagement()) { %><a href="<%= ctx %>/reports"><span class="ic">📊</span>
            <span class="tx"><span class="t1">Reports</span>
              <span class="t2">Collection &amp; more</span></span></a><% } %>
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
%>
