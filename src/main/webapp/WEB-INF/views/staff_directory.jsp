<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*, com.tution.model.User, com.tution.model.UserAudit, com.tution.model.Role" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<User> staff = (List<User>) request.getAttribute("staff");
    if (staff == null) staff = new ArrayList<User>();
    @SuppressWarnings("unchecked") List<UserAudit> trail =
        (List<UserAudit>) request.getAttribute("trail");
    if (trail == null) trail = new ArrayList<UserAudit>();
    @SuppressWarnings("unchecked") Set<String> assignable =
        (Set<String>) request.getAttribute("assignable");
    if (assignable == null) assignable = new HashSet<String>();
    String[] allRoles = (String[]) request.getAttribute("allRoles");
    if (allRoles == null) allRoles = new String[0];
    Integer adminCount = (Integer) request.getAttribute("adminCount");
    // who can be reported to: any active login (an ABM, a branch manager, ...)
    List<User> managers = new ArrayList<User>();
    for (User m : staff) if (m.isActive()) managers.add(m);

    String error      = (String) request.getAttribute("error");
    String flash      = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");

    int active = 0;
    Map<String,Integer> byRole = new LinkedHashMap<String,Integer>();
    for (User u : staff) {
        if (u.isActive()) active++;
        String k = u.getRoleLabel();
        Integer n = byRole.get(k);
        byRole.put(k, n == null ? 1 : n + 1);
    }
    boolean limited = !user.isAdmin();
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Staff &amp; Logins – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .sd-wrap { max-width:1180px; margin:18px auto; padding:0 14px; }
  .sd-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px;
             flex-wrap:wrap; margin:0 0 16px; }
  .sd-head h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .sd-head .sub { margin:3px 0 0; font-size:12.5px; color:#7b8b93; }

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
  .card p.hint { color:#5a6b73; font-size:13px; margin:0 0 14px; max-width:84ch; }

  .fgrid { display:grid; grid-template-columns:repeat(6,1fr); gap:14px 15px; align-items:start; }
  @media(max-width:1080px){ .fgrid { grid-template-columns:repeat(4,1fr); } }
  @media(max-width:760px) { .fgrid { grid-template-columns:repeat(2,1fr); } }
  @media(max-width:460px) { .fgrid { grid-template-columns:1fr; } }
  .sp2 { grid-column:span 2; }
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
  .lnk { background:none; border:0; padding:0; font:inherit; font-size:11.5px; font-weight:600;
         color:#0E5C3F; cursor:pointer; }
  .lnk:hover { text-decoration:underline; }
  .lnk.warn { color:#8c2020; }

  .tally { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
  .tally span { font-size:12px; font-weight:700; padding:5px 12px; border-radius:20px;
                background:#eef3f0; color:#3f5b4d; }
  .tally span b { font-variant-numeric:tabular-nums; }

  .t-wrap { overflow-x:auto; border:1px solid #eceff1; border-radius:9px; }
  table.t { width:100%; border-collapse:collapse; font-size:13.5px; min-width:900px; }
  table.t th { background:#f4f8f6; text-align:left; padding:11px 12px; font-size:10.5px;
      font-weight:800; letter-spacing:.45px; text-transform:uppercase; color:#3f5b4d;
      border-bottom:1px solid #dbe7e0; white-space:nowrap; }
  table.t td { padding:11px 12px; border-bottom:1px solid #eceff1; vertical-align:top; }
  table.t tbody tr:last-child td { border-bottom:none; }
  table.t tbody tr:hover td { background:#f7fbf9; }
  table.t tr.off td { background:#fbfbfb; color:#9aa8a2; }
  .nm { font-weight:650; color:#1f3a2c; }
  .mono { font-family:ui-monospace,Consolas,monospace; font-size:12px; color:#6d8177; }
  .sub2 { font-size:11px; color:#8b9aa1; margin-top:3px; }
  .muted { color:#7b8b93; }

  .rl { font-size:10px; font-weight:800; letter-spacing:.4px; text-transform:uppercase;
        padding:3px 9px; border-radius:20px; white-space:nowrap; display:inline-block; }
  /* keyed by role FAMILY, so the new roles share their family's colour */
  .rl-ADMIN      { background:#d9ece1; color:#0b4a33; }
  .rl-ACCOUNTANT { background:#dbeee6; color:#12634a; }
  .rl-HR         { background:#f3e4ee; color:#8a3a68; }
  .rl-STAFF      { background:#dde9fb; color:#1B4F9C; }
  .rl-COUNSELLOR { background:#fbeecd; color:#8A5E00; }
  .rl-TEACHER    { background:#d7eef3; color:#0F6C7E; }

  .st { font-size:10px; font-weight:800; padding:2px 9px; border-radius:20px; letter-spacing:.4px;
        text-transform:uppercase; display:inline-block; }
  .st-on  { background:#e3f3e9; color:#1b6b39; }
  .st-off { background:#eceff1; color:#6b7f75; }
  .tag { font-size:10px; font-weight:800; padding:2px 9px; border-radius:20px; letter-spacing:.4px;
         text-transform:uppercase; display:inline-block; background:#e9eef0; color:#42555e; }
  .tag.priv { background:#f7e4d4; color:#8a4b12; }

  details.row > summary { list-style:none; cursor:pointer; font-size:11.5px; font-weight:600;
                          color:#0E5C3F; }
  details.row > summary::-webkit-details-marker { display:none; }
  details.row > div { margin-top:9px; display:flex; gap:7px; flex-wrap:wrap; align-items:center; }
  details.row input, details.row select { height:31px; box-sizing:border-box; padding:0 9px;
      border:1px solid #cfd6da; border-radius:6px; font-size:12px; font-family:inherit;
      min-width:0; }
  details.row input:focus, details.row select:focus { outline:none; border-color:#0E5C3F;
      box-shadow:0 0 0 3px rgba(14,92,63,.15); }
  .acts { display:flex; gap:10px; flex-wrap:wrap; align-items:center; }

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
  <jsp:param name="active" value="staff"/>
</jsp:include>

<div class="sd-wrap">

  <div class="sd-head">
    <div>
      <h1>Staff &amp; logins</h1>
      <p class="sub"><%= staff.size() %> account<%= staff.size()==1?"":"s" %> &middot;
         <%= active %> active<% if (adminCount != null) { %> &middot;
         <%= adminCount %> administrator<%= adminCount==1?"":"s" %><% } %></p>
    </div>
  </div>

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <% if (limited) { %>
    <div class="note-box">
      <b>You can manage staff, counsellor and teacher accounts.</b> Administrator, accountant
      and HR logins reach the institute's money or its permissions, so only an administrator
      can create or change one.
    </div>
  <% } %>

  <%-- ────────── add someone ────────── --%>
  <div class="card primary">
    <div class="p-head">
      <h2>Add someone</h2>
      <span class="lede">They can sign in as soon as you save</span>
    </div>
    <div class="p-body">
      <p class="hint">The username is what they type to sign in and cannot be changed afterwards.
         Set a starting password, tell it to them directly, and ask them to change it. Nobody
         &mdash; including you &mdash; can read a password back out of the system afterwards.</p>
      <form method="post" action="<%= ctx %>/staff" autocomplete="off">
        <input type="hidden" name="action" value="create">
        <div class="fgrid">
          <div class="fl sp2"><label for="cName">Full name</label>
            <input type="text" name="fullName" id="cName" maxlength="100" required
                   placeholder="e.g. Anita Deshmukh"></div>
          <div class="fl"><label for="cUser">Username</label>
            <input type="text" name="username" id="cUser" maxlength="50" required
                   pattern="[A-Za-z0-9._-]{3,50}" placeholder="anita"
                   autocomplete="off"></div>
          <div class="fl"><label for="cRole">Role</label>
            <select name="role" id="cRole" required>
              <% for (String r : allRoles) { if (!assignable.contains(r)) continue; %>
                <option value="<%= r %>"<%= "TEACHER".equals(r) ? " selected" : "" %>><%= esc(Role.labelOf(r)) %></option>
              <% } %>
            </select></div>
          <div class="fl"><label for="cPw">Starting password</label>
            <input type="text" name="password" id="cPw" minlength="6" required
                   placeholder="at least 6 characters" autocomplete="new-password"></div>
          <div class="fl"><label for="cMob">Mobile <span class="opt">(optional)</span></label>
            <input type="tel" name="mobile" id="cMob" maxlength="15" pattern="[0-9+ ]{7,15}"
                   placeholder="9876543210"></div>
          <div class="fl sp2"><label for="cMail">Email <span class="opt">(optional)</span></label>
            <input type="email" name="email" id="cMail" maxlength="120"
                   placeholder="anita@example.com"></div>
          <div class="fl sp2"><label for="cMgr">Reports to <span class="opt">(optional &mdash; a counsellor's ABM)</span></label>
            <select name="reportsTo" id="cMgr">
              <option value="">Nobody</option>
              <% for (User m : managers) { %>
                <option value="<%= m.getUserId() %>"><%= esc(m.getFullName()) %> &middot; <%= esc(m.getRoleLabel()) %></option>
              <% } %>
            </select></div>
        </div>
        <div class="fact">
          <span class="note">Recorded on the change history below, with your name against it.</span>
          <button class="btn" type="submit">Create login</button>
        </div>
      </form>
    </div>
  </div>

  <%-- ────────── the people ────────── --%>
  <div class="card">
    <h2>Everyone with a login</h2>
    <p class="hint">An account is switched off rather than deleted &mdash; their attendance, leave
       and payslips are kept, and a deleted account would take all three with it.</p>

    <div class="tally">
      <% for (Map.Entry<String,Integer> e : byRole.entrySet()) { %>
        <span><b><%= e.getValue() %></b> <%= esc(e.getKey()) %></span>
      <% } %>
    </div>

    <div class="t-wrap">
      <table class="t">
        <thead><tr>
          <th style="width:190px">Name</th>
          <th style="width:115px">Username</th>
          <th style="width:110px">Role</th>
          <th style="min-width:170px">Contact</th>
          <th style="width:95px">Status</th>
          <th style="width:120px">Last signed in</th>
          <th style="min-width:230px">Manage</th>
        </tr></thead>
        <tbody>
        <% for (User u : staff) {
             boolean mayTouch = assignable.contains(u.getRole());
             boolean isSelf   = u.getUserId() == user.getUserId();
             boolean lastAdmin = "ADMIN".equals(u.getRole())
                              && adminCount != null && adminCount <= 1;
        %>
          <tr<%= u.isActive() ? "" : " class=\"off\"" %>>
            <td class="nm"><%= esc(u.getFullName()) %>
              <% if (isSelf) { %><div class="sub2">this is you</div><% } %>
              <% if (u.getReportsToName() != null) { %><div class="sub2">reports to <%= esc(u.getReportsToName()) %></div><% } %></td>
            <td class="mono"><%= esc(u.getUsername()) %></td>
            <td><span class="rl rl-<%= esc(Role.familyOf(u.getRole())) %>"><%= esc(u.getRoleLabel()) %></span></td>
            <td>
              <%= (u.getMobile()==null||u.getMobile().isEmpty())
                  ? "<span class=\"muted\">&mdash;</span>" : esc(u.getMobile()) %>
              <% if (u.getEmail()!=null && !u.getEmail().isEmpty()) { %>
                <div class="sub2"><%= esc(u.getEmail()) %></div><% } %>
            </td>
            <td><span class="st <%= u.isActive() ? "st-on" : "st-off" %>"><%=
                u.isActive() ? "Active" : "Off" %></span></td>
            <td class="muted"><%= u.getLastLogin()==null
                ? "<span class=\"muted\">never</span>" : esc(u.getLastLogin()) %></td>
            <td>
              <% if (!mayTouch) { %>
                <span class="muted" style="font-size:11.5px">Administrator only</span>
              <% } else { %>
                <div class="acts">
                  <details class="row">
                    <summary>Edit</summary>
                    <div>
                      <form method="post" action="<%= ctx %>/staff" style="display:flex;gap:7px;flex-wrap:wrap">
                        <input type="hidden" name="action" value="update">
                        <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                        <input type="text" name="fullName" required maxlength="100"
                               value="<%= esc(u.getFullName()) %>" style="flex:1 1 150px">
                        <input type="tel" name="mobile" maxlength="15" placeholder="mobile"
                               value="<%= esc(u.getMobile()) %>" style="flex:1 1 120px">
                        <input type="email" name="email" maxlength="120" placeholder="email"
                               value="<%= esc(u.getEmail()) %>" style="flex:1 1 160px">
                        <button class="btn sm" type="submit">Save</button>
                      </form>
                    </div>
                  </details>

                  <% if (!isSelf && !lastAdmin) { %>
                  <details class="row">
                    <summary>Role</summary>
                    <div>
                      <form method="post" action="<%= ctx %>/staff" style="display:flex;gap:7px"
                            onsubmit="return confirm('Change what <%= esc(u.getFullName()) %> can reach?');">
                        <input type="hidden" name="action" value="role">
                        <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                        <select name="role">
                          <% for (String r : allRoles) { if (!assignable.contains(r)) continue; %>
                            <option value="<%= r %>"<%= r.equals(u.getRole())?" selected":"" %>><%= esc(Role.labelOf(r)) %></option>
                          <% } %>
                        </select>
                        <button class="btn sm" type="submit">Move</button>
                      </form>
                    </div>
                  </details>
                  <% } %>

                  <details class="row">
                    <summary>Reports to</summary>
                    <div>
                      <form method="post" action="<%= ctx %>/staff" style="display:flex;gap:7px">
                        <input type="hidden" name="action" value="manager">
                        <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                        <select name="reportsTo" style="flex:1 1 170px">
                          <option value="">Nobody</option>
                          <% for (User m : managers) { if (m.getUserId() == u.getUserId()) continue; %>
                            <option value="<%= m.getUserId() %>"<%= u.getReportsTo() != null && u.getReportsTo().intValue() == m.getUserId() ? " selected" : "" %>><%= esc(m.getFullName()) %> &middot; <%= esc(m.getRoleLabel()) %></option>
                          <% } %>
                        </select>
                        <button class="btn sm" type="submit">Set</button>
                      </form>
                    </div>
                  </details>

                  <details class="row">
                    <summary>Password</summary>
                    <div>
                      <form method="post" action="<%= ctx %>/staff" autocomplete="off"
                            style="display:flex;gap:7px">
                        <input type="hidden" name="action" value="password">
                        <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                        <input type="text" name="password" minlength="6" required
                               placeholder="new password" autocomplete="new-password"
                               style="flex:1 1 150px">
                        <button class="btn sm alt" type="submit">Reset</button>
                      </form>
                    </div>
                  </details>

                  <% if (u.isActive()) { %>
                    <% if (!isSelf && !lastAdmin) { %>
                      <form method="post" action="<%= ctx %>/staff" style="display:inline"
                            onsubmit="return confirm('Switch off <%= esc(u.getFullName()) %>? They will not be able to sign in. Their records are kept.');">
                        <input type="hidden" name="action" value="disable">
                        <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                        <button class="lnk warn" type="submit">Switch off</button>
                      </form>
                    <% } %>
                  <% } else { %>
                    <form method="post" action="<%= ctx %>/staff" style="display:inline">
                      <input type="hidden" name="action" value="enable">
                      <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                      <button class="lnk" type="submit">Switch on</button>
                    </form>
                  <% } %>
                </div>
              <% } %>
            </td>
          </tr>
        <% } %>
        <% if (staff.isEmpty()) { %>
          <tr><td colspan="7" class="muted" style="padding:20px 12px">No accounts found.</td></tr>
        <% } %>
        </tbody>
      </table>
    </div>
  </div>

  <%-- ────────── change history ────────── --%>
  <div class="card">
    <details>
      <summary style="cursor:pointer;font-size:13px;color:#0E5C3F;font-weight:600">
        Change history <span class="muted">(<%= trail.size() %>)</span></summary>
      <div style="margin-top:14px">
        <p class="hint">Every login created, edited, moved between roles, reset or switched on and
           off &mdash; and by whom. Password resets are recorded; the passwords themselves are not
           stored in a readable form anywhere.</p>
        <div class="t-wrap">
          <table class="t" style="min-width:760px">
            <thead><tr>
              <th style="width:135px">When</th><th style="width:145px">Action</th>
              <th style="width:190px">Account</th><th>Detail</th><th style="width:150px">By</th>
            </tr></thead>
            <tbody>
            <% for (UserAudit a : trail) { %>
              <tr>
                <td class="muted"><%= esc(a.getActedAt()) %></td>
                <td><span class="tag <%= a.isPrivilegeChange() ? "priv" : "" %>"><%=
                    esc(a.getActionLabel()) %></span></td>
                <td><%= esc(a.getTargetName()) %>
                  <div class="sub2 mono"><%= esc(a.getTargetUsername()) %></div></td>
                <td class="muted"><%= a.getDetail()==null ? "&mdash;" : esc(a.getDetail()) %></td>
                <td class="muted"><%= a.getActedBy()==null ? "&mdash;" : esc(a.getActedBy()) %></td>
              </tr>
            <% } %>
            <% if (trail.isEmpty()) { %>
              <tr><td colspan="5" class="muted" style="padding:20px 12px">Nothing recorded yet.</td></tr>
            <% } %>
            </tbody>
          </table>
        </div>
      </div>
    </details>
  </div>

</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="staff"/></jsp:include>

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
