package com.linkedcamera.app.cameracontroller;

import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.util.Log;

import com.linkedcamera.app.MyDebug;

import java.io.IOException;
import java.io.OutputStream;

/** Wrapper class to store DngCreator and Image.
 */
public class RawImage {
    private static final String TAG = "RawImage";

    private final DngCreator dngCreator;
    private final Image image;

    public RawImage(DngCreator dngCreator, Image image) {
        this.dngCreator = dngCreator;
        this.image = image;
    }

    /** Writes the dng file to the supplied output.
     */
    public void writeImage(OutputStream dngOutput) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "writeImage");
        try {
            dngCreator.writeImage(dngOutput, image);
        }
        catch(AssertionError e) {
            // have had AssertionError from OnePlus 5 on Google Play; rethrow as an IOException so it's handled
            // in the same way
            MyDebug.logStackTrace(TAG, "failed to write SNG image", e);
            throw new IOException();
        }
        catch(IllegalStateException e) {
            // have had IllegalStateException from Galaxy Note 8 on Google Play; rethrow as an IOException so it's handled
            // in the same way
            MyDebug.logStackTrace(TAG, "failed to write SNG image", e);
            throw new IOException();
        }
    }

    /** Closes the image. Must be called to free up resources when no longer needed. After calling
     *  this method, this object should not be used.
     */
    public void close() {
        if( MyDebug.LOG )
            Log.d(TAG, "close");
        image.close();
        dngCreator.close();
    }
}
