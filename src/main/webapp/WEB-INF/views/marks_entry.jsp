<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.Exam, com.tution.model.Student, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Exam exam = (Exam) request.getAttribute("exam");
    if (exam == null) { response.sendRedirect(ctx + "/exams"); return; }
    @SuppressWarnings("unchecked") List<Student> students = (List<Student>) request.getAttribute("students");
    @SuppressWarnings("unchecked") Map<Integer,Map<String,Integer>> marks = (Map<Integer,Map<String,Integer>>) request.getAttribute("marks");
    boolean saved = Boolean.TRUE.equals(request.getAttribute("saved"));
    String error = (String) request.getAttribute("error");
    String[] subjects = exam.subjectList();
    int max = exam.getMaxPerSubject();
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Enter Marks – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .list-head { margin-bottom:14px; }
    .list-head h2 { font-family:'Playfair Display',serif; font-size:23px; color:var(--green-dark); }
    .list-head p { font-size:13px; color:var(--muted); margin-top:3px; }
    .alert { padding:11px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; font-weight:500; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid var(--border); }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .back-link { display:inline-block; margin-bottom:12px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
    .table-wrap { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow-x:auto; }
    table.mk { width:100%; border-collapse:collapse; font-size:13px; min-width:560px; }
    table.mk th { background:var(--green); color:#fff; text-align:left; padding:11px 12px; font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    table.mk td { padding:8px 12px; border-bottom:1px solid var(--border); }
    table.mk tr:last-child td { border-bottom:none; }
    table.mk tr:nth-child(even) td { background:var(--green-pale); }
    .nm { font-weight:700; color:var(--green-dark); }
    table.mk input { width:64px; padding:7px 8px; font-size:14px; border:1.5px solid var(--border); border-radius:6px; background:#fff; text-align:center; font-family:'Inter',sans-serif; }
    table.mk input:focus { border-color:var(--green); outline:none; box-shadow:0 0 0 3px rgba(26,122,74,0.13); }
    .save-bar { position:sticky; bottom:0; background:var(--white); border-top:1px solid var(--border); padding:14px; display:flex; justify-content:flex-end; gap:10px; border-radius:0 0 var(--radius) var(--radius); }
    .btn { padding:10px 18px; border:none; border-radius:7px; font-size:14px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; text-decoration:none; display:inline-block; }
    .btn-res { background:transparent; color:var(--green-dark); border:1.5px solid var(--green-dark); }
    .btn-save { background:var(--green); color:#fff; }
    .empty { text-align:center; padding:42px 20px; color:var(--muted); }
    .empty .ic { font-size:40px; margin-bottom:10px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="exams"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <a class="back-link" href="<%= ctx %>/exams">← Back to Exams</a>
  <div class="list-head">
    <h2>Enter Marks — <%= esc(exam.getExamName()) %></h2>
    <p><%= exam.getClassName()==null||"null".equals(exam.getClassName())?"All classes":esc(exam.getClassName()) %> · max <%= max %> per subject · <%= esc(exam.getExamDate()) %></p>
  </div>

  <% if (saved) { %><div class="alert ok">✓ Marks saved. <a href="<%= ctx %>/exam-results?examId=<%= exam.getExamId() %>" style="color:var(--success);font-weight:700;">View results →</a></div><% } %>
  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <% if (students == null || students.isEmpty() || subjects.length == 0) { %>
    <div class="table-wrap"><div class="empty"><div class="ic">📝</div><p>No students in this class to mark.</p></div></div>
  <% } else { %>
    <form method="post" action="<%= ctx %>/exam-marks">
      <input type="hidden" name="examId" value="<%= exam.getExamId() %>"/>
      <div class="table-wrap">
        <table class="mk">
          <thead>
            <tr><th>#</th><th>Student</th><% for (String s : subjects) { %><th><%= esc(s) %><br><small>/<%= max %></small></th><% } %></tr>
          </thead>
          <tbody>
          <% int i=1; for (Student st : students) {
                int sid = st.getStudentId();
                Map<String,Integer> sm = (marks==null)?null:marks.get(sid); %>
            <tr>
              <td><%= i++ %></td>
              <td class="nm"><%= esc(st.getFullName()) %></td>
              <% for (int j=0; j<subjects.length; j++) {
                    Integer v = (sm==null)?null:sm.get(subjects[j]);
                    String val = (v==null)?"":String.valueOf(v); %>
                <td><input type="number" name="mark_<%=sid%>_<%=j%>" min="0" max="<%=max%>" value="<%= val %>" placeholder="-"/></td>
              <% } %>
            </tr>
          <% } %>
          </tbody>
        </table>
        <div class="save-bar">
          <a class="btn btn-res" href="<%= ctx %>/exam-results?examId=<%= exam.getExamId() %>">View Results</a>
          <button type="submit" class="btn btn-save">💾 Save Marks</button>
        </div>
      </div>
    </form>
  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="exams"/></jsp:include>

</body>
</html>
