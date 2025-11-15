package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests related to video recording; note that tests to do with video mode that don't record are still part of MainTests.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(VideoTests.class)
@Suite.SuiteClasses({InstrumentedTest.class})
public class VideoInstrumentedTests {}
