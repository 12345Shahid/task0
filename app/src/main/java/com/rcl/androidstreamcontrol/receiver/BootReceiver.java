package com.rcl.androidstreamcontrol.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.rcl.androidstreamcontrol.service.ControlService;
import com.rcl.androidstreamcontrol.service.FollowerService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction()))) {
            
            Log.d(TAG, "Device booted! Restarting persistent services...");
            
            // Note: ControlService is an AccessibilityService. 
            // It will be auto-started by the OS if the user had it enabled.
            // However, we can start our background synchronization/FollowerService
            try {
                Intent serviceIntent = new Intent(context, FollowerService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                Log.d(TAG, "Successfully triggered FollowerService on boot.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start services on boot", e);
            }
        }
    }
}
