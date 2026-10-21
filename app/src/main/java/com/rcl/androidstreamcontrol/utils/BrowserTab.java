package com.rcl.androidstreamcontrol.utils;

import android.webkit.WebView;
import org.json.JSONObject;

/**
 * Represents an isolated multi-profile browser tab container.
 * Each tab encapsulates its own:
 * - Dedicated WebView instance
 * - Hardware Device Profile (Brand, Model, Screen dimensions, WebGL parameters)
 * - Canvas & WebGL Noise Randomization Seed
 * - Timezone and Region
 * - Dedicated Proxy Sticky Session Tag and Geo State
 */
public class BrowserTab {
    public String id;
    public String title;
    public String url;
    public WebView webView;
    public JSONObject profile;
    public boolean isNative;
    public double noiseSeed;
    public String timezone;
    public String proxySessionTag;
    public String geoState;
    public String geoCity;
    public String savedIp;

    public BrowserTab(
            String id,
            WebView webView,
            JSONObject profile,
            boolean isNative,
            double noiseSeed,
            String timezone,
            String proxySessionTag
    ) {
        this.id = id;
        this.webView = webView;
        this.profile = profile;
        this.isNative = isNative;
        this.noiseSeed = noiseSeed;
        this.timezone = (timezone != null && !timezone.isEmpty()) ? timezone : "America/New_York";
        this.proxySessionTag = (proxySessionTag != null && !proxySessionTag.isEmpty()) ? proxySessionTag : "active";
        this.geoState = "US";
        this.geoCity = "";
        this.savedIp = "";
        this.title = isNative ? "Native Device" : (profile != null ? profile.optString("name", "New Tab") : "New Tab");
        this.url = "https://www.google.com";
    }

    public String getDisplayName() {
        if (isNative) return "⚡ Native Device";
        if (profile != null) {
            String name = profile.optString("name", "Tab");
            // Abbreviate common long device names for sleek tab pills
            if (name.length() > 16) {
                return name.substring(0, 14) + "…";
            }
            return name;
        }
        return "Tab";
    }
}
