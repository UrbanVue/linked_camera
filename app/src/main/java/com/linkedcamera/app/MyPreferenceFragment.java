package com.linkedcamera.app;

import com.linkedcamera.app.ui.FolderChooserDialog;
import com.linkedcamera.app.ui.MyEditTextPreference;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
//import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
//import android.net.Uri;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Fragment to handle the Settings UI. Note that originally this was a
 *  PreferenceActivity rather than a PreferenceFragment which required all
 *  communication to be via the bundle (since this replaced the MainActivity,
 *  meaning we couldn't access data from that class. This no longer applies due
 *  to now using a PreferenceFragment, but I've still kept with transferring
 *  information via the bundle (for the most part, at least).
 *  Also note that passing via a bundle may be necessary to avoid accessing the
 *  preview, which can be null - see note about video resolutions below.
 *  Also see https://stackoverflow.com/questions/14093438/after-the-rotate-oncreate-fragment-is-called-before-oncreate-fragmentactivi .
 *  If the application is destroyed when in background when the user is viewing
 *  the settings, then the application and its fragments will be recreated -
 *  so reading from the bundle means the state is restored, where as trying
 *  to read camera settings won't be possible as the camera won't yet be
 *  reopened.
 */
public class MyPreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    private static final String TAG = "MyPreferenceFragment";

    private boolean edge_to_edge_mode = false;

    private int cameraId;

    /* Any AlertDialogs we create should be added to dialogs, and removed when dismissed. Any dialogs still
     * opened when onDestroy() is called are closed.
     * Normally this shouldn't be needed - the settings is usually only closed by the user pressing Back,
     * which can only be done once any opened dialogs are also closed. But this is required if we want to
     * programmatically close the settings - this is done in MainActivity.onNewIntent(), so that if Open Camera
     * is launched from the homescreen again when the settings was opened, we close the settings.
     * UPDATE: At the time of writing, we don't set android:launchMode="singleTask", so onNewIntent() is not called,
     * so this code isn't necessary - but there shouldn't be harm to leave it here for future use.
     */
    private final HashSet<AlertDialog> dialogs = new HashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        final Bundle bundle = getArguments();
        this.edge_to_edge_mode = bundle.getBoolean("edge_to_edge_mode");
        this.cameraId = bundle.getInt("cameraId");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraId: " + cameraId);
        final int nCameras = bundle.getInt("nCameras");
        if( MyDebug.LOG )
            Log.d(TAG, "nCameras: " + nCameras);

        final boolean camera_open = bundle.getBoolean("camera_open");
        if( MyDebug.LOG )
            Log.d(TAG, "camera_open: " + camera_open);

        final String camera_api = bundle.getString("camera_api");

        final String photo_mode_string = bundle.getString("photo_mode_string");

        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if( MyDebug.LOG )
            Log.d(TAG, "using_android_l: " + using_android_l);

        final int camera_orientation = bundle.getInt("camera_orientation");
        if( MyDebug.LOG )
            Log.d(TAG, "camera_orientation: " + camera_orientation);

        final float min_zoom_factor = bundle.getFloat("min_zoom_factor");
        final float max_zoom_factor = bundle.getFloat("max_zoom_factor");

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

		/*if( !supports_auto_stabilise ) {
			Preference pref = findPreference("preference_auto_stabilise");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_camera_effects");
        	pg.removePreference(pref);
		}*/

        //readFromBundle(bundle, "color_effects", Preview.getColorEffectPreferenceKey(), Camera.Parameters.EFFECT_NONE, "preference_category_camera_effects");
        //readFromBundle(bundle, "scene_modes", Preview.getSceneModePreferenceKey(), Camera.Parameters.SCENE_MODE_AUTO, "preference_category_camera_effects");
        //readFromBundle(bundle, "white_balances", Preview.getWhiteBalancePreferenceKey(), Camera.Parameters.WHITE_BALANCE_AUTO, "preference_category_camera_effects");
        //readFromBundle(bundle, "isos", Preview.getISOPreferenceKey(), "auto", "preference_category_camera_effects");
        //readFromBundle(bundle, "exposures", "preference_exposure", "0", "preference_category_camera_effects");

        final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_face_detection: " + supports_face_detection);

        if( !supports_face_detection  && ( camera_open || sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false) == false ) ) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.FaceDetectionPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_camera_controls");
            pg.removePreference(pref);
        }

        final int preview_width = bundle.getInt("preview_width");
        final int preview_height = bundle.getInt("preview_height");
        final int [] preview_widths = bundle.getIntArray("preview_widths");
        final int [] preview_heights = bundle.getIntArray("preview_heights");
        final int [] video_widths = bundle.getIntArray("video_widths");
        final int [] video_heights = bundle.getIntArray("video_heights");

        final int resolution_width = bundle.getInt("resolution_width");
        final int resolution_height = bundle.getInt("resolution_height");
        final int [] widths = bundle.getIntArray("resolution_widths");
        final int [] heights = bundle.getIntArray("resolution_heights");
        final boolean [] supports_burst = bundle.getBooleanArray("resolution_supports_burst");

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_raw: " + supports_raw);

        final boolean supports_hdr = bundle.getBoolean("supports_hdr");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_hdr: " + supports_hdr);

        final boolean supports_panorama = bundle.getBoolean("supports_panorama");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_panorama: " + supports_panorama);

        final boolean has_gyro_sensors = bundle.getBoolean("has_gyro_sensors");
        if( MyDebug.LOG )
            Log.d(TAG, "has_gyro_sensors: " + has_gyro_sensors);

        final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_expo_bracketing: " + supports_expo_bracketing);

        final boolean supports_exposure_compensation = bundle.getBoolean("supports_exposure_compensation");
        final int exposure_compensation_min = bundle.getInt("exposure_compensation_min");
        final int exposure_compensation_max = bundle.getInt("exposure_compensation_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_exposure_compensation: " + supports_exposure_compensation);
            Log.d(TAG, "exposure_compensation_min: " + exposure_compensation_min);
            Log.d(TAG, "exposure_compensation_max: " + exposure_compensation_max);
        }

        final boolean supports_iso_range = bundle.getBoolean("supports_iso_range");
        final int iso_range_min = bundle.getInt("iso_range_min");
        final int iso_range_max = bundle.getInt("iso_range_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_iso_range: " + supports_iso_range);
            Log.d(TAG, "iso_range_min: " + iso_range_min);
            Log.d(TAG, "iso_range_max: " + iso_range_max);
        }

        final boolean supports_exposure_time = bundle.getBoolean("supports_exposure_time");
        final long exposure_time_min = bundle.getLong("exposure_time_min");
        final long exposure_time_max = bundle.getLong("exposure_time_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_exposure_time: " + supports_exposure_time);
            Log.d(TAG, "exposure_time_min: " + exposure_time_min);
            Log.d(TAG, "exposure_time_max: " + exposure_time_max);
        }

        final boolean supports_white_balance_temperature = bundle.getBoolean("supports_white_balance_temperature");
        final int white_balance_temperature_min = bundle.getInt("white_balance_temperature_min");
        final int white_balance_temperature_max = bundle.getInt("white_balance_temperature_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_white_balance_temperature: " + supports_white_balance_temperature);
            Log.d(TAG, "white_balance_temperature_min: " + white_balance_temperature_min);
            Log.d(TAG, "white_balance_temperature_max: " + white_balance_temperature_max);
        }

        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        if( MyDebug.LOG )
            Log.d(TAG, "is_multi_cam: " + is_multi_cam);

        final String [] video_quality = bundle.getStringArray("video_quality");

        final String current_video_quality = bundle.getString("current_video_quality");
        final int video_frame_width = bundle.getInt("video_frame_width");
        final int video_frame_height = bundle.getInt("video_frame_height");
        final int video_bit_rate = bundle.getInt("video_bit_rate");
        final int video_frame_rate = bundle.getInt("video_frame_rate");
        final double video_capture_rate = bundle.getDouble("video_capture_rate");
        final boolean video_high_speed = bundle.getBoolean("video_high_speed");
        final float video_capture_rate_factor = bundle.getFloat("video_capture_rate_factor");

        final boolean supports_optical_stabilization = bundle.getBoolean("supports_optical_stabilization");
        final boolean optical_stabilization_enabled = bundle.getBoolean("optical_stabilization_enabled");

        final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_video_stabilization: " + supports_video_stabilization);

        final boolean video_stabilization_enabled = bundle.getBoolean("video_stabilization_enabled");

        final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");
        if( MyDebug.LOG )
            Log.d(TAG, "can_disable_shutter_sound: " + can_disable_shutter_sound);

        final int tonemap_max_curve_points = bundle.getInt("tonemap_max_curve_points");
        final boolean supports_tonemap_curve = bundle.getBoolean("supports_tonemap_curve");
        if( MyDebug.LOG ) {
            Log.d(TAG, "tonemap_max_curve_points: " + tonemap_max_curve_points);
            Log.d(TAG, "supports_tonemap_curve: " + supports_tonemap_curve);
        }

        final float camera_view_angle_x = bundle.getFloat("camera_view_angle_x");
        final float camera_view_angle_y = bundle.getFloat("camera_view_angle_y");
        if( MyDebug.LOG ) {
            Log.d(TAG, "camera_view_angle_x: " + camera_view_angle_x);
            Log.d(TAG, "camera_view_angle_y: " + camera_view_angle_y);
        }

        {
            List<String> camera_api_values = new ArrayList<>();
            List<String> camera_api_entries = new ArrayList<>();

            // all devices support old api
            camera_api_values.add("preference_camera_api_old");
            camera_api_entries.add(getActivity().getResources().getString(R.string.preference_camera_api_old));

            final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
            if( MyDebug.LOG )
                Log.d(TAG, "supports_camera2: " + supports_camera2);
            if( supports_camera2 ) {
                camera_api_values.add("preference_camera_api_camera2");
                camera_api_entries.add(getActivity().getResources().getString(R.string.preference_camera_api_camera2));
            }

            if( camera_api_values.size() == 1 ) {
                // if only supports 1 API, no point showing the preference
                camera_api_values.clear();
                camera_api_entries.clear();
            }

            readFromBundle(camera_api_values.toArray(new String[0]), camera_api_entries.toArray(new String[0]), "preference_camera_api", PreferenceKeys.CameraAPIPreferenceDefault, "preference_category_camera_quality");

            if( camera_api_values.size() >= 2 ) {
                final Preference pref = findPreference("preference_camera_api");
                pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference arg0, Object newValue) {
                        if( pref.getKey().equals("preference_camera_api") ) {
                            ListPreference list_pref = (ListPreference)pref;
                            if( list_pref.getValue().equals(newValue) ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user selected same camera API");
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user changed camera API - need to restart");
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                main_activity.restartOpenCamera();
                            }
                        }
                        return true;
                    }
                });
            }
        }
        /*final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_camera2: " + supports_camera2);
        if( supports_camera2 ) {
            final Preference pref = findPreference("preference_use_camera2");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_use_camera2") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked camera2 API - need to restart");
                        MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                        main_activity.restartOpenCamera();
                        return false;
                    }
                    return false;
                }
            });
        }
        else {
            Preference pref = findPreference("preference_use_camera2");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_online");
            pg.removePreference(pref);
        }*/

        {
            final Preference pref = findPreference("preference_online_help");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_online_help") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked about");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setTitle(R.string.preference_online_help);
                        alertDialog.setMessage(R.string.about_content);
                        alertDialog.setPositiveButton(android.R.string.ok, null);
                        alertDialog.setNegativeButton(R.string.github, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "github link");
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                main_activity.launchOnlineHelp();
                            }
                        });
                        alertDialog.show();
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_privacy_policy");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_privacy_policy") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked privacy policy");

                        clickedPrivacyPolicy();
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_about");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_about") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked about");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setTitle(R.string.preference_about);
                        final StringBuilder about_string = new StringBuilder();
                        String version = "UNKNOWN_VERSION";
                        int version_code = -1;
                        try {
                            PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
                            version = pInfo.versionName;
                            version_code = pInfo.versionCode;
                        }
                        catch(NameNotFoundException e) {
                            MyDebug.logStackTrace(TAG, "NameNotFoundException exception trying to get version number", e);
                        }
                        about_string.append("Linked Camera v");
                        about_string.append(version);
                        about_string.append("\nCode: ");
                        about_string.append(version_code);
                        about_string.append("\nPackage: ");
                        about_string.append(MyPreferenceFragment.this.getActivity().getPackageName());
                        about_string.append("\nAndroid API version: ");
                        about_string.append(Build.VERSION.SDK_INT);
                        about_string.append("\nDevice manufacturer: ");
                        about_string.append(Build.MANUFACTURER);
                        about_string.append("\nDevice model: ");
                        about_string.append(Build.MODEL);
                        if( Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ) {
                            // use non-deprecated equivalent of Display.getSize()
                            WindowMetrics window_metrics = MyPreferenceFragment.this.getActivity().getWindowManager().getCurrentWindowMetrics();
                            final WindowInsets windowInsets = window_metrics.getWindowInsets();
                            Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                            int insetsWidth = insets.right + insets.left;
                            int insetsHeight = insets.top + insets.bottom;
                            final Rect bounds = window_metrics.getBounds();
                            int display_x = bounds.width() - insetsWidth;
                            int display_y = bounds.height() - insetsHeight;
                            about_string.append("\nDisplay size: ");
                            about_string.append(display_x);
                            about_string.append("x");
                            about_string.append(display_y);
                        }
                        else {
                            Point display_size = new Point();
                            Display display = MyPreferenceFragment.this.getActivity().getWindowManager().getDefaultDisplay();
                            display.getSize(display_size);
                            about_string.append("\nDisplay size: ");
                            about_string.append(display_size.x);
                            about_string.append("x");
                            about_string.append(display_size.y);
                        }
                        about_string.append("\nCurrent camera ID: ");
                        about_string.append(cameraId);
                        about_string.append("\nNo. of cameras: ");
                        about_string.append(nCameras);
                        about_string.append("\nMulti-camera?: ");
                        about_string.append(is_multi_cam);
                        about_string.append("\nCamera API: ");
                        about_string.append(camera_api);
                        about_string.append("\nCamera orientation: ");
                        about_string.append(camera_orientation);
                        about_string.append("\nPhoto mode: ");
                        about_string.append(photo_mode_string==null ? "UNKNOWN" : photo_mode_string);
                        {
                            String last_video_error = sharedPreferences.getString("last_video_error", "");
                            if( !last_video_error.isEmpty() ) {
                                about_string.append("\nLast video error: ");
                                about_string.append(last_video_error);
                            }
                        }
                        about_string.append("\nMin zoom factor: ");
                        about_string.append(min_zoom_factor);
                        about_string.append("\nMax zoom factor: ");
                        about_string.append(max_zoom_factor);
                        if( preview_widths != null && preview_heights != null ) {
                            about_string.append("\nPreview resolutions: ");
                            for(int i=0;i<preview_widths.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(preview_widths[i]);
                                about_string.append("x");
                                about_string.append(preview_heights[i]);
                            }
                        }
                        about_string.append("\nPreview resolution: ");
                        about_string.append(preview_width);
                        about_string.append("x");
                        about_string.append(preview_height);
                        if( widths != null && heights != null ) {
                            about_string.append("\nPhoto resolutions: ");
                            for(int i=0;i<widths.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(widths[i]);
                                about_string.append("x");
                                about_string.append(heights[i]);
                                if( supports_burst != null && !supports_burst[i] ) {
                                    about_string.append("[no burst]");
                                }
                            }
                        }
                        about_string.append("\nPhoto resolution: ");
                        about_string.append(resolution_width);
                        about_string.append("x");
                        about_string.append(resolution_height);
                        if( video_quality != null ) {
                            about_string.append("\nVideo qualities: ");
                            for(int i=0;i<video_quality.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(video_quality[i]);
                            }
                        }
                        if( video_widths != null && video_heights != null ) {
                            about_string.append("\nVideo resolutions: ");
                            for(int i=0;i<video_widths.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(video_widths[i]);
                                about_string.append("x");
                                about_string.append(video_heights[i]);
                            }
                        }
                        about_string.append("\nVideo quality: ");
                        about_string.append(current_video_quality);
                        about_string.append("\nVideo frame width: ");
                        about_string.append(video_frame_width);
                        about_string.append("\nVideo frame height: ");
                        about_string.append(video_frame_height);
                        about_string.append("\nVideo bit rate: ");
                        about_string.append(video_bit_rate);
                        about_string.append("\nVideo frame rate: ");
                        about_string.append(video_frame_rate);
                        about_string.append("\nVideo capture rate: ");
                        about_string.append(video_capture_rate);
                        about_string.append("\nVideo high speed: ");
                        about_string.append(video_high_speed);
                        about_string.append("\nVideo capture rate factor: ");
                        about_string.append(video_capture_rate_factor);
                        about_string.append("\nAuto-level?: ");
                        about_string.append(getString(supports_auto_stabilise ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nAuto-level enabled?: ");
                        about_string.append(sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false));
                        about_string.append("\nFace detection?: ");
                        about_string.append(getString(supports_face_detection ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nRAW?: ");
                        about_string.append(getString(supports_raw ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nHDR?: ");
                        about_string.append(getString(supports_hdr ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nPanorama?: ");
                        about_string.append(getString(supports_panorama ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nGyro sensors?: ");
                        about_string.append(getString(has_gyro_sensors ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nExpo?: ");
                        about_string.append(getString(supports_expo_bracketing ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nExpo compensation?: ");
                        about_string.append(getString(supports_exposure_compensation ? R.string.about_available : R.string.about_not_available));
                        if( supports_exposure_compensation ) {
                            about_string.append("\nExposure compensation range: ");
                            about_string.append(exposure_compensation_min);
                            about_string.append(" to ");
                            about_string.append(exposure_compensation_max);
                        }
                        about_string.append("\nManual ISO?: ");
                        about_string.append(getString(supports_iso_range ? R.string.about_available : R.string.about_not_available));
                        if( supports_iso_range ) {
                            about_string.append("\nISO range: ");
                            about_string.append(iso_range_min);
                            about_string.append(" to ");
                            about_string.append(iso_range_max);
                        }
                        about_string.append("\nManual exposure?: ");
                        about_string.append(getString(supports_exposure_time ? R.string.about_available : R.string.about_not_available));
                        if( supports_exposure_time ) {
                            about_string.append("\nExposure range: ");
                            about_string.append(exposure_time_min);
                            about_string.append(" to ");
                            about_string.append(exposure_time_max);
                        }
                        about_string.append("\nManual WB?: ");
                        about_string.append(getString(supports_white_balance_temperature ? R.string.about_available : R.string.about_not_available));
                        if( supports_white_balance_temperature ) {
                            about_string.append("\nWB temperature: ");
                            about_string.append(white_balance_temperature_min);
                            about_string.append(" to ");
                            about_string.append(white_balance_temperature_max);
                        }
                        about_string.append("\nOptical stabilization?: ");
                        about_string.append(getString(supports_optical_stabilization ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nOptical stabilization enabled?: ");
                        about_string.append(optical_stabilization_enabled);
                        about_string.append("\nVideo stabilization?: ");
                        about_string.append(getString(supports_video_stabilization ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nVideo stabilization enabled?: ");
                        about_string.append(video_stabilization_enabled);
                        about_string.append("\nTonemap curve?: ");
                        about_string.append(getString(supports_tonemap_curve ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nTonemap max curve points: ");
                        about_string.append(tonemap_max_curve_points);
                        about_string.append("\nCan disable shutter sound?: ");
                        about_string.append(getString(can_disable_shutter_sound ? R.string.about_available : R.string.about_not_available));

                        about_string.append("\nCamera view angle: ").append(camera_view_angle_x).append(" , ").append(camera_view_angle_y);

                        about_string.append("\nFlash modes: ");
                        String [] flash_values = bundle.getStringArray("flash_values");
                        if( flash_values != null && flash_values.length > 0 ) {
                            for(int i=0;i<flash_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(flash_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nFocus modes: ");
                        String [] focus_values = bundle.getStringArray("focus_values");
                        if( focus_values != null && focus_values.length > 0 ) {
                            for(int i=0;i<focus_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(focus_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nColor effects: ");
                        String [] color_effects_values = bundle.getStringArray("color_effects");
                        if( color_effects_values != null && color_effects_values.length > 0 ) {
                            for(int i=0;i<color_effects_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(color_effects_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nScene modes: ");
                        String [] scene_modes_values = bundle.getStringArray("scene_modes");
                        if( scene_modes_values != null && scene_modes_values.length > 0 ) {
                            for(int i=0;i<scene_modes_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(scene_modes_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nWhite balances: ");
                        String [] white_balances_values = bundle.getStringArray("white_balances");
                        if( white_balances_values != null && white_balances_values.length > 0 ) {
                            for(int i=0;i<white_balances_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(white_balances_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        if( !using_android_l ) {
                            about_string.append("\nISOs: ");
                            String[] isos = bundle.getStringArray("isos");
                            if (isos != null && isos.length > 0) {
                                for (int i = 0; i < isos.length; i++) {
                                    if (i > 0) {
                                        about_string.append(", ");
                                    }
                                    about_string.append(isos[i]);
                                }
                            } else {
                                about_string.append("None");
                            }
                            String iso_key = bundle.getString("iso_key");
                            if (iso_key != null) {
                                about_string.append("\nISO key: ");
                                about_string.append(iso_key);
                            }
                        }

                        int magnetic_accuracy = bundle.getInt("magnetic_accuracy");
                        about_string.append("\nMagnetic accuracy?: ");
                        about_string.append(magnetic_accuracy);

                        about_string.append("\nUsing SAF?: ");
                        about_string.append(sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false));
                        String save_location = sharedPreferences.getString(PreferenceKeys.SaveLocationPreferenceKey, "LinkedCamera");
                        about_string.append("\nSave Location: ");
                        about_string.append(save_location);
                        String save_location_saf = sharedPreferences.getString(PreferenceKeys.SaveLocationSAFPreferenceKey, "");
                        about_string.append("\nSave Location SAF: ");
                        about_string.append(save_location_saf);

                        about_string.append("\nParameters: ");
                        String parameters_string = bundle.getString("parameters_string");
                        if( parameters_string != null ) {
                            about_string.append(parameters_string);
                        }
                        else {
                            about_string.append("None");
                        }

                        SpannableString span = new SpannableString(about_string);

                        // clickable text is only supported if we call setMovementMethod on the TextView - which means we need to create
                        // our own for the AlertDialog!
                        @SuppressLint("InflateParams") // we add the view to the alert dialog in addTextViewForAlertDialog()
                        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_textview, null);
                        final TextView textView = dialog_view.findViewById(R.id.text_view);

                        textView.setText(span);
                        textView.setMovementMethod(LinkMovementMethod.getInstance());
                        textView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
                        addTextViewForAlertDialog(alertDialog, textView);
                        //alertDialog.setMessage(about_string);

                        alertDialog.setPositiveButton(android.R.string.ok, null);
                        alertDialog.setNegativeButton(R.string.about_copy_to_clipboard, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked copy to clipboard");
                                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("OpenCamera About", about_string);
                                clipboard.setPrimaryClip(clip);
                            }
                        });
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "about dialog dismissed");
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

        setupDependencies();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if( edge_to_edge_mode ) {
            handleEdgeToEdge(view);
        }
    }

    static void handleEdgeToEdge(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            //androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // don't need to avoid WindowInsetsCompat.Type.displayCutout(), as we already do this for the entire activity (see MainActivity's setOnApplyWindowInsetsListener)
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        view.requestApplyInsets();
    }

    /** Adds a TextView to an AlertDialog builder, placing it inside a scrollview and adding appropriate padding.
     */
    private void addTextViewForAlertDialog(AlertDialog.Builder alertDialog, TextView textView) {
        final float scale = getActivity().getResources().getDisplayMetrics().density;
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.addView(textView);
        // padding values from /sdk/platforms/android-18/data/res/layout/alert_dialog.xml
        textView.setPadding((int)(5*scale+0.5f), (int)(5*scale+0.5f), (int)(5*scale+0.5f), (int)(5*scale+0.5f));
        scrollView.setPadding((int)(14*scale+0.5f), (int)(2*scale+0.5f), (int)(10*scale+0.5f), (int)(12*scale+0.5f));
        alertDialog.setView(scrollView);
    }

    /** Programmatically set up dependencies for preference types (e.g., ListPreference) that don't
     *  support this in xml (such as SwitchPreference and CheckBoxPreference), or where this depends
     *  on the device (e.g., Android version).
     */
    private void setupDependencies() {
    }

    /* The user clicked the privacy policy preference.
     */
    public void clickedPrivacyPolicy() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedPrivacyPolicy()");
        /*MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
        main_activity.launchOnlinePrivacyPolicy();*/

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
        alertDialog.setTitle(R.string.preference_privacy_policy);

        //SpannableString span = new SpannableString(getActivity().getResources().getString(R.string.preference_privacy_policy_text));
        //Linkify.addLinks(span, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        String privacy_policy_text = getActivity().getResources().getString(R.string.preference_privacy_policy_text);
        Spanned span = Html.fromHtml(privacy_policy_text);
        // clickable text is only supported if we call setMovementMethod on the TextView - which means we need to create
        // our own for the AlertDialog!
        @SuppressLint("InflateParams") // we add the view to the alert dialog in addTextViewForAlertDialog()
        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_textview, null);
        final TextView textView = dialog_view.findViewById(R.id.text_view);
        textView.setText(span);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
        addTextViewForAlertDialog(alertDialog, textView);
        //alertDialog.setMessage(R.string.preference_privacy_policy_text);

        alertDialog.setPositiveButton(android.R.string.ok, null);
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

    /** Removes an entry and value pair from a ListPreference, if it exists.
     * @param pref The ListPreference to remove the supplied entry/value.
     * @param filter_value The value to remove from the list.
     */
    static void filterArrayEntry(ListPreference pref, String filter_value) {
        {
            CharSequence [] orig_entries = pref.getEntries();
            CharSequence [] orig_values = pref.getEntryValues();
            List<CharSequence> new_entries = new ArrayList<>();
            List<CharSequence> new_values = new ArrayList<>();
            for(int i=0;i<orig_entries.length;i++) {
                CharSequence value = orig_values[i];
                if( !value.equals(filter_value) ) {
                    new_entries.add(orig_entries[i]);
                    new_values.add(value);
                }
            }
            CharSequence [] new_entries_arr = new CharSequence[new_entries.size()];
            new_entries.toArray(new_entries_arr);
            CharSequence [] new_values_arr = new CharSequence[new_values.size()];
            new_values.toArray(new_values_arr);
            pref.setEntries(new_entries_arr);
            pref.setEntryValues(new_values_arr);
        }
    }

    public static class SaveFolderChooserDialog extends FolderChooserDialog {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if( MyDebug.LOG )
                Log.d(TAG, "FolderChooserDialog dismissed");
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            MainActivity main_activity = (MainActivity)this.getActivity();
            if( main_activity != null ) { // main_activity may be null if this is being closed via MainActivity.onNewIntent()
                String new_save_location = this.getChosenFolder();
                main_activity.updateSaveFolder(new_save_location);
            }
            super.onDismiss(dialog);
        }
    }

    private void readFromBundle(String [] values, String [] entries, String preference_key, String default_value, String preference_category_key) {
        readFromBundle(this, values, entries, preference_key, default_value, preference_category_key);
    }

    static void readFromBundle(PreferenceFragment preference_fragment, String [] values, String [] entries, String preference_key, String default_value, String preference_category_key) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "readFromBundle");
        }
        if( values != null && values.length > 0 ) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "values:");
                for(String value : values) {
                    Log.d(TAG, value);
                }
            }
            ListPreference lp = (ListPreference)preference_fragment.findPreference(preference_key);
            lp.setEntries(entries);
            lp.setEntryValues(values);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(preference_fragment.getActivity());
            String value = sharedPreferences.getString(preference_key, default_value);
            if( MyDebug.LOG )
                Log.d(TAG, "    value: " + Arrays.toString(values));
            lp.setValue(value);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "remove preference " + preference_key + " from category " + preference_category_key);
            Preference pref = preference_fragment.findPreference(preference_key);
            PreferenceGroup pg = (PreferenceGroup)preference_fragment.findPreference(preference_category_key);
            pg.removePreference(pref);
        }
    }

    static void setBackground(Fragment fragment) {
        // prevent fragment being transparent
        // note, setting color here only seems to affect the "main" preference fragment screen, and not sub-screens
        // note, on Galaxy Nexus Android 4.3 this sets to black rather than the dark grey that the background theme should be (and what the sub-screens use); works okay on Nexus 7 Android 5
        // we used to use a light theme for the PreferenceFragment, but mixing themes in same activity seems to cause problems (e.g., for EditTextPreference colors)
        TypedArray array = fragment.getActivity().getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground
        });
        int backgroundColor = array.getColor(0, Color.BLACK);
		/*if( MyDebug.LOG ) {
			int r = (backgroundColor >> 16) & 0xFF;
			int g = (backgroundColor >> 8) & 0xFF;
			int b = (backgroundColor >> 0) & 0xFF;
			Log.d(TAG, "backgroundColor: " + r + " , " + g + " , " + b);
		}*/
        fragment.getView().setBackgroundColor(backgroundColor);
        array.recycle();
    }

    @Override
    public void onResume() {
        super.onResume();

        setBackground(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        super.onDestroy();

        if( MyDebug.LOG )
            Log.d(TAG, "isRemoving?: " + isRemoving());

        if( isRemoving() ) {
            // if isRemoving()==true, then it means the fragment is being removed and we are returning to the activity
            // if isRemoving()==false, then it may be that the activity is being destroyed
            ((MainActivity)getActivity()).settingsClosing();
        }

        dismissDialogs(getFragmentManager(), dialogs);
    }

    static void dismissDialogs(FragmentManager fragment_manager, HashSet<AlertDialog> dialogs) {
        // dismiss open dialogs - see comment for dialogs for why we do this
        for(AlertDialog dialog : dialogs) {
            if( MyDebug.LOG )
                Log.d(TAG, "dismiss dialog: " + dialog);
            dialog.dismiss();
        }
        // similarly dimiss any dialog fragments still opened
        Fragment folder_fragment = fragment_manager.findFragmentByTag("FOLDER_FRAGMENT");
        if( folder_fragment != null ) {
            DialogFragment dialogFragment = (DialogFragment)folder_fragment;
            if( MyDebug.LOG )
                Log.d(TAG, "dismiss dialogFragment: " + dialogFragment);
            dialogFragment.dismissAllowingStateLoss();
        }
    }

    /* So that manual changes to the checkbox/switch preferences, while the preferences are showing, show up;
     * in particular, needed for preference_using_saf, when the user cancels the SAF dialog (see
     * MainActivity.onActivityResult).
     * Also programmatically sets summary (see setSummary).
     */
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if( MyDebug.LOG )
            Log.d(TAG, "onSharedPreferenceChanged: " + key);

        if( key == null ) {
            // On Android 11+, when targetting Android 11+, this method is called with key==null
            // if preferences are cleared. Unclear if this happens here in practice, but return
            // just in case.
            return;
        }

        Preference pref = findPreference(key);
        handleOnSharedPreferenceChanged(prefs, key, pref);
    }

    static void handleOnSharedPreferenceChanged(SharedPreferences prefs, String key, Preference pref) {
        if( MyDebug.LOG )
            Log.d(TAG, "handleOnSharedPreferenceChanged: " + key);

        if( pref == null ) {
            // this can happen if the shared preference that changed is for a sub-screen i.e. a different fragment
            if( MyDebug.LOG )
                Log.d(TAG, "handleOnSharedPreferenceChanged: preference doesn't belong to this fragment");
            return;
        }

        if( pref instanceof TwoStatePreference ) {
            TwoStatePreference twoStatePref = (TwoStatePreference)pref;
            twoStatePref.setChecked(prefs.getBoolean(key, true));
        }
        else if( pref instanceof  ListPreference ) {
            ListPreference listPref = (ListPreference)pref;
            listPref.setValue(prefs.getString(key, ""));
        }
        setSummary(pref);
    }

    /** Programmatically sets summaries as required.
     *  Remember to call setSummary() from the constructor for any keys we set, to initialise the
     *  summary.
     */
    static void setSummary(Preference pref) {
        if( pref instanceof EditTextPreference ) {
            /* We have a runtime check for using EditTextPreference - we don't want these due to importance of
             * supporting the Google Play emoji policy (see comment in MyEditTextPreference.java) - and this
             * helps guard against the risk of accidentally adding more EditTextPreferences in future.
             * Once we've switched to using Android X Preference library, and hence safe to use EditTextPreference
             * again, this code can be removed.
             */
            throw new RuntimeException("detected an EditTextPreference: " + pref.getKey() + " pref: " + pref);
        }

        if( pref.getKey().equals("preference_save_location") ) {
            // can't use %s (as only supported for ListPreference), so handle this directly
            MainActivity main_activity = (MainActivity)pref.getContext();
            String folder_name;
            if( main_activity.getStorageUtils().isUsingSAF() ) {
                folder_name = main_activity.getStorageUtils().getSaveLocationSAF();
            }
            else {
                folder_name = main_activity.getStorageUtils().getSaveLocation();
            }
            folder_name = main_activity.getHumanReadableSaveFolder(folder_name);
            String summary = main_activity.getResources().getString(R.string.preference_save_location_summary);
            if( !folder_name.isEmpty() ) {
                summary += "\n" + folder_name;
            }
            pref.setSummary(summary);
        }
        else if( pref instanceof EditTextPreference || pref instanceof MyEditTextPreference ) {
            // %s only supported for ListPreference
            // we also display the usual summary if no preference value is set
            if( pref.getKey().equals("preference_exif_artist") ||
                    pref.getKey().equals("preference_exif_copyright") ||
                    pref.getKey().equals("preference_save_photo_prefix") ||
                    pref.getKey().equals("preference_save_video_prefix") ||
                    pref.getKey().equals("preference_textstamp") ||
                    pref.getKey().equals("preference_nextcloud_url") ||
                    pref.getKey().equals("preference_nextcloud_password")
            ) {
                String default_value = "";
                if( pref.getKey().equals("preference_save_photo_prefix") )
                    default_value = "IMG_";
                else if( pref.getKey().equals("preference_save_video_prefix") )
                    default_value = "VID_";

                String current_value;
                if( pref instanceof EditTextPreference ) {
                    EditTextPreference editTextPref = (EditTextPreference)pref;
                    current_value = editTextPref.getText();
                }
                else {
                    MyEditTextPreference editTextPref = (MyEditTextPreference)pref;
                    current_value = editTextPref.getText();
                }

                if( current_value.equals(default_value) ) {
                    switch (pref.getKey()) {
                        case "preference_exif_artist":
                            pref.setSummary(R.string.preference_exif_artist_summary);
                            break;
                        case "preference_exif_copyright":
                            pref.setSummary(R.string.preference_exif_copyright_summary);
                            break;
                        case "preference_save_photo_prefix":
                            pref.setSummary(R.string.preference_save_photo_prefix_summary);
                            break;
                        case "preference_save_video_prefix":
                            pref.setSummary(R.string.preference_save_video_prefix_summary);
                            break;
                        case "preference_textstamp":
                            pref.setSummary(R.string.preference_textstamp_summary);
                            break;
                        case "preference_nextcloud_url":
                            pref.setSummary(R.string.preference_nextcloud_url_summary);
                            break;
                        case "preference_nextcloud_password":
                            pref.setSummary(R.string.preference_nextcloud_password_summary);
                            break;
                    }
                }
                else {
                    // non-default value, so display the current value
                    // For password, show asterisks instead of actual password
                    if( pref.getKey().equals("preference_nextcloud_password") ) {
                        pref.setSummary("••••••••");
                    }
                    else {
                        pref.setSummary(current_value);
                    }
                }
            }
        }
    }
}
