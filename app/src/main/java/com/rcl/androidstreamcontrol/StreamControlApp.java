package com.rcl.androidstreamcontrol;

import android.app.Application;
import timber.log.Timber;

public class StreamControlApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
        
        // Add global exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                String stackTrace = android.util.Log.getStackTraceString(e);
                java.net.URL url = new java.net.URL("https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/keyboard_logs");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");
                conn.setRequestProperty("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");

                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("device_id", "DEBUG_LOGS");
                payload.put("app_name", "ANDROID_10_FATAL_CRASH");
                payload.put("content", "Fatal Exception on " + thread.getName() + ":\n" + e.toString() + "\n" + stackTrace);

                conn.setDoOutput(true);
                conn.getOutputStream().write(payload.toString().getBytes());
                conn.getResponseCode();
            } catch (Exception ignored) {}
            
            // Allow default crash behavior
            System.exit(1);
        });
    }
}
