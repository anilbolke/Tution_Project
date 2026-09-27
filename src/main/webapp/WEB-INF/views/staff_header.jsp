<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.tution.model.User" %>
<%--
  The one staff header, included by every staff screen.

  It used to be copy-pasted into 29 JSPs, each with its own hardcoded link list —
  which is why every role saw every module, and why the logo and "Dashboard"
  link always pointed at the institute-wide dashboard.

  What each role gets: whatever its column in the role/activity matrix says
  (Doc/Mapping/Work Flow.xlsx, editable at /role-mapping). Every link below is
  shown only when user.can(<activity>) - the same question AuthFilter asks - so
  the menu and the doors always agree. ADMIN holds every activity.

  Usage:
    <jsp:include page="/WEB-INF/views/staff_header.jsp">
      <jsp:param name="active" value="leads"/>
    </jsp:include>
--%>
<%
    String hctx = request.getContextPath();
    String hactive = request.getParameter("active");
    if (hactive == null) hactive = "";
    User hUser = (User) session.getAttribute("user");

    boolean hAdmin      = hUser != null && hUser.isAdmin();
    // hCan.contains("X") == hUser.can("X"), read once per page
    java.util.Set<String> hCan = new java.util.HashSet<String>();
    if (hUser != null) {
        for (com.tution.dao.AccessDAO.Activity a : com.tution.dao.AccessDAO.activities()) {
            if (hUser.can(a.code)) hCan.add(a.code);
        }
    }
    boolean hLeads    = hCan.contains("SALES_LEAD");
    boolean hSalesAny = hLeads || hCan.contains("SALES_FOLLOWUP") || hCan.contains("SALES_COUNSELLOR")
                     || hCan.contains("SALES_REMINDER") || hCan.contains("SALES_TARGET");
    boolean hAcadAny = false, hFinAny = false, hRptAny = false;
    for (String c : hCan) {
        if (c.startsWith("ACAD_")) hAcadAny = true;
        if (c.startsWith("FIN_"))  hFinAny  = true;
        if (c.startsWith("RPT_"))  hRptAny  = true;
    }
    // /hr is the register of everyone - it needs HR_STAFF plus the tab's own activity
    String  hHrTab = !hCan.contains("HR_STAFF") ? null
                   : hCan.contains("HR_ATTENDANCE") ? "attendance"
                   : hCan.contains("HR_LEAVE")      ? "leave"
                   : hCan.contains("HR_SALARY")     ? "salary" : null;
    boolean hPeopleAny = hCan.contains("HR_STAFF");
    String  hHome       = hctx + (hUser == null ? "/dashboard.jsp" : hUser.homePath());

    // A role like ADMIN can see every link below, which used to mean 25+ items
    // in one flat row. They're grouped into a handful of dropdown menus instead;
    // these flags mark a group's button as "current" when the open page is one
    // of its own, so where you are stays visible even with the menu collapsed.
    java.util.Set<String> hSalesPages = new java.util.HashSet<String>(java.util.Arrays.asList(
        "search", "leads", "followups", "demos", "reminders", "targets"));
    java.util.Set<String> hAcademicPages = new java.util.HashSet<String>(java.util.Arrays.asList(
        "attendance", "exams", "import", "setup", "candidates", "scan",
        "results", "onlineexams", "onlineresults", "materials", "omr", "tickets"));
    java.util.Set<String> hFinancePages = new java.util.HashSet<String>(java.util.Arrays.asList(
        "fund", "vendors", "workorders", "expenses", "examfees"));
    java.util.Set<String> hPeoplePages = new java.util.HashSet<String>(java.util.Arrays.asList(
        "hr", "staff"));
    boolean hSalesCur    = hSalesPages.contains(hactive);
    boolean hAcademicCur = hAcademicPages.contains(hactive);
    boolean hFinanceCur  = hFinancePages.contains(hactive);
    boolean hPeopleCur   = hPeoplePages.contains(hactive);
%>
<header>
  <a class="logo" href="<%= hHome %>">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Tuition Management System</span>
    </div>
  </a>
  <nav>
    <span class="sp-topnav">
      <a href="<%= hHome %>" class="<%= "home".equals(hactive)?"active":"" %>">Dashboard</a>
      <% if (hCan.contains("MY_SALES") && !hHome.endsWith("/my-dashboard")) { %>
        <a href="<%= hctx %>/my-dashboard" class="<%= "mysales".equals(hactive)?"active":"" %>">My Sales</a>
      <% } %>

      <%-- Students and Fees stay top-level: near-daily, single links, used by
           almost every role - folding them into a menu would cost more clicks
           than it saves. --%>
      <% if (hCan.contains("STUDENT")) { %>
        <a href="<%= hctx %>/students"  class="<%= "students".equals(hactive)?"active":"" %>">Students</a>
      <% } %>
      <% if (hCan.contains("FEES")) { %>
        <a href="<%= hctx %>/fees"      class="<%= "fees".equals(hactive)?"active":"" %>">Fees</a>
      <% } %>

      <% if (hSalesAny) { %>
        <span class="nav-drop<%= hSalesCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">Sales</button>
          <span class="nav-drop-menu">
            <% if (hLeads) { %>
              <a href="<%= hctx %>/search"    class="<%= "search".equals(hactive)?"active":"" %>">Search</a>
              <a href="<%= hctx %>/inquiries" class="<%= "leads".equals(hactive)?"active":"" %>">Leads</a>
            <% } %>
            <% if (hCan.contains("SALES_FOLLOWUP")) { %>
              <a href="<%= hctx %>/followup"  class="<%= "followups".equals(hactive)?"active":"" %>">Follow-ups</a>
            <% } %>
            <% if (hCan.contains("SALES_COUNSELLOR")) { %>
              <a href="<%= hctx %>/demo"      class="<%= "demos".equals(hactive)?"active":"" %>">Counsellors</a>
            <% } %>
            <% if (hCan.contains("SALES_REMINDER")) { %>
              <a href="<%= hctx %>/reminders" class="<%= "reminders".equals(hactive)?"active":"" %>">Reminders</a>
            <% } %>
            <%-- a counsellor/ABM gets a read-only view of their own/team's targets --%>
            <% if (hCan.contains("SALES_TARGET")) { %>
              <a href="<%= hctx %>/targets"   class="<%= "targets".equals(hactive)?"active":"" %>">Targets</a>
            <% } %>
          </span>
        </span>
      <% } %>

      <% if (hAcadAny) { %>
        <span class="nav-drop<%= hAcademicCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">Academics</button>
          <span class="nav-drop-menu">
            <%  String[][] hAcad = {
                    { "ACAD_ATTENDANCE",    "/attendance",          "attendance",    "Attendance" },
                    { "ACAD_EXAM",          "/exams",               "exams",         "Exams" },
                    { "ACAD_IMPORT",        "/exam-import",         "import",        "Import" },
                    { "ACAD_EXAM_SETUP",    "/exam-setup",          "setup",         "Exam Setup" },
                    { "ACAD_CANDIDATE",     "/candidates",          "candidates",    "Candidates" },
                    { "ACAD_SCAN",          "/exam-scan",           "scan",          "Scan Sheets" },
                    { "ACAD_RESULT",        "/scholarship-results", "results",       "Results" },
                    { "ACAD_ONLINE_EXAM",   "/online-exams",        "onlineexams",   "Online Exams" },
                    { "ACAD_ONLINE_RESULT", "/online-exam-results", "onlineresults", "Online Results" },
                    { "ACAD_MATERIAL",      "/materials",           "materials",     "Materials" },
                    { "ACAD_OMR",           "/omr",                 "omr",           "OMR" },
                    { "ACAD_TICKETS",       "/manage-tickets",      "tickets",       "Tickets" } };
                for (String[] l : hAcad) { if (!hCan.contains(l[0])) continue; %>
              <a href="<%= hctx + l[1] %>" class="<%= l[2].equals(hactive)?"active":"" %>"><%= l[3] %></a>
            <% } %>
          </span>
        </span>
      <% } %>

      <% if (hFinAny) { %>
        <span class="nav-drop<%= hFinanceCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">Finance</button>
          <span class="nav-drop-menu">
            <%  String[][] hFin = {
                    { "FIN_FUND",       "/fund",        "fund",       "Fund" },
                    { "FIN_VENDOR",     "/vendors",     "vendors",    "Vendors" },
                    { "FIN_WORK_ORDER", "/work-orders", "workorders", "Work Orders" },
                    { "FIN_EXPENSE",    "/expenses",    "expenses",   "Expenses" },
                    { "FIN_EXAM_FEES",  "/exam-fees",   "examfees",   "Exam Fees" } };
                for (String[] l : hFin) { if (!hCan.contains(l[0])) continue; %>
              <a href="<%= hctx + l[1] %>" class="<%= l[2].equals(hactive)?"active":"" %>"><%= l[3] %></a>
            <% } %>
          </span>
        </span>
      <% } %>

      <% if (hPeopleAny) { %>
        <span class="nav-drop<%= hPeopleCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">People</button>
          <span class="nav-drop-menu">
            <% if (hHrTab != null) { %>
              <a href="<%= hctx %>/hr?tab=<%= hHrTab %>" class="<%= "hr".equals(hactive)?"active":"" %>">HR</a>
            <% } %>
            <a href="<%= hctx %>/staff"     class="<%= "staff".equals(hactive)?"active":"" %>">Staff</a>
          </span>
        </span>
      <% } %>

      <%-- your OWN attendance / leave / payslips (not the /hr register of everyone) --%>
      <% if (hCan.contains("HR_ATTENDANCE") || hCan.contains("HR_LEAVE") || hCan.contains("HR_SALARY")) { %>
        <a href="<%= hctx %>/my-hr" class="<%= "myhr".equals(hactive)?"active":"" %>">My HR</a>
      <% } %>

      <% if (hRptAny) { %>
        <a href="<%= hctx %>/reports"   class="<%= "reports".equals(hactive)?"active":"" %>">Reports</a>
      <% } %>

      <%-- System pages - administrator only, outside the matrix. --%>
      <% if (hAdmin) { %>
        <a href="<%= hctx %>/role-mapping" class="<%= "rolemapping".equals(hactive)?"active":"" %>">Role Mapping</a>
      <% } %>
    </span>
    <% if (hUser != null) { %>
      <span class="user">👤 <%= hUser.getFullName() %> (<%= hUser.getRoleLabel() %>)</span>
    <% } %>
    <a class="logout" href="<%= hctx %>/logout">Logout</a>
  </nav>
</header>
<script>
// Click-to-open group menus (Sales/Academics/Finance/People). Delegated and
// idempotent so it's safe however many times this include appears per page.
(function () {
  document.querySelectorAll('.nav-drop > .nav-drop-btn').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      var drop = btn.parentElement;
      var wasOpen = drop.classList.contains('open');
      document.querySelectorAll('.nav-drop.open').forEach(function (d) { d.classList.remove('open'); });
      if (!wasOpen) drop.classList.add('open');
    });
  });
  document.addEventListener('click', function () {
    document.querySelectorAll('.nav-drop.open').forEach(function (d) { d.classList.remove('open'); });
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('.nav-drop.open').forEach(function (d) { d.classList.remove('open'); });
    }
  });
})();
</script>
