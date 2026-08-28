<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String ctx     = request.getContextPath();
    boolean success = Boolean.TRUE.equals(request.getAttribute("success"));
    String error    = (String) request.getAttribute("error");
    String savedName = (String) request.getAttribute("savedName");
    String vName   = val(request.getAttribute("fullName"));
    String vMobile = val(request.getAttribute("mobile"));
    String vEmail  = val(request.getAttribute("email"));
    String vClass  = val(request.getAttribute("classInterest"));
    String vSource = val(request.getAttribute("source"));
    String vMsg    = val(request.getAttribute("message"));
%>
<%!
    private String val(Object o) { return o == null ? "" : o.toString(); }
    private String sel(String a, String b) { return a.equals(b) ? "selected" : ""; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Inquiry – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .inq-wrap { max-width:520px; margin:0 auto; padding:30px 1rem 60px; }
    .inq-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow); overflow:hidden; }
    .inq-hero { background:linear-gradient(120deg,var(--green-dark) 0%,var(--green-mid) 100%); padding:26px 26px 22px; text-align:center; }
    .inq-hero .eyebrow { display:inline-block; background:var(--accent); color:var(--green-dark); font-size:10px;
      font-weight:800; letter-spacing:1.5px; text-transform:uppercase; padding:3px 13px; border-radius:20px; margin-bottom:10px; }
    .inq-hero h1 { font-family:'Playfair Display',serif; font-size:23px; color:#fff; margin-bottom:6px; }
    .inq-hero p { color:#A8D9BC; font-size:13px; }
    .inq-body { padding:24px 26px; }
    .field { margin-bottom:14px; }
    .field label { display:block; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
    .req { color:#E05A2B; margin-left:2px; }
    .field input, .field select, .field textarea { width:100%; padding:11px 13px; font-size:15px; font-family:'Inter',sans-serif;
      border:1.5px solid var(--border); border-radius:7px; color:var(--text); background:var(--green-pale);
      transition:border-color .2s, box-shadow .2s; outline:none; }
    .field textarea { resize:vertical; min-height:74px; }
    .field input:focus, .field select:focus, .field textarea:focus { border-color:var(--green); box-shadow:0 0 0 3px rgba(26,122,74,0.13); background:#fff; }
    .field-row { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
    .btn-submit { width:100%; padding:13px; background:var(--green); color:#fff; border:none; border-radius:8px;
      font-size:16px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; transition:background .2s; margin-top:4px; }
    .btn-submit:hover { background:var(--green-dark); }
    .alert { padding:12px 14px; border-radius:8px; font-size:13px; font-weight:500; margin-bottom:16px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .terms-box { background:var(--green-pale); border:1px solid var(--border); border-radius:8px;
      padding:12px 14px; margin-bottom:12px; font-size:12px; color:var(--muted); line-height:1.65; max-height:150px; overflow-y:auto; }
    .terms-box strong { display:block; color:var(--green-dark); font-size:13px; margin-bottom:6px; }
    .terms-box ul { margin:0 0 0 16px; padding:0; }
    .terms-box li { margin-bottom:4px; }
    .check-row { display:flex; align-items:flex-start; gap:10px; margin-bottom:16px; }
    .check-row input[type="checkbox"] { -webkit-appearance:auto; appearance:auto; width:18px; height:18px;
      min-width:18px; margin-top:1px; accent-color:var(--green); cursor:pointer; }
    .check-row label { font-size:13px; color:var(--text); font-weight:500; cursor:pointer; line-height:1.5; }
    .inq-foot { text-align:center; font-size:12px; color:var(--muted); padding:0 26px 22px; }
    .inq-foot a { color:var(--green); font-weight:600; text-decoration:none; }
    .success-screen { text-align:center; padding:42px 26px; }
    .success-icon { font-size:52px; margin-bottom:14px; }
    .success-screen h2 { font-family:'Playfair Display',serif; font-size:22px; color:var(--green-dark); margin-bottom:10px; }
    .success-screen p { font-size:14px; color:var(--muted); line-height:1.6; margin-bottom:20px; }
    .btn-secondary { padding:11px 24px; background:transparent; color:var(--green-dark); border:2px solid var(--green-dark);
      border-radius:8px; font-size:14px; font-weight:600; text-decoration:none; display:inline-block; }
    .btn-secondary:hover { background:var(--green-dark); color:#fff; }
  </style>
</head>
<body>

<header>
  <a class="logo" href="<%= ctx %>/login.jsp">
    <img class="logo-mark" src="<%= request.getContextPath() %>/img/havellsson-banner.webp" alt="Havellsson NEET Samrat">
    <div class="logo-text">
      <span class="sub">Expert Coaching · 11th &amp; 12th Science</span>
    </div>
  </a>
  <nav>
    <a href="<%= ctx %>/inquiry.jsp" class="logout">Inquiry</a>
    <a href="<%= ctx %>/login.jsp">Staff Login</a>
  </nav>
</header>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="inq-wrap">
  <div class="inq-card">

  <% if (success) {
       String waMobile = (String) request.getAttribute("savedMobile");
       String waClass  = (String) request.getAttribute("savedClass");
       String waLink = null;
       boolean waSent = false;
       if (waMobile != null && !waMobile.isEmpty()) {
           com.tution.service.WhatsAppService wa = new com.tution.service.WhatsAppService();
           waSent = wa.sendInquiry(waMobile, savedName, waClass);          // auto-send approved template
           waLink = wa.link(waMobile, wa.inquiryMessage(savedName, waClass)); // tap-to-send fallback
       }
  %>
    <div class="success-screen">
      <div class="success-icon">🎉</div>
      <h2>Thank You<%= (savedName == null || savedName.isEmpty()) ? "" : ", " + savedName %>!</h2>
      <p>Your inquiry has been received. Our team will call you within 24 hours to share batch details and answer your questions.</p>
      <% if (waSent) { %>
        <p style="margin-bottom:14px;color:#15803D;font-weight:600;">✓ WhatsApp confirmation sent automatically</p>
      <% } else if (waLink != null) { %>
        <p style="margin-bottom:14px;"><a class="btn-wa" href="<%= waLink %>" target="_blank" rel="noopener">📱 Send WhatsApp Confirmation</a></p>
      <% } %>
      <a class="btn-secondary" href="<%= ctx %>/inquiry.jsp">Submit Another Inquiry</a>
    </div>
  <% } else { %>

    <div class="inq-hero">
      <div class="eyebrow">Step 1 of 2 — Inquiry</div>
      <h1>Enquire About Admission</h1>
      <p>Leave your details and we'll get back to you shortly.</p>
    </div>

    <form class="inq-body" action="<%= ctx %>/inquiry" method="post">

      <% if (error != null) { %>
        <div class="alert error"><%= error %></div>
      <% } %>

      <div class="field">
        <label>Full Name <span class="req">*</span></label>
        <input type="text" name="fullName" placeholder="e.g. Priya Deshmukh" value="<%= vName %>" required>
      </div>

      <div class="field-row">
        <div class="field">
          <label>Mobile <span class="req">*</span></label>
          <input type="tel" name="mobile" placeholder="10-digit number" maxlength="10" inputmode="numeric" pattern="[6-9][0-9]{9}" value="<%= vMobile %>" required>
        </div>
        <div class="field">
          <label>Email</label>
          <input type="email" name="email" placeholder="optional" value="<%= vEmail %>">
        </div>
      </div>

      <div class="field-row">
        <div class="field">
          <label>Class Interested</label>
          <select name="classInterest">
            <option value="">Select</option>
            <option <%= sel("11th - Science (PCB)", vClass) %>>11th - Science (PCB)</option>
            <option <%= sel("11th - Science (PCM)", vClass) %>>11th - Science (PCM)</option>
            <option <%= sel("12th - Science (PCB)", vClass) %>>12th - Science (PCB)</option>
            <option <%= sel("12th - Science (PCM)", vClass) %>>12th - Science (PCM)</option>
          </select>
        </div>
        <div class="field">
          <label>How did you hear about us?</label>
          <select name="source">
            <option value="">Select</option>
            <option <%= sel("Friend / Referral", vSource) %>>Friend / Referral</option>
            <option <%= sel("Social Media", vSource) %>>Social Media</option>
            <option <%= sel("Newspaper / Pamphlet", vSource) %>>Newspaper / Pamphlet</option>
            <option <%= sel("Walk-in", vSource) %>>Walk-in</option>
            <option <%= sel("Other", vSource) %>>Other</option>
          </select>
        </div>
      </div>

      <div class="field">
        <label>Message</label>
        <textarea name="message" placeholder="Any questions? (optional)"><%= vMsg %></textarea>
      </div>

      <div class="terms-box">
        <strong>Terms &amp; Conditions</strong>
        <ul>
          <li>The information provided in this form is true and accurate to the best of my knowledge.</li>
          <li>I authorise Havellsson NEET Samrat to contact me via call, SMS, email and WhatsApp regarding admission, batches, fees and updates.</li>
          <li>Submitting this inquiry does not guarantee admission; seats are subject to availability and eligibility.</li>
          <li>Fees, batch timings and the schedule are decided by the institute and may change.</li>
          <li>My personal details will be used only for admission-related communication and will not be shared with third parties.</li>
        </ul>
      </div>

      <div class="check-row">
        <input type="checkbox" id="agree" name="agree" required>
        <label for="agree">I have read and agree to the Terms &amp; Conditions of Havellsson NEET Samrat.</label>
      </div>

      <button type="submit" class="btn-submit">Submit Inquiry →</button>
    </form>

    <div class="inq-foot">
      Are you staff? <a href="<%= ctx %>/login.jsp">Sign in here</a>
    </div>

  <% } %>

  </div>
</div>

</body>
</html>
