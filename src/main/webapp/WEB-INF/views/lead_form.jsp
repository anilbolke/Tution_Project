<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.Inquiry, com.tution.model.User, com.tution.util.Dates" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    Inquiry lead = (Inquiry) request.getAttribute("lead");
    if (lead == null) lead = new Inquiry();
    String error = (String) request.getAttribute("error");

    @SuppressWarnings("unchecked") List<String> sources = (List<String>) request.getAttribute("sources");
    @SuppressWarnings("unchecked") List<String> courses = (List<String>) request.getAttribute("courses");
    @SuppressWarnings("unchecked") List<String> batches = (List<String>) request.getAttribute("batches");
    @SuppressWarnings("unchecked") Map<Integer,String> counsellors =
        (Map<Integer,String>) request.getAttribute("counsellors");
    @SuppressWarnings("unchecked") List<String> stages = (List<String>) request.getAttribute("stages");
    String subStagesJson = (String) request.getAttribute("subStagesJson");
    if (subStagesJson == null) subStagesJson = "{}";

    boolean editing = lead.getInquiryId() > 0;
    String  pageTitle = editing ? "Edit Enquiry" : "New Enquiry";
%>
<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String v(String s) { return s == null ? "" : esc(s); }
    private String sel(String current, String option) {
        return option.equals(current) ? " selected" : "";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title><%= pageTitle %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .form-head { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap;
      gap:12px; margin-bottom:18px; }
    .form-head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .back-link { font-size:13px; color:var(--muted); text-decoration:none; }
    .back-link:hover { color:var(--green-dark); }

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
    .fld input, .fld select, .fld textarea { padding:10px 12px; border:1.5px solid var(--border);
      border-radius:8px; font-size:13.5px; font-family:inherit; background:#fff; color:var(--text); }
    .fld input:focus, .fld select:focus, .fld textarea:focus { outline:none; border-color:var(--green); }
    .fld textarea { resize:vertical; min-height:70px; }

    .save-bar { display:flex; gap:12px; justify-content:flex-end; align-items:center; flex-wrap:wrap;
      background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:16px 20px; }
    .btn-primary { background:var(--green); color:#fff; border:none; border-radius:9px; padding:13px 32px;
      font-size:14.5px; font-weight:700; cursor:pointer; font-family:inherit; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); border-radius:9px; padding:12px 24px;
      font-size:14px; font-weight:600; color:var(--muted); text-decoration:none; font-family:inherit; }

    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px;
      border-radius:8px; font-size:13px; margin-bottom:18px; }

    /* Duplicate-enquiry warning, filled in by the AJAX check on mobile blur. */
    #dupBox { display:none; background:#FFF6E0; border:1.5px solid #F0D89A; color:#7A5200;
      border-radius:9px; padding:13px 15px; font-size:13px; margin-bottom:16px; }
    #dupBox b { color:#5C3E00; }
    #dupBox a { color:var(--green-dark); font-weight:700; }
    #dupBox ul { margin:8px 0 0 18px; }
    #dupBox li { margin-bottom:3px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="leads"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <div class="form-head">
    <h2><%= pageTitle %></h2>
    <a class="back-link" href="<%= editing ? ctx + "/lead?id=" + lead.getInquiryId() : ctx + "/inquiries" %>">← Back</a>
  </div>

  <% if (error != null) { %>
    <div class="alert error"><%= esc(error) %></div>
  <% } %>

  <div id="dupBox"></div>

  <form method="post" action="<%= ctx %>/lead" id="leadForm">
    <input type="hidden" name="action" value="<%= editing ? "update" : "create" %>">
    <input type="hidden" name="inquiryId" value="<%= lead.getInquiryId() %>">

    <!-- 1. Student details -->
    <div class="card">
      <h3>Student Details</h3>
      <div class="grid">
        <div class="fld">
          <label>Student Name <span class="req">*</span></label>
          <input type="text" name="fullName" required maxlength="100" value="<%= v(lead.getFullName()) %>">
        </div>
        <div class="fld">
          <label>Mobile Number <span class="req">*</span></label>
          <input type="tel" name="mobile" id="mobile" required pattern="[6-9][0-9]{9}" maxlength="10"
                 value="<%= v(lead.getMobile()) %>" placeholder="10-digit mobile">
        </div>
        <div class="fld">
          <label>Parent / Guardian Name</label>
          <input type="text" name="parentName" maxlength="100" value="<%= v(lead.getParentName()) %>">
        </div>
        <div class="fld">
          <label>Parent Mobile</label>
          <input type="tel" name="parentMobile" pattern="[6-9][0-9]{9}" maxlength="10"
                 value="<%= v(lead.getParentMobile()) %>">
        </div>
        <div class="fld">
          <label>Email ID</label>
          <input type="email" name="email" maxlength="120" value="<%= v(lead.getEmail()) %>">
        </div>
        <div class="fld">
          <label>Date of Birth</label>
          <input type="date" name="dob" value="<%= v(lead.getDob()) %>">
        </div>
        <div class="fld">
          <label>Gender</label>
          <select name="gender">
            <option value="">— Select —</option>
            <option<%= sel(lead.getGender(),"Male") %>>Male</option>
            <option<%= sel(lead.getGender(),"Female") %>>Female</option>
            <option<%= sel(lead.getGender(),"Other") %>>Other</option>
          </select>
        </div>
        <div class="fld">
          <label>City</label>
          <input type="text" name="city" maxlength="60" value="<%= v(lead.getCity()) %>">
        </div>
        <div class="fld wide">
          <label>Address</label>
          <input type="text" name="address" maxlength="255" value="<%= v(lead.getAddress()) %>">
        </div>
      </div>
    </div>

    <!-- 2. Academic details -->
    <div class="card">
      <h3>Academic Details</h3>
      <div class="grid">
        <div class="fld">
          <label>Current Class</label>
          <input type="text" name="currentClass" maxlength="40" value="<%= v(lead.getCurrentClass()) %>">
        </div>
        <div class="fld">
          <label>Previous Qualification</label>
          <input type="text" name="prevQualification" maxlength="80" value="<%= v(lead.getPrevQualification()) %>">
        </div>
        <div class="fld">
          <label>School / College Name</label>
          <input type="text" name="schoolName" maxlength="120" value="<%= v(lead.getSchoolName()) %>">
        </div>
        <div class="fld">
          <label>Board / University</label>
          <select name="board">
            <option value="">— Select —</option>
            <option<%= sel(lead.getBoard(),"Maharashtra HSC Board") %>>Maharashtra HSC Board</option>
            <option<%= sel(lead.getBoard(),"CBSE") %>>CBSE</option>
            <option<%= sel(lead.getBoard(),"ICSE") %>>ICSE</option>
            <option<%= sel(lead.getBoard(),"IB") %>>IB</option>
            <option<%= sel(lead.getBoard(),"Other") %>>Other</option>
          </select>
        </div>
        <div class="fld">
          <label>Percentage / Grade</label>
          <input type="text" name="percentage" maxlength="20" value="<%= v(lead.getPercentage()) %>">
        </div>
      </div>
    </div>

    <!-- 3. Course interest -->
    <div class="card">
      <h3>Course Interest</h3>
      <div class="grid">
        <div class="fld">
          <label>Course Name</label>
          <select name="courseName">
            <option value="">— Select —</option>
            <% if (courses != null) for (String c : courses) { %>
              <option<%= sel(lead.getCourseName(), c) %>><%= esc(c) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Batch Preference</label>
          <select name="batchPref">
            <option value="">— Any —</option>
            <% if (batches != null) for (String b : batches) { %>
              <option<%= sel(lead.getBatchPref(), b) %>><%= esc(b) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Learning Mode</label>
          <select name="learningMode">
            <option value="">— Select —</option>
            <option<%= sel(lead.getLearningMode(),"Online") %>>Online</option>
            <option<%= sel(lead.getLearningMode(),"Offline") %>>Offline</option>
            <option<%= sel(lead.getLearningMode(),"Hybrid") %>>Hybrid</option>
          </select>
        </div>
        <div class="fld">
          <label>Preferred Branch</label>
          <input type="text" name="branch" maxlength="60" value="<%= v(lead.getBranch()) %>">
        </div>
        <div class="fld">
          <label>Expected Joining Date</label>
          <input type="date" name="expectedJoinDate" value="<%= v(lead.getExpectedJoinDate()) %>">
        </div>
      </div>
    </div>

    <!-- 4. Enquiry details -->
    <div class="card">
      <h3>Enquiry Details</h3>
      <div class="grid">
        <div class="fld">
          <label>Lead Source</label>
          <select name="source">
            <option value="">— Select —</option>
            <% if (sources != null) for (String s : sources) { %>
              <option<%= sel(lead.getSource(), s) %>><%= esc(s) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Lead Stage</label>
          <select name="leadStage" class="lead-stage" data-sub="leadSubStage">
            <option value="">— Select —</option>
            <% if (stages != null) for (String s : stages) { %>
              <option<%= sel(lead.getLeadStage(), s) %>><%= esc(s) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Lead Sub Stage</label>
          <select name="leadSubStage" id="leadSubStage"
                  data-selected="<%= v(lead.getLeadSubStage()) %>">
            <option value="">— choose a stage first —</option>
          </select>
        </div>
        <div class="fld">
          <label>Assigned Counsellor</label>
          <select name="counsellorId"<%= user.isAdmin() ? "" : " disabled" %>>
            <option value="">— Unassigned —</option>
            <% if (counsellors != null) for (Map.Entry<Integer,String> e : counsellors.entrySet()) {
                 boolean s = lead.getCounsellorId() != null && lead.getCounsellorId().intValue() == e.getKey(); %>
              <option value="<%= e.getKey() %>"<%= s ? " selected" : "" %>><%= esc(e.getValue()) %></option>
            <% } %>
          </select>
        </div>
        <div class="fld">
          <label>Enquiry Status</label>
          <select name="status">
            <option value="NEW"<%= sel(lead.getStatus(),"NEW") %>>New</option>
            <option value="CONTACTED"<%= sel(lead.getStatus(),"CONTACTED") %>>Contacted</option>
            <option value="INTERESTED"<%= sel(lead.getStatus(),"INTERESTED") %>>Interested</option>
            <option value="DEMO_PENDING"<%= sel(lead.getStatus(),"DEMO_PENDING") %>>Counsellor Pending</option>
            <option value="DEMO_COMPLETED"<%= sel(lead.getStatus(),"DEMO_COMPLETED") %>>Counsellor Completed</option>
            <option value="FOLLOWUP_REQUIRED"<%= sel(lead.getStatus(),"FOLLOWUP_REQUIRED") %>>Follow-up Required</option>
            <option value="NOT_INTERESTED"<%= sel(lead.getStatus(),"NOT_INTERESTED") %>>Not Interested</option>
            <option value="LOST"<%= sel(lead.getStatus(),"LOST") %>>Lost</option>
          </select>
        </div>
        <div class="fld">
          <label>Next Follow-up Date &amp; Time</label>
          <input type="datetime-local" name="nextFollowupDate" value="<%= Dates.forInput(lead.getNextFollowupDate()) %>">
        </div>
      </div>
    </div>

    <!-- 5. Follow-up information -->
    <div class="card">
      <h3>Follow-up Information</h3>
      <div class="grid">
        <div class="fld wide">
          <label>Student Requirements</label>
          <textarea name="studentRequirements" maxlength="500"><%= v(lead.getStudentRequirements()) %></textarea>
        </div>
        <div class="fld wide">
          <label>Parent Feedback</label>
          <textarea name="parentFeedback" maxlength="500"><%= v(lead.getParentFeedback()) %></textarea>
        </div>
        <div class="fld wide">
          <label>Counsellor Remarks</label>
          <textarea name="counsellorRemarks" maxlength="500"><%= v(lead.getCounsellorRemarks()) %></textarea>
        </div>
        <div class="fld wide">
          <label>Enquiry Message / Notes</label>
          <textarea name="message" maxlength="500"><%= v(lead.getMessage()) %></textarea>
        </div>
      </div>
    </div>

    <div class="save-bar">
      <a class="btn-ghost" href="<%= editing ? ctx + "/lead?id=" + lead.getInquiryId() : ctx + "/inquiries" %>">Cancel</a>
      <button type="submit" class="btn-primary"><%= editing ? "Save Changes" : "Create Enquiry" %></button>
    </div>
  </form>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

<script>window.LEAD_SUB_STAGES = <%= subStagesJson %>;</script>
<script src="<%= ctx %>/js/leadstage.js"></script>

<script>
  // Duplicate-enquiry warning. Runs when the mobile field loses focus, so the
  // counsellor sees the existing enquiry BEFORE filling in the rest of the form.
  (function () {
    var ctx      = '<%= ctx %>';
    var excludeId= <%= lead.getInquiryId() %>;
    var input    = document.getElementById('mobile');
    var box      = document.getElementById('dupBox');
    if (!input || !box) return;

    input.addEventListener('blur', function () {
      var m = input.value.trim();
      box.style.display = 'none';
      if (!/^[6-9]\d{9}$/.test(m)) return;

      var xhr = new XMLHttpRequest();
      xhr.open('GET', ctx + '/lead?action=checkdup&mobile=' + encodeURIComponent(m)
                    + '&excludeId=' + excludeId, true);
      xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
      xhr.onload = function () {
        if (xhr.status !== 200) return;
        var data;
        try { data = JSON.parse(xhr.responseText); } catch (e) { return; }
        if (!data.duplicates || !data.duplicates.length) return;

        var html = '<b>⚠ An enquiry for this mobile number already exists.</b><ul>';
        data.duplicates.forEach(function (d) {
          html += '<li>' + escapeHtml(d.name) + ' — status <b>' + escapeHtml(d.status) + '</b>'
               +  (d.counsellor ? ' · counsellor ' + escapeHtml(d.counsellor) : '')
               +  ' &nbsp;<a href="' + ctx + '/lead?id=' + d.id + '">Open it →</a></li>';
        });
        html += '</ul>You can still create a new enquiry below if this is a different student.';
        box.innerHTML = html;
        box.style.display = 'block';
        box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      };
      xhr.send();
    });

    function escapeHtml(s) {
      return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
  })();
</script>
</body>
</html>
