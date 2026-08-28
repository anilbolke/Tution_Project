<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.Material" %>
<%
    Student student = (Student) session.getAttribute("student");
    if (student == null) { response.sendRedirect(request.getContextPath() + "/student-login.jsp"); return; }
    String ctx = request.getContextPath();
    @SuppressWarnings("unchecked") List<Material> materials = (List<Material>) request.getAttribute("materials");
    String type = (String) request.getAttribute("type");
    boolean video = "VIDEO".equals(type);
    String error = (String) request.getAttribute("error");
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
  <title><%= video?"E-Content":"Study Material" %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <link rel="stylesheet" href="<%= ctx %>/css/student.css">
  <style>
    .back-link { display:inline-block; margin-bottom:14px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
    h2.t { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:4px; }
    .sub-t { font-size:13px; color:var(--muted); margin-bottom:18px; }
    .res-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(240px,1fr)); gap:16px; }
    .res-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow:hidden; text-decoration:none; color:var(--text); transition:transform .15s, box-shadow .15s; display:block; }
    .res-card:hover { transform:translateY(-3px); box-shadow:0 8px 28px rgba(26,122,74,0.16); }
    .thumb { position:relative; aspect-ratio:16/9; background:#000; }
    .thumb img { width:100%; height:100%; object-fit:cover; display:block; }
    .play { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; font-size:44px; color:#fff; text-shadow:0 2px 8px rgba(0,0,0,.5); }
    .pdf-top { height:96px; background:linear-gradient(135deg,var(--green-dark),var(--green-mid)); display:flex; align-items:center; justify-content:center; font-size:42px; }
    .res-body { padding:14px 16px; }
    .res-body h3 { font-size:14px; color:var(--green-dark); margin-bottom:5px; }
    .meta { font-size:12px; color:var(--muted); }
    .pill { display:inline-block; background:var(--green-light); color:var(--green-dark); font-size:11px; font-weight:600; padding:2px 8px; border-radius:10px; margin-top:8px; }
    .empty { text-align:center; padding:48px 20px; color:var(--muted); background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); }
    .empty .ic { font-size:42px; margin-bottom:12px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }

    /* ── Reader: the PDF opens over the page, not in a new tab ── */
    .rd-back { position:fixed; inset:0; background:rgba(8,26,17,.72); z-index:900;
               display:none; align-items:center; justify-content:center; padding:18px; }
    .rd-back.on { display:flex; }
    .rd { background:#fff; border-radius:12px; width:min(1000px,100%); height:100%;
          max-height:calc(100vh - 36px); display:flex; flex-direction:column; overflow:hidden;
          box-shadow:0 18px 60px rgba(0,0,0,.45); }
    .rd-head { display:flex; align-items:center; gap:12px; padding:11px 14px;
               background:var(--green-dark); color:#fff; flex:0 0 auto; }
    .rd-head h3 { font-size:14.5px; font-weight:700; margin:0; flex:1 1 auto;
                  overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .rd-head .sub { font-size:11.5px; opacity:.8; font-weight:500; }
    .rd-x { background:rgba(255,255,255,.16); border:0; color:#fff; width:30px; height:30px;
            border-radius:50%; font-size:17px; line-height:1; cursor:pointer; flex:0 0 auto; }
    .rd-x:hover { background:rgba(255,255,255,.3); }
    /* Pages are drawn onto canvases, so there is no browser PDF viewer and
       therefore no viewer toolbar to hide - the same in every browser. */
    .rd-body { flex:1 1 auto; overflow:auto; background:#525659; padding:14px 0;
               -webkit-overflow-scrolling:touch; }
    .rd-page { display:block; margin:0 auto 14px; background:#fff; max-width:calc(100% - 24px);
               box-shadow:0 2px 10px rgba(0,0,0,.4); }
    .rd-load { color:#fff; text-align:center; padding:40px 20px; font-size:13.5px; }
    .rd-load .sp { display:inline-block; width:26px; height:26px; margin-bottom:10px;
                   border:3px solid rgba(255,255,255,.25); border-top-color:#fff;
                   border-radius:50%; animation:rdspin .8s linear infinite; }
    @keyframes rdspin { to { transform:rotate(360deg); } }
    .rd-err { color:#FFD9D4; text-align:center; padding:40px 20px; font-size:13.5px; }
    .rd-note { flex:0 0 auto; padding:8px 14px; font-size:11.5px; color:var(--muted);
               background:#F6FAF7; border-top:1px solid var(--border); text-align:center; }
    .rd-note b { color:var(--green-dark); }
    @media (max-width:620px) { .rd-back { padding:0; } .rd { border-radius:0; max-height:100vh; } }
  </style>
</head>
<body>
<header>
  <a class="logo" href="<%= ctx %>/student-dashboard.jsp">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Student Portal</span>
    </div>
  </a>
  <nav>
    <span class="sp-topnav">
      <a href="<%= ctx %>/student-dashboard.jsp">Home</a>
      <a href="<%= ctx %>/student-attendance">Attendance</a>
      <a href="<%= ctx %>/student-resources?type=PDF" class="<%= video?"":"active" %>">Study</a>
      <a href="<%= ctx %>/student-resources?type=VIDEO" class="<%= video?"active":"" %>">Videos</a>
      <a href="<%= ctx %>/student-exams">Exams</a>
      <a href="<%= ctx %>/student-tickets">Support</a>
    </span>
    <span class="user sp-username">🎓 <%= student.getFullName() %></span>
    <a class="logout" href="<%= ctx %>/student-logout">Logout</a>
  </nav>
</header>
<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">
  <h2 class="t"><%= video?"▶️ E-Content":"📄 Study Material" %></h2>
  <p class="sub-t">For your class: <strong><%= student.getClassName()==null?"All":esc(student.getClassName()) %></strong></p>

  <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

  <% if (materials == null || materials.isEmpty()) { %>
    <div class="empty"><div class="ic"><%= video?"🎬":"🗂️" %></div><p>No <%= video?"videos":"study material" %> available yet.</p></div>
  <% } else { %>
    <div class="res-grid">
      <% for (Material m : materials) {
            if (video) {
               String vid = m.youtubeId(); %>
        <a class="res-card" href="https://www.youtube.com/watch?v=<%= vid %>" target="_blank" rel="noopener">
          <div class="thumb">
            <img src="https://img.youtube.com/vi/<%= vid %>/hqdefault.jpg" alt="">
            <div class="play">▶</div>
          </div>
          <div class="res-body">
            <h3><%= esc(m.getTitle()) %></h3>
            <div class="meta"><%= m.getSubject()==null?"":esc(m.getSubject()) %></div>
          </div>
        </a>
      <%   } else { %>
        <%-- Opens in the reader over this page. No direct file URL is put on the
             page at all, so there is nothing to right-click and save as, and
             nothing to forward to somebody who is not a student here. --%>
        <a class="res-card" href="#" role="button"
           onclick="openReader(<%= m.getMaterialId() %>, this.dataset.t, this.dataset.s); return false;"
           data-t="<%= esc(m.getTitle()) %>"
           data-s="<%= m.getSubject()==null?"":esc(m.getSubject()) %>">
          <div class="pdf-top">📄</div>
          <div class="res-body">
            <h3><%= esc(m.getTitle()) %></h3>
            <div class="meta"><%= m.getSubject()==null?"":esc(m.getSubject()) %> · <%= m.getCreatedAt()!=null?m.getCreatedAt().substring(0,Math.min(10,m.getCreatedAt().length())):"" %></div>
            <span class="pill">👁 Read</span>
          </div>
        </a>
      <%   } } %>
    </div>
  <% } %>
</div>

<%-- ────────── the reader ────────── --%>
<div class="rd-back" id="rdBack" onclick="if(event.target===this) closeReader();">
  <div class="rd" role="dialog" aria-modal="true" aria-labelledby="rdTitle">
    <div class="rd-head">
      <div style="min-width:0;flex:1 1 auto">
        <h3 id="rdTitle">&nbsp;</h3>
        <div class="sub" id="rdSub">&nbsp;</div>
      </div>
      <button class="rd-x" type="button" onclick="closeReader()" aria-label="Close">&times;</button>
    </div>
    <div class="rd-body" id="rdBody"></div>
    <div class="rd-note" id="rdNote">Reading view — for study only. Please do not share outside your batch.</div>
  </div>
</div>

<script src="<%= ctx %>/js/pdfjs/pdf.min.js?v=3.11.174"></script>
<script>
  /* Every page is rendered onto a <canvas> by PDF.js rather than handed to the
     browser's own PDF viewer. That viewer is where the download and print
     buttons live, and Firefox ignores the #toolbar=0 hint that hides them in
     Chrome - so the only way to get the same behaviour everywhere is to not use
     it at all.

     This still does not make the file unobtainable: PDF.js has to fetch the
     bytes to draw them, and a screenshot needs no bytes. It removes the offer,
     which is what was asked for. The access check in StudentMaterialServlet is
     the part that actually protects anything. */
  pdfjsLib.GlobalWorkerOptions.workerSrc = '<%= ctx %>/js/pdfjs/pdf.worker.min.js?v=3.11.174';

  var rdBack = document.getElementById('rdBack');
  var rdBody = document.getElementById('rdBody');
  var rdNote = document.getElementById('rdNote');
  var rdTask = null;               // the in-flight load, so a fast close cancels it
  var rdToken = 0;                 // guards against a slow load painting over a newer one

  function openReader(id, title, subject) {
    document.getElementById('rdTitle').textContent = title || 'Study material';
    document.getElementById('rdSub').textContent   = subject || '';
    rdBack.classList.add('on');
    document.body.style.overflow = 'hidden';
    render(id, ++rdToken);
  }

  function render(id, token) {
    rdBody.innerHTML = '<div class="rd-load"><div class="sp"></div><div>Opening…</div></div>';
    rdNote.textContent = 'Reading view — for study only. Please do not share outside your batch.';

    rdTask = pdfjsLib.getDocument({
      url: '<%= ctx %>/student-material?id=' + id,
      // The session cookie has to go with it or the servlet refuses the request.
      withCredentials: true
    });

    rdTask.promise.then(function (pdf) {
      if (token !== rdToken) return;                 // a newer document won
      rdBody.innerHTML = '';
      rdNote.innerHTML = '<b>' + pdf.numPages + ' page' + (pdf.numPages === 1 ? '' : 's') +
        '</b> — reading view, for study only. Please do not share outside your batch.';

      // Pages are drawn in order rather than all at once: a 40-page PDF would
      // otherwise start forty renders in parallel and lock up a cheap phone.
      var page = 1;
      (function next() {
        if (page > pdf.numPages || token !== rdToken) return;
        pdf.getPage(page).then(function (p) {
          if (token !== rdToken) return;
          var wrap  = rdBody.clientWidth || 900;
          var base  = p.getViewport({ scale: 1 });
          // Cap the pixel ratio: retina at full scale on a 40-page document
          // eats memory for no visible gain at this size.
          var ratio = Math.min(window.devicePixelRatio || 1, 2);
          var scale = ((wrap - 34) / base.width) * ratio;
          var vp    = p.getViewport({ scale: scale });

          var canvas = document.createElement('canvas');
          canvas.className = 'rd-page';
          canvas.width  = Math.floor(vp.width);
          canvas.height = Math.floor(vp.height);
          canvas.style.width = Math.floor(vp.width / ratio) + 'px';
          rdBody.appendChild(canvas);

          return p.render({ canvasContext: canvas.getContext('2d'), viewport: vp }).promise
                  .then(function () { page++; next(); });
        }).catch(function () { page++; next(); });    // one bad page must not stop the rest
      })();
    }).catch(function (err) {
      if (token !== rdToken) return;
      var msg = (err && (err.status === 403))
        ? 'That material is not for your class.'
        : (err && err.status === 401)
          ? 'Your session has expired. Please sign in again.'
          : 'Sorry, this file could not be opened.';
      rdBody.innerHTML = '<div class="rd-err">' + msg + '</div>';
    });
  }

  function closeReader() {
    rdToken++;                                     // anything still loading is now stale
    if (rdTask && rdTask.destroy) { try { rdTask.destroy(); } catch (e) {} }
    rdTask = null;
    rdBack.classList.remove('on');
    rdBody.innerHTML = '';                         // frees the canvases
    document.body.style.overflow = '';
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && rdBack.classList.contains('on')) closeReader();
  });
</script>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<nav class="tabbar">
  <a href="<%= ctx %>/student-dashboard.jsp"><span class="ti">🏠</span>Home</a>
  <a href="<%= ctx %>/student-attendance"><span class="ti">📅</span>Attendance</a>
  <a href="<%= ctx %>/student-resources?type=PDF" class="<%= video?"":"active" %>"><span class="ti">📄</span>Study</a>
  <a href="<%= ctx %>/student-resources?type=VIDEO" class="<%= video?"active":"" %>"><span class="ti">▶️</span>Videos</a>
  <a href="<%= ctx %>/student-exams"><span class="ti">📝</span>Exams</a>
  <a href="<%= ctx %>/student-tickets"><span class="ti">🎫</span>Support</a>
</nav>
</body>
</html>
