package com.evil.payload;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

public class Main {
    private static final String TAG = "EvilPayload";

    // Required zero-argument constructor for instantiation via reflection
    public Main() {}

    /**
     * The entry point invoked by DexLoaderManager.
     * @param context The Application context passed from the host app.
     */
    public void start(Context context) {
        Log.d(TAG, "==================================================");
        Log.d(TAG, "SUCCESS! The dynamic payload has been executed.");
        Log.d(TAG, "Code injected into: " + context.getPackageName());
        Log.d(TAG, "==================================================");

        // Show a visual toast so we can see it on the emulator
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "🚨 Dynamic Payload Executed! 🚨", Toast.LENGTH_LONG).show();
            }
        });
    }
}
