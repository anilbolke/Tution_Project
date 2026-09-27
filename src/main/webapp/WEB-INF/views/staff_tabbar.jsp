<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.tution.model.User, com.tution.model.Role" %>
<%--
  Mobile bottom tab bar: Home plus the first four screens this role may open,
  from a priority list that suits its kind of work (a counsellor's are sales, a
  teacher's academic). What "may open" means is the role/activity matrix — the
  same user.can() the header and AuthFilter use, so a tab never leads to a
  redirect.
--%>
<%
    String tctx = request.getContextPath();
    String tactive = request.getParameter("active");
    if (tactive == null) tactive = "";
    User tUser = (User) session.getAttribute("user");
    String tFamily = tUser == null ? "" : Role.familyOf(tUser.getRole());

    // { activity, path, active-key, icon, label }
    String[][] tAll;
    if ("COUNSELLOR".equals(tFamily)) {
        tAll = new String[][] {
            { "SALES_LEAD", "/inquiries", "leads", "📥", "Leads" },
            { "SALES_FOLLOWUP", "/followup", "followups", "📞", "Calls" },
            { "SALES_COUNSELLOR", "/demo", "demos", "🎓", "Counsellors" },
            { "SALES_LEAD", "/search", "search", "🔍", "Search" },
            { "STUDENT", "/students", "students", "👥", "Students" },
            { "FEES", "/fees", "fees", "💰", "Fees" } };
    } else if ("TEACHER".equals(tFamily)) {
        tAll = new String[][] {
            { "ACAD_ATTENDANCE", "/attendance", "attendance", "📅", "Attend" },
            { "ACAD_EXAM", "/exams", "exams", "🧪", "Exams" },
            { "ACAD_ONLINE_EXAM", "/online-exams", "onlineexams", "💻", "Online" },
            { "ACAD_MATERIAL", "/materials", "materials", "📚", "Material" },
            { "ACAD_OMR", "/omr", "omr", "📄", "OMR" },
            { "ACAD_TICKETS", "/manage-tickets", "tickets", "🎫", "Tickets" },
            { "STUDENT", "/students", "students", "👥", "Students" } };
    } else if ("ACCOUNTANT".equals(tFamily)) {
        tAll = new String[][] {
            { "FIN_FUND", "/fund", "fund", "🏦", "Fund" },
            { "FIN_EXPENSE", "/expenses", "expenses", "🧾", "Expenses" },
            { "FEES", "/fees", "fees", "💰", "Fees" },
            { "FIN_WORK_ORDER", "/work-orders", "workorders", "📋", "Orders" },
            { "FIN_VENDOR", "/vendors", "vendors", "🏢", "Vendors" } };
    } else if ("HR".equals(tFamily)) {
        tAll = new String[][] {
            { "HR_STAFF", "/staff", "staff", "👤", "Staff" },
            { "HR_STAFF", "/hr", "hr", "🗂️", "HR" },
            { "SALES_LEAD", "/inquiries", "leads", "📥", "Leads" },
            { "FEES", "/fees", "fees", "💰", "Dues" },
            { "STUDENT", "/students", "students", "👥", "Students" } };
    } else {
        tAll = new String[][] {
            { "STUDENT", "/students", "students", "👥", "Students" },
            { "FEES", "/fees", "fees", "💰", "Fees" },
            { "SALES_LEAD", "/inquiries", "leads", "📥", "Leads" },
            { "ACAD_ATTENDANCE", "/attendance", "attendance", "📅", "Attend" },
            { "ACAD_EXAM", "/exams", "exams", "🧪", "Exams" },
            { "FIN_FUND", "/fund", "fund", "🏦", "Fund" },
            { "ACAD_MATERIAL", "/materials", "materials", "📚", "Material" } };
    }
%>
<nav class="tabbar">
  <% if (tUser != null) { %>
    <a href="<%= tctx + tUser.homePath() %>" class="<%= "home".equals(tactive)?"active":"" %>"><span class="ti">🏠</span>Home</a>
    <%  int shown = 0;
        for (String[] t : tAll) {
            if (shown == 4) break;
            // /hr is decided by its tab, not the path table; bare /hr opens the attendance tab
            boolean ok = "/hr".equals(t[1]) ? tUser.can("HR_STAFF") && tUser.can("HR_ATTENDANCE")
                                            : tUser.mayOpen(t[1]);
            if (!ok) continue;
            shown++; %>
      <a href="<%= tctx + t[1] %>" class="<%= t[2].equals(tactive)?"active":"" %>"><span class="ti"><%= t[3] %></span><%= t[4] %></a>
    <% } %>
  <% } %>
</nav>
