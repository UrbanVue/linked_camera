package net.sourceforge.opencamera.test;

import android.media.CamcorderProfile;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.CameraController2;
import net.sourceforge.opencamera.HDRProcessor;
import net.sourceforge.opencamera.ImageSaver;
import net.sourceforge.opencamera.LocationSupplier;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.preview.VideoQualityHandler;
import net.sourceforge.opencamera.TextFormatter;
import net.sourceforge.opencamera.ui.DrawPreview;
import net.sourceforge.opencamera.ui.MainUI;
import net.sourceforge.opencamera.ui.PopupView;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

class Log {
    public static void d(String tag, String text) {
        System.out.println(tag + ": " + text);
    }
}

/**
 * Note, need to run with MyDebug.LOG set to false, due to Android's Log.d not being mocked (good
 * practice to test release code anyway).
 */
public class UnitTest {
    private static final String TAG = "UnitTest";

    @Test
    public void testLocationToDMS() {
        Log.d(TAG, "testLocationToDMS");

        String location_string = LocationSupplier.locationToDMS(0.0);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°0'0\"", location_string);

        location_string = LocationSupplier.locationToDMS(0.0000306);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°0'0\"", location_string);

        location_string = LocationSupplier.locationToDMS(0.000306);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°0'1\"", location_string);

        location_string = LocationSupplier.locationToDMS(0.00306);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°0'11\"", location_string);

        location_string = LocationSupplier.locationToDMS(0.9999);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°59'59\"", location_string);

        location_string = LocationSupplier.locationToDMS(1.7438);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("1°44'37\"", location_string);

        location_string = LocationSupplier.locationToDMS(53.000137);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("53°0'0\"", location_string);

        location_string = LocationSupplier.locationToDMS(147.00938);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("147°0'33\"", location_string);

        location_string = LocationSupplier.locationToDMS(-0.0);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°0'0\"", location_string);

        location_string = LocationSupplier.locationToDMS(-0.0000306);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("0°0'0\"", location_string);

        location_string = LocationSupplier.locationToDMS(-0.000306);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("-0°0'1\"", location_string);

        location_string = LocationSupplier.locationToDMS(-0.00306);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("-0°0'11\"", location_string);

        location_string = LocationSupplier.locationToDMS(-0.9999);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("-0°59'59\"", location_string);

        location_string = LocationSupplier.locationToDMS(-1.7438);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("-1°44'37\"", location_string);

        location_string = LocationSupplier.locationToDMS(-53.000137);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("-53°0'0\"", location_string);

        location_string = LocationSupplier.locationToDMS(-147.00938);
        Log.d(TAG, "location_string: " + location_string);
        assertEquals("-147°0'33\"", location_string);
    }

    @Test
    public void testDateString() throws ParseException {
        Log.d(TAG, "testDateString");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
        Date date1 = sdf.parse("2017/01/31");
        assertEquals( TextFormatter.getDateString("preference_stamp_dateformat_none", date1), "" );
        assertEquals( TextFormatter.getDateString("preference_stamp_dateformat_yyyymmdd", date1), "2017-01-31" );
        assertEquals( TextFormatter.getDateString("preference_stamp_dateformat_ddmmyyyy", date1), "31/01/2017" );
        assertEquals( TextFormatter.getDateString("preference_stamp_dateformat_mmddyyyy", date1), "01/31/2017" );
    }

    @Test
    public void testTimeString() throws ParseException {
        Log.d(TAG, "testTimeString");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        // n.b., do case insensitive checks as at one point I saw the text formatter change from upper case to lower case for AM/PM
        Date time1 = sdf.parse("00:00:00");
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_none", time1), "" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_12hour", time1).toLowerCase(Locale.US), "12:00:00 am" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_24hour", time1), "00:00:00" );
        Date time2 = sdf.parse("08:15:43");
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_none", time2), "" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_12hour", time2).toLowerCase(Locale.US), "08:15:43 am" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_24hour", time2), "08:15:43" );
        Date time3 = sdf.parse("12:00:00");
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_none", time3), "" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_12hour", time3).toLowerCase(Locale.US), "12:00:00 pm" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_24hour", time3), "12:00:00" );
        Date time4 = sdf.parse("13:53:06");
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_none", time4), "" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_12hour", time4).toLowerCase(Locale.US), "01:53:06 pm" );
        assertEquals( TextFormatter.getTimeString("preference_stamp_timeformat_24hour", time4), "13:53:06" );
    }

    @Test
    public void testFormatTime() {
        Log.d(TAG, "testFormatTime");
        assertEquals( TextFormatter.formatTimeMS(952), "00:00:00,952" );
        assertEquals( TextFormatter.formatTimeMS(1092), "00:00:01,092" );
        assertEquals( TextFormatter.formatTimeMS(37301), "00:00:37,301" );
        assertEquals( TextFormatter.formatTimeMS(306921), "00:05:06,921" );
        assertEquals( TextFormatter.formatTimeMS(5391002), "01:29:51,002" );
        assertEquals( TextFormatter.formatTimeMS(92816837), "25:46:56,837" );
        assertEquals( TextFormatter.formatTimeMS(792816000), "220:13:36,000" );
    }

    @Test
    public void testBestPreviewFps() {
        Log.d(TAG, "testBestPreviewFps");

        List<int []> list0 = new ArrayList<>();
        list0.add(new int[]{15000, 15000});
        list0.add(new int[]{15000, 30000});
        list0.add(new int[]{7000, 30000});
        list0.add(new int[]{30000, 30000});
        int [] best_fps0 = Preview.chooseBestPreviewFps(list0);
        assertTrue(best_fps0[0] == 7000 && best_fps0[1] == 30000);

        List<int []> list1 = new ArrayList<>();
        list1.add(new int[]{15000, 15000});
        list1.add(new int[]{7000, 60000});
        list1.add(new int[]{15000, 30000});
        list1.add(new int[]{7000, 30000});
        list1.add(new int[]{30000, 30000});
        int [] best_fps1 = Preview.chooseBestPreviewFps(list1);
        assertTrue(best_fps1[0] == 7000 && best_fps1[1] == 60000);

        List<int []> list2 = new ArrayList<>();
        list2.add(new int[]{15000, 15000});
        list2.add(new int[]{7000, 15000});
        list2.add(new int[]{7000, 10000});
        list2.add(new int[]{8000, 19000});
        int [] best_fps2 = Preview.chooseBestPreviewFps(list2);
        assertTrue(best_fps2[0] == 8000 && best_fps2[1] == 19000);
    }

    @Test
    public void testMatchPreviewFpsToVideo() {
        Log.d(TAG, "matchPreviewFpsToVideo");

        List<int []> list0 = new ArrayList<>();
        list0.add(new int[]{15000, 15000});
        list0.add(new int[]{15000, 30000});
        list0.add(new int[]{7000, 30000});
        list0.add(new int[]{30000, 30000});
        int [] best_fps0 = Preview.matchPreviewFpsToVideo(list0, 30000);
        assertTrue(best_fps0[0] == 30000 && best_fps0[1] == 30000);

        List<int []> list1 = new ArrayList<>();
        list1.add(new int[]{15000, 15000});
        list1.add(new int[]{7000, 60000});
        list1.add(new int[]{15000, 30000});
        list1.add(new int[]{7000, 30000});
        list1.add(new int[]{30000, 30000});
        int [] best_fps1 = Preview.matchPreviewFpsToVideo(list1, 15000);
        assertTrue(best_fps1[0] == 15000 && best_fps1[1] == 15000);

        List<int []> list2 = new ArrayList<>();
        list2.add(new int[]{15000, 15000});
        list2.add(new int[]{7000, 15000});
        list2.add(new int[]{7000, 10000});
        list2.add(new int[]{8000, 19000});
        int [] best_fps2 = Preview.matchPreviewFpsToVideo(list2, 7000);
        assertTrue(best_fps2[0] == 7000 && best_fps2[1] == 10000);
    }

    private void compareVideoQuality(List<String> video_quality, List<String> exp_video_quality) {
        for(int i=0;i<video_quality.size();i++) {
            Log.d(TAG, "supported video quality: " + video_quality.get(i));
        }
        for(int i=0;i<exp_video_quality.size();i++) {
            Log.d(TAG, "expected video quality: " + exp_video_quality.get(i));
        }
        assertEquals(video_quality.size(), exp_video_quality.size());
        for(int i=0;i<video_quality.size();i++) {
            String quality = video_quality.get(i);
            String exp_quality = exp_video_quality.get(i);
            assertEquals(quality, exp_quality);
        }
    }

    /** Test for setting correct video resolutions and profiles.
     */
    @Test
    public void testVideoResolutions1() {
        VideoQualityHandler video_quality_handler = new VideoQualityHandler();

        List<CameraController.Size> video_sizes = new ArrayList<>();
        video_sizes.add(new CameraController.Size(1920, 1080));
        video_sizes.add(new CameraController.Size(1280, 720));
        video_sizes.add(new CameraController.Size(1600, 900));
        video_quality_handler.setVideoSizes(video_sizes);
        video_quality_handler.sortVideoSizes();

        List<Integer> profiles = new ArrayList<>();
        List<VideoQualityHandler.Dimension2D> dimensions = new ArrayList<>();
        profiles.add(CamcorderProfile.QUALITY_HIGH);
        dimensions.add(new VideoQualityHandler.Dimension2D(1920, 1080));
        profiles.add(CamcorderProfile.QUALITY_1080P);
        dimensions.add(new VideoQualityHandler.Dimension2D(1920, 1080));
        profiles.add(CamcorderProfile.QUALITY_720P);
        dimensions.add(new VideoQualityHandler.Dimension2D(1280, 720));
        profiles.add(CamcorderProfile.QUALITY_LOW);
        dimensions.add(new VideoQualityHandler.Dimension2D(1280, 720));
        video_quality_handler.initialiseVideoQualityFromProfiles(profiles, dimensions);

        List<String> video_quality = video_quality_handler.getSupportedVideoQuality();
        List<String> exp_video_quality = new ArrayList<>();
        exp_video_quality.add("" + CamcorderProfile.QUALITY_HIGH);
        exp_video_quality.add(CamcorderProfile.QUALITY_720P + "_r1600x900");
        exp_video_quality.add("" + CamcorderProfile.QUALITY_720P);
        compareVideoQuality(video_quality, exp_video_quality);
    }

    /** Test for setting correct video resolutions and profiles.
     */
    @Test
    public void testVideoResolutions2() {
        VideoQualityHandler video_quality_handler = new VideoQualityHandler();

        List<CameraController.Size> video_sizes = new ArrayList<>();
        video_sizes.add(new CameraController.Size(1920, 1080));
        video_sizes.add(new CameraController.Size(1280, 720));
        video_sizes.add(new CameraController.Size(1600, 900));
        video_quality_handler.setVideoSizes(video_sizes);
        video_quality_handler.sortVideoSizes();

        List<Integer> profiles = new ArrayList<>();
        List<VideoQualityHandler.Dimension2D> dimensions = new ArrayList<>();
        profiles.add(CamcorderProfile.QUALITY_HIGH);
        dimensions.add(new VideoQualityHandler.Dimension2D(1920, 1080));
        profiles.add(CamcorderProfile.QUALITY_720P);
        dimensions.add(new VideoQualityHandler.Dimension2D(1280, 720));
        profiles.add(CamcorderProfile.QUALITY_LOW);
        dimensions.add(new VideoQualityHandler.Dimension2D(1280, 720));
        video_quality_handler.initialiseVideoQualityFromProfiles(profiles, dimensions);

        List<String> video_quality = video_quality_handler.getSupportedVideoQuality();
        List<String> exp_video_quality = new ArrayList<>();
        exp_video_quality.add("" + CamcorderProfile.QUALITY_HIGH);
        exp_video_quality.add(CamcorderProfile.QUALITY_720P + "_r1600x900");
        exp_video_quality.add("" + CamcorderProfile.QUALITY_720P);
        compareVideoQuality(video_quality, exp_video_quality);
    }

    /** Test for setting correct video resolutions and profiles.
     */
    @Test
    public void testVideoResolutions3() {
        VideoQualityHandler video_quality_handler = new VideoQualityHandler();

        List<CameraController.Size> video_sizes = new ArrayList<>();
        video_sizes.add(new CameraController.Size(1920, 1080));
        video_sizes.add(new CameraController.Size(1280, 720));
        video_sizes.add(new CameraController.Size(960, 720));
        video_sizes.add(new CameraController.Size(800, 480));
        video_sizes.add(new CameraController.Size(720, 576));
        video_sizes.add(new CameraController.Size(720, 480));
        video_sizes.add(new CameraController.Size(768, 576));
        video_sizes.add(new CameraController.Size(640, 480));
        video_sizes.add(new CameraController.Size(320, 240));
        video_sizes.add(new CameraController.Size(352, 288));
        video_sizes.add(new CameraController.Size(240, 160));
        video_sizes.add(new CameraController.Size(176, 144));
        video_sizes.add(new CameraController.Size(128, 96));
        video_quality_handler.setVideoSizes(video_sizes);
        video_quality_handler.sortVideoSizes();

        List<Integer> profiles = new ArrayList<>();
        List<VideoQualityHandler.Dimension2D> dimensions = new ArrayList<>();
        profiles.add(CamcorderProfile.QUALITY_HIGH);
        dimensions.add(new VideoQualityHandler.Dimension2D(1920, 1080));
        profiles.add(CamcorderProfile.QUALITY_1080P);
        dimensions.add(new VideoQualityHandler.Dimension2D(1920, 1080));
        profiles.add(CamcorderProfile.QUALITY_720P);
        dimensions.add(new VideoQualityHandler.Dimension2D(1280, 720));
        profiles.add(CamcorderProfile.QUALITY_480P);
        dimensions.add(new VideoQualityHandler.Dimension2D(720, 480));
        profiles.add(CamcorderProfile.QUALITY_CIF);
        dimensions.add(new VideoQualityHandler.Dimension2D(352, 288));
        profiles.add(CamcorderProfile.QUALITY_QVGA);
        dimensions.add(new VideoQualityHandler.Dimension2D(320, 240));
        profiles.add(CamcorderProfile.QUALITY_LOW);
        dimensions.add(new VideoQualityHandler.Dimension2D(320, 240));
        video_quality_handler.initialiseVideoQualityFromProfiles(profiles, dimensions);

        List<String> video_quality = video_quality_handler.getSupportedVideoQuality();
        List<String> exp_video_quality = new ArrayList<>();
        exp_video_quality.add("" + CamcorderProfile.QUALITY_HIGH);
        exp_video_quality.add("" + CamcorderProfile.QUALITY_720P);
        exp_video_quality.add(CamcorderProfile.QUALITY_480P + "_r960x720");
        exp_video_quality.add(CamcorderProfile.QUALITY_480P + "_r768x576");
        exp_video_quality.add(CamcorderProfile.QUALITY_480P + "_r720x576");
        exp_video_quality.add(CamcorderProfile.QUALITY_480P + "_r800x480");
        exp_video_quality.add("" + CamcorderProfile.QUALITY_480P);
        exp_video_quality.add(CamcorderProfile.QUALITY_CIF + "_r640x480");
        exp_video_quality.add("" + CamcorderProfile.QUALITY_CIF);
        exp_video_quality.add("" + CamcorderProfile.QUALITY_QVGA);
        exp_video_quality.add(CamcorderProfile.QUALITY_LOW + "_r240x160");
        exp_video_quality.add(CamcorderProfile.QUALITY_LOW + "_r176x144");
        exp_video_quality.add(CamcorderProfile.QUALITY_LOW + "_r128x96");
        compareVideoQuality(video_quality, exp_video_quality);
    }

    /** Test for setting correct video resolutions and profiles.
     *  Case from https://sourceforge.net/p/opencamera/discussion/general/thread/b95bfb83/?limit=25#14ac
     */
    @Test
    public void testVideoResolutions4() {
        VideoQualityHandler video_quality_handler = new VideoQualityHandler();

        // Video quality: 4_r864x480, 4, 2
        // Video resolutions: 176x144, 480x320, 640x480, 864x480, 1280x720, 1920x1080
        List<CameraController.Size> video_sizes = new ArrayList<>();
        video_sizes.add(new CameraController.Size(176, 144));
        video_sizes.add(new CameraController.Size(480, 320));
        video_sizes.add(new CameraController.Size(640, 480));
        video_sizes.add(new CameraController.Size(864, 480));
        video_sizes.add(new CameraController.Size(1280, 720));
        video_sizes.add(new CameraController.Size(1920, 1080));
        video_quality_handler.setVideoSizes(video_sizes);
        video_quality_handler.sortVideoSizes();

        List<Integer> profiles = new ArrayList<>();
        List<VideoQualityHandler.Dimension2D> dimensions = new ArrayList<>();
        profiles.add(CamcorderProfile.QUALITY_HIGH);
        dimensions.add(new VideoQualityHandler.Dimension2D(1920, 1080));
        profiles.add(CamcorderProfile.QUALITY_480P);
        dimensions.add(new VideoQualityHandler.Dimension2D(640, 480));
        profiles.add(CamcorderProfile.QUALITY_QCIF);
        dimensions.add(new VideoQualityHandler.Dimension2D(176, 144));
        video_quality_handler.initialiseVideoQualityFromProfiles(profiles, dimensions);

        List<String> video_quality = video_quality_handler.getSupportedVideoQuality();
        List<String> exp_video_quality = new ArrayList<>();
        exp_video_quality.add("" + CamcorderProfile.QUALITY_HIGH);
        exp_video_quality.add(CamcorderProfile.QUALITY_480P + "_r1280x720");
        exp_video_quality.add(CamcorderProfile.QUALITY_480P + "_r864x480");
        exp_video_quality.add("" + CamcorderProfile.QUALITY_480P);
        exp_video_quality.add(CamcorderProfile.QUALITY_QCIF + "_r480x320");
        exp_video_quality.add("" + CamcorderProfile.QUALITY_QCIF);
        compareVideoQuality(video_quality, exp_video_quality);
    }

    /** Tests for Preview.getOptimalVideoPictureSize().
     *  Tests the choice of photo snapshot resolutions in video mode.
     */
    @Test
    public void testVideoPhotoResolution() {
        Log.d(TAG, "testVideoPhotoResolution");

        List<CameraController.Size> sizes = new ArrayList<>();
        sizes.add(new CameraController.Size(4640, 3480));
        sizes.add(new CameraController.Size(4640, 2610));
        sizes.add(new CameraController.Size(3488, 3488));
        sizes.add(new CameraController.Size(3840, 2160));
        sizes.add(new CameraController.Size(3456, 3456));
        sizes.add(new CameraController.Size(1920, 1080));
        sizes.add(new CameraController.Size(1728, 1728));
        sizes.add(new CameraController.Size(1440, 1080));

        CameraController.Size max_video_size1 = new CameraController.Size(3840, 2160);

        CameraController.Size photo_size1 = Preview.getOptimalVideoPictureSize(sizes, 16.0f/9.0f, max_video_size1);
        Log.d(TAG, "photo_size1: " + photo_size1.width + " x " + photo_size1.height);
        assertEquals(new CameraController.Size(3840, 2160), photo_size1);

        CameraController.Size photo_size1b = Preview.getOptimalVideoPictureSize(sizes, 1.0f, max_video_size1);
        Log.d(TAG, "photo_size1b: " + photo_size1b.width + " x " + photo_size1b.height);
        assertEquals(new CameraController.Size(1728, 1728), photo_size1b);

        CameraController.Size photo_size1c = Preview.getOptimalVideoPictureSize(sizes, 4.0f/3.0f, max_video_size1);
        Log.d(TAG, "photo_size1c: " + photo_size1c.width + " x " + photo_size1c.height);
        assertEquals(new CameraController.Size(1440, 1080), photo_size1c);

        CameraController.Size max_video_size2 = new CameraController.Size(1920, 1080);

        CameraController.Size photo_size2 = Preview.getOptimalVideoPictureSize(sizes, 16.0f/9.0f, max_video_size2);
        Log.d(TAG, "photo_size2: " + photo_size2.width + " x " + photo_size2.height);
        assertEquals(new CameraController.Size(1920, 1080), photo_size2);

        CameraController.Size photo_size2b = Preview.getOptimalVideoPictureSize(sizes, 1.0f, max_video_size2);
        Log.d(TAG, "photo_size2b: " + photo_size2b.width + " x " + photo_size2b.height);
        assertEquals(new CameraController.Size(1440, 1080), photo_size2b);

        CameraController.Size photo_size2c = Preview.getOptimalVideoPictureSize(sizes, 4.0f/3.0f, max_video_size2);
        Log.d(TAG, "photo_size2c: " + photo_size2c.width + " x " + photo_size2c.height);
        assertEquals(new CameraController.Size(1440, 1080), photo_size2c);

    }

    /** Test for choosing resolution for panorama mode.
     */
    @Test
    public void testPanoramaResolutions() {
        {
            List<CameraController.Size> sizes = new ArrayList<>();
            sizes.add(new CameraController.Size(4640, 3480));
            sizes.add(new CameraController.Size(4640, 2610));
            sizes.add(new CameraController.Size(3488, 3488));
            sizes.add(new CameraController.Size(3840, 2160));
            sizes.add(new CameraController.Size(3456, 3456));
            sizes.add(new CameraController.Size(1920, 1080));
            sizes.add(new CameraController.Size(1728, 1728));
            sizes.add(new CameraController.Size(1440, 1080));
            sizes.add(new CameraController.Size(1200, 900));

            CameraController.Size chosen_size = MyApplicationInterface.choosePanoramaResolution(sizes);
            assertEquals(chosen_size, new CameraController.Size(1440, 1080));
        }
        {
            List<CameraController.Size> sizes = new ArrayList<>();
            sizes.add(new CameraController.Size(4640, 3480));
            sizes.add(new CameraController.Size(4640, 2610));
            sizes.add(new CameraController.Size(3488, 3488));
            sizes.add(new CameraController.Size(3840, 2160));
            sizes.add(new CameraController.Size(3456, 3456));
            sizes.add(new CameraController.Size(1920, 1080));
            sizes.add(new CameraController.Size(1728, 1728));
            sizes.add(new CameraController.Size(1200, 900));

            CameraController.Size chosen_size = MyApplicationInterface.choosePanoramaResolution(sizes);
            assertEquals(chosen_size, new CameraController.Size(1200, 900));
        }
        {
            List<CameraController.Size> sizes = new ArrayList<>();
            sizes.add(new CameraController.Size(4640, 3480));
            sizes.add(new CameraController.Size(4640, 2610));
            sizes.add(new CameraController.Size(3488, 3488));
            sizes.add(new CameraController.Size(3840, 2160));
            sizes.add(new CameraController.Size(3456, 3456));
            sizes.add(new CameraController.Size(1920, 1080));
            sizes.add(new CameraController.Size(1728, 1728));

            // no 4:3 with width below 2080
            CameraController.Size chosen_size = MyApplicationInterface.choosePanoramaResolution(sizes);
            assertEquals(chosen_size, new CameraController.Size(1920, 1080));
        }
        {
            List<CameraController.Size> sizes = new ArrayList<>();
            sizes.add(new CameraController.Size(4640, 3480));
            sizes.add(new CameraController.Size(4640, 2610));
            sizes.add(new CameraController.Size(3488, 3488));
            sizes.add(new CameraController.Size(3840, 2160));
            sizes.add(new CameraController.Size(3456, 3456));
            sizes.add(new CameraController.Size(1728, 1728));

            // no 4:3 with width below 2080
            CameraController.Size chosen_size = MyApplicationInterface.choosePanoramaResolution(sizes);
            assertEquals(chosen_size, new CameraController.Size(1728, 1728));
        }
        {
            List<CameraController.Size> sizes = new ArrayList<>();
            sizes.add(new CameraController.Size(4640, 3480));
            sizes.add(new CameraController.Size(4640, 2610));
            sizes.add(new CameraController.Size(3488, 3488));
            sizes.add(new CameraController.Size(3840, 2160));
            sizes.add(new CameraController.Size(3456, 3456));

            // no resolutions with width below 2080
            CameraController.Size chosen_size = MyApplicationInterface.choosePanoramaResolution(sizes);
            assertEquals(chosen_size, new CameraController.Size(3456, 3456));
        }
    }

	/*@Test
	public void testExponentialScaling() {
		Log.d(TAG, "testExponentialScaling");
		assertEquals(100, (int)MainActivity.exponentialScaling(0.0f, 100, 1600));
		assertEquals(1600, (int)MainActivity.exponentialScaling(1.0f, 100, 1600));
	}*/

    @Test
    public void testFormatLevelAngle() {
        Log.d(TAG, "testFormatLevelAngle");

        assertEquals( "0.1", DrawPreview.formatLevelAngle(0.1));
        assertEquals( "1.2", DrawPreview.formatLevelAngle(1.21));
        assertEquals( "1.3", DrawPreview.formatLevelAngle(1.29));
        assertEquals( "0.0", DrawPreview.formatLevelAngle(0.0));
        assertEquals( "0.0", DrawPreview.formatLevelAngle(-0.0));
        assertEquals( "0.0", DrawPreview.formatLevelAngle(-0.0001));
        assertEquals( "-0.1", DrawPreview.formatLevelAngle(-0.1));
        assertEquals( "-10.7", DrawPreview.formatLevelAngle(-10.6753));
    }

    @Test
    public void testImageSaverQueueSize() {
        Log.d(TAG, "testImageSaverQueueSize");

        // if any of these values change, review the comments in ImageSaver.getQueueSize().

        assertTrue(ImageSaver.computeQueueSize(64) >= 6);

        assertTrue(ImageSaver.computeQueueSize(128) >= ImageSaver.computeQueueSize(64));

        assertTrue(ImageSaver.computeQueueSize(256) >= ImageSaver.computeQueueSize(128));
        assertTrue(ImageSaver.computeQueueSize(256) <= 19);

        assertTrue(ImageSaver.computeQueueSize(512) >= ImageSaver.computeQueueSize(256));
        assertTrue(ImageSaver.computeQueueSize(512) >= 34);
        assertTrue(ImageSaver.computeQueueSize(512) <= 70);
    }

    @Test
    public void testImageSaverRequestCost() {
        Log.d(TAG, "testImageSaverRequestCost");

        assertTrue( ImageSaver.computeRequestCost(true, 1) > ImageSaver.computeRequestCost(false, 1));
        assertEquals( ImageSaver.computeRequestCost(false, 3), 3*ImageSaver.computeRequestCost(false, 1));

    }

    private static class float4 {
        final float r, g, b, a;

        float4(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override
        public boolean equals(Object o) {
            if( !(o instanceof float4) )
                return false;
            float4 that = (float4)o;
            return this.r == that.r && this.g == that.g && this.b == that.b && this.a == that.a;
        }

        @Override
        public int hashCode() {
            // must override this, as we override equals()
            return (int)(531*r + 227*g + b*31 + a);
        }
    }

    /** Duplicates the code in avg_brighter.rs for median filter, to test this.
     *  Finds median of the supplied values, sorting by the alpha component.
     */
    @SuppressWarnings("UnusedAssignment")
    private float4 findMedian(float4 p0, float4 p1, float4 p2, float4 p3, float4 p4) {
        // old version
        /*if( p0.a > p1.a ) {
            float4 temp_p = p0;
            p0 = p1;
            p1 = temp_p;
        }
        if( p0.a > p2.a ) {
            float4 temp_p = p0;
            p0 = p2;
            p2 = temp_p;
        }
        if( p0.a > p3.a ) {
            float4 temp_p = p0;
            p0 = p3;
            p3 = temp_p;
        }
        if( p0.a > p4.a ) {
            float4 temp_p = p0;
            p0 = p4;
            p4 = temp_p;
        }
        //
        if( p1.a > p2.a ) {
            float4 temp_p = p1;
            p1 = p2;
            p2 = temp_p;
        }
        if( p1.a > p3.a ) {
            float4 temp_p = p1;
            p1 = p3;
            p3 = temp_p;
        }
        if( p1.a > p4.a ) {
            float4 temp_p = p1;
            p1 = p4;
            p4 = temp_p;
        }
        //
        if( p2.a > p3.a ) {
            float4 temp_p = p2;
            p2 = p3;
            p3 = temp_p;
        }
        if( p2.a > p4.a ) {
            float4 temp_p = p2;
            p2 = p4;
            p4 = temp_p;
        }*/

        // new version
        if( p0.a > p1.a ) {
            float4 temp_p = p0;
            p0 = p1;
            p1 = temp_p;
        }
        if( p3.a > p4.a ) {
            float4 temp_p = p3;
            p3 = p4;
            p4 = temp_p;
        }
        if( p0.a > p3.a ) {
            float4 temp_p = p0;
            p0 = p3;
            p3 = temp_p;

            temp_p = p1;
            p1 = p4;
            p4 = temp_p;
        }
        if( p1.a > p2.a ) {
            if( p2.a > p3.a ) {
                if( p2.a > p4.a ) {
                    p2 = p4;
                }
                // else median is p2
            }
            else {
                if( p1.a > p3.a ) {
                    p2 = p3;
                }
                else {
                    p2 = p1;
                }
            }
        }
        else {
            if( p1.a > p3.a ) {
                if( p1.a > p4.a ) {
                    p2 = p4;
                }
                else {
                    p2 = p1;
                }
            }
            else {
                if( p2.a > p3.a ) {
                    p2 = p3;
                }
                // else median is p2
            }
        }

        Log.d(TAG, "median is: " + p2.r + " , " + p2.g + " , " + p2.b + " , " + p2.a);
        return p2;
    }

    @Test
    public void testMedian() {
        Log.d(TAG, "testMedian");

        float4 m0 = findMedian(
                new float4(127, 0, 64, 127),
                new float4(49, 49, 49, 49),
                new float4(0, 0, 0, 0),
                new float4(120, 120, 121, 121),
                new float4(0, 51, 53, 53)
        );
        assertEquals(m0, new float4(0, 51, 53, 53));

        float4 m1 = findMedian(
                new float4(127, 0, 64, 127),
                new float4(49, 49, 71, 71),
                new float4(120, 120, 121, 121),
                new float4(127, 151, 64, 151),
                new float4(0, 51, 53, 53)
        );
        assertEquals(m1, new float4(120, 120, 121, 121));

        float4 m2 = findMedian(
                new float4(127, 0, 64, 127),
                new float4(49, 49, 71, 71),
                new float4(49, 49, 71, 71),
                new float4(120, 120, 121, 121),
                new float4(0, 51, 53, 53)
        );
        assertEquals(m2, new float4(49, 49, 71, 71));

        float4 m3 = findMedian(
                new float4(127, 0, 64, 127),
                new float4(49, 149, 71, 149),
                new float4(120, 120, 121, 121),
                new float4(27, 51, 64, 64),
                new float4(0, 51, 53, 53)
        );
        assertEquals(m3, new float4(120, 120, 121, 121));

        float4 m4 = findMedian(
                new float4(127, 0, 64, 127),
                new float4(49, 149, 71, 149),
                new float4(120, 120, 121, 121),
                new float4(27, 51, 64, 64),
                new float4(0, 51, 153, 153)
        );
        assertEquals(m4, new float4(127, 0, 64, 127));

        float4 m5 = findMedian(
                new float4(130, 0, 64, 130),
                new float4(49, 149, 71, 149),
                new float4(120, 120, 121, 121),
                new float4(127, 51, 64, 127),
                new float4(0, 51, 153, 153)
        );
        assertEquals(m5, new float4(130, 0, 64, 130));

        float4 m6 = findMedian(
                new float4(130, 0, 64, 130),
                new float4(49, 49, 71, 71),
                new float4(120, 120, 121, 121),
                new float4(27, 51, 64, 64),
                new float4(0, 0, 0, 0)
        );
        assertEquals(m6, new float4(49, 49, 71, 71));

        float4 m7 = findMedian(
                new float4(130, 0, 64, 130),
                new float4(49, 49, 71, 71),
                new float4(120, 120, 121, 121),
                new float4(27, 51, 64, 64),
                new float4(0, 100, 0, 100)
        );
        assertEquals(m7, new float4(0, 100, 0, 100));

        float4 m8 = findMedian(
                new float4(130, 0, 64, 130),
                new float4(49, 49, 71, 71),
                new float4(120, 181, 121, 181),
                new float4(27, 51, 164, 164),
                new float4(0, 100, 0, 100)
        );
        assertEquals(m8, new float4(130, 0, 64, 130));
    }

    @Test
    public void testBrightenFactors() {
        Log.d(TAG, "testBrightenFactors");

        // If any of these tests fail due to changes to HDRProcessor, consider that we might want to update the values tested in
        // computeBrightenFactors(), rather than simply updating the expected results, to preserve what the test is meant to test.

        //HDRProcessor.BrightenFactors brighten_factors = HDRProcessor.computeBrightenFactors(true,1600, 1000000000L/12, 20, 170);
        HDRProcessor.BrightenFactors brighten_factors = HDRProcessor.computeBrightenFactors(true,1600, 1000000000L/12, 42, 170);
        assertEquals(1.5f, brighten_factors.gain, 1.0e-5f);
        assertEquals(8.0f, brighten_factors.low_x, 0.1f);
        assertEquals(255.5f, brighten_factors.mid_x, 1.0e-5f);
        assertEquals(1.0f, brighten_factors.gamma, 1.0e-5f);

        // this checks for stability - we change the inputs so we enter "use piecewise function with gain and gamma", but
        // we should not significantly change the values of gain or low_x, and gamma should be close to 1
        brighten_factors = HDRProcessor.computeBrightenFactors(true,1600, 1000000000L/12, 42, 171);
        assertEquals(1.5f, brighten_factors.gain, 1.0e-5f);
        assertEquals(8.0f, brighten_factors.low_x, 0.1f);
        assertEquals(136.0f, brighten_factors.mid_x, 0.5f);
        assertEquals(1.0f, brighten_factors.gamma, 0.5f);

    }

    @Test
    public void testFocusBracketingDistances() {
        Log.d(TAG, "testFocusBracketingDistances");

        List<Float> focus_distances = CameraController2.setupFocusBracketingDistances(1.0f/0.1f, 1.0f/10.0f, 5);
        for(int i=0;i<focus_distances.size();i++)
            Log.d(TAG, i + ": " + focus_distances.get(i));
        assertEquals(5, focus_distances.size());
        assertEquals(1.0f/0.1f, focus_distances.get(0), 1.0e-5);
        // linear interpolation in distance:
		/*assertEquals(1.0f/2.575f, focus_distances.get(1), 1.0e-5);
		assertEquals(1.0f/5.05f, focus_distances.get(2), 1.0e-5);
		assertEquals(1.0f/7.525f, focus_distances.get(3), 1.0e-5);*/
        // log interpolation:
        assertEquals(1.0f/(0.138647f*(10.0f-0.1f) + 0.1f), focus_distances.get(1), 1.0e-5);
        assertEquals(1.0f/(0.317394f*(10.0f-0.1f) + 0.1f), focus_distances.get(2), 1.0e-5);
        assertEquals(1.0f/(0.569323f*(10.0f-0.1f) + 0.1f), focus_distances.get(3), 1.0e-5);
        assertEquals(1.0f/10.0f, focus_distances.get(4), 1.0e-5);

        focus_distances = CameraController2.setupFocusBracketingDistances(1.0f/10.0f, 1.0f/0.1f, 5);
        for(int i=0;i<focus_distances.size();i++)
            Log.d(TAG, i + ": " + focus_distances.get(i));
        assertEquals(5, focus_distances.size());
        // should be reverse of above
        assertEquals(1.0f/0.1f, focus_distances.get(4), 1.0e-5);
        // linear interpolation in distance:
		/*assertEquals(1.0f/2.575f, focus_distances.get(3), 1.0e-5);
		assertEquals(1.0f/5.05f, focus_distances.get(2), 1.0e-5);
		assertEquals(1.0f/7.525f, focus_distances.get(1), 1.0e-5);*/
        // log interpolation:
        assertEquals(1.0f/(0.138647f*(10.0f-0.1f) + 0.1f), focus_distances.get(3), 1.0e-5);
        assertEquals(1.0f/(0.317394f*(10.0f-0.1f) + 0.1f), focus_distances.get(2), 1.0e-5);
        assertEquals(1.0f/(0.569323f*(10.0f-0.1f) + 0.1f), focus_distances.get(1), 1.0e-5);
        assertEquals(1.0f/10.0f, focus_distances.get(0), 1.0e-5);

        focus_distances = CameraController2.setupFocusBracketingDistances(1.0f/0.1f, 1.0f/15.0f, 3);
        for(int i=0;i<focus_distances.size();i++)
            Log.d(TAG, i + ": " + focus_distances.get(i));
        assertEquals(3, focus_distances.size());
        assertEquals(1.0f/0.1f, focus_distances.get(0), 1.0e-5);
        // linear interpolation in distance:
        //assertEquals(1.0f/5.05f, focus_distances.get(1), 1.0e-5); // not 7.55, as we clamp distances to a max of 10m when averaging
        // log interpolation:
        assertEquals(1.0f/(0.369070f*(10.0f-0.1f) + 0.1f), focus_distances.get(1), 1.0e-5); // we clamp distances to a max of 10m when averaging
        assertEquals(1.0f/15.0f, focus_distances.get(2), 1.0e-5);

        focus_distances = CameraController2.setupFocusBracketingDistances(1.0f/15.0f, 1.0f/0.1f, 3);
        for(int i=0;i<focus_distances.size();i++)
            Log.d(TAG, i + ": " + focus_distances.get(i));
        assertEquals(3, focus_distances.size());
        // should be reverse of above
        assertEquals(1.0f/0.1f, focus_distances.get(2), 1.0e-5);
        // linear interpolation in distance:
        //assertEquals(1.0f/5.05f, focus_distances.get(1), 1.0e-5); // not 7.55, as we clamp distances to a max of 10m when averaging
        // log interpolation:
        assertEquals(1.0f/(0.369070f*(10.0f-0.1f) + 0.1f), focus_distances.get(1), 1.0e-5); // we clamp distances to a max of 10m when averaging
        assertEquals(1.0f/15.0f, focus_distances.get(0), 1.0e-5);

        focus_distances = CameraController2.setupFocusBracketingDistances(1.0f/0.1f, 1.0f/0.2f, 3);
        for(int i=0;i<focus_distances.size();i++)
            Log.d(TAG, i + ": " + focus_distances.get(i));
        assertEquals(3, focus_distances.size());
        assertEquals(1.0f/0.1f, focus_distances.get(0), 1.0e-5);
        // log interpolation:
        assertEquals(1.0f/(0.369070f*(0.2f-0.1f) + 0.1f), focus_distances.get(1), 1.0e-5);
        assertEquals(1.0f/0.2f, focus_distances.get(2), 1.0e-5);

        focus_distances = CameraController2.setupFocusBracketingDistances(1.0f/0.2f, 1.0f/0.1f, 3);
        for(int i=0;i<focus_distances.size();i++)
            Log.d(TAG, i + ": " + focus_distances.get(i));
        assertEquals(3, focus_distances.size());
        // should be reverse of above
        assertEquals(1.0f/0.1f, focus_distances.get(2), 1.0e-5);
        // log interpolation:
        assertEquals(1.0f/(0.369070f*(0.2f-0.1f) + 0.1f), focus_distances.get(1), 1.0e-5);
        assertEquals(1.0f/0.2f, focus_distances.get(0), 1.0e-5);
    }

    /** Tests MainActivity.processUserSaveLocation() (used for checking save locations when specifying user folders with non-SAF scoped storage).
     */
    @Test
    public void testprocessUserSaveLocation() {
        assertEquals("OpenCamera", MainActivity.processUserSaveLocation("OpenCamera"));

        assertEquals("OpenCamera", MainActivity.processUserSaveLocation("OpenCamera/"));

        assertEquals("", MainActivity.processUserSaveLocation(""));

        assertEquals("", MainActivity.processUserSaveLocation("/"));

        assertEquals("blah_a/blah_b", MainActivity.processUserSaveLocation("blah_a/blah_b"));

        assertEquals("blah_a/blah_b", MainActivity.processUserSaveLocation("blah_a/blah_b/"));

        assertEquals("blah_a/blah_b", MainActivity.processUserSaveLocation("blah_a//blah_b"));

        assertEquals("blah_a/blah_b", MainActivity.processUserSaveLocation("blah_a///blah_b"));

        assertEquals("blah_a/blah_b/blah_c", MainActivity.processUserSaveLocation("blah_a///blah_b/blah_c//"));

        assertEquals("OpenCamera", MainActivity.processUserSaveLocation("/OpenCamera"));

        assertEquals("OpenCamera", MainActivity.processUserSaveLocation("//OpenCamera"));

        assertEquals("OpenCamera", MainActivity.processUserSaveLocation("///OpenCamera"));

        assertEquals("blah_a/blah_b/blah_c", MainActivity.processUserSaveLocation("/blah_a///blah_b/blah_c//"));

    }

    /** Tests MainActivity.checkSaveLocation() (used for checking save locations when updating to scoped storage).
     */
    @Test
    public void testCheckSaveLocation() {
        Log.d(TAG, "testCheckSaveLocation");

        // Should be same as Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()
        // - needed as Environment is not mocked.
        final String dcim_path = "/storage/emulated/0/DCIM";

        MainActivity.CheckSaveLocationResult res;

        res = MainActivity.checkSaveLocation("");
        assertEquals(new MainActivity.CheckSaveLocationResult(true, null), res);

        res = MainActivity.checkSaveLocation("OpenCamera");
        assertEquals(new MainActivity.CheckSaveLocationResult(true, null), res);

        res = MainActivity.checkSaveLocation("blah_a/blah_b");
        assertEquals(new MainActivity.CheckSaveLocationResult(true, null), res);

        res = MainActivity.checkSaveLocation("OpenCamera/");
        assertEquals(new MainActivity.CheckSaveLocationResult(true, null), res);

        res = MainActivity.checkSaveLocation("blah_a/blah_b/");
        assertEquals(new MainActivity.CheckSaveLocationResult(true, null), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/DCIM/OpenCamera/subfolder/", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, "OpenCamera/subfolder/"), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/DCIM/OpenCamera/subfolder", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, "OpenCamera/subfolder"), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/DCIM/OpenCamera/", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, "OpenCamera/"), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/DCIM/OpenCamera", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, "OpenCamera"), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/DCIM/", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, ""), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/DCIM", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, ""), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0/Pictures", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, null), res);

        res = MainActivity.checkSaveLocation("/storage/emulated/0", dcim_path);
        assertEquals(new MainActivity.CheckSaveLocationResult(false, null), res);
    }

    @Test
    public void sortLuminanceInfo() {
        Log.d(TAG, "sortLuminanceInfo");

        List<HDRProcessor.LuminanceInfo> luminanceInfos = new ArrayList<>();
        List<HDRProcessor.LuminanceInfo> luminanceInfosSorted;

        //noinspection RedundantOperationOnEmptyContainer
        luminanceInfos.clear();
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(0, 64, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(16, 80, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(33, 116, 255, false));
        luminanceInfosSorted = new ArrayList<>(luminanceInfos);
        Collections.sort(luminanceInfosSorted);
        assertEquals(luminanceInfos.size(), luminanceInfosSorted.size());
        assertEquals(luminanceInfos.get(0), luminanceInfosSorted.get(0));
        assertEquals(luminanceInfos.get(1), luminanceInfosSorted.get(1));
        assertEquals(luminanceInfos.get(2), luminanceInfosSorted.get(2));

        luminanceInfos.clear();
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(16, 80,255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(0, 64, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(33, 116, 255, false));
        luminanceInfosSorted = new ArrayList<>(luminanceInfos);
        Collections.sort(luminanceInfosSorted);
        assertEquals(luminanceInfos.size(), luminanceInfosSorted.size());
        assertEquals(luminanceInfos.get(1), luminanceInfosSorted.get(0));
        assertEquals(luminanceInfos.get(0), luminanceInfosSorted.get(1));
        assertEquals(luminanceInfos.get(2), luminanceInfosSorted.get(2));

        luminanceInfos.clear();
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(33, 116, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(0, 64, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(16, 80, 255, false));
        luminanceInfosSorted = new ArrayList<>(luminanceInfos);
        Collections.sort(luminanceInfosSorted);
        assertEquals(luminanceInfos.size(), luminanceInfosSorted.size());
        assertEquals(luminanceInfos.get(1), luminanceInfosSorted.get(0));
        assertEquals(luminanceInfos.get(2), luminanceInfosSorted.get(1));
        assertEquals(luminanceInfos.get(0), luminanceInfosSorted.get(2));

        // case that requires using min value as well as median value
        luminanceInfos.clear();
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(93, 255, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(68, 255, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(17, 193, 255, false));
        luminanceInfosSorted = new ArrayList<>(luminanceInfos);
        Collections.sort(luminanceInfosSorted);
        assertEquals(luminanceInfos.size(), luminanceInfosSorted.size());
        assertEquals(luminanceInfos.get(2), luminanceInfosSorted.get(0));
        assertEquals(luminanceInfos.get(1), luminanceInfosSorted.get(1));
        assertEquals(luminanceInfos.get(0), luminanceInfosSorted.get(2));

        // case that should never use min value
        luminanceInfos.clear();
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(60, 255, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(70, 240, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(80, 95, 255, false));
        luminanceInfosSorted = new ArrayList<>(luminanceInfos);
        Collections.sort(luminanceInfosSorted);
        assertEquals(luminanceInfos.size(), luminanceInfosSorted.size());
        assertEquals(luminanceInfos.get(2), luminanceInfosSorted.get(0));
        assertEquals(luminanceInfos.get(1), luminanceInfosSorted.get(1));
        assertEquals(luminanceInfos.get(0), luminanceInfosSorted.get(2));

        // case that requires using hi value as well as median and min values
        luminanceInfos.clear();
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(17, 31, 100, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(34, 127, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(93, 255, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(68, 255, 255, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(0, 0, 90, false));
        luminanceInfos.add(new HDRProcessor.LuminanceInfo(0, 0, 80, false));
        luminanceInfosSorted = new ArrayList<>(luminanceInfos);
        Collections.sort(luminanceInfosSorted);
        assertEquals(luminanceInfos.size(), luminanceInfosSorted.size());
        assertEquals(luminanceInfos.get(5), luminanceInfosSorted.get(0));
        assertEquals(luminanceInfos.get(4), luminanceInfosSorted.get(1));
        assertEquals(luminanceInfos.get(0), luminanceInfosSorted.get(2));
        assertEquals(luminanceInfos.get(1), luminanceInfosSorted.get(3));
        assertEquals(luminanceInfos.get(3), luminanceInfosSorted.get(4));
        assertEquals(luminanceInfos.get(2), luminanceInfosSorted.get(5));

    }

    private void roundTripWhiteBalanceTemperature(int temperature) {
        float [] rggb = CameraController2.convertTemperatureToRggb(temperature);
        Log.d(TAG, "red: " + rggb[0]);
        Log.d(TAG, "green even: " + rggb[1]);
        Log.d(TAG, "green odd: " + rggb[2]);
        Log.d(TAG, "blue: " + rggb[3]);
        int new_temperature = CameraController2.convertRggbToTemperature(rggb);
        assertEquals(temperature, new_temperature);
    }

    @Test
    public void whiteBalanceTemperature() {
        Log.d(TAG, "whiteBalanceTemperature");

        // n.b., round trip won't work for low temperatures due to hitting max gain
        roundTripWhiteBalanceTemperature(3000);
        roundTripWhiteBalanceTemperature(4000);
        roundTripWhiteBalanceTemperature(5000);
        roundTripWhiteBalanceTemperature(6000);
        roundTripWhiteBalanceTemperature(6600);
        roundTripWhiteBalanceTemperature(7000);
        roundTripWhiteBalanceTemperature(8000);
        roundTripWhiteBalanceTemperature(9000);
        roundTripWhiteBalanceTemperature(10000);
        roundTripWhiteBalanceTemperature(12000);
        roundTripWhiteBalanceTemperature(15000);
    }

    @Test
    public void testNRSceneIsLowLight() {
        Log.d(TAG, "testNRSceneIsLowLight");

        // Galaxy S10e:
        assertFalse(HDRProcessor.sceneIsLowLight(1000, 1000000000L/25));
        assertFalse(HDRProcessor.sceneIsLowLight(1600, 1000000000L/25));
        assertTrue(HDRProcessor.sceneIsLowLight(3200, 1000000000L/17));
        assertTrue(HDRProcessor.sceneIsLowLight(800, 1000000000L/5));
        assertTrue(HDRProcessor.sceneIsLowLight(400, 1000000000L/5));

        // Nokia 8:
        assertFalse(HDRProcessor.sceneIsLowLight(800, 1000000000L/14));
        assertFalse(HDRProcessor.sceneIsLowLight(752, 1000000000L/10)); // see testAvg36
        assertFalse(HDRProcessor.sceneIsLowLight(1044, 1000000000L/10)); // see testAvg23
        assertTrue(HDRProcessor.sceneIsLowLight(1505, 1000000000L/10)); // see testAvg49
        assertTrue(HDRProcessor.sceneIsLowLight(1551, 1000000000L/10));
        assertTrue(HDRProcessor.sceneIsLowLight(1600, 1000000000L/3)); // see testAvg51
        assertTrue(HDRProcessor.sceneIsLowLight(1600, 1000000000L/11)); // see testHDR51

        // Nexus 6:
        assertFalse(HDRProcessor.sceneIsLowLight(749, 1000000000L/12)); // see testAvg47
        assertFalse(HDRProcessor.sceneIsLowLight(1000, 1000000000L/12));
        assertTrue(HDRProcessor.sceneIsLowLight(1196, 1000000000L/12));

        // misc:
        assertTrue(HDRProcessor.sceneIsLowLight(1600, 1000000000L/17)); // see testAvg1
    }

    @Test
    public void testISOButtonStrings() {
        Log.d(TAG, "testISOButtonStrings");

        int [] iso_button_values = {50, 100, 200, 400, 800, 1600, 3200, 6400};
        for(int current_iso=1;current_iso<=10000;current_iso++) {
            //Log.d(TAG, "current_iso: " + current_iso);
            int index = -1;
            for(int i=0;i<iso_button_values.length && index==-1;i++) {
                if( iso_button_values[i] == current_iso ) {
                    index = i;
                }
            }
            // should only match the same button!
            for(int i=0;i<iso_button_values.length;i++) {
                String button_text = PopupView.getButtonOptionString(false, "ISO", MainUI.ISOToButtonText(iso_button_values[i]));
                //Log.d(TAG, "    i = " + i + " iso: " + iso_button_values[i] + " : " + button_text);
                assertEquals(i==index, MainUI.ISOTextEquals(button_text, String.valueOf(current_iso)));
            }
        }
    }

    private void checkCameraController2ZoomRatios(float min_zoom, float max_zoom) {
        List<Integer> ratios = new ArrayList<>();

        int zoom_value_1x = CameraController2.computeZoomRatios(ratios, min_zoom, max_zoom);
        assertEquals(100, (int)ratios.get(zoom_value_1x));
        if( min_zoom == 1.0f ) {
            assertEquals(0, zoom_value_1x);
        }
        else {
            assertTrue(zoom_value_1x > 0);
        }

        int zoom_ratio = 100;
        while( zoom_ratio <= (int)(100*max_zoom + 0.5) ) {
            assertTrue(ratios.contains(zoom_ratio));
            zoom_ratio *= 2;
        }
    }

    @Test
    public void testCameraController2ZoomRatios() {
        Log.d(TAG, "testCameraController2ZoomRatios");

        checkCameraController2ZoomRatios(1.0f, 1.0f);

        checkCameraController2ZoomRatios(1.0f, 2.0f);
        checkCameraController2ZoomRatios(1.0f, 4.0f);
        checkCameraController2ZoomRatios(1.0f, 8.0f);
        checkCameraController2ZoomRatios(1.0f, 10.0f);
        checkCameraController2ZoomRatios(1.0f, 16.0f);
        checkCameraController2ZoomRatios(1.0f, 20.0f);

        checkCameraController2ZoomRatios(0.7f, 4.0f);
        checkCameraController2ZoomRatios(0.7f, 8.0f);
        checkCameraController2ZoomRatios(0.7f, 10.0f);
        checkCameraController2ZoomRatios(0.7f, 16.0f);
        checkCameraController2ZoomRatios(0.7f, 20.0f);
    }

    private void checkAdjustResolutionForVideoCapabilities(int video_width, int video_height, ImageSaver.IntRange supported_widths, ImageSaver.IntRange supported_heights, int width_alignment, int height_alignment, int expected_width, int expected_height) {
        Log.d(TAG, "test size: " + video_width + " x " + video_height);
        CameraController.Size size = ImageSaver.adjustResolutionForVideoCapabilities(video_width, video_height, supported_widths, supported_heights, width_alignment, height_alignment);
        Log.d(TAG, "    adjusted size: " + size.width + " x " + size.height);
        assertEquals(expected_width, size.width);
        assertEquals(expected_height, size.height);
    }

    /** Tests for ImageSaver.adjustResolutionForVideoCapabilities().
     */
    @Test
    public void testAdjustResolutionForVideoCapabilities() {
        Log.d(TAG, "testAdjustResolutionForVideoCapabilities");

        checkAdjustResolutionForVideoCapabilities(1920, 1440,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                1920, 1440);
        checkAdjustResolutionForVideoCapabilities(1440, 1920,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                1440, 1920);

        checkAdjustResolutionForVideoCapabilities(2560, 1920,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                2560, 1920);
        checkAdjustResolutionForVideoCapabilities(1920, 2560,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                1632, 2176);

        checkAdjustResolutionForVideoCapabilities(2800, 2000,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                2800, 2000);
        checkAdjustResolutionForVideoCapabilities(2000, 2800,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                1560, 2176);

        checkAdjustResolutionForVideoCapabilities(3840, 2160,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                3840, 2160);
        checkAdjustResolutionForVideoCapabilities(2160, 3840,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                1224, 2176);

        checkAdjustResolutionForVideoCapabilities(2560, 1280,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                2560, 1280);
        checkAdjustResolutionForVideoCapabilities(1280, 2560,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                1088, 2176);

        checkAdjustResolutionForVideoCapabilities(176, 144,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                176, 144);
        checkAdjustResolutionForVideoCapabilities(144, 176,
                new ImageSaver.IntRange(160, 3840), new ImageSaver.IntRange(128, 2176),
                8, 8,
                160, 200);
    }

    /** Tests for CameraController2.sizeSubset().
     */
    @Test
    public void testSizeSubset() {
        Log.d(TAG, "testSizeSubset");
        assertTrue(CameraController2.sizeSubset(null, null, null, null));
        assertTrue(CameraController2.sizeSubset(null, null, new int[]{1920}, new int[]{1080}));
        assertFalse(CameraController2.sizeSubset(new int[]{1920}, new int[]{1080}, null, null));

        assertTrue(CameraController2.sizeSubset(new int[]{1920}, new int[]{1080}, new int[]{1920}, new int[]{1080}));
        assertTrue(CameraController2.sizeSubset(new int[]{1920, 1280}, new int[]{1080, 720}, new int[]{1920, 1280}, new int[]{1080, 720}));
        assertTrue(CameraController2.sizeSubset(new int[]{1280, 1920}, new int[]{720, 1080}, new int[]{1920, 1280}, new int[]{1080, 720}));
        assertTrue(CameraController2.sizeSubset(new int[]{1920}, new int[]{1080}, new int[]{1920, 1280}, new int[]{1080, 720}));
        assertTrue(CameraController2.sizeSubset(new int[]{1920}, new int[]{1080}, new int[]{1280, 1920}, new int[]{720, 1080}));

        assertFalse(CameraController2.sizeSubset(new int[]{1920, 1280}, new int[]{1080, 720}, new int[]{1920}, new int[]{1080}));
        assertFalse(CameraController2.sizeSubset(new int[]{1920, 1280}, new int[]{1080, 720}, new int[]{2380}, new int[]{720}));
    }
}
