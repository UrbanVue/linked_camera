package com.linkedcamera.app;

import android.util.Log;

/** Helper class for logging.
 */
public class MyDebug {
    /** Global constant to control logging, should always be set to false in
     *  released versions.
     */
    public static final boolean LOG = false;

    /** Wrapper to print exceptions, should use instead of e.printStackTrace().
     */
    public static void logStackTrace(String tag, String msg, Throwable tr) {
        if( LOG ) {
            // don't log exceptions in releases
            Log.e(tag, msg, tr);
        }
    }
}
