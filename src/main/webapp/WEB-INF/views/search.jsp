<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.SearchHit, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<SearchHit> hits = (List<SearchHit>) request.getAttribute("hits");
    String  q         = (String)  request.getAttribute("q");
    String  error     = (String)  request.getAttribute("error");
    Boolean searched  = (Boolean) request.getAttribute("searched");
    String  pfMobile  = (String)  request.getAttribute("prefillMobile");
    String  pfName    = (String)  request.getAttribute("prefillName");
    if (q == null) q = "";

    int leadCount = 0, stuCount = 0;
    if (hits != null) {
        for (SearchHit h : hits) { if (h.isLead()) leadCount++; else stuCount++; }
    }
    boolean nothingFound = Boolean.TRUE.equals(searched) && (hits == null || hits.isEmpty());
%>
<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String urlEnc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
    private String badgeClass(String status) {
        if (status == null) return "st-new";
        switch (status) {
            case "ADMITTED":          return "st-admitted";
            case "CONVERTED":         return "st-converted";
            case "CONTACTED":         return "st-contacted";
            case "INTERESTED":        return "st-interested";
            case "DEMO_PENDING":
            case "DEMO_COMPLETED":    return "st-demo";
            case "FOLLOWUP_REQUIRED": return "st-followup";
            case "NOT_INTERESTED":
            case "LOST":              return "st-lost";
            default:                  return "st-new";
        }
    }
    private String dash(String s) { return (s == null || s.isEmpty()) ? "—" : s; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Search – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .search-hero { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:26px 24px; margin-bottom:22px; }
    .search-hero h2 { font-family:'Playfair Display',serif; font-size:23px; color:var(--green-dark); margin-bottom:4px; }
    .search-hero p { font-size:13px; color:var(--muted); margin-bottom:16px; }
    .search-row { display:flex; gap:10px; flex-wrap:wrap; }
    .search-row input { flex:1 1 320px; padding:13px 16px; border:2px solid var(--border); border-radius:10px;
      font-size:15px; font-family:inherit; }
    .search-row input:focus { outline:none; border-color:var(--green); }
    .search-row button { background:var(--green); color:#fff; border:none; border-radius:10px; padding:13px 26px;
      font-size:14px; font-weight:700; cursor:pointer; font-family:inherit; }
    .search-row button:hover { background:var(--green-dark); }

    .res-group { margin-bottom:22px; }
    .res-group h3 { font-size:13px; text-transform:uppercase; letter-spacing:0.5px; color:var(--green-dark);
      margin-bottom:10px; display:flex; align-items:center; gap:8px; }
    .res-group h3 .n { background:var(--green-light); color:var(--green-dark); border-radius:20px;
      padding:2px 10px; font-size:11px; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:760px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:11px 14px; font-size:11px;
      font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:11px 14px; border-bottom:1px solid var(--border); vertical-align:middle; }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 10px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-new { background:#FFF4D6; color:#9A6B00; }
    .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-interested { background:#E4DDFF; color:#4B2E9C; }
    .st-demo { background:#D9F2F7; color:#0F6C7E; }
    .st-followup { background:#FFE6D6; color:#9C4A16; }
    .st-converted, .st-admitted { background:var(--green-light); color:var(--success); }
    .st-lost { background:#EEE; color:#666; }
    .btn-open { display:inline-block; background:var(--green); color:#fff; text-decoration:none; font-size:12px;
      font-weight:700; padding:7px 13px; border-radius:6px; white-space:nowrap; }
    .btn-open:hover { background:var(--green-dark); }

    /* The whole reason this screen exists: a dead end must offer the next step. */
    .no-match { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:46px 24px; text-align:center; }
    .no-match .ic { font-size:48px; margin-bottom:14px; }
    .no-match h3 { font-size:19px; color:var(--green-dark); margin-bottom:6px; }
    .no-match p { font-size:13.5px; color:var(--muted); margin-bottom:20px; }
    .no-match .cta { display:inline-block; background:var(--green); color:#fff; text-decoration:none;
      font-size:15px; font-weight:700; padding:14px 30px; border-radius:10px; }
    .no-match .cta:hover { background:var(--green-dark); }
    .hint { font-size:12px; color:var(--muted); margin-top:14px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px;
      border-radius:8px; font-size:13px; margin-bottom:18px; }
    .tips { font-size:12.5px; color:var(--muted); margin-top:10px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="search"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="search-hero">
    <h2>Find a student or enquiry</h2>
    <p>Search leads and admitted students together — by mobile number, parent's mobile, name or admission number.</p>
    <form method="get" action="<%= ctx %>/search" class="search-row">
      <input type="text" name="q" value="<%= esc(q) %>" autofocus
             placeholder="e.g. 9876543210 or Rahul Patil or HNS-2627-0481">
      <button type="submit">Search</button>
    </form>
    <div class="tips">Tip: a 10-digit number is matched exactly against student, parent and alternate mobiles.</div>
  </div>

  <% if (error != null) { %>
    <div class="alert error"><%= esc(error) %></div>
  <% } %>

  <% if (nothingFound) { %>
    <div class="no-match">
      <div class="ic">🔍</div>
      <h3>No record found for “<%= esc(q) %>”</h3>
      <p>This person is not in the enquiry list and has not been admitted.</p>
      <a class="cta" href="<%= ctx %>/lead?new=1<%
             if (pfMobile != null) { %>&mobile=<%= urlEnc(pfMobile) %><% }
             if (pfName   != null) { %>&name=<%= urlEnc(pfName) %><% } %>">➕ Create New Enquiry</a>
      <div class="hint">The new enquiry form opens with <%= (pfMobile != null) ? "this mobile number" : "this name" %> already filled in.</div>
    </div>
  <% } else if (hits != null && !hits.isEmpty()) { %>

    <% if (leadCount > 0) { %>
      <div class="res-group">
        <h3>Enquiries / Leads <span class="n"><%= leadCount %></span></h3>
        <div class="table-wrap">
          <table class="lst">
            <thead>
              <tr><th>Name</th><th>Mobile</th><th>Parent Mobile</th><th>Course Interest</th>
                  <th>Status</th><th>Counsellor</th><th>Source</th><th></th></tr>
            </thead>
            <tbody>
            <% for (SearchHit h : hits) { if (!h.isLead()) continue; %>
              <tr>
                <td class="nm"><%= esc(h.getName()) %></td>
                <td><%= esc(dash(h.getMobile())) %></td>
                <td><%= esc(dash(h.getParentMobile())) %></td>
                <td><%= esc(dash(h.getCourseOrClass())) %></td>
                <td><span class="badge <%= badgeClass(h.getStatus()) %>"><%= esc(h.getStatus()) %></span></td>
                <td><%= esc(dash(h.getCounsellorName())) %></td>
                <td><%= esc(dash(h.getReference())) %></td>
                <td><a class="btn-open" href="<%= ctx %>/lead?id=<%= h.getId() %>">Open →</a></td>
              </tr>
            <% } %>
            </tbody>
          </table>
        </div>
      </div>
    <% } %>

    <% if (stuCount > 0) { %>
      <div class="res-group">
        <h3>Admitted Students <span class="n"><%= stuCount %></span></h3>
        <div class="table-wrap">
          <table class="lst">
            <thead>
              <tr><th>Name</th><th>Admission No</th><th>Mobile</th><th>Parent Mobile</th>
                  <th>Class</th><th>Status</th><th>Counsellor</th><th></th></tr>
            </thead>
            <tbody>
            <% for (SearchHit h : hits) { if (!h.isStudent()) continue; %>
              <tr>
                <td class="nm"><%= esc(h.getName()) %></td>
                <td><%= esc(dash(h.getReference())) %></td>
                <td><%= esc(dash(h.getMobile())) %></td>
                <td><%= esc(dash(h.getParentMobile())) %></td>
                <td><%= esc(dash(h.getCourseOrClass())) %></td>
                <td><span class="badge st-admitted">ADMITTED</span></td>
                <td><%= esc(dash(h.getCounsellorName())) %></td>
                <td><a class="btn-open" href="<%= ctx %>/student?id=<%= h.getId() %>">Open →</a></td>
              </tr>
            <% } %>
            </tbody>
          </table>
        </div>
      </div>
    <% } %>

    <div class="tips">
      Not the person you meant?
      <a href="<%= ctx %>/lead?new=1<% if (pfMobile != null) { %>&mobile=<%= urlEnc(pfMobile) %><% } %>">Create a new enquiry instead →</a>
    </div>

  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
