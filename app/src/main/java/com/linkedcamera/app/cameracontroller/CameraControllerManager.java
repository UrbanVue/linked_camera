package com.linkedcamera.app.cameracontroller;

import android.content.Context;
import android.util.SizeF;

/** Provides additional support related to the Android camera APIs. This is to
 *  support functionality that doesn't require a camera to have been opened.
 */
public abstract class CameraControllerManager {
    public abstract int getNumberOfCameras();
    /** Returns whether the supplied cameraId is front, back or external.
     */
    public abstract CameraController.Facing getFacing(int cameraId);

    /** Tries to return a textual description for the camera, such as front/back, along with extra
     *  details if possible such as "ultra-wide". Will be null if no description can be determined.
     */
    public abstract String getDescription(Context context, int cameraId);

    public static class CameraInfo {
        public SizeF view_angle;
    }

    /** Version of getDescription() that supports Camera2 camera ID strings (used for physical cameras), also returns the
     *  view angles in info, if info is non-null.
     */
    public abstract String getDescription(CameraInfo info, Context context, String cameraIdS, boolean include_type, boolean include_angles);
}
