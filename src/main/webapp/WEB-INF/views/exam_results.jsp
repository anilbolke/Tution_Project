<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.Exam, com.tution.model.SubjectScore, com.tution.model.User" %>
<%@ page import="com.tution.dao.ExamResultDAO" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<Exam> exams = (List<Exam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<Exam>();
    @SuppressWarnings("unchecked") List<ExamResultDAO.Row> results =
        (List<ExamResultDAO.Row>) request.getAttribute("results");
    if (results == null) results = new ArrayList<ExamResultDAO.Row>();
    @SuppressWarnings("unchecked") List<String> schools = (List<String>) request.getAttribute("schools");
    if (schools == null) schools = new ArrayList<String>();
    @SuppressWarnings("unchecked") Map<String,Integer> counts =
        (Map<String,Integer>) request.getAttribute("counts");
    if (counts == null) counts = new LinkedHashMap<String,Integer>();

    Exam exam = (Exam) request.getAttribute("exam");
    Integer examIdA = (Integer) request.getAttribute("examId");
    int eid = examIdA == null ? 0 : examIdA.intValue();
    String fSchool = (String) request.getAttribute("fSchool");
    if (fSchool == null) fSchool = "";
    Boolean dryRun = (Boolean) request.getAttribute("dryRun");

    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");

    List<String> subjectNames = new ArrayList<String>();
    for (ExamResultDAO.Row r : results) {
        if (!r.subjects.isEmpty()) { for (SubjectScore s : r.subjects) subjectNames.add(s.name); break; }
    }
    double avgPct = 0; int top = 0;
    for (ExamResultDAO.Row r : results) { avgPct += r.percentage; if (r.rawScore > top) top = r.rawScore; }
    if (!results.isEmpty()) avgPct /= results.size();
    int withScholarship = 0;
    for (ExamResultDAO.Row r : results) if (r.scholarshipPct > 0) withScholarship++;
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Exam Results – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .rs-wrap { max-width:1300px; margin:18px auto; padding:0 14px; }
  .rs-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .rs-card h2 { margin:0 0 3px; font-size:16px; }
  .rs-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }
  .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  .row label { display:block; font-size:12px; color:#5a6b73; margin-bottom:4px; }
  .row select { padding:8px 10px; border:1px solid #cfd6da; border-radius:6px; font-size:14px; }
  .tally { display:flex; gap:10px; flex-wrap:wrap; margin:10px 0 0; }
  .tally div { border:1px solid #e3e6e8; border-radius:8px; padding:9px 14px; min-width:104px; background:#f8fafb; }
  .tally b { display:block; font-size:20px; } .tally span { font-size:12px; color:#5a6b73; }
  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:6px 8px; border-bottom:1px solid #eceff1; text-align:left; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  .roll { font-family:Consolas,monospace; font-weight:600; letter-spacing:1px; }
  .num { text-align:right; font-variant-numeric:tabular-nums; }
  .rank { font-weight:700; }
  .sch { font-weight:700; color:#1b6b39; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }
  .a-warn{ background:#fff6e3; border:1px solid #f0d9a8; color:#7a5510; }
  .muted { color:#8697a0; }
  .scroll { overflow-x:auto; }
  .tools { display:flex; gap:8px; flex-wrap:wrap; margin-top:12px; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="results"/>
</jsp:include>

<div class="rs-wrap">
  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>
  <% if (flash != null)      { %><div class="alert a-ok"><%= esc(flash) %></div><% } %>

  <div class="rs-card">
    <h2>Exam results</h2>
    <p class="hint">
      Ranked by score. Scholarship is the single highest route each candidate qualifies for &mdash;
      hover it to see which rule won.
    </p>
    <form method="get" action="<%= ctx %>/scholarship-results" class="row">
      <div>
        <label for="examId">Exam</label>
        <select name="examId" id="examId" onchange="this.form.submit()">
          <% for (Exam e : exams) { %>
            <option value="<%= e.getExamId() %>" <%= e.getExamId()==eid ? "selected" : "" %>>
              <%= esc(e.getExamName()) %> (<%= esc(e.getExamType()) %>)
            </option>
          <% } %>
        </select>
      </div>
      <div>
        <label for="school">School</label>
        <select name="school" id="school" onchange="this.form.submit()">
          <option value="">All schools</option>
          <% for (String s : schools) { %>
            <option value="<%= esc(s) %>" <%= s.equals(fSchool) ? "selected" : "" %>><%= esc(s) %></option>
          <% } %>
        </select>
      </div>
    </form>

    <% if (exam != null) { %>
      <div class="tally">
        <div><b><%= results.size() %></b><span>results</span></div>
        <div><b><%= top %></b><span>top score</span></div>
        <div><b><%= String.format("%.1f", avgPct) %>%</b><span>average</span></div>
        <div><b><%= withScholarship %></b><span>earned a scholarship</span></div>
        <% Integer ab = counts.get("ABSENT"); if (ab != null) { %>
          <div><b><%= ab %></b><span>absent</span></div>
        <% } %>
      </div>

      <%-- Publishing is the end of the whole pipeline, so it is the filled
           button and it sits last, after the things you do on the way there. --%>
      <div class="tools btn-row">
        <a class="btn btn-light" href="<%= ctx %>/exam-scan?examId=<%= eid %>">📄 Scan more sheets</a>
        <a class="btn btn-light" href="<%= ctx %>/scholarship-results?examId=<%= eid %><%=
            fSchool.isEmpty() ? "" : "&school=" + u(fSchool) %>&export=xlsx">
          ⬇ Export to the institute template
        </a>
        <span class="spacer"></span>
        <form method="post" action="<%= ctx %>/scholarship-results" style="margin:0;"
              onsubmit="return confirm('Publish results? Each candidate becomes a RESULT_DECLARED lead carrying their scholarship, ready for the counsellors. Students already admitted are left untouched.');">
          <input type="hidden" name="action" value="publish">
          <input type="hidden" name="examId" value="<%= eid %>">
          <button class="btn" type="submit" <%= results.isEmpty() ? "disabled" : "" %>
                  title="<%= results.isEmpty()
                            ? "Nothing to publish yet - scan some sheets first"
                            : "Hands every scored candidate to the counsellors as a lead" %>">
            <%= exam.isResultPublished() ? "🔄 Re-publish results" : "🚀 Publish &amp; hand to counsellors" %>
          </button>
        </form>
      </div>
      <% if (exam.isResultPublished()) { %>
        <p class="hint" style="margin-top:9px;">
          Already published &mdash; publishing again refreshes each lead's scholarship from the
          current result.
        </p>
      <% } %>
    <% } %>
  </div>

  <% if (Boolean.TRUE.equals(dryRun)) { %>
    <div class="alert a-warn">
      WhatsApp sending is switched off (<code>DRY_RUN</code>), as asked. The Send button shows the
      exact message that would go out instead of sending it.
    </div>
  <% } %>

  <div class="rs-card">
    <% if (results.isEmpty()) { %>
      <p class="muted">No results yet for this exam. Scan the answer sheets first.</p>
    <% } else { %>
    <div class="scroll">
      <table class="t">
        <tr>
          <th>Rank</th><th>Roll</th><th>Name</th><th>School</th><th>Bk</th>
          <% for (String s : subjectNames) { %><th class="num"><%= esc(s) %></th><% } %>
          <th class="num">Right</th><th class="num">Wrong</th><th class="num">Score</th>
          <th class="num">%</th><th class="num">Scholarship</th><th>Slip</th><th>Result SMS</th>
        </tr>
        <% for (ExamResultDAO.Row r : results) { %>
          <tr>
            <td class="rank"><%= r.rank %></td>
            <td class="roll"><%= esc(r.rollNo) %></td>
            <td><%= esc(r.name) %></td>
            <td><%= esc(r.school) %></td>
            <td><%= esc(r.booklet) %></td>
            <% for (String sn : subjectNames) { String v = "-";
                 for (SubjectScore s : r.subjects) if (sn.equals(s.name)) v = String.valueOf(s.score); %>
              <td class="num"><%= v %></td>
            <% } %>
            <td class="num"><%= r.correct %></td>
            <td class="num"><%= r.wrong %></td>
            <td class="num"><b><%= r.rawScore %></b> / <%= r.maxScore %></td>
            <td class="num"><%= String.format("%.2f", r.percentage) %></td>
            <td class="num sch" title="<%= esc(r.awardCriteria == null ? "" : r.awardCriteria) %>">
              <%= String.format("%.2f", r.scholarshipPct) %>%
            </td>
            <td>
              <a class="btn-light btn-sm" target="_blank" title="Score slip as a PDF"
                 href="<%= ctx %>/scholarship-results?examId=<%= eid %>&action=slip&candidateId=<%= r.candidateId %>">PDF</a>
            </td>
            <td>
              <form method="post" action="<%= ctx %>/scholarship-results" style="margin:0;">
                <input type="hidden" name="action" value="whatsapp">
                <input type="hidden" name="examId" value="<%= eid %>">
                <input type="hidden" name="candidateId" value="<%= r.candidateId %>">
                <button class="btn-light btn-sm" type="submit"
                        title="<%= Boolean.TRUE.equals(dryRun)
                                  ? "Shows the message without sending it - auto-send is off"
                                  : "Sends the result to the parent over WhatsApp" %>">
                  <%= Boolean.TRUE.equals(dryRun) ? "Preview" : "Send" %>
                </button>
              </form>
            </td>
          </tr>
        <% } %>
      </table>
    </div>
    <p class="hint" style="margin-top:10px;">
      Marking +<%= exam == null ? 4 : exam.getMarkCorrect() %> / <%= exam == null ? -1 : exam.getMarkWrong() %>.
      Equal scores share a rank.
    </p>
    <% } %>
  </div>
</div>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String u(String v) {
        try { return java.net.URLEncoder.encode(v, "UTF-8"); } catch (Exception e) { return ""; }
    }
%>
</body>
</html>
