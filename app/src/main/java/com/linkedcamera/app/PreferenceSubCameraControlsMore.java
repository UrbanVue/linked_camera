package com.linkedcamera.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.linkedcamera.app.ui.FolderChooserDialog;

import java.io.File;

public class PreferenceSubCameraControlsMore extends PreferenceSubScreen {
    private static final String TAG = "PfSubCameraControlsMore";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_camera_controls_more);

        final Bundle bundle = getArguments();
        /*final int cameraId = bundle.getInt("cameraId");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraId: " + cameraId);
        final int nCameras = bundle.getInt("nCameras");
        if( MyDebug.LOG )
            Log.d(TAG, "nCameras: " + nCameras);*/

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");
        if( MyDebug.LOG )
            Log.d(TAG, "can_disable_shutter_sound: " + can_disable_shutter_sound);
        if( !can_disable_shutter_sound ) {
            // Camera.enableShutterSound requires JELLY_BEAN_MR1 or greater
            Preference pref = findPreference("preference_shutter_sound");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_camera_controls_more");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        /*{
        	EditTextPreference edit = (EditTextPreference)findPreference("preference_save_location");
        	InputFilter filter = new InputFilter() {
        		// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
        		String disallowed = "|\\?*<\":>";
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    for(int i=start;i<end;i++) {
                    	if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
                            return "";
                    	}
                    }
                    return null;
                }
        	};
        	edit.getEditText().setFilters(new InputFilter[]{filter});
        }*/
        {
            Preference pref = findPreference("preference_save_location");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked save location");
                    MainActivity main_activity = (MainActivity)PreferenceSubCameraControlsMore.this.getActivity();
                    if( main_activity.getStorageUtils().isUsingSAF() ) {
                        main_activity.openFolderChooserDialogSAF(true);
                        return true;
                    }
                    else if( MainActivity.useScopedStorage() ) {
                        // we can't use an EditTextPreference (or MyEditTextPreference) due to having to support non-scoped-storage, or when SAF is enabled...
                        // anyhow, this means we can share code when called from gallery long-press anyway
                        AlertDialog.Builder alertDialog = main_activity.createSaveFolderDialog();
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "save folder dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return true;
                    }
                    else {
                        File start_folder = main_activity.getStorageUtils().getImageFolder();

                        FolderChooserDialog fragment = new MyPreferenceFragment.SaveFolderChooserDialog();
                        fragment.setStartFolder(start_folder);
                        fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
                        return true;
                    }
                }
            });
        }

        {
            final Preference pref = findPreference("preference_using_saf");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_using_saf") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked saf");
                        if( sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false) ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "saf is now enabled");
                            // seems better to alway re-show the dialog when the user selects, to make it clear where files will be saved (as the SAF location in general will be different to the non-SAF one)
                            //String uri = sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "");
                            //if( uri.length() == 0 )
                            {
                                MainActivity main_activity = (MainActivity)PreferenceSubCameraControlsMore.this.getActivity();
                                Toast.makeText(main_activity, R.string.saf_select_save_location, Toast.LENGTH_SHORT).show();
                                main_activity.openFolderChooserDialogSAF(true);
                            }
                        }
                        else {
                            if( MyDebug.LOG )
                                Log.d(TAG, "saf is now disabled");
                            // need to update the summary, as switching back to non-SAF folder
                            MyPreferenceFragment.setSummary(findPreference("preference_save_location"));
                        }
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_calibrate_level");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_calibrate_level") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked calibrate level option");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubCameraControlsMore.this.getActivity());
                        alertDialog.setTitle(getActivity().getResources().getString(R.string.preference_calibrate_level));
                        alertDialog.setMessage(R.string.preference_calibrate_level_dialog);
                        alertDialog.setPositiveButton(R.string.preference_calibrate_level_calibrate, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked calibrate level");
                                MainActivity main_activity = (MainActivity)PreferenceSubCameraControlsMore.this.getActivity();
                                if( main_activity.getPreview().hasLevelAngleStable() ) {
                                    double current_level_angle = main_activity.getPreview().getLevelAngleUncalibrated();
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, (float)current_level_angle);
                                    editor.apply();
                                    main_activity.getPreview().updateLevelAngles();
                                    Toast.makeText(main_activity, R.string.preference_calibrate_level_calibrated, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        alertDialog.setNegativeButton(R.string.preference_calibrate_level_reset, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked reset calibration level");
                                MainActivity main_activity = (MainActivity)PreferenceSubCameraControlsMore.this.getActivity();
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f);
                                editor.apply();
                                main_activity.getPreview().updateLevelAngles();
                                Toast.makeText(main_activity, R.string.preference_calibrate_level_calibration_reset, Toast.LENGTH_SHORT).show();
                            }
                        });
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "calibration dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return false;
                    }
                    return false;
                }
            });
        }

        // preference_save_location done in onResume
        MyPreferenceFragment.setSummary(findPreference("preference_save_photo_prefix"));
        MyPreferenceFragment.setSummary(findPreference("preference_save_video_prefix"));

        setupDependencies();

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    @Override
    public void onResume() {
        super.onResume();

        // we need to call this onResume too, to handle updating the summary when changing location via SAF dialoga
        MyPreferenceFragment.setSummary(findPreference("preference_save_location"));
    }

    /** Programmatically set up dependencies for preference types (e.g., ListPreference) that don't
     *  support this in xml (such as SwitchPreference and CheckBoxPreference), or where this depends
     *  on the device (e.g., Android version).
     */
    private void setupDependencies() {
        // set up dependency for preference_audio_noise_control_sensitivity on preference_audio_control
        ListPreference pref = (ListPreference)findPreference("preference_audio_control");
        if( pref != null ) { // may be null if preference not supported
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    String value = newValue.toString();
                    setAudioNoiseControlSensitivityDependency(value);
                    return true;
                }
            });
            setAudioNoiseControlSensitivityDependency(pref.getValue()); // ensure dependency is enabled/disabled as required for initial value
        }
    }

    private void setAudioNoiseControlSensitivityDependency(String newValue) {
        Preference dependent = findPreference("preference_audio_noise_control_sensitivity");
        if( dependent != null ) { // just in case
            boolean enable_dependent = "noise".equals(newValue);
            if( MyDebug.LOG )
                Log.d(TAG, "clicked audio control: " + newValue + " enable_dependent: " + enable_dependent);
            dependent.setEnabled(enable_dependent);
        }
    }

}
