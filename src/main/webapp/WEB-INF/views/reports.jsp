<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.ReportResult, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    ReportResult rep = (ReportResult) request.getAttribute("report");
    @SuppressWarnings("unchecked")
    List<String[]> types = (List<String[]>) request.getAttribute("types");
    String type  = (String) request.getAttribute("type");
    String from  = (String) request.getAttribute("from");
    String to    = (String) request.getAttribute("to");
    String error = (String) request.getAttribute("error");
    String msg   = request.getParameter("msg");
    if (type == null) type = "";
    if (from == null) from = "";
    if (to == null)   to = "";

    String exportUrl = ctx + "/report-export?type=" + type
                     + "&from=" + java.net.URLEncoder.encode(from, "UTF-8")
                     + "&to=" + java.net.URLEncoder.encode(to, "UTF-8");
%>
<%!
    private String esc(String x) {
        if (x == null) return "";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    /** Section separators the DAO emits as "— … —" get their own styling. */
    private boolean isDivider(String[] row) {
        if (row == null || row.length == 0 || row[0] == null) return false;
        return row[0].startsWith("—");
    }
    private boolean isBlankRow(String[] row) {
        if (row == null) return true;
        for (String c : row) { if (c != null && !c.isEmpty()) return false; }
        return true;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reports – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:flex-start; justify-content:space-between; flex-wrap:wrap;
      gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .list-head .desc { font-size:13px; color:var(--muted); margin-top:4px; max-width:640px; }
    .btn { display:inline-block; text-decoration:none; font-size:13px; font-weight:700; padding:10px 18px;
      border-radius:8px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); color:var(--muted); }

    .picker { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:14px; }
    .picker a { text-decoration:none; font-size:12.5px; font-weight:700; padding:8px 14px;
      border-radius:20px; border:1.5px solid var(--border); color:var(--muted); background:var(--white); }
    .picker a.on { background:var(--green); color:#fff; border-color:var(--green); }

    .filters { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:14px 18px; margin-bottom:16px; display:flex; gap:12px; align-items:end; flex-wrap:wrap; }
    .filters .f { display:flex; flex-direction:column; }
    .filters label { font-size:10.5px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--muted); margin-bottom:4px; }
    .filters input { padding:8px 10px; border:1.5px solid var(--border); border-radius:7px;
      font-size:13px; font-family:inherit; }
    .filters .quick { display:flex; gap:6px; flex-wrap:wrap; margin-left:auto; }
    .filters .quick a { font-size:11.5px; color:var(--muted); text-decoration:none;
      border:1px solid var(--border); border-radius:14px; padding:5px 11px; }
    .filters .quick a:hover { color:var(--green-dark); border-color:var(--green); }
    .pit { font-size:12.5px; color:var(--muted); }

    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.rep { width:100%; border-collapse:collapse; font-size:13px; }
    table.rep th { background:var(--green); color:#fff; text-align:left; padding:11px 13px; font-size:10.5px;
      font-weight:700; text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    table.rep td { padding:9px 13px; border-bottom:1px solid var(--border); }
    table.rep tr:last-child td { border-bottom:none; }
    table.rep tr:nth-child(even) td { background:var(--green-pale); }
    table.rep td.num, table.rep th.num { text-align:right; }
    table.rep tr.divider td { background:var(--green-light) !important; font-weight:800;
      color:var(--green-dark); font-size:11.5px; text-transform:uppercase; letter-spacing:0.4px; }
    table.rep tr.spacer td { background:var(--white) !important; padding:4px; border-bottom:none; }
    table.rep tfoot td { background:var(--green-dark) !important; color:#fff; font-weight:800;
      font-size:13.5px; padding:12px 13px; }

    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .count { font-size:12.5px; color:var(--muted); margin:10px 0; }

    @media print {
      header, .neet-ribbon, .picker, .filters, .footer, .tabbar, .no-print { display:none !important; }
      .table-wrap { box-shadow:none; }
      body { background:#fff; }
    }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="reports"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <div>
      <h2><%= rep == null ? "Reports" : esc(rep.getTitle()) %></h2>
      <% if (rep != null && rep.getDescription() != null) { %>
        <div class="desc"><%= esc(rep.getDescription()) %></div>
      <% } %>
    </div>
    <div style="display:flex;gap:9px;flex-wrap:wrap;" class="no-print">
      <a class="btn btn-ghost" href="javascript:window.print()">🖨 Print</a>
      <a class="btn btn-primary" href="<%= exportUrl %>">⬇ Download Excel</a>
    </div>
  </div>

  <% if ("exporterror".equals(msg)) { %>
    <div class="alert error">The export failed. Please try again.</div>
  <% } %>
  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <div class="picker no-print">
    <% if (types != null) for (String[] t : types) { %>
      <a class="<%= t[0].equals(type) ? "on" : "" %>"
         href="<%= ctx %>/reports?type=<%= t[0] %>&from=<%= esc(from) %>&to=<%= esc(to) %>"><%= esc(t[1]) %></a>
    <% } %>
  </div>

  <% if (rep != null && rep.isDateRanged()) {
       boolean allTime = from.isEmpty() && to.isEmpty(); %>
    <% if (allTime) { %>
      <p class="pit" style="margin-bottom:10px;">Showing <b>all time</b> — no date filter applied.</p>
    <% } %>
    <form class="filters no-print" method="get" action="<%= ctx %>/reports">
      <input type="hidden" name="type" value="<%= esc(type) %>">
      <div class="f"><label>From</label><input type="date" name="from" value="<%= esc(from) %>"></div>
      <div class="f"><label>To</label><input type="date" name="to" value="<%= esc(to) %>"></div>
      <div class="f"><label>&nbsp;</label><button type="submit" class="btn btn-primary" style="padding:9px 18px;">Apply</button></div>
      <%
         java.time.LocalDate td = java.time.LocalDate.now();
         String[][] quick = {
           { "Today",        td.toString(), td.toString() },
           { "This month",   td.withDayOfMonth(1).toString(), td.toString() },
           { "Last month",   td.minusMonths(1).withDayOfMonth(1).toString(),
                             td.withDayOfMonth(1).minusDays(1).toString() },
           { "Last 90 days", td.minusDays(90).toString(), td.toString() },
           { "All time",     "", "" }
         };
      %>
      <div class="quick">
        <% for (String[] q : quick) { %>
          <a href="<%= ctx %>/reports?type=<%= esc(type) %>&from=<%= q[1] %>&to=<%= q[2] %>"><%= q[0] %></a>
        <% } %>
      </div>
    </form>
  <% } else if (rep != null) { %>
    <p class="pit">This report shows the position as of today — a date range does not apply.</p>
  <% } %>

  <% if (rep == null || rep.isEmpty()) { %>
    <div class="table-wrap">
      <div class="empty">
        <div class="ic">📊</div>
        <p>No data for this report<%= (rep != null && rep.isDateRanged()) ? " in the selected period" : "" %>.</p>
      </div>
    </div>
  <% } else { %>
    <div class="count"><%= rep.size() %> row<%= rep.size() == 1 ? "" : "s" %></div>
    <div class="table-wrap">
      <table class="rep">
        <thead>
          <tr>
            <% String[] cols = rep.getColumns();
               for (int i = 0; i < cols.length; i++) { %>
              <th class="<%= rep.isNumericCol(i) ? "num" : "" %>"><%= esc(cols[i]) %></th>
            <% } %>
          </tr>
        </thead>
        <tbody>
        <% for (String[] row : rep.getRows()) {
             String cls = isDivider(row) ? "divider" : (isBlankRow(row) ? "spacer" : ""); %>
          <tr class="<%= cls %>">
            <% for (int i = 0; i < cols.length; i++) {
                 String cell = (i < row.length && row[i] != null) ? row[i] : ""; %>
              <td class="<%= rep.isNumericCol(i) ? "num" : "" %>"><%= esc(cell) %></td>
            <% } %>
          </tr>
        <% } %>
        </tbody>
        <% if (rep.getTotals() != null) { %>
        <tfoot>
          <tr>
            <% String[] tot = rep.getTotals();
               for (int i = 0; i < cols.length; i++) {
                 String cell = (i < tot.length && tot[i] != null) ? tot[i] : ""; %>
              <td class="<%= rep.isNumericCol(i) ? "num" : "" %>"><%= esc(cell) %></td>
            <% } %>
          </tr>
        </tfoot>
        <% } %>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
