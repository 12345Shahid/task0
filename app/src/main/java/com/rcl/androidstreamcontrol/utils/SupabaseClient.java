package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SupabaseClient {
    private static final String TAG = "SupabaseClient";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    // HARDCODED TENANT CREDENTIALS FOR MAAS BUILD
    public static final String TENANT_ID = "admin@controdroid.com";
    public static final String TENANT_PASSWORD = "password123";

    private static final long TOKEN_REFRESH_AGE_MS = 40L * 60L * 1000L; // Refresh JWT after 40 mins (expires at 60 mins)
    private static final Object AUTH_LOCK = new Object();

    public interface AuthCallback {
        void onSuccess(String userId, String token);
        void onError(String error);
    }

    private static String nowIsoUtc() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String[] postAuthEndpoint(String path, String email, String password) throws Exception {
        JSONObject authPayload = new JSONObject();
        authPayload.put("email", email);
        authPayload.put("password", password);

        HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
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
            String userId = res.getJSONObject("user").getString("id");
            String token = res.optString("access_token", "");
            if (!userId.isEmpty() && !token.isEmpty()) {
                return new String[]{userId, token};
            }
        }
        return null;
    }

    public static void signUpOrLogin(String email, String password, String deviceName, AuthCallback callback) {
        new Thread(() -> {
            try {
                // Try login first for existing devices, then sign up if new
                String[] auth = postAuthEndpoint("/auth/v1/token?grant_type=password", email, password);
                if (auth == null) {
                    auth = postAuthEndpoint("/auth/v1/signup", email, password);
                }

                if (auth != null) {
                    registerDevice(auth[0], auth[1], deviceName);
                    if (callback != null) callback.onSuccess(auth[0], auth[1]);
                } else {
                    if (callback != null) callback.onError("Auth failed for " + email);
                }
            } catch (Exception e) {
                Log.e(TAG, "Auth exception", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void registerDevice(String userId, String token, String deviceName) {
        try {
            String nowStr = nowIsoUtc();
            JSONObject payload = new JSONObject();
            payload.put("id", userId);
            payload.put("device_name", deviceName);
            payload.put("status", "online");
            payload.put("last_heartbeat", nowStr);
            payload.put("last_seen", nowStr);
            payload.put("tenant_id", TENANT_ID);
            payload.put("tenant_password", TENANT_PASSWORD);

            HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/devices").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("apikey", ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Device registration code: " + code + " (" + deviceName + ")");
        } catch (Exception e) {
            Log.e(TAG, "Device registration failed", e);
        }
    }

    /**
     * Ensures the device is registered with a valid, non-expired Supabase Auth JWT
     * and updates its online heartbeat. Automatically refreshes expired JWTs on 401/403
     * or when older than 40 minutes.
     */
    public static void ensureDeviceAndHeartbeat(Context context) {
        final Context appCtx = context.getApplicationContext();
        new Thread(() -> {
            synchronized (AUTH_LOCK) {
                try {
                    SharedPreferences prefs = appCtx.getSharedPreferences("PortalPrefs", Context.MODE_PRIVATE);
                    String uid = prefs.getString("supabase_uid", null);
                    String token = prefs.getString("supabase_token", null);
                    long tokenObtainedAt = prefs.getLong("token_obtained_at", 0L);

                    boolean hasValidCredentials =
                            uid != null && uid.contains("-")
                            && token != null && !token.contains("...") && token.length() > 50;
                    boolean tokenNeedsRefresh = (System.currentTimeMillis() - tokenObtainedAt) > TOKEN_REFRESH_AGE_MS;

                    if (hasValidCredentials && !tokenNeedsRefresh) {
                        int code = sendHeartbeatHttp(uid, token);
                        if (code >= 200 && code < 300) {
                            return;
                        }
                        if (code != 401 && code != 403) {
                            // Transient network error; keep existing credentials
                            return;
                        }
                        Log.w(TAG, "Heartbeat HTTP " + code + " — refreshing expired Supabase token.");
                    }

                    refreshOrRegisterDeviceSync(appCtx, prefs);
                } catch (Exception e) {
                    Log.e(TAG, "ensureDeviceAndHeartbeat failed", e);
                }
            }
        }).start();
    }

    private static void refreshOrRegisterDeviceSync(Context appCtx, SharedPreferences prefs) throws Exception {
        String androidId = Settings.Secure.getString(appCtx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = "dev" + (System.currentTimeMillis() % 100000);
        }
        String email = "device_" + androidId.toLowerCase(Locale.US) + "@mindful.app";
        String password = "MindfulDevice123!";

        String deviceName = prefs.getString("device_name", null);
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = computeNextDeviceName();
        }

        // Login first (for returning devices), otherwise sign up new device account
        String[] auth = postAuthEndpoint("/auth/v1/token?grant_type=password", email, password);
        if (auth == null) {
            auth = postAuthEndpoint("/auth/v1/signup", email, password);
        }

        if (auth != null) {
            String newUid = auth[0];
            String newToken = auth[1];
            prefs.edit()
                    .putString("supabase_uid", newUid)
                    .putString("supabase_token", newToken)
                    .putLong("token_obtained_at", System.currentTimeMillis())
                    .putString("device_name", deviceName)
                    .putString("tenant_id", TENANT_ID)
                    .putString("tenant_password", TENANT_PASSWORD)
                    .apply();

            registerDevice(newUid, newToken, deviceName);
            Log.d(TAG, "Device session refreshed & online: " + deviceName + " (" + newUid + ")");
        } else {
            Log.e(TAG, "Failed to authenticate device " + email);
        }
    }

    private static String computeNextDeviceName() {
        try {
            HttpURLConnection countConn = (HttpURLConnection) new URL(
                    SUPABASE_URL + "/rest/v1/devices?select=id"
            ).openConnection();
            countConn.setRequestMethod("GET");
            countConn.setConnectTimeout(6000);
            countConn.setReadTimeout(6000);
            countConn.setRequestProperty("apikey", ANON_KEY);
            if (countConn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(countConn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                JSONArray arr = new JSONArray(sb.toString());
                return "Device" + (50 + arr.length());
            }
        } catch (Exception ignored) {
        }
        return "Device_" + (Build.MODEL != null ? Build.MODEL.replaceAll("\\s+", "") : "Android");
    }

    private static int sendHeartbeatHttp(String userId, String token) {
        try {
            String nowStr = nowIsoUtc();
            JSONObject payload = new JSONObject();
            payload.put("status", "online");
            payload.put("last_heartbeat", nowStr);
            payload.put("last_seen", nowStr);

            HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/devices?id=eq." + userId).openConnection();
            conn.setRequestMethod("PATCH");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("apikey", ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }
            int code = conn.getResponseCode();
            Log.d(TAG, "Heartbeat for " + userId + " -> HTTP " + code);
            return code;
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat HTTP failed", e);
            return -1;
        }
    }

    public static void updateHeartbeat(String userId, String token) {
        new Thread(() -> sendHeartbeatHttp(userId, token)).start();
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
