package com.rcl.androidstreamcontrol.simulation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import com.rcl.androidstreamcontrol.BrowserActivity;
import com.rcl.androidstreamcontrol.utils.BrowserTab;
import com.rcl.androidstreamcontrol.utils.HumanTouchKinematicsEngine;
import com.rcl.androidstreamcontrol.utils.WebViewProxyManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Traffic Simulation Engine — 5-Layer Human Browsing Protocol.
 *
 * Implements:
 * Layer 1: Page Load & Impression (Full DOM & resource render, active viewport, 3-5s settlement)
 * Layer 2: Interaction & Hesitation (5-15s hero hesitation, Bézier movement, DOM hover synthesis 1-3s)
 * Layer 3: Session Deep Dive (Thumb scroll 200-400px, 2-5s pause, 50-70% depth, reverse scroll, multi-page)
 * Layer 4: Viewability Verification (getBoundingClientRect check, in-viewport 1-2s before tap)
 * Layer 5: Project Link Engagement (Browse gallery, external tab open 10-20s, return & browse 10-15s)
 */
public class TrafficSimulationEngine {

    private static final String TAG = "TrafficSimEngine";

    public interface TelemetryCallback {
        void onStatusUpdate(String phase, String detail, int elapsedSeconds, int pagesVisited, int clicksGenerated);
        void onSessionCompleted(boolean success, String summary);
    }

    private final BrowserActivity activity;
    private final WebView webView;
    private final String portfolioUrl;
    private final BehaviorProfile profile;
    private final TelemetryCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private HumanTouchKinematicsEngine kinematicsEngine;
    private volatile boolean isRunning = false;
    private long sessionStartTime = 0;
    private int pagesVisited = 0;
    private int clicksGenerated = 0;
    private int currentScrollDepth = 0;
    private String currentTargetUrl;
    private Runnable tickerRunnable;

    public TrafficSimulationEngine(
            BrowserActivity activity,
            WebView webView,
            String portfolioUrl,
            BehaviorProfile profile,
            TelemetryCallback callback
    ) {
        this.activity = activity;
        this.webView = webView;
        this.portfolioUrl = portfolioUrl != null ? portfolioUrl.trim() : "https://duckduckgo.com";
        this.currentTargetUrl = this.portfolioUrl;
        this.profile = profile;
        this.callback = callback;
        if (webView != null) {
            this.kinematicsEngine = new HumanTouchKinematicsEngine(webView);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        sessionStartTime = SystemClock.elapsedRealtime();

        if (kinematicsEngine != null) {
            kinematicsEngine.start();
        }

        // Ensure selective residential proxy mode is active so whitelisted target traffic routes via US proxy
        WebViewProxyManager.setFullProxyMode(false);
        WebViewProxyManager.resumeProxy();

        // Auto-whitelist portfolio domain
        try {
            android.net.Uri uri = android.net.Uri.parse(portfolioUrl);
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) {
                WebViewProxyManager.addWhitelistedDomain(activity != null ? activity.getApplicationContext() : null, host);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse portfolio host for whitelist", e);
        }

        startTelemetryTicker();
        executeLayer1Arrival();
    }

    public void stop() {
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (kinematicsEngine != null) {
            kinematicsEngine.stop();
        }
        notifyStatus("Stopped", "Simulation stopped by user or system", getElapsedSeconds());
    }

    private int getElapsedSeconds() {
        if (sessionStartTime == 0) return 0;
        return (int) ((SystemClock.elapsedRealtime() - sessionStartTime) / 1000);
    }

    private void startTelemetryTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                int elapsed = getElapsedSeconds();
                if (callback != null) {
                    callback.onStatusUpdate(
                            "Running (" + profile.archetype.getDisplayName() + ")",
                            "Scroll depth: " + currentScrollDepth + "% | Pages: " + pagesVisited + " | Clicks: " + clicksGenerated,
                            elapsed, pagesVisited, clicksGenerated
                    );
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(tickerRunnable);
    }

    private void notifyStatus(String phase, String detail, int elapsed) {
        if (callback != null) {
            callback.onStatusUpdate(phase, detail, elapsed, pagesVisited, clicksGenerated);
        }
    }

    // =========================================================================
    // LAYER 1: Arrival & Impression
    // =========================================================================
    private void executeLayer1Arrival() {
        if (!isRunning || webView == null) return;

        // Apply authentic referrer and UTM campaign tracking from TrafficSourceProfile
        final String landingUrl = (profile != null && profile.trafficSource != null)
                ? profile.trafficSource.appendTrackingParameters(portfolioUrl)
                : portfolioUrl;
        final Map<String, String> initialHeaders = (profile != null && profile.trafficSource != null)
                ? profile.trafficSource.getInitialHttpHeaders()
                : new HashMap<>();

        String channelName = (profile != null && profile.trafficSource != null)
                ? profile.trafficSource.channelType.getDisplayName()
                : "Direct";

        notifyStatus("Layer 1: Arrival (" + channelName + ")", "Target URL: " + landingUrl, getElapsedSeconds());

        // Load the page with authentic HTTP Fetch Metadata headers
        activity.runOnUiThread(() -> {
            if (webView != null) {
                if (initialHeaders != null && !initialHeaders.isEmpty()) {
                    webView.loadUrl(landingUrl, initialHeaders);
                } else {
                    webView.loadUrl(landingUrl);
                }
            }
        });

        // Wait for page load and resources to render (3 to 5 seconds after load)
        waitForPageReady(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                verifyAdCreativePresence(() -> {
                    if (!isRunning) return;
                    int settlementDelayMs = 2500 + random.nextInt(1501); // 2.5 - 4s
                    notifyStatus("Layer 1: Impression Verified",
                            "DOM & In-Page Push Creative active. Settling (" + (settlementDelayMs / 1000) + "s)", getElapsedSeconds());

                    handler.postDelayed(() -> {
                        if (!isRunning) return;
                        pagesVisited++;
                        executeLayer2Interaction();
                    }, settlementDelayMs);
                });
            }
        });
    }

    private void verifyAdCreativePresence(Runnable onVerified) {
        if (!isRunning || webView == null) {
            if (onVerified != null) onVerified.run();
            return;
        }

        final int maxAdChecks = 8; // Check every 800ms up to ~6.4 seconds
        final int[] checks = {0};

        Runnable adCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning || webView == null) return;
                checks[0]++;

                webView.evaluateJavascript(
                        "(function() {" +
                        "  var hasInPage = document.querySelector('[class*=\"inpage\"], [id*=\"inpage\"], [class*=\"push\"], [id*=\"push\"]') !== null;" +
                        "  var hasAdScript = document.querySelector('script[src*=\"fond-appointment\"], script[src*=\"hilltop\"]') !== null;" +
                        "  var hasAdFrame = document.querySelector('iframe[src*=\"fond-appointment\"], iframe[src*=\"hilltop\"]') !== null;" +
                        "  return hasInPage || hasAdScript || hasAdFrame;" +
                        "})()",
                        value -> {
                            if (!isRunning) return;
                            boolean adFound = (value != null && "true".equalsIgnoreCase(value.trim()));
                            if (adFound) {
                                notifyStatus("Layer 1: Ad Creative Verified",
                                        "HilltopAds In-Page Push physically rendered in DOM", getElapsedSeconds());
                                if (onVerified != null) onVerified.run();
                            } else if (checks[0] >= maxAdChecks) {
                                notifyStatus("Layer 1: Settlement Complete",
                                        "Ad script settlement window elapsed. Proceeding.", getElapsedSeconds());
                                if (onVerified != null) onVerified.run();
                            } else {
                                handler.postDelayed(this, 800);
                            }
                        }
                );
            }
        };

        handler.postDelayed(adCheckRunnable, 500);
    }

    private void waitForPageReady(Runnable onReady) {
        final int maxChecks = 30; // 15 seconds max wait
        final int[] checks = {0};

        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning || webView == null) return;
                checks[0]++;

                webView.evaluateJavascript("document.readyState", value -> {
                    if (!isRunning) return;
                    boolean ready = (value != null && value.contains("complete"));
                    if (ready || checks[0] >= maxChecks) {
                        if (onReady != null) onReady.run();
                    } else {
                        handler.postDelayed(this, 500);
                    }
                });
            }
        };
        handler.postDelayed(checkRunnable, 1000);
    }

    // =========================================================================
    // LAYER 2: Interaction & Hesitation (Reading hero, hover states)
    // =========================================================================
    private void executeLayer2Interaction() {
        if (!isRunning || webView == null) return;

        int hesitationMs = profile.heroHesitationSeconds * 1000;
        notifyStatus("Layer 2: Hero Hesitation",
                "Simulating visitor reading hero section (" + profile.heroHesitationSeconds + "s pause)", getElapsedSeconds());

        // Perform subtle micro-tremors and Bézier movement during reading
        long halfHesitation = hesitationMs / 2;
        handler.postDelayed(() -> {
            if (!isRunning || webView == null) return;
            // Synthesize subtle mouse hover on navigation or hero elements
            synthesizeDomHover();
        }, halfHesitation);

        handler.postDelayed(() -> {
            if (!isRunning) return;
            notifyStatus("Layer 2: Dwell Complete", "Finished hero examination. Beginning deep dive exploration.", getElapsedSeconds());
            executeLayer3DeepDive(0);
        }, hesitationMs);
    }

    private void synthesizeDomHover() {
        if (webView == null || !isRunning) return;

        String js = "(function() {" +
                "  var els = document.querySelectorAll('header a, nav a, .nav-item, h1, h2, .project-card, .card, button');" +
                "  if (els.length > 0) {" +
                "    var target = els[Math.floor(Math.random() * els.length)];" +
                "    var rect = target.getBoundingClientRect();" +
                "    var evt = new MouseEvent('mouseover', {bubbles: true, cancelable: true, clientX: rect.x + rect.width/2, clientY: rect.y + rect.height/2});" +
                "    target.dispatchEvent(evt);" +
                "    return JSON.stringify({found: true, x: rect.x + rect.width/2, y: rect.y + rect.height/2});" +
                "  }" +
                "  return JSON.stringify({found: false});" +
                "})();";

        webView.evaluateJavascript(js, result -> {
            if (!isRunning || result == null) return;
            try {
                // Strip quotes if wrapped
                String jsonStr = result;
                if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                    jsonStr = jsonStr.substring(1, jsonStr.length() - 1).replace("\\\"", "\"");
                }
                JSONObject obj = new JSONObject(jsonStr);
                if (obj.optBoolean("found", false) && kinematicsEngine != null) {
                    float x = (float) obj.optDouble("x", 150);
                    float y = (float) obj.optDouble("y", 150);
                    // Do slight micro-jitter at the hover point
                    kinematicsEngine.performHumanTap(x, y, null);
                }
            } catch (Exception ignored) {}
        });
    }

    // =========================================================================
    // LAYER 3: Session Behavior & Exploration (Deep dive, natural scrolling)
    // =========================================================================
    private void executeLayer3DeepDive(final int scrollCount) {
        if (!isRunning || webView == null) return;

        // Query current scroll depth
        String depthJs = "(function() {" +
                "  var scrollY = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;" +
                "  var total = (document.documentElement.scrollHeight || 1) - window.innerHeight;" +
                "  return total > 0 ? Math.min(100, Math.round((scrollY / total) * 100)) : 100;" +
                "})();";

        webView.evaluateJavascript(depthJs, value -> {
            if (!isRunning) return;
            try {
                currentScrollDepth = Integer.parseInt(value.trim());
            } catch (Exception e) {
                currentScrollDepth = Math.min(100, scrollCount * 18);
            }

            notifyStatus("Layer 3: Exploring Page",
                    "Scroll depth: " + currentScrollDepth + "% / " + profile.targetScrollDepthPercent + "% (Step #" + (scrollCount + 1) + ")",
                    getElapsedSeconds());

            // If we have reached the target scroll depth
            if (currentScrollDepth >= profile.targetScrollDepthPercent || scrollCount >= 6) {
                handlePageExplorationCompleted();
                return;
            }

            // Decide whether to reverse-scroll (20% probability)
            boolean reverseScroll = (scrollCount > 1 && random.nextInt(100) < 20);

            if (reverseScroll) {
                notifyStatus("Layer 3: Reverse Scroll", "Simulating re-reading prior section", getElapsedSeconds());
                if (kinematicsEngine != null) {
                    kinematicsEngine.performNaturalScroll(false, 0.35f, () -> {
                        scheduleNextScroll(scrollCount + 1);
                    });
                } else {
                    scheduleNextScroll(scrollCount + 1);
                }
            } else {
                // Natural downward scroll
                float distFraction = Math.min(0.65f, (float) profile.scrollStepPx / (webView.getHeight() > 0 ? webView.getHeight() : 1200));
                distFraction = Math.max(0.25f, distFraction);

                if (kinematicsEngine != null) {
                    kinematicsEngine.performNaturalScroll(true, distFraction, () -> {
                        scheduleNextScroll(scrollCount + 1);
                    });
                } else {
                    scheduleNextScroll(scrollCount + 1);
                }
            }
        });
    }

    private void scheduleNextScroll(final int nextScrollCount) {
        if (!isRunning) return;

        // Reading dwell: pause 2 to 5 seconds between sections
        long pause = profile.pauseBetweenScrollsMs + random.nextInt(1200);

        // Every 3 scrolls, simulate deep reading of a text paragraph (3-8 seconds)
        if (nextScrollCount % 3 == 0) {
            pause += profile.paragraphReadingDwellMs;
            notifyStatus("Layer 3: Reading Text",
                    "Deep dwell: reading paragraph content (" + (pause / 1000) + "s)", getElapsedSeconds());
        }

        handler.postDelayed(() -> {
            executeLayer3DeepDive(nextScrollCount);
        }, pause);
    }

    private void handlePageExplorationCompleted() {
        if (!isRunning) return;

        // Check if visitor should bounce
        if (profile.shouldBounce && pagesVisited >= 1) {
            notifyStatus("Layer 3: Session Complete",
                    "Visitor finished reading landing page (Simulating 35% natural bounce rate)", getElapsedSeconds());
            concludeSession(true, "Completed natural bounce session. Stayed " + getElapsedSeconds() + "s, read 1 page, depth " + currentScrollDepth + "%.");
            return;
        }

        // Check if target page count reached
        if (pagesVisited >= profile.targetPagesCount) {
            if (profile.shouldClickProjectLink && clicksGenerated == 0) {
                // Must execute Layer 5 before leaving
                executeLayer5ProjectEngagement();
            } else {
                concludeSession(true, "Session completed naturally. Visited " + pagesVisited + " pages, " + clicksGenerated + " clicks in " + getElapsedSeconds() + "s.");
            }
            return;
        }

        // Navigate to the next page (e.g. Projects, About, Blog, Contact)
        navigateNextInternalPage();
    }

    private void navigateNextInternalPage() {
        if (!isRunning || webView == null) return;

        notifyStatus("Layer 3: Page Navigation", "Finding next portfolio section link...", getElapsedSeconds());

        String findLinksJs = "(function() {" +
                "  var links = document.querySelectorAll('header a, nav a, .nav-link, footer a, a');" +
                "  var results = [];" +
                "  var currHost = window.location.hostname;" +
                "  for (var i = 0; i < links.length; i++) {" +
                "    var href = links[i].getAttribute('href') || '';" +
                "    var text = (links[i].innerText || links[i].textContent || '').trim().toLowerCase();" +
                "    if (!href || href.startsWith('#') || href.startsWith('javascript:')) continue;" +
                "    // Check internal link" +
                "    var isInternal = href.startsWith('/') || href.includes(currHost);" +
                "    if (isInternal || text.includes('project') || text.includes('about') || text.includes('blog') || text.includes('contact')) {" +
                "      var rect = links[i].getBoundingClientRect();" +
                "      results.push({" +
                "        text: text," +
                "        href: links[i].href," +
                "        x: Math.round(rect.left + rect.width / 2)," +
                "        y: Math.round(rect.top + rect.height / 2)," +
                "        top: Math.round(rect.top)," +
                "        height: Math.round(rect.height)" +
                "      });" +
                "    }" +
                "  }" +
                "  return JSON.stringify(results.slice(0, 10));" +
                "})();";

        webView.evaluateJavascript(findLinksJs, result -> {
            if (!isRunning) return;
            try {
                String clean = cleanJsJson(result);
                JSONArray arr = new JSONArray(clean);

                if (arr.length() > 0) {
                    // Pick a relevant internal page
                    JSONObject targetLink = null;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject link = arr.getJSONObject(i);
                        String txt = link.optString("text", "");
                        if (txt.contains("project") || txt.contains("about") || txt.contains("blog") || txt.contains("contact")) {
                            targetLink = link;
                            break;
                        }
                    }
                    if (targetLink == null) {
                        targetLink = arr.getJSONObject(random.nextInt(arr.length()));
                    }

                    final JSONObject selected = targetLink;
                    // Execute Layer 4: Viewability Verification before clicking
                    executeLayer4ViewabilityAndClick(selected, () -> {
                        pagesVisited++;
                        notifyStatus("Layer 3: Page Transition",
                                "Navigated to: " + selected.optString("text", "page"), getElapsedSeconds());

                        // Wait for new page load
                        waitForPageReady(() -> {
                            if (!isRunning) return;
                            handler.postDelayed(() -> {
                                executeLayer3DeepDive(0);
                            }, 2500 + random.nextInt(2000));
                        });
                    });
                } else {
                    // Fallback if no DOM links found
                    concludeSession(true, "Exploration finished. Visited " + pagesVisited + " pages.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing internal links", e);
                concludeSession(true, "Completed session.");
            }
        });
    }

    // =========================================================================
    // LAYER 4: Viewability Verification (The Visibility Check)
    // =========================================================================
    private void executeLayer4ViewabilityAndClick(final JSONObject elementTarget, final Runnable onNavigated) {
        if (!isRunning || webView == null || elementTarget == null) return;

        final float targetX = (float) elementTarget.optDouble("x", 200);
        final float targetY = (float) elementTarget.optDouble("y", 200);
        final String href = elementTarget.optString("href", "");

        notifyStatus("Layer 4: Viewability Check",
                "Verifying target element is in viewport for 1-2s before tap", getElapsedSeconds());

        // Check if element is in viewport
        int viewHeight = webView.getHeight() > 0 ? webView.getHeight() : 1200;
        boolean inViewport = (targetY >= 50 && targetY <= viewHeight - 50);

        if (!inViewport) {
            // Scroll element into view
            notifyStatus("Layer 4: Scroll into View", "Target below fold. Scrolling smoothly to bring into view.", getElapsedSeconds());
            if (kinematicsEngine != null) {
                kinematicsEngine.performNaturalScroll(targetY > viewHeight, 0.4f, () -> {
                    // Wait 1-2s fixation dwell
                    waitAndTap(targetX, Math.max(100f, Math.min(targetY, viewHeight - 100f)), href, onNavigated);
                });
            } else {
                waitAndTap(targetX, Math.max(100f, Math.min(targetY, viewHeight - 100f)), href, onNavigated);
            }
        } else {
            // In viewport: wait 1 to 2 seconds fixation
            waitAndTap(targetX, targetY, href, onNavigated);
        }
    }

    private void waitAndTap(float x, float y, String href, Runnable onNavigated) {
        if (!isRunning) return;

        int fixationDwellMs = 1200 + random.nextInt(900); // 1.2 to 2.1s
        handler.postDelayed(() -> {
            if (!isRunning) return;

            notifyStatus("Layer 4: Human Tap", "Dispatching organic tap (Fitts's Law + micro-tremor)", getElapsedSeconds());

            if (kinematicsEngine != null) {
                kinematicsEngine.performHumanTap(x, y, () -> {
                    // Navigate to href if browser doesn't automatically trigger within 800ms
                    handler.postDelayed(() -> {
                        if (!isRunning) return;
                        if (href != null && !href.isEmpty() && webView != null) {
                            String currUrl = webView.getUrl();
                            if (currUrl == null || !currUrl.equals(href)) {
                                Map<String, String> sameOriginHeaders = new HashMap<>();
                                if (currUrl != null && !currUrl.isEmpty()) {
                                    sameOriginHeaders.put("Referer", currUrl);
                                }
                                sameOriginHeaders.put("Sec-Fetch-Site", "same-origin");
                                sameOriginHeaders.put("Sec-Fetch-Mode", "navigate");
                                sameOriginHeaders.put("Sec-Fetch-Dest", "document");
                                sameOriginHeaders.put("Sec-Fetch-User", "?1");
                                sameOriginHeaders.put("Upgrade-Insecure-Requests", "1");
                                webView.loadUrl(href, sameOriginHeaders);
                            }
                        }
                        if (onNavigated != null) onNavigated.run();
                    }, 800);
                });
            } else {
                if (href != null && !href.isEmpty() && webView != null) {
                    String currUrl = webView.getUrl();
                    Map<String, String> sameOriginHeaders = new HashMap<>();
                    if (currUrl != null && !currUrl.isEmpty()) {
                        sameOriginHeaders.put("Referer", currUrl);
                    }
                    sameOriginHeaders.put("Sec-Fetch-Site", "same-origin");
                    sameOriginHeaders.put("Sec-Fetch-Mode", "navigate");
                    sameOriginHeaders.put("Sec-Fetch-Dest", "document");
                    sameOriginHeaders.put("Sec-Fetch-User", "?1");
                    sameOriginHeaders.put("Upgrade-Insecure-Requests", "1");
                    webView.loadUrl(href, sameOriginHeaders);
                }
                if (onNavigated != null) onNavigated.run();
            }
        }, fixationDwellMs);
    }

    // =========================================================================
    // LAYER 5: Project Link Engagement (Conversion Simulation)
    // =========================================================================
    private void executeLayer5ProjectEngagement() {
        if (!isRunning || webView == null) return;

        notifyStatus("Layer 5: Projects Gallery",
                "Navigating to Projects Gallery to inspect cards and external demos", getElapsedSeconds());

        // Find project external links
        String findProjectsJs = "(function() {" +
                "  var links = document.querySelectorAll('.project-card a, .projects a, a[target=\"_blank\"], a[href^=\"http\"]');" +
                "  var results = [];" +
                "  var currHost = window.location.hostname;" +
                "  for (var i = 0; i < links.length; i++) {" +
                "    var href = links[i].href || '';" +
                "    if (!href || href.startsWith('#') || href.startsWith('javascript:')) continue;" +
                "    var isExternal = !href.includes(currHost);" +
                "    if (isExternal || links[i].getAttribute('target') === '_blank') {" +
                "      var rect = links[i].getBoundingClientRect();" +
                "      results.push({" +
                "        text: (links[i].innerText || 'Project Demo').trim()," +
                "        href: href," +
                "        x: Math.round(rect.left + rect.width / 2)," +
                "        y: Math.round(rect.top + rect.height / 2)" +
                "      });" +
                "    }" +
                "  }" +
                "  return JSON.stringify(results.slice(0, 5));" +
                "})();";

        webView.evaluateJavascript(findProjectsJs, result -> {
            if (!isRunning) return;
            try {
                String clean = cleanJsJson(result);
                JSONArray arr = new JSONArray(clean);

                if (arr.length() > 0) {
                    JSONObject projectLink = arr.getJSONObject(random.nextInt(arr.length()));
                    final String externalUrl = projectLink.optString("href", "");
                    final String title = projectLink.optString("text", "External Demo");

                    notifyStatus("Layer 5: External Demo Found",
                            "Opening external project in Tab 2: " + title, getElapsedSeconds());

                    // Open external project in new tab
                    openExternalProjectTab(externalUrl, title);
                } else {
                    // No external link found on page, conclude session gracefully
                    concludeSession(true, "Session completed. Visited " + pagesVisited + " pages.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error finding project link", e);
                concludeSession(true, "Session completed.");
            }
        });
    }

    private void openExternalProjectTab(String externalUrl, String title) {
        if (!isRunning || activity == null) return;

        clicksGenerated++;
        notifyStatus("Layer 5: Tab 2 Active",
                "Viewing external project for " + profile.externalTabStaySeconds + "s: " + externalUrl, getElapsedSeconds());

        activity.runOnUiThread(() -> {
            try {
                // Open new isolated tab
                BrowserTab newTab = activity.createNewTab(null, false);
                if (newTab != null && newTab.webView != null) {
                    newTab.webView.loadUrl(externalUrl);

                    // Keep Tab 2 open for 10-20 seconds simulating reading project demo
                    handler.postDelayed(() -> {
                        if (!isRunning) return;

                        notifyStatus("Layer 5: Closing Tab 2",
                                "Reading finished. Closing external tab and returning to portfolio", getElapsedSeconds());

                        activity.runOnUiThread(() -> {
                            try {
                                int tabCount = activity.getTabCount();
                                if (tabCount > 1) {
                                    activity.closeTab(tabCount - 1);
                                }
                            } catch (Exception ignored) {}

                            // Post return browse: 10 to 15 seconds
                            notifyStatus("Layer 5: Post-Return Browse",
                                    "Browsing portfolio for " + profile.postReturnBrowseSeconds + "s more before departure",
                                    getElapsedSeconds());

                            handler.postDelayed(() -> {
                                concludeSession(true, "Full 5-layer simulation successful. Total duration: "
                                        + getElapsedSeconds() + "s | Clicks: " + clicksGenerated + " | Pages: " + pagesVisited);
                            }, profile.postReturnBrowseSeconds * 1000L);
                        });

                    }, profile.externalTabStaySeconds * 1000L);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening external project tab", e);
                concludeSession(true, "Session completed with external link interaction.");
            }
        });
    }

    private void concludeSession(boolean success, String summary) {
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (kinematicsEngine != null) {
            kinematicsEngine.stop();
        }
        notifyStatus("Finished", summary, getElapsedSeconds());
        if (callback != null) {
            callback.onSessionCompleted(success, summary);
        }
    }

    private String cleanJsJson(String raw) {
        if (raw == null) return "[]";
        raw = raw.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
            raw = raw.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (raw.isEmpty()) return "[]";
        return raw;
    }
}
