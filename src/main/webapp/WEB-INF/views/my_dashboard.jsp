<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.User, com.tution.model.SalesStats,
                 com.tution.model.Inquiry, com.tution.model.LeadDemo, com.tution.util.FeeCalculator,
                 com.tution.util.Dates" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    SalesStats st = (SalesStats) request.getAttribute("stats");
    @SuppressWarnings("unchecked") List<Inquiry>  dueLeads   = (List<Inquiry>)  request.getAttribute("dueLeads");
    @SuppressWarnings("unchecked") List<Inquiry>  hotLeads   = (List<Inquiry>)  request.getAttribute("hotLeads");
    @SuppressWarnings("unchecked") List<LeadDemo> todayDemos = (List<LeadDemo>) request.getAttribute("todayDemos");
    @SuppressWarnings("unchecked") Map<Integer,String> counsellors =
        (Map<Integer,String>) request.getAttribute("counsellors");
    Integer targetId = (Integer) request.getAttribute("targetId");
    Boolean viewingOther = (Boolean) request.getAttribute("viewingOther");
    String today = (String) request.getAttribute("today");
    String error = (String) request.getAttribute("error");
    if (today == null) today = java.time.LocalDate.now().toString();

    String whoName = user.getFullName();
    if (Boolean.TRUE.equals(viewingOther) && counsellors != null && targetId != null
            && counsellors.containsKey(targetId)) {
        whoName = counsellors.get(targetId);
    }
%>
<%!
    private String stCls(String s) {
        if ("Ahead".equals(s))    return "s-ahead";
        if ("On track".equals(s)) return "s-track";
        if ("Behind".equals(s))   return "s-behind";
        return "s-none";
    }
    private String bar(double achieved, double expected) {
        double a = Math.max(0, Math.min(100, achieved));
        double e = Math.max(0, Math.min(100, expected));
        return "<div class=\"bar\"><i style=\"width:" + a + "%\"></i>"
             + "<span class=\"exp\" style=\"left:" + e + "%\"></span></div>";
    }
    private String esc(String x) {
        if (x == null) return "";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String d(String x) { return (x == null || x.isEmpty()) ? "—" : esc(x); }
    private String badgeClass(String s) {
        if (s == null) return "st-new";
        switch (s) {
            case "CONTACTED":         return "st-contacted";
            case "INTERESTED":        return "st-interested";
            case "DEMO_PENDING":
            case "DEMO_COMPLETED":    return "st-demo";
            case "FOLLOWUP_REQUIRED": return "st-followup";
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
  <title>My Dashboard – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .welcome { margin-bottom:18px; }
    .welcome h2 { font-family:'Playfair Display',serif; font-size:26px; color:var(--green-dark); }
    .welcome p { font-size:13.5px; color:var(--muted); margin-top:3px; }

    .kpis { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; margin-bottom:20px; }
    @media(max-width:900px){ .kpis { grid-template-columns:repeat(2,1fr); } }
    @media(max-width:520px){ .kpis { grid-template-columns:1fr; } }
    .kpi { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:16px 18px; border-left:5px solid var(--green); text-decoration:none; display:block; }
    .kpi.red { border-left-color:#C0392B; }
    .kpi.amber { border-left-color:#D69E00; }
    .kpi.blue { border-left-color:#1B4F9C; }
    .kpi .n { font-size:27px; font-weight:800; color:var(--green-dark); line-height:1.15; }
    .kpi.red .n { color:#C0392B; }
    .kpi .l { font-size:11.5px; color:var(--muted); text-transform:uppercase; letter-spacing:0.4px;
      font-weight:700; margin-top:4px; }
    .kpi .s { font-size:11.5px; color:var(--muted); margin-top:3px; }

    .cols { display:grid; grid-template-columns:1.4fr 1fr; gap:18px; align-items:start; }
    @media(max-width:900px){ .cols { grid-template-columns:1fr; } }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:20px 22px; margin-bottom:18px; }
    .card h3 { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:var(--green-dark);
      padding-bottom:9px; border-bottom:2px solid var(--green-light); margin-bottom:14px;
      display:flex; justify-content:space-between; align-items:center; }
    .card h3 a { font-size:11.5px; font-weight:600; color:var(--green); text-decoration:none;
      text-transform:none; letter-spacing:0; }

    table.mini { width:100%; border-collapse:collapse; font-size:13px; }
    table.mini th { text-align:left; padding:7px 8px; font-size:10.5px; text-transform:uppercase;
      letter-spacing:0.3px; color:var(--muted); border-bottom:1.5px solid var(--border); }
    table.mini td { padding:8px; border-bottom:1px solid var(--border); }
    table.mini tr:last-child td { border-bottom:none; }
    table.mini tr.late td { background:#FFF5F4; }
    .nm { font-weight:700; color:var(--green-dark); text-decoration:none; }
    .nm:hover { text-decoration:underline; }
    .badge { display:inline-block; font-size:9.5px; font-weight:800; padding:2px 8px; border-radius:10px;
      text-transform:uppercase; letter-spacing:0.3px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; }
    .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-interested { background:#E4DDFF; color:#4B2E9C; }
    .st-demo { background:#D9F2F7; color:#0F6C7E; }
    .st-followup { background:#FFE6D6; color:#9C4A16; }
    .pr-hot { background:#FDE2E0; color:#C0392B; }
    .pr-warm { background:#FFF4D6; color:#9A6B00; }
    .pr-cold { background:#E6F0FF; color:#1B4F9C; }
    .due-late { color:#C0392B; font-weight:700; }

    /* Pipeline funnel: bar width is share of the open pipeline. */
    .funnel .f-row { margin-bottom:10px; }
    .funnel .f-top { display:flex; justify-content:space-between; font-size:12.5px; margin-bottom:3px; }
    .funnel .f-bar { height:9px; border-radius:5px; background:var(--green-light); overflow:hidden; }
    .funnel .f-fill { height:100%; background:var(--green); border-radius:5px; }
    .funnel .f-fill.amber { background:#D69E00; }
    .funnel .f-fill.blue { background:#1B4F9C; }
    .funnel .f-fill.grey { background:#B9C4BD; }

    .empty { text-align:center; padding:26px 12px; color:var(--muted); font-size:13px; }
    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.info { background:#E6F0FF; color:#1B4F9C; border:1px solid #BBD3F5; }
    .picker { display:flex; gap:9px; align-items:center; flex-wrap:wrap; margin-bottom:16px; }
    .picker select { padding:8px 11px; border:1.5px solid var(--border); border-radius:7px;
      font-size:13px; font-family:inherit; }
    /* ── own quarterly target ── */
    .tgt-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:16px 18px; margin-bottom:18px; border-left:5px solid #2f9e5f; }
    .tgt-head { display:flex; justify-content:space-between; align-items:baseline; margin-bottom:10px; }
    .tgt-title { font-weight:800; color:var(--green-dark); font-size:14px; letter-spacing:.3px; }
    .tgt-left { font-size:12px; color:var(--muted); }
    .tgt-row { display:grid; grid-template-columns:88px 150px 1fr 44px 74px; gap:10px;
      align-items:center; margin-bottom:8px; }
    @media(max-width:700px){ .tgt-row { grid-template-columns:80px 1fr; } .tgt-row .bar { grid-column:1/-1; } }
    .tgt-lbl { font-size:11px; font-weight:700; text-transform:uppercase; color:var(--muted); }
    .tgt-val { font-size:13px; font-weight:700; color:var(--green-dark); }
    .tgt-pct { font-size:13px; font-weight:700; text-align:right; }
    .bar { position:relative; height:16px; background:#eef1f2; border-radius:9px; overflow:hidden; }
    .bar i { position:absolute; left:0; top:0; bottom:0; background:#2f9e5f; }
    .bar .exp { position:absolute; top:-2px; bottom:-2px; width:2px; background:#42555e; }
    .tgt-st { font-size:10.5px; font-weight:800; padding:3px 9px; border-radius:12px; text-align:center; }
    .s-ahead { background:#d8efdf; color:#1b6b39; }
    .s-track { background:#e4eef7; color:#1d4e79; }
    .s-behind{ background:#f7d4d4; color:#8c2020; }
    .s-none  { background:#f0f2f3; color:#69777e; }
    .tgt-foot { font-size:11.5px; color:var(--muted); margin-top:8px; line-height:1.5; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="mysales"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="welcome">
    <h2><%= Boolean.TRUE.equals(viewingOther) ? esc(whoName) : "Welcome, " + esc(whoName) + " 👋" %></h2>
    <p><%= Boolean.TRUE.equals(viewingOther)
            ? "Counsellor performance view"
            : "Your pipeline and everything due today." %></p>
  </div>

  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <%-- Own quarterly target. A counsellor sees only their own. --%>
  <%
    com.tution.model.CounsellorTarget mt =
        (com.tution.model.CounsellorTarget) request.getAttribute("myTarget");
    String qLabel = (String) request.getAttribute("quarterLabel");
    if (mt != null && !mt.isUnset()) {
  %>
    <div class="tgt-card">
      <div class="tgt-head">
        <span class="tgt-title">Your target — <%= esc(qLabel) %></span>
        <span class="tgt-left"><%= mt.daysLeft() %> day<%= mt.daysLeft()==1?"":"s" %> left</span>
      </div>

      <div class="tgt-row">
        <span class="tgt-lbl">Admissions</span>
        <span class="tgt-val"><%= mt.getAdmissionsActual() %> / <%= mt.getAdmissionsTarget() %></span>
        <%= bar(mt.admissionsPct(), mt.expectedPct()) %>
        <span class="tgt-pct"><%= String.format("%.0f", mt.admissionsPct()) %>%</span>
        <span class="tgt-st <%= stCls(mt.admissionsStatus()) %>"><%= mt.admissionsStatus() %></span>
      </div>

      <div class="tgt-row">
        <span class="tgt-lbl">Revenue</span>
        <span class="tgt-val">Rs. <%= String.format("%,d", mt.getRevenueActual()) %>
              / <%= String.format("%,d", mt.getRevenueTarget()) %></span>
        <%= bar(mt.revenuePct(), mt.expectedPct()) %>
        <span class="tgt-pct"><%= String.format("%.0f", mt.revenuePct()) %>%</span>
        <span class="tgt-st <%= stCls(mt.revenueStatus()) %>"><%= mt.revenueStatus() %></span>
      </div>

      <p class="tgt-foot">
        The marker on each bar is where <%= String.format("%.0f", mt.expectedPct()) %>% of the quarter
        has gone &mdash; being ahead of it matters more than the headline number until the quarter ends.
        Revenue counts admissions closed this quarter; cash collected so far is
        Rs. <%= String.format("%,d", mt.getCollectedActual()) %>.
        <% if (mt.admissionsShortfall() > 0 || mt.revenueShortfall() > 0) { %>
          <br>Still to close: <b><%= mt.admissionsShortfall() %></b> admission(s),
          <b>Rs. <%= String.format("%,d", mt.revenueShortfall()) %></b>.
        <% } else { %>
          <br><b>Target met.</b>
        <% } %>
      </p>
    </div>
  <% } %>

  <% if (user.isAdmin()) { %>
    <form class="picker" method="get" action="<%= ctx %>/my-dashboard">
      <label style="font-size:12.5px;color:var(--muted);font-weight:700;">Viewing:</label>
      <select name="counsellorId" onchange="this.form.submit()">
        <% if (counsellors != null) for (Map.Entry<Integer,String> e : counsellors.entrySet()) {
             boolean on = targetId != null && targetId.intValue() == e.getKey(); %>
          <option value="<%= e.getKey() %>"<%= on ? " selected" : "" %>><%= esc(e.getValue()) %></option>
        <% } %>
      </select>
      <a href="<%= ctx %>/dashboard.jsp" style="font-size:12.5px;color:var(--muted);">← Institute dashboard</a>
    </form>
  <% } %>

  <% if (st != null) { %>
  <div class="kpis">
    <a class="kpi red" href="<%= ctx %>/followup?view=overdue">
      <div class="n"><%= st.followupsOverdue %></div>
      <div class="l">Overdue Follow-ups</div>
      <div class="s">need chasing now</div>
    </a>
    <a class="kpi amber" href="<%= ctx %>/followup?view=today">
      <div class="n"><%= st.followupsToday %></div>
      <div class="l">Due Today</div>
      <div class="s">calls scheduled for today</div>
    </a>
    <a class="kpi blue" href="<%= ctx %>/demo">
      <div class="n"><%= st.demosToday %></div>
      <div class="l">Demos Today</div>
      <div class="s"><%= st.demosUpcoming %> in the next 7 days</div>
    </a>
    <a class="kpi" href="<%= ctx %>/inquiries">
      <div class="n"><%= st.leadsTotal %></div>
      <div class="l">Open Leads</div>
      <div class="s"><%= st.leadsNew %> not yet contacted</div>
    </a>
  </div>

  <div class="kpis">
    <div class="kpi">
      <div class="n"><%= st.leadsMtd %></div>
      <div class="l">Enquiries This Month</div>
    </div>
    <div class="kpi">
      <div class="n"><%= st.conversionsMtd %></div>
      <div class="l">Admissions This Month</div>
    </div>
    <div class="kpi">
      <div class="n"><%= st.conversionRate() %>%</div>
      <div class="l">Conversion Rate</div>
      <div class="s"><%= st.conversionsMtd %> of <%= st.leadsMtd %> leads</div>
    </div>
    <div class="kpi blue">
      <div class="n"><%= FeeCalculator.inr(st.revenueMtd) %></div>
      <div class="l">Collected This Month</div>
      <div class="s"><%= FeeCalculator.inr(st.pendingFees) %> still pending</div>
    </div>
  </div>
  <% } %>

  <div class="cols">
    <div>
      <div class="card">
        <h3>Follow-ups Due <a href="<%= ctx %>/followup">Open the queue →</a></h3>
        <% if (dueLeads == null || dueLeads.isEmpty()) { %>
          <div class="empty">✅ Nothing due. You are all caught up.</div>
        <% } else { %>
          <table class="mini">
            <thead><tr><th>Due</th><th>Name</th><th>Mobile</th><th>Priority</th><th>Status</th></tr></thead>
            <tbody>
            <% int shown = 0;
               for (Inquiry q : dueLeads) { if (shown++ >= 10) break;
                 boolean late = q.isOverdue(today); %>
              <tr class="<%= late ? "late" : "" %>">
                <td class="<%= late ? "due-late" : "" %>"><%= d(Dates.display(q.getNextFollowupDate())) %></td>
                <td><a class="nm" href="<%= ctx %>/lead?id=<%= q.getInquiryId() %>"><%= esc(q.getFullName()) %></a></td>
                <td><%= d(q.getMobile()) %></td>
                <td><span class="badge <%= prioClass(q.getPriority()) %>"><%= d(q.getPriority()) %></span></td>
                <td><span class="badge <%= badgeClass(q.getStatus()) %>"><%= d(q.getStatus()) %></span></td>
              </tr>
            <% } %>
            </tbody>
          </table>
          <% if (dueLeads.size() > 10) { %>
            <p style="font-size:12.5px;color:var(--muted);margin-top:10px;">
              and <%= dueLeads.size() - 10 %> more —
              <a href="<%= ctx %>/followup?view=all" style="color:var(--green);font-weight:600;">see all →</a></p>
          <% } %>
        <% } %>
      </div>

      <div class="card">
        <h3>Upcoming Counsellors <a href="<%= ctx %>/demo">Counsellor diary →</a></h3>
        <% if (todayDemos == null || todayDemos.isEmpty()) { %>
          <div class="empty">No Counsellors booked in the next 7 days.</div>
        <% } else { %>
          <table class="mini">
            <thead><tr><th>Date</th><th>Time</th><th>Student</th><th>Subject</th><th>Faculty</th></tr></thead>
            <tbody>
            <% for (LeadDemo dm : todayDemos) { %>
              <tr class="<%= today.equals(dm.getDemoDate()) ? "late" : "" %>">
                <td><b><%= d(dm.getDemoDate()) %></b></td>
                <td><%= d(dm.getDemoTime()) %></td>
                <td><a class="nm" href="<%= ctx %>/lead?id=<%= dm.getInquiryId() %>"><%= esc(dm.getLeadName()) %></a></td>
                <td><%= d(dm.getSubject()) %></td>
                <td><%= d(dm.getFacultyName()) %></td>
              </tr>
            <% } %>
            </tbody>
          </table>
        <% } %>
      </div>
    </div>

    <div>
      <% if (st != null) { %>
      <div class="card">
        <h3>My Pipeline</h3>
        <div class="funnel">
          <% int max = Math.max(1, st.leadsTotal);
             String[][] rows = {
               {"New",                String.valueOf(st.leadsNew),        "",      "NEW"},
               {"Contacted",          String.valueOf(st.leadsContacted),  "blue",  "CONTACTED"},
               {"Interested",         String.valueOf(st.leadsInterested), "blue",  "INTERESTED"},
               {"Demo stage",         String.valueOf(st.leadsDemo),       "amber", "DEMO_PENDING"},
               {"Follow-up required", String.valueOf(st.leadsFollowup),   "amber", "FOLLOWUP_REQUIRED"},
               {"Lost / not interested", String.valueOf(st.leadsLost),    "grey",  "LOST"}
             };
             for (String[] r : rows) {
               int n = Integer.parseInt(r[1]);
               int pct = (int) Math.round(n * 100.0 / max); %>
            <div class="f-row">
              <div class="f-top">
                <a class="nm" style="font-weight:600;font-size:12.5px;"
                   href="<%= ctx %>/inquiries?status=<%= r[3] %>"><%= r[0] %></a>
                <b><%= n %></b>
              </div>
              <div class="f-bar"><div class="f-fill <%= r[2] %>" style="width:<%= Math.min(100, pct) %>%;"></div></div>
            </div>
          <% } %>
        </div>
      </div>
      <% } %>

      <div class="card">
        <h3>Hot Leads <a href="<%= ctx %>/inquiries?priority=Hot">See all →</a></h3>
        <% if (hotLeads == null || hotLeads.isEmpty()) { %>
          <div class="empty">No hot leads right now.</div>
        <% } else { %>
          <table class="mini">
            <thead><tr><th>Name</th><th>Mobile</th><th>Status</th></tr></thead>
            <tbody>
            <% int hs = 0; for (Inquiry q : hotLeads) { if (hs++ >= 8) break; %>
              <tr>
                <td><a class="nm" href="<%= ctx %>/lead?id=<%= q.getInquiryId() %>"><%= esc(q.getFullName()) %></a></td>
                <td><%= d(q.getMobile()) %></td>
                <td><span class="badge <%= badgeClass(q.getStatus()) %>"><%= d(q.getStatus()) %></span></td>
              </tr>
            <% } %>
            </tbody>
          </table>
        <% } %>
      </div>
    </div>
  </div>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
