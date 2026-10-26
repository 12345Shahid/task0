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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Anti-Detect Module — Dynamic Device Profile Pool
 *
 * Supports dynamic fetching, updating, and deleting profiles from Supabase.
 * - Editable in Supabase Table Editor (via 'device_profiles' table or 'keyboard_logs?app_name=device_profile')
 * - Local offline disk caching for zero-latency startup and offline reliability
 * - Fallback to 250 built-in high-end mobile device profiles (80 iOS + 170 Android)
 */
public class DeviceProfilePool {

    private static final String TAG = "DeviceProfilePool";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";
    private static final String CACHE_FILE = "device_profiles_cache.json";

    private static final List<JSONObject> sDynamicProfiles = new CopyOnWriteArrayList<>();
    private static volatile boolean sInitialized = false;
    private static volatile boolean sLoadedFromCloud = false;

    private static final String[][] PROFILES = {
        // ==================== 80 iOS Profiles (Apple Inc. / Apple GPU) ====================
        {"iPhone 15 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 15",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 15",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "430", "932", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "393", "852", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 14 Plus",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.2 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 14",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 14",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 14",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 14",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.2 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1.2 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1.2 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.2 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 13 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6.1 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "428", "926", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 12",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 12",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 12",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.2 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "390", "844", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 12 mini",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 11 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone 11 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone 11 Pro Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone 11 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 11 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 11 Pro",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "375", "812", "Apple Inc.", "Apple GPU"},
        {"iPhone 11",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone 11",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone 11",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.2 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 3",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
         "America/Chicago", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 3",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Denver", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 3",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
         "America/Phoenix", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 3",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Los_Angeles", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 2",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Anchorage", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 2",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
         "America/Honolulu", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone SE 2",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Puerto_Rico", "en-US", "375", "667", "Apple Inc.", "Apple GPU"},
        {"iPhone XR",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Boise", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone XR",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
         "America/New_York", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone XR",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.7.8 Mobile/15E148 Safari/604.1",
         "America/Detroit", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},
        {"iPhone XS Max",
         "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
         "America/Indiana/Indianapolis", "en-US", "414", "896", "Apple Inc.", "Apple GPU"},

        // ==================== 170 Android Profiles (Pixel, Samsung, OnePlus, Xiaomi, etc.) ====================
        {"Pixel 9 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel Fold",
         "Mozilla/5.0 (Linux; Android 14; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel Fold",
         "Mozilla/5.0 (Linux; Android 14; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel Fold",
         "Mozilla/5.0 (Linux; Android 14; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 6 Pro",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6 Pro",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6 Pro",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 5",
         "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Pixel 5",
         "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Pixel 5a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 5a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Pixel 5a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 5a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24+",
         "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24+",
         "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24+",
         "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24",
         "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24",
         "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24",
         "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23+",
         "Mozilla/5.0 (Linux; Android 13; SM-S916B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23+",
         "Mozilla/5.0 (Linux; Android 13; SM-S916B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23+",
         "Mozilla/5.0 (Linux; Android 13; SM-S916B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23",
         "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23",
         "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23",
         "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-S711B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S23 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-S711B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S23 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-S711B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22+",
         "Mozilla/5.0 (Linux; Android 12; SM-S906B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22+",
         "Mozilla/5.0 (Linux; Android 12; SM-S906B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22",
         "Mozilla/5.0 (Linux; Android 12; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22",
         "Mozilla/5.0 (Linux; Android 12; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22",
         "Mozilla/5.0 (Linux; Android 12; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S21 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21",
         "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21",
         "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy Note 20 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 650"},
        {"Samsung Galaxy Note 20 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 650"},
        {"Samsung Galaxy Note 20 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 650"},
        {"Samsung Galaxy Z Fold5",
         "Mozilla/5.0 (Linux; Android 14; SM-F946B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Fold5",
         "Mozilla/5.0 (Linux; Android 14; SM-F946B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Fold5",
         "Mozilla/5.0 (Linux; Android 14; SM-F946B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Flip5",
         "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Flip5",
         "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Flip5",
         "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Fold4",
         "Mozilla/5.0 (Linux; Android 13; SM-F936B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Z Fold4",
         "Mozilla/5.0 (Linux; Android 13; SM-F936B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Z Flip4",
         "Mozilla/5.0 (Linux; Android 13; SM-F721B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Z Flip4",
         "Mozilla/5.0 (Linux; Android 13; SM-F721B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Tab S9",
         "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Boise", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Tab S9",
         "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/New_York", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Tab S8",
         "Mozilla/5.0 (Linux; Android 12; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Detroit", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Tab S8",
         "Mozilla/5.0 (Linux; Android 12; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy A54",
         "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy A54",
         "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy A54",
         "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy A53",
         "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G68 MP4"},
        {"Samsung Galaxy A53",
         "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G68 MP4"},
        {"Samsung Galaxy A52",
         "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 618"},
        {"Samsung Galaxy A52",
         "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 618"},
        {"Samsung Galaxy A34",
         "Mozilla/5.0 (Linux; Android 13; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G68 MC4"},
        {"Samsung Galaxy A34",
         "Mozilla/5.0 (Linux; Android 13; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G68 MC4"},
        {"Samsung Galaxy A34",
         "Mozilla/5.0 (Linux; Android 13; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G68 MC4"},
        {"Samsung Galaxy A14",
         "Mozilla/5.0 (Linux; Android 13; SM-A145R) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G52 MC2"},
        {"Samsung Galaxy A14",
         "Mozilla/5.0 (Linux; Android 13; SM-A145R) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G52 MC2"},
        {"Samsung Galaxy M54",
         "Mozilla/5.0 (Linux; Android 13; SM-M546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy M54",
         "Mozilla/5.0 (Linux; Android 13; SM-M546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12R",
         "Mozilla/5.0 (Linux; Android 14; CPH2609) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 12R",
         "Mozilla/5.0 (Linux; Android 14; CPH2609) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 12R",
         "Mozilla/5.0 (Linux; Android 14; CPH2609) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus Open",
         "Mozilla/5.0 (Linux; Android 14; CPH2551) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus Open",
         "Mozilla/5.0 (Linux; Android 14; CPH2551) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus Open",
         "Mozilla/5.0 (Linux; Android 14; CPH2551) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 10 Pro",
         "Mozilla/5.0 (Linux; Android 12; NE2213) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10 Pro",
         "Mozilla/5.0 (Linux; Android 12; NE2213) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10 Pro",
         "Mozilla/5.0 (Linux; Android 12; NE2213) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10T",
         "Mozilla/5.0 (Linux; Android 13; CPH2415) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10T",
         "Mozilla/5.0 (Linux; Android 13; CPH2415) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 9 Pro",
         "Mozilla/5.0 (Linux; Android 12; LE2123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"OnePlus 9 Pro",
         "Mozilla/5.0 (Linux; Android 12; LE2123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"OnePlus Nord 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2491) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G710 MC10"},
        {"OnePlus Nord 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2491) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G710 MC10"},
        {"OnePlus Nord 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2491) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G710 MC10"},
        {"OnePlus Nord CE 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2569) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 642L"},
        {"OnePlus Nord CE 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2569) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 642L"},
        {"OnePlus Nord N30",
         "Mozilla/5.0 (Linux; Android 13; CPH2513) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 619"},
        {"OnePlus Nord N30",
         "Mozilla/5.0 (Linux; Android 13; CPH2513) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 619"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Detroit", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Chicago", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Denver", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14",
         "Mozilla/5.0 (Linux; Android 14; 23127PN0CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14",
         "Mozilla/5.0 (Linux; Android 14; 23127PN0CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14",
         "Mozilla/5.0 (Linux; Android 14; 23127PN0CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 13 Ultra",
         "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Ultra",
         "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Ultra",
         "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Pro",
         "Mozilla/5.0 (Linux; Android 13; 2210132G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Pro",
         "Mozilla/5.0 (Linux; Android 13; 2210132G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13",
         "Mozilla/5.0 (Linux; Android 13; 2211133G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13",
         "Mozilla/5.0 (Linux; Android 13; 2211133G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13T Pro",
         "Mozilla/5.0 (Linux; Android 13; 23078PND5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC11"},
        {"Xiaomi 13T Pro",
         "Mozilla/5.0 (Linux; Android 13; 23078PND5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC11"},
        {"Xiaomi Redmi Note 13 Pro",
         "Mozilla/5.0 (Linux; Android 14; 2404) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 710"},
        {"Xiaomi Redmi Note 13 Pro",
         "Mozilla/5.0 (Linux; Android 14; 2404) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 710"},
        {"Xiaomi Redmi Note 13 Pro",
         "Mozilla/5.0 (Linux; Android 14; 2404) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 710"},
        {"Redmi Note 13 Pro+",
         "Mozilla/5.0 (Linux; Android 13; 23090RA98G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G610 MC4"},
        {"Redmi Note 13 Pro+",
         "Mozilla/5.0 (Linux; Android 13; 23090RA98G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G610 MC4"},
        {"Redmi Note 13 Pro+",
         "Mozilla/5.0 (Linux; Android 13; 23090RA98G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G610 MC4"},
        {"Redmi K70 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23117RK66C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.120 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Redmi K70 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23117RK66C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Redmi K70 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23117RK66C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Redmi K70",
         "Mozilla/5.0 (Linux; Android 14; 2311DRK48C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
    };

    /**
     * Initializes the Device Profile Pool:
     * 1. Immediately loads offline cached profiles from disk if available.
     * 2. Asynchronously synchronizes with Supabase cloud to fetch any added, updated, or deleted profiles.
     */
    public static synchronized void init(Context context) {
        if (sInitialized) return;
        sInitialized = true;

        if (context != null) {
            loadFromLocalCache(context);
        }

        syncProfilesFromSupabase(context, null);
    }

    /**
     * Synchronizes dynamic device profiles from Supabase.
     * Checks dedicated 'device_profiles' table first; if not present or empty,
     * queries 'keyboard_logs' with app_name = 'device_profile'.
     */
    public static void syncProfilesFromSupabase(Context context, Runnable onComplete) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<JSONObject> fetched = tryFetchFromDeviceProfilesTable();
                if (fetched == null || fetched.isEmpty()) {
                    fetched = fetchFromKeyboardLogsTable();
                }

                if (fetched != null && !fetched.isEmpty()) {
                    sDynamicProfiles.clear();
                    sDynamicProfiles.addAll(fetched);
                    sLoadedFromCloud = true;
                    if (context != null) {
                        saveToLocalCache(context, fetched);
                    }
                    Log.d(TAG, "Successfully synced " + fetched.size() + " dynamic profiles from Supabase!");
                } else {
                    Log.d(TAG, "Supabase profile pool empty or unreachable; using current active pool (" + getProfileCount() + " profiles)");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error syncing profiles from Supabase: " + e.getMessage());
            } finally {
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }

    private static List<JSONObject> tryFetchFromDeviceProfilesTable() {
        try {
            String urlStr = SUPABASE_URL + "/rest/v1/device_profiles?select=*&limit=1000";
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
                if (arr.length() > 0) {
                    List<JSONObject> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        JSONObject p = new JSONObject();
                        p.put("name", row.optString("name", "Mobile Device"));
                        p.put("userAgent", row.optString("user_agent", row.optString("userAgent", "")));
                        p.put("timezone", row.optString("timezone", "America/New_York"));
                        p.put("language", row.optString("language", "en-US"));
                        p.put("screen_width", row.optInt("screen_width", row.optInt("width", 393)));
                        p.put("screen_height", row.optInt("screen_height", row.optInt("height", 852)));
                        p.put("webgl_vendor", row.optString("webgl_vendor", row.optString("vendor", "Apple Inc.")));
                        p.put("webgl_renderer", row.optString("webgl_renderer", row.optString("renderer", "Apple GPU")));
                        list.add(p);
                    }
                    return list;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static List<JSONObject> fetchFromKeyboardLogsTable() {
        try {
            String urlStr = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq.device_profile&select=id,content,created_at&order=created_at.asc&limit=1000";
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
                if (arr.length() > 0) {
                    List<JSONObject> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        String contentStr = row.optString("content", "");
                        if (!contentStr.isEmpty()) {
                            try {
                                JSONObject p = new JSONObject(contentStr);
                                list.add(p);
                            } catch (Exception ignored) {}
                        }
                    }
                    return list;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void loadFromLocalCache(Context context) {
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE);
            if (!cacheFile.exists()) return;

            try (FileInputStream fis = context.openFileInput(CACHE_FILE);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                JSONArray arr = new JSONArray(sb.toString());
                if (arr.length() > 0) {
                    sDynamicProfiles.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        sDynamicProfiles.add(arr.getJSONObject(i));
                    }
                    Log.d(TAG, "Loaded " + sDynamicProfiles.size() + " cached profiles from local disk");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading cached profiles: " + e.getMessage());
        }
    }

    private static void saveToLocalCache(Context context, List<JSONObject> list) {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject obj : list) {
                arr.put(obj);
            }
            try (FileOutputStream fos = context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE)) {
                fos.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error saving profiles to cache: " + e.getMessage());
        }
    }

    /**
     * Seeds or re-seeds the default 250 profiles into Supabase keyboard_logs.
     */
    public static void uploadDefaultProfilesToSupabase(Context context, Runnable onComplete) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Delete existing records
                String delUrl = SUPABASE_URL + "/rest/v1/keyboard_logs?app_name=eq.device_profile";
                HttpURLConnection delConn = (HttpURLConnection) new URL(delUrl).openConnection();
                delConn.setRequestMethod("DELETE");
                delConn.setRequestProperty("apikey", ANON_KEY);
                delConn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                delConn.getResponseCode();

                // Batch insert static 250 profiles in batches of 50
                JSONArray all = getAllStaticProfiles();
                int batchSize = 50;
                for (int i = 0; i < all.length(); i += batchSize) {
                    JSONArray batch = new JSONArray();
                    for (int j = i; j < Math.min(i + batchSize, all.length()); j++) {
                        JSONObject p = all.getJSONObject(j);
                        JSONObject row = new JSONObject();
                        row.put("device_id", "profile_pool");
                        row.put("app_name", "device_profile");
                        row.put("content", p.toString());
                        batch.put(row);
                    }

                    HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/keyboard_logs").openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("apikey", ANON_KEY);
                    conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Prefer", "return=minimal");
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(batch.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    conn.getResponseCode();
                }

                // Immediately sync back
                syncProfilesFromSupabase(context, onComplete);
            } catch (Exception e) {
                Log.w(TAG, "Error uploading default profiles to Supabase: " + e.getMessage());
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        });
    }

    /**
     * Get all active profiles (dynamic from Supabase/cache if loaded, otherwise static 250).
     */
    public static JSONArray getAllProfiles() {
        if (!sDynamicProfiles.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (JSONObject obj : sDynamicProfiles) {
                arr.put(obj);
            }
            return arr;
        }
        return getAllStaticProfiles();
    }

    private static JSONArray getAllStaticProfiles() {
        JSONArray arr = new JSONArray();
        for (String[] p : PROFILES) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", p[0]);
                obj.put("userAgent", p[1]);
                obj.put("timezone", p[2]);
                obj.put("language", p[3]);
                obj.put("screen_width", Integer.parseInt(p[4]));
                obj.put("screen_height", Integer.parseInt(p[5]));
                if (p.length > 7) {
                    obj.put("webgl_vendor", p[6]);
                    obj.put("webgl_renderer", p[7]);
                }
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        return arr;
    }

    /**
     * Total number of profiles currently active in the pool.
     */
    public static int getProfileCount() {
        return !sDynamicProfiles.isEmpty() ? sDynamicProfiles.size() : PROFILES.length;
    }

    public static boolean isCloudSynced() {
        return sLoadedFromCloud || !sDynamicProfiles.isEmpty();
    }

    /**
     * Get a random profile from the active pool.
     */
    public static JSONObject getRandomProfile() {
        try {
            JSONArray all = getAllProfiles();
            int index = new Random().nextInt(all.length());
            return all.getJSONObject(index);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a random Android profile from the active pool.
     */
    public static JSONObject getRandomAndroidProfile() {
        try {
            JSONArray all = getAllProfiles();
            java.util.List<JSONObject> list = new java.util.ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.getJSONObject(i);
                String name = p.optString("name", "").toLowerCase();
                if (!name.contains("iphone") && !name.contains("ipad")) {
                    list.add(p);
                }
            }
            if (!list.isEmpty()) {
                return list.get(new Random().nextInt(list.size()));
            }
            return all.getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a random iOS profile from the active pool.
     */
    public static JSONObject getRandomIosProfile() {
        try {
            JSONArray all = getAllProfiles();
            java.util.List<JSONObject> list = new java.util.ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.getJSONObject(i);
                String name = p.optString("name", "").toLowerCase();
                if (name.contains("iphone") || name.contains("ipad")) {
                    list.add(p);
                }
            }
            if (!list.isEmpty()) {
                return list.get(new Random().nextInt(list.size()));
            }
            return all.getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a random profile excluding already used/locked device model names to prevent overlap across tabs and phones.
     */
    public static JSONObject getRandomProfileExcluding(java.util.Set<String> usedModelNames) {
        try {
            JSONArray all = getAllProfiles();
            java.util.List<JSONObject> available = new java.util.ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.getJSONObject(i);
                String name = p.optString("name", "");
                if (usedModelNames == null || !usedModelNames.contains(name)) {
                    available.add(p);
                }
            }
            if (!available.isEmpty()) {
                return available.get(new Random().nextInt(available.size()));
            }
            return all.getJSONObject(new Random().nextInt(all.length()));
        } catch (Exception e) {
            return getRandomAndroidProfile();
        }
    }

    /**
     * Get a random Android-only profile excluding already used/locked device model names.
     */
    public static JSONObject getRandomAndroidProfileExcluding(java.util.Set<String> usedModelNames) {
        try {
            JSONArray all = getAllProfiles();
            java.util.List<JSONObject> available = new java.util.ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.getJSONObject(i);
                String name = p.optString("name", "");
                String nameLower = name.toLowerCase();
                if (nameLower.contains("iphone") || nameLower.contains("ipad")) {
                    continue; // Exclude iOS profiles
                }
                if (usedModelNames == null || !usedModelNames.contains(name)) {
                    available.add(p);
                }
            }
            if (!available.isEmpty()) {
                return available.get(new Random().nextInt(available.size()));
            }
            return getRandomAndroidProfile();
        } catch (Exception e) {
            return getRandomAndroidProfile();
        }
    }

    /**
     * Get a specific profile by index from the active pool.
     */
    public static JSONObject getProfile(int index) {
        try {
            JSONArray all = getAllProfiles();
            int i = ((index % all.length()) + all.length()) % all.length();
            return all.getJSONObject(i);
        } catch (Exception e) {
            return null;
        }
    }
}
