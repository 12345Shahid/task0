package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;

import com.rcl.androidstreamcontrol.service.ControlService;

import org.json.JSONArray;
import org.json.JSONObject;

public class RemoteUnlockExecutor {

    private static final String TAG = "RemoteUnlockExecutor";
    private static final int TAP_DELAY_MS = 320;
    private static final int WAKE_SETTLE_MS = 450;
    private static final int SWIPE_SETTLE_MS = 650;

    /**
     * Replays the stored PIN using the saved coordinates.
     * 1. Wakes up the screen if asleep (and waits for display panel activation).
     * 2. Swipes up to reveal the lockscreen PIN keypad.
     * 3. Calls ControlService.performAbsoluteTap() for each digit in order.
     */
    public static void executeStoredUnlock(Context context, Runnable callback) {
        JSONArray pinSequence = UnlockCoordinateStore.loadCoordinates(context);
        if (pinSequence == null || pinSequence.length() == 0) {
            Log.e(TAG, "No calibration data found. Cannot unlock remotely.");
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        boolean wokeScreen = false;

        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "AuraApp:RemoteUnlockWake"
                );
                wakeLock.acquire(12000);
                wokeScreen = true;
                Log.d(TAG, "Acquired WakeLock to turn on display");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error waking screen", e);
        }

        // Wait for display panel to wake before dispatching the swipe-up gesture
        long preSwipeDelay = wokeScreen ? WAKE_SETTLE_MS : 50;
        handler.postDelayed(() -> {
            ControlService cs = ControlService.getSharedInstance();
            if (cs != null) {
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                float startX = dm.widthPixels * 0.5f;
                float startY = dm.heightPixels * 0.80f;
                float endX = dm.widthPixels * 0.5f;
                float endY = dm.heightPixels * 0.25f;
                cs.performSwipe(startX, startY, endX, endY, 280);
                Log.d(TAG, "Dispatched swipe-up gesture to reveal lockscreen PIN keypad");
            }

            handler.postDelayed(() -> replayCoordinates(pinSequence, handler, callback), SWIPE_SETTLE_MS);
        }, preSwipeDelay);
    }

    /**
     * Replays stored PIN taps directly without waking or swiping up.
     * Used right after calibration when the user's PIN keypad is already open on screen.
     */
    public static void executePinTapsOnly(Context context, Runnable callback) {
        JSONArray pinSequence = UnlockCoordinateStore.loadCoordinates(context);
        if (pinSequence == null || pinSequence.length() == 0) {
            Log.e(TAG, "No calibration data found.");
            return;
        }
        replayCoordinates(pinSequence, new Handler(Looper.getMainLooper()), callback);
    }

    private static void replayCoordinates(JSONArray pinSequence, Handler handler, Runnable callback) {
        Log.d(TAG, "Starting unlock tap replay. " + pinSequence.length() + " taps to replay.");
        for (int i = 0; i < pinSequence.length(); i++) {
            final int index = i;
            handler.postDelayed(() -> {
                try {
                    JSONObject entry = pinSequence.getJSONObject(index);
                    float absX = (float) entry.getDouble("x");
                    float absY = (float) entry.getDouble("y");

                    ControlService activeCs = ControlService.getSharedInstance();
                    if (activeCs != null) {
                        activeCs.performAbsoluteTap(absX, absY);
                        Log.d(TAG, "Replayed tap " + (index + 1) + " at (" + absX + ", " + absY + ")");
                    } else {
                        Log.e(TAG, "ControlService not running! Cannot tap.");
                    }

                    if (index == pinSequence.length() - 1) {
                        handler.postDelayed(() -> {
                            Log.d(TAG, "Remote unlock sequence completed.");
                            if (callback != null) callback.run();
                        }, 500);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error replaying tap " + index, e);
                }
            }, (long) index * TAP_DELAY_MS);
        }
    }

    /**
     * Quick check — returns true if the device has stored unlock coordinates.
     */
    public static boolean isReady(Context context) {
        return UnlockCoordinateStore.isCalibrated(context);
    }
}
