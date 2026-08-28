<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.Exam, com.tution.model.SubjectScore, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<Exam> exams = (List<Exam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<Exam>();
    @SuppressWarnings("unchecked") Map<Integer,String> templates =
        (Map<Integer,String>) request.getAttribute("templates");
    if (templates == null) templates = new LinkedHashMap<Integer,String>();
    @SuppressWarnings("unchecked") List<SubjectScore> smap =
        (List<SubjectScore>) request.getAttribute("subjectMap");
    if (smap == null) smap = new ArrayList<SubjectScore>();
    @SuppressWarnings("unchecked") Map<String,Integer> keyCounts =
        (Map<String,Integer>) request.getAttribute("keyCounts");
    if (keyCounts == null) keyCounts = new LinkedHashMap<String,Integer>();
    @SuppressWarnings("unchecked") Map<String,String> keyText =
        (Map<String,String>) request.getAttribute("keyText");
    if (keyText == null) keyText = new LinkedHashMap<String,String>();
    @SuppressWarnings("unchecked") List<String> readiness =
        (List<String>) request.getAttribute("readiness");

    Exam exam = (Exam) request.getAttribute("exam");
    Integer issuedMax  = (Integer) request.getAttribute("issuedMax");
    Integer registered = (Integer) request.getAttribute("registered");
    Integer suggestFrom = (Integer) request.getAttribute("suggestFrom");

    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");

    int totalQ = exam == null ? 60 : exam.getTotalQuestions();
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Exam Setup – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .es-wrap { max-width:1050px; margin:18px auto; padding:0 14px; }
  .es-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:17px; margin-bottom:14px; }
  .es-card h2 { margin:0 0 3px; font-size:16px; }
  .es-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 13px; }
  .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  .row label { display:block; font-size:12px; color:#5a6b73; margin-bottom:4px; }
  .row input, .row select { padding:8px 10px; border:1px solid #cfd6da; border-radius:6px; font-size:14px; }
  textarea { width:100%; padding:9px 10px; border:1px solid #cfd6da; border-radius:6px;
             font-family:Consolas,Menlo,monospace; font-size:12.5px; box-sizing:border-box; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }
  .a-todo{ background:#fff6e3; border:1px solid #f0d9a8; color:#7a5510; }
  .a-todo ul { margin:6px 0 0 18px; padding:0; }
  .a-todo li { margin-bottom:3px; }
  table.sm { width:100%; border-collapse:collapse; font-size:13px; margin-bottom:10px; }
  table.sm th, table.sm td { padding:6px 8px; border-bottom:1px solid #eceff1; text-align:left; }
  table.sm th { background:#f6f8f9; font-size:12px; color:#42555e; }
  table.sm input { padding:6px 8px; border:1px solid #cfd6da; border-radius:5px; font-size:13px; }
  .bk { border:1px solid #e3e6e8; border-radius:8px; padding:12px; margin-bottom:10px; background:#fbfcfc; }
  .bk h3 { margin:0 0 6px; font-size:14px; display:flex; align-items:center; gap:9px; }
  .badge { font-size:11px; padding:2px 8px; border-radius:20px; font-weight:600; }
  .b-ok   { background:#d8efdf; color:#1b6b39; }
  .b-part { background:#fbecc8; color:#7a5510; }
  .b-none { background:#f0f2f3; color:#69777e; }
  .muted { color:#8697a0; font-size:12px; }
  .sumline { font-size:13px; color:#42555e; margin-top:8px; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="setup"/>
</jsp:include>

<div class="es-wrap">
  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>
  <% if (flash != null)      { %><div class="alert a-ok"><%= esc(flash) %></div><% } %>

  <div class="es-card">
    <h2>Exam setup</h2>
    <p class="hint">Everything a scholarship exam needs before a sheet can be scored.</p>
    <form method="get" action="<%= ctx %>/exam-setup" class="row">
      <div>
        <label for="examId">Exam</label>
        <select name="examId" id="examId" onchange="this.form.submit()">
          <option value="">-- new exam --</option>
          <% for (Exam e : exams) { %>
            <option value="<%= e.getExamId() %>"
              <%= exam != null && e.getExamId()==exam.getExamId() ? "selected" : "" %>>
              <%= esc(e.getExamName()) %> (<%= esc(e.getExamType()) %>)
            </option>
          <% } %>
        </select>
      </div>
      <% if (exam != null) { %>
        <div><a class="btn btn-light" href="<%= ctx %>/candidates?examId=<%= exam.getExamId() %>">👥 Candidates<%=
          registered == null ? "" : " (" + registered + ")" %></a></div>
        <div><a class="btn btn-light" href="<%= ctx %>/exam-import">⬆ Import students</a></div>
        <div><a class="btn btn-light" href="<%= ctx %>/exam-scan?examId=<%= exam.getExamId() %>">📄 Scan sheets</a></div>
        <div><a class="btn btn-light" href="<%= ctx %>/scholarship-results?examId=<%= exam.getExamId() %>">📊 Results</a></div>
      <% } %>
    </form>
  </div>

  <% if (exam != null && readiness != null && !readiness.isEmpty()) { %>
    <div class="alert a-todo">
      <b>Before this exam can be scanned:</b>
      <ul><% for (String t : readiness) { %><li><%= esc(t) %></li><% } %></ul>
    </div>
  <% } else if (exam != null) { %>
    <div class="alert a-ok">This exam is fully set up and ready to scan.</div>
  <% } %>

  <!-- ── settings ── -->
  <div class="es-card">
    <h2><%= exam == null ? "Create a scholarship exam" : "Settings" %></h2>
    <p class="hint">
      The marking scheme on the printed sheet is <b>+4 for correct, &minus;1 for wrong</b>.
      Negative marking does not change the maximum, which is questions &times; marks per correct answer.
    </p>
    <form method="post" action="<%= ctx %>/exam-setup">
      <input type="hidden" name="action" value="<%= exam == null ? "create" : "settings" %>">
      <input type="hidden" name="examId" value="<%= exam == null ? 0 : exam.getExamId() %>">
      <div class="row" style="margin-bottom:11px;">
        <div style="flex:2;">
          <label for="examName">Exam name</label>
          <input type="text" name="examName" id="examName" style="width:100%;" required
                 value="<%= exam == null ? "" : esc(exam.getExamName()) %>">
        </div>
        <div>
          <label for="examType">Type</label>
          <select name="examType" id="examType">
            <% for (String t : new String[]{"HAMSE","HACKSE","HAT"}) { %>
              <option value="<%= t %>" <%= exam != null && t.equals(exam.getExamType()) ? "selected" : "" %>><%= t %></option>
            <% } %>
          </select>
        </div>
        <div>
          <label for="examDate">Date</label>
          <input type="date" name="examDate" id="examDate"
                 value="<%= exam == null ? "" : esc(exam.getExamDate()) %>">
        </div>
        <div>
          <label for="className">Class</label>
          <input type="text" name="className" id="className" size="8"
                 value="<%= exam == null ? "" : esc(exam.getClassName()) %>">
        </div>
      </div>
      <div class="row" style="margin-bottom:11px;">
        <div style="flex:2;">
          <label for="templateId">Answer sheet layout</label>
          <select name="templateId" id="templateId" style="width:100%;">
            <option value="">-- not chosen --</option>
            <% for (Map.Entry<Integer,String> t : templates.entrySet()) { %>
              <option value="<%= t.getKey() %>"
                <%= exam != null && t.getKey().equals(exam.getTemplateId()) ? "selected" : "" %>>
                <%= esc(t.getValue()) %>
              </option>
            <% } %>
          </select>
        </div>
        <div>
          <label for="totalQuestions">Questions</label>
          <input type="number" name="totalQuestions" id="totalQuestions" size="5" min="1" max="400"
                 value="<%= totalQ %>">
        </div>
        <div>
          <label for="markCorrect">Marks: correct</label>
          <input type="number" name="markCorrect" id="markCorrect" size="4" min="1"
                 value="<%= exam == null ? 4 : exam.getMarkCorrect() %>">
        </div>
        <div>
          <label for="markWrong">Marks: wrong</label>
          <input type="number" name="markWrong" id="markWrong" size="4" max="0"
                 value="<%= exam == null ? -1 : exam.getMarkWrong() %>">
        </div>
        <div>
          <label>Maximum</label>
          <input type="text" readonly style="width:70px;background:#f4f6f7;"
                 value="<%= exam == null ? 240 : exam.getMaxScore() %>">
        </div>
      </div>
      <div class="row">
        <div>
          <label for="rollFrom">Roll block from</label>
          <input type="number" name="rollFrom" id="rollFrom" size="7" min="1" max="99999"
                 value="<%= exam == null ? (suggestFrom == null ? 1 : suggestFrom) : exam.getRollBlockFrom() %>">
        </div>
        <div>
          <label for="rollTo">to</label>
          <input type="number" name="rollTo" id="rollTo" size="7" min="1" max="99999"
                 value="<%= exam == null ? (suggestFrom == null ? 2500 : suggestFrom + 2499) : exam.getRollBlockTo() %>">
        </div>
        <div><button class="btn" type="submit"><%= exam == null ? "✚ Create exam" : "💾 Save settings" %></button></div>
      </div>
      <div class="sumline">
        Roll numbers are globally unique and never reused, so blocks must not overlap between exams.
        <% if (exam != null && issuedMax != null && issuedMax > 0) { %>
          <b>Sequences up to <%= issuedMax %> have already been issued</b> for this exam, so the block
          must keep covering them.
        <% } %>
      </div>
    </form>
  </div>

  <% if (exam != null) { %>
  <!-- ── subject split ── -->
  <div class="es-card">
    <h2>Subject split</h2>
    <% int blanks = Math.max(0, 6 - smap.size()); %>
    <p class="hint">
      Which questions belong to which subject &mdash; you choose the ranges, e.g. 1&ndash;10 one subject,
      11&ndash;30 another. Every question from 1 to <%= totalQ %> must be in exactly one subject, or the
      subject marks will not add up to the paper total.
    </p>
    <form method="post" action="<%= ctx %>/exam-setup">
      <input type="hidden" name="action" value="subjects">
      <input type="hidden" name="examId" value="<%= exam.getExamId() %>">
      <table class="sm">
        <tr><th style="width:45%;">Subject</th><th>First question</th><th>Last question</th></tr>
        <%
          for (SubjectScore r : smap) { %>
          <tr>
            <td><input type="text" name="subjectName" value="<%= esc(r.name) %>" style="width:95%;"></td>
            <td><input type="number" name="qFrom" value="<%= r.from %>" min="1" max="<%= totalQ %>" style="width:80px;"></td>
            <td><input type="number" name="qTo"   value="<%= r.to %>"   min="1" max="<%= totalQ %>" style="width:80px;"></td>
          </tr>
        <% }
          for (int i = 0; i < blanks; i++) { %>
          <tr>
            <td><input type="text" name="subjectName" value="" placeholder="e.g. Physics" style="width:95%;"></td>
            <td><input type="number" name="qFrom" value="" min="1" max="<%= totalQ %>" style="width:80px;"></td>
            <td><input type="number" name="qTo"   value="" min="1" max="<%= totalQ %>" style="width:80px;"></td>
          </tr>
        <% } %>
      </table>
      <button class="btn" type="submit">💾 Save subject split</button>
      <span class="muted" style="margin-left:9px;">
        Medical papers usually split Physics / Chemistry / Biology; Engineering swaps Biology for Maths.
        Clear a row's name to remove it.
      </span>
    </form>
  </div>

  <!-- ── answer keys ── -->
  <div class="es-card">
    <h2>Answer keys</h2>
    <p class="hint">
      The paper is shuffled into booklets A&ndash;D, so each needs its own key. The booklet a student
      bubbled is read off their sheet and the matching key is used &mdash; a sheet whose booklet bubble is
      blank is sent for review rather than scored against a default.
      Paste <code>A,B,C,D,...</code> in question order, or <code>1:A 2:B ...</code>. Use <code>-</code> to drop a question.
    </p>
    <% for (String b : new String[]{"A","B","C","D"}) {
         Integer n = keyCounts.get(b);
         int have = n == null ? 0 : n;
         String cls   = have == 0 ? "b-none" : have < totalQ ? "b-part" : "b-ok";
         String label = have == 0 ? "not set" : have + " / " + totalQ + (have < totalQ ? " incomplete" : " complete");
    %>
      <div class="bk">
        <h3>Booklet <%= b %> <span class="badge <%= cls %>"><%= label %></span></h3>
        <form method="post" action="<%= ctx %>/exam-setup">
          <input type="hidden" name="action" value="key">
          <input type="hidden" name="examId" value="<%= exam.getExamId() %>">
          <input type="hidden" name="booklet" value="<%= b %>">
          <textarea name="keyText" rows="3" placeholder="A,B,C,D,A,... (<%= totalQ %> answers)"><%= esc(keyText.get(b)) %></textarea>
          <div style="margin-top:7px;">
            <button class="btn" type="submit">💾 Save booklet <%= b %></button>
            <span class="muted">Saving an empty box clears this key.</span>
          </div>
        </form>
      </div>
    <% } %>
  </div>
  <% } %>
</div>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
%>
</body>
</html>
