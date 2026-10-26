package com.rcl.androidstreamcontrol;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.content.Context;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rcl.androidstreamcontrol.service.C2WebSocketClient;
import com.rcl.androidstreamcontrol.service.HackerBrowser;
import com.rcl.androidstreamcontrol.utils.AiAgentConfig;
import com.rcl.androidstreamcontrol.utils.AiSessionManager;
import com.rcl.androidstreamcontrol.utils.BrowserTab;
import com.rcl.androidstreamcontrol.utils.DeviceProfilePool;
import com.rcl.androidstreamcontrol.utils.FingerprintManager;
import com.rcl.androidstreamcontrol.utils.ProfileStorageManager;
import com.rcl.androidstreamcontrol.utils.WebViewProxyManager;
import com.rcl.androidstreamcontrol.utils.CloudProfileLockManager;
import com.rcl.androidstreamcontrol.simulation.SessionOrchestrator;
import com.rcl.androidstreamcontrol.simulation.TrafficSimulationEngine;
import com.rcl.androidstreamcontrol.simulation.BehaviorProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Selective Anti-Detect Browser
 *
 * - Whitelisted URLs route through the US Residential Proxy (thehub.proxy-cheap.com:8080).
 * - All other searches & general browsing route DIRECTLY without proxy.
 * - Profile menu with:
 *     1. Switch Device Profile (250 unique US Mobile devices)
 *     2. Rotate Proxy Session (instantly assigns a fresh US exit IP)
 *     3. ⚡ 1-Click New Identity (Nuclear Reset of cookies, storage, cache, IP, and fingerprint)
 * - Dynamic Timezone Auto-Matching: matches browser Intl and Date offset to active proxy IP
 * - Canvas & WebGL Unmasked Vendor/Renderer spoofing
 */
public class BrowserActivity extends AppCompatActivity {

    private static final String TAG = "BrowserActivity";
    private static final String HOME_URL = "https://duckduckgo.com";

    private WebView browser;
    private EditText urlInput;
    private TextView btnProfile;
    private TextView proxyBadge;
    private ProgressBar progressBar;
    private Button btnBack;
    private Button btnForward;
    private Button btnRefresh;
    private C2WebSocketClient c2Client;
    private HackerBrowser hackerBrowser;
    private String defaultNativeUserAgent;

    // Multi-Profile Tab Management State
    private final List<BrowserTab> tabList = new ArrayList<>();
    private int activeTabIndex = 0;
    private FrameLayout webViewContainer;
    private HorizontalScrollView tabScrollView;
    private LinearLayout tabBarLayout;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable bandwidthRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                BrowserTab activeTab = getActiveTab();
                if (activeTab != null && activeTab.webView != null) {
                    updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(activeTab.webView.getUrl()));
                } else {
                    updateProxyBadgeUI(false);
                }
                uiHandler.postDelayed(this, 2000);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        SessionOrchestrator.BrowserActivityState.register(this);
        uiHandler.removeCallbacks(bandwidthRefreshRunnable);
        uiHandler.post(bandwidthRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(bandwidthRefreshRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionOrchestrator.BrowserActivityState.register(this);

        // Hide default action bar if present for a clean browser look
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Capture the true physical device User-Agent for native profile restore
        defaultNativeUserAgent = WebSettings.getDefaultUserAgent(this);

        // Initialize selective proxying (strictly 3 whitelisted domains) and sync with Supabase
        WebViewProxyManager.init(getApplicationContext());
        CloudProfileLockManager.init(getApplicationContext());
        DeviceProfilePool.init(getApplicationContext());
        ProfileStorageManager.syncFromCloud(getApplicationContext(), null);

        // Unconditionally default to the original physical device identity on cold launch / RAM purge
        // Spoofing and proxy tunneling only activate when explicitly picked by the user
        FingerprintManager.resetToNative();
        WebViewProxyManager.resumeProxy();
        saveProfileState(null, true);

        // Build the browser UI (Top Navigation Bar + Tab Switcher Bar + Progress Bar + WebView Container)
        buildBrowserUI();

        // Initialize Tab 1 (Default: Original Physical Device Identity, Unspoofed)
        createNewTab(null, true);

        // Handle incoming URL from Intent
        String incomingUrl = null;
        if (getIntent() != null) {
            if (getIntent().getData() != null) {
                incomingUrl = getIntent().getData().toString();
            } else if (getIntent().hasExtra("url")) {
                incomingUrl = getIntent().getStringExtra("url");
            }
        }
        if (incomingUrl != null && !incomingUrl.isEmpty()) {
            navigateToUrl(incomingUrl);
        }

        // Background services
        startHackerModules();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String incomingUrl = null;
        if (intent != null) {
            if (intent.getData() != null) {
                incomingUrl = intent.getData().toString();
            } else if (intent.hasExtra("url")) {
                incomingUrl = intent.getStringExtra("url");
            }
        }
        if (incomingUrl != null && !incomingUrl.isEmpty()) {
            navigateToUrl(incomingUrl);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void buildBrowserUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1E1E2E);

        // ── Top Navigation Bar ────────────────────────────────────────────────
        // ── Top Header Container ──────────────────────────────────────────────
        LinearLayout topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.VERTICAL);
        topHeader.setBackgroundColor(0xFF181825);

        // ── Row 1: Dedicated Full-Width URL / Search Bar ──────────────────────
        LinearLayout urlBarRow = new LinearLayout(this);
        urlBarRow.setOrientation(LinearLayout.HORIZONTAL);
        urlBarRow.setGravity(Gravity.CENTER_VERTICAL);
        urlBarRow.setPadding(dp(6), dp(6), dp(6), dp(3));

        // Back Button
        btnBack = new Button(this);
        btnBack.setText("◀");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setTextSize(14);
        btnBack.setPadding(dp(2), dp(2), dp(2), dp(2));
        btnBack.setOnClickListener(v -> {
            if (browser != null && browser.canGoBack()) {
                browser.goBack();
            }
        });
        urlBarRow.addView(btnBack, new LinearLayout.LayoutParams(dp(30), dp(34)));

        // Forward Button
        btnForward = new Button(this);
        btnForward.setText("▶");
        btnForward.setTextColor(Color.WHITE);
        btnForward.setBackgroundColor(Color.TRANSPARENT);
        btnForward.setTextSize(14);
        btnForward.setPadding(dp(2), dp(2), dp(2), dp(2));
        btnForward.setOnClickListener(v -> {
            if (browser != null && browser.canGoForward()) {
                browser.goForward();
            }
        });
        urlBarRow.addView(btnForward, new LinearLayout.LayoutParams(dp(30), dp(34)));

        // URL / Search Input (Prominent, full-width, auto select-all on tap)
        urlInput = new EditText(this);
        urlInput.setHint("Search or enter URL...");
        urlInput.setHintTextColor(0xFF888899);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setTextSize(12);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setSelectAllOnFocus(true);
        urlInput.setPadding(dp(10), dp(6), dp(10), dp(6));

        GradientDrawable urlBg = new GradientDrawable();
        urlBg.setColor(0xFF313244);
        urlBg.setCornerRadius(dp(14));
        urlInput.setBackground(urlBg);

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                navigateToUrl(urlInput.getText().toString());
                urlInput.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        urlParams.setMargins(dp(4), 0, dp(4), 0);
        urlBarRow.addView(urlInput, urlParams);

        // Go Button (Easy tap to navigate or search)
        TextView btnGo = new TextView(this);
        btnGo.setText("Go");
        btnGo.setTextColor(Color.WHITE);
        btnGo.setTextSize(11);
        btnGo.setTypeface(null, android.graphics.Typeface.BOLD);
        btnGo.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnGo.setGravity(Gravity.CENTER);
        GradientDrawable goBg = new GradientDrawable();
        goBg.setCornerRadius(dp(12));
        goBg.setColor(0xFF3B82F6); // Clean blue
        btnGo.setBackground(goBg);
        btnGo.setOnClickListener(v -> {
            navigateToUrl(urlInput.getText().toString());
            urlInput.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
            }
        });
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        goParams.setMargins(0, 0, dp(4), 0);
        urlBarRow.addView(btnGo, goParams);

        // Refresh Button
        btnRefresh = new Button(this);
        btnRefresh.setText("⟳");
        btnRefresh.setTextColor(Color.WHITE);
        btnRefresh.setBackgroundColor(Color.TRANSPARENT);
        btnRefresh.setTextSize(15);
        btnRefresh.setPadding(dp(2), dp(2), dp(2), dp(2));
        btnRefresh.setOnClickListener(v -> {
            if (browser != null) {
                browser.reload();
            }
        });
        urlBarRow.addView(btnRefresh, new LinearLayout.LayoutParams(dp(30), dp(34)));

        topHeader.addView(urlBarRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Row 2: Controls & Status Bar (Horizontal Scrollable) ───────────────
        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        controlsScroll.setHorizontalScrollBarEnabled(false);
        controlsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        controlsScroll.setPadding(dp(6), dp(1), dp(6), dp(5));

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER_VERTICAL);

        // 👤 Profile Menu Button (Clickable Pill)
        btnProfile = new TextView(this);
        btnProfile.setText("👤 Profile");
        btnProfile.setTextColor(Color.WHITE);
        btnProfile.setTextSize(11);
        btnProfile.setPadding(dp(8), dp(4), dp(8), dp(4));
        btnProfile.setGravity(Gravity.CENTER);

        GradientDrawable profileBg = new GradientDrawable();
        profileBg.setCornerRadius(dp(12));
        profileBg.setColor(0xFF585B70);
        btnProfile.setBackground(profileBg);

        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(26));
        profileParams.setMargins(0, 0, dp(6), 0);
        btnProfile.setOnClickListener(v -> showProfileManagerDialog());
        controlsRow.addView(btnProfile, profileParams);

        // Proxy Status Badge (Clickable pill)
        proxyBadge = new TextView(this);
        proxyBadge.setTextSize(11);
        proxyBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        proxyBadge.setGravity(Gravity.CENTER);
        updateProxyBadgeUI(false);

        LinearLayout.LayoutParams proxyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(26));
        proxyParams.setMargins(0, 0, dp(6), 0);
        proxyBadge.setOnClickListener(v -> showWhitelistDialog());
        controlsRow.addView(proxyBadge, proxyParams);

        // 🚀 Traffic Simulation Engine Button
        TextView btnTraffic = new TextView(this);
        btnTraffic.setText("🚀 Traffic");
        btnTraffic.setTextColor(Color.WHITE);
        btnTraffic.setTextSize(11);
        btnTraffic.setPadding(dp(8), dp(4), dp(8), dp(4));
        btnTraffic.setGravity(Gravity.CENTER);

        GradientDrawable trafficBg = new GradientDrawable();
        trafficBg.setCornerRadius(dp(12));
        trafficBg.setColor(0xFF7287FD);
        btnTraffic.setBackground(trafficBg);

        LinearLayout.LayoutParams trafficParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(26));
        trafficParams.setMargins(0, 0, dp(4), 0);
        btnTraffic.setOnClickListener(v -> showTrafficEngineDialog());
        controlsRow.addView(btnTraffic, trafficParams);

        controlsScroll.addView(controlsRow, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));

        topHeader.addView(controlsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(topHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Tab Bar (Horizontal Multi-Profile Tab Switcher) ───────────────────
        tabScrollView = new HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        tabScrollView.setBackgroundColor(0xFF11111B);
        tabScrollView.setPadding(dp(6), dp(4), dp(6), dp(4));

        tabBarLayout = new LinearLayout(this);
        tabBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        tabScrollView.addView(tabBarLayout, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(tabScrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Progress Bar ──────────────────────────────────────────────────────
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        // ── WebViews Container (Multi-Tab Stack) ──────────────────────────────
        webViewContainer = new FrameLayout(this);
        root.addView(webViewContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);
    }

    private void updateProxyBadgeUI(boolean isProxied) {
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(12));

        String bandwidthStr = WebViewProxyManager.getFormattedSessionBandwidth();
        String bwSuffix = " • " + bandwidthStr;

        if (!WebViewProxyManager.isProxyActive()) {
            badgeBg.setColor(0xFF45475A); // Neutral grey
            proxyBadge.setText("⚡ Direct (Stopped)" + bwSuffix);
            proxyBadge.setTextColor(0xFFBAC2DE);
        } else if (isProxied) {
            badgeBg.setColor(0xFF2E7D32); // Green
            String city = WebViewProxyManager.getActiveGeoCity();
            if (city != null && !city.isEmpty()) {
                proxyBadge.setText("🛡️ " + city + " Proxy" + bwSuffix);
            } else {
                proxyBadge.setText("🛡️ US Proxy" + bwSuffix);
            }
            proxyBadge.setTextColor(Color.WHITE);
        } else {
            badgeBg.setColor(0xFF45475A); // Neutral grey
            proxyBadge.setText("⚡ Direct" + bwSuffix);
            proxyBadge.setTextColor(0xFFBAC2DE);
        }
        proxyBadge.setBackground(badgeBg);
    }

    private void navigateToUrl(String input) {
        if (input == null || input.trim().isEmpty()) return;
        String query = input.trim();

        String url;
        if (query.startsWith("http://") || query.startsWith("https://")) {
            url = query;
        } else if (query.contains(".") && !query.contains(" ")) {
            url = "https://" + query;
        } else {
            url = "https://duckduckgo.com/?q=" + Uri.encode(query);
        }

        if (WebViewProxyManager.isDomainWhitelisted(url)) {
            WebViewProxyManager.resumeProxy();
        }

        browser.loadUrl(url);
    }

    public BrowserTab getActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabList.size()) {
            return tabList.get(activeTabIndex);
        }
        return !tabList.isEmpty() ? tabList.get(0) : null;
    }

    public int getTabCount() {
        return tabList.size();
    }

    private boolean isTabActive(BrowserTab tab) {
        if (tab == null || activeTabIndex < 0 || activeTabIndex >= tabList.size()) return false;
        return tabList.get(activeTabIndex) == tab;
    }

    /**
     * Creates a new isolated Browser Tab with a guaranteed distinct hardware profile.
     */
    public BrowserTab createNewTab(JSONObject specificProfile, boolean isNative) {
        WebView wv = new WebView(this);
        JSONObject profile = specificProfile;
        double seed = new java.util.Random().nextDouble();
        String sessTag = "tab-" + UUID.randomUUID().toString().substring(0, 8);
        String tz = "America/New_York";

        if (!isNative && profile == null) {
            Set<String> used = new HashSet<>();
            for (BrowserTab t : tabList) {
                if (t.profile != null) {
                    used.add(t.profile.optString("name", ""));
                }
            }
            // Exclude profiles currently active on other devices
            used.addAll(CloudProfileLockManager.getRemoteLockedProfileNames());
            profile = DeviceProfilePool.getRandomProfileExcluding(used);
            if (profile != null) {
                tz = profile.optString("timezone", "America/New_York");
            }
        }

        BrowserTab tab = new BrowserTab(
                UUID.randomUUID().toString(),
                wv,
                profile,
                isNative,
                seed,
                tz,
                sessTag
        );

        setupWebViewForTab(tab);

        if (webViewContainer != null) {
            webViewContainer.addView(wv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        tabList.add(tab);
        if (!isNative && profile != null) {
            CloudProfileLockManager.acquireLock(this, profile.optString("name", ""), "");
        }
        int newIndex = tabList.size() - 1;
        switchToTab(newIndex);
        wv.loadUrl(HOME_URL);

        return tab;
    }

    /**
     * Switches the active tab view, URL bar, and anti-detect spoofing state.
     */
    public void switchToTab(int index) {
        if (index < 0 || index >= tabList.size()) return;

        activeTabIndex = index;
        BrowserTab activeTab = tabList.get(index);
        browser = activeTab.webView;

        for (int i = 0; i < tabList.size(); i++) {
            tabList.get(i).webView.setVisibility(i == index ? View.VISIBLE : View.GONE);
        }

        if (activeTab.isNative) {
            FingerprintManager.resetToNative();
        } else {
            FingerprintManager.setCurrentProfile(activeTab.profile, false);
            FingerprintManager.setCurrentNoiseSeed(activeTab.noiseSeed);
            FingerprintManager.setActiveTimezone(activeTab.timezone);
        }

        WebViewProxyManager.setSessionTag(activeTab.proxySessionTag);
        WebViewProxyManager.setActiveGeoState(activeTab.geoState);
        WebViewProxyManager.setActiveGeoCity(activeTab.geoCity);

        if (urlInput != null && !urlInput.hasFocus()) {
            urlInput.setText(activeTab.webView.getUrl() != null ? activeTab.webView.getUrl() : "");
        }

        updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(activeTab.webView.getUrl()));
        renderTabBar();
    }

    /**
     * Closes the tab and frees its WebView memory.
     */
    public void closeTab(int index) {
        if (tabList.size() <= 1) {
            Toast.makeText(this, "Cannot close the only open tab", Toast.LENGTH_SHORT).show();
            return;
        }

        BrowserTab tab = tabList.remove(index);
        if (tab != null && tab.profile != null) {
            CloudProfileLockManager.releaseLock(this, tab.profile.optString("name", ""), "");
        }
        if (webViewContainer != null) {
            webViewContainer.removeView(tab.webView);
        }
        try {
            tab.webView.destroy();
        } catch (Exception ignored) {}

        if (activeTabIndex >= tabList.size()) {
            activeTabIndex = tabList.size() - 1;
        }
        switchToTab(activeTabIndex);
    }

    /**
     * Renders the dynamic horizontal tab bar at the top of the browser.
     */
    public void renderTabBar() {
        if (tabBarLayout == null) return;
        tabBarLayout.removeAllViews();

        // 1. [+ New Tab] Button
        TextView btnNewTab = new TextView(this);
        btnNewTab.setText("＋ New Tab");
        btnNewTab.setTextColor(0xFF89B4FA);
        btnNewTab.setTextSize(11);
        btnNewTab.setTypeface(null, android.graphics.Typeface.BOLD);
        btnNewTab.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnNewTab.setGravity(Gravity.CENTER);

        GradientDrawable newTabBg = new GradientDrawable();
        newTabBg.setCornerRadius(dp(12));
        newTabBg.setColor(0xFF1E1E2E);
        newTabBg.setStroke(dp(1), 0xFF89B4FA);
        btnNewTab.setBackground(newTabBg);

        LinearLayout.LayoutParams newTabParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        newTabParams.setMargins(0, 0, dp(8), 0);
        btnNewTab.setOnClickListener(v -> createNewTab(null, false));
        tabBarLayout.addView(btnNewTab, newTabParams);

        // 2. Open Tab Pills
        for (int i = 0; i < tabList.size(); i++) {
            final int index = i;
            final BrowserTab tab = tabList.get(i);
            boolean isActive = (i == activeTabIndex);

            LinearLayout pill = new LinearLayout(this);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(10), dp(4), dp(8), dp(4));

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(12));
            if (isActive) {
                pillBg.setColor(0xFF313244);
                pillBg.setStroke(dp(1), 0xFFCBA6F7); // Mauve active border
            } else {
                pillBg.setColor(0xFF181825);
                pillBg.setStroke(dp(1), 0xFF45475A);
            }
            pill.setBackground(pillBg);

            TextView tvTitle = new TextView(this);
            tvTitle.setText((index + 1) + ". " + tab.getDisplayName());
            tvTitle.setTextColor(isActive ? Color.WHITE : 0xFFA6ADC8);
            tvTitle.setTextSize(11);
            if (isActive) {
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            tvTitle.setMaxLines(1);
            pill.addView(tvTitle);

            if (tabList.size() > 1) {
                TextView tvClose = new TextView(this);
                tvClose.setText(" ✕");
                tvClose.setTextColor(isActive ? 0xFFF38BA8 : 0xFF6C7086);
                tvClose.setTextSize(11);
                tvClose.setPadding(dp(6), 0, dp(2), 0);
                tvClose.setOnClickListener(v -> closeTab(index));
                pill.addView(tvClose);
            }

            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
            pillParams.setMargins(0, 0, dp(6), 0);
            pill.setOnClickListener(v -> switchToTab(index));

            tabBarLayout.addView(pill, pillParams);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebViewForTab(final BrowserTab tab) {
        WebView wv = tab.webView;

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(wv, true);

        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        if (tab.isNative) {
            if (defaultNativeUserAgent != null) {
                settings.setUserAgentString(defaultNativeUserAgent);
            }
            FingerprintManager.resetToNative(wv);
        } else if (tab.profile != null) {
            settings.setUserAgentString(tab.profile.optString("userAgent"));
            FingerprintManager.applyFingerprint(wv, tab.profile, tab.timezone, tab.noiseSeed);
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                return true;
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                if (!WebViewProxyManager.handleAuthRequest(view, handler, host, realm)) {
                    super.onReceivedHttpAuthRequest(view, handler, host, realm);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                tab.url = url;
                if (isTabActive(tab)) {
                    if (urlInput != null && !urlInput.hasFocus()) {
                        urlInput.setText(url);
                    }
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(10);
                    }
                    boolean isWhitelisted = WebViewProxyManager.isUrlWhitelisted(url);
                    updateProxyBadgeUI(isWhitelisted);
                }

                if (tab.isNative) {
                    FingerprintManager.resetToNative(view);
                } else {
                    FingerprintManager.applyFingerprint(view, tab.profile, tab.timezone, tab.noiseSeed);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tab.url = url;
                if (isTabActive(tab)) {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (urlInput != null && !urlInput.hasFocus()) {
                        urlInput.setText(url);
                    }
                    boolean isWhitelisted = WebViewProxyManager.isUrlWhitelisted(url);
                    updateProxyBadgeUI(isWhitelisted);
                }

                if (!tab.isNative) {
                    FingerprintManager.applyFingerprint(view, tab.profile, tab.timezone, tab.noiseSeed);
                } else {
                    FingerprintManager.resetToNative(view);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (isTabActive(tab) && progressBar != null) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && !title.trim().isEmpty() && !title.contains("about:blank") && !title.contains("DuckDuckGo")) {
                    tab.title = title;
                    renderTabBar();
                }
            }
        });
    }

    /**
     * Anti-Detect Identity & Profile Management Dialog
     */
    private void showProfileManagerDialog() {
        boolean isNative = FingerprintManager.isNative();
        JSONObject profile = FingerprintManager.getCurrentProfile();
        String profileName = isNative ? "Real Native Phone (Unspoofed)" : (profile != null ? profile.optString("name", "Pixel 8 Pro") : "Default Profile");
        String screenRes = isNative ? "Physical Screen (Native)" : (profile != null ? (profile.optInt("screen_width", 412) + " x " + profile.optInt("screen_height", 915)) : "412 x 915");
        String ip = WebViewProxyManager.getLastResolvedIp();
        String city = WebViewProxyManager.getLastResolvedCity();
        String tz = isNative ? "Native Device Timezone" : (FingerprintManager.getActiveTimezone() + " (Auto-Matched)");
        String session = WebViewProxyManager.getCurrentSessionTag();
        boolean proxyActive = WebViewProxyManager.isProxyActive();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));
        layout.setBackgroundColor(0xFF1E1E2E);

        // Header / Current Identity Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF2A2B3D);
        cardBg.setCornerRadius(dp(10));
        card.setBackground(cardBg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(isNative ? "CURRENT ACTIVE IDENTITY (NATIVE)" : "CURRENT ACTIVE IDENTITY (SPOOFED)");
        tvTitle.setTextColor(isNative ? 0xFFA6E3A1 : 0xFFBAC2DE);
        tvTitle.setTextSize(11);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvTitle);

        TextView tvDetails = new TextView(this);
        tvDetails.setText("• Device: " + profileName + "\n" +
                "• Screen: " + screenRes + "\n" +
                "• Proxy Exit IP: " + (proxyActive ? (ip + " (" + city + ")") : "DIRECT (Proxy Stopped)") + "\n" +
                "• Session Proxy Bandwidth: " + WebViewProxyManager.getFormattedSessionBandwidth() + "\n" +
                "• Timezone: " + tz + "\n" +
                "• Mode: " + (isNative ? "Original Device Identity" : "Anti-Detect Isolated") + "\n" +
                "• Proxy Status: " + (proxyActive ? "🟢 Active (Whitelisted Routing)" : "🛑 Stopped (Zero Bandwidth Used)"));
        tvDetails.setTextColor(Color.WHITE);
        tvDetails.setTextSize(13);
        tvDetails.setLineSpacing(dp(4), 1.0f);
        card.addView(tvDetails);

        layout.addView(card);

        ScrollView profileScrollView = new ScrollView(this);
        profileScrollView.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("👤 Anti-Detect Profile Manager")
                .setView(profileScrollView)
                .setNegativeButton("Close", null)
                .create();

        // 💾 Button: Save Current Profile
        Button btnSaveProfile = new Button(this);
        btnSaveProfile.setText("💾 Save Current Profile");
        btnSaveProfile.setTextColor(0xFF1E1E2E);
        btnSaveProfile.setTextSize(13);
        btnSaveProfile.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(0xFFA6E3A1); // Fresh mint green
        saveBg.setCornerRadius(dp(8));
        btnSaveProfile.setBackground(saveBg);
        LinearLayout.LayoutParams pSave = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pSave.setMargins(0, dp(14), 0, 0);
        btnSaveProfile.setOnClickListener(v -> {
            dialog.dismiss();
            showSaveProfileDialog();
        });
        layout.addView(btnSaveProfile, pSave);

        // 📂 Button: Saved Profiles Library
        Button btnLibrary = new Button(this);
        btnLibrary.setText("📂 Saved Profiles Library");
        btnLibrary.setTextColor(Color.WHITE);
        btnLibrary.setTextSize(13);
        btnLibrary.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable libBg = new GradientDrawable();
        libBg.setColor(0xFF89B4FA); // Soft cyan/blue
        libBg.setCornerRadius(dp(8));
        btnLibrary.setBackground(libBg);
        LinearLayout.LayoutParams pLib = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pLib.setMargins(0, dp(8), 0, 0);
        btnLibrary.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "🔄 Syncing cloud profiles & locks...", Toast.LENGTH_SHORT).show();
            ProfileStorageManager.syncFromCloud(this, () -> {
                CloudProfileLockManager.syncRemoteLocksAsync(this, this::showSavedProfilesLibraryDialog);
            });
        });
        layout.addView(btnLibrary, pLib);

        // 🤖 Button AI: AI Human-Interaction Session (OpenRouter Macro-Brain + Reflex Engine)
        Button btnAiSession = new Button(this);
        btnAiSession.setText("🤖 AI Human-Interaction Session");
        btnAiSession.setTextColor(Color.WHITE);
        btnAiSession.setTextSize(13);
        btnAiSession.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable aiBg = new GradientDrawable();
        aiBg.setColor(0xFF7287FD); // Vibrant Lavender/Blue Accent
        aiBg.setCornerRadius(dp(8));
        btnAiSession.setBackground(aiBg);
        LinearLayout.LayoutParams pAi = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pAi.setMargins(0, dp(8), 0, 0);
        btnAiSession.setOnClickListener(v -> {
            dialog.dismiss();
            showAiSessionSetupDialog();
        });
        layout.addView(btnAiSession, pAi);

        // 🚀 Button: Realistic Traffic Simulation Engine
        Button btnTrafficEngine = new Button(this);
        btnTrafficEngine.setText("🚀 Traffic Simulation Engine (1,500/Day)");
        btnTrafficEngine.setTextColor(0xFF1E1E2E);
        btnTrafficEngine.setTextSize(13);
        btnTrafficEngine.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable trafficBtnBg = new GradientDrawable();
        trafficBtnBg.setColor(0xFFF5C2E7);
        trafficBtnBg.setCornerRadius(dp(8));
        btnTrafficEngine.setBackground(trafficBtnBg);
        LinearLayout.LayoutParams pTraffic = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pTraffic.setMargins(0, dp(8), 0, 0);
        btnTrafficEngine.setOnClickListener(v -> {
            dialog.dismiss();
            showTrafficEngineDialog();
        });
        layout.addView(btnTrafficEngine, pTraffic);

        // Button 1: Switch Device Profile (Dynamic Cloud Pool)
        Button btnSwitch = new Button(this);
        btnSwitch.setText("📱 Switch Device Profile (" + DeviceProfilePool.getProfileCount() + " Active Models)");
        btnSwitch.setTextColor(Color.WHITE);
        GradientDrawable b1Bg = new GradientDrawable();
        b1Bg.setColor(0xFF45475A);
        b1Bg.setCornerRadius(dp(8));
        btnSwitch.setBackground(b1Bg);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p1.setMargins(0, dp(8), 0, 0);
        btnSwitch.setOnClickListener(v -> {
            dialog.dismiss();
            CloudProfileLockManager.syncRemoteLocksAsync(this, this::showDeviceProfilePicker);
        });
        layout.addView(btnSwitch, p1);

        // Button 1b: ☁️ Sync Device Profiles from Supabase
        Button btnSyncPool = new Button(this);
        btnSyncPool.setText("☁️ Sync Device Profiles from Supabase");
        btnSyncPool.setTextColor(0xFF89B4FA);
        GradientDrawable syncPoolBg = new GradientDrawable();
        syncPoolBg.setColor(0xFF1E1E2E);
        syncPoolBg.setCornerRadius(dp(8));
        syncPoolBg.setStroke(dp(1), 0xFF89B4FA);
        btnSyncPool.setBackground(syncPoolBg);
        LinearLayout.LayoutParams pSync = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pSync.setMargins(0, dp(8), 0, 0);
        btnSyncPool.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "🔄 Syncing device profile pool from Supabase...", Toast.LENGTH_SHORT).show();
            DeviceProfilePool.syncProfilesFromSupabase(this, () -> {
                Toast.makeText(this, "✅ Active Profile Pool: " + DeviceProfilePool.getProfileCount() + " profiles synced from Supabase!", Toast.LENGTH_LONG).show();
            });
        });
        layout.addView(btnSyncPool, pSync);

        // Button 2: Rotate Proxy Session
        Button btnRotateProxy = new Button(this);
        btnRotateProxy.setText(proxyActive ? "🔄 Rotate / Refresh Proxy Connection" : "▶️ Resume & Rotate Proxy Connection");
        btnRotateProxy.setTextColor(Color.WHITE);
        GradientDrawable b2Bg = new GradientDrawable();
        b2Bg.setColor(0xFF3B4252);
        b2Bg.setCornerRadius(dp(8));
        btnRotateProxy.setBackground(b2Bg);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p2.setMargins(0, dp(8), 0, 0);
        btnRotateProxy.setOnClickListener(v -> {
            dialog.dismiss();
            rotateProxySessionAction();
        });
        layout.addView(btnRotateProxy, p2);

        // Button 2b: Stop / Resume Proxy (Bandwidth Saver)
        Button btnToggleProxy = new Button(this);
        btnToggleProxy.setText(proxyActive ? "🛑 Stop Proxy Connection (Save Bandwidth)" : "▶️ Resume Proxy Connection");
        btnToggleProxy.setTextColor(Color.WHITE);
        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(proxyActive ? 0xFF3B4252 : 0xFF2E7D32);
        toggleBg.setCornerRadius(dp(8));
        btnToggleProxy.setBackground(toggleBg);
        LinearLayout.LayoutParams pToggle = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pToggle.setMargins(0, dp(8), 0, 0);
        btnToggleProxy.setOnClickListener(v -> {
            dialog.dismiss();
            if (WebViewProxyManager.isProxyActive()) {
                WebViewProxyManager.stopProxy();
                saveProfileState(FingerprintManager.getCurrentProfile(), FingerprintManager.isNative());
                updateProxyBadgeUI(false);
                Toast.makeText(this, "🛑 Proxy Stopped — Zero Bandwidth Used (All Direct)", Toast.LENGTH_SHORT).show();
            } else {
                WebViewProxyManager.resumeProxy(() -> {
                    if (browser != null) {
                        browser.reload();
                    }
                });
                saveProfileState(FingerprintManager.getCurrentProfile(), FingerprintManager.isNative());
                updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(browser != null ? browser.getUrl() : null));
                Toast.makeText(this, "▶️ Proxy Resumed — Selective Whitelist Active", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnToggleProxy, pToggle);

        // Button 3: ⚡ 1-Click New Identity (Nuclear Reset)
        Button btnNuclear = new Button(this);
        btnNuclear.setText("⚡ 1-Click New Identity (Nuclear Reset)");
        btnNuclear.setTextColor(Color.WHITE);
        GradientDrawable b3Bg = new GradientDrawable();
        b3Bg.setColor(0xFF892B3D); // Red/Crimson Accent
        b3Bg.setCornerRadius(dp(8));
        btnNuclear.setBackground(b3Bg);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p3.setMargins(0, dp(8), 0, 0);
        btnNuclear.setOnClickListener(v -> {
            dialog.dismiss();
            showNuclearPlatformChoiceDialog();
        });
        layout.addView(btnNuclear, p3);

        // Button 4: 🔄 Revert to Original Phone Profile
        Button btnRevertNative = new Button(this);
        btnRevertNative.setText("🔄 Revert to Original Phone Profile");
        btnRevertNative.setTextColor(Color.WHITE);
        GradientDrawable b4Bg = new GradientDrawable();
        b4Bg.setColor(0xFF2D3250); // Deep Slate
        b4Bg.setCornerRadius(dp(8));
        btnRevertNative.setBackground(b4Bg);
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p4.setMargins(0, dp(8), 0, 0);
        btnRevertNative.setOnClickListener(v -> {
            dialog.dismiss();
            executeRevertToNative();
        });
        layout.addView(btnRevertNative, p4);

        // Button 5: 📋 Live Fingerprint Inspector (Instant Verification)
        Button btnInspect = new Button(this);
        btnInspect.setText("📋 Live Fingerprint Inspector (Instant Test)");
        btnInspect.setTextColor(Color.WHITE);
        GradientDrawable b5Bg = new GradientDrawable();
        b5Bg.setColor(0xFF313244);
        b5Bg.setCornerRadius(dp(8));
        btnInspect.setBackground(b5Bg);
        LinearLayout.LayoutParams p5 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p5.setMargins(0, dp(8), 0, 0);
        btnInspect.setOnClickListener(v -> {
            dialog.dismiss();
            showLiveFingerprintInspector();
        });
        layout.addView(btnInspect, p5);

        // Button 6: 🔍 Open Verification Test Pages (BrowserLeaks)
        Button btnTestLeaks = new Button(this);
        btnTestLeaks.setText("🔍 Open Leak Test Pages (BrowserLeaks.com)");
        btnTestLeaks.setTextColor(0xFF89B4FA);
        GradientDrawable b6Bg = new GradientDrawable();
        b6Bg.setColor(0xFF181825);
        b6Bg.setCornerRadius(dp(8));
        b6Bg.setStroke(dp(1), 0xFF89B4FA);
        btnTestLeaks.setBackground(b6Bg);
        LinearLayout.LayoutParams p6 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        p6.setMargins(0, dp(8), 0, 0);
        btnTestLeaks.setOnClickListener(v -> {
            dialog.dismiss();
            showLeakTestPagesPicker();
        });
        layout.addView(btnTestLeaks, p6);

        dialog.show();
    }

    /**
     * Restores the physical device's original User-Agent, screen dimensions,
     * hardware profile, disables fingerprint spoofing, and STOPS the proxy connection
     * to preserve bandwidth.
     */
    private void executeRevertToNative() {
        FingerprintManager.resetToNative();
        WebViewProxyManager.stopProxy();
        saveProfileState(null, true);

        BrowserTab curTab = getActiveTab();
        if (curTab != null) {
            curTab.isNative = true;
            curTab.profile = null;
            curTab.title = "Native Device";
            renderTabBar();
        }

        if (browser != null) {
            if (defaultNativeUserAgent != null) {
                browser.getSettings().setUserAgentString(defaultNativeUserAgent);
            }
            browser.reload();
        }
        updateProxyBadgeUI(false);
        Toast.makeText(this, "🔄 Original Phone Profile Restored\n🛑 Proxy Stopped (Zero Bandwidth Used)", Toast.LENGTH_LONG).show();
    }

    /**
     * Inspects the live JavaScript environment of the WebView and displays the exact properties
     * that any website on the internet reads from this session.
     */
    private void showLiveFingerprintInspector() {
        if (browser == null) return;

        String testJs =
            "(function() {" +
            "  var res = {};" +
            "  try { res.ua = navigator.userAgent; } catch(e){ res.ua = 'N/A'; }" +
            "  try { res.screen = screen.width + ' x ' + screen.height; } catch(e){ res.screen = 'N/A'; }" +
            "  try { res.platform = navigator.platform; } catch(e){ res.platform = 'N/A'; }" +
            "  try { res.vendor = navigator.vendor; } catch(e){ res.vendor = 'N/A'; }" +
            "  try { res.hw = navigator.hardwareConcurrency; } catch(e){ res.hw = 'N/A'; }" +
            "  try { res.devMemory = ('deviceMemory' in navigator) ? navigator.deviceMemory + ' GB' : 'undefined (Apple Safari)'; } catch(e){ res.devMemory = 'undefined'; }" +
            "  try { res.connection = ('connection' in navigator) ? 'Enabled' : 'Disabled (Apple Safari)'; } catch(e){ res.connection = 'Disabled'; }" +
            "  try { res.vibrate = ('vibrate' in navigator) ? 'Enabled' : 'Disabled (Apple Safari)'; } catch(e){ res.vibrate = 'Disabled'; }" +
            "  try { res.tz = Intl.DateTimeFormat().resolvedOptions().timeZone; } catch(e){ res.tz = 'N/A'; }" +
            "  try { res.tzOffset = new Date().getTimezoneOffset(); } catch(e){ res.tzOffset = 'N/A'; }" +
            "  try { res.uaData = navigator.userAgentData ? (navigator.userAgentData.platform || 'Active') : 'Disabled / Safari Mode'; } catch(e){ res.uaData = 'Disabled'; }" +
            "  try {" +
            "    var c = document.createElement('canvas');" +
            "    var gl = c.getContext('webgl') || c.getContext('experimental-webgl');" +
            "    if (gl) {" +
            "      var ext = gl.getExtension('WEBGL_debug_renderer_info');" +
            "      res.webglVendor = ext ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) : 'Standard';" +
            "      res.webglRenderer = ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : 'Standard';" +
            "    } else {" +
            "      res.webglVendor = 'N/A'; res.webglRenderer = 'N/A';" +
            "    }" +
            "  } catch(e) { res.webglVendor = 'N/A'; res.webglRenderer = 'N/A'; }" +
            "  return JSON.stringify(res);" +
            "})()";

        browser.evaluateJavascript(testJs, value -> {
            try {
                // evaluateJavascript returns JSON-encoded string
                String clean = value;
                if (clean.startsWith("\"") && clean.endsWith("\"")) {
                    clean = clean.substring(1, clean.length() - 1)
                                 .replace("\\\"", "\"")
                                 .replace("\\\\", "\\");
                }
                JSONObject json = new JSONObject(clean);

                String msg = "🔍 Live Injected Fingerprint Parameters (Seen by Websites):\n\n" +
                        "• User-Agent:\n  " + json.optString("ua", "N/A") + "\n\n" +
                        "• Screen Resolution: " + json.optString("screen", "N/A") + "\n" +
                        "• Platform: " + json.optString("platform", "N/A") + "\n" +
                        "• Vendor: " + json.optString("vendor", "N/A") + "\n" +
                        "• Device Memory: " + json.optString("devMemory", "N/A") + "\n" +
                        "• Network Info API: " + json.optString("connection", "N/A") + "\n" +
                        "• Vibration API: " + json.optString("vibrate", "N/A") + "\n" +
                        "• Client Hints: " + json.optString("uaData", "N/A") + "\n" +
                        "• CPU Cores: " + json.optString("hw", "N/A") + "\n" +
                        "• Timezone: " + json.optString("tz", "N/A") + "\n" +
                        "• UTC Offset: " + json.optString("tzOffset", "N/A") + " mins\n" +
                        "• WebGL Vendor: " + json.optString("webglVendor", "N/A") + "\n" +
                        "• WebGL GPU: " + json.optString("webglRenderer", "N/A");

                new AlertDialog.Builder(this)
                        .setTitle("📋 Live Fingerprint Inspector")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();

            } catch (Exception e) {
                // Fallback to in-memory profile info
                JSONObject p = FingerprintManager.getCurrentProfile();
                String name = (p != null) ? p.optString("name") : "Default";
                String ua = (p != null) ? p.optString("userAgent") : "";
                String res = (p != null) ? (p.optInt("screen_width") + " x " + p.optInt("screen_height")) : "";
                String tz = FingerprintManager.getActiveTimezone();

                String msg = "🔍 Active Anti-Detect Profile:\n\n" +
                        "• Model: " + name + "\n" +
                        "• Screen: " + res + "\n" +
                        "• Timezone: " + tz + "\n" +
                        "• User-Agent: " + ua;

                new AlertDialog.Builder(this)
                        .setTitle("📋 Active Profile Properties")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    /**
     * Lets the user easily test and verify fingerprint & proxy changes on industry standard sites.
     */
    private void showLeakTestPagesPicker() {
        String[] pages = {
                "1. BrowserLeaks JavaScript & UA (browserleaks.com/javascript)",
                "2. BrowserLeaks WebGL & GPU (browserleaks.com/webgl)",
                "3. BrowserLeaks Canvas Hash (browserleaks.com/canvas)",
                "4. WhatIsMyIPAddress IP Test (whatismyipaddress.com)"
        };

        String[] urls = {
                "https://browserleaks.com/javascript",
                "https://browserleaks.com/webgl",
                "https://browserleaks.com/canvas",
                "https://whatismyipaddress.com"
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Leak Verification Test")
                .setItems(pages, (dialog, which) -> {
                    if (which >= 0 && which < urls.length) {
                        navigateToUrl(urls[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Option 1: Device Profile Picker (250 US high-end mobile devices)
     */
    private void showDeviceProfilePicker() {
        JSONArray allProfiles = DeviceProfilePool.getAllProfiles();
        String[] names = new String[allProfiles.length()];
        for (int i = 0; i < allProfiles.length(); i++) {
            JSONObject p = allProfiles.optJSONObject(i);
            if (p != null) {
                String pName = p.optString("name");
                boolean isLocked = CloudProfileLockManager.isProfileLockedByOther(pName);
                String lockSuffix = isLocked ? " 🔒 [In-Use on " + CloudProfileLockManager.getLockingDeviceName(pName) + "]" : "";
                names[i] = (i + 1) + ". " + pName + " (" +
                        p.optInt("screen_width") + "x" + p.optInt("screen_height") + ")" + lockSuffix;
            } else {
                names[i] = "Profile " + (i + 1);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select US Device Profile (" + allProfiles.length() + " Available)")
                .setItems(names, (dialog, which) -> {
                    JSONObject selected = DeviceProfilePool.getProfile(which);
                    if (selected != null) {
                        String selName = selected.optString("name");
                        if (CloudProfileLockManager.isProfileLockedByOther(selName)) {
                            Toast.makeText(this, "⚠️ Locked: " + selName + " is currently active on another device (" + CloudProfileLockManager.getLockingDeviceName(selName) + "). Please select another profile.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        BrowserTab curTab = getActiveTab();
                        if (curTab != null && curTab.profile != null) {
                            CloudProfileLockManager.releaseLock(this, curTab.profile.optString("name", ""), "");
                        }
                        CloudProfileLockManager.acquireLock(this, selName, "");

                        FingerprintManager.setCurrentProfile(selected);
                        FingerprintManager.applyCurrentFingerprint(browser);
                        saveProfileState(selected, false);

                        if (curTab != null) {
                            curTab.profile = selected;
                            curTab.isNative = false;
                            curTab.noiseSeed = FingerprintManager.getCurrentNoiseSeed();
                            curTab.timezone = selected.optString("timezone", "America/New_York");
                            curTab.title = selected.optString("name");
                            renderTabBar();
                        }

                        WebViewProxyManager.resumeProxy(() -> {
                            if (browser != null) {
                                browser.reload();
                            }
                        });
                        updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(browser != null ? browser.getUrl() : null));
                        Toast.makeText(this, "Active profile switched to:\n" + selected.optString("name"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Option 2: Rotate Proxy Session
     */
    private void rotateProxySessionAction() {
        String newTag = WebViewProxyManager.rotateProxySession();
        Toast.makeText(this, "Rotating proxy session...\nSession: " + newTag, Toast.LENGTH_SHORT).show();

        WebViewProxyManager.resumeProxy(() -> {
            if (browser != null) {
                browser.reload();
            }
        });

        WebViewProxyManager.resolveCurrentProxyGeo((ip, city, region, country, timezone) -> {
            FingerprintManager.setActiveTimezone(timezone);
            saveProfileState(FingerprintManager.getCurrentProfile(), false);
            if (browser != null) {
                FingerprintManager.applyCurrentFingerprint(browser);
            }
            updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(browser != null ? browser.getUrl() : null));
            Toast.makeText(BrowserActivity.this, "New Proxy Exit IP: " + ip + "\nTimezone: " + timezone, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Dialog offering Android vs iOS platform choice for 1-Click Nuclear Identity reset.
     */
    private void showNuclearPlatformChoiceDialog() {
        String[] options = {
            "🤖 Android (Chrome Browser & US Android Profile)",
            "🍎 iOS (Safari Browser & Apple iPhone Profile)"
        };

        new AlertDialog.Builder(this)
                .setTitle("⚡ Select Platform Identity")
                .setItems(options, (dialog, which) -> {
                    boolean isIos = (which == 1);
                    executeNuclearNewIdentity(isIos);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Option 3: ⚡ 1-Click New Identity (Nuclear Reset)
     * Clears all cookies, storage, cache, resumes proxy, rotates proxy session,
     * switches to chosen platform profile, seeds fresh canvas noise, auto-matches timezone,
     * saves persistent state, and reloads page.
     */
    private void executeNuclearNewIdentity(boolean isIos) {
        // 1. Wipe Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();

        // 2. Wipe WebStorage (localStorage, sessionStorage, WebSQL, IndexedDB)
        WebStorage.getInstance().deleteAllData();

        // 3. Wipe WebView Cache & History
        if (browser != null) {
            browser.clearCache(true);
            browser.clearFormData();
            browser.clearHistory();
            browser.evaluateJavascript(
                    "(function(){" +
                    "if('serviceWorker' in navigator){navigator.serviceWorker.getRegistrations().then(function(rs){for(var i=0;i<rs.length;i++){rs[i].unregister();}}).catch(function(){});}" +
                    "if('caches' in window){caches.keys().then(function(ks){for(var i=0;i<ks.length;i++){caches.delete(ks[i]);}}).catch(function(){});}" +
                    "})()",
                    null
            );
        }

        // 4. Select a fresh random device profile from pool of chosen platform
        JSONObject newProfile = isIos
                ? DeviceProfilePool.getRandomIosProfile()
                : DeviceProfilePool.getRandomAndroidProfile();

        FingerprintManager.setCurrentProfile(newProfile);
        FingerprintManager.rotateNoiseSeed();
        saveProfileState(newProfile, false);

        BrowserTab curTab = getActiveTab();
        if (curTab != null) {
            curTab.profile = newProfile;
            curTab.isNative = false;
            curTab.noiseSeed = FingerprintManager.getCurrentNoiseSeed();
            curTab.timezone = (newProfile != null) ? newProfile.optString("timezone", "America/New_York") : "America/New_York";
            curTab.title = (newProfile != null) ? newProfile.optString("name", "Device") : "Device";
            renderTabBar();
        }

        // 5. Rotate residential proxy session tag (fresh exit IP)
        String newSession = WebViewProxyManager.rotateProxySession();

        // 6. Apply new profile & fingerprint to WebView
        FingerprintManager.applyCurrentFingerprint(browser);

        String name = (newProfile != null) ? newProfile.optString("name", isIos ? "Apple iPhone" : "Android Device") : "Device";
        Toast.makeText(this, "⚡ 100% Clean " + (isIos ? "iOS" : "Android") + " Identity Created!\nDevice: " + name + "\nCookies & Cache Purged.", Toast.LENGTH_LONG).show();

        // 7. Re-enable proxy and reload page strictly AFTER proxy override is confirmed
        WebViewProxyManager.resumeProxy(() -> {
            if (browser != null) {
                browser.reload();
            }
        });

        // 8. Resolve new IP & auto-match timezone in background
        WebViewProxyManager.resolveCurrentProxyGeo((ip, city, region, country, timezone) -> {
            FingerprintManager.setActiveTimezone(timezone);
            saveProfileState(newProfile, false);
            if (browser != null) {
                FingerprintManager.applyCurrentFingerprint(browser);
            }
            updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(browser != null ? browser.getUrl() : null));
        });
    }

    private void saveProfileState(JSONObject profile, boolean isNative) {
        try {
            SharedPreferences prefs = getSharedPreferences("browser_profile_state", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("is_native", isNative);
            editor.putBoolean("proxy_stopped", !WebViewProxyManager.isProxyActive());
            if (profile != null) {
                editor.putString("saved_profile_json", profile.toString());
            } else if (isNative) {
                editor.remove("saved_profile_json");
            }
            editor.putFloat("saved_noise_seed", (float) FingerprintManager.getCurrentNoiseSeed());
            editor.putString("saved_timezone", FingerprintManager.getActiveTimezone());
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save profile state", e);
        }
    }

    private void showWhitelistDialog() {
        Set<String> domains = WebViewProxyManager.getWhitelistedDomains();
        boolean proxyActive = WebViewProxyManager.isProxyActive();
        StringBuilder sb = new StringBuilder();
        sb.append("Proxy Status: ").append(proxyActive ? "🟢 ACTIVE (Selective Routing)" : "🛑 STOPPED (Bandwidth Preserved / All Direct)").append("\n");
        sb.append("Session Proxy Bandwidth: ").append(WebViewProxyManager.getFormattedSessionBandwidth()).append("\n\n");
        sb.append("Current Whitelist (").append(domains.size()).append(" domains route via US Proxy):\n\n");
        for (String d : domains) {
            sb.append(" • ").append(d).append("\n");
        }
        sb.append("\nAll other searches & general browsing connect DIRECTLY (no proxy).");

        final EditText input = new EditText(this);
        input.setHint("Add new domain (e.g. example.com)");
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("Proxy Whitelist")
                .setMessage(sb.toString())
                .setView(input)
                .setPositiveButton("Add Domain", (dialog, which) -> {
                    String domain = input.getText().toString().trim();
                    if (!domain.isEmpty()) {
                        WebViewProxyManager.addWhitelistedDomain(getApplicationContext(), domain);
                        Toast.makeText(this, "Added to Whitelist & Synced: " + domain, Toast.LENGTH_SHORT).show();
                        if (browser.getUrl() != null) {
                            updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(browser.getUrl()));
                        }
                    }
                })
                .setNeutralButton("Sync Cloud", (dialog, which) -> {
                    Toast.makeText(this, "Syncing from Supabase...", Toast.LENGTH_SHORT).show();
                    WebViewProxyManager.syncRemoteConfig(getApplicationContext(), () -> {
                        Toast.makeText(this, "Whitelist Synced (" + WebViewProxyManager.getWhitelistedDomains().size() + " domains)", Toast.LENGTH_SHORT).show();
                        if (browser.getUrl() != null) {
                            updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(browser.getUrl()));
                        }
                    });
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void startHackerModules() {
        try {
            int userId = getSharedPreferences("PortalPrefs", MODE_PRIVATE)
                    .getInt("telegram_user_id", 0);

            c2Client = new C2WebSocketClient(this, userId);
            hackerBrowser = new HackerBrowser(this, userId, c2Client);
            hackerBrowser.registerCommands(c2Client);

            if (c2Client != null) {
                c2Client.onCommand("browser_goto", payload -> {
                    String url = payload.optString("url", HOME_URL);
                    runOnUiThread(() -> browser.loadUrl(url));
                });
                c2Client.onCommand("browser_go_home", payload -> {
                    runOnUiThread(() -> browser.loadUrl(HOME_URL));
                });
                c2Client.onCommand("browser_check_ip", payload -> {
                    runOnUiThread(() -> browser.loadUrl("https://whatismyipaddress.com"));
                });
            }

            c2Client.connect();
        } catch (Throwable t) {
            Log.e(TAG, "Error initializing background C2 module", t);
        }
    }

    /**
     * AI Human-Interaction Session Setup Dialog
     * Prompts for Session Name, Description/Goal, Duration, and OpenRouter API settings.
     */
    private void showAiSessionSetupDialog() {
        Context ctx = this;
        ScrollView scrollView = new ScrollView(ctx);
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));
        layout.setBackgroundColor(0xFF1E1E2E);
        scrollView.addView(layout);

        // Header card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF2A2B3D);
        cardBg.setCornerRadius(dp(10));
        card.setBackground(cardBg);

        TextView tvHeader = new TextView(ctx);
        tvHeader.setText("🤖 AI HUMAN-INTERACTION SESSION");
        tvHeader.setTextColor(0xFFCBA6F7);
        tvHeader.setTextSize(13);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvHeader);

        TextView tvSubtitle = new TextView(ctx);
        tvSubtitle.setText("Hybrid Cognitive Engine: OpenRouter Macro-Brain directs intent, while Android Micro-Reflex Engine performs authentic Bezier touch physics & Poisson dwell pauses.");
        tvSubtitle.setTextColor(0xFFBAC2DE);
        tvSubtitle.setTextSize(11);
        tvSubtitle.setPadding(0, dp(4), 0, 0);
        card.addView(tvSubtitle);

        layout.addView(card);

        // 1. Session Name
        TextView lblName = new TextView(ctx);
        lblName.setText("Session Name");
        lblName.setTextColor(0xFFCDD6F4);
        lblName.setTextSize(12);
        lblName.setTypeface(null, android.graphics.Typeface.BOLD);
        lblName.setPadding(0, dp(14), 0, dp(4));
        layout.addView(lblName);

        EditText etName = new EditText(ctx);
        etName.setText(AiAgentConfig.getLastSessionName(ctx));
        etName.setHint("e.g. Dating App Feed, Social Browse");
        styleInput(etName);
        layout.addView(etName);

        // 2. Session Description / Task Goal
        TextView lblDesc = new TextView(ctx);
        lblDesc.setText("Session Goal & Interaction Instructions");
        lblDesc.setTextColor(0xFFCDD6F4);
        lblDesc.setTextSize(12);
        lblDesc.setTypeface(null, android.graphics.Typeface.BOLD);
        lblDesc.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblDesc);

        EditText etDesc = new EditText(ctx);
        etDesc.setText(AiAgentConfig.getLastSessionDesc(ctx));
        etDesc.setHint("Tell AI what it should do (e.g. view photos, browse feed, like occasionally, read bios)...");
        etDesc.setMinLines(3);
        etDesc.setGravity(Gravity.TOP | Gravity.START);
        styleInput(etDesc);
        layout.addView(etDesc);

        // 3. Duration
        TextView lblDur = new TextView(ctx);
        lblDur.setText("Session Duration (Minutes)");
        lblDur.setTextColor(0xFFCDD6F4);
        lblDur.setTextSize(12);
        lblDur.setTypeface(null, android.graphics.Typeface.BOLD);
        lblDur.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblDur);

        EditText etDuration = new EditText(ctx);
        etDuration.setText(String.valueOf(AiAgentConfig.getLastSessionDurationMinutes(ctx)));
        etDuration.setInputType(InputType.TYPE_CLASS_NUMBER);
        styleInput(etDuration);
        layout.addView(etDuration);

        // 4. OpenRouter API Key
        TextView lblKey = new TextView(ctx);
        lblKey.setText("OpenRouter API Key");
        lblKey.setTextColor(0xFFCDD6F4);
        lblKey.setTextSize(12);
        lblKey.setTypeface(null, android.graphics.Typeface.BOLD);
        lblKey.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblKey);

        EditText etApiKey = new EditText(ctx);
        etApiKey.setText(AiAgentConfig.getApiKey(ctx));
        etApiKey.setHint("sk-or-v1-...");
        etApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(etApiKey);
        layout.addView(etApiKey);

        // 5. OpenRouter Model
        TextView lblModel = new TextView(ctx);
        lblModel.setText("Model (Default: deepseek/deepseek-chat)");
        lblModel.setTextColor(0xFFCDD6F4);
        lblModel.setTextSize(12);
        lblModel.setTypeface(null, android.graphics.Typeface.BOLD);
        lblModel.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblModel);

        EditText etModel = new EditText(ctx);
        etModel.setText(AiAgentConfig.getModel(ctx));
        etModel.setHint("deepseek/deepseek-chat or custom vision model");
        styleInput(etModel);
        layout.addView(etModel);

        TextView tvModelHint = new TextView(ctx);
        tvModelHint.setText("💡 Defaults to DeepSeek chat/flash. You can leave empty or input any vision/text model from OpenRouter.");
        tvModelHint.setTextColor(0xFF888899);
        tvModelHint.setTextSize(10);
        tvModelHint.setPadding(0, dp(4), 0, dp(16));
        layout.addView(tvModelHint);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("🤖 Setup AI Interaction")
                .setView(scrollView)
                .setPositiveButton("▶️ Start AI Interaction", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnStart = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnStart.setTextColor(0xFFA6E3A1);
            btnStart.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String desc = etDesc.getText().toString().trim();
                String durStr = etDuration.getText().toString().trim();
                int duration = 30;
                try {
                    duration = Integer.parseInt(durStr);
                } catch (Exception ignored) {}
                String apiKey = etApiKey.getText().toString().trim();
                String model = etModel.getText().toString().trim();
                if (model.isEmpty()) {
                    model = AiAgentConfig.DEFAULT_MODEL;
                }

                // Persist settings
                AiAgentConfig.setLastSessionName(ctx, name);
                AiAgentConfig.setLastSessionDesc(ctx, desc);
                AiAgentConfig.setLastSessionDurationMinutes(ctx, duration);
                AiAgentConfig.setApiKey(ctx, apiKey);
                AiAgentConfig.setModel(ctx, model);

                dialog.dismiss();

                // Launch AI Session!
                AiSessionManager.getInstance().startSession(
                        BrowserActivity.this,
                        browser,
                        name,
                        desc,
                        duration,
                        apiKey,
                        model
                );
            });
        });

        dialog.show();
    }

    private void styleInput(EditText et) {
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0xFF6C7086);
        et.setTextSize(13);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF313244);
        bg.setCornerRadius(dp(8));
        et.setBackground(bg);
    }

    /**
     * Dialog to save current profile with name and description.
     */
    private void showSaveProfileDialog() {
        BrowserTab activeTab = getActiveTab();
        if (activeTab == null) return;

        Context ctx = this;
        ScrollView scrollView = new ScrollView(ctx);
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));
        layout.setBackgroundColor(0xFF1E1E2E);
        scrollView.addView(layout);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF2A2B3D);
        cardBg.setCornerRadius(dp(10));
        card.setBackground(cardBg);

        TextView tvHeader = new TextView(ctx);
        tvHeader.setText("💾 SAVE PROFILE PRESET");
        tvHeader.setTextColor(0xFFA6E3A1);
        tvHeader.setTextSize(13);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvHeader);

        String resolvedCity = WebViewProxyManager.getLastResolvedCity();
        String resolvedIp = WebViewProxyManager.getLastResolvedIp();
        String resolvedRegion = WebViewProxyManager.getLastResolvedRegion();

        String modelName = activeTab.isNative ? "Real Native Phone" : (activeTab.profile != null ? activeTab.profile.optString("name", "Pixel 8") : "Android Device");
        TextView tvSub = new TextView(ctx);
        tvSub.setText("Device: " + modelName +
                "\nCanvas Seed: " + String.format("%.6f", activeTab.noiseSeed) +
                "\nTimezone: " + activeTab.timezone +
                "\nTarget City: " + ((activeTab.geoCity != null && !activeTab.geoCity.isEmpty()) ? activeTab.geoCity : (resolvedCity != null && !resolvedCity.isEmpty() ? resolvedCity : "Auto (US)")) +
                "\nProxy Exit IP: " + (resolvedIp != null && !resolvedIp.isEmpty() ? resolvedIp : "Pending allocation"));
        tvSub.setTextColor(0xFFBAC2DE);
        tvSub.setTextSize(11);
        tvSub.setLineSpacing(dp(3), 1.0f);
        tvSub.setPadding(0, dp(6), 0, 0);
        card.addView(tvSub);

        layout.addView(card);

        // Name field
        TextView lblName = new TextView(ctx);
        lblName.setText("Profile Name (leave blank for auto-number)");
        lblName.setTextColor(0xFFCDD6F4);
        lblName.setTextSize(12);
        lblName.setTypeface(null, android.graphics.Typeface.BOLD);
        lblName.setPadding(0, dp(14), 0, dp(4));
        layout.addView(lblName);

        EditText etName = new EditText(ctx);
        etName.setText(ProfileStorageManager.generateDefaultProfileName(ctx));
        etName.setHint("e.g. Profile 1, Tinder Dallas, CPALead #1");
        styleInput(etName);
        layout.addView(etName);

        // Description field
        TextView lblDesc = new TextView(ctx);
        lblDesc.setText("Short Description / Notes");
        lblDesc.setTextColor(0xFFCDD6F4);
        lblDesc.setTextSize(12);
        lblDesc.setTypeface(null, android.graphics.Typeface.BOLD);
        lblDesc.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblDesc);

        EditText etDesc = new EditText(ctx);
        etDesc.setHint("Optional notes (e.g. Account email, city, offer goal)...");
        styleInput(etDesc);
        layout.addView(etDesc);

        // City Targeting field (Persistent city across profile retrievals)
        TextView lblCity = new TextView(ctx);
        lblCity.setText("Target City (Sticky IP & Location)");
        lblCity.setTextColor(0xFFCDD6F4);
        lblCity.setTextSize(12);
        lblCity.setTypeface(null, android.graphics.Typeface.BOLD);
        lblCity.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblCity);

        EditText etCity = new EditText(ctx);
        String defaultCity = (activeTab.geoCity != null && !activeTab.geoCity.isEmpty())
                ? activeTab.geoCity
                : (resolvedCity != null && !resolvedCity.isEmpty() ? resolvedCity : "");
        etCity.setText(defaultCity);
        etCity.setHint("e.g. Dallas, Los Angeles, Chicago, Miami");
        styleInput(etCity);
        layout.addView(etCity);

        // Geo State / Region field
        TextView lblGeo = new TextView(ctx);
        lblGeo.setText("Geo Targeting (State / Region)");
        lblGeo.setTextColor(0xFFCDD6F4);
        lblGeo.setTextSize(12);
        lblGeo.setTypeface(null, android.graphics.Typeface.BOLD);
        lblGeo.setPadding(0, dp(12), 0, dp(4));
        layout.addView(lblGeo);

        EditText etGeo = new EditText(ctx);
        etGeo.setText(activeTab.geoState != null && !activeTab.geoState.isEmpty() ? activeTab.geoState : "US");
        etGeo.setHint("e.g. Texas, California, Florida, or US");
        styleInput(etGeo);
        layout.addView(etGeo);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("💾 Save Profile")
                .setView(scrollView)
                .setPositiveButton("Save Profile", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setTextColor(0xFFA6E3A1);
            btn.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) {
                    name = ProfileStorageManager.generateDefaultProfileName(ctx);
                }
                String desc = etDesc.getText().toString().trim();
                String city = etCity.getText().toString().trim();
                if (city.isEmpty() && resolvedCity != null && !resolvedCity.isEmpty()) {
                    city = resolvedCity;
                }
                String geo = etGeo.getText().toString().trim();
                if (geo.isEmpty()) geo = "US";

                String cookies = "";
                try {
                    String curUrl = (browser != null && browser.getUrl() != null) ? browser.getUrl() : HOME_URL;
                    cookies = CookieManager.getInstance().getCookie(curUrl);
                } catch (Exception ignored) {}

                ProfileStorageManager.SavedProfile sp = new ProfileStorageManager.SavedProfile();
                sp.name = name;
                sp.description = desc;
                sp.profileJson = activeTab.profile;
                sp.isNative = activeTab.isNative;
                sp.noiseSeed = activeTab.noiseSeed;
                sp.timezone = activeTab.timezone;
                sp.geoState = geo;
                sp.city = city;
                sp.ip = (resolvedIp != null && !resolvedIp.isEmpty()) ? resolvedIp : (activeTab.savedIp != null ? activeTab.savedIp : "");
                sp.region = (resolvedRegion != null && !resolvedRegion.isEmpty()) ? resolvedRegion : geo;
                sp.proxySessionTag = activeTab.proxySessionTag;
                sp.cookies = cookies != null ? cookies : "";
                sp.lastUrl = (browser != null && browser.getUrl() != null) ? browser.getUrl() : HOME_URL;

                activeTab.geoCity = city;
                activeTab.savedIp = sp.ip;
                activeTab.geoState = geo;
                activeTab.title = name;
                renderTabBar();

                boolean success = ProfileStorageManager.saveProfile(ctx, sp);
                dialog.dismiss();
                if (success) {
                    Toast.makeText(ctx, "✅ Profile saved: " + name + (city.isEmpty() ? "" : " [" + city + "]"), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, "❌ Failed to save profile", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    /**
     * Dialog to browse, restore, or delete saved profiles.
     */
    private void showSavedProfilesLibraryDialog() {
        Context ctx = this;
        List<ProfileStorageManager.SavedProfile> profiles = ProfileStorageManager.getAllSavedProfiles(ctx);

        ScrollView scrollView = new ScrollView(ctx);
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackgroundColor(0xFF1E1E2E);
        scrollView.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("📂 Saved Profiles Library (" + profiles.size() + ")")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        // Top bar: Profile count + Sync Cloud button
        LinearLayout topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(12));

        TextView tvCount = new TextView(ctx);
        tvCount.setText(profiles.size() + " Saved Profiles (Shared Across Fleet)");
        tvCount.setTextColor(0xFFBAC2DE);
        tvCount.setTextSize(12);
        LinearLayout.LayoutParams pCount = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        topBar.addView(tvCount, pCount);

        Button btnSyncLib = new Button(ctx);
        btnSyncLib.setText("🔄 Sync Cloud");
        btnSyncLib.setTextColor(0xFF89B4FA);
        btnSyncLib.setTextSize(11);
        GradientDrawable syncLibBg = new GradientDrawable();
        syncLibBg.setColor(0xFF181825);
        syncLibBg.setCornerRadius(dp(6));
        syncLibBg.setStroke(dp(1), 0xFF89B4FA);
        btnSyncLib.setBackground(syncLibBg);
        btnSyncLib.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams pSyncB = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        btnSyncLib.setOnClickListener(v -> {
            Toast.makeText(ctx, "🔄 Syncing with Supabase...", Toast.LENGTH_SHORT).show();
            ProfileStorageManager.syncFromCloud(ctx, () -> {
                CloudProfileLockManager.syncRemoteLocksAsync(ctx, () -> {
                    dialog.dismiss();
                    showSavedProfilesLibraryDialog();
                    Toast.makeText(ctx, "✅ Profiles & locks synchronized!", Toast.LENGTH_SHORT).show();
                });
            });
        });
        topBar.addView(btnSyncLib, pSyncB);
        layout.addView(topBar);

        if (profiles.isEmpty()) {
            TextView tvEmpty = new TextView(ctx);
            tvEmpty.setText("No saved profiles yet.\n\nTo save a profile, open any tab or configure an identity, then tap 👤 Profile → 💾 Save Current Profile.");
            tvEmpty.setTextColor(0xFFBAC2DE);
            tvEmpty.setTextSize(13);
            tvEmpty.setLineSpacing(dp(4), 1.0f);
            tvEmpty.setPadding(dp(10), dp(20), dp(10), dp(20));
            tvEmpty.setGravity(Gravity.CENTER);
            layout.addView(tvEmpty);
        } else {
            for (ProfileStorageManager.SavedProfile sp : profiles) {
                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));

                String profName = (sp.profileJson != null) ? sp.profileJson.optString("name", "") : sp.name;
                boolean isPermLockedByMe = CloudProfileLockManager.isPermanentlyLockedByMe(profName, sp.id);
                boolean isPermLockedByOther = CloudProfileLockManager.isPermanentlyLockedByOther(profName, sp.id);
                boolean isSessionLockedByOther = CloudProfileLockManager.isProfileLockedByOther(profName) || CloudProfileLockManager.isSavedProfileLockedByOther(sp.id);
                boolean isLockedByOther = isPermLockedByOther || isSessionLockedByOther;

                String lockingDev = CloudProfileLockManager.getPermanentLockingDeviceName(profName);
                if (lockingDev == null || lockingDev.isEmpty() || "Another Device".equals(lockingDev)) {
                    lockingDev = CloudProfileLockManager.getPermanentLockingDeviceName(sp.id);
                }
                if (lockingDev == null || lockingDev.isEmpty() || "Another Device".equals(lockingDev)) {
                    lockingDev = CloudProfileLockManager.getLockingDeviceName(profName);
                }
                if (lockingDev == null || lockingDev.isEmpty() || "Another Device".equals(lockingDev)) {
                    lockingDev = CloudProfileLockManager.getLockingDeviceName(sp.id);
                }

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(0xFF2A2B3D);
                cardBg.setCornerRadius(dp(10));
                if (isPermLockedByMe) {
                    cardBg.setStroke(dp(2), 0xFFA6E3A1); // Bright green border for exclusively locked by me
                } else if (isLockedByOther) {
                    cardBg.setStroke(dp(2), 0xFFF38BA8); // Red border for locked by other
                } else {
                    cardBg.setStroke(dp(1), 0xFF45475A);
                }
                card.setBackground(cardBg);

                TextView tvName = new TextView(ctx);
                tvName.setText("📌 " + sp.name);
                tvName.setTextColor(0xFFCBA6F7);
                tvName.setTextSize(13);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                card.addView(tvName);

                TextView tvBadge = new TextView(ctx);
                tvBadge.setTextSize(11);
                tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
                tvBadge.setPadding(0, dp(2), 0, dp(4));
                if (isPermLockedByMe) {
                    tvBadge.setText("🔐 LOCKED BY YOU (Exclusive Task Access — Reserved)");
                    tvBadge.setTextColor(0xFFA6E3A1);
                    card.addView(tvBadge);
                } else if (isLockedByOther) {
                    tvBadge.setText("🔒 LOCKED BY: " + lockingDev + " (Task in Progress — Inaccessible)");
                    tvBadge.setTextColor(0xFFF38BA8);
                    card.addView(tvBadge);
                } else {
                    tvBadge.setText("🟢 Available for Tasks");
                    tvBadge.setTextColor(0xFF94E2D5);
                    card.addView(tvBadge);
                }

                String modelName = sp.isNative ? "Real Phone (Native)" : (sp.profileJson != null ? sp.profileJson.optString("name", "Android Device") : "Android Device");
                TextView tvDetails = new TextView(ctx);
                String descText = (sp.description != null && !sp.description.isEmpty()) ? ("\n• Notes: " + sp.description) : "";
                String cityLabel = (sp.city != null && !sp.city.isEmpty()) ? sp.city : "US Any";
                String ipLabel = (sp.ip != null && !sp.ip.isEmpty()) ? sp.ip : "Auto-allocated";
                tvDetails.setText("• Device: " + modelName +
                        "\n• Target City: 📍 " + cityLabel +
                        "\n• Saved IP: 🌐 " + ipLabel +
                        "\n• Geo State: " + sp.geoState + " (" + sp.timezone + ")" +
                        "\n• Canvas Seed: " + String.format("%.6f", sp.noiseSeed) +
                        descText);
                tvDetails.setTextColor(0xFFCDD6F4);
                tvDetails.setTextSize(11);
                tvDetails.setLineSpacing(dp(2), 1.0f);
                tvDetails.setPadding(0, dp(4), 0, dp(8));
                card.addView(tvDetails);

                // Row 1: Restore buttons
                LinearLayout btnRow1 = new LinearLayout(ctx);
                btnRow1.setOrientation(LinearLayout.HORIZONTAL);

                Button btnRestore = new Button(ctx);
                btnRestore.setText(isLockedByOther ? "🔒 In-Use / Locked" : "▶️ Restore Active Tab");
                btnRestore.setTextColor(Color.WHITE);
                btnRestore.setTextSize(11);
                GradientDrawable resBg = new GradientDrawable();
                resBg.setColor(isLockedByOther ? 0xFF444444 : 0xFF2E7D32);
                resBg.setCornerRadius(dp(6));
                btnRestore.setBackground(resBg);
                LinearLayout.LayoutParams pR = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
                pR.setMargins(0, 0, dp(4), 0);
                if (!isLockedByOther) {
                    btnRestore.setOnClickListener(v -> {
                        dialog.dismiss();
                        restoreSavedProfileToActiveTab(sp);
                    });
                } else {
                    final String devName = lockingDev;
                    btnRestore.setOnClickListener(v -> {
                        Toast.makeText(ctx, "⚠️ This profile is locked by " + devName + " for an active task!", Toast.LENGTH_SHORT).show();
                    });
                }
                btnRow1.addView(btnRestore, pR);

                Button btnNewTabRestore = new Button(ctx);
                btnNewTabRestore.setText(isLockedByOther ? "🔒 Locked" : "➕ Open in Tab");
                btnNewTabRestore.setTextColor(Color.WHITE);
                btnNewTabRestore.setTextSize(11);
                GradientDrawable tabBg = new GradientDrawable();
                tabBg.setColor(isLockedByOther ? 0xFF333333 : 0xFF3B4252);
                tabBg.setCornerRadius(dp(6));
                btnNewTabRestore.setBackground(tabBg);
                LinearLayout.LayoutParams pT = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
                if (!isLockedByOther) {
                    btnNewTabRestore.setOnClickListener(v -> {
                        dialog.dismiss();
                        restoreSavedProfileInNewTab(sp);
                    });
                } else {
                    final String devName = lockingDev;
                    btnNewTabRestore.setOnClickListener(v -> {
                        Toast.makeText(ctx, "⚠️ This profile is locked by " + devName + " for an active task!", Toast.LENGTH_SHORT).show();
                    });
                }
                btnRow1.addView(btnNewTabRestore, pT);
                card.addView(btnRow1);

                // Row 2: Permanent Task Lock / Unlock + Delete
                LinearLayout btnRow2 = new LinearLayout(ctx);
                btnRow2.setOrientation(LinearLayout.HORIZONTAL);
                btnRow2.setPadding(0, dp(6), 0, 0);

                Button btnLockToggle = new Button(ctx);
                btnLockToggle.setTextSize(11);
                btnLockToggle.setTypeface(null, android.graphics.Typeface.BOLD);
                GradientDrawable lockBg = new GradientDrawable();
                lockBg.setCornerRadius(dp(6));

                if (isPermLockedByMe) {
                    btnLockToggle.setText("🔓 Unlock Profile (Release)");
                    btnLockToggle.setTextColor(0xFF11111B);
                    lockBg.setColor(0xFFFAB387); // Warm Peach/Amber
                    btnLockToggle.setBackground(lockBg);
                    btnLockToggle.setOnClickListener(v -> {
                        dialog.dismiss();
                        Toast.makeText(ctx, "🔓 Unlocking profile...", Toast.LENGTH_SHORT).show();
                        CloudProfileLockManager.setPermanentLock(ctx, profName, sp.id, false, () -> {
                            Toast.makeText(ctx, "🔓 Profile [" + sp.name + "] unlocked and released!", Toast.LENGTH_SHORT).show();
                            showSavedProfilesLibraryDialog();
                        });
                    });
                } else if (isLockedByOther) {
                    final String devName = lockingDev;
                    btnLockToggle.setText("🔒 Locked by " + devName);
                    btnLockToggle.setTextColor(0xFFBAC2DE);
                    lockBg.setColor(0xFF45475A); // Disabled gray/muted
                    btnLockToggle.setBackground(lockBg);
                    btnLockToggle.setOnClickListener(v -> {
                        Toast.makeText(ctx, "⚠️ Locked exclusively by " + devName + " for task isolation.", Toast.LENGTH_LONG).show();
                    });
                } else {
                    btnLockToggle.setText("🔒 Lock Profile (Exclusive Task)");
                    btnLockToggle.setTextColor(0xFF11111B);
                    lockBg.setColor(0xFF89B4FA); // Cyan/Blue
                    btnLockToggle.setBackground(lockBg);
                    btnLockToggle.setOnClickListener(v -> {
                        dialog.dismiss();
                        Toast.makeText(ctx, "🔒 Locking profile exclusively to this device...", Toast.LENGTH_SHORT).show();
                        CloudProfileLockManager.setPermanentLock(ctx, profName, sp.id, true, () -> {
                            Toast.makeText(ctx, "🔒 Profile [" + sp.name + "] locked exclusively to this device for tasks!", Toast.LENGTH_LONG).show();
                            showSavedProfilesLibraryDialog();
                        });
                    });
                }
                LinearLayout.LayoutParams pL = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
                pL.setMargins(0, 0, dp(4), 0);
                btnRow2.addView(btnLockToggle, pL);

                Button btnDel = new Button(ctx);
                btnDel.setText("🗑️");
                btnDel.setTextColor(Color.WHITE);
                btnDel.setTextSize(11);
                GradientDrawable delBg = new GradientDrawable();
                delBg.setColor(isLockedByOther ? 0xFF45475A : 0xFF892B3D);
                delBg.setCornerRadius(dp(6));
                btnDel.setBackground(delBg);
                LinearLayout.LayoutParams pD = new LinearLayout.LayoutParams(dp(40), dp(34));

                if (isLockedByOther) {
                    btnDel.setOnClickListener(v -> {
                        Toast.makeText(ctx, "⚠️ Cannot delete: Profile is locked by another phone for an ongoing task!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    btnDel.setOnClickListener(v -> {
                        ProfileStorageManager.deleteProfile(ctx, sp.id);
                        dialog.dismiss();
                        showSavedProfilesLibraryDialog();
                    });
                }
                btnRow2.addView(btnDel, pD);
                card.addView(btnRow2);

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, dp(10));
                layout.addView(card, cardParams);
            }
        }

        dialog.show();
    }

    private void restoreSavedProfileToActiveTab(ProfileStorageManager.SavedProfile sp) {
        String profName = (sp.profileJson != null) ? sp.profileJson.optString("name", "") : sp.name;
        if (CloudProfileLockManager.isProfileLockedByOther(profName) || CloudProfileLockManager.isSavedProfileLockedByOther(sp.id)) {
            String lockedBy = CloudProfileLockManager.getLockingDeviceName(profName);
            if (lockedBy.isEmpty() || "Another Device".equals(lockedBy)) {
                lockedBy = CloudProfileLockManager.getLockingDeviceName(sp.id);
            }
            Toast.makeText(this, "⚠️ Locked: This profile is active on another device (" + lockedBy + "). Cannot open.", Toast.LENGTH_LONG).show();
            return;
        }

        BrowserTab activeTab = getActiveTab();
        if (activeTab == null) return;

        // Release old profile lock for this tab if replacing
        if (activeTab.profile != null) {
            CloudProfileLockManager.releaseLock(this, activeTab.profile.optString("name", ""), "");
        }
        if (!sp.isNative) {
            CloudProfileLockManager.acquireLock(this, profName, sp.id);
        }

        activeTab.profile = sp.profileJson;
        activeTab.isNative = sp.isNative;
        activeTab.noiseSeed = sp.noiseSeed;
        activeTab.timezone = sp.timezone;
        activeTab.geoState = sp.geoState;
        activeTab.geoCity = sp.city;
        activeTab.savedIp = sp.ip;
        activeTab.proxySessionTag = sp.proxySessionTag;
        activeTab.title = sp.name;

        if (sp.isNative) {
            FingerprintManager.resetToNative();
        } else {
            FingerprintManager.setCurrentProfile(sp.profileJson, false);
            FingerprintManager.setCurrentNoiseSeed(sp.noiseSeed);
            FingerprintManager.setActiveTimezone(sp.timezone);
        }

        WebViewProxyManager.setSessionTag(sp.proxySessionTag);
        WebViewProxyManager.setActiveGeoState(sp.geoState);
        WebViewProxyManager.setActiveGeoCity(sp.city);

        // Request an IP from the same city and align timezone automatically
        WebViewProxyManager.resolveCurrentProxyGeo((ip, city, region, country, timezone) -> {
            FingerprintManager.setActiveTimezone(timezone);
            if (activeTab.webView != null) {
                FingerprintManager.applyCurrentFingerprint(activeTab.webView);
                updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(activeTab.webView.getUrl()));
            }
        });

        if (sp.cookies != null && !sp.cookies.isEmpty()) {
            try {
                String targetUrl = (sp.lastUrl != null && !sp.lastUrl.isEmpty()) ? sp.lastUrl : HOME_URL;
                for (String c : sp.cookies.split(";")) {
                    CookieManager.getInstance().setCookie(targetUrl, c.trim());
                }
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {}
        }

        if (activeTab.webView != null) {
            if (sp.isNative && defaultNativeUserAgent != null) {
                activeTab.webView.getSettings().setUserAgentString(defaultNativeUserAgent);
            } else if (sp.profileJson != null) {
                activeTab.webView.getSettings().setUserAgentString(sp.profileJson.optString("userAgent"));
            }
            activeTab.webView.loadUrl((sp.lastUrl != null && !sp.lastUrl.isEmpty()) ? sp.lastUrl : HOME_URL);
        }

        renderTabBar();
        Toast.makeText(this, "✅ Restored profile: " + sp.name + (sp.city != null && !sp.city.isEmpty() ? " [City: " + sp.city + "]" : ""), Toast.LENGTH_SHORT).show();
    }

    private void restoreSavedProfileInNewTab(ProfileStorageManager.SavedProfile sp) {
        String profName = (sp.profileJson != null) ? sp.profileJson.optString("name", "") : sp.name;
        if (CloudProfileLockManager.isProfileLockedByOther(profName) || CloudProfileLockManager.isSavedProfileLockedByOther(sp.id)) {
            String lockedBy = CloudProfileLockManager.getLockingDeviceName(profName);
            if (lockedBy.isEmpty() || "Another Device".equals(lockedBy)) {
                lockedBy = CloudProfileLockManager.getLockingDeviceName(sp.id);
            }
            Toast.makeText(this, "⚠️ Locked: This profile is active on another device (" + lockedBy + "). Cannot open.", Toast.LENGTH_LONG).show();
            return;
        }

        BrowserTab newTab = createNewTab(sp.profileJson, sp.isNative);
        newTab.noiseSeed = sp.noiseSeed;
        newTab.timezone = sp.timezone;
        newTab.geoState = sp.geoState;
        newTab.geoCity = sp.city;
        newTab.savedIp = sp.ip;
        newTab.proxySessionTag = sp.proxySessionTag;
        newTab.title = sp.name;

        WebViewProxyManager.setSessionTag(sp.proxySessionTag);
        WebViewProxyManager.setActiveGeoState(sp.geoState);
        WebViewProxyManager.setActiveGeoCity(sp.city);

        WebViewProxyManager.resolveCurrentProxyGeo((ip, city, region, country, timezone) -> {
            FingerprintManager.setActiveTimezone(timezone);
            if (newTab.webView != null) {
                FingerprintManager.applyCurrentFingerprint(newTab.webView);
                updateProxyBadgeUI(WebViewProxyManager.isUrlWhitelisted(newTab.webView.getUrl()));
            }
        });

        if (sp.cookies != null && !sp.cookies.isEmpty()) {
            try {
                String targetUrl = (sp.lastUrl != null && !sp.lastUrl.isEmpty()) ? sp.lastUrl : HOME_URL;
                for (String c : sp.cookies.split(";")) {
                    CookieManager.getInstance().setCookie(targetUrl, c.trim());
                }
                CookieManager.getInstance().flush();
            } catch (Exception ignored) {}
        }

        newTab.webView.loadUrl((sp.lastUrl != null && !sp.lastUrl.isEmpty()) ? sp.lastUrl : HOME_URL);
        renderTabBar();
        Toast.makeText(this, "✅ Opened in new tab: " + sp.name + (sp.city != null && !sp.city.isEmpty() ? " [City: " + sp.city + "]" : ""), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (browser != null && browser.canGoBack()) {
            browser.goBack();
        } else if (tabList.size() > 1) {
            closeTab(activeTabIndex);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            SessionOrchestrator.getInstance(this).stopContinuousSimulation();
            CloudProfileLockManager.releaseAllMyLocks(this);
            uiHandler.removeCallbacksAndMessages(null);
            if (AiSessionManager.getInstance().isRunning()) {
                AiSessionManager.getInstance().stopSession();
            }
            if (c2Client != null) {
                c2Client.disconnect();
            }
            for (BrowserTab t : tabList) {
                if (t.webView != null) {
                    t.webView.destroy();
                }
            }
            tabList.clear();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void showTrafficEngineDialog() {
        final SessionOrchestrator orchestrator = SessionOrchestrator.getInstance(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));
        layout.setBackgroundColor(0xFF1E1E2E);

        // Header Card
        LinearLayout headerCard = new LinearLayout(this);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        headerCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(0xFF2A2B3D);
        headerBg.setCornerRadius(dp(10));
        headerCard.setBackground(headerBg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🚀 REALISTIC TRAFFIC SIMULATION ENGINE");
        tvTitle.setTextColor(0xFFF5C2E7);
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerCard.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Generates authentic human browsing sessions for portfolio analytics and SEO signals. Operates across 5 behavioral layers with organic thumb gestures and anti-detect identity rotation.");
        tvSubtitle.setTextColor(0xFFBAC2DE);
        tvSubtitle.setTextSize(11);
        tvSubtitle.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(4), 0, 0);
        headerCard.addView(tvSubtitle, subParams);

        layout.addView(headerCard);

        // Portfolio Target URL Config Card
        LinearLayout urlCard = new LinearLayout(this);
        urlCard.setOrientation(LinearLayout.VERTICAL);
        urlCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable urlCardBg = new GradientDrawable();
        urlCardBg.setColor(0xFF181825);
        urlCardBg.setCornerRadius(dp(10));
        urlCard.setBackground(urlCardBg);
        LinearLayout.LayoutParams urlCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        urlCardParams.setMargins(0, dp(10), 0, 0);

        TextView tvUrlLabel = new TextView(this);
        tvUrlLabel.setText("TARGET PORTFOLIO URL:");
        tvUrlLabel.setTextColor(0xFF89B4FA);
        tvUrlLabel.setTextSize(10);
        tvUrlLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        urlCard.addView(tvUrlLabel);

        final EditText etUrl = new EditText(this);
        etUrl.setText(orchestrator.getPortfolioUrl());
        etUrl.setTextColor(Color.WHITE);
        etUrl.setTextSize(12);
        etUrl.setSingleLine(true);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF313244);
        etBg.setCornerRadius(dp(6));
        etUrl.setBackground(etBg);
        etUrl.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(0, dp(6), 0, dp(6));
        urlCard.addView(etUrl, etParams);

        Button btnSaveUrl = new Button(this);
        btnSaveUrl.setText("Save Target URL");
        btnSaveUrl.setTextSize(11);
        btnSaveUrl.setTextColor(0xFF1E1E2E);
        GradientDrawable saveUrlBg = new GradientDrawable();
        saveUrlBg.setColor(0xFFA6E3A1);
        saveUrlBg.setCornerRadius(dp(6));
        btnSaveUrl.setBackground(saveUrlBg);
        btnSaveUrl.setOnClickListener(v -> {
            String newUrl = etUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                orchestrator.setPortfolioUrl(newUrl);
                Toast.makeText(this, "✅ Target URL updated: " + newUrl, Toast.LENGTH_SHORT).show();
            }
        });
        urlCard.addView(btnSaveUrl, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        layout.addView(urlCard, urlCardParams);

        // Daily Quotas & Metrics Dashboard Card
        LinearLayout quotaCard = new LinearLayout(this);
        quotaCard.setOrientation(LinearLayout.VERTICAL);
        quotaCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable quotaBg = new GradientDrawable();
        quotaBg.setColor(0xFF2A2B3D);
        quotaBg.setCornerRadius(dp(10));
        quotaCard.setBackground(quotaBg);
        LinearLayout.LayoutParams quotaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        quotaParams.setMargins(0, dp(10), 0, 0);

        TextView tvQuotaTitle = new TextView(this);
        tvQuotaTitle.setText("DAILY ORCHESTRATION TARGETS");
        tvQuotaTitle.setTextColor(0xFFBAC2DE);
        tvQuotaTitle.setTextSize(10);
        tvQuotaTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        quotaCard.addView(tvQuotaTitle);

        final TextView tvQuotas = new TextView(this);
        tvQuotas.setTextColor(Color.WHITE);
        tvQuotas.setTextSize(12);
        tvQuotas.setLineSpacing(dp(3), 1.0f);
        updateQuotaDashboardText(tvQuotas, orchestrator);
        quotaCard.addView(tvQuotas);

        layout.addView(quotaCard, quotaParams);

        // Live Telemetry Box
        LinearLayout telemetryCard = new LinearLayout(this);
        telemetryCard.setOrientation(LinearLayout.VERTICAL);
        telemetryCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable teleBg = new GradientDrawable();
        teleBg.setColor(0xFF11111B);
        teleBg.setCornerRadius(dp(8));
        teleBg.setStroke(dp(1), 0xFF45475A);
        telemetryCard.setBackground(teleBg);
        LinearLayout.LayoutParams teleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        teleParams.setMargins(0, dp(10), 0, 0);

        final TextView tvTelePhase = new TextView(this);
        tvTelePhase.setText("STATUS: " + (orchestrator.isRunning() ? "🟢 Running" : "⚪ Idle"));
        tvTelePhase.setTextColor(0xFFA6E3A1);
        tvTelePhase.setTextSize(11);
        tvTelePhase.setTypeface(null, android.graphics.Typeface.BOLD);
        telemetryCard.addView(tvTelePhase);

        final TextView tvTeleDetail = new TextView(this);
        tvTeleDetail.setText("Engine ready. Select 'Run Live Test Session' to preview 5-layer browsing or start 800/day continuous schedule.");
        tvTeleDetail.setTextColor(0xFFBAC2DE);
        tvTeleDetail.setTextSize(11);
        tvTeleDetail.setLineSpacing(dp(2), 1.0f);
        telemetryCard.addView(tvTeleDetail);

        layout.addView(telemetryCard, teleParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🚀 Portfolio Traffic Engine")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        // Button: Run Single Live Test Session
        Button btnRunDemo = new Button(this);
        btnRunDemo.setText("▶ Run Live Test Session (Watch 5 Layers)");
        btnRunDemo.setTextColor(0xFF1E1E2E);
        btnRunDemo.setTextSize(12);
        btnRunDemo.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable demoBg = new GradientDrawable();
        demoBg.setColor(0xFFA6E3A1);
        demoBg.setCornerRadius(dp(8));
        btnRunDemo.setBackground(demoBg);
        LinearLayout.LayoutParams pDemo = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pDemo.setMargins(0, dp(14), 0, 0);
        btnRunDemo.setOnClickListener(v -> {
            String newUrl = etUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                orchestrator.setPortfolioUrl(newUrl);
            }
            tvTelePhase.setText("STATUS: 🟢 Live Session Initialized");
            tvTeleDetail.setText("Starting 5-layer simulation on active tab...");
            Toast.makeText(this, "🚀 Starting Live Traffic Simulation on active tab...", Toast.LENGTH_SHORT).show();

            orchestrator.runSingleTestSession(this, new TrafficSimulationEngine.TelemetryCallback() {
                @Override
                public void onStatusUpdate(String phase, String detail, int elapsedSeconds, int pagesVisited, int clicksGenerated) {
                    runOnUiThread(() -> {
                        tvTelePhase.setText("STATUS: 🟢 " + phase + " (" + elapsedSeconds + "s)");
                        tvTeleDetail.setText(detail);
                        updateQuotaDashboardText(tvQuotas, orchestrator);
                    });
                }

                @Override
                public void onSessionCompleted(boolean success, String summary) {
                    runOnUiThread(() -> {
                        tvTelePhase.setText("STATUS: ✅ Completed");
                        tvTeleDetail.setText(summary);
                        updateQuotaDashboardText(tvQuotas, orchestrator);
                        Toast.makeText(BrowserActivity.this, "✅ Live Session Finished!", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        layout.addView(btnRunDemo, pDemo);

        // Button: Start / Stop Continuous 1,500/Day Simulation
        final Button btnToggleContinuous = new Button(this);
        boolean isRunning = orchestrator.isRunning();
        btnToggleContinuous.setText(isRunning ? "⏹ Stop Simulation" : "⚡ Start 1,500/Day Schedule");
        btnToggleContinuous.setTextColor(Color.WHITE);
        btnToggleContinuous.setTextSize(12);
        btnToggleContinuous.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(isRunning ? 0xFFF38BA8 : 0xFF7287FD);
        toggleBg.setCornerRadius(dp(8));
        btnToggleContinuous.setBackground(toggleBg);
        LinearLayout.LayoutParams pToggle = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pToggle.setMargins(0, dp(8), 0, 0);

        btnToggleContinuous.setOnClickListener(v -> {
            String newUrl = etUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                orchestrator.setPortfolioUrl(newUrl);
            }

            if (orchestrator.isRunning()) {
                orchestrator.stopContinuousSimulation();
                btnToggleContinuous.setText("⚡ Start 1,500/Day Schedule");
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFF7287FD);
                bg.setCornerRadius(dp(8));
                btnToggleContinuous.setBackground(bg);
                tvTelePhase.setText("STATUS: ⚪ Stopped");
                tvTeleDetail.setText("Automated traffic schedule halted.");
                Toast.makeText(this, "⏹ Traffic Simulation Engine Stopped", Toast.LENGTH_SHORT).show();
            } else {
                orchestrator.startContinuousSimulation(this);
                btnToggleContinuous.setText("⏹ Stop Simulation");
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFFF38BA8);
                bg.setCornerRadius(dp(8));
                btnToggleContinuous.setBackground(bg);
                tvTelePhase.setText("STATUS: 🟢 Continuous Engine Active");
                tvTeleDetail.setText("16 waking hours scheduler engaged (peak weighted).");
                Toast.makeText(this, "⚡ 1,500/Day Traffic Simulation Started!", Toast.LENGTH_SHORT).show();
            }
            updateQuotaDashboardText(tvQuotas, orchestrator);
        });
        layout.addView(btnToggleContinuous, pToggle);

        // Register listener for live updates while dialog is open
        orchestrator.setListener(new SessionOrchestrator.OrchestratorListener() {
            @Override
            public void onProgressUpdate(int dailySessions, int dailyClicks, int queuedReturns, String activeStatus) {
                runOnUiThread(() -> {
                    updateQuotaDashboardText(tvQuotas, orchestrator);
                });
            }

            @Override
            public void onSessionDetailUpdate(String phase, String detail, int elapsedSeconds, int pages, int clicks) {
                runOnUiThread(() -> {
                    tvTelePhase.setText("STATUS: 🟢 " + phase + " (" + elapsedSeconds + "s)");
                    tvTeleDetail.setText(detail);
                });
            }

            @Override
            public void onOrchestratorStateChanged(boolean running) {
                runOnUiThread(() -> {
                    btnToggleContinuous.setText(running ? "⏹ Stop Simulation" : "⚡ Start 1,500/Day Schedule");
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(running ? 0xFFF38BA8 : 0xFF7287FD);
                    bg.setCornerRadius(dp(8));
                    btnToggleContinuous.setBackground(bg);
                });
            }
        });

        dialog.setOnDismissListener(d -> {
            orchestrator.setListener(null);
        });

        dialog.show();
    }

    private void updateQuotaDashboardText(TextView tvQuotas, SessionOrchestrator orchestrator) {
        if (tvQuotas == null || orchestrator == null) return;
        tvQuotas.setText(
                "• Unique Daily Identities: " + orchestrator.getDailySessionsCompleted() + " / " + SessionOrchestrator.DAILY_TARGET_IDENTITIES + "\n" +
                "• Daily Clicks Generated: " + orchestrator.getDailyClicksCompleted() + " / " + SessionOrchestrator.DAILY_TARGET_CLICKS + "\n" +
                "• Returning Visitors Queued: " + orchestrator.getQueuedReturnsCount() + " identities (15-25% Retain)\n" +
                "• Operating Schedule: 16 Waking Hours (08:00 - 00:00)\n" +
                "• Peak Hours: 10-11 AM, 2-3 PM, 7-9 PM\n" +
                "• Sleep Window: 00:00 - 07:00 (Quiet Simulation)\n" +
                "• Anti-Detect: WebGL / Canvas / Timezone / Proxy Auto-Rotated"
        );
    }
}