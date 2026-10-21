package com.rcl.androidstreamcontrol.utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Random;

/**
 * AI-Driven Human Interaction Session Manager.
 * Orchestrates the Macro-Brain (OpenRouter / DeepSeek) and Micro-Reflex Engine (HumanTouchKinematicsEngine).
 * Features:
 * - 30-minute countdown timer with sleek floating HUD overlay
 * - Autonomous organic human actions (Bezier curves, Gaussian dwell, Poisson pauses)
 * - Periodic cognitive check-ins with OpenRouter
 * - Instant safe Stop mechanism
 */
public class AiSessionManager {

    private static final String TAG = "AiSessionManager";
    private static volatile AiSessionManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private boolean isRunning = false;
    private Activity currentActivity;
    private WebView currentWebView;

    private String sessionName;
    private String sessionDesc;
    private int durationMinutes = 30;
    private int elapsedSeconds = 0;
    private String apiKey;
    private String modelName;

    private HumanTouchKinematicsEngine kinematicsEngine;
    private View floatingHudView;
    private TextView hudTimerText;
    private TextView hudStatusText;

    private long lastAiCheckinTime = 0L;
    private static final long AI_CHECKIN_INTERVAL_MS = 150_000L; // 2.5 minutes between AI calls

    private Runnable timerRunnable;
    private Runnable nextActionRunnable;

    private OpenRouterClient.AiActionPlan currentPlan;

    private AiSessionManager() {}

    public static synchronized AiSessionManager getInstance() {
        if (instance == null) {
            instance = new AiSessionManager();
        }
        return instance;
    }

    public boolean isRunning() {
        return isRunning;
    }

    private int dp(int value) {
        if (currentActivity == null) return value;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, currentActivity.getResources().getDisplayMetrics());
    }

    /**
     * Start an AI-Driven Human Interaction Session.
     */
    public void startSession(
            Activity activity,
            WebView webView,
            String name,
            String desc,
            int durationMins,
            String key,
            String model
    ) {
        if (isRunning) {
            stopSession();
        }

        this.currentActivity = activity;
        this.currentWebView = webView;
        this.sessionName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Organic Human Browsing";
        this.sessionDesc = (desc != null && !desc.trim().isEmpty()) ? desc.trim() : "Casually browse, view content, pause naturally";
        this.durationMinutes = durationMins > 0 ? durationMins : 30;
        this.elapsedSeconds = 0;
        this.apiKey = key;
        this.modelName = model;
        this.isRunning = true;
        this.lastAiCheckinTime = 0L;

        this.kinematicsEngine = new HumanTouchKinematicsEngine(webView);
        this.kinematicsEngine.start();

        // 1. Render Floating HUD Overlay
        showFloatingHud();

        // 2. Start Countdown Timer Ticker
        startTimerTicker();

        // 3. Kick off the first AI Macro consultation & reflex loop
        triggerAiConsultationAndLoop();

        Toast.makeText(activity, "🤖 AI Human-Interaction Session Started (" + durationMinutes + " mins)", Toast.LENGTH_SHORT).show();
    }

    /**
     * Stop the AI session immediately and remove HUD.
     */
    public void stopSession() {
        if (!isRunning && floatingHudView == null) return;

        isRunning = false;

        if (timerRunnable != null) {
            mainHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }

        if (nextActionRunnable != null) {
            mainHandler.removeCallbacks(nextActionRunnable);
            nextActionRunnable = null;
        }

        if (kinematicsEngine != null) {
            kinematicsEngine.stop();
            kinematicsEngine = null;
        }

        removeFloatingHud();

        if (currentActivity != null && !currentActivity.isFinishing()) {
            Toast.makeText(currentActivity, "⏹ AI Interaction Session Stopped", Toast.LENGTH_SHORT).show();
        }

        currentActivity = null;
        currentWebView = null;
    }

    /**
     * Displays the sleek floating HUD overlay on top of the activity.
     */
    private void showFloatingHud() {
        if (currentActivity == null || currentActivity.isFinishing()) return;

        ViewGroup decor = (ViewGroup) currentActivity.getWindow().getDecorView();

        LinearLayout hud = new LinearLayout(currentActivity);
        hud.setOrientation(LinearLayout.VERTICAL);
        hud.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE181825); // Sleek semi-transparent dark crust
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xFFCBA6F7); // Mauve border
        hud.setBackground(bg);
        hud.setElevation(dp(10));

        // Top Row: Indicator + Title + Timer + Stop Button
        LinearLayout topRow = new LinearLayout(currentActivity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvDot = new TextView(currentActivity);
        tvDot.setText("🟢 ");
        tvDot.setTextSize(10);
        topRow.addView(tvDot);

        TextView tvTitle = new TextView(currentActivity);
        tvTitle.setText(sessionName);
        tvTitle.setTextColor(0xFFCDD6F4);
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        topRow.addView(tvTitle, titleParams);

        hudTimerText = new TextView(currentActivity);
        hudTimerText.setText(formatTime(durationMinutes * 60));
        hudTimerText.setTextColor(0xFFA6E3A1); // Green accent
        hudTimerText.setTextSize(12);
        hudTimerText.setTypeface(null, android.graphics.Typeface.BOLD);
        hudTimerText.setPadding(dp(8), 0, dp(8), 0);
        topRow.addView(hudTimerText);

        Button btnStop = new Button(currentActivity);
        btnStop.setText("⏹ STOP");
        btnStop.setTextColor(0xFF1E1E2E);
        btnStop.setTextSize(10);
        btnStop.setTypeface(null, android.graphics.Typeface.BOLD);
        btnStop.setPadding(dp(8), dp(2), dp(8), dp(2));

        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setColor(0xFFF38BA8); // Soft Red
        stopBg.setCornerRadius(dp(8));
        btnStop.setBackground(stopBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        btnStop.setOnClickListener(v -> stopSession());
        topRow.addView(btnStop, btnParams);

        hud.addView(topRow);

        // Bottom Row: Status
        hudStatusText = new TextView(currentActivity);
        hudStatusText.setText("🤖 Initializing AI Macro-Brain...");
        hudStatusText.setTextColor(0xFFBAC2DE);
        hudStatusText.setTextSize(11);
        hudStatusText.setPadding(dp(2), dp(4), dp(2), 0);
        hud.addView(hudStatusText);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.setMargins(dp(16), dp(62), dp(16), 0); // Below top navigation bar

        decor.addView(hud, params);
        floatingHudView = hud;
    }

    private void removeFloatingHud() {
        if (floatingHudView != null && currentActivity != null) {
            ViewGroup decor = (ViewGroup) currentActivity.getWindow().getDecorView();
            decor.removeView(floatingHudView);
            floatingHudView = null;
            hudTimerText = null;
            hudStatusText = null;
        }
    }

    private String formatTime(int totalSecs) {
        int m = Math.max(0, totalSecs / 60);
        int s = Math.max(0, totalSecs % 60);
        return String.format("%02d:%02d", m, s);
    }

    /**
     * Updates countdown timer every second.
     */
    private void startTimerTicker() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                elapsedSeconds++;
                int remainingSeconds = (durationMinutes * 60) - elapsedSeconds;

                if (hudTimerText != null) {
                    hudTimerText.setText(formatTime(remainingSeconds));
                }

                if (remainingSeconds <= 0) {
                    // Session finished!
                    if (currentActivity != null && !currentActivity.isFinishing()) {
                        Toast.makeText(currentActivity, "🎉 AI Interaction Session Completed (" + durationMinutes + " mins)", Toast.LENGTH_LONG).show();
                    }
                    stopSession();
                    return;
                }

                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.postDelayed(timerRunnable, 1000);
    }

    /**
     * Extracts page context from WebView and consults OpenRouter.
     */
    private void triggerAiConsultationAndLoop() {
        if (!isRunning || currentWebView == null) return;

        lastAiCheckinTime = System.currentTimeMillis();

        if (hudStatusText != null) {
            hudStatusText.setText("🧠 Consulting AI Brain (" + (modelName != null && !modelName.isEmpty() ? modelName : "DeepSeek") + ")...");
        }

        String js = "(function() {" +
                "  try {" +
                "    var title = document.title || '';" +
                "    var url = window.location.href || '';" +
                "    var bodyText = document.body ? document.body.innerText.substring(0, 600).replace(/\\s+/g, ' ') : '';" +
                "    return JSON.stringify({ title: title, url: url, snippet: bodyText });" +
                "  } catch(e) {" +
                "    return JSON.stringify({ title: '', url: '', snippet: '' });" +
                "  }" +
                "})();";

        currentWebView.evaluateJavascript(js, value -> {
            if (!isRunning) return;

            String pageTitle = "";
            String currentUrl = currentWebView.getUrl();
            String pageExcerpt = "";

            try {
                if (value != null && !value.equals("null")) {
                    String cleanJson = value;
                    if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                        cleanJson = cleanJson.substring(1, cleanJson.length() - 1);
                        cleanJson = cleanJson.replace("\\\"", "\"").replace("\\\\", "\\");
                    }
                    JSONObject obj = new JSONObject(cleanJson);
                    pageTitle = obj.optString("title", "");
                    currentUrl = obj.optString("url", currentUrl);
                    pageExcerpt = obj.optString("snippet", "");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing page evaluation JSON", e);
            }

            int elapsedMins = elapsedSeconds / 60;

            OpenRouterClient.requestActionPlan(
                    apiKey,
                    modelName,
                    sessionName,
                    sessionDesc,
                    currentUrl,
                    pageTitle,
                    pageExcerpt,
                    elapsedMins,
                    durationMinutes,
                    new OpenRouterClient.Callback() {
                        @Override
                        public void onSuccess(OpenRouterClient.AiActionPlan plan) {
                            if (!isRunning) return;
                            currentPlan = plan;
                            executePlanAction(plan);
                        }

                        @Override
                        public void onError(String error, OpenRouterClient.AiActionPlan fallbackPlan) {
                            if (!isRunning) return;
                            Log.w(TAG, "OpenRouter warning: " + error + ", continuing with organic fallback");
                            currentPlan = fallbackPlan;
                            executePlanAction(fallbackPlan);
                        }
                    }
            );
        });
    }

    /**
     * Executes the action dictated by the AI plan using local kinematics.
     */
    private void executePlanAction(OpenRouterClient.AiActionPlan plan) {
        if (!isRunning || kinematicsEngine == null) return;

        if (hudStatusText != null) {
            hudStatusText.setText("👁️ " + plan.statusMessage);
        }

        String action = plan.action != null ? plan.action.toUpperCase() : "SCROLL_DOWN";

        switch (action) {
            case "READ_PAUSE":
                int pauseTime = (plan.pauseSeconds > 0 ? plan.pauseSeconds : (3 + random.nextInt(3))) * 1000;
                scheduleNextStep(pauseTime);
                break;

            case "CLICK_ELEMENT":
                // If a target keyword is given, try finding it via JS, otherwise tap natural area or scroll
                attemptContextualTap(plan.targetKeyword, () -> scheduleNextStep(2000 + random.nextInt(2500)));
                break;

            case "EXPLORE":
                // Slight exploratory scroll or pause
                boolean exploreDown = random.nextBoolean();
                kinematicsEngine.performNaturalScroll(exploreDown, 0.35f, () -> scheduleNextStep(2500 + random.nextInt(2000)));
                break;

            case "SCROLL_DOWN":
            default:
                float scrollDist = (plan.scrollAmount > 0.2f && plan.scrollAmount < 0.9f) ? plan.scrollAmount : 0.55f;
                kinematicsEngine.performNaturalScroll(true, scrollDist, () -> scheduleNextStep(2200 + random.nextInt(3000)));
                break;
        }
    }

    /**
     * Taps a target element on screen matching the keyword, or falls back to an organic feed tap.
     */
    private void attemptContextualTap(String keyword, Runnable onComplete) {
        if (!isRunning || currentWebView == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Query element position via JavaScript
        String kw = (keyword != null && !keyword.isEmpty()) ? keyword.toLowerCase().replace("'", "\\'") : "button";
        String js = "(function() {" +
                "  var kw = '" + kw + "';" +
                "  var els = Array.from(document.querySelectorAll('a, button, [role=button], img, .card, div[class*=profile], div[class*=photo]'));" +
                "  for (var i = 0; i < els.length; i++) {" +
                "    var el = els[i];" +
                "    var text = (el.innerText || el.getAttribute('aria-label') || el.className || '').toLowerCase();" +
                "    var rect = el.getBoundingClientRect();" +
                "    if (rect.width > 20 && rect.height > 20 && rect.top > 80 && rect.bottom < window.innerHeight - 40) {" +
                "      if (kw === '' || text.indexOf(kw) !== -1 || i === els.length - 1) {" +
                "        return JSON.stringify({ x: rect.left + (rect.width/2), y: rect.top + (rect.height/2) });" +
                "      }" +
                "    }" +
                "  }" +
                "  return JSON.stringify({ x: -1, y: -1 });" +
                "})();";

        currentWebView.evaluateJavascript(js, result -> {
            if (!isRunning) return;

            float tapX = -1;
            float tapY = -1;

            try {
                if (result != null && !result.equals("null")) {
                    String clean = result.replace("\\\"", "\"");
                    if (clean.startsWith("\"") && clean.endsWith("\"")) {
                        clean = clean.substring(1, clean.length() - 1);
                    }
                    JSONObject obj = new JSONObject(clean);
                    tapX = (float) obj.optDouble("x", -1);
                    tapY = (float) obj.optDouble("y", -1);
                }
            } catch (Exception ignored) {}

            if (tapX > 0 && tapY > 0) {
                kinematicsEngine.performHumanTap(tapX, tapY, onComplete);
            } else {
                // If no specific element found, perform a gentle thumb scroll instead
                kinematicsEngine.performNaturalScroll(true, 0.4f, onComplete);
            }
        });
    }

    /**
     * Schedules the next action step in the continuous human browsing stream.
     */
    private void scheduleNextStep(long delayMs) {
        if (!isRunning) return;

        nextActionRunnable = () -> {
            if (!isRunning) return;

            long now = System.currentTimeMillis();
            // Periodically check-in with OpenRouter every 2.5 minutes
            if (now - lastAiCheckinTime >= AI_CHECKIN_INTERVAL_MS) {
                triggerAiConsultationAndLoop();
            } else {
                // Local organic micro-reflex cycle
                continueLocalReflexCycle();
            }
        };

        mainHandler.postDelayed(nextActionRunnable, delayMs);
    }

    /**
     * Executes local reflex variations (reading, browsing, slight scrolls) between AI calls.
     */
    private void continueLocalReflexCycle() {
        if (!isRunning || kinematicsEngine == null) return;

        double r = random.nextDouble();

        if (r < 0.50) {
            // Natural downward scroll browse
            if (hudStatusText != null) hudStatusText.setText("📱 Scrolling down feed...");
            float dist = 0.40f + (random.nextFloat() * 0.35f);
            kinematicsEngine.performNaturalScroll(true, dist, () -> scheduleNextStep(2000 + random.nextInt(3500)));
        } else if (r < 0.80) {
            // Human reading pause
            if (hudStatusText != null) hudStatusText.setText("📖 Reading content & viewing details...");
            int pause = 2500 + random.nextInt(4000);
            scheduleNextStep(pause);
        } else if (r < 0.92) {
            // Slight reverse scroll (human looking back up at something)
            if (hudStatusText != null) hudStatusText.setText("👀 Checking previous item...");
            kinematicsEngine.performNaturalScroll(false, 0.25f, () -> scheduleNextStep(1800 + random.nextInt(2500)));
        } else {
            // Interactive tap exploration
            if (hudStatusText != null) hudStatusText.setText("👉 Exploring interactive item...");
            attemptContextualTap("", () -> scheduleNextStep(2200 + random.nextInt(3000)));
        }
    }
}
