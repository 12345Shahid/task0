package com.rcl.androidstreamcontrol.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;

import com.rcl.androidstreamcontrol.R;
import com.rcl.androidstreamcontrol.utils.WebViewProxyManager;
import com.rcl.androidstreamcontrol.utils.FingerprintManager;
import com.rcl.androidstreamcontrol.utils.DeviceProfilePool;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Gated Task Browser (CPA Tracker)
 *
 * A clean, full-screen browser window for CPA tasks only.
 * - Applies residential proxy (Proxy-Cheap) to WebView on mount
 * - Whitelists CPA tracking domains only
 * - Blocks navigation to any other domain (Google, Facebook, etc.)
 * - Clears proxy override on unmount (bandwidth protection)
 * - No address bar — only a Back button and progress bar
 */
public class TaskBrowserActivity extends Activity {

    private static final String TAG = "TaskBrowser";

    // Proxy-Cheap credentials (hardcoded fallback — C2 override via /api/proxy/config)
    private String proxyType = "http"; // "http" or "socks5" — dynamic from C2
    private String proxyHost = "thehub.proxy-cheap.com";
    private int proxyPort = 8080;
    private String proxyUser = "Ltj8SfJpTEukyVn";
    private String proxyPass = "0Pa7rRMzcehfeLR_country-US";

    // CPA allowed domains (whitelist) — static defaults, dynamically merged
    // from C2 /api/cpa/whitelist on activity start.
    private static final Set<String> ALLOWED_DOMAINS = new HashSet<>();
    static {
        ALLOWED_DOMAINS.add("cpagrip.com");
        ALLOWED_DOMAINS.add("cpalead.com");
        ALLOWED_DOMAINS.add("mylead.com");
        ALLOWED_DOMAINS.add("cpadollar.com");
        ALLOWED_DOMAINS.add("adscendmedia.com");
        ALLOWED_DOMAINS.add("cpx-research.com");
        ALLOWED_DOMAINS.add("cpacampaigns.com");
        ALLOWED_DOMAINS.add("ocacampaign.com");
        // IP-check pages — allowed for proxy testing
        ALLOWED_DOMAINS.add("whatismyipaddress.com");
        ALLOWED_DOMAINS.add("showmyip.com");
        ALLOWED_DOMAINS.add("api.ipify.org");
        ALLOWED_DOMAINS.add("ipinfo.io");
    }

    private WebView webView;
    private ProgressBar progressBar;
    private Button backButton;
    private Button rotateButton;
    private String taskUrl = "https://www.cpagrip.com";
    private boolean proxyApplied = false;

    // Anti-detect / fingerprint rotation
    private static final int MAX_ROTATIONS_PER_DAY = 5;
    private static final String PREFS_NAME = "fp_rotation_log";
    private JSONObject currentProfile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String extraUrl = getIntent().getStringExtra("task_url");
        if (extraUrl != null) taskUrl = extraUrl;

        buildUI();

        // Apply an initial random identity
        currentProfile = DeviceProfilePool.getRandomProfile();

        // Apply proxy before loading any content
        applyProxy();

        setupWebView();
        webView.loadUrl(taskUrl);

        // Fetch dynamic whitelist from C2
        fetchDynamicWhitelist();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUserAgentString(currentProfile != null
            ? currentProfile.optString("userAgent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36")
            : "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return shouldBlockUrl(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                // Re-inject canvas noise on every navigation to keep the
                // unique fingerprint active across sub-pages.
                FingerprintManager.injectCanvasNoise(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                // Ensure spoof is applied after full page render
                if (currentProfile != null) {
                    FingerprintManager.applyFingerprint(view, currentProfile);
                }
                Log.d(TAG, "Page finished: " + url);
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, android.webkit.HttpAuthHandler handler, String host, String realm) {
                if (!WebViewProxyManager.handleAuthRequest(view, handler, host, realm)) {
                    super.onReceivedHttpAuthRequest(view, handler, host, realm);
                }
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "Page error: " + error.getErrorCode() + " for " + request.getUrl());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });
    }

    /**
     * Whitelist check — block any URL not belonging to CPA domains.
     */
    private boolean shouldBlockUrl(String url) {
        String host = extractDomain(url);

        if (host == null || host.isEmpty()) {
            // Allow about:blank and data: URIs
            return false;
        }

        for (String allowed : ALLOWED_DOMAINS) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return false; // Allowed
            }
        }

        // Blocked — show toast and don't load
        Log.w(TAG, "Blocked navigation to: " + host);
        runOnUiThread(() -> Toast.makeText(this,
                "Navigation blocked. You can only access task links.",
                Toast.LENGTH_SHORT).show());
        return true; // Block the request
    }

    private String extractDomain(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Apply the residential proxy to this WebView.
     */
    private void applyProxy() {
        try {
            if (proxyHost == null || proxyHost.isEmpty()) {
                Log.w(TAG, "No proxy configured — using direct connection");
                return;
            }

            // Fetch proxy credentials from C2
            fetchProxyCredentials();

            if (proxyApplied) {
                WebViewProxyManager.clearAppProxy();
            }

            WebViewProxyManager.updateProxyCredentials(proxyType, proxyHost, proxyPort, proxyUser, proxyPass);
            proxyApplied = true;

        } catch (Exception e) {
            Log.e(TAG, "Proxy application failed", e);
        }
    }

    /**
     * Fetch proxy credentials from C2 server.
     */
    private void fetchProxyCredentials() {
        new Thread(() -> {
            try {
                URL url = new URL("https://c2-1-production.up.railway.app/api/proxy/config");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-secret", "32653b5a3611366e6126c367a5d1297b122561da88206629b62380a34720bce7");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    JSONObject json = new JSONObject(sb.toString());

                    if (json.has("type")) proxyType = json.getString("type");
                    if (json.has("host")) proxyHost = json.getString("host");
                    if (json.has("port")) proxyPort = json.getInt("port");
                    if (json.has("username")) proxyUser = json.getString("username");
                    if (json.has("password")) proxyPass = json.getString("password");

                    Log.d(TAG, "Proxy credentials fetched from C2: " + proxyHost + ":" + proxyPort);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch proxy config, using defaults", e);
            }
        }).start();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Top bar: Back + Rotate Identity + progress
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setPadding(dp(8), dp(8), dp(8), 0);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        backButton = new Button(this);
        backButton.setText("← Back");
        backButton.setOnClickListener(v -> onBackPressed());

        rotateButton = new Button(this);
        rotateButton.setText("🔄 Rotate Identity");
        rotateButton.setOnClickListener(v -> rotateIdentity());

        buttonRow.addView(backButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        buttonRow.addView(rotateButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        topBar.addView(buttonRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        topBar.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

        // WebView fills the rest
        webView = new WebView(this);

        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);
    }

    /**
     * Rotate Identity — pick a random profile, apply fingerprint, reload CPA URL.
     * Enforced limit: 5 rotations per day per user.
     */
    private void rotateIdentity() {
        int used = rotationsUsedToday();
        if (used >= MAX_ROTATIONS_PER_DAY) {
            Toast.makeText(this,
                    "Daily identity rotation limit reached. Try again tomorrow.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Pick a random profile (avoid repeating the current one if possible)
        JSONObject newProfile = DeviceProfilePool.getRandomProfile();
        if (currentProfile != null && newProfile != null &&
                newProfile.toString().equals(currentProfile.toString())) {
            newProfile = DeviceProfilePool.getRandomProfile();
        }
        currentProfile = newProfile;

        incrementRotationsToday();

        // Apply the new fingerprint
        if (currentProfile != null) {
            FingerprintManager.applyFingerprint(webView, currentProfile);
        }

        // Reload the current CPA URL with the new identity
        Toast.makeText(this,
                "Identity rotated: " + (currentProfile != null ? currentProfile.optString("name", "?") : "?"),
                Toast.LENGTH_SHORT).show();
        String current = webView.getUrl();
        webView.reload();
        Log.d(TAG, "Identity rotated -> " + (currentProfile != null ? currentProfile.optString("name", "?") : "?"));
    }

    // ── Rotation limit tracking (local SharedPreferences) ────────────────────

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private int rotationsUsedToday() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String key = todayKey();
        if (prefs.contains(key)) {
            return prefs.getInt(key, 0);
        }
        // New day — clear old data
        prefs.edit().clear().apply();
        return 0;
    }

    private void incrementRotationsToday() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String key = todayKey();
        int current = rotationsUsedToday();
        prefs.edit().putInt(key, current + 1).apply();
    }

    /**
     * Fetch dynamic domain whitelist from C2 server and merge with static list.
     */
    private void fetchDynamicWhitelist() {
        new Thread(() -> {
            try {
                URL url = new URL("https://c2-1-production.up.railway.app/api/cpa/whitelist");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-secret", "32653b5a3611366e6126c367a5d1297b122561da88206629b62380a34720bce7");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("domains")) {
                        org.json.JSONArray domains = json.getJSONArray("domains");
                        for (int i = 0; i < domains.length(); i++) {
                            String domain = domains.getString(i).trim().toLowerCase();
                            if (!domain.isEmpty()) {
                                ALLOWED_DOMAINS.add(domain);
                                Log.d(TAG, "Dynamic whitelist added: " + domain);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch dynamic whitelist, using static list", e);
            }
        }).start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            // Check if target page is allowed before going back
            String url = webView.getUrl();
            if (url != null && shouldBlockUrl(url)) {
                webView.goBack();
            } else {
                webView.goBack();
            }
        } else {
            cleanup();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    /**
     * Bandwidth protection — clear proxy when task browser closes.
     */
    private void cleanup() {
        if (proxyApplied) {
            WebViewProxyManager.clearAppProxy();
            proxyApplied = false;
            Log.d(TAG, "Task browser closed — proxy override cleared (normal Wi-Fi restored)");
        }
        if (webView != null) {
            webView.destroy();
        }
    }
}