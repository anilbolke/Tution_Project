<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.User, com.tution.model.Stats, com.tution.model.SalesStats,
                 com.tution.dao.StatsDAO, com.tution.dao.SalesStatsDAO, com.tution.util.FeeCalculator" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    Stats stats = null;
    try { stats = new StatsDAO().load(); } catch (Exception e) { /* show dashboard without stats */ }

    // Sales strip + leaderboard, management only. Failures here must never take
    // the operational dashboard down with them.
    SalesStats sales = null;
    List<SalesStats> board = null;
    // Quarterly targets are a different period from the month-to-date leaderboard,
    // so they get their own block rather than extra columns that would read as MTD.
    java.util.List<com.tution.model.CounsellorTarget> targets = null;
    String targetQuarter = null;
    // Institute money: the fund, what has been spent, what is owed to vendors and
    // what exam candidates still owe. Admin only, and independent of the sales
    // strip so one failing does not blank the other.
    com.tution.dao.FinanceStatsDAO.FinanceStats fin = null;
    if (user.isAdmin()) {
        try {
            com.tution.dao.TargetDAO tdao = new com.tution.dao.TargetDAO();
            java.time.LocalDate qs = com.tution.dao.TargetDAO.quarterStart(java.time.LocalDate.now());
            targets = tdao.forPeriod("QUARTER", qs, com.tution.dao.TargetDAO.quarterEnd(qs));
            targetQuarter = com.tution.dao.TargetDAO.quarterLabel(qs);
        } catch (Exception e) { /* the dashboard must still render without targets */ }
        try {
            SalesStatsDAO sdao = new SalesStatsDAO();
            sales = sdao.load(null);
            board = sdao.leaderboard();
        } catch (Exception e) { /* dashboard still renders without the sales strip */ }
        try {
            fin = new com.tution.dao.FinanceStatsDAO().load();
        } catch (Exception e) { /* and still renders without the finance strip */ }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Dashboard – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="home"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="welcome">
    <h2>Welcome, <%= user.getFullName() %> 👋</h2>
    <p>Choose a module to get started.</p>
  </div>

  <!-- Global search: the counsellor's first action is almost always "do we know
       this number?", so it sits above the tiles rather than inside a module. -->
  <form class="global-search" method="get" action="<%= ctx %>/search">
    <input type="text" name="q" placeholder="🔍  Search any student or enquiry — mobile, parent mobile, name or admission no">
    <button type="submit">Search</button>
  </form>
  <style>
    .global-search { display:flex; gap:10px; margin-bottom:22px; flex-wrap:wrap; }
    .global-search input { flex:1 1 320px; padding:14px 18px; border:2px solid var(--border);
      border-radius:11px; font-size:14.5px; font-family:inherit; background:var(--white);
      box-shadow:var(--shadow); }
    .global-search input:focus { outline:none; border-color:var(--green); }
    .global-search button { background:var(--green); color:#fff; border:none; border-radius:11px;
      padding:14px 30px; font-size:14px; font-weight:700; cursor:pointer; font-family:inherit; }
    .global-search button:hover { background:var(--green-dark); }
  </style>

  <%-- Sales strip: how the pipeline is performing this month, above the
       operational counters. Management only. --%>
  <% if (sales != null) { %>
  <div class="sales-strip">
    <a class="sc" href="<%= ctx %>/inquiries">
      <div class="v"><%= sales.leadsMtd %></div><div class="l">Enquiries This Month</div>
    </a>
    <a class="sc" href="<%= ctx %>/students">
      <div class="v"><%= sales.conversionsMtd %></div><div class="l">Admissions This Month</div>
    </a>
    <div class="sc">
      <div class="v"><%= sales.conversionRate() %>%</div><div class="l">Conversion Rate</div>
    </div>
    <a class="sc" href="<%= ctx %>/fees">
      <div class="v"><%= FeeCalculator.inr(sales.revenueMtd) %></div><div class="l">Revenue This Month</div>
    </a>
    <a class="sc red" href="<%= ctx %>/fees">
      <div class="v"><%= FeeCalculator.inr(sales.pendingFees) %></div><div class="l">Pending Fees</div>
    </a>
    <a class="sc <%= sales.actionsDue() > 0 ? "amber" : "" %>" href="<%= ctx %>/followup">
      <div class="v"><%= sales.actionsDue() %></div><div class="l">Actions Due Today</div>
    </a>
  </div>
  <style>
    .sales-strip { display:grid; grid-template-columns:repeat(6,1fr); gap:12px; margin-bottom:20px; }
    @media(max-width:1000px){ .sales-strip { grid-template-columns:repeat(3,1fr); } }
    @media(max-width:600px){ .sales-strip { grid-template-columns:repeat(2,1fr); } }
    .sales-strip .sc { background:var(--green-dark); color:#fff; border-radius:var(--radius);
      padding:14px 16px; text-decoration:none; display:block; }
    .sales-strip .sc.red { background:#8E2F24; }
    .sales-strip .sc.amber { background:#8A6100; }
    .sales-strip .sc .v { font-size:21px; font-weight:800; line-height:1.2; }
    .sales-strip .sc .l { font-size:10.5px; text-transform:uppercase; letter-spacing:0.4px;
      opacity:0.82; margin-top:3px; font-weight:700; }
  </style>
  <% } %>

  <%-- ────────── institute money ────────── --%>
  <% if (fin != null) { %>
  <div class="fin-strip">
    <a class="fc <%= fin.overdrawn ? "red" : "" %>" href="<%= ctx %>/fund">
      <div class="v">Rs. <%= com.tution.util.Money.fmt(fin.fundBalance) %></div>
      <div class="l">Fund Balance<%= fin.overdrawn ? " — Overdrawn" : "" %></div>
    </a>
    <a class="fc" href="<%= ctx %>/expenses">
      <div class="v">Rs. <%= com.tution.util.Money.fmt(fin.spentThisMonth) %></div>
      <div class="l">Spent This Month (<%= fin.vouchersMonth %>)</div>
    </a>
    <a class="fc <%= fin.vendorOutstanding.signum() > 0 ? "red" : "" %>"
       href="<%= ctx %>/work-orders?pay=UNPAID">
      <div class="v">Rs. <%= com.tution.util.Money.fmt(fin.vendorOutstanding) %></div>
      <div class="l">Owed To Vendors (<%= fin.openOrders %> orders)</div>
    </a>
    <a class="fc" href="<%= ctx %>/exam-fees">
      <div class="v">Rs. <%= com.tution.util.Money.fmt(fin.examCollected) %></div>
      <div class="l">Exam Fees Collected — <%= fin.examShare() %></div>
    </a>
    <a class="fc <%= fin.examOutstanding.signum() > 0 ? "amber" : "" %>" href="<%= ctx %>/exam-fees">
      <div class="v">Rs. <%= com.tution.util.Money.fmt(fin.examOutstanding) %></div>
      <div class="l">Exam Fees Due (<%= fin.candidatesDue %>)</div>
    </a>
    <a class="fc" href="<%= ctx %>/reports?type=fund-statement">
      <div class="v">📄</div>
      <div class="l">Finance Reports</div>
    </a>
  </div>
  <style>
    .fin-strip { display:grid; grid-template-columns:repeat(6,1fr); gap:12px; margin-bottom:20px; }
    @media(max-width:1000px){ .fin-strip { grid-template-columns:repeat(3,1fr); } }
    @media(max-width:600px){ .fin-strip { grid-template-columns:repeat(2,1fr); } }
    .fin-strip .fc { background:#123B2C; color:#fff; border-radius:var(--radius);
      padding:14px 16px; text-decoration:none; display:block; }
    .fin-strip .fc.red { background:#8E2F24; }
    .fin-strip .fc.amber { background:#8A6100; }
    .fin-strip .fc .v { font-size:19px; font-weight:800; line-height:1.2;
      font-variant-numeric:tabular-nums; }
    .fin-strip .fc .l { font-size:10.5px; text-transform:uppercase; letter-spacing:0.4px;
      opacity:0.82; margin-top:3px; font-weight:700; }
  </style>
  <% } %>

  <% if (stats != null) { %>
  <div class="stats-grid">
    <a class="stat-card" href="<%= ctx %>/students" style="text-decoration:none;">
      <div class="ic2">👥</div>
      <div><div class="v"><%= stats.students %></div><div class="l">Students</div></div>
    </a>
    <a class="stat-card amber" href="<%= ctx %>/inquiries" style="text-decoration:none;">
      <div class="ic2">📥</div>
      <div><div class="v"><%= stats.inquiriesNew %> <span style="font-size:13px;color:var(--muted);font-weight:600;">/ <%= stats.inquiriesTotal %></span></div><div class="l">New Inquiries</div></div>
    </a>
    <a class="stat-card" href="<%= ctx %>/fees" style="text-decoration:none;">
      <div class="ic2">💰</div>
      <div><div class="v"><%= FeeCalculator.inr(stats.collected) %></div><div class="l">Fees Collected</div></div>
    </a>
    <a class="stat-card" href="<%= ctx %>/fees" style="text-decoration:none;">
      <div class="ic2">📆</div>
      <div><div class="v"><%= FeeCalculator.inr(stats.collectedToday) %></div><div class="l">Today's Collection</div></div>
    </a>
    <a class="stat-card red" href="<%= ctx %>/fees" style="text-decoration:none;">
      <div class="ic2">🧾</div>
      <div><div class="v"><%= FeeCalculator.inr(stats.outstanding) %></div><div class="l">Outstanding</div></div>
    </a>
    <a class="stat-card blue" href="<%= ctx %>/attendance" style="text-decoration:none;">
      <div class="ic2">📅</div>
      <div><div class="v"><%= stats.presentToday %><span style="font-size:13px;color:var(--muted);font-weight:600;">/<%= stats.markedToday %></span></div><div class="l">Present Today</div></div>
    </a>
    <a class="stat-card blue" href="<%= ctx %>/exams" style="text-decoration:none;">
      <div class="ic2">🧪</div>
      <div><div class="v"><%= stats.exams %></div><div class="l">Exams</div></div>
    </a>
    <a class="stat-card purple" href="<%= ctx %>/fees" style="text-decoration:none;">
      <div class="ic2">📋</div>
      <div class="fee-break">
        <div><span class="fb-v" style="color:var(--success)"><%= stats.feePaid %></span><span class="fb-l">Paid</span></div>
        <div><span class="fb-v" style="color:#9A6B00"><%= stats.feePartial %></span><span class="fb-l">Partial</span></div>
        <div><span class="fb-v" style="color:#C0392B"><%= stats.feePending %></span><span class="fb-l">Pending</span></div>
      </div>
    </a>
  </div>
  <% } %>

  <div class="module-grid">
    <a class="module-card" href="<%= ctx %>/admission.jsp">
      <div class="ic">📝</div><h3>Admissions</h3>
      <p>Student registration, document upload &amp; batch allocation.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/inquiries">
      <div class="ic">📥</div><h3>Leads &amp; Enquiries</h3>
      <p>The sales pipeline — capture, filter &amp; convert enquiries.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/followup">
      <div class="ic">📞</div><h3>Follow-ups</h3>
      <p>Today's calls, overdue chases &amp; counselling history.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/demo">
      <div class="ic">🎓</div><h3>Counsellor Classes</h3>
      <p>Book trial classes and record Counsellor feedback.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/reminders">
      <div class="ic">🔔</div><h3>Reminders</h3>
      <p>Follow-up, Counsellor &amp; fee-due nudges over WhatsApp.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/students">
      <div class="ic">👥</div><h3>Students</h3>
      <p>View all admitted students &amp; their details.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/fees">
      <div class="ic">💰</div><h3>Fee Management</h3>
      <p>Total / paid / outstanding per student, slab-wise.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/fees">
      <div class="ic">🧾</div><h3>Payment Collection</h3>
      <p>Record Cash / UPI / bank payments &amp; print receipts.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/attendance">
      <div class="ic">📅</div><h3>Attendance</h3>
      <p>Daily marking (P/A/Late/Leave) &amp; per-student reports.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/exams">
      <div class="ic">🧪</div><h3>Examination</h3>
      <p>Create exams, enter subject-wise marks.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/exams">
      <div class="ic">📊</div><h3>Exam Results</h3>
      <p>Totals, %, grade, rank &amp; printable report cards.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/reports">
      <div class="ic">📈</div><h3>Sales Reports</h3>
      <p>Lead source, counsellor performance, collection &amp; ageing — with Excel export.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/materials">
      <div class="ic">📚</div><h3>Learning Materials</h3>
      <p>Upload study PDFs &amp; add e-content YouTube links.</p>
    </a>
    <a class="module-card" href="<%= ctx %>/omr">
      <div class="ic">📄</div><h3>OMR Scanner</h3>
      <p>Read NEET answer sheets locally &amp; auto-score (free).</p>
    </a>
    <a class="module-card" href="<%= ctx %>/manage-tickets">
      <div class="ic">🎫</div><h3>Support Tickets</h3>
      <p>View &amp; respond to concerns raised by students.</p>
    </a>
  </div>

  <%-- Quarterly targets. Kept apart from the leaderboard below because that is a
       month-to-date view and these are quarter-to-date; one table carrying both
       would invite the two to be read as the same period. --%>
  <%
    if (targets != null && !targets.isEmpty()) {
      boolean anyTarget = false;
      long qAdmT = 0, qAdmA = 0, qRevT = 0, qRevA = 0;
      double elapsed = 0;
      for (com.tution.model.CounsellorTarget t : targets) {
        if (!t.isUnset()) anyTarget = true;
        qAdmT += t.getAdmissionsTarget(); qAdmA += t.getAdmissionsActual();
        qRevT += t.getRevenueTarget();    qRevA += t.getRevenueActual();
        elapsed = t.expectedPct();
      }
      if (anyTarget) {
        double admPct = qAdmT > 0 ? qAdmA * 100.0 / qAdmT : 0;
        double revPct = qRevT > 0 ? qRevA * 100.0 / qRevT : 0;
  %>
  <div class="board">
    <h3>Quarterly Targets — <%= targetQuarter %>
        <a href="<%= ctx %>/targets" style="float:right;font-size:11px;text-transform:none;
           letter-spacing:0;font-weight:600;color:var(--green);text-decoration:none;">Set targets →</a></h3>
    <div class="board-wrap">
      <table class="board-tbl">
        <thead>
          <tr><th>Counsellor</th><th>Admissions</th><th>%</th>
              <th>Revenue booked</th><th>%</th><th>Pace</th><th>Still to close</th></tr>
        </thead>
        <tbody>
        <% for (com.tution.model.CounsellorTarget t : targets) {
             if (t.isUnset()) continue; %>
          <tr>
            <td class="who"><%= t.getCounsellorName() %></td>
            <td><b><%= t.getAdmissionsActual() %></b> / <%= t.getAdmissionsTarget() %></td>
            <td><%= String.format("%.0f", t.admissionsPct()) %>%</td>
            <td><%= FeeCalculator.inr(t.getRevenueActual()) %> / <%= FeeCalculator.inr(t.getRevenueTarget()) %></td>
            <td><%= String.format("%.0f", t.revenuePct()) %>%</td>
            <td><span class="pace <%= "Ahead".equals(t.revenueStatus()) ? "p-ahead"
                       : "Behind".equals(t.revenueStatus()) ? "p-behind" : "p-track" %>"><%=
                       t.revenueStatus() %></span></td>
            <td><%= t.admissionsShortfall() %> adm · <%= FeeCalculator.inr(t.revenueShortfall()) %></td>
          </tr>
        <% } %>
          <tr class="tot">
            <td class="who">Team</td>
            <td><b><%= qAdmA %></b> / <%= qAdmT %></td>
            <td><%= String.format("%.0f", admPct) %>%</td>
            <td><%= FeeCalculator.inr(qRevA) %> / <%= FeeCalculator.inr(qRevT) %></td>
            <td><%= String.format("%.0f", revPct) %>%</td>
            <td colspan="2" style="color:var(--muted);font-size:11.5px;">
              <%= String.format("%.0f", elapsed) %>% of the quarter elapsed
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
  <style>
    .pace { font-size:10.5px; font-weight:800; padding:2px 9px; border-radius:11px; white-space:nowrap; }
    .p-ahead { background:#D8EFDF; color:#1B6B39; }
    .p-track { background:#E4EEF7; color:#1D4E79; }
    .p-behind{ background:#F7D4D4; color:#8C2020; }
    .board-tbl tr.tot td { background:#F6F8F9; font-weight:700; border-top:2px solid #DFE4E6; }
  </style>
  <% } } %>

  <%-- Counsellor leaderboard: month-to-date, best converter first. Each row
       opens that counsellor's own dashboard. --%>
  <% if (board != null && !board.isEmpty()) { %>
  <div class="board">
    <h3>Counsellor Performance — This Month</h3>
    <div class="board-wrap">
      <table class="board-tbl">
        <thead>
          <tr><th>#</th><th>Counsellor</th><th>Open Leads</th><th>Enquiries</th>
              <th>Admissions</th><th>Conversion</th><th>Collected</th>
              <th>Actions Due</th><th></th></tr>
        </thead>
        <tbody>
        <% int rank = 1; for (SalesStats b : board) { %>
          <tr>
            <td><%= rank++ %></td>
            <td class="who"><%= b.counsellorName == null ? "—" : b.counsellorName %></td>
            <td><%= b.leadsTotal %></td>
            <td><%= b.leadsMtd %></td>
            <td><b><%= b.conversionsMtd %></b></td>
            <td>
              <div class="rate">
                <span><%= b.conversionRate() %>%</span>
                <div class="rbar"><div class="rfill" style="width:<%= Math.min(100, b.conversionRate()) %>%;"></div></div>
              </div>
            </td>
            <td><%= FeeCalculator.inr(b.revenueMtd) %></td>
            <td><%= b.actionsDue() > 0
                    ? "<span class=\"due\">" + b.actionsDue() + "</span>" : "0" %></td>
            <td><a class="open" href="<%= ctx %>/my-dashboard?counsellorId=<%= b.counsellorId %>">View →</a></td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  </div>
  <style>
    .board { margin-top:28px; }
    .board h3 { font-size:13px; text-transform:uppercase; letter-spacing:0.6px;
      color:var(--green-dark); margin-bottom:12px; }
    .board-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      overflow-x:auto; }
    .board-tbl { width:100%; border-collapse:collapse; font-size:13px; min-width:820px; }
    .board-tbl th { background:var(--green); color:#fff; text-align:left; padding:10px 13px;
      font-size:10.5px; text-transform:uppercase; letter-spacing:0.4px; font-weight:700; }
    .board-tbl td { padding:10px 13px; border-bottom:1px solid var(--border); }
    .board-tbl tr:last-child td { border-bottom:none; }
    .board-tbl tr:nth-child(even) td { background:var(--green-pale); }
    .board-tbl .who { font-weight:700; color:var(--green-dark); }
    .board-tbl .rate { display:flex; align-items:center; gap:8px; }
    .board-tbl .rbar { flex:1; height:7px; background:var(--green-light); border-radius:4px;
      overflow:hidden; min-width:50px; }
    .board-tbl .rfill { height:100%; background:var(--green); }
    .board-tbl .due { display:inline-block; background:#FDE2E0; color:#C0392B; font-weight:800;
      font-size:11px; padding:2px 9px; border-radius:10px; }
    .board-tbl .open { color:var(--green-dark); font-weight:700; text-decoration:none; font-size:12px; }
    .board-tbl .open:hover { text-decoration:underline; }
  </style>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
