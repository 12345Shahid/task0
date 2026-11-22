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
    private static final int TAP_DELAY_MS = 300;

    /**
     * Replays the stored PIN using the saved coordinates.
     * 1. Wakes up the screen if asleep.
     * 2. Swipes up to reveal the lockscreen PIN keypad.
     * 3. Calls ControlService.performAbsoluteTap() for each digit in order.
     *
     * @param context   Application context
     * @param callback  Called when done: callback.run() is called on main thread.
     *                  Pass null if you don't need a completion callback.
     */
    public static void executeStoredUnlock(Context context, Runnable callback) {
        JSONArray pinSequence = UnlockCoordinateStore.loadCoordinates(context);

        if (pinSequence == null || pinSequence.length() == 0) {
            Log.e(TAG, "No calibration data found. Cannot unlock remotely.");
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());

        // Step 1: Wake up the screen if it is asleep
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
                wakeLock.acquire(10000);
                Log.d(TAG, "Acquired WakeLock to turn on display");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error waking screen", e);
        }

        // Step 2: Swipe up on the lockscreen to reveal the PIN keypad
        ControlService cs = ControlService.getSharedInstance();
        if (cs != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float startX = dm.widthPixels * 0.5f;
            float startY = dm.heightPixels * 0.82f;
            float endX = dm.widthPixels * 0.5f;
            float endY = dm.heightPixels * 0.20f;
            cs.performSwipe(startX, startY, endX, endY, 250);
            Log.d(TAG, "Dispatched swipe-up gesture to reveal lockscreen PIN keypad");
        }

        // Step 3: Wait 650ms for the PIN keypad animation to settle, then replay taps
        handler.postDelayed(() -> {
            Log.d(TAG, "Starting remote unlock tap replay. " + pinSequence.length() + " taps to replay.");

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
        }, 650);
    }

    /**
     * Quick check — returns true if the device has stored unlock coordinates.
     */
    public static boolean isReady(Context context) {
        return UnlockCoordinateStore.isCalibrated(context);
    }
}
