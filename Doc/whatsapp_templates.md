# WhatsApp Templates — Havellsson NEET Samrat

Register these 3 templates in the notify24x7 / Meta WhatsApp Manager panel and wait
for **APPROVED** status before sending business-initiated messages.

- **Language code:** `en`
- **Variables** use `{{1}}`, `{{2}}`, … (must provide a sample for each on submission)
- Variables may not be adjacent and a template may not start/end with a variable
- Send endpoint (per Doc/WhatsappAPIDocument.json):
  `POST https://cpaasreseller.notify24x7.com/REST/directApi/message`
  headers: `wabaNumber`, `Key`, `Content-Type: application/json`

---

## 1. inquiry_received
- **Category:** UTILITY
- **Variables:** `{{1}}` = student name · `{{2}}` = class interested

**Body:**
```
Hello {{1}}! 🎓

Thank you for your interest in Havellsson NEET Samrat. We've received your inquiry for {{2}}.

Our counselor will call you within 24 hours with batch details, fees, and answers to your questions.

— Team Havellsson NEET Samrat
```

**Sample:** `{{1}}` = Priya Deshmukh · `{{2}}` = 12th - Science (PCB)

**Send payload:**
```json
{
  "to": "919812345678",
  "type": "template",
  "template": {
    "language": { "policy": "deterministic", "code": "en" },
    "name": "inquiry_received",
    "components": [
      { "type": "body", "parameters": [
        { "type": "text", "text": "Priya Deshmukh" },
        { "type": "text", "text": "12th - Science (PCB)" }
      ]}
    ]
  }
}
```

---

## 2. admission_confirmed
- **Category:** UTILITY
- **Variables:** `{{1}}` = name · `{{2}}` = admission no · `{{3}}` = class · `{{4}}` = fee plan

**Body:**
```
Congratulations {{1}}! 🎉

Your admission to Havellsson NEET Samrat is confirmed.

Admission No: {{2}}
Class: {{3}}
Fee Plan: {{4}}

Please save your admission number for all future reference. Your batch schedule will be shared shortly.

Welcome to the Havellsson family! 🏆
— Team Havellsson NEET Samrat
```

**Sample:** `{{1}}` = Priya Deshmukh · `{{2}}` = HNS-2627-0481 · `{{3}}` = 12th - Science (PCB) · `{{4}}` = Quarterly

**Send payload:**
```json
{
  "to": "919812345678",
  "type": "template",
  "template": {
    "language": { "policy": "deterministic", "code": "en" },
    "name": "admission_confirmed",
    "components": [
      { "type": "body", "parameters": [
        { "type": "text", "text": "Priya Deshmukh" },
        { "type": "text", "text": "HNS-2627-0481" },
        { "type": "text", "text": "12th - Science (PCB)" },
        { "type": "text", "text": "Quarterly" }
      ]}
    ]
  }
}
```

---

## 3. payment_received
- **Category:** UTILITY
- **Variables:** `{{1}}` = name · `{{2}}` = receipt no · `{{3}}` = amount · `{{4}}` = date · `{{5}}` = mode · `{{6}}` = balance due

**Body:**
```
Payment Received ✅

Dear {{1}}, we've successfully received your fee payment.

Receipt No: {{2}}
Amount Paid: {{3}}
Date: {{4}}
Mode: {{5}}
Balance Due: {{6}}

Please keep this message as your payment confirmation. Thank you!
— Team Havellsson NEET Samrat
```

**Sample:** `{{1}}` = Priya Deshmukh · `{{2}}` = RCPT-2627-0481 · `{{3}}` = ₹5,000 · `{{4}}` = 27 Jun 2026 · `{{5}}` = UPI · `{{6}}` = ₹28,700

**Send payload:**
```json
{
  "to": "919812345678",
  "type": "template",
  "template": {
    "language": { "policy": "deterministic", "code": "en" },
    "name": "payment_received",
    "components": [
      { "type": "body", "parameters": [
        { "type": "text", "text": "Priya Deshmukh" },
        { "type": "text", "text": "RCPT-2627-0481" },
        { "type": "text", "text": "₹5,000" },
        { "type": "text", "text": "27 Jun 2026" },
        { "type": "text", "text": "UPI" },
        { "type": "text", "text": "₹28,700" }
      ]}
    ]
  }
}
```

---

## Notes
- The variable order in the send payload **must match** the `{{1}}..{{n}}` order in the registered body.
- Keep names exactly: `inquiry_received`, `admission_confirmed`, `payment_received` — these are the `template.name` values used by the app.
- For the click-to-chat (wa.me) links currently in use, no approval is needed; templates are only required for **API auto-send** of business-initiated messages.
