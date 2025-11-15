package com.linkedcamera.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class PreferenceSubLicences extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubLicences";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_licences);

        {
            final Preference pref = findPreference("preference_licence_open_camera");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_open_camera") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked open camera licence");
                        // display the GPL v3 text
                        displayTextDialog(R.string.preference_licence_open_camera, "gpl-3.0.txt");
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_licence_androidx");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_androidx") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked androidx licence");
                        // display the Apache licence 2.0 text
                        displayTextDialog(R.string.preference_licence_androidx, "androidx_LICENSE-2.0.txt");
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_licence_google_icons");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_google_icons") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked google material design icons licence");
                        // display the Apache licence 2.0 text
                        displayTextDialog(R.string.preference_licence_google_icons, "google_material_design_icons_LICENSE-2.0.txt");
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_licence_online");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_online") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked online licences");
                        MainActivity main_activity = (MainActivity)PreferenceSubLicences.this.getActivity();
                        main_activity.launchOnlineLicences();
                        return false;
                    }
                    return false;
                }
            });
        }

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate done");
    }

    /* Displays a dialog with text loaded from a file in assets.
     */
    private void displayTextDialog(int title_id, String file) {
        try {
            InputStream inputStream = getActivity().getAssets().open(file);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubLicences.this.getActivity());
            alertDialog.setTitle(getActivity().getResources().getString(title_id));
            alertDialog.setMessage(scanner.next());
            alertDialog.setPositiveButton(android.R.string.ok, null);
            final AlertDialog alert = alertDialog.create();
            // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface arg0) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "text dialog dismissed");
                    dialogs.remove(alert);
                }
            });
            alert.show();
            dialogs.add(alert);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "failed to load text for dialog", e);
        }
    }
}
