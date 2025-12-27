package com.linkedcamera.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class PreferenceSubServer extends PreferenceSubScreen implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PreferenceSubServer";
    private static final String PREF_UPLOAD_QUEUE = "nextcloud_upload_queue";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_server);

        // Update queue count on load
        updateQueueCountSummary();

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register listener for preference changes
        PreferenceManager.getDefaultSharedPreferences(getActivity())
            .registerOnSharedPreferenceChangeListener(this);
        // Update queue count when returning to this screen
        updateQueueCountSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister listener
        PreferenceManager.getDefaultSharedPreferences(getActivity())
            .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_UPLOAD_QUEUE.equals(key)) {
            // Queue changed, update the summary
            updateQueueCountSummary();
        }
    }

    /**
     * Update the "Process Upload Queue" preference summary with current queue count
     */
    private void updateQueueCountSummary() {
        Preference processQueuePref = findPreference(PreferenceKeys.NextcloudProcessQueuePreferenceKey);
        if (processQueuePref != null) {
            int queueCount = getQueueCount();
            String summary;
            if (queueCount == 0) {
                summary = getString(R.string.preference_nextcloud_queue_empty);
            } else {
                summary = getString(R.string.preference_nextcloud_queue_count, queueCount);
            }
            processQueuePref.setSummary(summary);

            if (MyDebug.LOG)
                Log.d(TAG, "Updated queue summary: " + summary);
        }
    }

    /**
     * Get the current upload queue count
     */
    private int getQueueCount() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Set<String> queue = prefs.getStringSet(PREF_UPLOAD_QUEUE, new HashSet<String>());
        return queue.size();
    }

    @Override
    public boolean onPreferenceTreeClick(android.preference.PreferenceScreen preferenceScreen, Preference preference) {
        if( MyDebug.LOG )
            Log.d(TAG, "onPreferenceTreeClick: " + preference.getKey());

        if( preference.getKey().equals(PreferenceKeys.NextcloudProcessQueuePreferenceKey) ) {
            if (MyDebug.LOG)
                Log.d(TAG, "Process queue button clicked");

            // Check if there are items in queue
            int queueCount = getQueueCount();
            if (queueCount == 0) {
                Toast.makeText(getActivity(), R.string.preference_nextcloud_queue_empty, Toast.LENGTH_SHORT).show();
                return true;
            }

            // Check if WiFi is connected
            ConnectivityManager connectivityManager =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

            boolean isWifiConnected = false;
            if (connectivityManager != null) {
                NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                isWifiConnected = wifiInfo != null && wifiInfo.isConnected();
            }

            if (isWifiConnected) {
                if (MyDebug.LOG)
                    Log.d(TAG, "WiFi connected, starting queue processing");
                Toast.makeText(getActivity(), R.string.nextcloud_queue_processing, Toast.LENGTH_SHORT).show();
                NextcloudUploadService.processUploadQueue(getActivity());
            } else {
                if (MyDebug.LOG)
                    Log.d(TAG, "WiFi not connected");
                Toast.makeText(getActivity(), R.string.nextcloud_no_wifi, Toast.LENGTH_SHORT).show();
            }

            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
