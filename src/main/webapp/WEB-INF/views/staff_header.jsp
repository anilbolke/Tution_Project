<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.tution.model.User" %>
<%--
  The one staff header, included by every staff screen.

  It used to be copy-pasted into 29 JSPs, each with its own hardcoded link list —
  which is why every role saw every module, and why the logo and "Dashboard"
  link always pointed at the institute-wide dashboard.

  What each role gets:
    ADMIN       everything
    STAFF       everything operational, minus the counsellor's own sales tile
    COUNSELLOR  sales only — no academics, no institute dashboard, no reports
    TEACHER     academics only — no money, no sales pipeline

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
    boolean hCounsellor = hUser != null && hUser.isCounsellor();
    boolean hTeacher    = hUser != null && hUser.isTeacher();
    boolean hAccountant = hUser != null && hUser.isAccountant();
    boolean hHr         = hUser != null && hUser.isHr();
    // Every question below is now an allow-list on User. They used to be written
    // as "not a teacher" / "not a counsellor", which silently offered each new
    // role the whole menu the moment ACCOUNTANT and HR were added.
    boolean hSales      = hUser != null && hUser.canSeeSales();
    boolean hLeads      = hUser != null && hUser.canSeeLeads();
    boolean hFees       = hUser != null && hUser.canSeeFees();
    boolean hAcademic   = hUser != null && hUser.canSeeAcademic();
    boolean hMgmt       = hUser != null && hUser.canSeeManagement();
    boolean hTargets    = hUser != null && hUser.canSeeTargets();
    boolean hFinance    = hUser != null && hUser.canSeeFinance();
    boolean hPeople     = hUser != null && hUser.canSeePeople();
    // Front-desk money work — matching ExamFeeServlet.
    boolean hOffice     = hUser != null && (hUser.isAdmin() || hUser.isStaff()
                                            || hUser.isAccountant());
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
      <% if (hCounsellor) { %>
        <a href="<%= hctx %>/my-dashboard" class="<%= "home".equals(hactive)?"active":"" %>">My Dashboard</a>
      <% } else if (hTeacher) { %>
        <a href="<%= hctx %>/teacher-dashboard" class="<%= "home".equals(hactive)?"active":"" %>">Dashboard</a>
        <a href="<%= hctx %>/attendance" class="<%= "attendance".equals(hactive)?"active":"" %>">Attendance</a>
      <% } else if (hAccountant) { %>
        <a href="<%= hctx %>/finance-dashboard" class="<%= "home".equals(hactive)?"active":"" %>">Dashboard</a>
      <% } else if (hHr) { %>
        <a href="<%= hctx %>/hr-dashboard" class="<%= "home".equals(hactive)?"active":"" %>">Dashboard</a>
        <a href="<%= hctx %>/hr" class="<%= "hr".equals(hactive)?"active":"" %>">HR</a>
      <% } else { %>
        <a href="<%= hctx %>/dashboard.jsp" class="<%= "home".equals(hactive)?"active":"" %>">Dashboard</a>
        <% if (hAdmin) { %>
          <a href="<%= hctx %>/my-dashboard" class="<%= "mysales".equals(hactive)?"active":"" %>">My Sales</a>
        <% } %>
      <% } %>

      <%-- Students and Fees stay top-level: near-daily, single links, used by
           almost every role — folding them into a menu would cost more clicks
           than it saves. --%>
      <% if (hUser != null && hUser.canSeeStudents()) { %>
        <a href="<%= hctx %>/students"  class="<%= "students".equals(hactive)?"active":"" %>">Students</a>
      <% } %>
      <% if (hFees) { %>
        <a href="<%= hctx %>/fees"      class="<%= "fees".equals(hactive)?"active":"" %>">Fees</a>
      <% } %>

      <%-- Sales: the pipeline, front to back. Search/Leads are HR's too;
           Follow-ups/Counsellors/Reminders are the counsellor's daily chase;
           Targets is management's view of the same pipeline. --%>
      <% if (hLeads || hSales || hTargets) { %>
        <span class="nav-drop<%= hSalesCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">Sales</button>
          <span class="nav-drop-menu">
            <% if (hLeads) { %>
              <a href="<%= hctx %>/search"    class="<%= "search".equals(hactive)?"active":"" %>">Search</a>
              <a href="<%= hctx %>/inquiries" class="<%= "leads".equals(hactive)?"active":"" %>">Leads</a>
            <% } %>
            <% if (hSales) { %>
              <a href="<%= hctx %>/followup"  class="<%= "followups".equals(hactive)?"active":"" %>">Follow-ups</a>
              <a href="<%= hctx %>/demo"      class="<%= "demos".equals(hactive)?"active":"" %>">Counsellors</a>
              <a href="<%= hctx %>/reminders" class="<%= "reminders".equals(hactive)?"active":"" %>">Reminders</a>
            <% } %>
            <% if (hTargets) { %>
              <a href="<%= hctx %>/targets"   class="<%= "targets".equals(hactive)?"active":"" %>">Targets</a>
            <% } %>
          </span>
        </span>
      <% } %>

      <%-- Academics: exams end-to-end (setup through results) plus attendance,
           materials and support tickets. Not a counsellor's job. --%>
      <% if (hAcademic) { %>
        <span class="nav-drop<%= hAcademicCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">Academics</button>
          <span class="nav-drop-menu">
            <% if (!hTeacher) { %>
              <a href="<%= hctx %>/attendance" class="<%= "attendance".equals(hactive)?"active":"" %>">Attendance</a>
            <% } %>
            <a href="<%= hctx %>/exams"          class="<%= "exams".equals(hactive)?"active":"" %>">Exams</a>
            <%-- Bulk lead import is office work, not a teacher's. --%>
            <% if (!hTeacher) { %>
              <a href="<%= hctx %>/exam-import"  class="<%= "import".equals(hactive)?"active":"" %>">Import</a>
            <% } %>
            <a href="<%= hctx %>/exam-setup"    class="<%= "setup".equals(hactive)?"active":"" %>">Exam Setup</a>
            <a href="<%= hctx %>/candidates"     class="<%= "candidates".equals(hactive)?"active":"" %>">Candidates</a>
            <a href="<%= hctx %>/exam-scan"     class="<%= "scan".equals(hactive)?"active":"" %>">Scan Sheets</a>
            <a href="<%= hctx %>/scholarship-results"  class="<%= "results".equals(hactive)?"active":"" %>">Results</a>
            <a href="<%= hctx %>/online-exams"   class="<%= "onlineexams".equals(hactive)?"active":"" %>">Online Exams</a>
            <a href="<%= hctx %>/online-exam-results" class="<%= "onlineresults".equals(hactive)?"active":"" %>">Online Results</a>
            <a href="<%= hctx %>/materials"      class="<%= "materials".equals(hactive)?"active":"" %>">Materials</a>
            <a href="<%= hctx %>/omr"            class="<%= "omr".equals(hactive)?"active":"" %>">OMR</a>
            <a href="<%= hctx %>/manage-tickets" class="<%= "tickets".equals(hactive)?"active":"" %>">Tickets</a>
          </span>
        </span>
      <% } %>

      <%-- Finance: the institute's own money (admin/accountant, matching
           AuthFilter) plus exam fees taken at the counter (office staff too). --%>
      <% if (hFinance || hOffice) { %>
        <span class="nav-drop<%= hFinanceCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">Finance</button>
          <span class="nav-drop-menu">
            <% if (hFinance) { %>
              <a href="<%= hctx %>/fund"      class="<%= "fund".equals(hactive)?"active":"" %>">Fund</a>
              <a href="<%= hctx %>/vendors"   class="<%= "vendors".equals(hactive)?"active":"" %>">Vendors</a>
              <a href="<%= hctx %>/work-orders" class="<%= "workorders".equals(hactive)?"active":"" %>">Work Orders</a>
              <a href="<%= hctx %>/expenses"  class="<%= "expenses".equals(hactive)?"active":"" %>">Expenses</a>
            <% } %>
            <% if (hOffice) { %>
              <a href="<%= hctx %>/exam-fees" class="<%= "examfees".equals(hactive)?"active":"" %>">Exam Fees</a>
            <% } %>
          </span>
        </span>
      <% } %>

      <%-- People: staff attendance, leave, payroll and the directory. --%>
      <% if (hPeople) { %>
        <span class="nav-drop<%= hPeopleCur ? " current" : "" %>">
          <button type="button" class="nav-drop-btn">People</button>
          <span class="nav-drop-menu">
            <a href="<%= hctx %>/hr"        class="<%= "hr".equals(hactive)?"active":"" %>">HR</a>
            <a href="<%= hctx %>/staff"     class="<%= "staff".equals(hactive)?"active":"" %>">Staff</a>
          </span>
        </span>
      <% } %>

      <%-- Reports are institute-wide, so they stay on the management side. The
           accountant gets Reports (most of them are financial) but not Targets,
           which is a management decision rather than a bookkeeping one. --%>
      <% if (hMgmt || hAccountant) { %>
        <a href="<%= hctx %>/reports"   class="<%= "reports".equals(hactive)?"active":"" %>">Reports</a>
      <% } %>
    </span>
    <% if (hUser != null) { %>
      <span class="user">👤 <%= hUser.getFullName() %> (<%= hUser.getRole() %>)</span>
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
