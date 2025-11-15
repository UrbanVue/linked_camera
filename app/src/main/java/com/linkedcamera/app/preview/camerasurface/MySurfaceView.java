package com.linkedcamera.app.preview.camerasurface;

import com.linkedcamera.app.MyDebug;
import com.linkedcamera.app.cameracontroller.CameraController;
import com.linkedcamera.app.cameracontroller.CameraControllerException;
import com.linkedcamera.app.preview.Preview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

/** Provides support for the surface used for the preview, using a SurfaceView.
 */
public class MySurfaceView extends SurfaceView implements CameraSurface {
    private static final String TAG = "MySurfaceView";

    private final Preview preview;
    private final int [] measure_spec = new int[2];
    private final Handler handler = new Handler();
    private final Runnable tick;

    public
    MySurfaceView(Context context, final Preview preview) {
        super(context);
        this.preview = preview;
        if( MyDebug.LOG ) {
            Log.d(TAG, "new MySurfaceView");
        }

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        getHolder().addCallback(preview);
        // deprecated setting, but required on Android versions prior to 3.0
        //getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated

        tick = new Runnable() {
            public void run() {
				/*if( MyDebug.LOG )
					Log.d(TAG, "invalidate()");*/
                preview.test_ticker_called = true;
                invalidate();
                handler.postDelayed(this, preview.getFrameRate());
            }
        };
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void setPreviewDisplay(CameraController camera_controller) {
        if( MyDebug.LOG )
            Log.d(TAG, "setPreviewDisplay");
        try {
            camera_controller.setPreviewDisplay(this.getHolder());
        }
        catch(CameraControllerException e) {
            MyDebug.logStackTrace(TAG, "failed to set preview display", e);
        }
    }

    @Override
    public void setVideoRecorder(MediaRecorder video_recorder) {
        video_recorder.setPreviewDisplay(this.getHolder().getSurface());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return preview.touchEvent(event);
    }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        preview.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if( MyDebug.LOG )
            Log.d(TAG, "onMeasure: " + widthSpec + " x " + heightSpec);
        preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
        super.onMeasure(measure_spec[0], measure_spec[1]);
    }

    @Override
    public void setTransform(Matrix matrix) {
        if( MyDebug.LOG )
            Log.d(TAG, "setting transforms not supported for MySurfaceView");
        throw new RuntimeException();
    }

    @Override
    public void onPause() {
        if( MyDebug.LOG )
            Log.d(TAG, "onPause()");
        handler.removeCallbacks(tick);
    }

    @Override
    public void onResume() {
        if( MyDebug.LOG )
            Log.d(TAG, "onResume()");
        tick.run();
    }
}
