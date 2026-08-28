<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.dao.ExpenseDAO" %>
<%@ page import="com.tution.model.Expense, com.tution.model.FundAccount,
                 com.tution.model.User, com.tution.model.Vendor, com.tution.model.WorkOrder" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<Expense> rows = (List<Expense>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<Expense>();

    @SuppressWarnings("unchecked") List<FundAccount> funds =
        (List<FundAccount>) request.getAttribute("funds");
    if (funds == null) funds = new ArrayList<FundAccount>();

    @SuppressWarnings("unchecked") List<Vendor> vendors =
        (List<Vendor>) request.getAttribute("vendors");
    if (vendors == null) vendors = new ArrayList<Vendor>();

    @SuppressWarnings("unchecked") List<WorkOrder> orders =
        (List<WorkOrder>) request.getAttribute("orders");
    if (orders == null) orders = new ArrayList<WorkOrder>();

    @SuppressWarnings("unchecked") List<Object[]> cats =
        (List<Object[]>) request.getAttribute("cats");
    if (cats == null) cats = new ArrayList<Object[]>();

    @SuppressWarnings("unchecked") List<Object[]> byCat =
        (List<Object[]>) request.getAttribute("byCat");
    if (byCat == null) byCat = new ArrayList<Object[]>();

    ExpenseDAO.Totals tot = (ExpenseDAO.Totals) request.getAttribute("totals");

    int fFund   = intOf(request.getAttribute("fundId"));
    int fVendor = intOf(request.getAttribute("vendorId"));
    int fWo     = intOf(request.getAttribute("woId"));
    int fCat    = intOf(request.getAttribute("catId"));
    String fStatus = str(request.getAttribute("status"));
    String fQ      = str(request.getAttribute("q"));
    String fFrom   = str(request.getAttribute("from"));
    String fTo     = str(request.getAttribute("to"));

    String today = java.time.LocalDate.now().toString();

    BigDecimal biggest = Money.ZERO;
    for (Object[] c : byCat) {
        BigDecimal v = (BigDecimal) c[1];
        if (v.compareTo(biggest) > 0) biggest = v;
    }

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
<title>Expenses – Havellsson NEET Samrat</title>
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
  .tile .lb { font-size:11.5px; color:#5a6b73; font-weight:600; text-transform:uppercase; letter-spacing:.3px; }
  .tile .vl { font-size:22px; font-weight:700; color:#0E5C3F; margin-top:3px; font-variant-numeric:tabular-nums; }
  .tile.due .vl { color:#c0392b; }
  .tile .sb { font-size:11.5px; color:#7b8b93; margin-top:2px; }

  /* ── tables ──
     The register carries eight columns and will not fit a laptop, so it scrolls
     inside its own box rather than pushing the page sideways, and the Cancel
     column is pinned to the right edge so the action never scrolls out of
     reach. Row backgrounds are repeated on the pinned cell because a sticky
     cell slides over its neighbours and would otherwise be transparent. */
  .t-wrap { overflow-x:auto; border:1px solid #eceff1; border-radius:9px; }
  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:10px 11px; border-bottom:1px solid #eceff1; text-align:left;
      vertical-align:top; }
  table.t th { background:#f4f8f6; font-size:10.5px; font-weight:800; color:#3f5b4d;
      text-transform:uppercase; letter-spacing:.45px; white-space:nowrap;
      border-bottom:1px solid #dbe7e0; vertical-align:middle; }
  table.t tbody tr:last-child td { border-bottom:none; }
  table.t tbody tr:hover td { background:#f7fbf9; }
  table.t tr.sum td { background:#f4f8f6; font-weight:700; border-top:2px solid #dbe7e0;
      border-bottom:none; }
  /* cancelled: dimmed and tinted, not just faded, so it reads as a state */
  table.t tr.voided td { background:#fdf8f8; color:#96a5aa; }
  table.t tr.voided:hover td { background:#fbf2f2; }
  table.t tr.voided a { color:#96a5aa; }

  .t-wrap table.t { min-width:1010px; }
  .t-wrap thead th:last-child,
  .t-wrap tbody td:last-child { position:sticky; right:0; }
  .t-wrap thead th:last-child { background:#f4f8f6; z-index:3; }
  .t-wrap tbody td:last-child { background:#fff; z-index:2;
      box-shadow:-7px 0 7px -7px rgba(0,0,0,.16); }
  .t-wrap tbody tr:hover td:last-child { background:#f7fbf9; }
  .t-wrap tbody tr.voided td:last-child { background:#fdf8f8; }
  .t-wrap tfoot td:last-child { position:sticky; right:0; background:#f4f8f6; z-index:2; }

  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .muted { color:#7b8b93; }
  .mono { font-family:ui-monospace,Consolas,monospace; font-weight:700; letter-spacing:.5px; }
  .sub2 { font-size:11px; color:#8b9aa1; margin-top:3px; line-height:1.4; }

  .st { font-size:10px; font-weight:800; padding:2px 9px; border-radius:20px; white-space:nowrap;
        text-transform:uppercase; letter-spacing:.4px; display:inline-block; }
  .s-VOID { background:#f7d4d4; color:#8c2020; }

  /* ── form controls, page-wide ──
     One height, one radius, one border everywhere, and the select arrow drawn
     rather than left to the browser — no native one sits level with an input. */
  .frm { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  /* controls are width:100% now, so flex items need a basis to size against */
  .frm > .fl { flex:1 1 165px; }
  .frm.tight > .fl { flex:0 0 200px; }

  .fgrid { display:grid; grid-template-columns:repeat(6,1fr); gap:14px 15px; align-items:start; }
  @media(max-width:1080px){ .fgrid { grid-template-columns:repeat(4,1fr); } }
  @media(max-width:760px) { .fgrid { grid-template-columns:repeat(2,1fr); } }
  @media(max-width:460px) { .fgrid { grid-template-columns:1fr; } }
  .sp2 { grid-column:span 2; }
  .sp4 { grid-column:span 4; }

  .fl { display:flex; flex-direction:column; gap:6px; min-width:0; }
  .fl label { font-size:10.5px; font-weight:800; text-transform:uppercase; letter-spacing:.45px;
      color:#3f5b4d; }
  .fl label .opt { font-weight:600; text-transform:none; letter-spacing:0; color:#8fa79a;
      font-size:11px; }
  .fl input, .fl select { width:100%; height:38px; box-sizing:border-box; padding:0 11px;
      border:1px solid #cfd6da; border-radius:7px; background:#fff; font-size:13.5px;
      font-family:inherit; color:#1f3a2c; }
  .fl input::placeholder { color:#9fb4a8; }
  .fl input:hover, .fl select:hover { border-color:#a9bfb4; }
  .fl input:focus, .fl select:focus { outline:none; border-color:#0E5C3F;
      box-shadow:0 0 0 3px rgba(14,92,63,.15); }
  .fl select { appearance:none; -webkit-appearance:none; -moz-appearance:none;
      padding-right:31px; cursor:pointer;
      background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='11' height='7' viewBox='0 0 11 7'%3E%3Cpath d='M1 1l4.5 4.5L10 1' fill='none' stroke='%230E5C3F' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
      background-repeat:no-repeat; background-position:right 11px center; }

  .fact { display:flex; justify-content:flex-end; align-items:center; gap:13px; flex-wrap:wrap;
      margin-top:16px; padding-top:15px; border-top:1px solid #eceff1; }
  .fact .note { font-size:12px; color:#7b8b93; margin-right:auto; max-width:60ch; }
  .link-clear { font-size:12.5px; color:#7b8b93; text-decoration:none; }
  .link-clear:hover { color:#0E5C3F; }
  .btn { background:#0E5C3F; color:#fff; border:0; padding:8px 16px; border-radius:6px;
         font-size:13px; font-weight:600; cursor:pointer; text-decoration:none; display:inline-block; }
  .btn.alt { background:#fff; color:#42555e; border:1px solid #cfd6da; }
  .btn.sm  { padding:4px 10px; font-size:11.5px; }
  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.ok { background:#e3f3e9; color:#1b6b39; border:1px solid #bfe0cc; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .msg.warn{ background:#fdf1e3; color:#8a4b12; border:1px solid #f0d3ae; }

  /* ── Record an expense ──
     This is the one thing the page exists to do, but as a plain white card it
     sat flat among the read-only tables below it. Lifted out with a solid green
     header, a tint that fades back to white behind the fields, and a heavier
     shadow, so the eye lands here first. */
  .ef-card.record { padding:0; overflow:hidden; background:#fff; border:1px solid #bfe0cc;
      border-top:4px solid #0E5C3F; box-shadow:0 6px 20px rgba(14,92,63,.13); }
  .record .rec-head { display:flex; align-items:baseline; gap:10px; flex-wrap:wrap;
      background:#eaf6ef; border-bottom:1px solid #c9e4d6; padding:13px 18px; }
  .record .rec-head h2 { margin:0; font-size:16.5px; color:#0b4a33; letter-spacing:.2px; }
  .record .rec-head .lede { font-size:12.5px; color:#3f7a5c; }
  .record .rec-body { padding:16px 18px 18px; }
  .record p.hint { color:#5a6b73; margin:0 0 15px; max-width:82ch; }

  /* The record card keeps a green-tinted variant of the shared controls, so it
     reads as the primary panel without diverging from the page's shapes. */
  .record .grid { display:grid; grid-template-columns:repeat(6,1fr); gap:14px 15px;
      align-items:start; }
  @media(max-width:1080px){ .record .grid { grid-template-columns:repeat(4,1fr); } }
  @media(max-width:760px) { .record .grid { grid-template-columns:repeat(2,1fr); } }
  @media(max-width:460px) { .record .grid { grid-template-columns:1fr; } }
  .record .fl input, .record .fl select { border-color:#b9d7c6; }
  .record .fl input:hover, .record .fl select:hover { border-color:#8fc4a8; }
  /* the figure everything else hangs off */
  .record #amount { font-size:16px; font-weight:700; color:#0E5C3F;
      font-variant-numeric:tabular-nums; }
  #woHint { font-size:11.5px; line-height:1.35; }

  .record .rec-actions { display:flex; justify-content:flex-end; align-items:center; gap:13px;
      margin-top:16px; padding-top:15px; border-top:1px solid #e6f1ea; }
  .record .rec-actions .note { font-size:12px; color:#7b8b93; margin-right:auto; }
  .record .btn { padding:11px 26px; font-size:14px; box-shadow:0 2px 10px rgba(14,92,63,.28); }
  .record .btn:hover { background:#0b4a33; }

  .catbar { display:flex; align-items:center; gap:11px; font-size:12.5px; padding:5px 0; }
  .catbar + .catbar { border-top:1px solid #f2f6f4; }
  .catbar .nm { width:170px; color:#2f4a3c; font-weight:600; overflow:hidden;
                text-overflow:ellipsis; white-space:nowrap; }
  .catbar .track { flex:1; min-width:60px; height:10px; background:#eef3f0; border-radius:6px;
                   overflow:hidden; }
  .catbar .track i { display:block; height:100%; background:linear-gradient(90deg,#1b8a5c,#0E5C3F);
                     border-radius:6px; }
  .catbar .vl { width:115px; text-align:right; font-variant-numeric:tabular-nums; font-weight:700;
                color:#1f3a2c; }
  @media(max-width:560px){ .catbar .nm { width:110px; } .catbar .vl { width:92px; } }
  #woHint { font-size:11.5px; margin-top:4px; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="expenses"/>
</jsp:include>

<div class="ef-wrap">

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <% if (funds.isEmpty()) { %>
    <div class="ef-card">
      <h2>No fund to spend from</h2>
      <p class="hint" style="margin:0">An expense draws money out of a fund, so there has to be
         one first. <a href="<%= ctx %>/fund">Go to the fund &rarr;</a></p>
    </div>
  <% } else { %>

  <%-- ────────── record an expense ────────── --%>
  <div class="ef-card record">
    <div class="rec-head">
      <h2>Record an expense</h2>
      <span class="lede">Money leaves the fund the moment you save</span>
    </div>
    <div class="rec-body">
    <p class="hint">Naming a vendor narrows the work orders to that vendor's own — an expense
       can only settle an order raised on the same vendor, and never more than the order has
       left.</p>

    <form method="post" action="<%= ctx %>/expenses" id="expForm">
      <input type="hidden" name="action" value="add">
      <div class="grid">
        <%-- the money --%>
        <div class="fl sp2"><label for="fundId">Paid from</label>
          <select name="fundId" id="fundId" required>
            <% for (FundAccount f : funds) { %>
              <option value="<%= f.getFundId() %>"><%= esc(f.getName()) %>
                &nbsp;&middot;&nbsp;Rs. <%= Money.fmt(f.getBalance()) %></option>
            <% } %>
          </select></div>
        <div class="fl"><label for="amount">Amount</label>
          <input type="text" name="amount" id="amount" required inputmode="decimal"
                 placeholder="0.00" oninput="checkRoom()"></div>
        <div class="fl"><label for="tax">Of which tax</label>
          <input type="text" name="tax" id="tax" inputmode="decimal" placeholder="0.00"></div>
        <div class="fl"><label for="date">Date</label>
          <input type="date" name="date" id="date" value="<%= today %>"></div>
        <div class="fl"><label for="mode">Mode</label>
          <select name="mode" id="mode">
            <option>Cash</option><option>Bank Transfer</option><option>UPI</option>
            <option>Cheque</option><option>Card</option><option>Online</option>
          </select></div>

        <%-- what it was --%>
        <div class="fl sp2"><label for="categoryId">Category</label>
          <select name="categoryId" id="categoryId">
            <option value="0">&mdash; none &mdash;</option>
            <% for (Object[] c : cats) { %>
              <option value="<%= c[0] %>"><%= esc((String) c[1]) %></option>
            <% } %>
          </select></div>
        <div class="fl sp2"><label for="vendorSel">Vendor <span class="opt">(optional)</span></label>
          <select name="vendorId" id="vendorSel" onchange="fillOrders()">
            <option value="0">&mdash; no vendor &mdash;</option>
            <% for (Vendor v : vendors) { %>
              <option value="<%= v.getVendorId() %>"><%= esc(v.getName()) %></option>
            <% } %>
          </select></div>
        <div class="fl sp2"><label for="woSel">Work order <span class="opt">(optional)</span></label>
          <select name="workOrderId" id="woSel" onchange="checkRoom()">
            <option value="0">&mdash; not against a work order &mdash;</option>
          </select>
          <div id="woHint" class="muted">Choose a vendor first to see their open work orders.</div>
        </div>

        <%-- the paperwork --%>
        <div class="fl sp4"><label for="description">What it was for</label>
          <input type="text" name="description" id="description"
                 placeholder="60,000 OMR sheets, second lot"></div>
        <div class="fl"><label for="invoiceNo">Invoice no</label>
          <input type="text" name="invoiceNo" id="invoiceNo"></div>
        <div class="fl"><label for="txnRef">Reference</label>
          <input type="text" name="txnRef" id="txnRef" placeholder="Cheque / UTR"></div>
      </div>
      <div id="roomWarn" class="msg err" style="display:none;margin:15px 0 0"></div>

      <div class="rec-actions">
        <span class="note">Saved as a numbered voucher &mdash; correct a mistake by voiding it,
          never by deleting.</span>
        <button class="btn" type="submit">Pay and record</button>
      </div>
    </form>
    </div>
  </div>

  <%-- ────────── totals ────────── --%>
  <% if (tot != null) { %>
    <div class="tiles">
      <div class="tile due"><div class="lb">Spent</div>
        <div class="vl">Rs. <%= Money.fmt(tot.spent) %></div>
        <div class="sb"><%= fFrom %> to <%= fTo %></div></div>
      <div class="tile"><div class="lb">Vouchers</div>
        <div class="vl"><%= tot.vouchers %></div>
        <div class="sb">cancelled ones not counted</div></div>
      <div class="tile"><div class="lb">Of which tax</div>
        <div class="vl">Rs. <%= Money.fmt(tot.tax) %></div>
        <div class="sb">included in the amounts</div></div>
      <div class="tile"><div class="lb">Fund balance</div>
        <div class="vl">Rs. <%= funds.isEmpty() ? "0.00" : Money.fmt(funds.get(0).getBalance()) %></div>
        <div class="sb"><%= funds.isEmpty() ? "" : esc(funds.get(0).getName()) %></div></div>
    </div>
  <% } %>

  <%-- ────────── where it went ────────── --%>
  <% if (!byCat.isEmpty()) { %>
    <div class="ef-card">
      <h2>Where it went</h2>
      <p class="hint">Live vouchers between <%= esc(fFrom) %> and <%= esc(fTo) %>.</p>
      <% for (Object[] c : byCat) {
           BigDecimal v = (BigDecimal) c[1];
           int w = biggest.signum() == 0 ? 0
                 : (int) Math.round(v.doubleValue() * 100.0 / biggest.doubleValue());
      %>
        <div class="catbar">
          <span class="nm"><%= esc((String) c[0]) %></span>
          <span class="track"><i style="width:<%= w %>%"></i></span>
          <span class="vl"><%= Money.fmt(v) %></span>
          <span class="muted" style="width:60px"><%= c[2] %> item(s)</span>
        </div>
      <% } %>
    </div>
  <% } %>

  <%-- ────────── the register ────────── --%>
  <div class="ef-card">
    <h2>Expense register</h2>
    <p class="hint">Every voucher, with the vendor and the work order it settled. Cancelled
       vouchers stay listed and are credited back to the fund — nothing is ever deleted.</p>

    <% boolean anyFilter = fVendor > 0 || fCat > 0 || fWo > 0
                        || (fStatus != null && !fStatus.isEmpty())
                        || (fQ != null && !fQ.isEmpty()); %>
    <form method="get" action="<%= ctx %>/expenses">
      <div class="fgrid">
        <div class="fl"><label for="rgFrom">From</label>
          <input type="date" name="from" id="rgFrom" value="<%= esc(fFrom) %>"></div>
        <div class="fl"><label for="rgTo">To</label>
          <input type="date" name="to" id="rgTo" value="<%= esc(fTo) %>"></div>
        <div class="fl"><label for="rgVendor">Vendor</label>
          <select name="vendor" id="rgVendor">
            <option value="0">All vendors</option>
            <% for (Vendor v : vendors) { %>
              <option value="<%= v.getVendorId() %>" <%= v.getVendorId()==fVendor ? "selected" : "" %>
                ><%= esc(v.getName()) %></option>
            <% } %>
          </select></div>
        <div class="fl"><label for="rgCat">Category</label>
          <select name="cat" id="rgCat">
            <option value="0">All categories</option>
            <% for (Object[] c : cats) { %>
              <option value="<%= c[0] %>" <%= ((Integer) c[0]).intValue()==fCat ? "selected" : "" %>
                ><%= esc((String) c[1]) %></option>
            <% } %>
          </select></div>
        <div class="fl"><label for="rgStatus">Status</label>
          <select name="status" id="rgStatus">
            <option value="">All</option>
            <option value="ACTIVE" <%= "ACTIVE".equals(fStatus) ? "selected" : "" %>>Live only</option>
            <option value="VOID"   <%= "VOID".equals(fStatus)   ? "selected" : "" %>>Cancelled only</option>
          </select></div>
        <div class="fl"><label for="rgQ">Search</label>
          <input type="text" name="q" id="rgQ" value="<%= esc(fQ) %>"
                 placeholder="Voucher, invoice, order"></div>
      </div>
      <% if (fWo > 0) { %><input type="hidden" name="wo" value="<%= fWo %>"><% } %>
      <div class="fact">
        <% if (anyFilter) { %>
          <a class="link-clear" href="<%= ctx %>/expenses?from=<%= esc(fFrom) %>&amp;to=<%= esc(fTo) %>">Clear filters</a>
        <% } %>
        <button class="btn alt" type="submit">Apply</button>
      </div>
    </form>

    <% if (rows.isEmpty()) { %>
      <p class="hint" style="margin:14px 0 0"><%= anyFilter
           ? "No voucher in this range matches those filters."
           : "No vouchers in this range." %></p>
    <% } else {
         BigDecimal shown = Money.ZERO;
         int nVoid = 0;
         for (Expense x : rows) { if (x.isVoid()) nVoid++; else shown = shown.add(x.getAmount()); }
    %>
      <div class="t-wrap" style="margin-top:14px">
      <table class="t">
        <thead><tr>
          <th style="width:135px">Voucher</th><th style="width:100px">Date</th>
          <th style="min-width:230px">What for</th><th style="width:150px">Vendor</th>
          <th style="width:160px">Work order</th><th style="width:120px">Category</th>
          <th style="width:125px" class="num">Amount</th>
          <th style="width:95px">Action</th>
        </tr></thead>
        <tbody>
        <% for (Expense x : rows) { %>
          <tr<%= x.isVoid() ? " class=\"voided\"" : "" %>>
            <td class="mono" style="font-size:12px"><%= esc(x.getVoucherNo()) %>
              <% if (x.isVoid()) { %><div style="margin-top:4px"><span class="st s-VOID">Cancelled</span></div><% } %></td>
            <td style="white-space:nowrap"><%= esc(x.getExpenseDate()) %></td>
            <td><%= esc(nz(x.getDescription(), "—")) %>
              <div class="sub2">
                <%= esc(x.getPaymentMode()) %>
                <% if (x.getTxnRef() != null) { %> &middot; <%= esc(x.getTxnRef()) %><% } %>
                <% if (x.getInvoiceNo() != null) { %> &middot; inv <%= esc(x.getInvoiceNo()) %><% } %>
                <% if (x.getCreatedBy() != null) { %> &middot; by <%= esc(x.getCreatedBy()) %><% } %>
              </div>
              <% if (x.isVoid() && x.getVoidReason() != null) { %>
                <div class="sub2">Cancelled: <%= esc(x.getVoidReason()) %></div>
              <% } %></td>
            <td class="muted">
              <% if (x.hasVendor()) { %>
                <a href="<%= ctx %>/vendors?id=<%= x.getVendorId() %>"><%= esc(x.getVendorName()) %></a>
              <% } else { %>—<% } %></td>
            <td class="muted">
              <% if (x.hasWorkOrder()) { %>
                <a class="mono" style="font-size:11.5px"
                   href="<%= ctx %>/work-orders?id=<%= x.getWorkOrderId() %>"><%= esc(x.getWoNo()) %></a>
                <div class="sub2"><%= esc(x.getWoTitle()) %></div>
              <% } else { %>—<% } %></td>
            <td class="muted"><%= esc(nz(x.getCategoryName(), "—")) %></td>
            <td class="num"><strong><%= Money.fmt(x.getAmount()) %></strong>
              <% if (x.getTaxAmount().signum() > 0) { %>
                <div class="sub2">tax <%= Money.fmt(x.getTaxAmount()) %></div>
              <% } %></td>
            <td>
              <% if (!x.isVoid()) { %>
                <form method="post" action="<%= ctx %>/expenses" style="display:inline"
                      onsubmit="var r=prompt('Why is this voucher being cancelled? The amount goes back to the fund.');
                                if(!r){return false;} this.reason.value=r; return true;">
                  <input type="hidden" name="action" value="void">
                  <input type="hidden" name="expenseId" value="<%= x.getExpenseId() %>">
                  <input type="hidden" name="reason" value="">
                  <button class="btn alt sm" type="submit">Cancel</button>
                </form>
              <% } else { %><span class="muted">—</span><% } %>
            </td>
          </tr>
        <% } %>
        </tbody>
        <tfoot>
          <tr class="sum">
            <td colspan="6"><%= rows.size() - nVoid %> live voucher<%= (rows.size()-nVoid)==1?"":"s" %><%=
              nVoid == 0 ? "" : " — " + nVoid + " cancelled row" + (nVoid==1?"":"s")
                              + " listed but not counted" %></td>
            <td class="num"><%= Money.fmt(shown) %></td>
            <td></td>
          </tr>
        </tfoot>
      </table>
      </div>
    <% } %>
  </div>

  <%-- ────────── categories ────────── --%>
  <div class="ef-card">
    <details>
      <summary style="cursor:pointer;font-size:13px;color:#0E5C3F;font-weight:600">
        Add an expense category</summary>
      <form class="frm tight" method="post" action="<%= ctx %>/expenses" style="margin-top:13px">
        <input type="hidden" name="action" value="addcat">
        <div class="fl"><label for="catName">Category name</label>
          <input type="text" name="name" id="catName" required placeholder="e.g. Printing"></div>
        <button class="btn alt" type="submit">Add category</button>
      </form>
    </details>
  </div>

  <% } %>
</div>

<script>
  // Every work order that can still take money, grouped by vendor. Embedded
  // rather than fetched so the dropdown cannot disagree with the server: this is
  // the same payableAll() list the over-payment guard measures against.
  var ORDERS = [
<% for (WorkOrder w : orders) { %>
    { v: <%= w.getVendorId() %>, id: <%= w.getWorkOrderId() %>,
      no: "<%= js(w.getWoNo()) %>", t: "<%= js(w.getTitle()) %>",
      left: <%= w.getRemaining() %>, val: <%= w.getOrderValue() %> },
<% } %>
  ];

  function fmt(n) {
    var neg = n < 0; n = Math.abs(n);
    var s = n.toFixed(2), w = s.slice(0, -3), d = s.slice(-3);
    var last = w.slice(-3), rest = w.slice(0, -3), out = last;
    while (rest.length > 2) { out = rest.slice(-2) + ',' + out; rest = rest.slice(0, -2); }
    if (rest.length) out = rest + ',' + out;
    return (neg ? '-' : '') + out + d;
  }
  function money(s) {
    var n = parseFloat(String(s).replace(/[^0-9.\-]/g, ''));
    return isNaN(n) ? 0 : n;
  }

  function fillOrders() {
    var v = parseInt(document.getElementById('vendorSel').value, 10) || 0;
    var sel = document.getElementById('woSel'), hint = document.getElementById('woHint');
    sel.innerHTML = '<option value="0">— not against a work order —</option>';
    if (!v) { hint.textContent = 'Choose a vendor first to see their open work orders.';
              checkRoom(); return; }
    var mine = ORDERS.filter(function (o) { return o.v === v; });
    mine.forEach(function (o) {
      var opt = document.createElement('option');
      opt.value = o.id;
      opt.textContent = o.no + ' — ' + o.t + '  (Rs. ' + fmt(o.left) + ' left of ' + fmt(o.val) + ')';
      opt.dataset.left = o.left;
      sel.appendChild(opt);
    });
    hint.textContent = mine.length
      ? mine.length + ' open work order(s) for this vendor.'
      : 'This vendor has no open work orders — a draft or a fully paid order cannot take money.';
    checkRoom();
  }

  // Says what the amount will do before it is submitted. The server refuses an
  // over-payment under a row lock either way; this just saves the round trip and
  // names the remaining balance while the invoice is still in hand.
  function checkRoom() {
    var sel = document.getElementById('woSel');
    var opt = sel.options[sel.selectedIndex];
    var box = document.getElementById('roomWarn');
    var amt = money(document.getElementById('amount').value);
    if (!opt || !opt.dataset.left || !amt) { box.style.display = 'none'; return; }
    var left = parseFloat(opt.dataset.left);
    if (amt > left) {
      box.style.display = 'block';
      box.textContent = 'Rs. ' + fmt(amt - left) + ' more than ' + opt.textContent.split(' —')[0] +
        ' has left (Rs. ' + fmt(left) + '). This will be refused.';
    } else {
      box.style.display = 'none';
    }
  }
  fillOrders();
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

    /** For values going into a JavaScript string literal. */
    private String js(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"':  b.append("\\\""); break;
                case '\'': b.append("\\'");  break;
                case '\n': b.append("\\n");  break;
                case '\r': break;
                case '<':  b.append("\\u003C"); break;   // never close the script tag
                case '>':  b.append("\\u003E"); break;
                case '&':  b.append("\\u0026"); break;
                default:   b.append(c);
            }
        }
        return b.toString();
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private int intOf(Object o) {
        return (o instanceof Integer) ? ((Integer) o).intValue() : 0;
    }

    private String nz(String s, String dflt) {
        return (s == null || s.trim().isEmpty()) ? dflt : s;
    }
%>
