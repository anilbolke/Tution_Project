<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*, com.tution.model.User, com.tution.model.Role, com.tution.dao.AccessDAO" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    String[] roles = (String[]) request.getAttribute("roles");
    if (roles == null) roles = new String[0];
    @SuppressWarnings("unchecked") List<AccessDAO.Activity> acts =
        (List<AccessDAO.Activity>) request.getAttribute("activities");
    if (acts == null) acts = new ArrayList<AccessDAO.Activity>();
    @SuppressWarnings("unchecked") Map<String, Set<String>> grants =
        (Map<String, Set<String>>) request.getAttribute("grants");
    if (grants == null) grants = new HashMap<String, Set<String>>();
    @SuppressWarnings("unchecked") List<String[]> log = (List<String[]>) request.getAttribute("log");
    if (log == null) log = new ArrayList<String[]>();

    String flash      = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Role Mapping – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .rm-wrap { max-width:1320px; margin:18px auto; padding:0 14px 90px; }
  .rm-head h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .rm-head .sub { margin:3px 0 14px; font-size:12.5px; color:#7b8b93; max-width:92ch; }

  .grid-wrap { overflow:auto; max-height:calc(100vh - 230px); border:1px solid #dbe7e0;
               border-radius:10px; background:#fff; }
  table.rm { border-collapse:separate; border-spacing:0; font-size:13px; }
  table.rm th, table.rm td { border-bottom:1px solid #eceff1; }
  table.rm thead th { position:sticky; top:0; z-index:2; background:#f4f8f6; vertical-align:bottom;
      padding:8px 4px; font-size:10.5px; font-weight:800; letter-spacing:.3px; color:#3f5b4d;
      text-transform:uppercase; border-bottom:1px solid #c9e4d6; }
  table.rm thead th.role { width:64px; min-width:64px; }
  table.rm thead th.role span { writing-mode:vertical-rl; transform:rotate(180deg);
      white-space:nowrap; display:inline-block; max-height:140px; }
  table.rm thead th.role button { display:block; margin:6px auto 0; font-size:10px; }
  /* the activity column stays put while the roles scroll sideways */
  table.rm .act { position:sticky; left:0; z-index:1; background:#fff; text-align:left;
      padding:7px 12px; min-width:190px; white-space:nowrap; border-right:1px solid #dbe7e0; }
  table.rm thead th.act { z-index:3; background:#f4f8f6; }
  table.rm tr.tab td { background:#eaf6ef; color:#0b4a33; font-weight:800; font-size:11px;
      letter-spacing:.5px; text-transform:uppercase; padding:7px 12px; }
  table.rm tr.tab td.act { background:#eaf6ef; }
  table.rm td.c { text-align:center; padding:0; }
  table.rm td.c label { display:block; padding:7px 0; cursor:pointer; }
  table.rm td.c input { width:17px; height:17px; cursor:pointer; accent-color:#0E5C3F; }
  table.rm td.c.dirty { background:#fff4d6; }
  table.rm tbody tr:hover td { background:#f7fbf9; }
  table.rm tbody tr:hover td.dirty { background:#ffeebb; }
  table.rm tbody tr:hover td.act { background:#f7fbf9; }
  .rowbtn { float:right; margin-left:10px; }
  .lnk { background:none; border:0; padding:0; font:inherit; font-size:10.5px; font-weight:700;
         color:#0E5C3F; cursor:pointer; text-transform:none; letter-spacing:0; }
  .lnk:hover { text-decoration:underline; }

  .savebar { position:fixed; left:0; right:0; bottom:0; z-index:20; background:#fff;
      border-top:1px solid #dbe7e0; box-shadow:0 -4px 16px rgba(0,0,0,.06); }
  .savebar .in { max-width:1320px; margin:0 auto; padding:10px 14px; display:flex; gap:12px;
      align-items:center; flex-wrap:wrap; }
  .savebar .state { font-size:13px; color:#5a6b73; margin-right:auto; }
  .savebar .state b { color:#8a5e00; }
  .btn { background:#0E5C3F; color:#fff; border:0; padding:9px 18px; border-radius:6px;
         font-size:13px; font-weight:600; cursor:pointer; font-family:inherit; }
  .btn:hover { background:#0b4a33; }
  .btn:disabled { background:#9fb4a8; cursor:default; }
  .btn.alt { background:#fff; color:#42555e; border:1px solid #cfd6da; }

  .card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:14px 16px; margin-top:16px; }
  .card h2 { margin:0 0 8px; font-size:15px; }
  .log { font-size:12.5px; color:#42555e; }
  .log div { padding:7px 0; border-top:1px solid #eceff1; }
  .log div:first-child { border-top:0; }
  .log .w { color:#7b8b93; font-size:11.5px; }
  .log pre { margin:3px 0 0; white-space:pre-wrap; font-family:inherit; }
  @media (max-width:768px) { .savebar { bottom:58px; } .grid-wrap { max-height:none; } }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="rolemapping"/>
</jsp:include>

<div class="rm-wrap">
  <div class="rm-head">
    <h1>Role mapping</h1>
    <p class="sub">Which screens each role can open. Seeded from <b>Work Flow.xlsx</b>; a tick here is
      a YES there. Admin is not listed &mdash; it can open everything. A change applies to everybody
      on their next click; nobody needs to sign in again.</p>
  </div>

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (acts.isEmpty()) { %>
    <div class="msg err">The mapping tables are empty or could not be read. Run
      <code>database/migrations/2026-09-role-activity-matrix.sql</code>. Until then only Admin can open staff pages.</div>
  <% } %>

  <form method="post" action="<%= ctx %>/role-mapping" id="rmForm">
    <div class="grid-wrap">
      <table class="rm">
        <thead>
          <tr>
            <th class="act">Activity</th>
            <% for (String r : roles) { %>
              <th class="role" title="<%= esc(Role.labelOf(r)) %>"><span><%= esc(Role.labelOf(r)) %></span>
                <button type="button" class="lnk" data-col="<%= r %>">all/none</button></th>
            <% } %>
          </tr>
        </thead>
        <tbody>
          <% String lastTab = null;
             for (AccessDAO.Activity a : acts) {
               if (!a.tab.equals(lastTab)) { lastTab = a.tab; %>
            <tr class="tab"><td class="act"><%= esc(a.tab) %></td><td colspan="<%= roles.length %>"></td></tr>
          <%   } %>
            <tr>
              <td class="act"><%= esc(a.label) %>
                <button type="button" class="lnk rowbtn" data-row="<%= a.code %>">all/none</button></td>
              <% for (String r : roles) {
                   boolean on = grants.containsKey(r) && grants.get(r).contains(a.code); %>
                <td class="c"><label><input type="checkbox" name="<%= r %>" value="<%= a.code %>"
                    data-r="<%= r %>" data-a="<%= a.code %>" data-was="<%= on ? 1 : 0 %>"
                    aria-label="<%= esc(Role.labelOf(r)) %> – <%= esc(a.tab) %> – <%= esc(a.label) %>"
                    <%= on ? "checked" : "" %>></label></td>
              <% } %>
            </tr>
          <% } %>
        </tbody>
      </table>
    </div>

    <div class="savebar"><div class="in">
      <span class="state" id="state">No unsaved changes.</span>
      <button type="button" class="btn alt" id="undo" disabled>Undo changes</button>
      <button type="submit" class="btn" id="save" disabled>Save mapping</button>
    </div></div>
  </form>

  <div class="card">
    <h2>Recent changes</h2>
    <div class="log">
      <% if (log.isEmpty()) { %><div class="w">None yet.</div><% } %>
      <% for (String[] l : log) { %>
        <div><span class="w"><%= esc(l[0]) %> &middot; <%= esc(l[1] == null ? "—" : l[1]) %></span>
          <pre><%= esc(l[2]) %></pre></div>
      <% } %>
    </div>
  </div>
</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="rolemapping"/></jsp:include>

<script>
(function () {
  var boxes = Array.prototype.slice.call(document.querySelectorAll('table.rm input[type=checkbox]'));
  var state = document.getElementById('state'), save = document.getElementById('save'),
      undo = document.getElementById('undo'), submitting = false;

  function refresh() {
    var n = 0;
    boxes.forEach(function (b) {
      var dirty = (b.checked ? '1' : '0') !== b.getAttribute('data-was');
      b.closest('td').classList.toggle('dirty', dirty);
      if (dirty) n++;
    });
    state.innerHTML = n ? '<b>' + n + ' unsaved change' + (n === 1 ? '' : 's') + '</b> — highlighted in yellow.'
                        : 'No unsaved changes.';
    save.disabled = undo.disabled = !n;
  }
  // all/none: if every box in the line is ticked, clear them; otherwise tick them all
  function toggle(list) {
    var all = list.every(function (b) { return b.checked; });
    list.forEach(function (b) { b.checked = !all; });
    refresh();
  }
  document.querySelectorAll('[data-col]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var r = btn.getAttribute('data-col');
      toggle(boxes.filter(function (b) { return b.getAttribute('data-r') === r; }));
    });
  });
  document.querySelectorAll('[data-row]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var a = btn.getAttribute('data-row');
      toggle(boxes.filter(function (b) { return b.getAttribute('data-a') === a; }));
    });
  });
  boxes.forEach(function (b) { b.addEventListener('change', refresh); });
  undo.addEventListener('click', function () {
    boxes.forEach(function (b) { b.checked = b.getAttribute('data-was') === '1'; });
    refresh();
  });
  document.getElementById('rmForm').addEventListener('submit', function () { submitting = true; });
  window.addEventListener('beforeunload', function (e) {
    if (!submitting && !save.disabled) { e.preventDefault(); e.returnValue = ''; }
  });
  refresh();
})();
</script>
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
