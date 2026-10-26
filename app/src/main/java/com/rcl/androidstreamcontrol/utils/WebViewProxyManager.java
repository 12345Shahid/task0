package com.rcl.androidstreamcontrol.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Selective Anti-Detect Proxy Manager
 *
 * - Routes ONLY whitelisted URLs/domains through the configured residential proxy
 *   (thehub.proxy-cheap.com:8080).
 * - All other searches, general websites, and system traffic route DIRECTLY (no proxy).
 * - Traffic outside the app is NEVER proxied.
 * - Dynamic remote sync: Whitelist can be updated anytime from Supabase:
 *     Table: projects -> row: proxy_whitelist -> settings.domains
 * - Safe Main-Thread proxy application to prevent Chromium IPC deadlocks.
 */
public class WebViewProxyManager {

    private static final String TAG = "WebViewProxy";
    private static final String PREFS_NAME = "proxy_prefs";
    private static final String KEY_CACHED_DOMAINS = "cached_whitelist_v3";

    // Supabase Configuration
    private static final String SUPABASE_WHITELIST_URL =
            "https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/projects?name=eq.proxy_whitelist&select=settings";
    private static final String SUPABASE_PATCH_URL =
            "https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/projects?name=eq.proxy_whitelist";
    private static final String SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3OTgwNjg2MCwiZXhwIjoyMDk1MzgyODYwfQ.aFwaFR6TTnmAa1dg4ZknEhmYCS74ZqcAMw4Lg2LUk1Y";

    // Default Proxy Configuration (US Residential)
    private static volatile String proxyType = "http";
    private static volatile String proxyHost = "thehub.proxy-cheap.com";
    private static volatile int proxyPort = 8080;
    private static volatile String proxyUser = "Ltj8SfJpTEukyVn";
    private static volatile String proxyPass = "0Pa7rRMzcehfeLR_country-US";
    private static volatile String currentSessionTag = "active";
    private static volatile String activeGeoState = "";
    private static volatile String activeGeoCity = "";
    private static final java.util.concurrent.atomic.AtomicLong sessionBytesTransferred = new java.util.concurrent.atomic.AtomicLong(0);

    // Live Geolocation & Timezone matching state
    private static volatile String lastResolvedIp = "128.211.249.142";
    private static volatile String lastResolvedCity = "West Lafayette";
    private static volatile String lastResolvedRegion = "Indiana";
    private static volatile String lastResolvedTimezone = "America/Indiana/Indianapolis";

    public interface GeoCallback {
        void onResolved(String ip, String city, String region, String country, String timezone);
    }

    // Thread-safe set of domains that route through proxy
    private static final Set<String> WHITELISTED_DOMAINS = ConcurrentHashMap.newKeySet();

    // STRICTLY 3 DEFAULT DOMAINS + YieldMatrix & Ad Network:
    // 1. whatismyipaddress.com
    // 2. ipinfo.io
    // 3. browserleaks.com
    // 4. yieldmatrix-crypto-calculator.vercel.app
    // 5. fond-appointment.com
    private static final String[] DEFAULT_DOMAINS = {
            "whatismyipaddress.com",
            "ipinfo.io",
            "browserleaks.com",
            "iphey.com",
            "yieldmatrix-crypto-calculator.vercel.app",
            "fond-appointment.com",
            "vercel.app",
            "hilltopads.com",
            "hilltopads.net",
            "cpadollar.com",
            "tr.cpadollar.com",
            "nx2bs.com",
            "olie.site",
            "supplysurveylink.com",
            "sphereoffers.com"
    };

    private static volatile boolean proxyApplied = false;
    private static volatile boolean isProxyStopped = false;
    private static volatile boolean isFullProxyMode = false; // Selective Reverse Bypass: ONLY whitelisted domains route through proxy

    static {
        WHITELISTED_DOMAINS.addAll(Arrays.asList(DEFAULT_DOMAINS));
    }

    public static boolean isProxyActive() {
        return !isProxyStopped;
    }

    public static boolean isFullProxyMode() {
        return false;
    }

    public static void setFullProxyMode(boolean full) {
        // Enforce strictly selective whitelist proxying to save bandwidth on non-whitelisted sites
        isFullProxyMode = false;
        if (!isProxyStopped) {
            applySelectiveProxy();
        }
    }

    public static void stopProxy() {
        isProxyStopped = true;
        clearAppProxy();
        Log.d(TAG, "Proxy explicitly STOPPED — bandwidth preserved, all traffic routes DIRECT");
    }

    public static void resumeProxy() {
        resumeProxy(null);
    }

    public static void resumeProxy(Runnable onReady) {
        isProxyStopped = false;
        applySelectiveProxy(onReady);
        Log.d(TAG, "Proxy connection RESUMED — selective whitelist active");
    }

    /**
     * Sets up global java.net.Authenticator and WebViewDatabase auth cache
     * so HTTPS CONNECT tunnels authenticate without failing.
     */
    public static void setupAuthenticator(Context context) {
        try {
            java.net.Authenticator.setDefault(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    if (isProxyStopped) return null;
                    return new java.net.PasswordAuthentication(
                            proxyUser,
                            proxyPass != null ? proxyPass.toCharArray() : new char[0]
                    );
                }
            });
            System.setProperty("http.proxyUser", proxyUser);
            System.setProperty("http.proxyPassword", proxyPass != null ? proxyPass : "");
            System.setProperty("https.proxyUser", proxyUser);
            System.setProperty("https.proxyPassword", proxyPass != null ? proxyPass : "");
            try {
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.w(TAG, "Authenticator setup note: " + e.getMessage());
        }

        if (context != null) {
            try {
                android.webkit.WebViewDatabase db = android.webkit.WebViewDatabase.getInstance(context);
                if (db != null) {
                    db.setHttpAuthUsernamePassword(proxyHost, "", proxyUser, proxyPass != null ? proxyPass : "");
                    db.setHttpAuthUsernamePassword(proxyHost + ":" + proxyPort, "", proxyUser, proxyPass != null ? proxyPass : "");
                    for (String d : WHITELISTED_DOMAINS) {
                        db.setHttpAuthUsernamePassword(d, "", proxyUser, proxyPass != null ? proxyPass : "");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "WebViewDatabase auth note: " + e.getMessage());
            }
        }
    }

    /**
     * Initialize proxy manager: load cached domains, apply selective proxy, and kick off remote sync.
     */
    public static void init(Context context) {
        if (context == null) return;
        setupAuthenticator(context);
        loadCachedWhitelist(context);
        if (!isProxyStopped) {
            applySelectiveProxy(null);
            syncRemoteConfig(context, null);
            resolveCurrentProxyGeo(null);
        } else {
            clearAppProxy();
        }
    }

    public static String getProxyUser() {
        return proxyUser;
    }

    public static String getProxyPass() {
        return proxyPass;
    }

    public static String getProxyHost() {
        return proxyHost;
    }

    public static int getProxyPort() {
        return proxyPort;
    }

    public static String getCurrentSessionTag() {
        return currentSessionTag;
    }

    public static void setSessionTag(String tag) {
        currentSessionTag = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : "active";
    }

    public static void setActiveGeoState(String state) {
        activeGeoState = (state != null && !state.trim().isEmpty()) ? state.trim() : "";
    }

    public static String getActiveGeoState() {
        return activeGeoState;
    }

    public static void setActiveGeoCity(String city) {
        activeGeoCity = (city != null && !city.trim().isEmpty()) ? city.trim() : "";
    }

    public static String getActiveGeoCity() {
        return activeGeoCity;
    }

    public static void addSessionBytes(long bytes) {
        if (bytes > 0) {
            sessionBytesTransferred.addAndGet(bytes);
        }
    }

    public static long getSessionBytesTransferred() {
        return sessionBytesTransferred.get();
    }

    public static void resetSessionBandwidth() {
        sessionBytesTransferred.set(0);
    }

    public static String getFormattedSessionBandwidth() {
        long bytes = sessionBytesTransferred.get();
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public static String formatCityParam(String city) {
        if (city == null || city.trim().isEmpty()) return "";
        return city.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static String getEffectiveProxyPass() {
        return getEffectiveProxyPass(true);
    }

    public static String getEffectiveProxyPass(boolean includeCity) {
        String base = (proxyPass != null) ? proxyPass : "0Pa7rRMzcehfeLR_country-US";
        base = base.replaceAll("_(city|session|state)-[^_]+", "");
        if (!base.contains("_country-")) {
            base = base + "_country-US";
        }
        StringBuilder sb = new StringBuilder(base);
        if (includeCity && activeGeoCity != null && !activeGeoCity.trim().isEmpty()) {
            String cleanCity = formatCityParam(activeGeoCity);
            if (!cleanCity.isEmpty()) {
                sb.append("_city-").append(cleanCity);
            }
        }
        return sb.toString();
    }

    public static String getLastResolvedIp() {
        return lastResolvedIp;
    }

    public static String getLastResolvedCity() {
        return lastResolvedCity;
    }

    public static String getLastResolvedRegion() {
        return lastResolvedRegion;
    }

    public static String getLastResolvedTimezone() {
        return lastResolvedTimezone;
    }

    /**
     * Cycles the proxy connection tag and refreshes geolocation without tearing down proxy sockets.
     */
    public static String rotateProxySession() {
        isProxyStopped = false;
        currentSessionTag = "cycle-" + Long.toHexString(System.currentTimeMillis()).substring(4);
        Log.d(TAG, "Cycling proxy connection: " + currentSessionTag);
        resolveCurrentProxyGeo(null);
        return currentSessionTag;
    }

    /**
     * Resolves the proxy's current exit IP, city, and timezone via ip-api.com.
     * Updates FingerprintManager so the JavaScript timezone matches the proxy IP 1:1.
     */
    public static void resolveCurrentProxyGeo(GeoCallback callback) {
        new Thread(() -> {
            String ip = null;
            String city = null;
            String region = null;
            String country = "US";
            String timezone = null;

            try {
                java.net.Proxy proxy = new java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(proxyHost, proxyPort));
                URL url = new URL("http://ip-api.com/json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                String auth = proxyUser + ":" + getEffectiveProxyPass();
                String encodedAuth = android.util.Base64.encodeToString(
                        auth.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                conn.setRequestProperty("Proxy-Authorization", "Basic " + encodedAuth);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if ("success".equalsIgnoreCase(json.optString("status", ""))) {
                        ip = json.optString("query", null);
                        city = json.optString("city", "West Lafayette");
                        region = json.optString("regionName", "Indiana");
                        country = json.optString("countryCode", "US");
                        timezone = json.optString("timezone", "America/Indiana/Indianapolis");

                        if (ip != null) lastResolvedIp = ip;
                        if (city != null) lastResolvedCity = city;
                        if (region != null) lastResolvedRegion = region;
                        if (timezone != null) {
                            lastResolvedTimezone = timezone;
                            FingerprintManager.setActiveTimezone(timezone);
                        }
                        Log.d(TAG, "Proxy IP resolved: " + lastResolvedIp + " [" + lastResolvedCity + ", " + lastResolvedTimezone + "]");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Proxy geo resolution note: " + e.getMessage());
            }

            if (ip == null && activeGeoCity != null && !activeGeoCity.trim().isEmpty()) {
                try {
                    java.net.Proxy proxy = new java.net.Proxy(
                            java.net.Proxy.Type.HTTP,
                            new java.net.InetSocketAddress(proxyHost, proxyPort));
                    URL url = new URL("http://ip-api.com/json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                    String auth = proxyUser + ":" + getEffectiveProxyPass(false);
                    String encodedAuth = android.util.Base64.encodeToString(
                            auth.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                    conn.setRequestProperty("Proxy-Authorization", "Basic " + encodedAuth);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        if ("success".equalsIgnoreCase(json.optString("status", ""))) {
                            ip = json.optString("query", null);
                            city = json.optString("city", "New York");
                            region = json.optString("regionName", "New York");
                            country = json.optString("countryCode", "US");
                            timezone = json.optString("timezone", "America/New_York");

                            if (ip != null) lastResolvedIp = ip;
                            if (city != null) lastResolvedCity = city;
                            if (region != null) lastResolvedRegion = region;
                            if (timezone != null) {
                                lastResolvedTimezone = timezone;
                                FingerprintManager.setActiveTimezone(timezone);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            final String finalIp = (lastResolvedIp != null) ? lastResolvedIp : "Active";
            final String finalCity = (lastResolvedCity != null) ? lastResolvedCity : "US";
            final String finalRegion = (lastResolvedRegion != null) ? lastResolvedRegion : "";
            final String finalCountry = country;
            final String finalTz = (lastResolvedTimezone != null) ? lastResolvedTimezone : "America/New_York";

            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onResolved(finalIp, finalCity, finalRegion, finalCountry, finalTz);
                });
            }
        }).start();
    }

    public static Set<String> getWhitelistedDomains() {
        return Collections.unmodifiableSet(new HashSet<>(WHITELISTED_DOMAINS));
    }

    public static void updateProxyCredentials(String type, String host, int port, String user, String pass) {
        if (type != null && !type.isEmpty()) proxyType = type;
        if (host != null && !host.isEmpty()) proxyHost = host;
        if (port > 0) proxyPort = port;
        if (user != null) proxyUser = user;
        if (pass != null) {
            proxyPass = pass;
        }
        applySelectiveProxy();
        resolveCurrentProxyGeo(null);
    }

    /**
     * Add a domain locally, apply proxy, and sync to Supabase cloud.
     */
    public static void addWhitelistedDomain(Context context, String domain) {
        if (domain == null || domain.trim().isEmpty()) return;
        String normalized = normalizeDomain(domain.trim());
        WHITELISTED_DOMAINS.add(normalized);

        if (context != null) {
            saveCachedWhitelist(context);
        }

        applySelectiveProxy();

        // Push new domain to Supabase cloud in background
        new Thread(WebViewProxyManager::pushWhitelistToSupabase).start();
    }

    public static void addWhitelistedDomain(String domain) {
        addWhitelistedDomain(null, domain);
    }

    /**
     * Synchronize whitelist and proxy configuration asynchronously from Supabase.
     */
    public static void syncRemoteConfig(Context context, Runnable onComplete) {
        new Thread(() -> {
            boolean updated = false;

            // Fetch remote whitelist from Supabase
            try {
                URL url = new URL(SUPABASE_WHITELIST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", SUPABASE_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONArray rows = new JSONArray(sb.toString());
                    if (rows.length() > 0) {
                        JSONObject first = rows.getJSONObject(0);
                        if (first.has("settings")) {
                            JSONObject settings = first.getJSONObject("settings");
                            if (settings.has("domains")) {
                                JSONArray domains = settings.getJSONArray("domains");
                                Set<String> remoteSet = new HashSet<>();
                                for (int i = 0; i < domains.length(); i++) {
                                    String domain = normalizeDomain(domains.getString(i).trim());
                                    if (!domain.isEmpty()) {
                                        remoteSet.add(domain);
                                    }
                                }
                                if (!remoteSet.isEmpty()) {
                                    Set<String> merged = new HashSet<>(Arrays.asList(DEFAULT_DOMAINS));
                                    merged.addAll(remoteSet);
                                    if (!merged.equals(WHITELISTED_DOMAINS)) {
                                        WHITELISTED_DOMAINS.clear();
                                        WHITELISTED_DOMAINS.addAll(merged);
                                        updated = true;
                                        Log.d(TAG, "Supabase whitelist updated: " + WHITELISTED_DOMAINS.size() + " domains (merged with defaults)");
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Supabase whitelist sync note: " + e.getMessage());
            }

            // If updated, save cache and re-apply proxy on MAIN THREAD
            if (updated) {
                if (context != null) {
                    saveCachedWhitelist(context);
                }
                new Handler(Looper.getMainLooper()).post(WebViewProxyManager::applySelectiveProxy);
            }

            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
        }).start();
    }

    /**
     * Push current whitelist to Supabase cloud.
     */
    private static void pushWhitelistToSupabase() {
        try {
            URL url = new URL(SUPABASE_PATCH_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("apikey", SUPABASE_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            JSONArray domainArray = new JSONArray();
            for (String d : WHITELISTED_DOMAINS) {
                domainArray.put(d);
            }
            JSONObject payload = new JSONObject();
            JSONObject settings = new JSONObject();
            settings.put("domains", domainArray);
            payload.put("settings", settings);

            byte[] out = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Pushed updated whitelist to Supabase. Response code: " + code);
        } catch (Exception e) {
            Log.w(TAG, "Could not push whitelist to Supabase: " + e.getMessage());
        }
    }

    private static void loadCachedWhitelist(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String cached = prefs.getString(KEY_CACHED_DOMAINS, null);
            if (cached != null && !cached.isEmpty()) {
                String[] items = cached.split(",");
                Set<String> loaded = new HashSet<>();
                for (String it : items) {
                    String clean = normalizeDomain(it.trim());
                    if (!clean.isEmpty()) {
                        loaded.add(clean);
                    }
                }
                if (!loaded.isEmpty()) {
                    WHITELISTED_DOMAINS.clear();
                    WHITELISTED_DOMAINS.addAll(Arrays.asList(DEFAULT_DOMAINS));
                    WHITELISTED_DOMAINS.addAll(loaded);
                    Log.d(TAG, "Loaded " + WHITELISTED_DOMAINS.size() + " domains from cache (merged with defaults)");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading cached whitelist", e);
        }
    }

    private static void saveCachedWhitelist(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (String domain : WHITELISTED_DOMAINS) {
                if (sb.length() > 0) sb.append(",");
                sb.append(domain);
            }
            prefs.edit().putString(KEY_CACHED_DOMAINS, sb.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Error saving cached whitelist", e);
        }
    }

    public static boolean isUrlWhitelisted(String url) {
        if (isProxyStopped) return false;
        if (isFullProxyMode) return true;
        return isDomainWhitelisted(url);
    }

    public static boolean isDomainWhitelisted(String url) {
        if (url == null || url.isEmpty()) return false;
        String host = extractHost(url);
        if (host == null || host.isEmpty()) return false;

        for (String allowed : WHITELISTED_DOMAINS) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static String extractHost(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                return normalizeDomain(host);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String normalizeDomain(String domain) {
        return domain.toLowerCase().replaceFirst("^https?://", "").replaceFirst("^www\\.", "").split("/")[0].trim();
    }

    // In-process loopback proxy for injecting upstream Proxy-Authorization headers
    private static volatile ServerSocket localServerSocket = null;
    private static volatile int localProxyPort = 0;
    private static final ExecutorService proxyThreadPool = Executors.newCachedThreadPool();

    /**
     * Starts or ensures the local loopback proxy server is running.
     * This local proxy intercepts Chromium's CONNECT and HTTP requests for whitelisted domains,
     * appends the upstream residential proxy credentials (Proxy-Authorization: Basic ...),
     * and forwards cleanly without exposing 407 challenges to Chromium.
     */
    public static synchronized int ensureLocalProxyRunning() {
        if (localServerSocket != null && !localServerSocket.isClosed() && localProxyPort > 0) {
            return localProxyPort;
        }

        try {
            localServerSocket = new ServerSocket(0, 100, InetAddress.getByName("127.0.0.1"));
            localProxyPort = localServerSocket.getLocalPort();
            Log.d(TAG, "Local loopback proxy listening on 127.0.0.1:" + localProxyPort);

            Thread acceptThread = new Thread(() -> {
                while (localServerSocket != null && !localServerSocket.isClosed()) {
                    try {
                        Socket clientSocket = localServerSocket.accept();
                        clientSocket.setTcpNoDelay(true);
                        proxyThreadPool.execute(() -> handleClientConnection(clientSocket));
                    } catch (Exception e) {
                        if (localServerSocket == null || localServerSocket.isClosed()) break;
                    }
                }
            }, "LocalProxyAcceptor");
            acceptThread.setDaemon(true);
            acceptThread.start();

            return localProxyPort;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start local loopback proxy", e);
            return 0;
        }
    }

    private static void handleClientConnection(Socket clientSocket) {
        Socket upstreamSocket = null;
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int read;
            boolean headersComplete = false;

            while (!headersComplete && (read = in.read(buf)) != -1) {
                headerBuffer.write(buf, 0, read);
                addSessionBytes(read);
                byte[] current = headerBuffer.toByteArray();
                for (int i = 0; i <= current.length - 4; i++) {
                    if (current[i] == '\r' && current[i + 1] == '\n' &&
                        current[i + 2] == '\r' && current[i + 3] == '\n') {
                        headersComplete = true;
                        break;
                    }
                }
                if (headerBuffer.size() > 65536) break;
            }

            if (!headersComplete) {
                clientSocket.close();
                return;
            }

            byte[] rawHeaders = headerBuffer.toByteArray();
            String headerStr = new String(rawHeaders, StandardCharsets.ISO_8859_1);
            int firstLineEnd = headerStr.indexOf("\r\n");
            if (firstLineEnd == -1) {
                clientSocket.close();
                return;
            }

            String firstLine = headerStr.substring(0, firstLineEnd);
            String[] parts = firstLine.split(" ");
            if (parts.length < 2) {
                clientSocket.close();
                return;
            }

            String method = parts[0];
            String target = parts[1];

            // Upstream proxy credentials (with city targeting if set)
            String authHeader = "";
            if (proxyUser != null && !proxyUser.isEmpty()) {
                String creds = proxyUser + ":" + getEffectiveProxyPass(true);
                authHeader = "Proxy-Authorization: Basic " +
                        android.util.Base64.encodeToString(creds.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP) + "\r\n";
            }

            upstreamSocket = new Socket(proxyHost, proxyPort);
            upstreamSocket.setTcpNoDelay(true);
            upstreamSocket.setSoTimeout(15000);
            InputStream upstreamIn = upstreamSocket.getInputStream();
            OutputStream upstreamOut = upstreamSocket.getOutputStream();

            if ("CONNECT".equalsIgnoreCase(method)) {
                // HTTPS CONNECT Tunnel
                String connectReq = "CONNECT " + target + " HTTP/1.1\r\n" +
                        "Host: " + target + "\r\n" +
                        authHeader +
                        "Proxy-Connection: Keep-Alive\r\n\r\n";
                byte[] connectBytes = connectReq.getBytes(StandardCharsets.ISO_8859_1);
                upstreamOut.write(connectBytes);
                upstreamOut.flush();
                addSessionBytes(connectBytes.length);

                ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
                byte[] rBuf = new byte[1024];
                boolean respComplete = false;
                while (!respComplete && (read = upstreamIn.read(rBuf)) != -1) {
                    respBuf.write(rBuf, 0, read);
                    addSessionBytes(read);
                    byte[] cResp = respBuf.toByteArray();
                    for (int i = 0; i <= cResp.length - 4; i++) {
                        if (cResp[i] == '\r' && cResp[i + 1] == '\n' &&
                            cResp[i + 2] == '\r' && cResp[i + 3] == '\n') {
                            respComplete = true;
                            break;
                        }
                    }
                }

                String respStr = new String(respBuf.toByteArray(), StandardCharsets.ISO_8859_1);
                if (respStr.startsWith("HTTP/1.0 200") || respStr.startsWith("HTTP/1.1 200")) {
                    byte[] okBytes = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
                    out.write(okBytes);
                    out.flush();
                    addSessionBytes(okBytes.length);

                    upstreamSocket.setSoTimeout(0);
                    clientSocket.setSoTimeout(0);
                    pipeSockets(clientSocket, upstreamSocket);
                } else if (respStr.startsWith("HTTP/1.0 502") || respStr.startsWith("HTTP/1.1 502") ||
                           respStr.startsWith("HTTP/1.0 503") || respStr.startsWith("HTTP/1.1 503") ||
                           respStr.startsWith("HTTP/1.0 407") || respStr.startsWith("HTTP/1.1 407")) {
                    // Upstream error — fall back to country level baseline
                    try { upstreamSocket.close(); } catch (Exception ignored) {}
                    upstreamSocket = new Socket(proxyHost, proxyPort);
                    upstreamSocket.setTcpNoDelay(true);
                    upstreamSocket.setSoTimeout(15000);
                    upstreamIn = upstreamSocket.getInputStream();
                    upstreamOut = upstreamSocket.getOutputStream();

                    String fbCreds = proxyUser + ":" + getEffectiveProxyPass(false);
                    String fbAuth = "Proxy-Authorization: Basic " +
                            android.util.Base64.encodeToString(fbCreds.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP) + "\r\n";
                    String fbReq = "CONNECT " + target + " HTTP/1.1\r\n" +
                            "Host: " + target + "\r\n" +
                            fbAuth +
                            "Proxy-Connection: Keep-Alive\r\n\r\n";
                    byte[] fbReqBytes = fbReq.getBytes(StandardCharsets.ISO_8859_1);
                    upstreamOut.write(fbReqBytes);
                    upstreamOut.flush();
                    addSessionBytes(fbReqBytes.length);

                    respBuf.reset();
                    respComplete = false;
                    while (!respComplete && (read = upstreamIn.read(rBuf)) != -1) {
                        respBuf.write(rBuf, 0, read);
                        addSessionBytes(read);
                        byte[] cResp = respBuf.toByteArray();
                        for (int i = 0; i <= cResp.length - 4; i++) {
                            if (cResp[i] == '\r' && cResp[i + 1] == '\n' &&
                                cResp[i + 2] == '\r' && cResp[i + 3] == '\n') {
                                respComplete = true;
                                break;
                            }
                        }
                    }

                    respStr = new String(respBuf.toByteArray(), StandardCharsets.ISO_8859_1);
                    if (respStr.startsWith("HTTP/1.0 200") || respStr.startsWith("HTTP/1.1 200")) {
                        byte[] okBytes = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
                        out.write(okBytes);
                        out.flush();
                        addSessionBytes(okBytes.length);

                        upstreamSocket.setSoTimeout(0);
                        clientSocket.setSoTimeout(0);
                        pipeSockets(clientSocket, upstreamSocket);
                    } else {
                        out.write(respBuf.toByteArray());
                        out.flush();
                        clientSocket.close();
                        upstreamSocket.close();
                    }
                } else {
                    out.write(respBuf.toByteArray());
                    out.flush();
                    clientSocket.close();
                    upstreamSocket.close();
                }
            } else {
                // Plain HTTP request
                int headerInsertIndex = headerStr.indexOf("\r\n") + 2;
                String modifiedHeaders = headerStr.substring(0, headerInsertIndex) +
                        authHeader +
                        headerStr.substring(headerInsertIndex);

                byte[] modBytes = modifiedHeaders.getBytes(StandardCharsets.ISO_8859_1);
                upstreamOut.write(modBytes);
                upstreamOut.flush();
                addSessionBytes(modBytes.length);

                upstreamSocket.setSoTimeout(0);
                clientSocket.setSoTimeout(0);
                pipeSockets(clientSocket, upstreamSocket);
            }
        } catch (Exception e) {
            Log.d(TAG, "Local loopback proxy error: " + e.getMessage());
            try { clientSocket.close(); } catch (Exception ignored) {}
            if (upstreamSocket != null) {
                try { upstreamSocket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void pipeSockets(Socket s1, Socket s2) {
        proxyThreadPool.execute(() -> {
            try {
                InputStream in = s1.getInputStream();
                OutputStream out = s2.getOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                    addSessionBytes(len);
                }
            } catch (Exception ignored) {}
            try { s1.close(); } catch (Exception ignored) {}
            try { s2.close(); } catch (Exception ignored) {}
        });

        proxyThreadPool.execute(() -> {
            try {
                InputStream in = s2.getInputStream();
                OutputStream out = s1.getOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                    addSessionBytes(len);
                }
            } catch (Exception ignored) {}
            try { s1.close(); } catch (Exception ignored) {}
            try { s2.close(); } catch (Exception ignored) {}
        });
    }

    /**
     * Applies selective proxying:
     * - ONLY whitelisted domains will route through the US Residential Proxy via local authenticated loopback.
     * - All other domains connect directly.
     * - Safe execution: always runs on the Main UI thread to prevent Chromium IPC deadlocks.
     */
    public static void applySelectiveProxy() {
        applySelectiveProxy(null);
    }

    @SuppressLint("NewApi")
    public static void applySelectiveProxy(Runnable onComplete) {
        // ALWAYS run ProxyController on the Main Looper
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> applySelectiveProxy(onComplete));
            return;
        }

        if (isProxyStopped) {
            Log.d(TAG, "Proxy is explicitly stopped — clearing proxy override");
            clearAppProxy();
            if (onComplete != null) onComplete.run();
            return;
        }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w(TAG, "WebViewFeature.PROXY_OVERRIDE is not supported on this device/WebView");
            if (onComplete != null) onComplete.run();
            return;
        }

        try {
            int port = ensureLocalProxyRunning();
            String proxyAddress = (port > 0) ? ("127.0.0.1:" + port) : (proxyHost + ":" + proxyPort);

            ProxyConfig.Builder builder = new ProxyConfig.Builder();
            builder.addProxyRule(proxyAddress);
            builder.addDirect();

            if (!isFullProxyMode && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)) {
                for (String domain : WHITELISTED_DOMAINS) {
                    builder.addBypassRule(domain);
                    builder.addBypassRule("*." + domain);
                }
                builder.setReverseBypassEnabled(true);
                Log.d(TAG, "Configured Reverse Bypass proxy via " + proxyAddress + " for " + WHITELISTED_DOMAINS.size() + " domains");
            } else {
                Log.d(TAG, "Configured FULL PROXY mode via " + proxyAddress + " (100% of WebView traffic routed through US proxy)");
            }

            ProxyConfig config = builder.build();
            Executor executor = Runnable::run;

            ProxyController.getInstance().setProxyOverride(config, executor, () -> {
                proxyApplied = true;
                Log.d(TAG, "Selective proxy override applied successfully: " + proxyAddress);
                if (onComplete != null) {
                    onComplete.run();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply selective proxy", e);
            if (onComplete != null) onComplete.run();
        }
    }

    /**
     * Clear the proxy override completely and restore direct connection.
     */
    @SuppressLint("NewApi")
    public static void clearAppProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return;

        try {
            Executor executor = Runnable::run;
            ProxyController.getInstance().clearProxyOverride(executor, () -> {
                proxyApplied = false;
                Log.d(TAG, "Proxy override cleared — direct connection restored");
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear proxy override", e);
        }
    }

    /**
     * Handles HTTP 407 Proxy Authentication challenges.
     * Clean and non-blocking: directly supplies proxy credentials without SQLite locks.
     */
    public static boolean handleAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        if (isProxyStopped) {
            return false;
        }
        if (proxyUser != null && !proxyUser.isEmpty()) {
            Log.d(TAG, "Supplying proxy credentials to auth challenge: host=" + host);
            handler.proceed(proxyUser, getEffectiveProxyPass());
            return true;
        }
        return false;
    }
}