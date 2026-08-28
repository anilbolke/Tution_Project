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
    String centre = (String) request.getAttribute("centre");
    String school = (String) request.getAttribute("school");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Roll List – <%= exam == null ? "Exam" : esc(exam.getExamName()) %></title>
<style>
  body { font-family: Arial, Helvetica, sans-serif; color:#111; margin:22px; }
  .head { text-align:center; margin-bottom:14px; }
  .head h1 { font-size:19px; margin:0 0 3px; letter-spacing:.5px; }
  .head h2 { font-size:15px; margin:0 0 6px; font-weight:600; }
  .meta { font-size:12px; color:#333; }
  .meta span { margin:0 9px; }
  table { width:100%; border-collapse:collapse; font-size:12px; }
  th, td { border:1px solid #999; padding:5px 7px; text-align:left; }
  th { background:#eee; font-size:11px; text-transform:uppercase; letter-spacing:.3px; }
  .roll { font-family:Consolas,monospace; font-weight:bold; letter-spacing:1px; }
  /* Wide signature box: this column is filled in by hand in the hall. */
  .sign { width:130px; }
  .noprint { margin-bottom:14px; }
  tfoot td { border:none; padding-top:16px; font-size:12px; }
  @media print {
    body { margin:10mm; }
    .noprint { display:none; }
    thead { display:table-header-group; }   /* repeat the header on every page */
    tr { page-break-inside:avoid; }
  }
</style>
</head>
<body>

<div class="noprint">
  <button onclick="window.print()">Print</button>
  <a href="<%= ctx %>/candidates?examId=<%= exam == null ? 0 : exam.getExamId() %>">Back to candidates</a>
</div>

<div class="head">
  <h1>HAVELLSSON NEET SAMRAT</h1>
  <h2><%= exam == null ? "" : esc(exam.getExamName()) %>
      <%= exam == null || exam.getExamType() == null ? "" : "(" + esc(exam.getExamType()) + ")" %></h2>
  <div class="meta">
    <span><b>Date:</b> <%= exam == null ? "" : esc(exam.getExamDate()) %></span>
    <% if (centre != null && !centre.isEmpty()) { %><span><b>Centre:</b> <%= esc(centre) %></span><% } %>
    <% if (school != null && !school.isEmpty()) { %><span><b>School:</b> <%= esc(school) %></span><% } %>
    <span><b>Candidates:</b> <%= list.size() %></span>
  </div>
</div>

<table>
  <thead>
    <tr>
      <th style="width:26px;">#</th>
      <th style="width:74px;">Roll No</th>
      <th>Name</th>
      <th>School</th>
      <th style="width:46px;">Class</th>
      <th style="width:56px;">Booklet</th>
      <th class="sign">Signature</th>
    </tr>
  </thead>
  <tbody>
  <% int n = 0; for (ExamCandidate c : list) { n++; %>
    <tr>
      <td><%= n %></td>
      <td class="roll"><%= esc(c.getRollNo()) %></td>
      <td><%= esc(c.getFullName()) %></td>
      <td><%= esc(c.getSchoolName()) %></td>
      <td><%= esc(c.getClassName()) %></td>
      <td><%= c.getBookletCode() == null ? "" : esc(c.getBookletCode()) %></td>
      <td class="sign">&nbsp;</td>
    </tr>
  <% } %>
  </tbody>
  <tfoot>
    <tr><td colspan="7">
      Present: ____________ &nbsp;&nbsp; Absent: ____________ &nbsp;&nbsp;&nbsp;
      Invigilator name &amp; signature: ______________________________
    </td></tr>
  </tfoot>
</table>

<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
%>
</body>
</html>
