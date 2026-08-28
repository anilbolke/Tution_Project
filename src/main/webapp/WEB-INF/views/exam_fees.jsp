<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.dao.ExamPaymentDAO" %>
<%@ page import="com.tution.model.CandidateFee, com.tution.model.Exam,
                 com.tution.model.ExamPayment, com.tution.model.User" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<Exam> exams = (List<Exam>) request.getAttribute("exams");
    if (exams == null) exams = new ArrayList<Exam>();

    @SuppressWarnings("unchecked") List<CandidateFee> rows =
        (List<CandidateFee>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<CandidateFee>();

    @SuppressWarnings("unchecked") List<String> schools =
        (List<String>) request.getAttribute("schools");
    if (schools == null) schools = new ArrayList<String>();

    Exam exam = (Exam) request.getAttribute("exam");
    Integer examIdObj = (Integer) request.getAttribute("examId");
    int examId = examIdObj == null ? 0 : examIdObj.intValue();

    ExamPaymentDAO.Totals tot = (ExamPaymentDAO.Totals) request.getAttribute("totals");

    CandidateFee lk = (CandidateFee) request.getAttribute("lookup");
    @SuppressWarnings("unchecked") List<ExamPayment> lkPays =
        (List<ExamPayment>) request.getAttribute("lookupPayments");
    if (lkPays == null) lkPays = new ArrayList<ExamPayment>();
    String lookupError = (String) request.getAttribute("lookupError");

    String fState  = str(request.getAttribute("state"));
    String fSchool = str(request.getAttribute("school"));
    String fQ      = str(request.getAttribute("q"));

    String today = java.time.LocalDate.now().toString();

    // Pricing a fee is management work; taking one is not. The server enforces
    // this too - hiding the controls just stops the front desk being offered a
    // button that would only 403.
    boolean canPrice = user.isAdmin();

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
<title>Exam Fees – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .ef-wrap { max-width:1240px; margin:18px auto; padding:0 14px; }
  .ef-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .ef-card h2 { margin:0 0 3px; font-size:16px; }
  .ef-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }

  .tiles { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
  .tile { flex:1 1 175px; background:#fff; border:1px solid #e3e6e8; border-left:4px solid #0E5C3F;
          border-radius:10px; padding:12px 14px; }
  .tile.due { border-left-color:#c0392b; }
  .tile .lb { font-size:11.5px; color:#5a6b73; font-weight:600; text-transform:uppercase;
              letter-spacing:.3px; }
  .tile .vl { font-size:22px; font-weight:700; color:#0E5C3F; margin-top:3px;
              font-variant-numeric:tabular-nums; }
  .tile.due .vl { color:#c0392b; }
  .tile .sb { font-size:11.5px; color:#7b8b93; margin-top:2px; }

  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:8px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  table.t tr.sum td { background:#f6f8f9; font-weight:700; border-top:2px solid #dfe4e6; }
  table.t tbody tr:hover { background:#fafcfc; }
  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .muted { color:#7b8b93; }
  .roll { font-family:ui-monospace,Consolas,monospace; font-weight:700; letter-spacing:.5px; }

  .st { font-size:11px; font-weight:700; padding:2px 9px; border-radius:20px; white-space:nowrap; }
  .s-PAID    { background:#d8efdf; color:#1b6b39; }
  .s-PARTIAL { background:#fdf1e3; color:#8a4b12; }
  .s-UNPAID  { background:#f7d4d4; color:#8c2020; }
  .s-WAIVED  { background:#e4eef7; color:#1d4e79; }
  .s-NO_FEE  { background:#eceff1; color:#5a6b73; }

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

  .cand { display:flex; gap:20px; flex-wrap:wrap; align-items:flex-start;
          background:#f6f8f9; border:1px solid #e3e6e8; border-radius:9px; padding:13px 15px;
          margin-bottom:13px; }
  .cand .who { flex:1 1 260px; }
  .cand .who .nm { font-size:17px; font-weight:700; }
  .cand .who .mt { font-size:12.5px; color:#5a6b73; margin-top:2px; }
  .cand .pos { text-align:right; }
  .cand .pos .bal { font-size:22px; font-weight:700; font-variant-numeric:tabular-nums; }
  .rollbox { font-size:19px; padding:9px 12px !important; width:150px; letter-spacing:2px;
             font-family:ui-monospace,Consolas,monospace; text-transform:uppercase; }
  .chips a { font-size:12px; text-decoration:none; padding:4px 11px; border-radius:20px;
             border:1px solid #cfd6da; color:#42555e; margin-right:6px; display:inline-block; }
  .chips a.on { background:#0E5C3F; color:#fff; border-color:#0E5C3F; }
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

  <%-- ────────── collect by roll number ────────── --%>
  <div class="ef-card">
    <h2>Take a payment</h2>
    <p class="hint">Type the roll number from the candidate's hall ticket. Roll numbers are
       unique across every exam, so you do not need to pick the exam first.</p>

    <form class="frm" method="get" action="<%= ctx %>/exam-fees">
      <div class="fl"><label>Roll number</label>
        <input class="rollbox" type="text" name="roll" maxlength="6" autocomplete="off"
               placeholder="000000" autofocus></div>
      <button class="btn" type="submit">Find candidate</button>
    </form>

    <% if (lookupError != null) { %>
      <div class="msg err" style="margin-top:13px;margin-bottom:0"><%= esc(lookupError) %></div>
    <% } %>

    <% if (lk != null) {
         String stt = lk.getState();
    %>
      <div class="cand" style="margin-top:14px">
        <div class="who">
          <div class="nm"><%= esc(lk.getCandidateName()) %>
            <span class="st s-<%= stt %>" style="margin-left:6px"><%= esc(lk.getStateLabel()) %></span>
          </div>
          <div class="mt">
            Roll <span class="roll"><%= esc(lk.getRollNo()) %></span> &middot;
            <%= esc(lk.getExamName()) %>
            <% if (lk.getExamDate() != null) { %> (<%= esc(lk.getExamDate()) %>)<% } %>
          </div>
          <div class="mt">
            <%= esc(nz(lk.getSchoolName(), "School not recorded")) %>
            <% if (lk.getClassName() != null) { %> &middot; Class <%= esc(lk.getClassName()) %><% } %>
            <% if (lk.getMobile() != null) { %> &middot; <%= esc(lk.getMobile()) %><% } %>
          </div>
        </div>
        <div class="pos">
          <div class="muted" style="font-size:11.5px">Fee <%= Money.rs(lk.getPayable()) %>
            &middot; paid <%= Money.fmt(lk.getPaid()) %></div>
          <div class="bal" style="color:<%= lk.isDue() ? "#c0392b" : "#1b6b39" %>">
            <%= lk.isDue() ? "Rs. " + Money.fmt(lk.getBalance()) + " due" : "Settled" %>
          </div>
          <% if (lk.isOverPaid()) { %>
            <div class="muted" style="font-size:11.5px;color:#8a4b12">More was taken than was owed</div>
          <% } %>
        </div>
      </div>

      <% if (lk.isDue()) { %>
        <form class="frm" method="post" action="<%= ctx %>/exam-fees">
          <input type="hidden" name="action" value="collect">
          <input type="hidden" name="candidateId" value="<%= lk.getCandidateId() %>">
          <input type="hidden" name="examId" value="<%= lk.getExamId() %>">
          <input type="hidden" name="roll" value="<%= esc(lk.getRollNo()) %>">
          <div class="fl"><label>Amount</label>
            <input type="text" name="amount" style="width:120px"
                   value="<%= Money.fmt(lk.getBalance()) %>" required></div>
          <div class="fl"><label>Date</label>
            <input type="date" name="date" value="<%= today %>"></div>
          <div class="fl"><label>Mode</label>
            <select name="mode">
              <option>Cash</option><option>UPI</option><option>Bank Transfer</option>
              <option>Cheque</option><option>Card</option><option>Online</option>
            </select></div>
          <div class="fl"><label>Reference</label>
            <input type="text" name="ref" style="width:140px" placeholder="UPI / cheque no"></div>
          <div class="fl" style="flex:1 1 180px"><label>Remarks</label>
            <input type="text" name="remarks"></div>
          <button class="btn" type="submit">Receive payment</button>
        </form>
      <% } else if (CandidateFee.NO_FEE.equals(stt)) { %>
        <div class="msg warn" style="margin-bottom:0">
          No fee has been set for <strong><%= esc(lk.getExamName()) %></strong> yet, so there is
          nothing to collect. Set the exam fee below and this candidate becomes payable.
        </div>
      <% } else if (CandidateFee.WAIVED.equals(stt)) { %>
        <div class="msg warn" style="margin-bottom:0">
          This candidate's fee is waived<%= lk.getWaiverReason() == null ? ""
              : " — " + esc(lk.getWaiverReason()) %>. Nothing is due.
        </div>
      <% } else { %>
        <div class="msg ok" style="margin-bottom:0">Paid in full. Nothing further is due.</div>
      <% } %>

      <%-- ────────── waiver and concession ────────── --%>
      <% if (canPrice) { %>
      <details style="margin-top:13px">
        <summary style="cursor:pointer;font-size:12.5px;color:#0E5C3F;font-weight:600">
          Waiver or own amount for this candidate</summary>
        <div style="margin-top:11px;display:flex;gap:26px;flex-wrap:wrap">

          <form class="frm" method="post" action="<%= ctx %>/exam-fees" style="flex:1 1 320px">
            <input type="hidden" name="action" value="concession">
            <input type="hidden" name="candidateId" value="<%= lk.getCandidateId() %>">
            <input type="hidden" name="examId" value="<%= lk.getExamId() %>">
            <input type="hidden" name="roll" value="<%= esc(lk.getRollNo()) %>">
            <div class="fl"><label>Own amount (overrides the exam fee)</label>
              <input type="text" name="fee" style="width:130px"
                     value="<%= lk.getFeeAmount() == null ? "" : Money.fmt(lk.getFeeAmount()) %>"
                     placeholder="<%= Money.fmt(lk.getExamFee()) %>"></div>
            <button class="btn alt" type="submit">Save amount</button>
            <div class="muted" style="font-size:11.5px;flex-basis:100%">
              Leave it empty and save to put this candidate back on the exam's fee of
              <%= Money.rs(lk.getExamFee()) %>.
            </div>
          </form>

          <form class="frm" method="post" action="<%= ctx %>/exam-fees" style="flex:1 1 320px">
            <input type="hidden" name="action" value="waive">
            <input type="hidden" name="candidateId" value="<%= lk.getCandidateId() %>">
            <input type="hidden" name="examId" value="<%= lk.getExamId() %>">
            <input type="hidden" name="roll" value="<%= esc(lk.getRollNo()) %>">
            <% if (lk.isFeeWaived()) { %>
              <input type="hidden" name="waived" value="0">
              <div class="muted" style="font-size:12.5px;flex-basis:100%">
                Waived<%= lk.getWaiverReason() == null ? "" : " — " + esc(lk.getWaiverReason()) %>.
              </div>
              <button class="btn alt" type="submit">Remove waiver</button>
            <% } else { %>
              <input type="hidden" name="waived" value="1">
              <div class="fl" style="flex:1 1 220px"><label>Waive the fee — reason (required)</label>
                <input type="text" name="reason" placeholder="Sponsored seat / staff child" required></div>
              <button class="btn alt" type="submit">Waive fee</button>
              <div class="muted" style="font-size:11.5px;flex-basis:100%">
                A waiver cannot be applied over money already taken — cancel the receipt first.
              </div>
            <% } %>
          </form>
        </div>
      </details>
      <% } %>

      <% if (!lkPays.isEmpty()) { %>
        <table class="t" style="margin-top:14px">
          <thead><tr>
            <th style="width:135px">Receipt</th><th style="width:95px">Date</th>
            <th>Mode / reference</th><th style="width:110px" class="num">Amount</th>
            <th style="width:170px"></th>
          </tr></thead>
          <tbody>
          <% for (ExamPayment p : lkPays) { %>
            <tr<%= p.isVoid() ? " style=\"opacity:.55\"" : "" %>>
              <td class="roll" style="font-size:12px"><%= esc(p.getReceiptNo()) %>
                <% if (p.isVoid()) { %><br><span class="st s-UNPAID">CANCELLED</span><% } %></td>
              <td><%= esc(p.getPaymentDate()) %></td>
              <td><%= esc(p.getPaymentMode()) %>
                <% if (p.getTxnRef() != null) { %> <span class="muted">· <%= esc(p.getTxnRef()) %></span><% } %>
                <% if (p.getCollectedBy() != null) { %>
                  <div class="muted" style="font-size:11px">by <%= esc(p.getCollectedBy()) %></div><% } %>
                <% if (p.isVoid() && p.getVoidReason() != null) { %>
                  <div class="muted" style="font-size:11px">Cancelled: <%= esc(p.getVoidReason()) %></div><% } %>
              </td>
              <td class="num"><%= Money.fmt(p.getAmount()) %></td>
              <td>
                <a class="btn alt sm" target="_blank"
                   href="<%= ctx %>/exam-fees?receipt=<%= p.getExamPaymentId() %>">Receipt</a>
                <% if (!p.isVoid()) { %>
                  <form method="post" action="<%= ctx %>/exam-fees" style="display:inline"
                        onsubmit="var r=prompt('Why is this receipt being cancelled?');
                                  if(!r){return false;} this.reason.value=r; return true;">
                    <input type="hidden" name="action" value="void">
                    <input type="hidden" name="paymentId" value="<%= p.getExamPaymentId() %>">
                    <input type="hidden" name="roll" value="<%= esc(lk.getRollNo()) %>">
                    <input type="hidden" name="reason" value="">
                    <button class="btn alt sm" type="submit">Cancel</button>
                  </form>
                <% } %>
              </td>
            </tr>
          <% } %>
          </tbody>
        </table>
      <% } %>
    <% } %>
  </div>

  <%-- ────────── exam picker + totals ────────── --%>
  <% if (exams.isEmpty()) { %>
    <div class="ef-card">
      <h2>No scholarship exams yet</h2>
      <p class="hint">Create an exam and import candidates first — exam fees are charged
         per candidate, so there is nothing to bill until then.</p>
    </div>
  <% } else { %>

    <div class="ef-card">
      <form class="frm" method="get" action="<%= ctx %>/exam-fees">
        <div class="fl"><label>Exam</label>
          <select name="exam" onchange="this.form.submit()">
            <% for (Exam e : exams) { %>
              <option value="<%= e.getExamId() %>" <%= e.getExamId()==examId ? "selected" : "" %>>
                <%= esc(e.getExamName()) %><%= e.getExamDate()==null ? "" : " — " + esc(e.getExamDate()) %>
              </option>
            <% } %>
          </select></div>
        <div class="fl"><label>School</label>
          <select name="school">
            <option value="">All schools</option>
            <% for (String s : schools) { %>
              <option value="<%= esc(s) %>" <%= s.equals(fSchool) ? "selected" : "" %>><%= esc(s) %></option>
            <% } %>
          </select></div>
        <div class="fl"><label>Search</label>
          <input type="text" name="q" value="<%= esc(fQ) %>" placeholder="Roll, name or mobile"></div>
        <input type="hidden" name="state" value="<%= esc(fState) %>">
        <button class="btn alt" type="submit">Apply</button>
        <% if (exam != null) { %>
          <span style="flex:1 1 auto"></span>
          <span class="muted" style="font-size:12.5px">Fee for this exam</span>
        <% } %>
      </form>

      <% if (exam != null) { %>
        <div class="frm" style="margin-top:12px">
          <% if (canPrice) { %>
            <form class="frm" method="post" action="<%= ctx %>/exam-fees">
              <input type="hidden" name="action" value="setfee">
              <input type="hidden" name="examId" value="<%= examId %>">
              <div class="fl"><label>Exam fee (applies to every candidate without their own amount)</label>
                <input type="text" name="fee" style="width:130px"
                       value="<%= Money.fmt(exam.getExamFee()) %>"></div>
              <button class="btn alt" type="submit">Update fee</button>
            </form>
          <% } else { %>
            <div class="fl"><label>Exam fee</label>
              <div style="font-size:15px;font-weight:700;padding:6px 0">
                <%= Money.rs(exam.getExamFee()) %></div>
              <div class="muted" style="font-size:11.5px">Set by the administrator.</div></div>
          <% } %>
          <span style="flex:1 1 auto"></span>
          <a class="btn" href="<%= ctx %>/exam-fees?bulk=1&amp;exam=<%= examId %>"
             >School paid for a batch &rarr;</a>
        </div>
      <% } %>
    </div>

    <% if (tot != null) { %>
      <div class="tiles">
        <div class="tile"><div class="lb">Candidates</div>
          <div class="vl"><%= tot.candidates %></div>
          <div class="sb"><%= tot.waived %> waived</div></div>
        <div class="tile"><div class="lb">Expected</div>
          <div class="vl">Rs. <%= Money.fmt(tot.expected) %></div>
          <div class="sb">total billable</div></div>
        <div class="tile"><div class="lb">Collected</div>
          <div class="vl">Rs. <%= Money.fmt(tot.collected) %></div>
          <div class="sb"><%= pct(tot.collected, tot.expected) %> of expected</div></div>
        <div class="tile due"><div class="lb">Outstanding</div>
          <div class="vl">Rs. <%= Money.fmt(tot.outstanding) %></div>
          <div class="sb"><%= tot.candidatesDue %> candidate(s) still owe</div></div>
      </div>
    <% } %>

    <%-- ────────── the list ────────── --%>
    <div class="ef-card">
      <h2>All candidates<%= exam == null ? "" : " — " + esc(exam.getExamName()) %></h2>
      <p class="hint">Who has paid and who has not. Nothing here is stored as a status —
         it is worked out from the fee and the receipts, so it always matches the money taken.</p>

      <div class="chips" style="margin-bottom:12px">
        <% String base = ctx + "/exam-fees?exam=" + examId
                       + (fSchool.isEmpty() ? "" : "&school=" + url(fSchool))
                       + (fQ.isEmpty() ? "" : "&q=" + url(fQ)) + "&state=";
           String[][] chips = { {"", "All"}, {"UNPAID","Unpaid"}, {"PARTIAL","Part paid"},
                                {"PAID","Paid"}, {"WAIVED","Waived"}, {"NO_FEE","No fee set"} };
           for (String[] c : chips) { %>
          <a class="<%= fState.equals(c[0]) ? "on" : "" %>" href="<%= base %><%= c[0] %>"><%= c[1] %></a>
        <% } %>
      </div>

      <table class="t">
        <thead><tr>
          <th style="width:85px">Roll</th>
          <th>Candidate</th>
          <th>School</th>
          <th style="width:95px" class="num">Fee</th>
          <th style="width:95px" class="num">Paid</th>
          <th style="width:100px" class="num">Balance</th>
          <th style="width:95px">Status</th>
          <th style="width:80px"></th>
        </tr></thead>
        <tbody>
        <%
          java.math.BigDecimal sFee = Money.ZERO, sPaid = Money.ZERO, sBal = Money.ZERO;
          for (CandidateFee f : rows) {
            sFee  = sFee.add(f.getPayable());
            sPaid = sPaid.add(f.getPaid());
            sBal  = sBal.add(f.getBalance());
        %>
          <tr>
            <td class="roll"><%= esc(f.getRollNo()) %></td>
            <td><%= esc(f.getCandidateName()) %>
              <% if (f.getClassName() != null) { %>
                <div class="muted" style="font-size:11.5px">Class <%= esc(f.getClassName()) %></div><% } %></td>
            <td class="muted"><%= esc(nz(f.getSchoolName(), "—")) %></td>
            <td class="num"><%= Money.fmt(f.getPayable()) %></td>
            <td class="num"><%= Money.fmt(f.getPaid()) %></td>
            <td class="num" style="<%= f.isDue() ? "color:#c0392b;font-weight:700" : "" %>">
              <%= Money.fmt(f.getBalance()) %></td>
            <td><span class="st s-<%= f.getState() %>"><%= esc(f.getStateLabel()) %></span></td>
            <td><a class="btn alt sm" href="<%= ctx %>/exam-fees?roll=<%= url(f.getRollNo()) %>">Open</a></td>
          </tr>
        <% } %>

        <% if (rows.isEmpty()) { %>
          <tr><td colspan="8" class="muted" style="padding:18px 9px">
            No candidate matches that filter.</td></tr>
        <% } %>

          <tr class="sum">
            <td colspan="3"><%= rows.size() %> candidate(s)</td>
            <td class="num"><%= Money.fmt(sFee) %></td>
            <td class="num"><%= Money.fmt(sPaid) %></td>
            <td class="num"><%= Money.fmt(sBal) %></td>
            <td colspan="2"></td>
          </tr>
        </tbody>
      </table>
    </div>
  <% } %>
</div>
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

    /** Share of expected that has come in. "-" when nothing is billable. */
    private String pct(java.math.BigDecimal part, java.math.BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return "—";
        return Math.round(part.doubleValue() * 100.0 / whole.doubleValue()) + "%";
    }
%>
