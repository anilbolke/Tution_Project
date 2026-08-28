<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.CounsellorTarget, com.tution.model.CounsellorCourseTarget, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<CounsellorTarget> rows =
        (List<CounsellorTarget>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<CounsellorTarget>();
    @SuppressWarnings("unchecked") Map<Integer,int[]> prev =
        (Map<Integer,int[]>) request.getAttribute("prevTargets");
    if (prev == null) prev = new HashMap<Integer,int[]>();

    @SuppressWarnings("unchecked") Map<Integer,String> counsellorOptions =
        (Map<Integer,String>) request.getAttribute("counsellorOptions");
    if (counsellorOptions == null) counsellorOptions = new LinkedHashMap<Integer,String>();
    Integer selCounsellorId = (Integer) request.getAttribute("selCounsellorId");
    @SuppressWarnings("unchecked") List<CounsellorCourseTarget> ccRows =
        (List<CounsellorCourseTarget>) request.getAttribute("ccRows");
    if (ccRows == null) ccRows = new ArrayList<CounsellorCourseTarget>();
    @SuppressWarnings("unchecked") Map<Integer,int[]> prevCc =
        (Map<Integer,int[]>) request.getAttribute("prevCcTargets");
    if (prevCc == null) prevCc = new HashMap<Integer,int[]>();
    long[] ccTotals = (long[]) request.getAttribute("ccTotals");
    if (ccTotals == null) ccTotals = new long[4];

    String periodStart = (String) request.getAttribute("periodStart");
    String periodEnd   = (String) request.getAttribute("periodEnd");
    String periodLabel = (String) request.getAttribute("periodLabel");
    String prevQ = (String) request.getAttribute("prevQ");
    String nextQ = (String) request.getAttribute("nextQ");
    long[] totals = (long[]) request.getAttribute("totals");
    if (totals == null) totals = new long[4];

    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Counsellor Targets – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .tg-wrap { max-width:1180px; margin:18px auto; padding:0 14px; }
  .tg-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .tg-card h2 { margin:0 0 3px; font-size:16px; }
  .tg-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }
  .qnav { display:flex; gap:10px; align-items:center; flex-wrap:wrap; margin-bottom:12px; }
  .qnav .lbl { font-size:17px; font-weight:700; color:#0E5C3F; }
  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:8px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  table.t input { padding:6px 8px; border:1px solid #cfd6da; border-radius:5px; font-size:13px; }
  .num { text-align:right; font-variant-numeric:tabular-nums; }
  .bar { position:relative; height:16px; background:#eef1f2; border-radius:9px; overflow:hidden; min-width:110px; }
  .bar i { position:absolute; left:0; top:0; bottom:0; background:#2f9e5f; }
  .bar .exp { position:absolute; top:-2px; bottom:-2px; width:2px; background:#42555e; }
  .st { font-size:11px; font-weight:700; padding:2px 8px; border-radius:20px; white-space:nowrap; }
  .s-ahead { background:#d8efdf; color:#1b6b39; }
  .s-track { background:#e4eef7; color:#1d4e79; }
  .s-behind{ background:#f7d4d4; color:#8c2020; }
  .s-none  { background:#f0f2f3; color:#69777e; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }
  .muted { color:#8697a0; font-size:12px; }
  .scroll { overflow-x:auto; }
  tr.totals td { background:#f6f8f9; font-weight:700; border-top:2px solid #dfe4e6; }

  .cc-picker {
    display:flex; align-items:center; gap:12px; flex-wrap:wrap;
    background:var(--green-pale); border:1px solid var(--border); border-radius:9px;
    padding:12px 16px; margin-bottom:6px;
  }
  .cc-picker label {
    font-size:11.5px; font-weight:700; text-transform:uppercase; letter-spacing:.4px;
    color:var(--green-dark);
  }
  .cc-picker select {
    appearance:none; -webkit-appearance:none; -moz-appearance:none;
    padding:10px 38px 10px 14px; font-size:14px; font-family:'Inter',sans-serif;
    border:1.5px solid var(--border); border-radius:8px; background-color:#fff;
    background-image:url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'><path d='M1 1l5 5 5-5' stroke='%23135C37' stroke-width='1.6' fill='none' stroke-linecap='round' stroke-linejoin='round'/></svg>");
    background-repeat:no-repeat; background-position:right 14px center;
    min-width:260px; max-width:100%; color:var(--text); cursor:pointer; outline:none;
    transition:border-color .15s, box-shadow .15s;
  }
  .cc-picker select:hover   { border-color:var(--green-mid); }
  .cc-picker select:focus   { border-color:var(--green); box-shadow:0 0 0 3px rgba(26,122,74,0.15); }
  .cc-picker .picked-tag {
    font-size:12px; font-weight:700; color:var(--green-dark); background:var(--green-light);
    border-radius:20px; padding:5px 12px; white-space:nowrap;
  }
  @media (max-width:520px) {
    .cc-picker select { min-width:0; width:100%; }
  }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="targets"/>
</jsp:include>

<div class="tg-wrap">
  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>
  <% if (flash != null)      { %><div class="alert a-ok"><%= esc(flash) %></div><% } %>

  <div class="tg-card">
    <h2>Counsellor targets</h2>
    <p class="hint">
      Quarterly admission and revenue targets. Revenue is <b>booked</b> &mdash; the value of
      admissions closed in the quarter, not cash received &mdash; because a counsellor controls
      whether the admission closes, not the instalment plan behind it. Cash collected is shown
      alongside for reference.
    </p>
    <div class="qnav">
      <a class="btn btn-light" href="<%= ctx %>/targets?q=<%= prevQ %>">&larr; Previous</a>
      <span class="lbl"><%= esc(periodLabel) %></span>
      <a class="btn btn-light" href="<%= ctx %>/targets?q=<%= nextQ %>">Next &rarr;</a>
      <span class="muted"><%= esc(periodStart) %> to <%= esc(periodEnd) %></span>
      <% if (!prev.isEmpty()) { %>
        <button type="button" class="btn btn-light" onclick="copyPrev()">Copy from last quarter</button>
      <% } %>
    </div>

    <form method="post" action="<%= ctx %>/targets">
      <input type="hidden" name="periodStart" value="<%= esc(periodStart) %>">
      <div class="scroll">
        <table class="t">
          <tr>
            <th>Counsellor</th>
            <th style="width:96px;">Adm. target</th>
            <th style="width:130px;">Revenue target</th>
            <th style="width:170px;">Admissions</th>
            <th style="width:200px;">Revenue (booked)</th>
            <th class="num" style="width:110px;">Collected</th>
            <th style="width:150px;">Note</th>
          </tr>
          <% for (CounsellorTarget t : rows) {
               int[] p = prev.get(Integer.valueOf(t.getCounsellorId()));
          %>
          <tr>
            <td>
              <b><%= esc(t.getCounsellorName()) %></b>
              <% if ("ADMIN".equals(t.getRole())) { %> <span class="muted">(admin)</span><% } %>
              <input type="hidden" name="counsellorId" value="<%= t.getCounsellorId() %>">
              <% if (t.getSetBy() != null) { %><br><span class="muted">set by <%= esc(t.getSetBy()) %></span><% } %>
            </td>
            <td><input type="number" min="0" style="width:78px;" name="adm_<%= t.getCounsellorId() %>"
                       id="adm_<%= t.getCounsellorId() %>"
                       data-prev="<%= p == null ? 0 : p[0] %>"
                       value="<%= t.isUnset() && t.getAdmissionsTarget()==0 ? "" : t.getAdmissionsTarget() %>"></td>
            <td><input type="number" min="0" step="1000" style="width:112px;" name="rev_<%= t.getCounsellorId() %>"
                       id="rev_<%= t.getCounsellorId() %>"
                       data-prev="<%= p == null ? 0 : p[1] %>"
                       value="<%= t.isUnset() && t.getRevenueTarget()==0 ? "" : t.getRevenueTarget() %>"></td>
            <td>
              <%= t.getAdmissionsActual() %> / <%= t.getAdmissionsTarget() %>
              <%= bar(t.admissionsPct(), t.expectedPct()) %>
              <span class="st <%= stClass(t.admissionsStatus()) %>"><%= t.admissionsStatus() %></span>
            </td>
            <td>
              Rs. <%= money(t.getRevenueActual()) %> / <%= money(t.getRevenueTarget()) %>
              <%= bar(t.revenuePct(), t.expectedPct()) %>
              <span class="st <%= stClass(t.revenueStatus()) %>"><%= t.revenueStatus() %></span>
            </td>
            <td class="num muted">Rs. <%= money(t.getCollectedActual()) %></td>
            <td><input type="text" style="width:140px;" name="note_<%= t.getCounsellorId() %>"
                       value="<%= t.getNotes() == null ? "" : esc(t.getNotes()) %>"></td>
          </tr>
          <% } %>
          <tr class="totals">
            <td>Team</td>
            <td class="num"><%= totals[0] %></td>
            <td class="num">Rs. <%= money(totals[2]) %></td>
            <td><%= totals[1] %> / <%= totals[0] %></td>
            <td>Rs. <%= money(totals[3]) %> / <%= money(totals[2]) %></td>
            <td></td><td></td>
          </tr>
        </table>
      </div>
      <div style="margin-top:12px;">
        <button class="btn" type="submit">Save targets</button>
        <span class="muted" style="margin-left:8px;">
          Leave a row blank to record no target &mdash; that reads differently from a target of zero.
        </span>
      </div>
    </form>
  </div>

  <div class="tg-card">
    <h2>Counsellor targets by course</h2>
    <p class="hint">
      Break a counsellor's target down by course &mdash; how much of their number should
      come from each programme. Only counsellors with an overall target set above appear
      here; set that first, then open this section for them.
    </p>
    <% if (counsellorOptions.isEmpty()) { %>
      <p class="muted">No counsellor has an overall target for <%= esc(periodLabel) %> yet.
        Set one in the table above first.</p>
    <% } else { %>
    <form method="get" action="<%= ctx %>/targets" class="cc-picker">
      <input type="hidden" name="q" value="<%= esc(periodStart) %>">
      <label for="counsellorPick">Counsellor</label>
      <select name="counsellorId" id="counsellorPick" onchange="this.form.submit()">
        <option value="">Select a counsellor…</option>
        <% for (Map.Entry<Integer,String> e : counsellorOptions.entrySet()) {
             boolean s = selCounsellorId != null && selCounsellorId.intValue() == e.getKey().intValue(); %>
          <option value="<%= e.getKey() %>"<%= s ? " selected" : "" %>><%= esc(e.getValue()) %></option>
        <% } %>
      </select>
      <% if (selCounsellorId != null) { %>
        <span class="picked-tag"><%= ccRows.size() %> course<%= ccRows.size() == 1 ? "" : "s" %> below</span>
      <% } %>
      <noscript><button type="submit" class="btn btn-light">Go</button></noscript>
    </form>

    <% if (selCounsellorId == null) { %>
      <p class="muted" style="margin-top:10px;">Select a counsellor above to set their course-wise targets.</p>
    <% } else { %>
      <div class="qnav" style="margin-top:2px;">
        <span class="lbl"><%= esc(periodLabel) %></span>
        <% if (!prevCc.isEmpty()) { %>
          <button type="button" class="btn btn-light" onclick="copyPrevCc()">Copy from last quarter</button>
        <% } %>
      </div>
      <form method="post" action="<%= ctx %>/targets">
        <input type="hidden" name="scope" value="counsellor-course">
        <input type="hidden" name="periodStart" value="<%= esc(periodStart) %>">
        <input type="hidden" name="counsellorId" value="<%= selCounsellorId %>">
        <div class="scroll">
          <table class="t">
            <tr>
              <th>Course</th>
              <th style="width:96px;">Adm. target</th>
              <th style="width:130px;">Revenue target</th>
              <th style="width:170px;">Admissions</th>
              <th style="width:200px;">Revenue (booked)</th>
              <th class="num" style="width:110px;">Collected</th>
              <th style="width:150px;">Note</th>
            </tr>
            <% for (CounsellorCourseTarget t : ccRows) {
                 int[] p = prevCc.get(Integer.valueOf(t.getCourseId()));
            %>
            <tr>
              <td>
                <b><%= esc(t.getCourseName()) %></b>
                <input type="hidden" name="courseId" value="<%= t.getCourseId() %>">
                <% if (t.getSetBy() != null) { %><br><span class="muted">set by <%= esc(t.getSetBy()) %></span><% } %>
              </td>
              <td><input type="number" min="0" style="width:78px;" name="admcc_<%= t.getCourseId() %>"
                         id="admcc_<%= t.getCourseId() %>"
                         data-prev="<%= p == null ? 0 : p[0] %>"
                         value="<%= t.isUnset() && t.getAdmissionsTarget()==0 ? "" : t.getAdmissionsTarget() %>"></td>
              <td><input type="number" min="0" step="1000" style="width:112px;" name="revcc_<%= t.getCourseId() %>"
                         id="revcc_<%= t.getCourseId() %>"
                         data-prev="<%= p == null ? 0 : p[1] %>"
                         value="<%= t.isUnset() && t.getRevenueTarget()==0 ? "" : t.getRevenueTarget() %>"></td>
              <td>
                <%= t.getAdmissionsActual() %> / <%= t.getAdmissionsTarget() %>
                <%= bar(t.admissionsPct(), t.expectedPct()) %>
                <span class="st <%= stClass(t.admissionsStatus()) %>"><%= t.admissionsStatus() %></span>
              </td>
              <td>
                Rs. <%= money(t.getRevenueActual()) %> / <%= money(t.getRevenueTarget()) %>
                <%= bar(t.revenuePct(), t.expectedPct()) %>
                <span class="st <%= stClass(t.revenueStatus()) %>"><%= t.revenueStatus() %></span>
              </td>
              <td class="num muted">Rs. <%= money(t.getCollectedActual()) %></td>
              <td><input type="text" style="width:140px;" name="notecc_<%= t.getCourseId() %>"
                         value="<%= t.getNotes() == null ? "" : esc(t.getNotes()) %>"></td>
            </tr>
            <% } %>
            <tr class="totals">
              <td>This counsellor, all courses</td>
              <td class="num"><%= ccTotals[0] %></td>
              <td class="num">Rs. <%= money(ccTotals[2]) %></td>
              <td><%= ccTotals[1] %> / <%= ccTotals[0] %></td>
              <td>Rs. <%= money(ccTotals[3]) %> / <%= money(ccTotals[2]) %></td>
              <td></td><td></td>
            </tr>
          </table>
        </div>
        <div style="margin-top:12px;">
          <button class="btn" type="submit">Save course targets</button>
          <span class="muted" style="margin-left:8px;">
            Leave a row blank to record no target &mdash; that reads differently from a target of zero.
          </span>
        </div>
      </form>
    <% } %>
    <% } %>
  </div>
</div>

<script>
  // Targets usually move by a little each quarter rather than being rebuilt, so
  // this pre-fills last quarter's numbers for editing. It only fills EMPTY
  // boxes — it never overwrites something already typed or already saved.
  function copyPrev() {
    var boxes = document.querySelectorAll('input[id^="adm_"][data-prev], input[id^="rev_"][data-prev]');
    var filled = 0;
    for (var i = 0; i < boxes.length; i++) {
      var v = boxes[i].getAttribute('data-prev');
      if (boxes[i].value === '' && v && v !== '0') { boxes[i].value = v; filled++; }
    }
    if (!filled) alert('Nothing to copy — last quarter has no targets, or every box is already filled.');
  }
  function copyPrevCc() {
    var boxes = document.querySelectorAll('input[id^="admcc_"][data-prev], input[id^="revcc_"][data-prev]');
    var filled = 0;
    for (var i = 0; i < boxes.length; i++) {
      var v = boxes[i].getAttribute('data-prev');
      if (boxes[i].value === '' && v && v !== '0') { boxes[i].value = v; filled++; }
    }
    if (!filled) alert('Nothing to copy — last quarter has no targets for this counsellor, or every box is already filled.');
  }
</script>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String money(long v) { return String.format("%,d", v); }
    private String stClass(String s) {
        if ("Ahead".equals(s))    return "s-ahead";
        if ("On track".equals(s)) return "s-track";
        if ("Behind".equals(s))   return "s-behind";
        return "s-none";
    }
    /** Progress bar with a marker showing where the quarter says you should be. */
    private String bar(double achieved, double expected) {
        double a = Math.max(0, Math.min(100, achieved));
        double e = Math.max(0, Math.min(100, expected));
        return "<div class=\"bar\" title=\"" + String.format("%.1f", achieved)
             + "% achieved, " + String.format("%.0f", expected) + "% of the quarter elapsed\">"
             + "<i style=\"width:" + a + "%\"></i>"
             + "<span class=\"exp\" style=\"left:" + e + "%\"></span></div>";
    }
%>
</body>
</html>
