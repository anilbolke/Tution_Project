<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.OnlineExam, com.tution.model.OnlineExamAttempt, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<OnlineExam> exams = (List<OnlineExam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<OnlineExam>();
    @SuppressWarnings("unchecked") TreeSet<String> classes = (TreeSet<String>) request.getAttribute("classes");
    if (classes == null) classes = new TreeSet<String>();
    @SuppressWarnings("unchecked") List<OnlineExamAttempt> attempts =
        (List<OnlineExamAttempt>) request.getAttribute("attempts");
    if (attempts == null) attempts = new ArrayList<OnlineExamAttempt>();

    Integer fExamId = (Integer) request.getAttribute("fExamId");
    String fClassName = (String) request.getAttribute("fClassName");
    Integer submittedCount = (Integer) request.getAttribute("submittedCount");
    if (submittedCount == null) submittedCount = 0;
    Double avgPct = (Double) request.getAttribute("avgPct");
    if (avgPct == null) avgPct = 0.0;
    String error = (String) request.getAttribute("error");

    Integer currentPage = (Integer) request.getAttribute("currentPage");
    if (currentPage == null) currentPage = 1;
    Integer totalPages = (Integer) request.getAttribute("totalPages");
    if (totalPages == null) totalPages = 1;
    Integer totalCount = (Integer) request.getAttribute("totalCount");
    if (totalCount == null) totalCount = attempts.size();

    // Carried onto the pager links so Next/Previous keep the active filters.
    StringBuilder fq = new StringBuilder();
    if (fExamId != null) fq.append("&examId=").append(fExamId);
    if (fClassName != null && !fClassName.isEmpty()) fq.append("&className=").append(java.net.URLEncoder.encode(fClassName, "UTF-8"));
    String filterQs = fq.toString();
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Online Exam Results – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .oer-wrap { max-width:1100px; margin:18px auto; padding:0 14px; }
  .oer-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:17px; margin-bottom:14px; }
  .oer-card h2 { margin:0 0 3px; font-size:16px; }
  .oer-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 13px; }
  .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  .row label { display:block; font-size:12px; color:#5a6b73; margin-bottom:4px; }
  .row select { padding:8px 10px; border:1px solid #cfd6da; border-radius:6px; font-size:14px; min-width:200px; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  table.el { width:100%; border-collapse:collapse; font-size:13px; }
  table.el th, table.el td { padding:8px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.el th { background:#f6f8f9; font-size:12px; color:#42555e; }
  .badge { font-size:11px; padding:2px 8px; border-radius:20px; font-weight:600; white-space:nowrap; }
  .b-on  { background:#d8efdf; color:#1b6b39; }
  .b-off { background:#f0f2f3; color:#69777e; }
  .muted { color:#8697a0; font-size:12px; }
  .scroll { overflow-x:auto; }
  .stat { display:inline-block; margin-right:22px; }
  .stat b { font-size:18px; color:#1b6b39; display:block; }
  .stat span { font-size:11.5px; color:#8697a0; text-transform:uppercase; letter-spacing:.3px; }
  .btn-mini { display:inline-block; background:none; border:1px solid #cfd6da; border-radius:6px;
              padding:5px 10px; font-size:12px; cursor:pointer; text-decoration:none; color:#42555e; }
  .btn-mini:hover { background:#f6f8f9; }
  .btn-mini.disabled { color:#c3cacd; cursor:default; pointer-events:none; }
  .pager { display:flex; align-items:center; justify-content:center; gap:16px; margin-top:14px; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="onlineresults"/>
</jsp:include>

<div class="oer-wrap">
  <% if (error != null) { %><div class="alert a-err"><%= esc(error) %></div><% } %>

  <div class="oer-card">
    <h2>Online exam results</h2>
    <p class="hint">Every student's attempt across every online exam, in one place. Filter by exam or by class.</p>

    <form method="get" action="<%= ctx %>/online-exam-results" class="row">
      <div>
        <label for="examId">Exam</label>
        <select name="examId" id="examId" onchange="this.form.submit()">
          <option value="">All exams</option>
          <% for (OnlineExam e : exams) {
               boolean sel = fExamId != null && fExamId.intValue() == e.getOnlineExamId(); %>
            <option value="<%= e.getOnlineExamId() %>"<%= sel ? " selected" : "" %>><%= esc(e.getTitle()) %> (<%= esc(e.getClassName()) %>)</option>
          <% } %>
        </select>
      </div>
      <div>
        <label for="className">Class</label>
        <select name="className" id="className" onchange="this.form.submit()">
          <option value="">All classes</option>
          <% for (String c : classes) {
               boolean sel = c.equals(fClassName); %>
            <option value="<%= esc(c) %>"<%= sel ? " selected" : "" %>><%= esc(c) %></option>
          <% } %>
        </select>
      </div>
      <% if (fExamId != null || (fClassName != null && !fClassName.isEmpty())) { %>
        <div><a class="btn-mini" href="<%= ctx %>/online-exam-results" style="padding:9px 14px;">Clear filters</a></div>
      <% } %>
    </form>
  </div>

  <div class="oer-card">
    <div style="margin-bottom:14px;">
      <span class="stat"><b><%= totalCount %></b><span>Total attempts</span></span>
      <span class="stat"><b><%= submittedCount %></b><span>Submitted</span></span>
      <span class="stat"><b><%= avgPct %>%</b><span>Average score</span></span>
    </div>

    <% if (attempts.isEmpty()) { %>
      <p class="hint">No attempts match these filters.</p>
    <% } else { %>
      <div class="scroll">
        <table class="el">
          <tr><th>Exam</th><th>Class</th><th>Student</th><th>Admission No.</th><th>Score</th><th>%</th><th>Status</th><th>Submitted</th></tr>
          <% for (OnlineExamAttempt a : attempts) { %>
          <tr>
            <td><%= esc(a.getExamTitle()) %></td>
            <td><%= esc(a.getClassName()) %></td>
            <td><%= esc(a.getStudentName()) %></td>
            <td><%= esc(a.getAdmissionNo()) %></td>
            <td><%= a.isSubmitted() ? (a.getScore() + " / " + a.getTotalMarks()) : "—" %></td>
            <td><%= a.isSubmitted() ? (a.percentage() + "%") : "—" %></td>
            <td><span class="badge <%= a.isSubmitted() ? "b-on" : "b-off" %>"><%= a.isSubmitted() ? "Submitted" : "In progress" %></span></td>
            <td><%= a.getSubmittedAt() == null ? "—" : esc(a.getSubmittedAt()) %></td>
          </tr>
          <% } %>
        </table>
      </div>
      <% if (totalPages > 1) { %>
        <div class="pager">
          <% if (currentPage > 1) { %>
            <a class="btn-mini" href="<%= ctx %>/online-exam-results?page=<%= currentPage - 1 %><%= filterQs %>">&larr; Previous</a>
          <% } else { %>
            <span class="btn-mini disabled">&larr; Previous</span>
          <% } %>
          <span class="muted">Page <%= currentPage %> of <%= totalPages %> &middot; <%= totalCount %> attempt<%= totalCount == 1 ? "" : "s" %></span>
          <% if (currentPage < totalPages) { %>
            <a class="btn-mini" href="<%= ctx %>/online-exam-results?page=<%= currentPage + 1 %><%= filterQs %>">Next &rarr;</a>
          <% } else { %>
            <span class="btn-mini disabled">Next &rarr;</span>
          <% } %>
        </div>
      <% } %>
    <% } %>
  </div>
</div>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
%>
</body>
</html>
