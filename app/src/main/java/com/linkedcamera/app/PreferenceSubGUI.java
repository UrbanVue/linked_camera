package com.linkedcamera.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceSubGUI extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubGUI";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_gui);

        final Bundle bundle = getArguments();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final boolean camera_open = bundle.getBoolean("camera_open");
        if( MyDebug.LOG )
            Log.d(TAG, "camera_open: " + camera_open);

        final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_face_detection: " + supports_face_detection);

        final boolean supports_flash = bundle.getBoolean("supports_flash");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_flash: " + supports_flash);

        final boolean supports_preview_bitmaps = bundle.getBoolean("supports_preview_bitmaps");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_preview_bitmaps: " + supports_preview_bitmaps);

        final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_raw: " + supports_raw);

        final boolean supports_white_balance_lock = bundle.getBoolean("supports_white_balance_lock");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_white_balance_lock: " + supports_white_balance_lock);

        final boolean supports_exposure_lock = bundle.getBoolean("supports_exposure_lock");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_exposure_lock: " + supports_exposure_lock);

        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        if( MyDebug.LOG )
            Log.d(TAG, "is_multi_cam: " + is_multi_cam);

        final boolean has_physical_cameras = bundle.getBoolean("has_physical_cameras");
        if( MyDebug.LOG )
            Log.d(TAG, "has_physical_cameras: " + has_physical_cameras);

        if( !supports_face_detection  && ( camera_open || sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false) == false ) ) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference("preference_show_face_detection");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_flash ) {
            Preference pref = findPreference("preference_show_cycle_flash");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_preview_bitmaps ) {
            Preference pref = findPreference("preference_show_focus_peaking");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_auto_stabilise ) {
            Preference pref = findPreference("preference_show_auto_level");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_raw ) {
            Preference pref = findPreference("preference_show_cycle_raw");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_white_balance_lock ) {
            Preference pref = findPreference("preference_show_white_balance_lock");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_exposure_lock ) {
            Preference pref = findPreference("preference_show_exposure_lock");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !is_multi_cam && !has_physical_cameras ) {
            Preference pref = findPreference("preference_multi_cam_button");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }
}
