<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.FundAccount, com.tution.model.FundTransaction, com.tution.model.User" %>
<%@ page import="com.tution.model.FundAudit" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<FundAccount> funds =
        (List<FundAccount>) request.getAttribute("funds");
    if (funds == null) funds = new ArrayList<FundAccount>();

    FundAccount sel = (FundAccount) request.getAttribute("selected");

    @SuppressWarnings("unchecked") List<FundTransaction> rows =
        (List<FundTransaction>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<FundTransaction>();

    String from = (String) request.getAttribute("from");
    String to   = (String) request.getAttribute("to");
    BigDecimal opening  = (BigDecimal) request.getAttribute("opening");
    BigDecimal periodCr = (BigDecimal) request.getAttribute("periodCr");
    BigDecimal periodDr = (BigDecimal) request.getAttribute("periodDr");
    BigDecimal closing  = (BigDecimal) request.getAttribute("closing");
    if (opening  == null) opening  = Money.ZERO;
    if (periodCr == null) periodCr = Money.ZERO;
    if (periodDr == null) periodDr = Money.ZERO;
    if (closing  == null) closing  = Money.ZERO;

    @SuppressWarnings("unchecked") List<FundAudit> auditTrail =
        (List<FundAudit>) request.getAttribute("auditTrail");
    if (auditTrail == null) auditTrail = new ArrayList<FundAudit>();

    // How many expense vouchers point at the selected fund. Together with its
    // transaction count this decides whether Delete is offered or only Close.
    Integer selExpensesObj = (Integer) request.getAttribute("selExpenses");
    int selExpenses = selExpensesObj == null ? 0 : selExpensesObj.intValue();

    // The create form is a top-level action now, opened by the header button
    // (?new=1) and forced open when there is no fund at all, since nothing else
    // on this screen works until one exists.
    boolean showCreate = funds.isEmpty() || "1".equals(request.getParameter("new"));

    BigDecimal totalHeld = Money.ZERO;
    int openCount = 0;
    for (FundAccount f : funds) {
        totalHeld = totalHeld.add(f.getBalance());
        if (f.isActive()) openCount++;
    }
    int closedCount = funds.size() - openCount;

    // Closed funds are kept out of the tile row: it is a list of what you can
    // spend from, and a closed fund is not that. They stay one click away
    // rather than gone — a closed fund still holds its statement, and may still
    // hold money, so it must never become unreachable.
    boolean showClosed = "1".equals(request.getParameter("closed"));
    // Posted by the tile forms so acting on a closed fund keeps it in view.
    String closedFlag = showClosed ? "<input type=\"hidden\" name=\"closed\" value=\"1\">" : "";

    String today = java.time.LocalDate.now().toString();

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
<title>Expense Fund – Havellsson NEET Samrat</title>
<link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
<style>
  .fd-wrap { max-width:1180px; margin:18px auto; padding:0 14px; }
  .fd-card { background:#fff; border:1px solid #e3e6e8; border-radius:10px; padding:16px; margin-bottom:14px; }
  .fd-card h2 { margin:0 0 3px; font-size:16px; }
  .fd-card p.hint { color:#5a6b73; font-size:13px; margin:0 0 12px; }

  .fd-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px;
             flex-wrap:wrap; margin:0 0 14px; }
  .fd-head h1 { margin:0; font-size:20px; color:#0E5C3F; }
  .fd-head .sub { margin:3px 0 0; font-size:12.5px; color:#7b8b93; }
  /* the newly opened form should read as the thing you just asked for */
  .fd-newfund { border-left:4px solid #0E5C3F; }

  /* ── the page's primary action ──
     Same treatment as "Record an expense": tinted header, dark green text, and
     lifted off the flat card stack so the eye lands on it first. */
  .fd-card.primary { padding:0; overflow:hidden; border:1px solid #bfe0cc;
      border-top:4px solid #0E5C3F; box-shadow:0 6px 20px rgba(14,92,63,.13); }
  .primary .p-head { display:flex; align-items:baseline; gap:10px; flex-wrap:wrap;
      background:#eaf6ef; border-bottom:1px solid #c9e4d6; padding:13px 18px; }
  .primary .p-head h2 { margin:0; font-size:16.5px; color:#0b4a33; letter-spacing:.2px; }
  .primary .p-head .lede { font-size:12.5px; color:#3f7a5c; }
  .primary .p-body { padding:16px 18px 18px; }
  .primary .p-body p.hint { margin:0 0 15px; max-width:82ch; }

  .funds { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
  .fd-closed-toggle { margin:-6px 0 14px; font-size:12.5px; }
  .fd-closed-toggle a { color:#7b8b93; }
  /* The tile is no longer one big link: it carries its own rename and close
     controls, and interactive elements cannot sit inside an <a>. The figures
     stay a link, the actions sit in a strip beneath them. */
  .fund { flex:1 1 250px; background:#fff; border:1px solid #e3e6e8; border-left:4px solid #0E5C3F;
          border-radius:10px; display:flex; flex-direction:column; }
  .fund.on { border-color:#0E5C3F; box-shadow:0 0 0 2px rgba(14,92,63,.13); }
  .fund.neg { border-left-color:#c0392b; }
  .fmain { display:block; padding:13px 15px 11px; text-decoration:none; color:inherit; }
  .facts { display:flex; align-items:center; gap:12px; flex-wrap:wrap;
           border-top:1px solid #eceff1; padding:7px 15px 8px; }
  .facts form { margin:0; display:flex; gap:6px; align-items:center; flex:1 1 auto; }
  .lnk { background:none; border:0; padding:0; font:inherit; font-size:11.5px; font-weight:600;
         color:#0E5C3F; cursor:pointer; }
  .lnk:hover { text-decoration:underline; }
  .lnk.warn { color:#8c2020; }
  details.fren > summary { list-style:none; font-size:11.5px; font-weight:600;
                           color:#0E5C3F; cursor:pointer; }
  details.fren > summary::-webkit-details-marker { display:none; }
  /* opened, the rename row needs the whole tile width to be usable */
  details.fren[open] { flex:1 1 100%; }
  /* Deliberately shorter than the 38px page control: a full-height field would
     dominate a tile that is mostly a number. */
  details.fren input[type=text] { flex:1 1 auto; min-width:0; height:31px; box-sizing:border-box;
      padding:0 9px; border:1px solid #cfd6da; border-radius:6px; font-size:12.5px;
      font-family:inherit; color:#1f3a2c; }
  details.fren input[type=text]:focus { outline:none; border-color:#0E5C3F;
      box-shadow:0 0 0 3px rgba(14,92,63,.15); }
  details.fren > form { margin-top:7px; }
  .fund .nm { font-size:13px; color:#42555e; font-weight:600; }
  .fund .bal { font-size:24px; font-weight:700; color:#0E5C3F; margin:4px 0 2px;
               font-variant-numeric:tabular-nums; }
  .fund.neg .bal { color:#c0392b; }
  .fund .sub { font-size:11.5px; color:#7b8b93; }
  .fund .closed { font-size:10.5px; background:#eceff1; color:#5a6b73; padding:1px 7px;
                  border-radius:10px; margin-left:6px; }

  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:8px 9px; border-bottom:1px solid #eceff1; text-align:left;
                           vertical-align:top; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  table.t tr.sum td { background:#f6f8f9; font-weight:700; border-top:2px solid #dfe4e6; }
  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .cr { color:#1b6b39; }
  .dr { color:#8c2020; }
  .muted { color:#7b8b93; }
  .tag { font-size:10.5px; font-weight:700; padding:2px 7px; border-radius:10px;
         background:#e9eef0; color:#42555e; white-space:nowrap; }
  .tag.rev { background:#f7e4d4; color:#8a4b12; }

  /* ── form controls ──
     Every control on this page is the same height, radius and border, and the
     select arrow is drawn rather than left to the browser, which never renders
     one that sits level with a text input. */
  .frm { display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end; }
  /* controls are width:100% now, so flex items need a basis to size against */
  .frm > .fl { flex:1 1 165px; }
  .frm.tight > .fl { flex:0 0 165px; }

  /* Grid for the forms with enough fields to line up in columns; spans below
     keep related fields adjacent instead of wherever the wrap happens to fall. */
  .fgrid { display:grid; grid-template-columns:repeat(6,1fr); gap:14px 15px; align-items:start; }
  @media(max-width:1080px){ .fgrid { grid-template-columns:repeat(4,1fr); } }
  @media(max-width:760px) { .fgrid { grid-template-columns:repeat(2,1fr); } }
  @media(max-width:460px) { .fgrid { grid-template-columns:1fr; } }
  .sp2 { grid-column:span 2; }
  .sp3 { grid-column:span 3; }
  .sp4 { grid-column:span 4; }

  .fl { display:flex; flex-direction:column; gap:6px; min-width:0; }
  .fl label { font-size:10.5px; font-weight:800; text-transform:uppercase; letter-spacing:.45px;
      color:#3f5b4d; }
  .fl label .opt { font-weight:600; text-transform:none; letter-spacing:0; color:#8fa79a;
      font-size:11px; }
  .fl input, .fl select { width:100%; height:38px; box-sizing:border-box; padding:0 11px;
      border:1px solid #cfd6da; border-radius:7px; background:#fff; font-size:13.5px;
      font-family:inherit; color:#1f3a2c; }
  .fl textarea { width:100%; box-sizing:border-box; padding:9px 11px; border:1px solid #cfd6da;
      border-radius:7px; font-size:13.5px; font-family:inherit; color:#1f3a2c; }
  .fl input::placeholder { color:#9fb4a8; }
  .fl input:hover, .fl select:hover { border-color:#a9bfb4; }
  .fl input:focus, .fl select:focus, .fl textarea:focus { outline:none; border-color:#0E5C3F;
      box-shadow:0 0 0 3px rgba(14,92,63,.15); }
  .fl select { appearance:none; -webkit-appearance:none; -moz-appearance:none;
      padding-right:31px; cursor:pointer;
      background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='11' height='7' viewBox='0 0 11 7'%3E%3Cpath d='M1 1l4.5 4.5L10 1' fill='none' stroke='%230E5C3F' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
      background-repeat:no-repeat; background-position:right 11px center; }
  /* the figure the rest of the entry hangs off */
  .amt { font-size:16px; font-weight:700; color:#0E5C3F; font-variant-numeric:tabular-nums; }

  /* Submit sits on its own line, right-aligned, with room for a caveat. */
  .fact { display:flex; justify-content:flex-end; align-items:center; gap:13px; flex-wrap:wrap;
      margin-top:16px; padding-top:15px; border-top:1px solid #eceff1; }
  .fact .note { font-size:12px; color:#7b8b93; margin-right:auto; max-width:60ch; }
  .btn { background:#0E5C3F; color:#fff; border:0; padding:8px 16px; border-radius:6px;
         font-size:13px; font-weight:600; cursor:pointer;
         /* Stated explicitly: style.css now carries a shared .btn that is
            inline-flex, and this block would otherwise inherit it. */
         display:inline-block; text-decoration:none; }
  .btn.alt { background:#fff; color:#42555e; border:1px solid #cfd6da; }
  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.ok { background:#e3f3e9; color:#1b6b39; border:1px solid #bfe0cc; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .warnbar { background:#fdf1e3; border:1px solid #f0d3ae; color:#8a4b12; padding:9px 13px;
             border-radius:7px; font-size:12.5px; margin-bottom:12px; }
  details.more { margin-top:12px; }
  details.more summary { cursor:pointer; font-size:13px; color:#0E5C3F; font-weight:600; }
  details.more > div { margin-top:12px; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="fund"/>
</jsp:include>

<div class="fd-wrap">

  <%-- ── page head: what this screen is, and the one action that starts it ── --%>
  <div class="fd-head">
    <div>
      <h1>Expense funds</h1>
      <p class="sub">
        <% if (showClosed || closedCount == 0) { %>
          <%= funds.size() %> fund<%= funds.size() == 1 ? "" : "s" %><%
             if (closedCount > 0) { %> (<%= closedCount %> closed)<% } %>
        <% } else { %>
          <%= openCount %> open fund<%= openCount == 1 ? "" : "s" %>
        <% } %>
        &middot; Rs <%= Money.fmt(totalHeld) %> held in total
      </p>
    </div>
    <% if (!showCreate) { %>
      <a class="btn" href="<%= ctx %>/fund?<%= sel != null ? "id=" + sel.getFundId() + "&" : "" %>new=1">+ New fund</a>
    <% } %>
  </div>

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <%-- ── create a fund ──
       Top level rather than folded away at the foot of the page: on a fresh
       install nothing at all can happen until a fund exists, so this is the
       first thing the screen should offer, not the last thing you find. --%>
  <% if (showCreate) { %>
    <div class="fd-card fd-newfund">
      <h2><%= funds.isEmpty() ? "Create your first fund" : "Create a fund" %></h2>
      <p class="hint">One fund is enough for most institutes. Add more only if a branch or
         department keeps its own float and needs its own balance.</p>
      <form method="post" action="<%= ctx %>/fund">
        <input type="hidden" name="action" value="create">
        <div class="fgrid">
          <div class="fl sp2"><label for="nfName">Name</label>
            <input type="text" name="name" id="nfName" placeholder="Branch 2 Petty Cash"
                   maxlength="80" required autofocus></div>
          <div class="fl"><label for="nfOpening">Opening balance</label>
            <input type="text" name="opening" id="nfOpening" inputmode="decimal"
                   placeholder="0.00"></div>
          <div class="fl sp3"><label for="nfDesc">Description <span class="opt">(optional)</span></label>
            <input type="text" name="description" id="nfDesc"
                   placeholder="What this fund is for"></div>
        </div>
        <div class="fact">
          <% if (!funds.isEmpty()) { %>
            <a class="btn alt" href="<%= ctx %>/fund<%= sel != null ? "?id=" + sel.getFundId() : "" %>">Cancel</a>
          <% } %>
          <button class="btn" type="submit">Create fund</button>
        </div>
      </form>
    </div>
  <% } %>

  <%-- ── the funds themselves ── --%>
  <div class="funds">
    <% for (FundAccount f : funds) {
         // A closed fund is not somewhere money can go, so it is not in the
         // list of places money can go. Reachable via "Show closed".
         if (!f.isActive() && !showClosed) continue;
         boolean on  = sel != null && sel.getFundId() == f.getFundId();
         boolean neg = f.isOverdrawn();
    %>
      <div class="fund <%= on ? "on" : "" %> <%= neg ? "neg" : "" %>">
        <a class="fmain" href="<%= ctx %>/fund?id=<%= f.getFundId() %>">
          <div class="nm"><%= esc(f.getName()) %>
            <% if (!f.isActive()) { %><span class="closed">CLOSED</span><% } %>
          </div>
          <div class="bal">Rs. <%= Money.fmt(f.getBalance()) %></div>
          <div class="sub">
            in <%= Money.fmt(f.getCredits()) %> &middot;
            out <%= Money.fmt(f.getDebits()) %> &middot;
            <%= f.getTxnCount() %> entries
          </div>
        </a>
        <%-- Rename and close, right where the fund is. Both post the same
             audited actions the manage card below uses, so a tile shortcut
             cannot bypass the trail. --%>
        <div class="facts">
          <details class="fren">
            <summary>Rename</summary>
            <form method="post" action="<%= ctx %>/fund">
              <input type="hidden" name="action" value="rename">
              <input type="hidden" name="fundId" value="<%= f.getFundId() %>"><%= closedFlag %>
              <input type="text" name="name" value="<%= esc(f.getName()) %>"
                     maxlength="80" required aria-label="New name">
              <button class="lnk" type="submit">Save</button>
            </form>
          </details>
          <% if (f.isActive()) { %>
            <form method="post" action="<%= ctx %>/fund"
                  onsubmit="return confirm('Close &quot;<%= esc(f.getName()) %>&quot;? Its statement stays readable, but nothing new can be posted to it.');">
              <input type="hidden" name="action" value="close">
              <input type="hidden" name="fundId" value="<%= f.getFundId() %>"><%= closedFlag %>
              <button class="lnk warn" type="submit">Close</button>
            </form>
          <% } else { %>
            <form method="post" action="<%= ctx %>/fund">
              <input type="hidden" name="action" value="reopen">
              <input type="hidden" name="fundId" value="<%= f.getFundId() %>"><%= closedFlag %>
              <button class="lnk" type="submit">Reopen</button>
            </form>
          <% } %>
        </div>
      </div>
    <% } %>
    <% if (funds.isEmpty()) { %>
      <div class="fd-card" style="flex:1 1 100%">
        <h2>No fund yet</h2>
        <p class="hint">Name one above, then top it up. Every expense you record
           will draw it down.</p>
      </div>
    <% } else if (openCount == 0 && !showClosed) { %>
      <div class="fd-card" style="flex:1 1 100%">
        <h2>Every fund is closed</h2>
        <p class="hint">Nothing can be spent until one is reopened or a new one is created.</p>
      </div>
    <% } %>
  </div>

  <% if (closedCount > 0) { %>
    <p class="fd-closed-toggle">
      <% String keepId = sel == null ? "" : "id=" + sel.getFundId() + "&"; %>
      <% if (showClosed) { %>
        <a href="<%= ctx %>/fund?<%= keepId %>closed=0">Hide closed funds</a>
      <% } else { %>
        <a href="<%= ctx %>/fund?<%= keepId %>closed=1">Show closed
           (<%= closedCount %>) &rarr;</a>
      <% } %>
    </p>
  <% } %>

  <% if (sel != null) { %>

    <% if (sel.isOverdrawn()) { %>
      <div class="warnbar">
        <strong>This fund is overdrawn by Rs. <%= Money.fmt(sel.getBalance().negate()) %>.</strong>
        More has been spent than was credited. That usually means a top-up has not been
        entered yet rather than that anything is wrong — expenses are never blocked on
        a low balance, so the fund is allowed to go negative and show it.
      </div>
    <% } %>

    <%-- ── top up ── --%>
    <div class="fd-card primary">
      <div class="p-head">
        <h2>Add money to <%= esc(sel.getName()) %></h2>
        <span class="lede">Credited the moment you save</span>
      </div>
      <div class="p-body">
      <p class="hint">Everything spent from this fund is deducted automatically as expenses
         are recorded, so this is the only place money goes in.</p>
      <form method="post" action="<%= ctx %>/fund">
        <input type="hidden" name="action" value="topup">
        <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
        <div class="fgrid">
          <div class="fl"><label for="tuAmount">Amount</label>
            <input type="text" name="amount" id="tuAmount" class="amt" required
                   inputmode="decimal" placeholder="0.00"></div>
          <div class="fl"><label for="tuDate">Date</label>
            <input type="date" name="date" id="tuDate" value="<%= today %>"></div>
          <div class="fl"><label for="tuMode">Mode</label>
            <select name="mode" id="tuMode">
              <option>Cash</option><option>UPI</option><option>Bank Transfer</option>
              <option>Cheque</option><option>Card</option><option>Online</option>
            </select></div>
          <div class="fl"><label for="tuRef">Reference <span class="opt">(optional)</span></label>
            <input type="text" name="ref" id="tuRef" placeholder="Cheque / UPI ref"></div>
          <div class="fl sp2"><label for="tuNarr">Narration <span class="opt">(optional)</span></label>
            <input type="text" name="narration" id="tuNarr" placeholder="e.g. Q3 office float"></div>
        </div>
        <div class="fact">
          <span class="note">Posted as a credit on the statement, with your name against it.</span>
          <button class="btn" type="submit">Add money</button>
        </div>
      </form>
      </div>
    </div>

    <%-- ── statement ── --%>
    <div class="fd-card">
      <h2><%= esc(sel.getName()) %> — statement</h2>
      <p class="hint">Every credit and debit in order, with the balance after each one.
         Nothing here is ever edited or deleted: a mistake is corrected by a reversal,
         and both entries stay visible.</p>

      <form class="frm tight" method="get" action="<%= ctx %>/fund" style="margin-bottom:16px">
        <input type="hidden" name="id" value="<%= sel.getFundId() %>">
        <div class="fl"><label for="stFrom">From</label>
          <input type="date" name="from" id="stFrom" value="<%= esc(from) %>"></div>
        <div class="fl"><label for="stTo">To</label>
          <input type="date" name="to" id="stTo" value="<%= esc(to) %>"></div>
        <button class="btn alt" type="submit">Show</button>
      </form>

      <table class="t">
        <thead>
          <tr>
            <th style="width:92px">Date</th>
            <th>Particulars</th>
            <th style="width:105px">Type</th>
            <th style="width:120px" class="num">Credit</th>
            <th style="width:120px" class="num">Debit</th>
            <th style="width:130px" class="num">Balance</th>
            <th style="width:80px"></th>
          </tr>
        </thead>
        <tbody>
          <tr class="sum">
            <td colspan="5">Opening balance as on <%= esc(from) %></td>
            <td class="num">Rs. <%= Money.fmt(opening) %></td>
            <td></td>
          </tr>

          <% for (FundTransaction t : rows) {
               boolean isRev = FundTransaction.SRC_REVERSAL.equals(t.getSourceType());
          %>
            <tr>
              <td><%= esc(t.getTxnDate()) %></td>
              <td>
                <%= esc(t.getNarration() == null ? t.getSourceLabel() : t.getNarration()) %>
                <% if (t.getTxnRef() != null) { %>
                  <div class="muted" style="font-size:11.5px">
                    Ref <%= esc(t.getTxnRef()) %>
                    <% if (t.getPaymentMode() != null) { %> &middot; <%= esc(t.getPaymentMode()) %><% } %>
                  </div>
                <% } %>
                <% if (t.getCreatedBy() != null) { %>
                  <div class="muted" style="font-size:11px">by <%= esc(t.getCreatedBy()) %></div>
                <% } %>
              </td>
              <td><span class="tag <%= isRev ? "rev" : "" %>"><%= esc(t.getSourceLabel()) %></span></td>
              <td class="num cr"><%= t.isCredit() ? Money.fmt(t.getAmount()) : "" %></td>
              <td class="num dr"><%= t.isDebit()  ? Money.fmt(t.getAmount()) : "" %></td>
              <td class="num"><%= Money.fmt(t.getRunningBalance()) %></td>
              <td>
                <%-- Only a manual entry can be reversed here. An expense's debit is
                     reversed by voiding the expense itself, so the two can never
                     disagree. --%>
                <% if (!isRev && (FundTransaction.SRC_TOPUP.equals(t.getSourceType())
                               || FundTransaction.SRC_ADJUSTMENT.equals(t.getSourceType()))) { %>
                  <form method="post" action="<%= ctx %>/fund"
                        onsubmit="return confirm('Reverse this entry? Both it and the reversal stay on the statement.');">
                    <input type="hidden" name="action" value="reverse">
                    <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
                    <input type="hidden" name="txnId" value="<%= t.getTxnId() %>">
                    <input type="hidden" name="reason" value="Reversed from statement">
                    <button class="btn alt" style="padding:4px 9px;font-size:11.5px" type="submit">Reverse</button>
                  </form>
                <% } %>
              </td>
            </tr>
          <% } %>

          <% if (rows.isEmpty()) { %>
            <tr><td colspan="7" class="muted" style="padding:18px 9px">
              Nothing was posted between <%= esc(from) %> and <%= esc(to) %>.
            </td></tr>
          <% } %>

          <tr class="sum">
            <td colspan="3">Period total (<%= rows.size() %> entries)</td>
            <td class="num cr"><%= Money.fmt(periodCr) %></td>
            <td class="num dr"><%= Money.fmt(periodDr) %></td>
            <td class="num">Rs. <%= Money.fmt(closing) %></td>
            <td></td>
          </tr>
        </tbody>
      </table>

      <details class="more">
        <summary>Post an adjustment</summary>
        <div>
          <p class="hint">For a counting error in the cash box or an opening figure keyed in
             wrong — not for an expense, which should be recorded in the Expenses module so it
             carries a voucher and a vendor. Adjustments stay labelled as such on the statement
             forever, so they can always be told apart from real spending.</p>
          <form method="post" action="<%= ctx %>/fund">
            <input type="hidden" name="action" value="adjust">
            <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
            <div class="fgrid">
              <div class="fl sp2"><label for="adjDir">Direction</label>
                <select name="direction" id="adjDir">
                  <option value="CREDIT">Credit (increase)</option>
                  <option value="DEBIT">Debit (decrease)</option>
                </select></div>
              <div class="fl"><label for="adjAmount">Amount</label>
                <input type="text" name="amount" id="adjAmount" class="amt" required
                       inputmode="decimal" placeholder="0.00"></div>
              <div class="fl"><label for="adjDate">Date</label>
                <input type="date" name="date" id="adjDate" value="<%= today %>"></div>
              <div class="fl sp2"><label for="adjWhy">Reason</label>
                <input type="text" name="narration" id="adjWhy" required
                       placeholder="Why this adjustment is needed"></div>
            </div>
            <div class="fact">
              <span class="note">Stays labelled as an adjustment on the statement forever.</span>
              <button class="btn" type="submit">Post adjustment</button>
            </div>
          </form>
        </div>
      </details>
    </div>
  <% } %>

  <%-- ── close / delete the selected fund ── --%>
  <% if (sel != null) {
       // Deletable only when nothing has ever been recorded against it. Both
       // counts matter: a VOID expense leaves no live ledger row but its
       // voucher still points here.
       boolean empty = sel.getTxnCount() == 0 && selExpenses == 0;
  %>
  <div class="fd-card">
    <details class="more">
      <summary>Rename<%= empty ? " or delete" : ", close or delete" %> <%= esc(sel.getName()) %></summary>
      <div>

        <%-- Rename is offered whatever the fund's history: nothing joins on the
             name, so correcting it moves no money. It is still audited. --%>
        <p class="hint">Renaming is safe at any time — every entry, voucher and report
           joins on the fund itself, not its name, so no figure changes. A mis-named fund
           should be renamed rather than deleted and recreated: recreating would strand
           its statement.</p>
        <form method="post" action="<%= ctx %>/fund">
          <input type="hidden" name="action" value="rename">
          <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
          <div class="fgrid">
            <div class="fl sp3"><label for="rnName">New name</label>
              <input type="text" name="name" id="rnName" maxlength="80" required
                     value="<%= esc(sel.getName()) %>"></div>
            <div class="fl sp3"><label for="rnWhy">Reason <span class="opt">(recorded on the history)</span></label>
              <input type="text" name="reason" id="rnWhy" maxlength="255"
                     placeholder="e.g. amount typed into the name box"></div>
          </div>
          <div class="fact">
            <button class="btn" type="submit">Rename</button>
          </div>
        </form>

        <hr style="border:0;border-top:1px solid #eceff1;margin:16px 0">

        <% if (empty) { %>
          <p class="hint">Nothing has ever been posted to this fund, so deleting it loses
             nothing. The deletion itself is recorded on the fund history below, along with
             who did it — that record outlives the fund.</p>
          <form method="post" action="<%= ctx %>/fund"
                onsubmit="return confirm('Delete the fund &quot;<%= esc(sel.getName()) %>&quot;? This cannot be undone.');">
            <input type="hidden" name="action" value="delete">
            <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
            <div class="fgrid">
              <div class="fl sp4"><label for="delWhy">Reason <span class="opt">(recorded on the history)</span></label>
                <input type="text" name="reason" id="delWhy" maxlength="255"
                       placeholder="e.g. created by mistake"></div>
            </div>
            <div class="fact">
              <button class="btn" style="background:#8c2020;box-shadow:0 2px 10px rgba(140,32,32,.28)"
                      type="submit">Delete this fund</button>
            </div>
          </form>
        <% } else { %>
          <p class="hint">This fund has
             <strong><%= sel.getTxnCount() %></strong> ledger
             <%= sel.getTxnCount() == 1 ? "entry" : "entries" %> and
             <strong><%= selExpenses %></strong> expense
             <%= selExpenses == 1 ? "voucher" : "vouchers" %> behind it, so it cannot be
             deleted — that would take the whole statement with it. Closing is what getting
             rid of a fund in use means: the statement stays readable forever, and nothing
             new can be topped up or spent from it. It can be reopened later.</p>
        <% } %>

        <% if (!empty && sel.isActive()) { %>
          <form method="post" action="<%= ctx %>/fund"
                onsubmit="return confirm('Close &quot;<%= esc(sel.getName()) %>&quot;? Nothing new can be posted to it until it is reopened.');">
            <input type="hidden" name="action" value="close">
            <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
            <div class="fgrid">
              <div class="fl sp4"><label for="clWhy">Reason <span class="opt">(recorded on the history)</span></label>
                <input type="text" name="reason" id="clWhy" maxlength="255"
                       placeholder="e.g. branch float merged into the main fund"></div>
            </div>
            <div class="fact">
              <button class="btn" type="submit">Close this fund</button>
            </div>
          </form>
        <% } else if (!sel.isActive()) { %>
          <form method="post" action="<%= ctx %>/fund">
            <input type="hidden" name="action" value="reopen">
            <input type="hidden" name="fundId" value="<%= sel.getFundId() %>">
            <div class="fgrid">
              <div class="fl sp4"><label for="roWhy">Reason <span class="opt">(recorded on the history)</span></label>
                <input type="text" name="reason" id="roWhy" maxlength="255"
                       placeholder="e.g. branch reopened"></div>
            </div>
            <div class="fact">
              <button class="btn alt" type="submit">Reopen this fund</button>
            </div>
          </form>
        <% } %>

      </div>
    </details>
  </div>
  <% } %>

  <%-- ── fund history (audit trail) ── --%>
  <div class="fd-card">
    <details class="more">
      <summary>Fund history <span class="muted">(<%= auditTrail.size() %>)</span></summary>
      <div>
        <p class="hint">Every fund created, closed, reopened or deleted, and by whom.
           Deleted funds stay on this list — the name and balance shown are what they held
           at the moment they went, since there is no fund left to look them up on.</p>
        <table class="t">
          <thead>
            <tr>
              <th style="width:145px">When</th>
              <th style="width:90px">Action</th>
              <th>Fund</th>
              <th class="num" style="width:120px">Balance then</th>
              <th>Reason</th>
              <th style="width:140px">By</th>
            </tr>
          </thead>
          <tbody>
            <% for (FundAudit a : auditTrail) { %>
              <tr>
                <td class="muted"><%= esc(a.getActedAt()) %></td>
                <td><span class="tag <%= a.isDelete() ? "rev" : "" %>"><%= esc(a.getActionLabel()) %></span></td>
                <td><%= esc(a.getFundName()) %>
                  <% if (a.isRename() && a.getOldName() != null) { %>
                    <div class="muted" style="font-size:11px">was
                      &ldquo;<%= esc(a.getOldName()) %>&rdquo;</div>
                  <% } %>
                  <div class="muted" style="font-size:11px"><%= a.getTxnCount() %> entries at the time</div>
                </td>
                <td class="num">Rs. <%= Money.fmt(a.getBalance()) %></td>
                <td class="muted"><%= a.getReason() == null ? "&mdash;" : esc(a.getReason()) %></td>
                <td class="muted"><%= a.getActedBy() == null ? "&mdash;" : esc(a.getActedBy()) %></td>
              </tr>
            <% } %>
            <% if (auditTrail.isEmpty()) { %>
              <tr><td colspan="6" class="muted" style="padding:18px 9px">
                Nothing recorded yet.
              </td></tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </details>
  </div>

</div>
</body>
</html>

<%!
    /** Escapes text for HTML. Vendor names and narrations are free text. */
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
%>
