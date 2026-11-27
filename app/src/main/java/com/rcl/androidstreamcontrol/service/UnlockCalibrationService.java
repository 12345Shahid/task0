package com.rcl.androidstreamcontrol.service;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.rcl.androidstreamcontrol.R;
import com.rcl.androidstreamcontrol.utils.RemoteUnlockExecutor;
import com.rcl.androidstreamcontrol.utils.UnlockCoordinateStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Two-phase lockscreen PIN calibration overlay:
 *
 * Phase A (Ready / Top-Bar Only):
 *   Shows only a compact top floating bar (height = WRAP_CONTENT) so the rest of the
 *   screen is 100% pass-through. The user can lock the screen, wake it, and swipe up
 *   naturally to expose the PIN keypad without any touch interception.
 *
 * Phase B (Recording Over Visible Keypad):
 *   When the user taps "Record PIN" (while Keyguard is locked and keypad is visible),
 *   the translucent capture canvas expands below the top bar, records exact raw screen
 *   coordinates on ACTION_DOWN, and draws numbered glowing markers for visual confirmation.
 *   Tapping "Save & Test" immediately removes the overlay, persists encrypted coordinates,
 *   and test-replays the taps onto the still-visible keypad to unlock the phone.
 */
public class UnlockCalibrationService extends Service {

    private static final String TAG = "UnlockCalibration";
    public static final String EXTRA_AUTO_LOCK = "auto_lock";
    public static final String ACTION_CALIBRATION_UPDATED = "com.rcl.androidstreamcontrol.CALIBRATION_UPDATED";
    private static final int MAX_DIGITS = 20;

    private static final int BASE_OVERLAY_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private TextView statusText;
    private TextView btnLockNow;
    private TextView btnStartRecording;
    private TextView btnClear;
    private TextView btnSaveTest;
    private TapCaptureCanvasView captureCanvas;

    private final ArrayList<float[]> capturedCoords = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFinished = false;
    private boolean isRecording = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Calibration service started.");
        startForeground(9876, buildNotification());

        final boolean autoLock = intent != null && intent.getBooleanExtra(EXTRA_AUTO_LOCK, false);

        mainHandler.post(() -> {
            isFinished = false;
            isRecording = false;
            capturedCoords.clear();
            showOverlay();

            if (autoLock) {
                mainHandler.postDelayed(() -> {
                    ControlService cs = ControlService.getSharedInstance();
                    if (cs != null && !cs.lockDeviceScreen()) {
                        Toast.makeText(getApplicationContext(),
                                "Press your Power button to lock the screen.",
                                Toast.LENGTH_LONG).show();
                    }
                }, 350);
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeOverlayImmediate();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            return getResources().getDimensionPixelSize(resId);
        }
        return dp(28);
    }

    private TextView makePillButton(Context ctx, String text, int bgColor) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(9), dp(12), dp(9));
        btn.setClickable(true);
        btn.setFocusable(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        return btn;
    }

    private void showOverlay() {
        removeOverlayImmediate();

        try {
            ControlService cs = ControlService.getSharedInstance();
            Context uiCtx;
            int windowType;
            if (cs != null) {
                uiCtx = cs;
                windowManager = (WindowManager) cs.getSystemService(WINDOW_SERVICE);
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
                Log.d(TAG, "Using TYPE_ACCESSIBILITY_OVERLAY via ControlService");
            } else {
                uiCtx = this;
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
                Log.d(TAG, "Using TYPE_APPLICATION_OVERLAY fallback");
            }

            LinearLayout root = new LinearLayout(uiCtx);
            root.setOrientation(LinearLayout.VERTICAL);

            // Compact top control card (sits cleanly below status bar, never covers PIN keypad)
            LinearLayout topBar = new LinearLayout(uiCtx);
            topBar.setOrientation(LinearLayout.VERTICAL);
            topBar.setBackgroundColor(Color.argb(235, 16, 18, 30));
            topBar.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(12));

            statusText = new TextView(uiCtx);
            statusText.setTextColor(Color.WHITE);
            statusText.setTextSize(14f);
            statusText.setGravity(Gravity.CENTER);
            statusText.setText("🔑 Step 1: Lock phone & swipe up to show your PIN keypad.\nThen tap 🔴 Record PIN below.");
            topBar.addView(statusText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout buttonRow = new LinearLayout(uiCtx);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(10);

            btnLockNow = makePillButton(uiCtx, "🔒 Lock", Color.parseColor("#37474F"));
            btnStartRecording = makePillButton(uiCtx, "🔴 Record PIN", Color.parseColor("#0288D1"));
            btnClear = makePillButton(uiCtx, "🔄 Clear", Color.parseColor("#455A64"));
            btnSaveTest = makePillButton(uiCtx, "✅ Save & Test", Color.parseColor("#2E7D32"));
            TextView btnCancel = makePillButton(uiCtx, "✖ Cancel", Color.parseColor("#B71C1C"));

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            itemLp.setMargins(dp(4), 0, dp(4), 0);

            // Phase A buttons initially visible
            boolean canAutoLock = (cs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);
            btnLockNow.setVisibility(canAutoLock ? View.VISIBLE : View.GONE);
            btnStartRecording.setVisibility(View.VISIBLE);
            btnClear.setVisibility(View.GONE);
            btnSaveTest.setVisibility(View.GONE);

            buttonRow.addView(btnLockNow, itemLp);
            buttonRow.addView(btnStartRecording, itemLp);
            buttonRow.addView(btnClear, itemLp);
            buttonRow.addView(btnSaveTest, itemLp);
            buttonRow.addView(btnCancel, itemLp);
            topBar.addView(buttonRow, rowParams);

            captureCanvas = new TapCaptureCanvasView(uiCtx);
            captureCanvas.setVisibility(View.GONE);

            root.addView(topBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            root.addView(captureCanvas, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            btnLockNow.setOnClickListener(v -> {
                ControlService activeCs = ControlService.getSharedInstance();
                if (activeCs != null) {
                    activeCs.lockDeviceScreen();
                }
            });

            btnStartRecording.setOnClickListener(v -> enterRecordingPhase());

            btnClear.setOnClickListener(v -> {
                capturedCoords.clear();
                captureCanvas.clearDots();
                updateRecordingStatus();
                Toast.makeText(getApplicationContext(), "Cleared. Tap your PIN digits again.", Toast.LENGTH_SHORT).show();
            });

            btnSaveTest.setOnClickListener(v -> {
                if (capturedCoords.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Tap your PIN digits on the keypad first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                finishAndTestCalibration();
            });

            btnCancel.setOnClickListener(v -> cancelCalibration());

            overlayView = root;
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    BASE_OVERLAY_FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            overlayParams.gravity = Gravity.TOP | Gravity.START;

            windowManager.addView(overlayView, overlayParams);
            Log.d(TAG, "Phase A top banner added cleanly.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add calibration overlay", e);
            stopSelfCleanly();
        }
    }

    private void enterRecordingPhase() {
        if (isFinished || isRecording) return;

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) {
            Toast.makeText(getApplicationContext(),
                    "Please lock your phone first and swipe up to show your PIN keypad!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        isRecording = true;
        capturedCoords.clear();
        captureCanvas.clearDots();

        btnLockNow.setVisibility(View.GONE);
        btnStartRecording.setVisibility(View.GONE);
        btnClear.setVisibility(View.VISIBLE);
        btnSaveTest.setVisibility(View.VISIBLE);
        captureCanvas.setVisibility(View.VISIBLE);
        updateRecordingStatus();

        if (overlayView != null && windowManager != null && overlayParams != null) {
            overlayParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            overlayParams.flags = BASE_OVERLAY_FLAGS;
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
                Log.d(TAG, "Expanded to Phase B full capture mode.");
            } catch (Exception e) {
                Log.e(TAG, "Failed expanding overlay to Phase B", e);
            }
        }
    }

    private void updateRecordingStatus() {
        if (statusText != null && !isFinished) {
            statusText.setText("🔴 Tap each PIN digit on keypad (include ✓/OK if needed).\n"
                    + capturedCoords.size() + " tap(s) recorded.");
        }
    }

    private void finishAndTestCalibration() {
        if (isFinished) return;
        isFinished = true;

        final Context appCtx = getApplicationContext();
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

        UnlockCoordinateStore.saveCoordinates(appCtx, pinSequence);
        Log.d(TAG, "Calibration saved: " + capturedCoords.size() + " tap coordinates.");

        Intent updateIntent = new Intent(ACTION_CALIBRATION_UPDATED);
        updateIntent.setPackage(appCtx.getPackageName());
        appCtx.sendBroadcast(updateIntent);

        // Remove overlay immediately BEFORE replaying test taps onto the real keypad
        removeOverlayImmediate();
        Toast.makeText(appCtx,
                "✅ Saved " + capturedCoords.size() + " taps! Testing unlock now...",
                Toast.LENGTH_SHORT).show();

        stopSelfCleanly();

        // Wait briefly for window removal to settle, then tap the saved coordinates on the open keypad
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                RemoteUnlockExecutor.executePinTapsOnly(appCtx, null), 350);
    }

    private void cancelCalibration() {
        if (isFinished) return;
        isFinished = true;
        removeOverlayImmediate();
        Toast.makeText(getApplicationContext(), "Remote unlock setup cancelled.", Toast.LENGTH_SHORT).show();
        stopSelfCleanly();
    }

    private void stopSelfCleanly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            @SuppressWarnings("deprecation")
            boolean removed = true;
            stopForeground(removed);
        }
        stopSelf();
    }

    private void removeOverlayImmediate() {
        if (overlayView != null && windowManager != null) {
            View viewToRemove = overlayView;
            overlayView = null;
            try {
                windowManager.removeViewImmediate(viewToRemove);
                Log.d(TAG, "Overlay removed immediately.");
            } catch (Exception e) {
                try {
                    windowManager.removeView(viewToRemove);
                    Log.d(TAG, "Overlay removed via fallback removeView.");
                } catch (Exception e2) {
                    Log.e(TAG, "Error removing overlay", e2);
                }
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
                .setContentText("Open your PIN keypad and tap Record PIN to calibrate.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Translucent capture surface shown below the top control bar in Phase B.
     * Records raw screen coordinates for dispatchGesture and renders numbered dots at local coordinates.
     */
    private class TapCaptureCanvasView extends View {
        private final ArrayList<float[]> localDots = new ArrayList<>();
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TapCaptureCanvasView(Context context) {
            super(context);
            // Non-zero opacity satisfies Android 12+ tapjacking protections while leaving keypad clearly visible
            setBackgroundColor(Color.argb(24, 0, 229, 255));

            fillPaint.setColor(Color.argb(120, 0, 180, 216));
            fillPaint.setStyle(Paint.Style.FILL);

            ringPaint.setColor(Color.rgb(0, 229, 255));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2));

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(14));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        void clearDots() {
            localDots.clear();
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isFinished || !isRecording) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (capturedCoords.size() >= MAX_DIGITS) {
                    Toast.makeText(getContext(), "Maximum digits reached (" + MAX_DIGITS + "). Tap Save & Test.", Toast.LENGTH_SHORT).show();
                    return true;
                }
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                float localX = event.getX();
                float localY = event.getY();

                capturedCoords.add(new float[]{rawX, rawY});
                localDots.add(new float[]{localX, localY});
                invalidate();
                updateRecordingStatus();
                Log.d(TAG, "Captured tap #" + capturedCoords.size() + ": raw=(" + rawX + ", " + rawY + ")");
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_UP;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = dp(18);
            float yOffset = -(textPaint.descent() + textPaint.ascent()) / 2f;
            for (int i = 0; i < localDots.size(); i++) {
                float[] pt = localDots.get(i);
                canvas.drawCircle(pt[0], pt[1], radius, fillPaint);
                canvas.drawCircle(pt[0], pt[1], radius, ringPaint);
                canvas.drawText(String.valueOf(i + 1), pt[0], pt[1] + yOffset, textPaint);
            }
        }
    }
}
