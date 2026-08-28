<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, com.tution.model.Payment, com.tution.model.User,
                 com.tution.model.FeeInstallment, com.tution.service.FeeService,
                 com.tution.util.FeeCalculator" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Payment p = (Payment) request.getAttribute("payment");
    if (p == null) { response.sendRedirect(ctx + "/fees"); return; }
    int totalFee    = (Integer) request.getAttribute("totalFee");
    int paidToDate  = (Integer) request.getAttribute("paidToDate");
    int outstanding = (Integer) request.getAttribute("outstanding");
    FeeService.Position pos = (FeeService.Position) request.getAttribute("position");
    @SuppressWarnings("unchecked")
    List<FeeInstallment> schedule = (List<FeeInstallment>) request.getAttribute("schedule");
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Receipt <%= esc(p.getReceiptNo()) %></title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .toolbar { max-width:640px; margin:18px auto 0; display:flex; justify-content:space-between; align-items:center; padding:0 1rem; }
    .toolbar a { font-size:13px; color:var(--green); font-weight:600; text-decoration:none; }
    .btn-print { background:var(--green); color:#fff !important; border:none; border-radius:7px; padding:9px 18px; font-size:13px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; text-decoration:none; }
    .btn-print:hover { background:var(--green-dark); }
    .receipt { max-width:640px; margin:16px auto 50px; background:#fff; border-radius:var(--radius); box-shadow:var(--shadow); overflow:hidden; }
    .r-head { background:var(--green-dark); color:#fff; padding:22px 26px; display:flex; align-items:center; gap:14px; }
    .r-logo { width:48px; height:48px; min-width:48px; background:var(--accent); border-radius:9px; display:flex; align-items:center; justify-content:center; font-family:'Playfair Display',serif; font-size:24px; font-weight:900; color:var(--green-dark); }
    .r-head .brand { font-family:'Playfair Display',serif; font-size:20px; }
    .r-head .sub { font-size:12px; color:#A8D9BC; margin-top:2px; }
    .r-title { text-align:center; padding:14px; background:var(--accent); color:var(--green-dark); font-weight:800; letter-spacing:1px; text-transform:uppercase; font-size:13px; }
    .r-body { padding:24px 26px; }
    .r-meta { display:flex; justify-content:space-between; font-size:13px; margin-bottom:18px; }
    .r-meta .rno { font-weight:800; color:var(--green-dark); font-size:15px; }
    .r-row { display:flex; justify-content:space-between; font-size:14px; padding:9px 0; border-bottom:1px dashed var(--border); }
    .r-row span:first-child { color:var(--muted); }
    .r-row span:last-child { font-weight:600; }
    .itemised { border:1.5px solid var(--border); border-radius:9px; padding:12px 14px; margin-top:16px; }
    .itemised .i-row { display:flex; justify-content:space-between; font-size:13px; padding:4px 0; }
    .itemised .i-row.cut { color:#C0392B; }
    .itemised .i-row.tot { font-weight:800; color:var(--green-dark); border-top:1.5px solid var(--border);
      margin-top:6px; padding-top:8px; }
    .next-inst { background:#FFF6E0; border:1.5px solid #F0D89A; color:#7A5200; border-radius:9px;
      padding:10px 13px; font-size:13px; margin-top:14px; text-align:center; }
    .amount-box { background:var(--green-light); border:2px solid var(--green); border-radius:10px; padding:16px 20px; text-align:center; margin:20px 0; }
    .amount-box .lbl { font-size:11px; color:var(--muted); font-weight:700; text-transform:uppercase; letter-spacing:1px; }
    .amount-box .amt { font-size:34px; font-weight:900; color:var(--green-dark); line-height:1.1; }
    .ledger { display:grid; grid-template-columns:repeat(3,1fr); gap:8px; text-align:center; margin-top:8px; }
    .ledger div { background:var(--green-pale); border:1px solid var(--border); border-radius:8px; padding:10px 6px; }
    .ledger .v { font-weight:800; color:var(--green-dark); font-size:14px; }
    .ledger .l { font-size:10px; color:var(--muted); text-transform:uppercase; letter-spacing:0.4px; margin-top:2px; }
    .sign { display:flex; justify-content:space-between; margin-top:34px; font-size:12px; color:var(--muted); }
    .sign .line { border-top:1.5px solid var(--text); padding-top:6px; width:160px; text-align:center; }
    .r-foot { text-align:center; font-size:11px; color:var(--muted); padding:14px; border-top:1px solid var(--border); }
    @media print {
      header, .neet-ribbon, .toolbar, .footer, .tabbar { display:none !important; }
      body { background:#fff; }
      .receipt { box-shadow:none; margin:0; max-width:100%; border-radius:0; }
    }
  </style>
</head>
<body class="sp-body">

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="fees"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<%
   boolean waSent    = Boolean.TRUE.equals(request.getAttribute("waSent"));
   boolean waAlready = Boolean.TRUE.equals(request.getAttribute("waAlready"));
   boolean waQueued  = Boolean.TRUE.equals(request.getAttribute("waQueued"));
   String  waLink    = (String) request.getAttribute("waLink");
   String  pdfUrl    = (String) request.getAttribute("pdfUrl");
%>
<div class="toolbar">
  <a href="<%= ctx %>/collect?studentId=<%= p.getStudentId() %>">← Back to student</a>
  <div style="display:flex;gap:10px;align-items:center;">
    <% if (waSent || waAlready) { %><span style="color:#15803D;font-weight:600;font-size:13px;">✓ WhatsApp receipt sent</span>
    <%-- The send now runs in the background, so the page does not wait on the
         gateway. The tap-to-send link stays available as a fallback. --%>
    <% } else if (waQueued) { %><span style="color:#1B4F9C;font-weight:600;font-size:13px;">📤 WhatsApp receipt sending…</span>
    <% } %>
    <% if (!waSent && !waAlready && waLink != null) { %><a class="btn-wa" href="<%= waLink %>" target="_blank" rel="noopener">📱 WhatsApp</a><% } %>
    <% if (pdfUrl != null) { %><a class="btn-print" href="<%= pdfUrl %>" target="_blank" rel="noopener">📄 PDF Receipt</a><% } %>
    <a class="btn-print" href="javascript:window.print()">🖨 Print Receipt</a>
  </div>
</div>

<div class="receipt">
  <div class="r-head">
    <div class="r-logo">H</div>
    <div>
      <div class="brand">Havellsson NEET Samrat</div>
      <div class="sub">Expert Coaching · 11th &amp; 12th Science</div>
    </div>
  </div>
  <div class="r-title">Fee Payment Receipt</div>

  <div class="r-body">
    <div class="r-meta">
      <div><span class="rno"><%= esc(p.getReceiptNo()) %></span></div>
      <div>Date: <strong><%= esc(p.getPaymentDate()) %></strong></div>
    </div>

    <div class="r-row"><span>Student Name</span><span><%= esc(p.getStudentName()) %></span></div>
    <div class="r-row"><span>Admission No</span><span><%= esc(p.getAdmissionNo()) %></span></div>
    <div class="r-row"><span>Class</span><span><%= p.getClassName()==null?"—":esc(p.getClassName()) %></span></div>
    <div class="r-row"><span>Payment Mode</span><span><%= esc(p.getPaymentMode()) %></span></div>
    <% if (p.getRazorpayPaymentId()!=null && !p.getRazorpayPaymentId().isEmpty()) { %>
    <div class="r-row"><span>Txn ID</span><span><%= esc(p.getRazorpayPaymentId()) %></span></div>
    <% } %>
    <% if (p.getTxnRef()!=null && !p.getTxnRef().isEmpty()
           && !p.getTxnRef().equals(p.getRazorpayPaymentId())) { %>
    <div class="r-row"><span>Reference</span><span><%= esc(p.getTxnRef()) %></span></div>
    <% } %>
    <% if (p.getRemarks()!=null && !p.getRemarks().isEmpty()) { %>
    <div class="r-row"><span>Remarks</span><span><%= esc(p.getRemarks()) %></span></div>
    <% } %>

    <%-- Itemised fee breakdown. A parent who was granted a concession must be
         able to see it on the receipt, not just a net figure. --%>
    <% if (pos != null) { %>
    <div class="itemised">
      <div class="i-row"><span>Registration Fee</span><span><%= FeeCalculator.inr(pos.registrationFee) %></span></div>
      <div class="i-row"><span>Study Material</span><span><%= FeeCalculator.inr(pos.materialFee) %></span></div>
      <div class="i-row"><span>Tuition Fee</span><span><%= FeeCalculator.inr(pos.courseFee) %></span></div>
      <% if (pos.discount > 0) { %>
      <div class="i-row cut"><span>Discount</span><span>- <%= FeeCalculator.inr(pos.discount) %></span></div>
      <% } %>
      <% if (pos.scholarship > 0) { %>
      <div class="i-row cut"><span>Scholarship</span><span>- <%= FeeCalculator.inr(pos.scholarship) %></span></div>
      <% } %>
      <div class="i-row tot"><span>Total Payable</span><span><%= FeeCalculator.inr(pos.total) %></span></div>
    </div>
    <% } %>

    <div class="amount-box">
      <div class="lbl">Amount Received</div>
      <div class="amt"><%= FeeCalculator.inr(p.getAmount()) %></div>
    </div>

    <div class="ledger">
      <div><div class="v"><%= FeeCalculator.inr(totalFee) %></div><div class="l">Total Fee</div></div>
      <div><div class="v"><%= FeeCalculator.inr(paidToDate) %></div><div class="l">Paid To Date</div></div>
      <div><div class="v"><%= FeeCalculator.inr(outstanding) %></div><div class="l">Balance Due</div></div>
    </div>

    <%-- Next instalment, so the parent leaves knowing when to come back. --%>
    <% if (schedule != null && !schedule.isEmpty()) {
         com.tution.model.FeeInstallment nx = null;
         for (com.tution.model.FeeInstallment i : schedule) { if (!i.isSettled()) { nx = i; break; } }
         if (nx != null) { %>
      <div class="next-inst">Next instalment: <strong><%= FeeCalculator.inr(nx.getBalance()) %></strong>
        due <strong><%= esc(nx.getDueDate()) %></strong></div>
    <% } } %>

    <div class="sign">
      <div>Collected by: <strong><%= esc(p.getCollectedBy()) %></strong></div>
      <div class="line">Authorised Signatory</div>
    </div>
  </div>

  <div class="r-foot">This is a computer-generated receipt. Fees once paid are non-refundable unless the batch is cancelled by the institute.</div>
</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="fees"/></jsp:include>

</body>
</html>
