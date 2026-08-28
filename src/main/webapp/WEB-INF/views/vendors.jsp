<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.*" %>
<%@ page import="com.tution.model.User, com.tution.model.Vendor, com.tution.model.WorkOrder" %>
<%@ page import="com.tution.util.Money" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();

    @SuppressWarnings("unchecked") List<Vendor> rows = (List<Vendor>) request.getAttribute("rows");
    if (rows == null) rows = new ArrayList<Vendor>();

    @SuppressWarnings("unchecked") List<String> cats =
        (List<String>) request.getAttribute("categories");
    if (cats == null) cats = new ArrayList<String>();

    @SuppressWarnings("unchecked") List<WorkOrder> orders =
        (List<WorkOrder>) request.getAttribute("orders");
    if (orders == null) orders = new ArrayList<WorkOrder>();

    Vendor v = (Vendor) request.getAttribute("vendor");
    String fq = str(request.getAttribute("q"));
    Boolean showAllObj = (Boolean) request.getAttribute("showAll");
    boolean showAll = showAllObj != null && showAllObj.booleanValue();

    BigDecimal sumOrdered = Money.ZERO, sumPaid = Money.ZERO, sumDue = Money.ZERO;
    for (Vendor x : rows) {
        sumOrdered = sumOrdered.add(x.getOrdered());
        sumPaid    = sumPaid.add(x.getPaid());
        sumDue     = sumDue.add(x.getOutstanding());
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
<title>Vendors – Havellsson NEET Samrat</title>
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
  .s-CANCELLED   { background:#eceff1; color:#8c2020; text-decoration:line-through; }
  .off { background:#eceff1; color:#5a6b73; }

  .frm { display:flex; gap:10px; flex-wrap:wrap; align-items:flex-end; }
  .fl { display:flex; flex-direction:column; gap:4px; }
  .fl label { font-size:11.5px; color:#5a6b73; font-weight:600; }
  .fl input, .fl select, .fl textarea {
      padding:7px 9px; border:1px solid #cfd6da; border-radius:5px; font-size:13px; font-family:inherit; }
  .btn { background:#0E5C3F; color:#fff; border:0; padding:8px 16px; border-radius:6px;
         font-size:13px; font-weight:600; cursor:pointer; text-decoration:none; display:inline-block; }
  .btn.alt { background:#fff; color:#42555e; border:1px solid #cfd6da; }
  .btn.sm  { padding:4px 10px; font-size:11.5px; }
  .msg { padding:10px 13px; border-radius:7px; font-size:13px; margin-bottom:12px; }
  .msg.ok { background:#e3f3e9; color:#1b6b39; border:1px solid #bfe0cc; }
  .msg.err{ background:#fbe6e6; color:#8c2020; border:1px solid #eec4c4; }
  .msg.warn{ background:#fdf1e3; color:#8a4b12; border:1px solid #f0d3ae; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp">
  <jsp:param name="active" value="vendors"/>
</jsp:include>

<div class="ef-wrap">

  <% if (flash != null) { %><div class="msg ok"><%= esc(flash) %></div><% } %>
  <% if (flashError != null) { %><div class="msg err"><%= esc(flashError) %></div><% } %>
  <% if (error != null) { %><div class="msg err"><%= esc(error) %></div><% } %>

  <div class="tiles">
    <div class="tile"><div class="lb">Vendors</div>
      <div class="vl"><%= rows.size() %></div>
      <div class="sb"><%= showAll ? "including retired" : "active only" %></div></div>
    <div class="tile"><div class="lb">Ordered</div>
      <div class="vl">Rs. <%= Money.fmt(sumOrdered) %></div>
      <div class="sb">live work orders</div></div>
    <div class="tile"><div class="lb">Paid</div>
      <div class="vl">Rs. <%= Money.fmt(sumPaid) %></div>
      <div class="sb">expenses booked</div></div>
    <div class="tile due"><div class="lb">Outstanding</div>
      <div class="vl">Rs. <%= Money.fmt(sumDue) %></div>
      <div class="sb">still owed to vendors</div></div>
  </div>

  <%-- ────────── one vendor ────────── --%>
  <% if (v != null) { %>
    <div class="ef-card">
      <h2><%= esc(v.getName()) %>
        <% if (!v.isActive()) { %><span class="st off">Retired</span><% } %></h2>
      <p class="hint">
        <%= esc(nz(v.getCategory(), "No category")) %>
        <% if (v.getContactPerson() != null) { %> &middot; <%= esc(v.getContactPerson()) %><% } %>
        <% if (v.getMobile() != null) { %> &middot; <%= esc(v.getMobile()) %><% } %>
        <% if (v.getGstin() != null) { %> &middot; GSTIN <%= esc(v.getGstin()) %><% } %>
        &middot; <%= v.getOrderCount() %> work order(s),
        <strong>Rs. <%= Money.fmt(v.getOutstanding()) %></strong> outstanding
      </p>

      <% if (v.isOverPaid()) { %>
        <div class="msg warn">More has been paid to this vendor than has ever been ordered.
          Either an expense is booked against the wrong vendor, or a work order is missing.</div>
      <% } %>

      <% if (orders.isEmpty()) { %>
        <p class="hint" style="margin:0">No work orders yet.
          <a href="<%= ctx %>/work-orders?vendor=<%= v.getVendorId() %>">Raise one &rarr;</a></p>
      <% } else { %>
        <table class="t">
          <thead><tr>
            <th style="width:120px">Order</th><th>Work</th>
            <th style="width:95px">Date</th><th style="width:100px">Status</th>
            <th style="width:120px" class="num">Value</th>
            <th style="width:110px" class="num">Paid</th>
            <th style="width:120px" class="num">Remaining</th>
            <th style="width:95px">Payment</th>
          </tr></thead>
          <tbody>
          <% for (WorkOrder w : orders) { %>
            <tr>
              <td><a class="mono" style="font-size:12px"
                     href="<%= ctx %>/work-orders?id=<%= w.getWorkOrderId() %>"
                  ><%= esc(w.getWoNo()) %></a></td>
              <td><%= esc(w.getTitle()) %></td>
              <td><%= esc(w.getOrderDate()) %></td>
              <td><span class="st s-<%= w.getStatus() %>"><%= esc(w.getStatusLabel()) %></span></td>
              <td class="num"><%= Money.fmt(w.getOrderValue()) %></td>
              <td class="num"><%= Money.fmt(w.getPaid()) %></td>
              <td class="num"><strong><%= Money.fmt(w.getRemaining()) %></strong></td>
              <td><span class="st s-<%= w.getPaymentStatus() %>"><%= esc(w.getPaymentLabel()) %></span></td>
            </tr>
          <% } %>
          </tbody>
        </table>
        <p class="hint" style="margin:12px 0 0">
          <a class="btn alt sm" href="<%= ctx %>/work-orders?vendor=<%= v.getVendorId() %>"
            >All work orders for this vendor</a></p>
      <% } %>
    </div>
  <% } %>

  <%-- ────────── add / edit ────────── --%>
  <div class="ef-card">
    <details <%= v != null ? "open" : "" %>>
      <summary style="cursor:pointer;font-size:15px;font-weight:700;color:#0E5C3F">
        <%= v == null ? "Add a vendor" : "Edit " + esc(v.getName()) %></summary>
      <p class="hint" style="margin-top:9px">Bank and tax details are optional, but a vendor
         you actually transfer money to is worth keeping them for.</p>

      <form method="post" action="<%= ctx %>/vendors">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="vendorId" value="<%= v == null ? 0 : v.getVendorId() %>">
        <div class="frm">
          <div class="fl" style="flex:1 1 260px"><label>Name (required)</label>
            <input type="text" name="name" required
                   value="<%= v == null ? "" : esc(v.getName()) %>"></div>
          <div class="fl"><label>Category</label>
            <input type="text" name="category" list="cats" style="width:150px"
                   value="<%= v == null ? "" : esc(nz(v.getCategory(), "")) %>">
            <datalist id="cats">
              <% for (String c : cats) { %><option value="<%= esc(c) %>"><% } %>
            </datalist></div>
          <div class="fl"><label>Contact person</label>
            <input type="text" name="contactPerson" style="width:170px"
                   value="<%= v == null ? "" : esc(nz(v.getContactPerson(), "")) %>"></div>
          <div class="fl"><label>Mobile</label>
            <input type="text" name="mobile" style="width:130px" maxlength="10"
                   value="<%= v == null ? "" : esc(nz(v.getMobile(), "")) %>"></div>
          <div class="fl"><label>Email</label>
            <input type="text" name="email" style="width:190px"
                   value="<%= v == null ? "" : esc(nz(v.getEmail(), "")) %>"></div>
        </div>
        <div class="frm" style="margin-top:10px">
          <div class="fl" style="flex:1 1 300px"><label>Address</label>
            <input type="text" name="address"
                   value="<%= v == null ? "" : esc(nz(v.getAddress(), "")) %>"></div>
          <div class="fl"><label>GSTIN</label>
            <input type="text" name="gstin" style="width:170px" maxlength="15"
                   value="<%= v == null ? "" : esc(nz(v.getGstin(), "")) %>"></div>
          <div class="fl"><label>PAN</label>
            <input type="text" name="pan" style="width:120px" maxlength="10"
                   value="<%= v == null ? "" : esc(nz(v.getPan(), "")) %>"></div>
          <div class="fl"><label>Bank account</label>
            <input type="text" name="bankAccount" style="width:170px"
                   value="<%= v == null ? "" : esc(nz(v.getBankAccount(), "")) %>"></div>
          <div class="fl"><label>IFSC</label>
            <input type="text" name="bankIfsc" style="width:120px" maxlength="15"
                   value="<%= v == null ? "" : esc(nz(v.getBankIfsc(), "")) %>"></div>
        </div>
        <div class="frm" style="margin-top:10px">
          <div class="fl" style="flex:1 1 400px"><label>Notes</label>
            <input type="text" name="notes"
                   value="<%= v == null ? "" : esc(nz(v.getNotes(), "")) %>"></div>
          <button class="btn" type="submit"><%= v == null ? "Add vendor" : "Save changes" %></button>
          <% if (v != null) { %>
            <a class="btn alt" href="<%= ctx %>/vendors">Cancel</a>
            <% if (v.isActive()) { %>
              <button class="btn alt" type="submit" form="retire<%= v.getVendorId() %>"
                      >Retire vendor</button>
            <% } else { %>
              <button class="btn alt" type="submit" form="restore<%= v.getVendorId() %>"
                      >Restore vendor</button>
            <% } %>
          <% } %>
        </div>
      </form>

      <% if (v != null) { %>
        <form id="retire<%= v.getVendorId() %>" method="post" action="<%= ctx %>/vendors">
          <input type="hidden" name="action" value="retire">
          <input type="hidden" name="vendorId" value="<%= v.getVendorId() %>"></form>
        <form id="restore<%= v.getVendorId() %>" method="post" action="<%= ctx %>/vendors">
          <input type="hidden" name="action" value="restore">
          <input type="hidden" name="vendorId" value="<%= v.getVendorId() %>"></form>
      <% } %>
    </details>
  </div>

  <%-- ────────── the list ────────── --%>
  <div class="ef-card">
    <h2>Vendors</h2>
    <p class="hint">Ordered is the value of live work orders; paid is what has actually gone
       out against them. A retired vendor takes no new orders but can still be paid.</p>

    <form class="frm" method="get" action="<%= ctx %>/vendors" style="margin-bottom:12px">
      <div class="fl"><label>Search</label>
        <input type="text" name="q" value="<%= esc(fq) %>"
               placeholder="Name, contact, mobile or category" style="width:250px"></div>
      <div class="fl"><label>&nbsp;</label>
        <label style="font-size:12.5px;color:#42555e;font-weight:400;padding:7px 0">
          <input type="checkbox" name="all" value="1" <%= showAll ? "checked" : "" %>
                 onchange="this.form.submit()"> Show retired</label></div>
      <button class="btn alt" type="submit">Apply</button>
    </form>

    <% if (rows.isEmpty()) { %>
      <p class="hint" style="margin:0">No vendors<%= fq.isEmpty() ? " yet — add the first one above."
                                                                 : " match \"" + esc(fq) + "\"." %></p>
    <% } else { %>
      <table class="t">
        <thead><tr>
          <th>Vendor</th><th style="width:130px">Category</th><th style="width:170px">Contact</th>
          <th style="width:70px" class="num">Orders</th>
          <th style="width:130px" class="num">Ordered</th>
          <th style="width:120px" class="num">Paid</th>
          <th style="width:130px" class="num">Outstanding</th>
        </tr></thead>
        <tbody>
        <% for (Vendor x : rows) { %>
          <tr<%= x.isActive() ? "" : " style=\"opacity:.6\"" %>>
            <td><a href="<%= ctx %>/vendors?id=<%= x.getVendorId() %>"><%= esc(x.getName()) %></a>
              <% if (!x.isActive()) { %> <span class="st off">Retired</span><% } %>
              <% if (x.getOpenOrders() > 0) { %>
                <div class="muted" style="font-size:11px"><%= x.getOpenOrders() %> order(s) still to pay</div>
              <% } %></td>
            <td class="muted"><%= esc(nz(x.getCategory(), "—")) %></td>
            <td class="muted"><%= esc(nz(x.getContactPerson(), "—")) %>
              <% if (x.getMobile() != null) { %>
                <div style="font-size:11px"><%= esc(x.getMobile()) %></div><% } %></td>
            <td class="num"><%= x.getOrderCount() %></td>
            <td class="num"><%= Money.fmt(x.getOrdered()) %></td>
            <td class="num"><%= Money.fmt(x.getPaid()) %></td>
            <td class="num" <%= x.getOutstanding().signum() > 0
                                 ? "style=\"color:#c0392b;font-weight:700\"" : "" %>>
              <%= Money.fmt(x.getOutstanding()) %></td>
          </tr>
        <% } %>
        </tbody>
        <tfoot>
          <tr class="sum">
            <td colspan="4">All <%= rows.size() %> shown</td>
            <td class="num"><%= Money.fmt(sumOrdered) %></td>
            <td class="num"><%= Money.fmt(sumPaid) %></td>
            <td class="num"><%= Money.fmt(sumDue) %></td>
          </tr>
        </tfoot>
      </table>
    <% } %>
  </div>

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

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private String nz(String s, String dflt) {
        return (s == null || s.trim().isEmpty()) ? dflt : s;
    }
%>
