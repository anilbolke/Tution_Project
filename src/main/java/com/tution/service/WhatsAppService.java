package com.tution.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tution.util.WhatsAppConfig;

/**
 * Builds WhatsApp notification messages (3 templates) and either a click-to-chat
 * link (default) or sends via the Meta Cloud API (when configured).
 * Uses only the JDK — no SDK / extra JARs.
 */
public class WhatsAppService {

    private static final String BRAND = "Havellsson NEET Samrat";
    private static final Logger LOG = Logger.getLogger(WhatsAppService.class.getName());

    /** Outcome of the last API send on this service instance (null until a send is attempted). */
    private SendResult lastResult;
    public SendResult getLastResult() { return lastResult; }

    /** Captured response of a single send (what the gateway returned synchronously). */
    public static class SendResult {
        public boolean accepted;   // gateway took the message (HTTP < 400, no error in body)
        public int     httpCode;
        public String  messageId;  // provider/Meta message id, if returned
        public String  status;     // status string from the body, if present
        public String  body;       // raw response (truncated)
        public boolean accepted()  { return accepted; }
        public String  messageId() { return messageId; }
    }

    /* ──────────────── TEMPLATES ──────────────── */

    /** 1) Inquiry received — sent to the prospective student's mobile. */
    public String inquiryMessage(String name, String classInterest) {
        String cls = (classInterest == null || classInterest.isEmpty() || "null".equals(classInterest))
                   ? "your selected course" : classInterest;
        return "Hello " + safe(name) + "! 🎓\n\n"
             + "Thank you for your interest in *" + BRAND + "*. "
             + "We've received your inquiry for " + cls + ".\n\n"
             + "Our counselor will call you within 24 hours with batch details, fees, "
             + "and answers to all your questions.\n\n"
             + "For anything urgent, just reply to this message.\n\n"
             + "— Team " + BRAND;
    }

    /** 2) Admission confirmed — sent to the student/parent mobile. */
    public String admissionMessage(String name, String admissionNo, String className, String slab) {
        return "Congratulations " + safe(name) + "! 🎉\n\n"
             + "Your admission to *" + BRAND + "* is confirmed.\n\n"
             + "📋 Admission No: " + safe(admissionNo) + "\n"
             + "🎓 Class: " + nn(className) + "\n"
             + "💳 Fee Plan: " + nn(slab) + "\n\n"
             + "Please save your admission number for all future reference. "
             + "Your batch schedule will be shared with you shortly.\n\n"
             + "Welcome to the Havellsson family! 🏆\n"
             + "— Team " + BRAND;
    }

    /** 3) Payment success — sent to the student/parent mobile. */
    public String paymentMessage(String name, String receiptNo, String amount,
                                 String date, String mode, String balance) {
        return "Payment Received ✅\n\n"
             + "Dear " + safe(name) + ", we've successfully received your fee payment.\n\n"
             + "🧾 Receipt No: " + safe(receiptNo) + "\n"
             + "💰 Amount Paid: " + amount + "\n"
             + "📅 Date: " + safe(date) + "\n"
             + "💳 Mode: " + safe(mode) + "\n"
             + "🔻 Balance Due: " + balance + "\n\n"
             + "Please keep this message as your payment confirmation. Thank you!\n"
             + "— Team " + BRAND;
    }

    /* ──────────────── DELIVERY ──────────────── */

    /** Click-to-chat link (opens WhatsApp with the message pre-filled). */
    public String link(String mobile, String message) {
        return "https://wa.me/" + normalize(mobile) + "?text=" + enc(message);
    }

    /**
     * Sends a plain-text message via the notify24x7 CPaaS WhatsApp API.
     * Returns true on success. NOTE: plain text only works inside the 24h
     * customer-service window — for business-initiated sends use a Meta-approved
     * {@link #sendTemplate}. Only fires when WhatsAppConfig.isApi() is true.
     */
    public boolean sendViaApi
    (String mobile, String message) {
        return post("{\"to\":\"" + normalize(mobile) + "\","
                  + "\"type\":\"text\",\"recipient_type\":\"individual\","
                  + "\"text\":{\"body\":\"" + jsonEscape(message) + "\"}}");
    }

    /* ──────────────── APPROVED TEMPLATES (business-initiated) ──────────────── */

    /**
     * Sends a Meta-approved WhatsApp template with positional body params
     * ({{1}}, {{2}}, …) in the given language. Works outside the 24h window.
     * Returns true on success.
     */
    public boolean sendTemplate(String mobile, String templateName, String lang, String... params) {
        return post(templatePayload(mobile, templateName, lang, params));
    }

    /** Inquiry → student template {@code inquiry_stud} (Marathi): {{1}} name, {{2}} class. */
    public boolean sendInquiry(String mobile, String name, String classInterest) {
        String cls = (classInterest == null || classInterest.isEmpty() || "null".equals(classInterest))
                   ? "your selected course" : classInterest;
        return sendTemplate(mobile, WhatsAppConfig.TPL_INQUIRY, WhatsAppConfig.LANG_INQUIRY, safe(name), cls);
    }

    /** Admission confirmed → template {@code conf_admition}
     *  ({{1}} name, {{2}} admission no, {{3}} class, {{4}} fee plan). */
    public boolean sendAdmission(String mobile, String name, String admissionNo, String className, String slab) {
        return sendTemplate(mobile, WhatsAppConfig.TPL_ADMISSION, WhatsAppConfig.LANG_ADMISSION,
                safe(name), safe(admissionNo), nn(className), nn(slab));
    }

    /** Staff inquiry alert → template {@code inquiry_staff}
     *  ({{1}} date, {{2}} name, {{3}} class, {{4}} student mob, {{5}} parent mob). */
    public boolean sendInquiryStaff(String staffMobile, String date, String name, String className,
                                    String studentMob, String parentMob) {
        return sendTemplate(staffMobile, WhatsAppConfig.TPL_INQUIRY_STAFF, WhatsAppConfig.LANG_INQUIRY_STAFF,
                safe(date), safe(name), nn(className), safe(studentMob), nn(parentMob));
    }

    /** Payment success → template {@code stud_fee_receipt} ({{1}} name … {{6}} balance) with the
     *  receipt PDF attached as the document header. {@code docUrl} must be a PUBLIC URL (WhatsApp
     *  fetches it server-side). Returns false (→ caller falls back to tap-to-send) when it's blank. */
    public boolean sendPayment(String mobile, String name, String receiptNo, String amount,
                               String date, String mode, String balance, String docUrl) {
        if (docUrl == null || docUrl.isEmpty()) return false;
        String header = "{\"type\":\"header\",\"parameters\":[{\"type\":\"document\","
                      + "\"document\":{\"link\":\"" + jsonEscape(docUrl) + "\",\"filename\":\"Receipt-"
                      + jsonEscape(safe(receiptNo)) + ".pdf\"}}]}";
        return post(templatePayloadWithHeader(mobile, WhatsAppConfig.TPL_PAYMENT, WhatsAppConfig.LANG_PAYMENT, header,
                safe(name), safe(receiptNo), amount, safe(date), safe(mode), balance));
    }

    /**
     * Fetches the list of approved templates from the gateway
     * (POST {@code /getTemplateList}). Returns the raw JSON response, or "" when
     * the API isn't configured / the call fails. Handy to confirm the exact
     * approved template names + languages before wiring sends.
     */
    public String getTemplateList() {
        if (!WhatsAppConfig.isApi()) return "";
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(WhatsAppConfig.API_TEMPLATES_URL).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("wabaNumber", WhatsAppConfig.WABA_NUMBER);
            con.setRequestProperty("Key", WhatsAppConfig.API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) { os.write("{}".getBytes(StandardCharsets.UTF_8)); }
            int code = con.getResponseCode();
            String body = readBody(code < 400 ? con.getInputStream() : con.getErrorStream());
            LOG.info("WhatsApp getTemplateList → HTTP " + code + " | " + truncate(body, 1500));
            return body;
        } catch (Exception e) {
            LOG.warning("getTemplateList error: " + e);
            return "";
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** Builds the gateway "template" JSON body (body params only). */
    String templatePayload(String mobile, String templateName, String lang, String... params) {
        return templatePayloadWithHeader(mobile, templateName, lang, null, params);
    }

    /** Builds the "template" JSON body, optionally prefixing a ready header-component JSON
     *  (e.g. a document/image header). Empty body params become "-" (Meta rejects blanks). */
    String templatePayloadWithHeader(String mobile, String templateName, String lang,
                                     String headerComponent, String... params) {
        StringBuilder ps = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) ps.append(",");
            String v = (params[i] == null || params[i].isEmpty()) ? "-" : params[i];
            ps.append("{\"type\":\"text\",\"text\":\"").append(jsonEscape(v)).append("\"}");
        }
        StringBuilder comps = new StringBuilder("[");
        if (headerComponent != null && !headerComponent.isEmpty()) comps.append(headerComponent).append(",");
        if (params.length > 0) comps.append("{\"type\":\"body\",\"parameters\":[").append(ps).append("]}");
        // strip a dangling comma if there were no body params
        if (comps.charAt(comps.length() - 1) == ',') comps.setLength(comps.length() - 1);
        comps.append("]");
        return "{\"to\":\"" + normalize(mobile) + "\","
             + "\"type\":\"template\","
             + "\"template\":{"
             +   "\"language\":{\"policy\":\"deterministic\",\"code\":\"" + lang + "\"},"
             +   "\"name\":\"" + templateName + "\","
             +   "\"components\":" + comps
             + "}}";
    }

    /**
     * POSTs a ready JSON body to the CPaaS endpoint, then CAPTURES and LOGS the
     * gateway's response (HTTP code + message id + status + raw body). The parsed
     * outcome is kept in {@link #getLastResult()}. Returns true only when accepted
     * (HTTP &lt; 400 and no "error" in the body). No-op unless the API is configured.
     *
     * NOTE: "accepted" means the gateway queued it — actual delivered/read status
     * arrives later via provider webhooks, not in this response.
     */
    private boolean post(String jsonBody) {
        if (!WhatsAppConfig.isApi()) return false;
        SendResult r = new SendResult();
        lastResult = r;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(WhatsAppConfig.API_URL).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("wabaNumber", WhatsAppConfig.WABA_NUMBER);
            con.setRequestProperty("Key", WhatsAppConfig.API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            r.httpCode  = con.getResponseCode();
            String body = readBody(r.httpCode < 400 ? con.getInputStream() : con.getErrorStream());
            r.body      = truncate(body, 800);
            r.messageId = firstMatch(body, "(?:\"id\"|\"messageId\"|\"message_id\")\\s*:\\s*\"([^\"]+)\"");
            // The gateway reports failures as HTTP 200 with an error body, in TWO shapes:
            //   mmtpro's own:  [{"errorDescription":"…","errorCode":N}]
            //   Meta's, passed straight through:
            //                  {"error":{"message":"(#132012) …","code":132012,…}}
            // Only the first was detected, so a rejected send was logged as ACCEPTED and
            // callers went on to mark it sent (payments.wa_sent = 1) with nothing delivered.
            String errDesc  = firstMatch(body, "\"errorDescription\"\\s*:\\s*\"([^\"]+)\"");
            boolean metaErr = Pattern.compile("\"error\"\\s*:\\s*\\{").matcher(body).find();
            if (isBlank(errDesc) && metaErr) errDesc = firstMatch(body, "\"message\"\\s*:\\s*\"([^\"]+)\"");
            boolean hasError = !isBlank(errDesc) || metaErr || body.toLowerCase().contains("\"errorcode\"");
            r.status    = !isBlank(errDesc) ? errDesc
                          : firstMatch(body, "\"(?:message_status|status)\"\\s*:\\s*\"([^\"]+)\"");
            // A real send always comes back with a wamid; no id means nothing was queued.
            r.accepted  = r.httpCode < 400 && !hasError && !isBlank(r.messageId);

            String line = "WhatsApp send → HTTP " + r.httpCode + (r.accepted ? " ACCEPTED" : " FAILED")
                        + (isBlank(r.messageId) ? "" : " id=" + r.messageId)
                        + (isBlank(r.status)    ? "" : " status=" + r.status)
                        + " | resp=" + r.body;
            if (r.accepted) LOG.info(line); else LOG.warning(line);
            return r.accepted;
        } catch (Exception e) {
            r.accepted = false;
            r.body = "exception: " + e.getMessage();
            LOG.warning("WhatsApp send error: " + e);
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private String readBody(InputStream in) {
        if (in == null) return "";
        try (InputStream s = in) { return new String(s.readAllBytes(), StandardCharsets.UTF_8).trim(); }
        catch (Exception e) { return ""; }
    }
    private String firstMatch(String s, String regex) {
        if (s == null) return "";
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : "";
    }
    private boolean isBlank(String s)         { return s == null || s.isEmpty(); }
    private String  truncate(String s, int n) { return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "…"); }

    /* ──────────────── helpers ──────────────── */

    private String normalize(String mobile) {
        String m = (mobile == null) ? "" : mobile.replaceAll("[^0-9]", "");
        if (m.length() == 10) m = WhatsAppConfig.COUNTRY_CODE + m;
        return m;
    }
    private String enc(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return ""; }
    }
    private String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    private String safe(String s) { return s == null ? "" : s; }
    private String nn(String s)   { return (s == null || s.isEmpty() || "null".equals(s)) ? "-" : s; }
}
