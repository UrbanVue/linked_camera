package com.linkedcamera.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.widget.Toast;

public class PreferenceSubServer extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubServer";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_server);

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    @Override
    public boolean onPreferenceTreeClick(android.preference.PreferenceScreen preferenceScreen, Preference preference) {
        if( MyDebug.LOG )
            Log.d(TAG, "onPreferenceTreeClick: " + preference.getKey());

        if( preference.getKey().equals(PreferenceKeys.NextcloudProcessQueuePreferenceKey) ) {
            Log.d(TAG, "Process queue button clicked");

            // Check if WiFi is connected
            ConnectivityManager connectivityManager =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

            boolean isWifiConnected = false;
            if (connectivityManager != null) {
                NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                isWifiConnected = wifiInfo != null && wifiInfo.isConnected();
            }

            if (isWifiConnected) {
                Log.d(TAG, "WiFi connected, starting queue processing");
                Toast.makeText(getActivity(), R.string.nextcloud_queue_processing, Toast.LENGTH_SHORT).show();
                NextcloudUploadService.processUploadQueue(getActivity());
            } else {
                Log.d(TAG, "WiFi not connected");
                Toast.makeText(getActivity(), R.string.nextcloud_no_wifi, Toast.LENGTH_SHORT).show();
            }

            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
