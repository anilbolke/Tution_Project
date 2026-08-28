<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.tution.model.User" %>
<%--
  Mobile bottom tab bar. Role-aware for the same reason the header is: a
  counsellor's five tabs are sales work, a teacher's are academic, and neither
  should be shown the other's.
--%>
<%
    String tctx = request.getContextPath();
    String tactive = request.getParameter("active");
    if (tactive == null) tactive = "";
    User tUser = (User) session.getAttribute("user");
    boolean tCounsellor = tUser != null && tUser.isCounsellor();
    boolean tTeacher    = tUser != null && tUser.isTeacher();
    boolean tAccountant = tUser != null && tUser.isAccountant();
    boolean tHr         = tUser != null && tUser.isHr();
%>
<nav class="tabbar">
  <% if (tCounsellor) { %>
    <a href="<%= tctx %>/my-dashboard" class="<%= "home".equals(tactive)?"active":"" %>"><span class="ti">🏠</span>Home</a>
    <a href="<%= tctx %>/inquiries"    class="<%= "leads".equals(tactive)?"active":"" %>"><span class="ti">📥</span>Leads</a>
    <a href="<%= tctx %>/followup"     class="<%= "followups".equals(tactive)?"active":"" %>"><span class="ti">📞</span>Calls</a>
    <a href="<%= tctx %>/demo"         class="<%= "demos".equals(tactive)?"active":"" %>"><span class="ti">🎓</span>Counsellors</a>
    <a href="<%= tctx %>/search"       class="<%= "search".equals(tactive)?"active":"" %>"><span class="ti">🔍</span>Search</a>
  <% } else if (tTeacher) { %>
    <a href="<%= tctx %>/teacher-dashboard" class="<%= "home".equals(tactive)?"active":"" %>"><span class="ti">🏠</span>Home</a>
    <a href="<%= tctx %>/attendance" class="<%= "attendance".equals(tactive)?"active":"" %>"><span class="ti">📅</span>Attend</a>
    <a href="<%= tctx %>/exams"      class="<%= "exams".equals(tactive)?"active":"" %>"><span class="ti">🧪</span>Exams</a>
    <a href="<%= tctx %>/materials"  class="<%= "materials".equals(tactive)?"active":"" %>"><span class="ti">📚</span>Material</a>
    <a href="<%= tctx %>/omr"        class="<%= "omr".equals(tactive)?"active":"" %>"><span class="ti">📄</span>OMR</a>
  <% } else if (tAccountant) { %>
    <a href="<%= tctx %>/finance-dashboard" class="<%= "home".equals(tactive)?"active":"" %>"><span class="ti">🏠</span>Home</a>
    <a href="<%= tctx %>/fund"      class="<%= "fund".equals(tactive)?"active":"" %>"><span class="ti">🏦</span>Fund</a>
    <a href="<%= tctx %>/expenses"  class="<%= "expenses".equals(tactive)?"active":"" %>"><span class="ti">🧾</span>Expenses</a>
    <a href="<%= tctx %>/fees"      class="<%= "fees".equals(tactive)?"active":"" %>"><span class="ti">💰</span>Fees</a>
    <a href="<%= tctx %>/work-orders" class="<%= "workorders".equals(tactive)?"active":"" %>"><span class="ti">📋</span>Orders</a>
  <% } else if (tHr) { %>
    <a href="<%= tctx %>/hr-dashboard" class="<%= "home".equals(tactive)?"active":"" %>"><span class="ti">🏠</span>Home</a>
    <a href="<%= tctx %>/hr"        class="<%= "hr".equals(tactive)?"active":"" %>"><span class="ti">🗂️</span>HR</a>
    <a href="<%= tctx %>/staff"     class="<%= "staff".equals(tactive)?"active":"" %>"><span class="ti">👤</span>Staff</a>
    <a href="<%= tctx %>/inquiries" class="<%= "leads".equals(tactive)?"active":"" %>"><span class="ti">📥</span>Leads</a>
    <a href="<%= tctx %>/fees"      class="<%= "fees".equals(tactive)?"active":"" %>"><span class="ti">💰</span>Dues</a>
  <% } else { %>
    <a href="<%= tctx %>/dashboard.jsp" class="<%= "home".equals(tactive)?"active":"" %>"><span class="ti">🏠</span>Home</a>
    <a href="<%= tctx %>/students"      class="<%= "students".equals(tactive)?"active":"" %>"><span class="ti">👥</span>Students</a>
    <a href="<%= tctx %>/fees"          class="<%= "fees".equals(tactive)?"active":"" %>"><span class="ti">💰</span>Fees</a>
    <a href="<%= tctx %>/attendance"    class="<%= "attendance".equals(tactive)?"active":"" %>"><span class="ti">📅</span>Attend</a>
    <a href="<%= tctx %>/exams"         class="<%= "exams".equals(tactive)?"active":"" %>"><span class="ti">🧪</span>Exams</a>
  <% } %>
</nav>
