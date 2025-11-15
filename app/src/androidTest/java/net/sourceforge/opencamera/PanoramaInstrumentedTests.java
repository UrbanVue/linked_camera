package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests for Panorama algorithm - only need to run on a single device
 *  Should manually look over the images dumped onto DCIM/
 *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
 *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
 *  time to transfer to the device every time we run the tests.
 *  On Android 10+, scoped storage permission needs to be given to Open Camera for the DCIM/testOpenCamera/ folder.
 */
@RunWith(Categories.class)
@Categories.IncludeCategory(PanoramaTests.class)
@Suite.SuiteClasses({InstrumentedTest.class})
public class PanoramaInstrumentedTests {}
