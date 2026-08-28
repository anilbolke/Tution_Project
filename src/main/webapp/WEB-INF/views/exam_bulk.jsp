<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.dao.ExamPaymentDAO" %>
<%@ page import="com.tution.model.CandidateFee, com.tution.model.Exam,
                 com.tution.model.ExamPayment, com.tution.model.SchoolReceipt,
                 com.tution.model.User" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    Exam exam = (Exam) request.getAttribute("exam");
    Integer examIdObj = (Integer) request.getAttribute("examId");
    int examId = examIdObj == null ? 0 : examIdObj.intValue();

    @SuppressWarnings("unchecked") List<CandidateFee> rows =
        (List<CandidateFee>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<CandidateFee>();

    @SuppressWarnings("unchecked") List<String> schools =
        (List<String>) request.getAttribute("schools");
    if (schools == null) schools = new ArrayList<String>();

    @SuppressWarnings("unchecked") List<SchoolReceipt> cheques =
        (List<SchoolReceipt>) request.getAttribute("cheques");
    if (cheques == null) cheques = new ArrayList<SchoolReceipt>();

    SchoolReceipt cheque = (SchoolReceipt) request.getAttribute("cheque");
    ExamPaymentDAO.Totals tot = (ExamPaymentDAO.Totals) request.getAttribute("totals");

    String fSchool = str(request.getAttribute("school"));
    String today   = java.time.LocalDate.now().toString();

    // What the ticked list could absorb if everything on screen were selected.
    BigDecimal owedHere = Money.ZERO;
    for (CandidateFee c : rows) owedHere = owedHere.add(c.getBalance());

    String error      = (String) request.getAttribute("error");
    String flash      = (String) session.getAttribute("flash");
    String flashError = (String) session.getAttribute("flashError");
    session.removeAttribute("flash");
    session.removeAttribute("flashError");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>School Payment – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .ef-wrap { max-width:1240px; margin:18px auto; padding:0 14px; }
  .ef-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .ef-card h2 { margin:0 0 3px; font-size:16px; }
  .ef-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }

  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:8px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  table.t tr.sum td { background:#f6f8f9; font-weight:700; border-top:2px solid #dfe4e6; }
  table.t tbody tr:hover { background:#fafcfc; }
  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .muted { color:#7b8b93; }
  .roll { font-family:ui-monospace,Consolas,monospace; font-weight:700; letter-spacing:.5px; }

  .st { font-size:11px; font-weight:700; padding:2px 9px; border-radius:20px; white-space:nowrap; }
  .s-PARTIAL { background:#fdf1e3; color:#8a4b12; }
  .s-UNPAID  { background:#f7d4d4; color:#8c2020; }

  .frm { display:flex; gap:10px; flex-wrap:wrap; align-items:flex-end; }
  .fl { display:flex; flex-direction:column; gap:4px; }
  .fl label { font-size:11.5px; color:#5a6b73; font-weight:600; }
  .fl input, .fl select { padding:7px 9px; border:1px solid #cfd6da; border-radius:5px;
                          font-size:13px; font-family:inherit; }
  .btn { background:#0E5C3F; color:#fff; border:0; padding:8px 16px; border-radius:6px;
         font-size:13px; font-weight:600; cursor:pointer; text-decoration:none; display:inline-block; }
  .btn.alt { background:#fff; color:#42555e; border:1px solid #cfd6da; }
  .btn.sm  { padding:4px 10px; font-size:11.5px; }
  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.ok { background:#e3f3e9; color:#1b6b39; border:1px solid #bfe0cc; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .msg.warn{ background:#fdf1e3; color:#8a4b12; border:1px solid #f0d3ae; }

  .runner { position:sticky; bottom:0; background:#f6f8f9; border-top:2px solid #dfe4e6;
            padding:11px 15px; margin:0 -16px -16px; border-radius:0 0 10px 10px;
            display:flex; gap:14px; flex-wrap:wrap; align-items:flex-end; }
  .runner .lb { font-size:11.5px; color:#5a6b73; font-weight:600; }
  .runner .vl { font-size:19px; font-weight:700; font-variant-numeric:tabular-nums; color:#0E5C3F; }
  .amt { width:150px; font-size:17px !important; font-variant-numeric:tabular-nums; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="examfees"/>
</jsp:include>

<div class="ef-wrap">

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <div class="ef-card">
    <h2>School paid for a batch<%= exam == null ? "" : " — " + esc(exam.getExamName()) %></h2>
    <p class="hint">One payment from a school, split across the candidates it covers.
       Tick who it is for and enter the amount as it appears on the cheque — the money is
       given out in roll order, each candidate filled to their balance before the next one
       starts, so a short payment leaves one candidate to chase rather than everybody
       part&#8209;paid.
       <a href="<%= ctx %>/exam-fees?exam=<%= examId %>">Back to exam fees</a></p>

    <form class="frm" method="get" action="<%= ctx %>/exam-fees">
      <input type="hidden" name="bulk" value="1">
      <input type="hidden" name="exam" value="<%= examId %>">
      <div class="fl"><label>School</label>
        <select name="school" onchange="this.form.submit()">
          <option value="">All schools</option>
          <% for (String s : schools) { %>
            <option value="<%= esc(s) %>" <%= s.equals(fSchool) ? "selected" : "" %>><%= esc(s) %></option>
          <% } %>
        </select></div>
      <button class="btn alt" type="submit">Show</button>
      <% if (tot != null) { %>
        <span style="flex:1 1 auto"></span>
        <span class="muted" style="font-size:12.5px">
          Exam outstanding: <strong>Rs. <%= Money.fmt(tot.outstanding) %></strong>
          across <%= tot.candidatesDue %> candidate(s)</span>
      <% } %>
    </form>
  </div>

  <%-- ────────── the tick list ────────── --%>
  <% if (rows.isEmpty()) { %>
    <div class="ef-card">
      <h2>Nobody is owing here</h2>
      <p class="hint" style="margin:0">
        <% if (exam == null) { %>
          Pick an exam on the exam fees screen first.
        <% } else if (!fSchool.isEmpty()) { %>
          Every candidate from <strong><%= esc(fSchool) %></strong> is settled or waived.
          Try another school.
        <% } else { %>
          Every candidate in this exam is settled or waived — or no fee has been set yet.
        <% } %>
      </p>
    </div>
  <% } else { %>

    <form method="post" action="<%= ctx %>/exam-fees" id="bulkForm"
          onsubmit="return confirmSplit()">
      <input type="hidden" name="action" value="bulk">
      <input type="hidden" name="examId" value="<%= examId %>">
      <input type="hidden" name="school" value="<%= esc(fSchool) %>">

      <div class="ef-card">
        <h2>Who is this payment for?</h2>
        <p class="hint">Only candidates who still owe are listed — a settled or waived
           candidate cannot take any of the money.</p>

        <table class="t">
          <thead><tr>
            <th style="width:34px"><input type="checkbox" id="all" checked
                                          onclick="toggleAll(this)"></th>
            <th style="width:90px">Roll</th>
            <th>Candidate</th>
            <th>School</th>
            <th style="width:100px" class="num">Fee</th>
            <th style="width:100px" class="num">Paid</th>
            <th style="width:110px" class="num">Balance</th>
            <th style="width:90px">Status</th>
          </tr></thead>
          <tbody>
          <% for (CandidateFee c : rows) { %>
            <tr>
              <td><input type="checkbox" class="pick" name="candidateId"
                         value="<%= c.getCandidateId() %>" checked
                         data-bal="<%= Money.fmt(c.getBalance()) %>"
                         onclick="recalc()"></td>
              <td class="roll"><%= esc(c.getRollNo()) %></td>
              <td><%= esc(c.getCandidateName()) %>
                <% if (c.getClassName() != null) { %>
                  <div class="muted" style="font-size:11px">Class <%= esc(c.getClassName()) %></div>
                <% } %></td>
              <td class="muted"><%= esc(nz(c.getSchoolName(), "—")) %></td>
              <td class="num"><%= Money.fmt(c.getPayable()) %></td>
              <td class="num"><%= Money.fmt(c.getPaid()) %></td>
              <td class="num"><strong><%= Money.fmt(c.getBalance()) %></strong></td>
              <td><span class="st s-<%= c.getState() %>"><%= esc(c.getStateLabel()) %></span></td>
            </tr>
          <% } %>
          </tbody>
          <tfoot>
            <tr class="sum">
              <td colspan="6">All <%= rows.size() %> listed</td>
              <td class="num"><%= Money.fmt(owedHere) %></td>
              <td></td>
            </tr>
          </tfoot>
        </table>

        <div class="runner">
          <div>
            <div class="lb">Ticked</div>
            <div class="vl"><span id="nPicked"><%= rows.size() %></span> candidate(s)</div>
          </div>
          <div>
            <div class="lb">They owe</div>
            <div class="vl">Rs. <span id="owed"><%= Money.fmt(owedHere) %></span></div>
          </div>
          <div class="fl"><label>Amount received</label>
            <input class="amt" type="text" name="total" id="total"
                   value="<%= Money.fmt(owedHere) %>" required oninput="checkOver()"></div>
          <div class="fl"><label>Date</label>
            <input type="date" name="date" value="<%= today %>"></div>
          <div class="fl"><label>Mode</label>
            <select name="mode">
              <option>Cheque</option><option>Bank Transfer</option><option>UPI</option>
              <option>Cash</option><option>Card</option><option>Online</option>
            </select></div>
          <div class="fl"><label>Reference</label>
            <input type="text" name="ref" style="width:140px" placeholder="Cheque / UTR no"></div>
          <div class="fl" style="flex:1 1 160px"><label>Remarks</label>
            <input type="text" name="remarks"></div>
          <button class="btn" type="submit">Record payment</button>
        </div>
        <div id="warn" class="msg warn" style="display:none;margin:12px 0 0"></div>
      </div>
    </form>
  <% } %>

  <%-- ────────── the split that was just written ────────── --%>
  <% if (cheque != null) { %>
    <div class="ef-card">
      <h2><%= esc(cheque.getReceiptNo()) %> — <%= esc(cheque.getSchoolName()) %></h2>
      <p class="hint">
        Rs. <%= Money.fmt(cheque.getTotalAmount()) %> received <%= esc(cheque.getPaymentDate()) %>
        by <%= esc(cheque.getPaymentMode()) %><%
           if (cheque.getTxnRef() != null) { %> (<%= esc(cheque.getTxnRef()) %>)<% } %>,
        split across <%= cheque.getSplits().size() %> candidate(s)<%
           if (cheque.getCollectedBy() != null) { %> · keyed by <%= esc(cheque.getCollectedBy()) %><% } %>.
      </p>

      <% if (cheque.getUnallocated().signum() > 0) { %>
        <div class="msg warn">
          Rs. <%= Money.fmt(cheque.getUnallocated()) %> of this payment is no longer allocated —
          a candidate receipt from it has been cancelled. That money is sitting against the
          school with nobody credited for it.
        </div>
      <% } %>

      <table class="t">
        <thead><tr>
          <th style="width:135px">Receipt</th><th style="width:90px">Roll</th>
          <th>Candidate</th><th style="width:110px" class="num">Amount</th>
          <th style="width:110px"></th>
        </tr></thead>
        <tbody>
        <% for (ExamPayment p : cheque.getSplits()) { %>
          <tr<%= p.isVoid() ? " style=\"opacity:.55\"" : "" %>>
            <td class="roll" style="font-size:12px"><%= esc(p.getReceiptNo()) %>
              <% if (p.isVoid()) { %><br><span class="st s-UNPAID">CANCELLED</span><% } %></td>
            <td class="roll"><%= esc(p.getRollNo()) %></td>
            <td><%= esc(p.getCandidateName()) %></td>
            <td class="num"><%= Money.fmt(p.getAmount()) %></td>
            <td><a class="btn alt sm" target="_blank"
                   href="<%= ctx %>/exam-fees?receipt=<%= p.getExamPaymentId() %>">Receipt</a></td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

  <%-- ────────── previous school payments ────────── --%>
  <% if (!cheques.isEmpty()) { %>
    <div class="ef-card">
      <h2>School payments for this exam</h2>
      <p class="hint">Every payment received from a school, and how much of it is still
         credited to candidates.</p>
      <table class="t">
        <thead><tr>
          <th style="width:135px">Receipt</th><th style="width:95px">Date</th>
          <th>School</th><th>Mode / reference</th>
          <th style="width:70px" class="num">Split</th>
          <th style="width:110px" class="num">Amount</th>
          <th style="width:120px" class="num">Unallocated</th>
        </tr></thead>
        <tbody>
        <% for (SchoolReceipt r : cheques) { %>
          <tr>
            <td><a class="roll" style="font-size:12px"
                   href="<%= ctx %>/exam-fees?bulk=1&amp;exam=<%= examId %>&amp;school=<%= url(fSchool) %>&amp;cheque=<%= r.getSchoolReceiptId() %>"
                ><%= esc(r.getReceiptNo()) %></a></td>
            <td><%= esc(r.getPaymentDate()) %></td>
            <td><%= esc(r.getSchoolName()) %></td>
            <td><%= esc(r.getPaymentMode()) %>
              <% if (r.getTxnRef() != null) { %>
                <span class="muted">· <%= esc(r.getTxnRef()) %></span><% } %></td>
            <td class="num"><%= r.getCandidateCount() %></td>
            <td class="num"><%= Money.fmt(r.getTotalAmount()) %></td>
            <td class="num" <%= r.isFullyAllocated() ? "" : "style=\"color:#8a4b12;font-weight:700\"" %>>
              <%= Money.fmt(r.getUnallocated()) %></td>
          </tr>
        <% } %>
        </tbody>
      </table>
    </div>
  <% } %>

</div>

<script>
  function money(s) {
    var n = parseFloat(String(s).replace(/[^0-9.\-]/g, ''));
    return isNaN(n) ? 0 : n;
  }
  function fmt(n) {
    // Indian grouping, to match Money.fmt so the two never disagree on screen.
    var neg = n < 0; n = Math.abs(n);
    var s = n.toFixed(2), w = s.slice(0, -3), d = s.slice(-3);
    var last = w.slice(-3), rest = w.slice(0, -3), out = last;
    while (rest.length > 2) { out = rest.slice(-2) + ',' + out; rest = rest.slice(0, -2); }
    if (rest.length) out = rest + ',' + out;
    return (neg ? '-' : '') + out + d;
  }
  function picked() {
    return Array.prototype.filter.call(
      document.querySelectorAll('.pick'), function (c) { return c.checked; });
  }
  function owedNow() {
    return picked().reduce(function (t, c) { return t + money(c.dataset.bal); }, 0);
  }
  function recalc() {
    var p = picked(), owed = owedNow();
    document.getElementById('nPicked').textContent = p.length;
    document.getElementById('owed').textContent = fmt(owed);
    document.getElementById('total').value = fmt(owed);
    document.getElementById('all').checked =
      p.length === document.querySelectorAll('.pick').length;
    checkOver();
  }
  function toggleAll(box) {
    Array.prototype.forEach.call(document.querySelectorAll('.pick'),
      function (c) { c.checked = box.checked; });
    recalc();
  }
  // Says what the amount will actually do BEFORE it is submitted. The server
  // refuses an over-payment outright; warning here saves a round trip and,
  // more usefully, names the shortfall while the cheque is still in hand.
  function checkOver() {
    var owed = owedNow(), amt = money(document.getElementById('total').value);
    var w = document.getElementById('warn');
    if (amt > owed) {
      w.style.display = 'block';
      w.className = 'msg err';
      w.textContent = 'Rs. ' + fmt(amt - owed) + ' more than the Rs. ' + fmt(owed) +
        ' owed by the ' + picked().length + ' candidate(s) ticked. This will be refused — ' +
        'reduce the amount or tick more candidates.';
    } else if (amt > 0 && amt < owed) {
      w.style.display = 'block';
      w.className = 'msg warn';
      w.textContent = 'Rs. ' + fmt(owed - amt) + ' short. Candidates are filled in roll order, ' +
        'so the last ones ticked will stay part-paid or unpaid.';
    } else {
      w.style.display = 'none';
    }
  }
  function confirmSplit() {
    var p = picked();
    if (!p.length) { alert('Tick at least one candidate to pay for.'); return false; }
    var amt = money(document.getElementById('total').value);
    return confirm('Record Rs. ' + fmt(amt) + ' from this school and split it across ' +
                   p.length + ' candidate(s)?');
  }
  recalc();
</script>
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

    private String url(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private String nz(String s, String dflt) {
        return (s == null || s.trim().isEmpty()) ? dflt : s;
    }
%>
