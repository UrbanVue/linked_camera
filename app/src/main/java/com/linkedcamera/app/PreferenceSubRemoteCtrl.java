package com.linkedcamera.app;

import android.os.Bundle;
import android.util.Log;

public class PreferenceSubRemoteCtrl extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubRemoteCtrl";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_remote_ctrl);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }
}
