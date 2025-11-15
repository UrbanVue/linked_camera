package com.linkedcamera.app;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.util.Log;

import com.linkedcamera.app.ui.ArraySeekBarPreference;

public class PreferenceSubPreview extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubPreview";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_preview);

        final Bundle bundle = getArguments();

        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if( MyDebug.LOG )
            Log.d(TAG, "using_android_l: " + using_android_l);

        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        if( MyDebug.LOG )
            Log.d(TAG, "is_multi_cam: " + is_multi_cam);

        final boolean supports_preview_bitmaps = bundle.getBoolean("supports_preview_bitmaps");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_preview_bitmaps: " + supports_preview_bitmaps);

        {
            ListPreference pref = (ListPreference)findPreference("preference_ghost_image");

            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked ghost image: " + newValue);
                    if( newValue.equals("preference_ghost_image_selected") ) {
                        MainActivity main_activity = (MainActivity) PreferenceSubPreview.this.getActivity();
                        main_activity.openGhostImageChooserDialogSAF(true);
                    }
                    return true;
                }
            });
        }

        {
            final int max_ghost_image_alpha = 80; // limit max to 80% for privacy reasons, so it isn't possible to put in a state where camera is on, but no preview is shown
            final int ghost_image_alpha_step = 5; // should be exact divisor of max_ghost_image_alpha
            final int n_ghost_image_alpha = max_ghost_image_alpha/ghost_image_alpha_step;
            CharSequence [] entries = new CharSequence[n_ghost_image_alpha];
            CharSequence [] values = new CharSequence[n_ghost_image_alpha];
            for(int i=0;i<n_ghost_image_alpha;i++) {
                int alpha = ghost_image_alpha_step*(i+1);
                entries[i] = alpha + "%";
                values[i] = String.valueOf(alpha);
            }
            ArraySeekBarPreference sp = (ArraySeekBarPreference)findPreference("ghost_image_alpha");
            sp.setEntries(entries);
            sp.setEntryValues(values);
        }

        if( !using_android_l ) {
            Preference pref = findPreference("preference_focus_assist");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !is_multi_cam ) {
            Preference pref = findPreference("preference_show_camera_id");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !using_android_l ) {
            Preference pref = findPreference("preference_show_iso");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_preview_bitmaps ) {
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");

            Preference pref = findPreference("preference_histogram");
            pg.removePreference(pref);

            pref = findPreference("preference_zebra_stripes");
            pg.removePreference(pref);

            pref = findPreference("preference_zebra_stripes_foreground_color");
            pg.removePreference(pref);

            pref = findPreference("preference_zebra_stripes_background_color");
            pg.removePreference(pref);

            pref = findPreference("preference_focus_peaking");
            pg.removePreference(pref);

            pref = findPreference("preference_focus_peaking_color");
            pg.removePreference(pref);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }
}
