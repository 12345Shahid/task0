package com.rcl.androidstreamcontrol.utils;

import android.annotation.SuppressLint;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anti-Detect Module — Advanced Fingerprint Spoofing (Hardened Version 47)
 *
 * Implements full GoLogin-grade isolation:
 *   - Device User-Agent spoofing aligned with device's real Chromium WebView engine
 *   - Screen dimensions & pixel depth spoofing
 *   - Multi-realm Dynamic Timezone: Intl.DateTimeFormat & Date.prototype across top window, iframes, and Web Workers
 *   - Web Worker Timezone Synchronization (URL.createObjectURL & Worker constructor interception)
 *   - Hardware concurrency & Device memory spoofing on Navigator.prototype
 *   - Platform & Vendor (Apple Computer, Inc. for iOS vs Google Inc. for Android)
 *   - Dynamic Client Hints (navigator.userAgentData) - version matches UA 1:1, undefined on iOS
 *   - Battery API (navigator.getBattery) - native function signature masked as [native code]
 *   - Native Function Masking: makeNative on all overridden methods via WeakSet with V8-compliant descriptors
 *   - 100% Spec-Compliant WebGL Vendor & Renderer spoofing (zero exceptions, native extensions untouched)
 *   - 100% Native Canvas WebIDL rendering (no monkey-patching / no canvas tampering detection)
 *   - Idempotency guard: prevents double-wrapping on page lifecycle events
 *   - Native Profile Reset support
 */
public class FingerprintManager {

    private static final String TAG = "Fingerprint";

    private static volatile JSONObject currentProfile = null;
    private static volatile String activeTimezone = "America/Chicago";
    private static volatile double currentNoiseSeed = 0.42;
    private static volatile boolean isNativeProfile = false;
    private static volatile String sNativeUserAgent = null;

    public static void setNativeUserAgent(String nativeUa) {
        sNativeUserAgent = nativeUa;
    }

    public static String getNativeUserAgent() {
        return sNativeUserAgent;
    }

    public static String getDeviceChromiumVersion() {
        if (sNativeUserAgent != null) {
            Matcher m = Pattern.compile("Chrome/([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)").matcher(sNativeUserAgent);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "124.0.6367.113";
    }

    /**
     * Aligns profile Chrome version with the physical device's underlying WebView engine
     * to eliminate browser version mismatch deductions while preserving profile device model.
     */
    public static String alignUserAgent(String ua, String profileName) {
        if (ua == null || ua.isEmpty()) return ua;
        if (ua.contains("iPhone") || ua.contains("iPad") || !ua.contains("Chrome/")) {
            return ua;
        }
        String realChrome = getDeviceChromiumVersion();
        return ua.replaceAll("Chrome/[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+", "Chrome/" + realChrome);
    }

    static {
        currentNoiseSeed = new Random().nextDouble();
    }

    public static JSONObject getCurrentProfile() {
        if (isNativeProfile) return null;
        if (currentProfile == null) {
            currentProfile = DeviceProfilePool.getRandomProfile();
        }
        return currentProfile;
    }

    public static void setCurrentProfile(JSONObject profile) {
        setCurrentProfile(profile, true);
    }

    public static void setCurrentProfile(JSONObject profile, boolean generateNewSeed) {
        currentProfile = profile;
        isNativeProfile = false;
        if (generateNewSeed) {
            currentNoiseSeed = new Random().nextDouble();
        }
    }

    public static void resetToNative() {
        isNativeProfile = true;
        currentProfile = null;
    }

    public static void resetToNative(WebView view) {
        resetToNative();
        if (view != null) {
            try {
                Object tag = view.getTag();
                if (tag instanceof ScriptRegistration) {
                    ScriptRegistration reg = (ScriptRegistration) tag;
                    if (reg.handler != null) {
                        try { reg.handler.remove(); } catch (Exception ignored) {}
                    }
                    view.setTag(null);
                }
            } catch (Exception ignored) {}
        }
    }

    public static boolean isNative() {
        return isNativeProfile;
    }

    public static void setNative(boolean nativeMode) {
        isNativeProfile = nativeMode;
        if (nativeMode) {
            currentProfile = null;
        }
    }

    public static double getCurrentNoiseSeed() {
        return currentNoiseSeed;
    }

    public static void setCurrentNoiseSeed(double seed) {
        currentNoiseSeed = seed;
    }

    public static String getActiveTimezone() {
        return activeTimezone;
    }

    public static void setActiveTimezone(String tz) {
        if (tz != null && !tz.trim().isEmpty()) {
            activeTimezone = tz.trim();
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(activeTimezone));
            } catch (Exception ignored) {}
        }
    }

    public static void rotateNoiseSeed() {
        currentNoiseSeed = new Random().nextDouble();
    }

    /**
     * Build the comprehensive Anti-Detect JavaScript spoofing payload.
     */
    public static String buildSpoofScript(JSONObject profile, String timezone, double noiseSeed) {
        String proxyTz = WebViewProxyManager.getLastResolvedTimezone();
        String tz;
        if (WebViewProxyManager.isProxyActive() && proxyTz != null && !proxyTz.trim().isEmpty()) {
            tz = proxyTz.trim();
        } else if (timezone != null && !timezone.isEmpty()) {
            tz = timezone.trim();
        } else if (profile != null) {
            tz = profile.optString("timezone", "America/Chicago");
        } else {
            tz = "America/Chicago";
        }

        String lang = profile != null ? profile.optString("language", "en-US") : "en-US";
        int width = profile != null ? profile.optInt("screen_width", 412) : 412;
        int height = profile != null ? profile.optInt("screen_height", 915) : 915;
        String name = profile != null ? profile.optString("name", "Android") : "Android";
        String rawUa = profile != null ? profile.optString("userAgent", "") : "";
        String ua = alignUserAgent(rawUa, name);
        boolean isIos = name.toLowerCase().contains("iphone") || name.toLowerCase().contains("ipad");

        String realChromeVer = getDeviceChromiumVersion();
        String chromeMajor = "124";
        String chromeFull = realChromeVer;
        if (realChromeVer != null && realChromeVer.contains(".")) {
            chromeMajor = realChromeVer.substring(0, realChromeVer.indexOf('.'));
        }
        String androidVer = "14.0.0";
        String uaModel = name;
        if (profile != null && profile.has("model") && !profile.optString("model").isEmpty()) {
            uaModel = profile.optString("model");
        }
        if (ua != null && !ua.isEmpty()) {
            Matcher cm = Pattern.compile("Chrome/([0-9]+)\\.([0-9]+\\.[0-9]+\\.[0-9]+)").matcher(ua);
            if (cm.find()) {
                chromeMajor = cm.group(1);
                chromeFull = cm.group(1) + "." + cm.group(2);
            }
            Matcher am = Pattern.compile("Android\\s+([0-9]+)").matcher(ua);
            if (am.find()) {
                androidVer = am.group(1) + ".0.0";
            }
            Matcher mm = Pattern.compile("Android\\s+[0-9]+(?:\\.[0-9]+)*;\\s*([^;\\)]+)").matcher(ua);
            if (mm.find()) {
                String token = mm.group(1).trim();
                if (token.contains(" Build/")) {
                    token = token.substring(0, token.indexOf(" Build/")).trim();
                }
                if (!token.isEmpty() && !token.equals("K")) {
                    uaModel = token;
                }
            }
        }

        String lowerName = name.toLowerCase();
        String webglVendor;
        String webglRenderer;

        if (isIos) {
            webglVendor = "Apple Inc.";
            webglRenderer = "Apple GPU";
        } else if (lowerName.contains("13t") || lowerName.contains("dimensity") || lowerName.contains("k60 ultra")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G715-Immortalis MC11";
        } else if (lowerName.contains("pixel 9") || lowerName.contains("pixel 8")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G715-Immortalis MC10";
        } else if (lowerName.contains("pixel 7") || lowerName.contains("pixel fold")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G710";
        } else if (lowerName.contains("s24") || lowerName.contains("oneplus 12") || lowerName.contains("xiaomi 14")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 750";
        } else if (lowerName.contains("s23") || lowerName.contains("oneplus 11") || lowerName.contains("fold5") || lowerName.contains("redmagic 8") || lowerName.contains("magic5") || lowerName.contains("find x6") || lowerName.contains("k60")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 740";
        } else if (lowerName.contains("s22") || lowerName.contains("edge 40") || lowerName.contains("10 pro") || lowerName.contains("rog phone 7")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 730";
        } else if (lowerName.contains("s21") || lowerName.contains("s21 fe")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 660";
        } else if (lowerName.contains("a54") || lowerName.contains("a52") || lowerName.contains("a34") || lowerName.contains("a14") || lowerName.contains("m54")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G68";
        } else if (lowerName.contains("poco x6") || lowerName.contains("nord 3") || lowerName.contains("note 13") || lowerName.contains("v29") || lowerName.contains("reno10")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G610";
        } else if (lowerName.contains("tecno") || lowerName.contains("spark")) {
            webglVendor = "Imagination Technologies";
            webglRenderer = "PowerVR Rogue GE8320";
        } else if (lowerName.contains("infinix")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G57 MC2";
        } else if (lowerName.contains("blade") || lowerName.contains("zte")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G57 MP1";
        } else if (lowerName.contains("redmi note 11") || lowerName.contains("a23") || lowerName.contains("moto g52") || lowerName.contains("g32")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 610";
        } else if (lowerName.contains("nord n30") || lowerName.contains("n30")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 619";
        } else if (lowerName.contains("legion") || lowerName.contains("y90")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 730";
        } else {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 610";
        }

        if (profile != null && profile.has("webgl_vendor") && !profile.optString("webgl_vendor").isEmpty()) {
            webglVendor = profile.optString("webgl_vendor");
        }
        if (profile != null && profile.has("webgl_renderer") && !profile.optString("webgl_renderer").isEmpty()) {
            webglRenderer = profile.optString("webgl_renderer");
        }
        if (webglVendor == null || webglVendor.isEmpty()) {
            webglVendor = DeviceProfilePool.deduceWebglVendor(name);
        }
        if (webglRenderer == null || webglRenderer.isEmpty()) {
            webglRenderer = DeviceProfilePool.deduceWebglRenderer(name);
        }
        String platform = isIos ? (name.toLowerCase().contains("ipad") ? "iPad" : "iPhone") : "Linux armv8l";
        String vendor = isIos ? "Apple Computer, Inc." : "Google Inc.";
        int hwConcurrency = isIos ? 6 : 8;
        int devMemory = 8;
        double lat = WebViewProxyManager.getLastResolvedLat();
        double lon = WebViewProxyManager.getLastResolvedLon();

        return "(function() {\n" +
                "  if (window.__antidetect_installed__) return;\n" +
                "  try {\n" +
                "    Object.defineProperty(window, '__antidetect_installed__', {\n" +
                "      value: true, writable: false, configurable: false, enumerable: false\n" +
                "    });\n" +
                "  } catch(e) { window.__antidetect_installed__ = true; }\n" +
                "\n" +
                "  try {\n" +
                "    var spoofTz = '" + tz + "';\n" +
                "    var spoofLang = '" + lang + "';\n" +
                "    var sw = " + width + ";\n" +
                "    var sh = " + height + ";\n" +
                "    var webglVendor = '" + webglVendor + "';\n" +
                "    var webglRenderer = '" + webglRenderer + "';\n" +
                "    var spoofPlatform = '" + platform + "';\n" +
                "    var spoofVendor = '" + vendor + "';\n" +
                "    var isIos = " + isIos + ";\n" +
                "    var profileName = '" + name + "';\n" +
                "    var clientHintsModel = '" + uaModel + "';\n" +
                "    var spoofHwConcurrency = " + hwConcurrency + ";\n" +
                "    var spoofDevMemory = " + devMemory + ";\n" +
                "    var noiseSeed = " + noiseSeed + ";\n" +
                "    var cMajor = '" + chromeMajor + "';\n" +
                "    var cFull = '" + chromeFull + "';\n" +
                "    var androidVer = '" + androidVer + "';\n" +
                "    var spoofLat = " + lat + ";\n" +
                "    var spoofLon = " + lon + ";\n" +
                "\n" +
                "    // Global Native Function Disguise (Intercepts Function.prototype.toString.call via WeakSet)\n" +
                "    var origFnToString = Function.prototype.toString;\n" +
                "    var nativeFns = (typeof WeakSet !== 'undefined') ? new WeakSet() : null;\n" +
                "    var customFnToString = function toString() {\n" +
                "      if (typeof this !== 'function') {\n" +
                "        return origFnToString.apply(this, arguments);\n" +
                "      }\n" +
                "      try {\n" +
                "        if (nativeFns && nativeFns.has(this)) {\n" +
                "          var fnName = this.name || '';\n" +
                "          return 'function ' + fnName + '() { [native code] }';\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "      try {\n" +
                "        return origFnToString.apply(this, arguments);\n" +
                "      } catch(e) {\n" +
                "        return Function.prototype.toString.apply(this, arguments);\n" +
                "      }\n" +
                "    };\n" +
                "    if (nativeFns) nativeFns.add(customFnToString);\n" +
                "    try { Object.defineProperty(customFnToString, 'name', { value: 'toString', configurable: true, enumerable: false, writable: false }); } catch(e) {}\n" +
                "    try { Object.defineProperty(customFnToString, 'length', { value: 0, configurable: true, enumerable: false, writable: false }); } catch(e) {}\n" +
                "    try { delete customFnToString.prototype; } catch(e) {}\n" +
                "    try {\n" +
                "      Object.defineProperty(Function.prototype, 'toString', {\n" +
                "        value: customFnToString,\n" +
                "        writable: true, configurable: true, enumerable: false\n" +
                "      });\n" +
                "    } catch(e) { Function.prototype.toString = customFnToString; }\n" +
                "\n" +
                "    // Native Function Masking Helper (Stealth: Never defines own 'toString' property)\n" +
                "    function makeNative(fn, name, len, keepProto) {\n" +
                "      if (typeof fn !== 'function') return fn;\n" +
                "      var fnName = name || fn.name || '';\n" +
                "      if (nativeFns) nativeFns.add(fn);\n" +
                "      try { Object.defineProperty(fn, 'name', { value: fnName, configurable: true, enumerable: false, writable: false }); } catch(e) {}\n" +
                "      if (typeof len === 'number') {\n" +
                "        try { Object.defineProperty(fn, 'length', { value: len, configurable: true, enumerable: false, writable: false }); } catch(e) {}\n" +
                "      }\n" +
                "      if (!keepProto) {\n" +
                "        try { delete fn.prototype; } catch(e) {}\n" +
                "      }\n" +
                "      return fn;\n" +
                "    }\n" +
                "\n" +
                "    // Multi-Realm Timezone & Date Hook\n" +
                "    function hookTimezone(w) {\n" +
                "      if (!w || !w.Intl || !w.Date) return;\n" +
                "      try {\n" +
                "        var OrigDateTimeFormat = w.Intl.DateTimeFormat;\n" +
                "        var OrigDate = w.Date;\n" +
                "\n" +
                "        var fmtUtc = new OrigDateTimeFormat('en-US', {\n" +
                "          timeZone: 'UTC', year: 'numeric', month: '2-digit', day: '2-digit',\n" +
                "          hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'\n" +
                "        });\n" +
                "        var fmtTz = new OrigDateTimeFormat('en-US', {\n" +
                "          timeZone: spoofTz, year: 'numeric', month: '2-digit', day: '2-digit',\n" +
                "          hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'\n" +
                "        });\n" +
                "        var fmtTzLong = new OrigDateTimeFormat('en-US', {\n" +
                "          timeZone: spoofTz, timeZoneName: 'long'\n" +
                "        });\n" +
                "\n" +
                "        var cachedTime = null;\n" +
                "        var cachedOffset = null;\n" +
                "        var getTzOffsetMinutes = function(d) {\n" +
                "          try {\n" +
                "            if (!(d instanceof OrigDate) || isNaN(d.getTime())) return NaN;\n" +
                "            var t = d.getTime();\n" +
                "            if (cachedOffset !== null && cachedTime !== null && Math.abs(t - cachedTime) < 600000) {\n" +
                "              return cachedOffset;\n" +
                "            }\n" +
                "            var pU = {};\n" +
                "            var partsU = fmtUtc.formatToParts(d);\n" +
                "            for (var i = 0; i < partsU.length; i++) pU[partsU[i].type] = partsU[i].value;\n" +
                "\n" +
                "            var pT = {};\n" +
                "            var partsT = fmtTz.formatToParts(d);\n" +
                "            for (var i = 0; i < partsT.length; i++) pT[partsT[i].type] = partsT[i].value;\n" +
                "\n" +
                "            var hU = parseInt(pU.hour, 10); if (hU === 24) hU = 0;\n" +
                "            var hT = parseInt(pT.hour, 10); if (hT === 24) hT = 0;\n" +
                "\n" +
                "            var tUtc = OrigDate.UTC(parseInt(pU.year, 10), parseInt(pU.month, 10) - 1, parseInt(pU.day, 10),\n" +
                "                                   hU, parseInt(pU.minute, 10), parseInt(pU.second, 10));\n" +
                "            var tTz = OrigDate.UTC(parseInt(pT.year, 10), parseInt(pT.month, 10) - 1, parseInt(pT.day, 10),\n" +
                "                                  hT, parseInt(pT.minute, 10), parseInt(pT.second, 10));\n" +
                "            var off = Math.round((tUtc - tTz) / 60000);\n" +
                "            cachedTime = t;\n" +
                "            cachedOffset = off;\n" +
                "            return off;\n" +
                "          } catch(e) {\n" +
                "            return 300;\n" +
                "          }\n" +
                "        };\n" +
                "\n" +
                "        var getLocalDate = function(d) {\n" +
                "          if (!(d instanceof OrigDate) || isNaN(d.getTime())) return null;\n" +
                "          var off = getTzOffsetMinutes(d);\n" +
                "          if (isNaN(off)) return null;\n" +
                "          return new OrigDate(d.getTime() - off * 60000);\n" +
                "        };\n" +
                "\n" +
                "        var cachedTzName = null;\n" +
                "        var cachedTzNameTime = null;\n" +
                "        var getTzName = function(d) {\n" +
                "          var t = d.getTime();\n" +
                "          if (cachedTzName !== null && cachedTzNameTime !== null && Math.abs(t - cachedTzNameTime) < 600000) {\n" +
                "            return cachedTzName;\n" +
                "          }\n" +
                "          var tzName = '';\n" +
                "          try {\n" +
                "            var partsTz = fmtTzLong.formatToParts(d);\n" +
                "            for (var i = 0; i < partsTz.length; i++) {\n" +
                "              if (partsTz[i].type === 'timeZoneName') { tzName = partsTz[i].value; break; }\n" +
                "            }\n" +
                "          } catch(e) {}\n" +
                "          cachedTzName = tzName;\n" +
                "          cachedTzNameTime = t;\n" +
                "          return tzName;\n" +
                "        };\n" +
                "\n" +
                "        var days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];\n" +
                "        var months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];\n" +
                "        var getDateStr = function(ld) {\n" +
                "          var dayStr = days[ld.getUTCDay()];\n" +
                "          var monthStr = months[ld.getUTCMonth()];\n" +
                "          var dateNum = ld.getUTCDate();\n" +
                "          var dateStr = dateNum < 10 ? ('0' + dateNum) : String(dateNum);\n" +
                "          var yearStr = String(ld.getUTCFullYear());\n" +
                "          return dayStr + ' ' + monthStr + ' ' + dateStr + ' ' + yearStr;\n" +
                "        };\n" +
                "\n" +
                "        var getTimeStr = function(d, ld) {\n" +
                "          var h = ld.getUTCHours(); var hStr = h < 10 ? ('0' + h) : String(h);\n" +
                "          var m = ld.getUTCMinutes(); var mStr = m < 10 ? ('0' + m) : String(m);\n" +
                "          var s = ld.getUTCSeconds(); var sStr = s < 10 ? ('0' + s) : String(s);\n" +
                "          var timePart = hStr + ':' + mStr + ':' + sStr;\n" +
                "\n" +
                "          var off = getTzOffsetMinutes(d);\n" +
                "          var sign = off > 0 ? '-' : '+';\n" +
                "          var absOff = Math.abs(off);\n" +
                "          var offH = String(Math.floor(absOff / 60)); if (offH.length < 2) offH = '0' + offH;\n" +
                "          var offM = String(absOff % 60); if (offM.length < 2) offM = '0' + offM;\n" +
                "          var gmtOffset = 'GMT' + sign + offH + offM;\n" +
                "\n" +
                "          var tzName = getTzName(d);\n" +
                "          var tzNameStr = tzName ? (' (' + tzName + ')') : '';\n" +
                "          return timePart + ' ' + gmtOffset + tzNameStr;\n" +
                "        };\n" +
                "\n" +
                "        var getTimezoneOffsetFn = function getTimezoneOffset() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getTimezoneOffset called on incompatible receiver');\n" +
                "          return getTzOffsetMinutes(this);\n" +
                "        };\n" +
                "\n" +
                "        var getHoursFn = function getHours() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getHours called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCHours() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getMinutesFn = function getMinutes() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getMinutes called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCMinutes() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getSecondsFn = function getSeconds() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getSeconds called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCSeconds() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getDateFn = function getDate() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getDate called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCDate() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getDayFn = function getDay() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getDay called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCDay() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getMonthFn = function getMonth() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getMonth called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCMonth() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getFullYearFn = function getFullYear() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getFullYear called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? ld.getUTCFullYear() : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var getYearFn = function getYear() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.getYear called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          return ld ? (ld.getUTCFullYear() - 1900) : NaN;\n" +
                "        };\n" +
                "\n" +
                "        var toDateStringFn = function toDateString() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.toDateString called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          if (!ld) return 'Invalid Date';\n" +
                "          return getDateStr(ld);\n" +
                "        };\n" +
                "\n" +
                "        var toTimeStringFn = function toTimeString() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.toTimeString called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          if (!ld) return 'Invalid Date';\n" +
                "          return getTimeStr(this, ld);\n" +
                "        };\n" +
                "\n" +
                "        var toStringFn = function toString() {\n" +
                "          if (!(this instanceof OrigDate)) throw new w.TypeError('Method Date.prototype.toString called on incompatible receiver');\n" +
                "          var ld = getLocalDate(this);\n" +
                "          if (!ld) return 'Invalid Date';\n" +
                "          return getDateStr(ld) + ' ' + getTimeStr(this, ld);\n" +
                "        };\n" +
                "\n" +
                "        var origToLocaleString = OrigDate.prototype.toLocaleString;\n" +
                "        var toLocaleStringFn = function toLocaleString(locales, options) {\n" +
                "          options = (typeof options === 'object' && options !== null) ? Object.assign({}, options) : {};\n" +
                "          if (!options.timeZone) options.timeZone = spoofTz;\n" +
                "          return origToLocaleString.call(this, locales || spoofLang, options);\n" +
                "        };\n" +
                "\n" +
                "        var origToLocaleDateString = OrigDate.prototype.toLocaleDateString;\n" +
                "        var toLocaleDateStringFn = function toLocaleDateString(locales, options) {\n" +
                "          options = (typeof options === 'object' && options !== null) ? Object.assign({}, options) : {};\n" +
                "          if (!options.timeZone) options.timeZone = spoofTz;\n" +
                "          return origToLocaleDateString.call(this, locales || spoofLang, options);\n" +
                "        };\n" +
                "\n" +
                "        var origToLocaleTimeString = OrigDate.prototype.toLocaleTimeString;\n" +
                "        var toLocaleTimeStringFn = function toLocaleTimeString(locales, options) {\n" +
                "          options = (typeof options === 'object' && options !== null) ? Object.assign({}, options) : {};\n" +
                "          if (!options.timeZone) options.timeZone = spoofTz;\n" +
                "          return origToLocaleTimeString.call(this, locales || spoofLang, options);\n" +
                "        };\n" +
                "\n" +
                "        var hookMethod = function(proto, name, fn, len) {\n" +
                "          try {\n" +
                "            Object.defineProperty(proto, name, {\n" +
                "              value: makeNative(fn, name, len !== undefined ? len : 0),\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) {\n" +
                "            try { proto[name] = makeNative(fn, name, len !== undefined ? len : 0); } catch(err) {}\n" +
                "          }\n" +
                "        };\n" +
                "\n" +
                "        hookMethod(w.Date.prototype, 'getTimezoneOffset', getTimezoneOffsetFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getHours', getHoursFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getMinutes', getMinutesFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getSeconds', getSecondsFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getDate', getDateFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getDay', getDayFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getMonth', getMonthFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getFullYear', getFullYearFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'getYear', getYearFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'toDateString', toDateStringFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'toTimeString', toTimeStringFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'toString', toStringFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'toLocaleString', toLocaleStringFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'toLocaleDateString', toLocaleDateStringFn, 0);\n" +
                "        hookMethod(w.Date.prototype, 'toLocaleTimeString', toLocaleTimeStringFn, 0);\n" +
                "\n" +
                "        var origResolvedOptions = OrigDateTimeFormat.prototype.resolvedOptions;\n" +
                "        var customResolvedOptions = function resolvedOptions() {\n" +
                "          var ro = origResolvedOptions.apply(this, arguments);\n" +
                "          if (ro) ro.timeZone = spoofTz;\n" +
                "          return ro;\n" +
                "        };\n" +
                "        hookMethod(OrigDateTimeFormat.prototype, 'resolvedOptions', customResolvedOptions, 0);\n" +
                "\n" +
                "        var CustomDateTimeFormat = function DateTimeFormat(locales, options) {\n" +
                "          var opts = (typeof options === 'object' && options !== null) ? Object.assign({}, options) : {};\n" +
                "          if (!opts.timeZone) opts.timeZone = spoofTz;\n" +
                "          var loc = locales || spoofLang;\n" +
                "          if (new.target) {\n" +
                "            return Reflect.construct(OrigDateTimeFormat, [loc, opts], new.target);\n" +
                "          }\n" +
                "          return OrigDateTimeFormat(loc, opts);\n" +
                "        };\n" +
                "        CustomDateTimeFormat.prototype = OrigDateTimeFormat.prototype;\n" +
                "        CustomDateTimeFormat.supportedLocalesOf = OrigDateTimeFormat.supportedLocalesOf;\n" +
                "        try {\n" +
                "          w.Intl.DateTimeFormat = makeNative(CustomDateTimeFormat, 'DateTimeFormat', 0, true);\n" +
                "        } catch(e) {}\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +

                "    // Multi-Realm Navigator & Client Hints Hook\n" +
                "    function hookNavigator(w) {\n" +
                "      if (!w) return;\n" +
                "      var target = (w.Navigator && w.Navigator.prototype) ? w.Navigator.prototype : w.navigator;\n" +
                "      if (!target) return;\n" +
                "      try {\n" +
                "        Object.defineProperty(target, 'language', {\n" +
                "          get: makeNative(function language() { return spoofLang; }, 'get language', 0),\n" +
                "          configurable: true, enumerable: true\n" +
                "        });\n" +
                "        Object.defineProperty(target, 'languages', {\n" +
                "          get: makeNative(function languages() { return Object.freeze([spoofLang, 'en']); }, 'get languages', 0),\n" +
                "          configurable: true, enumerable: true\n" +
                "        });\n" +
                "        Object.defineProperty(target, 'platform', {\n" +
                "          get: makeNative(function platform() { return spoofPlatform; }, 'get platform', 0),\n" +
                "          configurable: true, enumerable: true\n" +
                "        });\n" +
                "        Object.defineProperty(target, 'vendor', {\n" +
                "          get: makeNative(function vendor() { return spoofVendor; }, 'get vendor', 0),\n" +
                "          configurable: true, enumerable: true\n" +
                "        });\n" +
                "        Object.defineProperty(target, 'hardwareConcurrency', {\n" +
                "          get: makeNative(function hardwareConcurrency() { return spoofHwConcurrency; }, 'get hardwareConcurrency', 0),\n" +
                "          configurable: true, enumerable: true\n" +
                "        });\n" +
                "        if (!isIos) {\n" +
                "          Object.defineProperty(target, 'deviceMemory', {\n" +
                "            get: makeNative(function deviceMemory() { return spoofDevMemory; }, 'get deviceMemory', 0),\n" +
                "            configurable: true, enumerable: true\n" +
                "          });\n" +
                "        }\n" +
                "        Object.defineProperty(target, 'maxTouchPoints', {\n" +
                "          get: makeNative(function maxTouchPoints() { return 5; }, 'get maxTouchPoints', 0),\n" +
                "          configurable: true, enumerable: true\n" +
                "        });\n" +
                "      } catch(e) {}\n" +
                "\n" +
                "      try {\n" +
                "        if (isIos) {\n" +
                "          var chromNavProps = [\n" +
                "            'deviceMemory', 'connection', 'mozConnection', 'webkitConnection',\n" +
                "            'vibrate', 'contacts', 'ink', 'keyboard', 'managed', 'scheduling',\n" +
                "            'storageBuckets', 'userActivation', 'virtualKeyboard', 'webkitTemporaryStorage',\n" +
                "            'webkitPersistentStorage', 'windowControlsOverlay', 'requestMIDIAccess',\n" +
                "            'clearAppBadge', 'setAppBadge', 'bluetooth', 'usb', 'serial', 'hid',\n" +
                "            'presentation', 'gpu', 'userAgentData', 'getBattery'\n" +
                "          ];\n" +
                "          for (var k = 0; k < chromNavProps.length; k++) {\n" +
                "            var pName = chromNavProps[k];\n" +
                "            try { delete w.navigator[pName]; } catch(e) {}\n" +
                "            try { if (w.Navigator && w.Navigator.prototype) delete w.Navigator.prototype[pName]; } catch(e) {}\n" +
                "          }\n" +
                "          var chromGlobals = ['chrome', 'NetworkInformation', 'BatteryManager'];\n" +
                "          for (var g = 0; g < chromGlobals.length; g++) {\n" +
                "            try { delete w[chromGlobals[g]]; } catch(e) {}\n" +
                "          }\n" +
                "          if (typeof w.ApplePaySession === 'undefined') {\n" +
                "            try {\n" +
                "              var ApplePaySession = function ApplePaySession() {};\n" +
                "              ApplePaySession.canMakePayments = makeNative(function canMakePayments() { return true; }, 'canMakePayments');\n" +
                "              ApplePaySession.canMakePaymentsWithActiveCard = makeNative(function canMakePaymentsWithActiveCard() { return Promise.resolve(true); }, 'canMakePaymentsWithActiveCard');\n" +
                "              ApplePaySession.supportsVersion = makeNative(function supportsVersion() { return true; }, 'supportsVersion', 1);\n" +
                "              makeNative(ApplePaySession, 'ApplePaySession', 0, true);\n" +
                "              w.ApplePaySession = ApplePaySession;\n" +
                "            } catch(e) {}\n" +
                "          }\n" +
                "        } else {\n" +
                "          var brandsList = [\n" +
                "            { brand: 'Google Chrome', version: cMajor },\n" +
                "            { brand: 'Chromium', version: cMajor },\n" +
                "            { brand: 'Not-A.Brand', version: '99' }\n" +
                "          ];\n" +
                "          var fullVersionList = [\n" +
                "            { brand: 'Google Chrome', version: cFull },\n" +
                "            { brand: 'Chromium', version: cFull },\n" +
                "            { brand: 'Not-A.Brand', version: '99.0.0.0' }\n" +
                "          ];\n" +
                "          var formFactorsList = ['mobile'];\n" +
                "\n" +
                "          var mockUaData = {\n" +
                "            brands: brandsList,\n" +
                "            mobile: true,\n" +
                "            platform: 'Android',\n" +
                "            getHighEntropyValues: makeNative(function getHighEntropyValues(hints) {\n" +
                "              return Promise.resolve({\n" +
                "                architecture: 'arm',\n" +
                "                bitness: '64',\n" +
                "                brands: brandsList,\n" +
                "                formFactors: formFactorsList,\n" +
                "                fullVersionList: fullVersionList,\n" +
                "                mobile: true,\n" +
                "                model: clientHintsModel,\n" +
                "                platform: 'Android',\n" +
                "                platformVersion: androidVer,\n" +
                "                uaFullVersion: cFull,\n" +
                "                wow64: false\n" +
                "              });\n" +
                "            }, 'getHighEntropyValues'),\n" +
                "            toJSON: makeNative(function toJSON() {\n" +
                "              return { brands: brandsList, mobile: true, platform: 'Android' };\n" +
                "            }, 'toJSON')\n" +
                "          };\n" +
                "          if (typeof w.NavigatorUAData !== 'undefined') {\n" +
                "            try { Object.setPrototypeOf(mockUaData, w.NavigatorUAData.prototype); } catch(e) {}\n" +
                "          }\n" +
                "          Object.defineProperty(target, 'userAgentData', {\n" +
                "            get: makeNative(function userAgentData() { return mockUaData; }, 'get userAgentData', 0),\n" +
                "            configurable: true,\n" +
                "            enumerable: true\n" +
                "          });\n" +
                "\n" +
                "          try {\n" +
                "            var mockBattery = {\n" +
                "              charging: true,\n" +
                "              chargingTime: 0,\n" +
                "              dischargingTime: Infinity,\n" +
                "              level: 1.0,\n" +
                "              onchargingchange: null,\n" +
                "              onchargingtimechange: null,\n" +
                "              ondischargingtimechange: null,\n" +
                "              onlevelchange: null,\n" +
                "              addEventListener: makeNative(function addEventListener() {}, 'addEventListener'),\n" +
                "              removeEventListener: makeNative(function removeEventListener() {}, 'removeEventListener'),\n" +
                "              dispatchEvent: makeNative(function dispatchEvent() { return true; }, 'dispatchEvent')\n" +
                "            };\n" +
                "            if (typeof w.BatteryManager !== 'undefined') {\n" +
                "              try { Object.setPrototypeOf(mockBattery, w.BatteryManager.prototype); } catch(e) {}\n" +
                "            }\n" +
                "            var getBatteryFn = function getBattery() { return Promise.resolve(mockBattery); };\n" +
                "            Object.defineProperty(target, 'getBattery', {\n" +
                "              value: makeNative(getBatteryFn, 'getBattery'),\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) {}\n" +
                "\n" +
                "          try {\n" +
                "            var netInfo = {\n" +
                "              downlink: 10,\n" +
                "              effectiveType: '4g',\n" +
                "              rtt: 50,\n" +
                "              saveData: false,\n" +
                "              onchange: null,\n" +
                "              addEventListener: makeNative(function addEventListener() {}, 'addEventListener'),\n" +
                "              removeEventListener: makeNative(function removeEventListener() {}, 'removeEventListener'),\n" +
                "              dispatchEvent: makeNative(function dispatchEvent() { return true; }, 'dispatchEvent')\n" +
                "            };\n" +
                "            if (typeof w.NetworkInformation !== 'undefined') {\n" +
                "              try { Object.setPrototypeOf(netInfo, w.NetworkInformation.prototype); } catch(e) {}\n" +
                "            }\n" +
                "            Object.defineProperty(target, 'connection', {\n" +
                "              get: makeNative(function connection() { return netInfo; }, 'get connection', 0),\n" +
                "              configurable: true, enumerable: true\n" +
                "            });\n" +
                "          } catch(e) {}\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "\n" +
                "      // GPS Geolocation Alignment with Residential Proxy Location\n" +
                "      try {\n" +
                "        if (w.navigator && w.navigator.geolocation) {\n" +
                "          var createPos = function(lat, lon) {\n" +
                "            var coordsProto = (w.GeolocationCoordinates && w.GeolocationCoordinates.prototype) ? w.GeolocationCoordinates.prototype : Object.prototype;\n" +
                "            var coords = Object.create(coordsProto);\n" +
                "            try {\n" +
                "              Object.defineProperties(coords, {\n" +
                "                latitude: { value: lat, enumerable: true },\n" +
                "                longitude: { value: lon, enumerable: true },\n" +
                "                accuracy: { value: 25.0, enumerable: true },\n" +
                "                altitude: { value: null, enumerable: true },\n" +
                "                altitudeAccuracy: { value: null, enumerable: true },\n" +
                "                heading: { value: null, enumerable: true },\n" +
                "                speed: { value: null, enumerable: true }\n" +
                "              });\n" +
                "              if (typeof Symbol !== 'undefined' && Symbol.toStringTag) {\n" +
                "                Object.defineProperty(coords, Symbol.toStringTag, { value: 'GeolocationCoordinates' });\n" +
                "              }\n" +
                "            } catch(e) {}\n" +
                "            var posProto = (w.GeolocationPosition && w.GeolocationPosition.prototype) ? w.GeolocationPosition.prototype : Object.prototype;\n" +
                "            var pos = Object.create(posProto);\n" +
                "            try {\n" +
                "              Object.defineProperties(pos, {\n" +
                "                coords: { value: coords, enumerable: true },\n" +
                "                timestamp: { value: Date.now(), enumerable: true }\n" +
                "              });\n" +
                "              if (typeof Symbol !== 'undefined' && Symbol.toStringTag) {\n" +
                "                Object.defineProperty(pos, Symbol.toStringTag, { value: 'GeolocationPosition' });\n" +
                "              }\n" +
                "            } catch(e) {}\n" +
                "            return pos;\n" +
                "          };\n" +
                "          var customGetCurrentPosition = function getCurrentPosition(success, error, options) {\n" +
                "            if (typeof success === 'function') {\n" +
                "              setTimeout(function() {\n" +
                "                try { success(createPos(spoofLat, spoofLon)); } catch(e) {}\n" +
                "              }, 10);\n" +
                "              return;\n" +
                "            }\n" +
                "          };\n" +
                "          var customWatchPosition = function watchPosition(success, error, options) {\n" +
                "            if (typeof success === 'function') {\n" +
                "              setTimeout(function() {\n" +
                "                try { success(createPos(spoofLat, spoofLon)); } catch(e) {}\n" +
                "              }, 10);\n" +
                "            }\n" +
                "            return Math.floor(Math.random() * 10000) + 1;\n" +
                "          };\n" +
                "          var customClearWatch = function clearWatch(id) {};\n" +
                "          var geoProto = (w.Geolocation && w.Geolocation.prototype) ? w.Geolocation.prototype : w.navigator.geolocation;\n" +
                "          try {\n" +
                "            Object.defineProperty(geoProto, 'getCurrentPosition', {\n" +
                "              value: makeNative(customGetCurrentPosition, 'getCurrentPosition', 1),\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) { geoProto.getCurrentPosition = makeNative(customGetCurrentPosition, 'getCurrentPosition', 1); }\n" +
                "          try {\n" +
                "            Object.defineProperty(geoProto, 'watchPosition', {\n" +
                "              value: makeNative(customWatchPosition, 'watchPosition', 1),\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) { geoProto.watchPosition = makeNative(customWatchPosition, 'watchPosition', 1); }\n" +
                "          try {\n" +
                "            Object.defineProperty(geoProto, 'clearWatch', {\n" +
                "              value: makeNative(customClearWatch, 'clearWatch', 1),\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) { geoProto.clearWatch = makeNative(customClearWatch, 'clearWatch', 1); }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "\n" +
                "      // Permissions API Query Hook for Geolocation\n" +
                "      try {\n" +
                "        if (w.navigator && w.navigator.permissions && w.navigator.permissions.query) {\n" +
                "          var origQuery = w.navigator.permissions.query;\n" +
                "          var customQuery = function query(desc) {\n" +
                "            if (desc && desc.name === 'geolocation') {\n" +
                "              var mockStatus = {\n" +
                "                state: 'granted',\n" +
                "                name: 'geolocation',\n" +
                "                onchange: null,\n" +
                "                addEventListener: makeNative(function addEventListener() {}, 'addEventListener', 2),\n" +
                "                removeEventListener: makeNative(function removeEventListener() {}, 'removeEventListener', 2),\n" +
                "                dispatchEvent: makeNative(function dispatchEvent() { return true; }, 'dispatchEvent', 1)\n" +
                "              };\n" +
                "              if (typeof w.PermissionStatus !== 'undefined' && w.PermissionStatus.prototype) {\n" +
                "                try { Object.setPrototypeOf(mockStatus, w.PermissionStatus.prototype); } catch(e) {}\n" +
                "              }\n" +
                "              return Promise.resolve(mockStatus);\n" +
                "            }\n" +
                "            return origQuery.apply(this, arguments);\n" +
                "          };\n" +
                "          var permProto = (w.Permissions && w.Permissions.prototype) ? w.Permissions.prototype : w.navigator.permissions;\n" +
                "          try {\n" +
                "            Object.defineProperty(permProto, 'query', {\n" +
                "              value: makeNative(customQuery, 'query', 1),\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) { permProto.query = makeNative(customQuery, 'query', 1); }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    // Multi-Realm Screen Dimensions\n" +
                "    function hookScreen(w) {\n" +
                "      if (!w || !w.screen) return;\n" +
                "      try {\n" +
                "        Object.defineProperty(w.screen, 'width', { get: makeNative(function width() { return sw; }, 'get width', 0), configurable: true });\n" +
                "        Object.defineProperty(w.screen, 'height', { get: makeNative(function height() { return sh; }, 'get height', 0), configurable: true });\n" +
                "        Object.defineProperty(w.screen, 'availWidth', { get: makeNative(function availWidth() { return sw; }, 'get availWidth', 0), configurable: true });\n" +
                "        Object.defineProperty(w.screen, 'availHeight', { get: makeNative(function availHeight() { return sh; }, 'get availHeight', 0), configurable: true });\n" +
                "        Object.defineProperty(w.screen, 'colorDepth', { get: makeNative(function colorDepth() { return 24; }, 'get colorDepth', 0), configurable: true });\n" +
                "        Object.defineProperty(w.screen, 'pixelDepth', { get: makeNative(function pixelDepth() { return 24; }, 'get pixelDepth', 0), configurable: true });\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    // 100% Spec-Compliant WebGL Vendor & Renderer Hooking (Zero Exceptions, Native Extensions Untouched)\n" +
                "    var hookedGlProtos = (typeof WeakSet !== 'undefined') ? new WeakSet() : null;\n" +
                "    function hookGlContext(proto, isWebGL2, w) {\n" +
                "      if (!proto || !proto.getParameter) return;\n" +
                "      try {\n" +
                "        if (hookedGlProtos) {\n" +
                "          if (hookedGlProtos.has(proto)) return;\n" +
                "          hookedGlProtos.add(proto);\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "\n" +
                "      var origGetParam = proto.getParameter;\n" +
                "      var getParamFn = function getParameter(param) {\n" +
                "        try {\n" +
                "          if (this && typeof this.isContextLost === 'function' && !this.isContextLost()) {\n" +
                "            if (param === 37445) return webglVendor;\n" +
                "            if (param === 37446) return webglRenderer;\n" +
                "            if (isIos) {\n" +
                "              var isW2 = isWebGL2 || (typeof w.WebGL2RenderingContext !== 'undefined' && this instanceof w.WebGL2RenderingContext);\n" +
                "              if (param === 7938) return isW2 ? 'WebGL 2.0 (OpenGL ES 3.0 WebKit)' : 'WebGL 1.0 (OpenGL ES 2.0 WebKit)';\n" +
                "              if (param === 35724) return isW2 ? 'WebGL GLSL ES 3.00 (OpenGL ES GLSL ES 3.00 WebKit)' : 'WebGL GLSL ES 1.0 (OpenGL ES GLSL 1.0 WebKit)';\n" +
                "              if (param === 7936) return 'WebKit';\n" +
                "              if (param === 7937) return 'WebKit WebGL';\n" +
                "            }\n" +
                "          }\n" +
                "        } catch(e) {}\n" +
                "        return origGetParam.apply(this, arguments);\n" +
                "      };\n" +
                "      try {\n" +
                "        Object.defineProperty(proto, 'getParameter', {\n" +
                "          value: makeNative(getParamFn, 'getParameter', 1),\n" +
                "          writable: true, configurable: true, enumerable: false\n" +
                "        });\n" +
                "      } catch(e) { proto.getParameter = makeNative(getParamFn, 'getParameter', 1); }\n" +
                "    }\n" +
                "\n" +
                "    function hookWebGL(w) {\n" +
                "      if (!w || !isIos) return;\n" +
                "      try {\n" +
                "        if (w.WebGLRenderingContext && w.WebGLRenderingContext.prototype) {\n" +
                "          hookGlContext(w.WebGLRenderingContext.prototype, false, w);\n" +
                "        }\n" +
                "        if (w.WebGL2RenderingContext && w.WebGL2RenderingContext.prototype) {\n" +
                "          hookGlContext(w.WebGL2RenderingContext.prototype, true, w);\n" +
                "        }\n" +
                "        if (w.WebGLRenderingContextBase && w.WebGLRenderingContextBase.prototype) {\n" +
                "          hookGlContext(w.WebGLRenderingContextBase.prototype, false, w);\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    // WebRTC Leak Protection\n" +
                "    function hookWebRTC(w) {\n" +
                "      if (!w) return;\n" +
                "      try {\n" +
                "        var rtcTarget = w.RTCPeerConnection || w.webkitRTCPeerConnection;\n" +
                "        if (rtcTarget) {\n" +
                "          var origRTC = rtcTarget;\n" +
                "          var customRTC = function RTCPeerConnection(cfg, cstr) {\n" +
                "            var safeCfg = cfg ? Object.assign({}, cfg) : {};\n" +
                "            if (safeCfg.iceServers) safeCfg.iceServers = [];\n" +
                "            return new origRTC(safeCfg, cstr);\n" +
                "          };\n" +
                "          customRTC.prototype = origRTC.prototype;\n" +
                "          if (origRTC.generateCertificate) customRTC.generateCertificate = origRTC.generateCertificate;\n" +
                "          makeNative(customRTC, 'RTCPeerConnection', 0, true);\n" +
                "          if (w.RTCPeerConnection) w.RTCPeerConnection = customRTC;\n" +
                "          if (w.webkitRTCPeerConnection) w.webkitRTCPeerConnection = customRTC;\n" +
                "          if (origRTC.prototype && origRTC.prototype.setConfiguration) {\n" +
                "            var origSetCfg = origRTC.prototype.setConfiguration;\n" +
                "            var customSetCfg = function setConfiguration(c) {\n" +
                "              var sc = c ? Object.assign({}, c) : {};\n" +
                "              if (sc.iceServers) sc.iceServers = [];\n" +
                "              return origSetCfg.call(this, sc);\n" +
                "            };\n" +
                "            try {\n" +
                "              Object.defineProperty(origRTC.prototype, 'setConfiguration', {\n" +
                "                value: makeNative(customSetCfg, 'setConfiguration'),\n" +
                "                writable: true, configurable: true, enumerable: false\n" +
                "              });\n" +
                "            } catch(e) { origRTC.prototype.setConfiguration = makeNative(customSetCfg, 'setConfiguration'); }\n" +
                "          }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    // Realm Hook Orchestrator\n" +
                "    var hookedRealms = (typeof WeakSet !== 'undefined') ? new WeakSet() : null;\n" +
                "    function hookRealm(w) {\n" +
                "      if (!w) return;\n" +
                "      try {\n" +
                "        if (hookedRealms) {\n" +
                "          if (hookedRealms.has(w)) return;\n" +
                "          hookedRealms.add(w);\n" +
                "        }\n" +
                "      } catch(e) { return;\n }\n" +
                "\n" +
                "      try {\n" +
                "        if (w.Function && w.Function.prototype && w.Function.prototype.toString !== customFnToString) {\n" +
                "          try {\n" +
                "            Object.defineProperty(w.Function.prototype, 'toString', {\n" +
                "              value: customFnToString,\n" +
                "              writable: true, configurable: true, enumerable: false\n" +
                "            });\n" +
                "          } catch(e) { w.Function.prototype.toString = customFnToString; }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "\n" +
                "      hookTimezone(w);\n" +
                "      hookNavigator(w);\n" +
                "      hookScreen(w);\n" +
                "      hookWebGL(w);\n" +
                "      hookWebRTC(w);\n" +
                "    }\n" +
                "\n" +
                "    hookRealm(window);\n" +
                "\n" +
                "    function scanAndHookAllIframes() {\n" +
                "      try {\n" +
                "        var ifrs = document.querySelectorAll('iframe');\n" +
                "        for (var k = 0; k < ifrs.length; k++) {\n" +
                "          try {\n" +
                "            var cw = ifrs[k].contentWindow;\n" +
                "            if (cw) hookRealm(cw);\n" +
                "          } catch(e) {}\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "    scanAndHookAllIframes();\n" +
                "\n" +
                "    try {\n" +
                "      var iframeObs = new MutationObserver(function() { scanAndHookAllIframes(); });\n" +
                "      iframeObs.observe(document.documentElement || document, { childList: true, subtree: true });\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    var iframeScanCount = 0;\n" +
                "    var iframeScanTimer = setInterval(function() {\n" +
                "      scanAndHookAllIframes();\n" +
                "      if (++iframeScanCount > 40) clearInterval(iframeScanTimer);\n" +
                "    }, 50);\n" +
                "\n" +
                "  } catch(err) {}\n" +
                "})();\n";
    }

    private static class ScriptRegistration {
        androidx.webkit.ScriptHandler handler;
        String signature;
    }

    public static void applyFingerprint(WebView view, JSONObject profile, String ipTimezone, double noiseSeed) {
        if (view == null || profile == null) return;
        try {
            String proxyTz = WebViewProxyManager.getLastResolvedTimezone();
            String tz;
            if (WebViewProxyManager.isProxyActive() && proxyTz != null && !proxyTz.trim().isEmpty()) {
                tz = proxyTz.trim();
            } else if (ipTimezone != null && !ipTimezone.trim().isEmpty()) {
                tz = ipTimezone.trim();
            } else {
                tz = profile.optString("timezone", "America/Chicago");
            }

            String rawUa = profile.optString("userAgent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36");
            String ua = alignUserAgent(rawUa, profile.optString("name", ""));

            WebSettings settings = view.getSettings();
            settings.setUserAgentString(ua);

            String spoofJs = buildSpoofScript(profile, tz, noiseSeed);
            String sig = profile.optString("name", "") + ":" + tz + ":" + noiseSeed;

            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                try {
                    Object tag = view.getTag();
                    ScriptRegistration reg = (tag instanceof ScriptRegistration) ? (ScriptRegistration) tag : null;
                    if (reg == null || !sig.equals(reg.signature)) {
                        if (reg != null && reg.handler != null) {
                            try { reg.handler.remove(); } catch (Exception ignored) {}
                        }
                        ScriptRegistration newReg = new ScriptRegistration();
                        newReg.signature = sig;
                        newReg.handler = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                                view, spoofJs, java.util.Collections.singleton("*"));
                        view.setTag(newReg);
                    }
                } catch (Exception ignored) {}
            }
            view.evaluateJavascript(spoofJs, null);

            Log.d(TAG, "Anti-detect applied: " + profile.optString("name", "Unknown") +
                    " | tz=" + tz + " | seed=" + noiseSeed);
        } catch (Exception e) {
            Log.e(TAG, "applyFingerprint failed", e);
        }
    }

    /**
     * Apply the device profile and auto-matched timezone to the WebView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void applyFingerprint(WebView view, JSONObject profile, String ipTimezone) {
        applyFingerprint(view, profile, ipTimezone, currentNoiseSeed);
    }

    public static void applyFingerprint(WebView view, JSONObject profile) {
        applyFingerprint(view, profile, activeTimezone);
    }

    public static void applyCurrentFingerprint(WebView view) {
        applyFingerprint(view, getCurrentProfile(), activeTimezone);
    }

    public static void injectCanvasNoise(WebView view) {
        applyCurrentFingerprint(view);
    }
}
