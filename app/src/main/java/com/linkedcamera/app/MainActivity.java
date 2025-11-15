package com.linkedcamera.app;

import com.linkedcamera.app.cameracontroller.CameraController;
import com.linkedcamera.app.cameracontroller.CameraControllerManager;
import com.linkedcamera.app.cameracontroller.CameraControllerManager2;
import com.linkedcamera.app.preview.Preview;
import com.linkedcamera.app.preview.VideoProfile;
import com.linkedcamera.app.remotecontrol.BluetoothRemoteControl;
import com.linkedcamera.app.ui.DrawPreview;
import com.linkedcamera.app.ui.FolderChooserDialog;
import com.linkedcamera.app.ui.MainUI;
import com.linkedcamera.app.ui.ManualSeekbars;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.Manifest;
import android.app.Fragment;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.speech.tts.TextToSpeech;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.exifinterface.media.ExifInterface;

import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** The main Activity for Open Camera.
 */
public class MainActivity extends AppCompatActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {
    private static final String TAG = "MainActivity";

    private static int activity_count = 0;

    private boolean app_is_paused = true;

    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;

    // components: always non-null (after onCreate())
    private BluetoothRemoteControl bluetoothRemoteControl;
    private PermissionHandler permissionHandler;
    private SettingsManager settingsManager;
    private MainUI mainUI;
    private ManualSeekbars manualSeekbars;
    private MyApplicationInterface applicationInterface;
    private TextFormatter textFormatter;
    private SoundPoolManager soundPoolManager;
    private MagneticSensor magneticSensor;
    //private SpeechControl speechControl;

    private Preview preview;
    private OrientationEventListener orientationEventListener;
    private View.OnLayoutChangeListener layoutChangeListener;
    private int large_heap_memory;
    private boolean supports_auto_stabilise;
    private boolean supports_force_video_4k;
    private boolean supports_camera2;
    private SaveLocationHistory save_location_history; // save location for non-SAF
    private SaveLocationHistory save_location_history_saf; // save location for SAF (only initialised when SAF is used)
    private boolean saf_dialog_from_preferences; // if a SAF dialog is opened, this records whether we opened it from the Preferences
    private boolean camera_in_background; // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
    private GestureDetector gestureDetector;
    private boolean screen_is_locked; // whether screen is "locked" - this is Open Camera's own lock to guard against accidental presses, not the standard Android lock
    private final Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<>();
    private ValueAnimator gallery_save_anim;
    private boolean last_continuous_fast_burst; // whether the last photo operation was a continuous_fast_burst
    private Future<?> update_gallery_future;

    private TextToSpeech textToSpeech;
    private boolean textToSpeechSuccess;

    private AudioListener audio_listener; // may be null - created when needed

    //private boolean ui_placement_right = true;

    //private final boolean edge_to_edge_mode = false; // whether running always in edge-to-edge mode
    //private final boolean edge_to_edge_mode = true; // whether running always in edge-to-edge mode
    private final boolean edge_to_edge_mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM; // whether running always in edge-to-edge mode
    private boolean want_no_limits; // whether we want to run with FLAG_LAYOUT_NO_LIMITS
    private boolean set_window_insets_listener; // whether we've enabled a setOnApplyWindowInsetsListener()
    private int navigation_gap; // gap for navigation bar along bottom (portrait) or right (landscape)
    private int navigation_gap_landscape; // gap for navigation bar along left (portrait) or bottom (landscape); only set for edge_to_edge_mode==true
    private int navigation_gap_reverse_landscape; // gap for navigation bar along right (portrait) or top (landscape); only set for edge_to_edge_mode==true
    public static volatile boolean test_preview_want_no_limits; // test flag, if set to true then instead use test_preview_want_no_limits_value; needs to be static, as it needs to be set before activity is created to take effect
    public static volatile boolean test_preview_want_no_limits_value;
    public volatile boolean test_set_show_under_navigation; // test flag, the value of enable for the last call of showUnderNavigation() (or false if not yet called)
    public static volatile boolean test_force_system_orientation; // test flag, if set to true, that getSystemOrientation() returns test_system_orientation
    public static volatile SystemOrientation test_system_orientation = SystemOrientation.PORTRAIT;
    public static volatile boolean test_force_window_insets; // test flag, if set to true, then the OnApplyWindowInsetsListener will read from the following flags
    public static volatile Insets test_insets; // test insets for WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout()
    public static volatile Insets test_cutout_insets; // test insets for WindowInsets.Type.displayCutout()

    // whether this is a multi-camera device (note, this isn't simply having more than 1 camera, but also having more than one with the same facing)
    // note that in most cases, code should check the MultiCamButtonPreferenceKey preference as well as the is_multi_cam flag,
    // this can be done via isMultiCamEnabled().
    private boolean is_multi_cam;
    // These lists are lists of camera IDs with the same "facing" (front, back or external).
    // Only initialised if is_multi_cam==true.
    private List<Integer> back_camera_ids;
    private List<Integer> front_camera_ids;
    private List<Integer> other_camera_ids;

    private final ToastBoxer switch_video_toast = new ToastBoxer();
    private final ToastBoxer screen_locked_toast = new ToastBoxer();
    private final ToastBoxer stamp_toast = new ToastBoxer();
    private final ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();
    private final ToastBoxer white_balance_lock_toast = new ToastBoxer();
    private final ToastBoxer exposure_lock_toast = new ToastBoxer();
    private final ToastBoxer audio_control_toast = new ToastBoxer();
    private final ToastBoxer store_location_toast = new ToastBoxer();
    private boolean block_startup_toast = false; // used when returning from Settings/Popup - if we're displaying a toast anyway, don't want to display the info toast too
    private String push_info_toast_text; // can be used to "push" extra text to the info text for showPhotoVideoToast()
    private boolean push_switched_camera = false; // whether to display animation for switching front/back cameras

    // application shortcuts:
    static private final String ACTION_SHORTCUT_CAMERA = "net.sourceforge.opencamera.SHORTCUT_CAMERA";
    static private final String ACTION_SHORTCUT_SELFIE = "net.sourceforge.opencamera.SHORTCUT_SELFIE";
    static private final String ACTION_SHORTCUT_VIDEO = "net.sourceforge.opencamera.SHORTCUT_VIDEO";
    static private final String ACTION_SHORTCUT_GALLERY = "net.sourceforge.opencamera.SHORTCUT_GALLERY";
    static private final String ACTION_SHORTCUT_SETTINGS = "net.sourceforge.opencamera.SHORTCUT_SETTINGS";

    private static final int CHOOSE_SAVE_FOLDER_SAF_CODE = 42;
    private static final int CHOOSE_GHOST_IMAGE_SAF_CODE = 43;
    private static final int CHOOSE_LOAD_SETTINGS_SAF_CODE = 44;

    // for testing; must be volatile for test project reading the state
    // n.b., avoid using static, as static variables are shared between different instances of an application,
    // and won't be reset in subsequent tests in a suite!
    public boolean is_test; // whether called from OpenCamera.test testing
    public volatile Bitmap gallery_bitmap;
    public volatile boolean test_low_memory;
    public volatile boolean test_have_angle;
    public volatile float test_angle;
    public volatile Uri test_last_saved_imageuri; // uri of last image; set if using scoped storage OR using SAF
    public volatile String test_last_saved_image; // filename (including full path) of last image; set if not using scoped storage nor using SAF (i.e., writing using File API)
    public static boolean test_force_supports_camera2; // okay to be static, as this is set for an entire test suite
    public volatile String test_save_settings_file;

    // update: notifications now removed due to needing permissions on Android 13+
    //private boolean has_notification;
    //private final String CHANNEL_ID = "open_camera_channel";
    //private final int image_saving_notification_id = 1;

    private static final float WATER_DENSITY_FRESHWATER = 1.0f;
    private static final float WATER_DENSITY_SALTWATER = 1.03f;
    private float mWaterDensity = 1.0f;

    // whether to lock to landscape orientation, or allow switching between portrait and landscape orientations
    //public static final boolean lock_to_landscape = true;
    public static final boolean lock_to_landscape = false;

    // handling for lock_to_landscape==false:

    public enum SystemOrientation {
        LANDSCAPE,
        PORTRAIT,
        REVERSE_LANDSCAPE
    }

    private MyDisplayListener displayListener;

    private boolean has_cached_system_orientation;
    private SystemOrientation cached_system_orientation;

    private boolean hasOldSystemOrientation;
    private SystemOrientation oldSystemOrientation;

    private boolean has_cached_display_rotation;
    private long cached_display_rotation_time_ms;
    private int cached_display_rotation;

    List<Integer> exposure_seekbar_values; // mapping from exposure_seekbar progress value to preview exposure compensation
    private int exposure_seekbar_values_zero; // index in exposure_seekbar_values that maps to zero preview exposure compensation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "onCreate: " + this);
            debug_time = System.currentTimeMillis();
        }
        activity_count++;
        if( MyDebug.LOG )
            Log.d(TAG, "activity_count: " + activity_count);
        //EdgeToEdge.enable(this, SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT)); // test edge-to-edge on pre-Android 15
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // initialise any unset preferences to their default values
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting default preference values: " + (System.currentTimeMillis() - debug_time));

        if( getIntent() != null && getIntent().getExtras() != null ) {
            // whether called from testing
            is_test = getIntent().getExtras().getBoolean("test_project");
            if( MyDebug.LOG )
                Log.d(TAG, "is_test: " + is_test);
        }
        /*if( getIntent() != null && getIntent().getExtras() != null ) {
            // whether called from Take Photo widget
            if( MyDebug.LOG )
                Log.d(TAG, "take_photo?: " + getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO));
        }*/
        if( MyDebug.LOG ) {
            // whether called from Take Photo widget
            Log.d(TAG, "take_photo?: " + TakePhoto.TAKE_PHOTO);
        }
        if( getIntent() != null && getIntent().getAction() != null ) {
            // invoked via the manifest shortcut?
            if( MyDebug.LOG )
                Log.d(TAG, "shortcut: " + getIntent().getAction());
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // determine whether we should support "auto stabilise" feature
        // risk of running out of memory on lower end devices, due to manipulation of large bitmaps
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if( MyDebug.LOG ) {
            Log.d(TAG, "large max memory = " + activityManager.getLargeMemoryClass() + "MB");
        }
        large_heap_memory = activityManager.getLargeMemoryClass();
        if( large_heap_memory >= 128 ) {
            supports_auto_stabilise = true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "supports_auto_stabilise? " + supports_auto_stabilise);

        // hack to rule out phones unlikely to have 4K video, so no point even offering the option!
        // both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL), as does Galaxy K Zoom
        if( activityManager.getLargeMemoryClass() >= 512 ) {
            supports_force_video_4k = true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "supports_force_video_4k? " + supports_force_video_4k);

        // set up components
        bluetoothRemoteControl = new BluetoothRemoteControl(this);
        permissionHandler = new PermissionHandler(this);
        settingsManager = new SettingsManager(this);
        mainUI = new MainUI(this);
        manualSeekbars = new ManualSeekbars();
        applicationInterface = new MyApplicationInterface(this, savedInstanceState);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating application interface: " + (System.currentTimeMillis() - debug_time));
        textFormatter = new TextFormatter(this);
        soundPoolManager = new SoundPoolManager(this);
        magneticSensor = new MagneticSensor(this);
        //speechControl = new SpeechControl(this);

        // determine whether we support Camera2 API
        // must be done before setDeviceDefaults()
        initCamera2Support();

        // set some per-device defaults
        // must be done before creating the Preview (as setDeviceDefaults() may set Camera2 API)
        boolean has_done_first_time = sharedPreferences.contains(PreferenceKeys.FirstTimePreferenceKey);
        if( MyDebug.LOG )
            Log.d(TAG, "has_done_first_time: " + has_done_first_time);
        if( !has_done_first_time ) {
            // must be done after initCamera2Support()
            setDeviceDefaults();
        }

        boolean settings_is_open = settingsIsOpen();
        if( MyDebug.LOG )
            Log.d(TAG, "settings_is_open?: " + settings_is_open);
        // settings_is_open==true can happen if application is recreated when settings is open
        // to reproduce: go to settings, then turn screen off and on (and unlock)
        if( !settings_is_open ) {
            // set up window flags for normal operation
            setWindowFlagsForCamera();
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting window flags: " + (System.currentTimeMillis() - debug_time));

        save_location_history = new SaveLocationHistory(this, PreferenceKeys.SaveLocationHistoryBasePreferenceKey, getStorageUtils().getSaveLocation());
        checkSaveLocations();
        if( applicationInterface.getStorageUtils().isUsingSAF() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "create new SaveLocationHistory for SAF");
            save_location_history_saf = new SaveLocationHistory(this, PreferenceKeys.SaveLocationHistorySAFBasePreferenceKey, getStorageUtils().getSaveLocationSAF());
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after updating folder history: " + (System.currentTimeMillis() - debug_time));

        // set up sensors
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        // accelerometer sensor (for device orientation)
        if( mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "found accelerometer");
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "no support for accelerometer");
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating accelerometer sensor: " + (System.currentTimeMillis() - debug_time));

        // magnetic sensor (for compass direction)
        magneticSensor.initSensor(mSensorManager);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating magnetic sensor: " + (System.currentTimeMillis() - debug_time));

        // clear any seek bars (just in case??)
        mainUI.closeExposureUI();

        // set up the camera and its preview
        preview = new Preview(applicationInterface, (this.findViewById(R.id.preview)));
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating preview: " + (System.currentTimeMillis() - debug_time));

        if( settings_is_open ) {
            // must be done after creating preview
            setWindowFlagsForSettings();
        }

        {
            // don't show orientation animations
            // must be done after creating Preview (so we know if Camera2 API or not)
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            // If locked to landscape, ROTATION_ANIMATION_SEAMLESS/JUMPCUT has the problem that when going to
            // Settings in portrait, we briefly see the UI change - this is because we set the flag
            // to no longer lock to landscape, and that change happens too quickly.
            // This isn't a problem when lock_to_landscape==false, and we want
            // ROTATION_ANIMATION_SEAMLESS so that there is no/minimal pause from the preview when
            // rotating the device. However if using old camera API, we get an ugly transition with
            // ROTATION_ANIMATION_SEAMLESS (probably related to not using TextureView?)
            if( lock_to_landscape || !preview.usingCamera2API() )
                layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
            else if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
                layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
            else
                layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
            getWindow().setAttributes(layout);
        }

        // Setup multi-camera buttons (must be done after creating preview so we know which Camera API is being used,
        // and before initialising on-screen visibility).
        // We only allow the separate icon for switching cameras if:
        // - there are at least 2 types of "facing" camera, and
        // - there are at least 2 cameras with the same "facing".
        // If there are multiple cameras but all with different "facing", then the switch camera
        // icon is used to iterate over all cameras.
        // If there are more than two cameras, but all cameras have the same "facing, we still stick
        // with using the switch camera icon to iterate over all cameras.
        int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
        if( n_cameras > 2 ) {
            this.back_camera_ids = new ArrayList<>();
            this.front_camera_ids = new ArrayList<>();
            this.other_camera_ids = new ArrayList<>();
            for(int i=0;i<n_cameras;i++) {
                switch( preview.getCameraControllerManager().getFacing(i) ) {
                    case FACING_BACK:
                        back_camera_ids.add(i);
                        break;
                    case FACING_FRONT:
                        front_camera_ids.add(i);
                        break;
                    default:
                        // we assume any unknown cameras are also external
                        other_camera_ids.add(i);
                        break;
                }
            }
            boolean multi_same_facing = back_camera_ids.size() >= 2 || front_camera_ids.size() >= 2 || other_camera_ids.size() >= 2;
            int n_facing = 0;
            if( !back_camera_ids.isEmpty() )
                n_facing++;
            if( !front_camera_ids.isEmpty() )
                n_facing++;
            if( !other_camera_ids.isEmpty() )
                n_facing++;
            this.is_multi_cam = multi_same_facing && n_facing >= 2;
            //this.is_multi_cam = false; // test
            if( MyDebug.LOG ) {
                Log.d(TAG, "multi_same_facing: " + multi_same_facing);
                Log.d(TAG, "n_facing: " + n_facing);
                Log.d(TAG, "is_multi_cam: " + is_multi_cam);
            }

            if( !is_multi_cam ) {
                this.back_camera_ids = null;
                this.front_camera_ids = null;
                this.other_camera_ids = null;
            }
        }

        // initialise on-screen button visibility
        View switchCameraButton = findViewById(R.id.switch_camera);
        switchCameraButton.setVisibility(n_cameras > 1 ? View.VISIBLE : View.GONE);
        // switchMultiCameraButton visibility updated below in mainUI.updateOnScreenIcons(), as it also depends on user preference
        View speechRecognizerButton = findViewById(R.id.audio_control);
        speechRecognizerButton.setVisibility(View.GONE); // disabled by default, until the speech recognizer is created
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting button visibility: " + (System.currentTimeMillis() - debug_time));
        View pauseVideoButton = findViewById(R.id.pause_video);
        pauseVideoButton.setVisibility(View.GONE);
        View takePhotoVideoButton = findViewById(R.id.take_photo_when_video_recording);
        takePhotoVideoButton.setVisibility(View.GONE);
        View cancelPanoramaButton = findViewById(R.id.cancel_panorama);
        cancelPanoramaButton.setVisibility(View.GONE);

        // We initialise optional controls to invisible/gone, so they don't show while the camera is opening - the actual visibility is
        // set in cameraSetup().
        // Note that ideally we'd set this in the xml, but doing so for R.id.zoom causes a crash on Galaxy Nexus startup beneath
        // setContentView()!
        // To be safe, we also do so for take_photo and zoom_seekbar (we already know we've had no reported crashes for focus_seekbar,
        // however).
        View takePhotoButton = findViewById(R.id.take_photo);
        takePhotoButton.setVisibility(View.INVISIBLE);
        View zoomSeekbar = findViewById(R.id.zoom_seekbar);
        zoomSeekbar.setVisibility(View.INVISIBLE);

        // initialise state of on-screen icons
        mainUI.updateOnScreenIcons();

        if( MainActivity.lock_to_landscape ) {
            // listen for orientation event change (only required if lock_to_landscape==true
            // (MainUI.onOrientationChanged() does nothing if lock_to_landscape==false)
            orientationEventListener = new OrientationEventListener(this) {
                @Override
                public void onOrientationChanged(int orientation) {
                    MainActivity.this.mainUI.onOrientationChanged(orientation);
                }
            };
            if( MyDebug.LOG )
                Log.d(TAG, "onCreate: time after setting orientation event listener: " + (System.currentTimeMillis() - debug_time));
        }

        layoutChangeListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if( MyDebug.LOG )
                    Log.d(TAG, "onLayoutChange");

                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode() ) {
                    Point display_size = new Point();
                    applicationInterface.getDisplaySize(display_size, true);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "    display width: " + display_size.x);
                        Log.d(TAG, "    display height: " + display_size.y);
                        Log.d(TAG, "    layoutUI display width: " + mainUI.layoutUI_display_w);
                        Log.d(TAG, "    layoutUI display height: " + mainUI.layoutUI_display_h);
                    }
                    // We need to call layoutUI when the window is resized without an orientation change -
                    // this can happen in split-screen or multi-window mode, where onConfigurationChanged
                    // is not guaranteed to be called.
                    // We check against the size of when layoutUI was last called, to avoid repeated calls
                    // when the resize is due to the device rotating and onConfigurationChanged is called -
                    // in fact we'd have a problem of repeatedly calling layoutUI, since doing layoutUI
                    // causes onLayoutChange() to be called again.
                    if( display_size.x != mainUI.layoutUI_display_w || display_size.y != mainUI.layoutUI_display_h ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "call layoutUI due to resize");
                        mainUI.layoutUI();
                    }
                }
            }
        };

        // set up take photo long click
        takePhotoButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if( !allowLongPress() ) {
                    // return false, so a regular click will still be triggered when the user releases the touch
                    return false;
                }
                return longClickedTakePhoto();
            }
        });
        // set up on touch listener so we can detect if we've released from a long click
        takePhotoButton.setOnTouchListener(new View.OnTouchListener() {
            // the suppressed warning ClickableViewAccessibility suggests calling view.performClick for ACTION_UP, but this
            // results in an additional call to clickedTakePhoto() - that is, if there is no long press, we get two calls to
            // clickedTakePhoto instead one one; and if there is a long press, we get one call to clickedTakePhoto where
            // there should be none.
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if( motionEvent.getAction() == MotionEvent.ACTION_UP ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "takePhotoButton ACTION_UP");
                    takePhotoButtonLongClickCancelled();
                    if( MyDebug.LOG )
                        Log.d(TAG, "takePhotoButton ACTION_UP done");
                }
                return false;
            }
        });

        // set up gallery button long click
        View galleryButton = findViewById(R.id.gallery);
        galleryButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if( !allowLongPress() ) {
                    // return false, so a regular click will still be triggered when the user releases the touch
                    return false;
                }
                //preview.showToast(null, "Long click");
                longClickedGallery();
                return true;
            }
        });

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting long click listeners: " + (System.currentTimeMillis() - debug_time));

        // listen for gestures
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating gesture detector: " + (System.currentTimeMillis() - debug_time));

        setupSystemUiVisibilityListener();
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting system ui visibility listener: " + (System.currentTimeMillis() - debug_time));

        // show "about" dialog for first time use
        if( !has_done_first_time ) {
            if( !is_test ) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                alertDialog.setTitle(R.string.app_name);
                alertDialog.setMessage(R.string.intro_text);
                alertDialog.setPositiveButton(android.R.string.ok, null);
                alertDialog.setNegativeButton(R.string.preference_online_help, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "online help");
                        launchOnlineHelp();
                    }
                });
                alertDialog.show();
            }

            setFirstTimeFlag();
        }

        {
            // handle What's New dialog
            int version_code = -1;
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                version_code = pInfo.versionCode;
            }
            catch(PackageManager.NameNotFoundException e) {
                MyDebug.logStackTrace(TAG, "NameNotFoundException exception trying to get version number", e);
            }
            if( version_code != -1 ) {
                int latest_version = sharedPreferences.getInt(PreferenceKeys.LatestVersionPreferenceKey, 0);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "version_code: " + version_code);
                    Log.d(TAG, "latest_version: " + latest_version);
                }
                //final boolean whats_new_enabled = false;
                final boolean whats_new_enabled = true;
                if( whats_new_enabled ) {
                    // whats_new_version is the version code that the What's New text is written for. Normally it will equal the
                    // current release (version_code), but it some cases we may want to leave it unchanged.
                    // E.g., we have a "What's New" for 1.44 (64), but then push out a quick fix for 1.44.1 (65). We don't want to
                    // show the dialog again to people who already received 1.44 (64), but we still want to show the dialog to people
                    // upgrading from earlier versions.
                    int whats_new_version = 1; // 1.0
                    whats_new_version = Math.min(whats_new_version, version_code); // whats_new_version should always be <= version_code, but just in case!
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "whats_new_version: " + whats_new_version);
                    }
                    final boolean force_whats_new = false;
                    //final boolean force_whats_new = true; // for testing
                    boolean allow_show_whats_new = sharedPreferences.getBoolean(PreferenceKeys.ShowWhatsNewPreferenceKey, true);
                    if( MyDebug.LOG )
                        Log.d(TAG, "allow_show_whats_new: " + allow_show_whats_new);
                    // don't show What's New if this is the first time the user has run
                    if( has_done_first_time && allow_show_whats_new && ( force_whats_new || whats_new_version > latest_version ) ) {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                        alertDialog.setTitle(R.string.whats_new);
                        alertDialog.setMessage(R.string.whats_new_text);
                        alertDialog.setPositiveButton(android.R.string.ok, null);
                        alertDialog.show();
                    }
                }
                // We set the latest_version whether or not the dialog is shown - if we showed the first time dialog, we don't
                // want to then show the What's New dialog next time we run! Similarly if the user had disabled showing the dialog,
                // but then enables it, we still shouldn't show the dialog until the new time Open Camera upgrades.
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
                editor.apply();
            }
        }

        setModeFromIntents(savedInstanceState);

        // load icons
        preloadIcons(R.array.flash_icons);
        preloadIcons(R.array.focus_mode_icons);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after preloading icons: " + (System.currentTimeMillis() - debug_time));

        // initialise text to speech engine
        textToSpeechSuccess = false;
        // run in separate thread so as to not delay startup time
        new Thread(new Runnable() {
            public void run() {
                textToSpeech = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "TextToSpeech initialised");
                        if( status == TextToSpeech.SUCCESS ) {
                            textToSpeechSuccess = true;
                            if( MyDebug.LOG )
                                Log.d(TAG, "TextToSpeech succeeded");
                        }
                        else {
                            if( MyDebug.LOG )
                                Log.d(TAG, "TextToSpeech failed");
                        }
                    }
                });
            }
        }).start();

        // handle on back behaviour
        popupOnBackPressedCallback = new PopupOnBackPressedCallback(false);
        this.getOnBackPressedDispatcher().addCallback(this, popupOnBackPressedCallback);
        pausePreviewOnBackPressedCallback = new PausePreviewOnBackPressedCallback(false);
        this.getOnBackPressedDispatcher().addCallback(this, pausePreviewOnBackPressedCallback);
        screenLockOnBackPressedCallback = new ScreenLockOnBackPressedCallback(false);
        this.getOnBackPressedDispatcher().addCallback(this, screenLockOnBackPressedCallback);

        // create notification channel - only needed on Android 8+
        // update: notifications now removed due to needing permissions on Android 13+
        /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            CharSequence name = "Open Camera Image Saving";
            String description = "Notification channel for processing and saving images in the background";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }*/

        // so we get the icons rotation even when rotating for the first time - see onSystemOrientationChanged
        this.hasOldSystemOrientation = true;
        this.oldSystemOrientation = getSystemOrientation();

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: total time for Activity startup: " + (System.currentTimeMillis() - debug_time));
    }

    /** Whether to use codepaths that are compatible with scoped storage.
     */
    public static boolean useScopedStorage() {
        //return false;
        //return true;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /** Whether this is a multi camera device, and the user preference is set to enable the multi-camera button.
     */
    public boolean isMultiCamEnabled() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return is_multi_cam && sharedPreferences.getBoolean(PreferenceKeys.MultiCamButtonPreferenceKey, true);
    }

    /** Whether this is a multi camera device, whether or not the user preference is set to enable
     *  the multi-camera button.
     */
    public boolean isMultiCam() {
        return is_multi_cam;
    }

    /* Returns the camera Id in use by the preview - or the one we requested, if the camera failed
     * to open.
     * Needed as Preview.getCameraId() returns 0 if camera_controller==null, but if the camera
     * fails to open, we want the switch camera icons to still work as expected!
     */
    private int getActualCameraId() {
        if( preview.getCameraController() == null )
            return applicationInterface.getCameraIdPref();
        else
            return preview.getCameraId();
    }

    /** Whether the icon switch_multi_camera should be displayed. This is if the following are all
     *  true:
     *  - The device is a multi camera device (MainActivity.is_multi_cam==true).
     *  - The user preference for using the separate icons is enabled
     *    (PreferenceKeys.MultiCamButtonPreferenceKey).
     *  - For the current camera ID, there are at least two cameras with the same front/back/external
     *    "facing" (e.g., imagine a device with two back cameras, but only one front camera - no point
     *    showing the multi-cam icon for just a single logical front camera).
     *  OR there are physical cameras for the current camera, and again the user preference
     *  PreferenceKeys.MultiCamButtonPreferenceKey is enabled.
     */
    public boolean showSwitchMultiCamIcon() {
        if( preview.hasPhysicalCameras() ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if( sharedPreferences.getBoolean(PreferenceKeys.MultiCamButtonPreferenceKey, true) )
                return true;
        }
        if( isMultiCamEnabled() ) {
            int cameraId = getActualCameraId();
            switch( preview.getCameraControllerManager().getFacing(cameraId) ) {
                case FACING_BACK:
                    if( back_camera_ids.size() > 1 )
                        return true;
                    break;
                case FACING_FRONT:
                    if( front_camera_ids.size() > 1 )
                        return true;
                    break;
                default:
                    if( other_camera_ids.size() > 1 )
                        return true;
                    break;
            }
        }
        return false;
    }

    /** Whether user preference is set to allow long press actions.
     */
    private boolean allowLongPress() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(PreferenceKeys.AllowLongPressPreferenceKey, true);
    }

    /* This method sets the preference defaults which are set specific for a particular device.
     * This method should be called when Open Camera is run for the very first time after installation,
     * or when the user has requested to "Reset settings".
     */
    void setDeviceDefaults() {
        if( MyDebug.LOG )
            Log.d(TAG, "setDeviceDefaults");
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        //SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        //boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        //boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        //boolean is_nexus = Build.MODEL.toLowerCase(Locale.US).contains("nexus");
        //boolean is_nexus6 = Build.MODEL.toLowerCase(Locale.US).contains("nexus 6");
        //boolean is_pixel_phone = Build.DEVICE != null && Build.DEVICE.equals("sailfish");
        //boolean is_pixel_xl_phone = Build.DEVICE != null && Build.DEVICE.equals("marlin");
        /*if( MyDebug.LOG ) {
            //Log.d(TAG, "is_samsung? " + is_samsung);
            //Log.d(TAG, "is_oneplus? " + is_oneplus);
            //Log.d(TAG, "is_nexus? " + is_nexus);
            //Log.d(TAG, "is_nexus6? " + is_nexus6);
            //Log.d(TAG, "is_pixel_phone? " + is_pixel_phone);
            //Log.d(TAG, "is_pixel_xl_phone? " + is_pixel_xl_phone);
        }*/
        /*if( is_samsung || is_oneplus ) {
            // The problems we used to have on Samsung Galaxy devices are now fixed, by setting
            // TEMPLATE_PREVIEW for the precaptureBuilder in CameraController2. This also fixes the
            // problems with OnePlus 3T having blue tinge if flash is on, and the scene is bright
            // enough not to need it
            if( MyDebug.LOG )
                Log.d(TAG, "set fake flash for camera2");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, true);
            editor.apply();
        }*/
		/*if( is_nexus6 ) {
			// Nexus 6 captureBurst() started having problems with Android 7 upgrade - images appeared in wrong order (and with wrong order of shutter speeds in exif info), as well as problems with the camera failing with serious errors
			// we set this even for Nexus 6 devices not on Android 7, as at some point they'll likely be upgraded to Android 7
			// Update: now fixed in v1.37, this was due to bug where we set RequestTag.CAPTURE for all captures in takePictureBurstExpoBracketing(), rather than just the last!
			if( MyDebug.LOG )
				Log.d(TAG, "disable fast burst for camera2");
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, false);
			editor.apply();
		}*/
        if( is_samsung && !is_test ) {
            // Samsung Galaxy devices (including S10e, S24) have problems with HDR/expo - base images come out with wrong exposures.
            // This can be fixed by not using fast bast, allowing us to adjust the preview exposure to match.
            if( MyDebug.LOG )
                Log.d(TAG, "disable fast burst for camera2");
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, false);
            editor.apply();
        }
        if( supports_camera2 && !is_test ) {
            // n.b., when testing, we explicitly decide whether to run with Camera2 API or not
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            int n_cameras = manager2.getNumberOfCameras();
            boolean all_supports_camera2 = true; // whether all cameras have at least LIMITED support for Camera2 (risky to default to Camera2 if any cameras are LEGACY, as not easy to test such devices)
            for(int i=0;i<n_cameras && all_supports_camera2;i++) {
                if( !manager2.allowCamera2Support(i) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera " + i + " doesn't have at least LIMITED support for Camera2 API");
                    all_supports_camera2 = false;
                }
            }

            if( all_supports_camera2 ) {
                boolean default_to_camera2 = false;
                boolean is_google = Build.MANUFACTURER.toLowerCase(Locale.US).contains("google");
                boolean is_nokia = Build.MANUFACTURER.toLowerCase(Locale.US).contains("hmd global");
                boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
                if( is_google && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S )
                    default_to_camera2 = true;
                else if( is_nokia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P )
                    default_to_camera2 = true;
                else if( is_samsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S )
                    default_to_camera2 = true;
                else if( is_oneplus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE )
                    default_to_camera2 = true;

                if( default_to_camera2 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "default to camera2 API");
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
                    editor.apply();
                }
            }
        }
    }

    /** Switches modes if required, if called from a relevant intent/tile.
     */
    private void setModeFromIntents(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "setModeFromIntents");
        if( savedInstanceState != null ) {
            // If we're restoring from a saved state, we shouldn't be resetting any modes
            if( MyDebug.LOG )
                Log.d(TAG, "restoring from saved state");
            return;
        }
        boolean done_facing = false;
        String action = this.getIntent().getAction();
        if( MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(action) || MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from video intent");
            applicationInterface.setVideoPref(true);
        }
        else if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action) || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from photo intent");
            applicationInterface.setVideoPref(false);
        }
        else if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileService.TILE_ID.equals(action)) || ACTION_SHORTCUT_CAMERA.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from quick settings tile or application shortcut for Open Camera: photo mode");
            applicationInterface.setVideoPref(false);
        }
        else if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileServiceVideo.TILE_ID.equals(action)) || ACTION_SHORTCUT_VIDEO.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from quick settings tile or application shortcut for Open Camera: video mode");
            applicationInterface.setVideoPref(true);
        }
        else if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileServiceFrontCamera.TILE_ID.equals(action)) || ACTION_SHORTCUT_SELFIE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from quick settings tile or application shortcut for Open Camera: selfie mode");
            done_facing = true;
            applicationInterface.switchToCamera(true);
        }
        else if( ACTION_SHORTCUT_GALLERY.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from application shortcut for Open Camera: gallery");
            openGallery();
        }
        else if( ACTION_SHORTCUT_SETTINGS.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from application shortcut for Open Camera: settings");
            openSettings();
        }

        Bundle extras = this.getIntent().getExtras();
        if( extras != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "handle intent extra information");
            if( !done_facing ) {
                int camera_facing = extras.getInt("android.intent.extras.CAMERA_FACING", -1);
                if( camera_facing == 0 || camera_facing == 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extras.CAMERA_FACING: " + camera_facing);
                    applicationInterface.switchToCamera(camera_facing==1);
                    done_facing = true;
                }
            }
            if( !done_facing ) {
                if( extras.getInt("android.intent.extras.LENS_FACING_FRONT", -1) == 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extras.LENS_FACING_FRONT");
                    applicationInterface.switchToCamera(true);
                    done_facing = true;
                }
            }
            if( !done_facing ) {
                if( extras.getInt("android.intent.extras.LENS_FACING_BACK", -1) == 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extras.LENS_FACING_BACK");
                    applicationInterface.switchToCamera(false);
                    done_facing = true;
                }
            }
            if( !done_facing ) {
                if( extras.getBoolean("android.intent.extra.USE_FRONT_CAMERA", false) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extra.USE_FRONT_CAMERA");
                    applicationInterface.switchToCamera(true);
                    done_facing = true;
                }
            }
        }

        // N.B., in practice the hasSetCameraId() check is pointless as we don't save the camera ID in shared preferences, so it will always
        // be false when application is started from onCreate(), unless resuming from saved instance (in which case we shouldn't be here anyway)
        if( !done_facing && !applicationInterface.hasSetCameraId() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "initialise to back camera");
            // most devices have first camera as back camera anyway so this wouldn't be needed, but some (e.g., LG G6) have first camera
            // as front camera, so we should explicitly switch to back camera
            applicationInterface.switchToCamera(false);
        }
    }

    /** Determine whether we support Camera2 API.
     */
    private void initCamera2Support() {
        if( MyDebug.LOG )
            Log.d(TAG, "initCamera2Support");
        supports_camera2 = false;
        {
            // originally we allowed Camera2 if all cameras support at least LIMITED
            // as of 1.45, we allow Camera2 if at least one camera supports at least LIMITED - this
            // is to support devices that might have a camera with LIMITED or better support, but
            // also a LEGACY camera
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            supports_camera2 = false;
            int n_cameras = manager2.getNumberOfCameras();
            if( n_cameras == 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "Camera2 reports 0 cameras");
                supports_camera2 = false;
            }
            for(int i=0;i<n_cameras && !supports_camera2;i++) {
                if( manager2.allowCamera2Support(i) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera " + i + " has at least limited support for Camera2 API");
                    supports_camera2 = true;
                }
            }
        }

        //test_force_supports_camera2 = true; // test
        if( test_force_supports_camera2 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "forcing supports_camera2");
            supports_camera2 = true;
        }

        if( MyDebug.LOG )
            Log.d(TAG, "supports_camera2? " + supports_camera2);

        // handle the switch from a boolean preference_use_camera2 to String preference_camera_api
        // that occurred in v1.48
        if( supports_camera2 ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if( !sharedPreferences.contains(PreferenceKeys.CameraAPIPreferenceKey) // doesn't have the new key set yet
                    && sharedPreferences.contains("preference_use_camera2") // has the old key set
                    && sharedPreferences.getBoolean("preference_use_camera2", false) // and camera2 was enabled
            ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "transfer legacy camera2 boolean preference to new api option");
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
                editor.remove("preference_use_camera2"); // remove the old key, just in case
                editor.apply();
            }
        }
    }

    /** Handles users updating to a version with scoped storage (this could be Android 10 users upgrading
     *  to the version of Open Camera with scoped storage; or users who later upgrade to Android 10).
     *  With scoped storage, we no longer support saving outside of DCIM/ when not using SAF.
     *  This updates if necessary both the current save location, and the save folder history.
     */
    private void checkSaveLocations() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkSaveLocations");
        if( useScopedStorage() ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean any_changes = false;
            String save_location = getStorageUtils().getSaveLocation();
            CheckSaveLocationResult res = checkSaveLocation(save_location);
            if( !res.res ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "save_location not valid with scoped storage: " + save_location);
                String new_folder;
                if( res.alt == null ) {
                    // no alternative, fall back to default
                    new_folder = "LinkedCamera";
                }
                else {
                    // replace with the alternative
                    if( MyDebug.LOG )
                        Log.d(TAG, "alternative: " + res.alt);
                    new_folder = res.alt;
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.SaveLocationPreferenceKey, new_folder);
                editor.apply();
                any_changes = true;
            }

            // now check history
            // go backwards so we can remove easily
            for(int i=save_location_history.size()-1;i>=0;i--) {
                String this_location = save_location_history.get(i);
                res = checkSaveLocation(this_location);
                if( !res.res ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "save_location in history " + i + " not valid with scoped storage: " + this_location);
                    if( res.alt == null ) {
                        // no alternative, remove
                        save_location_history.remove(i);
                    }
                    else {
                        // replace with the alternative
                        if( MyDebug.LOG )
                            Log.d(TAG, "alternative: " + res.alt);
                        save_location_history.set(i, res.alt);
                    }
                    any_changes = true;
                }
            }

            if( any_changes ) {
                this.save_location_history.updateFolderHistory(this.getStorageUtils().getSaveLocation(), false);
            }
        }
    }

    /** Result from checkSaveLocation. Ideally we'd just use android.util.Pair, but that's not mocked
     *  for use in unit tests.
     *  See checkSaveLocation() for documentation.
     */
    public static class CheckSaveLocationResult {
        final boolean res;
        final String alt;

        public CheckSaveLocationResult(boolean res, String alt) {
            this.res = res;
            this.alt = alt;
        }

        @Override
        public boolean equals(Object o) {
            if( !(o instanceof CheckSaveLocationResult) ) {
                return false;
            }
            CheckSaveLocationResult that = (CheckSaveLocationResult)o;
            // stop dumb inspection that suggests replacing warning with an error(!) (Objects class is not available on all API versions)
            // and the other inspection suggests replacing with code that would cause a nullpointerexception
            //noinspection EqualsReplaceableByObjectsCall,StringEquality
            return that.res == this.res && ( (that.alt == this.alt) || (that.alt != null && that.alt.equals(this.alt) ) );
            //return that.res == this.res && ( (that.alt == this.alt) || (that.alt != null && that.alt.equals(this.alt) ) );
        }

        @Override
        public int hashCode() {
            return (res ? 1249 : 1259) ^ (alt == null ? 0 : alt.hashCode());
        }

        @NonNull
        @Override
        public String toString() {
            return "CheckSaveLocationResult{" + res + " , " + alt + "}";
        }
    }

    public static CheckSaveLocationResult checkSaveLocation(final String folder) {
        return checkSaveLocation(folder, null);
    }

    /** Checks to see if the supplied folder (in the format as used by our preferences) is supported
     *  with scoped storage.
     * @return The Boolean is always non-null, and returns whether the save location is valid.
     *         If the return is false, then if the String is non-null, this stores an alternative
     *         form that is valid. If null, there is no valid alternative.
     * @param base_folder This should normally be null, but can be used to specify manually the
     *                    folder instead of using StorageUtils.getBaseFolder() - needed for unit
     *                    tests as Environment class (for Environment.getExternalStoragePublicDirectory())
     *                    is not mocked.
     */
    public static CheckSaveLocationResult checkSaveLocation(final String folder, String base_folder) {
        /*if( MyDebug.LOG )
            Log.d(TAG, "DCIM path: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());*/
        if( StorageUtils.saveFolderIsFull(folder) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "checkSaveLocation for full path: " + folder);
            // But still check to see if the full path is part of DCIM. Since when using the
            // file dialog method with non-scoped storage, if the user specifies multiple subfolders
            // e.g. DCIM/blah_a/blah_b, we don't spot that in FolderChooserDialog.useFolder(), and
            // instead still store that as the full path.

            if( base_folder == null )
                base_folder = StorageUtils.getBaseFolder().getAbsolutePath();
            // strip '/' as last character - makes it easier to also spot cases where the folder is the
            // DCIM folder, but doesn't have a '/' last character
            if( !base_folder.isEmpty() && base_folder.charAt(base_folder.length()-1) == '/' )
                base_folder = base_folder.substring(0, base_folder.length()-1);
            if( MyDebug.LOG )
                Log.d(TAG, "    compare to base_folder: " + base_folder);
            String alt_folder = null;
            if( folder.startsWith(base_folder) ) {
                alt_folder = folder.substring(base_folder.length());
                // also need to strip the first '/' if it exists
                if( !alt_folder.isEmpty() && alt_folder.charAt(0) == '/' )
                    alt_folder = alt_folder.substring(1);
            }

            return new CheckSaveLocationResult(false, alt_folder);
        }
        else {
            // already in expected format (indicates a sub-folder of DCIM)
            return new CheckSaveLocationResult(true, null);
        }
    }

    private void preloadIcons(int icons_id) {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "preloadIcons: " + icons_id);
            debug_time = System.currentTimeMillis();
        }
        String [] icons = getResources().getStringArray(icons_id);
        for(String icon : icons) {
            int resource = getResources().getIdentifier(icon, null, this.getApplicationContext().getPackageName());
            if( MyDebug.LOG )
                Log.d(TAG, "load resource: " + resource);
            Bitmap bm = BitmapFactory.decodeResource(getResources(), resource);
            this.preloaded_bitmap_resources.put(resource, bm);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "preloadIcons: total time for preloadIcons: " + (System.currentTimeMillis() - debug_time));
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
        }
    }

    @Override
    protected void onStop() {
        if( MyDebug.LOG )
            Log.d(TAG, "onStop");
        super.onStop();

        // we stop location listening in onPause, but done here again just to be certain!
        applicationInterface.getLocationSupplier().freeLocationListeners();
    }

    @Override
    protected void onDestroy() {
        if( MyDebug.LOG ) {
            Log.d(TAG, "onDestroy");
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
        }
        activity_count--;
        if( MyDebug.LOG )
            Log.d(TAG, "activity_count: " + activity_count);

        // should do asap before waiting for images to be saved - as risk the application will be killed whilst waiting for that to happen,
        // and we want to avoid notifications hanging around
        cancelImageSavingNotification();

        if( want_no_limits && navigation_gap != 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS");
            // it's unclear why this matters - but there is a bug when exiting split-screen mode, if the split-screen mode had set want_no_limits:
            // even though the application is created when leaving split-screen mode, we still end up with the window flags for showing
            // under the navigation bar!
            // update: this issue is also fixed by not allowing want_no_limits mode in multi-window mode, but still good to reset things here
            // just in case
            showUnderNavigation(false);
        }

        // reduce risk of losing any images
        // we don't do this in onPause or onStop, due to risk of ANRs
        // note that even if we did call this earlier in onPause or onStop, we'd still want to wait again here: as it can happen
        // that a new image appears after onPause/onStop is called, in which case we want to wait until images are saved,
        waitUntilImageQueueEmpty();

        preview.onDestroy();
        if( applicationInterface != null ) {
            applicationInterface.onDestroy();
        }
        // Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
        for(Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
            if( MyDebug.LOG )
                Log.d(TAG, "recycle: " + entry.getKey());
            entry.getValue().recycle();
        }
        preloaded_bitmap_resources.clear();
        if( textToSpeech != null ) {
            // http://stackoverflow.com/questions/4242401/tts-error-leaked-serviceconnection-android-speech-tts-texttospeech-solved
            if( MyDebug.LOG )
                Log.d(TAG, "free textToSpeech");
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        // we stop location listening in onPause, but done here again just to be certain!
        applicationInterface.getLocationSupplier().freeLocationListeners();

        super.onDestroy();
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy done");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void setFirstTimeFlag() {
        if( MyDebug.LOG )
            Log.d(TAG, "setFirstTimeFlag");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.FirstTimePreferenceKey, true);
        editor.apply();
    }

    private static String getOnlineHelpUrl(String append) {
        if( MyDebug.LOG )
            Log.d(TAG, "getOnlineHelpUrl: " + append);
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        // also if we change this method name or where it's located, remember to update the mention in
        // opencamera_source.txt
        //return "https://opencamera.sourceforge.io/" + append;
        return "https://github.com/UrbanVue/linked_camera" + append;
    }

    void launchOnlineHelp() {
        if( MyDebug.LOG )
            Log.d(TAG, "launchOnlineHelp");
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getOnlineHelpUrl("")));
        startActivity(browserIntent);
    }

    void launchOnlinePrivacyPolicy() {
        if( MyDebug.LOG )
            Log.d(TAG, "launchOnlinePrivacyPolicy");
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        //Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getOnlineHelpUrl("index.html#privacy")));
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getOnlineHelpUrl("privacy_oc.html")));
        startActivity(browserIntent);
    }

    void launchOnlineLicences() {
        if( MyDebug.LOG )
            Log.d(TAG, "launchOnlineLicences");
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getOnlineHelpUrl("#licence")));
        startActivity(browserIntent);
    }

    /* Audio trigger - either loud sound, or speech recognition.
     * This performs some additional checks before taking a photo.
     */
    void audioTrigger() {
        if( MyDebug.LOG )
            Log.d(TAG, "ignore audio trigger due to popup open");
        if( popupIsOpen() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "ignore audio trigger due to popup open");
        }
        else if( camera_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "ignore audio trigger due to camera in background");
        }
        else if( preview.isTakingPhotoOrOnTimer() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "ignore audio trigger due to already taking photo or on timer");
        }
        else if( preview.isVideoRecording() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "ignore audio trigger due to already recording video");
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "schedule take picture due to loud noise");
            //takePicture();
            this.runOnUiThread(new Runnable() {
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "taking picture due to audio trigger");
                    takePicture(false);
                }
            });
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if( MyDebug.LOG )
            Log.d(TAG, "onKeyDown: " + keyCode);
        if( camera_in_background ) {
            // don't allow keys such as volume keys for taking photo when camera in background!
            if( MyDebug.LOG )
                Log.d(TAG, "camera is in background");
        }
        else {
            boolean handled = mainUI.onKeyDown(keyCode, event);
            if( handled )
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if( MyDebug.LOG )
            Log.d(TAG, "onKeyUp: " + keyCode);
        if( camera_in_background ) {
            // don't allow keys such as volume keys for taking photo when camera in background!
            if( MyDebug.LOG )
                Log.d(TAG, "camera is in background");
        }
        else {
            mainUI.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    private void zoomByStep(int change) {
        if( MyDebug.LOG )
            Log.d(TAG, "zoomByStep: " + change);
        if( preview.supportsZoom() && change != 0 ) {
            if( preview.getCameraController() != null ) {
                // If the minimum zoom is < 1.0, the seekbar will have repeated entries for 1x zoom
                // (so it's easier for the user to zoom to exactly 1.0x). But if using the -/+ buttons,
                // volume keys etc to zoom, we want to skip over these repeated values.
                int zoom_factor = preview.getCameraController().getZoom();
                int new_zoom_factor = zoom_factor + change;
                if( MyDebug.LOG )
                    Log.d(TAG, "new_zoom_factor: " + new_zoom_factor);
                while( new_zoom_factor > 0 && new_zoom_factor < preview.getMaxZoom() && preview.getZoomRatio(new_zoom_factor) == preview.getZoomRatio() ) {
                    if( change > 0 )
                        change++;
                    else
                        change--;
                    new_zoom_factor = zoom_factor + change;
                    if( MyDebug.LOG )
                        Log.d(TAG, "skip over constant region: " + new_zoom_factor);
                }
            }

            mainUI.changeSeekbar(R.id.zoom_seekbar, -change); // seekbar is opposite direction to zoom array
        }
    }

    public void zoomIn() {
        zoomByStep(1);
    }

    public void zoomOut() {
        zoomByStep(-1);
    }

    public void changeExposure(int change) {
        if( preview.supportsExposures() ) {
            if( exposure_seekbar_values != null ) {
                SeekBar seekBar = this.findViewById(R.id.exposure_seekbar);
                int progress = seekBar.getProgress();
                int new_progress = progress + change;
                int current_exposure = getExposureSeekbarValue(progress);
                if( new_progress < 0 || new_progress > exposure_seekbar_values.size()-1 ) {
                    // skip
                }
                else if( getExposureSeekbarValue(new_progress) == 0 && current_exposure != 0 ) {
                    // snap to the central repeated zero
                    new_progress = exposure_seekbar_values_zero;
                    change = new_progress - progress;
                }
                else {
                    // skip over the repeated zeroes
                    while( new_progress > 0 && new_progress < exposure_seekbar_values.size()-1 && getExposureSeekbarValue(new_progress) == current_exposure ) {
                        if( change > 0 )
                            change++;
                        else
                            change--;
                        new_progress = progress + change;
                        if( MyDebug.LOG )
                            Log.d(TAG, "skip over constant region: " + new_progress);
                    }
                }
            }
            mainUI.changeSeekbar(R.id.exposure_seekbar, change);
        }
    }

    public int getExposureSeekbarProgressZero() {
        return exposure_seekbar_values_zero;
    }

    /** Returns the exposure compensation corresponding to a progress on the seekbar.
     *  Caller is responsible for checking that progress is within valid range.
     */
    public int getExposureSeekbarValue(int progress) {
        return exposure_seekbar_values.get(progress);
    }

    public void changeISO(int change) {
        if( preview.supportsISORange() ) {
            mainUI.changeSeekbar(R.id.iso_seekbar, change);
        }
    }

    public void changeFocusDistance(int change, boolean is_target_distance) {
        mainUI.changeSeekbar(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar, change);
    }

    private final SensorEventListener accelerometerListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            preview.onAccelerometerSensorChanged(event);
        }
    };

    public float getWaterDensity() {
        return this.mWaterDensity;
    }

    @Override
    protected void onResume() {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "onResume");
            debug_time = System.currentTimeMillis();
        }
        super.onResume();
        this.app_is_paused = false; // must be set before initLocation() at least

        // this is intentionally true, not false, as the uncovering happens in DrawPreview when we receive frames from the camera after it's opened
        // (this should already have been set from the call in onPause(), but we set it here again just in case)
        applicationInterface.getDrawPreview().setCoverPreview(true);

        applicationInterface.getDrawPreview().clearDimPreview(); // shouldn't be needed, but just in case the dim preview flag got set somewhere

        cancelImageSavingNotification();

        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the MyPreferenceFragment settings
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);

        if( edge_to_edge_mode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ) {
            // needed on Android 15, otherwise the navigation bar is not transparent
            getWindow().setNavigationBarContrastEnforced(false);
        }

        registerDisplayListener();

        mSensorManager.registerListener(accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        magneticSensor.registerMagneticListener(mSensorManager);
        if( orientationEventListener != null ) {
            orientationEventListener.enable();
        }
        getWindow().getDecorView().addOnLayoutChangeListener(layoutChangeListener);

        // if BLE remote control is enabled, then start the background BLE service
        bluetoothRemoteControl.startRemoteControl();

        //speechControl.initSpeechRecognizer();
        initLocation();
        initGyroSensors();
        applicationInterface.getImageSaver().onResume();
        soundPoolManager.initSound();
        soundPoolManager.loadSound(R.raw.mybeep);
        soundPoolManager.loadSound(R.raw.mybeep_hi);

        resetCachedSystemOrientation(); // just in case?
        mainUI.layoutUI();

        // Process Nextcloud upload queue if WiFi is available
        NextcloudUploadService.processUploadQueue(this);

        // If the cached last media has exif datetime info, it's fine to just call updateGalleryIcon(),
        // which will find the most recent media (and takes care of if the cached last image may have
        // been deleted).
        // If it doesn't have exif datetime tags, updateGalleryIcon() may not be able to find the most
        // recent media, so we stick with the cached uri if we can test that it's still accessible.
        if( !getStorageUtils().getLastMediaScannedHasNoExifDateTime() ) {
            updateGalleryIcon();
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "last media has no exif datetime, so check it still exists");
            boolean uri_exists = false;
            InputStream inputStream = null;
            Uri check_uri = getStorageUtils().getLastMediaScannedCheckUri();
            if( MyDebug.LOG )
                Log.d(TAG, "check_uri: " + check_uri);
            try {
                inputStream = this.getContentResolver().openInputStream(check_uri);
                if( inputStream != null )
                    uri_exists = true;
            }
            catch(Exception ignored) {
            }
            finally {
                if( inputStream != null ) {
                    try {
                        inputStream.close();
                    }
                    catch(IOException e) {
                        MyDebug.logStackTrace(TAG, "failed to close inputStream", e);
                    }
                }
            }

            if( uri_exists ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "    most recent uri exists");
                // also re-allow ghost image again in case that option is set (since we won't be
                // doing this via updateGalleryIcon())
                applicationInterface.getDrawPreview().allowGhostImage();
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "    most recent uri no longer valid");
                updateGalleryIcon();
            }
        }

        applicationInterface.reset(false); // should be called before opening the camera in preview.onResume()

        if( !camera_in_background ) {
            // don't restart camera if we're showing a dialog or settings
            preview.onResume();
        }

        {
            // show a toast for the camera if it's not the first for front of back facing (otherwise on multi-front/back camera
            // devices, it's easy to forget if set to a different camera)
            // but we only show this when resuming, not every time the camera opens
            // OR show the toast for the camera if it's a physical camera
            int cameraId = applicationInterface.getCameraIdPref();
            String cameraIdSPhysical = applicationInterface.getCameraIdSPhysicalPref();
            if( cameraId > 0 || cameraIdSPhysical != null ) {
                CameraControllerManager camera_controller_manager = preview.getCameraControllerManager();
                CameraController.Facing front_facing = camera_controller_manager.getFacing(cameraId);
                if( MyDebug.LOG )
                    Log.d(TAG, "front_facing: " + front_facing);
                if( camera_controller_manager.getNumberOfCameras() > 2 || cameraIdSPhysical != null ) {
                    boolean camera_is_default = true;
                    if( cameraIdSPhysical != null )
                        camera_is_default = false;
                    for(int i=0;i<cameraId && camera_is_default;i++) {
                        CameraController.Facing that_front_facing = camera_controller_manager.getFacing(i);
                        if( MyDebug.LOG )
                            Log.d(TAG, "camera " + i + " that_front_facing: " + that_front_facing);
                        if( that_front_facing == front_facing ) {
                            // found an earlier camera with same front/back facing
                            camera_is_default = false;
                        }
                    }
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera_is_default: " + camera_is_default);
                    if( !camera_is_default ) {
                        this.pushCameraIdToast(cameraId, cameraIdSPhysical);
                    }
                }
            }

            this.announceCameraForAccessibility(cameraId, cameraIdSPhysical);
        }

        push_switched_camera = false; // just in case

        if( MyDebug.LOG ) {
            Log.d(TAG, "onResume: total time to resume: " + (System.currentTimeMillis() - debug_time));
        }
    }

    /** Give details on the camera for talkback. Should be called when resuming (but not every time the
     *  camera is reopened), or if switching camera.
     */
    private void announceCameraForAccessibility(int cameraId, String cameraIdSPhysical) {
        String description = cameraIdSPhysical != null ?
                preview.getCameraControllerManager().getDescription(null, this, cameraIdSPhysical, true, false) :
                preview.getCameraControllerManager().getDescription(this, cameraId);
        if( description != null ) {
            String talkback_string = description;
            if( cameraIdSPhysical == null )
                talkback_string += " " + getResources().getString(R.string.camera_id) + " " + cameraId;
            else
                talkback_string += " " + getResources().getString(R.string.lens) + " " + cameraIdSPhysical;
            if( MyDebug.LOG )
                Log.d(TAG, "talkback_string: " + talkback_string);
            this.preview.getView().announceForAccessibility(talkback_string);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if( MyDebug.LOG )
            Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        if( !this.camera_in_background && hasFocus ) {
            // low profile mode is cleared when app goes into background
            // and for Kit Kat immersive mode, we want to set up the timer
            // we do in onWindowFocusChanged rather than onResume(), to also catch when window lost focus due to notification bar being dragged down (which prevents resetting of immersive mode)
            initImmersiveMode();
        }
    }

    @Override
    protected void onPause() {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "onPause");
            debug_time = System.currentTimeMillis();
        }
        super.onPause(); // docs say to call this before freeing other things
        this.app_is_paused = true;

        mainUI.destroyPopup(); // important as user could change/reset settings from Android settings when pausing
        if( this.switch_multi_camera_dialog != null ) {
            // to be safe - again, camera ID could reset
            if( MyDebug.LOG )
                Log.d(TAG, "clear switch_multi_camera_dialog");
            this.switch_multi_camera_dialog = null;
        }
        unregisterDisplayListener();
        mSensorManager.unregisterListener(accelerometerListener);
        magneticSensor.unregisterMagneticListener(mSensorManager);
        if( orientationEventListener != null ) {
            orientationEventListener.disable();
        }
        getWindow().getDecorView().removeOnLayoutChangeListener(layoutChangeListener);
        bluetoothRemoteControl.stopRemoteControl();
        freeAudioListener(false);
        //speechControl.stopSpeechRecognizer();
        applicationInterface.getLocationSupplier().freeLocationListeners();
        applicationInterface.stopPanorama(true); // in practice not needed as we should stop panorama when camera is closed, but good to do it explicitly here, before disabling the gyro sensors
        applicationInterface.getGyroSensor().disableSensors();
        applicationInterface.getImageSaver().onPause();
        soundPoolManager.releaseSound();
        applicationInterface.clearLastImages(); // this should happen when pausing the preview, but call explicitly just to be safe
        applicationInterface.getDrawPreview().clearGhostImage();
        preview.onPause();
        applicationInterface.getDrawPreview().setCoverPreview(true); // must be after we've closed the preview (otherwise risk that further frames from preview will unset the cover_preview flag in DrawPreview)

        if( applicationInterface.getImageSaver().getNImagesToSave() > 0) {
            createImageSavingNotification();
        }

        if( update_gallery_future != null ) {
            update_gallery_future.cancel(true);
        }

        // intentionally do this again, just in case something turned location on since - keep this right at the end:
        applicationInterface.getLocationSupplier().freeLocationListeners();

        // don't want to enter immersive mode when in background
        // needs to be last in case anything above indirectly called initImmersiveMode()
        cancelImmersiveTimer();

        if( MyDebug.LOG ) {
            Log.d(TAG, "onPause: total time to pause: " + (System.currentTimeMillis() - debug_time));
        }
    }

    private class MyDisplayListener implements DisplayManager.DisplayListener {
        private int old_rotation;

        private MyDisplayListener() {
            int rotation = MainActivity.this.getWindowManager().getDefaultDisplay().getRotation();
            if( MyDebug.LOG ) {
                Log.d(TAG, "MyDisplayListener");
                Log.d(TAG, "rotation: " + rotation);
            }
            old_rotation = rotation;
        }

        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            int rotation = MainActivity.this.getWindowManager().getDefaultDisplay().getRotation();
            if( MyDebug.LOG ) {
                Log.d(TAG, "onDisplayChanged: " + displayId);
                Log.d(TAG, "rotation: " + rotation);
                Log.d(TAG, "old_rotation: " + old_rotation);
            }
            if( ( rotation == Surface.ROTATION_0 && old_rotation == Surface.ROTATION_180 ) ||
                    ( rotation == Surface.ROTATION_180 && old_rotation == Surface.ROTATION_0 ) ||
                    ( rotation == Surface.ROTATION_90 && old_rotation == Surface.ROTATION_270 ) ||
                    ( rotation == Surface.ROTATION_270 && old_rotation == Surface.ROTATION_90 )
            ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "onDisplayChanged: switched between landscape and reverse orientation");
                onSystemOrientationChanged();
            }

            old_rotation = rotation;
        }
    }

    /** Creates and registers a display listener, needed to handle switches between landscape and
     *  reverse landscape (without going via portrait) when lock_to_landscape==false.
     */
    private void registerDisplayListener() {
        if( MyDebug.LOG )
            Log.d(TAG, "registerDisplayListener");
        if( !lock_to_landscape ) {
            displayListener = new MyDisplayListener();
            DisplayManager displayManager = (DisplayManager) this.getSystemService(Context.DISPLAY_SERVICE);
            displayManager.registerDisplayListener(displayListener, null);
        }
    }

    private void unregisterDisplayListener() {
        if( MyDebug.LOG )
            Log.d(TAG, "unregisterDisplayListener");
        if( displayListener != null ) {
            DisplayManager displayManager = (DisplayManager) this.getSystemService(Context.DISPLAY_SERVICE);
            displayManager.unregisterDisplayListener(displayListener);
            displayListener = null;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        if( MyDebug.LOG )
            Log.d(TAG, "onConfigurationChanged(): " + newConfig.orientation);
        // configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
        // needed if app is paused/resumed when settings is open and device is in portrait mode
        // update: need this all the time when lock_to_landscape==false
        onSystemOrientationChanged();
        super.onConfigurationChanged(newConfig);
    }

    private void onSystemOrientationChanged() {
        if( MyDebug.LOG )
            Log.d(TAG, "onSystemOrientationChanged");

        // n.b., need to call this first, before preview.setCameraDisplayOrientation(), since
        // preview.setCameraDisplayOrientation() will call getDisplayRotation() and we don't want
        // to be using the outdated cached value now that the rotation has changed!
        // update: no longer relevant, as preview.setCameraDisplayOrientation() now sets
        // prefer_later to true to avoid using cached value. But might as well call it first anyway.
        resetCachedSystemOrientation();

        preview.setCameraDisplayOrientation();
        if( !lock_to_landscape ) {
            SystemOrientation newSystemOrientation = getSystemOrientation();
            if( hasOldSystemOrientation && oldSystemOrientation == newSystemOrientation ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "onSystemOrientationChanged: orientation hasn't changed");
            }
            else {
                if( hasOldSystemOrientation ) {
                    // handle rotation animation
                    int start_rotation = getRotationFromSystemOrientation(oldSystemOrientation) - getRotationFromSystemOrientation(newSystemOrientation);
                    if( MyDebug.LOG )
                        Log.d(TAG, "start_rotation: " + start_rotation);
                    if( start_rotation < -180 )
                        start_rotation += 360;
                    else if( start_rotation > 180 )
                        start_rotation -= 360;
                    mainUI.layoutUIWithRotation(start_rotation);
                }
                else {
                    mainUI.layoutUI();
                }
                applicationInterface.getDrawPreview().updateSettings();

                hasOldSystemOrientation = true;
                oldSystemOrientation = newSystemOrientation;
            }
        }
    }

    /** Returns the current system orientation.
     *  Note if lock_to_landscape is true, this always returns LANDSCAPE even if called when we're
     *  allowing configuration changes (e.g., in Settings or a dialog is showing). (This method,
     *  and hence calls to it, were added to support lock_to_landscape==false behaviour, and we
     *  want to avoid changing behaviour for lock_to_landscape==true behaviour.)
     *  Note that this also caches the orientation: firstly for performance (as this is called from
     *  DrawPreview), secondly to support REVERSE_LANDSCAPE, we don't want a sudden change if
     *  getDefaultDisplay().getRotation() changes after the configuration changes.
     */
    public SystemOrientation getSystemOrientation() {
        if( test_force_system_orientation ) {
            return test_system_orientation;
        }
        if( lock_to_landscape ) {
            return SystemOrientation.LANDSCAPE;
        }
        if( has_cached_system_orientation ) {
            return cached_system_orientation;
        }
        SystemOrientation result;
        int system_orientation = getResources().getConfiguration().orientation;
        if( MyDebug.LOG )
            Log.d(TAG, "system orientation: " + system_orientation);
        switch( system_orientation ) {
            case Configuration.ORIENTATION_LANDSCAPE:
                result = SystemOrientation.LANDSCAPE;
                // now try to distinguish between landscape and reverse landscape
                {
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    if( MyDebug.LOG )
                        Log.d(TAG, "rotation: " + rotation);
                    switch( rotation ) {
                        case Surface.ROTATION_0:
                        case Surface.ROTATION_90:
                            // landscape
                            if( MyDebug.LOG )
                                Log.d(TAG, "landscape");
                            break;
                        case Surface.ROTATION_180:
                        case Surface.ROTATION_270:
                            // reverse landscape
                            if( MyDebug.LOG )
                                Log.d(TAG, "reverse landscape");
                            result = SystemOrientation.REVERSE_LANDSCAPE;
                            break;
                        default:
                            if( MyDebug.LOG )
                                Log.e(TAG, "unknown rotation: " + rotation);
                            break;
                    }
                }
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                result = SystemOrientation.PORTRAIT;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                if( MyDebug.LOG )
                    Log.e(TAG, "unknown system orientation: " + system_orientation);
                result = SystemOrientation.LANDSCAPE;
                break;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "system orientation is now: " + result);
        this.has_cached_system_orientation = true;
        this.cached_system_orientation = result;
        return result;
    }

    /** Returns rotation in degrees (as a multiple of 90 degrees) corresponding to the supplied
     *  system orientation.
     */
    public static int getRotationFromSystemOrientation(SystemOrientation system_orientation) {
        int rotation;
        if( system_orientation == MainActivity.SystemOrientation.PORTRAIT )
            rotation = 270;
        else if( system_orientation == MainActivity.SystemOrientation.REVERSE_LANDSCAPE )
            rotation = 180;
        else
            rotation = 0;
        return rotation;
    }

    private void resetCachedSystemOrientation() {
        this.has_cached_system_orientation = false;
        this.has_cached_display_rotation = false;
    }

    /** A wrapper for getWindowManager().getDefaultDisplay().getRotation(), except if
     *  lock_to_landscape==false && prefer_later==false, this uses a cached value.
     */
    public int getDisplayRotation(boolean prefer_later) {
        /*if( MyDebug.LOG ) {
            Log.d(TAG, "getDisplayRotationDegrees");
            Log.d(TAG, "prefer_later: " + prefer_later);
        }*/
        if( lock_to_landscape || prefer_later ) {
            return getWindowManager().getDefaultDisplay().getRotation();
        }
        // we cache to reduce effect of annoying problem where rotation changes shortly before the
        // configuration actually changes (several frames), so on-screen elements would briefly show
        // in wrong location when device rotates from/to portrait and landscape; also not a bad idea
        // to cache for performance anyway, to avoid calling
        // getWindowManager().getDefaultDisplay().getRotation() every frame
        long time_ms = System.currentTimeMillis();
        if( has_cached_display_rotation && time_ms < cached_display_rotation_time_ms + 1000 ) {
            return cached_display_rotation;
        }
        has_cached_display_rotation = true;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        cached_display_rotation = rotation;
        cached_display_rotation_time_ms = time_ms;
        return rotation;
    }

    public void waitUntilImageQueueEmpty() {
        if( MyDebug.LOG )
            Log.d(TAG, "waitUntilImageQueueEmpty");
        applicationInterface.getImageSaver().waitUntilDone();
    }

    /**
     * @return True if the long-click is handled, otherwise return false to indicate that regular
     * click should still be triggered when the user releases the touch.
     */
    private boolean longClickedTakePhoto() {
        if( MyDebug.LOG )
            Log.d(TAG, "longClickedTakePhoto");
        if( preview.isVideo() ) {
            // no long-click action for video mode
        }
        else if( supportsFastBurst() ) {
            // need to check whether fast burst is supported (including for the current resolution),
            // in case we're in Standard photo mode
            CameraController.Size current_size = preview.getCurrentPictureSize();
            if( current_size != null && current_size.supports_burst ) {
                MyApplicationInterface.PhotoMode photo_mode = applicationInterface.getPhotoMode();
                if( photo_mode == MyApplicationInterface.PhotoMode.Standard &&
                        applicationInterface.isRawOnly(photo_mode) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "fast burst not supported in RAW-only mode");
                    // in JPEG+RAW mode, a continuous fast burst will only produce JPEGs which is fine; but in RAW only mode,
                    // no images at all would be saved! (Or we could switch to produce JPEGs anyway, but this seems misleading
                    // in RAW only mode.)
                }
                else if( photo_mode == MyApplicationInterface.PhotoMode.Standard ||
                        photo_mode == MyApplicationInterface.PhotoMode.FastBurst ) {
                    this.takePicturePressed(false, true);
                    return true;
                }
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "fast burst not supported for this resolution");
            }
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "fast burst not supported");
        }
        // return false, so a regular click will still be triggered when the user releases the touch
        return false;
    }

    public void clickedTakePhoto(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedTakePhoto");
        this.takePicture(false);
    }

    /** User has clicked button to take a photo snapshot whilst video recording.
     */
    public void clickedTakePhotoVideoSnapshot(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedTakePhotoVideoSnapshot");
        this.takePicture(true);
    }

    public void clickedPauseVideo(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedPauseVideo");
        pauseVideo();
    }

    public void pauseVideo() {
        if( MyDebug.LOG )
            Log.d(TAG, "pauseVideo");
        if( preview.isVideoRecording() ) { // just in case
            preview.pauseVideo();
            mainUI.setPauseVideoContentDescription();
        }
    }

    public void clickedCancelPanorama(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedCancelPanorama");
        applicationInterface.stopPanorama(true);
    }

    public void clickedCycleRaw(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedCycleRaw");

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String new_value = null;
        switch( sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no") ) {
            case "preference_raw_no":
                new_value = "preference_raw_yes";
                break;
            case "preference_raw_yes":
                new_value = "preference_raw_only";
                break;
            case "preference_raw_only":
                new_value = "preference_raw_no";
                break;
            default:
                Log.e(TAG, "unrecognised raw preference");
                break;
        }
        if( new_value != null ) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(PreferenceKeys.RawPreferenceKey, new_value);
            editor.apply();

            mainUI.updateCycleRawIcon();
            applicationInterface.getDrawPreview().updateSettings();
            preview.reopenCamera(); // needed for RAW options to take effect
        }
    }

    public void clickedStoreLocation(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedStoreLocation");
        boolean value = applicationInterface.getGeotaggingPref();
        value = !value;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.LocationPreferenceKey, value);
        editor.apply();

        mainUI.updateStoreLocationIcon();
        applicationInterface.getDrawPreview().updateSettings(); // because we cache the geotagging setting
        initLocation(); // required to enable or disable GPS, also requests permission if necessary
        this.closePopup();

        String message = getResources().getString(R.string.preference_location) + ": " + getResources().getString(value ? R.string.on : R.string.off);
        preview.showToast(store_location_toast, message, true);
    }

    public void clickedTextStamp(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedTextStamp");
        this.closePopup();

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.preference_textstamp);

        final View dialog_view = LayoutInflater.from(this).inflate(R.layout.alertdialog_edittext, null);
        final EditText editText = dialog_view.findViewById(R.id.edit_text);
        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
        editText.setHint(getResources().getString(R.string.preference_textstamp));
        editText.setText(applicationInterface.getTextStampPref());
        alertDialog.setView(dialog_view);
        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if( MyDebug.LOG )
                    Log.d(TAG, "custom text stamp clicked okay");

                String custom_text = editText.getText().toString();
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.TextStampPreferenceKey, custom_text);
                editor.apply();

                mainUI.updateTextStampIcon();
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog alert = alertDialog.create();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                if( MyDebug.LOG )
                    Log.d(TAG, "custom stamp text dialog dismissed");
                setWindowFlagsForCamera();
                showPreview(true);
            }
        });

        showPreview(false);
        setWindowFlagsForSettings();
        showAlert(alert);
    }

    public void clickedStamp(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedStamp");

        this.closePopup();

        boolean value = applicationInterface.getStampPref().equals("preference_stamp_yes");
        value = !value;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.StampPreferenceKey, value ? "preference_stamp_yes" : "preference_stamp_no");
        editor.apply();

        mainUI.updateStampIcon();
        applicationInterface.getDrawPreview().updateSettings();
        preview.showToast(stamp_toast, value ? R.string.stamp_enabled : R.string.stamp_disabled, true);
    }

    public void clickedFocusPeaking(View view) {
        clickedFocusPeaking();
    }

    public void clickedFocusPeaking() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedFocusPeaking");
        boolean value = applicationInterface.getFocusPeakingPref();
        value = !value;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.FocusPeakingPreferenceKey, value ? "preference_focus_peaking_on" : "preference_focus_peaking_off");
        editor.apply();

        mainUI.updateFocusPeakingIcon();
        applicationInterface.getDrawPreview().updateSettings(); // needed to update focus peaking
    }

    public void clickedAutoLevel(View view) {
        clickedAutoLevel();
    }

    public void clickedAutoLevel() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedAutoLevel");
        boolean value = applicationInterface.getAutoStabilisePref();
        value = !value;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, value);
        editor.apply();

        boolean done_dialog = false;
        if( value ) {
            boolean done_auto_stabilise_info = sharedPreferences.contains(PreferenceKeys.AutoStabiliseInfoPreferenceKey);
            if( !done_auto_stabilise_info ) {
                mainUI.showInfoDialog(R.string.preference_auto_stabilise, R.string.auto_stabilise_info, PreferenceKeys.AutoStabiliseInfoPreferenceKey);
                done_dialog = true;
            }
        }

        if( !done_dialog ) {
            String message = getResources().getString(R.string.preference_auto_stabilise) + ": " + getResources().getString(value ? R.string.on : R.string.off);
            preview.showToast(this.getChangedAutoStabiliseToastBoxer(), message, true);
        }

        mainUI.updateAutoLevelIcon();
        applicationInterface.getDrawPreview().updateSettings(); // because we cache the auto-stabilise setting
        this.closePopup();
    }

    public void clickedCycleFlash(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedCycleFlash");

        preview.cycleFlash(true, true);
        mainUI.updateCycleFlashIcon();
    }

    public void clickedFaceDetection(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedFaceDetection");

        this.closePopup();

        boolean value = applicationInterface.getFaceDetectionPref();
        value = !value;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PreferenceKeys.FaceDetectionPreferenceKey, value);
        editor.apply();

        mainUI.updateFaceDetectionIcon();
        preview.showToast(stamp_toast, value ? R.string.face_detection_enabled : R.string.face_detection_disabled, true);
        block_startup_toast = true; // so the toast from reopening camera is suppressed, otherwise it conflicts with the face detection toast
        preview.reopenCamera();
    }

    public void clickedAudioControl(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedAudioControl");
        // check hasAudioControl just in case!
        if( !hasAudioControl() ) {
            if( MyDebug.LOG )
                Log.e(TAG, "clickedAudioControl, but hasAudioControl returns false!");
            return;
        }
        this.closePopup();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String audio_control = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none");
        /*if( audio_control.equals("voice") && speechControl.hasSpeechRecognition() ) {
            if( speechControl.isStarted() ) {
                speechControl.stopListening();
            }
            else {
                boolean has_audio_permission = true;
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
                    if( MyDebug.LOG )
                        Log.d(TAG, "check for record audio permission");
                    if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "record audio permission not available");
                        applicationInterface.requestRecordAudioPermission();
                        has_audio_permission = false;
                    }
                }
                if( has_audio_permission ) {
                    speechControl.showToast(true);
                    speechControl.startSpeechRecognizerIntent();
                    speechControl.speechRecognizerStarted();
                }
            }
        }
        else*/ if( audio_control.equals("noise") ){
            if( audio_listener != null ) {
                freeAudioListener(false);
            }
            else {
                startAudioListener();
            }
        }
    }

    /* Returns the cameraId that the "Switch camera" button will switch to.
     * Note that this may not necessarily be the next camera ID, on multi camera devices (if
     * isMultiCamEnabled() returns true).
     */
    public int getNextCameraId() {
        if( MyDebug.LOG )
            Log.d(TAG, "getNextCameraId");
        int cameraId = getActualCameraId();
        if( MyDebug.LOG )
            Log.d(TAG, "current cameraId: " + cameraId);
        if( this.preview.canSwitchCamera() ) {
            if( isMultiCamEnabled() ) {
                // don't use preview.getCameraController(), as it may be null if user quickly switches between cameras
                switch( preview.getCameraControllerManager().getFacing(cameraId) ) {
                    case FACING_BACK:
                        if( !front_camera_ids.isEmpty() )
                            cameraId = front_camera_ids.get(0);
                        else if( !other_camera_ids.isEmpty() )
                            cameraId = other_camera_ids.get(0);
                        break;
                    case FACING_FRONT:
                        if( !other_camera_ids.isEmpty() )
                            cameraId = other_camera_ids.get(0);
                        else if( !back_camera_ids.isEmpty() )
                            cameraId = back_camera_ids.get(0);
                        break;
                    default:
                        if( !back_camera_ids.isEmpty() )
                            cameraId = back_camera_ids.get(0);
                        else if( !front_camera_ids.isEmpty() )
                            cameraId = front_camera_ids.get(0);
                        break;
                }
            }
            else {
                int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
                cameraId = (cameraId+1) % n_cameras;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "next cameraId: " + cameraId);
        return cameraId;
    }

    /* Returns the next cameraId with the same-facing as current camera.
     * Should only be called if isMultiCamEnabled() returns true.
     * Only used for testing, now that we bring up a menu instead of cycling.
     */
    /*public int testGetNextMultiCameraId() {
        if( MyDebug.LOG )
            Log.d(TAG, "testGetNextMultiCameraId");
        if( !isMultiCamEnabled() ) {
            Log.e(TAG, "testGetNextMultiCameraId() called but not in multi-cam mode");
            throw new RuntimeException("testGetNextMultiCameraId() called but not in multi-cam mode");
        }
        List<Integer> camera_set;
        // don't use preview.getCameraController(), as it may be null if user quickly switches between cameras
        int currCameraId = getActualCameraId();
        switch( preview.getCameraControllerManager().getFacing(currCameraId) ) {
            case FACING_BACK:
                camera_set = back_camera_ids;
                break;
            case FACING_FRONT:
                camera_set = front_camera_ids;
                break;
            default:
                camera_set = other_camera_ids;
                break;
        }
        int cameraId;
        int indx = camera_set.indexOf(currCameraId);
        if( indx == -1 ) {
            Log.e(TAG, "camera id not in current camera set");
            // this shouldn't happen, but if it does, revert to the first camera id in the set
            // update: oddly had reports of IndexOutOfBoundsException crashes from Google Play from camera_set.get(0)
            // because of camera_set having length 0, so stick with currCameraId in such cases
            if( camera_set.size() == 0 ) {
                Log.e(TAG, "camera_set is empty");
                cameraId = currCameraId;
            }
            else
                cameraId = camera_set.get(0);
        }
        else {
            indx = (indx+1) % camera_set.size();
            cameraId = camera_set.get(indx);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "next multi cameraId: " + cameraId);
        return cameraId;
    }*/

    private void pushCameraIdToast(int cameraId, String cameraIdSPhysical) {
        if( MyDebug.LOG )
            Log.d(TAG, "pushCameraIdToast: " + cameraId);
        if( preview.getCameraControllerManager().getNumberOfCameras() > 2 || cameraIdSPhysical != null ) {
            // telling the user which camera is pointless for only two cameras, but on devices that now
            // expose many cameras it can be confusing, so show a toast to at least display the id
            // similarly we want to show a toast if using a physical camera, so user doesn't forget
            String description = cameraIdSPhysical != null ?
                    preview.getCameraControllerManager().getDescription(null, this, cameraIdSPhysical, true, true) :
                    preview.getCameraControllerManager().getDescription(this, cameraId);
            if( description != null ) {
                String toast_string = description;
                if( cameraIdSPhysical == null ) // only add the ID if not a physical camera
                    toast_string += ": " + getResources().getString(R.string.camera_id) + " " + cameraId;
                //preview.showToast(null, toast_string);
                this.push_info_toast_text = toast_string;
            }
        }
    }

    public void userSwitchToCamera(int cameraId, String cameraIdSPhysical) {
        if( MyDebug.LOG )
            Log.d(TAG, "userSwitchToCamera: " + cameraId + " / " + cameraIdSPhysical);
        View switchCameraButton = findViewById(R.id.switch_camera);
        View switchMultiCameraButton = findViewById(R.id.switch_multi_camera);
        // prevent slowdown if user repeatedly clicks:
        switchCameraButton.setEnabled(false);
        switchMultiCameraButton.setEnabled(false);
        applicationInterface.reset(true);
        this.getApplicationInterface().getDrawPreview().setDimPreview(true);
        if( this.switch_multi_camera_dialog != null ) {
            // only clear if switching to a different camera ID (switching between lenses is fine)
            int curr_camera_id = getActualCameraId();
            if( MyDebug.LOG )
                Log.d(TAG, "curr_camera_id: " + curr_camera_id);
            if( cameraId != curr_camera_id ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "clear switch_multi_camera_dialog");
                this.switch_multi_camera_dialog = null;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "keep switch_multi_camera_dialog");
            }
        }
        this.preview.setCamera(cameraId, cameraIdSPhysical);
        switchCameraButton.setEnabled(true);
        switchMultiCameraButton.setEnabled(true);
        // no need to call mainUI.setSwitchCameraContentDescription - this will be called from Preview.cameraSetup when the
        // new camera is opened
        this.announceCameraForAccessibility(cameraId, cameraIdSPhysical);
    }

    /**
     * Selects the next camera on the phone - in practice, switches between
     * front and back cameras
     */
    public void clickedSwitchCamera(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedSwitchCamera");
        if( preview.isOpeningCamera() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "already opening camera in background thread");
            return;
        }
        this.closePopup();
        if( this.preview.canSwitchCamera() ) {
            int cameraId = getNextCameraId();
            if( !isMultiCamEnabled() ) {
                pushCameraIdToast(cameraId, null);
            }
            else {
                // In multi-cam mode, no need to show the toast when just switching between front and back cameras.
                // But it is useful to clear an active fake toast, otherwise have issue if the user uses
                // clickedSwitchMultiCamera() (which displays a fake toast for the camera via the info toast), then
                // immediately uses clickedSwitchCamera() - the toast for the wrong camera will still be lingering
                // until it expires, which looks a bit strange.
                // (If using non-fake toasts, this isn't an issue, at least on Android 10+, as now toasts seem to
                // disappear when the user touches the screen anyway.)
                preview.clearActiveFakeToast();
            }
            userSwitchToCamera(cameraId, null);

            push_switched_camera = true;
        }
    }

    /** Returns list of logical cameras with same facing as the supplied camera_id.
     */
    public List<Integer> getSameFacingLogicalCameras(int camera_id) {
        List<Integer> logical_camera_ids = new ArrayList<>();
        CameraController.Facing this_facing = preview.getCameraControllerManager().getFacing(camera_id);
        for(int i=0;i<preview.getCameraControllerManager().getNumberOfCameras();i++) {
            if( preview.getCameraControllerManager().getFacing(i) != this_facing ) {
                // only show cameras with same facing
                continue;
            }
            logical_camera_ids.add(i);
        }
        return logical_camera_ids;
    }

    private AlertDialog switch_multi_camera_dialog;

    private AlertDialog createSwitchMultiCameraDialog() {
        if( MyDebug.LOG )
            Log.d(TAG, "createSwitchMultiCameraDialog");
        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.choose_camera);

        int curr_camera_id = getActualCameraId();
        List<Integer> logical_camera_ids = getSameFacingLogicalCameras(curr_camera_id);
        if( MyDebug.LOG )
            Log.d(TAG, "createSwitchMultiCameraDialog: time after logical_camera_ids: " + (System.currentTimeMillis() - debug_time));

        int n_logical_cameras = logical_camera_ids.size();
        int n_cameras = n_logical_cameras;
        if( preview.hasPhysicalCameras() ) {
            n_cameras += preview.getPhysicalCameras().size();
            //n_cameras++; // for the info message
        }
        CharSequence [] items = new CharSequence[n_cameras];
        int [] items_logical_camera_id = new int[n_cameras];
        String [] items_physical_camera_id = new String[n_cameras];
        int index=0;
        int selected=-1;
        String curr_physical_camera_id = applicationInterface.getCameraIdSPhysicalPref();
        for(int i=0;i<n_logical_cameras;i++) {
            int logical_camera_id = logical_camera_ids.get(i);
            if( MyDebug.LOG )
                Log.d(TAG, "createSwitchMultiCameraDialog: time before getDescription: " + (System.currentTimeMillis() - debug_time));
            String camera_name = logical_camera_id + ": " + preview.getCameraControllerManager().getDescription(this, logical_camera_id);
            if( MyDebug.LOG )
                Log.d(TAG, "createSwitchMultiCameraDialog: time after getDescription: " + (System.currentTimeMillis() - debug_time));
            if( logical_camera_id == curr_camera_id ) {
                // this is the current logical camera
                if( preview.hasPhysicalCameras() ) {
                    camera_name +=  " (" + getResources().getString(R.string.auto_lens) + ")";
                }
                if( curr_physical_camera_id == null ) {
                    // the logical camera is being used directly
                    selected = index;
                    //String html_camera_name = "<b>[" + camera_name + "]</b>";
                    String html_camera_name = "<b>" + camera_name + "</b>";
                    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                        items[index] = Html.fromHtml(html_camera_name, Html.FROM_HTML_MODE_LEGACY);
                    }
                    else {
                        items[index] = Html.fromHtml(html_camera_name);
                    }
                }
                else {
                    // a physical camera is in use, so don't bold this entry
                    items[index] = camera_name;
                }
                items_logical_camera_id[index] = logical_camera_id;
                items_physical_camera_id[index] = null;
                index++;

                if( preview.hasPhysicalCameras() ) {
                    // also add the physical cameras that underlie the current logical camera
                    Set<String> physical_camera_ids = preview.getPhysicalCameras();

                    // sort by view angle
                    class PhysicalCamera {
                        private final String id;
                        private final String description;
                        private final SizeF view_angle;

                        private PhysicalCamera(String id) {
                            this.id = id;
                            CameraControllerManager.CameraInfo info = new CameraControllerManager.CameraInfo();
                            this.description = preview.getCameraControllerManager().getDescription(info, MainActivity.this, id, false, true);
                            this.view_angle = info.view_angle;
                        }
                    }
                    ArrayList<PhysicalCamera> physical_cameras = new ArrayList<>();
                    for(String physical_id : physical_camera_ids) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "createSwitchMultiCameraDialog: time before getDescription: " + (System.currentTimeMillis() - debug_time));
                        physical_cameras.add(new PhysicalCamera(physical_id));
                        if( MyDebug.LOG )
                            Log.d(TAG, "createSwitchMultiCameraDialog: time after getDescription: " + (System.currentTimeMillis() - debug_time));
                    }
                    {
                        Collections.sort(physical_cameras, new Comparator<>() {
                            @Override
                            public int compare(PhysicalCamera o1, PhysicalCamera o2) {
                                float diff = o2.view_angle.getWidth() - o1.view_angle.getWidth();
                                if( Math.abs(diff) < 1.0e-5f )
                                    return 0;
                                else if( diff > 0.0f )
                                    return 1;
                                else
                                    return -1;
                            }
                        });
                    }

                    int j=0;
                    String indent = "&nbsp;&nbsp;&nbsp;&nbsp;";
                    for(PhysicalCamera physical_camera : physical_cameras) {
                        String physical_id = physical_camera.id;
                        camera_name = getResources().getString(R.string.lens) + " " + j + ": " + physical_camera.description;
                        String html_camera_name;
                        if( curr_physical_camera_id != null && curr_physical_camera_id.equals(physical_id) ) {
                            // this is the current physical camera
                            selected = index;
                            //html_camera_name = indent + "<b>[" + camera_name + "]</b>";
                            html_camera_name = indent + "<b>" + camera_name + "</b>";
                        }
                        else {
                            html_camera_name = indent + camera_name;
                        }
                        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                            items[index] = Html.fromHtml(html_camera_name, Html.FROM_HTML_MODE_LEGACY);
                        }
                        else {
                            items[index] = Html.fromHtml(html_camera_name);
                        }
                        items_logical_camera_id[index] = logical_camera_id;
                        items_physical_camera_id[index] = physical_id;
                        index++;

                        j++;
                    }
                }
            }
            else {
                items[index] = camera_name;
                items_logical_camera_id[index] = logical_camera_id;
                items_physical_camera_id[index] = null;
                index++;
            }
        }
        /*if( preview.hasPhysicalCameras() ) {
            items[index] = getResources().getString(R.string.physical_cameras_info);
            items_logical_camera_id[index] = -1;
            items_physical_camera_id[index] = null;
            //index++;
        }*/
        if( MyDebug.LOG )
            Log.d(TAG, "createSwitchMultiCameraDialog: time after building menu: " + (System.currentTimeMillis() - debug_time));

        //alertDialog.setItems(items, new DialogInterface.OnClickListener() {
        alertDialog.setSingleChoiceItems(items, selected, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if( MyDebug.LOG )
                    Log.d(TAG, "selected: " + which);
                int logical_camera = items_logical_camera_id[which];
                String physical_camera = items_physical_camera_id[which];
                if( MyDebug.LOG ) {
                    Log.d(TAG, "logical_camera: " + logical_camera);
                    Log.d(TAG, "physical_camera: " + physical_camera);
                }
                int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
                if( logical_camera >= 0 && logical_camera < n_cameras ) {
                    if( preview.isOpeningCamera() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "already opening camera in background thread");
                        return;
                    }
                    MainActivity.this.closePopup();
                    if( MainActivity.this.preview.canSwitchCamera() ) {
                        pushCameraIdToast(logical_camera, physical_camera);
                        userSwitchToCamera(logical_camera, physical_camera);
                    }
                }
                //setWindowFlagsForCamera();
                //showPreview(true);
                dialog.dismiss(); // need to explicitly dismiss for setSingleChoiceItems
            }
        });
        if( MyDebug.LOG )
            Log.d(TAG, "createSwitchMultiCameraDialog: time after setting items: " + (System.currentTimeMillis() - debug_time));
        /*alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                setWindowFlagsForCamera();
                showPreview(true);
            }
        });*/
        //setWindowFlagsForSettings(false); // set set_lock_protect to false - no need to protect this dialog with lock screen (fine to run above lock screen if that option is set)
        //showAlert(alertDialog.create());
        AlertDialog dialog = alertDialog.create();
        if( MyDebug.LOG )
            Log.d(TAG, "createSwitchMultiCameraDialog: time after dialog create: " + (System.currentTimeMillis() - debug_time));
        if( preview.hasPhysicalCameras() ) {
            TextView footer = new TextView(this);
            footer.setText(R.string.physical_cameras_info);
            final float scale = getResources().getDisplayMetrics().density;
            final int padding = (int) (5 * scale + 0.5f); // convert dps to pixels
            footer.setPadding(padding, padding, padding, padding);
            dialog.getListView().addFooterView(footer, null, false);
            if( MyDebug.LOG )
                Log.d(TAG, "createSwitchMultiCameraDialog: time after adding footer: " + (System.currentTimeMillis() - debug_time));
        }
        if( dialog.getWindow() != null ) {
            dialog.getWindow().setWindowAnimations(R.style.DialogAnimation);
        }
        return dialog;
    }

    /** User can long-click on switch multi cam icon to bring up a menu to switch to any camera.
     *  Update: from v1.53 onwards with support for exposing physical lens, we always call this with
     *  a regular click on the switch multi cam icon.
     */
    public void clickedSwitchMultiCamera(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedSwitchMultiCamera");

        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }
        //showPreview(false);
        //AlertDialog dialog = createSwitchMultiCameraDialog();
        if( switch_multi_camera_dialog == null ) {
            switch_multi_camera_dialog = createSwitchMultiCameraDialog();
        }
        AlertDialog dialog = switch_multi_camera_dialog;
        if( MyDebug.LOG )
            Log.d(TAG, "clickedSwitchMultiCamera: time before showing dialog: " + (System.currentTimeMillis() - debug_time));
        dialog.show();
        if( MyDebug.LOG )
            Log.d(TAG, "clickedSwitchMultiCamera: total time: " + (System.currentTimeMillis() - debug_time));
    }

    /**
     * Toggles Photo/Video mode
     */
    public void clickedSwitchVideo(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedSwitchVideo");
        this.closePopup();
        mainUI.destroyPopup(); // important as we don't want to use a cached popup, as we can show different options depending on whether we're in photo or video mode

        // In practice stopping the gyro sensor shouldn't be needed as (a) we don't show the switch
        // photo/video icon when recording, (b) at the time of writing switching to video mode
        // reopens the camera, which will stop panorama recording anyway, but we do this just to be
        // safe.
        applicationInterface.stopPanorama(true);

        View switchVideoButton = findViewById(R.id.switch_video);
        switchVideoButton.setEnabled(false); // prevent slowdown if user repeatedly clicks
        applicationInterface.reset(false);
        this.getApplicationInterface().getDrawPreview().setDimPreview(true);
        this.preview.switchVideo(false, true);
        switchVideoButton.setEnabled(true);

        mainUI.setTakePhotoIcon();
        mainUI.setPopupIcon(); // needed as turning to video mode or back can turn flash mode off or back on

        // ensure icons invisible if they're affected by being in video mode or not (e.g., on-screen RAW icon)
        // (if enabling them, we'll make the icon visible later on)
        checkDisableGUIIcons();

        if( !block_startup_toast ) {
            this.showPhotoVideoToast(true);
        }
    }

    public void clickedWhiteBalanceLock(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedWhiteBalanceLock");
        this.preview.toggleWhiteBalanceLock();
        mainUI.updateWhiteBalanceLockIcon();
        preview.showToast(white_balance_lock_toast, preview.isWhiteBalanceLocked() ? R.string.white_balance_locked : R.string.white_balance_unlocked, true);
    }

    public void clickedExposureLock(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedExposureLock");
        this.preview.toggleExposureLock();
        mainUI.updateExposureLockIcon();
        preview.showToast(exposure_lock_toast, preview.isExposureLocked() ? R.string.exposure_locked : R.string.exposure_unlocked, true);
    }

    public void clickedExposure(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedExposure");
        mainUI.toggleExposureUI();
    }

    public void clickedSettings(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedSettings");
        KeyguardUtils.requireKeyguard(this, this::openSettings);
    }

    public boolean popupIsOpen() {
        return mainUI.popupIsOpen();
    }

    // for testing
    public View getUIButton(String key) {
        return mainUI.getUIButton(key);
    }

    public void closePopup() {
        mainUI.closePopup();
    }

    public Bitmap getPreloadedBitmap(int resource) {
        return this.preloaded_bitmap_resources.get(resource);
    }

    public void clickedPopupSettings(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedPopupSettings");
        mainUI.togglePopupSettings();
    }

    private final PreferencesListener preferencesListener = new PreferencesListener();

    /** Keeps track of changes to SharedPreferences.
     */
    class PreferencesListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String TAG = "PreferencesListener";

        private boolean any_significant_change; // whether any changes that require updateForSettings have been made since startListening()
        private boolean any_change; // whether any changes have been made since startListening()

        void startListening() {
            if( MyDebug.LOG )
                Log.d(TAG, "startListening");
            any_significant_change = false;
            any_change = false;

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            // n.b., registerOnSharedPreferenceChangeListener warns that we must keep a reference to the listener (which
            // is this class) as long as we want to listen for changes, otherwise the listener may be garbage collected!
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        }

        void stopListening() {
            if( MyDebug.LOG )
                Log.d(TAG, "stopListening");
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if( MyDebug.LOG )
                Log.d(TAG, "onSharedPreferenceChanged: " + key);

            if( key == null ) {
                // on Android 11+, when targetting Android 11+, this method is called with key==null
                // if preferences are cleared (see testSettings(), or when doing "Reset settings")
                return;
            }

            any_change = true;

            switch( key ) {
                // we whitelist preferences where we're sure that we don't need to call updateForSettings() if they've changed
                //case "preference_face_detection": // need to update camera controller
                case "preference_timer":
                case "preference_burst_mode":
                case "preference_burst_interval":
                case "preference_touch_capture":
                case "preference_pause_preview":
                case "preference_shutter_sound":
                case "preference_timer_beep":
                case "preference_timer_speak":
                case "preference_volume_keys":
                    //case "preference_audio_control": // need to update the UI
                case "preference_audio_noise_control_sensitivity":
                    //case "preference_enable_remote": // handled below
                    //case "preference_remote_type":
                    //case "preference_remote_device_name": // handled below
                    //case "preference_remote_disconnect_screen_dim":
                    //case "preference_water_type": // handled below
                case "preference_lock_orientation":
                    //case "preference_save_location": // we could probably whitelist this, but accessed it a lot of places...
                case "preference_using_saf":
                case "preference_save_photo_prefix":
                case "preference_save_video_prefix":
                case "preference_save_zulu_time":
                case "preference_show_when_locked":
                case "preference_startup_focus":
                    //case "preference_preview_size": // need to update preview
                    //case "preference_ghost_image": // don't whitelist this, as may need to reload ghost image (at fullscreen resolution) if "last" is enabled
                case "ghost_image_alpha":
                case "preference_focus_assist":
                case "preference_show_zoom":
                case "preference_show_angle":
                case "preference_show_angle_line":
                case "preference_show_pitch_lines":
                case "preference_angle_highlight_color":
                    //case "preference_show_geo_direction": // don't whitelist these, as if enabled we need to call checkMagneticAccuracy()
                    //case "preference_show_geo_direction_lines": // as above
                case "preference_show_battery":
                case "preference_show_time":
                case "preference_free_memory":
                case "preference_show_iso":
                case "preference_histogram":
                case "preference_zebra_stripes":
                case "preference_zebra_stripes_foreground_color":
                case "preference_zebra_stripes_background_color":
                case "preference_focus_peaking":
                case "preference_focus_peaking_color":
                case "preference_show_video_max_amp":
                case "preference_grid":
                case "preference_crop_guide":
                case "preference_thumbnail_animation":
                case "preference_take_photo_border":
                    //case "preference_rotate_preview": // need to update the Preview
                    //case "preference_ui_placement": // need to update the UI
                    //case "preference_immersive_mode": // probably could whitelist?
                    //case "preference_show_face_detection": // need to update the UI
                    //case "preference_show_cycle_flash": // need to update the UI
                    //case "preference_show_auto_level": // need to update the UI
                    //case "preference_show_stamp": // need to update the UI
                    //case "preference_show_textstamp": // need to update the UI
                    //case "preference_show_store_location": // need to update the UI
                    //case "preference_show_cycle_raw": // need to update the UI
                    //case "preference_show_white_balance_lock": // need to update the UI
                    //case "preference_show_exposure_lock": // need to update the UI
                    //case "preference_show_zoom_slider_controls": // need to update the UI
                    //case "preference_show_take_photo": // need to update the UI
                case "preference_show_toasts":
                case "preference_show_whats_new":
                //case "preference_multi_cam_button": // need to update the UI
                case "preference_keep_display_on":
                case "preference_max_brightness":
                    //case "preference_resolution": // need to set up camera controller and preview
                    //case "preference_quality": // need to set up camera controller
                    //case "preference_image_format": // need to set up camera controller (as it can affect the image quality that we set)
                    //case "preference_raw": // need to update as it affects how we set up camera controller
                    //case "preference_raw_expo_bracketing": // as above
                    //case "preference_raw_focus_bracketing": // as above
                    //case "preference_nr_save": // we could probably whitelist this, but have not done so in case in future we allow RAW to be saved for the base image
                    //case "preference_hdr_save_expo": // we need to update if this is changed, as it affects whether we request RAW or not in HDR mode when RAW is enabled
                case "preference_hdr_tonemapping":
                case "preference_hdr_contrast_enhancement":
                    //case "preference_expo_bracketing_n_images": // need to set up camera controller
                    //case "preference_expo_bracketing_stops": // need to set up camera controller
                case "preference_panorama_crop":
                    //case "preference_panorama_save": // we could probably whitelist this, but have not done so in case in future we allow RAW to be saved for the base images
                case "preference_front_camera_mirror":
                case "preference_exif_artist":
                case "preference_exif_copyright":
                case "preference_stamp":
                case "preference_stamp_dateformat":
                case "preference_stamp_timeformat":
                case "preference_stamp_gpsformat":
                case "preference_stamp_geo_address":
                case "preference_units_distance":
                case "preference_textstamp":
                case "preference_stamp_fontsize":
                case "preference_stamp_font_color":
                case "preference_stamp_style":
                    //case "preference_camera2_fake_flash": // need to update camera controller
                    //case "preference_camera2_fast_burst": // could probably whitelist?
                    //case "preference_camera2_photo_video_recording": // need to update camera controller
                case "preference_background_photo_saving":
                    //case "preference_video_quality": // need to update camera controller and preview
                    //case "preference_video_stabilization": // need to update camera controller
                    //case "preference_video_output_format": // could probably whitelist, but safest to restart camera
                    //case "preference_video_log": // need to update camera controller
                    //case "preference_video_profile_gamma": // as above
                    //case "preference_video_max_duration": // could probably whitelist, but safest to restart camera
                    //case "preference_video_restart": // could probably whitelist, but safest to restart camera
                    //case "preference_video_max_filesize": // could probably whitelist, but safest to restart camera
                    //case "preference_video_restart_max_filesize": // could probably whitelist, but safest to restart camera
                case "preference_record_audio":
                case "preference_record_audio_src":
                case "preference_record_audio_channels":
                case "preference_lock_video":
                case "preference_video_subtitle":
                    //case "preference_video_bitrate": // could probably whitelist, but safest to restart camera
                    //case "preference_video_fps": // could probably whitelist, but safest to restart camera
                    //case "preference_force_video_4k": // could probably whitelist, but safest to restart camera
                case "preference_video_low_power_check":
                case "preference_video_flash":
                    //case "preference_location": // need to enable/disable gps listeners etc
                    //case "preference_gps_direction": // need to update listeners
                case "preference_require_location":
                    //case "preference_antibanding": // need to set up camera controller
                    //case "preference_edge_mode": // need to set up camera controller
                    //case "preference_noise_reduction_mode": // need to set up camera controller
                    //case "preference_camera_api": // no point whitelisting as we restart anyway
                    if( MyDebug.LOG )
                        Log.d(TAG, "this change doesn't require update");
                    break;
                case PreferenceKeys.EnableRemote:
                    bluetoothRemoteControl.startRemoteControl();
                    break;
                case PreferenceKeys.RemoteName:
                    // The remote address changed, restart the service
                    if (bluetoothRemoteControl.remoteEnabled())
                        bluetoothRemoteControl.stopRemoteControl();
                    bluetoothRemoteControl.startRemoteControl();
                    break;
                case PreferenceKeys.WaterType:
                    boolean wt = sharedPreferences.getBoolean(PreferenceKeys.WaterType, true);
                    mWaterDensity = wt ? WATER_DENSITY_SALTWATER : WATER_DENSITY_FRESHWATER;
                    break;
                default:
                    if( MyDebug.LOG )
                        Log.d(TAG, "this change does require update");
                    any_significant_change = true;
                    break;
            }
        }

        boolean anyChange() {
            return any_change;
        }

        boolean anySignificantChange() {
            return any_significant_change;
        }
    }

    public void openSettings() {
        if( MyDebug.LOG )
            Log.d(TAG, "openSettings");
        closePopup(); // important to close the popup to avoid confusing with back button callbacks
        preview.cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
        preview.cancelRepeat(); // similarly cancel the auto-repeat mode!
        preview.stopVideo(false); // important to stop video, as we'll be changing camera parameters when the settings window closes
        applicationInterface.stopPanorama(true); // important to stop panorama recording, as we might end up as we'll be changing camera parameters when the settings window closes
        stopAudioListeners();
        // close back handler callbacks (so back button is enabled again when going to settings) - in theory shouldn't be needed as all of these should
        // be disabled now, but just in case:
        this.enablePopupOnBackPressedCallback(false);
        this.enablePausePreviewOnBackPressedCallback(false);
        this.enableScreenLockOnBackPressedCallback(false);

        Bundle bundle = new Bundle();
        bundle.putBoolean("edge_to_edge_mode", edge_to_edge_mode);
        bundle.putInt("cameraId", this.preview.getCameraId());
        bundle.putString("cameraIdSPhysical", this.applicationInterface.getCameraIdSPhysicalPref());
        bundle.putInt("nCameras", preview.getCameraControllerManager().getNumberOfCameras());
        bundle.putBoolean("camera_open", this.preview.getCameraController() != null);
        bundle.putString("camera_api", this.preview.getCameraAPI());
        bundle.putBoolean("using_android_l", this.preview.usingCamera2API());
        if( this.preview.getCameraController() != null ) {
            bundle.putInt("camera_orientation", this.preview.getCameraController().getCameraOrientation());
        }
        bundle.putString("photo_mode_string", getPhotoModeString(applicationInterface.getPhotoMode(), true));
        bundle.putBoolean("supports_auto_stabilise", this.supports_auto_stabilise);
        bundle.putBoolean("supports_flash", this.preview.supportsFlash());
        bundle.putBoolean("supports_force_video_4k", this.supports_force_video_4k);
        bundle.putBoolean("supports_camera2", this.supports_camera2);
        bundle.putBoolean("supports_face_detection", this.preview.supportsFaceDetection());
        bundle.putBoolean("supports_jpeg_r", this.preview.supportsJpegR());
        bundle.putBoolean("supports_raw", this.preview.supportsRaw());
        bundle.putBoolean("supports_burst_raw", this.supportsBurstRaw());
        bundle.putBoolean("supports_optimise_focus_latency", this.supportsOptimiseFocusLatency());
        bundle.putBoolean("supports_preshots", this.supportsPreShots());
        bundle.putBoolean("supports_hdr", this.supportsHDR());
        bundle.putBoolean("supports_nr", this.supportsNoiseReduction());
        bundle.putBoolean("supports_panorama", this.supportsPanorama());
        bundle.putBoolean("has_gyro_sensors", applicationInterface.getGyroSensor().hasSensors());
        bundle.putBoolean("supports_expo_bracketing", this.supportsExpoBracketing());
        bundle.putBoolean("supports_preview_bitmaps", this.supportsPreviewBitmaps());
        bundle.putInt("max_expo_bracketing_n_images", this.maxExpoBracketingNImages());
        bundle.putBoolean("supports_exposure_compensation", this.preview.supportsExposures());
        bundle.putInt("exposure_compensation_min", this.preview.getMinimumExposure());
        bundle.putInt("exposure_compensation_max", this.preview.getMaximumExposure());
        bundle.putBoolean("supports_iso_range", this.preview.supportsISORange());
        bundle.putInt("iso_range_min", this.preview.getMinimumISO());
        bundle.putInt("iso_range_max", this.preview.getMaximumISO());
        bundle.putBoolean("supports_exposure_time", this.preview.supportsExposureTime());
        bundle.putBoolean("supports_exposure_lock", this.preview.supportsExposureLock());
        bundle.putBoolean("supports_white_balance_lock", this.preview.supportsWhiteBalanceLock());
        bundle.putLong("exposure_time_min", this.preview.getMinimumExposureTime());
        bundle.putLong("exposure_time_max", this.preview.getMaximumExposureTime());
        bundle.putBoolean("supports_white_balance_temperature", this.preview.supportsWhiteBalanceTemperature());
        bundle.putInt("white_balance_temperature_min", this.preview.getMinimumWhiteBalanceTemperature());
        bundle.putInt("white_balance_temperature_max", this.preview.getMaximumWhiteBalanceTemperature());
        bundle.putBoolean("is_multi_cam", this.is_multi_cam);
        bundle.putBoolean("has_physical_cameras", this.preview.hasPhysicalCameras());
        bundle.putBoolean("supports_optical_stabilization", this.preview.supportsOpticalStabilization());
        bundle.putBoolean("optical_stabilization_enabled", this.preview.getOpticalStabilization());
        bundle.putBoolean("supports_video_stabilization", this.preview.supportsVideoStabilization());
        bundle.putBoolean("video_stabilization_enabled", this.preview.getVideoStabilization());
        bundle.putBoolean("can_disable_shutter_sound", this.preview.canDisableShutterSound());
        bundle.putInt("tonemap_max_curve_points", this.preview.getTonemapMaxCurvePoints());
        bundle.putBoolean("supports_tonemap_curve", this.preview.supportsTonemapCurve());
        bundle.putBoolean("supports_photo_video_recording", this.preview.supportsPhotoVideoRecording());
        bundle.putFloat("camera_view_angle_x", preview.getViewAngleX(false));
        bundle.putFloat("camera_view_angle_y", preview.getViewAngleY(false));
        bundle.putFloat("min_zoom_factor", preview.getMinZoomRatio());
        bundle.putFloat("max_zoom_factor", preview.getMaxZoomRatio());

        putBundleExtra(bundle, "color_effects", this.preview.getSupportedColorEffects());
        putBundleExtra(bundle, "scene_modes", this.preview.getSupportedSceneModes());
        putBundleExtra(bundle, "white_balances", this.preview.getSupportedWhiteBalances());
        putBundleExtra(bundle, "isos", this.preview.getSupportedISOs());
        bundle.putInt("magnetic_accuracy", magneticSensor.getMagneticAccuracy());
        bundle.putString("iso_key", this.preview.getISOKey());
        if( this.preview.getCameraController() != null ) {
            bundle.putString("parameters_string", preview.getCameraController().getParametersString());
        }
        List<String> antibanding = this.preview.getSupportedAntiBanding();
        putBundleExtra(bundle, "antibanding", antibanding);
        if( antibanding != null ) {
            String [] entries_arr = new String[antibanding.size()];
            int i=0;
            for(String value: antibanding) {
                entries_arr[i] = getMainUI().getEntryForAntiBanding(value);
                i++;
            }
            bundle.putStringArray("antibanding_entries", entries_arr);
        }
        List<String> edge_modes = this.preview.getSupportedEdgeModes();
        putBundleExtra(bundle, "edge_modes", edge_modes);
        if( edge_modes != null ) {
            String [] entries_arr = new String[edge_modes.size()];
            int i=0;
            for(String value: edge_modes) {
                entries_arr[i] = getMainUI().getEntryForNoiseReductionMode(value);
                i++;
            }
            bundle.putStringArray("edge_modes_entries", entries_arr);
        }
        List<String> noise_reduction_modes = this.preview.getSupportedNoiseReductionModes();
        putBundleExtra(bundle, "noise_reduction_modes", noise_reduction_modes);
        if( noise_reduction_modes != null ) {
            String [] entries_arr = new String[noise_reduction_modes.size()];
            int i=0;
            for(String value: noise_reduction_modes) {
                entries_arr[i] = getMainUI().getEntryForNoiseReductionMode(value);
                i++;
            }
            bundle.putStringArray("noise_reduction_modes_entries", entries_arr);
        }

        List<CameraController.Size> preview_sizes = this.preview.getSupportedPreviewSizes();
        if( preview_sizes != null ) {
            int [] widths = new int[preview_sizes.size()];
            int [] heights = new int[preview_sizes.size()];
            int i=0;
            for(CameraController.Size size: preview_sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                i++;
            }
            bundle.putIntArray("preview_widths", widths);
            bundle.putIntArray("preview_heights", heights);
        }
        bundle.putInt("preview_width", preview.getCurrentPreviewSize().width);
        bundle.putInt("preview_height", preview.getCurrentPreviewSize().height);

        // Note that we set check_burst to false, as the Settings always displays all supported resolutions (along with the "saved"
        // resolution preference, even if that doesn't support burst and we're in a burst mode).
        // This is to be consistent with other preferences, e.g., we still show RAW settings even though that might not be supported
        // for the current photo mode.
        List<CameraController.Size> sizes = this.preview.getSupportedPictureSizes(false);
        if( sizes != null ) {
            int [] widths = new int[sizes.size()];
            int [] heights = new int[sizes.size()];
            boolean [] supports_burst = new boolean[sizes.size()];
            int i=0;
            for(CameraController.Size size: sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                supports_burst[i] = size.supports_burst;
                i++;
            }
            bundle.putIntArray("resolution_widths", widths);
            bundle.putIntArray("resolution_heights", heights);
            bundle.putBooleanArray("resolution_supports_burst", supports_burst);
        }
        if( preview.getCurrentPictureSize() != null ) {
            bundle.putInt("resolution_width", preview.getCurrentPictureSize().width);
            bundle.putInt("resolution_height", preview.getCurrentPictureSize().height);
        }

        //List<String> video_quality = this.preview.getVideoQualityHander().getSupportedVideoQuality();
        String fps_value = applicationInterface.getVideoFPSPref(); // n.b., this takes into account slow motion mode putting us into a high frame rate
        if( MyDebug.LOG )
            Log.d(TAG, "fps_value: " + fps_value);
        List<String> video_quality = this.preview.getSupportedVideoQuality(fps_value);
        if( video_quality == null || video_quality.isEmpty() ) {
            Log.e(TAG, "can't find any supported video sizes for current fps!");
            // fall back to unfiltered list
            video_quality = this.preview.getVideoQualityHander().getSupportedVideoQuality();
        }
        if( video_quality != null && this.preview.getCameraController() != null ) {
            String [] video_quality_arr = new String[video_quality.size()];
            String [] video_quality_string_arr = new String[video_quality.size()];
            int i=0;
            for(String value: video_quality) {
                video_quality_arr[i] = value;
                video_quality_string_arr[i] = this.preview.getCamcorderProfileDescription(value);
                i++;
            }
            bundle.putStringArray("video_quality", video_quality_arr);
            bundle.putStringArray("video_quality_string", video_quality_string_arr);

            boolean is_high_speed = this.preview.fpsIsHighSpeed(fps_value);
            bundle.putBoolean("video_is_high_speed", is_high_speed);
            String video_quality_preference_key = PreferenceKeys.getVideoQualityPreferenceKey(this.preview.getCameraId(), applicationInterface.getCameraIdSPhysicalPref(), is_high_speed);
            if( MyDebug.LOG )
                Log.d(TAG, "video_quality_preference_key: " + video_quality_preference_key);
            bundle.putString("video_quality_preference_key", video_quality_preference_key);
        }

        if( preview.getVideoQualityHander().getCurrentVideoQuality() != null ) {
            bundle.putString("current_video_quality", preview.getVideoQualityHander().getCurrentVideoQuality());
        }
        VideoProfile camcorder_profile = preview.getVideoProfile();
        bundle.putInt("video_frame_width", camcorder_profile.videoFrameWidth);
        bundle.putInt("video_frame_height", camcorder_profile.videoFrameHeight);
        bundle.putInt("video_bit_rate", camcorder_profile.videoBitRate);
        bundle.putInt("video_frame_rate", camcorder_profile.videoFrameRate);
        bundle.putDouble("video_capture_rate", camcorder_profile.videoCaptureRate);
        bundle.putBoolean("video_high_speed", preview.isVideoHighSpeed());
        bundle.putFloat("video_capture_rate_factor", applicationInterface.getVideoCaptureRateFactor());

        List<CameraController.Size> video_sizes = this.preview.getVideoQualityHander().getSupportedVideoSizes();
        if( video_sizes != null ) {
            int [] widths = new int[video_sizes.size()];
            int [] heights = new int[video_sizes.size()];
            int i=0;
            for(CameraController.Size size: video_sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                i++;
            }
            bundle.putIntArray("video_widths", widths);
            bundle.putIntArray("video_heights", heights);
        }

        // set up supported fps values
        if( preview.usingCamera2API() ) {
            // with Camera2, we know what frame rates are supported
            int [] candidate_fps = {15, 24, 25, 30, 60, 96, 100, 120, 240};
            List<Integer> video_fps = new ArrayList<>();
            List<Boolean> video_fps_high_speed = new ArrayList<>();
            for(int fps : candidate_fps) {
                if( preview.fpsIsHighSpeed(String.valueOf(fps)) ) {
                    video_fps.add(fps);
                    video_fps_high_speed.add(true);
                }
                else if( this.preview.getVideoQualityHander().videoSupportsFrameRate(fps) ) {
                    video_fps.add(fps);
                    video_fps_high_speed.add(false);
                }
            }
            int [] video_fps_array = new int[video_fps.size()];
            for(int i=0;i<video_fps.size();i++) {
                video_fps_array[i] = video_fps.get(i);
            }
            bundle.putIntArray("video_fps", video_fps_array);
            boolean [] video_fps_high_speed_array = new boolean[video_fps_high_speed.size()];
            for(int i=0;i<video_fps_high_speed.size();i++) {
                video_fps_high_speed_array[i] = video_fps_high_speed.get(i);
            }
            bundle.putBooleanArray("video_fps_high_speed", video_fps_high_speed_array);
        }
        else {
            // with old API, we don't know what frame rates are supported, so we make it up and let the user try
            // probably shouldn't allow 120fps, but we did in the past, and there may be some devices where this did work?
            int [] video_fps = {15, 24, 25, 30, 60, 96, 100, 120};
            bundle.putIntArray("video_fps", video_fps);
            boolean [] video_fps_high_speed_array = new boolean[video_fps.length];
            for(int i=0;i<video_fps.length;i++) {
                video_fps_high_speed_array[i] = false; // no concept of high speed frame rates in old API
            }
            bundle.putBooleanArray("video_fps_high_speed", video_fps_high_speed_array);
        }

        putBundleExtra(bundle, "flash_values", this.preview.getSupportedFlashValues());
        putBundleExtra(bundle, "focus_values", this.preview.getSupportedFocusValues());

        preferencesListener.startListening();

        showPreview(false);
        setWindowFlagsForSettings(); // important to do after passing camera info into bundle, since this will close the camera
        MyPreferenceFragment fragment = new MyPreferenceFragment();
        fragment.setArguments(bundle);
        // use commitAllowingStateLoss() instead of commit(), does to "java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState" crash seen on Google Play
        // see http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit
        getFragmentManager().beginTransaction().add(android.R.id.content, fragment, "PREFERENCE_FRAGMENT").addToBackStack(null).commitAllowingStateLoss();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "onPreferenceStartFragment");
            Log.d(TAG, "pref: " + pref.getFragment());
        }

        // instantiate the new fragment
        //final Bundle args = pref.getExtras();
        // we want to pass the caller preference fragment's bundle to the new sub-screen (this will be a
        // copy of the bundle originally created in openSettings()
        final Bundle args = new Bundle(caller.getArguments());

        final Fragment fragment = Fragment.instantiate(this, pref.getFragment(), args);
        fragment.setTargetFragment(caller, 0);
        if( MyDebug.LOG )
            Log.d(TAG, "replace fragment");
        /*getFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();*/
        getFragmentManager().beginTransaction().add(android.R.id.content, fragment, "PREFERENCE_FRAGMENT_"+pref.getFragment()).addToBackStack(null).commitAllowingStateLoss();

        /*
        // AndroidX version:
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // replace the existing fragment with the new fragment:
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();
         */
        return true;
    }

    public void updateForSettings(boolean update_camera) {
        updateForSettings(update_camera, null);
    }

    public void updateForSettings(boolean update_camera, String toast_message) {
        updateForSettings(update_camera, toast_message, false, false);
    }

    /** Must be called when an settings (as stored in SharedPreferences) are made, so we can update the
     *  camera, and make any other necessary changes.
     * @param update_camera Whether the camera needs to be updated. Can be set to false if we know changes
     *                      haven't been made to the camera settings, or we already reopened it.
     * @param toast_message If non-null, display this toast instead of the usual camera "startup" toast
     *                      that's shown in showPhotoVideoToast(). If non-null but an empty string, then
     *                      this means no toast is shown at all.
     * @param keep_popup    If false, the popup will be closed and destroyed. Set to true if you're sure
     *                      that the changed setting isn't one that requires the PopupView to be recreated
     * @param allow_dim     If true, for Camera2 API a dimming effect will be applied if updating the
     *                      camera.
     */
    public void updateForSettings(boolean update_camera, String toast_message, boolean keep_popup, boolean allow_dim) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateForSettings()");
            if( toast_message != null ) {
                Log.d(TAG, "toast_message: " + toast_message);
            }
        }
        long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }
        // make sure we're into continuous video mode
        // workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
        // so to be safe, we always reset to continuous video mode, and then reset it afterwards
    	/*String saved_focus_value = preview.updateFocusForVideo(); // n.b., may be null if focus mode not changed
		if( MyDebug.LOG )
			Log.d(TAG, "saved_focus_value: " + saved_focus_value);*/

        if( MyDebug.LOG )
            Log.d(TAG, "update folder history");
        save_location_history.updateFolderHistory(getStorageUtils().getSaveLocation(), true); // this also updates the last icon for ghost image, if that pref has changed
        // no need to update save_location_history_saf, as we always do this in onActivityResult()
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateForSettings: time after update folder history: " + (System.currentTimeMillis() - debug_time));
        }

        imageQueueChanged(); // needed at least for changing photo mode, but might as well call it always

        if( !keep_popup ) {
            mainUI.destroyPopup(); // important as we don't want to use a cached popup
            if( MyDebug.LOG ) {
                Log.d(TAG, "updateForSettings: time after destroy popup: " + (System.currentTimeMillis() - debug_time));
            }
        }

        // update camera for changes made in prefs - do this without closing and reopening the camera app if possible for speed!
        // but need workaround for Nexus 7 bug on old camera API, where scene mode doesn't take effect unless the camera is restarted - I can reproduce this with other 3rd party camera apps, so may be a Nexus 7 issue...
        // doesn't happen if we allow using Camera2 API on Nexus 7, but reopen for consistency (and changing scene modes via
        // popup menu no longer should be calling updateForSettings() for Camera2, anyway)
        boolean need_reopen = false;
        if( update_camera && preview.getCameraController() != null ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String scene_mode = preview.getCameraController().getSceneMode();
            if( MyDebug.LOG )
                Log.d(TAG, "scene mode was: " + scene_mode);
            String key = PreferenceKeys.SceneModePreferenceKey;
            String value = sharedPreferences.getString(key, CameraController.SCENE_MODE_DEFAULT);
            // n.b., on Android 4.3 emulator, scene mode is returned as null (this may be because it doesn't support
            // scene modes at all) - treat this the same as auto
            if( scene_mode == null )
                scene_mode = CameraController.SCENE_MODE_DEFAULT;
            if( !value.equals(scene_mode) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "scene mode changed to: " + value);
                need_reopen = true;
            }
            else {
                if( applicationInterface.useCamera2() ) {
                    // need to reopen if fake flash mode changed, as it changes the available camera features, and we can only set this after opening the camera
                    boolean camera2_fake_flash = preview.getCameraController().getUseCamera2FakeFlash();
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera2_fake_flash was: " + camera2_fake_flash);
                    if( applicationInterface.useCamera2FakeFlash() != camera2_fake_flash ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "camera2_fake_flash changed");
                        need_reopen = true;
                    }
                }
            }

            if( !need_reopen ) {
                CameraController.TonemapProfile old_tonemap_profile = preview.getCameraController().getTonemapProfile();
                if( old_tonemap_profile != CameraController.TonemapProfile.TONEMAPPROFILE_OFF ) {
                    CameraController.TonemapProfile new_tonemap_profile = applicationInterface.getVideoTonemapProfile();
                    if( new_tonemap_profile != CameraController.TonemapProfile.TONEMAPPROFILE_OFF && new_tonemap_profile != old_tonemap_profile ) {
                        // needed for Galaxy S10e when changing from TONEMAP_MODE_CONTRAST_CURVE to TONEMAP_MODE_PRESET_CURVE,
                        // otherwise the contrast curve remains active!
                        if( MyDebug.LOG )
                            Log.d(TAG, "switching between tonemap profiles");
                        need_reopen = true;
                    }
                }
            }

            if( !need_reopen ) {
                boolean old_is_extension = preview.getCameraController().isCameraExtension();
                boolean new_is_extension = applicationInterface.isCameraExtensionPref();
                if( old_is_extension || new_is_extension ) {
                    // At least on Galaxy S10e, we have problems stopping and starting a camera extension session,
                    // e.g., when changing resolutions whilst in an extension mode (XHDR or bokeh) or switching
                    // from XHDR to other modes (including non-extension modes like STD). Problems such as preview
                    // no longer receiving frames, or the call to createExtensionSession() (or createCaptureSession)
                    // hanging. So therefore we should reopen the camera if at least
                    // old_is_extension==true.
                    // This isn't required if old_is_extension==false but new_is_extension==true,
                    // but we still do so since reopening the camera occurs on a background thread
                    // (opening an extension session seems to take longer, so better not to block
                    // the UI thread).
                    if( MyDebug.LOG )
                        Log.d(TAG, "need to reopen camera for changes to extension session");
                    need_reopen = true;
                }
            }
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "need_reopen: " + need_reopen);
            Log.d(TAG, "updateForSettings: time after check need_reopen: " + (System.currentTimeMillis() - debug_time));
        }

        mainUI.layoutUI(); // needed in case we've changed UI placement; or in "top" mode, if we've enabled/disabled on-screen UI icons
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateForSettings: time after layoutUI: " + (System.currentTimeMillis() - debug_time));
        }

        // ensure icons invisible if disabling them from showing from the Settings
        // (if enabling them, we'll make the icon visible later on)
        checkDisableGUIIcons();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String audio_control = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none");
        // better to only display the audio control icon if it matches specific known supported types
        // (important now that "voice" is no longer supported)
        //if( !audio_control.equals("voice") && !audio_control.equals("noise") ) {
        if( !audio_control.equals("noise") ) {
            View speechRecognizerButton = findViewById(R.id.audio_control);
            speechRecognizerButton.setVisibility(View.GONE);
        }

        //speechControl.initSpeechRecognizer(); // in case we've enabled or disabled speech recognizer

        // we no longer call initLocation() here (for having enabled or disabled geotagging), as that's
        // done in setWindowFlagsForCamera() - important not to call it here as well, otherwise if
        // permission wasn't granted, we'll ask for permission twice in a row (on Android 9 or earlier
        // at least)
        //initLocation(); // in case we've enabled or disabled GPS

        initGyroSensors(); // in case we've entered or left panorama
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateForSettings: time after init speech and location: " + (System.currentTimeMillis() - debug_time));
        }
        if( toast_message != null )
            block_startup_toast = true;
        if( !update_camera ) {
            // don't try to update camera
        }
        else if( preview.isPreviewStarting() ) {
            // ideally we should avoid getting into this state - we shouldn't allow changing settings if preview is still opening (e.g., we prevent opening
            // the popup menu if preview is not started, and we close camera when going to settings)
            Log.e(TAG, "updateForSettings: preview is still starting");
        }
        else if( need_reopen || preview.getCameraController() == null ) { // if camera couldn't be opened before, might as well try again
            if( allow_dim )
                applicationInterface.getDrawPreview().setDimPreview(true);
            preview.reopenCamera();
            if( MyDebug.LOG ) {
                Log.d(TAG, "updateForSettings: time after reopen: " + (System.currentTimeMillis() - debug_time));
            }
        }
        else {
            preview.setCameraDisplayOrientation(); // need to call in case the preview rotation option was changed
            if( MyDebug.LOG ) {
                Log.d(TAG, "updateForSettings: time after set display orientation: " + (System.currentTimeMillis() - debug_time));
            }
            if( allow_dim )
                applicationInterface.getDrawPreview().setDimPreview(true);
            preview.pausePreview(true);
            if( MyDebug.LOG ) {
                Log.d(TAG, "updateForSettings: time after pause: " + (System.currentTimeMillis() - debug_time));
            }

            Handler handler = new Handler();
            // We run setupCamera on the UI thread, but we do it on a post-delayed so that the dimming effect (for Camera2 API) has a chance to run.
            // Even if allow_dim==false, still run as a postDelayed (a) for consistency, (b) to allow UI to run for a bit (to avoid risk of slow frames).
            handler.postDelayed(new Runnable() {
                public void run() {
                    // if we ever support calling this with wait_until_started==true, beware of trying to change resolution repeatedly when the popup menu
                    // is open - as currently we'll have problems if trying to calling updateForSettings when preview is already starting on background
                    // thread (see above)
                    preview.setupCamera(false, true, null);
                }
            }, DrawPreview.dim_effect_time_c+16); // +16 to allow time for a frame update to run
        }
        // don't set block_startup_toast to false yet, as camera might be closing/opening on background thread
        if( toast_message != null && !toast_message.isEmpty() )
            preview.showToast(null, toast_message, true);

        // don't need to reset to saved_focus_value, as we'll have done this when setting up the camera (or will do so when the camera is reopened, if need_reopen)
    	/*if( saved_focus_value != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "switch focus back to: " + saved_focus_value);
    		preview.updateFocus(saved_focus_value, true, false);
    	}*/

        magneticSensor.registerMagneticListener(mSensorManager); // check whether we need to register or unregister the magnetic listener
        magneticSensor.checkMagneticAccuracy();

        if( MyDebug.LOG ) {
            Log.d(TAG, "updateForSettings: done: " + (System.currentTimeMillis() - debug_time));
        }
    }

    /** Disables the optional on-screen icons if either user doesn't want to enable them, or not
     *  supported). Note that displaying icons is done via MainUI.showGUI.
     * @return Whether an icon's visibility was changed.
     */
    private boolean checkDisableGUIIcons() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkDisableGUIIcons");
        boolean changed = false;
        if( !supportsExposureButton() ) {
            View button = findViewById(R.id.exposure);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showExposureLockIcon() ) {
            View button = findViewById(R.id.exposure_lock);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showWhiteBalanceLockIcon() ) {
            View button = findViewById(R.id.white_balance_lock);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showCycleRawIcon() ) {
            View button = findViewById(R.id.cycle_raw);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showStoreLocationIcon() ) {
            View button = findViewById(R.id.store_location);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showTextStampIcon() ) {
            View button = findViewById(R.id.text_stamp);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showStampIcon() ) {
            View button = findViewById(R.id.stamp);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showFocusPeakingIcon() ) {
            View button = findViewById(R.id.focus_peaking);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showAutoLevelIcon() ) {
            View button = findViewById(R.id.auto_level);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showCycleFlashIcon() ) {
            View button = findViewById(R.id.cycle_flash);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !mainUI.showFaceDetectionIcon() ) {
            View button = findViewById(R.id.face_detection);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( !showSwitchMultiCamIcon() ) {
            // also handle the multi-cam icon here, as this can change when switching between front/back cameras
            // (e.g., if say a device only has multiple back cameras)
            View button = findViewById(R.id.switch_multi_camera);
            changed = changed || (button.getVisibility() != View.GONE);
            button.setVisibility(View.GONE);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "checkDisableGUIIcons: " + changed);
        return changed;
    }

    public MyPreferenceFragment getPreferenceFragment() {
        return (MyPreferenceFragment)getFragmentManager().findFragmentByTag("PREFERENCE_FRAGMENT");
    }

    private boolean settingsIsOpen() {
        return getPreferenceFragment() != null;
    }

    /** Call when the settings is going to be closed.
     */
    void settingsClosing() {
        if( MyDebug.LOG )
            Log.d(TAG, "close settings");
        setWindowFlagsForCamera();
        showPreview(true);

        preferencesListener.stopListening();

        // Update the cached settings in DrawPreview
        // Note that some GUI related settings won't trigger preferencesListener.anyChange(), so
        // we always call this. Perhaps we could add more classifications to PreferencesListener
        // to mark settings that need us to update DrawPreview but not call updateForSettings().
        // However, DrawPreview.updateSettings() should be a quick function (the main point is
        // to avoid reading the preferences in every single frame).
        applicationInterface.getDrawPreview().updateSettings();

        // Set the flag to cover the preview until the camera is open and receiving frames again
        // (for Camera2 API) - avoids showing a flash of the preview from before the user went to
        // the settings.
        applicationInterface.getDrawPreview().setCoverPreview(true);

        if( preferencesListener.anyChange() ) {
            mainUI.updateOnScreenIcons();
        }

        if( preferencesListener.anySignificantChange() ) {
            // don't need to update camera, as we now pause/resume camera when going to settings
            updateForSettings(false);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "no need to call updateForSettings() for changes made to preferences");
            if( preferencesListener.anyChange() ) {
                // however we should still destroy cached popup, in case UI settings need to be kept in
                // sync (e.g., changing the Repeat Mode)
                mainUI.destroyPopup();
            }
        }
    }

    private PopupOnBackPressedCallback popupOnBackPressedCallback;

    private class PopupOnBackPressedCallback extends OnBackPressedCallback {
        public PopupOnBackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if( MyDebug.LOG )
                Log.d(TAG, "PopupOnBackPressedCallback.handleOnBackPressed");
            if( popupIsOpen() ) {
                // close popup will disable the PopupOnBackPressedCallback, so no need to do it here
                closePopup();
            }
            else {
                // shouldn't be here (if popup isn't open, this callback shouldn't be enabled), but just in case
                if( MyDebug.LOG )
                    Log.e(TAG, "PopupOnBackPressedCallback was enabled but popup menu not open?!");
                this.setEnabled(false);
                MainActivity.this.onBackPressed();
            }
        }
    }

    public void enablePopupOnBackPressedCallback(boolean enabled) {
        if( MyDebug.LOG )
            Log.d(TAG, "enablePopupOnBackPressedCallback: " + enabled);
        if( popupOnBackPressedCallback != null ) {
            popupOnBackPressedCallback.setEnabled(enabled);
        }
    }

    private PausePreviewOnBackPressedCallback pausePreviewOnBackPressedCallback;

    private class PausePreviewOnBackPressedCallback extends OnBackPressedCallback {
        public PausePreviewOnBackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if( MyDebug.LOG )
                Log.d(TAG, "PausePreviewOnBackPressedCallback.handleOnBackPressed");

            if( preview != null && preview.isPreviewPaused() ) {
                // starting the preview will disable the PausePreviewOnBackPressedCallback, so no need to do it here
                if( MyDebug.LOG )
                    Log.d(TAG, "preview was paused, so unpause it");
                preview.startCameraPreview(true, null);
            }
            else {
                // shouldn't be here (if preview isn't paused, this callback shouldn't be enabled), but just in case
                if( MyDebug.LOG )
                    Log.e(TAG, "PausePreviewOnBackPressedCallback was enabled but preview not paused?!");
                this.setEnabled(false);
                MainActivity.this.onBackPressed();
            }
        }
    }

    public void enablePausePreviewOnBackPressedCallback(boolean enabled) {
        if( MyDebug.LOG )
            Log.d(TAG, "enablePausePreviewOnBackPressedCallback: " + enabled);
        if( pausePreviewOnBackPressedCallback != null ) {
            pausePreviewOnBackPressedCallback.setEnabled(enabled);
        }
    }

    private ScreenLockOnBackPressedCallback screenLockOnBackPressedCallback;

    private class ScreenLockOnBackPressedCallback extends OnBackPressedCallback {
        public ScreenLockOnBackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if( MyDebug.LOG )
                Log.d(TAG, "ScreenLockOnBackPressedCallback.handleOnBackPressed");

            if( screen_is_locked ) {
                preview.showToast(screen_locked_toast, R.string.screen_is_locked);
            }
            else {
                // shouldn't be here (if screen isn't locked, this callback shouldn't be enabled), but just in case
                if( MyDebug.LOG )
                    Log.e(TAG, "ScreenLockOnBackPressedCallback was enabled but screen isn't locked?!");
                this.setEnabled(false);
                MainActivity.this.onBackPressed();
            }
        }
    }

    private void enableScreenLockOnBackPressedCallback(boolean enabled) {
        if( MyDebug.LOG )
            Log.d(TAG, "enableScreenLockOnBackPressedCallback: " + enabled);
        if( screenLockOnBackPressedCallback != null ) {
            screenLockOnBackPressedCallback.setEnabled(enabled);
        }
    }

    // should no longer use onBackPressed() - instead use OnBackPressedCallback, for upcoming changes in Android 14+ (predictive back gestures)
    /*@Override
    public void onBackPressed() {
        if( MyDebug.LOG )
            Log.d(TAG, "onBackPressed");
        if( screen_is_locked ) {
            preview.showToast(screen_locked_toast, R.string.screen_is_locked);
            return;
        }

        if( settingsIsOpen() ) {
            settingsClosing();
        }
        else if( preview != null && preview.isPreviewPaused() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "preview was paused, so unpause it");
            preview.startCameraPreview();
            return;
        }
        else {
            if( popupIsOpen() ) {
                closePopup();
                return;
            }
        }
        super.onBackPressed();
    }*/

    /** Returns whether we are always running in edge-to-edge mode. (If false, we may still sometimes
     *  run edge-to-edge.)
     */
    public boolean getEdgeToEdgeMode() {
        return this.edge_to_edge_mode;
    }

    /** Whether to allow the application to show under the navigation bar, or not.
     *  Arguably we could enable this all the time, but in practice we only enable for cases when
     *  want_no_limits==true and navigation_gap!=0 (if want_no_limits==false, there's no need to
     *  show under the navigation bar; if navigation_gap==0, there is no navigation bar).
     */
    private void showUnderNavigation(boolean enable) {
        if( MyDebug.LOG )
            Log.d(TAG, "showUnderNavigation: " + enable);

        if( edge_to_edge_mode ) {
            // we are already always in edge-to-edge mode
            return;
        }

        {
            // We used to use window flag FLAG_LAYOUT_NO_LIMITS, but this didn't work properly on
            // Android 11 (didn't take effect until orientation changed or application paused/resumed).
            // Although system ui visibility flags are deprecated on Android 11, this still works better
            // than the FLAG_LAYOUT_NO_LIMITS flag (which was not well documented anyway).
            // Update, now using WindowCompat.setDecorFitsSystemWindows. This is non-deprecated, and
            // documented at https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually .
            /*int flags = getWindow().getDecorView().getSystemUiVisibility();
            if( enable ) {
                getWindow().getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            else {
                getWindow().getDecorView().setSystemUiVisibility(flags & ~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }*/
            test_set_show_under_navigation = enable;
            // in theory the VANILLA_ICE_CREAM is redundant as we shouldn't be here on Android 15+ anyway (since edge_to_edge_mode==true), but
            // wrapping in case this helps Google Play recommendation to avoid deprecated APIs for edge-to-edge
            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ) {
                WindowCompat.setDecorFitsSystemWindows(getWindow(), !enable);
            }
        }

        // in theory the VANILLA_ICE_CREAM is redundant as we shouldn't be here on Android 15+ anyway (since edge_to_edge_mode==true), but
        // wrapping in case this helps Google Play recommendation to avoid deprecated APIs for edge-to-edge
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ) {
            getWindow().setNavigationBarColor(enable ? Color.TRANSPARENT : Color.BLACK);
        }
    }

    public int getNavigationGap() {
        return (want_no_limits || edge_to_edge_mode) ? navigation_gap : 0;
    }

    public int getNavigationGapLandscape() {
        return edge_to_edge_mode ? navigation_gap_landscape : 0;
    }

    public int getNavigationGapReverseLandscape() {
        return edge_to_edge_mode ? navigation_gap_reverse_landscape : 0;
    }

    /** The system is now such that we have entered or exited immersive mode. If visible is true,
     *  system UI is now visible such that we should exit immersive mode. If visible is false, the
     *  system has entered immersive mode.
     */
    private void immersiveModeChanged(boolean visible) {
        if( MyDebug.LOG )
            Log.d(TAG, "immersiveModeChanged: " + visible);
        if( !usingKitKatImmersiveMode() )
            return;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String immersive_mode = sharedPreferences.getString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_off");
        boolean hide_ui = immersive_mode.equals("immersive_mode_gui") || immersive_mode.equals("immersive_mode_everything");

        if( visible ) {
            if( MyDebug.LOG )
                Log.d(TAG, "system bars now visible");
            // change UI due to having exited immersive mode
            if( hide_ui )
                mainUI.setImmersiveMode(false);
            setImmersiveTimer();
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "system bars now NOT visible");
            // change UI due to having entered immersive mode
            if( hide_ui )
                mainUI.setImmersiveMode(true);
        }
    }

    /** Set up listener to handle listening for system ui changes (for immersive mode), and setting
      * a WindowsInsetsListener to find the navigation_gap.
      */
    private void setupSystemUiVisibilityListener() {
        View decorView = getWindow().getDecorView();

        {
            // set a window insets listener to find the navigation_gap
            if( MyDebug.LOG )
                Log.d(TAG, "set a window insets listener");
            this.set_window_insets_listener = true;
            decorView.getRootView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                private boolean has_last_system_orientation;
                private SystemOrientation last_system_orientation;
                @Override
                public @NonNull WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets windowInsets) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "onApplyWindowInsets");
                    int inset_left;
                    int inset_top;
                    int inset_right;
                    int inset_bottom;
                    if( edge_to_edge_mode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {
                        // take opportunity to use non-deprecated versions; also for edge_to_edge_mode==true, we need to use getInsetsIgnoringVisibility for
                        // immersive mode (since for edge_to_edge_mode==true, we are not using setSystemUiVisibility() / SYSTEM_UI_FLAG_LAYOUT_STABLE in setImmersiveMode())
                        // also compare with MyApplicationInterface.getDisplaySize() - in particular we don't care about caption/system bar that is returned on e.g.
                        // OnePlus Pad for insets.top when in landscape orientation (since the system bar isn't shown); however we also need to subtract any from the cutout -
                        // since this code is for finding what margins we need to set to avoid navigation bars; avoiding the cutout is done below for the entire
                        // Open Camera view
                        Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                        Insets cutout_insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
                        if( test_force_window_insets ) {
                            insets = test_insets;
                            cutout_insets = test_cutout_insets;
                        }
                        inset_left = insets.left - cutout_insets.left;
                        inset_top = insets.top - cutout_insets.top;
                        inset_right = insets.right - cutout_insets.right;
                        inset_bottom = insets.bottom - cutout_insets.bottom;
                    }
                    else {
                        inset_left = windowInsets.getSystemWindowInsetLeft();
                        inset_top = windowInsets.getSystemWindowInsetTop();
                        inset_right = windowInsets.getSystemWindowInsetRight();
                        inset_bottom = windowInsets.getSystemWindowInsetBottom();
                    }
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "inset left: " + inset_left);
                        Log.d(TAG, "inset top: " + inset_top);
                        Log.d(TAG, "inset right: " + inset_right);
                        Log.d(TAG, "inset bottom: " + inset_bottom);
                    }

                    if( edge_to_edge_mode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {
                        // easier to ensure the entire activity avoids display cutouts - for the preview, we still support
                        // it showing under the navigation bar
                        Insets insets = windowInsets.getInsets(WindowInsets.Type.displayCutout());
                        if( test_force_window_insets ) {
                            insets = test_cutout_insets;
                        }
                        v.setPadding(insets.left, insets.top, insets.right, insets.bottom);

                        // also handle change of immersive mode (instead of using deprecated setOnSystemUiVisibilityChangeListener below
                        immersiveModeChanged( windowInsets.isVisible(WindowInsets.Type.navigationBars()) );
                    }

                    resetCachedSystemOrientation(); // don't want to get cached result - this can sometimes happen e.g. on Pixel 6 Pro when switching between landscape and reverse landscape
                    SystemOrientation system_orientation = getSystemOrientation();
                    int new_navigation_gap, new_navigation_gap_landscape, new_navigation_gap_reverse_landscape;
                    switch ( system_orientation ) {
                        case PORTRAIT:
                            if( MyDebug.LOG )
                                Log.d(TAG, "portrait");
                            new_navigation_gap = inset_bottom;
                            new_navigation_gap_landscape = inset_left;
                            new_navigation_gap_reverse_landscape = inset_right;
                            break;
                        case LANDSCAPE:
                            if( MyDebug.LOG )
                                Log.d(TAG, "landscape");
                            new_navigation_gap = inset_right;
                            new_navigation_gap_landscape = inset_bottom;
                            new_navigation_gap_reverse_landscape = inset_top;
                            break;
                        case REVERSE_LANDSCAPE:
                            if( MyDebug.LOG )
                                Log.d(TAG, "reverse landscape");
                            new_navigation_gap = inset_left;
                            new_navigation_gap_landscape = inset_top;
                            new_navigation_gap_reverse_landscape = inset_bottom;
                            break;
                        default:
                            Log.e(TAG, "unknown system_orientation?!: " + system_orientation);
                            new_navigation_gap = 0;
                            new_navigation_gap_landscape = 0;
                            new_navigation_gap_reverse_landscape = 0;
                            break;
                    }
                    if( !edge_to_edge_mode ) {
                        // we only care about avoiding a landscape navigation bar (e.g., large tablets in landscape orientation) for edge_to_edge_mode==true
                        // in theory this could be useful when edge_to_edge_mode==false, but in practice we will never enter edge-to-edge-mode if the
                        // navigation bar is along the landscape-edge, so restrict behaviour change to edge_to_edge_mode==true
                        new_navigation_gap_landscape = 0;
                        new_navigation_gap_reverse_landscape = 0;
                    }

                    // for edge_to_edge_mode==false, we only enter this case if system orientation changes, due to issues where this callback may be called first with 0 navigation gap
                    // (see notes below)
                    // for edge_to_edge_mode==true, simpler to always react to updated insets - in particular, in split-window mode, the navigation gaps can
                    // change when device rotates, even though the application remains in the same orientation
                    if( (edge_to_edge_mode || (has_last_system_orientation && system_orientation != last_system_orientation)) && (new_navigation_gap != navigation_gap || new_navigation_gap_landscape != navigation_gap_landscape || new_navigation_gap_reverse_landscape != navigation_gap_reverse_landscape ) ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "navigation_gap changed from " + navigation_gap + " to " + new_navigation_gap);

                        navigation_gap = new_navigation_gap;
                        navigation_gap_landscape = new_navigation_gap_landscape;
                        navigation_gap_reverse_landscape = new_navigation_gap_reverse_landscape;

                        if( MyDebug.LOG )
                            Log.d(TAG, "want_no_limits: " + want_no_limits);
                        if( want_no_limits || edge_to_edge_mode ) {
                            // If we want no_limits mode, then need to take care in case of device orientation
                            // in cases where that changes the navigation_gap:
                            // - Need to set showUnderNavigation() (in case navigation_gap when from zero to non-zero or vice versa).
                            // - Need to call layoutUI() (for different value of navigation_gap)

                            // Need to call showUnderNavigation() from handler for it to take effect.
                            // Similarly we have problems if we call layoutUI without post-ing it -
                            // sometimes when rotating a device, we get a call to OnApplyWindowInsetsListener
                            // with 0 navigation_gap followed by the call with the correct non-zero values -
                            // posting the call to layoutUI means it runs after the second call, so we have the
                            // correct navigation_gap.
                            Handler handler = new Handler();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "runnable for change in navigation_gap due to orientation change");
                                    if( navigation_gap != 0 ) {
                                        if( MyDebug.LOG )
                                            Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS");
                                        showUnderNavigation(true);
                                    }
                                    else {
                                        if( MyDebug.LOG )
                                            Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS");
                                        showUnderNavigation(false);
                                    }
                                    // needed for OnePlus Pad when rotating, to avoid delay in updating last_take_photo_top_time (affects placement of on-screen text e.g. zoom)
                                    // need to do this from handler for this to take effect (otherwise last_take_photo_top_time won't update to new value)
                                    applicationInterface.getDrawPreview().onNavigationGapChanged();

                                    if( MyDebug.LOG )
                                        Log.d(TAG, "layout UI due to changing navigation_gap");
                                    mainUI.layoutUI();
                                }
                            });
                        }
                    }
                    else if( !edge_to_edge_mode && navigation_gap == 0 ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "navigation_gap changed from zero to " + new_navigation_gap);
                        navigation_gap = new_navigation_gap;
                        // Sometimes when this callback is called, the navigation_gap may still be 0 even if
                        // the device doesn't have physical navigation buttons - we need to wait
                        // until we have found a non-zero value before switching to no limits.
                        // On devices with physical navigation bar, navigation_gap should remain 0
                        // (and there's no point setting FLAG_LAYOUT_NO_LIMITS)
                        if( want_no_limits && navigation_gap != 0 ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS");
                            showUnderNavigation(true);
                        }
                    }

                    if( has_last_system_orientation && (
                            ( system_orientation == SystemOrientation.LANDSCAPE && last_system_orientation == SystemOrientation.REVERSE_LANDSCAPE ) ||
                            ( system_orientation == SystemOrientation.REVERSE_LANDSCAPE && last_system_orientation == SystemOrientation.LANDSCAPE )
                    ) ) {
                        // hack - this should be done via MyDisplayListener.onDisplayChanged(), but that doesn't work on Galaxy S24+ (either MyDisplayListener.onDisplayChanged()
                        // isn't called, or getDefaultDisplay().getRotation() is still returning the old rotation)
                        if( MyDebug.LOG )
                            Log.d(TAG, "onApplyWindowInsets: switched between landscape and reverse orientation");
                        onSystemOrientationChanged();
                    }

                    has_last_system_orientation = true;
                    last_system_orientation = system_orientation;

                    // see comments in MainUI.layoutUI() for why we don't use this
                    /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getSystemOrientation() == SystemOrientation.LANDSCAPE ) {
                        Rect privacy_indicator_rect = windowInsets.getPrivacyIndicatorBounds();
                        if( privacy_indicator_rect != null ) {
                            Rect window_bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "privacy_indicator_rect: " + privacy_indicator_rect);
                                Log.d(TAG, "window_bounds: " + window_bounds);
                            }
                            privacy_indicator_gap = window_bounds.right - privacy_indicator_rect.left;
                            if( privacy_indicator_gap < 0 )
                                privacy_indicator_gap = 0; // just in case??
                            if( MyDebug.LOG )
                                Log.d(TAG, "privacy_indicator_gap: " + privacy_indicator_gap);
                        }
                    }
                    else {
                        privacy_indicator_gap = 0;
                    }*/
                    return getWindow().getDecorView().getRootView().onApplyWindowInsets(windowInsets);
                }
            });
        }

        if( edge_to_edge_mode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {
            // already handled by the setOnApplyWindowInsetsListener above
        }
        else {
            decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        // Note that system bars will only be "visible" if none of the
                        // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.

                        if( MyDebug.LOG )
                            Log.d(TAG, "onSystemUiVisibilityChange: " + visibility);

                        // Note that Android example code says to test against SYSTEM_UI_FLAG_FULLSCREEN,
                        // but this stopped working on Android 11, as when calling setSystemUiVisibility(0)
                        // to exit immersive mode, when we arrive here the flag SYSTEM_UI_FLAG_FULLSCREEN
                        // is still set. Fixed by checking for SYSTEM_UI_FLAG_HIDE_NAVIGATION instead -
                        // which makes some sense since we run in fullscreen mode all the time anyway.
                        //if( (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ) {
                        if( (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0 ) {
                            immersiveModeChanged(true);
                        }
                        else {
                            immersiveModeChanged(false);
                        }
                    }
                });
        }
    }

    public boolean usingKitKatImmersiveMode() {
        // whether we are using a Kit Kat style immersive mode (either hiding navigation bar, GUI, or everything)
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String immersive_mode = sharedPreferences.getString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_off");
        if( immersive_mode.equals("immersive_mode_navigation") || immersive_mode.equals("immersive_mode_gui") || immersive_mode.equals("immersive_mode_everything") )
            return true;
        return false;
    }

    public boolean usingKitKatImmersiveModeEverything() {
        // whether we are using a Kit Kat style immersive mode for everything
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String immersive_mode = sharedPreferences.getString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_off");
        if( immersive_mode.equals("immersive_mode_everything") )
            return true;
        return false;
    }


    private Handler immersive_timer_handler = null;
    private Runnable immersive_timer_runnable = null;

    private void cancelImmersiveTimer() {
        if( immersive_timer_handler != null && immersive_timer_runnable != null ) {
            immersive_timer_handler.removeCallbacks(immersive_timer_runnable);
            immersive_timer_handler = null;
            immersive_timer_runnable = null;
        }
    }

    private void setImmersiveTimer() {
        cancelImmersiveTimer();
        if( app_is_paused ) {
            // don't want to enter immersive mode from background
            // problem that even after onPause, we can end up here via various callbacks
            return;
        }
        immersive_timer_handler = new Handler();
        immersive_timer_handler.postDelayed(immersive_timer_runnable = new Runnable(){
            @Override
            public void run(){
                if( MyDebug.LOG )
                    Log.d(TAG, "setImmersiveTimer: run");
                // even though timer should have been cancelled when in background, check app_is_paused just in case
                if( !app_is_paused && !camera_in_background && !popupIsOpen() && usingKitKatImmersiveMode() )
                    setImmersiveMode(true);
            }
        }, 5000);
    }

    public void initImmersiveMode() {
        if( !usingKitKatImmersiveMode() ) {
            setImmersiveMode(true);
        }
        else {
            // don't start in immersive mode, only after a timer
            setImmersiveTimer();
        }
    }

    void setImmersiveMode(boolean on) {
        if( MyDebug.LOG )
            Log.d(TAG, "setImmersiveMode: " + on);
        // n.b., preview.setImmersiveMode() is called from onSystemUiVisibilityChange()

        // don't allow the kitkat-style immersive mode for panorama mode (problem that in "full" immersive mode, the gyro spot can't be seen - we could fix this, but simplest to just disallow)
        boolean enable_immersive = on && usingKitKatImmersiveMode() && applicationInterface.getPhotoMode() != MyApplicationInterface.PhotoMode.Panorama;
        if( MyDebug.LOG )
            Log.d(TAG, "enable_immersive?: " + enable_immersive);

        if( edge_to_edge_mode ) {
            // take opportunity to avoid deprecated setSystemUiVisibility
            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            int type = WindowInsetsCompat.Type.navigationBars(); // only show/hide navigation bars, as we run with system bars always hidden
            if( enable_immersive ) {
                windowInsetsController.hide(type);
            }
            else {
                windowInsetsController.show(type);
            }
        }
        else {
            // save whether we set SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION - since this flag might be enabled for showUnderNavigation(true), at least indirectly by setDecorFitsSystemWindows() on old versions of Android
            int saved_flags = getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if( MyDebug.LOG )
                Log.d(TAG, "saved_flags?: " + saved_flags);
            if( enable_immersive ) {
                getWindow().getDecorView().setSystemUiVisibility(saved_flags | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
            else {
                getWindow().getDecorView().setSystemUiVisibility(saved_flags);
            }
        }
    }

    /** Sets the brightness level for normal operation (when camera preview is visible).
     *  If force_max is true, this always forces maximum brightness; otherwise this depends on user preference.
     */
    public void setBrightnessForCamera(boolean force_max) {
        if( MyDebug.LOG )
            Log.d(TAG, "setBrightnessForCamera");
        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
        // done here rather than onCreate, so that changing it in preferences takes effect without restarting app
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final WindowManager.LayoutParams layout = getWindow().getAttributes();
        if( force_max || sharedPreferences.getBoolean(PreferenceKeys.MaxBrightnessPreferenceKey, false) ) {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        }
        else {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }

        // this must be called from the ui thread
        // sometimes this method may be called not on UI thread, e.g., Preview.takePhotoWhenFocused->CameraController2.takePicture
        // ->CameraController2.runFakePrecapture->Preview/onFrontScreenTurnOn->MyApplicationInterface.turnFrontScreenFlashOn
        // -> this.setBrightnessForCamera
        this.runOnUiThread(new Runnable() {
            public void run() {
                getWindow().setAttributes(layout);
            }
        });
    }

    /**
     * Set the brightness to minimal in case the preference key is set to do it
     */
    public void setBrightnessToMinimumIfWanted() {
        if( MyDebug.LOG )
            Log.d(TAG, "setBrightnessToMinimum");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final WindowManager.LayoutParams layout = getWindow().getAttributes();
        if( sharedPreferences.getBoolean(PreferenceKeys.DimWhenDisconnectedPreferenceKey, false) ) {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        }
        else {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }

        this.runOnUiThread(new Runnable() {
            public void run() {
                getWindow().setAttributes(layout);
            }
        });

    }

    /** Sets the window flags for normal operation (when camera preview is visible).
     */
    public void setWindowFlagsForCamera() {
        if( MyDebug.LOG )
            Log.d(TAG, "setWindowFlagsForCamera");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ) {
            // we set this to prevent what's on the preview being used to show under the "recent apps" view - potentially useful
            // for privacy reasons
            setRecentsScreenshotEnabled(false);
        }

        if( lock_to_landscape ) {
            // force to landscape mode
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE); // testing for devices with unusual sensor orientation (e.g., Nexus 5X)
        }
        else {
            // allow orientation to change for camera, even if user has locked orientation
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
        if( preview != null ) {
            // also need to call preview.setCameraDisplayOrientation, as this handles if the user switched from portrait to reverse landscape whilst in settings/etc
            // as switching from reverse landscape back to landscape isn't detected in onConfigurationChanged
            // update: now probably irrelevant now that we close/reopen the camera, but keep it here anyway
            preview.setCameraDisplayOrientation();
        }
        if( preview != null && mainUI != null ) {
            // layoutUI() is needed because even though we call layoutUI from MainUI.onOrientationChanged(), certain things
            // (ui_rotation) depend on the system orientation too.
            // Without this, going to Settings, then changing orientation, then exiting settings, would show the icons with the
            // wrong orientation.
            // We put this here instead of onConfigurationChanged() as onConfigurationChanged() isn't called when switching from
            // reverse landscape to landscape orientation: so it's needed to fix if the user starts in portrait, goes to settings
            // or a dialog, then switches to reverse landscape, then exits settings/dialog - the system orientation will switch
            // to landscape (which Open Camera is forced to).
            mainUI.layoutUI();
        }


        // keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
        if( sharedPreferences.getBoolean(PreferenceKeys.KeepDisplayOnPreferenceKey, true) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "do keep screen on");
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "don't keep screen on");
            this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if( sharedPreferences.getBoolean(PreferenceKeys.ShowWhenLockedPreferenceKey, false) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "do show when locked");
            // keep Open Camera on top of screen-lock (will still need to unlock when going to gallery or settings)
            showWhenLocked(true);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "don't show when locked");
            showWhenLocked(false);
        }

        if( want_no_limits && navigation_gap != 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS");
            showUnderNavigation(true);
        }

        setBrightnessForCamera(false);

        initImmersiveMode();
        camera_in_background = false;

        magneticSensor.clearDialog(); // if the magnetic accuracy was opened, it must have been closed now
        if( !app_is_paused ) {
            // Needs to be called after camera_in_background is set to false.
            // Note that the app_is_paused guard is in some sense unnecessary, as initLocation tests for that too,
            // but useful for error tracking - ideally we want to make sure that initLocation is never called when
            // app is paused. It can happen here because setWindowFlagsForCamera() is called from
            // onCreate()
            initLocation();

            // Similarly only want to reopen the camera if no longer paused
            if( preview != null ) {
                preview.onResume();
            }
        }
    }

    private void setWindowFlagsForSettings() {
        setWindowFlagsForSettings(true);
    }

    /** Sets the window flags for when the settings window is open.
     * @param set_lock_protect If true, then window flags will be set to protect by screen lock, no
     *                         matter what the preference setting
     *                         PreferenceKeys.getShowWhenLockedPreferenceKey() is set to. This
     *                         should be true for the Settings window, and anything else that might
     *                         need protecting. But some callers use this method for opening other
     *                         things (such as info dialogs).
     */
    public void setWindowFlagsForSettings(boolean set_lock_protect) {
        if( MyDebug.LOG )
            Log.d(TAG, "setWindowFlagsForSettings: " + set_lock_protect);

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ) {
            // in settings mode, okay to revert to default behaviour for using a screenshot for "recent apps" view
            setRecentsScreenshotEnabled(true);
        }

        // allow screen rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        // revert to standard screen blank behaviour
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if( want_no_limits && navigation_gap != 0 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS");
            showUnderNavigation(false);
        }
        if( set_lock_protect ) {
            // settings should still be protected by screen lock
            showWhenLocked(false);
        }

        {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(layout);
        }

        setImmersiveMode(false);
        camera_in_background = true;

        // we disable location listening when showing settings or a dialog etc - saves battery life, also better for privacy
        applicationInterface.getLocationSupplier().freeLocationListeners();

        // similarly we close the camera
        preview.onPause(false);

        push_switched_camera = false; // just in case
    }

    private void showWhenLocked(boolean show) {
        if( MyDebug.LOG )
            Log.d(TAG, "showWhenLocked: " + show);
        // although FLAG_SHOW_WHEN_LOCKED is deprecated, setShowWhenLocked(false) does not work
        // correctly: if we turn screen off and on when camera is open (so we're now running above
        // the lock screen), going to settings does not show the lock screen, i.e.,
        // setShowWhenLocked(false) does not take effect!
		/*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			if( MyDebug.LOG )
				Log.d(TAG, "use setShowWhenLocked");
			setShowWhenLocked(show);
		}
		else*/ {
            if( show ) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
            else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
        }
    }

    /** Use this is place of simply alert.show(), if the orientation has just been set to allow
     *  rotation via setWindowFlagsForSettings(). On some devices (e.g., OnePlus 3T with Android 8),
     *  the dialog doesn't show properly if the phone is held in portrait. A workaround seems to be
     *  to use postDelayed. Note that postOnAnimation() doesn't work.
     */
    public void showAlert(final AlertDialog alert) {
        if( MyDebug.LOG )
            Log.d(TAG, "showAlert");
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                alert.show();
            }
        }, 20);
        // note that 1ms usually fixes the problem, but not always; 10ms seems fine, have set 20ms
        // just in case
    }

    public void showPreview(boolean show) {
        if( MyDebug.LOG )
            Log.d(TAG, "showPreview: " + show);
        final ViewGroup container = findViewById(R.id.hide_container);
        container.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /** Rotates the supplied bitmap according to the orientation tag stored in the exif data. If no
     *  rotation is required, the input bitmap is returned. If rotation is required, the input
     *  bitmap is recycled.
     * @param uri Uri containing the JPEG with Exif information to use.
     */
    public Bitmap rotateForExif(Bitmap bitmap, Uri uri) throws IOException {
        ExifInterface exif;
        InputStream inputStream = null;
        try {
            inputStream = this.getContentResolver().openInputStream(uri);
            exif = new ExifInterface(inputStream);
        }
        finally {
            if( inputStream != null )
                inputStream.close();
        }

        if( exif != null ) {
            int exif_orientation_s = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            boolean needs_tf = false;
            int exif_orientation = 0;
            // see http://jpegclub.org/exif_orientation.html
            // and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
            if( exif_orientation_s == ExifInterface.ORIENTATION_UNDEFINED || exif_orientation_s == ExifInterface.ORIENTATION_NORMAL ) {
                // leave unchanged
            }
            else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_180 ) {
                needs_tf = true;
                exif_orientation = 180;
            }
            else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_90 ) {
                needs_tf = true;
                exif_orientation = 90;
            }
            else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_270 ) {
                needs_tf = true;
                exif_orientation = 270;
            }
            else {
                // just leave unchanged for now
                if( MyDebug.LOG )
                    Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "    exif orientation: " + exif_orientation);

            if( needs_tf ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "    need to rotate bitmap due to exif orientation tag");
                Matrix m = new Matrix();
                m.setRotate(exif_orientation, bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f);
                Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), m, true);
                if( rotated_bitmap != bitmap ) {
                    bitmap.recycle();
                    bitmap = rotated_bitmap;
                }
            }
        }
        return bitmap;
    }

    /** Loads a thumbnail from the supplied image uri (not videos). Note this loads from the bitmap
     *  rather than reading from MediaStore. Therefore this works with SAF uris as well as
     *  MediaStore uris, as well as allowing control over the resolution of the thumbnail.
     *  If sample_factor is 1, this returns a bitmap scaled to match the display resolution. If
     *  sample_factor is greater than 1, it will be scaled down to a lower resolution.
     *  We now use this for photos in preference to APIs like
     *  MediaStore.Images.Thumbnails.getThumbnail(). Advantages are simplifying the code, reducing
     *  number of different codepaths, but also seems to help against device specific bugs
     *  in getThumbnail() e.g. Pixel 6 Pro with x-night in portrait.
     */
    private Bitmap loadThumbnailFromUri(Uri uri, int sample_factor) {
        Bitmap thumbnail = null;
        try {
            //thumbnail = MediaStore.Images.Media.getBitmap(getContentResolver(), media.uri);
            // only need to load a bitmap as large as the screen size
            BitmapFactory.Options options = new BitmapFactory.Options();
            InputStream is = getContentResolver().openInputStream(uri);
            // get dimensions
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            int bitmap_width = options.outWidth;
            int bitmap_height = options.outHeight;
            Point display_size = new Point();
            applicationInterface.getDisplaySize(display_size, true);
            if( MyDebug.LOG ) {
                Log.d(TAG, "bitmap_width: " + bitmap_width);
                Log.d(TAG, "bitmap_height: " + bitmap_height);
                Log.d(TAG, "display width: " + display_size.x);
                Log.d(TAG, "display height: " + display_size.y);
            }
            // align dimensions
            if( display_size.x < display_size.y ) {
                //noinspection SuspiciousNameCombination
                display_size.set(display_size.y, display_size.x);
            }
            if( bitmap_width < bitmap_height ) {
                int dummy = bitmap_width;
                //noinspection SuspiciousNameCombination
                bitmap_width = bitmap_height;
                bitmap_height = dummy;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "bitmap_width: " + bitmap_width);
                Log.d(TAG, "bitmap_height: " + bitmap_height);
                Log.d(TAG, "display width: " + display_size.x);
                Log.d(TAG, "display height: " + display_size.y);
            }
            // only care about height, to save worrying about different aspect ratios
            options.inSampleSize = 1;
            while( bitmap_height / (2*options.inSampleSize) >= display_size.y ) {
                options.inSampleSize *= 2;
            }
            options.inSampleSize *= sample_factor;
            if( MyDebug.LOG ) {
                Log.d(TAG, "inSampleSize: " + options.inSampleSize);
            }
            options.inJustDecodeBounds = false;
            // need a new inputstream, see https://stackoverflow.com/questions/2503628/bitmapfactory-decodestream-returning-null-when-options-are-set
            is.close();
            is = getContentResolver().openInputStream(uri);
            thumbnail = BitmapFactory.decodeStream(is, null, options);
            if( thumbnail == null ) {
                Log.e(TAG, "decodeStream returned null bitmap for ghost image last");
            }
            is.close();

            thumbnail = rotateForExif(thumbnail, uri);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "failed to load bitmap for ghost image last", e);
        }
        return thumbnail;
    }

    /** Shows the default "blank" gallery icon, when we don't have a thumbnail available.
     */
    private void updateGalleryIconToBlank() {
        if( MyDebug.LOG )
            Log.d(TAG, "updateGalleryIconToBlank");
        ImageButton galleryButton = this.findViewById(R.id.gallery);
        int bottom = galleryButton.getPaddingBottom();
        int top = galleryButton.getPaddingTop();
        int right = galleryButton.getPaddingRight();
        int left = galleryButton.getPaddingLeft();
	    /*if( MyDebug.LOG )
			Log.d(TAG, "padding: " + bottom);*/
        galleryButton.setImageBitmap(null);
        galleryButton.setImageResource(R.drawable.baseline_photo_library_white_48);
        // workaround for setImageResource also resetting padding, Android bug
        galleryButton.setPadding(left, top, right, bottom);
        gallery_bitmap = null;
    }

    /** Shows a thumbnail for the gallery icon.
     */
    void updateGalleryIcon(Bitmap thumbnail) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateGalleryIcon: " + thumbnail);
        // If we're currently running the background task to update the gallery (see updateGalleryIcon()), we should cancel that!
        // Otherwise if user takes a photo whilst the background task is still running, the thumbnail from the latest photo will
        // be overridden when the background task completes. This is more likely when using SAF on Android 10+ with scoped storage,
        // due to SAF's poor performance for folders with large number of files.
        if( update_gallery_future != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "cancel update_gallery_future");
            update_gallery_future.cancel(true);
        }
        ImageButton galleryButton = this.findViewById(R.id.gallery);
        galleryButton.setImageBitmap(thumbnail);
        gallery_bitmap = thumbnail;
    }

    /** Updates the gallery icon by searching for the most recent photo.
     *  Launches the task in a separate thread.
     */
    public void updateGalleryIcon() {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateGalleryIcon");
            debug_time = System.currentTimeMillis();
        }
        if( update_gallery_future != null ) {
            Log.d(TAG, "previous updateGalleryIcon task already running");
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String ghost_image_pref = sharedPreferences.getString(PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off");
        final boolean ghost_image_last = ghost_image_pref.equals("preference_ghost_image_last");

        final Handler handler = new Handler(Looper.getMainLooper());

        //new AsyncTask<Void, Void, Bitmap>() {
        Runnable runnable = new Runnable() {
            private static final String TAG = "updateGalleryIcon";
            private Uri uri;
            private boolean is_raw;
            private boolean is_video;

            @Override
            //protected Bitmap doInBackground(Void... params) {
            public void run() {
                if( MyDebug.LOG )
                    Log.d(TAG, "doInBackground");
                StorageUtils.Media media = applicationInterface.getStorageUtils().getLatestMedia();
                Bitmap thumbnail = null;
                KeyguardManager keyguard_manager = (KeyguardManager)MainActivity.this.getSystemService(Context.KEYGUARD_SERVICE);
                boolean is_locked = keyguard_manager != null && keyguard_manager.inKeyguardRestrictedInputMode();
                if( MyDebug.LOG )
                    Log.d(TAG, "is_locked?: " + is_locked);
                if( media != null && getContentResolver() != null && !is_locked ) {
                    // check for getContentResolver() != null, as have had reported Google Play crashes

                    uri = media.getMediaStoreUri(MainActivity.this);
                    is_raw = media.filename != null && StorageUtils.filenameIsRaw(media.filename);
                    is_video = media.video;

                    if( ghost_image_last && !media.video ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "load full size bitmap for ghost image last photo");
                        // use sample factor of 1 so that it's full size for ghost image
                        thumbnail = loadThumbnailFromUri(media.uri, 1);
                    }
                    if( thumbnail == null ) {
                        try {
                            if( !media.video ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "load thumbnail for photo");
                                // use sample factor as this image is only used for thumbnail; and
                                // unlike code in MyApplicationInterface.saveImage() we don't need to
                                // worry about the thumbnail animation when taking/saving a photo
                                thumbnail = loadThumbnailFromUri(media.uri, 8);
                            }
                            else if( !media.mediastore ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "load thumbnail for video from SAF uri");
                                ParcelFileDescriptor pfd_saf = null; // keep a reference to this as long as retriever, to avoid risk of pfd_saf being garbage collected
                                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                try {
                                    pfd_saf = getContentResolver().openFileDescriptor(media.uri, "r");
                                    retriever.setDataSource(pfd_saf.getFileDescriptor());
                                    thumbnail = retriever.getFrameAtTime(-1);
                                }
                                catch(Exception e) {
                                    MyDebug.logStackTrace(TAG, "failed to load video thumbnail", e);
                                }
                                finally {
                                    try {
                                        retriever.release();
                                    }
                                    catch(RuntimeException ex) {
                                        // ignore
                                    }
                                    try {
                                        if( pfd_saf != null ) {
                                            pfd_saf.close();
                                        }
                                    }
                                    catch(IOException e) {
                                        MyDebug.logStackTrace(TAG, "failed to close pfd_saf", e);
                                    }
                                }
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "load thumbnail for video");
                                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                                    final Size size = new Size(512, 384); // same as MediaStore.ThumbnailConstants.MINI_SIZE, which is used for MediaStore.Video.Thumbnails.MINI_KIND
                                    thumbnail = getContentResolver().loadThumbnail(media.uri, size, new CancellationSignal());
                                }
                                else {
                                    // non-deprecated getContentResolver().loadThumbnail requires Android Q
                                    //noinspection deprecation
                                    thumbnail = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), media.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                                }
                            }
                        }
                        catch(Throwable e) {
                            // have had Google Play NoClassDefFoundError crashes from getThumbnail() for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn)
                            // also NegativeArraySizeException - best to catch everything
                            if( MyDebug.LOG )
                                Log.e(TAG, "thumbnail exception");
                            MyDebug.logStackTrace(TAG, "thumbnail exception", e);
                        }
                    }
                }
                //return thumbnail;

                final Bitmap thumbnail_f = thumbnail;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onPostExecute(thumbnail_f);
                    }
                });
            }

            /** Runs on UI thread, after background work is complete.
             */
            private void onPostExecute(Bitmap thumbnail) {
                if( MyDebug.LOG )
                    Log.d(TAG, "onPostExecute");
                if( update_gallery_future != null && update_gallery_future.isCancelled() ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "was cancelled");
                    update_gallery_future = null;
                    return;
                }
                // since we're now setting the thumbnail to the latest media on disk, we need to make sure clicking the Gallery goes to this
                applicationInterface.getStorageUtils().clearLastMediaScanned();
                if( uri != null ) {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "found media uri: " + uri);
                        Log.d(TAG, "    is_raw?: " + is_raw);
                    }
                    applicationInterface.getStorageUtils().setLastMediaScanned(uri, is_raw, false, null);
                }
                if( thumbnail != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set gallery button to thumbnail");
                    updateGalleryIcon(thumbnail);
                    applicationInterface.getDrawPreview().updateThumbnail(thumbnail, is_video, false); // needed in case last ghost image is enabled
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set gallery button to blank");
                    updateGalleryIconToBlank();
                }

                update_gallery_future = null;
            }
        //}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        //executor.execute(runnable);
        update_gallery_future = executor.submit(runnable);

        if( MyDebug.LOG )
            Log.d(TAG, "updateGalleryIcon: total time to update gallery icon: " + (System.currentTimeMillis() - debug_time));
    }

    void savingImage(final boolean started) {
        if( MyDebug.LOG )
            Log.d(TAG, "savingImage: " + started);

        this.runOnUiThread(new Runnable() {
            public void run() {
                final ImageButton galleryButton = findViewById(R.id.gallery);
                if( started ) {
                    //galleryButton.setColorFilter(0x80ffffff, PorterDuff.Mode.MULTIPLY);
                    if( gallery_save_anim == null ) {
                        gallery_save_anim = ValueAnimator.ofInt(Color.argb(200, 255, 255, 255), Color.argb(63, 255, 255, 255));
                        gallery_save_anim.setEvaluator(new ArgbEvaluator());
                        gallery_save_anim.setRepeatCount(ValueAnimator.INFINITE);
                        gallery_save_anim.setRepeatMode(ValueAnimator.REVERSE);
                        gallery_save_anim.setDuration(500);
                    }
                    gallery_save_anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                            galleryButton.setColorFilter((Integer)animation.getAnimatedValue(), PorterDuff.Mode.MULTIPLY);
                        }
                    });
                    gallery_save_anim.start();
                }
                else
                if( gallery_save_anim != null ) {
                    gallery_save_anim.cancel();
                }
                galleryButton.setColorFilter(null);
            }
        });
    }

    /** Called when the number of images being saved in ImageSaver changes (or otherwise something
     *  that changes our calculation of whether we can take a new photo, e.g., changing photo mode).
     */
    void imageQueueChanged() {
        if( MyDebug.LOG )
            Log.d(TAG, "imageQueueChanged");
        applicationInterface.getDrawPreview().setImageQueueFull( !applicationInterface.canTakeNewPhoto() );

        /*if( applicationInterface.getImageSaver().getNImagesToSave() == 0) {
            cancelImageSavingNotification();
        }
        else if( has_notification ) {
            // call again to update the text of remaining images
            createImageSavingNotification();
        }*/
    }

    /** Creates a notification to indicate still saving images (or updates an existing one).
      * Update: notifications now removed due to needing permissions on Android 13+.
     */
    private void createImageSavingNotification() {
        if( MyDebug.LOG )
            Log.d(TAG, "createImageSavingNotification");
        /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            int n_images_to_save = applicationInterface.getImageSaver().getNRealImagesToSave();
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notify_take_photo)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.image_saving_notification) + " " + n_images_to_save + " " + getString(R.string.remaining))
                    //.setStyle(new Notification.BigTextStyle()
                    //        .bigText("Much longer text that cannot fit one line..."))
                    //.setPriority(Notification.PRIORITY_DEFAULT)
                    ;
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.notify(image_saving_notification_id, builder.build());
            has_notification = true;
        }*/
    }

    /** Cancels the notification for saving images.
     *  Update: notifications now removed due to needing permissions on Android 13+.
     */
    private void cancelImageSavingNotification() {
        if( MyDebug.LOG )
            Log.d(TAG, "cancelImageSavingNotification");
        /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.cancel(image_saving_notification_id);
            has_notification = false;
        }*/
    }

    public void clickedGallery(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedGallery");
        openGallery();
    }

    private void openGallery() {
        if( MyDebug.LOG )
            Log.d(TAG, "openGallery");
        //Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Uri uri = applicationInterface.getStorageUtils().getLastMediaScanned();
        boolean is_raw = uri != null && applicationInterface.getStorageUtils().getLastMediaScannedIsRaw();
        if( MyDebug.LOG && uri != null ) {
            Log.d(TAG, "found cached most recent uri: " + uri);
            Log.d(TAG, "    is_raw: " + is_raw);
        }
        if( uri == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "go to latest media");
            StorageUtils.Media media = applicationInterface.getStorageUtils().getLatestMedia();
            if( media != null ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "latest uri:" + media.uri);
                    Log.d(TAG, "filename: " + media.filename);
                }
                uri = media.getMediaStoreUri(this);
                if( MyDebug.LOG )
                    Log.d(TAG, "media uri:" + uri);
                is_raw = media.filename != null && StorageUtils.filenameIsRaw(media.filename);
                if( MyDebug.LOG )
                    Log.d(TAG, "is_raw:" + is_raw);
            }
        }

        if( uri != null && !MainActivity.useScopedStorage() ) {
            // check uri exists
            // note, with scoped storage this isn't reliable when using SAF - since we don't actually have permission to access mediastore URIs that
            // were created via Storage Access Framework, even though Open Camera was the application that saved them(!)
            try {
                ContentResolver cr = getContentResolver();
                ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
                if( pfd == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "uri no longer exists (1): " + uri);
                    uri = null;
                    is_raw = false;
                }
                else {
                    pfd.close();
                }
            }
            catch(IOException e) {
                if( MyDebug.LOG )
                    Log.d(TAG, "uri no longer exists (2): " + uri);
                uri = null;
                is_raw = false;
            }
        }
        if( uri == null ) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            is_raw = false;
        }
        if( !is_test ) {
            // don't do if testing, as unclear how to exit activity to finish test (for testGallery())
            if( MyDebug.LOG )
                Log.d(TAG, "launch uri:" + uri);
            final String REVIEW_ACTION = "com.android.camera.action.REVIEW";
            boolean done = false;
            if( !is_raw ) {
                // REVIEW_ACTION means we can view video files without autoplaying.
                // However, Google Photos at least has problems with going to a RAW photo (in RAW only mode),
                // unless we first pause and resume Open Camera.
                // Update: on Galaxy S10e with Android 11 at least, no longer seem to have problems, but leave
                // the check for is_raw just in case for older devices.
                if( MyDebug.LOG )
                    Log.d(TAG, "try REVIEW_ACTION");
                try {
                    Intent intent = new Intent(REVIEW_ACTION, uri);
                    this.startActivity(intent);
                    done = true;
                }
                catch(ActivityNotFoundException e) {
                    MyDebug.logStackTrace(TAG, "failed to start REVIEW_ACTION intent", e);
                }
            }
            if( !done ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "try ACTION_VIEW");
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    this.startActivity(intent);
                }
                catch(ActivityNotFoundException e) {
                    MyDebug.logStackTrace(TAG, "failed to start ACTION_VIEW intent", e);
                    preview.showToast(null, R.string.no_gallery_app);
                }
                catch(SecurityException e) {
                    // have received this crash from Google Play - don't display a toast, simply do nothing
                    MyDebug.logStackTrace(TAG, "SecurityException from ACTION_VIEW startActivity", e);
                }
            }
        }
    }

    /** Opens the Storage Access Framework dialog to select a folder for save location.
     * @param from_preferences Whether called from the Preferences
     */
    void openFolderChooserDialogSAF(boolean from_preferences) {
        if( MyDebug.LOG )
            Log.d(TAG, "openFolderChooserDialogSAF: " + from_preferences);
        this.saf_dialog_from_preferences = from_preferences;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        //Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, CHOOSE_SAVE_FOLDER_SAF_CODE);
    }

    /** Opens the Storage Access Framework dialog to select a file for ghost image.
     * @param from_preferences Whether called from the Preferences
     */
    void openGhostImageChooserDialogSAF(boolean from_preferences) {
        if( MyDebug.LOG )
            Log.d(TAG, "openGhostImageChooserDialogSAF: " + from_preferences);
        this.saf_dialog_from_preferences = from_preferences;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, CHOOSE_GHOST_IMAGE_SAF_CODE);
        }
        catch(ActivityNotFoundException e) {
            // see https://stackoverflow.com/questions/34021039/action-open-document-not-working-on-miui/34045627
            preview.showToast(null, R.string.open_files_saf_exception_ghost);
            MyDebug.logStackTrace(TAG, "ActivityNotFoundException from startActivityForResult", e);
        }
    }

    /** Opens the Storage Access Framework dialog to select a file for loading settings.
     * @param from_preferences Whether called from the Preferences
     */
    void openLoadSettingsChooserDialogSAF(boolean from_preferences) {
        if( MyDebug.LOG )
            Log.d(TAG, "openLoadSettingsChooserDialogSAF: " + from_preferences);
        this.saf_dialog_from_preferences = from_preferences;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml"); // note that application/xml doesn't work (can't select the xml files)!
        try {
            startActivityForResult(intent, CHOOSE_LOAD_SETTINGS_SAF_CODE);
        }
        catch(ActivityNotFoundException e) {
            // see https://stackoverflow.com/questions/34021039/action-open-document-not-working-on-miui/34045627
            preview.showToast(null, R.string.open_files_saf_exception_generic);
            MyDebug.logStackTrace(TAG, "ActivityNotFoundException from startActivityForResult", e);
        }
    }

    /** Call when the SAF save history has been updated.
     *  This is only public so we can call from testing.
     * @param save_folder The new SAF save folder Uri.
     */
    public void updateFolderHistorySAF(String save_folder) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateSaveHistorySAF");
        if( save_location_history_saf == null ) {
            save_location_history_saf = new SaveLocationHistory(this, "save_location_history_saf", save_folder);
        }
        save_location_history_saf.updateFolderHistory(save_folder, true);
    }

    /** Listens for the response from the Storage Access Framework dialog to select a folder
     *  (as opened with openFolderChooserDialogSAF()).
     */
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if( MyDebug.LOG )
            Log.d(TAG, "onActivityResult: " + requestCode);

        super.onActivityResult(requestCode, resultCode, resultData);

        switch( requestCode ) {
            case CHOOSE_SAVE_FOLDER_SAF_CODE:
                if( resultCode == RESULT_OK && resultData != null ) {
                    Uri treeUri = resultData.getData();
                    if( MyDebug.LOG )
                        Log.d(TAG, "returned treeUri: " + treeUri);
                    // see https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions :
                    final int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    try {
					/*if( true )
						throw new SecurityException(); // test*/
                        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);

                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.SaveLocationSAFPreferenceKey, treeUri.toString());
                        editor.apply();

                        if( MyDebug.LOG )
                            Log.d(TAG, "update folder history for saf");
                        updateFolderHistorySAF(treeUri.toString());

                        String file = applicationInterface.getStorageUtils().getImageFolderPath();
                        if( file != null ) {
                            preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + file);
                        }
                    }
                    catch(SecurityException e) {
                        MyDebug.logStackTrace(TAG, "SecurityException failed to take permission", e);
                        preview.showToast(null, R.string.saf_permission_failed);
                        // failed - if the user had yet to set a save location, make sure we switch SAF back off
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                        String uri = sharedPreferences.getString(PreferenceKeys.SaveLocationSAFPreferenceKey, "");
                        if( uri.isEmpty() ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "no SAF save location was set");
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putBoolean(PreferenceKeys.UsingSAFPreferenceKey, false);
                            editor.apply();
                        }
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "SAF dialog cancelled");
                    // cancelled - if the user had yet to set a save location, make sure we switch SAF back off
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    String uri = sharedPreferences.getString(PreferenceKeys.SaveLocationSAFPreferenceKey, "");
                    if( uri.isEmpty() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "no SAF save location was set");
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(PreferenceKeys.UsingSAFPreferenceKey, false);
                        editor.apply();
                        preview.showToast(null, R.string.saf_cancelled);
                    }
                }

                if( !saf_dialog_from_preferences ) {
                    setWindowFlagsForCamera();
                    showPreview(true);
                }
                break;
            case CHOOSE_GHOST_IMAGE_SAF_CODE:
                if( resultCode == RESULT_OK && resultData != null ) {
                    Uri fileUri = resultData.getData();
                    if( MyDebug.LOG )
                        Log.d(TAG, "returned single fileUri: " + fileUri);
                    // persist permission just in case?
                    final int takeFlags = resultData.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
					/*if( true )
						throw new SecurityException(); // test*/
                        // Check for the freshest data.
                        getContentResolver().takePersistableUriPermission(fileUri, takeFlags);

                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.GhostSelectedImageSAFPreferenceKey, fileUri.toString());
                        editor.apply();
                    }
                    catch(SecurityException e) {
                        MyDebug.logStackTrace(TAG, "SecurityException failed to take permission", e);
                        preview.showToast(null, R.string.saf_permission_failed_open_image);
                        // failed - if the user had yet to set a ghost image
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                        String uri = sharedPreferences.getString(PreferenceKeys.GhostSelectedImageSAFPreferenceKey, "");
                        if( uri.isEmpty() ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "no SAF ghost image was set");
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off");
                            editor.apply();
                        }
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "SAF dialog cancelled");
                    // cancelled - if the user had yet to set a ghost image, make sure we switch the option back off
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    String uri = sharedPreferences.getString(PreferenceKeys.GhostSelectedImageSAFPreferenceKey, "");
                    if( uri.isEmpty() ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "no SAF ghost image was set");
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off");
                        editor.apply();
                    }
                }

                if( !saf_dialog_from_preferences ) {
                    setWindowFlagsForCamera();
                    showPreview(true);
                }
                break;
            case CHOOSE_LOAD_SETTINGS_SAF_CODE:
                if( resultCode == RESULT_OK && resultData != null ) {
                    Uri fileUri = resultData.getData();
                    if( MyDebug.LOG )
                        Log.d(TAG, "returned single fileUri: " + fileUri);
                    // persist permission just in case?
                    final int takeFlags = resultData.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
					/*if( true )
						throw new SecurityException(); // test*/
                        // Check for the freshest data.
                        getContentResolver().takePersistableUriPermission(fileUri, takeFlags);

                        settingsManager.loadSettings(fileUri);
                    }
                    catch(SecurityException e) {
                        MyDebug.logStackTrace(TAG, "SecurityException failed to take permission", e);
                        preview.showToast(null, R.string.restore_settings_failed);
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "SAF dialog cancelled");
                }

                if( !saf_dialog_from_preferences ) {
                    setWindowFlagsForCamera();
                    showPreview(true);
                }
                break;
        }
    }

    /** Update the save folder (for non-SAF methods).
     */
    void updateSaveFolder(String new_save_location) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateSaveFolder: " + new_save_location);
        if( new_save_location != null ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String orig_save_location = this.applicationInterface.getStorageUtils().getSaveLocation();

            if( !orig_save_location.equals(new_save_location) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "changed save_folder to: " + this.applicationInterface.getStorageUtils().getSaveLocation());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.SaveLocationPreferenceKey, new_save_location);
                editor.apply();

                this.save_location_history.updateFolderHistory(this.getStorageUtils().getSaveLocation(), true);
                String save_folder_name = getHumanReadableSaveFolder(this.applicationInterface.getStorageUtils().getSaveLocation());
                this.preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + save_folder_name);
            }
        }
    }

    public static class MyFolderChooserDialog extends FolderChooserDialog {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if( MyDebug.LOG )
                Log.d(TAG, "FolderChooserDialog dismissed");
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            MainActivity main_activity = (MainActivity)this.getActivity();
            // activity may be null, see https://stackoverflow.com/questions/13116104/best-practice-to-reference-the-parent-activity-of-a-fragment
            // have had Google Play crashes from this
            if( main_activity != null ) {
                main_activity.setWindowFlagsForCamera();
                main_activity.showPreview(true);
                String new_save_location = this.getChosenFolder();
                main_activity.updateSaveFolder(new_save_location);
            }
            else {
                if( MyDebug.LOG )
                    Log.e(TAG, "activity no longer exists!");
            }
            super.onDismiss(dialog);
        }
    }

    /** Processes a user specified save folder. This should be used with the non-SAF scoped storage
     *  method, where the user types a folder directly.
     */
    public static String processUserSaveLocation(String folder) {
        // filter repeated '/', e.g., replace // with /:
        String strip = "//";
        while( !folder.isEmpty() && folder.contains(strip) ) {
            folder = folder.replaceAll(strip, "/");
        }

        if( !folder.isEmpty() && folder.charAt(0) == '/' ) {
            // strip '/' as first character - as absolute paths not allowed with scoped storage
            // whilst we do block entering a '/' as first character in the InputFilter, users could
            // get around this (e.g., put a '/' as second character, then delete the first character)
            folder = folder.substring(1);
        }

        if( !folder.isEmpty() && folder.charAt(folder.length()-1) == '/' ) {
            // strip '/' as last character - MediaStore will ignore it, but seems cleaner to strip it out anyway
            // (we still need to allow '/' as last character in the InputFilter, otherwise users won't be able to type it whilst writing a subfolder)
            folder = folder.substring(0, folder.length()-1);
        }

        return folder;
    }

    /** Creates a dialog builder for specifying a save folder dialog (used when not using SAF,
     *  and on scoped storage, as an alternative to using FolderChooserDialog).
     */
    public AlertDialog.Builder createSaveFolderDialog() {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.preference_save_location);

        final View dialog_view = LayoutInflater.from(this).inflate(R.layout.alertdialog_edittext, null);
        final EditText editText = dialog_view.findViewById(R.id.edit_text);

        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
        editText.setHint(getResources().getString(R.string.preference_save_location));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        editText.setText(sharedPreferences.getString(PreferenceKeys.SaveLocationPreferenceKey, "LinkedCamera"));
        InputFilter filter = new InputFilter() {
            // whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
            final String disallowed = "|\\?*<\":>";
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for(int i=start;i<end;i++) {
                    if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
                        return "";
                    }
                }
                // also check for '/', not allowed at start
                if( dstart == 0 && start < source.length() && source.charAt(start) == '/' ) {
                    return "";
                }
                return null;
            }
        };
        editText.setFilters(new InputFilter[]{filter});

        alertDialog.setView(dialog_view);

        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if( MyDebug.LOG )
                    Log.d(TAG, "save location clicked okay");

                String folder = editText.getText().toString();
                folder = processUserSaveLocation(folder);

                updateSaveFolder(folder);
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel, null);

        return alertDialog;
    }

    /** Opens Open Camera's own (non-Storage Access Framework) dialog to select a folder.
     */
    private void openFolderChooserDialog() {
        if( MyDebug.LOG )
            Log.d(TAG, "openFolderChooserDialog");
        showPreview(false);
        setWindowFlagsForSettings();

        if( MainActivity.useScopedStorage() ) {
            AlertDialog.Builder alertDialog = createSaveFolderDialog();
            final AlertDialog alert = alertDialog.create();
            // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface arg0) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "save folder dialog dismissed");
                    setWindowFlagsForCamera();
                    showPreview(true);
                }
            });
            alert.show();
        }
        else {
            File start_folder = getStorageUtils().getImageFolder();

            FolderChooserDialog fragment = new MyFolderChooserDialog();
            fragment.setStartFolder(start_folder);
            // use commitAllowingStateLoss() instead of fragment.show(), does to "java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState" crash seen on Google Play
            // see https://stackoverflow.com/questions/14262312/java-lang-illegalstateexception-can-not-perform-this-action-after-onsaveinstanc
            //fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
            getFragmentManager().beginTransaction().add(fragment, "FOLDER_FRAGMENT").commitAllowingStateLoss();
        }
    }

    /** Returns a human readable string for the save_folder (as stored in the preferences).
     */
    String getHumanReadableSaveFolder(String save_folder) {
        if( applicationInterface.getStorageUtils().isUsingSAF() ) {
            // try to get human readable form if possible
            String file_name = applicationInterface.getStorageUtils().getFilePathFromDocumentUriSAF(Uri.parse(save_folder), true);
            if( file_name != null ) {
                save_folder = file_name;
            }
        }
        else {
            // The strings can either be a sub-folder of DCIM, or (pre-scoped-storage) a full path, so normally either can be displayed.
            // But with scoped storage, an empty string is used to mean DCIM, so seems clearer to say that instead of displaying a blank line!
            if( MainActivity.useScopedStorage() && save_folder.isEmpty() ) {
                save_folder = "DCIM";
            }
        }
        return save_folder;
    }

    /** User can long-click on gallery to select a recent save location from the history, of if not available,
     *  go straight to the file dialog to pick a folder.
     */
    private void longClickedGallery() {
        if( MyDebug.LOG )
            Log.d(TAG, "longClickedGallery");
        if( applicationInterface.getStorageUtils().isUsingSAF() ) {
            if( save_location_history_saf == null || save_location_history_saf.size() <= 1 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "go straight to choose folder dialog for SAF");
                openFolderChooserDialogSAF(false);
                return;
            }
        }
        else {
            if( save_location_history.size() <= 1 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "go straight to choose folder dialog");
                openFolderChooserDialog();
                return;
            }
        }

        final SaveLocationHistory history = applicationInterface.getStorageUtils().isUsingSAF() ? save_location_history_saf : save_location_history;
        showPreview(false);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.choose_save_location);
        CharSequence [] items = new CharSequence[history.size()+2];
        int index=0;
        // history is stored in order most-recent-last
        for(int i=0;i<history.size();i++) {
            String folder_name = history.get(history.size() - 1 - i);
            folder_name = getHumanReadableSaveFolder(folder_name);
            items[index++] = folder_name;
        }
        final int clear_index = index;
        items[index++] = getResources().getString(R.string.clear_folder_history);
        final int new_index = index;
        //noinspection UnusedAssignment
        items[index++] = getResources().getString(R.string.choose_another_folder);
        //alertDialog.setItems(items, new DialogInterface.OnClickListener() {
        alertDialog.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if( which == clear_index ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "selected clear save history");
                    new AlertDialog.Builder(MainActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.clear_folder_history)
                            .setMessage(R.string.clear_folder_history_question)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "confirmed clear save history");
                                    if( applicationInterface.getStorageUtils().isUsingSAF() )
                                        clearFolderHistorySAF();
                                    else
                                        clearFolderHistory();
                                    setWindowFlagsForCamera();
                                    showPreview(true);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "don't clear save history");
                                    setWindowFlagsForCamera();
                                    showPreview(true);
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface arg0) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "cancelled clear save history");
                                    setWindowFlagsForCamera();
                                    showPreview(true);
                                }
                            })
                            .show();
                }
                else if( which == new_index ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "selected choose new folder");
                    if( applicationInterface.getStorageUtils().isUsingSAF() ) {
                        openFolderChooserDialogSAF(false);
                    }
                    else {
                        openFolderChooserDialog();
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "selected: " + which);
                    if( which >= 0 && which < history.size() ) {
                        String save_folder = history.get(history.size() - 1 - which);
                        if( MyDebug.LOG )
                            Log.d(TAG, "changed save_folder from history to: " + save_folder);
                        String save_folder_name = getHumanReadableSaveFolder(save_folder);
                        preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + save_folder_name);
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        if( applicationInterface.getStorageUtils().isUsingSAF() )
                            editor.putString(PreferenceKeys.SaveLocationSAFPreferenceKey, save_folder);
                        else
                            editor.putString(PreferenceKeys.SaveLocationPreferenceKey, save_folder);
                        editor.apply();
                        history.updateFolderHistory(save_folder, true); // to move new selection to most recent
                    }
                    setWindowFlagsForCamera();
                    showPreview(true);
                }

                dialog.dismiss(); // need to explicitly dismiss for setSingleChoiceItems
            }
        });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                setWindowFlagsForCamera();
                showPreview(true);
            }
        });
        //getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        setWindowFlagsForSettings();
        showAlert(alertDialog.create());
    }

    /** Clears the non-SAF folder history.
     */
    public void clearFolderHistory() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearFolderHistory");
        save_location_history.clearFolderHistory(getStorageUtils().getSaveLocation());
    }

    /** Clears the SAF folder history.
     */
    public void clearFolderHistorySAF() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearFolderHistorySAF");
        save_location_history_saf.clearFolderHistory(getStorageUtils().getSaveLocationSAF());
    }

    static private void putBundleExtra(Bundle bundle, String key, List<String> values) {
        if( values != null ) {
            String [] values_arr = new String[values.size()];
            int i=0;
            for(String value: values) {
                values_arr[i] = value;
                i++;
            }
            bundle.putStringArray(key, values_arr);
        }
    }

    public void clickedShare(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedShare");
        applicationInterface.shareLastImage();
    }

    public void clickedTrash(View view) {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedTrash");
        applicationInterface.trashLastImage();
    }

    /** User has pressed the take picture button, or done an equivalent action to request this (e.g.,
     *  volume buttons, audio trigger).
     * @param photo_snapshot If true, then the user has requested taking a photo whilst video
     *                       recording. If false, either take a photo or start/stop video depending
     *                       on the current mode.
     */
    public void takePicture(boolean photo_snapshot) {
        if( MyDebug.LOG )
            Log.d(TAG, "takePicture");

        if( applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama ) {
            if( preview.isTakingPhoto() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "ignore whilst taking panorama photo");
            }
            else if( applicationInterface.getGyroSensor().isRecording() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "panorama complete");
                applicationInterface.finishPanorama();
                return;
            }
            else if( !applicationInterface.canTakeNewPhoto() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "can't start new panoroma, still saving in background");
                // we need to test here, otherwise the Preview won't take a new photo - but we'll think we've
                // started the panorama!
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "start panorama");
                applicationInterface.startPanorama();
            }
        }

        this.takePicturePressed(photo_snapshot, false);
    }

    /** Returns whether the last photo operation was a continuous fast burst.
     */
    boolean lastContinuousFastBurst() {
        return this.last_continuous_fast_burst;
    }

    /**
     * @param photo_snapshot If true, then the user has requested taking a photo whilst video
     *                       recording. If false, either take a photo or start/stop video depending
     *                       on the current mode.
     * @param continuous_fast_burst If true, then start a continuous fast burst.
     */
    void takePicturePressed(boolean photo_snapshot, boolean continuous_fast_burst) {
        if( MyDebug.LOG )
            Log.d(TAG, "takePicturePressed");

        closePopup();

        this.last_continuous_fast_burst = continuous_fast_burst;
        this.preview.takePicturePressed(photo_snapshot, continuous_fast_burst);
    }

    /** Lock the screen - this is Open Camera's own lock to guard against accidental presses,
     *  not the standard Android lock.
     */
    void lockScreen() {
        findViewById(R.id.locker).setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility") @Override
            public boolean onTouch(View arg0, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
                //return true;
            }
        });
        screen_is_locked = true;
        this.enableScreenLockOnBackPressedCallback(true); // also disable back button
    }

    /** Unlock the screen (see lockScreen()).
     */
    void unlockScreen() {
        findViewById(R.id.locker).setOnTouchListener(null);
        screen_is_locked = false;
        this.enableScreenLockOnBackPressedCallback(false); // reenable back button
    }

    /** Whether the screen is locked (see lockScreen()).
     */
    public boolean isScreenLocked() {
        return screen_is_locked;
    }

    /** Listen for gestures.
     *  Doing a swipe will unlock the screen (see lockScreen()).
     */
    private class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            try {
                if( MyDebug.LOG )
                    Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
                final ViewConfiguration vc = ViewConfiguration.get(MainActivity.this);
                //final int swipeMinDistance = 4*vc.getScaledPagingTouchSlop();
                final float scale = getResources().getDisplayMetrics().density;
                final int swipeMinDistance = (int) (160 * scale + 0.5f); // convert dps to pixels
                final int swipeThresholdVelocity = vc.getScaledMinimumFlingVelocity();
                if( MyDebug.LOG ) {
                    Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
                    Log.d(TAG, "swipeMinDistance: " + swipeMinDistance);
                }
                float xdist = e1.getX() - e2.getX();
                float ydist = e1.getY() - e2.getY();
                float dist2 = xdist*xdist + ydist*ydist;
                float vel2 = velocityX*velocityX + velocityY*velocityY;
                if( dist2 > swipeMinDistance*swipeMinDistance && vel2 > swipeThresholdVelocity*swipeThresholdVelocity ) {
                    preview.showToast(screen_locked_toast, R.string.unlocked);
                    unlockScreen();
                }
            }
            catch(Exception e) {
                MyDebug.logStackTrace(TAG, "onFling failed", e);
            }
            return false;
        }

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            preview.showToast(screen_locked_toast, R.string.screen_is_locked);
            return true;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        if( MyDebug.LOG )
            Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(state);
        if( this.preview != null ) {
            preview.onSaveInstanceState(state);
        }
        if( this.applicationInterface != null ) {
            applicationInterface.onSaveInstanceState(state);
        }
    }

    public boolean supportsExposureButton() {
        if( preview.isVideoHighSpeed() ) {
            // manual ISO/exposure not supported for high speed video mode
            // it's safer not to allow opening the panel at all (otherwise the user could open it, and switch to manual)
            return false;
        }
        if( applicationInterface.isCameraExtensionPref() ) {
            // nothing in this UI (exposure compensation, manual ISO/exposure, manual white balance) is supported for camera extensions
            return false;
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String iso_value = sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
        boolean manual_iso = !iso_value.equals(CameraController.ISO_DEFAULT);
        return preview.supportsExposures() || (manual_iso && preview.supportsISORange() );
    }

    void cameraSetup() {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "cameraSetup");
            debug_time = System.currentTimeMillis();
        }
        if( preview.getCameraController() == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "camera controller is null");
            return;
        }

        boolean old_want_no_limits = want_no_limits;
        this.want_no_limits = false;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "multi-window mode");
            // don't support want_no_limits mode in multi-window mode - extra complexity that the
            // preview size could change from simply resizing the window; also problem that the
            // navigation_gap, and whether we'd want want_no_limits, can both change depending on
            // device orientation (because application can e.g. be in landscape mode even if device
            // has switched to portrait)
        }
        else if( set_window_insets_listener && !edge_to_edge_mode ) {
            Point display_size = new Point();
            applicationInterface.getDisplaySize(display_size, true);
            int display_width = Math.max(display_size.x, display_size.y);
            int display_height = Math.min(display_size.x, display_size.y);
            double display_aspect_ratio = ((double)display_width)/(double)display_height;
            double preview_aspect_ratio = preview.getCurrentPreviewAspectRatio();
            if( MyDebug.LOG ) {
                Log.d(TAG, "display_aspect_ratio: " + display_aspect_ratio);
                Log.d(TAG, "preview_aspect_ratio: " + preview_aspect_ratio);
            }
            boolean preview_is_wide = preview_aspect_ratio > display_aspect_ratio + 1.0e-5f;
            if( test_preview_want_no_limits ) {
                preview_is_wide = test_preview_want_no_limits_value;
            }
            if( preview_is_wide ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "preview is wide, set want_no_limits");
                this.want_no_limits = true;

                if( !old_want_no_limits ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "need to change to FLAG_LAYOUT_NO_LIMITS");
                    // Ideally we'd just go straight to FLAG_LAYOUT_NO_LIMITS mode, but then all calls to onApplyWindowInsets()
                    // end up returning a value of 0 for the navigation_gap! So we need to wait until we know the navigation_gap.
                    if( navigation_gap != 0 ) {
                        // already have navigation gap, can go straight into no limits mode
                        if( MyDebug.LOG )
                            Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS");
                        showUnderNavigation(true);
                        // need to layout the UI again due to now taking the navigation gap into account
                        if( MyDebug.LOG )
                            Log.d(TAG, "layout UI due to changing want_no_limits behaviour");
                        mainUI.layoutUI();
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "but navigation_gap is 0");
                    }
                }
            }
            else if( old_want_no_limits && navigation_gap != 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS");
                showUnderNavigation(false);
                // need to layout the UI again due to no longer taking the navigation gap into account
                if( MyDebug.LOG )
                    Log.d(TAG, "layout UI due to changing want_no_limits behaviour");
                mainUI.layoutUI();
            }
        }

        if( this.supportsForceVideo4K() && preview.usingCamera2API() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "using Camera2 API, so can disable the force 4K option");
            this.disableForceVideo4K();
        }
        if( this.supportsForceVideo4K() && preview.getVideoQualityHander().getSupportedVideoSizes() != null ) {
            for(CameraController.Size size : preview.getVideoQualityHander().getSupportedVideoSizes()) {
                if( size.width >= 3840 && size.height >= 2160 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera natively supports 4K, so can disable the force option");
                    this.disableForceVideo4K();
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after handling Force 4K option: " + (System.currentTimeMillis() - debug_time));

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        {
            if( MyDebug.LOG )
                Log.d(TAG, "set up zoom");
            if( MyDebug.LOG )
                Log.d(TAG, "has_zoom? " + preview.supportsZoom());
            SeekBar zoomSeekBar = findViewById(R.id.zoom_seekbar);

            if( preview.supportsZoom() ) {
                zoomSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                zoomSeekBar.setMax(preview.getMaxZoom());
                zoomSeekBar.setProgress(preview.getMaxZoom()-preview.getCameraController().getZoom());
                zoomSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    private long last_haptic_time;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "zoom onProgressChanged: " + progress);
                        // note we zoom even if !fromUser, as various other UI controls (multitouch, volume key zoom)
                        // indirectly set zoom via this method, from setting the zoom slider
                        // if hasSmoothZoom()==true, then the preview already handled zooming to the current value
                        if( !preview.hasSmoothZoom() ) {
                            int new_zoom_factor = preview.getMaxZoom() - progress;
                            if( fromUser && preview.getCameraController() != null ) {
                                float old_zoom_ratio = preview.getZoomRatio();
                                float new_zoom_ratio = preview.getZoomRatio(new_zoom_factor);
                                if( new_zoom_ratio != old_zoom_ratio ) {
                                    last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                                }
                            }
                            preview.zoomTo(new_zoom_factor, false, true);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                if( sharedPreferences.getBoolean(PreferenceKeys.ShowZoomSliderControlsPreferenceKey, true) ) {
                    if( !mainUI.inImmersiveMode() ) {
                        zoomSeekBar.setVisibility(View.VISIBLE);
                    }
                }
                else {
                    zoomSeekBar.setVisibility(View.INVISIBLE); // should be INVISIBLE not GONE, as the focus_seekbar is aligned to be left to this; in future we might want this similarly for exposure panel
                }
            }
            else {
                zoomSeekBar.setVisibility(View.INVISIBLE); // should be INVISIBLE not GONE, as the focus_seekbar is aligned to be left to this; in future we might want this similarly for the exposure panel
            }
            if( MyDebug.LOG )
                Log.d(TAG, "cameraSetup: time after setting up zoom: " + (System.currentTimeMillis() - debug_time));

            View takePhotoButton = findViewById(R.id.take_photo);
            if( sharedPreferences.getBoolean(PreferenceKeys.ShowTakePhotoPreferenceKey, true) ) {
                if( !mainUI.inImmersiveMode() ) {
                    takePhotoButton.setVisibility(View.VISIBLE);
                }
            }
            else {
                takePhotoButton.setVisibility(View.INVISIBLE);
            }
        }
        {
            if( MyDebug.LOG )
                Log.d(TAG, "set up manual focus");
            setManualFocusSeekbar(false);
            setManualFocusSeekbar(true);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting up manual focus: " + (System.currentTimeMillis() - debug_time));
        {
            if( preview.supportsISORange()) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set up iso");
                final SeekBar iso_seek_bar = findViewById(R.id.iso_seekbar);
                iso_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                //setProgressSeekbarExponential(iso_seek_bar, preview.getMinimumISO(), preview.getMaximumISO(), preview.getCameraController().getISO());
                manualSeekbars.setProgressSeekbarISO(iso_seek_bar, preview.getMinimumISO(), preview.getMaximumISO(), preview.getCameraController().getISO());
                iso_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    private long last_haptic_time;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "iso seekbar onProgressChanged: " + progress);
						/*double frac = progress/(double)iso_seek_bar.getMax();
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time frac: " + frac);
						double scaling = MainActivity.seekbarScaling(frac);
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time scaling: " + scaling);
						int min_iso = preview.getMinimumISO();
						int max_iso = preview.getMaximumISO();
						int iso = min_iso + (int)(scaling * (max_iso - min_iso));*/
						/*int min_iso = preview.getMinimumISO();
						int max_iso = preview.getMaximumISO();
						int iso = (int)exponentialScaling(frac, min_iso, max_iso);*/
                        // n.b., important to update even if fromUser==false (e.g., so this works when user changes ISO via clicking
                        // the ISO buttons rather than moving the slider directly, see MainUI.setupExposureUI())
                        preview.setISO( manualSeekbars.getISO(progress) );
                        mainUI.updateSelectedISOButton();
                        if( fromUser ) {
                            last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                if( preview.supportsExposureTime() ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set up exposure time");
                    final SeekBar exposure_time_seek_bar = findViewById(R.id.exposure_time_seekbar);
                    exposure_time_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                    //setProgressSeekbarExponential(exposure_time_seek_bar, preview.getMinimumExposureTime(), preview.getMaximumExposureTime(), preview.getCameraController().getExposureTime());
                    manualSeekbars.setProgressSeekbarShutterSpeed(exposure_time_seek_bar, preview.getMinimumExposureTime(), preview.getMaximumExposureTime(), preview.getCameraController().getExposureTime());
                    exposure_time_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                        private long last_haptic_time;

                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "exposure_time seekbar onProgressChanged: " + progress);
							/*double frac = progress/(double)exposure_time_seek_bar.getMax();
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time frac: " + frac);
							long min_exposure_time = preview.getMinimumExposureTime();
							long max_exposure_time = preview.getMaximumExposureTime();
							long exposure_time = exponentialScaling(frac, min_exposure_time, max_exposure_time);*/
                            preview.setExposureTime( manualSeekbars.getExposureTime(progress) );
                            if( fromUser ) {
                                last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
                }
            }
        }
        setManualWBSeekbar();
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting up iso: " + (System.currentTimeMillis() - debug_time));
        {
            exposure_seekbar_values = null;
            if( preview.supportsExposures() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set up exposure compensation");
                final int min_exposure = preview.getMinimumExposure();
                SeekBar exposure_seek_bar = findViewById(R.id.exposure_seekbar);
                exposure_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state

                final int exposure_seekbar_n_repeated_zero = 3; // how many times to repeat 0 for R.id.exposure_seekbar, so that it "sticks" to zero when changing seekbar

                //exposure_seek_bar.setMax( preview.getMaximumExposure() - min_exposure + exposure_seekbar_n_repeated_zero-1 );
                //exposure_seek_bar.setProgress( preview.getCurrentExposure() - min_exposure );

                exposure_seekbar_values = new ArrayList<>();
                int current_exposure = preview.getCurrentExposure();
                int current_progress = 0;
                for(int i=min_exposure;i<=preview.getMaximumExposure();i++) {
                    exposure_seekbar_values.add(i);
                    if( i == 0 ) {
                        exposure_seekbar_values_zero = exposure_seekbar_values.size()-1;
                        exposure_seekbar_values_zero += (exposure_seekbar_n_repeated_zero-1)/2; // centre within the region of zeroes
                        for(int j=0;j<exposure_seekbar_n_repeated_zero-1;j++) {
                            exposure_seekbar_values.add(i);
                        }
                    }
                    if( i == current_exposure ) {
                        if( i == 0 ) {
                            current_progress += exposure_seekbar_values_zero;
                        }
                        else {
                            current_progress = exposure_seekbar_values.size()-1;
                        }
                    }
                }
                exposure_seek_bar.setMax( exposure_seekbar_values.size()-1 );
                exposure_seek_bar.setProgress( current_progress );
                exposure_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    private long last_haptic_time;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "exposure seekbar onProgressChanged: " + progress);
                        if( exposure_seekbar_values == null ) {
                            Log.e(TAG, "exposure_seekbar_values is null");
                            return;
                        }
                        int new_exposure = getExposureSeekbarValue(progress);
                        if( fromUser ) {
                            // check if not scrolling past the repeated zeroes
                            if( preview.getCurrentExposure() != new_exposure ) {
                                last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                            }
                        }
                        preview.setExposure(new_exposure);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting up exposure: " + (System.currentTimeMillis() - debug_time));

        // On-screen icons such as exposure lock, white balance lock, face detection etc are made visible if necessary in
        // MainUI.showGUI()
        // However still need to enable visibility of icons where visibility depends on camera setup - e.g., exposure button
        // not supported for high speed video frame rates - see testTakeVideoFPSHighSpeedManual().
        // (Disabling is done in checkDisableGUIIcons(), called below.)
        View exposureButton = findViewById(R.id.exposure);
        //exposureButton.setVisibility(supportsExposureButton() && !mainUI.inImmersiveMode() ? View.VISIBLE : View.GONE);
        if( supportsExposureButton() && !mainUI.inImmersiveMode() )
            exposureButton.setVisibility(View.VISIBLE);

        // needed as availability of some icons is per-camera (e.g., flash, RAW)
        // for making icons visible, this is done elsewhere in call to MainUI.showGUI()
        if( checkDisableGUIIcons() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "cameraSetup: need to layoutUI as we hid some icons");
            mainUI.layoutUI();
        }

        // need to update some icons, e.g., white balance and exposure lock due to them being turned off when pause/resuming
        mainUI.updateOnScreenIcons();

        mainUI.setPopupIcon(); // needed so that the icon is set right even if no flash mode is set when starting up camera (e.g., switching to front camera with no flash)
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting popup icon: " + (System.currentTimeMillis() - debug_time));

        mainUI.setTakePhotoIcon();
        mainUI.setSwitchCameraContentDescription();
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting take photo icon: " + (System.currentTimeMillis() - debug_time));

        if( !block_startup_toast ) {
            this.showPhotoVideoToast(false);
        }
        block_startup_toast = false;
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: total time for cameraSetup: " + (System.currentTimeMillis() - debug_time));

        this.getApplicationInterface().getDrawPreview().setDimPreview(false);

        if( push_switched_camera ) {
            push_switched_camera = false;
            View switchCameraButton = findViewById(R.id.switch_camera);
            switchCameraButton.animate().rotationBy(180).setDuration(250).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
    }

    public static long performHapticFeedback(SeekBar seekBar, long last_haptic_time) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(seekBar.getContext());
        if( sharedPreferences.getBoolean(PreferenceKeys.AllowHapticFeedbackPreferenceKey, true) ) {
            long time_ms = System.currentTimeMillis();
            if( time_ms > last_haptic_time + 16 ) {
                last_haptic_time = time_ms;
                // SEGMENT_TICK or SEGMENT_TICK doesn't work on Galaxy S24+ at least, even though on Android 14!
                /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK);
                }
                else*/ {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
        }
        return last_haptic_time;
    }

    public void setManualFocusSeekbarProgress(final boolean is_target_distance, float focus_distance) {
        final SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        ManualSeekbars.setProgressSeekbarScaled(focusSeekBar, 0.0, preview.getMinimumFocusDistance(), focus_distance);
    }

    private void setManualFocusSeekbar(final boolean is_target_distance) {
        if( MyDebug.LOG )
            Log.d(TAG, "setManualFocusSeekbar");
        final SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        focusSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
        setManualFocusSeekbarProgress(is_target_distance, is_target_distance ? preview.getCameraController().getFocusBracketingTargetDistance() : preview.getCameraController().getFocusDistance());
        focusSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            private boolean has_saved_zoom;
            private int saved_zoom_factor;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if( !is_target_distance && applicationInterface.isFocusBracketingSourceAutoPref() ) {
                    // source is set from continuous focus, not by changing the seekbar
                    if( fromUser ) {
                        // but if user has manually changed, then exit auto mode
                        applicationInterface.setFocusBracketingSourceAutoPref(false);
                        mainUI.destroyPopup(); // need to recreate popup
                    }
                    else {
                        return;
                    }
                }
                double frac = progress/(double)focusSeekBar.getMax();
                double scaling = ManualSeekbars.seekbarScaling(frac);
                float focus_distance = (float)(scaling * preview.getMinimumFocusDistance());
                preview.setFocusDistance(focus_distance, is_target_distance, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if( MyDebug.LOG )
                    Log.d(TAG, "manual focus seekbar: onStartTrackingTouch");
                has_saved_zoom = false;
                if( preview.supportsZoom() ) {
                    int focus_assist = applicationInterface.getFocusAssistPref();
                    if( focus_assist > 0 && preview.getCameraController() != null ) {
                        has_saved_zoom = true;
                        saved_zoom_factor = preview.getCameraController().getZoom();
                        if( MyDebug.LOG )
                            Log.d(TAG, "zoom by " + focus_assist + " for focus assist, zoom factor was: " + saved_zoom_factor);
                        int new_zoom_factor = preview.getScaledZoomFactor(focus_assist);
                        preview.getCameraController().setZoom(new_zoom_factor);
                    }
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if( MyDebug.LOG )
                    Log.d(TAG, "manual focus seekbar: onStopTrackingTouch");
                if( has_saved_zoom && preview.getCameraController() != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "unzoom for focus assist, zoom factor was: " + saved_zoom_factor);
                    preview.getCameraController().setZoom(saved_zoom_factor);
                }
                preview.stoppedSettingFocusDistance(is_target_distance);
            }
        });
        setManualFocusSeekBarVisibility(is_target_distance);
    }

    public boolean showManualFocusSeekbar(final boolean is_target_distance) {
        if( (applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.FocusBracketing) && !preview.isVideo() ) {
            return true; // both seekbars shown in focus bracketing mode
        }
        if( is_target_distance ) {
            return false; // target seekbar only shown in focus bracketing mode
        }
        boolean is_visible = preview.getCurrentFocusValue() != null && this.getPreview().getCurrentFocusValue().equals("focus_mode_manual2");
        return is_visible;
    }

    void setManualFocusSeekBarVisibility(final boolean is_target_distance) {
        boolean is_visible = showManualFocusSeekbar(is_target_distance);
        SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        final int visibility = is_visible ? View.VISIBLE : View.GONE;
        focusSeekBar.setVisibility(visibility);
        if( is_visible ) {
            applicationInterface.getDrawPreview().updateSettings(); // needed so that we reset focus_seekbars_margin_left, as the focus seekbars can only be updated when visible
        }
    }

    public void setManualWBSeekbar() {
        if( MyDebug.LOG )
            Log.d(TAG, "setManualWBSeekbar");
        if( preview.getSupportedWhiteBalances() != null && preview.supportsWhiteBalanceTemperature() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set up manual white balance");
            SeekBar white_balance_seek_bar = findViewById(R.id.white_balance_seekbar);
            white_balance_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
            final int minimum_temperature = preview.getMinimumWhiteBalanceTemperature();
            final int maximum_temperature = preview.getMaximumWhiteBalanceTemperature();
			/*
			// white balance should use linear scaling
			white_balance_seek_bar.setMax(maximum_temperature - minimum_temperature);
			white_balance_seek_bar.setProgress(preview.getCameraController().getWhiteBalanceTemperature() - minimum_temperature);
			*/
            manualSeekbars.setProgressSeekbarWhiteBalance(white_balance_seek_bar, minimum_temperature, maximum_temperature, preview.getCameraController().getWhiteBalanceTemperature());
            white_balance_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                private long last_haptic_time;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "white balance seekbar onProgressChanged: " + progress);
                    //int temperature = minimum_temperature + progress;
                    //preview.setWhiteBalanceTemperature(temperature);
                    preview.setWhiteBalanceTemperature( manualSeekbars.getWhiteBalanceTemperature(progress) );
                    if( fromUser ) {
                        last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    public boolean supportsAutoStabilise() {
        if( applicationInterface.isRawOnly() )
            return false; // if not saving JPEGs, no point having auto-stabilise mode, as it won't affect the RAW images
        if( applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama )
            return false; // not supported in panorama mode
        return this.supports_auto_stabilise;
    }

    /** Returns whether the device supports auto-level at all. Most callers probably want to use
     *  supportsAutoStabilise() which also checks whether auto-level is allowed with current options.
     */
    public boolean deviceSupportsAutoStabilise() {
        return this.supports_auto_stabilise;
    }

    public boolean supportsDRO() {
        if( applicationInterface.isRawOnly(MyApplicationInterface.PhotoMode.DRO) )
            return false; // if not saving JPEGs, no point having DRO mode, as it won't affect the RAW images
        return true;
    }

    public boolean supportsHDR() {
        // we also require the device have sufficient memory to do the processing
        return large_heap_memory >= 128 && preview.supportsExpoBracketing();
    }

    public boolean supportsExpoBracketing() {
        if( applicationInterface.isImageCaptureIntent() )
            return false; // don't support expo bracketing mode if called from image capture intent
        return preview.supportsExpoBracketing();
    }

    public boolean supportsFocusBracketing() {
        if( applicationInterface.isImageCaptureIntent() )
            return false; // don't support focus bracketing mode if called from image capture intent
        return preview.supportsFocusBracketing();
    }

    /** Whether we support the auto mode for setting source focus distance for focus bracketing mode.
     *  Note the caller should still separately call supportsFocusBracketing() to see if focus
     *  bracketing is supported in the first place.
     */
    public boolean supportsFocusBracketingSourceAuto() {
        return preview.supportsFocus() && preview.getSupportedFocusValues().contains("focus_mode_continuous_picture");
    }

    public boolean supportsPanorama() {
        // don't support panorama mode if called from image capture intent
        // in theory this works, but problem that currently we'd end up doing the processing on the UI thread, so risk ANR
        if( applicationInterface.isImageCaptureIntent() )
            return false;
        // require 256MB just to be safe, due to the large number of images that may be created
        // remember to update the FAQ "Why isn't Panorama supported on my device?" if this changes
        return large_heap_memory >= 256 && applicationInterface.getGyroSensor().hasSensors();
        //return false; // currently blocked for release
    }

    public boolean supportsFastBurst() {
        if( applicationInterface.isImageCaptureIntent() )
            return false; // don't support burst mode if called from image capture intent
        // require 512MB just to be safe, due to the large number of images that may be created
        return( preview.usingCamera2API() && large_heap_memory >= 512 && preview.supportsBurst() );
    }

    public boolean supportsNoiseReduction() {
        // we require Android 7 to limit to more modern devices (for performance reasons)
        return( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && preview.usingCamera2API() && large_heap_memory >= 512 && preview.supportsBurst() && preview.supportsExposureTime() );
        //return false; // currently blocked for release
    }

    /** Whether the Camera vendor extension is supported (see
     * https://developer.android.com/reference/android/hardware/camera2/CameraExtensionCharacteristics ).
     */
    public boolean supportsCameraExtension(int extension) {
        return preview.supportsCameraExtension(extension);
    }

    /** Whether RAW mode would be supported for various burst modes (expo bracketing etc).
     *  Note that caller should still separately check preview.supportsRaw() if required.
     */
    public boolean supportsBurstRaw() {
        return( large_heap_memory >= 512 );
    }

    public boolean supportsOptimiseFocusLatency() {
        // whether to support optimising focus for latency
        // in theory this works on any device, as well as old or Camera2 API, but restricting this for now to avoid risk of poor default behaviour
        // on older devices
        return( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && preview.usingCamera2API() );
    }

    public boolean supportsPreviewBitmaps() {
        // In practice we only use TextureView on Android 5+ (with Camera2 API enabled) anyway, but have put an explicit check here -
        return preview.getView() instanceof TextureView && large_heap_memory >= 128;
    }

    public boolean supportsPreShots() {
        // Need at least Android 5+ for TextureView
        // Need at least Android 8+ for video encoding classes
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && preview.getView() instanceof TextureView && large_heap_memory >= 512;
    }

    private int maxExpoBracketingNImages() {
        return preview.maxExpoBracketingNImages();
    }

    public boolean supportsForceVideo4K() {
        return this.supports_force_video_4k;
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    private void disableForceVideo4K() {
        this.supports_force_video_4k = false;
    }

    public Preview getPreview() {
        return this.preview;
    }

    public boolean isCameraInBackground() {
        return this.camera_in_background;
    }

    public boolean isAppPaused() {
        return this.app_is_paused;
    }

    public BluetoothRemoteControl getBluetoothRemoteControl() {
        return bluetoothRemoteControl;
    }

    public PermissionHandler getPermissionHandler() {
        return permissionHandler;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public MainUI getMainUI() {
        return this.mainUI;
    }

    public ManualSeekbars getManualSeekbars() {
        return this.manualSeekbars;
    }

    public MyApplicationInterface getApplicationInterface() {
        return this.applicationInterface;
    }

    public TextFormatter getTextFormatter() {
        return this.textFormatter;
    }

    SoundPoolManager getSoundPoolManager() {
        return this.soundPoolManager;
    }

    public LocationSupplier getLocationSupplier() {
        return this.applicationInterface.getLocationSupplier();
    }

    public StorageUtils getStorageUtils() {
        return this.applicationInterface.getStorageUtils();
    }

    public File getImageFolder() {
        return this.applicationInterface.getStorageUtils().getImageFolder();
    }

    public ToastBoxer getChangedAutoStabiliseToastBoxer() {
        return changed_auto_stabilise_toast;
    }

    private String getPhotoModeString(MyApplicationInterface.PhotoMode photo_mode, boolean string_for_std) {
        String photo_mode_string = null;
        switch( photo_mode ) {
            case Standard:
                if( string_for_std )
                    photo_mode_string = getResources().getString(R.string.photo_mode_standard_full);
                break;
            case DRO:
                photo_mode_string = getResources().getString(R.string.photo_mode_dro);
                break;
            case HDR:
                photo_mode_string = getResources().getString(R.string.photo_mode_hdr);
                break;
            case ExpoBracketing:
                photo_mode_string = getResources().getString(R.string.photo_mode_expo_bracketing_full);
                break;
            case FocusBracketing: {
                photo_mode_string = getResources().getString(R.string.photo_mode_focus_bracketing_full);
                int n_images = applicationInterface.getFocusBracketingNImagesPref();
                photo_mode_string += " (" + n_images + ")";
                break;
            }
            case FastBurst: {
                photo_mode_string = getResources().getString(R.string.photo_mode_fast_burst_full);
                int n_images = applicationInterface.getBurstNImages();
                photo_mode_string += " (" + n_images + ")";
                break;
            }
            case NoiseReduction:
                photo_mode_string = getResources().getString(R.string.photo_mode_noise_reduction_full);
                break;
            case Panorama:
                photo_mode_string = getResources().getString(R.string.photo_mode_panorama_full);
                break;
            case X_Auto:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_auto_full);
                break;
            case X_HDR:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_hdr_full);
                break;
            case X_Night:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_night_full);
                break;
            case X_Bokeh:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_bokeh_full);
                break;
            case X_Beauty:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_beauty_full);
                break;
        }
        return photo_mode_string;
    }

    /** Displays a toast with information about the current preferences.
     *  If always_show is true, the toast is always displayed; otherwise, we only display
     *  a toast if it's important to notify the user (i.e., unusual non-default settings are
     *  set). We want a balance between not pestering the user too much, whilst also reminding
     *  them if certain settings are on.
     */
    private void showPhotoVideoToast(boolean always_show) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "showPhotoVideoToast");
            Log.d(TAG, "always_show? " + always_show);
        }
        CameraController camera_controller = preview.getCameraController();
        if( camera_controller == null || this.camera_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "camera not open or in background");
            return;
        }
        String toast_string;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean simple = true;
        boolean video_high_speed = preview.isVideoHighSpeed();
        MyApplicationInterface.PhotoMode photo_mode = applicationInterface.getPhotoMode();
        if( preview.isVideo() ) {
            VideoProfile profile = preview.getVideoProfile();

            String extension_string = profile.fileExtension;
            if( !profile.fileExtension.equals("mp4") ) {
                simple = false;
            }

            String bitrate_string;
            if( profile.videoBitRate >= 10000000 )
                bitrate_string = profile.videoBitRate/1000000 + "Mbps";
            else if( profile.videoBitRate >= 10000 )
                bitrate_string = profile.videoBitRate/1000 + "Kbps";
            else
                bitrate_string = profile.videoBitRate + "bps";
            String bitrate_value = applicationInterface.getVideoBitratePref();
            if( !bitrate_value.equals("default") ) {
                simple = false;
            }

            double capture_rate = profile.videoCaptureRate;
            String capture_rate_string = (capture_rate < 9.5f) ? new DecimalFormat("#0.###").format(capture_rate) : String.valueOf((int) (profile.videoCaptureRate + 0.5));
            toast_string = getResources().getString(R.string.video) + ": " + profile.videoFrameWidth + "x" + profile.videoFrameHeight + "\n" +
                    capture_rate_string + getResources().getString(R.string.fps) + (video_high_speed ? " [" + getResources().getString(R.string.high_speed) + "]" : "") + ", " + bitrate_string + " (" + extension_string + ")";

            String fps_value = applicationInterface.getVideoFPSPref();
            if( !fps_value.equals("default") || video_high_speed ) {
                simple = false;
            }

            float capture_rate_factor = applicationInterface.getVideoCaptureRateFactor();
            if( Math.abs(capture_rate_factor - 1.0f) > 1.0e-5 ) {
                toast_string += "\n" + getResources().getString(R.string.preference_video_capture_rate) + ": " + capture_rate_factor + "x";
                simple = false;
            }

            {
                CameraController.TonemapProfile tonemap_profile = applicationInterface.getVideoTonemapProfile();
                if( tonemap_profile != CameraController.TonemapProfile.TONEMAPPROFILE_OFF && preview.supportsTonemapCurve() ) {
                    if( applicationInterface.getVideoTonemapProfile() != CameraController.TonemapProfile.TONEMAPPROFILE_OFF && preview.supportsTonemapCurve() ) {
                        int string_id = 0;
                        switch( tonemap_profile ) {
                            case TONEMAPPROFILE_REC709:
                                string_id = R.string.preference_video_rec709;
                                break;
                            case TONEMAPPROFILE_SRGB:
                                string_id = R.string.preference_video_srgb;
                                break;
                            case TONEMAPPROFILE_LOG:
                                string_id = R.string.video_log;
                                break;
                            case TONEMAPPROFILE_GAMMA:
                                string_id = R.string.preference_video_gamma;
                                break;
                            case TONEMAPPROFILE_JTVIDEO:
                                string_id = R.string.preference_video_jtvideo;
                                break;
                            case TONEMAPPROFILE_JTLOG:
                                string_id = R.string.preference_video_jtlog;
                                break;
                            case TONEMAPPROFILE_JTLOG2:
                                string_id = R.string.preference_video_jtlog2;
                                break;
                        }
                        if( string_id != 0 ) {
                            simple = false;
                            toast_string += "\n" + getResources().getString(string_id);
                            if( tonemap_profile == CameraController.TonemapProfile.TONEMAPPROFILE_GAMMA ) {
                                toast_string += " " + applicationInterface.getVideoProfileGamma();
                            }
                        }
                        else {
                            Log.e(TAG, "unknown tonemap_profile: " + tonemap_profile);
                        }
                    }
                }
            }

            boolean record_audio = applicationInterface.getRecordAudioPref();
            if( !record_audio ) {
                toast_string += "\n" + getResources().getString(R.string.audio_disabled);
                simple = false;
            }
            String max_duration_value = sharedPreferences.getString(PreferenceKeys.VideoMaxDurationPreferenceKey, "0");
            if( !max_duration_value.isEmpty() && !max_duration_value.equals("0") ) {
                String [] entries_array = getResources().getStringArray(R.array.preference_video_max_duration_entries);
                String [] values_array = getResources().getStringArray(R.array.preference_video_max_duration_values);
                int index = Arrays.asList(values_array).indexOf(max_duration_value);
                if( index != -1 ) { // just in case!
                    String entry = entries_array[index];
                    toast_string += "\n" + getResources().getString(R.string.max_duration) +": " + entry;
                    simple = false;
                }
            }
            long max_filesize = applicationInterface.getVideoMaxFileSizeUserPref();
            if( max_filesize != 0 ) {
                toast_string += "\n" + getResources().getString(R.string.max_filesize) +": ";
                if( max_filesize >= 1024*1024*1024 ) {
                    long max_filesize_gb = max_filesize/(1024*1024*1024);
                    toast_string += max_filesize_gb + getResources().getString(R.string.gb_abbreviation);
                }
                else {
                    long max_filesize_mb = max_filesize/(1024*1024);
                    toast_string += max_filesize_mb + getResources().getString(R.string.mb_abbreviation);
                }
                simple = false;
            }
            if( applicationInterface.getVideoFlashPref() && preview.supportsFlash() ) {
                toast_string += "\n" + getResources().getString(R.string.preference_video_flash);
                simple = false;
            }
        }
        else {
            if( photo_mode == MyApplicationInterface.PhotoMode.Panorama ) {
                // don't show resolution in panorama mode
                toast_string = "";
            }
            else {
                toast_string = getResources().getString(R.string.photo);
                CameraController.Size current_size = preview.getCurrentPictureSize();
                toast_string += " " + current_size.width + "x" + current_size.height;
            }

            String photo_mode_string = getPhotoModeString(photo_mode, false);
            if( photo_mode_string != null ) {
                toast_string += (toast_string.isEmpty() ? "" : "\n") + getResources().getString(R.string.photo_mode) + ": " + photo_mode_string;
                if( photo_mode != MyApplicationInterface.PhotoMode.DRO && photo_mode != MyApplicationInterface.PhotoMode.HDR && photo_mode != MyApplicationInterface.PhotoMode.NoiseReduction )
                    simple = false;
            }

            if( preview.supportsFocus() && preview.getSupportedFocusValues().size() > 1 && photo_mode != MyApplicationInterface.PhotoMode.FocusBracketing ) {
                String focus_value = preview.getCurrentFocusValue();
                if( focus_value != null && !focus_value.equals("focus_mode_auto") && !focus_value.equals("focus_mode_continuous_picture") ) {
                    String focus_entry = preview.findFocusEntryForValue(focus_value);
                    if( focus_entry != null ) {
                        toast_string += "\n" + focus_entry;
                    }
                }
            }

            if( applicationInterface.getAutoStabilisePref() ) {
                // important as users are sometimes confused at the behaviour if they don't realise the option is on
                toast_string += (toast_string.isEmpty() ? "" : "\n") + getResources().getString(R.string.preference_auto_stabilise);
                simple = false;
            }
        }
        if( applicationInterface.getFaceDetectionPref() ) {
            // important so that the user realises why touching for focus/metering areas won't work - easy to forget that face detection has been turned on!
            toast_string += "\n" + getResources().getString(R.string.preference_face_detection);
            simple = false;
        }
        if( !video_high_speed ) {
            //manual ISO only supported for high speed video
            String iso_value = applicationInterface.getISOPref();
            if( !iso_value.equals(CameraController.ISO_DEFAULT) ) {
                toast_string += "\nISO: " + iso_value;
                if( preview.supportsExposureTime() ) {
                    long exposure_time_value = applicationInterface.getExposureTimePref();
                    toast_string += " " + preview.getExposureTimeString(exposure_time_value);
                }
                simple = false;
            }
            int current_exposure = camera_controller.getExposureCompensation();
            if( current_exposure != 0 ) {
                toast_string += "\n" + preview.getExposureCompensationString(current_exposure);
                simple = false;
            }
        }
        try {
            String scene_mode = camera_controller.getSceneMode();
            String white_balance = camera_controller.getWhiteBalance();
            String color_effect = camera_controller.getColorEffect();
            if( scene_mode != null && !scene_mode.equals(CameraController.SCENE_MODE_DEFAULT) ) {
                toast_string += "\n" + getResources().getString(R.string.scene_mode) + ": " + mainUI.getEntryForSceneMode(scene_mode);
                simple = false;
            }
            if( white_balance != null && !white_balance.equals(CameraController.WHITE_BALANCE_DEFAULT) ) {
                toast_string += "\n" + getResources().getString(R.string.white_balance) + ": " + mainUI.getEntryForWhiteBalance(white_balance);
                if( white_balance.equals("manual") && preview.supportsWhiteBalanceTemperature() ) {
                    toast_string += " " + camera_controller.getWhiteBalanceTemperature();
                }
                simple = false;
            }
            if( color_effect != null && !color_effect.equals(CameraController.COLOR_EFFECT_DEFAULT) ) {
                toast_string += "\n" + getResources().getString(R.string.color_effect) + ": " + mainUI.getEntryForColorEffect(color_effect);
                simple = false;
            }
        }
        catch(RuntimeException e) {
            // catch runtime error from camera_controller old API from camera.getParameters()
            MyDebug.logStackTrace(TAG, "failed to get info from camera controller", e);
        }
        String lock_orientation = applicationInterface.getLockOrientationPref();
        if( !lock_orientation.equals("none") && photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
            // panorama locks to portrait, but don't want to display that in the toast
            String [] entries_array = getResources().getStringArray(R.array.preference_lock_orientation_entries);
            String [] values_array = getResources().getStringArray(R.array.preference_lock_orientation_values);
            int index = Arrays.asList(values_array).indexOf(lock_orientation);
            if( index != -1 ) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + entry;
                simple = false;
            }
        }
        String timer = sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0");
        if( !timer.equals("0") && photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
            String [] entries_array = getResources().getStringArray(R.array.preference_timer_entries);
            String [] values_array = getResources().getStringArray(R.array.preference_timer_values);
            int index = Arrays.asList(values_array).indexOf(timer);
            if( index != -1 ) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + getResources().getString(R.string.preference_timer) + ": " + entry;
                simple = false;
            }
        }
        String repeat = applicationInterface.getRepeatPref();
        if( !repeat.equals("1") ) {
            String [] entries_array = getResources().getStringArray(R.array.preference_burst_mode_entries);
            String [] values_array = getResources().getStringArray(R.array.preference_burst_mode_values);
            int index = Arrays.asList(values_array).indexOf(repeat);
            if( index != -1 ) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + getResources().getString(R.string.preference_burst_mode) + ": " + entry;
                simple = false;
            }
        }
		/*if( audio_listener != null ) {
			toast_string += "\n" + getResources().getString(R.string.preference_audio_noise_control);
		}*/

        if( MyDebug.LOG ) {
            Log.d(TAG, "toast_string: " + toast_string);
            Log.d(TAG, "simple?: " + simple);
            Log.d(TAG, "push_info_toast_text: " + push_info_toast_text);
        }
        final boolean use_fake_toast = true;
        if( !simple || always_show ) {
            if( push_info_toast_text != null ) {
                toast_string = push_info_toast_text + "\n" + toast_string;
            }
            preview.showToast(switch_video_toast, toast_string, use_fake_toast);
        }
        else if( push_info_toast_text != null ) {
            preview.showToast(switch_video_toast, push_info_toast_text, use_fake_toast);
        }
        push_info_toast_text = null; // reset
    }

    private void freeAudioListener(boolean wait_until_done) {
        if( MyDebug.LOG )
            Log.d(TAG, "freeAudioListener");
        if( audio_listener != null ) {
            audio_listener.release(wait_until_done);
            audio_listener = null;
        }
        mainUI.audioControlStopped();
    }

    private void startAudioListener() {
        if( MyDebug.LOG )
            Log.d(TAG, "startAudioListener");
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
            if( MyDebug.LOG )
                Log.d(TAG, "check for record audio permission");
            if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "record audio permission not available");
                applicationInterface.requestRecordAudioPermission();
                return;
            }
        }

        MyAudioTriggerListenerCallback callback = new MyAudioTriggerListenerCallback(this);
        audio_listener = new AudioListener(callback);
        if( audio_listener.status() ) {
            preview.showToast(audio_control_toast, R.string.audio_listener_started, true);

            audio_listener.start();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String sensitivity_pref = sharedPreferences.getString(PreferenceKeys.AudioNoiseControlSensitivityPreferenceKey, "0");
            int audio_noise_sensitivity;
            switch(sensitivity_pref) {
                case "3":
                    audio_noise_sensitivity = 50;
                    break;
                case "2":
                    audio_noise_sensitivity = 75;
                    break;
                case "1":
                    audio_noise_sensitivity = 125;
                    break;
                case "-1":
                    audio_noise_sensitivity = 150;
                    break;
                case "-2":
                    audio_noise_sensitivity = 200;
                    break;
                case "-3":
                    audio_noise_sensitivity = 400;
                    break;
                default:
                    // default
                    audio_noise_sensitivity = 100;
                    break;
            }
            callback.setAudioNoiseSensitivity(audio_noise_sensitivity);
            mainUI.audioControlStarted();
        }
        else {
            audio_listener.release(true); // shouldn't be needed, but just to be safe
            audio_listener = null;
            preview.showToast(null, R.string.audio_listener_failed);
        }
    }

    public boolean hasAudioControl() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String audio_control = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none");
        /*if( audio_control.equals("voice") ) {
            return speechControl.hasSpeechRecognition();
        }
        else*/ if( audio_control.equals("noise") ) {
            return true;
        }
        return false;
    }

	/*void startAudioListeners() {
		initAudioListener();
		// no need to restart speech recognizer, as we didn't free it in stopAudioListeners(), and it's controlled by a user button
	}*/

    public void stopAudioListeners() {
        freeAudioListener(true);
        /*if( speechControl.hasSpeechRecognition() ) {
            // no need to free the speech recognizer, just stop it
            speechControl.stopListening();
        }*/
    }

    public void initLocation() {
        if( MyDebug.LOG )
            Log.d(TAG, "initLocation");
        if( app_is_paused ) {
            if( MyDebug.LOG )
                Log.d(TAG, "initLocation: app is paused!");
            // we shouldn't need this (as we only call initLocation() when active), but just in case we end up here after onPause...
            // in fact this happens when we need to grant permission for location - the call to initLocation() from
            // MainActivity.onRequestPermissionsResult()->PermissionsHandler.onRequestPermissionsResult() will be when the application
            // is still paused - so we won't do anything here, but instead initLocation() will be called after when resuming.
        }
        else if( camera_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "initLocation: camera in background!");
            // we will end up here if app is pause/resumed when camera in background (settings, dialog, etc)
        }
        else if( !applicationInterface.getLocationSupplier().setupLocationListener() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "location permission not available, so request permission");
            permissionHandler.requestLocationPermission();
        }
    }

    private void initGyroSensors() {
        if( MyDebug.LOG )
            Log.d(TAG, "initGyroSensors");
        if( applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama ) {
            applicationInterface.getGyroSensor().enableSensors();
        }
        else {
            applicationInterface.getGyroSensor().disableSensors();
        }
    }

    void speak(String text) {
        if( textToSpeech != null && textToSpeechSuccess ) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if( MyDebug.LOG )
            Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHandler.onRequestPermissionsResult(requestCode, grantResults);
    }

    public void restartOpenCamera() {
        if( MyDebug.LOG )
            Log.d(TAG, "restartOpenCamera");
        this.waitUntilImageQueueEmpty();
        // see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
        Intent intent = this.getBaseContext().getPackageManager().getLaunchIntentForPackage( this.getBaseContext().getPackageName() );
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.startActivity(intent);
    }

    public void takePhotoButtonLongClickCancelled() {
        if( MyDebug.LOG )
            Log.d(TAG, "takePhotoButtonLongClickCancelled");
        if( preview.getCameraController() != null && preview.getCameraController().isContinuousBurstInProgress() ) {
            preview.getCameraController().stopContinuousBurst();
        }
    }

    // for testing:
    public SaveLocationHistory getSaveLocationHistory() {
        return this.save_location_history;
    }

    public SaveLocationHistory getSaveLocationHistorySAF() {
        return this.save_location_history_saf;
    }

    public void usedFolderPicker() {
        if( applicationInterface.getStorageUtils().isUsingSAF() ) {
            save_location_history_saf.updateFolderHistory(getStorageUtils().getSaveLocationSAF(), true);
        }
        else {
            save_location_history.updateFolderHistory(getStorageUtils().getSaveLocation(), true);
        }
    }

    public boolean hasThumbnailAnimation() {
        return this.applicationInterface.hasThumbnailAnimation();
    }

    /*public boolean testHasNotification() {
        return has_notification;
    }*/
}
