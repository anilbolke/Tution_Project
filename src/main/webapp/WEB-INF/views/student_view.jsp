<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.User,
                 com.tution.model.Payment, com.tution.model.Inquiry" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    Student s = (Student) request.getAttribute("student");
    @SuppressWarnings("unchecked")
    List<Payment> payments = (List<Payment>) request.getAttribute("payments");
    Integer paidTotal = (Integer) request.getAttribute("paidTotal");
    Inquiry sourceLead = (Inquiry) request.getAttribute("sourceLead");
    String error = (String) request.getAttribute("error");
    String msg   = request.getParameter("msg");
    int payCount = (payments == null) ? 0 : payments.size();
%>
<%!
    private String esc(String x) {
        if (x == null) return "";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String d(String x) { return (x == null || x.isEmpty()) ? "—" : esc(x); }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title><%= s == null ? "Student" : esc(s.getFullName()) %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .stu-head { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:22px 24px; margin-bottom:18px; display:flex; justify-content:space-between;
      align-items:flex-start; gap:18px; flex-wrap:wrap; }
    .id-block { display:flex; gap:16px; align-items:center; }
    .avatar { width:72px; height:72px; border-radius:50%; object-fit:cover; border:3px solid var(--green-light); }
    .avatar-ph { width:72px; height:72px; border-radius:50%; background:var(--green-light);
      color:var(--green-dark); display:flex; align-items:center; justify-content:center;
      font-size:26px; font-weight:800; }
    .stu-head h2 { font-family:'Playfair Display',serif; font-size:25px; color:var(--green-dark); }
    .chips { display:flex; gap:8px; flex-wrap:wrap; margin-top:8px; }
    .badge { display:inline-block; font-size:10.5px; font-weight:800; padding:4px 11px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; }
    .b-adm { background:var(--green-light); color:var(--green-dark); }
    .b-cls { background:#DDEBFF; color:#1B4F9C; }
    .b-off { background:#EEE; color:#666; }
    .b-on  { background:var(--green-light); color:var(--success); }

    .head-actions { display:flex; gap:9px; flex-wrap:wrap; }
    .btn { display:inline-block; text-decoration:none; font-size:13px; font-weight:700; padding:10px 18px;
      border-radius:8px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); color:var(--muted); }
    .btn-danger { background:none; border:1.5px solid #E9B4AE; color:#C0392B; }

    .cols { display:grid; grid-template-columns:2fr 1fr; gap:18px; align-items:start; }
    @media(max-width:900px){ .cols { grid-template-columns:1fr; } }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:20px 22px; margin-bottom:18px; }
    .card h3 { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:var(--green-dark);
      padding-bottom:9px; border-bottom:2px solid var(--green-light); margin-bottom:14px; }
    .kv { display:grid; grid-template-columns:repeat(2,1fr); gap:12px 20px; }
    @media(max-width:620px){ .kv { grid-template-columns:1fr; } }
    .kv .k { font-size:10.5px; text-transform:uppercase; letter-spacing:0.3px; color:var(--muted);
      font-weight:700; margin-bottom:2px; }
    .kv .val { font-size:13.5px; }
    .kv .full { grid-column:1 / -1; }

    table.mini { width:100%; border-collapse:collapse; font-size:12.5px; }
    table.mini th { text-align:left; padding:7px 8px; font-size:10.5px; text-transform:uppercase;
      letter-spacing:0.3px; color:var(--muted); border-bottom:1.5px solid var(--border); }
    table.mini td { padding:8px; border-bottom:1px solid var(--border); }
    table.mini tr:last-child td { border-bottom:none; }

    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid #BFE3CE; }
    .soon { font-size:12.5px; color:var(--muted); font-style:italic; }
    .doclink { font-size:13px; color:var(--green-dark); font-weight:700; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="students"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>

  <% if (s == null) { %>
    <div class="card"><p>Student not found. <a href="<%= ctx %>/students">Back to the list →</a></p></div>
  <% } else { %>

    <% if (msg != null) { %>
      <div class="alert <%= "error".equals(msg) ? "error" : "ok" %>"><%=
          "saved".equals(msg)       ? "Changes saved." :
          "deactivated".equals(msg) ? "Student marked inactive." :
          "activated".equals(msg)   ? "Student re-activated." :
          "error".equals(msg)       ? "Something went wrong. Please try again." : "Done." %></div>
    <% } %>

    <div class="stu-head">
      <div class="id-block">
        <% if (s.getPhotoPath() != null && !s.getPhotoPath().isEmpty()) { %>
          <img class="avatar" src="<%= ctx %>/<%= esc(s.getPhotoPath()) %>" alt="">
        <% } else {
             String n = s.getFullName() == null || s.getFullName().isEmpty() ? "?" : s.getFullName().substring(0,1); %>
          <div class="avatar-ph"><%= esc(n.toUpperCase()) %></div>
        <% } %>
        <div>
          <h2><%= esc(s.getFullName()) %></h2>
          <div class="chips">
            <span class="badge b-adm"><%= esc(s.getAdmissionNo()) %></span>
            <% if (s.getClassName() != null) { %><span class="badge b-cls"><%= esc(s.getClassName()) %></span><% } %>
            <span class="badge <%= s.isActive() ? "b-on" : "b-off" %>"><%= s.isActive() ? "Active" : "Inactive" %></span>
          </div>
        </div>
      </div>
      <div class="head-actions">
        <a class="btn btn-ghost" href="<%= ctx %>/students">← All Students</a>
        <a class="btn btn-ghost" href="<%= ctx %>/student?id=<%= s.getStudentId() %>&edit=1">✎ Edit</a>
        <% if (user.canSeeFees()) { %>
          <a class="btn btn-primary" href="<%= ctx %>/collect?studentId=<%= s.getStudentId() %>">Collect Fee →</a>
        <% } %>
      </div>
    </div>

    <div class="cols">
      <div>
        <div class="card">
          <h3>Personal Details</h3>
          <div class="kv">
            <div><div class="k">Date of Birth</div><div class="val"><%= d(s.getDob()) %></div></div>
            <div><div class="k">Gender</div><div class="val"><%= d(s.getGender()) %></div></div>
            <div><div class="k">Student Mobile</div><div class="val"><%= d(s.getStudentMobile()) %></div></div>
            <div><div class="k">Alternate Mobile</div><div class="val"><%= d(s.getAltMobile()) %></div></div>
            <div><div class="k">Email</div><div class="val"><%= d(s.getStudentEmail()) %></div></div>
            <div><div class="k">Parent / Guardian</div><div class="val"><%= d(s.getParentName()) %></div></div>
            <div><div class="k">Parent Mobile</div><div class="val"><%= d(s.getParentMobile()) %></div></div>
            <div class="full"><div class="k">Address</div><div class="val"><%= d(s.getAddress()) %></div></div>
          </div>
        </div>

        <div class="card">
          <h3>Academic &amp; Batch</h3>
          <div class="kv">
            <div><div class="k">Class</div><div class="val"><%= d(s.getClassName()) %></div></div>
            <div><div class="k">Board</div><div class="val"><%= d(s.getBoard()) %></div></div>
            <div><div class="k">Previous School</div><div class="val"><%= d(s.getPrevSchool()) %></div></div>
            <div><div class="k">10th % / CGPA</div><div class="val"><%= d(s.getPrevMarks()) %></div></div>
            <div><div class="k">Batch</div><div class="val"><%= d(s.getBatchName()) %></div></div>
            <div><div class="k">Branch</div><div class="val"><%= d(s.getBranch()) %></div></div>
          </div>
        </div>

        <%-- A teacher has no business seeing what a family has paid. --%>
        <% if (user.canSeeFees()) { %>
        <div class="card">
          <h3>Payment History <span style="font-weight:400;color:var(--muted);">(<%= payCount %>)</span></h3>
          <% if (payCount == 0) { %>
            <p class="soon">No payment recorded yet.</p>
          <% } else { %>
            <table class="mini">
              <thead><tr><th>Receipt</th><th>Date</th><th>Amount</th><th>Mode</th><th></th></tr></thead>
              <tbody>
              <% for (Payment p : payments) { %>
                <tr>
                  <td><%= esc(p.getReceiptNo()) %></td>
                  <td><%= esc(p.getPaymentDate()) %></td>
                  <td>Rs. <%= p.getAmount() %></td>
                  <td><%= esc(p.getPaymentMode()) %></td>
                  <td><a class="doclink" href="<%= ctx %>/receipt?paymentId=<%= p.getPaymentId() %>">View →</a></td>
                </tr>
              <% } %>
              </tbody>
            </table>
            <% if (paidTotal != null) { %>
              <p style="margin-top:10px;font-size:13px;"><b>Total paid: Rs. <%= paidTotal %></b></p>
            <% } %>
          <% } %>
        </div>
        <% } %>
      </div>

      <div>
        <div class="card">
          <h3>Admission</h3>
          <div class="kv" style="grid-template-columns:1fr;">
            <div><div class="k">Admission No</div><div class="val"><%= esc(s.getAdmissionNo()) %></div></div>
            <div><div class="k">Admitted On</div><div class="val"><%= d(s.getCreatedAt()) %></div></div>
            <div><div class="k">Fee Plan</div><div class="val"><%= d(s.getFeeSlab()) %></div></div>
            <div><div class="k">Counsellor</div><div class="val"><%= d(s.getCounsellorName()) %></div></div>
          </div>
        </div>

        <%-- The link back to the lead this admission came from: it makes the
             conversion traceable in both directions. --%>
        <% if (sourceLead != null && user.canSeeSales()) { %>
        <div class="card">
          <h3>Source Enquiry</h3>
          <div class="kv" style="grid-template-columns:1fr;">
            <div><div class="k">Lead Source</div><div class="val"><%= d(sourceLead.getSource()) %></div></div>
            <div><div class="k">Enquiry Date</div><div class="val"><%= d(sourceLead.getCreatedAt()) %></div></div>
          </div>
          <p style="margin-top:12px;">
            <a class="doclink" href="<%= ctx %>/lead?id=<%= sourceLead.getInquiryId() %>">Open the enquiry &amp; its follow-up history →</a>
          </p>
        </div>
        <% } %>

        <div class="card">
          <h3>Documents</h3>
          <p style="font-size:13px;margin-bottom:8px;">
            Photo:
            <% if (s.getPhotoPath() != null && !s.getPhotoPath().isEmpty()) { %>
              <a class="doclink" href="<%= ctx %>/<%= esc(s.getPhotoPath()) %>" target="_blank">View</a>
            <% } else { %><span class="soon">not uploaded</span><% } %>
          </p>
          <p style="font-size:13px;">
            ID Proof:
            <% if (s.getIdProofPath() != null && !s.getIdProofPath().isEmpty()) { %>
              <a class="doclink" href="<%= ctx %>/<%= esc(s.getIdProofPath()) %>" target="_blank">View</a>
            <% } else { %><span class="soon">not uploaded</span><% } %>
          </p>
        </div>

        <% if (user.isAdmin()) { %>
        <div class="card">
          <h3>Status</h3>
          <form method="post" action="<%= ctx %>/student"
                onsubmit="return confirm('<%= s.isActive() ? "Mark this student inactive?" : "Re-activate this student?" %>');">
            <input type="hidden" name="studentId" value="<%= s.getStudentId() %>">
            <input type="hidden" name="action" value="<%= s.isActive() ? "deactivate" : "activate" %>">
            <p style="font-size:12.5px;color:var(--muted);margin-bottom:10px;">
              <%= s.isActive()
                  ? "Marking a student inactive keeps all their records but takes them out of day-to-day lists."
                  : "This student is currently inactive." %>
            </p>
            <button type="submit" class="btn <%= s.isActive() ? "btn-danger" : "btn-primary" %>" style="width:100%;">
              <%= s.isActive() ? "Mark Inactive" : "Re-activate" %>
            </button>
          </form>
        </div>
        <% } %>
      </div>
    </div>

  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="students"/></jsp:include>

</body>
</html>
