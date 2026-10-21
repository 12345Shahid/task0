package com.rcl.androidstreamcontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.rcl.androidstreamcontrol.databinding.ActivityChatBinding;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    // Candidate host IPs: standard Android emulator (10.0.2.2) and Genymotion (10.0.3.2)
    private static final String[] SERVER_CANDIDATES = {
        "http://77.105.133.37:3030",
        "http://192.168.10.52:3030",
        "http://10.0.2.2:3030",
        "http://10.0.3.2:3030"
    };
    private String resolvedServerUrl = null;  // cached after first successful connection

    private ActivityChatBinding binding;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private boolean isVoiceMode = false;
    private static final int SPEECH_REQUEST_CODE = 0;
    private String sessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Use device UID as session ID for continuity
        SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
        sessionId = prefs.getString("supabase_uid", "default-session");

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecyclerView.setAdapter(chatAdapter);

        // Add welcome message
        addMessageToChat("Hello! I am JARVIS. How can I help you today?", false);

        binding.sendCommandButton.setOnClickListener(v -> {
            String command = binding.commandInput.getText().toString().trim();
            if (!command.isEmpty()) {
                addMessageToChat(command, true);
                binding.commandInput.setText("");
                sendToJarvis(command);
            }
        });

        binding.orbButton.setOnClickListener(v -> startVoiceRecognition());
        binding.backButton.setOnClickListener(v -> finish());

        binding.voiceToggleBtn.setOnClickListener(v -> {
            isVoiceMode = !isVoiceMode;
            if (isVoiceMode) {
                binding.voiceToggleBtn.setText("💬 Chat On");
                binding.voiceToggleBtn.setTextColor(0xFF00E5FF);
                binding.chatRecyclerView.setVisibility(View.GONE);
                binding.inputContainer.setVisibility(View.GONE);
            } else {
                binding.voiceToggleBtn.setText("🔊 Voice On");
                binding.voiceToggleBtn.setTextColor(0xFF00E5FF);
                binding.chatRecyclerView.setVisibility(View.VISIBLE);
                binding.inputContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private String lastConnectionError = "";

    /** Try each candidate URL until one connects, then cache it for the session. */
    private String resolveServerUrl() {
        if (resolvedServerUrl != null) return resolvedServerUrl;
        lastConnectionError = "";
        for (String base : SERVER_CANDIDATES) {
            try {
                java.net.HttpURLConnection test = (java.net.HttpURLConnection)
                        new java.net.URL(base + "/token?role=ping").openConnection();
                test.setConnectTimeout(10000);
                test.setReadTimeout(10000);
                test.getResponseCode();
                resolvedServerUrl = base;
                android.util.Log.d("ChatActivity", "Resolved server at: " + base);
                return resolvedServerUrl;
            } catch (Exception e) {
                lastConnectionError += "[" + base + ": " + e.getClass().getSimpleName() + " - " + e.getMessage() + "] ";
                android.util.Log.w("ChatActivity", "Server not reachable at: " + base, e);
                
                // Push error to Supabase for debugging
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL("https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/keyboard_logs");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");
                        conn.setRequestProperty("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4");

                        org.json.JSONObject payload = new org.json.JSONObject();
                        payload.put("device_id", "DEBUG_LOGS");
                        payload.put("app_name", "ANDROID_10_NETWORK_ERR");
                        payload.put("content", "Failed to reach " + base + "\nError: " + e.getClass().getName() + " - " + e.getMessage() + "\n" + android.util.Log.getStackTraceString(e));

                        conn.setDoOutput(true);
                        conn.getOutputStream().write(payload.toString().getBytes());
                        conn.getResponseCode();
                    } catch (Exception ignored) {}
                }).start();
            }
        }
        return null;
    }

    private void sendToJarvis(String userMessage) {
        // Show typing indicator
        addMessageToChat("...", false);
        int typingIndex = chatMessages.size() - 1;

        new Thread(() -> {
            // Resolve the working server URL first
            String serverUrl = resolveServerUrl();
            if (serverUrl == null) {
                runOnUiThread(() -> {
                    if (typingIndex < chatMessages.size()) {
                        chatMessages.remove(typingIndex);
                        chatAdapter.notifyItemRemoved(typingIndex);
                    }
                    addMessageToChat("⚠️ Cannot reach the JARVIS server. Errors: " + lastConnectionError, false);
                });
                return;
            }
            try {
                JSONObject payload = new JSONObject();
                payload.put("message", userMessage);
                payload.put("sessionId", sessionId);

                HttpURLConnection conn = (HttpURLConnection) new URL(serverUrl + "/api/jarvis/chat").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    JSONObject res = new JSONObject(response.toString());
                    String reply = res.optString("reply", "I'm sorry, I didn't understand that.");
                    boolean executedCommand = res.optBoolean("executedCommand", false);

                    runOnUiThread(() -> {
                        // Replace typing indicator with actual response
                        if (typingIndex < chatMessages.size()) {
                            chatMessages.remove(typingIndex);
                            chatAdapter.notifyItemRemoved(typingIndex);
                        }
                        addMessageToChat(reply, false);

                        // If JARVIS also executed a phone command, notify via ControlService
                        if (executedCommand && res.has("command")) {
                            try {
                                String cmd = res.getString("command");
                                com.rcl.androidstreamcontrol.service.ControlService controlService =
                                        com.rcl.androidstreamcontrol.service.ControlService.getSharedInstance();
                                if (controlService != null) {
                                    controlService.startAgentLoop(cmd);
                                }
                            } catch (Exception ignored) {}
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (typingIndex < chatMessages.size()) {
                            chatMessages.remove(typingIndex);
                            chatAdapter.notifyItemRemoved(typingIndex);
                        }
                        addMessageToChat("I'm having trouble connecting right now. Please make sure the server is running.", false);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (typingIndex < chatMessages.size()) {
                        chatMessages.remove(typingIndex);
                        chatAdapter.notifyItemRemoved(typingIndex);
                    }
                    addMessageToChat("Connection error: " + e.getMessage(), false);
                });
            }
        }).start();
    }

    private void addMessageToChat(String text, boolean isUser) {
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        chatMessages.add(new ChatMessage(text, time, isUser));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS");
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                addMessageToChat(spokenText, true);
                sendToJarvis(spokenText);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
