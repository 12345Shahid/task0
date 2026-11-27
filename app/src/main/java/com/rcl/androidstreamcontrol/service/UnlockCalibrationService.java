package com.rcl.androidstreamcontrol.service;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
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
 * 1-Click Autonomous Lockscreen PIN Calibration Service (v61).
 *
 * Flow:
 * 1. LOCKING: Immediately locks the screen via ControlService (GLOBAL_ACTION_LOCK_SCREEN).
 * 2. LOCKED_ASLEEP: Waits for user to wake the phone (ACTION_SCREEN_ON).
 * 3. AUTO_SWIPING: Automatically swipes up ~450ms after wake to reveal the PIN keypad.
 * 4. CAPTURING: Expands a zero-loss buffered capture overlay over the keypad.
 *    - Manual drags (>24dp) are classified as swipes, forwarded without recording.
 *    - Digit taps (<=24dp) are buffered at full typing speed with haptic + dot feedback.
 * 5. VERIFYING: 1.3s after the last tap (when >= 4 taps captured), removes the overlay
 *    and replays all captured taps onto the open PIN pad.
 * 6. Proof: Listens for ACTION_USER_PRESENT. Saves coordinates ONLY if unlocked via replay.
 *    Safely aborts after max 2 verification attempts so lockouts cannot occur.
 */
public class UnlockCalibrationService extends Service {

    private static final String TAG = "UnlockCalibration";
    public static final String EXTRA_AUTO_LOCK = "auto_lock";
    public static final String ACTION_CALIBRATION_UPDATED = "com.rcl.androidstreamcontrol.CALIBRATION_UPDATED";

    private static final int MIN_PIN_DIGITS = 4;
    private static final int MAX_DIGITS = 20;
    private static final int MAX_ATTEMPTS = 2;
    private static final long IDLE_SUBMIT_DELAY_MS = 1300L;
    private static final long VERIFY_TIMEOUT_MS = 4000L;
    private static final float SWIPE_THRESHOLD_DP = 24f;

    private static final int BASE_OVERLAY_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private enum State {
        IDLE,
        LOCKING,
        LOCKED_ASLEEP,
        AUTO_SWIPING,
        CAPTURING,
        VERIFYING,
        DONE
    }

    private State state = State.IDLE;
    private int attemptCount = 0;

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private TextView statusText;
    private TextView dotsText;
    private TapCaptureCanvasView captureCanvas;

    private final ArrayList<float[]> capturedCoords = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;
    private boolean autoWakeAfterLock = false;

    private final Runnable autoSubmitRunnable = this::triggerVerifyUnlock;
    private final Runnable verifyTimeoutRunnable = this::onVerifyTimeout;
    private final Runnable screenOnSequenceRunnable = this::runAutoSwipeAndCapture;
    private final Runnable autoWakeRunnable = this::performAutoWake;

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            Log.d(TAG, "System broadcast received: " + action + " (state=" + state + ")");

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mainHandler.post(() -> handleScreenOff());
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mainHandler.post(() -> handleScreenOn());
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mainHandler.post(() -> handleUserPresent());
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Autonomous calibration service started.");
        startForeground(9876, buildNotification());
        registerScreenReceiver();

        final boolean autoLock = intent == null || intent.getBooleanExtra(EXTRA_AUTO_LOCK, true);

        mainHandler.post(() -> {
            cancelAllTimers();
            removeOverlayImmediate();
            capturedCoords.clear();
            attemptCount = 0;
            autoWakeAfterLock = autoLock;

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (km != null && km.isKeyguardLocked()) {
                // Already locked (e.g. triggered remotely via CALIBRATION_START)
                state = State.LOCKED_ASLEEP;
                if (pm == null || pm.isInteractive()) {
                    handleScreenOn();
                }
                return;
            }

            state = State.LOCKING;
            if (autoLock) {
                mainHandler.postDelayed(() -> {
                    if (state != State.LOCKING) return;
                    ControlService cs = ControlService.getSharedInstance();
                    if (cs == null || !cs.lockDeviceScreen()) {
                        Toast.makeText(getApplicationContext(),
                                "Press Power button to lock screen, then unlock with your PIN.",
                                Toast.LENGTH_LONG).show();
                    }
                }, 250);
            } else {
                Toast.makeText(getApplicationContext(),
                        "Lock your phone now, then unlock with your PIN.",
                        Toast.LENGTH_LONG).show();
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
        cancelAllTimers();
        removeOverlayImmediate();
        unregisterScreenReceiver();
        super.onDestroy();
    }

    private void registerScreenReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterScreenReceiver() {
        if (!receiverRegistered) return;
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {
        }
        receiverRegistered = false;
    }

    private void cancelAllTimers() {
        mainHandler.removeCallbacks(autoSubmitRunnable);
        mainHandler.removeCallbacks(verifyTimeoutRunnable);
        mainHandler.removeCallbacks(screenOnSequenceRunnable);
        mainHandler.removeCallbacks(autoWakeRunnable);
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void handleScreenOff() {
        if (state == State.DONE || state == State.IDLE) return;
        mainHandler.removeCallbacks(autoSubmitRunnable);
        mainHandler.removeCallbacks(screenOnSequenceRunnable);
        mainHandler.removeCallbacks(autoWakeRunnable);

        if (state != State.VERIFYING) {
            capturedCoords.clear();
            state = State.LOCKED_ASLEEP;
            removeOverlayImmediate();
            if (autoWakeAfterLock) {
                autoWakeAfterLock = false;
                Log.d(TAG, "Screen locked — scheduling auto-wake in 700ms.");
                mainHandler.postDelayed(autoWakeRunnable, 700L);
            } else {
                Log.d(TAG, "Screen locked/off — waiting for user to wake phone.");
            }
        }
    }

    private void performAutoWake() {
        if (state != State.LOCKED_ASLEEP) return;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                @SuppressWarnings("deprecation")
                android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                        android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        android.os.PowerManager.ON_AFTER_RELEASE,
                        "AuraApp:CalibrationAutoWake"
                );
                wl.acquire(5000L);
                Log.d(TAG, "Auto-wake WakeLock acquired.");
            }
            // Safety fallback in case ACTION_SCREEN_ON was missed or raced with WakeLock
            mainHandler.postDelayed(() -> {
                if (state == State.LOCKED_ASLEEP) {
                    android.os.PowerManager pmCheck = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pmCheck == null || pmCheck.isInteractive()) {
                        handleScreenOn();
                    }
                }
            }, 550L);
        } catch (Exception e) {
            Log.e(TAG, "Auto-wake failed", e);
        }
    }

    private void handleScreenOn() {
        if (state == State.DONE || state == State.IDLE || state == State.VERIFYING) return;

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) {
            Log.w(TAG, "Screen woke unlocked (no PIN lock configured or Smart Lock active).");
            Toast.makeText(getApplicationContext(),
                    "No lockscreen PIN active — enable a PIN lock in Settings to calibrate.",
                    Toast.LENGTH_LONG).show();
            shutdownCleanly();
            return;
        }

        if (state == State.LOCKING || state == State.LOCKED_ASLEEP) {
            state = State.AUTO_SWIPING;
            capturedCoords.clear();
            mainHandler.removeCallbacks(screenOnSequenceRunnable);
            // Wait 450ms for Keyguard surface to finish rendering before auto-swiping
            mainHandler.postDelayed(screenOnSequenceRunnable, 450);
        }
    }

    private void runAutoSwipeAndCapture() {
        if (state != State.AUTO_SWIPING) return;

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) {
            handleUserPresent();
            return;
        }

        ControlService cs = ControlService.getSharedInstance();
        if (cs != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float startX = dm.widthPixels * 0.5f;
            float startY = dm.heightPixels * 0.80f;
            float endX = dm.widthPixels * 0.5f;
            float endY = dm.heightPixels * 0.25f;
            cs.performSwipe(startX, startY, endX, endY, 260);
            Log.d(TAG, "Dispatched auto-swipe to reveal PIN keypad.");
        }

        // Wait 550ms for PIN keypad transition animation before enabling capture
        mainHandler.postDelayed(() -> {
            if (state != State.AUTO_SWIPING) return;
            KeyguardManager kmAfter = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (kmAfter != null && !kmAfter.isKeyguardLocked()) {
                handleUserPresent();
                return;
            }
            state = State.CAPTURING;
            showCaptureOverlay("🔑 Tap your PIN normally (include ✓/OK if needed)");
        }, 550);
    }

    private void showCaptureOverlay(String headerMessage) {
        removeOverlayImmediate();

        try {
            ControlService cs = ControlService.getSharedInstance();
            Context uiCtx;
            int windowType;
            if (cs != null) {
                uiCtx = cs;
                windowManager = (WindowManager) cs.getSystemService(WINDOW_SERVICE);
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                uiCtx = this;
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
            }

            LinearLayout root = new LinearLayout(uiCtx);
            root.setOrientation(LinearLayout.VERTICAL);

            LinearLayout topBar = new LinearLayout(uiCtx);
            topBar.setOrientation(LinearLayout.VERTICAL);
            topBar.setBackgroundColor(Color.argb(235, 16, 18, 30));
            topBar.setPadding(dp(14), getStatusBarHeight() + dp(8), dp(14), dp(10));

            statusText = new TextView(uiCtx);
            statusText.setTextColor(Color.WHITE);
            statusText.setTextSize(13.5f);
            statusText.setGravity(Gravity.CENTER);
            statusText.setText(headerMessage);
            topBar.addView(statusText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout bottomBarRow = new LinearLayout(uiCtx);
            bottomBarRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomBarRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(8);

            dotsText = new TextView(uiCtx);
            dotsText.setTextColor(Color.parseColor("#00E5FF"));
            dotsText.setTextSize(15f);
            dotsText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            dotsText.setText("○ ○ ○ ○   Auto-unlocks 1.3s after last tap");
            bottomBarRow.addView(dotsText, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView btnClear = makePillButton(uiCtx, "🔄", Color.parseColor("#455A64"));
            TextView btnCancel = makePillButton(uiCtx, "✖", Color.parseColor("#B71C1C"));

            LinearLayout.LayoutParams smallBtnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            smallBtnLp.setMargins(dp(6), 0, 0, 0);

            bottomBarRow.addView(btnClear, smallBtnLp);
            bottomBarRow.addView(btnCancel, smallBtnLp);
            topBar.addView(bottomBarRow, rowLp);

            captureCanvas = new TapCaptureCanvasView(uiCtx);

            root.addView(topBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            root.addView(captureCanvas, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            btnClear.setOnClickListener(v -> {
                mainHandler.removeCallbacks(autoSubmitRunnable);
                capturedCoords.clear();
                captureCanvas.clearDots();
                updateDotsDisplay();
            });

            btnCancel.setOnClickListener(v -> {
                Toast.makeText(getApplicationContext(), "Remote unlock setup cancelled.", Toast.LENGTH_SHORT).show();
                shutdownCleanly();
            });

            overlayView = root;
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowType,
                    BASE_OVERLAY_FLAGS,
                    PixelFormat.TRANSLUCENT);
            overlayParams.gravity = Gravity.TOP | Gravity.START;

            windowManager.addView(overlayView, overlayParams);
            updateDotsDisplay();
            Log.d(TAG, "Autonomous capture overlay attached over keypad.");
        } catch (Exception e) {
            Log.e(TAG, "Failed attaching capture overlay", e);
            shutdownCleanly();
        }
    }

    private void updateDotsDisplay() {
        if (dotsText == null) return;
        int count = capturedCoords.size();
        if (count == 0) {
            dotsText.setText("○ ○ ○ ○   Tap PIN digits below");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int totalSlots = Math.max(MIN_PIN_DIGITS, count);
        for (int i = 0; i < totalSlots; i++) {
            sb.append(i < count ? "● " : "○ ");
        }
        if (count >= MIN_PIN_DIGITS) {
            sb.append("  Verifying in 1.3s...");
        } else {
            sb.append("  (").append(count).append(")");
        }
        dotsText.setText(sb.toString());
    }

    private void triggerVerifyUnlock() {
        if (state != State.CAPTURING || capturedCoords.size() < MIN_PIN_DIGITS) return;

        state = State.VERIFYING;
        attemptCount++;
        Log.d(TAG, "Idle submit triggered (attempt " + attemptCount + "). Replaying " + capturedCoords.size() + " taps.");

        // Detach overlay completely before replaying taps onto Keyguard
        removeOverlayImmediate();

        final Context appCtx = getApplicationContext();
        final JSONArray pinSequence = buildPinJsonArray();

        mainHandler.postDelayed(() -> {
            if (state != State.VERIFYING) return;
            // Replay directly via ControlService without saving yet — save only after ACTION_USER_PRESENT proves unlock!
            replayCapturedTapsDirectly(pinSequence);
            long dynamicTimeoutMs = (pinSequence.length() * 300L) + 3000L;
            mainHandler.postDelayed(verifyTimeoutRunnable, Math.max(VERIFY_TIMEOUT_MS, dynamicTimeoutMs));
        }, 180);
    }

    private void replayCapturedTapsDirectly(JSONArray pinSequence) {
        final long tapCadenceMs = 300L;
        for (int i = 0; i < pinSequence.length(); i++) {
            final int index = i;
            mainHandler.postDelayed(() -> {
                if (state != State.VERIFYING) return;
                try {
                    JSONObject entry = pinSequence.getJSONObject(index);
                    float absX = (float) entry.getDouble("x");
                    float absY = (float) entry.getDouble("y");
                    ControlService cs = ControlService.getSharedInstance();
                    if (cs != null) {
                        cs.performAbsoluteTap(absX, absY);
                        Log.d(TAG, "Verify tap " + (index + 1) + " at (" + absX + ", " + absY + ")");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error replaying verify tap " + index, e);
                }
            }, index * tapCadenceMs);
        }
    }

    private void onVerifyTimeout() {
        if (state != State.VERIFYING) return;

        // Double check Keyguard directly in case broadcast was delayed
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) {
            handleUserPresent();
            return;
        }

        Log.w(TAG, "Verify timeout: phone stayed locked on attempt " + attemptCount);
        capturedCoords.clear();

        if (attemptCount < MAX_ATTEMPTS) {
            state = State.CAPTURING;
            showCaptureOverlay("❌ Didn't unlock (include ✓/OK if needed). Try PIN again (" + attemptCount + "/" + MAX_ATTEMPTS + ")");
        } else {
            Toast.makeText(getApplicationContext(),
                    "Setup paused after 2 attempts to protect your lockscreen.",
                    Toast.LENGTH_LONG).show();
            shutdownCleanly();
        }
    }

    private void handleUserPresent() {
        if (state == State.DONE || state == State.IDLE) return;

        if (state == State.VERIFYING && capturedCoords.size() >= MIN_PIN_DIGITS) {
            state = State.DONE;
            mainHandler.removeCallbacks(verifyTimeoutRunnable);

            final Context appCtx = getApplicationContext();
            JSONArray pinSequence = buildPinJsonArray();
            UnlockCoordinateStore.saveCoordinates(appCtx, pinSequence);

            Intent updateIntent = new Intent(ACTION_CALIBRATION_UPDATED);
            updateIntent.setPackage(appCtx.getPackageName());
            appCtx.sendBroadcast(updateIntent);

            Toast.makeText(appCtx,
                    "✅ Remote Unlock calibrated & verified (" + capturedCoords.size() + " taps)!",
                    Toast.LENGTH_LONG).show();
            Log.d(TAG, "Calibration verified via ACTION_USER_PRESENT and saved (" + capturedCoords.size() + " taps).");
            shutdownCleanly();
        } else {
            // Unlocked via Fingerprint / Face Unlock / Smart Lock before buffered verification ran
            Log.w(TAG, "Phone unlocked externally (biometrics/manual) in state " + state + " with " + capturedCoords.size() + " taps.");
            Toast.makeText(getApplicationContext(),
                    "Unlocked via biometrics/manual — tap Setup again & unlock with your PIN digits.",
                    Toast.LENGTH_LONG).show();
            shutdownCleanly();
        }
    }

    private JSONArray buildPinJsonArray() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < capturedCoords.size(); i++) {
            float[] coord = capturedCoords.get(i);
            try {
                JSONObject obj = new JSONObject();
                obj.put("digit", i);
                obj.put("x", coord[0]);
                obj.put("y", coord[1]);
                arr.put(obj);
            } catch (Exception e) {
                Log.e(TAG, "JSON error", e);
            }
        }
        return arr;
    }

    private void setOverlayTouchable(boolean touchable) {
        if (overlayView == null || windowManager == null || overlayParams == null) return;
        if (touchable) {
            overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
        } catch (Exception e) {
            Log.e(TAG, "updateViewLayout failed", e);
        }
    }

    private void shutdownCleanly() {
        state = State.DONE;
        cancelAllTimers();
        removeOverlayImmediate();
        unregisterScreenReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            @SuppressWarnings("deprecation")
            boolean remove = true;
            stopForeground(remove);
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
                } catch (Exception ignored) {
                }
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : dp(28);
    }

    private TextView makePillButton(Context ctx, String text, int bgColor) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(7), dp(12), dp(7));
        btn.setClickable(true);
        btn.setFocusable(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(9));
        btn.setBackground(bg);
        return btn;
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
                .setContentTitle("Autonomous Unlock Setup")
                .setContentText("Unlock your phone with your PIN to complete setup.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Zero-loss buffered capture canvas.
     * Classifies drags (>24dp) as manual swipes and forwards them via ControlService,
     * while buffering stationary taps cleanly at full typing cadence.
     */
    private class TapCaptureCanvasView extends View {
        private final ArrayList<float[]> localDots = new ArrayList<>();
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float downRawX, downRawY, downLocalX, downLocalY;
        private long downTimeMs;
        private boolean isForwardingSwipe = false;

        TapCaptureCanvasView(Context context) {
            super(context);
            // Non-zero opacity passes Android 12+ untrusted-touch filter while leaving keypad clearly visible
            setBackgroundColor(Color.argb(20, 0, 229, 255));

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
            if (state != State.CAPTURING || isForwardingSwipe) return false;

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mainHandler.removeCallbacks(autoSubmitRunnable);
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downLocalX = event.getX();
                downLocalY = event.getY();
                downTimeMs = System.currentTimeMillis();
                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                return true;
            } else if (action == MotionEvent.ACTION_UP) {
                float upRawX = event.getRawX();
                float upRawY = event.getRawY();
                float distPx = (float) Math.hypot(upRawX - downRawX, upRawY - downRawY);
                float swipeThresholdPx = SWIPE_THRESHOLD_DP * getResources().getDisplayMetrics().density;

                if (distPx > swipeThresholdPx) {
                    // User performed a swipe (not a digit tap) — forward swipe to lockscreen and discard!
                    final long swipeDuration = Math.max(120L, Math.min(400L, System.currentTimeMillis() - downTimeMs));
                    Log.d(TAG, "Swipe detected (dist=" + distPx + "px) — forwarding without recording.");
                    isForwardingSwipe = true;
                    setOverlayTouchable(false);
                    mainHandler.postDelayed(() -> {
                        ControlService cs = ControlService.getSharedInstance();
                        if (cs != null) {
                            cs.performSwipe(downRawX, downRawY, upRawX, upRawY, swipeDuration);
                        }
                        mainHandler.postDelayed(() -> {
                            isForwardingSwipe = false;
                            if (state == State.CAPTURING) {
                                setOverlayTouchable(true);
                            }
                        }, swipeDuration + 180L);
                    }, 35L);
                    return true;
                }

                // Stationary tap — record coordinate with zero drop!
                if (capturedCoords.size() >= MAX_DIGITS) {
                    triggerVerifyUnlock();
                    return true;
                }

                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                capturedCoords.add(new float[]{downRawX, downRawY});
                localDots.add(new float[]{downLocalX, downLocalY});
                invalidate();
                updateDotsDisplay();
                Log.d(TAG, "Buffered tap #" + capturedCoords.size() + ": raw=(" + downRawX + ", " + downRawY + ")");

                if (capturedCoords.size() >= MAX_DIGITS) {
                    triggerVerifyUnlock();
                } else if (capturedCoords.size() >= MIN_PIN_DIGITS) {
                    mainHandler.postDelayed(autoSubmitRunnable, IDLE_SUBMIT_DELAY_MS);
                }
                return true;
            }
            return false;
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
