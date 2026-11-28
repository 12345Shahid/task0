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
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.rcl.androidstreamcontrol.R;
import com.rcl.androidstreamcontrol.utils.UnlockCoordinateStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 1-Click Invisible Autonomous Lockscreen PIN Calibration Service (v63).
 *
 * Flow:
 * 1. LOCKING: Immediately locks the screen via ControlService (GLOBAL_ACTION_LOCK_SCREEN).
 * 2. LOCKED_ASLEEP: 750ms after screen turns off, auto-wakes the screen (WAKE_LOCK).
 * 3. AUTO_SWIPING: 800ms after wake, executes a curved 72% upward swipe (0.86H -> 0.14H, 210ms)
 *    to pass Keyguard FalsingManager and bring up the PIN keypad automatically.
 * 4. CAPTURING: Attaches a completely invisible full-screen capture surface (no banner,
 *    no glowing markers, no cyan tint):
 *    - Drags > 24dp are forwarded as manual swipes without recording.
 *    - Stationary taps are buffered at full typing speed with zero dropped digits.
 * 5. VERIFYING: 1.3s after the last tap (when >= 4 taps buffered), removes the overlay
 *    and replays the coordinates onto the open PIN pad.
 * 6. Proof: Saves encrypted coordinates ONLY when ACTION_USER_PRESENT proves unlock.
 */
public class UnlockCalibrationService extends Service {

    private static final String TAG = "UnlockCalibration";
    public static final String EXTRA_AUTO_LOCK = "auto_lock";
    public static final String ACTION_CALIBRATION_UPDATED = "com.rcl.androidstreamcontrol.CALIBRATION_UPDATED";

    private static final int MIN_PIN_DIGITS = 4;
    private static final int MAX_DIGITS = 20;
    private static final int MAX_ATTEMPTS = 2;
    private static final long AUTO_WAKE_DELAY_MS = 750L;
    private static final long WAKE_TO_SWIPE_DELAY_MS = 800L;
    private static final long SWIPE_SETTLE_DELAY_MS = 600L;
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
            Log.d(TAG, "System broadcast: " + action + " (state=" + state + ")");

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
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (km != null && km.isKeyguardLocked()) {
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
                Log.d(TAG, "Screen locked — scheduling auto-wake in " + AUTO_WAKE_DELAY_MS + "ms.");
                mainHandler.postDelayed(autoWakeRunnable, AUTO_WAKE_DELAY_MS);
            }
        }
    }

    private void performAutoWake() {
        if (state != State.LOCKED_ASLEEP) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "AuraApp:CalibrationAutoWake"
                );
                wl.acquire(6000L);
                Log.d(TAG, "Auto-wake WakeLock acquired.");
            }
            mainHandler.postDelayed(() -> {
                if (state == State.LOCKED_ASLEEP) {
                    PowerManager pmCheck = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pmCheck == null || pmCheck.isInteractive()) {
                        handleScreenOn();
                    }
                }
            }, 600L);
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
            // Wait 800ms for Keyguard & FalsingManager initialization after wake
            mainHandler.postDelayed(screenOnSequenceRunnable, WAKE_TO_SWIPE_DELAY_MS);
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
            float startY = dm.heightPixels * 0.86f;
            float endX = dm.widthPixels * 0.5f;
            float endY = dm.heightPixels * 0.14f;
            cs.performSwipe(startX, startY, endX, endY, 210);
            Log.d(TAG, "Dispatched 72% curved auto-swipe to reveal PIN keypad.");
        }

        mainHandler.postDelayed(() -> {
            if (state != State.AUTO_SWIPING) return;
            KeyguardManager kmAfter = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (kmAfter != null && !kmAfter.isKeyguardLocked()) {
                handleUserPresent();
                return;
            }
            state = State.CAPTURING;
            showInvisibleCaptureOverlay();
        }, SWIPE_SETTLE_DELAY_MS);
    }

    private void showInvisibleCaptureOverlay() {
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

            // Pure invisible full-screen glass — zero top banner, zero cyan tint, zero visual markers
            overlayView = new InvisibleTapCaptureView(uiCtx);
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowType,
                    BASE_OVERLAY_FLAGS,
                    PixelFormat.TRANSLUCENT);
            overlayParams.gravity = Gravity.TOP | Gravity.START;

            windowManager.addView(overlayView, overlayParams);
            Log.d(TAG, "Invisible capture overlay attached.");
        } catch (Exception e) {
            Log.e(TAG, "Failed attaching capture overlay", e);
            shutdownCleanly();
        }
    }

    private void triggerVerifyUnlock() {
        if (state != State.CAPTURING || capturedCoords.size() < MIN_PIN_DIGITS) return;

        state = State.VERIFYING;
        attemptCount++;
        Log.d(TAG, "Idle submit triggered (attempt " + attemptCount + "). Replaying " + capturedCoords.size() + " taps.");

        removeOverlayImmediate();

        final JSONArray pinSequence = buildPinJsonArray();
        mainHandler.postDelayed(() -> {
            if (state != State.VERIFYING) return;
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

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) {
            handleUserPresent();
            return;
        }

        Log.w(TAG, "Verify timeout: phone stayed locked on attempt " + attemptCount);
        capturedCoords.clear();

        if (attemptCount < MAX_ATTEMPTS) {
            state = State.CAPTURING;
            Toast.makeText(getApplicationContext(),
                    "Didn't unlock — try PIN again (include ✓/OK if needed).",
                    Toast.LENGTH_LONG).show();
            showInvisibleCaptureOverlay();
        } else {
            Toast.makeText(getApplicationContext(),
                    "Setup stopped after 2 attempts to protect your lockscreen.",
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
            Log.d(TAG, "Calibration verified via ACTION_USER_PRESENT and saved.");
            shutdownCleanly();
        } else {
            Log.w(TAG, "Phone unlocked externally in state " + state + " with " + capturedCoords.size() + " taps.");
            Toast.makeText(getApplicationContext(),
                    "Unlocked without PIN verification — tap Setup again & enter PIN.",
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
                .setContentTitle("Unlock Setup")
                .setContentText("Unlock your phone with your PIN to finish.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Completely invisible touch capture glass (no tint, no circles, no banners).
     * Forwards drags (>24dp) transparently as swipes and buffers stationary PIN taps cleanly.
     */
    private class InvisibleTapCaptureView extends View {
        private float downRawX, downRawY;
        private long downTimeMs;
        private boolean isForwardingSwipe = false;

        InvisibleTapCaptureView(Context context) {
            super(context);
            // ~0.8% opacity black: invisible to the eye, satisfies Android 12+ non-zero alpha touch rule
            setBackgroundColor(Color.argb(2, 0, 0, 0));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (state != State.CAPTURING || isForwardingSwipe) return false;

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mainHandler.removeCallbacks(autoSubmitRunnable);
                downRawX = event.getRawX();
                downRawY = event.getRawY();
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
                    final long swipeDuration = Math.max(120L, Math.min(350L, System.currentTimeMillis() - downTimeMs));
                    Log.d(TAG, "Swipe detected (" + distPx + "px) — forwarding without recording.");
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

                if (capturedCoords.size() >= MAX_DIGITS) {
                    triggerVerifyUnlock();
                    return true;
                }

                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                capturedCoords.add(new float[]{downRawX, downRawY});
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
    }
}
