package com.linkedcamera.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

import com.linkedcamera.app.preview.Preview;
import com.linkedcamera.app.ui.ArraySeekBarPreference;

public class PreferenceSubPhoto extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubPhoto";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_photo);

        final Bundle bundle = getArguments();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final int cameraId = bundle.getInt("cameraId");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraId: " + cameraId);
        final String cameraIdSPhysical = bundle.getString("cameraIdSPhysical");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraIdSPhysical: " + cameraIdSPhysical);

        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if( MyDebug.LOG )
            Log.d(TAG, "using_android_l: " + using_android_l);

        final int [] widths = bundle.getIntArray("resolution_widths");
        final int [] heights = bundle.getIntArray("resolution_heights");
        final boolean [] supports_burst = bundle.getBooleanArray("resolution_supports_burst");

        final boolean supports_jpeg_r = bundle.getBoolean("supports_jpeg_r");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_jpeg_r: " + supports_jpeg_r);

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_raw: " + supports_raw);
        final boolean supports_burst_raw = bundle.getBoolean("supports_burst_raw");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_burst_raw: " + supports_burst_raw);

        final boolean supports_optimise_focus_latency = bundle.getBoolean("supports_optimise_focus_latency");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_optimise_focus_latency: " + supports_optimise_focus_latency);

        final boolean supports_preshots = bundle.getBoolean("supports_preshots");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_preshots: " + supports_preshots);

        final boolean supports_nr = bundle.getBoolean("supports_nr");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_nr: " + supports_nr);

        final boolean supports_hdr = bundle.getBoolean("supports_hdr");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_hdr: " + supports_hdr);

        final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_expo_bracketing: " + supports_expo_bracketing);

        final int max_expo_bracketing_n_images = bundle.getInt("max_expo_bracketing_n_images");
        if( MyDebug.LOG )
            Log.d(TAG, "max_expo_bracketing_n_images: " + max_expo_bracketing_n_images);

        final boolean supports_panorama = bundle.getBoolean("supports_panorama");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_panorama: " + supports_panorama);

        final boolean supports_photo_video_recording = bundle.getBoolean("supports_photo_video_recording");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_photo_video_recording: " + supports_photo_video_recording);

        if( widths != null && heights != null && supports_burst != null ) {
            CharSequence [] entries = new CharSequence[widths.length];
            CharSequence [] values = new CharSequence[widths.length];
            for(int i=0;i<widths.length;i++) {
                entries[i] = widths[i] + " x " + heights[i] + " " + Preview.getAspectRatioMPString(getResources(), widths[i], heights[i], supports_burst[i]);
                values[i] = widths[i] + " " + heights[i];
            }
            ListPreference lp = (ListPreference)findPreference("preference_resolution");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            String resolution_preference_key = PreferenceKeys.getResolutionPreferenceKey(cameraId, cameraIdSPhysical);
            String resolution_value = sharedPreferences.getString(resolution_preference_key, "");
            if( MyDebug.LOG )
                Log.d(TAG, "resolution_value: " + resolution_value);
            lp.setValue(resolution_value);
            // now set the key, so we save for the correct cameraId
            lp.setKey(resolution_preference_key);
        }
        else {
            Preference pref = findPreference("preference_resolution");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        {
            final int n_quality = 100;
            CharSequence [] entries = new CharSequence[n_quality];
            CharSequence [] values = new CharSequence[n_quality];
            for(int i=0;i<n_quality;i++) {
                entries[i] = (i+1) + "%";
                values[i] = String.valueOf(i + 1);
            }
            ArraySeekBarPreference sp = (ArraySeekBarPreference)findPreference("preference_quality");
            sp.setEntries(entries);
            sp.setEntryValues(values);
        }

        if( !supports_jpeg_r ) {
            ListPreference pref = (ListPreference)findPreference("preference_image_format");
            pref.setEntries(R.array.preference_image_format_entries_nojpegr);
            pref.setEntryValues(R.array.preference_image_format_values_nojpegr);
        }

        if( !supports_raw ) {
            Preference pref = findPreference("preference_raw");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }
        else {
            ListPreference pref = (ListPreference)findPreference("preference_raw");

            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
                // RAW only mode requires at least Android 7; earlier versions seem to have poorer support for DNG files
                pref.setEntries(R.array.preference_raw_entries_preandroid7);
                pref.setEntryValues(R.array.preference_raw_values_preandroid7);
            }

            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked raw: " + newValue);
                    if( newValue.equals("preference_raw_yes") || newValue.equals("preference_raw_only") ) {
                        // we check done_raw_info every time, so that this works if the user selects RAW again without leaving and returning to Settings
                        boolean done_raw_info = sharedPreferences.contains(PreferenceKeys.RawInfoPreferenceKey);
                        if( !done_raw_info ) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubPhoto.this.getActivity());
                            alertDialog.setTitle(R.string.preference_raw);
                            alertDialog.setMessage(R.string.raw_info);
                            alertDialog.setPositiveButton(android.R.string.ok, null);
                            alertDialog.setNegativeButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "user clicked dont_show_again for raw info dialog");
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putBoolean(PreferenceKeys.RawInfoPreferenceKey, true);
                                    editor.apply();
                                }
                            });
                            final AlertDialog alert = alertDialog.create();
                            // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface arg0) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "raw dialog dismissed");
                                    dialogs.remove(alert);
                                }
                            });
                            alert.show();
                            dialogs.add(alert);
                        }
                    }
                    return true;
                }
            });
        }

        if( !( supports_raw && supports_burst_raw ) ) {
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            Preference pref = findPreference("preference_raw_expo_bracketing");
            pg.removePreference(pref);
            pref = findPreference("preference_raw_focus_bracketing");
            pg.removePreference(pref);
        }

        if( !supports_optimise_focus_latency ) {
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            Preference pref = findPreference("preference_photo_optimise_focus");
            pg.removePreference(pref);
        }

        if( !supports_preshots ) {
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            Preference pref = findPreference("preference_save_preshots");
            pg.removePreference(pref);
        }

        if( !supports_nr ) {
            Preference pref = findPreference("preference_nr_save");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_hdr ) {
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");

            Preference pref = findPreference("preference_hdr_save_expo");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_tonemapping");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_contrast_enhancement");
            pg.removePreference(pref);
        }

        if( !supports_expo_bracketing || max_expo_bracketing_n_images <= 3 ) {
            Preference pref = findPreference("preference_expo_bracketing_n_images");
            //PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }
        if( !supports_expo_bracketing ) {
            Preference pref = findPreference("preference_expo_bracketing_stops");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if( !supports_panorama ) {
            //PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");

            Preference pref = findPreference("preference_panorama_crop");
            pg.removePreference(pref);

            pref = findPreference("preference_panorama_save");
            pg.removePreference(pref);
        }

        if( !using_android_l ) {
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");

            Preference pref = findPreference("preference_camera2_fake_flash");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_dummy_capture_hack");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_fast_burst");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_photo_video_recording");
            pg.removePreference(pref);
        }
        else {
            if( !supports_photo_video_recording ) {
                Preference pref = findPreference("preference_camera2_photo_video_recording");
                PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
                pg.removePreference(pref);
            }
        }

        {
            // remove preference_category_photo_debugging category if empty (which will be the case for old api)
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
            if( MyDebug.LOG )
                Log.d(TAG, "preference_category_photo_debugging children: " + pg.getPreferenceCount());
            if( pg.getPreferenceCount() == 0 ) {
                // pg.getParent() requires API level 26
                //PreferenceGroup parent = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
                PreferenceGroup parent = (PreferenceGroup)this.findPreference("preferences_root");
                parent.removePreference(pg);
            }
        }

        MyPreferenceFragment.setSummary(findPreference("preference_exif_artist"));
        MyPreferenceFragment.setSummary(findPreference("preference_exif_copyright"));
        MyPreferenceFragment.setSummary(findPreference("preference_textstamp"));

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }
}
