package net.sourceforge.opencamera.test;

import android.os.Build;

import junit.framework.Test;
import junit.framework.TestSuite;

import net.sourceforge.opencamera.TestUtils;

public class Nexus7Tests {
    // Tests to run specifically on Nexus 7
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        // we run the following tests on the Nexus 7 as a device that supports SAF, but doesn't have Android 7+ (where we use alternative methods for read/writing Exif tags without needing File)
        // update: we now (as of 1.48.2) use the same codepaths for exif tags for before and after Android 7, but might as well keep these tests here anyway
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoSAF"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoStampSAF"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testDirectionOnSAF"));

        // tests useful for device with no flash, and only 1 camera
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusFlashAvailability"));

        // tests for testing Camera2 API with LEGACY Camera2 functionality
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        if( TestUtils.isEmulator() && ( Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP || Build.VERSION.SDK_INT == Build.VERSION_CODES.M ) ) {
            // video doesn't work on Android 5 or 6 emulator!
        }
        else {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));
        }

        return suite;
    }
}
