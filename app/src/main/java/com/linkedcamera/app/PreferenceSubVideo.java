package com.linkedcamera.app;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceSubVideo extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubVideo";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_video);

        final Bundle bundle = getArguments();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final int cameraId = bundle.getInt("cameraId");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraId: " + cameraId);
        final String cameraIdSPhysical = bundle.getString("cameraIdSPhysical");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraIdSPhysical: " + cameraIdSPhysical);

        final boolean camera_open = bundle.getBoolean("camera_open");
        if( MyDebug.LOG )
            Log.d(TAG, "camera_open: " + camera_open);

        final String [] video_quality = bundle.getStringArray("video_quality");
        final String [] video_quality_string = bundle.getStringArray("video_quality_string");

        final int [] video_fps = bundle.getIntArray("video_fps");
        final boolean [] video_fps_high_speed = bundle.getBooleanArray("video_fps_high_speed");

        String fps_preference_key = PreferenceKeys.getVideoFPSPreferenceKey(cameraId, cameraIdSPhysical);
        if( MyDebug.LOG )
            Log.d(TAG, "fps_preference_key: " + fps_preference_key);
        String fps_value = sharedPreferences.getString(fps_preference_key, "default");
        if( MyDebug.LOG )
            Log.d(TAG, "fps_value: " + fps_value);

        final boolean supports_tonemap_curve = bundle.getBoolean("supports_tonemap_curve");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_tonemap_curve: " + supports_tonemap_curve);

        final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_video_stabilization: " + supports_video_stabilization);

        final boolean supports_force_video_4k = bundle.getBoolean("supports_force_video_4k");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_force_video_4k: " + supports_force_video_4k);

		/* Set up video resolutions.
		   Note that this will be the resolutions for either standard or high speed frame rate (where
		   the latter may also include being in slow motion mode), depending on the current setting when
		   this settings fragment is launched. A limitation is that if the user changes the fps value
		   within the settings, this list won't update until the user exits and re-enters the settings.
		   This could be fixed by setting a setOnPreferenceChangeListener for the preference_video_fps
		   ListPreference and updating, but we must not assume that the preview will be non-null (since
		   if the application is being recreated, MyPreferenceFragment.onCreate() is called via
		   MainActivity.onCreate()->super.onCreate() before the preview is created! So we still need to
		   read the info via a bundle, and only update when fps changes if the preview is non-null.
		 */
        if( video_quality != null && video_quality_string != null ) {
            CharSequence [] entries = new CharSequence[video_quality.length];
            CharSequence [] values = new CharSequence[video_quality.length];
            for(int i=0;i<video_quality.length;i++) {
                entries[i] = video_quality_string[i];
                values[i] = video_quality[i];
            }
            ListPreference lp = (ListPreference)findPreference("preference_video_quality");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            String video_quality_preference_key = bundle.getString("video_quality_preference_key");
            if( MyDebug.LOG )
                Log.d(TAG, "video_quality_preference_key: " + video_quality_preference_key);
            String video_quality_value = sharedPreferences.getString(video_quality_preference_key, "");
            if( MyDebug.LOG )
                Log.d(TAG, "video_quality_value: " + video_quality_value);
            // set the key, so we save for the correct cameraId and high-speed setting
            // this must be done before setting the value (otherwise the video resolutions preference won't be
            // updated correctly when this is called from the callback when the user switches between
            // normal and high speed frame rates
            lp.setKey(video_quality_preference_key);
            lp.setValue(video_quality_value);

            boolean is_high_speed = bundle.getBoolean("video_is_high_speed");
            String title = is_high_speed ? getResources().getString(R.string.video_quality) + " [" + getResources().getString(R.string.high_speed) + "]" : getResources().getString(R.string.video_quality);
            lp.setTitle(title);
            lp.setDialogTitle(title);
        }
        else {
            Preference pref = findPreference("preference_video_quality");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( video_fps != null ) {
            // build video fps settings
            CharSequence [] entries = new CharSequence[video_fps.length+1];
            CharSequence [] values = new CharSequence[video_fps.length+1];
            int i=0;
            // default:
            entries[i] = getResources().getString(R.string.preference_video_fps_default);
            values[i] = "default";
            i++;
            final String high_speed_append = " [" + getResources().getString(R.string.high_speed) + "]";
            for(int k=0;k<video_fps.length;k++) {
                int fps = video_fps[k];
                if( video_fps_high_speed != null && video_fps_high_speed[k] ) {
                    entries[i] = fps + high_speed_append;
                }
                else {
                    entries[i] = String.valueOf(fps);
                }
                values[i] = String.valueOf(fps);
                i++;
            }

            ListPreference lp = (ListPreference)findPreference("preference_video_fps");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            lp.setValue(fps_value);
            // now set the key, so we save for the correct cameraId
            lp.setKey(fps_preference_key);
        }

        if( !supports_tonemap_curve && ( camera_open || sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off").equals("off") ) ) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            // (needed for Pixel 6 Pro where setting to sRGB causes camera to fail to open when in video mode)
            Preference pref = findPreference(PreferenceKeys.VideoLogPreferenceKey);
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);

            pref = findPreference(PreferenceKeys.VideoProfileGammaPreferenceKey);
            //pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_video_stabilization ) {
            Preference pref = findPreference("preference_video_stabilization");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_force_video_4k || video_quality == null ) {
            Preference pref = findPreference("preference_force_video_4k");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_video_debugging");
            pg.removePreference(pref);
        }

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
            MyPreferenceFragment.filterArrayEntry((ListPreference)findPreference("preference_video_output_format"), "preference_video_output_format_mpeg4_hevc");
        }

        {
            ListPreference pref = (ListPreference)findPreference("preference_record_audio_src");

            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
                // some values require at least Android 7
                pref.setEntries(R.array.preference_record_audio_src_entries_preandroid7);
                pref.setEntryValues(R.array.preference_record_audio_src_values_preandroid7);
            }
        }

        setupDependencies();

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    /** Programmatically set up dependencies for preference types (e.g., ListPreference) that don't
     *  support this in xml (such as SwitchPreference and CheckBoxPreference), or where this depends
     *  on the device (e.g., Android version).
     */
    private void setupDependencies() {
        // set up dependency for preference_video_profile_gamma on preference_video_log
        ListPreference pref = (ListPreference)findPreference("preference_video_log");
        if( pref != null ) { // may be null if preference not supported
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    String value = newValue.toString();
                    setVideoProfileGammaDependency(value);
                    return true;
                }
            });
            setVideoProfileGammaDependency(pref.getValue()); // ensure dependency is enabled/disabled as required for initial value
        }

        if( !MyApplicationInterface.mediastoreSupportsVideoSubtitles() ) {
            // video subtitles only supported with SAF on Android 11+
            // since these preferences are entirely in separate sub-screens (and one isn't the parent of the other), we don't need
            // a dependency (and indeed can't use one, as the preference_using_saf won't exist here as a Preference)
            pref = (ListPreference)findPreference("preference_video_subtitle");
            if( pref != null ) {
                boolean using_saf = false;
                // n.b., not safe to call main_activity.getApplicationInterface().getStorageUtils().isUsingSAF() if fragment
                // is being recreated
                {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
                    if( sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false) ) {
                        using_saf = true;
                    }
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "using_saf: " + using_saf);

                //pref.setDependency("preference_using_saf");
                if( using_saf ) {
                    pref.setEnabled(true);
                }
                else {
                    pref.setEnabled(false);
                }
            }
        }
    }

    private void setVideoProfileGammaDependency(String newValue) {
        Preference dependent = findPreference("preference_video_profile_gamma");
        if( dependent != null ) { // just in case
            boolean enable_dependent = "gamma".equals(newValue);
            if( MyDebug.LOG )
                Log.d(TAG, "clicked video log: " + newValue + " enable_dependent: " + enable_dependent);
            dependent.setEnabled(enable_dependent);
        }
    }
}
