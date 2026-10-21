package com.rcl.androidstreamcontrol.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenRouter Client for AI-Driven Human Interaction Sessions.
 * Connects to OpenRouter completions API (default: DeepSeek chat / flash).
 * Sends high-level page context and receives macro-level navigation instructions.
 */
public class OpenRouterClient {

    private static final String TAG = "OpenRouterClient";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static class AiActionPlan {
        public String action = "SCROLL_DOWN"; // SCROLL_DOWN, READ_PAUSE, CLICK_ELEMENT, EXPLORE
        public String statusMessage = "Browsing naturally";
        public String targetKeyword = "";
        public int pauseSeconds = 3;
        public float scrollAmount = 0.55f; // Screen height fraction

        @Override
        public String toString() {
            return "AiActionPlan{" +
                    "action='" + action + '\'' +
                    ", statusMessage='" + statusMessage + '\'' +
                    ", targetKeyword='" + targetKeyword + '\'' +
                    ", pauseSeconds=" + pauseSeconds +
                    ", scrollAmount=" + scrollAmount +
                    '}';
        }
    }

    public interface Callback {
        void onSuccess(AiActionPlan plan);
        void onError(String error, AiActionPlan fallbackPlan);
    }

    /**
     * Request an action plan from OpenRouter asynchronously.
     */
    public static void requestActionPlan(
            final String apiKey,
            final String modelName,
            final String sessionName,
            final String sessionDesc,
            final String currentUrl,
            final String pageTitle,
            final String pageExcerpt,
            final int elapsedMinutes,
            final int totalMinutes,
            final Callback callback
    ) {
        // If API key is empty or not configured, immediately return an organic fallback plan
        if (apiKey == null || apiKey.trim().isEmpty()) {
            AiActionPlan fallback = generateHeuristicPlan(sessionName, sessionDesc, currentUrl, pageTitle);
            if (callback != null) {
                MAIN_HANDLER.post(() -> callback.onSuccess(fallback));
            }
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(AiAgentConfig.DEFAULT_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                conn.setRequestProperty("HTTP-Referer", "https://github.com/dgblack/AndroidStreamControl");
                conn.setRequestProperty("X-Title", "Android Task Browser");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(18000);
                conn.setDoOutput(true);

                String chosenModel = (modelName != null && !modelName.trim().isEmpty())
                        ? modelName.trim()
                        : AiAgentConfig.DEFAULT_MODEL;

                // Build OpenRouter Chat Completion Payload
                JSONObject root = new JSONObject();
                root.put("model", chosenModel);
                root.put("temperature", 0.65);
                root.put("max_tokens", 350);

                JSONArray messages = new JSONArray();

                // System prompt establishing the persona & strict JSON output format
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content",
                        "You are the cognitive macro-brain directing an authentic human browsing engine on an Android mobile device. " +
                        "Your duty: guide natural, stealthy, human-like interaction for a live session without triggering bot detection. " +
                        "Pacing must be organic: genuine reading pauses, scrolling down to browse, viewing details, and casual interaction. " +
                        "You MUST respond ONLY with a raw JSON object (no markdown, no backticks) with keys:\n" +
                        "{\n" +
                        "  \"action\": \"SCROLL_DOWN\" | \"READ_PAUSE\" | \"CLICK_ELEMENT\" | \"EXPLORE\",\n" +
                        "  \"statusMessage\": \"Short 3-5 word user-friendly status to display on mobile screen\",\n" +
                        "  \"targetKeyword\": \"optional keyword of element or button to interact with, e.g. 'profile', 'like', 'photo'\",\n" +
                        "  \"pauseSeconds\": integer between 2 and 7,\n" +
                        "  \"scrollAmount\": float between 0.3 and 0.8\n" +
                        "}"
                );
                messages.put(systemMsg);

                // User prompt with full session context & current page state
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content",
                        "SESSION NAME: " + sessionName + "\n" +
                        "SESSION GOAL & DESCRIPTION: " + sessionDesc + "\n" +
                        "SESSION PROGRESS: " + elapsedMinutes + "m of " + totalMinutes + "m completed\n" +
                        "CURRENT PAGE URL: " + currentUrl + "\n" +
                        "CURRENT PAGE TITLE: " + pageTitle + "\n" +
                        "PAGE VISIBLE CONTENT EXCERPT:\n" + (pageExcerpt != null && !pageExcerpt.isEmpty() ? pageExcerpt : "(No text excerpt available)") + "\n\n" +
                        "What is the next natural human browsing step?"
                );
                messages.put(userMsg);

                root.put("messages", messages);

                byte[] postData = root.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(postData.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStream is = conn.getInputStream();
                         BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }

                        AiActionPlan plan = parseAiResponse(sb.toString());
                        if (plan == null) {
                            plan = generateHeuristicPlan(sessionName, sessionDesc, currentUrl, pageTitle);
                        }
                        final AiActionPlan finalPlan = plan;
                        MAIN_HANDLER.post(() -> {
                            if (callback != null) callback.onSuccess(finalPlan);
                        });
                    }
                } else {
                    String errorMsg = "HTTP " + code + " from OpenRouter";
                    try (InputStream es = conn.getErrorStream()) {
                        if (es != null) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            errorMsg += ": " + sb.toString();
                        }
                    } catch (Exception ignored) {}

                    Log.w(TAG, errorMsg);
                    AiActionPlan fallback = generateHeuristicPlan(sessionName, sessionDesc, currentUrl, pageTitle);
                    final String err = errorMsg;
                    MAIN_HANDLER.post(() -> {
                        if (callback != null) callback.onError(err, fallback);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "OpenRouter call failed", e);
                AiActionPlan fallback = generateHeuristicPlan(sessionName, sessionDesc, currentUrl, pageTitle);
                final String err = e.getMessage() != null ? e.getMessage() : "Network error";
                MAIN_HANDLER.post(() -> {
                    if (callback != null) callback.onError(err, fallback);
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * Parses the raw JSON response from OpenRouter completions endpoint.
     */
    private static AiActionPlan parseAiResponse(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject first = choices.getJSONObject(0);
                JSONObject message = first.optJSONObject("message");
                if (message != null) {
                    String content = message.optString("content", "");
                    return parseContentToJsonPlan(content);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse OpenRouter response", e);
        }
        return null;
    }

    private static AiActionPlan parseContentToJsonPlan(String content) {
        if (content == null) return null;
        content = content.trim();

        // Strip markdown fences ```json ... ``` if model enclosed it
        if (content.startsWith("```")) {
            int firstNewline = content.indexOf('\n');
            if (firstNewline != -1) {
                content = content.substring(firstNewline + 1);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();
        }

        try {
            JSONObject obj = new JSONObject(content);
            AiActionPlan plan = new AiActionPlan();
            plan.action = obj.optString("action", "SCROLL_DOWN").toUpperCase();
            plan.statusMessage = obj.optString("statusMessage", "Browsing feed");
            plan.targetKeyword = obj.optString("targetKeyword", "");
            plan.pauseSeconds = Math.max(1, Math.min(obj.optInt("pauseSeconds", 3), 10));
            plan.scrollAmount = (float) Math.max(0.2, Math.min(obj.optDouble("scrollAmount", 0.55), 0.9));
            return plan;
        } catch (Exception e) {
            Log.w(TAG, "Content was not direct JSON, attempting extraction: " + content);
            // Fallback: search for JSON substring
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                try {
                    JSONObject obj = new JSONObject(content.substring(start, end + 1));
                    AiActionPlan plan = new AiActionPlan();
                    plan.action = obj.optString("action", "SCROLL_DOWN").toUpperCase();
                    plan.statusMessage = obj.optString("statusMessage", "Reading content");
                    plan.targetKeyword = obj.optString("targetKeyword", "");
                    plan.pauseSeconds = Math.max(1, Math.min(obj.optInt("pauseSeconds", 3), 10));
                    plan.scrollAmount = (float) Math.max(0.2, Math.min(obj.optDouble("scrollAmount", 0.55), 0.9));
                    return plan;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Autonomous heuristic fallback plan if API is unreachable, offline, or key is missing.
     * Incorporates user's session description keywords to maintain organic context.
     */
    public static AiActionPlan generateHeuristicPlan(
            String sessionName, String sessionDesc, String currentUrl, String pageTitle
    ) {
        AiActionPlan plan = new AiActionPlan();
        double r = Math.random();

        String combined = (sessionName + " " + sessionDesc).toLowerCase();
        boolean isDating = combined.contains("dating") || combined.contains("tinder") || combined.contains("bumble") || combined.contains("match");
        boolean isSocial = combined.contains("social") || combined.contains("instagram") || combined.contains("twitter") || combined.contains("feed");

        if (r < 0.45) {
            plan.action = "SCROLL_DOWN";
            plan.statusMessage = isDating ? "Viewing next profiles" : (isSocial ? "Browsing feed stories" : "Scrolling through content");
            plan.scrollAmount = 0.45f + (float)(Math.random() * 0.35f);
            plan.pauseSeconds = 2 + (int)(Math.random() * 3);
        } else if (r < 0.75) {
            plan.action = "READ_PAUSE";
            plan.statusMessage = isDating ? "Reading profile details & bio" : "Reading article & comments";
            plan.pauseSeconds = 3 + (int)(Math.random() * 4);
        } else if (r < 0.90) {
            plan.action = "CLICK_ELEMENT";
            plan.statusMessage = isDating ? "Viewing profile photo gallery" : "Viewing post details";
            plan.targetKeyword = isDating ? "photo" : "card";
            plan.pauseSeconds = 2 + (int)(Math.random() * 3);
        } else {
            plan.action = "EXPLORE";
            plan.statusMessage = "Casual screen exploration";
            plan.scrollAmount = 0.25f + (float)(Math.random() * 0.25f);
            plan.pauseSeconds = 3;
        }
        return plan;
    }
}
