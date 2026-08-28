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
    @SuppressWarnings("unchecked") List<Object[]> review = (List<Object[]>) request.getAttribute("review");
    if (review == null) review = new ArrayList<Object[]>();
    @SuppressWarnings("unchecked") Map<String,Integer> counts =
        (Map<String,Integer>) request.getAttribute("counts");
    if (counts == null) counts = new LinkedHashMap<String,Integer>();
    @SuppressWarnings("unchecked") List<String> blockers = (List<String>) request.getAttribute("blockers");
    @SuppressWarnings("unchecked") List<String> flashNotes = (List<String>) session.getAttribute("flashNotes");

    Exam exam = (Exam) request.getAttribute("exam");
    Integer examIdA = (Integer) request.getAttribute("examId");
    int eid = examIdA == null ? 0 : examIdA.intValue();

    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");
    session.removeAttribute("flashNotes");

    // subject columns come from the first result that has any
    List<String> subjectNames = new ArrayList<String>();
    for (ExamResultDAO.Row r : results) {
        if (!r.subjects.isEmpty()) {
            for (SubjectScore s : r.subjects) subjectNames.add(s.name);
            break;
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Scan Answer Sheets – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .sc-wrap { max-width:1250px; margin:18px auto; padding:0 14px; }
  .sc-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .sc-card h2 { margin:0 0 3px; font-size:16px; }
  .sc-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }
  .row { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  .row label { display:block; font-size:12px; color:#5a6b73; margin-bottom:4px; }
  .row input, .row select { padding:8px 10px; border:1px solid #cfd6da; border-radius:6px; font-size:14px; }
  .tally { display:flex; gap:10px; flex-wrap:wrap; margin:10px 0 0; }
  .tally div { border:1px solid #e3e6e8; border-radius:8px; padding:9px 14px; min-width:100px; background:#f8fafb; }
  .tally b { display:block; font-size:20px; } .tally span { font-size:12px; color:#5a6b73; }
  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:6px 8px; border-bottom:1px solid #eceff1; text-align:left; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  .roll { font-family:Consolas,monospace; font-weight:600; letter-spacing:1px; }
  .num { text-align:right; font-variant-numeric:tabular-nums; }
  .alert { padding:11px 14px; border-radius:8px; margin-bottom:12px; font-size:14px; }
  .a-err { background:#fdeaea; border:1px solid #f2bcbc; color:#8c2020; }
  .a-ok  { background:#e8f5ec; border:1px solid #b7dfc4; color:#1b6b39; }
  .a-warn{ background:#fff6e3; border:1px solid #f0d9a8; color:#7a5510; }
  .a-warn ul { margin:6px 0 0 18px; padding:0; } .a-warn li { margin-bottom:2px; }
  .pill { font-size:11px; padding:2px 8px; border-radius:20px; font-weight:600; }
  .m-NO_ROLL      { background:#f7d4d4; color:#8c2020; }
  .m-BAD_CHECKSUM { background:#f7d4d4; color:#8c2020; }
  .m-NOT_IN_EXAM  { background:#fbecc8; color:#7a5510; }
  .m-DUPLICATE    { background:#fbecc8; color:#7a5510; }
  .m-NO_BOOKLET   { background:#e6dcf5; color:#4a2a86; }
  .thumb { width:64px; border:1px solid #ccc; border-radius:3px; vertical-align:middle; cursor:zoom-in; }
  .muted { color:#8697a0; }
  .scroll { overflow-x:auto; }
  .sch { font-weight:700; color:#1b6b39; }
  .lightbox { display:none; position:fixed; inset:0; background:rgba(0,0,0,.8);
              z-index:1000; align-items:center; justify-content:center; padding:24px; }
  .lightbox.open { display:flex; }
  .lightbox img { max-width:95vw; max-height:95vh; border-radius:6px; box-shadow:0 10px 40px rgba(0,0,0,.5); }
  .lightbox .lb-close { position:absolute; top:14px; right:22px; color:#fff; font-size:32px;
                         cursor:pointer; line-height:1; user-select:none; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="scan"/>
</jsp:include>

<div class="sc-wrap">
  <% if (error != null)      { %><div class="alert a-err"><%= esc(error) %></div><% } %>
  <% if (flashError != null) { %><div class="alert a-err"><%= esc(flashError) %></div><% } %>
  <% if (flash != null)      { %><div class="alert a-ok"><%= esc(flash) %></div><% } %>
  <% if (flashNotes != null && !flashNotes.isEmpty()) { %>
    <div class="alert a-warn"><b>Sheets needing attention</b>
      <ul><% for (String n : flashNotes) { %><li><%= esc(n) %></li><% } %></ul></div>
  <% } %>

  <div class="sc-card">
    <h2>Scan answer sheets</h2>
    <p class="hint">
      Upload the scanned sheets for one exam &mdash; loose images, or the single multi-page PDF
      the scanner produces. Each page is identified by its bubbled roll number and scored
      against the answer key for the booklet it was sat on. Anything that cannot be identified
      with certainty goes to the review queue below &mdash; nothing is guessed.
    </p>
    <form method="get" action="<%= ctx %>/exam-scan" class="row" style="margin-bottom:12px;">
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
    </form>

    <% if (blockers != null && !blockers.isEmpty()) { %>
      <div class="alert a-warn">
        <b>Not ready to scan.</b>
        <ul><% for (String b : blockers) { %><li><%= esc(b) %></li><% } %></ul>
        Finish this on the <a href="<%= ctx %>/exam-setup?examId=<%= eid %>">exam setup</a> screen.
      </div>
    <% } else if (exam != null) { %>
      <form method="post" action="<%= ctx %>/exam-scan" enctype="multipart/form-data" class="row">
        <input type="hidden" name="examId" value="<%= eid %>">
        <div>
          <label for="sheets">Scanned sheets — images or a multi-page PDF</label>
          <input type="file" name="sheets" id="sheets"
                 accept="image/*,application/pdf,.pdf" multiple required>
          <div class="muted" style="font-size:11.5px;margin-top:4px;">
            A scanner PDF is split into pages and each one is scored separately.
            It must be a <b>scanned</b> PDF (pages stored as images).
          </div>
        </div>
        <div><button class="btn" type="submit">📄 Scan &amp; score</button></div>
      </form>
    <% } %>

    <% if (exam != null) { %>
      <div class="tally">
        <% for (String st : new String[]{"REGISTERED","RESULT_READY","ABSENT","APPEARED"}) {
             Integer n = counts.get(st); if (n == null) continue; %>
          <div><b><%= n %></b><span><%= st.toLowerCase().replace('_',' ') %></span></div>
        <% } %>
        <div><b><%= results.size() %></b><span>scored</span></div>
        <div><b><%= review.size() %></b><span>in review</span></div>
      </div>
      <div class="btn-row" style="margin-top:12px;">
        <form method="post" action="<%= ctx %>/exam-scan" style="margin:0;"
              onsubmit="return confirm('Mark every candidate with no scanned sheet as ABSENT? Do this only once all sheets are scanned.');">
          <input type="hidden" name="action" value="absentees">
          <input type="hidden" name="examId" value="<%= eid %>">
          <button class="btn btn-light" type="submit"
                  title="Only after every sheet has been scanned">🚫 Mark absentees</button>
        </form>
        <a class="btn btn-light" href="<%= ctx %>/candidates?examId=<%= eid %>">👥 Candidates</a>
        <a class="btn btn-light" href="<%= ctx %>/scholarship-results?examId=<%= eid %>">📊 Results</a>
      </div>
    <% } %>
  </div>

  <!-- ── review queue ── -->
  <% if (!review.isEmpty()) { %>
  <div class="sc-card">
    <h2 style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
      <span>Review queue &mdash; <%= review.size() %> sheet(s)</span>
      <button type="button" class="btn btn-light btn-sm" id="reviewToggleBtn"
              onclick="toggleReviewQueue()">Hide</button>
    </h2>
    <div id="reviewBody">
      <p class="hint">
        These could not be matched to a candidate with certainty. Identify each one by eye and give
        its roll number; the sheet is then linked, and you re-scan the paper to score it. If a sheet
        landed here by mistake (wrong exam selected, a stray blank page), discard it instead.
      </p>
      <div class="btn-row" style="margin:0 0 10px;">
        <form method="post" action="<%= ctx %>/exam-scan" style="margin:0;"
              onsubmit="return confirm('Discard all <%= review.size() %> sheet(s) in this review queue? This only removes the unmatched scan and its image — no candidate has a result yet, so nothing scored is affected. This cannot be undone.');">
          <input type="hidden" name="action" value="deleteAllReview">
          <input type="hidden" name="examId" value="<%= eid %>">
          <button class="btn-danger btn-sm" type="submit">🗑 Discard all</button>
        </form>
      </div>
      <div class="scroll">
        <table class="t">
          <tr><th>Sheet</th><th>Roll read</th><th>Booklet</th><th>Problem</th><th>Scanned</th><th>Assign to roll</th><th></th></tr>
          <% for (Object[] r : review) {
               String status = (String) r[4];
               boolean lowReg = Boolean.TRUE.equals(r[5]);
               String img = (String) r[6];
          %>
            <tr>
              <td>
                <% if (img != null) { %><img class="thumb" src="<%= ctx %>/<%= esc(img) %>" alt=""
                     onclick="openLightbox(this.src)"><% } %>
                <%= esc((String) r[1]) %>
              </td>
              <td class="roll"><%= r[2] == null || ((String) r[2]).isEmpty() ? "<span class=\"muted\">-</span>" : esc((String) r[2]) %></td>
              <td><%= r[3] == null ? "<span class=\"muted\">-</span>" : esc((String) r[3]) %></td>
              <td>
                <span class="pill m-<%= esc(status) %>"><%= esc(status.replace('_',' ')) %></span>
                <% if (lowReg) { %><br><span class="muted" style="font-size:11px;">weak page registration</span><% } %>
              </td>
              <td class="muted" style="font-size:12px;"><%= esc(String.valueOf(r[7])) %></td>
              <td>
                <form method="post" action="<%= ctx %>/exam-scan" style="margin:0;display:flex;gap:5px;">
                  <input type="hidden" name="action" value="rematch">
                  <input type="hidden" name="examId" value="<%= eid %>">
                  <input type="hidden" name="scanId" value="<%= r[0] %>">
                  <input type="text" name="rollNo" size="7" maxlength="6" placeholder="000000"
                         pattern="\d{6}" required style="padding:5px 7px;border:1px solid #cfd6da;border-radius:5px;">
                  <button class="btn btn-sm" type="submit"
                          title="Attach this sheet to that roll number">Link</button>
                </form>
              </td>
              <td>
                <form method="post" action="<%= ctx %>/exam-scan" style="margin:0;"
                      onsubmit="return confirm('Discard this sheet? This cannot be undone.');">
                  <input type="hidden" name="action" value="deleteScan">
                  <input type="hidden" name="examId" value="<%= eid %>">
                  <input type="hidden" name="scanId" value="<%= r[0] %>">
                  <button class="btn-danger btn-sm" type="submit" title="Remove this sheet from the review queue">🗑</button>
                </form>
              </td>
            </tr>
          <% } %>
        </table>
      </div>
    </div>
  </div>
  <script>
    function toggleReviewQueue() {
      var body = document.getElementById('reviewBody');
      var btn = document.getElementById('reviewToggleBtn');
      var hiding = body.style.display !== 'none';
      body.style.display = hiding ? 'none' : '';
      btn.textContent = hiding ? 'Show' : 'Hide';
      try { localStorage.setItem('examScanReviewHidden', hiding ? '1' : '0'); } catch (e) {}
    }
    (function() {
      try {
        if (localStorage.getItem('examScanReviewHidden') === '1') {
          document.getElementById('reviewBody').style.display = 'none';
          document.getElementById('reviewToggleBtn').textContent = 'Show';
        }
      } catch (e) {}
    })();
  </script>
  <% } %>

  <div class="lightbox" id="lightbox" onclick="if(event.target.id==='lightbox') closeLightbox();">
    <span class="lb-close" onclick="closeLightbox()">&times;</span>
    <img id="lightboxImg" src="" alt="Scanned sheet">
  </div>
  <script>
    function openLightbox(src) {
      document.getElementById('lightboxImg').src = src;
      document.getElementById('lightbox').classList.add('open');
    }
    function closeLightbox() {
      document.getElementById('lightbox').classList.remove('open');
      document.getElementById('lightboxImg').src = '';
    }
    document.addEventListener('keydown', function(e) { if (e.key === 'Escape') closeLightbox(); });
  </script>

  <!-- ── results ── -->
  <div class="sc-card">
    <h2>Results <%= results.isEmpty() ? "" : "&mdash; " + results.size() + " scored" %></h2>
    <% if (results.isEmpty()) { %>
      <p class="muted">Nothing scored yet for this exam.</p>
    <% } else { %>
    <div class="scroll">
      <table class="t">
        <tr>
          <th>Rank</th><th>Roll</th><th>Name</th><th>School</th><th>Bk</th>
          <% for (String s : subjectNames) { %><th class="num"><%= esc(s) %></th><% } %>
          <th class="num">Att</th><th class="num">Right</th><th class="num">Wrong</th>
          <th class="num">Score</th><th class="num">%</th><th class="num">Scholarship</th><th></th>
        </tr>
        <% for (ExamResultDAO.Row r : results) { %>
          <tr>
            <td><%= r.rank %></td>
            <td class="roll"><%= esc(r.rollNo) %></td>
            <td><%= esc(r.name) %></td>
            <td><%= esc(r.school) %></td>
            <td><%= esc(r.booklet) %></td>
            <% for (String sn : subjectNames) {
                 String v = "-";
                 for (SubjectScore s : r.subjects) if (sn.equals(s.name)) v = String.valueOf(s.score);
            %><td class="num"><%= v %></td><% } %>
            <td class="num"><%= r.attempted %></td>
            <td class="num"><%= r.correct %></td>
            <td class="num"><%= r.wrong %></td>
            <td class="num"><b><%= r.rawScore %></b> / <%= r.maxScore %></td>
            <td class="num"><%= String.format("%.2f", r.percentage) %></td>
            <td class="num sch" title="<%= esc(r.awardCriteria == null ? "" : r.awardCriteria) %>">
              <%= String.format("%.2f", r.scholarshipPct) %>%
            </td>
            <td>
              <form method="post" action="<%= ctx %>/exam-scan" style="margin:0;"
                    onsubmit="return confirm('Clear the result for <%= esc(r.name) %>? The sheet can then be re-scanned.');">
                <input type="hidden" name="action" value="clear">
                <input type="hidden" name="examId" value="<%= eid %>">
                <input type="hidden" name="candidateId" value="<%= r.candidateId %>">
                <button class="btn-danger btn-sm" type="submit"
                        title="Discards the score so the sheet can be re-scanned">Clear</button>
              </form>
            </td>
          </tr>
        <% } %>
      </table>
    </div>
    <p class="hint" style="margin-top:10px;">
      Scholarship is the highest single route the candidate qualifies for &mdash; hover the figure to see which.
      Marking is +<%= exam == null ? 4 : exam.getMarkCorrect() %> correct,
      <%= exam == null ? -1 : exam.getMarkWrong() %> wrong; percentage is floored at zero.
    </p>
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
