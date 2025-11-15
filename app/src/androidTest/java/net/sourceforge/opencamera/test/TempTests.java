package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TempTests {
    // Dummy test suite for running an arbitrary subset of tests.
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        //suite.addTest(TestSuite.createTest(MainActivityTest.class, "testZoom"));

        return suite;
    }
}
