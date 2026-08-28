<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<Student> students = (List<Student>) request.getAttribute("students");
    String error = (String) request.getAttribute("error");
    int total = (students == null) ? 0 : students.size();
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Students – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:20px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .count-pill { background:var(--green-light); color:var(--green-dark); font-size:12px; font-weight:700;
      padding:4px 12px; border-radius:20px; border:1px solid var(--border); }
    .btn-add { background:var(--green); color:#fff; text-decoration:none; font-size:13px; font-weight:700;
      padding:9px 16px; border-radius:7px; transition:background .2s; }
    .btn-add:hover { background:var(--green-dark); }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      overflow-x:auto; }
    table.stu { width:100%; border-collapse:collapse; font-size:13px; min-width:760px; }
    table.stu th { background:var(--green); color:#fff; text-align:left; padding:12px 14px;
      font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.stu td { padding:12px 14px; border-bottom:1px solid var(--border); color:var(--text); }
    table.stu tr:last-child td { border-bottom:none; }
    table.stu tr:nth-child(even) td { background:var(--green-pale); }
    .adm-no { font-weight:700; color:var(--green-dark); white-space:nowrap; }
    .stu-link { color:var(--green-dark); font-weight:700; text-decoration:none; white-space:nowrap; }
    .stu-link:hover { text-decoration:underline; }
    tr.inactive td { opacity:0.55; }
    .off-tag { display:inline-block; background:#EEE; color:#666; font-size:9.5px; font-weight:800;
      padding:2px 7px; border-radius:10px; margin-left:6px; text-transform:uppercase; }
    .slab-tag { display:inline-block; background:var(--green-light); color:var(--green-dark);
      font-size:11px; font-weight:700; padding:2px 9px; border-radius:12px; text-transform:capitalize; }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB;
      padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="students"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="list-head">
    <div>
      <h2>Admitted Students</h2>
    </div>
    <div style="display:flex; align-items:center; gap:12px;">
      <span class="count-pill"><%= total %> total</span>
      <a class="btn-add" href="<%= ctx %>/admission.jsp">+ New Admission</a>
    </div>
  </div>

  <% if (error != null) { %>
    <div class="alert error"><%= error %></div>
  <% } %>

  <% if (students == null || students.isEmpty()) { %>
    <div class="table-wrap">
      <div class="empty">
        <div class="ic">🗂️</div>
        <p>No students admitted yet.</p>
        <p style="margin-top:6px;"><a href="<%= ctx %>/admission.jsp" style="color:var(--green);font-weight:600;">Add the first admission →</a></p>
      </div>
    </div>
  <% } else { %>
    <div class="filter-bar">
      <input type="text" id="fSearch" placeholder="Search name, admission no, mobile…" oninput="applyFilters()">
      <select id="fClass" data-filter data-col="3" onchange="applyFilters()"><option value="">All classes</option></select>
      <span class="fcount"><b id="fCount"></b> shown</span>
    </div>
    <div class="table-wrap">
      <table class="stu" id="fTable">
        <thead>
          <tr>
            <th>#</th><th>Admission No</th><th>Name</th><th>Class</th>
            <th>Batch</th><th>Student Mobile</th><th>Parent Mobile</th>
            <th>Counsellor</th><th>Plan</th><th></th>
          </tr>
        </thead>
        <tbody>
          <% int i = 1; for (Student s : students) { %>
          <tr<%= s.isActive() ? "" : " class=\"inactive\"" %>>
            <td><%= i++ %></td>
            <td class="adm-no"><%= s.getAdmissionNo() %></td>
            <td>
              <a class="stu-link" href="<%= ctx %>/student?id=<%= s.getStudentId() %>"><%= s.getFullName() == null ? "" : s.getFullName() %></a>
              <% if (!s.isActive()) { %><span class="off-tag">inactive</span><% } %>
            </td>
            <td><%= s.getClassName() == null ? "—" : s.getClassName() %></td>
            <td><%= s.getBatchName() == null ? "—" : s.getBatchName() %></td>
            <td><%= s.getStudentMobile() == null ? "—" : s.getStudentMobile() %></td>
            <td><%= s.getParentMobile() == null ? "—" : s.getParentMobile() %></td>
            <td><%= s.getCounsellorName() == null ? "—" : s.getCounsellorName() %></td>
            <td><% if (s.getFeeSlab() != null) { %><span class="slab-tag"><%= s.getFeeSlab() %></span><% } else { %>—<% } %></td>
            <td><a class="stu-link" href="<%= ctx %>/student?id=<%= s.getStudentId() %>">Open →</a></td>
          </tr>
          <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="students"/></jsp:include>

<script src="<%= ctx %>/js/filter.js"></script>
</body>
</html>
