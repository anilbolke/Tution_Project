<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.tution.model.User, com.tution.model.Inquiry, com.tution.dao.InquiryDAO" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx          = request.getContextPath();
    String error        = (String) request.getAttribute("error");
    String admissionNo  = (String) request.getAttribute("admissionNo");
    String savedName    = (String) request.getAttribute("savedName");
    boolean showSuccess = (admissionNo != null);

    // Pre-fill when converting a lead into an admission. The lead now carries
    // most of the admission form already, so the counsellor re-types as little
    // as possible — that is the whole point of the conversion step.
    String preName = "", preMobile = "", preEmail = "", preClass = "";
    String preDob = "", preGender = "", preBoard = "", preSchool = "", prePercent = "";
    String preParentName = "", preParentMobile = "", preAddress = "";
    String preBatch = "", preBranch = "";
    String inquiryId = request.getParameter("inquiryId");
    if (inquiryId != null && inquiryId.matches("\\d+")) {
        try {
            Inquiry pq = new InquiryDAO().findById(Integer.parseInt(inquiryId));
            if (pq != null) {
                preName   = nz(pq.getFullName());
                preMobile = nz(pq.getMobile());
                preEmail  = nz(pq.getEmail());
                // The lead's chosen course is the admission class; fall back to
                // the legacy class_interest column for older enquiries.
                preClass  = (pq.getCourseName() != null && !pq.getCourseName().isEmpty())
                            ? pq.getCourseName() : nz(pq.getClassInterest());
                preDob          = nz(pq.getDob());
                preGender       = nz(pq.getGender());
                preBoard        = nz(pq.getBoard());
                preSchool       = nz(pq.getSchoolName());
                prePercent      = nz(pq.getPercentage());
                preParentName   = nz(pq.getParentName());
                preParentMobile = nz(pq.getParentMobile());
                preAddress      = nz(pq.getAddress());
                preBatch        = nz(pq.getBatchPref());
                preBranch       = nz(pq.getBranch());
            }
        } catch (Exception ignore) { }
    } else {
        inquiryId = null;
    }

    // Batch list comes from the masters table now, not a hardcoded <option> list.
    java.util.List<String> batchList = null;
    try { batchList = new com.tution.dao.MasterDAO().batches(); } catch (Exception ignore) { }

    // Fee plans and the GST rate drive the whole pricing section — every figure
    // on this page comes from the brochure via fee_plans, nothing is hardcoded.
    java.util.List<com.tution.model.FeePlan> plans = null;
    double gstRate = 0;
    try {
        plans = new com.tution.dao.FeePlanDAO().findAll();
        gstRate = new com.tution.dao.SettingsDAO().gstRate();
    } catch (Exception ignore) { }
%>
<%!
    private String nz(String s){ return s == null ? "" : s; }
    /** Human label for a fee-plan delivery mode, used as the optgroup heading. */
    private String modeLabel(String mode) {
        if (mode == null) return "Courses";
        switch (mode) {
            case "CLASSROOM":   return "Classroom Courses";
            case "HYBRID":      return "Hybrid Courses (HHC)";
            case "DISTANCE":    return "Distance Learning (HDLP)";
            case "TEST_SERIES": return "Test Series (HEATS)";
            case "SHORT":       return "Short Courses";
            default:            return mode;
        }
    }
    private String attr(String s){ return s==null? "": s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;"); }
    private String selOpt(String a, String b){ return a != null && a.equals(b) ? "selected" : ""; }

    /**
     * Value for a re-rendered field: what the operator just typed wins over the
     * inquiry pre-fill. Without this, a validation error or the duplicate warning
     * would wipe a long form and make the user retype everything.
     */
    private String pv(javax.servlet.http.HttpServletRequest req, String name, String fallback) {
        String v = req.getParameter(name);
        return attr((v != null && !v.isEmpty()) ? v : fallback);
    }
    /** Same idea for a &lt;select&gt;/radio: is this the option that was submitted? */
    private String pSel(javax.servlet.http.HttpServletRequest req, String name, String option) {
        return option.equals(req.getParameter(name)) ? "selected" : "";
    }
    private String pChk(javax.servlet.http.HttpServletRequest req, String name, String option) {
        return option.equals(req.getParameter(name)) ? "checked" : "";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>Admission – Havellsson NEET Samrat</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet"/>
<style>
  :root {
    --green:#1A7A4A; --green-dark:#135C37; --green-mid:#1E8F55;
    --green-light:#E8F7EF; --green-pale:#F0FAF4; --accent:#F4C542;
    --bg:#F2FAF5; --text:#0F2518; --muted:#5A7364; --border:#B8DECA;
    --white:#FFFFFF; --success:#15803D; --radius:10px; --shadow:0 4px 24px rgba(26,122,74,0.10);
  }
  *, *::before, *::after { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:'Inter',sans-serif; background:var(--bg); color:var(--text); min-height:100vh; }

  header {
    background:var(--green-dark); padding:0 1.5rem; display:flex; align-items:center;
    justify-content:space-between; height:64px; position:sticky; top:0; z-index:100;
    box-shadow:0 2px 12px rgba(0,0,0,0.2);
  }
  .logo { display:flex; align-items:center; gap:10px; text-decoration:none; }
  .logo-icon { width:42px; height:42px; background:var(--accent); border-radius:8px;
    display:flex; align-items:center; justify-content:center;
    font-family:'Playfair Display',serif; font-size:20px; color:var(--green-dark); font-weight:900; }
  /* Institute logo on a white chip — it is dark green on transparent, so it
     would be invisible against the dark green header. */
  .logo-mark { height:34px; width:auto; display:block; flex:none;
    background:#fff; border-radius:7px; padding:4px 10px;
    box-shadow:0 1px 4px rgba(0,0,0,0.18); }
  .logo-text .brand { display:block; font-size:15px; font-weight:700; color:#fff; }
  .logo-text .sub   { display:block; font-size:11px; color:#8ECFAC; margin-top:1px; }
  nav { display:flex; gap:4px; align-items:center; }
  nav a, nav span.user { color:#8ECFAC; font-size:13px; font-weight:600; text-decoration:none;
    padding:6px 14px; border-radius:6px; white-space:nowrap; transition:all .2s; }
  nav a:hover { color:var(--accent); }
  nav a.active { color:var(--green-dark); background:var(--accent); }

  .neet-ribbon { background:var(--accent); color:var(--green-dark); font-size:11px;
    font-weight:800; padding:5px 12px; text-align:center; letter-spacing:0.8px; }

  .hero-strip { background:linear-gradient(120deg,var(--green-dark) 0%,var(--green-mid) 100%);
    padding:26px 1.25rem 22px; text-align:center; }
  .eyebrow { display:inline-block; background:var(--accent); color:var(--green-dark);
    font-size:10px; font-weight:800; letter-spacing:1.5px; text-transform:uppercase;
    padding:3px 13px; border-radius:20px; margin-bottom:10px; }
  .hero-strip h1 { font-family:'Playfair Display',serif; font-size:27px; color:#fff; margin-bottom:6px; }
  .hero-strip p  { color:#A8D9BC; font-size:14px; max-width:460px; margin:0 auto; }

  .page-body { max-width:820px; margin:0 auto; padding:28px 1.5rem 60px; }

  .section-card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
    padding:24px 28px; margin-bottom:20px; }
  .section-header { display:flex; align-items:flex-start; gap:12px; margin-bottom:18px;
    padding-bottom:14px; border-bottom:1.5px solid var(--green-light); }
  .section-num { width:30px; height:30px; min-width:30px; background:var(--green); border-radius:7px;
    display:flex; align-items:center; justify-content:center; color:#fff; font-size:13px; font-weight:700; }
  .section-header h2 { font-size:15px; font-weight:700; color:var(--green-dark); }
  .section-header p  { font-size:12px; color:var(--muted); margin-top:2px; }

  .field { margin-bottom:14px; }
  .field label { display:block; font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:5px; }
  .req { color:#E05A2B; margin-left:2px; }
  input, select, textarea { width:100%; padding:11px 13px; font-size:15px; font-family:'Inter',sans-serif;
    border:1.5px solid var(--border); border-radius:7px; color:var(--text); background:var(--green-pale);
    transition:border-color .2s, box-shadow .2s; outline:none; -webkit-appearance:none; appearance:none; }
  input:focus, select:focus { border-color:var(--green); box-shadow:0 0 0 3px rgba(26,122,74,0.13); background:#fff; }
  select { background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath d='M1 1l5 5 5-5' stroke='%235A7364' fill='none' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
    background-repeat:no-repeat; background-position:right 12px center; padding-right:34px; background-color:var(--green-pale); }
  .field-row { display:grid; grid-template-columns:1fr; gap:14px; }
  @media(min-width:500px){ .field-row { grid-template-columns:1fr 1fr; } }

  .photo-upload-area { border:2px dashed var(--border); border-radius:8px; padding:24px 16px;
    text-align:center; cursor:pointer; background:var(--green-pale); transition:border-color .2s, background .2s; }
  .photo-upload-area:active { background:var(--green-light); }
  .photo-upload-area input { display:none; }
  .photo-icon { font-size:30px; margin-bottom:6px; }
  .photo-upload-area p { font-size:12px; color:var(--muted); }
  .photo-upload-area strong { font-size:14px; color:var(--green-dark); display:block; margin-bottom:4px; }
  #previewImg { width:80px; height:80px; border-radius:8px; object-fit:cover; display:none;
    margin:10px auto 0; border:2px solid var(--green); }

  .fee-fixed-block { background:var(--green-pale); border:1px solid var(--border); border-radius:8px;
    padding:14px 16px; margin-bottom:16px; }
  .fee-fixed-title { font-size:11px; font-weight:700; color:var(--muted); text-transform:uppercase;
    letter-spacing:0.8px; margin-bottom:10px; }
  .fee-fixed-row { display:flex; justify-content:space-between; align-items:center; font-size:13px;
    color:var(--text); padding:5px 0; }
  .fee-fixed-row:not(:last-child) { border-bottom:1px solid var(--border); }
  .fee-val { font-weight:700; color:var(--green-dark); }

  .grand-total-box { background:var(--green-dark); border-radius:10px; padding:18px 20px;
    text-align:center; margin-bottom:22px; }
  .gt-label  { font-size:11px; color:#8ECFAC; font-weight:700; text-transform:uppercase; letter-spacing:1px; margin-bottom:6px; }
  .gt-amount { font-size:36px; font-weight:900; color:var(--accent); line-height:1; margin-bottom:4px; }
  .gt-break  { font-size:12px; color:#A8D9BC; }

  .slab-label { font-size:13px; font-weight:600; color:var(--green-dark); margin-bottom:10px; }
  .slab-req   { color:#E05A2B; }
  .slab-grid  { display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-bottom:20px; }
  @media(min-width:560px){ .slab-grid { grid-template-columns:repeat(4,1fr); } }
  .slab-card { position:relative; }
  .slab-card input[type="radio"] { position:absolute; opacity:0; width:0; height:0; }
  .slab-card label { display:block; padding:14px 10px 12px; text-align:center; border:2px solid var(--border);
    border-radius:10px; cursor:pointer; background:var(--green-pale); transition:all .2s; position:relative;
    overflow:hidden; min-height:118px; }
  .slab-card input:checked + label { border-color:var(--green); background:var(--green-light);
    box-shadow:0 0 0 3px rgba(26,122,74,0.14); }
  .slab-card label:hover { border-color:var(--green-mid); }
  .slab-badge { position:absolute; top:0; right:0; background:var(--green); color:#fff; font-size:9px;
    font-weight:800; padding:3px 8px; border-radius:0 8px 0 8px; letter-spacing:0.5px; text-transform:uppercase; }
  .slab-badge.best { background:var(--accent); color:var(--green-dark); }
  .slab-name { font-size:11px; font-weight:700; color:var(--muted); margin-bottom:5px; text-transform:uppercase; letter-spacing:0.5px; }
  .slab-card input:checked + label .slab-name { color:var(--green-dark); }
  .slab-amount { font-size:17px; font-weight:800; color:var(--green-dark); line-height:1; }
  .slab-amount span { font-size:11px; font-weight:500; color:var(--muted); }
  .slab-detail { font-size:11px; color:var(--muted); margin-top:4px; }
  .slab-saving { font-size:11px; font-weight:700; margin-top:5px; }
  .slab-saving.save { color:var(--success); }
  .slab-saving.no-save { color:var(--muted); }

  #instalmentSection { animation:fadeIn .3s ease; }
  @keyframes fadeIn { from { opacity:0; transform:translateY(8px); } to { opacity:1; transform:none; } }
  .inst-header { margin-bottom:14px; }
  .inst-title { font-size:15px; font-weight:700; color:var(--green-dark); }
  .inst-subtitle { font-size:12px; color:var(--muted); margin-top:3px; }
  .inst-summary-strip { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; background:var(--green-dark);
    border-radius:10px; padding:14px 12px; margin-bottom:16px; text-align:center; }
  .inst-strip-item { border-right:1px solid rgba(255,255,255,0.1); }
  .inst-strip-item:last-child { border-right:none; }
  .ist-val { font-size:16px; font-weight:800; color:#fff; line-height:1.2; }
  .ist-val.green { color:var(--accent); }
  .ist-lbl { font-size:10px; color:#8ECFAC; font-weight:600; text-transform:uppercase; letter-spacing:0.6px; margin-top:3px; }
  .inst-table-wrap { overflow-x:auto; border-radius:8px; border:1px solid var(--border); }
  .inst-table { width:100%; border-collapse:collapse; font-size:13px; min-width:380px; }
  .inst-table th { background:var(--green); color:#fff; padding:10px 12px; text-align:left; font-size:11px;
    font-weight:700; text-transform:uppercase; letter-spacing:0.4px; }
  .inst-table td { padding:10px 12px; border-bottom:1px solid var(--border); color:var(--text); }
  .inst-table tr:last-child td { border-bottom:none; }
  .inst-table tr:nth-child(even) td { background:var(--green-pale); }
  .inst-num { font-weight:700; color:var(--green-dark); }
  .inst-amt { font-weight:700; color:var(--green-dark); white-space:nowrap; }
  .inst-due { font-size:12px; color:var(--muted); white-space:nowrap; }
  .inst-note { margin-top:12px; background:var(--green-pale); border-left:3px solid var(--green);
    border-radius:0 8px 8px 0; padding:10px 14px; font-size:12px; color:var(--muted); line-height:1.65; }

  .declaration-box { background:var(--green-pale); border:1px solid var(--border); border-radius:8px;
    padding:14px; font-size:13px; color:var(--muted); line-height:1.7; }
  .check-row { display:flex; align-items:flex-start; gap:10px; margin-top:14px; }
  .check-row input[type="checkbox"] { -webkit-appearance:auto; appearance:auto; width:18px; height:18px; min-width:18px;
    margin-top:1px; padding:0; background:none; border:none; accent-color:var(--green); cursor:pointer; }
  .check-row label { font-size:13px; color:var(--text); font-weight:500; cursor:pointer; line-height:1.5; }

  .alert { padding:12px 14px; border-radius:8px; font-size:13px; font-weight:500; margin-bottom:18px; }
  .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }

  .submit-area { text-align:center; margin-top:20px; }
  .btn-submit { width:100%; max-width:420px; padding:15px; background:var(--green); color:#fff; border:none;
    border-radius:8px; font-size:16px; font-weight:700; cursor:pointer; font-family:'Inter',sans-serif; transition:background .2s; }
  .btn-submit:hover { background:var(--green-dark); }
  .submit-note { font-size:12px; color:var(--muted); margin-top:10px; line-height:1.5; }

  .success-screen { text-align:center; padding:48px 20px; }
  .success-icon { font-size:56px; margin-bottom:16px; }
  .success-screen h2 { font-family:'Playfair Display',serif; font-size:24px; color:var(--green-dark); margin-bottom:10px; }
  .success-screen p { font-size:14px; color:var(--muted); max-width:360px; margin:0 auto 22px; line-height:1.65; }
  .admission-id { display:inline-block; background:var(--green-light); border:2px solid var(--green);
    border-radius:10px; padding:14px 28px; font-size:22px; font-weight:700; color:var(--green-dark);
    letter-spacing:2px; margin-bottom:22px; }
  .admission-id small { display:block; font-size:11px; color:var(--muted); font-weight:400; letter-spacing:0; margin-bottom:4px; }
  .btn-secondary { padding:11px 28px; background:transparent; color:var(--green-dark); border:2px solid var(--green-dark);
    border-radius:8px; font-size:14px; font-weight:600; cursor:pointer; font-family:'Inter',sans-serif; text-decoration:none; display:inline-block; }
  .btn-secondary:hover { background:var(--green-dark); color:#fff; }
</style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="students"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<% if (showSuccess) {
     String waMobile = (String) request.getAttribute("savedMobile");
     String waClass  = (String) request.getAttribute("savedClass");
     String waSlab   = (String) request.getAttribute("savedSlab");
     String waLink = null;
     boolean waSent = false;
     boolean ledgerWarning = Boolean.TRUE.equals(request.getAttribute("ledgerWarning"));
     if (waMobile != null && !waMobile.isEmpty()) {
         com.tution.service.WhatsAppService wa = new com.tution.service.WhatsAppService();
         waSent = wa.sendAdmission(waMobile, savedName, admissionNo, waClass, waSlab);          // auto-send approved template
         waLink = wa.link(waMobile, wa.admissionMessage(savedName, admissionNo, waClass, waSlab)); // tap-to-send fallback
     }
%>
<!-- ─── SUCCESS ─── -->
<div class="page-body sp-body">
  <div class="success-screen section-card">
    <div class="success-icon">🎉</div>
    <h2>Admission Saved!</h2>
    <p><%= (savedName == null ? "The student" : savedName) %>'s admission has been recorded successfully.</p>
    <div class="admission-id">
      <small>Admission Number</small>
      <span><%= admissionNo %></span>
    </div>
    <p style="font-size:13px;color:var(--muted);margin-bottom:22px;">
      Please share this number with the student for future reference.
    </p>
    <% if (ledgerWarning) { %>
      <div class="alert" style="background:#FFF6E0;color:#7A5200;border:1.5px solid #F0D89A;padding:13px 15px;border-radius:9px;font-size:13px;margin-bottom:16px;text-align:left;">
        ⚠ The admission is saved, but the fee ledger could not be opened automatically —
        this student has no instalment schedule yet. Open <a href="<%= ctx %>/fees" style="color:#7A5200;font-weight:700;">Fees</a>
        and set it up manually so due-date reminders aren't missed.
      </div>
    <% } %>
    <% if (waSent) { %>
      <p style="margin-bottom:16px;color:#15803D;font-weight:600;">✓ WhatsApp confirmation sent automatically</p>
    <% } else if (waLink != null) { %>
      <p style="margin-bottom:16px;"><a class="btn-wa" href="<%= waLink %>" target="_blank" rel="noopener">📱 Send WhatsApp Confirmation</a></p>
    <% } %>
    <a class="btn-secondary" href="<%= ctx %>/admission.jsp">+ New Admission</a>
  </div>
</div>
<% } else { %>

<div class="hero-strip">
  <div class="eyebrow">New Admission</div>
  <h1>Student Admission Form</h1>
  <p>Fill the form below to register a new student.</p>
</div>

<div class="page-body sp-body">
<form id="admissionForm" action="<%= ctx %>/admission" method="post" enctype="multipart/form-data">

  <% if (inquiryId != null) { %>
    <input type="hidden" name="inquiryId" value="<%= attr(inquiryId) %>"/>
    <div class="alert" style="background:var(--green-light);color:var(--green-dark);border:1px solid var(--border);padding:12px 14px;border-radius:8px;font-size:13px;margin-bottom:18px;">
      🔄 Converting inquiry #<%= attr(inquiryId) %> into an admission — details below are pre-filled. Complete the remaining fields and submit.
    </div>
  <% } %>

  <% String dupWarn = (String) request.getAttribute("duplicateWarning"); %>
  <% if (dupWarn != null) { %>
    <!-- Duplicate-admission guard: this mobile already belongs to an enrolled
         student. Siblings legitimately share a parent number, so the operator
         confirms rather than being blocked outright. -->
    <div class="alert" style="background:#FFF6E0;color:#7A5200;border:1.5px solid #F0D89A;padding:13px 15px;border-radius:9px;font-size:13px;margin-bottom:16px;">
      ⚠ <%= dupWarn %>
      <label style="display:flex;align-items:center;gap:8px;margin-top:11px;font-weight:700;color:#5C3E00;">
        <input type="checkbox" name="confirmDuplicate" value="1"/>
        Admit anyway — this is a different student
      </label>
    </div>
  <% } else if (error != null) { %>
    <div class="alert error"><%= error %></div>
  <% } %>

  <!-- 1 Student Info -->
  <div class="section-card">
    <div class="section-header">
      <div class="section-num">1</div>
      <div><h2>Student Information</h2><p>Academic &amp; personal details</p></div>
    </div>
    <div class="field">
      <label>Full Name <span class="req">*</span></label>
      <input type="text" name="fullName" placeholder="e.g. Priya Deshmukh" autocomplete="name" value="<%= pv(request,"fullName",preName) %>" required/>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Date of Birth <span class="req">*</span></label>
        <input type="date" name="dob" value="<%= pv(request,"dob",preDob) %>" required/>
      </div>
      <div class="field">
        <label>Gender <span class="req">*</span></label>
        <select name="gender" required>
          <option value="">Select</option>
          <option <%= selOpt("Male", preGender) %> <%= pSel(request,"gender","Male") %>>Male</option>
          <option <%= selOpt("Female", preGender) %> <%= pSel(request,"gender","Female") %>>Female</option>
          <option value="Other" <%= selOpt("Other", preGender) %> <%= pSel(request,"gender","Other") %>>Prefer not to say</option>
        </select>
      </div>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Current Class</label>
        <input type="text" name="currentClass" placeholder="e.g. 10th / 11th / 12th passed"
               value="<%= pv(request,"currentClass",preClass) %>"/>
        <p class="submit-note" style="text-align:left;margin-top:4px;">The programme is chosen in section 5 — it sets the course on record.</p>
      </div>
      <div class="field">
        <label>Board <span class="req">*</span></label>
        <select name="admBoard" required>
          <option value="">Select board</option>
          <option <%= selOpt("Maharashtra HSC Board", preBoard) %> <%= pSel(request,"admBoard","Maharashtra HSC Board") %>>Maharashtra HSC Board</option>
          <option <%= selOpt("CBSE", preBoard) %> <%= pSel(request,"admBoard","CBSE") %>>CBSE</option>
          <option <%= selOpt("ICSE", preBoard) %> <%= pSel(request,"admBoard","ICSE") %>>ICSE</option>
          <option <%= selOpt("IB", preBoard) %> <%= pSel(request,"admBoard","IB") %>>IB</option>
        </select>
      </div>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Previous School</label>
        <input type="text" name="school" placeholder="School attended last year" value="<%= pv(request,"school",preSchool) %>"/>
      </div>
      <div class="field">
        <label>10th % / CGPA</label>
        <input type="text" name="marks" placeholder="e.g. 88% or 8.8 CGPA" inputmode="decimal" value="<%= pv(request,"marks",prePercent) %>"/>
      </div>
    </div>
  </div>

  <!-- 2 Contact -->
  <div class="section-card">
    <div class="section-header">
      <div class="section-num">2</div>
      <div><h2>Contact &amp; Parent Details</h2><p>For updates, schedule &amp; fee receipts</p></div>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Student Mobile <span class="req">*</span></label>
        <input type="tel" name="stuMobile" placeholder="10-digit number" maxlength="10" inputmode="numeric" pattern="[6-9][0-9]{9}" value="<%= pv(request,"stuMobile",preMobile) %>" required/>
      </div>
      <div class="field">
        <label>Alternate Mobile <span class="req">*</span></label>
        <input type="tel" name="altMobile" placeholder="10-digit number" maxlength="10" inputmode="numeric" pattern="[6-9][0-9]{9}" value="<%= pv(request,"altMobile",preParentMobile) %>" required/>
      </div>
    </div>
    <div class="field">
      <label>Student Email</label>
      <input type="email" name="stuEmail" placeholder="For DPPs &amp; timetable" autocomplete="email" value="<%= pv(request,"stuEmail",preEmail) %>"/>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Parent / Guardian Name <span class="req">*</span></label>
        <input type="text" name="parentName" placeholder="Full name" value="<%= pv(request,"parentName",preParentName) %>" required/>
      </div>
      <div class="field">
        <label>Parent Mobile <span class="req">*</span></label>
        <input type="tel" name="parentMobile" placeholder="10-digit number" maxlength="10" inputmode="numeric" pattern="[6-9][0-9]{9}" value="<%= pv(request,"parentMobile",preParentMobile) %>" required/>
      </div>
    </div>
    <div class="field">
      <label>Residential Address</label>
      <input type="text" name="address" placeholder="Area / Society name is enough" autocomplete="street-address" value="<%= pv(request,"address",preAddress) %>"/>
    </div>
  </div>

  <!-- 3 Batch & branch -->
  <div class="section-card">
    <div class="section-header">
      <div class="section-num">3</div>
      <div><h2>Batch Allocation</h2><p>Which batch and branch the student joins</p></div>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Batch</label>
        <select name="batchName">
          <option value="">Not allocated yet</option>
          <% if (batchList != null) for (String b : batchList) { %>
            <option <%= pSel(request,"batchName",b) %> <%= selOpt(b, preBatch) %>><%= attr(b) %></option>
          <% } %>
        </select>
      </div>
      <div class="field">
        <label>Branch</label>
        <input type="text" name="branch" placeholder="Branch / centre" value="<%= pv(request,"branch",preBranch) %>"/>
      </div>
    </div>
  </div>

  <!-- 4 Documents -->
  <div class="section-card">
    <div class="section-header">
      <div class="section-num">4</div>
      <div><h2>Photo &amp; ID Proof</h2><p>Passport-size photo, plus Aadhaar / school ID</p></div>
    </div>
    <div class="photo-upload-area" onclick="document.getElementById('photoInput').click()">
      <input type="file" id="photoInput" name="photo" accept="image/*" onchange="previewPhoto(event)"/>
      <div class="photo-icon">📷</div>
      <strong>Tap to upload photo</strong>
      <p>JPG or PNG · max 4 MB</p>
      <img id="previewImg" src="" alt="Preview"/>
    </div>
    <div class="field" style="margin-top:14px;">
      <label>ID Proof (optional)</label>
      <input type="file" name="idProof" accept="image/*,application/pdf"/>
      <p style="font-size:12px;color:var(--muted);margin-top:5px;">Aadhaar, school ID or birth certificate · JPG, PNG or PDF · max 4 MB</p>
    </div>
  </div>

  <!-- 5 Additional documents -->
  <div class="section-card">
    <div class="section-header">
      <div class="section-num">5</div>
      <div><h2>Additional Documents</h2><p>Anything else the family has brought — all optional</p></div>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Document 1</label>
        <input type="file" name="doc1" accept="image/*,application/pdf"/>
      </div>
      <div class="field">
        <label>Document 2</label>
        <input type="file" name="doc2" accept="image/*,application/pdf"/>
      </div>
    </div>
    <div class="field-row">
      <div class="field">
        <label>Document 3</label>
        <input type="file" name="doc3" accept="image/*,application/pdf"/>
      </div>
      <div class="field">
        <label>Document 4</label>
        <input type="file" name="doc4" accept="image/*,application/pdf"/>
      </div>
    </div>
    <p style="font-size:12px;color:var(--muted);margin-top:5px;">JPG, PNG or PDF · max 4 MB each</p>
  </div>

  <!-- 6 Course & Fee Plan -->
  <div class="section-card">
    <div class="section-header">
      <div class="section-num">6</div>
      <div><h2>Course &amp; Fee Plan</h2><p>Pick the programme — the fee and instalments follow from it</p></div>
    </div>

    <div class="field">
      <label>Programme &amp; Batch <span class="req">*</span></label>
      <select name="planCode" id="planCode" required onchange="renderPlan()">
        <option value="">Select a course</option>
        <%
           String selPlan = pv(request, "planCode", "");
           String lastMode = "";
           if (plans != null) for (com.tution.model.FeePlan fp : plans) {
             if (!fp.getMode().equals(lastMode)) {
               if (!lastMode.isEmpty()) { %></optgroup><% }
               lastMode = fp.getMode(); %>
               <optgroup label="<%= attr(modeLabel(lastMode)) %>">
        <%   }
        %>
          <option value="<%= attr(fp.getCode()) %>"
                  data-reg="<%= fp.getRegistrationFee() %>"
                  data-fee="<%= fp.getCourseFee() %>"
                  data-gstinc="<%= fp.isGstInclusive() ? 1 : 0 %>"
                  data-pp="<%= attr(fp.getPpPattern()) %>"
                  data-months="<%= attr(fp.getDueMonths()) %>"
                  data-years="<%= fp.getDurationYears() %>"
                  <%= fp.getCode().equals(selPlan) ? "selected" : "" %>><%= attr(fp.getLabel()) %></option>
        <% } if (!lastMode.isEmpty()) { %></optgroup><% } %>
      </select>
      <p class="submit-note" id="planEligibility" style="text-align:left;margin-top:6px;"></p>
    </div>

    <div class="field-row">
      <div class="field">
        <label>Batch Start Date <span class="req">*</span></label>
        <input type="date" name="batchStartDate" id="batchStartDate" required
               value="<%= pv(request,"batchStartDate", java.time.LocalDate.now().toString()) %>"
               onchange="renderPlan()"/>
      </div>
      <div class="field">
        <label>Branch</label>
        <input type="text" name="branch" placeholder="Branch / centre" value="<%= pv(request,"branch",preBranch) %>"/>
      </div>
    </div>

    <div class="fee-fixed-block" id="feeBreakdown" style="display:none;">
      <div class="fee-fixed-title">Fee Breakdown</div>
      <div class="fee-fixed-row"><span>Registration Fee <small>(non-refundable, one-time)</small></span><span class="fee-val" id="brReg">&#8377;0</span></div>
      <div class="fee-fixed-row"><span>Course Fee</span><span class="fee-val" id="brCourse">&#8377;0</span></div>
      <div class="fee-fixed-row" id="brGstRow"><span>GST @ <span id="brGstRate">0</span>%</span><span class="fee-val" id="brGst">&#8377;0</span></div>
    </div>
    <div class="grand-total-box" id="feeTotalBox" style="display:none;">
      <div class="gt-label">Total Payable</div>
      <div class="gt-amount" id="brTotal">&#8377;0</div>
      <div class="gt-break" id="brBreak"></div>
    </div>

    <div class="inst-wrap" id="instWrap" style="display:none;">
      <table class="inst-table">
        <thead><tr><th>#</th><th>Instalment</th><th>Due Date</th><th style="text-align:right;">Amount</th></tr></thead>
        <tbody id="instTableBody"></tbody>
      </table>
      <p class="submit-note" style="text-align:left;">Registration is payable at admission and is not part of the part-payment split.</p>
    </div>
  </div>
<div class="section-card">
    <div class="section-header">
      <div class="section-num">7</div>
      <div><h2>Declaration</h2><p>Please read and confirm before submitting</p></div>
    </div>
    <div class="declaration-box">
      I declare that all information provided is true and accurate. I agree to abide by the rules of Havellsson NEET Samrat,
      attend classes regularly, and complete all assigned tests and DPPs. I understand that fees paid are non-refundable
      unless the batch is cancelled by the institute. I consent to receive SMS/WhatsApp updates regarding schedule and results.
    </div>
    <div class="check-row">
      <input type="checkbox" id="declare" name="declare" <%= pChk(request,"declare","on") %>/>
      <label for="declare">I have read and agree to the declaration and fee policy of Havellsson NEET Samrat.</label>
    </div>
  </div>

  <div class="submit-area">
    <button type="submit" class="btn-submit">Submit Admission Form →</button>
    <p class="submit-note">The admission number will be generated automatically on save.</p>
  </div>

</form>
</div>

<script>
// Every figure here mirrors what FeeService computes server-side: registration
// is a separate one-time charge, GST applies to the course fee only, and the
// part-payment percentages fall on the plan's fixed calendar months.
const GST_RATE = <%= gstRate %>;
const MONTHS = { JAN:0, FEB:1, MAR:2, APR:3, MAY:4, JUN:5, JUL:6,
                 AUG:7, SEP:8, OCT:9, NOV:10, DEC:11 };

function fmt(n){ return '\u20B9' + Math.round(n).toLocaleString('en-IN'); }
function fmtDate(d){ return d.toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' }); }

/** Next occurrence of a month strictly after `after`, keeping the start day. */
function nextOccurrence(after, month, day){
  let year = after.getFullYear();
  for (let g = 0; g < 4; g++){
    const last = new Date(year, month + 1, 0).getDate();
    const cand = new Date(year, month, Math.min(day, last));
    if (cand > after) return cand;
    year++;
  }
  return after;
}

function renderPlan(){
  const sel = document.getElementById('planCode');
  const opt = sel.options[sel.selectedIndex];
  const box = document.getElementById('feeBreakdown');
  const totBox = document.getElementById('feeTotalBox');
  const instWrap = document.getElementById('instWrap');
  if (!opt || !opt.value){
    box.style.display = totBox.style.display = instWrap.style.display = 'none';
    return;
  }

  const reg    = parseInt(opt.dataset.reg, 10) || 0;
  const course = parseInt(opt.dataset.fee, 10) || 0;
  const gstInc = opt.dataset.gstinc === '1';
  const rate   = gstInc ? 0 : GST_RATE;
  const gst    = Math.round(course * rate / 100);
  const total  = reg + course + gst;

  document.getElementById('brReg').textContent    = fmt(reg);
  document.getElementById('brCourse').textContent = fmt(course);
  document.getElementById('brGst').textContent    = fmt(gst);
  document.getElementById('brGstRate').textContent = rate;
  document.getElementById('brGstRow').style.display = gstInc ? 'none' : '';
  document.getElementById('brTotal').textContent  = fmt(total);
  document.getElementById('brBreak').textContent  =
      fmt(reg) + ' registration + ' + fmt(course) + ' course'
      + (gstInc ? ' (GST included)' : ' + ' + fmt(gst) + ' GST');
  box.style.display = totBox.style.display = 'block';

  // instalments
  const pct    = (opt.dataset.pp || '100').split(',').map(Number);
  const tokens = (opt.dataset.months || 'START').split(',');
  const startStr = document.getElementById('batchStartDate').value;
  const start = startStr ? new Date(startStr + 'T00:00:00') : new Date();

  const payable = course + gst;
  const amounts = pct.map(p => Math.floor(payable * p / 100));
  amounts[0] += payable - amounts.reduce((a, b) => a + b, 0);

  let prev = start;
  const rows = [];
  rows.push(['R', 'Registration (non-refundable)', start, reg]);
  for (let i = 0; i < amounts.length; i++){
    let due;
    if (i === 0 || tokens[i] === 'START'){ due = start; }
    else {
      const m = MONTHS[(tokens[i] || '').trim().toUpperCase()];
      due = (m === undefined) ? new Date(prev.getFullYear(), prev.getMonth() + 3, prev.getDate())
                              : nextOccurrence(prev, m, start.getDate());
    }
    prev = due;
    rows.push([i + 1, 'Part payment ' + (i + 1) + ' of ' + amounts.length + ' (' + pct[i] + '%)',
               due, amounts[i]]);
  }

  const body = document.getElementById('instTableBody');
  body.innerHTML = '';
  rows.forEach(function(r){
    if (r[3] <= 0) return;
    const tr = document.createElement('tr');
    tr.innerHTML = '<td>' + r[0] + '</td><td>' + r[1] + '</td><td>' + fmtDate(r[2])
                 + '</td><td style="text-align:right;">' + fmt(r[3]) + '</td>';
    body.appendChild(tr);
  });
  instWrap.style.display = 'block';

  const el = document.getElementById('planEligibility');
  if (el) el.textContent = 'Duration: ' + opt.dataset.years
        + (opt.dataset.years === '1' ? ' year' : ' years');
}

renderPlan();</script>
<% } %>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="students"/></jsp:include>

</body>
</html>
