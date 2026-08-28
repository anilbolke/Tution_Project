<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.time.LocalDate, com.tution.model.Student, com.tution.model.Payment,
                 com.tution.model.User, com.tution.model.FeeInstallment, com.tution.service.FeeService,
                 com.tution.util.FeeCalculator" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Student student = (Student) request.getAttribute("student");
    if (student == null) { response.sendRedirect(ctx + "/fees"); return; }
    String slabLabel = (String) request.getAttribute("slabLabel");
    int totalFee    = (Integer) request.getAttribute("totalFee");
    int paid        = (Integer) request.getAttribute("paid");
    int outstanding = (Integer) request.getAttribute("outstanding");
    @SuppressWarnings("unchecked")
    List<Payment> history = (List<Payment>) request.getAttribute("history");
    @SuppressWarnings("unchecked")
    List<FeeInstallment> schedule = (List<FeeInstallment>) request.getAttribute("schedule");
    FeeInstallment nextDue = (FeeInstallment) request.getAttribute("nextDue");
    FeeService.Position position = (FeeService.Position) request.getAttribute("position");
    String error = (String) request.getAttribute("error");
    String flash = (String) session.getAttribute("flashError");
    if (flash != null) { session.removeAttribute("flashError"); if (error == null) error = flash; }
    Object fa = request.getAttribute("formAmount");
    String today = LocalDate.now().toString();
    boolean settled = outstanding <= 0;
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Collect Fee – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .collect-grid { display:grid; grid-template-columns:1fr; gap:20px; }
    @media(min-width:780px){ .collect-grid { grid-template-columns:1fr 1fr; } }
    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); padding:22px 24px; }
    .card h3 { font-size:15px; font-weight:700; color:var(--green-dark); margin-bottom:14px; padding-bottom:12px; border-bottom:1.5px solid var(--green-light); }
    .stu-line { display:flex; justify-content:space-between; font-size:13px; padding:6px 0; }
    .stu-line span:first-child { color:var(--muted); }
    .stu-line span:last-child { font-weight:600; color:var(--text); }
    .next-due { background:#FFF6E0; border:1.5px solid #F0D89A; color:#7A5200; border-radius:9px;
      padding:11px 13px; font-size:13px; margin-bottom:14px; }
    .next-due .od { color:#C0392B; font-weight:700; margin-left:6px; }
    .alloc-note { font-size:11.5px; color:var(--muted); margin:2px 0 12px; }
    .conc-line { display:flex; justify-content:space-between; font-size:12.5px; padding:3px 0; color:#C0392B; }
    table.sch { width:100%; border-collapse:collapse; font-size:12.5px; margin-top:6px; }
    table.sch th { text-align:left; padding:7px 8px; font-size:10px; text-transform:uppercase;
      letter-spacing:0.3px; color:var(--muted); border-bottom:1.5px solid var(--border); }
    table.sch td { padding:7px 8px; border-bottom:1px solid var(--border); }
    table.sch tr:last-child td { border-bottom:none; }
    table.sch tr.paid td { background:var(--green-pale); }
    table.sch tr.over td { background:#FFF5F4; }
    .ibadge { display:inline-block; font-size:9.5px; font-weight:800; padding:2px 8px; border-radius:10px;
      text-transform:uppercase; letter-spacing:0.3px; }
    .st-PENDING { background:#FFF4D6; color:#9A6B00; }
    .st-PARTIAL { background:#DDEBFF; color:#1B4F9C; }
    .st-PAID    { background:var(--green-light); color:var(--success); }
    .st-OVERDUE { background:#FDE2E0; color:#C0392B; }
    .fee-strip { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; background:var(--green-dark); border-radius:10px; padding:14px 12px; text-align:center; margin-top:14px; }
    .fs-item:not(:last-child){ border-right:1px solid rgba(255,255,255,0.12); }
    .fs-val { font-size:17px; font-weight:800; color:#fff; }
    .fs-val.acc { color:var(--accent); }
    .fs-lbl { font-size:10px; color:#8ECFAC; font-weight:600; text-transform:uppercase; letter-spacing:0.5px; margin-top:3px; }
    .field { margin-bottom:14px; }
    .field label { display:block; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .field input, .field select, .field textarea { width:100%; padding:11px 13px; font-size:15px; font-family:'Inter',sans-serif; border:1.5px solid var(--border); border-radius:7px; color:var(--text); background:var(--green-pale); outline:none; }
    .field input:focus, .field select:focus, .field textarea:focus { border-color:var(--green); box-shadow:0 0 0 3px rgba(26,122,74,0.13); background:#fff; }
    .field-row { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
    .btn-submit { width:100%; padding:13px; background:var(--green); color:#fff; border:none; border-radius:8px; font-size:16px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; }
    .btn-submit:hover { background:var(--green-dark); }
    .btn-online { width:100%; padding:12px; background:#0B2540; color:#fff; border:none; border-radius:8px; font-size:14px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; margin-top:10px; }
    .btn-online:hover { background:#06192C; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:14px; }
    .settled-box { background:var(--green-light); border:1px solid var(--border); color:var(--success); border-radius:8px; padding:16px; text-align:center; font-weight:600; }
    .hist-table { width:100%; border-collapse:collapse; font-size:13px; }
    .hist-table th { background:var(--green); color:#fff; text-align:left; padding:9px 11px; font-size:10px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
    .hist-table td { padding:9px 11px; border-bottom:1px solid var(--border); }
    .hist-table tr:nth-child(even) td { background:var(--green-pale); }
    .rno { font-weight:700; color:var(--green-dark); white-space:nowrap; }
    .hist-empty { font-size:13px; color:var(--muted); padding:14px 0; }
    .back-link { display:inline-block; margin-bottom:14px; font-size:13px; color:var(--green); text-decoration:none; font-weight:600; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <a class="back-link" href="<%= ctx %>/fees">← Back to Fees</a>

  <div class="collect-grid">

    <!-- LEFT: student + collect form -->
    <div>
      <div class="card" style="margin-bottom:20px;">
        <h3>Student</h3>
        <div class="stu-line"><span>Name</span><span><%= esc(student.getFullName()) %></span></div>
        <div class="stu-line"><span>Admission No</span><span><%= esc(student.getAdmissionNo()) %></span></div>
        <div class="stu-line"><span>Class</span><span><%= student.getClassName()==null?"—":esc(student.getClassName()) %></span></div>
        <div class="stu-line"><span>Plan</span><span><%= esc(slabLabel) %></span></div>
        <% if (position != null && position.hasConcession()) { %>
          <% if (position.discount > 0) { %>
            <div class="conc-line"><span>Discount applied</span><span>- <%= FeeCalculator.inr(position.discount) %></span></div>
          <% } %>
          <% if (position.scholarship > 0) { %>
            <div class="conc-line"><span>Scholarship</span><span>- <%= FeeCalculator.inr(position.scholarship) %></span></div>
          <% } %>
        <% } %>
        <div class="fee-strip">
          <div class="fs-item"><div class="fs-val"><%= FeeCalculator.inr(totalFee) %></div><div class="fs-lbl">Total</div></div>
          <div class="fs-item"><div class="fs-val acc"><%= FeeCalculator.inr(paid) %></div><div class="fs-lbl">Paid</div></div>
          <div class="fs-item"><div class="fs-val"><%= FeeCalculator.inr(outstanding) %></div><div class="fs-lbl">Due</div></div>
        </div>
      </div>

      <div class="card">
        <h3>Collect Payment</h3>
        <% if (error != null) { %><div class="alert error"><%= error %></div><% } %>

        <% if (settled) { %>
          <div class="settled-box">✓ Fees fully settled — nothing outstanding.</div>
        <% } else { %>
          <%-- Default the amount to the next instalment rather than the whole
               balance: that is what the parent has actually come in to pay. --%>
          <% int defaultAmount = (nextDue != null) ? Math.min(nextDue.getBalance(), outstanding) : outstanding;
             if (defaultAmount <= 0) defaultAmount = outstanding; %>

          <% if (nextDue != null) { %>
            <div class="next-due">
              Next instalment: <b><%= esc(nextDue.getLabel()) %></b> ·
              due <b><%= esc(nextDue.getDueDate()) %></b> ·
              <b><%= FeeCalculator.inr(nextDue.getBalance()) %></b>
              <% if (nextDue.isOverdue(today)) { %>
                <span class="od">overdue by <%= nextDue.daysOverdue(today) %> day(s)</span>
              <% } %>
            </div>
          <% } %>

          <form action="<%= ctx %>/collect" method="post">
            <input type="hidden" name="studentId" value="<%= student.getStudentId() %>"/>
            <div class="field-row">
              <div class="field">
                <label>Amount (₹)</label>
                <input type="number" name="amount" min="1" max="<%= outstanding %>" inputmode="numeric"
                       value="<%= fa != null ? fa : defaultAmount %>" required/>
              </div>
              <div class="field">
                <label>Date</label>
                <input type="date" name="paymentDate" value="<%= today %>" required/>
              </div>
            </div>
            <div class="field-row">
              <div class="field">
                <label>Payment Mode</label>
                <select name="mode" id="payMode" required>
                  <option value="Cash">Cash</option>
                  <option value="UPI">UPI</option>
                  <option value="Bank Transfer">Bank Transfer</option>
                  <option value="Card">Card</option>
                  <option value="Cheque">Cheque</option>
                </select>
              </div>
              <div class="field">
                <label id="refLabel">Reference (optional)</label>
                <input type="text" name="txnRef" id="txnRef" maxlength="60"
                       placeholder="UPI ref / cheque no / bank ref"/>
              </div>
            </div>
            <div class="field">
              <label>Remarks (optional)</label>
              <input type="text" name="remarks" placeholder="e.g. 1st instalment"/>
            </div>
            <p class="alloc-note">The amount is applied to the oldest unpaid instalment first.</p>
            <button type="submit" class="btn-submit">Record Payment &amp; Generate Receipt →</button>
            <button type="submit" class="btn-online" formaction="<%= ctx %>/pay-online" formmethod="post">💳 Pay Online (Razorpay)</button>
          </form>
        <% } %>
      </div>
    </div>

    <!-- RIGHT: schedule + payment history -->
    <div>
    <div class="card" style="margin-bottom:20px;">
      <h3>Instalment Schedule
        <a href="<%= ctx %>/fee-plan?studentId=<%= student.getStudentId() %>"
           style="float:right;font-size:12px;font-weight:600;color:var(--green);text-decoration:none;">Edit plan →</a>
      </h3>
      <% if (schedule == null || schedule.isEmpty()) { %>
        <div class="hist-empty">No instalment schedule — this student pays in full.</div>
      <% } else { %>
        <table class="sch">
          <thead><tr><th>#</th><th>Due</th><th>Amount</th><th>Paid</th><th>Status</th></tr></thead>
          <tbody>
          <% for (FeeInstallment i : schedule) {
               String st = i.getStatus() == null ? "PENDING" : i.getStatus(); %>
            <tr class="<%= i.isSettled() ? "paid" : ("OVERDUE".equals(st) ? "over" : "") %>">
              <td><%= i.getSeq() %></td>
              <td><%= i.getDueDate() == null ? "—" : esc(i.getDueDate()) %></td>
              <td><%= FeeCalculator.inr(i.getAmount()) %></td>
              <td><%= FeeCalculator.inr(i.getPaidAmount()) %></td>
              <td><span class="ibadge st-<%= esc(st) %>"><%= esc(st) %></span></td>
            </tr>
          <% } %>
          </tbody>
        </table>
      <% } %>
    </div>

    <div class="card">
      <h3>Payment History</h3>
      <% if (history == null || history.isEmpty()) { %>
        <div class="hist-empty">No payments recorded yet.</div>
      <% } else { %>
        <table class="hist-table">
          <thead><tr><th>Receipt</th><th>Date</th><th>Amount</th><th>Mode</th><th></th></tr></thead>
          <tbody>
          <% for (Payment p : history) { %>
            <tr>
              <td class="rno"><%= esc(p.getReceiptNo()) %></td>
              <td><%= esc(p.getPaymentDate()) %></td>
              <td><%= FeeCalculator.inr(p.getAmount()) %></td>
              <td><%= esc(p.getPaymentMode()) %></td>
              <td><a href="<%= ctx %>/receipt?paymentId=<%= p.getPaymentId() %>" style="color:var(--green);font-weight:600;text-decoration:none;font-size:12px;">View</a></td>
            </tr>
          <% } %>
          </tbody>
        </table>
      <% } %>
    </div>
    </div>

  </div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<script>
  // Relabel the reference field to match the mode, so staff know what to type.
  (function () {
    var mode = document.getElementById('payMode');
    var lbl  = document.getElementById('refLabel');
    var inp  = document.getElementById('txnRef');
    if (!mode || !lbl || !inp) return;
    var map = {
      'Cash':          ['Reference (optional)', ''],
      'UPI':           ['UPI Reference',        'e.g. 412345678901'],
      'Bank Transfer': ['Bank Reference / UTR', 'e.g. UTR number'],
      'Card':          ['Card Txn Reference',   'last 4 digits or txn id'],
      'Cheque':        ['Cheque Number',        'e.g. 004512']
    };
    function sync() {
      var m = map[mode.value] || map['Cash'];
      lbl.textContent = m[0];
      inp.placeholder = m[1];
    }
    mode.addEventListener('change', sync);
    sync();
  })();
</script>
</body>
</html>
