<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.OmrResult, com.tution.model.OmrScan, com.tution.model.OmrBatchRow, com.tution.model.SubjectScore, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    OmrResult result = (OmrResult) request.getAttribute("result");
    String overlayUrl = (String) request.getAttribute("overlayUrl");
    String error = (String) request.getAttribute("error");
    @SuppressWarnings("unchecked") List<OmrScan> recent = (List<OmrScan>) request.getAttribute("recent");
%>
<%!
    private String cellStyle(OmrResult r, int q) {
        String a = r.getAnswers().get(q);
        String conf = r.getConfidence().get(q);
        if (r.isScored() && r.getCorrectKey().containsKey(q)) {
            if (a == null) return "background:#eee;color:#888;";
            return a.equalsIgnoreCase(r.getCorrectKey().get(q))
                 ? "background:#D5F5E3;color:#15803D;font-weight:700;"
                 : "background:#FADBD8;color:#C0392B;font-weight:700;";
        }
        if ("ambiguous".equals(conf)) return "background:#FFF4D6;color:#9A6B00;font-weight:700;";
        if (a == null)               return "background:#f4f4f4;color:#aaa;";
        return "background:#E8F7EF;color:#15803D;font-weight:700;";
    }
    private String cellText(OmrResult r, int q) {
        String a = r.getAnswers().get(q);
        if (a != null) return a;
        return "ambiguous".equals(r.getConfidence().get(q)) ? "?" : "–";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title>OMR Scanner – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    h2.t { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:4px; }
    .sub { font-size:13px; color:var(--muted); margin-bottom:18px; }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:22px 24px; margin-bottom:20px; }
    .card h3 { font-size:15px; font-weight:700; color:var(--green-dark); margin-bottom:14px; }
    .field { margin-bottom:13px; }
    .field label { display:block; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .field input[type=text], .field input[type=number], .field textarea, .field input[type=file] {
      width:100%; padding:10px 12px; font-size:14px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:7px; background:var(--green-pale); }
    .field textarea { resize:vertical; min-height:64px; }
    .field-row { display:grid; grid-template-columns:1fr 1fr; gap:13px; }
    table.subj-ranges { width:100%; border-collapse:collapse; }
    table.subj-ranges th { text-align:left; font-size:11px; color:var(--muted); text-transform:uppercase; letter-spacing:0.4px; padding:4px 8px; }
    table.subj-ranges td { padding:4px 8px; }
    table.subj-ranges input { width:100%; padding:8px 10px; font-size:14px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:6px; background:var(--green-pale); }
    table.subj-ranges td:nth-child(2), table.subj-ranges td:nth-child(3), table.subj-ranges th:nth-child(2), table.subj-ranges th:nth-child(3) { width:120px; }
    .check { display:flex; align-items:center; gap:8px; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:13px; }
    .check input { width:17px; height:17px; accent-color:var(--green); }
    .btn-submit { width:100%; padding:12px; background:var(--green); color:#fff; border:none; border-radius:8px; font-size:15px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
    .stat-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(120px,1fr)); gap:12px; margin-bottom:16px; }
    .sc { background:var(--green-pale); border:1px solid var(--border); border-radius:10px; padding:14px; text-align:center; }
    .sc .v { font-size:24px; font-weight:800; color:var(--green-dark); line-height:1; }
    .sc .l { font-size:10px; color:var(--muted); text-transform:uppercase; letter-spacing:0.5px; margin-top:5px; }
    .sc.score .v { color:#1B4F9C; }
    .grid4 { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; }
    @media(max-width:700px){ .grid4 { grid-template-columns:repeat(2,1fr); } }
    table.ans { width:100%; border-collapse:collapse; font-size:12px; }
    table.ans td { border:1px solid #eee; padding:3px 6px; text-align:center; }
    table.ans td.q { color:var(--muted); font-weight:600; width:34px; }
    .overlay-wrap img { width:100%; border:1px solid var(--border); border-radius:8px; }
    .legend { font-size:11px; color:var(--muted); margin-top:8px; }
    .legend span { display:inline-block; width:11px; height:11px; border-radius:3px; vertical-align:middle; margin:0 3px 0 10px; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.lst { width:100%; border-collapse:collapse; font-size:13px; min-width:640px; }
    table.lst th { background:var(--green); color:#fff; text-align:left; padding:11px 14px; font-size:11px; text-transform:uppercase; letter-spacing:0.4px; }
    table.lst td { padding:10px 14px; border-bottom:1px solid var(--border); }
    table.lst tr:nth-child(even) td { background:var(--green-pale); }
  </style>
</head>
<body>
<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="omr"/></jsp:include>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <h2 class="t">OMR Answer-Sheet Scanner</h2>
  <p class="sub">Free, fully local reading — no external/paid service. Upload a sheet scan; the bubbles are read on the server.</p>

  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <div class="card">
    <h3>Scan a Sheet</h3>
    <form action="<%= ctx %>/omr" method="post" enctype="multipart/form-data">
      <div class="field"><label>Title</label><input type="text" name="title" placeholder="e.g. Roll 1024 — Mock 1 (Side 2)"></div>
      <div class="field"><label>Answer Sheet — JPG / PNG, or a multi-page PDF</label><input type="file" name="sheet" accept="image/*,application/pdf"></div>
      <p style="font-size:12px;color:var(--muted);margin:-6px 0 12px;">A PDF of scanned sheets is read page-by-page (each page = one student's sheet).</p>
      <label class="check"><input type="checkbox" name="useSample" value="1"> Use bundled sample sheet (for testing)</label>
      <div class="field">
        <label>Answer Key (optional — to auto-score)</label>
        <textarea name="answerKey" placeholder="e.g.  1:A 2:C 3:D …  or a plain sequence  A,C,D,B,…  (use '-' to skip a question)"></textarea>
      </div>

      <div class="field">
        <label>Subject Question Ranges (for subject-wise calculation)</label>
        <table class="subj-ranges">
          <thead><tr><th>Subject</th><th>From</th><th>To</th></tr></thead>
          <tbody>
            <tr><td><input type="text" name="subj1" value="Physics"></td><td><input type="number" name="from1" value="1"></td><td><input type="number" name="to1" value="45"></td></tr>
            <tr><td><input type="text" name="subj2" value="Chemistry"></td><td><input type="number" name="from2" value="46"></td><td><input type="number" name="to2" value="90"></td></tr>
            <tr><td><input type="text" name="subj3" value="Botany"></td><td><input type="number" name="from3" value="91"></td><td><input type="number" name="to3" value="135"></td></tr>
            <tr><td><input type="text" name="subj4" value="Zoology"></td><td><input type="number" name="from4" value="136"></td><td><input type="number" name="to4" value="180"></td></tr>
          </tbody>
        </table>
        <p style="font-size:11px;color:var(--muted);margin-top:6px;">Ranges should not overlap and should cover all questions. Defaults = NEET (45 each).</p>
      </div>

      <div class="field-row">
        <div class="field"><label>Marks per correct</label><input type="number" name="markCorrect" value="4"></div>
        <div class="field"><label>Marks per wrong</label><input type="number" name="markWrong" value="-1"></div>
      </div>
      <button type="submit" class="btn-submit">📄 Read Sheet</button>
    </form>
  </div>

  <%
     List<OmrBatchRow> batch = (List<OmrBatchRow>) request.getAttribute("batch");
     boolean batchScored = Boolean.TRUE.equals(request.getAttribute("scored"));
  %>
  <% if (batch != null) {
       int tAtt=0,tBlank=0,tAmb=0,tScore=0,tCorr=0;
       for (OmrBatchRow r : batch){ tAtt+=r.attempted; tBlank+=r.blank; tAmb+=r.ambiguous; if(r.score!=null)tScore+=r.score; if(r.correct!=null)tCorr+=r.correct; }
  %>
  <div class="card">
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
      <h3 style="margin:0;">PDF Batch — <%= request.getAttribute("batchTitle") %> &nbsp;(<%= batch.size() %> page<%= batch.size()==1?"":"s" %>)</h3>
      <a href="<%= ctx %>/omr-export" style="background:#15803D;color:#fff;text-decoration:none;font-weight:700;font-size:13px;padding:9px 16px;border-radius:7px;">⬇ Download Excel</a>
    </div>
    <div class="stat-grid">
      <div class="sc"><div class="v"><%= batch.size() %></div><div class="l">Sheets</div></div>
      <div class="sc"><div class="v"><%= tAtt %></div><div class="l">Total Attempted</div></div>
      <div class="sc"><div class="v"><%= tAmb %></div><div class="l">Total Ambiguous</div></div>
      <% if (batchScored) { %>
        <div class="sc"><div class="v" style="color:#15803D"><%= tCorr %></div><div class="l">Total Correct</div></div>
        <div class="sc score"><div class="v"><%= tScore %></div><div class="l">Total Score</div></div>
      <% } %>
    </div>
    <div class="table-wrap">
      <table class="lst">
        <% List<SubjectScore> bSubs = (batch.isEmpty() || batch.get(0).subjects == null) ? null : batch.get(0).subjects; %>
        <thead><tr><th>Sheet</th><th>Student</th><th>Attempted</th><th>Blank</th><th>Amb.</th>
          <% if (batchScored) { %><th>Correct</th><th>Wrong</th><th>Score</th><% } %>
          <% if (bSubs != null) for (SubjectScore bs : bSubs) { %><th title="<%= bs.name %> (Q<%= bs.from %>–<%= bs.to %>)"><%= bs.name.length() > 4 ? bs.name.substring(0, 4) : bs.name %></th><% } %>
          <th>Overlay</th></tr></thead>
        <tbody>
        <% for (OmrBatchRow r : batch) { %>
          <tr>
            <td style="font-weight:700;color:var(--green-dark)">Page <%= r.page %></td>
            <td><% if (r.studentName != null) { %><span style="font-weight:600;color:var(--green-dark)"><%= r.studentName %></span>
                <% } else if (r.rollNo != null && !r.rollNo.isEmpty()) { %><span style="color:#9A6B00;" title="no match">#<%= r.rollNo %></span>
                <% } else { %><span style="color:var(--muted);">—</span><% } %></td>
            <td><%= r.attempted %></td><td><%= r.blank %></td><td><%= r.ambiguous %></td>
            <% if (batchScored) { %>
              <td style="color:#15803D"><%= r.correct %></td>
              <td style="color:#C0392B"><%= r.wrong %></td>
              <td style="font-weight:700"><%= r.score %></td>
            <% } %>
            <% if (r.subjects != null) for (SubjectScore rs : r.subjects) { %><td><%= batchScored ? rs.score : (rs.attempted + "/" + rs.questions) %></td><% } %>
            <td><% if (r.overlayUrl != null) { %><a href="<%= r.overlayUrl %>" target="_blank" style="color:var(--green);font-weight:600;">view</a><% } %></td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  </div>
  <% } %>

  <% if (result != null) { %>
  <div class="card">
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
      <h3 style="margin:0;">Result — <%= request.getAttribute("title") %></h3>
      <a href="<%= ctx %>/omr-export" style="background:#15803D;color:#fff;text-decoration:none;font-weight:700;font-size:13px;padding:9px 16px;border-radius:7px;">⬇ Download Excel</a>
    </div>
    <%
       String rollNo = (String) request.getAttribute("rollNo");
       com.tution.model.Student rollStudent = (com.tution.model.Student) request.getAttribute("rollStudent");
    %>
    <% if (rollStudent != null) { %>
      <div style="background:var(--green-light);border:1px solid var(--green);border-radius:8px;padding:11px 14px;margin-bottom:14px;font-size:14px;">
        🎓 <strong><%= rollStudent.getFullName() %></strong>
        <span style="color:var(--muted);">· <%= rollStudent.getAdmissionNo() %><%= rollStudent.getClassName()==null?"":" · "+rollStudent.getClassName() %></span>
        <span style="color:var(--muted);font-size:12px;">(Roll <%= rollNo %>)</span>
      </div>
    <% } else if (rollNo != null && !rollNo.isEmpty()) { %>
      <div style="background:#FFF4D6;border:1px solid #E0C26A;border-radius:8px;padding:11px 14px;margin-bottom:14px;font-size:13px;color:#9A6B00;">
        🔢 Roll read: <strong><%= rollNo %></strong> — no matching student found.
      </div>
    <% } %>
    <div class="stat-grid">
      <div class="sc"><div class="v"><%= result.attempted() %></div><div class="l">Attempted</div></div>
      <div class="sc"><div class="v"><%= result.blankCount() %></div><div class="l">Blank</div></div>
      <div class="sc"><div class="v"><%= result.getAmbiguous().size() %></div><div class="l">Ambiguous</div></div>
      <% if (result.isScored()) { %>
        <div class="sc"><div class="v" style="color:#15803D"><%= result.getCorrect() %></div><div class="l">Correct</div></div>
        <div class="sc"><div class="v" style="color:#C0392B"><%= result.getWrong() %></div><div class="l">Wrong</div></div>
        <div class="sc score"><div class="v"><%= result.getScore() %></div><div class="l">Score</div></div>
      <% } %>
    </div>

    <%
       List<SubjectScore> subs = result.getSubjects();
       boolean subScored = false; if (subs != null) for (SubjectScore ss : subs) if (ss.scored) subScored = true;
    %>
    <% if (subs != null && !subs.isEmpty()) { %>
    <h3 style="margin-bottom:8px;">Subject-wise</h3>
    <div class="table-wrap" style="margin-bottom:18px;">
      <table class="lst">
        <thead><tr><th>Subject</th><th>Range</th><th>Attempted</th>
          <% if (subScored) { %><th>Correct</th><th>Wrong</th><th>Score</th><% } %></tr></thead>
        <tbody>
          <% for (SubjectScore ss : subs) { %>
          <tr>
            <td style="font-weight:600;color:var(--green-dark)"><%= ss.name %></td>
            <td>Q<%= ss.from %>–<%= ss.to %></td>
            <td><%= ss.attempted %>/<%= ss.questions %></td>
            <% if (subScored) { %>
              <td style="color:#15803D"><%= ss.correct %></td>
              <td style="color:#C0392B"><%= ss.wrong %></td>
              <td style="font-weight:700"><%= ss.score %></td>
            <% } %>
          </tr>
          <% } %>
        </tbody>
      </table>
    </div>
    <% } %>

    <div style="display:grid;grid-template-columns:1fr;gap:18px;">
      <div>
        <h3 style="margin-bottom:8px;">Detected Answers</h3>
        <div class="grid4">
          <% for (int c=0;c<4;c++){ int from=c*45+1, to=c*45+45; %>
            <table class="ans"><tbody>
            <% for (int q=from;q<=to;q++){ %>
              <tr><td class="q"><%= q %></td><td style="<%= cellStyle(result,q) %>"><%= cellText(result,q) %></td></tr>
            <% } %>
            </tbody></table>
          <% } %>
        </div>
        <div class="legend">
          <span style="background:#E8F7EF"></span>answered
          <span style="background:#FFF4D6"></span>ambiguous (?)
          <span style="background:#f4f4f4"></span>blank (–)
          <% if (result.isScored()) { %><span style="background:#D5F5E3"></span>correct<span style="background:#FADBD8"></span>wrong<% } %>
        </div>
      </div>
      <% if (overlayUrl != null) { %>
      <div class="overlay-wrap">
        <h3 style="margin-bottom:8px;">Detection Overlay <span style="font-size:11px;color:var(--muted);font-weight:400;">(green = read as filled)</span></h3>
        <img src="<%= overlayUrl %>" alt="overlay">
      </div>
      <% } %>
    </div>
  </div>
  <% } %>

  <div class="table-wrap">
    <table class="lst">
      <thead><tr><th>#</th><th>Title</th><th>Attempted</th><th>Blank</th><th>Amb.</th><th>Correct</th><th>Wrong</th><th>Score</th><th>By</th></tr></thead>
      <tbody>
        <% if (recent != null) for (OmrScan s : recent) { %>
          <tr>
            <td><%= s.getScanId() %></td>
            <td style="font-weight:600;color:var(--green-dark)"><%= s.getTitle()==null?"":s.getTitle() %></td>
            <td><%= s.getAttempted() %></td>
            <td><%= s.getBlank() %></td>
            <td><%= s.getAmbiguous() %></td>
            <td><%= s.getCorrect()==null?"—":s.getCorrect() %></td>
            <td><%= s.getWrong()==null?"—":s.getWrong() %></td>
            <td><%= s.getScore()==null?"—":s.getScore() %></td>
            <td><%= s.getScannedBy()==null?"":s.getScannedBy() %></td>
          </tr>
        <% } %>
      </tbody>
    </table>
  </div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="exams"/></jsp:include>
</body>
</html>
