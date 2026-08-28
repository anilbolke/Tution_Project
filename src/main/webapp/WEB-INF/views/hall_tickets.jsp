<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.Exam, com.tution.model.ExamCandidate, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Exam exam = (Exam) request.getAttribute("exam");
    @SuppressWarnings("unchecked") List<ExamCandidate> list =
        (List<ExamCandidate>) request.getAttribute("candidates");
    if (list == null) list = new ArrayList<ExamCandidate>();
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Hall Tickets – <%= exam == null ? "Exam" : esc(exam.getExamName()) %></title>
<style>
  body { font-family: Arial, Helvetica, sans-serif; color:#111; margin:16px; background:#f4f6f7; }
  .noprint { margin-bottom:14px; }
  .sheet { max-width:900px; margin:0 auto; }
  .ticket {
    border:1.5px dashed #555; border-radius:4px; padding:12px 14px;
    margin-bottom:12px; background:#fff;
    /* Two per A4 half; never split a ticket across a page. */
    page-break-inside:avoid;
  }
  .t-head { display:flex; justify-content:space-between; align-items:flex-start;
            border-bottom:1px solid #ccc; padding-bottom:7px; margin-bottom:9px; }
  .t-head h1 { font-size:14px; margin:0; letter-spacing:.5px; }
  .t-head .sub { font-size:11px; color:#444; margin-top:2px; }
  .t-head .type { font-size:11px; border:1px solid #666; border-radius:3px; padding:2px 7px; }
  .grid { display:flex; gap:14px; }
  .col { flex:1; }
  .f { margin-bottom:5px; font-size:12px; }
  .f label { display:inline-block; width:96px; color:#555; }
  .f b { font-weight:bold; }
  .rollbox { border:2px solid #111; border-radius:4px; padding:6px 12px; text-align:center; min-width:132px; }
  .rollbox span { display:block; font-size:9px; letter-spacing:1px; color:#555; }
  .rollbox b { font-family:Consolas,monospace; font-size:23px; letter-spacing:4px; }
  .photo { width:78px; height:92px; border:1px solid #777; font-size:9px; color:#777;
           display:flex; align-items:center; justify-content:center; text-align:center; }
  .rules { margin-top:9px; padding-top:7px; border-top:1px dotted #bbb; font-size:10px; color:#333; }
  .rules ol { margin:3px 0 0 15px; padding:0; }
  .rules li { margin-bottom:1px; }
  .signrow { display:flex; justify-content:space-between; margin-top:12px; font-size:10px; color:#555; }
  .signrow div { border-top:1px solid #777; padding-top:2px; width:150px; text-align:center; }
  @media print {
    body { margin:0; background:#fff; }
    .noprint { display:none; }
    .ticket { margin:0 0 6mm; border-style:dashed; }
    .sheet { max-width:none; }
  }
</style>
</head>
<body>

<div class="noprint">
  <button onclick="window.print()">Print <%= list.size() %> hall ticket(s)</button>
  <a href="<%= ctx %>/candidates?examId=<%= exam == null ? 0 : exam.getExamId() %>">Back to candidates</a>
</div>

<div class="sheet">
<% for (ExamCandidate c : list) { %>
  <div class="ticket">
    <div class="t-head">
      <div>
        <h1>HAVELLSSON NEET SAMRAT</h1>
        <div class="sub">Prasha Building, Second Floor, Near Global Hospital, VIP Road, Nanded</div>
      </div>
      <div class="type">HALL TICKET<%= c.getExamType() == null ? "" : " &middot; " + esc(c.getExamType()) %></div>
    </div>

    <div class="grid">
      <div class="col">
        <div class="f"><label>Name</label><b><%= esc(c.getFullName()) %></b></div>
        <div class="f"><label>School</label><%= esc(c.getSchoolName()) %></div>
        <div class="f"><label>Class / Board</label><%= esc(c.getClassName()) %><%=
            c.getBoard() == null || c.getBoard().isEmpty() ? "" : " / " + esc(c.getBoard()) %></div>
        <div class="f"><label>Exam</label><%= esc(c.getExamName()) %></div>
        <div class="f"><label>Date</label><b><%= esc(c.getExamDate()) %></b></div>
        <div class="f"><label>Centre</label><b><%= c.getExamCentre() == null || c.getExamCentre().isEmpty()
            ? "To be announced" : esc(c.getExamCentre()) %></b></div>
        <div class="f"><label>Contact</label><%= esc(c.getMobile()) %></div>
      </div>
      <div style="text-align:center;">
        <div class="rollbox">
          <span>ROLL NUMBER</span>
          <b><%= esc(c.getRollNo()) %></b>
        </div>
        <div class="photo" style="margin:8px auto 0;">Affix recent<br>passport photo</div>
      </div>
    </div>

    <div class="rules">
      <b>Instructions</b>
      <ol>
        <li>Bring this hall ticket. You will not be allowed to sit the exam without it.</li>
        <li>Reach the centre 30 minutes before the start time.</li>
        <li><b>Bubble your roll number exactly as printed above</b> on the answer sheet. Marks cannot be
            matched to you if it is wrong or left blank.</li>
        <li>Use a black or blue ball pen. Fill each bubble completely; do not tick or cross.</li>
        <li>The paper has 60 questions. Marking is <b>+4 for a correct answer and &minus;1 for a wrong one</b>,
            so leave a question blank rather than guessing wildly.</li>
        <li>Mobile phones, smart watches and calculators are not permitted.</li>
      </ol>
    </div>

    <div class="signrow">
      <div>Candidate's signature</div>
      <div>Invigilator's signature</div>
    </div>
  </div>
<% } %>

<% if (list.isEmpty()) { %>
  <p style="text-align:center;color:#777;">No candidates match the current filter.</p>
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
