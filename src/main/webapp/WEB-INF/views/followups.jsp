<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.Inquiry, com.tution.model.User,
                 com.tution.util.Dates, java.net.URLEncoder" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<Inquiry> leads = (List<Inquiry>) request.getAttribute("leads");
    int[] counts = (int[]) request.getAttribute("counts");
    String view  = (String) request.getAttribute("view");
    String error = (String) request.getAttribute("error");
    String msg   = request.getParameter("msg");
    if (view == null) view = "today";
    String today = java.time.LocalDate.now().toString();
    int total = (leads == null) ? 0 : leads.size();
    int cToday   = (counts != null && counts.length > 0) ? counts[0] : 0;
    int cOverdue = (counts != null && counts.length > 1) ? counts[1] : 0;
    int cWeek    = (counts != null && counts.length > 2) ? counts[2] : 0;
    @SuppressWarnings("unchecked") List<String> stages = (List<String>) request.getAttribute("stages");
    String subStagesJson = (String) request.getAttribute("subStagesJson");
    if (subStagesJson == null) subStagesJson = "{}";

    @SuppressWarnings("unchecked") Map<Integer,String> counsellors =
        (Map<Integer,String>) request.getAttribute("counsellors");

    String fq          = (String) request.getAttribute("fq");
    String fpriority   = (String) request.getAttribute("fpriority");
    String fstage      = (String) request.getAttribute("fstage");
    String fstatus     = (String) request.getAttribute("fstatus");
    String fcounsellor = (String) request.getAttribute("fcounsellor");

    // The active filters as a query fragment, so the view tabs carry them
    // instead of silently resetting the queue every time you switch window.
    StringBuilder qsb = new StringBuilder();
    String[][] active = { {"q", fq}, {"priority", fpriority}, {"stage", fstage},
                          {"status", fstatus}, {"counsellor", fcounsellor} };
    for (String[] p : active) {
        if (p[1] != null && !p[1].isEmpty()) {
            qsb.append('&').append(p[0]).append('=').append(URLEncoder.encode(p[1], "UTF-8"));
        }
    }
    String fqs = qsb.toString();
    boolean filtered = fqs.length() > 0;

    // The same filters as hidden fields, so logging a call from a filtered
    // queue comes back to that same filtered queue.
    StringBuilder hb = new StringBuilder();
    for (String[] p : active) {
        if (p[1] != null && !p[1].isEmpty()) {
            hb.append("<input type=\"hidden\" name=\"").append(p[0])
              .append("\" value=\"").append(esc(p[1])).append("\">");
        }
    }
    String filterHidden = hb.toString();
%>
<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String d(String s) { return (s == null || s.isEmpty()) ? "—" : esc(s); }
    private String v(String s) { return s == null ? "" : esc(s); }
    private String sel(String current, String option) {
        return option.equals(current == null ? "" : current) ? " selected" : "";
    }
    private String badgeClass(String status) {
        if (status == null) return "st-new";
        switch (status) {
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
    private String tabTitle(String v) {
        switch (v) {
            case "overdue": return "Overdue follow-ups";
            case "week":    return "Due in the next 7 days";
            case "all":     return "All scheduled follow-ups";
            default:        return "Follow-ups due today";
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Follow-ups – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }

    .filters { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:16px 18px; margin-bottom:16px; }
    .filters .row { display:grid; grid-template-columns:repeat(5,1fr); gap:10px 12px; }
    @media(max-width:1000px){ .filters .row { grid-template-columns:repeat(3,1fr); } }
    @media(max-width:640px){ .filters .row { grid-template-columns:repeat(2,1fr); } }
    .filters .f { display:flex; flex-direction:column; }
    .filters .f.grow { grid-column:span 2; }
    @media(max-width:640px){ .filters .f.grow { grid-column:span 2; } }
    .filters label { font-size:10.5px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--muted); margin-bottom:4px; }
    .filters input, .filters select { padding:8px 10px; border:1.5px solid var(--border);
      border-radius:7px; font-size:13px; font-family:inherit; min-width:0; }
    .filters .acts { display:flex; gap:9px; align-items:center; margin-top:12px; flex-wrap:wrap; }
    .link-clear { font-size:12.5px; color:var(--muted); text-decoration:none; }
    .link-clear:hover { color:var(--green-dark); }
    .filter-note { font-size:12.5px; color:#9C4A16; background:#FFF3EA; border:1px solid #F0D3AE;
      border-radius:7px; padding:7px 11px; margin-left:auto; }

    .tiles { display:grid; grid-template-columns:repeat(3,1fr); gap:14px; margin-bottom:18px; }
    @media(max-width:640px){ .tiles { grid-template-columns:1fr; } }
    .tile { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:16px 18px; text-decoration:none; display:block; border-left:5px solid var(--green); }
    .tile.red { border-left-color:#C0392B; }
    .tile.blue { border-left-color:#1B4F9C; }
    .tile.on { outline:2px solid var(--green); }
    .tile .n { font-size:27px; font-weight:800; color:var(--green-dark); line-height:1.1; }
    .tile.red .n { color:#C0392B; }
    .tile .l { font-size:12px; color:var(--muted); text-transform:uppercase; letter-spacing:0.4px;
      font-weight:700; margin-top:4px; }

    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:920px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:11px 13px; font-size:11px;
      font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:10px 13px; border-bottom:1px solid var(--border); vertical-align:middle; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    table.lst tr.late td { background:#FFF5F4; }

    /* Eight columns do not fit a laptop screen, so the queue scrolls sideways.
       Pin the Action cell to the right edge — "Log call" is the whole point of
       this page and must never be the thing you have to scroll to find. The
       backgrounds are repeated per row state because a sticky cell slides over
       its neighbours and would otherwise be transparent. */
    table.lst thead th:last-child,
    table.lst tbody tr:not(.logrow) td:last-child { position:sticky; right:0; }
    table.lst thead th:last-child { background:var(--green); z-index:3; }
    table.lst tbody tr:not(.logrow) td:last-child { background:var(--white); z-index:2;
      box-shadow:-7px 0 7px -7px rgba(0,0,0,0.22); }
    table.lst tbody tr:nth-child(even):not(.logrow) td:last-child { background:var(--green-pale); }
    table.lst tbody tr.late:not(.logrow) td:last-child { background:#FFF5F4; }

    /* Due date over due time, so showing the time costs no column width. */
    .due-tm { font-size:11px; font-weight:600; color:var(--muted); margin-top:2px; }
    .due-late .due-tm { color:#C0392B; }
    .nm { font-weight:700; color:var(--green-dark); text-decoration:none; }
    .nm:hover { text-decoration:underline; }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 9px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; }
    .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-interested { background:#E4DDFF; color:#4B2E9C; }
    .st-demo { background:#D9F2F7; color:#0F6C7E; }
    .st-followup { background:#FFE6D6; color:#9C4A16; }
    .pr-hot { background:#FDE2E0; color:#C0392B; }
    .pr-warm { background:#FFF4D6; color:#9A6B00; }
    .pr-cold { background:#E6F0FF; color:#1B4F9C; }
    .due-late { color:#C0392B; font-weight:700; }
    .btn { display:inline-block; text-decoration:none; font-size:12px; font-weight:700; padding:6px 12px;
      border-radius:6px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); color:var(--muted); }

    /* Inline logging: the queue is a work list, so a call can be recorded
       without leaving the page. */
    .logrow td { background:#F7FCF9 !important; padding:14px 16px; }
    /* Seven fields, since Lead Stage / Sub Stage joined the row and Next Action
       now carries a time. Auto-fit rather than fixed tracks: fixed ones added up
       to more than the table's width, so the form itself stretched the table and
       shoved the Action column off-screen. This wraps to whatever room there is,
       with 180px kept as the floor so the date-and-time picker stays readable. */
    .logform { display:grid; grid-template-columns:repeat(auto-fit, minmax(180px, 1fr));
      gap:10px; align-items:end; }
    /* the free-text field earns the extra room when there is any */
    .logform .wide { grid-column:span 2; }
    .logform .fld { display:flex; flex-direction:column; min-width:0; }
    .logform label { font-size:10.5px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--muted); margin-bottom:4px; }
    .logform input, .logform select, .logform textarea { padding:8px 10px; border:1.5px solid var(--border);
      border-radius:7px; font-size:13px; font-family:inherit; min-width:0; }
    .logform textarea { resize:vertical; min-height:38px; }
    details.q > summary { cursor:pointer; list-style:none; }
    details.q > summary::-webkit-details-marker { display:none; }

    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid #BFE3CE; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="followups"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2><%= tabTitle(view) %></h2>
    <span style="font-size:12.5px;color:var(--muted);"><%= total %> lead<%= total == 1 ? "" : "s" %></span>
  </div>

  <% if (msg != null) { %>
    <div class="alert ok"><%= "logged".equals(msg) ? "Follow-up recorded." : "Done." %></div>
  <% } %>
  <% if (error != null) { %>
    <div class="alert error"><%= esc(error) %></div>
  <% } %>

  <%-- ── narrow the queue ──
       The tabs above choose WHEN something is due; these choose WHICH leads
       are worth looking at inside that window. --%>
  <form class="filters" method="get" action="<%= ctx %>/followup">
    <input type="hidden" name="view" value="<%= esc(view) %>">
    <div class="row">
      <div class="f grow">
        <label>Search</label>
        <input type="text" name="q" value="<%= v(fq) %>" placeholder="Name, mobile, email">
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
        <label>Lead Stage</label>
        <select name="stage">
          <option value=""<%= sel(fstage,"") %>>All</option>
          <% if (stages != null) for (String s : stages) { %>
            <option value="<%= esc(s) %>"<%= sel(fstage, s) %>><%= esc(s) %></option>
          <% } %>
        </select>
      </div>
      <div class="f">
        <label>Status</label>
        <select name="status">
          <option value=""<%= sel(fstatus,"") %>>All</option>
          <option value="NEW"<%= sel(fstatus,"NEW") %>>New</option>
          <option value="CONTACTED"<%= sel(fstatus,"CONTACTED") %>>Contacted</option>
          <option value="INTERESTED"<%= sel(fstatus,"INTERESTED") %>>Interested</option>
          <option value="DEMO_PENDING"<%= sel(fstatus,"DEMO_PENDING") %>>Demo Pending</option>
          <option value="DEMO_COMPLETED"<%= sel(fstatus,"DEMO_COMPLETED") %>>Demo Completed</option>
          <option value="FOLLOWUP_REQUIRED"<%= sel(fstatus,"FOLLOWUP_REQUIRED") %>>Follow-up Required</option>
        </select>
      </div>
      <%-- Shown when there is a choice: management sees every counsellor, an
           ABM their team; a lone counsellor's list is just themself. --%>
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
      <button type="submit" class="btn btn-primary">Apply</button>
      <% if (filtered) { %>
        <a class="link-clear" href="<%= ctx %>/followup?view=<%= esc(view) %>">Clear filters</a>
        <span class="filter-note">Counts below are filtered too</span>
      <% } %>
    </div>
  </form>

  <div class="tiles">
    <a class="tile red <%= "overdue".equals(view) ? "on" : "" %>" href="<%= ctx %>/followup?view=overdue<%= fqs %>">
      <div class="n"><%= cOverdue %></div><div class="l">Overdue</div>
    </a>
    <a class="tile <%= "today".equals(view) ? "on" : "" %>" href="<%= ctx %>/followup?view=today<%= fqs %>">
      <div class="n"><%= cToday %></div><div class="l">Due Today</div>
    </a>
    <a class="tile blue <%= "week".equals(view) ? "on" : "" %>" href="<%= ctx %>/followup?view=week<%= fqs %>">
      <div class="n"><%= cWeek %></div><div class="l">Next 7 Days</div>
    </a>
  </div>

  <p style="margin-bottom:14px;font-size:12.5px;">
    <a href="<%= ctx %>/followup?view=all<%= fqs %>" style="color:var(--muted);">Show every scheduled follow-up →</a>
  </p>

  <% if (leads == null || leads.isEmpty()) { %>
    <div class="table-wrap">
      <div class="empty">
        <% if (filtered) { %>
          <div class="ic">🔍</div>
          <p>No lead in this window matches those filters.
             <a href="<%= ctx %>/followup?view=<%= esc(view) %>">Clear them</a> to see the whole queue.</p>
        <% } else { %>
          <div class="ic">✅</div>
          <p>Nothing due here. <%= "overdue".equals(view) ? "No follow-up has been missed." : "You are all caught up." %></p>
        <% } %>
      </div>
    </div>
  <% } else { %>
    <div class="table-wrap">
      <table class="lst">
        <thead>
          <tr>
            <th>Due</th><th>Name</th><th>Mobile</th><th>Course Interest</th>
            <th>Priority</th><th>Status</th><th>Counsellor</th><th>Action</th>
          </tr>
        </thead>
        <tbody>
        <% for (Inquiry q : leads) {
             boolean late = q.isOverdue(today);
             String course = (q.getCourseName() != null && !q.getCourseName().isEmpty())
                             ? q.getCourseName() : q.getClassInterest();
             String dueTime = Dates.timeOf(q.getNextFollowupDate()); %>
          <tr<%= late ? " class=\"late\"" : "" %>>
            <td<%= late ? " class=\"due-late\"" : "" %>><%= d(Dates.dayOf(q.getNextFollowupDate())) %><%
                 if (!dueTime.isEmpty()) { %><div class="due-tm"><%= esc(dueTime) %></div><% } %></td>
            <td><a class="nm" href="<%= ctx %>/lead?id=<%= q.getInquiryId() %>"><%= esc(q.getFullName()) %></a></td>
            <td><%= d(q.getMobile()) %></td>
            <td><%= d(course) %></td>
            <td><span class="badge <%= prioClass(q.getPriority()) %>"><%= d(q.getPriority()) %></span></td>
            <td><span class="badge <%= badgeClass(q.getStatus()) %>"><%= d(q.getStatus()) %></span></td>
            <td><%= d(q.getCounsellorName()) %></td>
            <td>
              <details class="q">
                <summary><span class="btn btn-primary">Log call</span></summary>
              </details>
            </td>
          </tr>
          <tr class="logrow" id="log<%= q.getInquiryId() %>" style="display:none;">
            <td colspan="8">
              <form method="post" action="<%= ctx %>/followup" class="logform">
                <input type="hidden" name="inquiryId" value="<%= q.getInquiryId() %>">
                <input type="hidden" name="back" value="queue">
                <input type="hidden" name="view" value="<%= esc(view) %>"><%= filterHidden %>
                <div class="fld">
                  <label>Type</label>
                  <select name="commType">
                    <option>Call</option><option>WhatsApp</option><option>Email</option>
                    <option>Visit</option><option>Demo Discussion</option>
                  </select>
                </div>
                <div class="fld wide">
                  <label>Discussion Summary</label>
                  <textarea name="discussion" required placeholder="What was discussed?"></textarea>
                </div>
                <div class="fld">
                  <label>Move Lead To</label>
                  <select name="outcomeStatus">
                    <option value="">— leave unchanged —</option>
                    <option value="CONTACTED">Contacted</option>
                    <option value="INTERESTED">Interested</option>
                    <option value="DEMO_PENDING">Demo Pending</option>
                    <option value="FOLLOWUP_REQUIRED">Follow-up Required</option>
                    <option value="NOT_INTERESTED">Not Interested</option>
                    <option value="LOST">Lost</option>
                  </select>
                </div>
                <div class="fld">
                  <label>Lead Stage</label>
                  <select name="leadStage" class="lead-stage" data-sub="sub<%= q.getInquiryId() %>">
                    <option value="">— leave unchanged —</option>
                    <% if (stages != null) for (String s : stages) { %>
                      <option<%= s.equals(q.getLeadStage()) ? " selected" : "" %>><%= esc(s) %></option>
                    <% } %>
                  </select>
                </div>
                <div class="fld">
                  <label>Lead Sub Stage</label>
                  <select name="leadSubStage" id="sub<%= q.getInquiryId() %>"
                          data-selected="<%= esc(q.getLeadSubStage() == null ? "" : q.getLeadSubStage()) %>">
                    <option value="">— choose a stage first —</option>
                  </select>
                </div>
                <div class="fld">
                  <label>Next Action</label>
                  <input type="datetime-local" name="nextActionDate"
                         value="<%= Dates.forInput(q.getNextFollowupDate()) %>">
                </div>
                <div class="fld">
                  <button type="submit" class="btn btn-primary" style="padding:9px 16px;">Save</button>
                </div>
              </form>
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

<script>window.LEAD_SUB_STAGES = <%= subStagesJson %>;</script>
<script src="<%= ctx %>/js/leadstage.js"></script>

<script>
  // "Log call" reveals the inline form row beneath the lead. Kept as a plain
  // toggle so the form still submits normally with JS-less fallbacks intact.
  document.querySelectorAll('details.q').forEach(function (dt) {
    dt.addEventListener('toggle', function () {
      var row = dt.closest('tr').nextElementSibling;
      if (row && row.classList.contains('logrow')) {
        row.style.display = dt.open ? 'table-row' : 'none';
        if (dt.open) {
          var ta = row.querySelector('textarea');
          if (ta) ta.focus();
        }
      }
    });
  });
</script>
</body>
</html>
