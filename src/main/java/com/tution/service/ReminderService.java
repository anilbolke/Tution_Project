package com.tution.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.tution.dao.DemoDAO;
import com.tution.dao.InquiryDAO;
import com.tution.dao.InstallmentDAO;
import com.tution.dao.ReminderLogDAO;
import com.tution.model.FeeInstallment;
import com.tution.model.Inquiry;
import com.tution.model.LeadDemo;
import com.tution.model.Reminder;
import com.tution.util.Dates;
import com.tution.util.ReminderConfig;

/**
 * Builds the reminder queue and sends it.
 *
 * Three kinds of reminder share one pipeline: follow-ups due, demos coming up,
 * and fee instalments approaching their due date. Every send is claimed in
 * reminder_log first, so a second scheduler run — or an impatient click on the
 * Send button — cannot message the same person twice about the same thing.
 */
public class ReminderService {

    private static final Logger LOG = Logger.getLogger(ReminderService.class.getName());

    private final InquiryDAO     inquiryDAO = new InquiryDAO();
    private final DemoDAO        demoDAO    = new DemoDAO();
    private final InstallmentDAO instDAO    = new InstallmentDAO();
    private final ReminderLogDAO logDAO     = new ReminderLogDAO();
    private final WhatsAppService whatsApp  = new WhatsAppService();

    /** Outcome of a run, for logging and for the "N sent" message on screen. */
    public static class RunResult {
        public int considered;
        public int sent;
        public int skipped;      // already logged
        public int failed;
        public int noMobile;
        public int dryRun;       // would have been sent, but DRY_RUN is on

        @Override
        public String toString() {
            return "considered=" + considered + " sent=" + sent + " skipped=" + skipped
                 + " failed=" + failed + " noMobile=" + noMobile + " dryRun=" + dryRun;
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Queue
    // ────────────────────────────────────────────────────────────────

    /**
     * Everything due as of today, in one list, soonest first.
     *
     * Overdue items are included deliberately — a follow-up missed yesterday
     * still needs chasing, and dropping it would quietly lose the lead.
     */
    public List<Reminder> queue() throws SQLException {
        return queue(null);
    }

    /**
     * @param scopeCounsellorId when set, only this counsellor's leads, demos and
     *                          students — their own work queue rather than the
     *                          institute's
     */
    public List<Reminder> queue(Integer scopeCounsellorId) throws SQLException {
        LocalDate today = LocalDate.now();
        List<Reminder> out = new ArrayList<>();
        Set<String> sent = logDAO.sentKeys(today.minusDays(30).toString());

        // ── follow-ups due today or earlier ──
        InquiryDAO.Filter f = new InquiryDAO.Filter();
        f.openOnly   = true;
        f.orderByDue = true;
        f.dueFrom    = "1900-01-01";
        f.dueTo      = today.toString();
        f.scopeCounsellorId = scopeCounsellorId;
        for (Inquiry q : inquiryDAO.find(f)) {
            Reminder r = new Reminder();
            r.setKind("FOLLOWUP");
            r.setRefId(q.getInquiryId());
            r.setInquiryId(Integer.valueOf(q.getInquiryId()));
            r.setName(q.getFullName());
            r.setMobile(q.getMobile());
            r.setParentMobile(q.getParentMobile());
            r.setDueDate(q.getNextFollowupDate());
            r.setCounsellorName(q.getCounsellorName());
            r.setDetail((q.getCourseName() != null && !q.getCourseName().isEmpty())
                        ? q.getCourseName() : q.getClassInterest());
            r.setAlreadySent(sent.contains(key(r)));
            out.add(r);
        }

        // ── demos in the next DEMO_LEAD_DAYS ──
        String demoTo = today.plusDays(ReminderConfig.DEMO_LEAD_DAYS).toString();
        for (LeadDemo d : demoDAO.find(today.toString(), demoTo, "SCHEDULED", scopeCounsellorId)) {
            Reminder r = new Reminder();
            r.setKind("DEMO");
            r.setRefId(d.getDemoId());
            r.setInquiryId(Integer.valueOf(d.getInquiryId()));
            r.setName(d.getLeadName());
            r.setMobile(d.getLeadMobile());
            r.setDueDate(d.getDemoDate());
            r.setCounsellorName(d.getCounsellorName());
            r.setDetail(join(d.getSubject(), d.getDemoTime(), d.getFacultyName()));
            r.setAlreadySent(sent.contains(key(r)));
            out.add(r);
        }

        // ── fee instalments due within FEE_LEAD_DAYS, plus anything overdue ──
        String feeTo = today.plusDays(ReminderConfig.FEE_LEAD_DAYS).toString();
        for (FeeInstallment i : instDAO.findDue(null, feeTo, false, scopeCounsellorId)) {
            Reminder r = new Reminder();
            r.setKind("FEE_DUE");
            r.setRefId(i.getInstallmentId());
            r.setStudentId(Integer.valueOf(i.getStudentId()));
            r.setName(i.getStudentName());
            r.setMobile(i.getStudentMobile());
            r.setParentMobile(i.getParentMobile());
            r.setDueDate(i.getDueDate());
            r.setAmount(i.getBalance());
            r.setDetail(i.getLabel() == null ? i.getAdmissionNo() : i.getLabel());
            r.setAlreadySent(sent.contains(key(r)));
            out.add(r);
        }

        out.sort((a, b) -> {
            String da = a.getDueDate() == null ? "" : a.getDueDate();
            String db = b.getDueDate() == null ? "" : b.getDueDate();
            return da.compareTo(db);
        });
        return out;
    }

    // ────────────────────────────────────────────────────────────────
    //  Send
    // ────────────────────────────────────────────────────────────────

    /**
     * Sends every unsent item in the queue. This is what the scheduler calls.
     *
     * @param kindFilter send only this kind, or null for all
     */
    public RunResult sendAll(String kindFilter) throws SQLException {
        return sendAll(kindFilter, null);
    }

    /** Sends only within one counsellor's scope. */
    public RunResult sendAll(String kindFilter, Integer scopeCounsellorId) throws SQLException {
        RunResult res = new RunResult();
        for (Reminder r : queue(scopeCounsellorId)) {
            if (kindFilter != null && !kindFilter.equals(r.getKind())) {
                continue;
            }
            res.considered++;
            if (r.isAlreadySent()) {
                res.skipped++;
                continue;
            }
            switch (sendOne(r)) {
                case SENT:      res.sent++;     break;
                case DUPLICATE: res.skipped++;  break;
                case NO_MOBILE: res.noMobile++; break;
                case DRY_RUN:   res.dryRun++;   break;
                default:        res.failed++;   break;
            }
        }
        return res;
    }

    /** Sends one reminder chosen by kind + id, used by the manual Send button. */
    public Outcome sendOne(String kind, int refId) throws SQLException {
        return sendOne(kind, refId, null);
    }

    /** Sends one reminder, refusing anything outside the caller's scope. */
    public Outcome sendOne(String kind, int refId, Integer scopeCounsellorId) throws SQLException {
        for (Reminder r : queue(scopeCounsellorId)) {
            if (r.getKind().equals(kind) && r.getRefId() == refId) {
                return sendOne(r);
            }
        }
        return Outcome.NOT_FOUND;
    }

    public enum Outcome { SENT, DUPLICATE, FAILED, NO_MOBILE, NOT_FOUND, DRY_RUN }

    /**
     * Claims the reminder, sends it, then records the outcome.
     *
     * A failed send releases the claim so the item comes back tomorrow — a
     * reminder that arrives late is far better than one silently suppressed.
     */
    private Outcome sendOne(Reminder r) throws SQLException {
        String mobile = r.targetMobile();
        if (mobile.isEmpty()) {
            return Outcome.NO_MOBILE;
        }

        // Dry run: report what WOULD go out, touch nothing. Checked before the
        // claim so a rehearsal cannot suppress the real reminder later.
        if (ReminderConfig.DRY_RUN) {
            LOG.info("DRY RUN — would send " + r.getKind() + " #" + r.getRefId()
                     + " to " + mobile + " (" + r.getName() + ")");
            return Outcome.DRY_RUN;
        }

        if (!logDAO.claim(r.getKind(), r.getRefId(), r.getDueDate(), "WHATSAPP")) {
            return Outcome.DUPLICATE;
        }

        boolean ok;
        String detail;
        String messageId = null;
        try {
            ok = dispatch(r, mobile);
            WhatsAppService.SendResult last = whatsApp.getLastResult();
            messageId = (last == null) ? null : last.messageId;
            detail = (last == null) ? null
                   : ("http=" + last.httpCode + " status=" + last.status + " " + last.body);

            // HTTP 200 on its own is not proof of anything: this gateway returns
            // 200 for some rejections, and an empty 200 body means the message
            // was never queued. Insist on a message id or an explicit accepted
            // status, so a silent non-delivery is retried rather than recorded
            // as a success and never chased again.
            if (ok && isBlank(messageId) && !acceptedStatus(last)) {
                ok = false;
                detail = "no message id in gateway response — treated as failed; " + detail;
                LOG.warning("Reminder " + r.getKind() + " #" + r.getRefId()
                          + ": gateway returned no message id, will retry. " + detail);
            }

            logDAO.complete(r.getKind(), r.getRefId(), r.getDueDate(), "WHATSAPP",
                            ok, messageId, detail);
        } catch (RuntimeException e) {
            LOG.warning("Reminder send threw for " + r.getKind() + " #" + r.getRefId()
                        + ": " + e.getMessage());
            logDAO.release(r.getKind(), r.getRefId(), r.getDueDate(), "WHATSAPP");
            return Outcome.FAILED;
        }

        if (!ok) {
            logDAO.release(r.getKind(), r.getRefId(), r.getDueDate(), "WHATSAPP");
            return Outcome.FAILED;
        }
        markSourceFlag(r);
        return Outcome.SENT;
    }

    private static boolean acceptedStatus(WhatsAppService.SendResult r) {
        if (r == null || r.status == null) {
            return false;
        }
        String s = r.status.toLowerCase();
        return s.contains("accept") || s.contains("sent") || s.contains("queued");
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /**
     * Picks the approved template for the reminder kind and fills it.
     *
     * Fee dues have a purpose-built template. Follow-ups and demos do not, so
     * they go through the institute's generic one-liner with the whole message
     * composed into its second parameter — see {@link #composeMessage}.
     */
    private boolean dispatch(Reminder r, String mobile) {
        if ("FEE_DUE".equals(r.getKind())) {
            // fees_reminder reads:
            //   "pay fee amount Rs.{{1}} of {{2}} before {{3}} for {{4}}"
            // so {{2}} is WHAT the payment is and {{4}} is WHO it is for —
            // getting those the wrong way round produces "Rs.49,088 of Anil
            // ... for Part payment 1 of 3".
            return whatsApp.sendTemplate(mobile,
                ReminderConfig.TPL_FEE_DUE, ReminderConfig.LANG_FEE_DUE,
                String.format("%,d", r.getAmount()),
                nn(r.getDetail()),
                friendlyDate(r.getDueDate()),
                nn(r.getName()));
        }
        // com_1_line: {{1}} name, {{2}} message
        return whatsApp.sendTemplate(mobile,
            ReminderConfig.TPL_GENERIC, ReminderConfig.LANG_GENERIC,
            nn(r.getName()), composeMessage(r));
    }

    /**
     * The body for a reminder that has no dedicated template.
     *
     * Kept to a single line because the generic template renders it as one
     * paragraph, and free of newlines because Meta rejects parameters
     * containing them.
     */
    public static String composeMessage(Reminder r) {
        StringBuilder sb = new StringBuilder();
        if ("DEMO".equals(r.getKind())) {
            sb.append("This is a reminder of your demo class at Havellsson NEET Samrat on ")
              .append(friendlyDate(r.getDueDate()));
            if (r.getDetail() != null && !r.getDetail().isEmpty()) {
                sb.append(" (").append(r.getDetail()).append(')');
            }
            sb.append(". Please reach the centre 10 minutes early.");
        } else {
            sb.append("Our counsellor ");
            if (r.getCounsellorName() != null && !r.getCounsellorName().isEmpty()) {
                sb.append(r.getCounsellorName()).append(' ');
            }
            sb.append("will call you today regarding your enquiry");
            if (r.getDetail() != null && !r.getDetail().isEmpty()) {
                sb.append(" for ").append(r.getDetail());
            }
            sb.append(". Please let us know if another time suits you better.");
        }
        // Meta rejects newlines and tabs inside a parameter.
        return sb.toString().replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    /**
     * Sets the reminder_sent flag on the source row too. reminder_log is the
     * authority, but these flags let the demo and fee screens show at a glance
     * that a nudge has gone out.
     */
    private void markSourceFlag(Reminder r) {
        try {
            if ("DEMO".equals(r.getKind())) {
                demoDAO.markReminderSent(r.getRefId());
            } else if ("FEE_DUE".equals(r.getKind())) {
                instDAO.markReminderSent(r.getRefId());
            }
        } catch (SQLException e) {
            LOG.warning("Could not flag source row for " + r.getKind() + " #" + r.getRefId());
        }
    }

    /** Flags overdue instalments. Runs as part of the nightly job. */
    public int refreshOverdue() throws SQLException {
        return instDAO.markOverdue();
    }

    /**
     * A follow-up's due date now carries a time, but the reminder log is keyed
     * on the DAY — so the key drops the time, matching what sentKeys() reads
     * back out of the log.
     */
    private static String key(Reminder r) {
        String day = Dates.dayOf(r.getDueDate());
        return r.getKind() + ":" + r.getRefId() + ":" + (day == null ? "" : day);
    }

    private static String nn(String s) { return (s == null || s.isEmpty()) ? "-" : s; }

    /**
     * "2026-08-15" becomes "15 Aug 2026".
     *
     * These messages go to parents, so a stored ISO date must never reach them
     * as-is. Anything unparseable is passed through rather than dropped.
     */
    public static String friendlyDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return "-";
        }
        try {
            return java.time.LocalDate.parse(iso.trim())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",
                        java.util.Locale.ENGLISH));
        } catch (Exception e) {
            return iso;
        }
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
