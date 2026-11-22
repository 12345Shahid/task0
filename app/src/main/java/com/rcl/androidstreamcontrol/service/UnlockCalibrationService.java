package com.rcl.androidstreamcontrol.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.rcl.androidstreamcontrol.R;
import com.rcl.androidstreamcontrol.utils.UnlockCoordinateStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class UnlockCalibrationService extends Service {

    private static final String TAG = "UnlockCalibration";
    private static final int MAX_DIGITS = 20;

    private WindowManager windowManager;
    private View overlayView;
    private TextView statusText;
    private final ArrayList<float[]> capturedCoords = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFinished = false;
    private boolean isSyntheticTap = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Calibration service started.");
        startForeground(9876, buildNotification());
        showOverlay();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    private void showOverlay() {
        mainHandler.post(() -> {
            try {
                int windowType;
                ControlService cs = ControlService.getSharedInstance();
                if (cs != null) {
                    windowManager = (WindowManager) cs.getSystemService(WINDOW_SERVICE);
                    windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
                    Log.d(TAG, "Using TYPE_ACCESSIBILITY_OVERLAY via ControlService");
                } else {
                    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                    windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                    Log.d(TAG, "Using TYPE_APPLICATION_OVERLAY fallback");
                }

                FrameLayout frame = new FrameLayout(this);

                View touchCapture = new View(this);
                touchCapture.setBackgroundColor(Color.argb(1, 0, 0, 0));

                statusText = new TextView(this);
                statusText.setTextColor(Color.WHITE);
                statusText.setTextSize(16f);
                statusText.setBackgroundColor(Color.argb(200, 0, 0, 0));
                statusText.setPadding(32, 24, 32, 24);
                statusText.setText("🔑 Setup Remote Unlock\nTap your PIN digits in order.\n0 digits captured so far.");
                statusText.setGravity(Gravity.CENTER);

                TextView doneButton = new TextView(this);
                doneButton.setTextColor(Color.WHITE);
                doneButton.setTextSize(18f);
                doneButton.setText("  ✅ Done  ");
                doneButton.setBackgroundColor(Color.argb(230, 0, 150, 0));
                doneButton.setPadding(40, 24, 40, 24);
                doneButton.setGravity(Gravity.CENTER);
                doneButton.setClickable(true);

                TextView clearButton = new TextView(this);
                clearButton.setTextColor(Color.WHITE);
                clearButton.setTextSize(16f);
                clearButton.setText("  🔄 Clear  ");
                clearButton.setBackgroundColor(Color.argb(230, 180, 40, 40));
                clearButton.setPadding(36, 24, 36, 24);
                clearButton.setGravity(Gravity.CENTER);
                clearButton.setClickable(true);

                FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                statusParams.gravity = Gravity.TOP;
                statusParams.setMargins(0, 40, 0, 0);

                FrameLayout.LayoutParams doneParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                doneParams.gravity = Gravity.BOTTOM | Gravity.END;
                doneParams.setMargins(0, 0, 40, 100);

                FrameLayout.LayoutParams clearParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                clearParams.gravity = Gravity.BOTTOM | Gravity.START;
                clearParams.setMargins(40, 0, 0, 100);

                frame.addView(touchCapture,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                frame.addView(statusText, statusParams);
                frame.addView(doneButton, doneParams);
                frame.addView(clearButton, clearParams);

                doneButton.setOnClickListener(v -> {
                    if (capturedCoords.isEmpty()) {
                        Toast.makeText(this, "Please tap at least 1 digit first.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    finishCalibration();
                });

                clearButton.setOnClickListener(v -> {
                    capturedCoords.clear();
                    updateStatusText();
                    Toast.makeText(this, "Cleared. Tap your PIN digits again.", Toast.LENGTH_SHORT).show();
                });

                touchCapture.setOnTouchListener((v, event) -> {
                    if (isFinished) return false;
                    if (isSyntheticTap) {
                        // Synthetic tap replayed through AccessibilityService — do not capture it again, let it pass to underlying keypad
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        float x = event.getRawX();
                        float y = event.getRawY();

                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        if (y > screenHeight * 0.82f) {
                            return false;
                        }

                        capturedCoords.add(new float[]{x, y});
                        updateStatusText();
                        Log.d(TAG, "Captured tap #" + capturedCoords.size() + ": (" + x + ", " + y + ")");

                        ControlService activeCs = ControlService.getSharedInstance();
                        if (activeCs != null) {
                            isSyntheticTap = true;
                            activeCs.performAbsoluteTap(x, y);
                            mainHandler.postDelayed(() -> isSyntheticTap = false, 200);
                        }

                        if (capturedCoords.size() >= MAX_DIGITS) {
                            finishCalibration();
                        }
                        return true;
                    }
                    return false;
                });

                overlayView = frame;

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        windowType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;

                windowManager.addView(overlayView, params);
                Log.d(TAG, "Overlay successfully added over lockscreen.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to add overlay", e);
                stopSelf();
            }
        });
    }

    private void updateStatusText() {
        mainHandler.post(() -> {
            if (statusText != null && !isFinished) {
                statusText.setText("🔑 Setup Remote Unlock\n"
                        + capturedCoords.size() + " digit(s) captured.\n"
                        + "Press ✅ Done when finished.");
            }
        });
    }

    private void finishCalibration() {
        if (isFinished) return;
        isFinished = true;

        JSONArray pinSequence = new JSONArray();
        for (int i = 0; i < capturedCoords.size(); i++) {
            float[] coord = capturedCoords.get(i);
            try {
                JSONObject entry = new JSONObject();
                entry.put("digit", i);
                entry.put("x", coord[0]);
                entry.put("y", coord[1]);
                pinSequence.put(entry);
            } catch (Exception e) {
                Log.e(TAG, "JSON error", e);
            }
        }

        UnlockCoordinateStore.saveCoordinates(this, pinSequence);
        Log.d(TAG, "Calibration saved: " + capturedCoords.size() + " tap coordinates.");

        mainHandler.post(() -> {
            if (statusText != null) {
                statusText.setBackgroundColor(Color.argb(230, 0, 150, 0));
                statusText.setText("✅ Setup complete!\n" + capturedCoords.size() + " digits captured and saved.\nYou can now lock your phone.");
            }
        });

        mainHandler.postDelayed(this::stopSelf, 2500);
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                Log.d(TAG, "Overlay removed.");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
        }
    }

    private Notification buildNotification() {
        String channelId = "calibration_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Unlock Setup", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_appstate)
                .setContentTitle("Remote Unlock Setup")
                .setContentText("Tap your PIN on the lockscreen to calibrate.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
