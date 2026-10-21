package com.rcl.androidstreamcontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.rcl.androidstreamcontrol.utils.SupabaseClient;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button continueButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if already logged in
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedEmail = prefs.getString("email", null);
        String savedPassword = prefs.getString("password", null);

        if (savedEmail != null && savedPassword != null) {
            startMainActivity();
            return;
        }

        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        continueButton = findViewById(R.id.continue_button);
        progressBar = findViewById(R.id.login_progress);

        continueButton.setOnClickListener(v -> performLogin());
        
    }

    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedDeviceName = prefs.getString("deviceName", null);
        if (savedDeviceName != null) {
            // Use existing device name for this installation
            proceedWithLogin(email, password, savedDeviceName);
        } else {
            // Generate sequential device name by counting existing devices
            generateSequentialDeviceName(email, password);
        }
    }

    private void generateSequentialDeviceName(String email, String password) {
        new Thread(() -> {
            String deviceName;
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(
                    "https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/devices?select=id"
                ).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");
                conn.setRequestProperty("Prefer", "count=exact");
                conn.getResponseCode();
                // Count via response header or body
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();
                org.json.JSONArray arr = new org.json.JSONArray(response.toString());
                int count = arr.length();
                deviceName = "Device" + (50 + count);
            } catch (Exception e) {
                android.util.Log.e("LoginActivity", "Failed to count devices, using fallback", e);
                deviceName = "Device" + (System.currentTimeMillis() % 1000);
            }
            final String finalName = deviceName;
            runOnUiThread(() -> proceedWithLogin(email, password, finalName));
        }).start();
    }

    private void proceedWithLogin(String email, String password, String deviceName) {

        progressBar.setVisibility(View.VISIBLE);
        continueButton.setEnabled(false);

        SupabaseClient.signUpOrLogin(email, password, deviceName, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String userId, String token) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    // Save credentials
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    prefs.edit()
                        .putString("email", email)
                        .putString("password", password)
                        .putString("deviceName", deviceName)
                        .apply();
                        
                    SharedPreferences portalPrefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
                    portalPrefs.edit()
                        .putString("supabase_uid", userId)
                        .putString("supabase_token", token)
                        .putString("device_name", deviceName)
                        .putString("tenant_id", email) // Email is tenant id
                        .putString("tenant_password", password)
                        .apply();

                    Toast.makeText(LoginActivity.this, "Welcome!", Toast.LENGTH_SHORT).show();
                    startMainActivity();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    continueButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, RemoteAndroid.class);
        startActivity(intent);
        finish();
    }
}
