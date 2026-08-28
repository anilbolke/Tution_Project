<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.FeeSummary, com.tution.model.User, com.tution.util.FeeCalculator" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<FeeSummary> rows = (List<FeeSummary>) request.getAttribute("rows");
    String error = (String) request.getAttribute("error");
    long totBilled = request.getAttribute("totBilled")==null?0:(Long)request.getAttribute("totBilled");
    long totPaid   = request.getAttribute("totPaid")==null?0:(Long)request.getAttribute("totPaid");
    long totDue    = request.getAttribute("totDue")==null?0:(Long)request.getAttribute("totDue");
    long totConcession = request.getAttribute("totConcession")==null?0:(Long)request.getAttribute("totConcession");
    int count = (rows==null)?0:rows.size();
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    private String stClass(String s){ if("PAID".equals(s))return "st-paid"; if("PARTIAL".equals(s))return "st-partial"; return "st-pending"; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Fees – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:18px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700; padding:4px 12px; border-radius:20px; border:1px solid var(--border); }
    .stat-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:14px; margin-bottom:22px; }
    .stat-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:18px 20px; border-left:5px solid var(--green); }
    .stat-card.paid { border-left-color:var(--success); }
    .stat-card.due  { border-left-color:#E05A2B; }
    .stat-label { font-size:11px; font-weight:700; color:var(--muted); text-transform:uppercase; letter-spacing:0.6px; margin-bottom:6px; }
    .stat-value { font-size:24px; font-weight:800; color:var(--green-dark); }
    .stat-card.due .stat-value { color:#E05A2B; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:820px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:12px 14px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:11px 14px; border-bottom:1px solid var(--border); }
    table.lst tr:last-child td { border-bottom:none; }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .amt { white-space:nowrap; font-weight:600; }
    .amt.due { color:#E05A2B; }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 10px; border-radius:12px; text-transform:uppercase; letter-spacing:0.4px; }
    .st-paid { background:var(--green-light); color:var(--success); }
    .st-partial { background:#FFF4D6; color:#9A6B00; }
    .st-pending { background:#FDE7E0; color:#C0392B; }
    .btn-collect { display:inline-block; background:var(--green); color:#fff; text-decoration:none; font-size:12px; font-weight:700; padding:7px 13px; border-radius:6px; white-space:nowrap; transition:background .2s; }
    .btn-plan { display:inline-block; border:1.5px solid var(--border); color:var(--muted); text-decoration:none;
      font-size:12px; font-weight:700; padding:6px 11px; border-radius:6px; white-space:nowrap; }
    .btn-plan:hover { color:var(--green-dark); border-color:var(--green); }
    td.acts { display:flex; gap:6px; align-items:center; flex-wrap:wrap; }
    .conc { color:#C0392B; font-weight:700; }
    .btn-collect:hover { background:var(--green-dark); }
    .done-txt { font-size:12px; color:var(--success); font-weight:600; }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Fee Management</h2>
    <span class="count-pill"><%= count %> students</span>
  </div>

  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <div class="stat-grid">
    <div class="stat-card">
      <div class="stat-label">Total Billed</div>
      <div class="stat-value"><%= FeeCalculator.inr(totBilled) %></div>
    </div>
    <div class="stat-card paid">
      <div class="stat-label">Collected</div>
      <div class="stat-value"><%= FeeCalculator.inr(totPaid) %></div>
    </div>
    <div class="stat-card due">
      <div class="stat-label">Outstanding</div>
      <div class="stat-value"><%= FeeCalculator.inr(totDue) %></div>
    </div>
    <% if (totConcession > 0) { %>
    <div class="stat-card">
      <div class="stat-label">Concessions Given</div>
      <div class="stat-value"><%= FeeCalculator.inr(totConcession) %></div>
    </div>
    <% } %>
  </div>

  <% if (rows == null || rows.isEmpty()) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">💰</div><p>No students to bill yet.</p></div></div>
  <% } else { %>
    <div class="filter-bar">
      <input type="text" id="fSearch" placeholder="Search name or admission no…" oninput="applyFilters()">
      <select data-filter data-col="9" onchange="applyFilters()">
        <option value="">All statuses</option>
        <option value="PAID">Paid</option>
        <option value="PARTIAL">Partial</option>
        <option value="PENDING">Pending</option>
      </select>
      <span class="fcount"><b id="fCount"></b> shown</span>
    </div>
    <div class="table-wrap">
      <table class="lst" id="fTable">
        <thead>
          <tr><th>#</th><th>Admission No</th><th>Name</th><th>Class</th><th>Plan</th>
              <th>Total</th><th>Concession</th><th>Paid</th><th>Outstanding</th><th>Status</th><th>Action</th></tr>
        </thead>
        <tbody>
          <% int i=1; for (FeeSummary r : rows) { %>
          <tr>
            <td><%= i++ %></td>
            <td class="nm"><%= esc(r.getAdmissionNo()) %></td>
            <td><%= esc(r.getFullName()) %></td>
            <td><%= r.getClassName()==null?"—":esc(r.getClassName()) %></td>
            <td><%= esc(r.getSlabLabel()) %></td>
            <td class="amt"><%= FeeCalculator.inr(r.getTotalFee()) %></td>
            <td class="amt"><%= r.getConcession() > 0
                  ? "<span class=\"conc\">- " + FeeCalculator.inr(r.getConcession()) + "</span>" : "—" %></td>
            <td class="amt"><%= FeeCalculator.inr(r.getPaid()) %></td>
            <td class="amt due"><%= FeeCalculator.inr(r.getOutstanding()) %></td>
            <td><span class="badge <%= stClass(r.getStatus()) %>"><%= r.getStatus() %></span></td>
            <td class="acts">
              <% if ("PAID".equals(r.getStatus())) { %>
                <span class="done-txt">✓ Settled</span>
              <% } else { %>
                <a class="btn-collect" href="<%= ctx %>/collect?studentId=<%= r.getStudentId() %>">Collect →</a>
              <% } %>
              <a class="btn-plan" href="<%= ctx %>/fee-plan?studentId=<%= r.getStudentId() %>">Plan</a>
            </td>
          </tr>
          <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<script src="<%= ctx %>/js/filter.js"></script>
</body>
</html>
