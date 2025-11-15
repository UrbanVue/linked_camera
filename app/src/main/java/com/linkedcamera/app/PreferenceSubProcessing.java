package com.linkedcamera.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

import com.linkedcamera.app.cameracontroller.CameraController;

public class PreferenceSubProcessing extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubProcessing";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_processing);

        final Bundle bundle = getArguments();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final boolean camera_open = bundle.getBoolean("camera_open");
        if( MyDebug.LOG )
            Log.d(TAG, "camera_open: " + camera_open);

        boolean has_antibanding = false;
        String [] antibanding_values = bundle.getStringArray("antibanding");
        if( antibanding_values != null && antibanding_values.length > 0 ) {
            String [] antibanding_entries = bundle.getStringArray("antibanding_entries");
            if( antibanding_entries != null && antibanding_entries.length == antibanding_values.length ) { // should always be true here, but just in case
                MyPreferenceFragment.readFromBundle(this, antibanding_values, antibanding_entries, PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT, "preferences_root");
                has_antibanding = true;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "has_antibanding?: " + has_antibanding);
        if( !has_antibanding && ( camera_open || sharedPreferences.getString(PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT).equals(CameraController.ANTIBANDING_DEFAULT) ) ) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.AntiBandingPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        boolean has_edge_mode = false;
        String [] edge_mode_values = bundle.getStringArray("edge_modes");
        if( edge_mode_values != null && edge_mode_values.length > 0 ) {
            String [] edge_mode_entries = bundle.getStringArray("edge_modes_entries");
            if( edge_mode_entries != null && edge_mode_entries.length == edge_mode_values.length ) { // should always be true here, but just in case
                MyPreferenceFragment.readFromBundle(this, edge_mode_values, edge_mode_entries, PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT, "preferences_root");
                has_edge_mode = true;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "has_edge_mode?: " + has_edge_mode);
        if( !has_edge_mode && ( camera_open || sharedPreferences.getString(PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT).equals(CameraController.EDGE_MODE_DEFAULT) ) ) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.EdgeModePreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        boolean has_noise_reduction_mode = false;
        String [] noise_reduction_mode_values = bundle.getStringArray("noise_reduction_modes");
        if( noise_reduction_mode_values != null && noise_reduction_mode_values.length > 0 ) {
            String [] noise_reduction_mode_entries = bundle.getStringArray("noise_reduction_modes_entries");
            if( noise_reduction_mode_entries != null && noise_reduction_mode_entries.length == noise_reduction_mode_values.length ) { // should always be true here, but just in case
                MyPreferenceFragment.readFromBundle(this, noise_reduction_mode_values, noise_reduction_mode_entries, PreferenceKeys.CameraNoiseReductionModePreferenceKey, CameraController.NOISE_REDUCTION_MODE_DEFAULT, "preferences_root");
                has_noise_reduction_mode = true;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "has_noise_reduction_mode?: " + has_noise_reduction_mode);
        if( !has_noise_reduction_mode && ( camera_open || sharedPreferences.getString(PreferenceKeys.CameraNoiseReductionModePreferenceKey, CameraController.NOISE_REDUCTION_MODE_DEFAULT).equals(CameraController.NOISE_REDUCTION_MODE_DEFAULT) ) ) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.CameraNoiseReductionModePreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }
}
