package net.sourceforge.opencamera.test;

import android.os.Build;

import junit.framework.Test;
import junit.framework.TestSuite;

import net.sourceforge.opencamera.TestUtils;

public class OldDeviceTests {
    // Small set of tests to run on very old devices.
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        // put these tests first as they require various permissions be allowed, that can only be set by user action
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchVideo"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPause"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveModes"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusFlashAvailability"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testGallery"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettings"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettingsSaveLoad"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFolderChooserNew"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFolderChooserInvalid"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveFolderHistory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettingsPrivacyPolicy"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationOn"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelLowMemory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAngles"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAnglesLowMemory"));

        if( TestUtils.isEmulator() && (  Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP || Build.VERSION.SDK_INT == Build.VERSION_CODES.M ) ) {
            // video doesn't work on Android 5 or 6 emulator!
        }
        else {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitles"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideo"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideoDurationLimit"));
        }

        return suite;
    }
}
