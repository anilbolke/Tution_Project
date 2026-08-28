<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*, com.tution.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") Map<String,Object> d =
        (Map<String,Object>) request.getAttribute("d");
    if (d == null) d = new HashMap<String,Object>();
    @SuppressWarnings("unchecked") List<Map<String,Object>> classes =
        (List<Map<String,Object>>) request.getAttribute("classes");
    if (classes == null) classes = new ArrayList<Map<String,Object>>();
    @SuppressWarnings("unchecked") List<Map<String,Object>> exams =
        (List<Map<String,Object>>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<Map<String,Object>>();
    String today = (String) request.getAttribute("today");
    String error = (String) request.getAttribute("error");
    String[] MONTHS = {"","January","February","March","April","May","June",
                       "July","August","September","October","November","December"};
    int m = (Integer) request.getAttribute("m");

    int students = i(d.get("students"));
    int marked   = i(d.get("markedToday"));
    int absent   = i(d.get("absentToday"));
    int mMarks   = i(d.get("monthMarks"));
    int mPresent = i(d.get("monthPresent"));
    int rate     = mMarks == 0 ? 0 : (mPresent * 100 / mMarks);

    int classesToDo = 0;
    for (Map<String,Object> c : classes) {
        if (i(c.get("marked")) < i(c.get("students"))) classesToDo++;
    }
    int examsToDo = 0;
    for (Map<String,Object> e : exams) {
        if (i(e.get("pct")) < 100) examsToDo++;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Teacher Dashboard – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .dw { max-width:1180px; margin:18px auto; padding:0 14px; }
  .dh { margin:0 0 16px; }
  .dh h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .dh .sub { margin:3px 0 0; font-size:12.5px; color:#7b8b93; }

  .todo { display:grid; grid-template-columns:repeat(auto-fit,minmax(255px,1fr)); gap:12px;
          margin-bottom:18px; }
  .todo a { display:block; text-decoration:none; color:inherit; background:#fff;
            border:1px solid #e3e6e8; border-left:4px solid #0E5C3F; border-radius:10px;
            padding:14px 16px; }
  .todo a:hover { box-shadow:0 4px 14px rgba(14,92,63,.10); }
  .todo a.act { border-left-color:#c0392b; background:#fffaf9; }
  .todo a.done { border-left-color:#bfe0cc; }
  .todo .n { font-size:24px; font-weight:700; color:#0E5C3F; line-height:1.1;
             font-variant-numeric:tabular-nums; }
  .todo a.act .n { color:#c0392b; }
  .todo .l { font-size:13px; font-weight:650; color:#1f3a2c; margin-top:4px; }
  .todo .s { font-size:11.5px; color:#7b8b93; margin-top:3px; }

  .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:14px; align-items:start; }
  @media(max-width:900px){ .grid2 { grid-template-columns:1fr; } }

  .card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px;
          margin-bottom:14px; }
  .card h2 { margin:0 0 3px; font-size:15px; }
  .card h2 a { float:right; font-size:11.5px; font-weight:600; color:#0E5C3F;
               text-decoration:none; }
  .card p.hint { color:#5a6b73; font-size:12.5px; margin:0 0 13px; }

  .rowbar { display:flex; align-items:center; gap:11px; font-size:13px; padding:8px 0; }
  .rowbar + .rowbar { border-top:1px solid #f2f6f4; }
  .rowbar .nm { width:130px; color:#2f4a3c; font-weight:600; overflow:hidden;
                text-overflow:ellipsis; white-space:nowrap; }
  .rowbar .track { flex:1; min-width:50px; height:10px; background:#eef3f0; border-radius:6px;
                   overflow:hidden; }
  .rowbar .track i { display:block; height:100%; background:linear-gradient(90deg,#1b8a5c,#0E5C3F);
                     border-radius:6px; }
  .rowbar.todo0 .track i { background:#d7dedb; }
  .rowbar .vl { width:96px; text-align:right; font-variant-numeric:tabular-nums;
                font-size:12px; color:#5a6b73; }
  .rowbar .vl b { color:#1f3a2c; }

  .kv { display:grid; grid-template-columns:1fr auto; gap:9px 12px; font-size:13.5px; }
  .kv .k { color:#5a6b73; }
  .kv .v { font-weight:700; color:#1f3a2c; font-variant-numeric:tabular-nums; text-align:right; }
  .kv .v.warn { color:#c0392b; }
  .kv hr { grid-column:1/-1; border:0; border-top:1px solid #eceff1; margin:2px 0; }

  /* quick links: proper targets, not a bare list */
  .goto { display:grid; grid-template-columns:repeat(auto-fit,minmax(158px,1fr)); gap:10px; }
  .goto a { display:flex; align-items:center; gap:10px; text-decoration:none; color:inherit;
            border:1px solid #dfe7e2; border-radius:9px; padding:11px 13px; background:#fbfdfc; }
  .goto a:hover { border-color:#0E5C3F; background:#f2f9f5; }
  .goto .ic { width:30px; height:30px; flex:none; border-radius:8px; background:#eaf6ef;
              display:flex; align-items:center; justify-content:center; font-size:15px; }
  .goto .tx { min-width:0; }
  .goto .t1 { font-size:13px; font-weight:650; color:#1f3a2c; }
  .goto .t2 { font-size:11px; color:#8b9aa1; margin-top:1px; }

  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .muted { color:#7b8b93; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="home"/>
</jsp:include>

<div class="dw">

  <div class="dh">
    <h1>Good day, <%= esc(first(user.getFullName())) %></h1>
    <p class="sub"><%= esc(today) %> &middot; <%= students %> students across
       <%= i(d.get("classes")) %> class<%= i(d.get("classes"))==1?"":"es" %></p>
  </div>

  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <div class="todo">
    <a class="<%= classesToDo > 0 ? "act" : "done" %>" href="<%= ctx %>/attendance">
      <div class="n"><%= classesToDo %></div>
      <div class="l"><%= classesToDo == 0 ? "Attendance is done" : "Classes not fully marked" %></div>
      <div class="s"><%= classesToDo == 0
          ? marked + " marked, " + absent + " absent today"
          : "mark today&rsquo;s register &rarr;" %></div>
    </a>
    <a class="<%= examsToDo > 0 ? "act" : "done" %>" href="<%= ctx %>/exams">
      <div class="n"><%= examsToDo %></div>
      <div class="l"><%= examsToDo == 0 ? "All marks entered" : "Exams awaiting marks" %></div>
      <div class="s"><%= examsToDo == 0
          ? i(d.get("exams")) + " exam(s) on record"
          : "enter marks &rarr;" %></div>
    </a>
    <a class="<%= i(d.get("openTickets")) > 0 ? "act" : "done" %>" href="<%= ctx %>/manage-tickets">
      <div class="n"><%= i(d.get("openTickets")) %></div>
      <div class="l"><%= i(d.get("openTickets")) == 0 ? "No open concerns" : "Student concerns open" %></div>
      <div class="s"><%= i(d.get("openTickets")) == 0 ? "nothing waiting" : "reply &rarr;" %></div>
    </a>
  </div>

  <div class="grid2">
    <div>
      <div class="card">
        <h2>Today by class<a href="<%= ctx %>/attendance">Mark &rarr;</a></h2>
        <p class="hint">How much of each class register is filled in for <%= esc(today) %>.</p>
        <% for (Map<String,Object> c : classes) {
             int st = i(c.get("students")), mk = i(c.get("marked"));
             int pct = st == 0 ? 0 : mk * 100 / st; %>
          <div class="rowbar <%= mk == 0 ? "todo0" : "" %>">
            <span class="nm"><%= esc(String.valueOf(c.get("className"))) %></span>
            <span class="track"><i style="width:<%= pct %>%"></i></span>
            <span class="vl"><b><%= mk %></b> / <%= st %> marked</span>
          </div>
        <% } %>
        <% if (classes.isEmpty()) { %>
          <p class="muted" style="font-size:13px;margin:0">No active students yet.</p>
        <% } %>
      </div>

      <div class="card">
        <h2><%= MONTHS[m] %> attendance<a href="<%= ctx %>/attendance-report">Report &rarr;</a></h2>
        <div class="kv">
          <div class="k">Marks recorded</div><div class="v"><%= mMarks %></div>
          <div class="k">Present or late</div><div class="v"><%= mPresent %></div>
          <hr>
          <div class="k">Attendance rate</div>
          <div class="v <%= (mMarks>0 && rate<75) ? "warn" : "" %>"><%= rate %>%</div>
        </div>
      </div>
    </div>

    <div>
      <div class="card">
        <h2>Exams &amp; marks<a href="<%= ctx %>/exams">All exams &rarr;</a></h2>
        <p class="hint">How much of each exam&rsquo;s mark sheet is filled in.</p>
        <% for (Map<String,Object> e : exams) {
             int pct = i(e.get("pct")); %>
          <div class="rowbar <%= pct == 0 ? "todo0" : "" %>">
            <span class="nm" title="<%= esc(String.valueOf(e.get("name"))) %>"><%=
              esc(String.valueOf(e.get("name"))) %></span>
            <span class="track"><i style="width:<%= pct %>%"></i></span>
            <span class="vl"><b><%= pct %>%</b> entered</span>
          </div>
        <% } %>
        <% if (exams.isEmpty()) { %>
          <p class="muted" style="font-size:13px;margin:0">No exams yet.
             <a href="<%= ctx %>/exam-new">Create one &rarr;</a></p>
        <% } %>
      </div>

      <div class="card">
        <h2>Go to</h2>
        <div class="goto">
          <a href="<%= ctx %>/attendance"><span class="ic">📅</span>
            <span class="tx"><span class="t1">Attendance</span>
              <span class="t2">Mark today</span></span></a>
          <a href="<%= ctx %>/exam-marks"><span class="ic">✏️</span>
            <span class="tx"><span class="t1">Enter marks</span>
              <span class="t2">Per exam</span></span></a>
          <a href="<%= ctx %>/exam-results"><span class="ic">🏅</span>
            <span class="tx"><span class="t1">Results</span>
              <span class="t2">Ranks &amp; cards</span></span></a>
          <a href="<%= ctx %>/students"><span class="ic">👥</span>
            <span class="tx"><span class="t1">Students</span>
              <span class="t2"><%= students %> active</span></span></a>
          <a href="<%= ctx %>/materials"><span class="ic">📚</span>
            <span class="tx"><span class="t1">Materials</span>
              <span class="t2"><%= i(d.get("materials")) %> uploaded</span></span></a>
          <a href="<%= ctx %>/omr"><span class="ic">📄</span>
            <span class="tx"><span class="t1">OMR scan</span>
              <span class="t2">Answer sheets</span></span></a>
        </div>
      </div>
    </div>
  </div>

</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>

<%!
    private String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  b.append("&amp;");  break;
                case '<':  b.append("&lt;");   break;
                case '>':  b.append("&gt;");   break;
                case '"':  b.append("&quot;"); break;
                case '\'': b.append("&#39;");  break;
                default:   b.append(c);
            }
        }
        return b.toString();
    }
    private String first(String name) {
        if (name == null || name.isEmpty()) return "there";
        int sp = name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : name;
    }
    private int i(Object o) { return (o instanceof Integer) ? (Integer) o : 0; }
%>
