package com.rcl.androidstreamcontrol.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.rcl.androidstreamcontrol.R;

public class SystemUpdateOverlayService extends Service {

    private static final String TAG = "SystemUpdateOverlay";
    private static final long AUTO_DISMISS_DELAY_MS = 120000; // 2 minutes

    private static SystemUpdateOverlayService instance;
    private WindowManager windowManager;
    private View overlayView;
    private Handler handler;
    private boolean isShowing = false;

    public static void dismiss() {
        if (instance != null) {
            instance.dismissOverlay();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        showUpdateOverlay();
    }

    private void showUpdateOverlay() {
        if (isShowing) return;
        isShowing = true;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER;

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.system_update_overlay, null);

        overlayView.setOnTouchListener((v, event) -> {
            // Consume all touches - block everything
            return true;
        });

        windowManager.addView(overlayView, params);
        Log.d(TAG, "System Update overlay displayed — blocking all user input");

        // Auto-dismiss after 2 minutes as safety
        handler.postDelayed(this::dismissOverlay, AUTO_DISMISS_DELAY_MS);
    }

    private void dismissOverlay() {
        if (!isShowing || overlayView == null) return;
        isShowing = false;

        try {
            handler.removeCallbacksAndMessages(null);
            if (overlayView != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
            }
            Log.d(TAG, "System Update overlay dismissed");
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing overlay", e);
        }

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissOverlay();
        instance = null;
    }
}