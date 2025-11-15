package com.linkedcamera.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * BroadcastReceiver that listens for WiFi connectivity changes
 * and processes the Nextcloud upload queue when WiFi becomes available
 */
public class NextcloudWiFiReceiver extends BroadcastReceiver {
    private static final String TAG = "NextcloudWiFiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (MyDebug.LOG)
            Log.d(TAG, "onReceive: " + intent.getAction());

        // Check if Nextcloud upload is enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);

        if (!uploadEnabled) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload not enabled, ignoring");
            return;
        }

        // Check if WiFi is now connected
        ConnectivityManager connectivityManager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (wifiInfo != null && wifiInfo.isConnected()) {
                if (MyDebug.LOG)
                    Log.d(TAG, "WiFi connected, processing upload queue");

                // Process the upload queue
                NextcloudUploadService.processUploadQueue(context);
            } else {
                if (MyDebug.LOG)
                    Log.d(TAG, "WiFi not connected");
            }
        }
    }
}
