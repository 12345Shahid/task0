package com.rcl.androidstreamcontrol.simulation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import com.rcl.androidstreamcontrol.BrowserActivity;
import com.rcl.androidstreamcontrol.utils.BrowserTab;
import com.rcl.androidstreamcontrol.utils.CloudProfileLockManager;
import com.rcl.androidstreamcontrol.utils.DeviceProfilePool;
import com.rcl.androidstreamcontrol.utils.FingerprintManager;
import com.rcl.androidstreamcontrol.utils.ProfileStorageManager;
import com.rcl.androidstreamcontrol.utils.WebViewProxyManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Session Orchestrator — Manages 800 Daily Identities & 1000 Click Distribution.
 *
 * Implements:
 * - 800 unique daily visitor identities
 * - 1000 total interactions/clicks distributed naturally across identities
 * - 16 waking hours distribution (8 AM - midnight), peak hours (10-11 AM, 2-3 PM, 7-9 PM)
 * - Sleep hours simulation (midnight - 7 AM: quiet period)
 * - Returning visitor simulation (15-25% return 2-6 hours later for 30-45s session with cookies preserved)
 * - Anti-detect identity rotation (DeviceProfilePool, Canvas seed, proxy session, timezone matching)
 */
public class SessionOrchestrator {

    private static final String TAG = "SessionOrchestrator";
    private static final String PREFS_NAME = "traffic_orchestrator_prefs";
    private static final String KEY_PORTFOLIO_URL = "target_portfolio_url";
    private static final String KEY_DAILY_DATE = "daily_date_str";
    private static final String KEY_SESSIONS_COUNT = "daily_sessions_count";
    private static final String KEY_CLICKS_COUNT = "daily_clicks_count";
    private static final String KEY_RETURNING_QUEUE = "returning_visitors_queue";

    public static final int TARGET_CAMPAIGN_IMPRESSIONS = 110;
    public static final int TARGET_CAMPAIGN_CLICKS = 6; // Strict 5.4% CTR (6 clicks / 110 impressions)
    public static final int DAILY_TARGET_IDENTITIES = TARGET_CAMPAIGN_IMPRESSIONS;
    public static final int DAILY_TARGET_CLICKS = TARGET_CAMPAIGN_CLICKS;

    public interface OrchestratorListener {
        void onProgressUpdate(int dailySessions, int dailyClicks, int queuedReturns, String activeStatus);
        void onSessionDetailUpdate(String phase, String detail, int elapsedSeconds, int pages, int clicks);
        void onOrchestratorStateChanged(boolean running);
    }

    private static volatile SessionOrchestrator instance;

    public static SessionOrchestrator getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionOrchestrator.class) {
                if (instance == null) {
                    instance = new SessionOrchestrator(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private volatile boolean isContinuousRunning = false;
    private TrafficSimulationEngine activeEngine = null;
    private OrchestratorListener listener = null;

    // Daily distribution tracker
    private int dailySessionsCompleted = 0;
    private int dailyClicksCompleted = 0;
    private String todayDateStr = "";

    // Queue of identities scheduled to return
    public static class ScheduledReturn {
        public ProfileStorageManager.SavedProfile savedProfile;
        public long scheduledTimeMillis;
        public int allocatedClicks;

        public ScheduledReturn(ProfileStorageManager.SavedProfile sp, long scheduledTime, int clicks) {
            this.savedProfile = sp;
            this.scheduledTimeMillis = scheduledTime;
            this.allocatedClicks = clicks;
        }
    }

    private final Queue<ScheduledReturn> returningQueue = new ArrayDeque<>();
    private final Runnable nextScheduleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isContinuousRunning) return;
            executeNextScheduledSession();
        }
    };

    private SessionOrchestrator(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadState();
    }

    public synchronized void setListener(OrchestratorListener listener) {
        this.listener = listener;
        notifyListener();
    }

    public boolean isRunning() {
        return isContinuousRunning || (activeEngine != null && activeEngine.isRunning());
    }

    public String getPortfolioUrl() {
        return prefs.getString(KEY_PORTFOLIO_URL, "https://yieldmatrix-crypto-calculator.vercel.app");
    }

    public void setPortfolioUrl(String url) {
        if (url != null) {
            prefs.edit().putString(KEY_PORTFOLIO_URL, url.trim()).apply();
        }
    }

    public int getDailySessionsCompleted() {
        return dailySessionsCompleted;
    }

    public int getDailyClicksCompleted() {
        return dailyClicksCompleted;
    }

    public int getQueuedReturnsCount() {
        return returningQueue.size();
    }

    private void loadState() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        todayDateStr = prefs.getString(KEY_DAILY_DATE, "");
        if (!today.equals(todayDateStr)) {
            // New day reset
            todayDateStr = today;
            dailySessionsCompleted = 0;
            dailyClicksCompleted = 0;
            returningQueue.clear();
            saveState();
        } else {
            dailySessionsCompleted = prefs.getInt(KEY_SESSIONS_COUNT, 0);
            dailyClicksCompleted = prefs.getInt(KEY_CLICKS_COUNT, 0);
        }
    }

    private void saveState() {
        prefs.edit()
                .putString(KEY_DAILY_DATE, todayDateStr)
                .putInt(KEY_SESSIONS_COUNT, dailySessionsCompleted)
                .putInt(KEY_CLICKS_COUNT, dailyClicksCompleted)
                .apply();
    }

    private void notifyListener() {
        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onProgressUpdate(
                            dailySessionsCompleted,
                            dailyClicksCompleted,
                            returningQueue.size(),
                            isContinuousRunning ? "Continuous Engine Active" : (activeEngine != null && activeEngine.isRunning() ? "Single Session Active" : "Idle")
                    );
                    listener.onOrchestratorStateChanged(isRunning());
                }
            });
        }
    }

    /**
     * Executes a single, realistic session live in the active browser tab.
     * Useful for verification, testing, and live visual monitoring.
     */
    public void runSingleTestSession(BrowserActivity activity, TrafficSimulationEngine.TelemetryCallback testCallback) {
        if (activeEngine != null && activeEngine.isRunning()) {
            activeEngine.stop();
        }

        BrowserTab activeTab = activity.getActiveTab();
        if (activeTab == null || activeTab.webView == null) {
            if (testCallback != null) {
                testCallback.onSessionCompleted(false, "No active browser tab found.");
            }
            return;
        }

        // Setup distinct hardware profile and proxy session
        applyFreshIdentity(activeTab, null);

        // Generate profile: single demo session has 1 click to demonstrate Layer 5 external tab engagement
        BehaviorProfile profile = BehaviorProfile.createRandom(false, 1);

        String url = getPortfolioUrl();
        activeEngine = new TrafficSimulationEngine(activity, activeTab.webView, url, profile, new TrafficSimulationEngine.TelemetryCallback() {
            @Override
            public void onStatusUpdate(String phase, String detail, int elapsedSeconds, int pagesVisited, int clicksGenerated) {
                if (listener != null) {
                    listener.onSessionDetailUpdate(phase, detail, elapsedSeconds, pagesVisited, clicksGenerated);
                }
                if (testCallback != null) {
                    testCallback.onStatusUpdate(phase, detail, elapsedSeconds, pagesVisited, clicksGenerated);
                }
            }

            @Override
            public void onSessionCompleted(boolean success, String summary) {
                dailySessionsCompleted++;
                dailyClicksCompleted += profile.allocatedClicks;
                saveState();
                notifyListener();

                // 20% chance to queue as returning visitor
                if (random.nextInt(100) < 20) {
                    queueReturningVisitor(activeTab, profile.allocatedClicks);
                }

                if (testCallback != null) {
                    testCallback.onSessionCompleted(success, summary);
                }
            }
        });

        notifyListener();
        activeEngine.start();
    }

    /**
     * Starts continuous automated traffic orchestration according to the 16-hour schedule.
     */
    public synchronized void startContinuousSimulation(BrowserActivity activity) {
        if (isContinuousRunning) return;
        isContinuousRunning = true;
        dailySessionsCompleted = 0;
        dailyClicksCompleted = 0;
        saveState();
        WebViewProxyManager.setFullProxyMode(true);
        WebViewProxyManager.resumeProxy();
        notifyListener();
        executeNextScheduledSession();
    }

    /**
     * Stops continuous traffic orchestration and cancels pending sessions.
     */
    public synchronized void stopContinuousSimulation() {
        isContinuousRunning = false;
        mainHandler.removeCallbacks(nextScheduleRunnable);
        if (activeEngine != null && activeEngine.isRunning()) {
            activeEngine.stop();
            activeEngine = null;
        }
        WebViewProxyManager.stopProxy();
        notifyListener();
    }

    private void executeNextScheduledSession() {
        if (!isContinuousRunning) return;

        // Synchronize with US West Coast / California Time (America/Los_Angeles: ~9:45 PM evening)
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // Check if 110-impression campaign target reached
        if (dailySessionsCompleted >= TARGET_CAMPAIGN_IMPRESSIONS) {
            Log.i(TAG, "Test campaign target of " + TARGET_CAMPAIGN_IMPRESSIONS + " impressions reached! Stopping.");
            isContinuousRunning = false;
            if (listener != null) {
                listener.onProgressUpdate(dailySessionsCompleted, dailyClicksCompleted, returningQueue.size(), "Campaign Complete (" + dailySessionsCompleted + " impressions)");
                listener.onOrchestratorStateChanged(false);
            }
            return;
        }

        // Determine if this session is a returning visitor
        long now = System.currentTimeMillis();
        ScheduledReturn returning = null;
        if (!returningQueue.isEmpty() && returningQueue.peek().scheduledTimeMillis <= now) {
            returning = returningQueue.poll();
        }

        final boolean isReturning = (returning != null);
        final ScheduledReturn currentReturn = returning;

        // Allocate clicks according to remaining target and distribution
        int clicksForSession = allocateClicksForNextSession();

        // Run session on BrowserActivity
        BrowserActivity activity = BrowserActivityState.getActiveActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            // Retry in 15 seconds if activity temporarily not bound
            mainHandler.postDelayed(nextScheduleRunnable, 15000L);
            return;
        }

        BrowserTab activeTab = activity.getActiveTab();
        if (activeTab == null || activeTab.webView == null) {
            mainHandler.postDelayed(nextScheduleRunnable, 10000L);
            return;
        }

        // Configure identity
        if (isReturning && currentReturn.savedProfile != null) {
            restoreReturningIdentity(activeTab, currentReturn.savedProfile);
        } else {
            applyFreshIdentity(activeTab, null);
        }

        BehaviorProfile profile = BehaviorProfile.createRandom(isReturning, clicksForSession);
        String url = getPortfolioUrl();

        activeEngine = new TrafficSimulationEngine(activity, activeTab.webView, url, profile, new TrafficSimulationEngine.TelemetryCallback() {
            @Override
            public void onStatusUpdate(String phase, String detail, int elapsedSeconds, int pagesVisited, int clicksGenerated) {
                if (listener != null) {
                    listener.onSessionDetailUpdate(phase, detail, elapsedSeconds, pagesVisited, clicksGenerated);
                }
            }

            @Override
            public void onSessionCompleted(boolean success, String summary) {
                dailySessionsCompleted++;
                dailyClicksCompleted += clicksForSession;
                saveState();
                notifyListener();

                // If not already a return, consider queuing for return 2-6 hours later
                if (!isReturning && random.nextInt(100) < 20) {
                    queueReturningVisitor(activeTab, clicksForSession);
                }

                // Schedule next session based on hourly cadence
                scheduleNextSessionDelay(hour);
            }
        });

        activeEngine.start();
    }

    private int allocateClicksForNextSession() {
        int remainingClicks = Math.max(0, TARGET_CAMPAIGN_CLICKS - dailyClicksCompleted);
        int remainingSessions = Math.max(1, TARGET_CAMPAIGN_IMPRESSIONS - dailySessionsCompleted);

        if (remainingClicks <= 0) return 0;

        // Exact 5% CTR distribution across 110 impressions:
        double clickProbability = (double) remainingClicks / (double) remainingSessions;
        return (random.nextDouble() < clickProbability) ? 1 : 0;
    }

    private void scheduleNextSessionDelay(int currentHour) {
        if (!isContinuousRunning) return;

        // Organic human cadence for West Coast / California evening hours (9 PM - 11 PM):
        // 18 to 32 seconds natural dwell between sessions
        int delaySeconds = 18 + random.nextInt(15);
        mainHandler.postDelayed(nextScheduleRunnable, delaySeconds * 1000L);
    }

    private void applyFreshIdentity(BrowserTab tab, JSONObject specificProfile) {
        try {
            // Clean cookies and web storage for fresh identity
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            WebStorage.getInstance().deleteAllData();

            // Clear WebView cache, history, and service workers to force fresh download
            if (tab != null && tab.webView != null) {
                tab.webView.post(() -> {
                    try {
                        tab.webView.clearCache(true);
                        tab.webView.clearFormData();
                        tab.webView.clearHistory();
                        tab.webView.evaluateJavascript(
                                "(function(){" +
                                "if('serviceWorker' in navigator){navigator.serviceWorker.getRegistrations().then(function(rs){for(var i=0;i<rs.length;i++){rs[i].unregister();}}).catch(function(){});}" +
                                "if('caches' in window){caches.keys().then(function(ks){for(var i=0;i<ks.length;i++){caches.delete(ks[i]);}}).catch(function(){});}" +
                                "})()",
                                null
                        );
                    } catch (Exception ignored) {}
                });
            }

            // Distinct hardware profile (strictly Android flagship profiles for Android engine consistency)
            Set<String> used = new HashSet<>(CloudProfileLockManager.getRemoteLockedProfileNames());
            JSONObject prof = specificProfile != null ? specificProfile : DeviceProfilePool.getRandomAndroidProfileExcluding(used);
            if (prof == null) prof = DeviceProfilePool.getRandomAndroidProfile();

            double seed = random.nextDouble();
            String sessionTag = "tab-" + UUID.randomUUID().toString().substring(0, 8);
            String tz = "America/Los_Angeles"; // Enforce California West Coast evening time

            tab.profile = prof;
            tab.isNative = false;
            tab.noiseSeed = seed;
            tab.timezone = tz;
            tab.proxySessionTag = sessionTag;

            FingerprintManager.setCurrentProfile(prof, false);
            FingerprintManager.setCurrentNoiseSeed(seed);
            FingerprintManager.setActiveTimezone(tz);

            WebViewProxyManager.setFullProxyMode(true);
            WebViewProxyManager.setSessionTag(sessionTag);
            WebViewProxyManager.resumeProxy();
            if (tab.webView != null) {
                FingerprintManager.applyCurrentFingerprint(tab.webView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying fresh identity", e);
        }
    }

    private void queueReturningVisitor(BrowserTab tab, int previousClicks) {
        try {
            ProfileStorageManager.SavedProfile sp = new ProfileStorageManager.SavedProfile();
            sp.profileJson = tab.profile;
            sp.isNative = tab.isNative;
            sp.noiseSeed = tab.noiseSeed;
            sp.timezone = tab.timezone;
            sp.proxySessionTag = tab.proxySessionTag;
            sp.cookies = CookieManager.getInstance().getCookie(getPortfolioUrl());
            sp.lastUrl = getPortfolioUrl();

            // Return in 2 to 6 hours
            long returnDelayMs = (2 + random.nextInt(5)) * 3600L * 1000L + random.nextInt(60) * 60000L;
            long returnTime = System.currentTimeMillis() + returnDelayMs;

            returningQueue.offer(new ScheduledReturn(sp, returnTime, Math.max(0, previousClicks - 1)));
            Log.i(TAG, "Queued returning visitor identity. Return in " + (returnDelayMs / 3600000.0) + " hours.");
        } catch (Exception e) {
            Log.w(TAG, "Error saving returning visitor", e);
        }
    }

    private void restoreReturningIdentity(BrowserTab tab, ProfileStorageManager.SavedProfile sp) {
        try {
            tab.profile = sp.profileJson;
            tab.isNative = sp.isNative;
            tab.noiseSeed = sp.noiseSeed;
            tab.timezone = sp.timezone;
            tab.proxySessionTag = sp.proxySessionTag;

            FingerprintManager.setCurrentProfile(sp.profileJson, false);
            FingerprintManager.setCurrentNoiseSeed(sp.noiseSeed);
            FingerprintManager.setActiveTimezone(sp.timezone);
            WebViewProxyManager.setFullProxyMode(false);
            WebViewProxyManager.setSessionTag(sp.proxySessionTag);
            WebViewProxyManager.resumeProxy();

            if (sp.cookies != null && !sp.cookies.isEmpty()) {
                CookieManager.getInstance().setCookie(getPortfolioUrl(), sp.cookies);
                CookieManager.getInstance().flush();
            }

            if (tab.webView != null) {
                FingerprintManager.applyCurrentFingerprint(tab.webView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring returning identity", e);
        }
    }

    /**
     * Weakly holds the active BrowserActivity reference for continuous background runs.
     */
    public static class BrowserActivityState {
        private static volatile java.lang.ref.WeakReference<BrowserActivity> currentActivityRef = null;

        public static void register(BrowserActivity act) {
            currentActivityRef = new java.lang.ref.WeakReference<>(act);
        }

        public static BrowserActivity getActiveActivity() {
            return currentActivityRef != null ? currentActivityRef.get() : null;
        }
    }
}
