package com.rcl.androidstreamcontrol.utils;

import android.annotation.SuppressLint;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.Random;
import java.util.UUID;

/**
 * Anti-Detect Module — Advanced Fingerprint Spoofing
 *
 * Implements full GoLogin-grade isolation:
 *   - Device User-Agent spoofing
 *   - Screen dimensions & pixel depth spoofing
 *   - Dynamic Timezone: Intl.DateTimeFormat & Date.prototype (toString, toTimeString, toLocaleString, getTimezoneOffset)
 *   - Hardware concurrency & Device memory spoofing
 *   - Platform & Vendor (Apple Computer, Inc. for iOS vs Google Inc. for Android)
 *   - Dynamic Client Hints (navigator.userAgentData) - version matches UA 1:1, undefined on iOS
 *   - Battery API (navigator.getBattery) - native function signature masked as [native code]
 *   - Native Function Masking: makeNative on all overridden methods
 *   - WebGL Unmasked Vendor & Renderer spoofing (Qualcomm Adreno / Apple GPU)
 *   - Microscopic Canvas seeded noise injection
 *   - Native Profile Reset support
 */
public class FingerprintManager {

    private static final String TAG = "Fingerprint";

    private static volatile JSONObject currentProfile = null;
    private static volatile String activeTimezone = "America/Indiana/Indianapolis";
    private static volatile double currentNoiseSeed = 0.42;
    private static volatile boolean isNativeProfile = false;

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
        }
    }

    public static void rotateNoiseSeed() {
        currentNoiseSeed = new Random().nextDouble();
    }

    /**
     * Build the comprehensive Anti-Detect JavaScript spoofing payload.
     */
    public static String buildSpoofScript(JSONObject profile, String timezone, double noiseSeed) {
        String tz = (timezone != null && !timezone.isEmpty())
                ? timezone
                : (profile != null ? profile.optString("timezone", "America/Indiana/Indianapolis") : "America/Indiana/Indianapolis");

        String lang = profile != null ? profile.optString("language", "en-US") : "en-US";
        int width = profile != null ? profile.optInt("screen_width", 412) : 412;
        int height = profile != null ? profile.optInt("screen_height", 915) : 915;
        String name = profile != null ? profile.optString("name", "Android") : "Android";
        String ua = profile != null ? profile.optString("userAgent", "") : "";
        boolean isIos = name.toLowerCase().contains("iphone") || name.toLowerCase().contains("ipad");

        String chromeMajor = "124";
        String chromeFull = "124.0.6367.113";
        String androidVer = "14.0.0";
        if (ua != null && !ua.isEmpty()) {
            java.util.regex.Matcher cm = java.util.regex.Pattern.compile("Chrome/([0-9]+)\\.([0-9]+\\.[0-9]+\\.[0-9]+)").matcher(ua);
            if (cm.find()) {
                chromeMajor = cm.group(1);
                chromeFull = cm.group(1) + "." + cm.group(2);
            }
            java.util.regex.Matcher am = java.util.regex.Pattern.compile("Android\\s+([0-9]+)").matcher(ua);
            if (am.find()) {
                androidVer = am.group(1) + ".0.0";
            }
        }

        String lowerName = name.toLowerCase();
        String webglVendor;
        String webglRenderer;

        if (isIos) {
            webglVendor = "Apple Inc.";
            webglRenderer = "Apple GPU";
        } else if (lowerName.contains("pixel 8")) {
            webglVendor = "ARM";
            webglRenderer = "Mali-G715-Immortalis MC10";
        } else if (lowerName.contains("pixel 7")) {
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
        } else if (lowerName.contains("legion") || lowerName.contains("y90")) {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 730";
        } else {
            webglVendor = "Qualcomm";
            webglRenderer = "Adreno (TM) 740";
        }

        if (profile != null && profile.has("webgl_vendor")) {
            webglVendor = profile.optString("webgl_vendor", webglVendor);
        }
        if (profile != null && profile.has("webgl_renderer")) {
            webglRenderer = profile.optString("webgl_renderer", webglRenderer);
        }
        String platform = isIos ? (name.toLowerCase().contains("ipad") ? "iPad" : "iPhone") : "Linux armv8l";
        String vendor = isIos ? "Apple Computer, Inc." : "Google Inc.";
        int hwConcurrency = isIos ? 6 : 8;
        int devMemory = 8;

        return "(function() {\n" +
                "  try {\n" +
                "    var tz = '" + tz + "';\n" +
                "    var lang = '" + lang + "';\n" +
                "    var sw = " + width + ";\n" +
                "    var sh = " + height + ";\n" +
                "    var webglVendor = '" + webglVendor + "';\n" +
                "    var webglRenderer = '" + webglRenderer + "';\n" +
                "    var platform = '" + platform + "';\n" +
                "    var vendor = '" + vendor + "';\n" +
                "    var isIos = " + isIos + ";\n" +
                "    var profileName = '" + name + "';\n" +
                "    var hwConcurrency = " + hwConcurrency + ";\n" +
                "    var devMemory = " + devMemory + ";\n" +
                "    var noiseSeed = " + noiseSeed + ";\n" +
                "    var cMajor = '" + chromeMajor + "';\n" +
                "    var cFull = '" + chromeFull + "';\n" +
                "    var androidVer = '" + androidVer + "';\n" +
                "\n" +
                "    // Global Native Function Disguise (Intercepts Function.prototype.toString.call)\n" +
                "    try {\n" +
                "      var origFnToString = Function.prototype.toString;\n" +
                "      var customFnToString = function toString() {\n" +
                "        try {\n" +
                "          if (this && this.__isNativeCustom) {\n" +
                "            var fnName = this.name || '';\n" +
                "            return 'function ' + fnName + '() { [native code] }';\n" +
                "          }\n" +
                "        } catch(e) {}\n" +
                "        return origFnToString.apply(this, arguments);\n" +
                "      };\n" +
                "      customFnToString.__isNativeCustom = true;\n" +
                "      try { Object.defineProperty(customFnToString, 'name', { value: 'toString', configurable: true }); } catch(e) {}\n" +
                "      Function.prototype.toString = customFnToString;\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // Native Function Masking Helper\n" +
                "    function makeNative(fn, name) {\n" +
                "      if (typeof fn !== 'function') return fn;\n" +
                "      var fnName = name || fn.name || '';\n" +
                "      var nativeStr = 'function ' + fnName + '() { [native code] }';\n" +
                "      try { fn.__isNativeCustom = true; } catch(e) {}\n" +
                "      try { Object.defineProperty(fn, 'name', { value: fnName, configurable: true }); } catch(e) {}\n" +
                "      try {\n" +
                "        var fnToStr = function toString() { return nativeStr; };\n" +
                "        fnToStr.__isNativeCustom = true;\n" +
                "        Object.defineProperty(fnToStr, 'name', { value: 'toString', configurable: true });\n" +
                "        Object.defineProperty(fn, 'toString', {\n" +
                "          value: fnToStr,\n" +
                "          writable: true,\n" +
                "          configurable: true\n" +
                "        });\n" +
                "      } catch(e) {}\n" +
                "      return fn;\n" +
                "    }\n" +
                "\n" +
                "    // 1. Timezone & Comprehensive Date Spoofing\n" +
                "    try {\n" +
                "      var tzOffsets = {\n" +
                "        'America/New_York': { offset: 240, str: 'GMT-0400 (Eastern Daylight Time)' },\n" +
                "        'America/Detroit': { offset: 240, str: 'GMT-0400 (Eastern Daylight Time)' },\n" +
                "        'America/Indiana/Indianapolis': { offset: 240, str: 'GMT-0400 (Eastern Daylight Time)' },\n" +
                "        'America/Puerto_Rico': { offset: 240, str: 'GMT-0400 (Atlantic Standard Time)' },\n" +
                "        'America/Chicago': { offset: 300, str: 'GMT-0500 (Central Daylight Time)' },\n" +
                "        'America/Denver': { offset: 360, str: 'GMT-0600 (Mountain Daylight Time)' },\n" +
                "        'America/Boise': { offset: 360, str: 'GMT-0600 (Mountain Daylight Time)' },\n" +
                "        'America/Phoenix': { offset: 420, str: 'GMT-0700 (Mountain Standard Time)' },\n" +
                "        'America/Los_Angeles': { offset: 420, str: 'GMT-0700 (Pacific Daylight Time)' },\n" +
                "        'America/Anchorage': { offset: 480, str: 'GMT-0800 (Alaska Daylight Time)' },\n" +
                "        'Pacific/Honolulu': { offset: 600, str: 'GMT-1000 (Hawaii Standard Time)' }\n" +
                "      };\n" +
                "      var activeTzInfo = tzOffsets[tz] || { offset: 240, str: 'GMT-0400 (Eastern Daylight Time)' };\n" +
                "\n" +
                "      var OrigDateTimeFormat = Intl.DateTimeFormat;\n" +
                "      var PatchedDateTimeFormat = function(locales, options) {\n" +
                "        options = Object.assign({}, options);\n" +
                "        if (!options.timeZone) options.timeZone = tz;\n" +
                "        return new OrigDateTimeFormat(locales || 'en-US', options);\n" +
                "      };\n" +
                "      PatchedDateTimeFormat.prototype = OrigDateTimeFormat.prototype;\n" +
                "      PatchedDateTimeFormat.supportedLocalesOf = OrigDateTimeFormat.supportedLocalesOf;\n" +
                "      makeNative(PatchedDateTimeFormat, 'DateTimeFormat');\n" +
                "      Intl.DateTimeFormat = PatchedDateTimeFormat;\n" +
                "\n" +
                "      var getTimezoneOffsetFn = function getTimezoneOffset() {\n" +
                "        return activeTzInfo.offset;\n" +
                "      };\n" +
                "      Date.prototype.getTimezoneOffset = makeNative(getTimezoneOffsetFn, 'getTimezoneOffset');\n" +
                "\n" +
                "      var toStringFn = function toString() {\n" +
                "        var f = new OrigDateTimeFormat('en-US', {\n" +
                "          timeZone: tz, weekday: 'short', year: 'numeric', month: 'short',\n" +
                "          day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false\n" +
                "        });\n" +
                "        var m = {};\n" +
                "        var parts = f.formatToParts(this);\n" +
                "        for (var i = 0; i < parts.length; i++) m[parts[i].type] = parts[i].value;\n" +
                "        return m.weekday + ' ' + m.month + ' ' + m.day + ' ' + m.year + ' ' +\n" +
                "               m.hour + ':' + m.minute + ':' + m.second + ' ' + activeTzInfo.str;\n" +
                "      };\n" +
                "      Date.prototype.toString = makeNative(toStringFn, 'toString');\n" +
                "\n" +
                "      var toTimeStringFn = function toTimeString() {\n" +
                "        var f = new OrigDateTimeFormat('en-US', {\n" +
                "          timeZone: tz, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false\n" +
                "        });\n" +
                "        var m = {};\n" +
                "        var parts = f.formatToParts(this);\n" +
                "        for (var i = 0; i < parts.length; i++) m[parts[i].type] = parts[i].value;\n" +
                "        return m.hour + ':' + m.minute + ':' + m.second + ' ' + activeTzInfo.str;\n" +
                "      };\n" +
                "      Date.prototype.toTimeString = makeNative(toTimeStringFn, 'toTimeString');\n" +
                "\n" +
                "      var origToLocaleString = Date.prototype.toLocaleString;\n" +
                "      var toLocaleStringFn = function toLocaleString(locales, options) {\n" +
                "        options = Object.assign({}, options);\n" +
                "        if (!options.timeZone) options.timeZone = tz;\n" +
                "        return origToLocaleString.call(this, locales || 'en-US', options);\n" +
                "      };\n" +
                "      Date.prototype.toLocaleString = makeNative(toLocaleStringFn, 'toLocaleString');\n" +
                "\n" +
                "      var origToLocaleDateString = Date.prototype.toLocaleDateString;\n" +
                "      var toLocaleDateStringFn = function toLocaleDateString(locales, options) {\n" +
                "        options = Object.assign({}, options);\n" +
                "        if (!options.timeZone) options.timeZone = tz;\n" +
                "        return origToLocaleDateString.call(this, locales || 'en-US', options);\n" +
                "      };\n" +
                "      Date.prototype.toLocaleDateString = makeNative(toLocaleDateStringFn, 'toLocaleDateString');\n" +
                "\n" +
                "      var origToLocaleTimeString = Date.prototype.toLocaleTimeString;\n" +
                "      var toLocaleTimeStringFn = function toLocaleTimeString(locales, options) {\n" +
                "        options = Object.assign({}, options);\n" +
                "        if (!options.timeZone) options.timeZone = tz;\n" +
                "        return origToLocaleTimeString.call(this, locales || 'en-US', options);\n" +
                "      };\n" +
                "      Date.prototype.toLocaleTimeString = makeNative(toLocaleTimeStringFn, 'toLocaleTimeString');\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // 2. Navigator Properties Spoofing\n" +
                "    try {\n" +
                "      Object.defineProperty(navigator, 'language', { get: function() { return lang; }, configurable: true });\n" +
                "      Object.defineProperty(navigator, 'languages', { get: function() { return [lang, 'en']; }, configurable: true });\n" +
                "      Object.defineProperty(navigator, 'platform', { get: function() { return platform; }, configurable: true });\n" +
                "      Object.defineProperty(navigator, 'vendor', { get: function() { return vendor; }, configurable: true });\n" +
                "      Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return hwConcurrency; }, configurable: true });\n" +
                "      if (!isIos) {\n" +
                "        Object.defineProperty(navigator, 'deviceMemory', { get: function() { return devMemory; }, configurable: true });\n" +
                "      }\n" +
                "      Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; }, configurable: true });\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // 3. Client Hints, Chromium Leaks Removal & Battery API\n" +
                "    try {\n" +
                "      if (isIos) {\n" +
                "        var proto = (typeof Navigator !== 'undefined') ? Navigator.prototype : null;\n" +
                "        var chromNavProps = [\n" +
                "          'deviceMemory',\n" +
                "          'connection', 'mozConnection', 'webkitConnection',\n" +
                "          'vibrate',\n" +
                "          'contacts',\n" +
                "          'ink',\n" +
                "          'keyboard',\n" +
                "          'managed',\n" +
                "          'scheduling',\n" +
                "          'storageBuckets',\n" +
                "          'userActivation',\n" +
                "          'virtualKeyboard',\n" +
                "          'webkitTemporaryStorage',\n" +
                "          'webkitPersistentStorage',\n" +
                "          'windowControlsOverlay',\n" +
                "          'requestMIDIAccess',\n" +
                "          'clearAppBadge',\n" +
                "          'setAppBadge',\n" +
                "          'bluetooth',\n" +
                "          'usb',\n" +
                "          'serial',\n" +
                "          'hid',\n" +
                "          'presentation',\n" +
                "          'gpu',\n" +
                "          'userAgentData',\n" +
                "          'getBattery'\n" +
                "        ];\n" +
                "        for (var k = 0; k < chromNavProps.length; k++) {\n" +
                "          var pName = chromNavProps[k];\n" +
                "          try { delete navigator[pName]; } catch(e) {}\n" +
                "          try { if (proto) delete proto[pName]; } catch(e) {}\n" +
                "        }\n" +
                "\n" +
                "        var chromGlobals = [\n" +
                "          'chrome',\n" +
                "          'NetworkInformation',\n" +
                "          'ContactsManager',\n" +
                "          'Ink',\n" +
                "          'Keyboard',\n" +
                "          'NavigatorManagedData',\n" +
                "          'Scheduling',\n" +
                "          'StorageBucketManager',\n" +
                "          'UserActivation',\n" +
                "          'VirtualKeyboard',\n" +
                "          'WindowControlsOverlay',\n" +
                "          'MIDIAccess',\n" +
                "          'MIDIInputMap',\n" +
                "          'MIDIOutputMap',\n" +
                "          'BatteryManager',\n" +
                "          'Bluetooth',\n" +
                "          'USB',\n" +
                "          'Serial',\n" +
                "          'HID'\n" +
                "        ];\n" +
                "        for (var g = 0; g < chromGlobals.length; g++) {\n" +
                "          try { delete window[chromGlobals[g]]; } catch(e) {}\n" +
                "        }\n" +
                "\n" +
                "        if (typeof window.ApplePaySession === 'undefined') {\n" +
                "          try {\n" +
                "            var ApplePaySession = function ApplePaySession() {};\n" +
                "            ApplePaySession.canMakePayments = makeNative(function canMakePayments() { return true; }, 'canMakePayments');\n" +
                "            ApplePaySession.canMakePaymentsWithActiveCard = makeNative(function canMakePaymentsWithActiveCard() { return Promise.resolve(true); }, 'canMakePaymentsWithActiveCard');\n" +
                "            ApplePaySession.supportsVersion = makeNative(function supportsVersion() { return true; }, 'supportsVersion');\n" +
                "            makeNative(ApplePaySession, 'ApplePaySession');\n" +
                "            window.ApplePaySession = ApplePaySession;\n" +
                "          } catch(e) {}\n" +
                "        }\n" +
                "      } else {\n" +
                "        try {\n" +
                "          var cMatch = (navigator.userAgent || '').match(/Chrome\\/([0-9]+)\\.([0-9]+\\.[0-9]+\\.[0-9]+)/);\n" +
                "          if (cMatch) {\n" +
                "            cMajor = cMatch[1];\n" +
                "            cFull = cMatch[1] + '.' + cMatch[2];\n" +
                "          }\n" +
                "        } catch(e) {}\n" +
                "\n" +
                "        var brandsList = [\n" +
                "          { brand: 'Google Chrome', version: cMajor },\n" +
                "          { brand: 'Chromium', version: cMajor },\n" +
                "          { brand: 'Not-A.Brand', version: '99' }\n" +
                "        ];\n" +
                "        var fullVersionList = [\n" +
                "          { brand: 'Google Chrome', version: cFull },\n" +
                "          { brand: 'Chromium', version: cFull },\n" +
                "          { brand: 'Not-A.Brand', version: '99.0.0.0' }\n" +
                "        ];\n" +
                "        var formFactorsList = ['mobile'];\n" +
                "\n" +
                "        var getHighEntropyValuesFn = function getHighEntropyValues(hints) {\n" +
                "          return Promise.resolve({\n" +
                "            architecture: 'arm',\n" +
                "            bitness: '64',\n" +
                "            brands: brandsList,\n" +
                "            formFactors: formFactorsList,\n" +
                "            fullVersionList: fullVersionList,\n" +
                "            mobile: true,\n" +
                "            model: profileName,\n" +
                "            platform: 'Android',\n" +
                "            platformVersion: androidVer,\n" +
                "            uaFullVersion: cFull,\n" +
                "            wow64: false\n" +
                "          });\n" +
                "        };\n" +
                "        var toJSONFn = function toJSON() {\n" +
                "          return {\n" +
                "            brands: brandsList,\n" +
                "            mobile: true,\n" +
                "            platform: 'Android'\n" +
                "          };\n" +
                "        };\n" +
                "\n" +
                "        var mockUaData = {\n" +
                "          brands: brandsList,\n" +
                "          mobile: true,\n" +
                "          platform: 'Android',\n" +
                "          getHighEntropyValues: makeNative(getHighEntropyValuesFn, 'getHighEntropyValues'),\n" +
                "          toJSON: makeNative(toJSONFn, 'toJSON')\n" +
                "        };\n" +
                "        if (typeof window.NavigatorUAData !== 'undefined') {\n" +
                "          try { Object.setPrototypeOf(mockUaData, window.NavigatorUAData.prototype); } catch(e) {}\n" +
                "        }\n" +
                "        Object.defineProperty(navigator, 'userAgentData', {\n" +
                "          get: function() { return mockUaData; },\n" +
                "          configurable: true,\n" +
                "          enumerable: true\n" +
                "        });\n" +
                "\n" +
                "        try {\n" +
                "          var mockBattery = {\n" +
                "            charging: true,\n" +
                "            chargingTime: 0,\n" +
                "            dischargingTime: Infinity,\n" +
                "            level: 1.0,\n" +
                "            onchargingchange: null,\n" +
                "            onchargingtimechange: null,\n" +
                "            ondischargingtimechange: null,\n" +
                "            onlevelchange: null,\n" +
                "            addEventListener: makeNative(function addEventListener() {}, 'addEventListener'),\n" +
                "            removeEventListener: makeNative(function removeEventListener() {}, 'removeEventListener'),\n" +
                "            dispatchEvent: makeNative(function dispatchEvent() { return true; }, 'dispatchEvent')\n" +
                "          };\n" +
                "          if (typeof window.BatteryManager !== 'undefined') {\n" +
                "            try { Object.setPrototypeOf(mockBattery, window.BatteryManager.prototype); } catch(e) {}\n" +
                "          }\n" +
                "          var getBatteryFn = function getBattery() {\n" +
                "            return Promise.resolve(mockBattery);\n" +
                "          };\n" +
                "          Object.defineProperty(navigator, 'getBattery', {\n" +
                "            value: makeNative(getBatteryFn, 'getBattery'),\n" +
                "            writable: true,\n" +
                "            configurable: true,\n" +
                "            enumerable: true\n" +
                "          });\n" +
                "        } catch(e) {}\n" +
                "\n" +
                "        try {\n" +
                "          var netInfo = {\n" +
                "            downlink: 10,\n" +
                "            effectiveType: '4g',\n" +
                "            rtt: 50,\n" +
                "            saveData: false,\n" +
                "            onchange: null,\n" +
                "            addEventListener: makeNative(function addEventListener() {}, 'addEventListener'),\n" +
                "            removeEventListener: makeNative(function removeEventListener() {}, 'removeEventListener'),\n" +
                "            dispatchEvent: makeNative(function dispatchEvent() { return true; }, 'dispatchEvent')\n" +
                "          };\n" +
                "          if (typeof window.NetworkInformation !== 'undefined') {\n" +
                "            try { Object.setPrototypeOf(netInfo, window.NetworkInformation.prototype); } catch(e) {}\n" +
                "          }\n" +
                "          Object.defineProperty(navigator, 'connection', {\n" +
                "            get: function() { return netInfo; },\n" +
                "            configurable: true,\n" +
                "            enumerable: true\n" +
                "          });\n" +
                "        } catch(e) {}\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // SpeechSynthesis API Polyfill (Reports Enabled on BrowserLeaks)\n" +
                "    try {\n" +
                "      if (typeof window.speechSynthesis === 'undefined' || !window.speechSynthesis) {\n" +
                "        var MockSpeechSynthesis = function SpeechSynthesis() {};\n" +
                "        MockSpeechSynthesis.prototype.speak = makeNative(function speak() {}, 'speak');\n" +
                "        MockSpeechSynthesis.prototype.cancel = makeNative(function cancel() {}, 'cancel');\n" +
                "        MockSpeechSynthesis.prototype.pause = makeNative(function pause() {}, 'pause');\n" +
                "        MockSpeechSynthesis.prototype.resume = makeNative(function resume() {}, 'resume');\n" +
                "        MockSpeechSynthesis.prototype.getVoices = makeNative(function getVoices() { return []; }, 'getVoices');\n" +
                "        MockSpeechSynthesis.prototype.addEventListener = makeNative(function addEventListener() {}, 'addEventListener');\n" +
                "        MockSpeechSynthesis.prototype.removeEventListener = makeNative(function removeEventListener() {}, 'removeEventListener');\n" +
                "        MockSpeechSynthesis.prototype.dispatchEvent = makeNative(function dispatchEvent() { return true; }, 'dispatchEvent');\n" +
                "        MockSpeechSynthesis.prototype.paused = false;\n" +
                "        MockSpeechSynthesis.prototype.pending = false;\n" +
                "        MockSpeechSynthesis.prototype.speaking = false;\n" +
                "        MockSpeechSynthesis.prototype.onvoiceschanged = null;\n" +
                "        makeNative(MockSpeechSynthesis, 'SpeechSynthesis');\n" +
                "        window.SpeechSynthesis = MockSpeechSynthesis;\n" +
                "        window.speechSynthesis = new MockSpeechSynthesis();\n" +
                "      }\n" +
                "      if (typeof window.SpeechSynthesisUtterance === 'undefined') {\n" +
                "        var MockUtterance = function SpeechSynthesisUtterance(text) { this.text = text || ''; };\n" +
                "        makeNative(MockUtterance, 'SpeechSynthesisUtterance');\n" +
                "        window.SpeechSynthesisUtterance = MockUtterance;\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // 4. Screen Dimensions Spoofing\n" +
                "    try {\n" +
                "      Object.defineProperty(screen, 'width', { get: function() { return sw; }, configurable: true });\n" +
                "      Object.defineProperty(screen, 'height', { get: function() { return sh; }, configurable: true });\n" +
                "      Object.defineProperty(screen, 'availWidth', { get: function() { return sw; }, configurable: true });\n" +
                "      Object.defineProperty(screen, 'availHeight', { get: function() { return sh; }, configurable: true });\n" +
                "      Object.defineProperty(screen, 'colorDepth', { get: function() { return 24; }, configurable: true });\n" +
                "      Object.defineProperty(screen, 'pixelDepth', { get: function() { return 24; }, configurable: true });\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // 5 & 6. WebGL & Canvas Multi-Realm Fingerprint Protection (Top Window & Iframes)\n" +
                "    function hookRealm(w) {\n" +
                "      if (!w) return;\n" +
                "      try {\n" +
                "        if (w.__antiDetectHooked) return;\n" +
                "        w.__antiDetectHooked = true;\n" +
                "      } catch(e) { return; }\n" +
                "\n" +
                "      // WebGL Vendor, Renderer, Hardware Limits, Extensions & readPixels Spoofing on Realm\n" +
                "      try {\n" +
                "        var hookGlContext = function(proto, isWebGL2) {\n" +
                "          if (!proto) return;\n" +
                "          try {\n" +
                "            if (Object.prototype.hasOwnProperty.call(proto, '__glHooked')) return;\n" +
                "            Object.defineProperty(proto, '__glHooked', { value: true, writable: true, configurable: true });\n" +
                "          } catch(e) {\n" +
                "            if (proto.__glHooked) return;\n" +
                "            proto.__glHooked = true;\n" +
                "          }\n" +
                "\n" +
                "          var origGetParam = proto.getParameter;\n" +
                "          if (origGetParam) {\n" +
                "            var getParamFn = function getParameter(param) {\n" +
                "              if (param === 37445) return webglVendor;\n" +
                "              if (param === 37446) return webglRenderer;\n" +
                "              if (param === 3379) {\n" +
                "                var v = origGetParam.apply(this, arguments);\n" +
                "                return Math.max(v || 4096, 16384);\n" +
                "              }\n" +
                "              if (param === 34076) {\n" +
                "                var v = origGetParam.apply(this, arguments);\n" +
                "                return Math.max(v || 4096, 16384);\n" +
                "              }\n" +
                "              if (isIos) {\n" +
                "                if (param === 7938) return isWebGL2 ? 'WebGL 2.0 (OpenGL ES 3.0 WebKit)' : 'WebGL 1.0 (OpenGL ES 2.0 WebKit)';\n" +
                "                if (param === 35724) return isWebGL2 ? 'WebGL GLSL ES 3.00 (OpenGL ES GLSL ES 3.00 WebKit)' : 'WebGL GLSL ES 1.0 (OpenGL ES GLSL 1.0 WebKit)';\n" +
                "                if (param === 7936) return 'WebKit';\n" +
                "                if (param === 7937) return 'WebKit WebGL';\n" +
                "                if (param === 33902) return new Float32Array([1, 1]);\n" +
                "                if (param === 35661) return 32;\n" +
                "              }\n" +
                "              return origGetParam.apply(this, arguments);\n" +
                "            };\n" +
                "            try {\n" +
                "              Object.defineProperty(proto, 'getParameter', {\n" +
                "                value: makeNative(getParamFn, 'getParameter'),\n" +
                "                writable: true, configurable: true, enumerable: true\n" +
                "              });\n" +
                "            } catch(e) { proto.getParameter = makeNative(getParamFn, 'getParameter'); }\n" +
                "          }\n" +
                "          var origGetExts = proto.getSupportedExtensions;\n" +
                "          if (origGetExts) {\n" +
                "            var getExtsFn = function getSupportedExtensions() {\n" +
                "              var exts = origGetExts.apply(this, arguments);\n" +
                "              if (exts && Array.isArray(exts)) {\n" +
                "                if (exts.indexOf('WEBGL_debug_renderer_info') === -1) {\n" +
                "                  exts.push('WEBGL_debug_renderer_info');\n" +
                "                }\n" +
                "                if (isIos) {\n" +
                "                  exts = exts.filter(function(e) {\n" +
                "                    return e !== 'WEBGL_compressed_texture_etc1' && e !== 'WEBGL_compressed_texture_etc' && e !== 'WEBGL_compressed_texture_atc';\n" +
                "                  });\n" +
                "                  if (exts.indexOf('WEBGL_compressed_texture_astc') === -1) {\n" +
                "                    exts.push('WEBGL_compressed_texture_astc');\n" +
                "                  }\n" +
                "                }\n" +
                "              }\n" +
                "              return exts;\n" +
                "            };\n" +
                "            try {\n" +
                "              Object.defineProperty(proto, 'getSupportedExtensions', {\n" +
                "                value: makeNative(getExtsFn, 'getSupportedExtensions'),\n" +
                "                writable: true, configurable: true, enumerable: true\n" +
                "              });\n" +
                "            } catch(e) { proto.getSupportedExtensions = makeNative(getExtsFn, 'getSupportedExtensions'); }\n" +
                "          }\n" +
                "          var origGetExt = proto.getExtension;\n" +
                "          if (origGetExt) {\n" +
                "            var getExtFn = function getExtension(name) {\n" +
                "              if (isIos && (name === 'WEBGL_compressed_texture_etc1' || name === 'WEBGL_compressed_texture_etc' || name === 'WEBGL_compressed_texture_atc')) {\n" +
                "                return null;\n" +
                "              }\n" +
                "              var ext = origGetExt.apply(this, arguments);\n" +
                "              if (!ext && name === 'WEBGL_debug_renderer_info') {\n" +
                "                return {\n" +
                "                  UNMASKED_VENDOR_WEBGL: 37445,\n" +
                "                  UNMASKED_RENDERER_WEBGL: 37446\n" +
                "                };\n" +
                "              }\n" +
                "              return ext;\n" +
                "            };\n" +
                "            try {\n" +
                "              Object.defineProperty(proto, 'getExtension', {\n" +
                "                value: makeNative(getExtFn, 'getExtension'),\n" +
                "                writable: true, configurable: true, enumerable: true\n" +
                "              });\n" +
                "            } catch(e) { proto.getExtension = makeNative(getExtFn, 'getExtension'); }\n" +
                "          }\n" +
                "          var origReadPixels = proto.readPixels;\n" +
                "          if (origReadPixels) {\n" +
                "            var readPixelsFn = function readPixels(x, y, width, height, format, type, pixels) {\n" +
                "              var res = origReadPixels.apply(this, arguments);\n" +
                "              try {\n" +
                "                if (pixels && pixels.length && typeof pixels === 'object') {\n" +
                "                  var step = Math.max(16, Math.floor(pixels.length / 64));\n" +
                "                  for (var pi = 0; pi < pixels.length; pi += step) {\n" +
                "                    var pr = Math.sin(noiseSeed * 888 + pi) * 10000;\n" +
                "                    var pDelta = (pr - Math.floor(pr) > 0.5) ? 1 : -1;\n" +
                "                    if (pixels instanceof Float32Array) {\n" +
                "                      pixels[pi] += pDelta * 0.0001;\n" +
                "                    } else {\n" +
                "                      pixels[pi] = Math.min(255, Math.max(0, pixels[pi] + pDelta));\n" +
                "                    }\n" +
                "                  }\n" +
                "                }\n" +
                "              } catch(e) {}\n" +
                "              return res;\n" +
                "            };\n" +
                "            try {\n" +
                "              Object.defineProperty(proto, 'readPixels', {\n" +
                "                value: makeNative(readPixelsFn, 'readPixels'),\n" +
                "                writable: true, configurable: true, enumerable: true\n" +
                "              });\n" +
                "            } catch(e) { proto.readPixels = makeNative(readPixelsFn, 'readPixels'); }\n" +
                "          }\n" +
                "        };\n" +
                "\n" +
                "        if (w.WebGLRenderingContextBase && w.WebGLRenderingContextBase.prototype) {\n" +
                "          hookGlContext(w.WebGLRenderingContextBase.prototype, false);\n" +
                "        }\n" +
                "        if (w.WebGLRenderingContext && w.WebGLRenderingContext.prototype) {\n" +
                "          hookGlContext(w.WebGLRenderingContext.prototype, false);\n" +
                "        }\n" +
                "        if (w.WebGL2RenderingContext && w.WebGL2RenderingContext.prototype) {\n" +
                "          hookGlContext(w.WebGL2RenderingContext.prototype, true);\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "\n" +
                "      // Canvas Micro-Noise Spoofing (Unique Fingerprint Signature)\n" +
                "      try {\n" +
                "        if (w.HTMLCanvasElement && w.HTMLCanvasElement.prototype) {\n" +
                "          var origToDataURL = w.HTMLCanvasElement.prototype.toDataURL;\n" +
                "          var origGetCtx = w.HTMLCanvasElement.prototype.getContext;\n" +
                "\n" +
                "          var customGetCtx = makeNative(function getContext(type) {\n" +
                "            var ctx = origGetCtx.apply(this, arguments);\n" +
                "            try {\n" +
                "              if (this.ownerDocument && this.ownerDocument.defaultView) {\n" +
                "                hookRealm(this.ownerDocument.defaultView);\n" +
                "              }\n" +
                "              if (ctx && typeof ctx === 'object') {\n" +
                "                var tStr = String(type || '').toLowerCase();\n" +
                "                if (tStr.indexOf('webgl') !== -1) {\n" +
                "                  var isW2 = tStr.indexOf('webgl2') !== -1;\n" +
                "                  var curr = ctx;\n" +
                "                  while (curr && curr !== Object.prototype) {\n" +
                "                    hookGlContext(curr, isW2);\n" +
                "                    curr = Object.getPrototypeOf(curr);\n" +
                "                  }\n" +
                "                }\n" +
                "              }\n" +
                "            } catch(e) {}\n" +
                "            return ctx;\n" +
                "          }, 'getContext');\n" +
                "\n" +
                "          try {\n" +
                "            Object.defineProperty(w.HTMLCanvasElement.prototype, 'getContext', {\n" +
                "              value: customGetCtx,\n" +
                "              writable: true, configurable: true, enumerable: true\n" +
                "            });\n" +
                "          } catch(e) { w.HTMLCanvasElement.prototype.getContext = customGetCtx; }\n" +
                "\n" +
                "          var toDataURLFn = function toDataURL() {\n" +
                "            try {\n" +
                "              if (!this.__noiseApplied && this.width > 0 && this.height > 0) {\n" +
                "                this.__noiseApplied = true;\n" +
                "                var ctx = origGetCtx.call(this, '2d');\n" +
                "                if (ctx) {\n" +
                "                  var origG = (w.CanvasRenderingContext2D && w.CanvasRenderingContext2D.prototype)\n" +
                "                    ? w.CanvasRenderingContext2D.prototype.getImageData : ctx.getImageData;\n" +
                "                  var origP = (w.CanvasRenderingContext2D && w.CanvasRenderingContext2D.prototype)\n" +
                "                    ? w.CanvasRenderingContext2D.prototype.putImageData : ctx.putImageData;\n" +
                "                  var imgData = origG.call(ctx, 0, 0, this.width, this.height);\n" +
                "                  var d = imgData.data;\n" +
                "                  var w_ = this.width;\n" +
                "                  var h_ = this.height;\n" +
                "                  for (var y = 0; y < h_; y += 2) {\n" +
                "                    for (var x = 0; x < w_; x += 2) {\n" +
                "                      var idx = (y * w_ + x) * 4;\n" +
                "                      var r = Math.sin(noiseSeed * 777 + idx) * 10000;\n" +
                "                      var delta = (r - Math.floor(r) > 0.5) ? 1 : -1;\n" +
                "                      d[idx + 1] = Math.min(255, Math.max(0, d[idx + 1] + delta));\n" +
                "                    }\n" +
                "                  }\n" +
                "                  origP.call(ctx, imgData, 0, 0);\n" +
                "                }\n" +
                "              }\n" +
                "            } catch(e) {}\n" +
                "            return origToDataURL.apply(this, arguments);\n" +
                "          };\n" +
                "          try {\n" +
                "            Object.defineProperty(w.HTMLCanvasElement.prototype, 'toDataURL', {\n" +
                "              value: makeNative(toDataURLFn, 'toDataURL'),\n" +
                "              writable: true, configurable: true, enumerable: true\n" +
                "            });\n" +
                "          } catch(e) { w.HTMLCanvasElement.prototype.toDataURL = makeNative(toDataURLFn, 'toDataURL'); }\n" +
                "\n" +
                "          if (w.HTMLCanvasElement.prototype.toBlob) {\n" +
                "            var origToBlob = w.HTMLCanvasElement.prototype.toBlob;\n" +
                "            var toBlobFn = function toBlob(cb, type, quality) {\n" +
                "              try {\n" +
                "                if (!this.__noiseApplied && this.width > 0 && this.height > 0) {\n" +
                "                  this.__noiseApplied = true;\n" +
                "                  var ctx = origGetCtx.call(this, '2d');\n" +
                "                  if (ctx) {\n" +
                "                    var origG = (w.CanvasRenderingContext2D && w.CanvasRenderingContext2D.prototype)\n" +
                "                      ? w.CanvasRenderingContext2D.prototype.getImageData : ctx.getImageData;\n" +
                "                    var origP = (w.CanvasRenderingContext2D && w.CanvasRenderingContext2D.prototype)\n" +
                "                      ? w.CanvasRenderingContext2D.prototype.putImageData : ctx.putImageData;\n" +
                "                    var imgData = origG.call(ctx, 0, 0, this.width, this.height);\n" +
                "                    var d = imgData.data;\n" +
                "                    var w_ = this.width;\n" +
                "                    var h_ = this.height;\n" +
                "                    for (var y = 0; y < h_; y += 2) {\n" +
                "                      for (var x = 0; x < w_; x += 2) {\n" +
                "                        var idx = (y * w_ + x) * 4;\n" +
                "                        var r = Math.sin(noiseSeed * 777 + idx) * 10000;\n" +
                "                        var delta = (r - Math.floor(r) > 0.5) ? 1 : -1;\n" +
                "                        d[idx + 1] = Math.min(255, Math.max(0, d[idx + 1] + delta));\n" +
                "                      }\n" +
                "                    }\n" +
                "                    origP.call(ctx, imgData, 0, 0);\n" +
                "                  }\n" +
                "                }\n" +
                "              } catch(e) {}\n" +
                "              return origToBlob.apply(this, arguments);\n" +
                "            };\n" +
                "            try {\n" +
                "              Object.defineProperty(w.HTMLCanvasElement.prototype, 'toBlob', {\n" +
                "                value: makeNative(toBlobFn, 'toBlob'),\n" +
                "                writable: true, configurable: true, enumerable: true\n" +
                "              });\n" +
                "            } catch(e) { w.HTMLCanvasElement.prototype.toBlob = makeNative(toBlobFn, 'toBlob'); }\n" +
                "          }\n" +
                "        }\n" +
                "\n" +
                "        if (w.OffscreenCanvas && w.OffscreenCanvas.prototype) {\n" +
                "          var origOffGetCtx = w.OffscreenCanvas.prototype.getContext;\n" +
                "          var customOffGetCtx = makeNative(function getContext(type) {\n" +
                "            var ctx = origOffGetCtx.apply(this, arguments);\n" +
                "            try {\n" +
                "              if (ctx && typeof ctx === 'object') {\n" +
                "                var tStr = String(type || '').toLowerCase();\n" +
                "                if (tStr.indexOf('webgl') !== -1) {\n" +
                "                  var isW2 = tStr.indexOf('webgl2') !== -1;\n" +
                "                  var curr = ctx;\n" +
                "                  while (curr && curr !== Object.prototype) {\n" +
                "                    hookGlContext(curr, isW2);\n" +
                "                    curr = Object.getPrototypeOf(curr);\n" +
                "                  }\n" +
                "                }\n" +
                "              }\n" +
                "            } catch(e) {}\n" +
                "            return ctx;\n" +
                "          }, 'getContext');\n" +
                "\n" +
                "          try {\n" +
                "            Object.defineProperty(w.OffscreenCanvas.prototype, 'getContext', {\n" +
                "              value: customOffGetCtx,\n" +
                "              writable: true, configurable: true, enumerable: true\n" +
                "            });\n" +
                "          } catch(e) { w.OffscreenCanvas.prototype.getContext = customOffGetCtx; }\n" +
                "        }\n" +
                "\n" +
                "        if (w.CanvasRenderingContext2D && w.CanvasRenderingContext2D.prototype) {\n" +
                "          var origGetImgData = w.CanvasRenderingContext2D.prototype.getImageData;\n" +
                "          var getImgDataFn = function getImageData(sx, sy, sw, sh) {\n" +
                "            var imgData = origGetImgData.apply(this, arguments);\n" +
                "            try {\n" +
                "              var d = imgData.data;\n" +
                "              for (var i = 0; i < d.length; i += 8) {\n" +
                "                var r = Math.sin(noiseSeed * 777 + i) * 10000;\n" +
                "                var delta = (r - Math.floor(r) > 0.5) ? 1 : -1;\n" +
                "                d[i + 1] = Math.min(255, Math.max(0, d[i + 1] + delta));\n" +
                "              }\n" +
                "            } catch(e) {}\n" +
                "            return imgData;\n" +
                "          };\n" +
                "          try {\n" +
                "            Object.defineProperty(w.CanvasRenderingContext2D.prototype, 'getImageData', {\n" +
                "              value: makeNative(getImgDataFn, 'getImageData'),\n" +
                "              writable: true, configurable: true, enumerable: true\n" +
                "            });\n" +
                "          } catch(e) { w.CanvasRenderingContext2D.prototype.getImageData = makeNative(getImgDataFn, 'getImageData'); }\n" +
                "        }\n" +
                "\n" +
                "        if (w.Document && w.Document.prototype) {\n" +
                "          var origCreateEl = w.Document.prototype.createElement;\n" +
                "          var customCreateEl = makeNative(function createElement(tagName) {\n" +
                "            var el = origCreateEl.apply(this, arguments);\n" +
                "            try {\n" +
                "              if (this.defaultView) hookRealm(this.defaultView);\n" +
                "            } catch(e) {}\n" +
                "            return el;\n" +
                "          }, 'createElement');\n" +
                "          try {\n" +
                "            Object.defineProperty(w.Document.prototype, 'createElement', {\n" +
                "              value: customCreateEl,\n" +
                "              writable: true, configurable: true, enumerable: true\n" +
                "            });\n" +
                "          } catch(e) { w.Document.prototype.createElement = customCreateEl; }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    // Hook top window immediately\n" +
                "    hookRealm(window);\n" +
                "\n" +
                "    // Intercept iframe contentWindow and contentDocument getters\n" +
                "    try {\n" +
                "      if (typeof HTMLIFrameElement !== 'undefined') {\n" +
                "        var origContentDocDesc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentDocument');\n" +
                "        if (origContentDocDesc && origContentDocDesc.get) {\n" +
                "          Object.defineProperty(HTMLIFrameElement.prototype, 'contentDocument', {\n" +
                "            get: function() {\n" +
                "              var doc = origContentDocDesc.get.apply(this, arguments);\n" +
                "              try {\n" +
                "                if (doc && doc.defaultView) hookRealm(doc.defaultView);\n" +
                "              } catch(e) {}\n" +
                "              return doc;\n" +
                "            },\n" +
                "            configurable: true,\n" +
                "            enumerable: true\n" +
                "          });\n" +
                "        }\n" +
                "\n" +
                "        var origContentWinDesc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');\n" +
                "        if (origContentWinDesc && origContentWinDesc.get) {\n" +
                "          Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {\n" +
                "            get: function() {\n" +
                "              var win = origContentWinDesc.get.apply(this, arguments);\n" +
                "              try {\n" +
                "                if (win) hookRealm(win);\n" +
                "              } catch(e) {}\n" +
                "              return win;\n" +
                "            },\n" +
                "            configurable: true,\n" +
                "            enumerable: true\n" +
                "          });\n" +
                "        }\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    // Periodic scanner & MutationObserver to catch any dynamic iframes\n" +
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
                "      var iframeObs = new MutationObserver(function() {\n" +
                "        scanAndHookAllIframes();\n" +
                "      });\n" +
                "      iframeObs.observe(document.documentElement || document, { childList: true, subtree: true });\n" +
                "    } catch(e) {}\n" +
                "\n" +
                "    var iframeScanCount = 0;\n" +
                "    var iframeScanTimer = setInterval(function() {\n" +
                "      scanAndHookAllIframes();\n" +
                "      if (++iframeScanCount > 40) clearInterval(iframeScanTimer);\n" +
                "    }, 50);\n" +
                "  } catch(err) {}\n" +
                "})();\n" +
                "";
    }

    private static class ScriptRegistration {
        androidx.webkit.ScriptHandler handler;
        String signature;
    }

    public static void applyFingerprint(WebView view, JSONObject profile, String ipTimezone, double noiseSeed) {
        if (view == null || profile == null) return;
        try {
            String tz = (ipTimezone != null && !ipTimezone.trim().isEmpty())
                    ? ipTimezone.trim()
                    : profile.optString("timezone", "America/New_York");

            String ua = profile.optString("userAgent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36");

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
