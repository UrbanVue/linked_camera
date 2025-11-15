package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

import net.sourceforge.opencamera.TestUtils;

public class VideoTests {
    // Tests related to video recording; note that tests to do with video mode that don't record are still part of MainTests
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));
        // put these tests first as they require various permissions be allowed, that can only be set by user action:
        if( !TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoAudioControl"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSAF"));
        if( !TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitles"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitlesSAF"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitlesGPSSAF"));
        }
        if( TestUtils.test_camera2 ) {
            // tests for video log profile (but these don't actually record video)
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile1"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile2"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile3"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile1_extra_strong"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile2_extra_strong"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLogProfile3_extra_strong"));
        }

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideoDurationLimit"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testImmersiveMode"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testImmersiveModeEverything"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoStabilization"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoExposureLock"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoFocusArea"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoQuick"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoQuickSAF"));
        if( !TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxDuration"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxDurationRestart"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxDurationRestartInterrupt"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSettings"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMacro"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoPause"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoPauseStop"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshot"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshotTimer"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshotPausePreview"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSnapshotMax"));
        if( !TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoFlashVideo"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoTimerInterrupt"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoPopup"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoTimerPopup"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoAvailableMemory"));
        if( !TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoAvailableMemory2"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxFileSize1"));
        if( !TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxFileSize2"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxFileSize3"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxFileSize4"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoMaxFileSize4SAF"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoTimeLapse"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoForceFailure"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoForceFailureSAF"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoForceIOException"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoForceCameraControllerException"));
        if( TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoLogProfile"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoJTLogProfile"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoGammaProfile"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testVideoEdgeModeNoiseReductionMode"));
        }
        // put tests which change bitrate, fps or test 4K at end
        if( TestUtils.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoFPSHighSpeedManual"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSlowMotion"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoFPS"));
        }
        // update: now deprecating these tests, as setting these settings can be dodgy on some devices
        /*suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoBitrate"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo4K"));*/

        return suite;
    }
}
