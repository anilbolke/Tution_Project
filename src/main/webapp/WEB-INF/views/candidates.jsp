<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.Exam, com.tution.model.ExamCandidate, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<Exam> exams = (List<Exam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<Exam>();
    @SuppressWarnings("unchecked") List<ExamCandidate> list =
        (List<ExamCandidate>) request.getAttribute("candidates");
    if (list == null) list = new ArrayList<ExamCandidate>();
    @SuppressWarnings("unchecked") List<String> schools = (List<String>) request.getAttribute("schools");
    if (schools == null) schools = new ArrayList<String>();
    @SuppressWarnings("unchecked") List<String> centres = (List<String>) request.getAttribute("centres");
    if (centres == null) centres = new ArrayList<String>();
    @SuppressWarnings("unchecked") Map<String,Integer> counts =
        (Map<String,Integer>) request.getAttribute("counts");
    if (counts == null) counts = new LinkedHashMap<String,Integer>();

    Exam exam = (Exam) request.getAttribute("exam");
    Integer examId = (Integer) request.getAttribute("examId");
    int eid = examId == null ? 0 : examId.intValue();
    Integer totalReg = (Integer) request.getAttribute("totalRegistered");
    String fSchool = s((String) request.getAttribute("fSchool"));
    String fCentre = s((String) request.getAttribute("fCentre"));
    String fStatus = s((String) request.getAttribute("fStatus"));
    String fQ      = s((String) request.getAttribute("fQ"));

    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");

    String qs = "examId=" + eid
        + (fSchool.isEmpty() ? "" : "&school=" + u(fSchool))
        + (fCentre.isEmpty() ? "" : "&centre=" + u(fCentre))
        + (fStatus.isEmpty() ? "" : "&status=" + u(fStatus))
        + (fQ.isEmpty()      ? "" : "&q="      + u(fQ));
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Exam Candidates – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .cd-wrap { max-width:1250px; margin:18px auto; padding:0 14px; }
  .cd-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .cd-bar { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  .cd-bar label { display:block; font-size:12px; color:#5a6b73; margin-bottom:4px; }
  .cd-bar input, .cd-bar select { padding:8px 10px; border:1px solid #cfd6da; border-radius:6px; font-size:14px; }
  .tally { display:flex; gap:10px; flex-wrap:wrap; margin:12px 0 0; }
  .tally div { border:1px solid #e3e6e8; border-radius:8px; padding:9px 14px; min-width:104px; background:#f8fafb; }
  .tally b { display:block; font-size:20px; line-height:1.15; }
  .tally span { font-size:12px; color:#5a6b73; }
  table.cd { width:100%; border-collapse:collapse; font-size:13px; }
  table.cd th, table.cd td { padding:7px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.cd th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  .roll { font-family:Consolas,Menlo,monospace; font-size:14px; letter-spacing:1px; font-weight:600; }
  .pill { font-size:11px; padding:2px 8px; border-radius:20px; font-weight:600; }
  .st-REGISTERED   { background:#e4eef7; color:#1d4e79; }
  .st-APPEARED     { background:#d8efdf; color:#1b6b39; }
  .st-ABSENT       { background:#f7d4d4; color:#8c2020; }
  .st-RESULT_READY { background:#e6dcf5; color:#4a2a86; }
  .legacy { background:#fbecc8; color:#7a5510; font-size:10px; padding:1px 6px; border-radius:20px; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }
  .tools { display:flex; gap:8px; flex-wrap:wrap; margin-top:12px; }
  .scroll { overflow-x:auto; }
  .muted { color:#8697a0; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="candidates"/>
</jsp:include>

<div class="cd-wrap">
  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>
  <% if (flash != null)      { %><div class="alert a-ok"><%= esc(flash) %></div><% } %>

  <% if (exams.isEmpty()) { %>
    <div class="cd-card">
      <h2>Exam candidates</h2>
      <p class="muted">No scholarship exams exist yet. Create a HAMSE / HACKSE / HAT exam,
         give it a roll-number block, then import a school list.</p>
    </div>
  <% } else { %>

  <div class="cd-card">
    <form method="get" action="<%= ctx %>/candidates" class="cd-bar">
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
        <select name="school" id="school">
          <option value="">All schools</option>
          <% for (String s : schools) { %>
            <option value="<%= esc(s) %>" <%= s.equals(fSchool) ? "selected" : "" %>><%= esc(s) %></option>
          <% } %>
        </select>
      </div>
      <div>
        <label for="centre">Centre</label>
        <select name="centre" id="centre">
          <option value="">All centres</option>
          <% for (String s : centres) { %>
            <option value="<%= esc(s) %>" <%= s.equals(fCentre) ? "selected" : "" %>><%= esc(s) %></option>
          <% } %>
        </select>
      </div>
      <div>
        <label for="status">Status</label>
        <select name="status" id="status">
          <option value="">All</option>
          <% for (String st : new String[]{"REGISTERED","APPEARED","ABSENT","RESULT_READY"}) { %>
            <option value="<%= st %>" <%= st.equals(fStatus) ? "selected" : "" %>><%= st %></option>
          <% } %>
        </select>
      </div>
      <div>
        <label for="q">Roll / name / mobile</label>
        <input type="text" name="q" id="q" value="<%= esc(fQ) %>" size="20">
      </div>
      <div><button class="btn" type="submit">🔍 Filter</button></div>
      <div><a class="btn btn-light" href="<%= ctx %>/candidates?examId=<%= eid %>">Clear</a></div>
    </form>

    <div class="tally">
      <div><b><%= totalReg == null ? 0 : totalReg %></b><span>candidates</span></div>
      <% for (String st : new String[]{"REGISTERED","APPEARED","ABSENT","RESULT_READY"}) {
           Integer n = counts.get(st); if (n == null) continue; %>
        <div><b><%= n %></b><span><%= st.toLowerCase().replace('_',' ') %></span></div>
      <% } %>
      <% if (exam != null && exam.getRollBlockFrom() > 0) { %>
        <div><b><%= exam.rollsRemaining() %></b><span>rolls left in block</span></div>
      <% } %>
    </div>

    <%-- Printing the hall tickets is what this screen is for on exam week, so it
         is the one filled button; everything else stays secondary. --%>
    <div class="tools btn-row">
      <a class="btn" href="<%= ctx %>/hall-tickets?<%= qs %>" target="_blank">🎫 Print hall tickets</a>
      <a class="btn btn-light" href="<%= ctx %>/roll-list?<%= qs %>" target="_blank">📋 Print roll list</a>
      <a class="btn btn-light" href="<%= ctx %>/candidates?<%= qs %>&export=xlsx">⬇ Export to Excel</a>
      <form method="post" action="<%= ctx %>/candidates" style="margin:0;"
            onsubmit="return confirm('Assign booklet codes A-D across all candidates in roll order? This overwrites any codes already set.');">
        <input type="hidden" name="action" value="booklets">
        <input type="hidden" name="examId" value="<%= eid %>">
        <button class="btn btn-light" type="submit"
                title="Spreads booklet codes A-D across candidates in roll order">🔤 Auto-assign booklets</button>
      </form>
      <form method="post" action="<%= ctx %>/candidates" style="margin:0;"
            onsubmit="return confirm('Mark every candidate with no scanned sheet as ABSENT? Do this only once all sheets are scanned.');">
        <input type="hidden" name="action" value="absentees">
        <input type="hidden" name="examId" value="<%= eid %>">
        <button class="btn btn-light" type="submit"
                title="Only after every sheet has been scanned">🚫 Mark absentees</button>
      </form>
    </div>
  </div>

  <div class="cd-card">
    <div class="scroll">
      <table class="cd">
        <tr>
          <th>Roll No</th><th>Name</th><th>School</th><th>Class</th>
          <th>Contact</th><th>Centre</th><th>Booklet</th><th>Att.</th><th>Status</th><th></th>
        </tr>
        <% if (list.isEmpty()) { %>
          <tr><td colspan="10" class="muted" style="padding:18px;">
            No candidates match. Import a school list to register students for this exam.
          </td></tr>
        <% } %>
        <% for (ExamCandidate c : list) { %>
          <tr>
            <td>
              <span class="roll"><%= esc(c.getRollNo()) %></span>
              <% if (c.isLegacyRoll()) { %> <span class="legacy" title="Issued by the school before this system; no check digit">legacy</span><% } %>
            </td>
            <td><%= esc(c.getFullName()) %></td>
            <td><%= esc(c.getSchoolName()) %></td>
            <td><%= esc(c.getClassName()) %></td>
            <td><%= esc(c.getMobile()) %></td>
            <td><%= esc(c.getExamCentre()) %></td>
            <td><%= c.getBookletCode() == null ? "<span class=\"muted\">-</span>" : esc(c.getBookletCode()) %></td>
            <td><%= c.getAttemptNo() %></td>
            <td><span class="pill st-<%= esc(c.getStatus()) %>"><%= esc(c.getStatus()) %></span></td>
            <td>
              <% if (!c.hasSat()) { %>
                <form method="post" action="<%= ctx %>/candidates" style="margin:0;"
                      onsubmit="return confirm('Remove <%= esc(c.getFullName()) %> (roll <%= esc(c.getRollNo()) %>) from this exam? The roll number will not be reissued.');">
                  <input type="hidden" name="action" value="delete">
                  <input type="hidden" name="examId" value="<%= eid %>">
                  <input type="hidden" name="candidateId" value="<%= c.getCandidateId() %>">
                  <button class="btn-danger btn-sm" type="submit"
                          title="Removes this candidate; the roll number is not reissued">Remove</button>
                </form>
              <% } %>
            </td>
          </tr>
        <% } %>
      </table>
    </div>
  </div>
  <% } %>
</div>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String s(String v) { return v == null ? "" : v; }
    private String u(String v) {
        try { return java.net.URLEncoder.encode(v, "UTF-8"); } catch (Exception e) { return ""; }
    }
%>
</body>
</html>
