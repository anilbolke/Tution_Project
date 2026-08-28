<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Set, java.util.Map, com.tution.model.Student, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<Student> students = (List<Student>) request.getAttribute("students");
    @SuppressWarnings("unchecked") Set<String> classes = (Set<String>) request.getAttribute("classes");
    @SuppressWarnings("unchecked") Map<Integer,String> statusMap = (Map<Integer,String>) request.getAttribute("statusMap");
    String date = (String) request.getAttribute("date");
    String classFilter = (String) request.getAttribute("classFilter");
    if (classFilter == null) classFilter = "";
    boolean saved = Boolean.TRUE.equals(request.getAttribute("saved"));
    String error = (String) request.getAttribute("error");
    int count = (students==null)?0:students.size();
%>
<%!
    private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
    private String ck(String cur, String val){ return val.equals(cur) ? "checked" : ""; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Attendance – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .toolbar { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 18px; margin-bottom:18px; display:flex; flex-wrap:wrap; align-items:flex-end; gap:14px; }
    .toolbar .fld label { display:block; font-size:12px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .toolbar input, .toolbar select { padding:9px 11px; font-size:14px; border:1.5px solid var(--border); border-radius:7px; background:var(--green-pale); font-family:'Inter',sans-serif; }
    .btn { padding:9px 16px; border:none; border-radius:7px; font-size:13px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; text-decoration:none; display:inline-block; }
    .btn-go { background:var(--green-dark); color:#fff; }
    .btn-report { background:transparent; color:var(--green-dark); border:1.5px solid var(--green-dark); margin-left:auto; }
    .alert { padding:11px 14px; border-radius:8px; font-size:13px; font-weight:500; margin-bottom:16px; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid var(--border); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.att { width:100%; border-collapse:collapse; font-size:13px; min-width:560px; }
    table.att th { background:var(--green); color:#fff; text-align:left; padding:11px 14px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.att td { padding:9px 14px; border-bottom:1px solid var(--border); }
    table.att tr:last-child td { border-bottom:none; }
    table.att tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    .att-opts { display:flex; gap:5px; }
    .att-opts input { position:absolute; opacity:0; width:0; height:0; }
    .att-opts label { display:inline-flex; align-items:center; justify-content:center; min-width:32px; height:30px; border:1.5px solid var(--border); border-radius:6px; font-size:12px; font-weight:700; cursor:pointer; color:var(--muted); background:#fff; transition:all .12s; }
    .att-opts input:checked + label.p  { background:var(--green); color:#fff; border-color:var(--green); }
    .att-opts input:checked + label.a  { background:#C0392B; color:#fff; border-color:#C0392B; }
    .att-opts input:checked + label.l  { background:#B8860B; color:#fff; border-color:#B8860B; }
    .att-opts input:checked + label.lv { background:#5A7364; color:#fff; border-color:#5A7364; }
    .save-bar { position:sticky; bottom:0; background:var(--white); border-top:1px solid var(--border); padding:14px; display:flex; gap:10px; justify-content:flex-end; border-radius:0 0 var(--radius) var(--radius); }
    .btn-allp { background:var(--green-light); color:var(--green-dark); border:1.5px solid var(--border); }
    .btn-save { background:var(--green); color:#fff; }
    .empty { text-align:center; padding:42px 20px; color:var(--muted); }
    .empty .ic { font-size:40px; margin-bottom:10px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="attendance"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <h2>Mark Attendance</h2>
  </div>

  <% if (saved) { %><div class="alert ok">✓ Attendance saved for <%= esc(date) %>.</div><% } %>
  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <!-- filter bar -->
  <form class="toolbar" method="get" action="<%= ctx %>/attendance">
    <div class="fld">
      <label>Date</label>
      <input type="date" name="date" value="<%= esc(date) %>"/>
    </div>
    <div class="fld">
      <label>Class</label>
      <select name="class">
        <option value="">All classes</option>
        <% if (classes != null) for (String c : classes) { %>
          <option value="<%= esc(c) %>" <%= c.equals(classFilter)?"selected":"" %>><%= esc(c) %></option>
        <% } %>
      </select>
    </div>
    <button type="submit" class="btn btn-go">Load</button>
    <a class="btn btn-report" href="<%= ctx %>/attendance-report">📊 View Report</a>
  </form>

  <% if (students == null || students.isEmpty()) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">📅</div><p>No students<%= classFilter.isEmpty()?"":" in this class" %> to mark.</p></div></div>
  <% } else { %>
    <form method="post" action="<%= ctx %>/attendance">
      <input type="hidden" name="date" value="<%= esc(date) %>"/>
      <input type="hidden" name="class" value="<%= esc(classFilter) %>"/>
      <div class="filter-bar">
        <input type="text" id="fSearch" placeholder="Search student name or admission no…" oninput="applyFilters()">
        <span class="fcount"><b id="fCount"></b> shown</span>
      </div>
      <div class="table-wrap">
        <table class="att" id="fTable">
          <thead><tr><th>#</th><th>Name</th><th>Admission No</th><th>Class</th><th>Status</th></tr></thead>
          <tbody>
          <% int i=1; for (Student s : students) {
                int id = s.getStudentId();
                String cur = (statusMap!=null && statusMap.get(id)!=null) ? statusMap.get(id) : "Present"; %>
            <tr>
              <td><%= i++ %></td>
              <td class="nm"><%= esc(s.getFullName()) %></td>
              <td><%= esc(s.getAdmissionNo()) %></td>
              <td><%= s.getClassName()==null?"—":esc(s.getClassName()) %></td>
              <td>
                <div class="att-opts">
                  <input type="radio" id="p_<%=id%>"  name="status_<%=id%>" value="Present" <%= ck(cur,"Present") %>/><label class="p"  for="p_<%=id%>">P</label>
                  <input type="radio" id="a_<%=id%>"  name="status_<%=id%>" value="Absent"  <%= ck(cur,"Absent")  %>/><label class="a"  for="a_<%=id%>">A</label>
                  <input type="radio" id="l_<%=id%>"  name="status_<%=id%>" value="Late"    <%= ck(cur,"Late")    %>/><label class="l"  for="l_<%=id%>">Late</label>
                  <input type="radio" id="lv_<%=id%>" name="status_<%=id%>" value="Leave"   <%= ck(cur,"Leave")   %>/><label class="lv" for="lv_<%=id%>">Leave</label>
                </div>
              </td>
            </tr>
          <% } %>
          </tbody>
        </table>
        <div class="save-bar">
          <button type="button" class="btn btn-allp" onclick="markAllPresent()">Mark all Present</button>
          <button type="submit" class="btn btn-save">💾 Save Attendance</button>
        </div>
      </div>
    </form>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="attendance"/></jsp:include>

<script>
  function markAllPresent(){
    document.querySelectorAll('input[id^="p_"]').forEach(function(r){ r.checked = true; });
  }
</script>

<script src="<%= ctx %>/js/filter.js"></script>
</body>
</html>
