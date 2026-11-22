package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.rcl.androidstreamcontrol.service.ControlService;

import org.json.JSONArray;
import org.json.JSONObject;

public class RemoteUnlockExecutor {

    private static final String TAG = "RemoteUnlockExecutor";
    private static final int TAP_DELAY_MS = 300;

    /**
     * Replays the stored PIN using the saved coordinates.
     * Calls ControlService.performAbsoluteTap() for each digit in order.
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
        Log.d(TAG, "Starting remote unlock. " + pinSequence.length() + " taps to replay.");

        for (int i = 0; i < pinSequence.length(); i++) {
            final int index = i;
            handler.postDelayed(() -> {
                try {
                    JSONObject entry = pinSequence.getJSONObject(index);
                    float absX = (float) entry.getDouble("x");
                    float absY = (float) entry.getDouble("y");

                    ControlService cs = ControlService.getSharedInstance();
                    if (cs != null) {
                        cs.performAbsoluteTap(absX, absY);
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
