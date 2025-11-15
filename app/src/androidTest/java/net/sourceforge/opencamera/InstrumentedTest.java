package net.sourceforge.opencamera;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.*;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.ui.DrawPreview;
import net.sourceforge.opencamera.ui.PopupView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

interface MainTests {}

interface PhotoTests {}

interface VideoTests {}

interface HDRTests {}

interface HDRNTests {}

interface AvgTests {}

interface PanoramaTests {}

// ignore warning about "Call to Thread.sleep in a loop", this is only test code
/** @noinspection BusyWait*/
@RunWith(AndroidJUnit4.class)
public class InstrumentedTest {

    private static final String TAG = "InstrumentedTest";

    static final Intent intent;
    static {
        // used for code to run before the activity is started
        intent = new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class);
        TestUtils.setDefaultIntent(intent);
        intent.putExtra("test_project_junit4", true);

        // need to run this here, not in before(), so it's run before activity is created (otherwise Camera2 won't be enabled)
        TestUtils.initTest(ApplicationProvider.getApplicationContext());
    }

    @Rule
    //public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(MainActivity.class);
    //public ActivityScenarioRule<MainActivity> mActivityRule = new ActivityScenarioRule<>(MainActivity.class);
    public final ActivityScenarioRule<MainActivity> mActivityRule = new ActivityScenarioRule<>(intent);

    /** This is run before each test, but after the activity is started (unlike MainActivityTest.setUp() which
     *  is run before the activity is started).
     */
    @Before
    public void before() throws InterruptedException {
        Log.d(TAG, "before");

        // don't run TestUtils.initTest() here - instead we do it in the static code block, and then
        // after each test

        // the following was true for MainActivityTest (using ActivityInstrumentationTestCase2), unclear if it's true for
        // InstrumentedTest:
        // don't waitUntilCameraOpened() here, as if an assertion fails in setUp(), it can cause later tests to hang in the suite
        // instead we now wait for camera to open in setToDefault()
        //waitUntilCameraOpened();
    }

    @After
    public void after() throws InterruptedException {
        Log.d(TAG, "after");

        // As noted above, we need to call TestUtils.initTest() before the activity starts (so in
        // the static code block, and not before()). But we should still call initTest() before every
        // subsequent test (so that settings are reset, and test static variables are reset), so
        // easiest to do this after each test. This also means the application is left in a default
        // state after running tests.
        mActivityRule.getScenario().onActivity(activity -> {
            Log.d(TAG, "after: init");
            TestUtils.initTest(activity);
        });

        Log.d(TAG, "after done");
    }

    private interface GetActivityValueCallback<T> {
        T get(MainActivity activity);
    }

    /** This helper method simplifies getting data from the MainActivity.
     *  We can't call MainActivity classes directly, but instead have to go via
     *  mActivityRule.getScenario().onActivity().
     */
    private <T> T getActivityValue(GetActivityValueCallback<T> cb) {
        AtomicReference<T> resultRef = new AtomicReference<>();
        mActivityRule.getScenario().onActivity(activity -> resultRef.set( cb.get(activity) ));
        return resultRef.get();
    }

    private void waitUntilCameraOpened() {
        waitUntilCameraOpened(true);
    }

    private void waitUntilCameraOpened(boolean wait_for_preview) {
        Log.d(TAG, "wait until camera opened");
        long time_s = System.currentTimeMillis();

        boolean done = false;
        while( !done ) {
            assertTrue( System.currentTimeMillis() - time_s < 20000 );
            done = getActivityValue(activity -> activity.getPreview().openCameraAttempted());
        }

        Log.d(TAG, "camera is open!");

        try {
            Thread.sleep(100); // sleep a bit just to be safe
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }

        if( wait_for_preview ) {
            waitUntilPreviewStarted(); // needed for Camera2 API when starting preview on background thread and not waiting for it to start
        }
    }

    private void waitUntilPreviewStarted() {
        Log.d(TAG, "wait until preview started");
        long time_s = System.currentTimeMillis();

        boolean done = false;
        while( !done ) {
            assertTrue( System.currentTimeMillis() - time_s < 20000 );
            done = getActivityValue(activity -> activity.getPreview().isPreviewStarted());
        }

        Log.d(TAG, "preview is started!");

        try {
            Thread.sleep(100); // sleep a bit just to be safe
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }
    }

    private void waitUntilTimer() {
        Log.d(TAG, "wait until timer stopped");
        boolean done = false;
        while( !done ) {
            done = getActivityValue(activity -> !activity.getPreview().isOnTimer());
        }
    }

    private void restart() {
        restart(true);
    }

    private void restart(boolean wait_for_preview) {
        Log.d(TAG, "restart");
        mActivityRule.getScenario().recreate();
        waitUntilCameraOpened(wait_for_preview);
        Log.d(TAG, "restart done");
    }

    private void pauseAndResume() {
        Log.d(TAG, "pauseAndResume");
        boolean camera_is_open = getActivityValue(activity -> activity.getPreview().getCameraController() != null);
        pauseAndResume(camera_is_open);
    }

    private void pauseAndResume(boolean wait_until_camera_opened) {
        Log.d(TAG, "pauseAndResume: " + wait_until_camera_opened);

        mActivityRule.getScenario().onActivity(activity -> {
            Log.d(TAG, "pause...");
            getInstrumentation().callActivityOnPause(activity);
            Log.d(TAG, "resume...");
            getInstrumentation().callActivityOnResume(activity);
        });
        if( wait_until_camera_opened ) {
            waitUntilCameraOpened();
        }
    }

    private void updateForSettings() {
        Log.d(TAG, "updateForSettings");
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(Looper.getMainLooper().getThread(), Thread.currentThread()); // check on UI thread
            // updateForSettings has code that must run on UI thread
            activity.initLocation(); // initLocation now called via MainActivity.setWindowFlagsForCamera() rather than updateForSettings()
            activity.getApplicationInterface().getDrawPreview().updateSettings();
            activity.updateForSettings(true);
        });

        waitUntilCameraOpened(); // may need to wait if camera is reopened, e.g., when changing scene mode - see testSceneMode()
        // but we also need to wait for the delay if instead we've stopped and restarted the preview, the latter now only happens after dim_effect_time_c
        try {
            Thread.sleep(DrawPreview.dim_effect_time_c+50); // wait for updateForSettings
        }
        catch(InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }
    }

    /** Used to click when we have View instead of an Id. It should only be called from onActivity()
     *  (so that we can be sure we're already on the UI thread).
     */
    private void clickView(final View view) {
        Log.d(TAG, "clickView: "+ view);
        assertEquals(Looper.getMainLooper().getThread(), Thread.currentThread()); // check on UI thread
        assertEquals(view.getVisibility(), View.VISIBLE);
        assertTrue(view.performClick());
    }

    private void openPopupMenu() {
        Log.d(TAG, "openPopupMenu");
        assertFalse( getActivityValue(MainActivity::popupIsOpen) );
        onView(withId(R.id.popup)).check(matches(isDisplayed()));
        onView(withId(R.id.popup)).perform(click());

        Log.d(TAG, "wait for popup to open");

        boolean done = false;
        while( !done ) {
            done = getActivityValue(MainActivity::popupIsOpen);
        }
        Log.d(TAG, "popup is now open");
    }

    private void switchToFlashValue(String required_flash_value) {
        Log.d(TAG, "switchToFlashValue: "+ required_flash_value);
        boolean supports_flash = getActivityValue(activity -> activity.getPreview().supportsFlash());
        if( supports_flash ) {
            String flash_value = getActivityValue(activity -> activity.getPreview().getCurrentFlashValue());
            Log.d(TAG, "start flash_value: "+ flash_value);
            if( !flash_value.equals(required_flash_value) ) {

                openPopupMenu();

                String flash_value_f = flash_value;
                mActivityRule.getScenario().onActivity(activity -> {
                    View currentFlashButton = activity.getUIButton("TEST_FLASH_" + flash_value_f);
                    assertNotNull(currentFlashButton);
                    assertEquals(currentFlashButton.getAlpha(), PopupView.ALPHA_BUTTON_SELECTED, 1.0e-5);
                    View flashButton = activity.getUIButton("TEST_FLASH_" + required_flash_value);
                    assertNotNull(flashButton);
                    assertEquals(flashButton.getAlpha(), PopupView.ALPHA_BUTTON, 1.0e-5);
                    clickView(flashButton);
                });

                flash_value = getActivityValue(activity -> activity.getPreview().getCurrentFlashValue());
                Log.d(TAG, "changed flash_value to: "+ flash_value);
            }
            assertEquals(flash_value, required_flash_value);
            String controller_flash_value = getActivityValue(activity -> activity.getPreview().getCameraController().getFlashValue());
            Log.d(TAG, "controller_flash_value: "+ controller_flash_value);
            if( flash_value.equals("flash_frontscreen_auto") || flash_value.equals("flash_frontscreen_on") ) {
                // for frontscreen flash, the controller flash value will be "" (due to real flash not supported) - although on Galaxy Nexus this is "flash_off" due to parameters.getFlashMode() returning Camera.Parameters.FLASH_MODE_OFF
                assertTrue(controller_flash_value.isEmpty() || controller_flash_value.equals("flash_off"));
            }
            else {
                Log.d(TAG, "expected_flash_value: "+ flash_value);
                assertEquals(flash_value, controller_flash_value);
            }
        }
    }

    private void switchToFocusValue(String required_focus_value) {
        Log.d(TAG, "switchToFocusValue: "+ required_focus_value);
        boolean supports_focus = getActivityValue(activity -> activity.getPreview().supportsFocus());
        if( supports_focus ) {
            String focus_value = getActivityValue(activity -> activity.getPreview().getCurrentFocusValue());
            Log.d(TAG, "start focus_value: "+ focus_value);
            if( !focus_value.equals(required_focus_value) ) {

                openPopupMenu();

                mActivityRule.getScenario().onActivity(activity -> {
                    View focusButton = activity.getUIButton("TEST_FOCUS_" + required_focus_value);
                    assertNotNull(focusButton);
                    clickView(focusButton);
                });

                focus_value = getActivityValue(activity -> activity.getPreview().getCurrentFocusValue());
                Log.d(TAG, "changed focus_value to: "+ focus_value);
            }
            assertEquals(focus_value, required_focus_value);
            String controller_focus_value = getActivityValue(activity -> activity.getPreview().getCameraController().getFocusValue());
            Log.d(TAG, "controller_focus_value: "+ controller_focus_value);
            boolean using_camera2 = getActivityValue(activity -> activity.getPreview().usingCamera2API());
            String compare_focus_value = focus_value;
            if( compare_focus_value.equals("focus_mode_locked") )
                compare_focus_value = "focus_mode_auto";
            else if( compare_focus_value.equals("focus_mode_infinity") && using_camera2 )
                compare_focus_value = "focus_mode_manual2";
            assertEquals(compare_focus_value, controller_focus_value);
        }
    }

    private void switchToISO(int required_iso) {
        Log.d(TAG, "switchToISO: "+ required_iso);
        int iso = getActivityValue(activity -> activity.getPreview().getCameraController().getISO());
        Log.d(TAG, "start iso: "+ iso);
        if( iso != required_iso ) {
            mActivityRule.getScenario().onActivity(activity -> {
                View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
                View exposureContainer = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_container);
                assertEquals(exposureContainer.getVisibility(), View.GONE);
                clickView(exposureButton);
            });
            mActivityRule.getScenario().onActivity(activity -> {
                View exposureContainer = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_container);
                assertEquals(exposureContainer.getVisibility(), View.VISIBLE);
                View isoButton = activity.getUIButton("TEST_ISO_" + required_iso);
                assertNotNull(isoButton);
                clickView(isoButton);
            });
            try {
                Thread.sleep(DrawPreview.dim_effect_time_c+50); // wait for updateForSettings
                //this.getInstrumentation().waitForIdleSync();
            }
            catch(InterruptedException e) {
                Log.e(TAG, "InterruptedException from sleep", e);
            }
            iso = getActivityValue(activity -> activity.getPreview().getCameraController().getISO());
            Log.d(TAG, "changed iso to: "+ iso);
            mActivityRule.getScenario().onActivity(activity -> {
                View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
                View exposureContainer = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_container);
                clickView(exposureButton);
                assertEquals(exposureContainer.getVisibility(), View.GONE);
            });
        }
        assertEquals(iso, required_iso);
    }

    /* Sets the camera up to a predictable state:
     * - Flash off (if flash supported)
     * - Focus mode picture continuous (if focus modes supported)
     * As a side-effect, the camera and/or camera parameters values may become invalid.
     */
    private void setToDefault() {
        waitUntilCameraOpened();

        assertFalse( getActivityValue(activity -> activity.getPreview().isVideo()) );

        switchToFlashValue("flash_off");
        switchToFocusValue("focus_mode_continuous_picture");

        // pause for safety - needed for Nokia 8 at least otherwise some tests like testContinuousPictureFocusRepeat,
        // testLocationOff result in hang whilst waiting for photo to be taken, and hit the timeout in waitForTakePhoto()
        try {
            Thread.sleep(200);
        }
        catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }
    }

    /*@Test
    public void testDummy() {
        Log.d(TAG, "testDummy");
    }*/

    private static void checkHistogramDetails(TestUtils.HistogramDetails hdrHistogramDetails, int exp_min_value, int exp_median_value, int exp_max_value) {
        TestUtils.checkHistogramDetails(hdrHistogramDetails, exp_min_value, exp_median_value, exp_max_value);
    }

    /** Tests calling the DRO routine with 0.0 factor, and DROALGORITHM_NONE - and that the resultant image is identical.
     */
    @Category(HDRTests.class)
    @Test
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testDROZero() throws IOException, InterruptedException {
        Log.d(TAG, "testDROZero");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            Bitmap bitmap = TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR3/input1.jpg");
            Bitmap bitmap_saved = bitmap.copy(bitmap.getConfig(), false);

            try {
                Thread.sleep(1000); // wait for camera to open
            }
            catch(InterruptedException e) {
                Log.e(TAG, "InterruptedException from sleep", e);
            }

            List<Bitmap> inputs = new ArrayList<>();
            inputs.add(bitmap);
            try {
                activity.getApplicationInterface().getHDRProcessor().processHDR(inputs, true, null, true, null, 0.0f, 4, true, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_NONE);
            }
            catch(HDRProcessorException e) {
                Log.e(TAG, "processHDR failed", e);
                throw new RuntimeException();
            }

            TestUtils.saveBitmap(activity, inputs.get(0), "droZerotestHDR3_output.jpg");
            TestUtils.checkHistogram(activity, bitmap);

            // check bitmaps are the same
            Log.d(TAG, "compare bitmap " + bitmap);
            Log.d(TAG, "with bitmap_saved " + bitmap_saved);
            // sameAs doesn't seem to work
            //assertTrue( bitmap.sameAs(bitmap_saved) );
            assertEquals(bitmap.getWidth(), bitmap_saved.getWidth());
            assertEquals(bitmap.getHeight(), bitmap_saved.getHeight());
            int [] old_row = new int[bitmap.getWidth()];
            int [] new_row = new int[bitmap.getWidth()];
            for(int y=0;y<bitmap.getHeight();y++) {
                //Log.d(TAG, "check row " + y + " / " + bitmap.getHeight());
                bitmap_saved.getPixels(old_row, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
                bitmap.getPixels(new_row, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
                for(int x=0;x<bitmap.getWidth();x++) {
                    //int old_pixel = bitmap_saved.getPixel(x, y);
                    //int new_pixel = bitmap.getPixel(x, y);
                    int old_pixel = old_row[x];
                    int new_pixel = new_row[x];
                    assertEquals(old_pixel, new_pixel);
                }
            }

            bitmap.recycle();
            bitmap_saved.recycle();
            try {
                Thread.sleep(500);
            }
            catch(InterruptedException e) {
                Log.e(TAG, "InterruptedException from sleep", e);
            }
        });
    }

    /** Tests DRO only on a dark image.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Category(HDRTests.class)
    @Test
    public void testDRODark0() throws IOException, InterruptedException {
        Log.d(TAG, "testDRODark0");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.avg_images_path + "testAvg3/input0.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testDRODark0_output.jpg", true, -1, -1);
        });
    }

    /** Tests DRO only on a dark image.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Category(HDRTests.class)
    @Test
    public void testDRODark1() throws IOException, InterruptedException {
        Log.d(TAG, "testDRODark1");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.avg_images_path + "testAvg8/input0.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testDRODark1_output.jpg", true, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "saintpaul".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR1() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR1");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input3.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input4.jpg") );

            // actual ISO unknown, so guessing
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR1_output.jpg", false, 1600, 1000000000L);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
            //checkHistogramDetails(hdrHistogramDetails, 1, 44, 253);
            //checkHistogramDetails(hdrHistogramDetails, 1, 42, 253);
            //checkHistogramDetails(hdrHistogramDetails, 1, 24, 254);
            checkHistogramDetails(hdrHistogramDetails, 2, 30, 254);
        });
    }

    /** Tests HDR algorithm on test samples "saintpaul", but with 5 images.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR1_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR1_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input3.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input4.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "saintpaul/input5.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR1_exp5_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0, 0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 3, 43, 251);
            //checkHistogramDetails(hdrHistogramDetails, 6, 42, 251);
            checkHistogramDetails(hdrHistogramDetails, 6, 49, 252);
        });
    }

    /** Tests HDR algorithm on test samples "stlouis".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "stlouis/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "stlouis/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "stlouis/input3.jpg") );

            // actual ISO unknown, so guessing
            TestUtils.subTestHDR(activity, inputs, "testHDR2_output.jpg", false, 1600, (long)(1000000000L*2.5));

            int [] exp_offsets_x = {0, 0, 2};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR3".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR3() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR3");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread

            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR3/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR3/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR3/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR3_output.jpg", false, 40, 1000000000L/680);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {1, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //TestUtils.checkHistogramDetails(hdrHistogramDetails, 3, 104, 255);
            //TestUtils.checkHistogramDetails(hdrHistogramDetails, 4, 113, 255);
            TestUtils.checkHistogramDetails(hdrHistogramDetails, 8, 113, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR4".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR4() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR4");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread

            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR4/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR4/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR4/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR4_output.jpg", true, 102, 1000000000L/60);

            int [] exp_offsets_x = {-2, 0, 2};
            int [] exp_offsets_y = {-1, 0, 1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR5".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR5/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR5/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR5/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR5_output.jpg", false, 40, 1000000000L/398);

            // Nexus 6:
            //int [] exp_offsets_x = {0, 0, 0};
            //int [] exp_offsets_y = {-1, 0, 0};
            // OnePlus 3T:
            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR6".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR6() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR6");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR6/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR6/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR6/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR6_output.jpg", false, 40, 1000000000L/2458);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {1, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR7".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR7");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR7/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR7/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR7/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR7_output.jpg", false, 40, 1000000000L/538);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR8".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR8() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR8");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR8/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR8/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR8/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR8_output.jpg", false, 40, 1000000000L/148);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR9".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR9() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR9");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR9/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR9/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR9/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR9_output.jpg", false, 40, 1000000000L/1313);

            int [] exp_offsets_x = {-1, 0, 1};
            int [] exp_offsets_y = {0, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR10".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR10() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR10");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR10/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR10/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR10/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR10_output.jpg", false, 107, 1000000000L/120);

            int [] exp_offsets_x = {2, 0, 0};
            int [] exp_offsets_y = {5, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR11".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR11() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR11");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR11/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR11/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR11/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR11_output.jpg", true, 40, 1000000000L/2662);

            int [] exp_offsets_x = {-2, 0, 1};
            int [] exp_offsets_y = {1, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 0, 48, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 65, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 72, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR12".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR12() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR12");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR12/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR12/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR12/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR12_output.jpg", true, 1196, 1000000000L/12);

            int [] exp_offsets_x = {0, 0, 7};
            int [] exp_offsets_y = {0, 0, 8};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR13".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR13() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR13");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR13/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR13/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR13/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR13_output.jpg", false, 323, 1000000000L/24);

            int [] exp_offsets_x = {0, 0, 2};
            int [] exp_offsets_y = {0, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR14".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR14() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR14");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR14/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR14/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR14/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR14_output.jpg", false, 40, 1000000000L/1229);

            int [] exp_offsets_x = {0, 0, 1};
            int [] exp_offsets_y = {0, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR15".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR15() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR15");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR15/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR15/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR15/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR15_output.jpg", false, 40, 1000000000L/767);

            int [] exp_offsets_x = {1, 0, -1};
            int [] exp_offsets_y = {2, 0, -3};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR16".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR16() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR16");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR16/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR16/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR16/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR16_output.jpg", false, 52, 1000000000L/120);

            int [] exp_offsets_x = {-1, 0, 2};
            int [] exp_offsets_y = {1, 0, -6};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR17".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR17() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR17");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR17/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR17/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR17/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR17_output.jpg", true, 557, 1000000000L/12);

            // Nexus 6:
            //int [] exp_offsets_x = {0, 0, -3};
            //int [] exp_offsets_y = {0, 0, -4};
            // OnePlus 3T:
            int [] exp_offsets_x = {0, 0, -2};
            int [] exp_offsets_y = {0, 0, -3};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR18".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR18() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR18");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR18/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR18/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR18/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR18_output.jpg", true, 100, 1000000000L/800);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 1, 113, 254);
            //checkHistogramDetails(hdrHistogramDetails, 1, 119, 255);
            //checkHistogramDetails(hdrHistogramDetails, 5, 120, 255);
            checkHistogramDetails(hdrHistogramDetails, 2, 120, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR19".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR19() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR19");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR19/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR19/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR19/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR19_output.jpg", true, 100, 1000000000L/160);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR20".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR20() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR20");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR20/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR20/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR20/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR20_output.jpg", true, 100, 1000000000L*2);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {-1, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR21".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR21() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR21");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR21/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR21/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR21/input2.jpg") );

            // ISO and exposure unknown, so guessing
            TestUtils.subTestHDR(activity, inputs, "testHDR21_output.jpg", true, 800, 1000000000L/12);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR22".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR22() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR22");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR22/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR22/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR22/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR22_output.jpg", true, 391, 1000000000L/12);

            // Nexus 6:
            //int [] exp_offsets_x = {1, 0, -5};
            //int [] exp_offsets_y = {1, 0, -6};
            // OnePlus 3T:
            int [] exp_offsets_x = {0, 0, -5};
            int [] exp_offsets_y = {1, 0, -6};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 2 images.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR23_exp2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0068.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0064.png") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_exp2_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0};
            int [] exp_offsets_y = {0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 33, 78, 250);
            checkHistogramDetails(hdrHistogramDetails, 17, 75, 250);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 2 images, and greater exposure gap.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR23_exp2b() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp2b");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0070.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0062.png") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_exp2b_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0};
            int [] exp_offsets_y = {0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR23() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0068.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0066.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0064.png") );

            // ISO unknown, so guessing
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_output.jpg", false, 1600, 1000000000L);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 17, 81, 255);
            //checkHistogramDetails(hdrHistogramDetails, 32, 74, 255);
            checkHistogramDetails(hdrHistogramDetails, 23, 71, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 4 images.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR23_exp4() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp4");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0070.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0068.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0064.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0062.png") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_exp4_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 31, 75, 254);
            checkHistogramDetails(hdrHistogramDetails, 23, 74, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 5 images.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR23_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0070.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0068.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0066.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0064.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0062.png") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_exp5_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0, 0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 17, 81, 255);
            //checkHistogramDetails(hdrHistogramDetails, 28, 82, 255);
            //checkHistogramDetails(hdrHistogramDetails, 21, 74, 255);
            checkHistogramDetails(hdrHistogramDetails, 17, 74, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 6 images.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR23_exp6() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp6");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0072.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0070.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0068.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0064.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0062.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0061.png") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_exp6_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0, 0, 0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0, 0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 32, 76, 254);
            checkHistogramDetails(hdrHistogramDetails, 25, 75, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR23", but with 7 images.
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR23_exp7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR23_exp7");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0072.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0070.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0068.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0066.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0064.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0062.png") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR23/memorial0061.png") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR23_exp7_output.jpg", false, -1, -1);

            int [] exp_offsets_x = {0, 0, 0, 0, 0, 0, 0};
            int [] exp_offsets_y = {0, 0, 0, 0, 0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 17, 81, 255);
            //checkHistogramDetails(hdrHistogramDetails, 28, 82, 255);
            checkHistogramDetails(hdrHistogramDetails, 20, 72, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR24".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR24() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR24");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR24/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR24/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR24/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR24_output.jpg", true, 40, 1000000000L/422);

            int [] exp_offsets_x = {0, 0, 1};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR25".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR25() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR25");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR25/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR25/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR25/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR25_output.jpg", true, 40, 1000000000L/1917);

            int [] exp_offsets_x = {0, 0, 0};
            int [] exp_offsets_y = {1, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR26".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR26() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR26");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR26/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR26/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR26/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR26_output.jpg", true, 40, 1000000000L/5325);

            int [] exp_offsets_x = {-1, 0, 1};
            int [] exp_offsets_y = {1, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 0, 104, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 119, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR27".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR27() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR27");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR27/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR27/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR27/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR27_output.jpg", true, 40, 1000000000L/949);

            int [] exp_offsets_x = {0, 0, 2};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR28".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR28() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR28");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR28/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR28/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR28/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR28_output.jpg", true, 294, 1000000000L/20);

            int [] exp_offsets_x = {0, 0, 2};
            int [] exp_offsets_y = {0, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR29".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR29() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR29");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR29/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR29/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR29/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR29_output.jpg", false, 40, 1000000000L/978);

            int [] exp_offsets_x = {-1, 0, 3};
            int [] exp_offsets_y = {0, 0, -1};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR30".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR30() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR30");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR30/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR30/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR30/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR30_output.jpg", false, 40, 1000000000L/978);

            // offsets for full image
            //int [] exp_offsets_x = {-6, 0, -1};
            //int [] exp_offsets_y = {23, 0, -13};
            // offsets using centre quarter image
            int [] exp_offsets_x = {-5, 0, 0};
            int [] exp_offsets_y = {22, 0, -13};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR31".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR31() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR31");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR31/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR31/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR31/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR31_output.jpg", false, 40, 1000000000L/422);

            // offsets for full image
            //int [] exp_offsets_x = {0, 0, 4};
            //int [] exp_offsets_y = {21, 0, -11};
            // offsets using centre quarter image
            int [] exp_offsets_x = {0, 0, 3};
            int [] exp_offsets_y = {21, 0, -11};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR32".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR32() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR32");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR32/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR32/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR32/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR32_output.jpg", true, 40, 1000000000L/1331);

            int [] exp_offsets_x = {1, 0, 0};
            int [] exp_offsets_y = {13, 0, -10};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 3, 101, 251);
            //checkHistogramDetails(hdrHistogramDetails, 3, 109, 251);
            //checkHistogramDetails(hdrHistogramDetails, 6, 111, 252);
            checkHistogramDetails(hdrHistogramDetails, 2, 111, 252);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR33".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR33() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR33");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR33/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR33/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR33/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR33_output.jpg", true, 40, 1000000000L/354);

            int [] exp_offsets_x = {13, 0, -10};
            int [] exp_offsets_y = {24, 0, -12};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR34".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR34() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR34");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR34/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR34/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR34/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR34_output.jpg", true, 40, 1000000000L/4792);

            int [] exp_offsets_x = {5, 0, -8};
            int [] exp_offsets_y = {0, 0, -2};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR35".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR35() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR35");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR35/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR35/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR35/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR35_output.jpg", true, 40, 1000000000L/792);

            int [] exp_offsets_x = {-10, 0, 3};
            int [] exp_offsets_y = {7, 0, -3};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR36".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR36() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR36");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR36/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR36/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR36/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR36_output.jpg", false, 100, 1000000000L/1148);

            int [] exp_offsets_x = {2, 0, -2};
            int [] exp_offsets_y = {-4, 0, 2};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR37".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR37() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR37");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR37/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR37/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR37/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR37_output.jpg", false, 46, 1000000000L/120);

            int [] exp_offsets_x = {0, 0, 3};
            int [] exp_offsets_y = {2, 0, -19};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR38".
     *  Tests with Filmic tonemapping.
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR38Filmic() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR38Filmic");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR38/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR38/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR38/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR38_filmic_output.jpg", false, 125, 1000000000L/2965, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_FU2);

            int [] exp_offsets_x = {-1, 0, 0};
            int [] exp_offsets_y = {0, 0, 0};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            //checkHistogramDetails(hdrHistogramDetails, 0, 92, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 93, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR39".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR39() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR39");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR39/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR39/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR39/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR39_output.jpg", false, 125, 1000000000L/2135);

            int [] exp_offsets_x = {-6, 0, -2};
            int [] exp_offsets_y = {6, 0, -8};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            checkHistogramDetails(hdrHistogramDetails, 0, 128, 222);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR40".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR40() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR40");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR40_output.jpg", false, 50, 1000000000L/262);

            int [] exp_offsets_x = {5, 0, -2};
            int [] exp_offsets_y = {13, 0, 24};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            checkHistogramDetails(hdrHistogramDetails, 1, 138, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR40" with Exponential tonemapping.
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR40Exponential() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR40Exponential");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR40_exponential_output.jpg", false, 50, 1000000000L/262, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_EXPONENTIAL);

            int [] exp_offsets_x = {5, 0, -2};
            int [] exp_offsets_y = {13, 0, 24};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            checkHistogramDetails(hdrHistogramDetails, 1, 138, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR40" with Filmic tonemapping.
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR40Filmic() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR40Filmic");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR40/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR40_filmic_output.jpg", false, 50, 1000000000L/262, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_FU2);

            int [] exp_offsets_x = {5, 0, -2};
            int [] exp_offsets_y = {13, 0, 24};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);

            checkHistogramDetails(hdrHistogramDetails, 1, 130, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR41".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR41() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR41");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR41/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR41/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR41/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR41_output.jpg", false, 925, 1000000000L/25);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR42".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR42() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR42");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR42/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR42/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR42/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR42_output.jpg", false, 112, 1000000000L/679);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR43".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR43() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR43");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR43/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR43/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR43/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR43_output.jpg", false, 1196, 1000000000L/12);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR44".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR44() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR44");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR44/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR44/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR44/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR44_output.jpg", false, 100, 1000000000L/1016);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR45".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR45() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR45");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6314.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6312.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6310.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6309.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6311.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6313.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6315.jpg") );

            // ISO 100, exposure time 2s, but pass in -1 since these are HDRNTests
            TestUtils.subTestHDR(activity, inputs, "testHDR45_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR45".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR45_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR45_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6314.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6312.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6310.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6309.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6311.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6313.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6315.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR45_exp5_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR45".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR45_exp7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR45_exp7");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6314.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6312.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6310.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6309.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6311.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6313.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR45/IMG_6315.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR45_exp7_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR46".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR46() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR46");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 06.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 05.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 04.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 03.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 02.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 01.jpg") );

            // ISO 100, exposure time 1/60s, but pass in -1 since these are HDRNTests
            TestUtils.subTestHDR(activity, inputs, "testHDR46_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR46".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR46_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR46_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 06.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 05.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 04.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 03.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 02.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR46/Izmir Harbor - ppw - 01.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR46_exp5_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR47_exp2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47_exp2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();

            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDR47_exp2_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR47() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();

            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 08.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 07.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 06.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 04.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 02.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 01.jpg") );

            // ISO 400, exposure time 1/60s, but pass in -1 since these are HDRNTests
            TestUtils.subTestHDR(activity, inputs, "testHDR47_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR47_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 08.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 07.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 06.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 04.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 02.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 01.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR47_exp5_output.jpg", false, -1, -1);

            //checkHistogramDetails(hdrHistogramDetails, 1, 73, 255);
            checkHistogramDetails(hdrHistogramDetails, 1, 80, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR47".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR47_exp7() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR47_exp7");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 08.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 07.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 06.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 05.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 04.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 03.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 02.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR47/High Five - ppw - 01.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR47_exp7_output.jpg", false, -1, -1);

            //checkHistogramDetails(hdrHistogramDetails, 1, 73, 255);
            checkHistogramDetails(hdrHistogramDetails, 1, 80, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR48".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR48() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR48");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();

            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input3.jpg") );
            //inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input4.jpg") );

            // ISO 100, exposure time 1/716s, but pass in -1 since these are HDRNTests
            TestUtils.subTestHDR(activity, inputs, "testHDR48_output.jpg", false, -1, -1);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR48".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR48_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR48_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();

            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input3.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR48/input4.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR48_exp5_output.jpg", false, -1, -1);

            //checkHistogramDetails(hdrHistogramDetails, 0, 59, 241);
            checkHistogramDetails(hdrHistogramDetails, 0, 67, 241);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR49_exp2() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49_exp2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input3.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR49_exp2_output.jpg", false, -1, -1);

            //checkHistogramDetails(hdrHistogramDetails, 12, 120, 251);
            checkHistogramDetails(hdrHistogramDetails, 0, 122, 251);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR49() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input3.jpg") );

            // ISO 100, exposure time 1/417s, but pass in -1 since these are HDRNTests
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR49_output.jpg", false, -1, -1);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 89, 254);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR49_exp4() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49_exp4");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input3.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input4.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR49_exp4_output.jpg", false, -1, -1);

            checkHistogramDetails(hdrHistogramDetails, 19, 109, 244);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR49".
     */
    @Category(HDRNTests.class)
    @Test
    public void testHDR49_exp5() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR49_exp5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input2.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input3.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR49/input4.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR49_exp5_output.jpg", false, -1, -1);

            //checkHistogramDetails(hdrHistogramDetails, 0, 72, 244);
            //checkHistogramDetails(hdrHistogramDetails, 0, 78, 243);
            checkHistogramDetails(hdrHistogramDetails, 0, 87, 243);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR50".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR50() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR50");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR50/IMG_20180626_221357_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR50/IMG_20180626_221357_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR50/IMG_20180626_221357_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR50_output.jpg", false, 867, 1000000000L/14);

            checkHistogramDetails(hdrHistogramDetails, 0, 69, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR51".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR51() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR51");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR51/IMG_20180323_104702_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR51/IMG_20180323_104702_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR51/IMG_20180323_104702_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR51_output.jpg", true, 1600, 1000000000L/11);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR52".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR52() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR52");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR52/IMG_20181023_143633_EXP0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR52/IMG_20181023_143633_EXP1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR52/IMG_20181023_143633_EXP2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR52_output.jpg", false, 100, 1000000000L/2105);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR53".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR53() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR53");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR53/IMG_20181106_135411_EXP0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR53/IMG_20181106_135411_EXP1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR53/IMG_20181106_135411_EXP2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR53_output.jpg", false, 103, 1000000000L/5381);

            //checkHistogramDetails(hdrHistogramDetails, 0, 55, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 72, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR54".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR54() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR54");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR54/IMG_20181107_115508_EXP0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR54/IMG_20181107_115508_EXP1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR54/IMG_20181107_115508_EXP2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR54_output.jpg", false, 752, 1000000000L/14);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR55".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR55() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR55");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR55/IMG_20181107_115608_EXP0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR55/IMG_20181107_115608_EXP1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR55/IMG_20181107_115608_EXP2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR55_output.jpg", false, 1505, 1000000000L/10);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR56".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR56() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR56");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR56/180502_141722_OC_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR56/180502_141722_OC_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR56/180502_141722_OC_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR56_output.jpg", false, 50, 1000000000L/40);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR57".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR57() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR57");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR57/IMG_20181119_145313_EXP0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR57/IMG_20181119_145313_EXP1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR57/IMG_20181119_145313_EXP2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR57_output.jpg", true, 100, 1000000000L/204);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR58".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR58() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR58");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR58/IMG_20190911_210146_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR58/IMG_20190911_210146_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR58/IMG_20190911_210146_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR58_output.jpg", false, 1250, 1000000000L/10);
            //HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR58_output.jpg", false, 1250, 1000000000L/10, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_CLAMP);

            checkHistogramDetails(hdrHistogramDetails, 11, 119, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR59".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR59() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR59");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR59/IMG_20190911_210154_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR59/IMG_20190911_210154_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR59/IMG_20190911_210154_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR59_output.jpg", false, 1250, 1000000000L/10);
            //HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR59_output.jpg", false, 1250, 1000000000L/10, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_CLAMP);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR60".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR60() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR60");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR60/IMG_20200507_020319_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR60/IMG_20200507_020319_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR60/IMG_20200507_020319_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR60_output.jpg", false, 491, 1000000000L/10);
            //HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR60_output.jpg", false, 491, 1000000000L/10, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_CLAMP);

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR61".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR61() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR61");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR61/IMG_20191111_145230_0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR61/IMG_20191111_145230_1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR61/IMG_20191111_145230_2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR61_output.jpg", false, 50, 1000000000L/5025);

            checkHistogramDetails(hdrHistogramDetails, 0, 93, 255);

            int [] exp_offsets_x = {0, 0, 1};
            int [] exp_offsets_y = {0, 0, -2};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDR62".
     */
    @Category(HDRTests.class)
    @Test
    public void testHDR62() throws IOException, InterruptedException {
        Log.d(TAG, "testHDR62");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR62/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR62/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDR62/input2.jpg") );

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestHDR(activity, inputs, "testHDR62_output.jpg", false, 100, 1000000000L/485);

            checkHistogramDetails(hdrHistogramDetails, 0, 113, 247);

            int [] exp_offsets_x = {0, 0, -3};
            int [] exp_offsets_y = {3, 0, -6};
            TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y);
        });
    }

    /** Tests HDR algorithm on test samples "testHDRtemp".
     *  Used for one-off testing, or to recreate HDR images from the base exposures to test an updated algorithm.
     *  The test images should be copied to the test device into DCIM/testOpenCamera/testdata/hdrsamples/testHDRtemp/ .
     */
    @Test
    public void testHDRtemp() throws IOException, InterruptedException {
        Log.d(TAG, "testHDRtemp");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<Bitmap> inputs = new ArrayList<>();
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDRtemp/input0.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDRtemp/input1.jpg") );
            inputs.add( TestUtils.getBitmapFromFile(activity, TestUtils.hdr_images_path + "testHDRtemp/input2.jpg") );

            TestUtils.subTestHDR(activity, inputs, "testHDRtemp_output.jpg", true, 100, 1000000000L/100);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg1".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg1() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg1");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg1/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg1/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg1/input2.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity, inputs, "testAvg1_output.jpg", 1600, 1000000000L/17, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //int [] exp_offsets_x = {0, 3, 0};
                        //int [] exp_offsets_y = {0, 1, 0};
                        //int [] exp_offsets_x = {0, 4, 0};
                        //int [] exp_offsets_y = {0, 1, 0};
                        //int [] exp_offsets_x = {0, 2, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        int [] exp_offsets_x = {0, 4, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 2 ) {
                        //int [] exp_offsets_x = {0, 6, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        //int [] exp_offsets_x = {0, 8, 0};
                        //int [] exp_offsets_y = {0, 1, 0};
                        //int [] exp_offsets_x = {0, 7, 0};
                        //int [] exp_offsets_y = {0, -1, 0};
                        //int [] exp_offsets_x = {0, 8, 0};
                        //int [] exp_offsets_y = {0, -4, 0};
                        int [] exp_offsets_x = {0, 8, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg2".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg2() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg2/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg2/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg2/input2.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg2_output.jpg", 1600, 1000000000L/17, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //int [] exp_offsets_x = {0, -15, 0};
                        //int [] exp_offsets_y = {0, -10, 0};
                        //int [] exp_offsets_x = {0, -15, 0};
                        //int [] exp_offsets_y = {0, -11, 0};
                        //int [] exp_offsets_x = {0, -12, 0};
                        //int [] exp_offsets_y = {0, -12, 0};
                        int [] exp_offsets_x = {0, -16, 0};
                        int [] exp_offsets_y = {0, -12, 0};
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 2 ) {
                        //int [] exp_offsets_x = {0, -15, 0};
                        //int [] exp_offsets_y = {0, -10, 0};
                        //int [] exp_offsets_x = {0, -13, 0};
                        //int [] exp_offsets_y = {0, -12, 0};
                        //int [] exp_offsets_x = {0, -12, 0};
                        //int [] exp_offsets_y = {0, -14, 0};
                        int [] exp_offsets_x = {0, -12, 0};
                        int [] exp_offsets_y = {0, -12, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg3".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg3() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg3");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg3/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg3/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg3/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg3/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg3/input4.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg3_output.jpg", 1600, 1000000000L/16, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                /*if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 2, 0};
                    //int [] exp_offsets_y = {0, -18, 0};
                    //int [] exp_offsets_x = {0, -1, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -9, 0};
                    //int [] exp_offsets_y = {0, -11, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, -10, 0};
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, -8, 0};
                    assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, -18, 0};
                    //int [] exp_offsets_y = {0, 17, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -7, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, -8, 0};
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 8, 0};
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, -25, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -9, 0};
                    //int [] exp_offsets_y = {0, 14, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    //int [] exp_offsets_x = {0, -12, 0};
                    //int [] exp_offsets_y = {0, 12, 0};
                    int [] exp_offsets_x = {0, -12, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    //int [] exp_offsets_x = {0, -29, 0};
                    //int [] exp_offsets_y = {0, -22, 0};
                    //int [] exp_offsets_x = {0, -2, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, -7, 0};
                    //int [] exp_offsets_y = {0, 11, 0};
                    //int [] exp_offsets_x = {0, -6, 0};
                    //int [] exp_offsets_y = {0, 14, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 2, 0};
                    //int [] exp_offsets_x = {0, -8, 0};
                    //int [] exp_offsets_y = {0, 12, 0};
                    int [] exp_offsets_x = {0, -8, 0};
                    int [] exp_offsets_y = {0, 4, 0};
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }*/
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 21, 177);
            //checkHistogramDetails(hdrHistogramDetails, 0, 21, 152);
            //checkHistogramDetails(hdrHistogramDetails, 0, 21, 166);
            checkHistogramDetails(hdrHistogramDetails, 0, 23, 194);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg4".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg4() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg4");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg4/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg4/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg4/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg4/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg4/input4.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg4_output.jpg", 1600, 1000000000L/16, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //int [] exp_offsets_x = {0, 5, 0};
                        //int [] exp_offsets_y = {0, 2, 0};
                        int [] exp_offsets_x = {0, 5, 0};
                        int [] exp_offsets_y = {0, 1, 0};
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 2 ) {
                        //int [] exp_offsets_x = {0, 3, 0};
                        //int [] exp_offsets_y = {0, 5, 0};
                        int [] exp_offsets_x = {0, 4, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        //int [] exp_offsets_x = {0, 0, 0};
                        //int [] exp_offsets_y = {0, 7, 0};
                        //int [] exp_offsets_x = {0, 1, 0};
                        //int [] exp_offsets_y = {0, 6, 0};
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 8, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 4 ) {
                        //int [] exp_offsets_x = {0, 4, 0};
                        //int [] exp_offsets_y = {0, 8, 0};
                        //int [] exp_offsets_x = {0, 3, 0};
                        //int [] exp_offsets_y = {0, 7, 0};
                        //int [] exp_offsets_x = {0, 3, 0};
                        //int [] exp_offsets_y = {0, 8, 0};
                        int [] exp_offsets_x = {0, 3, 0};
                        int [] exp_offsets_y = {0, 9, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg5".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg5() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg5/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg5/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg5/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg5/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg5/input4.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg5_output.jpg", 1600, 1000000000L/16, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                /*if( index == 1 ) {
                    //int [] exp_offsets_x = {0, 4, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    //int [] exp_offsets_x = {0, 5, 0};
                    //int [] exp_offsets_y = {0, 0, 0};
                    //int [] exp_offsets_x = {0, 6, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    int [] exp_offsets_x = {0, 4, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 0);
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 2 ) {
                    //int [] exp_offsets_x = {0, 7, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    int [] exp_offsets_x = {0, 8, 0};
                    int [] exp_offsets_y = {0, -4, 0};
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 3 ) {
                    //int [] exp_offsets_x = {0, 9, 0};
                    //int [] exp_offsets_y = {0, -2, 0};
                    //int [] exp_offsets_x = {0, 8, 0};
                    //int [] exp_offsets_y = {0, -1, 0};
                    int [] exp_offsets_x = {0, 8, 0};
                    int [] exp_offsets_y = {0, 0, 0};
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else if( index == 4 ) {
                    //int [] exp_offsets_x = {0, 10, 0};
                    //int [] exp_offsets_y = {0, -4, 0};
                    int [] exp_offsets_x = {0, 11, 0};
                    int [] exp_offsets_y = {0, -3, 0};
                    TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                }
                else {
                    assertTrue(false);
                }*/
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg6".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg6() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg6");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg6/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg6/input7.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg6_output.jpg", 1600, 1000000000L/17, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                /*if( true )
                    return;*/
                    if( index == 1 ) {
                        //int [] exp_offsets_x = {0, 0, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        //int [] exp_offsets_x = {0, -2, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                    else if( index == 2 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 4 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 5 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 6 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 7 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 18, 51, 201);
            //checkHistogramDetails(hdrHistogramDetails, 14, 38, 200);
            //checkHistogramDetails(hdrHistogramDetails, 0, 9, 193);
            //checkHistogramDetails(hdrHistogramDetails, 0, 9, 199);
            //checkHistogramDetails(hdrHistogramDetails, 12, 46, 202);
            //checkHistogramDetails(hdrHistogramDetails, 12, 46, 205);
            //checkHistogramDetails(hdrHistogramDetails, 12, 44, 209);
            //checkHistogramDetails(hdrHistogramDetails, 12, 44, 202);
            //checkHistogramDetails(hdrHistogramDetails, 5, 16, 190);
            checkHistogramDetails(hdrHistogramDetails, 5, 19, 199);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg7".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg7() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg7");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg7/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg7/input7.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg7_output.jpg", 1600, 1000000000L/16, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //int [] exp_offsets_x = {0, 0, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        //int [] exp_offsets_x = {0, -10, 0};
                        //int [] exp_offsets_y = {0, 6, 0};
                        //int [] exp_offsets_x = {0, -6, 0};
                        //int [] exp_offsets_y = {0, 2, 0};
                        //int [] exp_offsets_x = {0, -4, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        //int [] exp_offsets_x = {0, 0, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        //int [] exp_offsets_x = {0, -4, 0};
                        //int [] exp_offsets_y = {0, 0, 0};
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg8".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg8() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg8");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg8/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg8/input7.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg8_output.jpg", 1600, 1000000000L/16, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 4, 26, 92);
            //checkHistogramDetails(hdrHistogramDetails, 3, 19, 68);
            //checkHistogramDetails(hdrHistogramDetails, 0, 10, 60);
            //checkHistogramDetails(hdrHistogramDetails, 1, 8, 72);
            //checkHistogramDetails(hdrHistogramDetails, 1, 6, 64);
            //checkHistogramDetails(hdrHistogramDetails, 1, 15, 75);
            checkHistogramDetails(hdrHistogramDetails, 1, 16, 78);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg9".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg9() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg9");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            final boolean use_auto_photos = true;

            if( use_auto_photos ) {
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto0.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto1.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto2.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto3.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto4.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto5.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto6.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input_auto7.jpg");
            }
            else {
                inputs.add(TestUtils.avg_images_path + "testAvg9/input0.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input1.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input2.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input3.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input4.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input5.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input6.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg9/input7.jpg");
            }

            String out_filename = use_auto_photos ? "testAvg9_auto_output.jpg" : "testAvg9_output.jpg";

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, out_filename, 1600, use_auto_photos ? 1000000000L/16 : 1000000000L/11, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg10".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg10() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg10");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            final boolean use_auto_photos = false;

            if( use_auto_photos ) {
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto0.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto1.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto2.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto3.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto4.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto5.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto6.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input_auto7.jpg");
            }
            else {
                inputs.add(TestUtils.avg_images_path + "testAvg10/input0.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input1.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input2.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input3.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input4.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input5.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input6.jpg");
                inputs.add(TestUtils.avg_images_path + "testAvg10/input7.jpg");
            }

            String out_filename = use_auto_photos ? "testAvg10_auto_output.jpg" : "testAvg10_output.jpg";

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, out_filename, 1196, use_auto_photos ? 1000000000L/12 : 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg11".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg11() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg11");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // note, we don't actually use 8 images for a bright scene like this, but it serves as a good test for
            // misalignment/ghosting anyway
            inputs.add(TestUtils.avg_images_path + "testAvg11/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg11/input7.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg11_output.jpg", 100, 1000000000L/338, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //int [] exp_offsets_x = {0, 4, 0};
                        //int [] exp_offsets_y = {0, -8, 0};
                        //int [] exp_offsets_x = {0, 6, 0};
                        //int [] exp_offsets_y = {0, -8, 0};
                        //int [] exp_offsets_x = {0, -6, 0};
                        //int [] exp_offsets_y = {0, 8, 0};
                        int [] exp_offsets_x = {0, -4, 0};
                        int [] exp_offsets_y = {0, 8, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                        //assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                    }
                    else if( index == 2 ) {
                        //int [] exp_offsets_x = {0, -5, 0};
                        //int [] exp_offsets_y = {0, -1, 0};
                        //int [] exp_offsets_x = {0, -10, 0};
                        //int [] exp_offsets_y = {0, 6, 0};
                        int [] exp_offsets_x = {0, -8, 0};
                        int [] exp_offsets_y = {0, 8, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        //int [] exp_offsets_x = {0, -1, 0};
                        //int [] exp_offsets_y = {0, -18, 0};
                        //int [] exp_offsets_x = {0, 0, 0};
                        //int [] exp_offsets_y = {0, -16, 0};
                        //int [] exp_offsets_x = {0, -4, 0};
                        //int [] exp_offsets_y = {0, -10, 0};
                        //int [] exp_offsets_x = {0, -4, 0};
                        //int [] exp_offsets_y = {0, -8, 0};
                        int [] exp_offsets_x = {0, -4, 0};
                        int [] exp_offsets_y = {0, -12, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 4 ) {
                        //int [] exp_offsets_x = {0, -3, 0};
                        //int [] exp_offsets_y = {0, -20, 0};
                        //int [] exp_offsets_x = {0, -2, 0};
                        //int [] exp_offsets_y = {0, -18, 0};
                        //int [] exp_offsets_x = {0, -6, 0};
                        //int [] exp_offsets_y = {0, -12, 0};
                        int [] exp_offsets_x = {0, -8, 0};
                        int [] exp_offsets_y = {0, -12, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 5 ) {
                        //int [] exp_offsets_x = {0, -8, 0};
                        //int [] exp_offsets_y = {0, 2, 0};
                        //int [] exp_offsets_x = {0, -10, 0};
                        //int [] exp_offsets_y = {0, 4, 0};
                        //int [] exp_offsets_x = {0, -12, 0};
                        //int [] exp_offsets_y = {0, 10, 0};
                        int [] exp_offsets_x = {0, -12, 0};
                        int [] exp_offsets_y = {0, 8, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 6 ) {
                        //int [] exp_offsets_x = {0, 0, 0};
                        //int [] exp_offsets_y = {0, -6, 0};
                        //int [] exp_offsets_x = {0, 2, 0};
                        //int [] exp_offsets_y = {0, -6, 0};
                        //int [] exp_offsets_x = {0, -4, 0};
                        //int [] exp_offsets_y = {0, 2, 0};
                        int [] exp_offsets_x = {0, -4, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 7 ) {
                        //int [] exp_offsets_x = {0, 7, 0};
                        //int [] exp_offsets_y = {0, -2, 0};
                        //int [] exp_offsets_x = {0, 6, 0};
                        //int [] exp_offsets_y = {0, 6, 0};
                        //int [] exp_offsets_x = {0, 4, 0};
                        //int [] exp_offsets_y = {0, 4, 0};
                        //int [] exp_offsets_x = {0, 8, 0};
                        //int [] exp_offsets_y = {0, 8, 0};
                        int [] exp_offsets_x = {0, 4, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg12".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg12() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg12");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg12/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg12/input1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg12_output.jpg", 100, 1000000000L/1617, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 30, 254);
            //checkHistogramDetails(hdrHistogramDetails, 0, 27, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 20, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 17, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 31, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg13".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg13() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg13");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg13/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg13/input1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg13_output.jpg", 100, 1000000000L/2482, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg14".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg14() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg14");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg14/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg14/input7.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg14_output.jpg", 1600, 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, -8, 0};
                        int [] exp_offsets_y = {0, -8, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                    else if( index == 7 ) {
                        //int [] exp_offsets_x = {0, 4, 0};
                        //int [] exp_offsets_y = {0, 28, 0};
                        int [] exp_offsets_x = {0, 4, 0};
                        int [] exp_offsets_y = {0, 40, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 25, 245);
            checkHistogramDetails(hdrHistogramDetails, 0, 18, 246);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg15".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg15() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg15");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg15/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg15/input1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg15_output.jpg", 100, 1000000000L/1525, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            checkHistogramDetails(hdrHistogramDetails, 0, 38, 254);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg16".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg16() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg16");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg16/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg16/input1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg16_output.jpg", 100, 1000000000L/293, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg17".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg17() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg17");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg17/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg17/input7.jpg");

            // the input images record ISO=800, but they were taken with OnePlus 3T which has bug where ISO is reported as max
            // of 800; in reality for a scene this dark, it was probably more like ISO 1600
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg17_output.jpg", 1600, 1000000000L/17, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, -8, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                    else if( index == 7 ) {
                        int [] exp_offsets_x = {0, 12, 0};
                        int [] exp_offsets_y = {0, 28, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 100, 233);
            //checkHistogramDetails(hdrHistogramDetails, 0, 100, 236);
            //checkHistogramDetails(hdrHistogramDetails, 0, 92, 234);
            //checkHistogramDetails(hdrHistogramDetails, 0, 102, 241);
            //checkHistogramDetails(hdrHistogramDetails, 0, 102, 238);
            checkHistogramDetails(hdrHistogramDetails, 0, 103, 244);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg18".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg18() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg18");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg18/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg18/input1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg18_output.jpg", 100, 1000000000L/591, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        //assertTrue(activity.getApplicationInterface().getHDRProcessor().sharp_index == 1);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg19".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg19() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg19");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // repeat same image twice
            inputs.add(TestUtils.avg_images_path + "testAvg19/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg19/input0.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg19_output.jpg", 100, 1000000000L/2483, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 88, 252);
            //checkHistogramDetails(hdrHistogramDetails, 0, 77, 252);
            //checkHistogramDetails(hdrHistogramDetails, 0, 87, 252);
            //checkHistogramDetails(hdrHistogramDetails, 0, 74, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 58, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg20".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg20() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg20");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // repeat same image twice
            inputs.add(TestUtils.avg_images_path + "testAvg20/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg20/input0.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg20_output.jpg", 100, 1000000000L/3124, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg21".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg21() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg21");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // repeat same image twice
            inputs.add(TestUtils.avg_images_path + "testAvg21/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg21/input0.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg21_output.jpg", 102, 1000000000L/6918, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg22".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg22() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg22");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // repeat same image twice
            inputs.add(TestUtils.avg_images_path + "testAvg22/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg22/input0.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg22_output.jpg", 100, 1000000000L/3459, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg23".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg23() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg23");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_3.jpg");
            // only test 4 images, to reflect latest behaviour that we take 4 images for this ISO
        /*inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_4.jpg");
        inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_5.jpg");
        inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_6.jpg");
        inputs.add(TestUtils.avg_images_path + "testAvg23/IMG_20180520_111250_7.jpg");*/

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg23_output.jpg", 1044, 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, -4, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 2 ) {
                        int [] exp_offsets_x = {0, -4, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        int [] exp_offsets_x = {0, -8, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 4 ) {
                        int [] exp_offsets_x = {0, -8, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 5 ) {
                        int [] exp_offsets_x = {0, -12, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 6 ) {
                        int [] exp_offsets_x = {0, -12, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 7 ) {
                        int [] exp_offsets_x = {0, -12, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 81, 251);
            //checkHistogramDetails(hdrHistogramDetails, 0, 80, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 83, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg24".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg24() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg24");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg24/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg24/input1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg24_output.jpg", 100, 1000000000L/2421, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 77, 250);
            //checkHistogramDetails(hdrHistogramDetails, 0, 74, 250);
            //checkHistogramDetails(hdrHistogramDetails, 0, 86, 250);
            //checkHistogramDetails(hdrHistogramDetails, 0, 86, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 80, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 56, 254);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg25".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg25() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg25");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg25/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg25/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg25/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg25/input3.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg25_output.jpg", 512, 1000000000L/20, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg26".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg26() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg26");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // note we now take only 3 images for bright scenes, but still test with 4 images as this serves as a good test
            // against ghosting
            inputs.add(TestUtils.avg_images_path + "testAvg26/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg26/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg26/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg26/input3.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg26_output.jpg", 100, 1000000000L/365, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                /*if( true )
                    return;*/
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 2 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, -4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg27".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg27() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg27");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg27/IMG_20180610_205929_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg27/IMG_20180610_205929_1.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg27_output.jpg", 100, 1000000000L/482, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg28".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg28() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg28");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg28/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg28/input008.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg28_output.jpg", 811, 1000000000L/21, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 21, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 18, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 8, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 13, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg29".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg29() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg29");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg29/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input008.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg29/input009.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg29_output.jpg", 40, 1000000000L/2660, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 88, 127, 255);
            //checkHistogramDetails(hdrHistogramDetails, 92, 134, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg30".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg30() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg30");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg30/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg30/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg30/input003.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg30_output.jpg", 60, 1000000000L/411, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 2 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, -4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        int [] exp_offsets_x = {0, 0, 0};
                        int [] exp_offsets_y = {0, -4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else {
                        fail();
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 134, 254);
            //checkHistogramDetails(hdrHistogramDetails, 0, 144, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 107, 254);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg31".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg31() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg31");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg31/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input008.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input009.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg31/input010.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg31_output.jpg", 609, 1000000000L/25, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 24, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 9, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 13, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg32".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg32() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg32");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg32/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg32/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg32/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg32/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg32/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg32/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg32/input007.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg32_output.jpg", 335, 1000000000L/120, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 34, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 13, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 36, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 61, 254);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg33".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg33() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg33");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg33/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input008.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input009.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg33/input010.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg33_output.jpg", 948, 1000000000L/18, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 81, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 63, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg34".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg34() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg34");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg34/IMG_20180627_121959_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg34/IMG_20180627_121959_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg34/IMG_20180627_121959_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg34_output.jpg", 100, 1000000000L/289, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 86, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 108, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 114, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 103, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg35".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg35() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg35");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg35/IMG_20180711_144453_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg35/IMG_20180711_144453_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg35/IMG_20180711_144453_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg35_output.jpg", 100, 1000000000L/2549, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 165, 247);
            checkHistogramDetails(hdrHistogramDetails, 0, 169, 248);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg36".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg36() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg36");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_3.jpg");
            // only test 4 images, to reflect latest behaviour that we take 4 images for this ISO/exposure time
        /*inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_4.jpg");
        inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_5.jpg");
        inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_6.jpg");
        inputs.add(TestUtils.avg_images_path + "testAvg36/IMG_20180709_114831_7.jpg");*/

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg36_output.jpg", 752, 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, -12, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                    else if( index == 3 ) {
                        int [] exp_offsets_x = {0, -28, 0};
                        int [] exp_offsets_y = {0, 0, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 86, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg37".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg37() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg37");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg37/IMG_20180715_173155_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg37/IMG_20180715_173155_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg37/IMG_20180715_173155_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg37/IMG_20180715_173155_3.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg37_output.jpg", 131, 1000000000L/50, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 12, 109, 255);
            //checkHistogramDetails(hdrHistogramDetails, 3, 99, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 99, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 125, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 94, 255);
            checkHistogramDetails(hdrHistogramDetails, 6, 94, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg38".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg38() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg38");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg38/IMG_20180716_232102_7.jpg");

            // n.b., this was a zoomed in photo, but can't quite remember the exact zoom level!
            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg38_output.jpg", 1505, 1000000000L/10, 3.95f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });
        });
    }

    /** Tests Avg algorithm on test samples "testAvg39".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg39() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg39");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg39/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input008.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input009.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg39/input010.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg39_output.jpg", 521, 1000000000L/27, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 64, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 25, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg40".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg40() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg40");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg40/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input008.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg40/input009.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg40_output.jpg", 199, 1000000000L/120, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 50, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 19, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 50, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 67, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg41".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg41() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg41");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            // example from Google HDR+ dataset
            // note, the number of input images doesn't necessarily match what we'd take for this scene, but we want to compare
            // to the Google HDR+ result
            inputs.add(TestUtils.avg_images_path + "testAvg41/input001.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input002.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input003.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input004.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input005.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input006.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input007.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input008.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input009.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg41/input010.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg41_output.jpg", 100, 1000000000L/869, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 49, 255);
            //checkHistogramDetails(hdrHistogramDetails, 0, 37, 255);
            checkHistogramDetails(hdrHistogramDetails, 0, 59, 254);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg42".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg42() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg42");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg42/IMG_20180822_145152_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg42/IMG_20180822_145152_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg42/IMG_20180822_145152_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg42_output.jpg", 100, 1000000000L/2061, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 67, 254);
            checkHistogramDetails(hdrHistogramDetails, 0, 61, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg43".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg43() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg43");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg43/IMG_20180831_143226_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg43/IMG_20180831_143226_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg43/IMG_20180831_143226_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg43_output.jpg", 100, 1000000000L/2152, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            checkHistogramDetails(hdrHistogramDetails, 0, 69, 253);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg44".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg44() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg44");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg44/IMG_20180830_133917_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg44/IMG_20180830_133917_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg44/IMG_20180830_133917_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg44_output.jpg", 40, 1000000000L/2130, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg45".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg45() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg45");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg45/IMG_20180719_133947_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg45/IMG_20180719_133947_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg45/IMG_20180719_133947_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg45_output.jpg", 100, 1000000000L/865, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 75, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg46".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg46() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg46");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg46/IMG_20180903_203141_7.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg46_output.jpg", 1505, 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg47".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg47() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg47");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg47/IMG_20180911_114752_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg47/IMG_20180911_114752_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg47/IMG_20180911_114752_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg47/IMG_20180911_114752_3.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg47_output.jpg", 749, 1000000000L/12, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg48".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg48() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg48");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg48/IMG_20180911_110520_7.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg48_output.jpg", 1196, 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg49".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg49() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg49");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg49/IMG_20180911_120200_7.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg49_output.jpg", 1505, 1000000000L/10, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 30, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg50".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg50() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg50");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg50/IMG_20181015_144335_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg50/IMG_20181015_144335_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg50/IMG_20181015_144335_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg50/IMG_20181015_144335_3.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg50_output.jpg", 114, 1000000000L/33, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            checkHistogramDetails(hdrHistogramDetails, 0, 91, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg51".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg51() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg51");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_3.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg51/IMG_20181025_182917_7.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg51_output.jpg", 1600, 1000000000L/3, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                    if( index == 1 ) {
                        int [] exp_offsets_x = {0, 8, 0};
                        int [] exp_offsets_y = {0, 4, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                        assertEquals(0, activity.getApplicationInterface().getHDRProcessor().sharp_index);
                    }
                    else if( index == 7 ) {
                        int [] exp_offsets_x = {0, 60, 0};
                        int [] exp_offsets_y = {0, 28, 0};
                        TestUtils.checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, activity.getApplicationInterface().getHDRProcessor().getAvgSampleSize());
                    }
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 91, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvg52".
     */
    @Category(AvgTests.class)
    @Test
    public void testAvg52() throws IOException, InterruptedException {
        Log.d(TAG, "testAvg52");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvg52/IMG_20181119_144836_0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg52/IMG_20181119_144836_1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvg52/IMG_20181119_144836_2.jpg");

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvg52_output.jpg", 100, 1000000000L/297, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 0, 91, 255);
        });
    }

    /** Tests Avg algorithm on test samples "testAvgtemp".
     *  Used for one-off testing, or to recreate NR images from the base exposures to test an updated alorithm.
     *  The test images should be copied to the test device into DCIM/testOpenCamera/testdata/hdrsamples/testAvgtemp/ .
     */
    @Test
    public void testAvgtemp() throws IOException, InterruptedException {
        Log.d(TAG, "testAvgtemp");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input0.png");
            /*inputs.add(TestUtils.avg_images_path + "testAvgtemp/input0.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input1.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input2.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input3.jpg");*/
            /*inputs.add(TestUtils.avg_images_path + "testAvgtemp/input4.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input5.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input6.jpg");
            inputs.add(TestUtils.avg_images_path + "testAvgtemp/input7.jpg");*/

            TestUtils.HistogramDetails hdrHistogramDetails = TestUtils.subTestAvg(activity,inputs, "testAvgtemp_output.jpg", 250, 1000000000L/33, 1.0f, new TestUtils.TestAvgCallback() {
                @Override
                public void doneProcessAvg(int index) {
                    Log.d(TAG, "doneProcessAvg: " + index);
                }
            });

            //checkHistogramDetails(hdrHistogramDetails, 1, 39, 253);
        });
    }

    /** Tests panorama algorithm on test samples "testPanoramaWhite".
     *  This tests that auto-alignment fails gracefully if we can't find any matches.
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanoramaWhite() throws IOException, InterruptedException {
        Log.d(TAG, "testPanoramaWhite");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            inputs.add(TestUtils.panorama_images_path + "testPanoramaWhite/input0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanoramaWhite/input0.jpg");
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;
            float panorama_pics_per_screen = 2.0f;
            String output_name = "testPanoramaWhite_output.jpg";

            TestUtils.subTestPanorama(activity, inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 2.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama1".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama1() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama1");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input3.jpg");
            float camera_angle_x = 62.93796f;
            float camera_angle_y = 47.44656f;
            float panorama_pics_per_screen = 2.0f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(47.44656/49.56283);
            String output_name = "testPanorama1_output.jpg";

            TestUtils.subTestPanorama(activity, inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 2.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama2".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama2() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            /*final float panorama_pics_per_screen = 1.0f;
            //inputs.add(TestUtils.panorama_images_path + "testPanorama2xxx/input0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2xxx/input1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2xxx/input2.jpg");*/
            /*final float panorama_pics_per_screen = 2.0f;
            //inputs.add(TestUtils.panorama_images_path + "testPanorama1/input0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama1/input3.jpg");
            String output_name = "testPanorama1_output.jpg";*/
            float panorama_pics_per_screen = 4.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama2/input0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2/input1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2/input2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2/input3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2/input4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama2/input5.jpg");
            String output_name = "testPanorama2_output.jpg";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 2.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama3".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama3() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama3");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 4.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131249.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131252.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131255.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131258.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131301.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131303.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131305.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131307.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131315.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131317.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131320.jpg");
            String output_name = "testPanorama3_output.jpg";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama3", with panorama_pics_per_screen set
     *  to 4.0.
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama3_picsperscreen2() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama3_picsperscreen2");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 2.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131249.jpg");
            //inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131252.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131255.jpg");
            //inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131258.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131301.jpg");
            //inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131303.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131305.jpg");
            //inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131307.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131315.jpg");
            //inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131317.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama3/IMG_20190214_131320.jpg");
            String output_name = "testPanorama3_picsperscreen2_output.jpg";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, null, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama4".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama4() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama4");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 4.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317_7.jpg");
            String output_name = "testPanorama4_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama4/IMG_20190222_225317.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama5".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama5() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama5");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 4.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524_7.jpg");
            String output_name = "testPanorama5_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama5/IMG_20190223_220524.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama6".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama6() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama6");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 4.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232_7.jpg");
            String output_name = "testPanorama6_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama6/IMG_20190225_154232.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama7".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama7() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama7");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 4.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510_8.jpg");
            String output_name = "testPanorama7_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama7/IMG_20190225_155510.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama8".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama8() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama8");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 2.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama8/IMG_20190227_001431_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama8/IMG_20190227_001431_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama8/IMG_20190227_001431_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama8/IMG_20190227_001431_3.jpg");
            String output_name = "testPanorama8_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama8/IMG_20190227_001431.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/52.26029);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama9".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama9() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama9");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213_6.jpg");
            String output_name = "testPanorama9_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama9/IMG_20190301_145213.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/50.44399);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);

            try {
                Thread.sleep(1000); // need to wait for debug images to be saved/broadcast?
            }
            catch(InterruptedException e) {
                Log.e(TAG, "InterruptedException from sleep", e);
            }
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama10".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama10() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama10");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_9.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_10.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_11.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948_12.jpg");
            String output_name = "testPanorama10_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama10/IMG_20190301_144948.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/50.44399);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama11".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama11() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama11");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652_6.jpg");
            String output_name = "testPanorama11_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama11/IMG_20190306_143652.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/50.44399);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama12".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama12() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama12");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008_9.jpg");
            String output_name = "testPanorama12_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama12/IMG_20190308_152008.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;
            // these images were taken with incorrect camera view angles, so we compensate in the test:
            panorama_pics_per_screen *= (float)(50.282097/50.44399);

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama13".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama13() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama13");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152_9.jpg");
            String output_name = "testPanorama13_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama13/IMG_20190512_014152.xml";
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama14".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama14() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama14");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249_9.jpg");
            String output_name = "testPanorama14_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama14/IMG_20190513_151249.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama15".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama15() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama15");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624_9.jpg");
            String output_name = "testPanorama15_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama15/IMG_20190513_151624.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama16".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama16() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama16");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731_9.jpg");
            String output_name = "testPanorama16_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama16/IMG_20190624_151731.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama17".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama17() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama17");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423_9.jpg");
            String output_name = "testPanorama17_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama17/IMG_20190625_135423.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama18".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama18() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama18");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559_9.jpg");
            String output_name = "testPanorama18_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama18/IMG_20190626_152559.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama19".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama19() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama19");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059_9.jpg");
            String output_name = "testPanorama19_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama19/IMG_20190627_134059.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama20".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama20() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama20");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027_9.jpg");
            String output_name = "testPanorama20_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama20/IMG_20190628_145027.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama21".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama21() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama21");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552_9.jpg");
            String output_name = "testPanorama21_output.jpg";
            String gyro_name = TestUtils.panorama_images_path + "testPanorama21/IMG_20190628_145552.xml";
            //gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 0.5f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama22".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama22() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama22");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama22/IMG_20190629_165627_7.jpg");
            String output_name = "testPanorama22_output.jpg";
            String gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama23".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama23() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama23");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama23/IMG_20190702_145916_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama23/IMG_20190702_145916_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama23/IMG_20190702_145916_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama23/IMG_20190702_145916_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama23/IMG_20190702_145916_4.jpg");
            String output_name = "testPanorama23_output.jpg";
            String gyro_name = null;
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama24".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama24() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama24");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama24/IMG_20190703_154333_9.jpg");
            String output_name = "testPanorama24_output.jpg";
            String gyro_name = null;
            // taken with OnePlus 3T, Camera2 API:
            float camera_angle_x = 62.93796f;
            float camera_angle_y = 47.44656f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama25".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama25() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama25");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            //float panorama_pics_per_screen = 3.33333f / 2.0f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama25/IMG_20190706_215940_6.jpg");
            String output_name = "testPanorama25_output.jpg";
            String gyro_name = null;
            // taken with Nokia 8, Camera2 API:
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama26".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama26() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama26");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama26/IMG_20190706_214842_6.jpg");
            String output_name = "testPanorama26_output.jpg";
            String gyro_name = null;
            // taken with Nokia 8, Camera2 API:
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama27".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama27() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama27");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama27/IMG_20190706_192120_6.jpg");
            String output_name = "testPanorama27_output.jpg";
            String gyro_name = null;
            // taken with Nokia 8, Camera2 API:
            float camera_angle_x = 66.708595f;
            float camera_angle_y = 50.282097f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama28".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama28() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama28");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            // right-to-left:
        /*inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_9.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_8.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_7.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_6.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_5.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_4.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_3.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_2.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_1.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_0.jpg");*/
            // converted from original JPEGs to PNG using Nokia 8:
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_0.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_1.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_2.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_3.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_4.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_5.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_6.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_7.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_8.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/input_bitmap_9.png");
            String output_name = "testPanorama28_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, Camera2 API, standard rear camera:
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama28", but with a nbnq similar set of
     *  input images. Instead of converting the original JPEGs to PNG on Nokia 8, this was done on
     *  the Samsung Galaxy S10e, which gives small differences, but enough to show up potential
     *  stability issues.
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama28_galaxys10e() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama28_galaxys10e");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            // right-to-left:
        /*inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_9.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_8.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_7.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_6.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_5.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_4.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_3.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_2.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_1.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama28/IMG_20190725_134756_0.jpg");*/
            // converted from original JPEGs to PNG using Samsung Galaxy S10e:
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_0.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_1.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_2.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_3.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_4.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_5.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_6.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_7.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_8.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama28/galaxys10e_input_bitmap_9.png");
            String output_name = "testPanorama28_galaxys10e_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, Camera2 API, standard rear camera:
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama29".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama29() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama29");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            // right-to-left:
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_9.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama29/IMG_20190719_145852_0.jpg");
            String output_name = "testPanorama29_output.jpg";
            String gyro_name = null;
            // taken with Nokia 8, old API:
            float camera_angle_x = 66.1062f;
            float camera_angle_y = 49.88347f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama30".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama30() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama30");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
        /*inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_0.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_1.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_2.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_3.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_4.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_5.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_6.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_7.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_8.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_9.jpg");*/
            // converted from original JPEGs to PNG using Nokia 8:
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_0.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_1.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_2.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_3.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_4.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_5.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_6.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_7.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_8.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/nokia8_input_bitmap_9.png");
            String output_name = "testPanorama30_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, old API, standard rear camera:
            // n.b., camera angles are indeed the exact same as with Camera2
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama30", but with a nbnq similar set of
     *  input images. Instead of converting the original JPEGs to PNG on Nokia 8, this was done on
     *  the Samsung Galaxy S10e, which gives small differences, but enough to show up potential
     *  stability issues.
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama30_galaxys10e() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama30_galaxys10e");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
        /*inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_0.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_1.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_2.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_3.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_4.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_5.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_6.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_7.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_8.jpg");
        inputs.add(TestUtils.panorama_images_path + "testPanorama30/IMG_20190723_142934_9.jpg");*/
            // converted from original JPEGs to PNG using Samsung Galaxy S10e:
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_0.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_1.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_2.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_3.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_4.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_5.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_6.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_7.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_8.png");
            inputs.add(TestUtils.panorama_images_path + "testPanorama30/galaxys10e_input_bitmap_9.png");
            String output_name = "testPanorama30_galaxys10e_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, old API, standard rear camera:
            // n.b., camera angles are indeed the exact same as with Camera2
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama31".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama31() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama31");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama31/IMG_20190704_135633_6.jpg");
            String output_name = "testPanorama31_output.jpg";
            String gyro_name = null;
            // taken with OnePlus 3T, Camera2 API:
            float camera_angle_x = 62.93796f;
            float camera_angle_y = 47.44656f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama3".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama32() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama32");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama32/IMG_20190705_145938_8.jpg");
            String output_name = "testPanorama32_output.jpg";
            String gyro_name = null;
            // taken with OnePlus 3T, old API:
            float camera_angle_x = 60.0f;
            float camera_angle_y = 45.0f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama33".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama33() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama33");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama33/IMG_20190713_013437_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama33/IMG_20190713_013437_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama33/IMG_20190713_013437_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama33/IMG_20190713_013437_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama33/IMG_20190713_013437_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama33/IMG_20190713_013437_5.jpg");
            String output_name = "testPanorama33_output.jpg";
            String gyro_name = null;
            // taken with Nokia 8, old API:
            float camera_angle_x = 66.1062f;
            float camera_angle_y = 49.88347f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama34".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama34() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama34");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            // right-to-left:
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_9.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama34/IMG_20190717_144042_0.jpg");
            String output_name = "testPanorama34_output.jpg";
            String gyro_name = null;
            // taken with Nexus 6, old API:
            float camera_angle_x = 62.7533f;
            float camera_angle_y = 47.298824f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama35".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama35() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama35");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama35/IMG_20190717_145114_9.jpg");
            String output_name = "testPanorama35_output.jpg";
            String gyro_name = null;
            // taken with Nexus 7, old API:
            float camera_angle_x = 55.0f;
            float camera_angle_y = 41.401073f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama36".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama36() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama36");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama36/IMG_20190722_201331_7.jpg");
            String output_name = "testPanorama36_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, Camera2 API, ultra wide rear camera:
            float camera_angle_x = 104.00253f;
            float camera_angle_y = 81.008804f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama37".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama37() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama37");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama37/IMG_20190723_203441_8.jpg");
            String output_name = "testPanorama37_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, old API, standard rear camera:
            // n.b., camera angles are indeed the exact same as with Camera2
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    /** Tests panorama algorithm on test samples "testPanorama38".
     */
    @Category(PanoramaTests.class)
    @Test
    public void testPanorama38() throws IOException, InterruptedException {
        Log.d(TAG, "testPanorama38");

        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> { // for simplicity, run the entire test on the UI thread
            // list assets
            List<String> inputs = new ArrayList<>();

            float panorama_pics_per_screen = 3.33333f;
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_0.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_1.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_2.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_3.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_4.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_5.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_6.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_7.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_8.jpg");
            inputs.add(TestUtils.panorama_images_path + "testPanorama38/IMG_20190722_141148_9.jpg");
            String output_name = "testPanorama38_output.jpg";
            String gyro_name = null;
            // taken with Samsung Galaxy S10e, Camera2 API, standard rear camera:
            float camera_angle_x = 66.3177f;
            float camera_angle_y = 50.04736f;

            TestUtils.subTestPanorama(activity, inputs, output_name, gyro_name, panorama_pics_per_screen, camera_angle_x, camera_angle_y, 1.0f);
        });
    }

    private void waitForTakePhoto() {
        Log.d(TAG, "wait until finished taking photo");
        long time_s = System.currentTimeMillis();
        while(true) {
            boolean waiting = getActivityValue(activity -> (activity.getPreview().isTakingPhoto() || !activity.getApplicationInterface().canTakeNewPhoto()));
            if( !waiting ) {
                break;
            }
            mActivityRule.getScenario().onActivity(activity -> {
                TestUtils.waitForTakePhotoChecks(activity, time_s);
            });
        }

        Log.d(TAG, "done taking photo");
    }

    /** Tests behaviour of the MainActivity.OnApplyWindowInsetsListener() for edge-to-edge mode on
     *  Android 15.
     */
    @Category(MainTests.class)
    @Test
    public void testWindowInsets() throws InterruptedException {
        Log.d(TAG, "testWindowInsets");
        setToDefault();

        if( !getActivityValue(MainActivity::getEdgeToEdgeMode) ) {
            Log.d(TAG, "test requires edge-to-edge mode");
            return;
        }

        MainActivity.test_force_system_orientation = true;
        MainActivity.test_force_window_insets = true;

        // portrait, typical with navigation at bottom, cutout at top
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.PORTRAIT;
        MainActivity.test_insets = Insets.of(0, 200, 0, 300);
        MainActivity.test_cutout_insets = Insets.of(0, 200, 0, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(300, activity.getNavigationGap());
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });
        // landscape
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.LANDSCAPE;
        MainActivity.test_insets = Insets.of(200, 0, 300, 0);
        MainActivity.test_cutout_insets = Insets.of(200, 0, 0, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(300, activity.getNavigationGap());
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });
        // reverse landscape
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.REVERSE_LANDSCAPE;
        MainActivity.test_insets = Insets.of(300, 0, 200, 0);
        MainActivity.test_cutout_insets = Insets.of(0, 0, 200, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(300, activity.getNavigationGap());
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });

        // portrait, navigation at bottom, double cutout at top and bottom
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.PORTRAIT;
        MainActivity.test_insets = Insets.of(0, 100, 0, 500);
        MainActivity.test_cutout_insets = Insets.of(0, 100, 0, 300);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(200, activity.getNavigationGap()); // should only include the gap for the navigation, not the bottom cutout
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });
        // landscape
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.LANDSCAPE;
        MainActivity.test_insets = Insets.of(100, 0, 500, 0);
        MainActivity.test_cutout_insets = Insets.of(100, 0, 300, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(200, activity.getNavigationGap()); // should only include the gap for the navigation, not the bottom cutout
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });
        // reverse landscape
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.REVERSE_LANDSCAPE;
        MainActivity.test_insets = Insets.of(500, 0, 100, 0);
        MainActivity.test_cutout_insets = Insets.of(300, 0, 100, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(200, activity.getNavigationGap()); // should only include the gap for the navigation, not the bottom cutout
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });

        // portrait, no navigation bar, waterfall cutout
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.PORTRAIT;
        MainActivity.test_insets = Insets.of(50, 100, 50, 100);
        MainActivity.test_cutout_insets = Insets.of(50, 100, 50, 100);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            // navigation gaps should be 0, as shouldn't include cutout
            assertEquals(0, activity.getNavigationGap());
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });

        // landscape, navigation along landscape edge
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.LANDSCAPE;
        MainActivity.test_insets = Insets.of(100, 0, 0, 250);
        MainActivity.test_cutout_insets = Insets.of(100, 0, 0, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(0, activity.getNavigationGap());
            assertEquals(250, activity.getNavigationGapLandscape());
            assertEquals(0, activity.getNavigationGapReverseLandscape());
        });
        if( getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            // also test manual focus seekbar
            mActivityRule.getScenario().onActivity(activity -> {
                View focus_seekbar = activity.findViewById(R.id.focus_seekbar);
                assertEquals(focus_seekbar.getVisibility(), View.GONE);
            });
            switchToFocusValue("focus_mode_manual2");
            mActivityRule.getScenario().onActivity(activity -> {
                View focus_seekbar = activity.findViewById(R.id.focus_seekbar);
                assertEquals(focus_seekbar.getVisibility(), View.VISIBLE);
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)focus_seekbar.getLayoutParams();
                assertEquals(0, layoutParams.leftMargin);
                assertEquals(0, layoutParams.topMargin);
                assertEquals(0, layoutParams.rightMargin);
                assertEquals(250, layoutParams.bottomMargin);
            });
        }

        // reverse landscape, navigation along landscape edge
        MainActivity.test_system_orientation = MainActivity.SystemOrientation.REVERSE_LANDSCAPE;
        MainActivity.test_insets = Insets.of(0, 0, 100, 250);
        MainActivity.test_cutout_insets = Insets.of(0, 0, 100, 0);
        restart(); // restart to force OnApplyWindowInsetsListener() to be called with new test values
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(0, activity.getNavigationGap());
            assertEquals(0, activity.getNavigationGapLandscape());
            assertEquals(250, activity.getNavigationGapReverseLandscape());
        });
        if( getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            // also test manual focus seekbar
            switchToFocusValue("focus_mode_manual2");
            mActivityRule.getScenario().onActivity(activity -> {
                View focus_seekbar = activity.findViewById(R.id.focus_seekbar);
                assertEquals(focus_seekbar.getVisibility(), View.VISIBLE);
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)focus_seekbar.getLayoutParams();
                assertEquals(0, layoutParams.leftMargin);
                assertEquals(0, layoutParams.topMargin);
                assertEquals(0, layoutParams.rightMargin);
                assertEquals(0, layoutParams.bottomMargin);
            });
        }
    }

    private void subTestTouchToFocus(final boolean wait_after_focus, final boolean single_tap_photo, final boolean double_tap_photo, final boolean manual_can_auto_focus, final boolean can_focus_area, final String focus_value, final String focus_value_ui) throws InterruptedException {
        // touch to auto-focus with focus area (will also exit immersive mode)
        // autofocus shouldn't be immediately, but after a delay
        // and Galaxy S10e needs a longer delay for some reason, for the subsequent touch of the preview view to register
        Thread.sleep(2000);
        int saved_count = getActivityValue(activity -> activity.getPreview().count_cameraAutoFocus);
        Log.d(TAG, "saved count_cameraAutoFocus: " + saved_count);
        Log.d(TAG, "### about to click preview for autofocus");

        onView(anyOf(ViewMatchers.withClassName(endsWith("MySurfaceView")), ViewMatchers.withClassName(endsWith("MyTextureView")))).perform(click());

        Log.d(TAG, "### done click preview for autofocus");

        mActivityRule.getScenario().onActivity(activity -> {
            TestUtils.touchToFocusChecks(activity, single_tap_photo, double_tap_photo, manual_can_auto_focus, can_focus_area, focus_value, focus_value_ui, saved_count);
        });

        if( double_tap_photo ) {
            Thread.sleep(100);
            Log.d(TAG, "about to click preview again for double tap");
            //onView(withId(preview_view_id)).perform(ViewActions.doubleClick());
            mActivityRule.getScenario().onActivity(activity -> {
                //onView(anyOf(ViewMatchers.withClassName(endsWith("MySurfaceView")), ViewMatchers.withClassName(endsWith("MyTextureView")))).perform(click());
                activity.getPreview().onDoubleTap(); // calling tapView twice doesn't seem to work consistently, so we call this directly!
            });
        }
        if( wait_after_focus && !single_tap_photo && !double_tap_photo) {
            // don't wait after single or double tap photo taking, as the photo taking operation is already started
            Log.d(TAG, "wait after focus...");
            Thread.sleep(3000);
        }
    }

    private void subTestTakePhoto(boolean locked_focus, boolean immersive_mode, boolean touch_to_focus, boolean wait_after_focus, boolean single_tap_photo, boolean double_tap_photo, boolean is_raw, boolean test_wait_capture_result) throws InterruptedException {
        Thread.sleep(500);

        TestUtils.SubTestTakePhotoInfo info = getActivityValue(activity -> TestUtils.getSubTestTakePhotoInfo(activity, immersive_mode, single_tap_photo, double_tap_photo));

        int saved_count_cameraTakePicture = getActivityValue(activity -> activity.getPreview().count_cameraTakePicture);

        // count initial files in folder
        String [] files = getActivityValue(activity -> TestUtils.filesInSaveFolder(activity));
        int n_files = files == null ? 0 : files.length;
        Log.d(TAG, "n_files at start: " + n_files);

        int saved_count = getActivityValue(activity -> activity.getPreview().count_cameraAutoFocus);

        int saved_thumbnail_count = getActivityValue(activity -> activity.getApplicationInterface().getDrawPreview().test_thumbnail_anim_count);
        Log.d(TAG, "saved_thumbnail_count: " + saved_thumbnail_count);

        if( touch_to_focus ) {
            subTestTouchToFocus(wait_after_focus, single_tap_photo, double_tap_photo, info.manual_can_auto_focus, info.can_focus_area, info.focus_value, info.focus_value_ui);
        }
        Log.d(TAG, "saved count_cameraAutoFocus: " + saved_count);

        if( !single_tap_photo && !double_tap_photo ) {
            mActivityRule.getScenario().onActivity(activity -> {
                View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                assertFalse( activity.hasThumbnailAnimation() );
                Log.d(TAG, "about to click take photo");
                clickView(takePhotoButton);
                Log.d(TAG, "done clicking take photo");
            });
        }

        waitForTakePhoto();

        int new_count_cameraTakePicture = getActivityValue(activity -> activity.getPreview().count_cameraTakePicture);
        Log.d(TAG, "take picture count: " + new_count_cameraTakePicture);
        assertEquals(new_count_cameraTakePicture, saved_count_cameraTakePicture + 1);

        /*if( test_wait_capture_result ) {
            // if test_wait_capture_result, then we'll have waited too long for thumbnail animation
        }
        else if( info.is_focus_bracketing ) {
            // thumbnail animation may have already occurred (e.g., see testTakePhotoFocusBracketingHeavy()
        }
        else*/ if( info.has_thumbnail_anim ) {
            long time_s = System.currentTimeMillis();
            for(;;) {
                //boolean waiting = getActivityValue(activity -> !activity.hasThumbnailAnimation());
                boolean waiting = getActivityValue(activity -> (activity.getApplicationInterface().getDrawPreview().test_thumbnail_anim_count <= saved_thumbnail_count));
                if( !waiting ) {
                    break;
                }
                Log.d(TAG, "waiting for thumbnail animation");
                Thread.sleep(10);
                int allowed_time_ms = 10000;
                if( info.is_hdr || info.is_nr || info.is_expo ) {
                    // some devices need longer time (especially Nexus 6)
                    allowed_time_ms = 16000;
                }
                assertTrue( System.currentTimeMillis() - time_s < allowed_time_ms );
            }
        }
        else {
            boolean has_thumbnail_animation = getActivityValue(activity -> activity.hasThumbnailAnimation());
            assertFalse( has_thumbnail_animation );
            int new_thumbnail_count = getActivityValue(activity -> activity.getApplicationInterface().getDrawPreview().test_thumbnail_anim_count);
            assertEquals(saved_thumbnail_count, new_thumbnail_count);
        }

        mActivityRule.getScenario().onActivity(activity -> {
            activity.waitUntilImageQueueEmpty();

            TestUtils.checkFocusAfterTakePhoto(activity, info.focus_value, info.focus_value_ui);

            try {
                TestUtils.checkFilesAfterTakePhoto(activity, is_raw, test_wait_capture_result, files);
            }
            catch(InterruptedException e) {
                Log.e(TAG, "InterruptedException from checkFilesAfterTakePhoto", e);
            }

            TestUtils.checkFocusAfterTakePhoto2(activity, touch_to_focus, single_tap_photo, double_tap_photo, test_wait_capture_result, locked_focus, info.can_auto_focus, info.can_focus_area, saved_count);

            TestUtils.postTakePhotoChecks(activity, immersive_mode, info.exposureVisibility, info.exposureLockVisibility);

            assertFalse(activity.getApplicationInterface().getImageSaver().test_queue_blocked);
            assertTrue( activity.getPreview().getCameraController() == null || activity.getPreview().getCameraController().count_camera_parameters_exception == 0 );
        });

    }

    /*@Category(PhotoTests.class)
    @Test
    public void testTakePhoto() throws InterruptedException {
        Log.d(TAG, "testTakePhoto");
        setToDefault();
        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }*/

    /** Tests option to remove device exif info.
     */
    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoRemoveExifOn() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRemoveExifOn");
        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.RemoveDeviceExifPreferenceKey, "preference_remove_device_exif_on");
            editor.apply();
        });
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        mActivityRule.getScenario().onActivity(activity -> {
            try {
                TestUtils.testExif(activity, activity.test_last_saved_image, activity.test_last_saved_imageuri, false, false, false);
            }
            catch(IOException e) {
                Log.e(TAG, "testExif failed", e);
                fail();
            }
        });
    }

    /** Tests option to remove device exif info, but with auto-level to test codepath where we
     *  resave the bitmap.
     */
    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoRemoveExifOn2() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRemoveExifOn2");
        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.RemoveDeviceExifPreferenceKey, "preference_remove_device_exif_on");
            editor.putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, true);
            editor.apply();
        });
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        mActivityRule.getScenario().onActivity(activity -> {
            try {
                TestUtils.testExif(activity, activity.test_last_saved_image, activity.test_last_saved_imageuri, false, false, false);
            }
            catch(IOException e) {
                Log.e(TAG, "testExif failed", e);
                fail();
            }
        });
    }

    /** Tests option to remove device exif info, but keeping datetime tags.
     */
    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoRemoveExifKeepDatetime() throws InterruptedException {
        Log.d(TAG, "testTakePhotoRemoveExifKeepDatetime");
        setToDefault();

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.RemoveDeviceExifPreferenceKey, "preference_remove_device_exif_keep_datetime");
            editor.apply();
        });
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        mActivityRule.getScenario().onActivity(activity -> {
            try {
                TestUtils.testExif(activity, activity.test_last_saved_image, activity.test_last_saved_imageuri, false, true, false);
            }
            catch(IOException e) {
                Log.e(TAG, "testExif failed", e);
                fail();
            }
        });
    }

    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoVendorExtensions() throws InterruptedException {
        Log.d(TAG, "testTakePhotoVendorExtensions");
        setToDefault();

        List<String> supported_extension_modes = new ArrayList<>();
        mActivityRule.getScenario().onActivity(activity -> {
            if( activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_AUTOMATIC) )
                supported_extension_modes.add("preference_photo_mode_x_auto");
            if( activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_HDR) )
                supported_extension_modes.add("preference_photo_mode_x_hdr");
            if( activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT) )
                supported_extension_modes.add("preference_photo_mode_x_night");
            if( activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BOKEH) )
                supported_extension_modes.add("preference_photo_mode_x_bokeh");
            if( activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BEAUTY) )
                supported_extension_modes.add("preference_photo_mode_x_beauty");
        });

        if( supported_extension_modes.isEmpty() ) {
            Log.d(TAG, "test requires camera extensions");
            return;
        }

        boolean check_exif = true;
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        if( is_samsung && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ) {
            // Samsung Galaxy S10e Android 12 doesn't store various exif tags with vendor extensions
            // unclear if this is Samsung specific or Android version specific
            check_exif = false;
        }

        for(String photo_mode : supported_extension_modes) {
            mActivityRule.getScenario().onActivity(activity -> {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PreferenceKeys.PhotoModePreferenceKey, photo_mode);
                editor.apply();
            });
            updateForSettings();

            subTestTakePhoto(false, false, false, false, false, false, false, false);

            if( check_exif ) {
                mActivityRule.getScenario().onActivity(activity -> {
                    try {
                        TestUtils.testExif(activity, activity.test_last_saved_image, activity.test_last_saved_imageuri, true, true, false);
                    }
                    catch(IOException e) {
                        Log.e(TAG, "testExif failed", e);
                        fail();
                    }
                });
            }
        }

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
            editor.apply();
        });
        updateForSettings();

        if( getActivityValue(activity -> activity.getPreview().getCameraControllerManager().getNumberOfCameras()) > 1 ) {
            Log.d(TAG, "test front camera");
            mActivityRule.getScenario().onActivity(activity -> {
                Log.d(TAG, "switch camera");
                View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
                clickView(switchCameraButton);
            });
            waitUntilCameraOpened();

            for(String photo_mode : supported_extension_modes) {
                mActivityRule.getScenario().onActivity(activity -> {
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(PreferenceKeys.PhotoModePreferenceKey, photo_mode);
                    editor.apply();
                });
                updateForSettings();

                subTestTakePhoto(false, false, false, false, false, false, false, false);

                if( check_exif ) {
                    mActivityRule.getScenario().onActivity(activity -> {
                        try {
                            TestUtils.testExif(activity, activity.test_last_saved_image, activity.test_last_saved_imageuri, true, true, false);
                        }
                        catch(IOException e) {
                            Log.e(TAG, "testExif failed", e);
                            fail();
                        }
                    });
                }
            }

            mActivityRule.getScenario().onActivity(activity -> {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
                editor.apply();
            });
            updateForSettings();
        }
    }

    /** Tests preshots.
     */
    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoPreshots() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreshots");
        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }

        mActivityRule.getScenario().onActivity(activity -> {
            assertNotNull(activity.getPreview().getPreShotsRingBuffer());
            assertEquals(0, activity.getPreview().getPreShotsRingBuffer().getNBitmaps());
        });

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.PreShotsPreferenceKey, "preference_save_preshots_on");
            editor.apply();
        });
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);

        mActivityRule.getScenario().onActivity(activity -> {
            assertNotNull(activity.getPreview().getPreShotsRingBuffer());
            assertTrue(activity.getPreview().getPreShotsRingBuffer().getNBitmaps() > 0);
        });

        // test ring buffer flushed on pause
        mActivityRule.getScenario().onActivity(activity -> {
            Log.d(TAG, "pause...");
            getInstrumentation().callActivityOnPause(activity);
        });
        mActivityRule.getScenario().onActivity(activity -> {
            assertNotNull(activity.getPreview().getPreShotsRingBuffer());
            assertEquals(0, activity.getPreview().getPreShotsRingBuffer().getNBitmaps());
        });
    }

    /** Tests manual exposure longer than preview exposure rate, with the RequestTagType.RUN_POST_CAPTURE flag for Camera2 API.
     */
    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoManualISOExposurePostCapture() throws InterruptedException {
        Log.d(TAG, "testTakePhotoPreshots");
        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }
        else if( !getActivityValue(activity -> activity.getPreview().supportsISORange()) ) {
            Log.d(TAG, "test requires manual iso range");
            return;
        }

        switchToISO(100);

        mActivityRule.getScenario().onActivity(activity -> {
            // ensure we test RequestTagType.RUN_POST_CAPTURE even when not testing on a Samsung
            activity.getPreview().getCameraController().test_force_run_post_capture = true;
        });

        mActivityRule.getScenario().onActivity(activity -> {
            // open exposure UI
            View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
            View exposureContainer = activity.findViewById(net.sourceforge.opencamera.R.id.manual_exposure_container);
            assertEquals(exposureButton.getVisibility(), View.VISIBLE);
            assertEquals(exposureContainer.getVisibility(), View.GONE);

            clickView(exposureButton);
        });
        AtomicReference<Long> chosen_exposureRef = new AtomicReference<>();
        mActivityRule.getScenario().onActivity(activity -> {
            View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
            View exposureContainer = activity.findViewById(net.sourceforge.opencamera.R.id.manual_exposure_container);
            SeekBar isoSeekBar = activity.findViewById(net.sourceforge.opencamera.R.id.iso_seekbar);
            SeekBar exposureTimeSeekBar = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_time_seekbar);
            assertEquals(exposureButton.getVisibility(), View.VISIBLE);
            assertEquals(exposureContainer.getVisibility(), View.VISIBLE);
            assertEquals(isoSeekBar.getVisibility(), View.VISIBLE);
            assertEquals(exposureTimeSeekBar.getVisibility(), (activity.getPreview().supportsExposureTime() ? View.VISIBLE : View.GONE));
            //subTestISOButtonAvailability();

            // change exposure time to min of (max, 0.5s)
            int progress = exposureTimeSeekBar.getMax();
            while( progress > exposureTimeSeekBar.getMin() && activity.getManualSeekbars().getExposureTime(progress) > 1000000000L/2 ) {
                progress--;
            }
            chosen_exposureRef.set(activity.getManualSeekbars().getExposureTime(progress));

            Log.d(TAG, "change exposure time to progress " + progress + " time " + chosen_exposureRef.get());
            exposureTimeSeekBar.setProgress(progress);
        });
        long chosen_exposure = chosen_exposureRef.get();
        mActivityRule.getScenario().onActivity(activity -> {
            assertEquals(activity.getPreview().getCameraController().getISO(), 100);
            assertEquals(activity.getPreview().getCameraController().getExposureTime(), chosen_exposure);

            // close the exposure UI
            View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
            clickView(exposureButton);
        });
        mActivityRule.getScenario().onActivity(activity -> {
            View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
            View exposureContainer = activity.findViewById(net.sourceforge.opencamera.R.id.manual_exposure_container);
            assertEquals(exposureButton.getVisibility(), View.VISIBLE);
            assertEquals(exposureContainer.getVisibility(), View.GONE);
        });

        subTestTakePhoto(false, false, true, true, false, false, false, false);

    }

    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoJpegR() throws InterruptedException {
        Log.d(TAG, "testTakePhotoJpegR");

        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().supportsJpegR()) ) {
            Log.d(TAG, "jpeg_r not supported");
            return;
        }

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg_r");
            editor.apply();
        });
        updateForSettings();

        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /** Tests with flag set to force preview to take 6s to start (tests Camera2 behaviour for this happening on background thread, and
     *  not).
     */
    @Category(PhotoTests.class)
    @Test
    public void testTakePhotoSlowPreviewStart() throws InterruptedException {
        Log.d(TAG, "testTakePhotoSlowPreviewStart");

        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
            // matches the use of wait_until_started in Preview.cameraOpened()
            Log.d(TAG, "test requires Android 14+");
            return;
        }

        CameraController.test_force_slow_preview_start = true;
        long time_s = System.currentTimeMillis();
        restart(false); // restart to force preview to start with test flag
        Log.d(TAG, "time to restart: " + (System.currentTimeMillis() - time_s));
        assertTrue( System.currentTimeMillis() - time_s < 3000 ); // test didn't get stuck on UI thread

        // make sure the application isn't stuck on the UI thread
        time_s = System.currentTimeMillis();
        boolean done = false;
        while( !done ) {
            assertTrue( System.currentTimeMillis() - time_s < 500 );
            done = getActivityValue(activity -> activity.getPreview().isPreviewStarting());
        }

        waitUntilPreviewStarted();

        subTestTakePhoto(false, false, true, true, false, false, false, false);
    }

    /** Tests with flag set to force preview to take 6s to start (tests Camera2 behaviour for this happening on background thread, and
     *  not).
     */
    @Category(PhotoTests.class)
    @Test
    public void testSettingsSlowPreviewStart() throws InterruptedException {
        Log.d(TAG, "testSettingsSlowPreviewStart");

        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
            // matches the use of wait_until_started in Preview.cameraOpened()
            Log.d(TAG, "test requires Android 14+");
            return;
        }

        CameraController.test_force_slow_preview_start = true;
        long time_s = System.currentTimeMillis();
        restart(false); // restart to force preview to start with test flag
        Log.d(TAG, "time to restart: " + (System.currentTimeMillis() - time_s));
        assertTrue( System.currentTimeMillis() - time_s < 3000 ); // test didn't get stuck on UI thread

        assertFalse(getActivityValue(activity -> activity.getPreview().isPreviewStarted())); // shouldn't have started preview yet

        // go to settings
        assertFalse(getActivityValue(activity -> activity.isCameraInBackground()));
        mActivityRule.getScenario().onActivity(activity -> {
            View settingsButton = activity.findViewById(net.sourceforge.opencamera.R.id.settings);
            clickView(settingsButton);
        });
        assertTrue(getActivityValue(activity -> activity.isCameraInBackground()));

        // leave settings
        Thread.sleep(500);
        mActivityRule.getScenario().onActivity(activity -> {
            Log.d(TAG, "on back pressed...");
            activity.onBackPressed();
        });
        Thread.sleep(500);
        assertFalse(getActivityValue(activity -> activity.isCameraInBackground()));

        // make sure preview starts up
        waitUntilPreviewStarted();
    }

    private int getNFiles() {
        // count initial files in folder
        String [] files = getActivityValue(activity -> TestUtils.filesInSaveFolder(activity));
        Log.d(TAG, "getNFiles: " + Arrays.toString(files));
        return files == null ? 0 : files.length;
    }

    /**
     * @return The number of resultant video files
     */
    private int subTestTakeVideo(boolean test_exposure_lock, boolean test_focus_area, boolean allow_failure, boolean immersive_mode, TestUtils.VideoTestCallback test_cb, long time_ms, boolean max_filesize, int n_non_video_files) throws InterruptedException {
        boolean supports_exposure_lock = getActivityValue(activity -> activity.getPreview().supportsExposureLock());
        if( test_exposure_lock && !supports_exposure_lock ) {
            return 0;
        }

        Thread.sleep(500); // needed for Pixel 6 Pro with Camera 2 API

        mActivityRule.getScenario().onActivity(activity -> {
            TestUtils.preTakeVideoChecks(activity, immersive_mode);

            if( !activity.getPreview().isVideo() ) {
                View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
                clickView(switchVideoButton);
            }
        });

        waitUntilCameraOpened();

        mActivityRule.getScenario().onActivity(activity -> {
            assertTrue(activity.getPreview().isVideo());
            TestUtils.preTakeVideoChecks(activity, immersive_mode);
            // reset:
            activity.getApplicationInterface().test_n_videos_scanned = 0;
        });

        // count initial files in folder
        int n_files = getNFiles();
        Log.d(TAG, "n_files at start: " + n_files);

        // store status to compare with later
        int exposureVisibility = getActivityValue(activity -> {
            View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
            return exposureButton.getVisibility();
        });
        int exposureLockVisibility = getActivityValue(activity -> {
            View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
            return exposureLockButton.getVisibility();
        });

        mActivityRule.getScenario().onActivity(activity -> {
            Log.d(TAG, "about to click take video");
            View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
            clickView(takePhotoButton);
            Log.d(TAG, "done clicking take video");
        });
        getInstrumentation().waitForIdleSync();
        Log.d(TAG, "after idle sync");

        mActivityRule.getScenario().onActivity(activity -> {
            Preview preview = activity.getPreview();
            if( preview.usingCamera2API() ) {
                assertEquals(preview.getCurrentPreviewSize().width, preview.getCameraController().test_texture_view_buffer_w);
                assertEquals(preview.getCurrentPreviewSize().height, preview.getCameraController().test_texture_view_buffer_h);
            }
        });

        waitUntilTimer();

        int exp_n_new_files = 0;
        boolean failed_to_start = false;
        boolean is_video_recording = getActivityValue(activity -> activity.getPreview().isVideoRecording());
        if( is_video_recording ) {
            mActivityRule.getScenario().onActivity(activity -> {
                TestUtils.takeVideoRecordingChecks(activity, immersive_mode, exposureVisibility, exposureLockVisibility);
            });

            if( test_cb == null ) {
                if( !immersive_mode && time_ms > 500 ) {
                    // test turning torch on/off (if in immersive mode, popup button will be hidden)
                    switchToFlashValue("flash_torch");
                    Thread.sleep(500);
                    switchToFlashValue("flash_off");
                }

                Thread.sleep(time_ms);
                mActivityRule.getScenario().onActivity(activity -> {
                    TestUtils.takeVideoRecordingChecks(activity, immersive_mode, exposureVisibility, exposureLockVisibility);

                    Preview preview = activity.getPreview();
                    assertFalse(preview.hasFocusArea());
                    if( !allow_failure ) {
                        assertNull(preview.getCameraController().getFocusAreas());
                        assertNull(preview.getCameraController().getMeteringAreas());
                    }
                });

                if( test_focus_area ) {
                    // touch to auto-focus with focus area
                    Log.d(TAG, "touch to focus");
                    onView(anyOf(ViewMatchers.withClassName(endsWith("MySurfaceView")), ViewMatchers.withClassName(endsWith("MyTextureView")))).perform(click());
                    Log.d(TAG, "done touch to focus");
                    Thread.sleep(1000); // wait for autofocus
                    mActivityRule.getScenario().onActivity(activity -> {
                        Preview preview = activity.getPreview();
                        if( preview.supportsFocus() ) {
                            assertTrue(preview.hasFocusArea());
                            assertNotNull(preview.getCameraController().getFocusAreas());
                            assertEquals(1, preview.getCameraController().getFocusAreas().size());
                            assertNotNull(preview.getCameraController().getMeteringAreas());
                            assertEquals(1, preview.getCameraController().getMeteringAreas().size());
                        }
                    });

                    // this time, don't wait
                    Log.d(TAG, "touch again to focus");
                    onView(anyOf(ViewMatchers.withClassName(endsWith("MySurfaceView")), ViewMatchers.withClassName(endsWith("MyTextureView")))).perform(click());
                    Log.d(TAG, "done touch to focus");
                }

                if( test_exposure_lock ) {
                    mActivityRule.getScenario().onActivity(activity -> {
                        Log.d(TAG, "test exposure lock");
                        assertFalse(activity.getPreview().getCameraController().getAutoExposureLock());
                        View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
                        clickView(exposureLockButton);
                    });
                    getInstrumentation().waitForIdleSync();
                    Log.d(TAG, "after idle sync");
                    mActivityRule.getScenario().onActivity(activity -> {
                        assertTrue( activity.getPreview().getCameraController().getAutoExposureLock() );
                    });
                    Thread.sleep(2000);
                }

                mActivityRule.getScenario().onActivity(activity -> {
                    TestUtils.takeVideoRecordingChecks(activity, immersive_mode, exposureVisibility, exposureLockVisibility);

                    Log.d(TAG, "about to click stop video");
                    View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                    clickView(takePhotoButton);
                    Log.d(TAG, "done clicking stop video");
                });
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");
            }
            else {
                exp_n_new_files = test_cb.doTest();

                mActivityRule.getScenario().onActivity(activity -> {
                    if( activity.getPreview().isVideoRecording() ) {
                        Log.d(TAG, "about to click stop video");
                        View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                        clickView(takePhotoButton);
                        Log.d(TAG, "done clicking stop video");
                    }
                });
                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");
            }
        }
        else {
            Log.d(TAG, "didn't start video");
            assertTrue(allow_failure);
            failed_to_start = true;
        }

        int n_new_files = getNFiles() - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        int exp_n_new_files_f = exp_n_new_files;
        boolean failed_to_start_f = failed_to_start;
        mActivityRule.getScenario().onActivity(activity -> {
            TestUtils.checkFilesAfterTakeVideo(activity, allow_failure, test_cb != null, time_ms, n_non_video_files, failed_to_start_f, exp_n_new_files_f, n_new_files);

            TestUtils.postTakeVideoChecks(activity, immersive_mode, max_filesize, exposureVisibility, exposureLockVisibility);
        });

        return n_new_files;
    }

    /*@Category(VideoTests.class)
    @Test
    public void testTakeVideo() throws InterruptedException {
        Log.d(TAG, "testTakeVideo");

        setToDefault();

        int n_new_files = subTestTakeVideo(false, false, false, false, null, 5000, false, 0);

        assertEquals(1, n_new_files);
    }*/

    /** Test for bug fix made on 20221112, to do with Pixel 6 Pro and video resolution larger than
     *  FullHD but smaller than 4K. Problem that we selected 60fps because that's supported at
     *  FullHD (and is in the CamcorderProfile for FullHD), but the larger non-4K resolution does
     *  not support 60fps.
     */
    @Category(VideoTests.class)
    @Test
    public void testTakeVideoAltResolution() throws InterruptedException {
        Log.d(TAG, "testTakeVideoAltResolution");

        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().usingCamera2API()) ) {
            Log.d(TAG, "test requires camera2 api");
            return;
        }

        String chosen_video_quality = getActivityValue(activity -> {
            String return_quality = null;
            CamcorderProfile best_profile = null;
            List<String> supported_video_quality = activity.getPreview().getVideoQualityHander().getSupportedVideoQuality();
            if(  supported_video_quality != null ) {
                for(String quality : supported_video_quality) {
                    CamcorderProfile profile = activity.getPreview().getCamcorderProfile(quality);
                    if( profile.videoFrameWidth > 1920 && profile.videoFrameHeight > 1080 && profile.videoFrameWidth < 3840 && profile.videoFrameHeight < 2160 ) {
                        if( best_profile == null || profile.videoFrameWidth*profile.videoFrameHeight < best_profile.videoFrameWidth*best_profile.videoFrameHeight ) {
                            return_quality = quality;
                            best_profile = profile;
                        }
                    }
                }
            }
            if( return_quality != null ) {
                Log.d(TAG, "video_quality: " + return_quality);
                Log.d(TAG, "best_profile: " + best_profile.videoFrameWidth + " x " + best_profile.videoFrameHeight);
            }
            return return_quality;
        });
        if( chosen_video_quality ==  null ) {
            Log.d(TAG, "can't find desired video resolution");
            return;
        }
        mActivityRule.getScenario().onActivity(activity -> {
            activity.getApplicationInterface().setVideoQualityPref(chosen_video_quality);
        });

        int n_new_files = subTestTakeVideo(false, false, false, false, null, 5000, false, 0);

        assertEquals(1, n_new_files);

        pauseAndResume();

        mActivityRule.getScenario().onActivity(activity -> {
            String video_quality = activity.getApplicationInterface().getVideoQualityPref();
            Log.d(TAG, "video_quality: " + video_quality);
            assertEquals(chosen_video_quality, video_quality);
        });
    }

    private void subTestTakeVideoSnapshot() throws InterruptedException {
        Log.d(TAG, "subTestTakeVideoSnapshot");

        mActivityRule.getScenario().onActivity(activity -> {
            View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
            assertEquals(takePhotoVideoButton.getVisibility(), View.GONE);
        });

        subTestTakeVideo(false, false, false, false, new TestUtils.VideoTestCallback() {
            @Override
            public int doTest() {
                Log.d(TAG, "wait before taking photo");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    Log.e(TAG, "InterruptedException from sleep", e);
                    fail();
                }
                mActivityRule.getScenario().onActivity(activity -> {
                    View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                    View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
                    assertEquals(takePhotoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video));
                    assertEquals(takePhotoVideoButton.getVisibility(), View.VISIBLE);
                    assertTrue( activity.getPreview().isVideoRecording() );
                    assertFalse(activity.getPreview().isVideoRecordingPaused());

                    Log.d(TAG, "about to click take photo snapshot");
                    clickView(takePhotoVideoButton);
                    Log.d(TAG, "done clicking take photo snapshot");
                });

                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                waitForTakePhoto();

                mActivityRule.getScenario().onActivity(activity -> {
                    View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
                    assertEquals(takePhotoVideoButton.getVisibility(), View.VISIBLE);
                    assertTrue( activity.getPreview().isVideoRecording() );
                    assertFalse(activity.getPreview().isVideoRecordingPaused());
                });

                Log.d(TAG, "wait before stopping");
                try {
                    Thread.sleep(3000);
                }
                catch(InterruptedException e) {
                    Log.e(TAG, "InterruptedException from sleep", e);
                    fail();
                }
                mActivityRule.getScenario().onActivity(activity -> {
                    View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
                    View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
                    assertEquals(takePhotoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video));
                    assertEquals(takePhotoVideoButton.getVisibility(), View.VISIBLE);
                    assertTrue( activity.getPreview().isVideoRecording() );

                    Log.d(TAG, "about to click stop video");
                    clickView(takePhotoButton);
                    Log.d(TAG, "done clicking stop video");
                });

                getInstrumentation().waitForIdleSync();
                Log.d(TAG, "after idle sync");

                return 2;
            }
        }, 5000, false, 1);

        mActivityRule.getScenario().onActivity(activity -> {
            activity.waitUntilImageQueueEmpty();
        });
    }

    /** Test taking photo while recording video.
     */
    /*@Category(VideoTests.class)
    @Test
    public void testTakeVideoSnapshot() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSnapshot");

        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().supportsPhotoVideoRecording()) ) {
            Log.d(TAG, "video snapshot not supported");
            return;
        }

        subTestTakeVideoSnapshot();
    }*/

    /** Test taking photo while recording video, when JPEG_R is set.
     */
    @Category(VideoTests.class)
    @Test
    public void testTakeVideoSnapshotJpegR() throws InterruptedException {
        Log.d(TAG, "testTakeVideoSnapshotJpegR");

        setToDefault();

        if( !getActivityValue(activity -> activity.getPreview().supportsPhotoVideoRecording()) ) {
            Log.d(TAG, "video snapshot not supported");
            return;
        }
        else if( !getActivityValue(activity -> activity.getPreview().supportsJpegR()) ) {
            Log.d(TAG, "jpeg_r not supported");
            return;
        }

        mActivityRule.getScenario().onActivity(activity -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg_r");
            editor.apply();
        });
        updateForSettings();

        subTestTakeVideoSnapshot();
    }
}
