package com.rcl.androidstreamcontrol.utils;

import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SupabaseClient {
    private static final String TAG = "SupabaseClient";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    // HARDCODED TENANT CREDENTIALS FOR MAAS BUILD
    public static final String TENANT_ID = "admin@controdroid.com";
    public static final String TENANT_PASSWORD = "password123";

    public interface AuthCallback {
        void onSuccess(String userId, String token);
        void onError(String error);
    }

    public static void signUpOrLogin(String email, String password, String deviceName, AuthCallback callback) {
        new Thread(() -> {
            try {
                // Try Sign Up first
                String userId = null;
                String token = null;
                boolean signUpSuccess = false;

                JSONObject authPayload = new JSONObject();
                authPayload.put("email", email);
                authPayload.put("password", password);

                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/auth/v1/signup").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(authPayload.toString().getBytes());
                }

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();
                    JSONObject res = new JSONObject(response.toString());
                    userId = res.getJSONObject("user").getString("id");
                    token = res.optString("access_token");
                    signUpSuccess = true;
                }

                // If user already registered, login instead
                if (!signUpSuccess) {
                    conn = (HttpURLConnection) new URL(SUPABASE_URL + "/auth/v1/token?grant_type=password").openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("apikey", ANON_KEY);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(authPayload.toString().getBytes());
                    }

                    if (conn.getResponseCode() == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) response.append(line);
                        in.close();
                        JSONObject res = new JSONObject(response.toString());
                        userId = res.getJSONObject("user").getString("id");
                        token = res.getString("access_token");
                    } else {
                        if (callback != null) callback.onError("Auth failed: " + conn.getResponseMessage());
                        return;
                    }
                }

                if (userId != null && token != null) {
                    registerDevice(userId, token, deviceName);
                    if (callback != null) callback.onSuccess(userId, token);
                }

            } catch (Exception e) {
                Log.e(TAG, "Auth exception", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void registerDevice(String userId, String token, String deviceName) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("id", userId);
            payload.put("device_name", deviceName);
            payload.put("status", "online");
            payload.put("tenant_id", TENANT_ID);
            payload.put("tenant_password", TENANT_PASSWORD);

            HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/devices").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Device registration code: " + code);
        } catch (Exception e) {
            Log.e(TAG, "Device registration failed", e);
        }
    }

    public static void updateDeviceLocation(String userId, String token, double lat, double lng) {
        new Thread(() -> {
            try {
                JSONObject location = new JSONObject();
                location.put("lat", lat);
                location.put("lng", lng);

                JSONObject payload = new JSONObject();
                payload.put("last_location", location);

                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/devices?id=eq." + userId).openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, "Location update failed", e);
            }
        }).start();
    }
}
