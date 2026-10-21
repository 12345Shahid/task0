package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class AiAgentConfig {

    private static final String PREF_NAME = "ai_agent_prefs";
    private static final String KEY_OPENROUTER_API_KEY = "openrouter_api_key";
    private static final String KEY_OPENROUTER_MODEL = "openrouter_model";
    private static final String KEY_SESSION_NAME = "last_session_name";
    private static final String KEY_SESSION_DESC = "last_session_desc";
    private static final String KEY_SESSION_DURATION = "last_session_duration";

    // Default OpenRouter configuration
    public static final String DEFAULT_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    public static final String DEFAULT_API_KEY = "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";

    // Model configuration: Default is DeepSeek. User can change or leave empty in env/config.
    public static final String DEFAULT_MODEL = "deepseek/deepseek-chat";

    // Fallback model if vision is specifically requested or needed
    // User requirement: Keep empty so user can provide custom model name when needed.
    public static final String VISION_MODEL_FALLBACK = "";

    public static String getApiKey(Context context) {
        if (context == null) return DEFAULT_API_KEY;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_OPENROUTER_API_KEY, DEFAULT_API_KEY);
    }

    public static void setApiKey(Context context, String apiKey) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_OPENROUTER_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
    }

    public static String getModel(Context context) {
        if (context == null) return DEFAULT_MODEL;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_OPENROUTER_MODEL, DEFAULT_MODEL);
        if (saved == null || saved.trim().isEmpty()) {
            return DEFAULT_MODEL;
        }
        return saved.trim();
    }

    public static void setModel(Context context, String model) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_OPENROUTER_MODEL, model != null ? model.trim() : "").apply();
    }

    public static String getLastSessionName(Context context) {
        if (context == null) return "Dating / Social Feed Browsing";
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SESSION_NAME, "Dating / Social Feed Browsing");
    }

    public static void setLastSessionName(Context context, String name) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_SESSION_NAME, name).apply();
    }

    public static String getLastSessionDesc(Context context) {
        if (context == null) return "Sign up completed. Casually browse the feed, view photos, read profiles, like 1 in every 4 profiles, pause naturally.";
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SESSION_DESC, "Sign up completed. Casually browse the feed, view photos, read profiles, like 1 in every 4 profiles, pause naturally.");
    }

    public static void setLastSessionDesc(Context context, String desc) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_SESSION_DESC, desc).apply();
    }

    public static int getLastSessionDurationMinutes(Context context) {
        if (context == null) return 30;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getInt(KEY_SESSION_DURATION, 30);
    }

    public static void setLastSessionDurationMinutes(Context context, int mins) {
        if (context == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SESSION_DURATION, mins).apply();
    }
}
