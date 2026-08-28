<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.Exam" %>
<%@ page import="com.tution.model.ImportRow" %>
<%@ page import="com.tution.servlet.ExamImportServlet" %>
<%@ page import="com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<Exam> exams = (List<Exam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<Exam>();
    ExamImportServlet.Preview pv = (ExamImportServlet.Preview) request.getAttribute("preview");
    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flash");
    @SuppressWarnings("unchecked")
    List<String> flashNotes = (List<String>) session.getAttribute("flashNotes");
    session.removeAttribute("flash");
    session.removeAttribute("flashNotes");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Import Students – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .imp-wrap { max-width: 1150px; margin: 18px auto; padding: 0 14px; }
  .imp-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:18px; margin-bottom:16px; }
  .imp-card h2 { margin:0 0 4px; font-size:17px; }
  .imp-hint { color:#5a6b73; font-size:13px; margin:0 0 14px; }
  .imp-row { display:flex; gap:14px; flex-wrap:wrap; align-items:flex-end; }
  .imp-row label { display:block; font-size:12px; color:#5a6b73; margin-bottom:4px; }
  .imp-row input, .imp-row select { padding:8px 10px; border:1px solid #cfd6da; border-radius:6px; font-size:14px; }
  .tally { display:flex; gap:10px; flex-wrap:wrap; margin:0 0 14px; }
  .tally div { border-radius:8px; padding:10px 14px; min-width:110px; }
  .t-new  { background:#e8f5ec; border:1px solid #b7dfc4; }
  .t-dup  { background:#fff6e3; border:1px solid #f0d9a8; }
  .t-rej  { background:#fdeaea; border:1px solid #f2bcbc; }
  .tally b { display:block; font-size:22px; line-height:1.1; }
  .tally span { font-size:12px; color:#42555e; }
  table.imp { width:100%; border-collapse:collapse; font-size:13px; }
  table.imp th, table.imp td { padding:7px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.imp th { background:#f6f8f9; font-weight:600; font-size:12px; color:#42555e; }
  tr.r-dup { background:#fffdf5; }
  tr.r-rej { background:#fff7f7; }
  .pill { font-size:11px; padding:2px 8px; border-radius:20px; font-weight:600; }
  .p-new { background:#d8efdf; color:#1b6b39; }
  .p-dup { background:#fbecc8; color:#7a5510; }
  .p-rej { background:#f7d4d4; color:#8c2020; }
  .why { color:#8c2020; font-size:12px; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:14px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }
  .a-warn{ background:#fff6e3; border:1px solid #f0d9a8; color:#7a5510; }
  .actions { display:flex; gap:10px; align-items:center; margin-top:16px; flex-wrap:wrap; }
  .scroll { overflow-x:auto; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="exams"/>
</jsp:include>

<div class="imp-wrap">

  <% if (error != null) { %><div class="alert a-err"><%= error %></div><% } %>
  <% if (flash != null) { %>
    <div class="alert a-ok"><%= flash %></div>
    <% if (flashNotes != null) { for (String n : flashNotes) { %>
      <div class="alert a-warn"><%= n %></div>
    <% } } %>
  <% } %>

  <% if (pv == null) { %>
    <div class="imp-card">
      <h2>Import school student list</h2>
      <p class="imp-hint">
        Upload the school's filled copy of the lead template (.xlsx). Nothing is saved yet &mdash;
        you will see exactly what will happen and can confirm or cancel.
        Roll numbers are generated on confirm.
      </p>
      <form method="post" action="<%= ctx %>/exam-import" enctype="multipart/form-data">
        <div class="imp-row">
          <div>
            <label for="examId">Exam these students are sitting</label>
            <select name="examId" id="examId" required>
              <option value="">-- choose --</option>
              <% for (Exam e : exams) { %>
                <option value="<%= e.getExamId() %>">
                  <%= e.getExamName() %> (<%= e.getExamType() %><%
                     if (e.getRollBlockFrom() > 0) { %>, <%= e.rollsRemaining() %> rolls left<% } %>)
                </option>
              <% } %>
            </select>
          </div>
          <div>
            <label for="schoolName">School (used when a row leaves it blank)</label>
            <input type="text" name="schoolName" id="schoolName" size="30" placeholder="optional">
          </div>
          <div>
            <label for="file">Student list (.xlsx)</label>
            <input type="file" name="file" id="file" accept=".xlsx" required>
          </div>
          <div><button class="btn" type="submit">Check file</button></div>
        </div>
      </form>
      <% if (exams.isEmpty()) { %>
        <div class="alert a-warn" style="margin-top:14px;">
          No scholarship exams exist yet. Create a HAMSE / HACKSE / HAT exam and give it a
          roll-number block before importing students.
        </div>
      <% } %>
    </div>

  <% } else { %>
    <div class="imp-card">
      <h2>Check before importing</h2>
      <p class="imp-hint">
        <b><%= pv.fileName %></b> &rarr; <b><%= pv.examName %></b>
        <%= pv.schoolName == null || pv.schoolName.isEmpty() ? "" : " &middot; " + pv.schoolName %>
        &middot; <%= pv.total() %> row(s) read. <b>Nothing has been saved yet.</b>
      </p>

      <div class="tally">
        <div class="t-new"><b><%= pv.newCount %></b><span>will be added</span></div>
        <div class="t-dup"><b><%= pv.duplicateCount %></b><span>already known</span></div>
        <div class="t-rej"><b><%= pv.rejectedCount %></b><span>rejected</span></div>
      </div>

      <% if (pv.duplicateCount > 0) { %>
        <div class="alert a-warn">
          Rows marked <b>already known</b> match a lead we already hold. They will not create a second
          person &mdash; the existing lead is registered for this exam and gets a roll number.
        </div>
      <% } %>
      <% if (!pv.unknownHeaders.isEmpty()) { %>
        <div class="alert a-warn">
          Columns in the file that this system does not use, and will ignore:
          <b><%= String.join(", ", pv.unknownHeaders) %></b>
        </div>
      <% } %>

      <div class="scroll">
        <table class="imp">
          <tr>
            <th>Row</th><th>Status</th><th>Name</th><th>Contact</th><th>School</th>
            <th>Class</th><th>Course</th><th>Centre</th><th>Note</th>
          </tr>
          <% for (ImportRow r : pv.rows) {
               String cls = r.verdict == ImportRow.Verdict.REJECTED ? "r-rej"
                          : r.verdict == ImportRow.Verdict.DUPLICATE ? "r-dup" : "";
               String pill = r.verdict == ImportRow.Verdict.REJECTED ? "p-rej"
                          : r.verdict == ImportRow.Verdict.DUPLICATE ? "p-dup" : "p-new";
               String label = r.verdict == ImportRow.Verdict.REJECTED ? "Rejected"
                          : r.verdict == ImportRow.Verdict.DUPLICATE ? "Already known" : "New";
          %>
            <tr class="<%= cls %>">
              <td><%= r.rowNo %></td>
              <td><span class="pill <%= pill %>"><%= label %></span></td>
              <td><%= esc(r.getName()) %></td>
              <td><%= esc(r.getMobile()) %></td>
              <td><%= esc(r.getSchool()) %></td>
              <td><%= esc(r.get("current_class")) %></td>
              <td><%= esc(r.get("course_interest")) %></td>
              <td><%= esc(r.get("preferred_centre")) %></td>
              <td>
                <% if (!r.problems.isEmpty()) { %>
                  <span class="why"><%= esc(r.problemText()) %></span>
                <% } else if (r.existingMatchedOn != null) { %>
                  matched on <%= esc(r.existingMatchedOn) %>
                <% } %>
              </td>
            </tr>
          <% } %>
        </table>
      </div>

      <div class="actions">
        <form method="post" action="<%= ctx %>/exam-import" style="margin:0;">
          <input type="hidden" name="action" value="commit">
          <button class="btn" type="submit"
            <%= (pv.newCount + pv.duplicateCount) == 0 ? "disabled" : "" %>>
            Import <%= pv.newCount + pv.duplicateCount %> student(s) &amp; generate roll numbers
          </button>
        </form>
        <form method="post" action="<%= ctx %>/exam-import" style="margin:0;">
          <input type="hidden" name="action" value="cancel">
          <button class="btn btn-light" type="submit">Cancel</button>
        </form>
        <% if (pv.rejectedCount > 0) { %>
          <a class="btn btn-light" href="<%= ctx %>/exam-import?download=rejected">
            Download <%= pv.rejectedCount %> rejected row(s)
          </a>
        <% } %>
      </div>
    </div>
  <% } %>
</div>

<%!
    /** Escapes user/file-supplied text before it lands in the page. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
%>
</body>
</html>
