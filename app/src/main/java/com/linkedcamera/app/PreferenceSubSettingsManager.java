package com.linkedcamera.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.linkedcamera.app.ui.FolderChooserDialog;

import java.io.IOException;
import java.util.Date;

public class PreferenceSubSettingsManager extends PreferenceSubScreen {
    private static final String TAG = "PrefSubSettingsManager";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_sub_settings_manager);

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        {
            final Preference pref = findPreference("preference_save_settings");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_save_settings") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked save settings");

                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubSettingsManager.this.getActivity());
                        alertDialog.setTitle(R.string.preference_save_settings_filename);

                        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_edittext, null);
                        final EditText editText = dialog_view.findViewById(R.id.edit_text);

                        editText.setSingleLine();
                        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
                        editText.setHint(getResources().getString(R.string.preference_save_settings_filename));

                        alertDialog.setView(dialog_view);

                        final MainActivity main_activity = (MainActivity)PreferenceSubSettingsManager.this.getActivity();
                        try {
                            // find a default name - although we're only interested in the name rather than full path, this still
                            // requires checking the folder, so that we don't reuse an existing filename
                            String mediaFilename = main_activity.getStorageUtils().createOutputMediaFile(
                                    main_activity.getStorageUtils().getSettingsFolder(),
                                    StorageUtils.MEDIA_TYPE_PREFS, "", "xml", new Date()
                            ).getName();
                            if( MyDebug.LOG )
                                Log.d(TAG, "mediaFilename: " + mediaFilename);
                            int index = mediaFilename.lastIndexOf('.');
                            if( index != -1 ) {
                                // remove extension
                                mediaFilename = mediaFilename.substring(0, index);
                            }
                            editText.setText(mediaFilename);
                            editText.setSelection(mediaFilename.length());
                        }
                        catch(IOException e) {
                            MyDebug.logStackTrace(TAG, "failed to obtain a filename", e);
                        }

                        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "save settings clicked okay");

                                String filename = editText.getText().toString() + ".xml";
                                main_activity.getSettingsManager().saveSettings(filename);
                            }
                        });
                        alertDialog.setNegativeButton(android.R.string.cancel, null);
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "save settings dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        //MainActivity main_activity = (MainActivity)PreferenceSubSettingsManager.this.getActivity();
                        //main_activity.getSettingsManager().saveSettings();
                    }
                    return false;
                }
            });
        }
        {
            final Preference pref = findPreference("preference_restore_settings");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_restore_settings") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked restore settings");

                        loadSettings();
                    }
                    return false;
                }
            });
        }
        {
            final Preference pref = findPreference("preference_reset");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_reset") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked reset settings");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubSettingsManager.this.getActivity());
                        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                        alertDialog.setTitle(R.string.preference_reset);
                        alertDialog.setMessage(R.string.preference_reset_question);
                        alertDialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user confirmed reset");
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.clear();
                                editor.putBoolean(PreferenceKeys.FirstTimePreferenceKey, true);
                                try {
                                    PackageInfo pInfo = PreferenceSubSettingsManager.this.getActivity().getPackageManager().getPackageInfo(PreferenceSubSettingsManager.this.getActivity().getPackageName(), 0);
                                    int version_code = pInfo.versionCode;
                                    editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
                                }
                                catch(PackageManager.NameNotFoundException e) {
                                    MyDebug.logStackTrace(TAG, "NameNotFoundException trying to get version number", e);
                                }
                                editor.apply();
                                MainActivity main_activity = (MainActivity)PreferenceSubSettingsManager.this.getActivity();
                                main_activity.setDeviceDefaults();
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked reset - need to restart");
                                main_activity.restartOpenCamera();
                            }
                        });
                        alertDialog.setNegativeButton(android.R.string.no, null);
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "reset dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                    }
                    return false;
                }
            });
        }

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    private void loadSettings() {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings");
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubSettingsManager.this.getActivity());
        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialog.setTitle(R.string.preference_restore_settings);
        alertDialog.setMessage(R.string.preference_restore_settings_question);
        alertDialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if( MyDebug.LOG )
                    Log.d(TAG, "user confirmed to restore settings");
                MainActivity main_activity = (MainActivity)PreferenceSubSettingsManager.this.getActivity();
				/*if( main_activity.getStorageUtils().isUsingSAF() ) {
					main_activity.openLoadSettingsChooserDialogSAF(true);
				}
				else*/ {
                    FolderChooserDialog fragment = new PreferenceSubSettingsManager.LoadSettingsFileChooserDialog();
                    fragment.setShowDCIMShortcut(false);
                    fragment.setShowNewFolderButton(false);
                    fragment.setModeFolder(false);
                    fragment.setExtension(".xml");
                    fragment.setStartFolder(main_activity.getStorageUtils().getSettingsFolder());
                    if( MainActivity.useScopedStorage() ) {
                        // since we use File API to load, don't allow going outside of the application's folder, as we won't be able to read those files!
                        fragment.setMaxParent(main_activity.getExternalFilesDir(null));
                    }
                    fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
                }
            }
        });
        alertDialog.setNegativeButton(android.R.string.no, null);
        final AlertDialog alert = alertDialog.create();
        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                if( MyDebug.LOG )
                    Log.d(TAG, "reset dialog dismissed");
                dialogs.remove(alert);
            }
        });
        alert.show();
        dialogs.add(alert);
    }

    public static class LoadSettingsFileChooserDialog extends FolderChooserDialog {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if( MyDebug.LOG )
                Log.d(TAG, "FolderChooserDialog dismissed");
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            MainActivity main_activity = (MainActivity)this.getActivity();
            if( main_activity != null ) { // main_activity may be null if this is being closed via MainActivity.onNewIntent()
                String settings_file = this.getChosenFile();
                if( MyDebug.LOG )
                    Log.d(TAG, "settings_file: " + settings_file);
                if( settings_file != null ) {
                    main_activity.getSettingsManager().loadSettings(settings_file);
                }
            }
            super.onDismiss(dialog);
        }
    }
}
