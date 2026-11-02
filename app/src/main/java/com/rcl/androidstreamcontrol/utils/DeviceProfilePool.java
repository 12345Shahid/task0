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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String CACHE_FILE = "device_profiles_cache_v55.json";

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
         "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 9",
         "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G715"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 8a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC10"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7 Pro",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7",
         "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 7a",
         "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel Fold",
         "Mozilla/5.0 (Linux; Android 14; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel Fold",
         "Mozilla/5.0 (Linux; Android 14; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel Fold",
         "Mozilla/5.0 (Linux; Android 14; Pixel Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G710"},
        {"Pixel 6 Pro",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6 Pro",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6 Pro",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 6a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 6a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "ARM", "Mali-G78"},
        {"Pixel 5",
         "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Pixel 5",
         "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Pixel 5a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 5a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Pixel 5a",
         "Mozilla/5.0 (Linux; Android 13; Pixel 5a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 620"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24+",
         "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24+",
         "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24+",
         "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24",
         "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24",
         "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S24",
         "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 Ultra",
         "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23+",
         "Mozilla/5.0 (Linux; Android 13; SM-S916B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23+",
         "Mozilla/5.0 (Linux; Android 13; SM-S916B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23+",
         "Mozilla/5.0 (Linux; Android 13; SM-S916B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23",
         "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23",
         "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23",
         "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy S23 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-S711B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S23 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-S711B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S23 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-S711B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22+",
         "Mozilla/5.0 (Linux; Android 12; SM-S906B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22+",
         "Mozilla/5.0 (Linux; Android 12; SM-S906B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22",
         "Mozilla/5.0 (Linux; Android 12; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22",
         "Mozilla/5.0 (Linux; Android 12; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S22",
         "Mozilla/5.0 (Linux; Android 12; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy S21 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21 FE",
         "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21",
         "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy S21",
         "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"Samsung Galaxy Note 20 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 650"},
        {"Samsung Galaxy Note 20 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 650"},
        {"Samsung Galaxy Note 20 Ultra",
         "Mozilla/5.0 (Linux; Android 13; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 650"},
        {"Samsung Galaxy Z Fold5",
         "Mozilla/5.0 (Linux; Android 14; SM-F946B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Fold5",
         "Mozilla/5.0 (Linux; Android 14; SM-F946B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Fold5",
         "Mozilla/5.0 (Linux; Android 14; SM-F946B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Flip5",
         "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Flip5",
         "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Flip5",
         "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Z Fold4",
         "Mozilla/5.0 (Linux; Android 13; SM-F936B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Z Fold4",
         "Mozilla/5.0 (Linux; Android 13; SM-F936B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Z Flip4",
         "Mozilla/5.0 (Linux; Android 13; SM-F721B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Z Flip4",
         "Mozilla/5.0 (Linux; Android 13; SM-F721B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Tab S9",
         "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Boise", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Tab S9",
         "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/New_York", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 740"},
        {"Samsung Galaxy Tab S8",
         "Mozilla/5.0 (Linux; Android 12; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Detroit", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy Tab S8",
         "Mozilla/5.0 (Linux; Android 12; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "820", "1180", "Qualcomm", "Adreno (TM) 730"},
        {"Samsung Galaxy A54",
         "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy A54",
         "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy A54",
         "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy A53",
         "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G68 MP4"},
        {"Samsung Galaxy A53",
         "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G68 MP4"},
        {"Samsung Galaxy A52",
         "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 618"},
        {"Samsung Galaxy A52",
         "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 618"},
        {"Samsung Galaxy A34",
         "Mozilla/5.0 (Linux; Android 13; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "ARM", "Mali-G68 MC4"},
        {"Samsung Galaxy A34",
         "Mozilla/5.0 (Linux; Android 13; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "ARM", "Mali-G68 MC4"},
        {"Samsung Galaxy A34",
         "Mozilla/5.0 (Linux; Android 13; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G68 MC4"},
        {"Samsung Galaxy A14",
         "Mozilla/5.0 (Linux; Android 13; SM-A145R) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G52 MC2"},
        {"Samsung Galaxy A14",
         "Mozilla/5.0 (Linux; Android 13; SM-A145R) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G52 MC2"},
        {"Samsung Galaxy M54",
         "Mozilla/5.0 (Linux; Android 13; SM-M546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"Samsung Galaxy M54",
         "Mozilla/5.0 (Linux; Android 13; SM-M546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G68 MP5"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12",
         "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"OnePlus 12R",
         "Mozilla/5.0 (Linux; Android 14; CPH2609) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 12R",
         "Mozilla/5.0 (Linux; Android 14; CPH2609) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 12R",
         "Mozilla/5.0 (Linux; Android 14; CPH2609) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus Open",
         "Mozilla/5.0 (Linux; Android 14; CPH2551) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus Open",
         "Mozilla/5.0 (Linux; Android 14; CPH2551) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus Open",
         "Mozilla/5.0 (Linux; Android 14; CPH2551) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 11",
         "Mozilla/5.0 (Linux; Android 13; PHB110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"OnePlus 10 Pro",
         "Mozilla/5.0 (Linux; Android 12; NE2213) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10 Pro",
         "Mozilla/5.0 (Linux; Android 12; NE2213) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10 Pro",
         "Mozilla/5.0 (Linux; Android 12; NE2213) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10T",
         "Mozilla/5.0 (Linux; Android 13; CPH2415) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 10T",
         "Mozilla/5.0 (Linux; Android 13; CPH2415) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 730"},
        {"OnePlus 9 Pro",
         "Mozilla/5.0 (Linux; Android 12; LE2123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"OnePlus 9 Pro",
         "Mozilla/5.0 (Linux; Android 12; LE2123) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 660"},
        {"OnePlus Nord 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2491) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "ARM", "Mali-G710 MC10"},
        {"OnePlus Nord 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2491) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "ARM", "Mali-G710 MC10"},
        {"OnePlus Nord 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2491) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G710 MC10"},
        {"OnePlus Nord CE 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2569) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 642L"},
        {"OnePlus Nord CE 3",
         "Mozilla/5.0 (Linux; Android 13; CPH2569) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 642L"},
        {"OnePlus Nord N30",
         "Mozilla/5.0 (Linux; Android 13; CPH2513) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 619"},
        {"OnePlus Nord N30",
         "Mozilla/5.0 (Linux; Android 13; CPH2513) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 619"},
        {"Xiaomi Redmi Note 11",
         "Mozilla/5.0 (Linux; Android 13; 2201117TG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Xiaomi Redmi Note 11",
         "Mozilla/5.0 (Linux; Android 13; 2201117TG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Samsung Galaxy A23",
         "Mozilla/5.0 (Linux; Android 13; SM-A235F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Samsung Galaxy A23",
         "Mozilla/5.0 (Linux; Android 13; SM-A235F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Xiaomi Redmi 10C",
         "Mozilla/5.0 (Linux; Android 13; 220333QAG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Xiaomi Redmi 10C",
         "Mozilla/5.0 (Linux; Android 13; 220333QAG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Motorola Moto G52",
         "Mozilla/5.0 (Linux; Android 13; XT2221-1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Motorola Moto G52",
         "Mozilla/5.0 (Linux; Android 13; XT2221-1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Motorola Moto G32",
         "Mozilla/5.0 (Linux; Android 13; XT2235-2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Motorola Moto G32",
         "Mozilla/5.0 (Linux; Android 13; XT2235-2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Realme 9",
         "Mozilla/5.0 (Linux; Android 13; RMX3521) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Oppo A96",
         "Mozilla/5.0 (Linux; Android 13; CPH2333) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Vivo Y35",
         "Mozilla/5.0 (Linux; Android 13; V2205) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Honor X8",
         "Mozilla/5.0 (Linux; Android 13; TFY-LX1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 610"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Detroit", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Chicago", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Ultra",
         "Mozilla/5.0 (Linux; Android 14; 24031PN0DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Denver", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "414", "896", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14",
         "Mozilla/5.0 (Linux; Android 14; 23127PN0CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14",
         "Mozilla/5.0 (Linux; Android 14; 23127PN0CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 14",
         "Mozilla/5.0 (Linux; Android 14; 23127PN0CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Xiaomi 13 Ultra",
         "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Ultra",
         "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Ultra",
         "Mozilla/5.0 (Linux; Android 13; 2304FPN6DC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Pro",
         "Mozilla/5.0 (Linux; Android 13; 2210132G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13 Pro",
         "Mozilla/5.0 (Linux; Android 13; 2210132G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13",
         "Mozilla/5.0 (Linux; Android 13; 2211133G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13",
         "Mozilla/5.0 (Linux; Android 13; 2211133G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 740"},
        {"Xiaomi 13T Pro",
         "Mozilla/5.0 (Linux; Android 13; 23078PND5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Anchorage", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC11"},
        {"Xiaomi 13T Pro",
         "Mozilla/5.0 (Linux; Android 13; 23078PND5G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Honolulu", "en-US", "412", "915", "ARM", "Mali-G715-Immortalis MC11"},
        {"Xiaomi Redmi Note 13 Pro",
         "Mozilla/5.0 (Linux; Android 14; 2404) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Puerto_Rico", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 710"},
        {"Xiaomi Redmi Note 13 Pro",
         "Mozilla/5.0 (Linux; Android 14; 2404) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Boise", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 710"},
        {"Xiaomi Redmi Note 13 Pro",
         "Mozilla/5.0 (Linux; Android 14; 2404) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/New_York", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 710"},
        {"Redmi Note 13 Pro+",
         "Mozilla/5.0 (Linux; Android 13; 23090RA98G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Detroit", "en-US", "412", "915", "ARM", "Mali-G610 MC4"},
        {"Redmi Note 13 Pro+",
         "Mozilla/5.0 (Linux; Android 13; 23090RA98G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
         "America/Indiana/Indianapolis", "en-US", "412", "915", "ARM", "Mali-G610 MC4"},
        {"Redmi Note 13 Pro+",
         "Mozilla/5.0 (Linux; Android 13; 23090RA98G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
         "America/Chicago", "en-US", "412", "915", "ARM", "Mali-G610 MC4"},
        {"Redmi K70 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23117RK66C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Mobile Safari/537.36",
         "America/Denver", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Redmi K70 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23117RK66C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.201 Mobile Safari/537.36",
         "America/Phoenix", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Redmi K70 Pro",
         "Mozilla/5.0 (Linux; Android 14; 23117RK66C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36",
         "America/Los_Angeles", "en-US", "412", "915", "Qualcomm", "Adreno (TM) 750"},
        {"Redmi K70",
         "Mozilla/5.0 (Linux; Android 14; 2311DRK48C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
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
            detectNativeGpu(context);
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
                        String pName = row.optString("name", "Mobile Device");
                        p.put("name", pName);
                        p.put("userAgent", row.optString("user_agent", row.optString("userAgent", "")));
                        p.put("timezone", row.optString("timezone", "America/New_York"));
                        p.put("language", row.optString("language", "en-US"));
                        p.put("screen_width", row.optInt("screen_width", row.optInt("width", 393)));
                        p.put("screen_height", row.optInt("screen_height", row.optInt("height", 852)));
                        String v = row.optString("webgl_vendor", row.optString("vendor", ""));
                        if (v.isEmpty()) v = deduceWebglVendor(pName);
                        String r = row.optString("webgl_renderer", row.optString("renderer", ""));
                        if (r.isEmpty()) r = deduceWebglRenderer(pName);
                        p.put("webgl_vendor", v);
                        p.put("webgl_renderer", r);
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
                                String pName = p.optString("name", "");
                                if (!p.has("webgl_vendor") || p.optString("webgl_vendor").isEmpty()) {
                                    p.put("webgl_vendor", deduceWebglVendor(pName));
                                }
                                if (!p.has("webgl_renderer") || p.optString("webgl_renderer").isEmpty()) {
                                    p.put("webgl_renderer", deduceWebglRenderer(pName));
                                }
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
                        JSONObject p = arr.getJSONObject(i);
                        String pName = p.optString("name", "");
                        if (!p.has("webgl_vendor") || p.optString("webgl_vendor").isEmpty()) {
                            p.put("webgl_vendor", deduceWebglVendor(pName));
                        }
                        if (!p.has("webgl_renderer") || p.optString("webgl_renderer").isEmpty()) {
                            p.put("webgl_renderer", deduceWebglRenderer(pName));
                        }
                        sDynamicProfiles.add(p);
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
        String renderer = getDetectedNativeGpuRenderer();
        boolean is610 = (renderer != null && renderer.contains("610"));

        if (!sDynamicProfiles.isEmpty()) {
            if (is610) {
                int count610 = 0;
                for (JSONObject p : sDynamicProfiles) {
                    String r = p.optString("webgl_renderer", "");
                    if (r.contains("610") || isAdreno610Device(p.optString("name", ""))) {
                        count610++;
                    }
                }
                if (count610 >= 10) {
                    JSONArray arr = new JSONArray();
                    for (JSONObject obj : sDynamicProfiles) arr.put(obj);
                    return arr;
                }
                // Merge dynamic profiles with static Adreno 610 profiles so Adreno 610 is always available
                JSONArray staticArr = getAllStaticProfiles();
                JSONArray merged = new JSONArray();
                for (JSONObject obj : sDynamicProfiles) merged.put(obj);
                for (int i = 0; i < staticArr.length(); i++) {
                    JSONObject sp = staticArr.optJSONObject(i);
                    if (sp != null) {
                        String r = sp.optString("webgl_renderer", "");
                        if (r.contains("610") || isAdreno610Device(sp.optString("name", ""))) {
                            merged.put(sp);
                        }
                    }
                }
                return merged;
            } else {
                JSONArray arr = new JSONArray();
                for (JSONObject obj : sDynamicProfiles) {
                    arr.put(obj);
                }
                return arr;
            }
        }
        return getAllStaticProfiles();
    }

    private static java.util.List<JSONObject> sExpandedProfiles = null;

    public static synchronized JSONArray getAllStaticProfiles() {
        if (sExpandedProfiles != null && sExpandedProfiles.size() >= 600) {
            JSONArray arr = new JSONArray();
            for (JSONObject p : sExpandedProfiles) arr.put(p);
            return arr;
        }

        java.util.List<JSONObject> list = new java.util.ArrayList<>();
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
                } else {
                    obj.put("webgl_vendor", deduceWebglVendor(p[0]));
                    obj.put("webgl_renderer", deduceWebglRenderer(p[0]));
                }
                Matcher mm = Pattern.compile("Android\\s+[0-9]+(?:\\.[0-9]+)*;\\s*([^;\\)]+)").matcher(p[1]);
                if (mm.find()) {
                    String token = mm.group(1).trim();
                    if (token.contains(" Build/")) token = token.substring(0, token.indexOf(" Build/")).trim();
                    if (!token.isEmpty()) obj.put("model", token);
                }
                list.add(obj);
            } catch (Exception ignored) {}
        }

        generateAdditionalAndroidProfiles(list, 600);
        sExpandedProfiles = list;

        JSONArray arr = new JSONArray();
        for (JSONObject p : sExpandedProfiles) arr.put(p);
        return arr;
    }

    private static void generateAdditionalAndroidProfiles(java.util.List<JSONObject> list, int targetTotal) {
        String[][] models = {
                {"Samsung Galaxy S24 Ultra", "SM-S928U", "412", "915", "Qualcomm", "Adreno (TM) 750"},
                {"Samsung Galaxy S24+", "SM-S926U", "384", "854", "Qualcomm", "Adreno (TM) 750"},
                {"Samsung Galaxy S24", "SM-S921U", "360", "800", "Qualcomm", "Adreno (TM) 750"},
                {"Samsung Galaxy S23 Ultra", "SM-S918U", "412", "915", "Qualcomm", "Adreno (TM) 740"},
                {"Samsung Galaxy S23+", "SM-S916U", "384", "854", "Qualcomm", "Adreno (TM) 740"},
                {"Samsung Galaxy S23", "SM-S911U", "360", "800", "Qualcomm", "Adreno (TM) 740"},
                {"Samsung Galaxy Z Fold5", "SM-F946U", "412", "892", "Qualcomm", "Adreno (TM) 740"},
                {"Samsung Galaxy Z Flip5", "SM-F731U", "412", "960", "Qualcomm", "Adreno (TM) 740"},
                {"Samsung Galaxy A54 5G", "SM-A546U", "412", "915", "ARM", "Mali-G68 MP5"},
                {"Samsung Galaxy A55 5G", "SM-A556U", "412", "915", "ARM", "Mali-G68 MP5"},
                {"Google Pixel 9 Pro XL", "Pixel 9 Pro XL", "412", "915", "ARM", "Mali-G715 Immortalis"},
                {"Google Pixel 9 Pro", "Pixel 9 Pro", "393", "851", "ARM", "Mali-G715 Immortalis"},
                {"Google Pixel 9", "Pixel 9", "393", "851", "ARM", "Mali-G715 Immortalis"},
                {"Google Pixel 8 Pro", "Pixel 8 Pro", "412", "915", "ARM", "Mali-G715 Immortalis"},
                {"Google Pixel 8", "Pixel 8", "393", "851", "ARM", "Mali-G715 Immortalis"},
                {"Google Pixel 8a", "Pixel 8a", "393", "851", "ARM", "Mali-G715 Immortalis"},
                {"Google Pixel 7 Pro", "Pixel 7 Pro", "412", "892", "ARM", "Mali-G710 MC10"},
                {"Google Pixel 7", "Pixel 7", "393", "851", "ARM", "Mali-G710 MC10"},
                {"Google Pixel Fold", "Pixel Fold", "412", "892", "ARM", "Mali-G710 MC10"},
                {"OnePlus 12", "CPH2583", "412", "915", "Qualcomm", "Adreno (TM) 750"},
                {"OnePlus 12R", "CPH2609", "393", "851", "Qualcomm", "Adreno (TM) 740"},
                {"OnePlus Open", "CPH2551", "412", "892", "Qualcomm", "Adreno (TM) 740"},
                {"OnePlus 11", "CPH2449", "412", "915", "Qualcomm", "Adreno (TM) 740"},
                {"OnePlus 10 Pro", "NE2215", "412", "915", "Qualcomm", "Adreno (TM) 730"},
                {"Xiaomi 14 Ultra", "24030PN60G", "412", "915", "Qualcomm", "Adreno (TM) 750"},
                {"Xiaomi 14 Pro", "23116PN5BC", "412", "915", "Qualcomm", "Adreno (TM) 750"},
                {"Xiaomi 14", "23127PN0CG", "393", "851", "Qualcomm", "Adreno (TM) 750"},
                {"Xiaomi 13 Ultra", "2304FPN6DC", "412", "915", "Qualcomm", "Adreno (TM) 740"},
                {"Xiaomi 13 Pro", "2210132G", "412", "915", "Qualcomm", "Adreno (TM) 740"},
                {"Motorola Edge 40 Pro", "motorola edge 40 pro", "393", "851", "Qualcomm", "Adreno (TM) 740"},
                {"Motorola Razr 40 Ultra", "motorola razr 40 ultra", "412", "960", "Qualcomm", "Adreno (TM) 730"},
                {"Sony Xperia 1 V", "XQ-DQ72", "384", "854", "Qualcomm", "Adreno (TM) 740"},
                {"Sony Xperia 5 V", "XQ-DE72", "384", "854", "Qualcomm", "Adreno (TM) 740"},
                {"Nothing Phone (2)", "A065", "393", "851", "Qualcomm", "Adreno (TM) 730"},
                {"Xiaomi 13T Pro", "23078PND5G", "412", "915", "ARM", "Mali-G715-Immortalis MC11"},
                {"Xiaomi 13T", "2306EPN60G", "412", "915", "ARM", "Mali-G610 MC4"},
                {"Xiaomi Redmi K60 Ultra", "23078RKD5C", "412", "915", "ARM", "Mali-G715-Immortalis MC11"},
                {"OnePlus Nord 3", "CPH2493", "412", "915", "ARM", "Mali-G710 MC10"},
                // Authentic Qualcomm Snapdragon 680 / Adreno (TM) 610 devices
                {"Xiaomi Redmi Note 11", "2201117TG", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Xiaomi Redmi Note 11 NFC", "2201117TY", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Samsung Galaxy A23", "SM-A235F", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Samsung Galaxy A23 4G", "SM-A235M", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Xiaomi Redmi 10C", "220333QAG", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Motorola Moto G52", "XT2221-1", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Motorola Moto G52 Dual", "XT2221-2", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Motorola Moto G32", "XT2235-2", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Realme 9", "RMX3521", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Realme 9i", "RMX3491", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Oppo A96", "CPH2333", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Oppo A76", "CPH2375", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Vivo Y35", "V2205", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Vivo Y36 4G", "V2247", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Vivo Y22s", "V2206", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Honor X8", "TFY-LX1", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Honor X7", "CMA-LX1", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Xiaomi Poco M3", "M2010J19CG", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Xiaomi Redmi 9T", "M2010J19SG", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Motorola Moto G Power", "XT2041DL", "412", "915", "Qualcomm", "Adreno (TM) 610"},
                {"Realme 7i", "RMX2103", "412", "915", "Qualcomm", "Adreno (TM) 610"}
        };

        String[] chromeVersions = {
                "124.0.6367.113", "124.0.6367.179", "125.0.6422.112",
                "125.0.6422.165", "126.0.6478.71", "126.0.6478.122",
                "127.0.6533.64", "128.0.6613.88"
        };

        String[] androidVersions = {"14; K", "13; K", "15; K"};

        String activeProxyTz = WebViewProxyManager.getLastResolvedTimezone();
        String defaultTz = (activeProxyTz != null && !activeProxyTz.trim().isEmpty()) ? activeProxyTz.trim() : "America/New_York";
        String[] timezones = { defaultTz };

        int count = list.size();
        int mIdx = 0;
        int cvIdx = 0;
        int avIdx = 0;
        int tzIdx = 0;

        while (count < targetTotal) {
            String[] m = models[mIdx % models.length];
            String cv = chromeVersions[cvIdx % chromeVersions.length];
            String av = androidVersions[avIdx % androidVersions.length];
            String tz = timezones[tzIdx % timezones.length];

            String ua = "Mozilla/5.0 (Linux; Android " + av + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + cv + " Mobile Safari/537.36";

            try {
                JSONObject obj = new JSONObject();
                obj.put("name", m[0]);
                obj.put("model", m[1]);
                obj.put("userAgent", ua);
                obj.put("timezone", tz);
                obj.put("language", "en-US");
                obj.put("screen_width", Integer.parseInt(m[2]));
                obj.put("screen_height", Integer.parseInt(m[3]));
                obj.put("webgl_vendor", m[4]);
                obj.put("webgl_renderer", m[5]);
                list.add(obj);
                count++;
            } catch (Exception ignored) {}

            mIdx++;
            cvIdx++;
            avIdx++;
            tzIdx++;
        }
    }

    /**
     * Total number of profiles currently active in the pool.
     */
    public static int getProfileCount() {
        return getAllProfiles().length();
    }

    public static boolean isCloudSynced() {
        return sLoadedFromCloud || !sDynamicProfiles.isEmpty();
    }

    /**
     * Get a random profile from the active pool.
     */
    public static JSONObject getRandomProfile() {
        return getRandomAndroidProfile();
    }

    public static boolean isAdreno610Device(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("redmi note 11") || lower.contains("a23") || lower.contains("moto g52") ||
                lower.contains("g32") || lower.contains("redmi 10c") || lower.contains("realme 9") ||
                lower.contains("a96") || lower.contains("a76") || lower.contains("y35") ||
                lower.contains("y36") || lower.contains("y22s") || lower.contains("honor x8") ||
                lower.contains("honor x7") || lower.contains("poco m3") || lower.contains("redmi 9t") ||
                lower.contains("g power") || lower.contains("realme 7i") || lower.contains("sm-t500") ||
                lower.contains("2201117") || lower.contains("sm-a235") || lower.contains("220333") ||
                lower.contains("xt2221") || lower.contains("xt2235") || lower.contains("rmx3521") ||
                lower.contains("rmx3491") || lower.contains("cph2333") || lower.contains("cph2375") ||
                lower.contains("v2205") || lower.contains("v2247") || lower.contains("v2206") ||
                lower.contains("tfy-lx1") || lower.contains("cma-lx1") || lower.contains("m2010j19") ||
                lower.contains("xt2041") || lower.contains("rmx2103");
    }

    public static String deduceWebglVendor(String name) {
        if (name == null) return "Qualcomm";
        if (isAdreno610Device(name)) return "Qualcomm";
        String lower = name.toLowerCase();
        if (lower.contains("iphone") || lower.contains("ipad")) return "Apple Inc.";
        if (lower.contains("13t") || lower.contains("dimensity") || lower.contains("k60 ultra") ||
                lower.contains("pixel 9") || lower.contains("pixel 8") || lower.contains("pixel 7") ||
                lower.contains("pixel 6") || lower.contains("pixel fold") || lower.contains("nord 3") ||
                lower.contains("nord 2") || lower.contains("a54") || lower.contains("a55") ||
                lower.contains("a34") || lower.contains("a14") || lower.contains("m54") ||
                lower.contains("poco x6") || lower.contains("note 13") || lower.contains("v29") ||
                lower.contains("reno10") || lower.contains("infinix") || lower.contains("blade") ||
                lower.contains("zte") || lower.contains("mali")) {
            return "ARM";
        }
        return "Qualcomm";
    }

    public static String deduceWebglRenderer(String name) {
        if (name == null) return "Adreno (TM) 610";
        if (isAdreno610Device(name)) return "Adreno (TM) 610";
        String lower = name.toLowerCase();
        if (lower.contains("iphone") || lower.contains("ipad")) return "Apple GPU";
        if (lower.contains("13t pro") || lower.contains("k60 ultra") || lower.contains("dimensity 9200")) return "Mali-G715-Immortalis MC11";
        if (lower.contains("pixel 9") || lower.contains("pixel 8")) return "Mali-G715-Immortalis MC10";
        if (lower.contains("pixel 7") || lower.contains("pixel 6") || lower.contains("pixel fold")) return "Mali-G710";
        if (lower.contains("s24") || lower.contains("oneplus 12") || lower.contains("xiaomi 14")) return "Adreno (TM) 750";
        if (lower.contains("s23") || lower.contains("oneplus 11") || lower.contains("fold5") || lower.contains("redmagic 8") || lower.contains("magic5") || lower.contains("find x6") || lower.contains("k60")) return "Adreno (TM) 740";
        if (lower.contains("s22") || lower.contains("edge 40") || lower.contains("10 pro") || lower.contains("rog phone 7")) return "Adreno (TM) 730";
        if (lower.contains("s21") || lower.contains("s21 fe")) return "Adreno (TM) 660";
        if (lower.contains("a54") || lower.contains("a55") || lower.contains("a34")) return "Mali-G68";
        if (lower.contains("poco x6") || lower.contains("nord 3") || lower.contains("note 13")) return "Mali-G610";
        if (lower.contains("nord n30") || lower.contains("n30")) return "Adreno (TM) 619";
        return "Adreno (TM) 610";
    }

    private static volatile String sDetectedNativeGpuVendor = "Qualcomm";
    private static volatile String sDetectedNativeGpuRenderer = "Adreno (TM) 610";
    private static volatile boolean sGpuDetectionCompleted = false;

    public static String getDetectedNativeGpuVendor() {
        if (!sGpuDetectionCompleted) {
            detectNativeGpu(null);
        }
        return sDetectedNativeGpuVendor;
    }

    public static String getDetectedNativeGpuRenderer() {
        if (!sGpuDetectionCompleted) {
            detectNativeGpu(null);
        }
        return sDetectedNativeGpuRenderer;
    }

    public static synchronized void detectNativeGpu(Context context) {
        if (sGpuDetectionCompleted) return;
        sGpuDetectionCompleted = true;
        try {
            android.opengl.EGLDisplay dpy = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY);
            int[] vers = new int[2];
            android.opengl.EGL14.eglInitialize(dpy, vers, 0, vers, 1);
            int[] configAttr = {
                android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
                android.opengl.EGL14.EGL_NONE
            };
            android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
            int[] numConfig = new int[1];
            android.opengl.EGL14.eglChooseConfig(dpy, configAttr, 0, configs, 0, 1, numConfig, 0);
            int[] ctxAttr = {
                android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                android.opengl.EGL14.EGL_NONE
            };
            android.opengl.EGLContext ctx = android.opengl.EGL14.eglCreateContext(dpy, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
            int[] surfAttr = {
                android.opengl.EGL14.EGL_WIDTH, 1,
                android.opengl.EGL14.EGL_HEIGHT, 1,
                android.opengl.EGL14.EGL_NONE
            };
            android.opengl.EGLSurface surf = android.opengl.EGL14.eglCreatePbufferSurface(dpy, configs[0], surfAttr, 0);
            android.opengl.EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
            String v = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR);
            String r = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            android.opengl.EGL14.eglMakeCurrent(dpy, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT);
            android.opengl.EGL14.eglDestroySurface(dpy, surf);
            android.opengl.EGL14.eglDestroyContext(dpy, ctx);
            android.opengl.EGL14.eglTerminate(dpy);
            if (v != null && !v.trim().isEmpty()) sDetectedNativeGpuVendor = v.trim();
            if (r != null && !r.trim().isEmpty()) sDetectedNativeGpuRenderer = r.trim();
        } catch (Throwable ignored) {}

        if (sDetectedNativeGpuVendor == null || sDetectedNativeGpuVendor.isEmpty()) {
            String hw = (android.os.Build.HARDWARE != null ? android.os.Build.HARDWARE : "").toLowerCase();
            String board = (android.os.Build.BOARD != null ? android.os.Build.BOARD : "").toLowerCase();
            String model = (android.os.Build.MODEL != null ? android.os.Build.MODEL : "").toLowerCase();
            String device = (android.os.Build.DEVICE != null ? android.os.Build.DEVICE : "").toLowerCase();
            String product = (android.os.Build.PRODUCT != null ? android.os.Build.PRODUCT : "").toLowerCase();
            String soc = "";
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                soc = (android.os.Build.SOC_MODEL != null ? android.os.Build.SOC_MODEL : "").toLowerCase();
            }
            String combined = hw + " " + board + " " + model + " " + device + " " + product + " " + soc;
            if (combined.contains("mt") || combined.contains("dimensity") || combined.contains("mali") ||
                    combined.contains("corot") || combined.contains("13t") || combined.contains("23078") ||
                    combined.contains("tensor") || combined.contains("exynos")) {
                sDetectedNativeGpuVendor = "ARM";
                sDetectedNativeGpuRenderer = "Mali-G715-Immortalis MC11";
            } else {
                sDetectedNativeGpuVendor = "Qualcomm";
                sDetectedNativeGpuRenderer = "Adreno (TM) 610";
            }
        }
        Log.d("DeviceProfilePool", "Physical Phone Native GPU: " + sDetectedNativeGpuVendor + " / " + sDetectedNativeGpuRenderer);
    }

    /**
     * Get a random Android profile aligned with physical hardware GPU architecture.
     */
    public static JSONObject getRandomAndroidProfile() {
        try {
            String rendererDetect = getDetectedNativeGpuRenderer();
            if (rendererDetect == null || rendererDetect.isEmpty()) rendererDetect = "Adreno (TM) 610";
            boolean isAdreno610 = rendererDetect.contains("610");

            JSONArray all = getAllProfiles();
            java.util.List<JSONObject> list = new java.util.ArrayList<>();
            String vendorDetect = getDetectedNativeGpuVendor();
            if (vendorDetect == null || vendorDetect.isEmpty()) vendorDetect = "Qualcomm";
            boolean matchQualcomm = vendorDetect.toLowerCase().contains("qualcomm");
            boolean matchArm = vendorDetect.toLowerCase().contains("arm");

            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.getJSONObject(i);
                String name = p.optString("name", "");
                String nameLower = name.toLowerCase();
                if (nameLower.contains("iphone") || nameLower.contains("ipad")) {
                    continue;
                }
                String vendor = p.optString("webgl_vendor", "");
                if (vendor.isEmpty()) {
                    vendor = deduceWebglVendor(name);
                }
                String renderer = p.optString("webgl_renderer", "");
                if (renderer.isEmpty()) {
                    renderer = deduceWebglRenderer(name);
                }

                if (isAdreno610) {
                    if (renderer.contains("610") || isAdreno610Device(name)) {
                        list.add(p);
                    }
                } else if (matchQualcomm) {
                    if (vendor.equalsIgnoreCase("Qualcomm") && (renderer.equalsIgnoreCase(rendererDetect) || !renderer.contains("Mali"))) {
                        list.add(p);
                    }
                } else if (matchArm) {
                    if (vendor.equalsIgnoreCase("ARM") && (renderer.equalsIgnoreCase(rendererDetect) || !renderer.contains("Adreno"))) {
                        list.add(p);
                    }
                }
            }

            if (!list.isEmpty()) {
                List<JSONObject> exactGpuMatches = new ArrayList<>();
                for (JSONObject p : list) {
                    String r = p.optString("webgl_renderer", "");
                    if (r.isEmpty()) r = deduceWebglRenderer(p.optString("name", ""));
                    if (isAdreno610) {
                        if (r.contains("610") || isAdreno610Device(p.optString("name", ""))) {
                            exactGpuMatches.add(p);
                        }
                    } else if (r.equalsIgnoreCase(rendererDetect)) {
                        exactGpuMatches.add(p);
                    }
                }
                if (!exactGpuMatches.isEmpty()) {
                    return exactGpuMatches.get(new Random().nextInt(exactGpuMatches.size()));
                }
                return list.get(new Random().nextInt(list.size()));
            }

            // Fallback: If list was empty, pull directly from static profiles
            JSONArray staticAll = getAllStaticProfiles();
            for (int i = 0; i < staticAll.length(); i++) {
                JSONObject p = staticAll.getJSONObject(i);
                String name = p.optString("name", "");
                String nameLower = name.toLowerCase();
                if (nameLower.contains("iphone") || nameLower.contains("ipad")) continue;
                String renderer = p.optString("webgl_renderer", "");
                if (renderer.isEmpty()) renderer = deduceWebglRenderer(name);

                if (isAdreno610) {
                    if (renderer.contains("610") || isAdreno610Device(name)) {
                        list.add(p);
                    }
                } else if (matchQualcomm && renderer.equalsIgnoreCase(rendererDetect)) {
                    list.add(p);
                } else if (matchArm && renderer.equalsIgnoreCase(rendererDetect)) {
                    list.add(p);
                }
            }

            if (!list.isEmpty()) {
                return list.get(new Random().nextInt(list.size()));
            }

            // Absolute guarantee: Return authentic Redmi Note 11 (Adreno 610)
            JSONObject guaranteed = new JSONObject();
            guaranteed.put("name", "Xiaomi Redmi Note 11");
            guaranteed.put("model", "2201117TG");
            guaranteed.put("userAgent", "Mozilla/5.0 (Linux; Android 13; 2201117TG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36");
            guaranteed.put("timezone", "America/New_York");
            guaranteed.put("language", "en-US");
            guaranteed.put("screen_width", 412);
            guaranteed.put("screen_height", 915);
            guaranteed.put("webgl_vendor", "Qualcomm");
            guaranteed.put("webgl_renderer", "Adreno (TM) 610");
            return guaranteed;
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
        return getRandomAndroidProfileExcluding(usedModelNames);
    }

    /**
     * Get a random Android-only profile excluding already used/locked device model names.
     */
    public static JSONObject getRandomAndroidProfileExcluding(java.util.Set<String> usedModelNames) {
        try {
            String rendererDetect = getDetectedNativeGpuRenderer();
            if (rendererDetect == null || rendererDetect.isEmpty()) rendererDetect = "Adreno (TM) 610";
            boolean isAdreno610 = rendererDetect.contains("610");

            JSONArray all = getAllProfiles();
            java.util.List<JSONObject> available = new java.util.ArrayList<>();
            String vendorDetect = getDetectedNativeGpuVendor();
            if (vendorDetect == null || vendorDetect.isEmpty()) vendorDetect = "Qualcomm";
            boolean matchQualcomm = vendorDetect.toLowerCase().contains("qualcomm");
            boolean matchArm = vendorDetect.toLowerCase().contains("arm");

            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.getJSONObject(i);
                String name = p.optString("name", "");
                String nameLower = name.toLowerCase();
                if (nameLower.contains("iphone") || nameLower.contains("ipad")) {
                    continue; // Exclude iOS profiles
                }
                String vendor = p.optString("webgl_vendor", "");
                if (vendor.isEmpty()) {
                    vendor = deduceWebglVendor(name);
                }
                String renderer = p.optString("webgl_renderer", "");
                if (renderer.isEmpty()) {
                    renderer = deduceWebglRenderer(name);
                }

                if (isAdreno610) {
                    if (!renderer.contains("610") && !isAdreno610Device(name)) {
                        continue;
                    }
                } else if (matchQualcomm) {
                    if (!vendor.equalsIgnoreCase("Qualcomm") || !renderer.equalsIgnoreCase(rendererDetect)) {
                        continue;
                    }
                } else if (matchArm) {
                    if (!vendor.equalsIgnoreCase("ARM") || !renderer.equalsIgnoreCase(rendererDetect)) {
                        continue;
                    }
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
