package com.tution.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.tution.util.RazorpayConfig;

/**
 * Talks to Razorpay's REST API using only the JDK (no SDK / extra JARs).
 *  - createOrder(): POST /v1/orders  (Basic auth with key id:secret)
 *  - verify():      HMAC-SHA256(order_id|payment_id, key_secret) == signature
 *
 * In MOCK mode both methods short-circuit so the flow can be tested without keys.
 */
public class RazorpayService {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";
    private static final Pattern ORDER_ID  = Pattern.compile("\"id\"\\s*:\\s*\"(order_[^\"]+)\"");

    /** Creates an order for the given rupee amount and returns its order id. */
    public String createOrder(int amountRupees, String receipt) throws IOException {
        if (RazorpayConfig.isMock()) {
            return "order_TEST" + System.currentTimeMillis();
        }
        int paise = amountRupees * 100;
        String payload = "{\"amount\":" + paise + ",\"currency\":\"INR\",\"receipt\":\""
                       + receipt + "\",\"payment_capture\":1}";

        HttpURLConnection con = (HttpURLConnection) new URL(ORDERS_URL).openConnection();
        con.setRequestMethod("POST");
        String auth = Base64.getEncoder().encodeToString(
            (RazorpayConfig.KEY_ID + ":" + RazorpayConfig.KEY_SECRET).getBytes(StandardCharsets.UTF_8));
        con.setRequestProperty("Authorization", "Basic " + auth);
        con.setRequestProperty("Content-Type", "application/json");
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = con.getResponseCode();
        String resp = read(code < 400 ? con.getInputStream() : con.getErrorStream());
        if (code >= 400) {
            throw new IOException("Razorpay order creation failed (" + code + "): " + resp);
        }
        Matcher m = ORDER_ID.matcher(resp);
        if (m.find()) {
            return m.group(1);
        }
        throw new IOException("Could not parse order id from Razorpay response: " + resp);
    }

    /** Verifies the checkout callback signature. Always true in mock mode. */
    public boolean verify(String orderId, String paymentId, String signature) {
        if (RazorpayConfig.isMock()) {
            return true;
        }
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                RazorpayConfig.KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            // constant-time compare
            return constantTimeEquals(sb.toString(), signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private String read(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
