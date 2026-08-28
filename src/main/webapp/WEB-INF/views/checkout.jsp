<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.tution.model.Student, com.tution.model.User, com.tution.util.FeeCalculator" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String ctx = request.getContextPath();
    Student student = (Student) request.getAttribute("student");
    if (student == null) { response.sendRedirect(ctx + "/fees"); return; }
    int amount      = (Integer) request.getAttribute("amount");
    String orderId  = (String) request.getAttribute("orderId");
    boolean mock    = (Boolean) request.getAttribute("mock");
    String keyId    = (String) request.getAttribute("keyId");
    String mockPaymentId = (String) request.getAttribute("mockPaymentId");
%>
<%! private String esc(String s){ return s==null? "": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); } %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Pay Online – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .pay-wrap { max-width:460px; margin:30px auto 60px; padding:0 1rem; }
    .pay-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow:hidden; }
    .pay-hero { background:linear-gradient(120deg,var(--green-dark),var(--green-mid)); padding:24px 26px; text-align:center; color:#fff; }
    .pay-hero .amt { font-size:38px; font-weight:900; color:var(--accent); line-height:1.1; }
    .pay-hero .lbl { font-size:11px; color:#A8D9BC; text-transform:uppercase; letter-spacing:1px; margin-bottom:6px; }
    .pay-body { padding:22px 26px; }
    .row { display:flex; justify-content:space-between; font-size:13px; padding:7px 0; border-bottom:1px dashed var(--border); }
    .row span:first-child { color:var(--muted); }
    .row span:last-child { font-weight:600; }
    .test-banner { background:#FFF4D6; color:#9A6B00; border:1px solid #F4C542; border-radius:8px; padding:10px 12px; font-size:12px; font-weight:600; margin:16px 0; text-align:center; }
    .btn-pay { width:100%; padding:14px; background:var(--green); color:#fff; border:none; border-radius:8px; font-size:16px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; margin-top:16px; }
    .btn-pay:hover { background:var(--green-dark); }
    .btn-fail { width:100%; padding:11px; background:transparent; color:#C0392B; border:1.5px solid #E2B4AC; border-radius:8px; font-size:13px; font-weight:600; cursor:pointer; font-family:'Inter',sans-serif; margin-top:10px; text-decoration:none; display:block; text-align:center; }
    .cancel { display:block; text-align:center; margin-top:14px; font-size:13px; color:var(--muted); text-decoration:none; }
  </style>
</head>
<body>

<header>
  <a class="logo" href="<%= ctx %>/dashboard.jsp">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Tuition Management System</span>
    </div>
  </a>
  <nav><a href="<%= ctx %>/fees" class="logout">Fees</a><a href="<%= ctx %>/logout">Logout</a></nav>
</header>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="pay-wrap">
  <div class="pay-card">
    <div class="pay-hero">
      <div class="lbl">Amount to Pay</div>
      <div class="amt"><%= FeeCalculator.inr(amount) %></div>
    </div>
    <div class="pay-body">
      <div class="row"><span>Student</span><span><%= esc(student.getFullName()) %></span></div>
      <div class="row"><span>Admission No</span><span><%= esc(student.getAdmissionNo()) %></span></div>
      <div class="row"><span>Order ID</span><span><%= esc(orderId) %></span></div>

      <% if (mock) { %>
        <div class="test-banner">🧪 TEST MODE — no real money is charged. This simulates a Razorpay payment.</div>
        <form action="<%= ctx %>/pay-verify" method="post">
          <input type="hidden" name="studentId" value="<%= student.getStudentId() %>"/>
          <input type="hidden" name="amount" value="<%= amount %>"/>
          <input type="hidden" name="razorpay_order_id" value="<%= esc(orderId) %>"/>
          <input type="hidden" name="razorpay_payment_id" value="<%= esc(mockPaymentId) %>"/>
          <input type="hidden" name="razorpay_signature" value="mock_signature"/>
          <button type="submit" class="btn-pay">✓ Simulate Successful Payment</button>
        </form>
        <a class="btn-fail" href="<%= ctx %>/collect?studentId=<%= student.getStudentId() %>">✕ Cancel (Simulate Failure)</a>
      <% } else { %>
        <button id="rzpBtn" class="btn-pay">💳 Pay Securely with Razorpay</button>
        <a class="cancel" href="<%= ctx %>/collect?studentId=<%= student.getStudentId() %>">Cancel</a>

        <!-- hidden form submitted after a successful checkout -->
        <form id="verifyForm" action="<%= ctx %>/pay-verify" method="post">
          <input type="hidden" name="studentId" value="<%= student.getStudentId() %>"/>
          <input type="hidden" name="amount" value="<%= amount %>"/>
          <input type="hidden" name="razorpay_order_id" id="f_order"/>
          <input type="hidden" name="razorpay_payment_id" id="f_payment"/>
          <input type="hidden" name="razorpay_signature" id="f_sig"/>
        </form>

        <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
        <script>
          var options = {
            "key": "<%= esc(keyId) %>",
            "amount": <%= amount * 100 %>,
            "currency": "INR",
            "name": "Havellsson NEET Samrat",
            "description": "Fee payment - <%= esc(student.getAdmissionNo()) %>",
            "order_id": "<%= esc(orderId) %>",
            "prefill": { "name": "<%= esc(student.getFullName()) %>" },
            "theme": { "color": "#1A7A4A" },
            "handler": function (response) {
              document.getElementById('f_order').value   = response.razorpay_order_id;
              document.getElementById('f_payment').value = response.razorpay_payment_id;
              document.getElementById('f_sig').value     = response.razorpay_signature;
              document.getElementById('verifyForm').submit();
            },
            "modal": { "ondismiss": function(){ window.location.href = "<%= ctx %>/collect?studentId=<%= student.getStudentId() %>"; } }
          };
          var rzp = new Razorpay(options);
          document.getElementById('rzpBtn').onclick = function(e){ e.preventDefault(); rzp.open(); };
          rzp.open(); // auto-open the checkout
        </script>
      <% } %>
    </div>
  </div>
</div>

</body>
</html>
