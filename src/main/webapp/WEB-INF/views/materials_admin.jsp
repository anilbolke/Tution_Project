<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Set, com.tution.model.Material, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<Material> materials = (List<Material>) request.getAttribute("materials");
    @SuppressWarnings("unchecked") Set<String> classes = (Set<String>) request.getAttribute("classes");
    String error = (String) request.getAttribute("error");
    boolean saved = Boolean.TRUE.equals(request.getAttribute("saved"));
%>
<%! private String esc(String s){ return s==null||"null".equals(s)? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Learning Materials – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    h2.t { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:16px; }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:22px 24px; margin-bottom:22px; }
    .card h3 { font-size:15px; font-weight:700; color:var(--green-dark); margin-bottom:14px; }
    .seg { display:flex; gap:8px; margin-bottom:16px; }
    .seg label { flex:1; }
    .seg input { position:absolute; opacity:0; }
    .seg span { display:block; text-align:center; padding:10px; border:1.5px solid var(--border); border-radius:8px; font-size:13px; font-weight:700; color:var(--muted); cursor:pointer; background:var(--green-pale); }
    .seg input:checked + span { background:var(--green); color:#fff; border-color:var(--green); }
    .field { margin-bottom:13px; }
    .field label { display:block; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .field input, .field select { width:100%; padding:10px 12px; font-size:14px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:7px; background:var(--green-pale); outline:none; }
    .field input:focus, .field select:focus { border-color:var(--green); box-shadow:0 0 0 3px rgba(26,122,74,0.13); background:#fff; }
    .field-row { display:grid; grid-template-columns:1fr 1fr; gap:13px; }
    .btn-submit { width:100%; padding:12px; background:var(--green); color:#fff; border:none; border-radius:8px; font-size:15px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }
    .alert { padding:11px 14px; border-radius:8px; font-size:13px; margin-bottom:14px; font-weight:500; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid var(--border); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.m { width:100%; border-collapse:collapse; font-size:13px; min-width:640px; }
    table.m th { background:var(--green); color:#fff; text-align:left; padding:11px 14px; font-size:11px; text-transform:uppercase; letter-spacing:0.4px; }
    table.m td { padding:10px 14px; border-bottom:1px solid var(--border); }
    table.m tr:nth-child(even) td { background:var(--green-pale); }
    .type-tag { font-size:11px; font-weight:800; padding:2px 9px; border-radius:10px; }
    .t-pdf { background:#FDE7E0; color:#C0392B; } .t-vid { background:#DDEBFF; color:#1B4F9C; }
    .lnk { color:var(--green); font-weight:600; text-decoration:none; font-size:12px; }
    .del { color:#C0392B; font-weight:600; background:none; border:none; cursor:pointer; font-size:12px; font-family:'Inter',sans-serif; }
    #pdfRow, #vidRow { display:none; }
    .empty { text-align:center; padding:36px; color:var(--muted); }
  </style>
</head>
<body>
<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="materials"/></jsp:include>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <h2 class="t">Learning Materials</h2>

  <% if (saved) { %><div class="alert ok">✓ Saved.</div><% } %>
  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <div class="card">
    <h3>Add Material</h3>
    <form action="<%= ctx %>/materials" method="post" enctype="multipart/form-data">
      <div class="seg">
        <label><input type="radio" name="type" value="PDF" checked onchange="toggleType()"><span>📄 Study Material (PDF)</span></label>
        <label><input type="radio" name="type" value="VIDEO" onchange="toggleType()"><span>▶️ E-Content (YouTube)</span></label>
      </div>
      <div class="field">
        <label>Title</label>
        <input type="text" name="title" placeholder="e.g. Physics - Kinematics Notes" required>
      </div>
      <div class="field-row">
        <div class="field">
          <label>Class</label>
          <select name="className">
            <option value="">All classes</option>
            <% if (classes != null) for (String c : classes) { %><option value="<%= esc(c) %>"><%= esc(c) %></option><% } %>
          </select>
        </div>
        <div class="field">
          <label>Subject</label>
          <input type="text" name="subject" placeholder="e.g. Physics">
        </div>
      </div>
      <div class="field" id="pdfRow" style="display:block;">
        <label>PDF File (max 25 MB)</label>
        <input type="file" name="pdf" accept="application/pdf">
      </div>
      <div class="field" id="vidRow">
        <label>YouTube Link</label>
        <input type="url" name="youtubeUrl" placeholder="https://www.youtube.com/watch?v=...">
      </div>
      <button type="submit" class="btn-submit">＋ Add Material</button>
    </form>
  </div>

  <div class="filter-bar">
    <input type="text" id="fSearch" placeholder="Search title or subject…" oninput="applyFilters()">
    <select data-filter data-col="1" onchange="applyFilters()">
      <option value="">All types</option>
      <option value="PDF">PDF</option>
      <option value="VIDEO">Video</option>
    </select>
    <span class="fcount"><b id="fCount"></b> shown</span>
  </div>
  <div class="table-wrap">
    <% if (materials == null || materials.isEmpty()) { %>
      <div class="empty">No materials added yet.</div>
    <% } else { %>
      <table class="m" id="fTable">
        <thead><tr><th>Title</th><th>Type</th><th>Class</th><th>Subject</th><th>Link</th><th></th></tr></thead>
        <tbody>
        <% for (Material m : materials) { boolean v="VIDEO".equals(m.getType()); %>
          <tr>
            <td style="font-weight:600;color:var(--green-dark)"><%= esc(m.getTitle()) %></td>
            <td><span class="type-tag <%= v?"t-vid":"t-pdf" %>"><%= v?"VIDEO":"PDF" %></span></td>
            <td><%= m.getClassName()==null||"null".equals(m.getClassName())||m.getClassName().isEmpty()?"All":esc(m.getClassName()) %></td>
            <td><%= esc(m.getSubject()) %></td>
            <td><% if (v) { %><a class="lnk" href="<%= esc(m.getYoutubeUrl()) %>" target="_blank">▶ Watch</a><% } else { %><a class="lnk" href="<%= ctx %>/<%= esc(m.getFilePath()) %>" target="_blank">📄 Open</a><% } %></td>
            <td>
              <form action="<%= ctx %>/materials" method="post" onsubmit="return confirm('Delete this material?');" style="margin:0;">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="materialId" value="<%= m.getMaterialId() %>">
                <button type="submit" class="del">Delete</button>
              </form>
            </td>
          </tr>
        <% } %>
        </tbody>
      </table>
    <% } %>
  </div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

<script>
  function toggleType(){
    var v = document.querySelector('input[name="type"]:checked').value === 'VIDEO';
    document.getElementById('pdfRow').style.display = v ? 'none' : 'block';
    document.getElementById('vidRow').style.display = v ? 'block' : 'none';
  }
  toggleType();
</script>
<script src="<%= ctx %>/js/filter.js"></script>
</body>
</html>
