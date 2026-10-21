package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cloud Profile Lock Manager.
 *
 * Ensures multi-device isolation across phones:
 * 1. Automatic Session Locks: Active open tabs lock profiles with automated lease heartbeat.
 * 2. Permanent Locks: Users can explicitly lock profiles from the Saved Profiles Library.
 *    If someone locks 10 profiles, they stay locked exclusively to that user forever (across app reboots)
 *    and will NEVER be given or opened by anyone else until unlocked!
 */
public class CloudProfileLockManager {

    private static final String TAG = "CloudProfileLock";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    public static final String LOCK_APP_NAME = "profile_lock";
    public static final String PERMANENT_LOCK_APP_NAME = "permanent_profile_lock";
    private static final long LEASE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes heartbeat expiry for tab sessions

    private static volatile String sDeviceId = null;
    private static volatile String sDeviceModel = null;

    // Temporary session locks held by this device
    private static final Set<String> sMyActiveProfileNames = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> sMyActiveSavedIds = Collections.synchronizedSet(new HashSet<>());

    // Temporary session locks held by other devices
    private static final Set<String> sRemoteLockedProfiles = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> sRemoteProfileToDevice = new ConcurrentHashMap<>();
    private static final Set<String> sRemoteLockedSavedProfileIds = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> sRemoteSavedIdToDevice = new ConcurrentHashMap<>();

    // Permanent locks held by THIS device (never expire until unlocked)
    private static final Set<String> sMyPermanentLockedProfiles = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> sMyPermanentLockedSavedIds = Collections.synchronizedSet(new HashSet<>());

    // Permanent locks held by OTHER devices (never expire until unlocked by owner)
    private static final Set<String> sRemotePermanentLockedProfiles = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> sRemotePermanentLockedSavedIds = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> sRemotePermanentLockDeviceMap = new ConcurrentHashMap<>();

    private static ScheduledExecutorService sScheduler = null;
    private static volatile boolean sInitialized = false;

    public static synchronized void init(Context context) {
        if (sInitialized) return;
        sInitialized = true;

        ensureDeviceId(context);

        sScheduler = Executors.newSingleThreadScheduledExecutor();

        // 1. Initial remote lock sync
        syncRemoteLocksAsync(context, null);

        // 2. Periodic sync & heartbeat every 30 seconds
        sScheduler.scheduleWithFixedDelay(() -> {
            try {
                // Heartbeat my session locks so they don't expire
                refreshMyLocks(context);
                // Sync latest remote locks from other devices
                syncRemoteLocksInternal(context);
            } catch (Exception e) {
                Log.w(TAG, "Heartbeat/sync cycle error: " + e.getMessage());
            }
        }, 15, 30, TimeUnit.SECONDS);

        Log.d(TAG, "Initialized CloudProfileLockManager for device " + sDeviceId + " (" + sDeviceModel + ")");
    }

    public static String getDeviceId(Context context) {
        ensureDeviceId(context);
        return sDeviceId;
    }

    public static String getDeviceModel() {
        if (sDeviceModel == null) {
            sDeviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        }
        return sDeviceModel;
    }

    private static void ensureDeviceId(Context context) {
        if (sDeviceId != null) return;
        try {
            sDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {}

        if (sDeviceId == null || sDeviceId.trim().isEmpty() || "9774d56d682e549c".equals(sDeviceId)) {
            SharedPreferences sp = context.getSharedPreferences("device_identity_prefs", Context.MODE_PRIVATE);
            sDeviceId = sp.getString("unique_device_uuid", null);
            if (sDeviceId == null) {
                sDeviceId = "dev_" + UUID.randomUUID().toString().substring(0, 12);
                sp.edit().putString("unique_device_uuid", sDeviceId).apply();
            }
        }
        sDeviceModel = Build.MANUFACTURER + " " + Build.MODEL;
    }

    /**
     * Acquire a temporary cloud lock for a profile while a tab is active.
     */
    public static void acquireLock(Context context, String profileName, String savedProfileId) {
        if (profileName == null || profileName.trim().isEmpty()) return;
        ensureDeviceId(context);

        String cleanName = profileName.trim();
        sMyActiveProfileNames.add(cleanName);
        if (savedProfileId != null && !savedProfileId.trim().isEmpty()) {
            sMyActiveSavedIds.add(savedProfileId.trim());
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("device_id", sDeviceId);
                payload.put("app_name", LOCK_APP_NAME);

                JSONObject content = new JSONObject();
                content.put("profile", cleanName);
                if (savedProfileId != null && !savedProfileId.trim().isEmpty()) {
                    content.put("saved_id", savedProfileId.trim());
                }
                content.put("device_model", sDeviceModel);
                content.put("timestamp", System.currentTimeMillis());

                payload.put("content", content.toString());

                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/keyboard_logs").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                Log.d(TAG, "Acquired session lock for [" + cleanName + "]: HTTP " + code);
            } catch (Exception e) {
                Log.w(TAG, "Failed to acquire session lock for [" + cleanName + "]: " + e.getMessage());
            }
        });
    }

    /**
     * Release a temporary cloud lock when a tab is closed.
     */
    public static void releaseLock(Context context, String profileName, String savedProfileId) {
        if (profileName == null || profileName.trim().isEmpty()) return;
        ensureDeviceId(context);

        String cleanName = profileName.trim();
        sMyActiveProfileNames.remove(cleanName);
        if (savedProfileId != null && !savedProfileId.trim().isEmpty()) {
            sMyActiveSavedIds.remove(savedProfileId.trim());
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encodedFilter = URLEncoder.encode("*" + cleanName + "*", "UTF-8");
                String urlStr = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq." + LOCK_APP_NAME +
                        "&device_id=eq." + sDeviceId +
                        "&content=ilike." + encodedFilter;

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                int code = conn.getResponseCode();
                Log.d(TAG, "Released session lock for [" + cleanName + "]: HTTP " + code);
            } catch (Exception e) {
                Log.w(TAG, "Failed to release session lock for [" + cleanName + "]: " + e.getMessage());
            }
        });
    }

    /**
     * Toggle or set a PERMANENT lock on a profile.
     * Permanent locks never expire until the owner explicitly unlocks them.
     */
    public static void setPermanentLock(Context context, String profileName, String savedProfileId, boolean lock, Runnable onComplete) {
        ensureDeviceId(context);
        String cleanName = (profileName != null) ? profileName.trim() : "";
        String cleanSavedId = (savedProfileId != null) ? savedProfileId.trim() : "";

        if (lock) {
            if (!cleanName.isEmpty()) sMyPermanentLockedProfiles.add(cleanName);
            if (!cleanSavedId.isEmpty()) sMyPermanentLockedSavedIds.add(cleanSavedId);
        } else {
            if (!cleanName.isEmpty()) sMyPermanentLockedProfiles.remove(cleanName);
            if (!cleanSavedId.isEmpty()) sMyPermanentLockedSavedIds.remove(cleanSavedId);
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (lock) {
                    JSONObject payload = new JSONObject();
                    payload.put("device_id", sDeviceId);
                    payload.put("app_name", PERMANENT_LOCK_APP_NAME);

                    JSONObject content = new JSONObject();
                    content.put("profile", cleanName);
                    content.put("saved_id", cleanSavedId);
                    content.put("device_model", sDeviceModel);
                    content.put("locked_by_device", sDeviceId);
                    content.put("locked_at", System.currentTimeMillis());

                    payload.put("content", content.toString());

                    HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/keyboard_logs").openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("apikey", ANON_KEY);
                    conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = conn.getResponseCode();
                    Log.d(TAG, "Permanent lock set for [" + cleanName + " / " + cleanSavedId + "]: HTTP " + code);
                } else {
                    // Unlock: delete from Supabase
                    String filterStr;
                    if (!cleanSavedId.isEmpty()) {
                        filterStr = URLEncoder.encode("*" + cleanSavedId + "*", "UTF-8");
                    } else {
                        filterStr = URLEncoder.encode("*" + cleanName + "*", "UTF-8");
                    }
                    String urlStr = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq." + PERMANENT_LOCK_APP_NAME +
                            "&device_id=eq." + sDeviceId +
                            "&content=ilike." + filterStr;

                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestMethod("DELETE");
                    conn.setRequestProperty("apikey", ANON_KEY);
                    conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    int code = conn.getResponseCode();
                    Log.d(TAG, "Permanent lock removed for [" + cleanName + " / " + cleanSavedId + "]: HTTP " + code);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error setting permanent lock: " + e.getMessage());
            } finally {
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }

    /**
     * Check if a profile is permanently locked by ME.
     */
    public static boolean isPermanentlyLockedByMe(String profileName, String savedProfileId) {
        if (profileName != null && sMyPermanentLockedProfiles.contains(profileName.trim())) return true;
        if (savedProfileId != null && sMyPermanentLockedSavedIds.contains(savedProfileId.trim())) return true;
        return false;
    }

    /**
     * Check if a profile is permanently locked by ANOTHER user/device.
     */
    public static boolean isPermanentlyLockedByOther(String profileName, String savedProfileId) {
        if (profileName != null && sRemotePermanentLockedProfiles.contains(profileName.trim())) return true;
        if (savedProfileId != null && sRemotePermanentLockedSavedIds.contains(savedProfileId.trim())) return true;
        return false;
    }

    /**
     * Get device name of permanent locker.
     */
    public static String getPermanentLockingDeviceName(String profileOrId) {
        if (profileOrId == null) return "Another Device";
        String dev = sRemotePermanentLockDeviceMap.get(profileOrId.trim());
        return (dev != null && !dev.isEmpty()) ? dev : "Another Device";
    }

    /**
     * Release all temporary session locks held by this device upon app exit.
     * Note: Permanent locks stay persistent!
     */
    public static void releaseAllMyLocks(Context context) {
        ensureDeviceId(context);
        sMyActiveProfileNames.clear();
        sMyActiveSavedIds.clear();

        Thread t = new Thread(() -> {
            try {
                String urlStr = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq." + LOCK_APP_NAME +
                        "&device_id=eq." + sDeviceId;

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                int code = conn.getResponseCode();
                Log.d(TAG, "Cleaned session locks for device " + sDeviceId + ": HTTP " + code);
            } catch (Exception e) {
                Log.w(TAG, "Failed to clean session locks: " + e.getMessage());
            }
        });
        t.start();
        try {
            t.join(1200);
        } catch (InterruptedException ignored) {}
    }

    private static void refreshMyLocks(Context context) {
        if (sMyActiveProfileNames.isEmpty()) return;
        ensureDeviceId(context);

        for (String prof : new HashSet<>(sMyActiveProfileNames)) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("device_id", sDeviceId);
                payload.put("app_name", LOCK_APP_NAME);

                JSONObject content = new JSONObject();
                content.put("profile", prof);
                content.put("device_model", sDeviceModel);
                content.put("timestamp", System.currentTimeMillis());

                payload.put("content", content.toString());

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
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }
    }

    public static void syncRemoteLocksAsync(Context context, Runnable onComplete) {
        ensureDeviceId(context);
        Executors.newSingleThreadExecutor().execute(() -> {
            syncRemoteLocksInternal(context);
            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
        });
    }

    private static void syncRemoteLocksInternal(Context context) {
        try {
            ensureDeviceId(context);

            String urlStr = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=in.(" +
                    LOCK_APP_NAME + "," + PERMANENT_LOCK_APP_NAME + ")" +
                    "&select=id,device_id,app_name,content,created_at&order=created_at.desc&limit=300";

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray arr = new JSONArray(sb.toString());

                // Session locks
                Set<String> newProfiles = new HashSet<>();
                Map<String, String> newProfToDevice = new ConcurrentHashMap<>();
                Set<String> newSavedIds = new HashSet<>();
                Map<String, String> newSavedToDevice = new ConcurrentHashMap<>();

                // Permanent locks
                Set<String> newMyPermProfiles = new HashSet<>();
                Set<String> newMyPermSavedIds = new HashSet<>();
                Set<String> newRemotePermProfiles = new HashSet<>();
                Set<String> newRemotePermSavedIds = new HashSet<>();
                Map<String, String> newRemotePermLockMap = new ConcurrentHashMap<>();

                long now = System.currentTimeMillis();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    String devId = row.optString("device_id", "");
                    String appName = row.optString("app_name", "");
                    String contentStr = row.optString("content", "");
                    if (contentStr.isEmpty()) continue;

                    try {
                        JSONObject content = new JSONObject(contentStr);
                        String profile = content.optString("profile", "").trim();
                        String savedId = content.optString("saved_id", "").trim();
                        String model = content.optString("device_model", "Other Phone").trim();

                        if (PERMANENT_LOCK_APP_NAME.equals(appName)) {
                            // PERMANENT LOCK: No expiry, active forever until unlocked
                            if (sDeviceId.equals(devId)) {
                                if (!profile.isEmpty()) newMyPermProfiles.add(profile);
                                if (!savedId.isEmpty()) newMyPermSavedIds.add(savedId);
                            } else {
                                if (!profile.isEmpty()) {
                                    newRemotePermProfiles.add(profile);
                                    newRemotePermLockMap.put(profile, model);
                                }
                                if (!savedId.isEmpty()) {
                                    newRemotePermSavedIds.add(savedId);
                                    newRemotePermLockMap.put(savedId, model);
                                }
                            }
                        } else {
                            // SESSION LOCK: Skip my own, filter out expired > LEASE_TIMEOUT_MS
                            if (sDeviceId.equals(devId)) continue;
                            long ts = content.optLong("timestamp", 0);
                            if (ts > 0 && (now - ts) > LEASE_TIMEOUT_MS) {
                                continue;
                            }
                            if (!profile.isEmpty()) {
                                newProfiles.add(profile);
                                newProfToDevice.put(profile, model);
                            }
                            if (!savedId.isEmpty()) {
                                newSavedIds.add(savedId);
                                newSavedToDevice.put(savedId, model);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                sRemoteLockedProfiles.clear();
                sRemoteLockedProfiles.addAll(newProfiles);
                sRemoteProfileToDevice.clear();
                sRemoteProfileToDevice.putAll(newProfToDevice);

                sRemoteLockedSavedProfileIds.clear();
                sRemoteLockedSavedProfileIds.addAll(newSavedIds);
                sRemoteSavedIdToDevice.clear();
                sRemoteSavedIdToDevice.putAll(newSavedToDevice);

                sMyPermanentLockedProfiles.clear();
                sMyPermanentLockedProfiles.addAll(newMyPermProfiles);
                sMyPermanentLockedSavedIds.clear();
                sMyPermanentLockedSavedIds.addAll(newMyPermSavedIds);

                sRemotePermanentLockedProfiles.clear();
                sRemotePermanentLockedProfiles.addAll(newRemotePermProfiles);
                sRemotePermanentLockedSavedIds.clear();
                sRemotePermanentLockedSavedIds.addAll(newRemotePermSavedIds);
                sRemotePermanentLockDeviceMap.clear();
                sRemotePermanentLockDeviceMap.putAll(newRemotePermLockMap);

                Log.d(TAG, "Synced locks: " + sRemoteLockedProfiles.size() + " session locks, " +
                        sRemotePermanentLockedProfiles.size() + " remote perm locks, " +
                        sMyPermanentLockedProfiles.size() + " my perm locks");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error syncing remote profile locks: " + e.getMessage());
        }
    }

    public static boolean isProfileLockedByOther(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) return false;
        String clean = profileName.trim();
        return sRemoteLockedProfiles.contains(clean) || sRemotePermanentLockedProfiles.contains(clean);
    }

    public static boolean isSavedProfileLockedByOther(String savedProfileId) {
        if (savedProfileId == null || savedProfileId.trim().isEmpty()) return false;
        String clean = savedProfileId.trim();
        return sRemoteLockedSavedProfileIds.contains(clean) || sRemotePermanentLockedSavedIds.contains(clean);
    }

    public static String getLockingDeviceName(String profileOrId) {
        if (profileOrId == null) return "Another Device";
        String clean = profileOrId.trim();
        // Check permanent lock first
        String dev = sRemotePermanentLockDeviceMap.get(clean);
        if (dev != null && !dev.isEmpty()) return dev;
        // Check session lock
        dev = sRemoteProfileToDevice.get(clean);
        if (dev == null) {
            dev = sRemoteSavedIdToDevice.get(clean);
        }
        return (dev != null && !dev.isEmpty()) ? dev : "Another Device";
    }

    /**
     * Set of all profile names currently locked (session locks by others + permanent task locks by anyone).
     * These will NEVER be auto-assigned to random tabs.
     */
    public static Set<String> getRemoteLockedProfileNames() {
        Set<String> set = new HashSet<>(sRemoteLockedProfiles);
        set.addAll(sRemotePermanentLockedProfiles);
        set.addAll(sMyPermanentLockedProfiles);
        return set;
    }
}
