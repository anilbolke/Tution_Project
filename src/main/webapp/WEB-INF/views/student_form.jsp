<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.Student, com.tution.model.User,
                 com.tution.model.FeeSlab" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    Student s = (Student) request.getAttribute("student");
    if (s == null) { response.sendRedirect(ctx + "/students"); return; }
    String error = (String) request.getAttribute("error");

    @SuppressWarnings("unchecked") List<String> courses = (List<String>) request.getAttribute("courses");
    @SuppressWarnings("unchecked") List<String> batches = (List<String>) request.getAttribute("batches");
    @SuppressWarnings("unchecked") Map<Integer,String> counsellors =
        (Map<Integer,String>) request.getAttribute("counsellors");
    @SuppressWarnings("unchecked") Map<String,FeeSlab> slabs =
        (Map<String,FeeSlab>) request.getAttribute("slabs");
%>
<%!
    private String esc(String x) {
        if (x == null) return "";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String v(String x) { return x == null ? "" : esc(x); }
    private String sel(String current, String option) {
        return option.equals(current) ? " selected" : "";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Edit <%= esc(s.getFullName()) %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .form-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap;
      gap:12px; margin-bottom:18px; }
    .form-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .back-link { font-size:13px; color:var(--muted); text-decoration:none; }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:22px 24px; margin-bottom:18px; }
    .card > h3 { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:var(--green-dark);
      padding-bottom:9px; border-bottom:2px solid var(--green-light); margin-bottom:16px; }
    .grid { display:grid; grid-template-columns:repeat(3,1fr); gap:14px 18px; }
    @media(max-width:900px){ .grid { grid-template-columns:repeat(2,1fr); } }
    @media(max-width:620px){ .grid { grid-template-columns:1fr; } }
    .fld { display:flex; flex-direction:column; }
    .fld.wide { grid-column:1 / -1; }
    .fld label { font-size:11.5px; font-weight:700; color:var(--text); text-transform:uppercase;
      letter-spacing:0.3px; margin-bottom:5px; }
    .fld label .req { color:#C0392B; }
    .fld input, .fld select { padding:10px 12px; border:1.5px solid var(--border);
      border-radius:8px; font-size:13.5px; font-family:inherit; background:#fff; }
    .fld input:focus, .fld select:focus { outline:none; border-color:var(--green); }
    .fld .hint { font-size:11.5px; color:var(--muted); margin-top:4px; }
    .locked { background:#F4F6F5 !important; color:var(--muted); }
    .save-bar { display:flex; gap:12px; justify-content:flex-end; align-items:center; flex-wrap:wrap;
      background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 20px; }
    .btn-primary { background:var(--green); color:#fff; border:none; border-radius:9px; padding:13px 32px;
      font-size:14.5px; font-weight:700; cursor:pointer; font-family:inherit; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); border-radius:9px; padding:12px 24px;
      font-size:14px; font-weight:600; color:var(--muted); text-decoration:none; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px;
      border-radius:8px; font-size:13px; margin-bottom:18px; }
    .cur { font-size:12px; color:var(--muted); margin-top:5px; }
    .cur a { color:var(--green-dark); font-weight:700; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="students"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="form-head">
    <h2>Edit Student</h2>
    <a class="back-link" href="<%= ctx %>/student?id=<%= s.getStudentId() %>">← Back to profile</a>
  </div>

  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <form method="post" action="<%= ctx %>/student" enctype="multipart/form-data">
    <input type="hidden" name="action" value="update">
    <input type="hidden" name="studentId" value="<%= s.getStudentId() %>">

    <div class="card">
      <h3>Personal Details</h3>
      <div class="grid">
        <div class="fld">
          <label>Admission Number</label>
          <input type="text" class="locked" value="<%= esc(s.getAdmissionNo()) %>" disabled>
          <span class="hint">Fixed — it is printed on receipts and is the student's portal login.</span>
        </div>
        <div class="fld">
          <label>Full Name <span class="req">*</span></label>
          <input type="text" name="fullName" required maxlength="100" value="<%= v(s.getFullName()) %>">
        </div>
        <div class="fld">
          <label>Date of Birth</label>
          <input type="date" name="dob" value="<%= v(s.getDob()) %>">
        </div>
        <div class="fld">
          <label>Gender</label>
          <select name="gender">
            <option value="">— Select —</option>
            <option<%= sel(s.getGender(),"Male") %>>Male</option>
            <option<%= sel(s.getGender(),"Female") %>>Female</option>
            <option<%= sel(s.getGender(),"Other") %>>Other</option>
          </select>
        </div>
        <div class="fld">
          <label>Student Mobile <span class="req">*</span></label>
          <input type="tel" name="studentMobile" required pattern="[6-9][0-9]{9}" maxlength="10"
                 value="<%= v(s.getStudentMobile()) %>">
          <span class="hint">Also the student-portal password.</span>
        </div>
        <div class="fld">
          <label>Alternate Mobile</label>
          <input type="tel" name="altMobile" pattern="[6-9][0-9]{9}" maxlength="10"
                 value="<%= v(s.getAltMobile()) %>">
        </div>
        <div class="fld">
          <label>Email</label>
          <input type="email" name="studentEmail" maxlength="120" value="<%= v(s.getStudentEmail()) %>">
        </div>
        <div class="fld">
          <label>Parent / Guardian Name</label>
          <input type="text" name="parentName" maxlength="100" value="<%= v(s.getParentName()) %>">
        </div>
        <div class="fld">
          <label>Parent Mobile</label>
          <input type="tel" name="parentMobile" pattern="[6-9][0-9]{9}" maxlength="10"
                 value="<%= v(s.getParentMobile()) %>">
        </div>
        <div class="fld wide">
          <label>Address</label>
          <input type="text" name="address" maxlength="255" value="<%= v(s.getAddress()) %>">
        </div>
      </div>
    </div>

    <div class="card">
      <h3>Academic &amp; Batch</h3>
      <div class="grid">
        <div class="fld">
          <label>Class</label>
          <select name="className">
            <option value="">— Select —</option>
            <% if (courses != null) for (String c : courses) { %>
              <option<%= sel(s.getClassName(), c) %>><%= esc(c) %></option>
            <% } %>
            <%-- keep an out-of-master value visible rather than silently dropping it --%>
            <% if (s.getClassName() != null && !s.getClassName().isEmpty()
                   && (courses == null || !courses.contains(s.getClassName()))) { %>
              <option selected><%= esc(s.getClassName()) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Board</label>
          <select name="board">
            <option value="">— Select —</option>
            <option<%= sel(s.getBoard(),"Maharashtra HSC Board") %>>Maharashtra HSC Board</option>
            <option<%= sel(s.getBoard(),"CBSE") %>>CBSE</option>
            <option<%= sel(s.getBoard(),"ICSE") %>>ICSE</option>
            <option<%= sel(s.getBoard(),"IB") %>>IB</option>
            <option<%= sel(s.getBoard(),"Other") %>>Other</option>
          </select>
        </div>
        <div class="fld">
          <label>Previous School</label>
          <input type="text" name="prevSchool" maxlength="120" value="<%= v(s.getPrevSchool()) %>">
        </div>
        <div class="fld">
          <label>10th % / CGPA</label>
          <input type="text" name="prevMarks" maxlength="20" value="<%= v(s.getPrevMarks()) %>">
        </div>
        <div class="fld">
          <label>Batch</label>
          <select name="batchName">
            <option value="">Not allocated</option>
            <% if (batches != null) for (String b : batches) { %>
              <option<%= sel(s.getBatchName(), b) %>><%= esc(b) %></option>
            <% } %>
            <% if (s.getBatchName() != null && !s.getBatchName().isEmpty()
                   && (batches == null || !batches.contains(s.getBatchName()))) { %>
              <option selected><%= esc(s.getBatchName()) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Branch</label>
          <input type="text" name="branch" maxlength="60" value="<%= v(s.getBranch()) %>">
        </div>
      </div>
    </div>

    <div class="card">
      <h3>Fee Plan &amp; Ownership</h3>
      <div class="grid">
        <div class="fld">
          <label>Fee Plan</label>
          <select name="feeSlab">
            <option value="">— Select —</option>
            <% if (slabs != null) for (Map.Entry<String,FeeSlab> e : slabs.entrySet()) { %>
              <option value="<%= esc(e.getKey()) %>"<%= sel(s.getFeeSlab(), e.getKey()) %>>
                <%= esc(e.getValue().getLabel()) %> — Rs. <%= e.getValue().getPerMonth() %>/mo
              </option>
            <% } %>
          </select>
          <span class="hint">Changing the plan changes what this student is billed.</span>
        </div>
        <div class="fld">
          <label>Counsellor</label>
          <select name="counsellorId"<%= user.isAdmin() ? "" : " disabled" %>>
            <option value="">— Unassigned —</option>
            <% if (counsellors != null) for (Map.Entry<Integer,String> e : counsellors.entrySet()) {
                 boolean on = s.getCounsellorId() != null && s.getCounsellorId().intValue() == e.getKey(); %>
              <option value="<%= e.getKey() %>"<%= on ? " selected" : "" %>><%= esc(e.getValue()) %></option>
            <% } %>
          </select>
          <% if (!user.isAdmin()) { %><span class="hint">Only an administrator can reassign a student.</span><% } %>
        </div>
      </div>
    </div>

    <div class="card">
      <h3>Documents</h3>
      <div class="grid">
        <div class="fld">
          <label>Replace Photo</label>
          <input type="file" name="photo" accept="image/*">
          <div class="cur">
            <% if (s.getPhotoPath() != null && !s.getPhotoPath().isEmpty()) { %>
              Current: <a href="<%= ctx %>/<%= esc(s.getPhotoPath()) %>" target="_blank">view</a> — leave empty to keep it.
            <% } else { %>None uploaded yet.<% } %>
          </div>
        </div>
        <div class="fld">
          <label>Replace ID Proof</label>
          <input type="file" name="idProof" accept="image/*,application/pdf">
          <div class="cur">
            <% if (s.getIdProofPath() != null && !s.getIdProofPath().isEmpty()) { %>
              Current: <a href="<%= ctx %>/<%= esc(s.getIdProofPath()) %>" target="_blank">view</a> — leave empty to keep it.
            <% } else { %>None uploaded yet.<% } %>
          </div>
        </div>
      </div>
    </div>

    <div class="save-bar">
      <a class="btn-ghost" href="<%= ctx %>/student?id=<%= s.getStudentId() %>">Cancel</a>
      <button type="submit" class="btn-primary">Save Changes</button>
    </div>
  </form>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="students"/></jsp:include>

</body>
</html>
