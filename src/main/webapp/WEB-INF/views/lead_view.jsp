<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map, com.tution.model.Inquiry, com.tution.model.User,
                 com.tution.model.LeadFollowup, com.tution.model.LeadDemo, com.tution.util.Dates" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String ctx = request.getContextPath();
    Inquiry lead = (Inquiry) request.getAttribute("lead");
    String error = (String) request.getAttribute("error");
    String msg   = request.getParameter("msg");
    @SuppressWarnings("unchecked") Map<Integer,String> counsellors =
        (Map<Integer,String>) request.getAttribute("counsellors");
    @SuppressWarnings("unchecked") List<LeadFollowup> followups =
        (List<LeadFollowup>) request.getAttribute("followups");
    @SuppressWarnings("unchecked") List<LeadDemo> demos =
        (List<LeadDemo>) request.getAttribute("demos");
    String today = java.time.LocalDate.now().toString();
    int fCount = (followups == null) ? 0 : followups.size();
    int dCount = (demos == null) ? 0 : demos.size();
%>
<%!
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private String d(String s) { return (s == null || s.isEmpty()) ? "—" : esc(s); }
    private String sel(String current, String option) {
        return option.equals(current) ? " selected" : "";
    }
    private String badgeClass(String status) {
        if (status == null) return "st-new";
        switch (status) {
            case "CONVERTED":         return "st-converted";
            case "CONTACTED":         return "st-contacted";
            case "INTERESTED":        return "st-interested";
            case "DEMO_PENDING":
            case "DEMO_COMPLETED":    return "st-demo";
            case "FOLLOWUP_REQUIRED": return "st-followup";
            case "NOT_INTERESTED":
            case "LOST":              return "st-lost";
            default:                  return "st-new";
        }
    }
    private String prioClass(String p) {
        if ("Hot".equals(p))  return "pr-hot";
        if ("Cold".equals(p)) return "pr-cold";
        return "pr-warm";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title><%= lead == null ? "Lead" : esc(lead.getFullName()) %> – Havellsson NEET Samrat</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@700;900&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%= ctx %>/css/style.css?v=<%= com.tution.util.Assets.version(application) %>">
  <style>
    .lead-head { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:22px 24px; margin-bottom:18px; display:flex; justify-content:space-between;
      align-items:flex-start; gap:16px; flex-wrap:wrap; }
    .lead-head h2 { font-family:'Playfair Display',serif; font-size:25px; color:var(--green-dark); }
    .chips { display:flex; gap:8px; flex-wrap:wrap; margin-top:9px; }
    .badge { display:inline-block; font-size:10.5px; font-weight:800; padding:4px 11px; border-radius:12px;
      text-transform:uppercase; letter-spacing:0.4px; }
    .st-new { background:#FFF4D6; color:#9A6B00; }
    .st-contacted { background:#DDEBFF; color:#1B4F9C; }
    .st-interested { background:#E4DDFF; color:#4B2E9C; }
    .st-demo { background:#D9F2F7; color:#0F6C7E; }
    .st-followup { background:#FFE6D6; color:#9C4A16; }
    .st-converted { background:var(--green-light); color:var(--success); }
    .st-lost { background:#EEE; color:#666; }
    .pr-hot { background:#FDE2E0; color:#C0392B; }
    .pr-warm { background:#FFF4D6; color:#9A6B00; }
    .pr-cold { background:#E6F0FF; color:#1B4F9C; }
    .overdue-chip { background:#FDE2E0; color:#C0392B; }

    .head-actions { display:flex; gap:9px; flex-wrap:wrap; }
    .btn { display:inline-block; text-decoration:none; font-size:13px; font-weight:700; padding:10px 18px;
      border-radius:8px; border:none; cursor:pointer; font-family:inherit; white-space:nowrap; }
    .btn-primary { background:var(--green); color:#fff; }
    .btn-primary:hover { background:var(--green-dark); }
    .btn-ghost { background:none; border:1.5px solid var(--border); color:var(--muted); }

    .cols { display:grid; grid-template-columns:2fr 1fr; gap:18px; align-items:start; }
    @media(max-width:900px){ .cols { grid-template-columns:1fr; } }

    .card { background:var(--white); border-radius:var(--radius); box-shadow:var(--shadow);
      padding:20px 22px; margin-bottom:18px; }
    .card h3 { font-size:12px; text-transform:uppercase; letter-spacing:0.6px; color:var(--green-dark);
      padding-bottom:9px; border-bottom:2px solid var(--green-light); margin-bottom:14px; }
    .kv { display:grid; grid-template-columns:repeat(2,1fr); gap:12px 20px; }
    @media(max-width:620px){ .kv { grid-template-columns:1fr; } }
    .kv .k { font-size:10.5px; text-transform:uppercase; letter-spacing:0.3px; color:var(--muted);
      font-weight:700; margin-bottom:2px; }
    .kv .val { font-size:13.5px; color:var(--text); }
    .kv .full { grid-column:1 / -1; }

    .fld { display:flex; flex-direction:column; margin-bottom:12px; }
    .fld label { font-size:11px; font-weight:700; color:var(--text); text-transform:uppercase;
      letter-spacing:0.3px; margin-bottom:5px; }
    .fld input, .fld select, .fld textarea { padding:9px 11px; border:1.5px solid var(--border);
      border-radius:8px; font-size:13px; font-family:inherit; }
    .fld textarea { resize:vertical; min-height:58px; }

    .alert { padding:12px 14px; border-radius:8px; font-size:13px; margin-bottom:18px; }
    .alert.error { background:#FDECEA; color:#C0392B; border:1px solid #F5C6CB; }
    .alert.ok { background:var(--green-light); color:var(--success); border:1px solid #BFE3CE; }
    .soon { font-size:12.5px; color:var(--muted); font-style:italic; }

    .card h3 .cnt { background:var(--green-light); color:var(--green-dark); border-radius:20px;
      padding:2px 9px; font-size:10.5px; margin-left:7px; }

    /* Follow-up timeline: a vertical rail with one node per touchpoint. */
    .tl { list-style:none; position:relative; padding-left:26px; }
    .tl::before { content:""; position:absolute; left:9px; top:5px; bottom:5px; width:2px;
      background:var(--green-light); }
    .tl li { position:relative; padding:0 0 16px 0; }
    .tl li:last-child { padding-bottom:0; }
    .tl .dot { position:absolute; left:-26px; top:0; width:20px; height:20px; border-radius:50%;
      background:var(--white); border:2px solid var(--green-light); display:flex;
      align-items:center; justify-content:center; font-size:10px; }
    .tl .when { font-size:11px; color:var(--muted); }
    .tl .who { font-size:12px; font-weight:700; color:var(--green-dark); }
    .tl .txt { font-size:13px; margin-top:3px; white-space:pre-wrap; }
    .tl .obj { font-size:12px; margin-top:4px; color:#9C4A16; background:#FFF3EA;
      border-radius:6px; padding:5px 8px; }
    .tl .meta { font-size:11px; color:var(--muted); margin-top:5px; }

    .demo-row { border:1.5px solid var(--border); border-radius:9px; padding:12px 14px; margin-bottom:10px; }
    .demo-row:last-child { margin-bottom:0; }
    .demo-row .top { display:flex; justify-content:space-between; align-items:center;
      gap:10px; flex-wrap:wrap; }
    .demo-row .dt { font-weight:700; color:var(--green-dark); font-size:13.5px; }
    .demo-row .sub { font-size:12px; color:var(--muted); margin-top:3px; }
    .demo-row .fb { font-size:12.5px; margin-top:7px; white-space:pre-wrap; }
    .demo-row .stars { color:#D69E00; letter-spacing:1px; }
    .dm-SCHEDULED { background:#D9F2F7; color:#0F6C7E; }
    .dm-COMPLETED { background:var(--green-light); color:var(--success); }
    .dm-NO_SHOW { background:#FDE2E0; color:#C0392B; }
    .dm-CANCELLED { background:#EEE; color:#666; }
    .demo-row.missed { border-color:#F0B4AE; background:#FFF7F6; }

    details.inline > summary { cursor:pointer; font-size:12.5px; font-weight:700;
      color:var(--green-dark); padding:7px 0; list-style:none; }
    details.inline > summary::-webkit-details-marker { display:none; }
    details.inline > summary::before { content:"▸ "; }
    details.inline[open] > summary::before { content:"▾ "; }
    .row2 { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
  </style>
</head>
<body>

<jsp:include page="/WEB-INF/views/staff_header.jsp"><jsp:param name="active" value="leads"/></jsp:include>

<div class="neet-ribbon">🏆 Havellsson NEET Samrat — India's Trusted NEET Coaching</div>

<div class="page-body sp-body">

  <% if (error != null) { %>
    <div class="alert error"><%= esc(error) %></div>
  <% } %>

  <% if (lead == null) { %>
    <div class="card"><p>Lead not found. <a href="<%= ctx %>/inquiries">Back to the list →</a></p></div>
  <% } else { %>

    <% if (msg != null) { %>
      <div class="alert <%= ("error".equals(msg) || "demodate".equals(msg)) ? "error" : "ok" %>"><%=
          "created".equals(msg)  ? "Enquiry created." :
          "saved".equals(msg)    ? "Changes saved." :
          "status".equals(msg)   ? "Status updated." :
          "assigned".equals(msg) ? "Counsellor assigned — a WhatsApp alert was sent if a mobile is on file." :
          "logged".equals(msg)   ? "Follow-up recorded." :
          "demo".equals(msg)     ? "Demo updated." :
          "demodate".equals(msg) ? "Please pick a demo date." :
          "error".equals(msg)    ? "Something went wrong. Please try again." : "Done."
      %></div>
    <% } %>

    <div class="lead-head">
      <div>
        <h2><%= esc(lead.getFullName()) %></h2>
        <div class="chips">
          <span class="badge <%= badgeClass(lead.getStatus()) %>"><%= esc(lead.getStatus()) %></span>
          <span class="badge <%= prioClass(lead.getPriority()) %>"><%=
            d(lead.getLeadStage() == null || lead.getLeadStage().isEmpty()
              ? lead.getPriority() : lead.getLeadStage()) %></span>
        <% if (lead.getLeadSubStage() != null && !lead.getLeadSubStage().isEmpty()) { %>
          <span class="badge st-new"><%= esc(lead.getLeadSubStage()) %></span>
        <% } %>
          <% if (lead.isOverdue(today)) { %>
            <span class="badge overdue-chip">Follow-up overdue</span>
          <% } %>
          <% if (lead.isConverted() && lead.getConvertedAdmissionNo() != null) { %>
            <span class="badge st-converted">✓ <%= esc(lead.getConvertedAdmissionNo()) %></span>
          <% } %>
        </div>
      </div>
      <div class="head-actions">
        <a class="btn btn-ghost" href="<%= ctx %>/inquiries">← All Leads</a>
        <a class="btn btn-ghost" href="<%= ctx %>/lead?id=<%= lead.getInquiryId() %>&edit=1">✎ Edit</a>
        <% if (!lead.isConverted()) { %>
          <a class="btn btn-primary" href="<%= ctx %>/admission.jsp?inquiryId=<%= lead.getInquiryId() %>">Convert to Admission →</a>
        <% } %>
      </div>
    </div>

    <div class="cols">
      <div>
        <div class="card">
          <h3>Student Details</h3>
          <div class="kv">
            <div><div class="k">Mobile</div><div class="val"><%= d(lead.getMobile()) %></div></div>
            <div><div class="k">Parent Mobile</div><div class="val"><%= d(lead.getParentMobile()) %></div></div>
            <div><div class="k">Parent / Guardian</div><div class="val"><%= d(lead.getParentName()) %></div></div>
            <div><div class="k">Email</div><div class="val"><%= d(lead.getEmail()) %></div></div>
            <div><div class="k">Date of Birth</div><div class="val"><%= d(lead.getDob()) %></div></div>
            <div><div class="k">Gender</div><div class="val"><%= d(lead.getGender()) %></div></div>
            <div><div class="k">City</div><div class="val"><%= d(lead.getCity()) %></div></div>
            <div class="full"><div class="k">Address</div><div class="val"><%= d(lead.getAddress()) %></div></div>
          </div>
        </div>

        <div class="card">
          <h3>Academic Details</h3>
          <div class="kv">
            <div><div class="k">Current Class</div><div class="val"><%= d(lead.getCurrentClass()) %></div></div>
            <div><div class="k">Previous Qualification</div><div class="val"><%= d(lead.getPrevQualification()) %></div></div>
            <div><div class="k">School / College</div><div class="val"><%= d(lead.getSchoolName()) %></div></div>
            <div><div class="k">Board</div><div class="val"><%= d(lead.getBoard()) %></div></div>
            <div><div class="k">Percentage</div><div class="val"><%= d(lead.getPercentage()) %></div></div>
          </div>
        </div>

        <div class="card">
          <h3>Course Interest</h3>
          <div class="kv">
            <div><div class="k">Course</div><div class="val"><%= d(lead.getCourseName() != null ? lead.getCourseName() : lead.getClassInterest()) %></div></div>
            <div><div class="k">Batch Preference</div><div class="val"><%= d(lead.getBatchPref()) %></div></div>
            <div><div class="k">Learning Mode</div><div class="val"><%= d(lead.getLearningMode()) %></div></div>
            <div><div class="k">Preferred Branch</div><div class="val"><%= d(lead.getBranch()) %></div></div>
            <div><div class="k">Expected Joining</div><div class="val"><%= d(lead.getExpectedJoinDate()) %></div></div>
          </div>
        </div>

        <div class="card">
          <h3>Counselling Notes</h3>
          <div class="kv">
            <div class="full"><div class="k">Student Requirements</div><div class="val"><%= d(lead.getStudentRequirements()) %></div></div>
            <div class="full"><div class="k">Parent Feedback</div><div class="val"><%= d(lead.getParentFeedback()) %></div></div>
            <div class="full"><div class="k">Counsellor Remarks</div><div class="val"><%= d(lead.getCounsellorRemarks()) %></div></div>
            <div class="full"><div class="k">Enquiry Message</div><div class="val"><%= d(lead.getMessage()) %></div></div>
          </div>
        </div>

        <div class="card">
          <h3>Demos <span class="cnt"><%= dCount %></span></h3>
          <% if (dCount == 0) { %>
            <p class="soon">No demo booked yet.</p>
          <% } else { for (LeadDemo dm : demos) {
                 boolean missed = dm.isMissed(today); %>
            <div class="demo-row<%= missed ? " missed" : "" %>">
              <div class="top">
                <span class="dt"><%= d(dm.getDemoDate()) %><%
                     if (dm.getDemoTime() != null && !dm.getDemoTime().isEmpty()) { %> · <%= esc(dm.getDemoTime()) %><% } %></span>
                <span class="badge dm-<%= esc(dm.getStatus()) %>"><%= esc(dm.getStatus()) %></span>
              </div>
              <div class="sub">
                <%= d(dm.getSubject()) %> · <%= d(dm.getFacultyName()) %> · <%= d(dm.getMode()) %>
                <% if (missed) { %> · <b style="color:#C0392B;">date passed, no outcome recorded</b><% } %>
              </div>
              <% if (dm.getFeedback() != null && !dm.getFeedback().isEmpty()) { %>
                <div class="fb"><%= esc(dm.getFeedback()) %></div>
              <% } %>
              <% if (dm.getRating() != null) { %>
                <div class="sub stars"><%= dm.getStars() %></div>
              <% } %>

              <% if (dm.isOpen() && !lead.isConverted()) { %>
                <details class="inline">
                  <summary>Record outcome</summary>
                  <form method="post" action="<%= ctx %>/demo">
                    <input type="hidden" name="action" value="feedback">
                    <input type="hidden" name="demoId" value="<%= dm.getDemoId() %>">
                    <div class="row2">
                      <div class="fld">
                        <label>Outcome</label>
                        <select name="demoStatus">
                          <option value="COMPLETED">Attended</option>
                          <option value="NO_SHOW">Did not attend</option>
                          <option value="CANCELLED">Cancelled</option>
                        </select>
                      </div>
                      <div class="fld">
                        <label>Rating</label>
                        <select name="rating">
                          <option value="">—</option>
                          <option value="5">★★★★★</option>
                          <option value="4">★★★★☆</option>
                          <option value="3">★★★☆☆</option>
                          <option value="2">★★☆☆☆</option>
                          <option value="1">★☆☆☆☆</option>
                        </select>
                      </div>
                    </div>
                    <div class="fld">
                      <label>Counsellor Feedback</label>
                      <textarea name="feedback" maxlength="500" placeholder="What did the student and parent say?"></textarea>
                    </div>
                    <div class="fld">
                      <label>Next Follow-up Date &amp; Time</label>
                      <input type="datetime-local" name="nextFollowupDate">
                    </div>
                    <button type="submit" class="btn btn-primary" style="width:100%;">Save Outcome</button>
                  </form>
                </details>
              <% } %>
            </div>
          <% } } %>

          <% if (!lead.isConverted()) { %>
            <details class="inline" style="margin-top:12px;">
              <summary>➕ Schedule a Counsell</summary>
              <form method="post" action="<%= ctx %>/demo">
                <input type="hidden" name="action" value="schedule">
                <input type="hidden" name="inquiryId" value="<%= lead.getInquiryId() %>">
                <div class="row2">
                  <div class="fld">
                    <label>Date</label>
                    <input type="date" name="demoDate" required>
                  </div>
                  <div class="fld">
                    <label>Time</label>
                    <input type="text" name="demoTime" placeholder="e.g. 4:30 PM" maxlength="20">
                  </div>
                  <div class="fld">
                    <label>Subject</label>
                    <input type="text" name="subject" placeholder="e.g. Physics" maxlength="60">
                  </div>
                  <div class="fld">
                    <label>Faculty</label>
                    <input type="text" name="facultyName" maxlength="120">
                  </div>
                  <div class="fld">
                    <label>Mode</label>
                    <select name="mode">
                      <option>Offline</option>
                      <option>Online</option>
                    </select>
                  </div>
                </div>
                <button type="submit" class="btn btn-primary" style="width:100%;margin-top:6px;">Book Demo</button>
              </form>
            </details>
          <% } %>
        </div>

        <div class="card">
          <h3>Follow-up History <span class="cnt"><%= fCount %></span></h3>
          <% if (fCount == 0) { %>
            <p class="soon">No follow-up recorded yet. Use “Log Follow-up” on the right after the first call.</p>
          <% } else { %>
            <ul class="tl">
              <% for (LeadFollowup fu : followups) { %>
                <li>
                  <span class="dot"><%= fu.getIcon() %></span>
                  <div class="when"><%= d(fu.getCreatedAt()) %></div>
                  <div class="who"><%= d(fu.getCounsellorName()) %> · <%= d(fu.getCommType()) %></div>
                  <% if (fu.getDiscussion() != null && !fu.getDiscussion().isEmpty()) { %>
                    <div class="txt"><%= esc(fu.getDiscussion()) %></div>
                  <% } %>
                  <% if (fu.getObjection() != null && !fu.getObjection().isEmpty()) { %>
                    <div class="obj">⚑ Objection: <%= esc(fu.getObjection()) %></div>
                  <% } %>
                  <div class="meta">
                    <% if (fu.getOutcomeStatus() != null && !fu.getOutcomeStatus().isEmpty()) { %>
                      moved to <b><%= esc(fu.getOutcomeStatus()) %></b>
                    <% } %>
                    <% if (fu.getNextActionDate() != null) { %>
                      · next action <b><%= esc(Dates.display(fu.getNextActionDate())) %></b>
                    <% } %>
                  </div>
                </li>
              <% } %>
            </ul>
          <% } %>
        </div>
      </div>

      <div>
        <div class="card">
          <h3>Enquiry</h3>
          <div class="kv" style="grid-template-columns:1fr;">
            <div><div class="k">Lead Source</div><div class="val"><%= d(lead.getSource()) %></div></div>
            <div><div class="k">Enquiry Date</div><div class="val"><%= d(lead.getCreatedAt()) %></div></div>
            <div><div class="k">Assigned Counsellor</div><div class="val"><%= d(lead.getCounsellorName()) %></div></div>
            <div><div class="k">Next Follow-up</div><div class="val"><%= d(Dates.display(lead.getNextFollowupDate())) %></div></div>
          </div>
        </div>

        <% if (!lead.isConverted()) { %>
        <!-- The single most-used action on this page, so it sits at the top of
             the sidebar: log the call and set the next one in one submit. -->
        <div class="card">
          <h3>Log Follow-up</h3>
          <form method="post" action="<%= ctx %>/followup">
            <input type="hidden" name="inquiryId" value="<%= lead.getInquiryId() %>">
            <div class="fld">
              <label>Communication Type</label>
              <select name="commType">
                <option>Call</option>
                <option>WhatsApp</option>
                <option>Email</option>
                <option>Visit</option>
                <option>Demo Discussion</option>
              </select>
            </div>
            <div class="fld">
              <label>Discussion Summary</label>
              <textarea name="discussion" maxlength="2000" required
                        placeholder="What was discussed on this call?"></textarea>
            </div>
            <div class="fld">
              <label>Objection Raised</label>
              <textarea name="objection" maxlength="500"
                        placeholder="Fees too high, distance, timing…"></textarea>
            </div>
            <div class="fld">
              <label>Move Lead To</label>
              <select name="outcomeStatus">
                <option value="">— leave unchanged —</option>
                <option value="CONTACTED">Contacted</option>
                <option value="INTERESTED">Interested</option>
                <option value="DEMO_PENDING">Counsellor Pending</option>
                <option value="DEMO_COMPLETED">Counsellor Completed</option>
                <option value="FOLLOWUP_REQUIRED">Follow-up Required</option>
                <option value="NOT_INTERESTED">Not Interested</option>
                <option value="LOST">Lost</option>
              </select>
            </div>
            <div class="fld">
              <label>Next Action Date &amp; Time</label>
              <input type="datetime-local" name="nextActionDate"
                     value="<%= Dates.forInput(lead.getNextFollowupDate()) %>">
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Save Follow-up</button>
          </form>
        </div>

        <div class="card">
          <h3>Update Status</h3>
          <form method="post" action="<%= ctx %>/lead">
            <input type="hidden" name="action" value="status">
            <input type="hidden" name="inquiryId" value="<%= lead.getInquiryId() %>">
            <div class="fld">
              <label>Move to</label>
              <select name="status">
                <option value="NEW"<%= sel(lead.getStatus(),"NEW") %>>New</option>
                <option value="CONTACTED"<%= sel(lead.getStatus(),"CONTACTED") %>>Contacted</option>
                <option value="INTERESTED"<%= sel(lead.getStatus(),"INTERESTED") %>>Interested</option>
                <option value="DEMO_PENDING"<%= sel(lead.getStatus(),"DEMO_PENDING") %>>Demo Pending</option>
                <option value="DEMO_COMPLETED"<%= sel(lead.getStatus(),"DEMO_COMPLETED") %>>Demo Completed</option>
                <option value="FOLLOWUP_REQUIRED"<%= sel(lead.getStatus(),"FOLLOWUP_REQUIRED") %>>Follow-up Required</option>
                <option value="NOT_INTERESTED"<%= sel(lead.getStatus(),"NOT_INTERESTED") %>>Not Interested</option>
                <option value="LOST"<%= sel(lead.getStatus(),"LOST") %>>Lost</option>
              </select>
            </div>
            <div class="fld">
              <label>Next Follow-up Date &amp; Time</label>
              <input type="datetime-local" name="nextFollowupDate" value="<%= Dates.forInput(lead.getNextFollowupDate()) %>">
            </div>
            <div class="fld">
              <label>Remarks</label>
              <textarea name="remarks" maxlength="500" placeholder="What happened on this call?"></textarea>
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Update</button>
          </form>
        </div>
        <% } %>

        <% if (user.isAdmin()) { %>
        <div class="card">
          <h3>Assign Counsellor</h3>
          <form method="post" action="<%= ctx %>/lead">
            <input type="hidden" name="action" value="assign">
            <input type="hidden" name="inquiryId" value="<%= lead.getInquiryId() %>">
            <div class="fld">
              <label>Counsellor</label>
              <select name="counsellorId">
                <option value="">— Unassigned —</option>
                <% if (counsellors != null) for (Map.Entry<Integer,String> e : counsellors.entrySet()) {
                     boolean s = lead.getCounsellorId() != null && lead.getCounsellorId().intValue() == e.getKey(); %>
                  <option value="<%= e.getKey() %>"<%= s ? " selected" : "" %>><%= esc(e.getValue()) %></option>
                <% } %>
              </select>
            </div>
            <button type="submit" class="btn btn-primary" style="width:100%;">Assign</button>
          </form>
        </div>
        <% } %>
      </div>
    </div>

  <% } %>

</div>

<div class="footer">© 2026 Tuition Management System · Havellsson NEET Samrat</div>

<jsp:include page="/WEB-INF/views/staff_tabbar.jsp"><jsp:param name="active" value="home"/></jsp:include>

</body>
</html>
