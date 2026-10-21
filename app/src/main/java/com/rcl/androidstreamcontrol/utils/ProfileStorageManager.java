package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Persistent Saved Profiles Storage Manager.
 * Safely persists custom named browser profiles to internal private disk storage
 * and automatically synchronizes with Supabase cloud storage across all 30 devices:
 * - Device Model & Hardware Specifications
 * - Exact Canvas / WebGL Randomization Seed (guarantees 100% hash consistency)
 * - Proxy Session Tag & City/State Geo Preferences
 * - Cookies & Session Data for instant authenticated restore
 * - Multi-Device Task Locks (Permanent profile locking across phones)
 * Works offline, zero latency, persistent across reboots and cold launches.
 */
public class ProfileStorageManager {

    private static final String TAG = "ProfileStorage";
    private static final String PROFILES_FILE_NAME = "saved_profiles.json";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";
    public static final String SAVED_PROFILE_APP_NAME = "saved_profile";

    public static class SavedProfile {
        public String id;
        public String name;
        public String description;
        public long createdAt;
        public JSONObject profileJson;
        public boolean isNative;
        public double noiseSeed;
        public String timezone;
        public String geoState;
        public String city;
        public String ip;
        public String region;
        public String proxySessionTag;
        public String cookies;
        public String lastUrl;

        public SavedProfile() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = System.currentTimeMillis();
            this.geoState = "US";
            this.city = "";
            this.ip = "";
            this.region = "";
            this.isNative = false;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("name", name != null ? name : "");
                obj.put("description", description != null ? description : "");
                obj.put("createdAt", createdAt);
                if (profileJson != null) {
                    obj.put("profileJson", profileJson);
                }
                obj.put("isNative", isNative);
                obj.put("noiseSeed", noiseSeed);
                obj.put("timezone", timezone != null ? timezone : "America/New_York");
                obj.put("geoState", geoState != null ? geoState : "US");
                obj.put("city", city != null ? city : "");
                obj.put("ip", ip != null ? ip : "");
                obj.put("region", region != null ? region : "");
                obj.put("proxySessionTag", proxySessionTag != null ? proxySessionTag : "active");
                obj.put("cookies", cookies != null ? cookies : "");
                obj.put("lastUrl", lastUrl != null ? lastUrl : "https://www.google.com");
            } catch (Exception e) {
                Log.e(TAG, "Error serializing SavedProfile", e);
            }
            return obj;
        }

        public static SavedProfile fromJson(JSONObject obj) {
            if (obj == null) return null;
            SavedProfile sp = new SavedProfile();
            sp.id = obj.optString("id", UUID.randomUUID().toString());
            sp.name = obj.optString("name", "Unnamed Profile");
            sp.description = obj.optString("description", "");
            sp.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
            sp.profileJson = obj.optJSONObject("profileJson");
            sp.isNative = obj.optBoolean("isNative", false);
            sp.noiseSeed = obj.optDouble("noiseSeed", 0.42);
            sp.timezone = obj.optString("timezone", "America/New_York");
            sp.geoState = obj.optString("geoState", "US");
            sp.city = obj.optString("city", "");
            sp.ip = obj.optString("ip", "");
            sp.region = obj.optString("region", "");
            sp.proxySessionTag = obj.optString("proxySessionTag", "active");
            sp.cookies = obj.optString("cookies", "");
            sp.lastUrl = obj.optString("lastUrl", "https://www.google.com");
            return sp;
        }
    }

    /**
     * Retrieves all saved profiles from internal storage.
     */
    public static synchronized List<SavedProfile> getAllSavedProfiles(Context context) {
        List<SavedProfile> list = new ArrayList<>();
        if (context == null) return list;

        File file = new File(context.getFilesDir(), PROFILES_FILE_NAME);
        if (!file.exists()) {
            return list;
        }

        try (FileInputStream fis = context.openFileInput(PROFILES_FILE_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                SavedProfile sp = SavedProfile.fromJson(obj);
                if (sp != null) {
                    list.add(sp);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading saved profiles", e);
        }

        return list;
    }

    /**
     * Saves or updates a profile in the internal storage and syncs to Supabase cloud.
     */
    public static synchronized boolean saveProfile(Context context, SavedProfile profile) {
        if (context == null || profile == null) return false;

        List<SavedProfile> existing = getAllSavedProfiles(context);
        boolean updated = false;

        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).id.equals(profile.id)) {
                existing.set(i, profile);
                updated = true;
                break;
            }
        }

        if (!updated) {
            existing.add(profile);
        }

        boolean savedLocally = writeProfilesToFile(context, existing);

        // Asynchronously sync to Supabase cloud
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String devId = CloudProfileLockManager.getDeviceId(context);

                // Delete any existing cloud record for this profile id
                try {
                    String delFilter = URLEncoder.encode("*" + profile.id + "*", "UTF-8");
                    String delUrl = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq." + SAVED_PROFILE_APP_NAME +
                            "&content=ilike." + delFilter;
                    HttpURLConnection delConn = (HttpURLConnection) new URL(delUrl).openConnection();
                    delConn.setRequestMethod("DELETE");
                    delConn.setRequestProperty("apikey", ANON_KEY);
                    delConn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                    delConn.setConnectTimeout(5000);
                    delConn.setReadTimeout(5000);
                    delConn.getResponseCode();
                } catch (Exception ignored) {}

                // Upload updated profile JSON
                JSONObject payload = new JSONObject();
                payload.put("device_id", devId);
                payload.put("app_name", SAVED_PROFILE_APP_NAME);
                payload.put("content", profile.toJson().toString());

                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/keyboard_logs").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                Log.d(TAG, "Uploaded saved profile [" + profile.name + "] to Supabase: HTTP " + code);
            } catch (Exception e) {
                Log.w(TAG, "Error pushing saved profile to Supabase: " + e.getMessage());
            }
        });

        return savedLocally;
    }

    /**
     * Deletes a profile by ID locally and in the Supabase cloud.
     */
    public static synchronized boolean deleteProfile(Context context, String profileId) {
        if (context == null || profileId == null) return false;

        List<SavedProfile> existing = getAllSavedProfiles(context);
        boolean removed = false;

        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).id.equals(profileId)) {
                existing.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            writeProfilesToFile(context, existing);

            // Asynchronously delete from Supabase and unlock
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    String delFilter = URLEncoder.encode("*" + profileId + "*", "UTF-8");
                    String delUrl = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq." + SAVED_PROFILE_APP_NAME +
                            "&content=ilike." + delFilter;
                    HttpURLConnection delConn = (HttpURLConnection) new URL(delUrl).openConnection();
                    delConn.setRequestMethod("DELETE");
                    delConn.setRequestProperty("apikey", ANON_KEY);
                    delConn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                    delConn.setConnectTimeout(5000);
                    delConn.setReadTimeout(5000);
                    int code = delConn.getResponseCode();
                    Log.d(TAG, "Deleted saved profile [" + profileId + "] from Supabase: HTTP " + code);

                    // Also release any permanent lock
                    CloudProfileLockManager.setPermanentLock(context, "", profileId, false, null);
                } catch (Exception e) {
                    Log.w(TAG, "Error deleting profile from Supabase: " + e.getMessage());
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Synchronizes all saved profiles from Supabase cloud into local storage.
     * Ensures all 30 devices have access to the exact same shared fleet of profiles.
     */
    public static void syncFromCloud(Context context, Runnable onComplete) {
        if (context == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String urlStr = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq." + SAVED_PROFILE_APP_NAME +
                        "&select=id,device_id,content,created_at&order=created_at.desc&limit=500";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONArray arr = new JSONArray(sb.toString());
                    List<SavedProfile> cloudProfiles = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        String contentStr = row.optString("content", "");
                        if (!contentStr.isEmpty()) {
                            try {
                                SavedProfile sp = SavedProfile.fromJson(new JSONObject(contentStr));
                                if (sp != null && sp.id != null) {
                                    cloudProfiles.add(sp);
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    synchronized (ProfileStorageManager.class) {
                        List<SavedProfile> localProfiles = getAllSavedProfiles(context);
                        Map<String, SavedProfile> merged = new LinkedHashMap<>();
                        for (SavedProfile p : localProfiles) {
                            merged.put(p.id, p);
                        }
                        for (SavedProfile cp : cloudProfiles) {
                            if (!merged.containsKey(cp.id)) {
                                merged.put(cp.id, cp);
                            } else {
                                SavedProfile localP = merged.get(cp.id);
                                if (cp.createdAt > localP.createdAt) {
                                    merged.put(cp.id, cp);
                                }
                            }
                        }
                        List<SavedProfile> resultList = new ArrayList<>(merged.values());
                        writeProfilesToFile(context, resultList);
                        Log.d(TAG, "Synced saved profiles: " + resultList.size() + " total profiles (" + cloudProfiles.size() + " from cloud)");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error syncing saved profiles from cloud: " + e.getMessage());
            } finally {
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }

    /**
     * Generates a sensible default profile name based on count (e.g. "Profile 1", "Profile 4").
     */
    public static synchronized String generateDefaultProfileName(Context context) {
        List<SavedProfile> existing = getAllSavedProfiles(context);
        int max = 0;
        for (SavedProfile p : existing) {
            if (p.name != null && p.name.startsWith("Profile ")) {
                try {
                    int num = Integer.parseInt(p.name.replace("Profile ", "").trim());
                    if (num > max) max = num;
                } catch (Exception ignored) {}
            }
        }
        return "Profile " + (max > 0 ? (max + 1) : (existing.size() + 1));
    }

    private static boolean writeProfilesToFile(Context context, List<SavedProfile> list) {
        try {
            JSONArray arr = new JSONArray();
            for (SavedProfile p : list) {
                arr.put(p.toJson());
            }

            try (FileOutputStream fos = context.openFileOutput(PROFILES_FILE_NAME, Context.MODE_PRIVATE)) {
                fos.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing profiles to disk", e);
            return false;
        }
    }
}
