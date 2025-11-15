package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class MultiCameraTests {
    // Tests to run specifically on devices where MainActivity.isMultiCamEnabled() returns true.
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIconsAgainstCameras"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraAll"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraMulti"));

        return suite;
    }
}
