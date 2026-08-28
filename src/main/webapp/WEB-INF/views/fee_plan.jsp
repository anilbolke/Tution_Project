<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Student, com.tution.model.StudentFee,
                 com.tution.model.FeeInstallment, com.tution.model.FeeSlab, com.tution.model.FeePlan, com.tution.model.User,
                 com.tution.service.FeeService" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    Student s        = (Student) request.getAttribute("student");
    StudentFee led   = (StudentFee) request.getAttribute("ledger");
    FeeSlab slab     = (FeeSlab) request.getAttribute("slab");
    FeeService.Position pos = (FeeService.Position) request.getAttribute("position");
    @SuppressWarnings("unchecked")
    List<FeeInstallment> schedule = (List<FeeInstallment>) request.getAttribute("schedule");
    FeePlan feePlan = (FeePlan) request.getAttribute("feePlan");
    @SuppressWarnings("unchecked")
    List<FeePlan> planList = (List<FeePlan>) request.getAttribute("plans");
    Double gstRateAttr = (Double) request.getAttribute("gstRate");
    double gstRate = (gstRateAttr == null) ? 0 : gstRateAttr;
    String today = (String) request.getAttribute("today");
    String error = (String) request.getAttribute("error");
    String msg   = request.getParameter("msg");
    if (today == null) today = java.time.LocalDate.now().toString();

    int courseFee = (led != null) ? led.getCourseFee()       : (pos == null ? 0 : pos.courseFee);
    int regFee    = (led != null) ? led.getRegistrationFee() : (pos == null ? 0 : pos.registrationFee);
    int matFee    = (led != null) ? led.getMaterialFee()     : (pos == null ? 0 : pos.materialFee);
    int disc      = (led != null) ? led.getDiscount()        : 0;
    int schol     = (led != null) ? led.getScholarship()     : 0;
    String plan   = (led != null && led.getPlan() != null) ? led.getPlan() : "FULL";
    int schedCount = (schedule == null) ? 0 : schedule.size();
%>
<%!
    private String esc(String x) {
        if (x == null) return "";
        return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String d(String x) { return (x == null || x.isEmpty()) ? "—" : esc(x); }
    private String rs(int n) { return "Rs. " + String.format("%,d", n); }
    private String sel(String cur, String opt) { return opt.equals(cur) ? " selected" : ""; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Fee Plan – <%= s == null ? "" : esc(s.getFullName()) %></title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .head { display:flex; justify-content:space-between; align-items:flex-start; gap:14px;
      flex-wrap:wrap; margin-bottom:18px; }
    .head h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); }
    .head .sub { font-size:13px; color:var(--muted); margin-top:3px; }
    .btn { display:inline-block; text-decoration:none; font-size:13px; font-weight:700; padding:10px 18px;
      border-radius:8px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); color:var(--muted); }

    .cols { display:grid; grid-template-columns:1fr 1fr; gap:18px; align-items:start; }
    @media(max-width:900px){ .cols { grid-template-columns:1fr; } }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:20px 22px; margin-bottom:18px; }
    .card h3 { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:var(--green-dark);
      padding-bottom:9px; border-bottom:2px solid var(--green-light); margin-bottom:14px; }

    .fld { display:flex; flex-direction:column; margin-bottom:12px; }
    .fld label { font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.3px;
      color:var(--text); margin-bottom:5px; }
    .fld input, .fld select, .fld textarea { padding:10px 12px; border:1.5px solid var(--border);
      border-radius:8px; font-size:13.5px; font-family:inherit; }
    .fld .hint { font-size:11.5px; color:var(--muted); margin-top:4px; }
    .row2 { display:grid; grid-template-columns:1fr 1fr; gap:12px; }

    .totals { background:var(--green-pale); border-radius:9px; padding:14px 16px; margin-top:6px; }
    .totals .r { display:flex; justify-content:space-between; font-size:13.5px; padding:4px 0; }
    .totals .r.big { font-size:16px; font-weight:800; color:var(--green-dark);
      border-top:2px solid var(--green-light); margin-top:6px; padding-top:9px; }
    .totals .r.cut { color:#C0392B; }

    table.sch { width:100%; border-collapse:collapse; font-size:13px; }
    table.sch th { background:var(--green); color:#fff; text-align:left; padding:9px 11px; font-size:10.5px;
      text-transform:uppercase; letter-spacing:0.4px; }
    table.sch td { padding:9px 11px; border-bottom:1px solid var(--border); }
    table.sch tr:last-child td { border-bottom:none; }
    table.sch tr.paid td { background:var(--green-pale); }
    table.sch tr.over td { background:#FFF5F4; }
    .badge { display:inline-block; font-size:10px; font-weight:800; padding:3px 9px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; white-space:nowrap; }
    .st-PENDING { background:#FFF4D6; color:#9A6B00; }
    .st-PARTIAL { background:#DDEBFF; color:#1B4F9C; }
    .st-PAID    { background:var(--green-light); color:var(--success); }
    .st-OVERDUE { background:#FDE2E0; color:#C0392B; }

    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid #BFE3CE; }
    .alert.warn { background:#FFF6E0; color:#7A5200; border:1.5px solid #F0D89A; }
    .soon { font-size:12.5px; color:var(--muted); font-style:italic; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <% if (s == null) { %>
    <div class="card"><p>Student not found. <a href="<%= ctx %>/fees">Back to fees →</a></p></div>
  <% } else { %>

    <div class="head">
      <div>
        <h2>Fee Plan — <%= esc(s.getFullName()) %></h2>
        <div class="sub"><%= esc(s.getAdmissionNo()) %> · <%= d(s.getClassName()) %>
          <% if (slab != null) { %> · slab <%= esc(slab.getLabel()) %><% } %></div>
      </div>
      <div style="display:flex;gap:9px;flex-wrap:wrap;">
        <a class="btn btn-ghost" href="<%= ctx %>/student?id=<%= s.getStudentId() %>">← Profile</a>
        <a class="btn btn-primary" href="<%= ctx %>/collect?studentId=<%= s.getStudentId() %>">Collect Fee →</a>
      </div>
    </div>

    <% if (msg != null) { %>
      <div class="alert <%= "error".equals(msg) ? "error" : "ok" %>"><%=
        "saved".equals(msg) ? "Fee plan saved." :
        "error".equals(msg) ? "Something went wrong. Please try again." : "Done." %></div>
    <% } %>
    <% if (error != null) { %><div class="alert error"><%= esc(error) %></div><% } %>
    <% if (!user.isAdmin()) { %>
      <div class="alert warn">You can view this plan, but only an administrator can change a fee or grant a concession.</div>
    <% } %>

    <div class="cols">
      <div>
        <form method="post" action="<%= ctx %>/fee-plan">
          <input type="hidden" name="action" value="save">
          <input type="hidden" name="studentId" value="<%= s.getStudentId() %>">

          <div class="card">
            <h3>Fee &amp; Concession</h3>
            <div class="row2">
              <div class="fld">
                <label>Tuition / Course Fee</label>
                <input type="number" name="courseFee" min="0" value="<%= courseFee %>" <%= user.isAdmin() ? "" : "disabled" %>>
              </div>
              <div class="fld">
                <label>Registration Fee</label>
                <input type="number" name="registrationFee" min="0" value="<%= regFee %>" <%= user.isAdmin() ? "" : "disabled" %>>
              </div>
              <div class="fld">
                <label>Study Material</label>
                <input type="number" name="materialFee" min="0" value="<%= matFee %>" <%= user.isAdmin() ? "" : "disabled" %>>
              </div>
              <div class="fld">
                <label>Discount</label>
                <input type="number" name="discount" min="0" value="<%= disc %>" <%= user.isAdmin() ? "" : "disabled" %>>
              </div>
              <div class="fld">
                <label>Scholarship</label>
                <input type="number" name="scholarship" min="0" value="<%= schol %>" <%= user.isAdmin() ? "" : "disabled" %>>
              </div>
              <div class="fld">
                <label>GST %</label>
                <input type="number" step="0.01" min="0" name="gstRate"
                       value="<%= led == null ? gstRate : led.getGstRate() %>" <%= user.isAdmin() ? "" : "disabled" %>>
                <span class="hint">Charged on the course fee only, never on registration.</span>
              </div>
              <div class="fld">
                <label>Payment Plan</label>
                <select name="plan" <%= user.isAdmin() ? "" : "disabled" %>>
                  <option value="FULL"<%= sel(plan,"FULL") %>>Pay in full</option>
                  <option value="INSTALLMENT"<%= sel(plan,"INSTALLMENT") %>>Instalments</option>
                  <option value="EMI"<%= sel(plan,"EMI") %>>EMI</option>
                </select>
              </div>
            </div>
            <div class="fld">
              <label>Remarks / reason for concession</label>
              <input type="text" name="remarks" maxlength="255"
                     value="<%= led == null ? "" : esc(led.getRemarks()) %>" <%= user.isAdmin() ? "" : "disabled" %>>
              <span class="hint">Recorded against your name for the discount register.</span>
            </div>

            <%-- Registration sits outside the course total: it is non-refundable,
                 never discounted, and carries no GST. --%>
            <div class="totals">
              <div class="r"><span>Course fee</span><span id="tGross"><%= rs(courseFee + matFee) %></span></div>
              <div class="r cut"><span>Less concession</span><span id="tCut">- <%= rs(disc + schol) %></span></div>
              <div class="r"><span>Net course fee</span><span id="tNet"><%= rs(Math.max(0, courseFee + matFee - disc - schol)) %></span></div>
              <div class="r"><span>GST</span><span id="tGst"><%= rs(led == null ? 0 : led.getGstAmount()) %></span></div>
              <div class="r"><span>Registration <small>(non-refundable)</small></span><span id="tReg"><%= rs(regFee) %></span></div>
              <div class="r big"><span>Total payable</span><span id="tTotal"><%= rs(led == null ? (courseFee + matFee - disc - schol + regFee) : led.getTotalPayable()) %></span></div>
              <% if (pos != null) { %>
                <div class="r"><span>Already collected</span><span><%= rs(pos.paid) %></span></div>
                <div class="r"><span>Outstanding</span><span><%= rs(pos.outstanding) %></span></div>
              <% } %>
            </div>
          </div>

          <div class="card">
            <h3>Instalment Schedule</h3>
            <div class="row2">
              <div class="fld" style="grid-column:1 / -1;">
                <label>Fee Plan</label>
                <select name="planCode" <%= user.isAdmin() ? "" : "disabled" %>>
                  <option value="">— none —</option>
                  <% if (planList != null) for (FeePlan fp : planList) { %>
                    <option value="<%= esc(fp.getCode()) %>"<%= (feePlan != null && fp.getCode().equals(feePlan.getCode())) ? " selected" : "" %>>
                      <%= esc(fp.getLabel()) %>
                    </option>
                  <% } %>
                </select>
                <span class="hint">
                  <% if (feePlan != null) { %>
                    Brochure plan: <%= feePlan.getInstallments() %> part payment(s)
                    — <%= esc(feePlan.getPpPattern()) %>% on <%= esc(feePlan.getDueMonths()) %>
                  <% } else { %>
                    No brochure plan set — rebuilding the schedule needs one.
                  <% } %>
                </span>
              </div>
              <div class="fld">
                <label>First due date</label>
                <input type="date" name="startDate" value="<%= today %>" <%= user.isAdmin() ? "" : "disabled" %>>
              </div>
              <div class="fld">
                <label>Regenerate schedule</label>
                <select name="regenerate" <%= user.isAdmin() ? "" : "disabled" %>>
                  <option value="0">No — keep the current schedule</option>
                  <option value="1">Yes — rebuild it from these settings</option>
                </select>
                <span class="hint">Rebuilding re-applies money already paid, oldest first.</span>
              </div>
            </div>
            <% if (user.isAdmin()) { %>
              <button type="submit" class="btn btn-primary" style="width:100%;margin-top:6px;">Save Fee Plan</button>
            <% } %>
          </div>
        </form>
      </div>

      <div>
        <div class="card">
          <h3>Current Schedule <span style="font-weight:400;color:var(--muted);">(<%= schedCount %>)</span></h3>
          <% if (schedCount == 0) { %>
            <p class="soon">No schedule yet. Set the number of instalments on the left and choose
               “rebuild” to create one, or leave it as a single pay-in-full amount.</p>
          <% } else { %>
            <table class="sch">
              <thead><tr><th>#</th><th>Due</th><th>Amount</th><th>Paid</th><th>Status</th></tr></thead>
              <tbody>
              <% int totAmt = 0, totPaid = 0;
                 for (FeeInstallment i : schedule) {
                   totAmt += i.getAmount(); totPaid += i.getPaidAmount();
                   String st = i.getStatus() == null ? "PENDING" : i.getStatus();
                   boolean over = "OVERDUE".equals(st); %>
                <tr class="<%= i.isSettled() ? "paid" : (over ? "over" : "") %>">
                  <td><%= i.getSeq() %></td>
                  <td><%= d(i.getDueDate()) %></td>
                  <td><%= rs(i.getAmount()) %></td>
                  <td><%= rs(i.getPaidAmount()) %></td>
                  <td><span class="badge st-<%= esc(st) %>"><%= esc(st) %></span></td>
                </tr>
              <% } %>
              </tbody>
            </table>
            <div class="totals">
              <div class="r"><span>Scheduled</span><span><%= rs(totAmt) %></span></div>
              <div class="r"><span>Collected against schedule</span><span><%= rs(totPaid) %></span></div>
              <div class="r big"><span>Still due</span><span><%= rs(Math.max(0, totAmt - totPaid)) %></span></div>
            </div>
          <% } %>
        </div>
      </div>
    </div>

  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<script>
  // Live totals as the amounts are typed, so a concession's effect is visible
  // before saving.
  (function () {
    var ids = ['courseFee','registrationFee','materialFee','discount','scholarship','gstRate'];
    function num(n) { var el = document.querySelector('[name="' + n + '"]');
                      return el ? (parseInt(el.value, 10) || 0) : 0; }
    function fmt(v) { return 'Rs. ' + v.toLocaleString('en-IN'); }
    function calc() {
      var course = num('courseFee') + num('materialFee');
      var cut    = num('discount') + num('scholarship');
      var net    = Math.max(0, course - cut);
      var rateEl = document.querySelector('[name="gstRate"]');
      var rate   = rateEl ? (parseFloat(rateEl.value) || 0) : 0;
      var gst    = Math.round(net * rate / 100);
      var reg    = num('registrationFee');
      document.getElementById('tGross').textContent = fmt(course);
      document.getElementById('tCut').textContent   = '- ' + fmt(cut);
      document.getElementById('tNet').textContent   = fmt(net);
      document.getElementById('tGst').textContent   = fmt(gst);
      document.getElementById('tReg').textContent   = fmt(reg);
      document.getElementById('tTotal').textContent = fmt(reg + net + gst);
    }
    ids.forEach(function (n) {
      var el = document.querySelector('[name="' + n + '"]');
      if (el) el.addEventListener('input', calc);
    });
  })();
</script>
</body>
</html>
