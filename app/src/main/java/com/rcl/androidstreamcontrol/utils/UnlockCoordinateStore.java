package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Stores and retrieves the user's PIN unlock coordinates.
 * Data is AES-256-GCM encrypted using Android Keystore (hardware-backed on most devices).
 *
 * Storage format (before encryption):
 * [
 *   {"digit": 0, "x": 540.0, "y": 1420.0},
 *   {"digit": 1, "x": 720.0, "y": 1620.0},
 *   ...
 * ]
 *
 * The list is ordered — digit at index 0 is the first PIN digit to tap, etc.
 */
public class UnlockCoordinateStore {

    private static final String TAG = "UnlockCoordinateStore";
    private static final String KEY_ALIAS = "aura_unlock_key";
    private static final String PREFS_NAME = "unlock_prefs";
    private static final String PREFS_DATA_KEY = "pin_coords_enc";
    private static final String PREFS_IV_KEY = "pin_coords_iv";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Saves the full ordered PIN sequence to encrypted SharedPreferences.
    // pinSequence is a JSONArray of objects: [{digit, x, y}, ...]
    public static void saveCoordinates(Context context, JSONArray pinSequence) {
        try {
            String plaintext = pinSequence.toString();
            SecretKey key = getOrCreateKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString(PREFS_DATA_KEY, Base64.encodeToString(encrypted, Base64.DEFAULT))
                .putString(PREFS_IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
                .apply();

            Log.d(TAG, "PIN coordinates saved. Count: " + pinSequence.length());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save coordinates", e);
        }
    }

    // Loads and decrypts the stored PIN coordinate sequence.
    // Returns null if nothing is stored or decryption fails.
    public static JSONArray loadCoordinates(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String encB64 = prefs.getString(PREFS_DATA_KEY, null);
            String ivB64 = prefs.getString(PREFS_IV_KEY, null);
            if (encB64 == null || ivB64 == null) return null;

            byte[] encrypted = Base64.decode(encB64, Base64.DEFAULT);
            byte[] iv = Base64.decode(ivB64, Base64.DEFAULT);

            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new JSONArray(new String(decrypted, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load coordinates", e);
            return null;
        }
    }

    // Returns true if coordinates have been stored.
    public static boolean isCalibrated(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains(PREFS_DATA_KEY);
    }

    // Deletes stored coordinates (for re-calibration).
    public static void clearCoordinates(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit().clear().apply();
        Log.d(TAG, "Coordinates cleared.");
    }

    // Gets the AES key from Keystore, or creates it if it doesn't exist.
    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }

        // Key does not exist — create it
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return keyGenerator.generateKey();
    }
}
