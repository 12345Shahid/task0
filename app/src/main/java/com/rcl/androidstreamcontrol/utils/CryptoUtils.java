package com.rcl.androidstreamcontrol.utils;

import android.util.Base64;

public class CryptoUtils {

    // Simple XOR key for obfuscation (change this for each build in production)
    private static final String XOR_KEY = "M1ndfulT4sks2026";

    /**
     * XOR Encrypts or Decrypts a string.
     * Since XOR is symmetric, running this twice returns the original string.
     */
    public static String xor(String input) {
        if (input == null) return null;
        
        char[] key = XOR_KEY.toCharArray();
        char[] out = new char[input.length()];
        
        for (int i = 0; i < input.length(); i++) {
            out[i] = (char) (input.charAt(i) ^ key[i % key.length]);
        }
        
        return new String(out);
    }

    /**
     * Helper to Base64 encode an XOR'd string so it can be safely pasted into Java code.
     */
    public static String encryptAndEncode(String plainText) {
        String xored = xor(plainText);
        return Base64.encodeToString(xored.getBytes(), Base64.NO_WRAP);
    }

    /**
     * Decrypts a Base64 encoded XOR'd string back to plain text.
     */
    public static String decodeAndDecrypt(String base64EncodedXor) {
        byte[] decodedBytes = Base64.decode(base64EncodedXor, Base64.NO_WRAP);
        String xored = new String(decodedBytes);
        return xor(xored);
    }
}
