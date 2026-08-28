<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.dao.WorkOrderDAO" %>
<%@ page import="com.tution.model.User, com.tution.model.Vendor, com.tution.model.WorkOrder" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<WorkOrder> rows =
        (List<WorkOrder>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<WorkOrder>();

    @SuppressWarnings("unchecked") List<Vendor> vendors =
        (List<Vendor>) request.getAttribute("vendors");
    if (vendors == null) vendors = new ArrayList<Vendor>();

    WorkOrder ord = (WorkOrder) request.getAttribute("order");
    WorkOrderDAO.Totals tot = (WorkOrderDAO.Totals) request.getAttribute("totals");

    Integer vidObj = (Integer) request.getAttribute("vendorId");
    int fVendor = vidObj == null ? 0 : vidObj.intValue();
    String fStatus = str(request.getAttribute("status"));
    String fPay    = str(request.getAttribute("pay"));
    String fQ      = str(request.getAttribute("q"));

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
<title>Work Orders – Havellsson NEET Samrat</title>
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

  table.t { width:100%; border-collapse:collapse; font-size:13px; }
  table.t th, table.t td { padding:8px 9px; border-bottom:1px solid #eceff1; text-align:left; }
  table.t th { background:#f6f8f9; font-size:12px; color:#42555e; font-weight:600; }
  table.t tr.sum td { background:#f6f8f9; font-weight:700; border-top:2px solid #dfe4e6; }
  table.t tbody tr:hover { background:#fafcfc; }
  .num { text-align:right; font-variant-numeric:tabular-nums; white-space:nowrap; }
  .muted { color:#7b8b93; }
  .mono { font-family:ui-monospace,Consolas,monospace; font-weight:700; letter-spacing:.5px; }

  .st { font-size:11px; font-weight:700; padding:2px 9px; border-radius:20px; white-space:nowrap; }
  .s-FULLY_PAID     { background:#d8efdf; color:#1b6b39; }
  .s-PARTIALLY_PAID { background:#fdf1e3; color:#8a4b12; }
  .s-UNPAID         { background:#f7d4d4; color:#8c2020; }
  .s-DRAFT       { background:#eceff1; color:#5a6b73; }
  .s-ISSUED      { background:#e4eef7; color:#1d4e79; }
  .s-IN_PROGRESS { background:#e4eef7; color:#1d4e79; }
  .s-COMPLETED   { background:#d8efdf; color:#1b6b39; }
  .s-CANCELLED   { background:#eceff1; color:#8c2020; }

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
  .chips a { font-size:12px; text-decoration:none; padding:4px 11px; border-radius:20px;
             border:1px solid #cfd6da; color:#42555e; margin-right:6px; display:inline-block; }
  .chips a.on { background:#0E5C3F; color:#fff; border-color:#0E5C3F; }

  .bar { height:7px; background:#eceff1; border-radius:4px; overflow:hidden; margin-top:4px; }
  .bar i { display:block; height:100%; background:#0E5C3F; }
  .ord { display:flex; gap:20px; flex-wrap:wrap; align-items:flex-start;
         background:#f6f8f9; border:1px solid #e3e6e8; border-radius:9px; padding:13px 15px;
         margin-bottom:13px; }
  .ord .who { flex:1 1 280px; }
  .ord .pos { text-align:right; min-width:190px; }
  .ord .pos .bal { font-size:22px; font-weight:700; font-variant-numeric:tabular-nums; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="workorders"/>
</jsp:include>

<div class="ef-wrap">

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <% if (vendors.isEmpty()) { %>
    <div class="ef-card">
      <h2>No vendors yet</h2>
      <p class="hint" style="margin:0">A work order is a commitment to a particular vendor, so
         add the vendor first. <a href="<%= ctx %>/vendors">Go to vendors &rarr;</a></p>
    </div>
  <% } else { %>

  <% if (tot != null) { %>
    <div class="tiles">
      <div class="tile"><div class="lb">Work orders</div>
        <div class="vl"><%= tot.orders %></div>
        <div class="sb">excluding cancelled</div></div>
      <div class="tile"><div class="lb">Ordered</div>
        <div class="vl">Rs. <%= Money.fmt(tot.ordered) %></div>
        <div class="sb">committed to vendors</div></div>
      <div class="tile"><div class="lb">Paid</div>
        <div class="vl">Rs. <%= Money.fmt(tot.paid) %></div>
        <div class="sb"><%= pct(tot.paid, tot.ordered) %> of ordered</div></div>
      <div class="tile due"><div class="lb">Remaining</div>
        <div class="vl">Rs. <%= Money.fmt(tot.remaining) %></div>
        <div class="sb">still to pay</div></div>
    </div>
  <% } %>

  <%-- ────────── one order ────────── --%>
  <% if (ord != null) {
       int done = ord.getOrderValue().signum() == 0 ? 0
                : (int) Math.min(100, Math.round(ord.getPaid().doubleValue() * 100.0
                                               / ord.getOrderValue().doubleValue()));
  %>
    <div class="ef-card">
      <div class="ord">
        <div class="who">
          <div style="font-size:17px;font-weight:700">
            <span class="mono"><%= esc(ord.getWoNo()) %></span> &middot; <%= esc(ord.getTitle()) %>
            <span class="st s-<%= ord.getStatus() %>" style="margin-left:6px"
              ><%= esc(ord.getStatusLabel()) %></span>
          </div>
          <div class="muted" style="font-size:12.5px;margin-top:3px">
            <a href="<%= ctx %>/vendors?id=<%= ord.getVendorId() %>"><%= esc(ord.getVendorName()) %></a>
            &middot; ordered <%= esc(ord.getOrderDate()) %>
            <% if (ord.getExpectedDate() != null) { %>
              &middot; expected <%= esc(ord.getExpectedDate()) %><% } %>
            <% if (ord.getCreatedBy() != null) { %>
              &middot; raised by <%= esc(ord.getCreatedBy()) %><% } %>
          </div>
          <% if (ord.getDescription() != null) { %>
            <div style="font-size:12.5px;margin-top:6px"><%= esc(ord.getDescription()) %></div>
          <% } %>
          <% if (ord.getRemarks() != null) { %>
            <div class="muted" style="font-size:12px;margin-top:4px">
              Remarks: <%= esc(ord.getRemarks()) %></div>
          <% } %>
        </div>
        <div class="pos">
          <div class="muted" style="font-size:11.5px">
            Order <%= Money.rs(ord.getOrderValue()) %> &middot; paid <%= Money.fmt(ord.getPaid()) %>
            <% if (ord.getExpenseCount() > 0) { %> (<%= ord.getExpenseCount() %> expense(s))<% } %>
          </div>
          <div class="bal" style="color:<%= ord.getRemaining().signum() > 0 ? "#c0392b" : "#1b6b39" %>">
            <%= ord.getRemaining().signum() > 0
                  ? "Rs. " + Money.fmt(ord.getRemaining()) + " left"
                  : "Fully paid" %>
          </div>
          <div class="bar"><i style="width:<%= done %>%"></i></div>
          <div class="muted" style="font-size:11px;margin-top:3px"><%= done %>% paid</div>
          <% if (ord.isOverPaid()) { %>
            <div style="font-size:11.5px;color:#8a4b12;margin-top:3px">
              More has been paid than was ordered</div>
          <% } %>
        </div>
      </div>

      <% if (ord.isDraft()) { %>
        <div class="msg warn">
          This is a <strong>draft</strong>. Nothing can be paid against it until it is issued —
          that is the approval step, and it records who authorised the spend.
        </div>
      <% } else if (ord.isCancelled()) { %>
        <div class="msg warn">
          This order is cancelled. It does not count towards the vendor's ordered value and
          nothing can be paid against it.
        </div>
      <% } %>

      <div class="frm">
        <a class="btn alt" target="_blank"
           href="<%= ctx %>/work-orders?pdf=<%= ord.getWorkOrderId() %>">Work order PDF</a>
        <% if (!ord.isCancelled()) { %>
          <% for (String[] to : nextStates(ord.getStatus())) { %>
            <form method="post" action="<%= ctx %>/work-orders" style="display:inline"
              <%= "CANCELLED".equals(to[0])
                    ? " onsubmit=\"return confirm('Cancel " + esc(ord.getWoNo())
                      + "? Nothing can be paid against it afterwards.')\"" : "" %>>
              <input type="hidden" name="action" value="status">
              <input type="hidden" name="workOrderId" value="<%= ord.getWorkOrderId() %>">
              <input type="hidden" name="to" value="<%= to[0] %>">
              <button class="btn<%= "ISSUED".equals(to[0]) ? "" : " alt" %>" type="submit"
                ><%= to[1] %></button>
            </form>
          <% } %>
        <% } %>
        <span style="flex:1 1 auto"></span>
        <a class="btn alt" href="<%= ctx %>/work-orders">Close</a>
      </div>
    </div>
  <% } %>

  <%-- ────────── raise / edit ────────── --%>
  <div class="ef-card">
    <details <%= ord != null && !ord.isCancelled() ? "open" : "" %>>
      <summary style="cursor:pointer;font-size:15px;font-weight:700;color:#0E5C3F">
        <%= ord == null ? "Raise a work order" : "Edit " + esc(ord.getWoNo()) %></summary>
      <p class="hint" style="margin-top:9px">
        <% if (ord == null) { %>
          A new order starts as a draft. Issue it when it is agreed — only then can expenses
          be booked against it.
        <% } else { %>
          The vendor and the order number cannot be changed. Raise a new order if the work
          moved to somebody else.
        <% } %>
      </p>

      <form method="post" action="<%= ctx %>/work-orders">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="workOrderId" value="<%= ord == null ? 0 : ord.getWorkOrderId() %>">
        <div class="frm">
          <div class="fl"><label>Vendor</label>
            <% if (ord == null) { %>
              <select name="vendorId" required style="min-width:220px">
                <option value="">Choose a vendor…</option>
                <% for (Vendor v : vendors) { if (!v.isActive()) continue; %>
                  <option value="<%= v.getVendorId() %>"
                    <%= v.getVendorId()==fVendor ? "selected" : "" %>><%= esc(v.getName()) %></option>
                <% } %>
              </select>
            <% } else { %>
              <input type="text" value="<%= esc(ord.getVendorName()) %>" disabled style="min-width:220px">
              <input type="hidden" name="vendorId" value="<%= ord.getVendorId() %>">
            <% } %>
          </div>
          <div class="fl" style="flex:1 1 300px"><label>Title — what the money is for</label>
            <input type="text" name="title" required placeholder="OMR sheet printing — HAMSE Dec"
                   value="<%= ord == null ? "" : esc(ord.getTitle()) %>"></div>
          <div class="fl"><label>Order value</label>
            <input type="text" name="orderValue" style="width:140px" required
                   value="<%= ord == null ? "" : Money.fmt(ord.getOrderValue()) %>"></div>
          <div class="fl"><label>Order date</label>
            <input type="date" name="orderDate"
                   value="<%= ord == null ? today : esc(ord.getOrderDate()) %>"></div>
          <div class="fl"><label>Expected by</label>
            <input type="date" name="expectedDate"
                   value="<%= ord == null ? "" : esc(nz(ord.getExpectedDate(), "")) %>"></div>
        </div>
        <div class="frm" style="margin-top:10px">
          <div class="fl" style="flex:1 1 340px"><label>Details</label>
            <input type="text" name="description"
                   value="<%= ord == null ? "" : esc(nz(ord.getDescription(), "")) %>"></div>
          <div class="fl" style="flex:1 1 220px"><label>Remarks</label>
            <input type="text" name="remarks"
                   value="<%= ord == null ? "" : esc(nz(ord.getRemarks(), "")) %>"></div>
          <button class="btn" type="submit"
            ><%= ord == null ? "Save as draft" : "Save changes" %></button>
        </div>
      </form>
    </details>
  </div>

  <%-- ────────── the list ────────── --%>
  <div class="ef-card">
    <h2>Work orders</h2>
    <p class="hint">Paid comes from the expenses booked against each order — nothing here is
       a stored status, so it always matches the expense register.</p>

    <form class="frm" method="get" action="<%= ctx %>/work-orders" style="margin-bottom:10px">
      <div class="fl"><label>Vendor</label>
        <select name="vendor" onchange="this.form.submit()">
          <option value="0">All vendors</option>
          <% for (Vendor v : vendors) { %>
            <option value="<%= v.getVendorId() %>" <%= v.getVendorId()==fVendor ? "selected" : "" %>
              ><%= esc(v.getName()) %><%= v.isActive() ? "" : " (retired)" %></option>
          <% } %>
        </select></div>
      <div class="fl"><label>Status</label>
        <select name="status" onchange="this.form.submit()">
          <option value="">Any status</option>
          <% for (String[] s : ALL_STATES) { %>
            <option value="<%= s[0] %>" <%= s[0].equals(fStatus) ? "selected" : "" %>><%= s[1] %></option>
          <% } %>
        </select></div>
      <div class="fl"><label>Search</label>
        <input type="text" name="q" value="<%= esc(fQ) %>" placeholder="Number, title or vendor"></div>
      <input type="hidden" name="pay" value="<%= esc(fPay) %>">
      <button class="btn alt" type="submit">Apply</button>
    </form>

    <div class="chips" style="margin-bottom:12px">
      <a class="<%= fPay.isEmpty() ? "on" : "" %>"
         href="<%= ctx %>/work-orders?vendor=<%= fVendor %>&amp;status=<%= url(fStatus) %>&amp;q=<%= url(fQ) %>">All</a>
      <% for (String[] p : new String[][]{ {"UNPAID","Unpaid"}, {"PARTIALLY_PAID","Part paid"},
                                           {"FULLY_PAID","Fully paid"} }) { %>
        <a class="<%= p[0].equals(fPay) ? "on" : "" %>"
           href="<%= ctx %>/work-orders?vendor=<%= fVendor %>&amp;status=<%= url(fStatus) %>&amp;q=<%= url(fQ) %>&amp;pay=<%= p[0] %>"><%= p[1] %></a>
      <% } %>
    </div>

    <% if (rows.isEmpty()) { %>
      <p class="hint" style="margin:0">No work orders match.</p>
    <% } else {
         // The tiles above exclude cancelled orders; the table shows whatever the
         // filter matched. Summing a cancelled row into this footer without saying
         // so would put two different "ordered" figures on one screen, so the
         // cancelled ones are counted out here too and the count says as much.
         java.math.BigDecimal sv = Money.ZERO, sp = Money.ZERO, sr = Money.ZERO;
         int nCancelled = 0;
         for (WorkOrder w : rows) {
             if (w.isCancelled()) { nCancelled++; continue; }
             sv = sv.add(w.getOrderValue()); sp = sp.add(w.getPaid()); sr = sr.add(w.getRemaining());
         }
    %>
      <table class="t">
        <thead><tr>
          <th style="width:120px">Order</th><th>Work</th><th style="width:170px">Vendor</th>
          <th style="width:95px">Date</th><th style="width:105px">Status</th>
          <th style="width:125px" class="num">Value</th>
          <th style="width:115px" class="num">Paid</th>
          <th style="width:125px" class="num">Remaining</th>
          <th style="width:95px">Payment</th>
        </tr></thead>
        <tbody>
        <% for (WorkOrder w : rows) { %>
          <tr<%= w.isCancelled() ? " style=\"opacity:.55\"" : "" %>>
            <td><a class="mono" style="font-size:12px"
                   href="<%= ctx %>/work-orders?id=<%= w.getWorkOrderId() %>"
                ><%= esc(w.getWoNo()) %></a></td>
            <td><%= esc(w.getTitle()) %></td>
            <td class="muted"><%= esc(w.getVendorName()) %></td>
            <td><%= esc(w.getOrderDate()) %></td>
            <td><span class="st s-<%= w.getStatus() %>"><%= esc(w.getStatusLabel()) %></span></td>
            <td class="num"><%= Money.fmt(w.getOrderValue()) %></td>
            <td class="num"><%= Money.fmt(w.getPaid()) %></td>
            <td class="num"><strong><%= Money.fmt(w.getRemaining()) %></strong></td>
            <td><span class="st s-<%= w.getPaymentStatus() %>"><%= esc(w.getPaymentLabel()) %></span></td>
          </tr>
        <% } %>
        </tbody>
        <tfoot>
          <tr class="sum">
            <td colspan="5"><%= rows.size() - nCancelled %> live order(s)<%=
                nCancelled == 0 ? "" : " — " + nCancelled + " cancelled row(s) listed but not counted" %></td>
            <td class="num"><%= Money.fmt(sv) %></td>
            <td class="num"><%= Money.fmt(sp) %></td>
            <td class="num"><%= Money.fmt(sr) %></td>
            <td></td>
          </tr>
        </tfoot>
      </table>
    <% } %>
  </div>

  <% } %>
</div>
</body>
</html>

<%!
    /** Every lifecycle value, for the filter. */
    private static final String[][] ALL_STATES = {
        { "DRAFT", "Draft" }, { "ISSUED", "Issued" }, { "IN_PROGRESS", "In progress" },
        { "COMPLETED", "Completed" }, { "CANCELLED", "Cancelled" }
    };

    /**
     * The buttons offered on an order, which must agree with
     * WorkOrderService.allowed() - a button that leads to a refusal is worse
     * than no button.
     */
    private String[][] nextStates(String from) {
        if ("DRAFT".equals(from))
            return new String[][] { { "ISSUED", "Issue this order" }, { "CANCELLED", "Cancel" } };
        if ("ISSUED".equals(from))
            return new String[][] { { "IN_PROGRESS", "Mark in progress" },
                                    { "COMPLETED", "Mark completed" }, { "CANCELLED", "Cancel" } };
        if ("IN_PROGRESS".equals(from))
            return new String[][] { { "COMPLETED", "Mark completed" }, { "CANCELLED", "Cancel" } };
        if ("COMPLETED".equals(from))
            return new String[][] { { "IN_PROGRESS", "Reopen" }, { "CANCELLED", "Cancel" } };
        return new String[0][];
    }

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

    private String pct(java.math.BigDecimal part, java.math.BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return "—";
        return Math.round(part.doubleValue() * 100.0 / whole.doubleValue()) + "%";
    }
%>
