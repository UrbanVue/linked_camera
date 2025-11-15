package com.linkedcamera.app;

import com.linkedcamera.app.cameracontroller.CameraController;
import com.linkedcamera.app.cameracontroller.RawImage;
import com.linkedcamera.app.preview.ApplicationInterface;
import com.linkedcamera.app.preview.Preview;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
//import android.location.Address; // don't use until we have info for data privacy!
//import android.location.Geocoder; // don't use until we have info for data privacy!
import android.location.Location;

import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

/** Handles the saving (and any required processing) of photos.
 */
public class ImageSaver extends Thread {
    private static final String TAG = "ImageSaver";

    static final String hdr_suffix = "_HDR";
    static final String nr_suffix = "_NR";
    static final String pano_suffix = "_PANO";

    private final Paint p = new Paint();

    private final MainActivity main_activity;
    private final HDRProcessor hdrProcessor;
    private final PanoramaProcessor panoramaProcessor;

    /* We use a separate count n_images_to_save, rather than just relying on the queue size, so we can take() an image from queue,
     * but only decrement the count when we've finished saving the image.
     * In general, n_images_to_save represents the number of images still to process, including ones currently being processed.
     * Therefore we should always have n_images_to_save >= queue.size().
     * Also note, main_activity.imageQueueChanged() should be called on UI thread after n_images_to_save increases or
     * decreases.
     * Access to n_images_to_save should always be synchronized to this (i.e., the ImageSaver class).
     * n_real_images_to_save excludes "Dummy" or "on_destroy" requests, and should also be synchronized, and modified
     * at the same time as n_images_to_save.
     */
    private int n_images_to_save = 0;
    private int n_real_images_to_save = 0;
    private final int queue_capacity;
    private final BlockingQueue<Request> queue;
    private final static int queue_cost_jpeg_c = 1; // also covers WEBP
    private final static int queue_cost_dng_c = 6;
    //private final static int queue_cost_dng_c = 1;

    // Should be same as MainActivity.app_is_paused, but we keep our own copy to make threading easier (otherwise, all
    // accesses of MainActivity.app_is_paused would need to be synchronized).
    // Access to app_is_paused should always be synchronized to this (i.e., the ImageSaver class).
    private boolean app_is_paused = true;

    // for testing; must be volatile for test project reading the state
    // n.b., avoid using static, as static variables are shared between different instances of an application,
    // and won't be reset in subsequent tests in a suite!
    public static volatile boolean test_small_queue_size; // needs to be static, as it needs to be set before activity is created to take effect
    public volatile boolean test_slow_saving;
    public volatile boolean test_queue_blocked;

    static class Request {
        enum Type {
            JPEG, // also covers WEBP
            RAW,
            DUMMY,
            ON_DESTROY // indicate that application is being destroyed, so should exit thread
        }
        final Type type;
        enum ProcessType {
            NORMAL,
            HDR, // also covers DRO, if only 1 image in the request
            AVERAGE,
            PANORAMA,
            X_NIGHT
        }
        final ProcessType process_type; // for type==JPEG
        final boolean force_suffix; // affects filename suffixes for saving jpeg_images: if true, filenames will always be appended with a suffix like _0, even if there's only 1 image in jpeg_images
        final int suffix_offset; // affects filename suffixes for saving jpeg_images, when force_suffix is true or there are multiple images in jpeg_images: the suffixes will be offset by this number
        enum SaveBase {
            SAVEBASE_NONE,
            SAVEBASE_FIRST,
            SAVEBASE_ALL,
            SAVEBASE_ALL_PLUS_DEBUG // for PANORAMA
        }
        final SaveBase save_base; // whether to save the base images, for process_type HDR, AVERAGE or PANORAMA
        /* jpeg_images: for jpeg (may be null otherwise).
         * If process_type==HDR, this should be 1 or 3 images, and the images are combined/converted to a HDR image (if there's only 1
         * image, this uses fake HDR or "DRO").
         * If process_type==NORMAL, then multiple images are saved sequentially.
         */
        final List<byte []> jpeg_images;
        final List<Bitmap> preshot_bitmaps; // if non-null, bitmaps for preshots; bitmaps will be recycled once processed
        final RawImage raw_image; // for raw
        final boolean image_capture_intent;
        final Uri image_capture_intent_uri;
        final boolean using_camera2;
        final boolean using_camera_extensions;
        /* image_format allows converting the standard JPEG image into another file format.
#        */
        enum ImageFormat {
            STD, // leave unchanged from the standard JPEG format
            WEBP,
            PNG
        }
        ImageFormat image_format;
        int image_quality;
        boolean do_auto_stabilise;
        final double level_angle; // in degrees
        final List<float []> gyro_rotation_matrix; // used for panorama (one 3x3 matrix per jpeg_images entry), otherwise can be null
        boolean panorama_dir_left_to_right; // used for panorama
        float camera_view_angle_x; // used for panorama
        float camera_view_angle_y; // used for panorama
        final boolean is_front_facing;
        boolean mirror;
        final Date current_date;
        final HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm; // for HDR
        final String preference_hdr_contrast_enhancement; // for HDR
        final int iso; // not applicable for RAW image
        final long exposure_time; // not applicable for RAW image
        final float zoom_factor; // not applicable for RAW image
        String preference_stamp;
        String preference_textstamp;
        final int font_size;
        final int color;
        final String pref_style;
        final String preference_stamp_dateformat;
        final String preference_stamp_timeformat;
        final String preference_stamp_gpsformat;
        //final String preference_stamp_geo_address;
        final String preference_units_distance;
        final boolean panorama_crop; // used for panorama
        enum RemoveDeviceExif {
            OFF, // don't remove any device exif tags
            ON, // remove all device exif tags
            KEEP_DATETIME // remove all device exif tags except datetime tags
        }
        final RemoveDeviceExif remove_device_exif;
        final boolean store_location;
        final Location location;
        final boolean store_geo_direction;
        final double geo_direction; // in radians
        final boolean store_ypr; // whether to store geo_angle, pitch_angle, level_angle in USER_COMMENT if exif (for JPEGs)
        final double pitch_angle; // the pitch that the phone is at, in degrees
        final String custom_tag_artist;
        final String custom_tag_copyright;
        final int sample_factor; // sampling factor for thumbnail, higher means lower quality

        Request(Type type,
                ProcessType process_type,
                boolean force_suffix,
                int suffix_offset,
                SaveBase save_base,
                List<byte []> jpeg_images,
                List<Bitmap> preshot_bitmaps,
                RawImage raw_image,
                boolean image_capture_intent, Uri image_capture_intent_uri,
                boolean using_camera2, boolean using_camera_extensions,
                ImageFormat image_format, int image_quality,
                boolean do_auto_stabilise, double level_angle, List<float []> gyro_rotation_matrix,
                boolean is_front_facing,
                boolean mirror,
                Date current_date,
                HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm,
                String preference_hdr_contrast_enhancement,
                int iso,
                long exposure_time,
                float zoom_factor,
                String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                //String preference_stamp_geo_address,
                String preference_units_distance,
                boolean panorama_crop,
                RemoveDeviceExif remove_device_exif,
                boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                double pitch_angle, boolean store_ypr,
                String custom_tag_artist,
                String custom_tag_copyright,
                int sample_factor) {
            this.type = type;
            this.process_type = process_type;
            this.force_suffix = force_suffix;
            this.suffix_offset = suffix_offset;
            this.save_base = save_base;
            this.jpeg_images = jpeg_images;
            this.preshot_bitmaps = preshot_bitmaps;
            this.raw_image = raw_image;
            this.image_capture_intent = image_capture_intent;
            this.image_capture_intent_uri = image_capture_intent_uri;
            this.using_camera2 = using_camera2;
            this.using_camera_extensions = using_camera_extensions;
            this.image_format = image_format;
            this.image_quality = image_quality;
            this.do_auto_stabilise = do_auto_stabilise;
            this.level_angle = level_angle;
            this.gyro_rotation_matrix = gyro_rotation_matrix;
            this.is_front_facing = is_front_facing;
            this.mirror = mirror;
            this.current_date = current_date;
            this.preference_hdr_tonemapping_algorithm = preference_hdr_tonemapping_algorithm;
            this.preference_hdr_contrast_enhancement = preference_hdr_contrast_enhancement;
            this.iso = iso;
            this.exposure_time = exposure_time;
            this.zoom_factor = zoom_factor;
            this.preference_stamp = preference_stamp;
            this.preference_textstamp = preference_textstamp;
            this.font_size = font_size;
            this.color = color;
            this.pref_style = pref_style;
            this.preference_stamp_dateformat = preference_stamp_dateformat;
            this.preference_stamp_timeformat = preference_stamp_timeformat;
            this.preference_stamp_gpsformat = preference_stamp_gpsformat;
            //this.preference_stamp_geo_address = preference_stamp_geo_address;
            this.preference_units_distance = preference_units_distance;
            this.panorama_crop = panorama_crop;
            this.remove_device_exif = remove_device_exif;
            this.store_location = store_location;
            this.location = location;
            this.store_geo_direction = store_geo_direction;
            this.geo_direction = geo_direction;
            this.pitch_angle = pitch_angle;
            this.store_ypr = store_ypr;
            this.custom_tag_artist = custom_tag_artist;
            this.custom_tag_copyright = custom_tag_copyright;
            this.sample_factor = sample_factor;
        }

        /** Returns a copy of this object. Note that it is not a deep copy - data such as JPEG and RAW
         *  data will not be copied.
         */
        Request copy() {
            return new Request(this.type,
                    this.process_type,
                    this.force_suffix,
                    this.suffix_offset,
                    this.save_base,
                    this.jpeg_images,
                    this.preshot_bitmaps,
                    this.raw_image,
                    this.image_capture_intent, this.image_capture_intent_uri,
                    this.using_camera2, this.using_camera_extensions,
                    this.image_format, this.image_quality,
                    this.do_auto_stabilise, this.level_angle, this.gyro_rotation_matrix,
                    this.is_front_facing,
                    this.mirror,
                    this.current_date,
                    this.preference_hdr_tonemapping_algorithm,
                    this.preference_hdr_contrast_enhancement,
                    this.iso,
                    this.exposure_time,
                    this.zoom_factor,
                    this.preference_stamp, this.preference_textstamp, this.font_size, this.color, this.pref_style, this.preference_stamp_dateformat, this.preference_stamp_timeformat, this.preference_stamp_gpsformat,
                    //this.preference_stamp_geo_address,
                    this.preference_units_distance,
                    this.panorama_crop, this.remove_device_exif, this.store_location, this.location, this.store_geo_direction, this.geo_direction,
                    this.pitch_angle, this.store_ypr,
                    this.custom_tag_artist,
                    this.custom_tag_copyright,
                    this.sample_factor);
        }
    }

    ImageSaver(MainActivity main_activity) {
        super("ImageSaver");
        if( MyDebug.LOG )
            Log.d(TAG, "ImageSaver");
        this.main_activity = main_activity;

        ActivityManager activityManager = (ActivityManager) main_activity.getSystemService(Activity.ACTIVITY_SERVICE);
        this.queue_capacity = computeQueueSize(activityManager.getLargeMemoryClass());
        this.queue = new ArrayBlockingQueue<>(queue_capacity); // since we remove from the queue and then process in the saver thread, in practice the number of background photos - including the one being processed - is one more than the length of this queue

        this.hdrProcessor = new HDRProcessor(main_activity, main_activity.is_test);
        this.panoramaProcessor = new PanoramaProcessor(main_activity, hdrProcessor);

        p.setAntiAlias(true);
    }

    /** Returns the length of the image saver queue. In practice, the number of images that can be taken at once before the UI
     *  blocks is 1 more than this, as 1 image will be taken off the queue to process straight away.
     */
    public int getQueueSize() {
        return this.queue_capacity;
    }

    /** Compute a sensible size for the queue, based on the device's memory (large heap).
     */
    public static int computeQueueSize(int large_heap_memory) {
        if( MyDebug.LOG )
            Log.d(TAG, "large max memory = " + large_heap_memory + "MB");
        int max_queue_size;
        if( MyDebug.LOG )
            Log.d(TAG, "test_small_queue_size?: " + test_small_queue_size);
        if( test_small_queue_size ) {
            large_heap_memory = 0;
        }

        if( large_heap_memory >= 512 ) {
            // This should be at least 5*(queue_cost_jpeg_c+queue_cost_dng_c)-1 so we can take a burst of 5 photos
            // (e.g., in expo mode) with RAW+JPEG without blocking (we subtract 1, as the first image can be immediately
            // taken off the queue).
            // This should also be at least 19 so we can take a burst of 20 photos with JPEG without blocking (we subtract 1,
            // as the first image can be immediately taken off the queue).
            // This should be at most 70 for large heap 512MB (estimate based on reserving 160MB for post-processing and HDR
            // operations, then estimate a JPEG image at 5MB).
            max_queue_size = 34;
        }
        else if( large_heap_memory >= 256 ) {
            // This should be at most 19 for large heap 256MB.
            max_queue_size = 12;
        }
        else if( large_heap_memory >= 128 ) {
            // This should be at least 1*(queue_cost_jpeg_c+queue_cost_dng_c)-1 so we can take a photo with RAW+JPEG
            // without blocking (we subtract 1, as the first image can be immediately taken off the queue).
            // This should be at most 8 for large heap 128MB (allowing 80MB for post-processing).
            max_queue_size = 8;
        }
        else {
            // This should be at least 1*(queue_cost_jpeg_c+queue_cost_dng_c)-1 so we can take a photo with RAW+JPEG
            // without blocking (we subtract 1, as the first image can be immediately taken off the queue).
            max_queue_size = 6;
        }
        //max_queue_size = 1;
        //max_queue_size = 3;
        if( MyDebug.LOG )
            Log.d(TAG, "max_queue_size = " + max_queue_size);
        return max_queue_size;
    }

    /** Computes the cost for a particular request.
     *  Note that for RAW+DNG mode, computeRequestCost() is called twice for a given photo (one for each
     *  of the two requests: one RAW, one JPEG).
     * @param is_raw Whether RAW/DNG or JPEG.
     * @param n_images This is the number of JPEG or RAW images that are in the request.
     */
    public static int computeRequestCost(boolean is_raw, int n_images) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computeRequestCost");
            Log.d(TAG, "is_raw: " + is_raw);
            Log.d(TAG, "n_images: " + n_images);
        }
        int cost;
        if( is_raw )
            cost = n_images * queue_cost_dng_c;
        else {
            cost = n_images * queue_cost_jpeg_c;
            //cost = (n_images > 1 ? 2 : 1) * queue_cost_jpeg_c;
        }
        return cost;
    }

    /** Computes the cost (in terms of number of slots on the image queue) of a new photo.
     * @param n_raw The number of JPEGs that will be taken.
     * @param n_jpegs The number of JPEGs that will be taken.
     */
    int computePhotoCost(int n_raw, int n_jpegs) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computePhotoCost");
            Log.d(TAG, "n_raw: " + n_raw);
            Log.d(TAG, "n_jpegs: " + n_jpegs);
        }
        int cost = 0;
        if( n_raw > 0 )
            cost += computeRequestCost(true, n_raw);
        if( n_jpegs > 0 )
            cost += computeRequestCost(false, n_jpegs);
        if( MyDebug.LOG )
            Log.d(TAG, "cost: " + cost);
        return cost;
    }

    /** Whether taking an extra photo would overflow the queue, resulting in the UI hanging.
     * @param n_raw The number of JPEGs that will be taken.
     * @param n_jpegs The number of JPEGs that will be taken.
     */
    boolean queueWouldBlock(int n_raw, int n_jpegs) {
        int photo_cost = this.computePhotoCost(n_raw, n_jpegs);
        return this.queueWouldBlock(photo_cost);
    }

    /** Whether taking an extra photo would overflow the queue, resulting in the UI hanging.
     * @param photo_cost The result returned by computePhotoCost().
     */
    synchronized boolean queueWouldBlock(int photo_cost) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "queueWouldBlock");
            Log.d(TAG, "photo_cost: " + photo_cost);
            Log.d(TAG, "n_images_to_save: " + n_images_to_save);
            Log.d(TAG, "queue_capacity: " + queue_capacity);
        }
        // we add one to queue, to account for the image currently being processed; n_images_to_save includes an image
        // currently being processed
        if( n_images_to_save == 0 ) {
            // In theory, we should never have the extra_cost large enough to block the queue even when no images are being
            // saved - but we have this just in case. This means taking the photo will likely block the UI, but we don't want
            // to disallow ever taking photos!
            if( MyDebug.LOG )
                Log.d(TAG, "queue is empty");
            return false;
        }
        else if( n_images_to_save + photo_cost > queue_capacity + 1 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "queue would block");
            return true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "queue would not block");
        return false;
    }

    /** Returns the maximum number of DNG images that might be held by the image saver queue at once, before blocking.
     */
    int getMaxDNG() {
        int max_dng = (queue_capacity+1)/queue_cost_dng_c;
        max_dng++; // increase by 1, as the user can still take one extra photo if the queue is exactly full
        if( MyDebug.LOG )
            Log.d(TAG, "max_dng = " + max_dng);
        return max_dng;
    }

    /** Returns the number of images to save, weighted by their cost (e.g., so a single RAW image
     *  will be counted as multiple images).
     */
    public synchronized int getNImagesToSave() {
        return n_images_to_save;
    }

    /** Returns the number of images to save (e.g., so a single RAW image will only be counted as
     *  one image, unlike getNImagesToSave()).

     */
    public synchronized int getNRealImagesToSave() {
        return n_real_images_to_save;
    }

    /** Application has paused.
     */
    void onPause() {
        synchronized(this) {
            app_is_paused = true;
        }
    }

    /** Application has resumed.
     */
    void onResume() {
        synchronized(this) {
            app_is_paused = false;
        }
    }

    void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        {
            //  a request so that the imagesaver thread will complete
            Request request = new Request(Request.Type.ON_DESTROY,
                    Request.ProcessType.NORMAL,
                    false,
                    0,
                    Request.SaveBase.SAVEBASE_NONE,
                    null,
                    null,
                    null,
                    false, null,
                    false, false,
                    Request.ImageFormat.STD, 0,
                    false, 0.0, null,
                    false,
                    false,
                    null,
                    HDRProcessor.default_tonemapping_algorithm_c,
                    null,
                    0,
                    0,
                    1.0f,
                    null, null, 0, 0, null, null, null, null,
                    //null,
                    null,
                    false, Request.RemoveDeviceExif.OFF, false, null, false, 0.0,
                    0.0, false,
                    null, null,
                    1);
            if( MyDebug.LOG )
                Log.d(TAG, "add on_destroy request");
            addRequest(request, 1);
        }
        if( panoramaProcessor != null ) {
            panoramaProcessor.onDestroy();
        }
        if( hdrProcessor != null ) {
            hdrProcessor.onDestroy();
        }
    }

    @Override
    public void run() {
        if( MyDebug.LOG )
            Log.d(TAG, "starting ImageSaver thread...");
        while( true ) {
            try {
                if( MyDebug.LOG )
                    Log.d(TAG, "ImageSaver thread reading from queue, size: " + queue.size());
                Request request = queue.take(); // if empty, take() blocks until non-empty
                // Only decrement n_images_to_save after we've actually saved the image! Otherwise waitUntilDone() will return
                // even though we still have a last image to be saved.
                if( MyDebug.LOG )
                    Log.d(TAG, "ImageSaver thread found new request from queue, size is now: " + queue.size());
                boolean success;
                boolean on_destroy = false;
                switch (request.type) {
                    case RAW:
                        if (MyDebug.LOG)
                            Log.d(TAG, "request is raw");
                        success = saveImageNowRaw(request);
                        break;
                    case JPEG:
                        if (MyDebug.LOG)
                            Log.d(TAG, "request is jpeg");
                        success = saveImageNow(request);
                        break;
                    case DUMMY:
                        if (MyDebug.LOG)
                            Log.d(TAG, "request is dummy");
                        success = true;
                        break;
                    case ON_DESTROY:
                        if( MyDebug.LOG )
                            Log.d(TAG, "request is on_destroy");
                        success = true;
                        on_destroy = true;
                        break;
                    default:
                        if (MyDebug.LOG)
                            Log.e(TAG, "request is unknown type!");
                        success = false;
                        break;
                }
                if( test_slow_saving ) {
                    // ignore warning about "Call to Thread.sleep in a loop", this is only activated in test code
                    //noinspection BusyWait
                    Thread.sleep(2000);
                }
                if( MyDebug.LOG ) {
                    if( success )
                        Log.d(TAG, "ImageSaver thread successfully saved image");
                    else
                        Log.e(TAG, "ImageSaver thread failed to save image");
                }
                synchronized( this ) {
                    n_images_to_save--;
                    if( request.type != Request.Type.DUMMY && request.type != Request.Type.ON_DESTROY )
                        n_real_images_to_save--;
                    if( MyDebug.LOG )
                        Log.d(TAG, "ImageSaver thread processed new request from queue, images to save is now: " + n_images_to_save);
                    if( MyDebug.LOG && n_images_to_save < 0 ) {
                        Log.e(TAG, "images to save has become negative");
                        throw new RuntimeException();
                    }
                    else if( MyDebug.LOG && n_real_images_to_save < 0 ) {
                        Log.e(TAG, "real images to save has become negative");
                        throw new RuntimeException();
                    }
                    notifyAll();

                    main_activity.runOnUiThread(new Runnable() {
                        public void run() {
                            main_activity.imageQueueChanged();
                        }
                    });
                }
                if( on_destroy ) {
                    break;
                }
            }
            catch(InterruptedException e) {
                MyDebug.logStackTrace(TAG, "interrupted while trying to read from ImageSaver queue", e);
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "stopping ImageSaver thread...");
    }

    /** Saves a photo.
     *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
     *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
     *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
     *  successfully.
     */
    boolean saveImageJpeg(boolean do_in_background,
                          Request.ProcessType processType,
                          boolean force_suffix,
                          int suffix_offset,
                          boolean save_expo,
                          List<byte []> images,
                          List<Bitmap> preshot_bitmaps,
                          boolean image_capture_intent, Uri image_capture_intent_uri,
                          boolean using_camera2, boolean using_camera_extensions,
                          Request.ImageFormat image_format, int image_quality,
                          boolean do_auto_stabilise, double level_angle,
                          boolean is_front_facing,
                          boolean mirror,
                          Date current_date,
                          HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm,
                          String preference_hdr_contrast_enhancement,
                          int iso,
                          long exposure_time,
                          float zoom_factor,
                          String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                          //String preference_stamp_geo_address,
                          String preference_units_distance,
                          boolean panorama_crop,
                          Request.RemoveDeviceExif remove_device_exif,
                          boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                          double pitch_angle, boolean store_ypr,
                          String custom_tag_artist,
                          String custom_tag_copyright,
                          int sample_factor) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "saveImageJpeg");
            Log.d(TAG, "do_in_background? " + do_in_background);
            Log.d(TAG, "number of images: " + images.size());
        }
        return saveImage(do_in_background,
                false,
                processType,
                force_suffix,
                suffix_offset,
                save_expo,
                images,
                preshot_bitmaps,
                null,
                image_capture_intent, image_capture_intent_uri,
                using_camera2, using_camera_extensions,
                image_format, image_quality,
                do_auto_stabilise, level_angle,
                is_front_facing,
                mirror,
                current_date,
                preference_hdr_tonemapping_algorithm,
                preference_hdr_contrast_enhancement,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
                //preference_stamp_geo_address,
                preference_units_distance,
                panorama_crop, remove_device_exif, store_location, location, store_geo_direction, geo_direction,
                pitch_angle, store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor);
    }

    /** Saves a RAW photo.
     *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
     *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
     *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
     *  successfully.
     */
    boolean saveImageRaw(boolean do_in_background,
                         boolean force_suffix,
                         int suffix_offset,
                         RawImage raw_image,
                         Date current_date) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "saveImageRaw");
            Log.d(TAG, "do_in_background? " + do_in_background);
        }
        return saveImage(do_in_background,
                true,
                Request.ProcessType.NORMAL,
                force_suffix,
                suffix_offset,
                false,
                null,
                null,
                raw_image,
                false, null,
                false, false,
                Request.ImageFormat.STD, 0,
                false, 0.0,
                false,
                false,
                current_date,
                HDRProcessor.default_tonemapping_algorithm_c,
                null,
                0,
                0,
                1.0f,
                null, null, 0, 0, null, null, null, null,
                //null,
                null,
                false, Request.RemoveDeviceExif.OFF, false, null, false, 0.0,
                0.0, false,
                null, null,
                1);
    }

    private Request pending_image_average_request = null;

    /** Used for a batch of images that will be combined into a single request. This applies to
     *  processType AVERAGE and PANORAMA.
     */
    void startImageBatch(boolean do_in_background,
                           Request.ProcessType processType,
                           List<Bitmap> preshot_bitmaps,
                           Request.SaveBase save_base,
                           boolean image_capture_intent, Uri image_capture_intent_uri,
                           boolean using_camera2, boolean using_camera_extensions,
                           Request.ImageFormat image_format, int image_quality,
                           boolean do_auto_stabilise, double level_angle, boolean want_gyro_matrices,
                           boolean is_front_facing,
                           boolean mirror,
                           Date current_date,
                           int iso,
                           long exposure_time,
                           float zoom_factor,
                           String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                           //String preference_stamp_geo_address,
                           String preference_units_distance,
                           boolean panorama_crop,
                           Request.RemoveDeviceExif remove_device_exif,
                           boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                           double pitch_angle, boolean store_ypr,
                           String custom_tag_artist,
                           String custom_tag_copyright,
                           int sample_factor) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "startImageBatch");
            Log.d(TAG, "do_in_background? " + do_in_background);
        }
        pending_image_average_request = new Request(Request.Type.JPEG,
                processType,
                false,
                0,
                save_base,
                new ArrayList<>(),
                preshot_bitmaps,
                null,
                image_capture_intent, image_capture_intent_uri,
                using_camera2, using_camera_extensions,
                image_format, image_quality,
                do_auto_stabilise, level_angle, want_gyro_matrices ? new ArrayList<>() : null,
                is_front_facing,
                mirror,
                current_date,
                HDRProcessor.default_tonemapping_algorithm_c,
                null,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
                //preference_stamp_geo_address,
                preference_units_distance,
                panorama_crop, remove_device_exif, store_location, location, store_geo_direction, geo_direction,
                pitch_angle, store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor);
    }

    void addImageBatch(byte [] image, float [] gyro_rotation_matrix) {
        if( MyDebug.LOG )
            Log.d(TAG, "addImageBatch");
        if( pending_image_average_request == null ) {
            Log.e(TAG, "addImageBatch called but no pending_image_average_request");
            return;
        }
        pending_image_average_request.jpeg_images.add(image);
        if( gyro_rotation_matrix != null ) {
            float [] copy = new float[gyro_rotation_matrix.length];
            System.arraycopy(gyro_rotation_matrix, 0, copy, 0, gyro_rotation_matrix.length);
            pending_image_average_request.gyro_rotation_matrix.add(copy);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "image average request images: " + pending_image_average_request.jpeg_images.size());
    }

    Request getImageBatchRequest() {
        return pending_image_average_request;
    }

    void finishImageBatch(boolean do_in_background) {
        if( MyDebug.LOG )
            Log.d(TAG, "finishImageBatch");
        if( pending_image_average_request == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "finishImageBatch called but no pending_image_average_request");
            return;
        }
        if( do_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "add background request");
            int cost = computeRequestCost(false, pending_image_average_request.jpeg_images.size());
            addRequest(pending_image_average_request, cost);
        }
        else {
            // wait for queue to be empty
            waitUntilDone();
            saveImageNow(pending_image_average_request);
        }
        pending_image_average_request = null;
    }

    void flushImageBatch() {
        if( MyDebug.LOG )
            Log.d(TAG, "flushImageBatch");
        // aside from resetting the state, this allows the allocated JPEG data to be garbage collected
        pending_image_average_request = null;
    }

    /** Internal saveImage method to handle both JPEG and RAW.
     */
    private boolean saveImage(boolean do_in_background,
                              boolean is_raw,
                              Request.ProcessType processType,
                              boolean force_suffix,
                              int suffix_offset,
                              boolean save_expo,
                              List<byte []> jpeg_images,
                              List<Bitmap> preshot_bitmaps,
                              RawImage raw_image,
                              boolean image_capture_intent, Uri image_capture_intent_uri,
                              boolean using_camera2, boolean using_camera_extensions,
                              Request.ImageFormat image_format, int image_quality,
                              boolean do_auto_stabilise, double level_angle,
                              boolean is_front_facing,
                              boolean mirror,
                              Date current_date,
                              HDRProcessor.TonemappingAlgorithm preference_hdr_tonemapping_algorithm,
                              String preference_hdr_contrast_enhancement,
                              int iso,
                              long exposure_time,
                              float zoom_factor,
                              String preference_stamp, String preference_textstamp, int font_size, int color, String pref_style, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
                              //String preference_stamp_geo_address,
                              String preference_units_distance,
                              boolean panorama_crop,
                              Request.RemoveDeviceExif remove_device_exif,
                              boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
                              double pitch_angle, boolean store_ypr,
                              String custom_tag_artist,
                              String custom_tag_copyright,
                              int sample_factor) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "saveImage");
            Log.d(TAG, "do_in_background? " + do_in_background);
        }
        boolean success;

        //do_in_background = false;

        Request request = new Request(is_raw ? Request.Type.RAW : Request.Type.JPEG,
                processType,
                force_suffix,
                suffix_offset,
                save_expo ? Request.SaveBase.SAVEBASE_ALL : Request.SaveBase.SAVEBASE_NONE,
                jpeg_images,
                preshot_bitmaps,
                raw_image,
                image_capture_intent, image_capture_intent_uri,
                using_camera2, using_camera_extensions,
                image_format, image_quality,
                do_auto_stabilise, level_angle, null,
                is_front_facing,
                mirror,
                current_date,
                preference_hdr_tonemapping_algorithm,
                preference_hdr_contrast_enhancement,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
                //preference_stamp_geo_address,
                preference_units_distance,
                panorama_crop, remove_device_exif, store_location, location, store_geo_direction, geo_direction,
                pitch_angle, store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor);

        if( do_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "add background request");
            int cost = computeRequestCost(is_raw, is_raw ? 1 : request.jpeg_images.size());
            addRequest(request, cost);
            success = true; // always return true when done in background
        }
        else {
            // wait for queue to be empty
            waitUntilDone();
            if( is_raw ) {
                success = saveImageNowRaw(request);
            }
            else {
                success = saveImageNow(request);
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "success: " + success);
        return success;
    }

    /** Adds a request to the background queue, blocking if the queue is already full
     */
    private void addRequest(Request request, int cost) {
        if( MyDebug.LOG )
            Log.d(TAG, "addRequest, cost: " + cost);
        if( main_activity.isDestroyed() ) {
            // If the application is being destroyed as a new photo is being taken, it's not safe to continue
            // MainDestroy.onDestroy() does call waitUntilDone(), but this is extra protection in case an image comes in after that.
            Log.e(TAG, "application is destroyed, image lost!");
            return;
        }
        // this should not be synchronized on "this": BlockingQueue is thread safe, and if it's blocking in queue.put(), we'll hang because
        // the saver queue will need to synchronize on "this" in order to notifyAll() the main thread
        boolean done = false;
        while( !done ) {
            try {
                if( MyDebug.LOG )
                    Log.d(TAG, "ImageSaver thread adding to queue, size: " + queue.size());
                synchronized( this ) {
                    // see above for why we don't synchronize the queue.put call
                    // but we synchronize modification to avoid risk of problems related to compiler optimisation (local caching or reordering)
                    // also see FindBugs warning due to inconsistent synchronisation
                    n_images_to_save++; // increment before adding to the queue, just to make sure the main thread doesn't think we're all done
                    if( request.type != Request.Type.DUMMY && request.type != Request.Type.ON_DESTROY )
                        n_real_images_to_save++;

                    main_activity.runOnUiThread(new Runnable() {
                        public void run() {
                            main_activity.imageQueueChanged();
                        }
                    });
                }
                if( queue.size() + 1 > queue_capacity ) {
                    Log.e(TAG, "ImageSaver thread is going to block, queue already full: " + queue.size());
                    test_queue_blocked = true;
                    //throw new RuntimeException(); // test
                }
                queue.put(request); // if queue is full, put() blocks until it isn't full
                if( MyDebug.LOG ) {
                    synchronized( this ) { // keep FindBugs happy
                        Log.d(TAG, "ImageSaver thread added to queue, size is now: " + queue.size());
                        Log.d(TAG, "images still to save is now: " + n_images_to_save);
                        Log.d(TAG, "real images still to save is now: " + n_real_images_to_save);
                    }
                }
                done = true;
            }
            catch(InterruptedException e) {
                MyDebug.logStackTrace(TAG, "interrupted while trying to add to ImageSaver queue", e);
            }
        }
        if( cost > 0 ) {
            // add "dummy" requests to simulate the cost
            for(int i=0;i<cost-1;i++) {
                addDummyRequest();
            }
        }
    }

    private void addDummyRequest() {
        Request dummy_request = new Request(Request.Type.DUMMY,
                Request.ProcessType.NORMAL,
                false,
                0,
                Request.SaveBase.SAVEBASE_NONE,
                null,
                null,
                null,
                false, null,
                false, false,
                Request.ImageFormat.STD, 0,
                false, 0.0, null,
                false,
                false,
                null,
                HDRProcessor.default_tonemapping_algorithm_c,
                null,
                0,
                0,
                1.0f,
                null, null, 0, 0, null, null, null, null,
                //null,
                null,
                false, Request.RemoveDeviceExif.OFF, false, null, false, 0.0,
                0.0, false,
                null, null,
                1);
        if( MyDebug.LOG )
            Log.d(TAG, "add dummy request");
        addRequest(dummy_request, 1); // cost must be 1, so we don't have infinite recursion!
    }

    /** Wait until the queue is empty and all pending images have been saved.
     */
    void waitUntilDone() {
        if( MyDebug.LOG )
            Log.d(TAG, "waitUntilDone");
        synchronized( this ) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "waitUntilDone: queue is size " + queue.size());
                Log.d(TAG, "waitUntilDone: images still to save " + n_images_to_save);
            }
            while( n_images_to_save > 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "wait until done...");
                try {
                    wait();
                }
                catch(InterruptedException e) {
                    MyDebug.logStackTrace(TAG, "interrupted while waiting for ImageSaver queue to be empty", e);
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "waitUntilDone: queue is size " + queue.size());
                    Log.d(TAG, "waitUntilDone: images still to save " + n_images_to_save);
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "waitUntilDone: images all saved");
    }

    private void setBitmapOptionsSampleSize(BitmapFactory.Options options, int inSampleSize) {
        if( MyDebug.LOG )
            Log.d(TAG, "setBitmapOptionsSampleSize: " + inSampleSize);
        //options.inSampleSize = inSampleSize;
        if( inSampleSize > 1 ) {
            // use inDensity for better quality, as inSampleSize uses nearest neighbour
            options.inDensity = inSampleSize;
            options.inTargetDensity = 1;
        }
    }

    /** Loads a single jpeg as a Bitmaps.
     * @param mutable Whether the bitmap should be mutable. Note that when converting to bitmaps
     *                for the image post-processing (auto-stabilise etc), in general we need the
     *                bitmap to be mutable (for photostamp to work).
     */
    private Bitmap loadBitmap(byte [] jpeg_image, boolean mutable, int inSampleSize) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "loadBitmap");
            Log.d(TAG, "mutable?: " + mutable);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        if( MyDebug.LOG )
            Log.d(TAG, "options.inMutable is: " + options.inMutable);
        options.inMutable = mutable;
        setBitmapOptionsSampleSize(options, inSampleSize);
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg_image, 0, jpeg_image.length, options);
        if( bitmap == null ) {
            Log.e(TAG, "failed to decode bitmap");
        }
        return bitmap;
    }

    /** Helper class for loadBitmaps().
     */
    private static class LoadBitmapThread extends Thread {
        Bitmap bitmap;
        final BitmapFactory.Options options;
        final byte [] jpeg;
        LoadBitmapThread(BitmapFactory.Options options, byte [] jpeg) {
            super("LoadBitmapThread");
            this.options = options;
            this.jpeg = jpeg;
        }

        public void run() {
            this.bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        }
    }

    /** Converts the array of jpegs to Bitmaps. The bitmap with index mutable_id will be marked as mutable (or set to -1 to have no mutable bitmaps, or -2 to have all be mutable bitmaps).
     */
    private List<Bitmap> loadBitmaps(List<byte []> jpeg_images, int mutable_id, int inSampleSize) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "loadBitmaps");
            Log.d(TAG, "mutable_id: " + mutable_id);
        }
        BitmapFactory.Options mutable_options = new BitmapFactory.Options();
        mutable_options.inMutable = true; // bitmap that needs to be writable
        setBitmapOptionsSampleSize(mutable_options, inSampleSize);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = false; // later bitmaps don't need to be writable
        setBitmapOptionsSampleSize(options, inSampleSize);
        LoadBitmapThread [] threads = new LoadBitmapThread[jpeg_images.size()];
        for(int i=0;i<jpeg_images.size();i++) {
            threads[i] = new LoadBitmapThread( (i==mutable_id || mutable_id==-2) ? mutable_options : options, jpeg_images.get(i) );
        }
        // start threads
        if( MyDebug.LOG )
            Log.d(TAG, "start threads");
        for(int i=0;i<jpeg_images.size();i++) {
            threads[i].start();
        }
        // wait for threads to complete
        boolean ok = true;
        if( MyDebug.LOG )
            Log.d(TAG, "wait for threads to complete");
        try {
            for(int i=0;i<jpeg_images.size();i++) {
                threads[i].join();
            }
        }
        catch(InterruptedException e) {
            MyDebug.logStackTrace(TAG, "threads interrupted", e);
            ok = false;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "threads completed");

        List<Bitmap> bitmaps = new ArrayList<>();
        for(int i=0;i<jpeg_images.size() && ok;i++) {
            Bitmap bitmap = threads[i].bitmap;
            if( bitmap == null ) {
                Log.e(TAG, "failed to decode bitmap in thread: " + i);
                ok = false;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "bitmap " + i + ": " + bitmap + " is mutable? " + bitmap.isMutable());
            }
            bitmaps.add(bitmap);
        }

        if( !ok ) {
            if( MyDebug.LOG )
                Log.d(TAG, "cleanup from failure");
            for(int i=0;i<jpeg_images.size();i++) {
                if( threads[i].bitmap != null ) {
                    threads[i].bitmap.recycle();
                    threads[i].bitmap = null;
                }
            }
            bitmaps.clear();
            System.gc();
            return null;
        }

        return bitmaps;
    }

    /** Chooses the hdr_alpha to use for contrast enhancement in the HDR algorithm, based on the user
     *  preferences and scene details.
     */
    public static float getHDRAlpha(String preference_hdr_contrast_enhancement, long exposure_time, int n_bitmaps) {
        boolean use_hdr_alpha;
        if( n_bitmaps == 1 ) {
            // DRO always applies hdr_alpha
            use_hdr_alpha = true;
        }
        else {
            // else HDR
            switch( preference_hdr_contrast_enhancement ) {
                case "preference_hdr_contrast_enhancement_off":
                    use_hdr_alpha = false;
                    break;
                case "preference_hdr_contrast_enhancement_always":
                    use_hdr_alpha = true;
                    break;
                case "preference_hdr_contrast_enhancement_smart":
                default:
                    // Using local contrast enhancement helps scenes where the dynamic range is very large, which tends to be when we choose
                    // a short exposure time, due to fixing problems where some regions are too dark.
                    // This helps: testHDR11, testHDR19, testHDR34, testHDR53, testHDR61.
                    // Using local contrast enhancement in all cases can increase noise in darker scenes. This problem would occur
                    // (if we used local contrast enhancement) is: testHDR2, testHDR12, testHDR17, testHDR43, testHDR50, testHDR51,
                    // testHDR54, testHDR55, testHDR56.
                    use_hdr_alpha = (exposure_time < 1000000000L/59);
                    break;
            }
        }
        //use_hdr_alpha = true; // test
        float hdr_alpha = use_hdr_alpha ? 0.5f : 0.0f;
        if( MyDebug.LOG ) {
            Log.d(TAG, "preference_hdr_contrast_enhancement: " + preference_hdr_contrast_enhancement);
            Log.d(TAG, "exposure_time: " + exposure_time);
            Log.d(TAG, "hdr_alpha: " + hdr_alpha);
        }
        return hdr_alpha;
    }

    private final static String gyro_info_doc_tag = "open_camera_gyro_info";
    private final static String gyro_info_panorama_pics_per_screen_tag = "panorama_pics_per_screen";
    private final static String gyro_info_camera_view_angle_x_tag = "camera_view_angle_x";
    private final static String gyro_info_camera_view_angle_y_tag = "camera_view_angle_y";
    private final static String gyro_info_image_tag = "image";
    private final static String gyro_info_vector_tag = "vector";
    private final static String gyro_info_vector_right_type = "X";
    private final static String gyro_info_vector_up_type = "Y";
    private final static String gyro_info_vector_screen_type = "Z";

    private void writeGyroDebugXml(Writer writer, Request request) throws IOException {
        XmlSerializer xmlSerializer = Xml.newSerializer();

        xmlSerializer.setOutput(writer);
        xmlSerializer.startDocument("UTF-8", true);
        xmlSerializer.startTag(null, gyro_info_doc_tag);
        xmlSerializer.attribute(null, gyro_info_panorama_pics_per_screen_tag, String.valueOf(MyApplicationInterface.getPanoramaPicsPerScreen()));
        xmlSerializer.attribute(null, gyro_info_camera_view_angle_x_tag, String.valueOf(request.camera_view_angle_x));
        xmlSerializer.attribute(null, gyro_info_camera_view_angle_y_tag, String.valueOf(request.camera_view_angle_y));

        float [] inVector = new float[3];
        float [] outVector = new float[3];
        for(int i=0;i<request.gyro_rotation_matrix.size();i++) {
            xmlSerializer.startTag(null, gyro_info_image_tag);
            xmlSerializer.attribute(null, "index", String.valueOf(i));

            GyroSensor.setVector(inVector, 1.0f, 0.0f, 0.0f); // vector pointing in "right" direction
            GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
            xmlSerializer.startTag(null, gyro_info_vector_tag);
            xmlSerializer.attribute(null, "type", gyro_info_vector_right_type);
            xmlSerializer.attribute(null, "x", String.valueOf(outVector[0]));
            xmlSerializer.attribute(null, "y", String.valueOf(outVector[1]));
            xmlSerializer.attribute(null, "z", String.valueOf(outVector[2]));
            xmlSerializer.endTag(null, gyro_info_vector_tag);

            GyroSensor.setVector(inVector, 0.0f, 1.0f, 0.0f); // vector pointing in "up" direction
            GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
            xmlSerializer.startTag(null, gyro_info_vector_tag);
            xmlSerializer.attribute(null, "type", gyro_info_vector_up_type);
            xmlSerializer.attribute(null, "x", String.valueOf(outVector[0]));
            xmlSerializer.attribute(null, "y", String.valueOf(outVector[1]));
            xmlSerializer.attribute(null, "z", String.valueOf(outVector[2]));
            xmlSerializer.endTag(null, gyro_info_vector_tag);

            GyroSensor.setVector(inVector, 0.0f, 0.0f, -1.0f); // vector pointing behind the device's screen
            GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
            xmlSerializer.startTag(null, gyro_info_vector_tag);
            xmlSerializer.attribute(null, "type", gyro_info_vector_screen_type);
            xmlSerializer.attribute(null, "x", String.valueOf(outVector[0]));
            xmlSerializer.attribute(null, "y", String.valueOf(outVector[1]));
            xmlSerializer.attribute(null, "z", String.valueOf(outVector[2]));
            xmlSerializer.endTag(null, gyro_info_vector_tag);

            xmlSerializer.endTag(null, gyro_info_image_tag);
        }

        xmlSerializer.endTag(null, gyro_info_doc_tag);
        xmlSerializer.endDocument();
        xmlSerializer.flush();
    }

    @SuppressWarnings("WeakerAccess")
    public static class GyroDebugInfo {
        public static class GyroImageDebugInfo {
            public float [] vectorRight; // X axis
            public float [] vectorUp; // Y axis
            public float [] vectorScreen; // vector into the screen - actually the -Z axis
        }

        public final List<GyroImageDebugInfo> image_info;

        public GyroDebugInfo() {
            image_info = new ArrayList<>();
        }
    }

    public static boolean readGyroDebugXml(InputStream inputStream, GyroDebugInfo info) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();

            parser.require(XmlPullParser.START_TAG, null, gyro_info_doc_tag);
            GyroDebugInfo.GyroImageDebugInfo image_info = null;

            while( parser.next() != XmlPullParser.END_DOCUMENT ) {
                switch( parser.getEventType() ) {
                    case XmlPullParser.START_TAG: {
                        String name = parser.getName();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "start tag, name: " + name);
                        }

                        switch( name ) {
                            case gyro_info_image_tag:
                                info.image_info.add( image_info = new GyroDebugInfo.GyroImageDebugInfo() );
                                break;
                            case gyro_info_vector_tag:
                                if( image_info == null ) {
                                    Log.e(TAG, "vector tag outside of image tag");
                                    return false;
                                }
                                String type = parser.getAttributeValue(null, "type");
                                String x_s = parser.getAttributeValue(null, "x");
                                String y_s = parser.getAttributeValue(null, "y");
                                String z_s = parser.getAttributeValue(null, "z");
                                float [] vector = new float[3];
                                vector[0] = Float.parseFloat(x_s);
                                vector[1] = Float.parseFloat(y_s);
                                vector[2] = Float.parseFloat(z_s);
                                switch( type ) {
                                    case gyro_info_vector_right_type:
                                        image_info.vectorRight = vector;
                                        break;
                                    case gyro_info_vector_up_type:
                                        image_info.vectorUp = vector;
                                        break;
                                    case gyro_info_vector_screen_type:
                                        image_info.vectorScreen = vector;
                                        break;
                                    default:
                                        Log.e(TAG, "unknown type in vector tag: " + type);
                                        return false;
                                }
                                break;
                        }
                        break;
                    }
                    case XmlPullParser.END_TAG: {
                        String name = parser.getName();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "end tag, name: " + name);
                        }

                        //noinspection SwitchStatementWithTooFewBranches
                        switch( name ) {
                            case gyro_info_image_tag:
                                image_info = null;
                                break;
                        }
                        break;
                    }
                }
            }
        }
        catch(Exception e) {
            MyDebug.logStackTrace(TAG, "failed to parse xml", e);
            return false;
        }
        finally {
            try {
                inputStream.close();
            }
            catch(IOException e) {
                MyDebug.logStackTrace(TAG, "failed to close inputStream", e);
            }
        }
        return true;
    }

    private boolean processHDR(List<Bitmap> bitmaps, final Request request, long time_s) {
        float hdr_alpha = getHDRAlpha(request.preference_hdr_contrast_enhancement, request.exposure_time, bitmaps.size());
        if( MyDebug.LOG )
            Log.d(TAG, "before HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
        try {
            hdrProcessor.processHDR(bitmaps, true, null, true, null, hdr_alpha, 4, true, request.preference_hdr_tonemapping_algorithm, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA); // this will recycle all the bitmaps except bitmaps.get(0), which will contain the hdr image
        }
        catch(HDRProcessorException e) {
            MyDebug.logStackTrace(TAG, "HDRProcessorException from processHDR", e);
            if( e.getCode() == HDRProcessorException.UNEQUAL_SIZES ) {
                // this can happen on OnePlus 3T with old camera API with front camera, seems to be a bug that resolution changes when exposure compensation is set!
                Log.e(TAG, "UNEQUAL_SIZES");
                bitmaps.clear();
                System.gc();
                return false;
            }
            else {
                // throw RuntimeException, as we shouldn't ever get the error INVALID_N_IMAGES, if we do it's a programming error
                throw new RuntimeException();
            }
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "HDR performance: time after creating HDR image: " + (System.currentTimeMillis() - time_s));
        }
        if( MyDebug.LOG )
            Log.d(TAG, "after HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
        return true;
    }

    /** May be run in saver thread or picture callback thread (depending on whether running in background).
     */
    private boolean saveImageNow(final Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveImageNow");

        if( request.type != Request.Type.JPEG ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveImageNow called with non-jpeg request");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }
        else if( request.jpeg_images.isEmpty() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveImageNow called with zero images");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }

        if( request.preshot_bitmaps != null && !request.preshot_bitmaps.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            savePreshotBitmaps(request);
        }

        boolean success;
        if( request.process_type == Request.ProcessType.AVERAGE ) {
            if( MyDebug.LOG )
                Log.d(TAG, "average");

            saveBaseImages(request, "_");
            main_activity.savingImage(true);

            /*List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, 0);
            if (bitmaps == null) {
                if (MyDebug.LOG)
                    Log.e(TAG, "failed to load bitmaps");
                main_activity.savingImage(false);
                return false;
            }*/
            /*Bitmap nr_bitmap = loadBitmap(request.jpeg_images.get(0), true);

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
                try {
                    for(int i = 1; i < request.jpeg_images.size(); i++) {
                        Log.d(TAG, "processAvg for image: " + i);
                        Bitmap new_bitmap = loadBitmap(request.jpeg_images.get(i), false);
                        float avg_factor = (float) i;
                        hdrProcessor.processAvg(nr_bitmap, new_bitmap, avg_factor, true);
                        // processAvg recycles new_bitmap
                    }
                    //hdrProcessor.processAvgMulti(bitmaps, hdr_strength, 4);
                    //hdrProcessor.avgBrighten(nr_bitmap);
                }
                catch(HDRProcessorException e) {
                    MyDebug.logStackTrace(TAG, "HDRProcessorException from processAvg", e);
                    throw new RuntimeException();
                }
            }
            else {
                Log.e(TAG, "shouldn't have offered NoiseReduction as an option if not on Android 5");
                throw new RuntimeException();
            }*/
            Bitmap nr_bitmap;
            {
                try {
                    long time_s = System.currentTimeMillis();
                    int inSampleSize = hdrProcessor.getAvgSampleSize(request.iso, request.exposure_time);
                    //final boolean use_smp = false;
                    final boolean use_smp = true;
                    // n_smp_images is how many bitmaps to decompress at once if use_smp==true. Beware of setting too high -
                    // e.g., storing 4 16MP bitmaps takes 256MB of heap (NR requires at least 512MB large heap); also need to make
                    // sure there isn't a knock on effect on performance
                    //final int n_smp_images = 2;
                    final int n_smp_images = 4;
                    long this_time_s = System.currentTimeMillis();
                    List<Bitmap> bitmaps = null;
                    Bitmap bitmap0, bitmap1;
                    if( use_smp ) {
                        /*List<byte []> sub_jpeg_list = new ArrayList<>();
                        sub_jpeg_list.add(request.jpeg_images.get(0));
                        sub_jpeg_list.add(request.jpeg_images.get(1));
                        bitmaps = loadBitmaps(sub_jpeg_list, -1, inSampleSize);
                        bitmap0 = bitmaps.get(0);
                        bitmap1 = bitmaps.get(1);*/
                        int n_remaining = request.jpeg_images.size();
                        int n_load = Math.min(n_smp_images, n_remaining);
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "n_remaining: " + n_remaining);
                            Log.d(TAG, "n_load: " + n_load);
                        }
                        List<byte []> sub_jpeg_list = new ArrayList<>();
                        for(int j=0;j<n_load;j++) {
                            sub_jpeg_list.add(request.jpeg_images.get(j));
                        }
                        bitmaps = loadBitmaps(sub_jpeg_list, -1, inSampleSize);
                        if( MyDebug.LOG )
                            Log.d(TAG, "length of bitmaps list is now: " + bitmaps.size());
                        bitmap0 = bitmaps.get(0);
                        bitmap1 = bitmaps.get(1);
                    }
                    else {
                        bitmap0 = loadBitmap(request.jpeg_images.get(0), false, inSampleSize);
                        bitmap1 = loadBitmap(request.jpeg_images.get(1), false, inSampleSize);
                    }
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** time for loading first bitmaps: " + (System.currentTimeMillis() - this_time_s));
                    }
                    int width = bitmap0.getWidth();
                    int height = bitmap0.getHeight();
                    float avg_factor = 1.0f;
                    this_time_s = System.currentTimeMillis();
                    HDRProcessor.AvgData avg_data = hdrProcessor.processAvg(bitmap0, bitmap1, avg_factor, request.iso, request.exposure_time, request.zoom_factor);
                    if( bitmaps != null ) {
                        bitmaps.set(0, null);
                        bitmaps.set(1, null);
                    }
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** time for processing first two bitmaps: " + (System.currentTimeMillis() - this_time_s));
                    }

                    for(int i=2;i<request.jpeg_images.size();i++) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "processAvg for image: " + i);

                        this_time_s = System.currentTimeMillis();
                        Bitmap new_bitmap;
                        if( use_smp ) {
                            // check if we already loaded the bitmap
                            if( MyDebug.LOG )
                                Log.d(TAG, "length of bitmaps list: " + bitmaps.size());
                            if( i < bitmaps.size() ) {
                                if( MyDebug.LOG ) {
                                    Log.d(TAG, "already loaded bitmap from previous iteration with SMP");
                                }
                                new_bitmap = bitmaps.get(i);
                            }
                            else {
                                int n_remaining = request.jpeg_images.size() - i;
                                int n_load = Math.min(n_smp_images, n_remaining);
                                if( MyDebug.LOG ) {
                                    Log.d(TAG, "n_remaining: " + n_remaining);
                                    Log.d(TAG, "n_load: " + n_load);
                                }
                                List<byte []> sub_jpeg_list = new ArrayList<>();
                                for(int j=i;j<i+n_load;j++) {
                                    sub_jpeg_list.add(request.jpeg_images.get(j));
                                }
                                List<Bitmap> new_bitmaps = loadBitmaps(sub_jpeg_list, -1, inSampleSize);
                                bitmaps.addAll(new_bitmaps);
                                if( MyDebug.LOG )
                                    Log.d(TAG, "length of bitmaps list is now: " + bitmaps.size());
                                new_bitmap = bitmaps.get(i);
                            }
                        }
                        else {
                            new_bitmap = loadBitmap(request.jpeg_images.get(i), false, inSampleSize);
                        }
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "*** time for loading extra bitmap: " + (System.currentTimeMillis() - this_time_s));
                        }
                        avg_factor = (float)i;
                        this_time_s = System.currentTimeMillis();
                        hdrProcessor.updateAvg(avg_data, width, height, new_bitmap, avg_factor, request.iso, request.exposure_time, request.zoom_factor);
                        // updateAvg recycles new_bitmap
                        if( bitmaps != null ) {
                            bitmaps.set(i, null);
                        }
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "*** time for updating extra bitmap: " + (System.currentTimeMillis() - this_time_s));
                        }
                    }

                    this_time_s = System.currentTimeMillis();
                    nr_bitmap = hdrProcessor.avgBrighten(avg_data, width, height, request.iso, request.exposure_time);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** time for brighten: " + (System.currentTimeMillis() - this_time_s));
                    }
                    avg_data.destroy();
                    //noinspection UnusedAssignment
                    avg_data = null;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "*** total time for saving NR image: " + (System.currentTimeMillis() - time_s));
                    }
                }
                catch(HDRProcessorException e) {
                    MyDebug.logStackTrace(TAG, "HDRProcessorException", e);
                    throw new RuntimeException();
                }
            }

            if( MyDebug.LOG )
                Log.d(TAG, "nr_bitmap: " + nr_bitmap + " is mutable? " + nr_bitmap.isMutable());
            System.gc();
            main_activity.savingImage(false);

            if( MyDebug.LOG )
                Log.d(TAG, "save NR image");
            success = saveSingleImageNow(request, request.jpeg_images.get(0), nr_bitmap, nr_suffix, true, true, true, false);
            if( MyDebug.LOG && !success )
                Log.e(TAG, "saveSingleImageNow failed for nr image");
            nr_bitmap.recycle();
            System.gc();
        }
        else if( request.process_type == Request.ProcessType.HDR ) {
            if( MyDebug.LOG )
                Log.d(TAG, "hdr");
            if( request.jpeg_images.size() != 1 && request.jpeg_images.size() != 3 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "saveImageNow expected either 1 or 3 images for hdr, not " + request.jpeg_images.size());
                // throw runtime exception, as this is a programming error
                throw new RuntimeException();
            }

            long time_s = System.currentTimeMillis();
            if( request.jpeg_images.size() > 1 ) {
                // if there's only 1 image, we're in DRO mode, and shouldn't save the base image
                // note that in earlier Open Camera versions, we used "_EXP" as the suffix. We now use just "_" from 1.42 onwards, so Google
                // Photos will group them together. (Unfortunately using "_EXP_" doesn't work, the images aren't grouped!)
                saveBaseImages(request, "_");
                if( MyDebug.LOG ) {
                    Log.d(TAG, "HDR performance: time after saving base exposures: " + (System.currentTimeMillis() - time_s));
                }
            }

            // note, even if we failed saving some of the expo images, still try to save the HDR image
            if( MyDebug.LOG )
                Log.d(TAG, "create HDR image");
            main_activity.savingImage(true);

            // see documentation for HDRProcessor.processHDR() - because we're using release_bitmaps==true, we need to make sure that
            // the bitmap that will hold the output HDR image is mutable (in case of options like photo stamp)
            // see test testTakePhotoHDRPhotoStamp.
            int base_bitmap = (request.jpeg_images.size()-1)/2;
            if( MyDebug.LOG )
                Log.d(TAG, "base_bitmap: " + base_bitmap);
            List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, base_bitmap, 1);
            if( bitmaps == null ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to load bitmaps");
                main_activity.savingImage(false);
                return false;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "HDR performance: time after decompressing base exposures: " + (System.currentTimeMillis() - time_s));
            }

            if( !processHDR(bitmaps, request, time_s) ) {
                main_activity.getPreview().showToast(null, R.string.failed_to_process_hdr);
                main_activity.savingImage(false);
                return false;
            }

            Bitmap hdr_bitmap = bitmaps.get(0);
            if( MyDebug.LOG )
                Log.d(TAG, "hdr_bitmap: " + hdr_bitmap + " is mutable? " + hdr_bitmap.isMutable());
            bitmaps.clear();
            System.gc();
            main_activity.savingImage(false);

            if( MyDebug.LOG )
                Log.d(TAG, "save HDR image");
            int base_image_id = ((request.jpeg_images.size()-1)/2);
            if( MyDebug.LOG )
                Log.d(TAG, "base_image_id: " + base_image_id);
            String suffix = request.jpeg_images.size() == 1 ? "_DRO" : hdr_suffix;
            success = saveSingleImageNow(request, request.jpeg_images.get(base_image_id), hdr_bitmap, suffix, true, true, true, false);
            if( MyDebug.LOG && !success )
                Log.e(TAG, "saveSingleImageNow failed for hdr image");
            if( MyDebug.LOG ) {
                Log.d(TAG, "HDR performance: time after saving HDR image: " + (System.currentTimeMillis() - time_s));
            }
            hdr_bitmap.recycle();
            System.gc();
        }
        else if( request.process_type == Request.ProcessType.PANORAMA ) {
            if( MyDebug.LOG )
                Log.d(TAG, "panorama");

            // save text file with gyro info
            if( !request.image_capture_intent && request.save_base == Request.SaveBase.SAVEBASE_ALL_PLUS_DEBUG ) {
                /*final StringBuilder gyro_text = new StringBuilder();
                gyro_text.append("Panorama gyro debug info\n");
                gyro_text.append("n images: " + request.gyro_rotation_matrix.size() + ":\n");

                float [] inVector = new float[3];
                float [] outVector = new float[3];
                for(int i=0;i<request.gyro_rotation_matrix.size();i++) {
                    gyro_text.append("Image " + i + ":\n");

                    GyroSensor.setVector(inVector, 1.0f, 0.0f, 0.0f); // vector pointing in "right" direction
                    GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
                    gyro_text.append("    X: " + outVector[0] + " , " + outVector[1] + " , " + outVector[2] + "\n");

                    GyroSensor.setVector(inVector, 0.0f, 1.0f, 0.0f); // vector pointing in "up" direction
                    GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
                    gyro_text.append("    Y: " + outVector[0] + " , " + outVector[1] + " , " + outVector[2] + "\n");

                    GyroSensor.setVector(inVector, 0.0f, 0.0f, -1.0f); // vector pointing behind the device's screen
                    GyroSensor.transformVector(outVector, request.gyro_rotation_matrix.get(i), inVector);
                    gyro_text.append("    -Z: " + outVector[0] + " , " + outVector[1] + " , " + outVector[2] + "\n");

                }*/

                try {
                    StringWriter writer = new StringWriter();

                    writeGyroDebugXml(writer, request);

                    StorageUtils storageUtils = main_activity.getStorageUtils();
                    /*File saveFile = null;
                    Uri saveUri = null;
                    if( storageUtils.isUsingSAF() ) {
                        saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_GYRO_INFO, "", "xml", request.current_date);
                    }
                    else {
                        saveFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_GYRO_INFO, "", "xml", request.current_date);
                        if( MyDebug.LOG )
                            Log.d(TAG, "save to: " + saveFile.getAbsolutePath());
                    }*/
                    // We save to the application specific folder so this works on Android 10 with scoped storage, without having to
                    // rewrite the non-SAF codepath to use MediaStore API (which would also have problems that the gyro debug files would
                    // show up in the MediaStore, hence gallery applications!)
                    // We use this for older Android versions for consistency, plus not a bad idea of to have debug files in the application
                    // folder anyway.
                    File saveFile = storageUtils.createOutputMediaFile(main_activity.getExternalFilesDir(null), StorageUtils.MEDIA_TYPE_GYRO_INFO, "", "xml", request.current_date);
                    Uri saveUri = null;
                    if( MyDebug.LOG )
                        Log.d(TAG, "save to: " + saveFile.getAbsolutePath());

                    OutputStream outputStream;
                    if( saveFile != null )
                        outputStream = new FileOutputStream(saveFile);
                    else
                        outputStream = main_activity.getContentResolver().openOutputStream(saveUri);
                    try {
                        //outputStream.write(gyro_text.toString().getBytes());
                        //noinspection CharsetObjectCanBeUsed
                        outputStream.write(writer.toString().getBytes(Charset.forName("UTF-8")));
                    }
                    finally {
                        outputStream.close();
                    }

                    if( saveFile != null ) {
                        storageUtils.broadcastFile(saveFile, false, false, false, false, null);
                    }
                    else {
                        broadcastSAFFile(saveUri, false, false, false);
                    }
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to write gyro text file", e);
                }
            }

            // for now, just save all the images:
            //String suffix = "_";
            //success = saveImages(request, suffix, false, true, true);

            saveBaseImages(request, "_");

            main_activity.savingImage(true);

            long time_s = System.currentTimeMillis();

            if( MyDebug.LOG )
                Log.d(TAG, "panorama_dir_left_to_right: " + request.panorama_dir_left_to_right);
            if( !request.panorama_dir_left_to_right ) {
                Collections.reverse(request.jpeg_images);
                // shouldn't use gyro_rotation_matrix from this point, but keep in sync with jpeg_images just in case
                Collections.reverse(request.gyro_rotation_matrix);
            }

            // need all to be mutable - n.b., in practice setting to -1
            // doesn't cause a problem on some devices e.g. Galaxy S24+ because the bitmaps may be made
            // mutable in rotateForExif, but this can be reproduced on on emulator at least
            List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, -2, 1);
            if( bitmaps == null ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to load bitmaps");
                main_activity.savingImage(false);
                return false;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "panorama performance: time after decompressing base exposures: " + (System.currentTimeMillis() - time_s));
            }

            // rotate the bitmaps if necessary for exif tags
            for(int i=0;i<bitmaps.size();i++) {
                Bitmap bitmap = bitmaps.get(i);
                bitmap = rotateForExif(bitmap, request.jpeg_images.get(0));
                bitmaps.set(i, bitmap);
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "panorama performance: time after rotating for exif: " + (System.currentTimeMillis() - time_s));
            }

            Bitmap panorama;
            try {
                panorama = panoramaProcessor.panorama(bitmaps, MyApplicationInterface.getPanoramaPicsPerScreen(), request.camera_view_angle_y, request.panorama_crop);
            }
            catch(PanoramaProcessorException e) {
                MyDebug.logStackTrace(TAG, "PanoramaProcessorException from panorama", e);
                if( e.getCode() == PanoramaProcessorException.UNEQUAL_SIZES || e.getCode() == PanoramaProcessorException.FAILED_TO_CROP ) {
                    main_activity.getPreview().showToast(null, R.string.failed_to_process_panorama);
                    Log.e(TAG, "panorama failed: " + e.getCode());
                    bitmaps.clear();
                    System.gc();
                    main_activity.savingImage(false);
                    return false;
                }
                else {
                    // throw RuntimeException, as we shouldn't ever get the error INVALID_N_IMAGES, if we do it's a programming error
                    throw new RuntimeException();
                }
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "panorama performance: time after creating panorama image: " + (System.currentTimeMillis() - time_s));
            }
            if( MyDebug.LOG )
                Log.d(TAG, "panorama: " + panorama);
            bitmaps.clear();
            System.gc();

            main_activity.savingImage(false);

            if( MyDebug.LOG )
                Log.d(TAG, "save panorama image");
            success = saveSingleImageNow(request, request.jpeg_images.get(0), panorama, pano_suffix, true, true, true, true);
            if( MyDebug.LOG && !success )
                Log.e(TAG, "saveSingleImageNow failed for panorama image");
            panorama.recycle();
            System.gc();
        }
        else {
            // see note above how we used to use "_EXP" for the suffix for multiple images
            //String suffix = "_EXP";
            String suffix = "_";
            success = saveImages(request, suffix, false, true, true);
        }

        return success;
    }

    /** Alternative to android.util.Range&lt;Integer&gt;, since that is not mocked so can't be used
     *  in unit testing.
     */
    public static class IntRange {
        private final int lower;
        private final int upper;

        public IntRange(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;

            if( lower > upper ) {
                throw new IllegalArgumentException("lower must be <= upper");
            }
        }

        IntRange(Range<Integer> range) {
            this(range.getLower(), range.getUpper());
        }

        boolean contains(int value) {
            return value >= lower && value <= upper;
        }

        int clamp(int value) {
            if( value <= lower )
                return lower;
            else if( value >= upper )
                return upper;
            return value;
        }
    }

    public static CameraController.Size adjustResolutionForVideoCapabilities(int video_width, int video_height, IntRange supported_widths, IntRange supported_heights, int width_alignment, int height_alignment) {
        if( !supported_widths.contains(video_width) ) {
            double aspect = ((double)video_height) / ((double)video_width);
            video_width = supported_widths.clamp(video_width);
            video_height = (int)(aspect * video_width + 0.5);
            if( MyDebug.LOG )
                Log.d(TAG, "limit video (width) to: " + video_width + " x " + video_height);
        }
        if( !supported_heights.contains(video_height) ) {
            double aspect = ((double)video_height) / ((double)video_width);
            video_height = supported_heights.clamp(video_height);
            video_width = (int)(video_height / aspect + 0.5);
            if( MyDebug.LOG )
                Log.d(TAG, "limit video (height) to: " + video_width + " x " + video_height);
            // test width again
            if( !supported_widths.contains(video_width) ) {
                video_width = supported_widths.clamp(video_width);
                if( MyDebug.LOG )
                    Log.d(TAG, "can't find valid size that preserves aspect ratios! limit video (width) to: " + video_width + " x " + video_height);
            }
        }
        // Adjust for alignment - we could be cleverer and try to find an adjustment that preserves the aspect
        // ratio. But we'd hope that camera preview sizes already satisfy alignments - or if we had to adjust due to
        // being outside the supported widths or heights, then we should have clamped to something that already
        // satisfies the alignments
        int alignment = width_alignment;
        if( video_width % alignment != 0 ) {
            video_width += alignment - (video_width % alignment);
            if( MyDebug.LOG )
                Log.d(TAG, "adjust video width for alignment to: " + video_width);
        }
        alignment = height_alignment;
        if( video_height % alignment != 0 ) {
            video_height += alignment - (video_height % alignment);
            if( MyDebug.LOG )
                Log.d(TAG, "adjust height for alignment to: " + video_height);
        }
        return new CameraController.Size(video_width, video_height);
    }

    private static class MuxerInfo {
        MediaMuxer muxer;
        boolean muxer_started = false;
        int videoTrackIndex = -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void encodeVideoFrame(final MediaCodec encoder, MuxerInfo muxer_info, long presentationTimeUs, boolean end_of_stream) throws IOException {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if( end_of_stream ) {
            if( MyDebug.LOG )
                Log.d(TAG, "    signal end of stream");
            encoder.signalEndOfInputStream();
        }
        while( true ) {
            if( MyDebug.LOG )
                Log.d(TAG, "    start of loop for saving pre-shot");
            final int timeout_us = 10000;
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeout_us);
            if( MyDebug.LOG )
                Log.d(TAG, "    outputBufferIndex: " + outputBufferIndex);
            if( outputBufferIndex >= 0 ) {
                bufferInfo.presentationTimeUs = presentationTimeUs;
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                if( outputBuffer == null ) {
                    Log.e(TAG, "getOutputBuffer returned null");
                    throw new IOException();
                }

                if( (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if( bufferInfo.size != 0 ) {
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    muxer_info.muxer.writeSampleData(muxer_info.videoTrackIndex, outputBuffer, bufferInfo);
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false);

                break;
                /*if( (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ) {
                    if( MyDebug.LOG ) {
                        if( !end_of_stream ) {
                            Log.e(TAG, "    reached end of stream unexpectedly");
                        }
                        else {
                            Log.d(TAG, "    end of stream reached");
                        }
                    }
                    break;
                }*/
            }
            else {
                if( outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "    INFO_TRY_AGAIN_LATER");
                    /*if( !end_of_stream ) {
                        break;
                    }*/
                }
                else if( outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "    INFO_OUTPUT_FORMAT_CHANGED");
                    muxer_info.videoTrackIndex = muxer_info.muxer.addTrack(encoder.getOutputFormat());
                    muxer_info.muxer.start();
                    muxer_info.muxer_started = true;
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void savePreshotBitmaps(final Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "savePreshotBitmaps");

        main_activity.savingImage(true);

        List<Bitmap> preshot_bitmaps = request.preshot_bitmaps;
        if( MyDebug.LOG )
            Log.d(TAG, "number of preshots: " + preshot_bitmaps.size());

        ApplicationInterface.VideoMethod method = ApplicationInterface.VideoMethod.FILE;
        Uri video_uri = null;
        String video_filename = null;
        ParcelFileDescriptor video_pfd_saf = null;
        MediaMuxer muxer = null;
        //boolean muxer_started = false;
        MuxerInfo muxer_info = new MuxerInfo();
        MediaCodec encoder = null;
        boolean saved_preshots = false;
        try {
            // rotate if necessary
            // see comments in Preview.RefreshPreviewBitmapTask for update_preshot for why we need to rotote
            int rotation_degrees = main_activity.getPreview().getDisplayRotationDegrees(false);
            if( MyDebug.LOG )
                Log.d(TAG, "rotation_degrees: " + rotation_degrees);
            if( rotation_degrees != 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "rotate preshots");
                Matrix matrix = new Matrix();
                matrix.postRotate(-rotation_degrees);
                for(int i=0;i<preshot_bitmaps.size();i++) {
                    Bitmap bitmap = preshot_bitmaps.get(i);
                    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    bitmap.recycle();
                    preshot_bitmaps.set(i, new_bitmap);
                }

            }

            // resize if necessary - need to ensure we have supported dimensions for encoding to video

            int preshot_width = preshot_bitmaps.get(0).getWidth();
            int preshot_height = preshot_bitmaps.get(0).getHeight();
            // in some cases, the preview surface dimensions may not match the original camera preview dimensions
            // note that this alone isn't enough to guarantee being supported for video encoding, but makes sense to start with this value
            CameraController.Size preview_size = main_activity.getPreview().getCurrentPreviewSize();
            int video_width = preview_size.width;
            int video_height = preview_size.height;
            if( (preshot_width > preshot_height) != (video_width > video_height) ) {
                int dummy = video_height;
                //noinspection SuspiciousNameCombination
                video_height = video_width;
                video_width = dummy;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "preshot: " + preshot_width + " x " + preshot_height);
                Log.d(TAG, "preview: " + video_width + " x " + video_height);
            }

            long time_s = System.currentTimeMillis();
            final String mime_type = MediaFormat.MIMETYPE_VIDEO_AVC;
            MediaCodecList codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo best_codec_info = null;
            int best_error = 0;
            int best_offset = 0;
            {
                MediaCodecInfo [] codec_infos = codecs.getCodecInfos();
                for(MediaCodecInfo codec_info : codec_infos) {
                    if( !codec_info.isEncoder() ) {
                        continue;
                    }

                    boolean valid = false;
                    String [] types = codec_info.getSupportedTypes();
                    for(String type : types) {
                        if( type.equalsIgnoreCase(mime_type) ) {
                            valid = true;
                            break;
                        }
                    }

                    if( valid ) {
                        MediaCodecInfo.CodecCapabilities capabilities = codec_info.getCapabilitiesForType(mime_type);
                        MediaCodecInfo.VideoCapabilities video_capabilities = capabilities.getVideoCapabilities();
                        if( video_capabilities != null ) {
                            int error_w = Math.abs(video_capabilities.getSupportedWidths().clamp(video_width) - video_width);
                            int error_h = Math.abs(video_capabilities.getSupportedHeights().clamp(video_height) - video_height);
                            int error = error_w*error_h;
                            int offset_w = video_width % video_capabilities.getWidthAlignment();
                            int offset_h = video_height % video_capabilities.getHeightAlignment();
                            int offset = offset_w*offset_h;
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "video_capabilities:");
                                Log.d(TAG, "    width range: " + video_capabilities.getSupportedWidths());
                                Log.d(TAG, "    height range: " + video_capabilities.getSupportedHeights());
                                Log.d(TAG, "    width alignment: " + video_capabilities.getWidthAlignment());
                                Log.d(TAG, "    height alignment: " + video_capabilities.getHeightAlignment());
                                Log.d(TAG, "    error_w: " + error_w);
                                Log.d(TAG, "    error_h: " + error_h);
                                Log.d(TAG, "    offset_w: " + offset_w);
                                Log.d(TAG, "    offset_h: " + offset_h);
                            }
                            // prefer codec that's closest to supporting the width/height; among those, prefer codec with smallest adjustment needed for alignment
                            if( best_codec_info == null || error < best_error || offset < best_offset ) {
                                best_codec_info = codec_info;
                            }
                        }
                    }
                }
            }

            if( best_codec_info == null ) {
                Log.e(TAG, "can't find a valid codecinfo");
                // don't fail - hope for the best that we might find an encoder below anyway
            }
            else {
                MediaCodecInfo.CodecCapabilities capabilities = best_codec_info.getCapabilitiesForType(mime_type);
                MediaCodecInfo.VideoCapabilities video_capabilities = capabilities.getVideoCapabilities();
                Range<Integer> supported_widths = video_capabilities.getSupportedWidths();
                Range<Integer> supported_heights = video_capabilities.getSupportedHeights();
                int width_alignment = video_capabilities.getWidthAlignment();
                int height_alignment = video_capabilities.getHeightAlignment();
                CameraController.Size adjusted_size = adjustResolutionForVideoCapabilities(video_width, video_height, new IntRange(supported_widths), new IntRange(supported_heights), width_alignment, height_alignment);
                video_width = adjusted_size.width;
                video_height = adjusted_size.height;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "time for querying codec capabilities: " + (System.currentTimeMillis() - time_s));

            if( MyDebug.LOG )
                Log.d(TAG, "chosen video resolution: " + video_width + " x " + video_height);
            if( preshot_width != video_width || preshot_height != video_height ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "resize preshot bitmaps to: " + video_width + " x " + video_height);
                for(int i=0;i<preshot_bitmaps.size();i++) {
                    Bitmap bitmap = preshot_bitmaps.get(i);
                    Bitmap new_bitmap = Bitmap.createScaledBitmap(bitmap, video_width, video_height, true);
                    bitmap.recycle();
                    preshot_bitmaps.set(i, new_bitmap);
                }
            }

            // apply any post-processing
            Request preshot_request = request.copy();
            if( preshot_request.is_front_facing ) {
                // we need to mirror for front camera (or not mirror, if the mirror flag was set)
                // for front camera, the preview will typically be mirrored, whilst saved photos are not mirrored, so we need to undo this to be
                // consistent with the main photo
                preshot_request.mirror = !preshot_request.mirror;
            }

            for(int i=0;i<preshot_bitmaps.size();i++) {
                if( MyDebug.LOG )
                    Log.d(TAG, "apply post-processing for preshot bitmap: " + i);
                Bitmap bitmap = preshot_bitmaps.get(i);

                // applying DRO to preshots disabled, as it's slow...
                /*if( request.process_type == Request.ProcessType.HDR && request.jpeg_images.size() == 1 ) {
                    // note, ProcessType.HDR is used for photo modes DRO (if 1 JPEG image) and HDR (if 3 JPEG images) - we only want to apply DRO
                    // to the former
                    List<Bitmap> bitmaps = new ArrayList<>();
                    bitmaps.add(bitmap);
                    if( !processHDR(bitmaps, request, time_s) ) {
                        Log.e(TAG, "failed to apply DRO to preshot bitmap: " + i);
                        throw new IOException();
                    }
                    bitmap = bitmaps.get(0);
                }*/

                PostProcessBitmapResult postProcessBitmapResult = postProcessBitmap(preshot_request, null, bitmap, true);
                bitmap = postProcessBitmapResult.bitmap;
                preshot_bitmaps.set(i, bitmap);
            }

            if( MyDebug.LOG )
                Log.d(TAG, "convert preshot bitmaps to video");

            method = main_activity.getApplicationInterface().createOutputVideoMethod();

            if( MyDebug.LOG )
                Log.d(TAG, "method? " + method);
            final String extension = "mp4";
            final int muxer_format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            if( method == ApplicationInterface.VideoMethod.FILE ) {
                File videoFile = main_activity.getApplicationInterface().createOutputVideoFile(true, extension, request.current_date);
                video_filename = videoFile.getAbsolutePath();
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + video_filename);
                muxer = new MediaMuxer(video_filename, muxer_format);
            }
            else {
                Uri uri;
                if( method == ApplicationInterface.VideoMethod.SAF ) {
                    uri = main_activity.getApplicationInterface().createOutputVideoSAF(true, extension, request.current_date);
                }
                else if( method == ApplicationInterface.VideoMethod.MEDIASTORE ) {
                    uri = main_activity.getApplicationInterface().createOutputVideoMediaStore(true, extension, request.current_date);
                }
                else {
                    uri = main_activity.getApplicationInterface().createOutputVideoUri();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + uri);
                video_pfd_saf = main_activity.getContentResolver().openFileDescriptor(uri, "rw");
                video_uri = uri;
                muxer = new MediaMuxer(video_pfd_saf.getFileDescriptor(), muxer_format);
            }
            muxer_info.muxer = muxer;

            if( MyDebug.LOG ) {
                Log.d(TAG, "preshot width: " + video_width);
                Log.d(TAG, "preshot height: " + video_height);
            }
            MediaFormat format = MediaFormat.createVideoFormat(mime_type, video_width, video_height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, preshot_bitmaps.size()*500000*8); // 500KB per frame
            format.setString(MediaFormat.KEY_FRAME_RATE, null); // format passed to MediaCodecList.findEncoderForFormat() must not specify a KEY_FRAME_RATE - so we set the KEY_FRAME_RATE later
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            //int videoTrackIndex = muxer.addTrack(format);
            //muxer.start();

            //encoder = MediaCodec.createEncoderByType(mime_type);
            String encoder_name = codecs.findEncoderForFormat(format);
            if( MyDebug.LOG )
                Log.d(TAG, "encoder_name: " + encoder_name);
            if( encoder_name == null ) {
                Log.e(TAG, "failed to find encoder");
                throw new IOException();
            }
            else {
                encoder = MediaCodec.createByCodecName(encoder_name);

                // now set KEY_FRAME_RATE (must be after findEncoderForFormat(), see note above)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 1000/Preview.preshot_interval_ms);

                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                Surface inputSurface = encoder.createInputSurface();
                encoder.start();

                if( request.store_location ) {
                    muxer.setLocation((float)request.location.getLatitude(), (float)request.location.getLongitude());
                }

                //int videoTrackIndex = -1;
                //int videoTrackIndex = muxer.addTrack(encoder.getOutputFormat());
                //muxer.start();

                long presentationTimeUs = 0;
                for(int i=0;i<preshot_bitmaps.size();i++) {
                    Bitmap bitmap = preshot_bitmaps.get(i);
                    if( MyDebug.LOG )
                        Log.d(TAG, "save pre-shot: " + i + " time: " + presentationTimeUs);
                    //ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());
                    //bitmap.copyPixelsToBuffer(buffer);

                    //int inputBufferIndex = encoder.dequeueInputBuffer(-1);
                    //if( inputBufferIndex >= 0 ) {
                    //    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                    //    inputBuffer.clear();
                    //    inputBuffer.put(buffer);
                    //    encoder.queueInputBuffer(inputBufferIndex, 0, buffer.limit(), presentationTimeUs, 0);
                    //}

                    //encodeVideoFrame(encoder, muxer_info, presentationTimeUs, false);

                    Canvas canvas = inputSurface.lockCanvas(null);
                    int xpos = (canvas.getWidth() - bitmap.getWidth())/2;
                    int ypos = (canvas.getHeight() - bitmap.getHeight())/2;
                    if( MyDebug.LOG )
                        Log.d(TAG, "render at: " + xpos + " , " + ypos);
                    canvas.drawBitmap(bitmap, xpos, ypos, null);
                    inputSurface.unlockCanvasAndPost(canvas);

                    encodeVideoFrame(encoder, muxer_info, presentationTimeUs, false);
                    //if( true )
                    //    throw new IOException(); // test

                    preshot_bitmaps.set(i, null); // so we know this bitmap is recycled
                    bitmap.recycle();
                    presentationTimeUs += (Preview.preshot_interval_ms*1000);
                }

                encodeVideoFrame(encoder, muxer_info, presentationTimeUs, true);
            }

            saved_preshots = true; // success!
        }
        catch(IOException | IllegalStateException e) {
            // ideally want to catch MediaCodec.CodecException, but then entire class would need to target
            // Android L - instead we catch its superclass IllegalStateException
            MyDebug.logStackTrace(TAG, "failed saving preshots video", e);
            // cleanup
            for(int i=0;i<preshot_bitmaps.size();i++) {
                if( MyDebug.LOG )
                    Log.d(TAG, "recycle preshot bitmap: " + i);
                Bitmap bitmap = preshot_bitmaps.get(i);
                if( bitmap != null ) {
                    bitmap.recycle();
                }
            }
        }
        finally {
            if( encoder != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "stop encoder");
                encoder.stop();
                if( MyDebug.LOG )
                    Log.d(TAG, "release encoder");
                encoder.release();
            }
            if( muxer != null ) {
                if( muxer_info.muxer_started ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "stop muxer");
                    muxer.stop();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "release muxer");
                muxer.release();
            }
            try {
                if( video_pfd_saf != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "close video_pfd_saf: " + video_pfd_saf);
                    video_pfd_saf.close();
                }
            }
            catch(IOException e) {
                MyDebug.logStackTrace(TAG, "failed close resources", e);
            }
        }

        if( saved_preshots ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saved preshots successfully");
            main_activity.getApplicationInterface().completeVideo(method, video_uri);
            main_activity.getApplicationInterface().broadcastVideo(method, video_uri, video_filename);
        }

        main_activity.savingImage(false);
    }

    /** Saves all the JPEG images in request.jpeg_images.
     * @param request The request to save.
     * @param suffix If there is more than one image and first_only is false, the i-th image
     *               filename will be appended with (suffix+i).
     * @param first_only If true, only save the first image.
     * @param update_thumbnail Whether to update the thumbnail and show the animation.
     * @param share If true, the median image will be marked as the one to share (for pause preview
     *              option).
     * @return Whether all images were successfully saved.
     */
    private boolean saveImages(Request request, String suffix, boolean first_only, boolean update_thumbnail, boolean share) {
        boolean success = true;
        int mid_image = request.jpeg_images.size()/2;
        for(int i=0;i<request.jpeg_images.size();i++) {
            // note, even if one image fails, we still try saving the other images - might as well give the user as many images as we can...
            byte [] image = request.jpeg_images.get(i);
            boolean multiple_jpegs = request.jpeg_images.size() > 1 && !first_only;
            String filename_suffix = (multiple_jpegs || request.force_suffix) ? suffix + (i + request.suffix_offset) : "";
            if( request.process_type == Request.ProcessType.X_NIGHT ) {
                filename_suffix = "_Night" + filename_suffix;
            }
            boolean share_image = share && (i == mid_image);
            if( !saveSingleImageNow(request, image, null, filename_suffix, update_thumbnail, share_image, false, false) ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "saveSingleImageNow failed for image: " + i);
                success = false;
            }
            if( first_only )
                break; // only requested the first
        }
        return success;
    }

    /** Saves all the images in request.jpeg_images, depending on the save_base option.
     */
    private void saveBaseImages(Request request, String suffix) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveBaseImages");
        if( !request.image_capture_intent && request.save_base != Request.SaveBase.SAVEBASE_NONE ) {
            if( MyDebug.LOG )
                Log.d(TAG, "save base images");

            Request base_request = request;
            if( request.process_type == Request.ProcessType.PANORAMA ) {
                // Important to save base images for panorama in PNG format, to avoid risk of not being able to reproduce the
                // same issue - decompressing JPEGs can vary between devices!
                // Also disable options that don't really make sense for base panorama images.
                base_request = request.copy();
                base_request.image_format = Request.ImageFormat.PNG;
                base_request.preference_stamp = "preference_stamp_no";
                base_request.preference_textstamp = "";
                base_request.do_auto_stabilise = false;
                base_request.mirror = false;
            }
            else if( request.process_type == Request.ProcessType.AVERAGE ) {
                // In case the base image needs to be postprocessed, we still want to save base images for NR at the 100% JPEG quality
                base_request = request.copy();
                base_request.image_quality = 100;
            }
            // don't update the thumbnails, only do this for the final image - so user doesn't think it's complete, click gallery, then wonder why the final image isn't there
            // also don't mark these images as being shared
            saveImages(base_request, suffix, base_request.save_base == Request.SaveBase.SAVEBASE_FIRST, false, false);
            // ignore return of saveImages - as for deciding whether to pause preview or not (which is all we use the success return for), all that matters is whether we saved the final HDR image
        }
    }

    /** Computes the width and height of a centred crop region after having rotated an image.
     * @param result - Array of length 2 which will be filled with the returned width and height.
     * @param level_angle_rad_abs - Absolute value of angle of rotation, in radians.
     * @param w0 - Rotated width.
     * @param h0 - Rotated height.
     * @param w1 - Original width.
     * @param h1 - Original height.
     * @param max_width - Maximum width to return.
     * @param max_height - Maximum height to return.
     * @return - Whether a crop region could be successfully calculated.
     */
    public static boolean autoStabiliseCrop(int [] result, double level_angle_rad_abs, double w0, double h0, int w1, int h1, int max_width, int max_height) {
        boolean ok = false;
        result[0] = 0;
        result[1] = 0;

        double tan_theta = Math.tan(level_angle_rad_abs);
        double sin_theta = Math.sin(level_angle_rad_abs);
        double denom = ( h0/w0 + tan_theta );
        double alt_denom = ( w0/h0 + tan_theta );
        if( denom < 1.0e-14 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "zero denominator?!");
        }
        else if( alt_denom < 1.0e-14 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "zero alt denominator?!");
        }
        else {
            int w2 = (int)(( h0 + 2.0*h1*sin_theta*tan_theta - w0*tan_theta ) / denom);
            int h2 = (int)(w2*h0/w0);
            int alt_h2 = (int)(( w0 + 2.0*w1*sin_theta*tan_theta - h0*tan_theta ) / alt_denom);
            int alt_w2 = (int)(alt_h2*w0/h0);
            if( MyDebug.LOG ) {
                //Log.d(TAG, "h0 " + h0 + " 2.0*h1*sin_theta*tan_theta " + 2.0*h1*sin_theta*tan_theta + " w0*tan_theta " + w0*tan_theta + " / h0/w0 " + h0/w0 + " tan_theta " + tan_theta);
                Log.d(TAG, "w2 = " + w2 + " , h2 = " + h2);
                Log.d(TAG, "alt_w2 = " + alt_w2 + " , alt_h2 = " + alt_h2);
            }
            if( alt_w2 < w2 ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "chose alt!");
                }
                w2 = alt_w2;
                h2 = alt_h2;
            }
            if( w2 <= 0 )
                w2 = 1;
            else if( w2 > max_width )
                w2 = max_width;
            if( h2 <= 0 )
                h2 = 1;
            else if( h2 > max_height )
                h2 = max_height;

            ok = true;
            result[0] = w2;
            result[1] = h2;
        }
        return ok;
    }

    /** Performs the auto-stabilise algorithm on the image.
     * @param data The jpeg data.
     * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
     * @param level_angle The angle in degrees to rotate the image.
     * @param is_front_facing Whether the camera is front-facing.
     * @return A bitmap representing the auto-stabilised jpeg.
     */
    private Bitmap autoStabilise(byte [] data, Bitmap bitmap, double level_angle, boolean is_front_facing) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "autoStabilise");
            Log.d(TAG, "level_angle: " + level_angle);
            Log.d(TAG, "is_front_facing: " + is_front_facing);
        }
        while( level_angle < -90 )
            level_angle += 180;
        while( level_angle > 90 )
            level_angle -= 180;
        if( MyDebug.LOG )
            Log.d(TAG, "auto stabilising... angle: " + level_angle);
        if( bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to auto-stabilise");
            // bitmap doesn't need to be mutable here, as this won't be the final bitmap returned from the auto-stabilise code
            bitmap = loadBitmapWithRotation(data, false);
            if( bitmap == null ) {
                main_activity.getPreview().showToast(null, R.string.failed_to_auto_stabilise);
                System.gc();
            }
        }
        if( bitmap != null ) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if( MyDebug.LOG ) {
                Log.d(TAG, "level_angle: " + level_angle);
                Log.d(TAG, "decoded bitmap size " + width + ", " + height);
                Log.d(TAG, "bitmap size: " + width*height*4);
            }
                /*for(int y=0;y<height;y++) {
                    for(int x=0;x<width;x++) {
                        int col = bitmap.getPixel(x, y);
                        col = col & 0xffff0000; // mask out red component
                        bitmap.setPixel(x, y, col);
                    }
                }*/
            Matrix matrix = new Matrix();
            double level_angle_rad_abs = Math.abs( Math.toRadians(level_angle) );
            int w1 = width, h1 = height;
            double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
            double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));
            // apply a scale so that the overall image size isn't increased
            float orig_size = w1*h1;
            float rotated_size = (float)(w0*h0);
            float scale = (float)Math.sqrt(orig_size/rotated_size);
            if( main_activity.test_low_memory ) {
                if( MyDebug.LOG ) {
                    Log.d(TAG, "TESTING LOW MEMORY");
                    Log.d(TAG, "scale was: " + scale);
                }
                // test 20MP on Galaxy Nexus or Nexus 7; 29MP on Nexus 6 and 36MP OnePlus 3T
                if( width*height >= 7500 )
                    scale *= 1.5f;
                else
                    scale *= 2.0f;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "w0 = " + w0 + " , h0 = " + h0);
                Log.d(TAG, "w1 = " + w1 + " , h1 = " + h1);
                Log.d(TAG, "scale = sqrt " + orig_size + " / " + rotated_size + " = " + scale);
            }
            matrix.postScale(scale, scale);
            w0 *= scale;
            h0 *= scale;
            // warning "Possibly lossy implicit cast in compound assignment" suppressed:
            // it's intentional that we multiply int by float, and implicitly cast back to int
            // (the suggested solution is to first cast the float to int before multiplying, which
            // we don't want)
            //noinspection lossy-conversions
            w1 *= scale;
            //noinspection lossy-conversions
            h1 *= scale;
            if( MyDebug.LOG ) {
                Log.d(TAG, "after scaling: w0 = " + w0 + " , h0 = " + h0);
                Log.d(TAG, "after scaling: w1 = " + w1 + " , h1 = " + h1);
            }
            if( is_front_facing ) {
                matrix.postRotate((float)-level_angle);
            }
            else {
                matrix.postRotate((float)level_angle);
            }
            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            // careful, as new_bitmap is sometimes not a copy!
            if( new_bitmap != bitmap ) {
                bitmap.recycle();
                bitmap = new_bitmap;
            }
            System.gc();
            if( MyDebug.LOG ) {
                Log.d(TAG, "rotated and scaled bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
                Log.d(TAG, "rotated and scaled bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
            }

            int [] crop = new int [2];
            if( autoStabiliseCrop(crop, level_angle_rad_abs, w0, h0, w1, h1, bitmap.getWidth(), bitmap.getHeight()) ) {
                int w2 = crop[0];
                int h2 = crop[1];
                int x0 = (bitmap.getWidth()-w2)/2;
                int y0 = (bitmap.getHeight()-h2)/2;
                if( MyDebug.LOG ) {
                    Log.d(TAG, "x0 = " + x0 + " , y0 = " + y0);
                }
                new_bitmap = Bitmap.createBitmap(bitmap, x0, y0, w2, h2);
                if( new_bitmap != bitmap ) {
                    bitmap.recycle();
                    bitmap = new_bitmap;
                }
                System.gc();
            }

            if( MyDebug.LOG )
                Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
            // Usually createBitmap will return a mutable bitmap, but not if the source bitmap (which we set as immutable)
            // is returned (if the level angle is (tolerantly) 0.
            // see testPhotoStamp() for testing this.
            if( !bitmap.isMutable() ) {
                new_bitmap = bitmap.copy(bitmap.getConfig(), true);
                bitmap.recycle();
                bitmap = new_bitmap;
            }
        }
        return bitmap;
    }

    /** Mirrors the image.
     * @param data The jpeg data.
     * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
     * @return A bitmap representing the mirrored jpeg.
     */
    private Bitmap mirrorImage(byte [] data, Bitmap bitmap) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "mirrorImage");
        }
        if( bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to mirror");
            // bitmap doesn't need to be mutable here, as this won't be the final bitmap returned from the mirroring code
            bitmap = loadBitmapWithRotation(data, false);
            if( bitmap == null ) {
                // don't bother warning to the user - we simply won't mirror the image
                System.gc();
            }
        }
        if( bitmap != null ) {
            Matrix matrix = new Matrix();
            matrix.preScale(-1.0f, 1.0f);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            // careful, as new_bitmap is sometimes not a copy!
            if( new_bitmap != bitmap ) {
                bitmap.recycle();
                bitmap = new_bitmap;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
        }
        return bitmap;
    }

    /** Applies any photo stamp options (if they exist).
     * @param data The jpeg data.
     * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
     * @return A bitmap representing the stamped jpeg. Will be null if the input bitmap is null and
     *         no photo stamp is applied.
     */
    private Bitmap stampImage(final Request request, byte [] data, Bitmap bitmap) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "stampImage");
        }
        //final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
        boolean dategeo_stamp = request.preference_stamp.equals("preference_stamp_yes");
        boolean text_stamp = !request.preference_textstamp.isEmpty();
        if( dategeo_stamp || text_stamp ) {
            if( bitmap == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "decode bitmap in order to stamp info");
                bitmap = loadBitmapWithRotation(data, true);
                if( bitmap == null ) {
                    main_activity.getPreview().showToast(null, R.string.failed_to_stamp);
                    System.gc();
                }
            }
            if( bitmap != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "stamp info to bitmap: " + bitmap);
                if( MyDebug.LOG )
                    Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());

                String stamp_string = "";
                /* We now stamp via a TextView instead of using MyApplicationInterface.drawTextWithBackground().
                 * This is important in order to satisfy the Google emoji policy...
                 */

                int font_size = request.font_size;
                int color = request.color;
                String pref_style = request.pref_style;
                if( MyDebug.LOG )
                    Log.d(TAG, "pref_style: " + pref_style);
                String preference_stamp_dateformat = request.preference_stamp_dateformat;
                String preference_stamp_timeformat = request.preference_stamp_timeformat;
                String preference_stamp_gpsformat = request.preference_stamp_gpsformat;
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                if( MyDebug.LOG ) {
                    Log.d(TAG, "decoded bitmap size " + width + ", " + height);
                    Log.d(TAG, "bitmap size: " + width*height*4);
                }
                Canvas canvas = new Canvas(bitmap);
                p.setColor(Color.WHITE);
                // we don't use the density of the screen, because we're stamping to the image, not drawing on the screen (we don't want the font height to depend on the device's resolution)
                // instead we go by 1 pt == 1/72 inch height, and scale for an image height (or width if in portrait) of 4" (this means the font height is also independent of the photo resolution)
                int smallest_size = Math.min(width, height);
                float scale = ((float)smallest_size) / (72.0f*4.0f);
                int font_size_pixel = (int)(font_size * scale + 0.5f); // convert pt to pixels
                if( MyDebug.LOG ) {
                    Log.d(TAG, "scale: " + scale);
                    Log.d(TAG, "font_size: " + font_size);
                    Log.d(TAG, "font_size_pixel: " + font_size_pixel);
                }
                p.setTextSize(font_size_pixel);
                int offset_x = (int)(8 * scale + 0.5f); // convert pt to pixels
                int offset_y = (int)(8 * scale + 0.5f); // convert pt to pixels
                int diff_y = (int)((font_size+4) * scale + 0.5f); // convert pt to pixels
                int ypos = height - offset_y;
                p.setTextAlign(Align.RIGHT);
                MyApplicationInterface.Shadow draw_shadowed = MyApplicationInterface.Shadow.SHADOW_NONE;
                switch( pref_style ) {
                    case "preference_stamp_style_shadowed":
                        draw_shadowed = MyApplicationInterface.Shadow.SHADOW_OUTLINE;
                        break;
                    case "preference_stamp_style_plain":
                        draw_shadowed = MyApplicationInterface.Shadow.SHADOW_NONE;
                        break;
                    case "preference_stamp_style_background":
                        draw_shadowed = MyApplicationInterface.Shadow.SHADOW_BACKGROUND;
                        break;
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "draw_shadowed: " + draw_shadowed);
                if( dategeo_stamp ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "stamp date");
                    // doesn't respect user preferences such as 12/24 hour - see note about in draw() about DateFormat.getTimeInstance()
                    String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, request.current_date);
                    String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, request.current_date);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "date_stamp: " + date_stamp);
                        Log.d(TAG, "time_stamp: " + time_stamp);
                    }
                    if( !date_stamp.isEmpty() || !time_stamp.isEmpty() ) {
                        String datetime_stamp = "";
                        if( !date_stamp.isEmpty() )
                            datetime_stamp += date_stamp;
                        if( !time_stamp.isEmpty() ) {
                            if( !datetime_stamp.isEmpty() )
                                datetime_stamp += " ";
                            datetime_stamp += time_stamp;
                        }
                        //applicationInterface.drawTextWithBackground(canvas, p, datetime_stamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                        if( stamp_string.isEmpty() )
                            stamp_string = datetime_stamp;
                        else
                            stamp_string = datetime_stamp + "\n" + stamp_string;
                    }
                    ypos -= diff_y;
                    String gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, request.preference_units_distance, request.store_location, request.location, request.store_geo_direction, request.geo_direction);
                    if( !gps_stamp.isEmpty() ) {
                        // don't log gps_stamp, in case of privacy!

                        /*Address address = null;
                        if( request.store_location && !request.preference_stamp_geo_address.equals("preference_stamp_geo_address_no") ) {
                            boolean block_geocoder;
                            synchronized(this) {
                                block_geocoder = app_is_paused;
                            }
                            // try to find an address
                            // n.b., if we update the class being used, consider whether the info on Geocoder in preference_stamp_geo_address_summary needs updating
                            if( block_geocoder ) {
                                // seems safer to not try to initiate potential network connections (via geocoder) if Open Camera
                                // has paused and we're still saving images
                                if( MyDebug.LOG )
                                    Log.d(TAG, "don't call geocoder for photostamp as app is paused");
                            }
                            else if( Geocoder.isPresent() ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "geocoder is present");
                                Geocoder geocoder = new Geocoder(main_activity, Locale.getDefault());
                                try {
                                    List<Address> addresses = geocoder.getFromLocation(request.location.getLatitude(), request.location.getLongitude(), 1);
                                    if( addresses != null && addresses.size() > 0 ) {
                                        address = addresses.get(0);
                                        // don't log address, in case of privacy!
                                        if( MyDebug.LOG ) {
                                            Log.d(TAG, "max line index: " + address.getMaxAddressLineIndex());
                                        }
                                    }
                                }
                                catch(Exception e) {
                                    MyDebug.logStackTrace(TAG, "failed to read from geocoder", e);
                                }
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "geocoder not present");
                            }
                        }*/

                        //if( address == null || request.preference_stamp_geo_address.equals("preference_stamp_geo_address_both") )
                        {
                            if( MyDebug.LOG )
                                Log.d(TAG, "display gps coords");
                            // want GPS coords (either in addition to the address, or we don't have an address)
                            // we'll also enter here if store_location is false, but we have geo direction to display
                            //applicationInterface.drawTextWithBackground(canvas, p, gps_stamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                            if( stamp_string.isEmpty() )
                                stamp_string = gps_stamp;
                            else
                                stamp_string = gps_stamp + "\n" + stamp_string;
                            ypos -= diff_y;
                        }
                        /*else if( request.store_geo_direction ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "not displaying gps coords, but need to display geo direction");
                            // we are displaying an address instead of GPS coords, but we still need to display the geo direction
                            gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, request.preference_units_distance, false, null, request.store_geo_direction, request.geo_direction);
                            if( gps_stamp.length() > 0 ) {
                                // don't log gps_stamp, in case of privacy!
                                //applicationInterface.drawTextWithBackground(canvas, p, gps_stamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                                if( stamp_string.length() == 0 )
                                    stamp_string = gps_stamp;
                                else
                                    stamp_string = gps_stamp + "\n" + stamp_string;
                                ypos -= diff_y;
                            }
                        }*/

                        /*if( address != null ) {
                            for(int i=0;i<=address.getMaxAddressLineIndex();i++) {
                                // write in reverse order
                                String addressLine = address.getAddressLine(address.getMaxAddressLineIndex()-i);
                                //applicationInterface.drawTextWithBackground(canvas, p, addressLine, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                                if( stamp_string.length() == 0 )
                                    stamp_string = addressLine;
                                else
                                    stamp_string = addressLine + "\n" + stamp_string;
                                ypos -= diff_y;
                            }
                        }*/
                    }
                }
                if( text_stamp ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "stamp text");

                    //applicationInterface.drawTextWithBackground(canvas, p, request.preference_textstamp, color, Color.BLACK, width - offset_x, ypos, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, draw_shadowed);
                    if( stamp_string.isEmpty() )
                        stamp_string = request.preference_textstamp;
                    else
                        stamp_string = request.preference_textstamp + "\n" + stamp_string;

                    //noinspection UnusedAssignment
                    ypos -= diff_y;
                }

                if( !stamp_string.isEmpty() ) {
                    // don't log stamp_string, in case of privacy!

                    @SuppressLint("InflateParams")
                    final View stamp_view = LayoutInflater.from(main_activity).inflate(R.layout.stamp_image_text, null);
                    final LinearLayout layout = stamp_view.findViewById(R.id.layout);
                    final TextView textview = stamp_view.findViewById(R.id.text_view);

                    textview.setVisibility(View.VISIBLE);
                    textview.setTextColor(color);
                    textview.setTextSize(TypedValue.COMPLEX_UNIT_PX, font_size_pixel);
                    textview.setText(stamp_string);
                    if( draw_shadowed == MyApplicationInterface.Shadow.SHADOW_OUTLINE ) {
                        //noinspection PointlessArithmeticExpression
                        float shadow_radius = (1.0f * scale + 0.5f); // convert pt to pixels
                        shadow_radius = Math.max(shadow_radius, 1.0f);
                        if( MyDebug.LOG )
                            Log.d(TAG, "shadow_radius: " + shadow_radius);
                        textview.setShadowLayer(shadow_radius, 0.0f, 0.0f, Color.BLACK);
                    }
                    else if( draw_shadowed == MyApplicationInterface.Shadow.SHADOW_BACKGROUND ) {
                        textview.setBackgroundColor(Color.argb(64, 0, 0, 0));
                    }
                    //textview.setBackgroundColor(Color.BLACK); // test
                    textview.setGravity(Gravity.END); // so text is right-aligned - important when there are multiple lines

                    layout.measure(canvas.getWidth(), canvas.getHeight());
                    layout.layout(0, 0, canvas.getWidth(), canvas.getHeight());
                    canvas.translate(width - offset_x - textview.getWidth(), height - offset_y - textview.getHeight());
                    layout.draw(canvas);
                }
            }
        }
        return bitmap;
    }

    private static class PostProcessBitmapResult {
        final Bitmap bitmap;

        PostProcessBitmapResult(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }

    /** Performs post-processing on the data, or bitmap if non-null, for saveSingleImageNow.
     */
    private PostProcessBitmapResult postProcessBitmap(final Request request, byte [] data, Bitmap bitmap, boolean ignore_exif_orientation) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "postProcessBitmap");
        long time_s = System.currentTimeMillis();

        if( !ignore_exif_orientation ) {
            if( bitmap != null ) {
                // rotate the bitmap if necessary for exif tags
                if( MyDebug.LOG )
                    Log.d(TAG, "rotate pre-existing bitmap for exif tags?");
                bitmap = rotateForExif(bitmap, data);
            }
        }

        if( request.do_auto_stabilise ) {
            bitmap = autoStabilise(data, bitmap, request.level_angle, request.is_front_facing);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: time after auto-stabilise: " + (System.currentTimeMillis() - time_s));
        }
        if( request.mirror ) {
            bitmap = mirrorImage(data, bitmap);
        }
        if( request.image_format != Request.ImageFormat.STD && bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to convert file format");
            bitmap = loadBitmapWithRotation(data, true);
            if( bitmap == null ) {
                // if we can't load bitmap for converting file formats, don't want to continue
                System.gc();
                throw new IOException();
            }
        }
        if( request.remove_device_exif != Request.RemoveDeviceExif.OFF && bitmap == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "need to decode bitmap to strip exif tags");
            // if removing device exif data, it's easier to do this by going through the codepath that
            // resaves the bitmap, and then we avoid transferring/adding exif tags that we don't want
            bitmap = loadBitmapWithRotation(data, true);
            if( bitmap == null ) {
                // if we can't load bitmap for removing device tags, don't want to continue
                System.gc();
                throw new IOException();
            }
        }
        bitmap = stampImage(request, data, bitmap);
        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: time after photostamp: " + (System.currentTimeMillis() - time_s));
        }
        return new PostProcessBitmapResult(bitmap);
    }

    /** Converts from Request.ImageFormat to Bitmap.CompressFormat.
     */
    private static Bitmap.CompressFormat getBitmapCompressFormat(Request.ImageFormat image_format) {
        Bitmap.CompressFormat compress_format;
        switch( image_format ) {
            case WEBP:
                compress_format = Bitmap.CompressFormat.WEBP;
                break;
            case PNG:
                compress_format = Bitmap.CompressFormat.PNG;
                break;
            default:
                compress_format = Bitmap.CompressFormat.JPEG;
                break;
        }
        return compress_format;
    }

    /** May be run in saver thread or picture callback thread (depending on whether running in background).
     *  The requests.images field is ignored, instead we save the supplied data or bitmap.
     *  If bitmap is null, then the supplied jpeg data is saved. If bitmap is non-null, then the bitmap is
     *  saved, but the supplied data is still used to read EXIF data from.
     *  @param update_thumbnail - Whether to update the thumbnail (and show the animation).
     *  @param share_image - Whether this image should be marked as the one to share (if multiple images can
     *  be saved from a single shot (e.g., saving exposure images with HDR).
     *  @param ignore_raw_only - If true, then save even if RAW Only is set (needed for HDR mode
     *                         where we always save the HDR image even though it's a JPEG - the
     *                         RAW preference only affects the base images.
     * @param ignore_exif_orientation - If bitmap is non-null, then set this to true if the bitmap has already
     *                                  been rotated to account for Exif orientation tags in the data.
     */
    @SuppressLint("SimpleDateFormat")
    private boolean saveSingleImageNow(final Request request, byte [] data, Bitmap bitmap, String filename_suffix, boolean update_thumbnail, boolean share_image, boolean ignore_raw_only, boolean ignore_exif_orientation) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveSingleImageNow");

        if( request.type != Request.Type.JPEG ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveImageNow called with non-jpeg request");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }
        else if( data == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saveSingleImageNow called with no data");
            // throw runtime exception, as this is a programming error
            throw new RuntimeException();
        }
        long time_s = System.currentTimeMillis();

        boolean success = false;
        final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
        boolean raw_only = !ignore_raw_only && applicationInterface.isRawOnly();
        if( MyDebug.LOG )
            Log.d(TAG, "raw_only: " + raw_only);
        StorageUtils storageUtils = main_activity.getStorageUtils();

        String extension;
        switch( request.image_format ) {
            case WEBP:
                extension = "webp";
                break;
            case PNG:
                extension = "png";
                break;
            default:
                extension = "jpg";
                break;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "extension: " + extension);

        main_activity.savingImage(true);

        // If using SAF or image_capture_intent is true, or using scoped storage, only saveUri is non-null
        // Otherwise, only picFile is non-null
        File picFile = null;
        Uri saveUri = null;
        boolean use_media_store = false;
        ContentValues contentValues = null; // used if using scoped storage
        String savedFilePath = null; // Track file path for Nextcloud upload
        try {
            if( !raw_only ) {
                PostProcessBitmapResult postProcessBitmapResult = postProcessBitmap(request, data, bitmap, ignore_exif_orientation);
                bitmap = postProcessBitmapResult.bitmap;
            }

            if( raw_only ) {
                // don't save the JPEG
                success = true;
            }
            else if( request.image_capture_intent ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "image_capture_intent");
                if( request.image_capture_intent_uri != null )
                {
                    // Save the bitmap to the specified URI (use a try/catch block)
                    if( MyDebug.LOG )
                        Log.d(TAG, "save to: " + request.image_capture_intent_uri);
                    saveUri = request.image_capture_intent_uri;
                }
                else
                {
                    // If the intent doesn't contain an URI, send the bitmap as a parcel
                    // (it is a good idea to reduce its size to ~50k pixels before)
                    if( MyDebug.LOG )
                        Log.d(TAG, "sent to intent via parcel");
                    if( bitmap == null ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "create bitmap");
                        // bitmap we return doesn't need to be mutable
                        bitmap = loadBitmapWithRotation(data, false);
                    }
                    if( bitmap != null ) {
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "decoded bitmap size " + width + ", " + height);
                            Log.d(TAG, "bitmap size: " + width*height*4);
                        }
                        final int small_size_c = 128;
                        if( width > small_size_c ) {
                            float scale = ((float)small_size_c)/(float)width;
                            if( MyDebug.LOG )
                                Log.d(TAG, "scale to " + scale);
                            Matrix matrix = new Matrix();
                            matrix.postScale(scale, scale);
                            Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                            // careful, as new_bitmap is sometimes not a copy!
                            if( new_bitmap != bitmap ) {
                                bitmap.recycle();
                                bitmap = new_bitmap;
                            }
                        }
                    }
                    if( MyDebug.LOG ) {
                        if( bitmap != null ) {
                            Log.d(TAG, "returned bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
                            Log.d(TAG, "returned bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
                        }
                        else {
                            Log.e(TAG, "no bitmap created");
                        }
                    }
                    if( bitmap != null )
                        main_activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
                    main_activity.finish();
                }
            }
            else if( storageUtils.isUsingSAF() ) {
                saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, extension, request.current_date);
            }
            else if( MainActivity.useScopedStorage() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "use media store");
                use_media_store = true;
                Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                contentValues = new ContentValues();
                String picName = storageUtils.createMediaFilename(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, 0, "." + extension, request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "picName: " + picName);
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, picName);
                String mime_type = storageUtils.getImageMimeType(extension);
                if( MyDebug.LOG )
                    Log.d(TAG, "mime_type: " + mime_type);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, mime_type);
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    String relative_path = storageUtils.getSaveRelativeFolder();
                    if( MyDebug.LOG )
                        Log.d(TAG, "relative_path: " + relative_path);
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, relative_path);
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                // Note, we catch exceptions specific to insert() here and rethrow as IOException,
                // rather than catching below, to avoid catching things too broadly - e.g.,
                // IllegalStateException can also be thrown via "new Canvas" (from
                // postProcessBitmap()) but this is a programming error that we shouldn't catch.
                // Catching too broadly could mean we miss genuine problems that should be fixed.
                try {
                    saveUri = main_activity.getContentResolver().insert(folder, contentValues);
                    // Construct file path for Nextcloud upload
                    if( saveUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                        String relative_path = storageUtils.getSaveRelativeFolder();
                        // Ensure proper path separator
                        if( !relative_path.endsWith("/") )
                            relative_path = relative_path + "/";
                        savedFilePath = "/storage/emulated/0/" + relative_path + picName;
                    }
                }
                catch(IllegalArgumentException e) {
                    // can happen for mediastore method if invalid ContentResolver.insert() call
                    MyDebug.logStackTrace(TAG, "IllegalArgumentException inserting to mediastore", e);
                    throw new IOException();
                }
                catch(IllegalStateException e) {
                    // have received Google Play crashes from ContentResolver.insert() call for mediastore method
                    MyDebug.logStackTrace(TAG, "IllegalStateException inserting to mediastore", e);
                    throw new IOException();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "saveUri: " + saveUri);
                if( saveUri == null ) {
                    throw new IOException();
                }
            }
            else {
                picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, extension, request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + picFile.getAbsolutePath());
                if( picFile != null )
                    savedFilePath = picFile.getAbsolutePath();
            }

            if( MyDebug.LOG )
                Log.d(TAG, "saveUri: " + saveUri);

            if( picFile != null || saveUri != null ) {
                OutputStream outputStream;
                if( picFile != null )
                    outputStream = new FileOutputStream(picFile);
                else
                    outputStream = main_activity.getContentResolver().openOutputStream(saveUri);
                try {
                    if( bitmap != null ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "compress bitmap, quality " + request.image_quality);
                        Bitmap.CompressFormat compress_format = getBitmapCompressFormat(request.image_format);
                        bitmap.compress(compress_format, request.image_quality, outputStream);
                    }
                    else {
                        outputStream.write(data);
                    }
                }
                finally {
                    outputStream.close();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "saveImageNow saved photo");
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Save single image performance: time after saving photo: " + (System.currentTimeMillis() - time_s));
                }

                if( saveUri == null ) { // if saveUri is non-null, then we haven't succeeded until we've copied to the saveUri
                    success = true;
                }

                if( request.image_format == Request.ImageFormat.STD ) {
                    // handle transferring/setting Exif tags (JPEG format only)
                    if( bitmap != null ) {
                        // need to update EXIF data! (only supported for JPEG image formats)
                        if( MyDebug.LOG )
                            Log.d(TAG, "set Exif tags from data");
                        if( picFile != null ) {
                            setExifFromData(request, data, picFile);
                        }
                        else {
                            ParcelFileDescriptor parcelFileDescriptor = main_activity.getContentResolver().openFileDescriptor(saveUri, "rw");
                            try {
                                if( parcelFileDescriptor != null ) {
                                    FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                                    setExifFromData(request, data, fileDescriptor);
                                }
                                else {
                                    Log.e(TAG, "failed to create ParcelFileDescriptor for saveUri: " + saveUri);
                                }
                            }
                            finally {
                                if( parcelFileDescriptor != null ) {
                                    try {
                                        parcelFileDescriptor.close();
                                    }
                                    catch(IOException e) {
                                        MyDebug.logStackTrace(TAG, "fail to close parcelFileDescriptor", e);
                                    }
                                }
                            }
                        }
                    }
                    else {
                        updateExif(request, picFile, saveUri);
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "Save single image performance: time after updateExif: " + (System.currentTimeMillis() - time_s));
                        }
                    }
                }

                if( update_thumbnail ) {
                    // clear just in case we're unable to update this - don't want an out of date cached uri
                    storageUtils.clearLastMediaScanned();
                }

                boolean hasnoexifdatetime = request.remove_device_exif != Request.RemoveDeviceExif.OFF && request.remove_device_exif != Request.RemoveDeviceExif.KEEP_DATETIME;

                if( picFile != null && saveUri == null ) {
                    // broadcast for SAF is done later, when we've actually written out the file
                    storageUtils.broadcastFile(picFile, true, false, update_thumbnail, hasnoexifdatetime, null);
                    main_activity.test_last_saved_image = picFile.getAbsolutePath();
                }

                if( request.image_capture_intent ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "finish activity due to being called from intent");
                    main_activity.setResult(Activity.RESULT_OK);
                    main_activity.finish();
                }

                if( saveUri != null ) {
                    success = true;

                    if( use_media_store ) {
                        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                            contentValues.clear();
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                            main_activity.getContentResolver().update(saveUri, contentValues, null, null);

                            // Query the actual filename from MediaStore (includes ~2, ~3 suffixes if added)
                            android.database.Cursor cursor = null;
                            try {
                                cursor = main_activity.getContentResolver().query(
                                    saveUri,
                                    new String[]{MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH},
                                    null, null, null
                                );
                                if( cursor != null && cursor.moveToFirst() ) {
                                    int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                                    int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                                    if( nameIndex >= 0 && pathIndex >= 0 ) {
                                        String actualName = cursor.getString(nameIndex);
                                        String actualRelativePath = cursor.getString(pathIndex);
                                        if( actualName != null && actualRelativePath != null ) {
                                            savedFilePath = "/storage/emulated/0/" + actualRelativePath + actualName;
                                            if( MyDebug.LOG )
                                                Log.d(TAG, "Actual saved file path from MediaStore: " + savedFilePath);
                                        }
                                    }
                                }
                            }
                            catch(Exception e) {
                                if( MyDebug.LOG )
                                    Log.e(TAG, "Failed to query actual filename from MediaStore", e);
                            }
                            finally {
                                if( cursor != null )
                                    cursor.close();
                            }
                        }

                        // no need to broadcast when using mediastore method
                        if( !request.image_capture_intent ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "announce mediastore uri");
                            // in theory this is pointless, as announceUri no longer does anything on Android 7+,
                            // and mediastore method is only used on Android 10+, but keep this just in case
                            // announceUri does something in future
                            storageUtils.announceUri(saveUri, true, false);
                            if( update_thumbnail ) {
                                // we also want to save the uri - we can use the media uri directly, rather than having to scan it
                                storageUtils.setLastMediaScanned(saveUri, false, hasnoexifdatetime, saveUri);
                            }
                        }
                    }
                    else {
                        broadcastSAFFile(saveUri, update_thumbnail, hasnoexifdatetime, request.image_capture_intent);
                    }

                    main_activity.test_last_saved_imageuri = saveUri;
                }
            }
        }
        catch(FileNotFoundException e) {
            MyDebug.logStackTrace(TAG, "file not found", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "I/O error writing file", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }
        catch(SecurityException e) {
            // received security exception from copyFileToUri()->openOutputStream() from Google Play
            // update: no longer have copyFileToUri() (as no longer use temporary files for SAF), but might as well keep this
            MyDebug.logStackTrace(TAG, "security exception writing file", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
        }

        if( raw_only ) {
            // no saved image to record
        }
        else if( success && saveUri == null ) {
            applicationInterface.addLastImage(picFile, share_image);
        }
        else if( success && storageUtils.isUsingSAF() ){
            applicationInterface.addLastImageSAF(saveUri, share_image);
        }
        else if( success && use_media_store ){
            applicationInterface.addLastImageMediaStore(saveUri, share_image);
        }

        // I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
        if( success && main_activity.getPreview().getCameraController() != null && update_thumbnail ) {
            // update thumbnail - this should be done after restarting preview, so that the preview is started asap
            CameraController.Size size = main_activity.getPreview().getCameraController().getPictureSize();
            int ratio = (int) Math.ceil((double) size.width / main_activity.getPreview().getView().getWidth());
            int sample_size = Integer.highestOneBit(ratio);
            sample_size *= request.sample_factor;
            if( MyDebug.LOG ) {
                Log.d(TAG, "    picture width: " + size.width);
                Log.d(TAG, "    preview width: " + main_activity.getPreview().getView().getWidth());
                Log.d(TAG, "    ratio        : " + ratio);
                Log.d(TAG, "    sample_size  : " + sample_size);
            }
            Bitmap thumbnail;
            if( bitmap == null ) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = false;
                options.inSampleSize = sample_size;
                thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "thumbnail width: " + thumbnail.getWidth());
                    Log.d(TAG, "thumbnail height: " + thumbnail.getHeight());
                }
                // now get the rotation from the Exif data
                if( MyDebug.LOG )
                    Log.d(TAG, "rotate thumbnail for exif tags?");
                thumbnail = rotateForExif(thumbnail, data);
            }
            else {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                Matrix matrix = new Matrix();
                float scale = 1.0f / (float)sample_size;
                matrix.postScale(scale, scale);
                if( MyDebug.LOG )
                    Log.d(TAG, "    scale: " + scale);
                try {
                    thumbnail = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "thumbnail width: " + thumbnail.getWidth());
                        Log.d(TAG, "thumbnail height: " + thumbnail.getHeight());
                    }
                    // don't need to rotate for exif, as we already did that when creating the bitmap
                }
                catch(IllegalArgumentException e) {
                    // received IllegalArgumentException on Google Play from Bitmap.createBitmap; documentation suggests this
                    // means width or height are 0 - but trapping that didn't fix the problem
                    // or "the x, y, width, height values are outside of the dimensions of the source bitmap", but that can't be
                    // true here
                    // crashes seem to all be Android 7.1 or earlier, so maybe this is a bug that's been fixed - but catch it anyway
                    // as it's grown popular
                    MyDebug.logStackTrace(TAG, "can't create thumbnail bitmap due to IllegalArgumentException?!", e);
                    thumbnail = null;
                }
            }
            if( thumbnail == null ) {
                // received crashes on Google Play suggesting that thumbnail could not be created
                if( MyDebug.LOG )
                    Log.e(TAG, "failed to create thumbnail bitmap");
            }
            else {
                final Bitmap thumbnail_f = thumbnail;
                main_activity.runOnUiThread(new Runnable() {
                    public void run() {
                        applicationInterface.updateThumbnail(thumbnail_f, false);
                    }
                });
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Save single image performance: time after creating thumbnail: " + (System.currentTimeMillis() - time_s));
                }
            }
        }

        if( bitmap != null ) {
            bitmap.recycle();
        }

        System.gc();

        main_activity.savingImage(false);

        // Trigger Nextcloud upload if enabled and save was successful
        if( success && savedFilePath != null ) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(main_activity);
            boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);

            Log.d(TAG, "Nextcloud: success=" + success + ", savedFilePath=" + savedFilePath + ", uploadEnabled=" + uploadEnabled);

            if( uploadEnabled ) {
                Log.d(TAG, "Triggering Nextcloud upload for: " + savedFilePath);
                NextcloudUploadService.startUpload(main_activity, savedFilePath);
            }
        }

        if( MyDebug.LOG ) {
            Log.d(TAG, "Save single image performance: total time: " + (System.currentTimeMillis() - time_s));
        }
        return success;
    }

    /** As setExifFromFile, but can read the Exif tags directly from the jpeg data rather than a file.
     */
    private void setExifFromData(final Request request, byte [] data, File to_file) throws IOException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setExifFromData");
            Log.d(TAG, "to_file: " + to_file);
        }
        InputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(data);
            ExifInterface exif = new ExifInterface(inputStream);
            ExifInterface exif_new = new ExifInterface(to_file.getAbsolutePath());
            setExif(request, exif, exif_new);
        }
        finally {
            if( inputStream != null ) {
                inputStream.close();
            }
        }
    }

    private void broadcastSAFFile(Uri saveUri, boolean set_last_scanned, boolean hasnoexifdatetime, boolean image_capture_intent) {
        if( MyDebug.LOG )
            Log.d(TAG, "broadcastSAFFile");
        StorageUtils storageUtils = main_activity.getStorageUtils();
        storageUtils.broadcastUri(saveUri, true, false, set_last_scanned, hasnoexifdatetime, image_capture_intent);
    }

    /** As setExifFromFile, but can read the Exif tags directly from the jpeg data, and to a file descriptor, rather than a file.
     */
    private void setExifFromData(final Request request, byte [] data, FileDescriptor to_file_descriptor) throws IOException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "setExifFromData");
            Log.d(TAG, "to_file_descriptor: " + to_file_descriptor);
        }
        InputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(data);
            ExifInterface exif = new ExifInterface(inputStream);
            ExifInterface exif_new = new ExifInterface(to_file_descriptor);
            setExif(request, exif, exif_new);
        }
        finally {
            if( inputStream != null ) {
                inputStream.close();
            }
        }
    }

    /** Transfers device exif info. Should only be called if request.remove_device_exif == Request.RemoveDeviceExif.OFF.
     */
    private void transferDeviceExif(ExifInterface exif, ExifInterface exif_new) {
        if( MyDebug.LOG )
            Log.d(TAG, "transferDeviceExif");

        if( MyDebug.LOG )
            Log.d(TAG, "read back EXIF data");

        String exif_aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER); // previously TAG_APERTURE
        String exif_exposure_time = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
        String exif_flash = exif.getAttribute(ExifInterface.TAG_FLASH);
        String exif_focal_length = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
        // leave TAG_IMAGE_WIDTH/TAG_IMAGE_LENGTH, as this may have changed!
        //noinspection deprecation
        String exif_iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS); // previously TAG_ISO
        String exif_make = exif.getAttribute(ExifInterface.TAG_MAKE);
        String exif_model = exif.getAttribute(ExifInterface.TAG_MODEL);
        // leave orientation - since we rotate bitmaps to account for orientation, we don't want to write it to the saved image!
        String exif_white_balance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);

        String exif_aperture_value;
        String exif_brightness_value;
        String exif_cfa_pattern;
        String exif_color_space;
        String exif_components_configuration;
        String exif_compressed_bits_per_pixel;
        String exif_compression;
        String exif_contrast;
        String exif_device_setting_description;
        String exif_digital_zoom_ratio;
        String exif_exposure_bias_value;
        String exif_exposure_index;
        String exif_exposure_mode;
        String exif_exposure_program;
        String exif_flash_energy;
        String exif_focal_length_in_35mm_film;
        String exif_focal_plane_resolution_unit;
        String exif_focal_plane_x_resolution;
        String exif_focal_plane_y_resolution;
        String exif_gain_control;
        String exif_gps_area_information;
        String exif_gps_differential;
        String exif_gps_dop;
        String exif_gps_measure_mode;
        String exif_image_description;
        String exif_light_source;
        String exif_maker_note;
        String exif_max_aperture_value;
        String exif_metering_mode;
        String exif_oecf;
        String exif_photometric_interpretation;
        String exif_saturation;
        String exif_scene_capture_type;
        String exif_scene_type;
        String exif_sensing_method;
        String exif_sharpness;
        String exif_shutter_speed_value;
        String exif_software;
        String exif_user_comment;
        {
            // tags that are new in Android N - note we skip tags unlikely to be relevant for camera photos
            // update, now available in all Android versions thanks to using AndroidX ExifInterface
            exif_aperture_value = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE);
            exif_brightness_value = exif.getAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE);
            exif_cfa_pattern = exif.getAttribute(ExifInterface.TAG_CFA_PATTERN);
            exif_color_space = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE);
            exif_components_configuration = exif.getAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION);
            exif_compressed_bits_per_pixel = exif.getAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL);
            exif_compression = exif.getAttribute(ExifInterface.TAG_COMPRESSION);
            exif_contrast = exif.getAttribute(ExifInterface.TAG_CONTRAST);
            exif_device_setting_description = exif.getAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION);
            exif_digital_zoom_ratio = exif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO);
            // unclear if we should transfer TAG_EXIF_VERSION - don't want to risk conficting with whatever ExifInterface writes itself
            exif_exposure_bias_value = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE);
            exif_exposure_index = exif.getAttribute(ExifInterface.TAG_EXPOSURE_INDEX);
            exif_exposure_mode = exif.getAttribute(ExifInterface.TAG_EXPOSURE_MODE);
            exif_exposure_program = exif.getAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM);
            exif_flash_energy = exif.getAttribute(ExifInterface.TAG_FLASH_ENERGY);
            exif_focal_length_in_35mm_film = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
            exif_focal_plane_resolution_unit = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT);
            exif_focal_plane_x_resolution = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION);
            exif_focal_plane_y_resolution = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION);
            // TAG_F_NUMBER same as TAG_APERTURE
            exif_gain_control = exif.getAttribute(ExifInterface.TAG_GAIN_CONTROL);
            exif_gps_area_information = exif.getAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION);
            // don't care about TAG_GPS_DEST_*
            exif_gps_differential = exif.getAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL);
            exif_gps_dop = exif.getAttribute(ExifInterface.TAG_GPS_DOP);
            // TAG_GPS_IMG_DIRECTION, TAG_GPS_IMG_DIRECTION_REF won't have been recorded in the image yet - we add this ourselves in setGPSDirectionExif()
            // don't care about TAG_GPS_MAP_DATUM?
            exif_gps_measure_mode = exif.getAttribute(ExifInterface.TAG_GPS_MEASURE_MODE);
            // don't care about TAG_GPS_SATELLITES?
            // don't care about TAG_GPS_STATUS, TAG_GPS_TRACK, TAG_GPS_TRACK_REF, TAG_GPS_VERSION_ID
            exif_image_description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION);
            // unclear what TAG_IMAGE_UNIQUE_ID, TAG_INTEROPERABILITY_INDEX are
            // TAG_ISO_SPEED_RATINGS same as TAG_ISO
            // skip TAG_JPEG_INTERCHANGE_FORMAT, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
            exif_light_source = exif.getAttribute(ExifInterface.TAG_LIGHT_SOURCE);
            exif_maker_note = exif.getAttribute(ExifInterface.TAG_MAKER_NOTE);
            exif_max_aperture_value = exif.getAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE);
            exif_metering_mode = exif.getAttribute(ExifInterface.TAG_METERING_MODE);
            exif_oecf = exif.getAttribute(ExifInterface.TAG_OECF);
            exif_photometric_interpretation = exif.getAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION);
            // skip PIXEL_X/Y_DIMENSION, as it may have changed
            // don't care about TAG_PLANAR_CONFIGURATION
            // don't care about TAG_PRIMARY_CHROMATICITIES, TAG_REFERENCE_BLACK_WHITE?
            // don't care about TAG_RESOLUTION_UNIT
            // TAG_ROWS_PER_STRIP may have changed (if it's even relevant)
            // TAG_SAMPLES_PER_PIXEL may no longer be relevant if we've changed the image dimensions?
            exif_saturation = exif.getAttribute(ExifInterface.TAG_SATURATION);
            exif_scene_capture_type = exif.getAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE);
            exif_scene_type = exif.getAttribute(ExifInterface.TAG_SCENE_TYPE);
            exif_sensing_method = exif.getAttribute(ExifInterface.TAG_SENSING_METHOD);
            exif_sharpness = exif.getAttribute(ExifInterface.TAG_SHARPNESS);
            exif_shutter_speed_value = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE);
            exif_software = exif.getAttribute(ExifInterface.TAG_SOFTWARE);
            // don't care about TAG_SPATIAL_FREQUENCY_RESPONSE, TAG_SPECTRAL_SENSITIVITY?
            // don't care about TAG_STRIP_*
            // don't care about TAG_SUBJECT_*
            // TAG_SUBSEC_TIME_DIGITIZED same as TAG_SUBSEC_TIME_DIG
            // TAG_SUBSEC_TIME_ORIGINAL same as TAG_SUBSEC_TIME_ORIG
            // TAG_THUMBNAIL_IMAGE_* may have changed
            // don't care about TAG_TRANSFER_FUNCTION?
            exif_user_comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT);
            // don't care about TAG_WHITE_POINT?
            // TAG_X_RESOLUTION may have changed?
            // don't care about TAG_Y_*?
        }

        String exif_photographic_sensitivity = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
        String exif_sensitivity_type = exif.getAttribute(ExifInterface.TAG_SENSITIVITY_TYPE);
        String exif_standard_output_sensitivity = exif.getAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY);
        String exif_recommended_exposure_index = exif.getAttribute(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX);
        String exif_iso_speed = exif.getAttribute(ExifInterface.TAG_ISO_SPEED);
        String exif_custom_rendered = exif.getAttribute(ExifInterface.TAG_CUSTOM_RENDERED);
        String exif_lens_specification = exif.getAttribute(ExifInterface.TAG_LENS_SPECIFICATION);
        String exif_lens_name = exif.getAttribute(ExifInterface.TAG_LENS_MAKE);
        String exif_lens_model = exif.getAttribute(ExifInterface.TAG_LENS_MODEL);

        if( MyDebug.LOG )
            Log.d(TAG, "now write new EXIF data");
        if( exif_aperture != null )
            exif_new.setAttribute(ExifInterface.TAG_F_NUMBER, exif_aperture);
        if( exif_exposure_time != null )
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exif_exposure_time);
        if( exif_flash != null )
            exif_new.setAttribute(ExifInterface.TAG_FLASH, exif_flash);
        if( exif_focal_length != null )
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exif_focal_length);
        if( exif_iso != null )
            //noinspection deprecation
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, exif_iso);
        if( exif_make != null )
            exif_new.setAttribute(ExifInterface.TAG_MAKE, exif_make);
        if( exif_model != null )
            exif_new.setAttribute(ExifInterface.TAG_MODEL, exif_model);
        if( exif_white_balance != null )
            exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exif_white_balance);

        {
            if( exif_aperture_value != null )
                exif_new.setAttribute(ExifInterface.TAG_APERTURE_VALUE, exif_aperture_value);
            if( exif_brightness_value != null )
                exif_new.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, exif_brightness_value);
            if( exif_cfa_pattern != null )
                exif_new.setAttribute(ExifInterface.TAG_CFA_PATTERN, exif_cfa_pattern);
            if( exif_color_space != null )
                exif_new.setAttribute(ExifInterface.TAG_COLOR_SPACE, exif_color_space);
            if( exif_components_configuration != null )
                exif_new.setAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION, exif_components_configuration);
            if( exif_compressed_bits_per_pixel != null )
                exif_new.setAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, exif_compressed_bits_per_pixel);
            if( exif_compression != null )
                exif_new.setAttribute(ExifInterface.TAG_COMPRESSION, exif_compression);
            if( exif_contrast != null )
                exif_new.setAttribute(ExifInterface.TAG_CONTRAST, exif_contrast);
            if( exif_device_setting_description != null )
                exif_new.setAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, exif_device_setting_description);
            if( exif_digital_zoom_ratio != null )
                exif_new.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, exif_digital_zoom_ratio);
            if( exif_exposure_bias_value != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, exif_exposure_bias_value);
            if( exif_exposure_index != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_INDEX, exif_exposure_index);
            if( exif_exposure_mode != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, exif_exposure_mode);
            if( exif_exposure_program != null )
                exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, exif_exposure_program);
            if( exif_flash_energy != null )
                exif_new.setAttribute(ExifInterface.TAG_FLASH_ENERGY, exif_flash_energy);
            if( exif_focal_length_in_35mm_film != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, exif_focal_length_in_35mm_film);
            if( exif_focal_plane_resolution_unit != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, exif_focal_plane_resolution_unit);
            if( exif_focal_plane_x_resolution != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, exif_focal_plane_x_resolution);
            if( exif_focal_plane_y_resolution != null )
                exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, exif_focal_plane_y_resolution);
            if( exif_gain_control != null )
                exif_new.setAttribute(ExifInterface.TAG_GAIN_CONTROL, exif_gain_control);
            if( exif_gps_area_information != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, exif_gps_area_information);
            if( exif_gps_differential != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL, exif_gps_differential);
            if( exif_gps_dop != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_DOP, exif_gps_dop);
            if( exif_gps_measure_mode != null )
                exif_new.setAttribute(ExifInterface.TAG_GPS_MEASURE_MODE, exif_gps_measure_mode);
            if( exif_image_description != null )
                exif_new.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, exif_image_description);
            if( exif_light_source != null )
                exif_new.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, exif_light_source);
            if( exif_maker_note != null )
                exif_new.setAttribute(ExifInterface.TAG_MAKER_NOTE, exif_maker_note);
            if( exif_max_aperture_value != null )
                exif_new.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, exif_max_aperture_value);
            if( exif_metering_mode != null )
                exif_new.setAttribute(ExifInterface.TAG_METERING_MODE, exif_metering_mode);
            if( exif_oecf != null )
                exif_new.setAttribute(ExifInterface.TAG_OECF, exif_oecf);
            if( exif_photometric_interpretation != null )
                exif_new.setAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, exif_photometric_interpretation);
            if( exif_saturation != null )
                exif_new.setAttribute(ExifInterface.TAG_SATURATION, exif_saturation);
            if( exif_scene_capture_type != null )
                exif_new.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, exif_scene_capture_type);
            if( exif_scene_type != null )
                exif_new.setAttribute(ExifInterface.TAG_SCENE_TYPE, exif_scene_type);
            if( exif_sensing_method != null )
                exif_new.setAttribute(ExifInterface.TAG_SENSING_METHOD, exif_sensing_method);
            if( exif_sharpness != null )
                exif_new.setAttribute(ExifInterface.TAG_SHARPNESS, exif_sharpness);
            if( exif_shutter_speed_value != null )
                exif_new.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, exif_shutter_speed_value);
            if( exif_software != null )
                exif_new.setAttribute(ExifInterface.TAG_SOFTWARE, exif_software);
            if( exif_user_comment != null )
                exif_new.setAttribute(ExifInterface.TAG_USER_COMMENT, exif_user_comment);
        }

        if( exif_photographic_sensitivity != null )
            exif_new.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, exif_photographic_sensitivity);
        if( exif_sensitivity_type != null )
            exif_new.setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, exif_sensitivity_type);
        if( exif_standard_output_sensitivity != null )
            exif_new.setAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, exif_standard_output_sensitivity);
        if( exif_recommended_exposure_index != null )
            exif_new.setAttribute(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX, exif_recommended_exposure_index);
        if( exif_iso_speed != null )
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED, exif_iso_speed);
        if( exif_custom_rendered != null )
            exif_new.setAttribute(ExifInterface.TAG_CUSTOM_RENDERED, exif_custom_rendered);
        if( exif_lens_specification != null )
            exif_new.setAttribute(ExifInterface.TAG_LENS_SPECIFICATION, exif_lens_specification);
        if( exif_lens_name != null )
            exif_new.setAttribute(ExifInterface.TAG_LENS_MAKE, exif_lens_name);
        if( exif_lens_model != null )
            exif_new.setAttribute(ExifInterface.TAG_LENS_MODEL, exif_lens_model);

    }

    /** Transfers device exif info related to date and time.
     */
    private void transferDeviceExifDateTime(ExifInterface exif, ExifInterface exif_new) {
        if( MyDebug.LOG )
            Log.d(TAG, "transferDeviceExifDateTime");

        // tags related to date and time

        String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        String exif_datetime_original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
        String exif_datetime_digitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
        String exif_subsec_time = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME);
        String exif_subsec_time_orig = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL); // previously TAG_SUBSEC_TIME_ORIG
        String exif_subsec_time_dig = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED); // previously TAG_SUBSEC_TIME_DIG
        String exif_offset_time = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME);
        String exif_offset_time_orig = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL);
        String exif_offset_time_dig = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED);

        if( exif_datetime != null )
            exif_new.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
        if( exif_datetime_original != null )
            exif_new.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exif_datetime_original);
        if( exif_datetime_digitized != null )
            exif_new.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exif_datetime_digitized);
        if( exif_subsec_time != null )
            exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME, exif_subsec_time);
        if( exif_subsec_time_orig != null )
            exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, exif_subsec_time_orig);
        if( exif_subsec_time_dig != null )
            exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, exif_subsec_time_dig);
        if( exif_offset_time != null )
            exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME, exif_offset_time);
        if( exif_offset_time_orig != null )
            exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, exif_offset_time_orig);
        if( exif_offset_time_dig != null )
            exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, exif_offset_time_dig);

    }

    /** Transfers device exif info related to gps location.
     */
    private void transferDeviceExifGPS(ExifInterface exif, ExifInterface exif_new) {
        if( MyDebug.LOG )
            Log.d(TAG, "transferDeviceExifGPS");

        // tags for gps info

        String exif_gps_processing_method = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD);
        String exif_gps_latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
        String exif_gps_latitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
        String exif_gps_longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
        String exif_gps_longitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
        String exif_gps_altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
        String exif_gps_altitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
        String exif_gps_datestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
        String exif_gps_timestamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
        String exif_gps_speed = exif.getAttribute(ExifInterface.TAG_GPS_SPEED);
        String exif_gps_speed_ref = exif.getAttribute(ExifInterface.TAG_GPS_SPEED_REF);

        if( exif_gps_processing_method != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, exif_gps_processing_method);
        if( exif_gps_latitude != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, exif_gps_latitude);
        if( exif_gps_latitude_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exif_gps_latitude_ref);
        if( exif_gps_longitude != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, exif_gps_longitude);
        if( exif_gps_longitude_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exif_gps_longitude_ref);
        if( exif_gps_altitude != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, exif_gps_altitude);
        if( exif_gps_altitude_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, exif_gps_altitude_ref);
        if( exif_gps_datestamp != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, exif_gps_datestamp);
        if( exif_gps_timestamp != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, exif_gps_timestamp);
        if( exif_gps_speed != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED, exif_gps_speed);
        if( exif_gps_speed_ref != null )
            exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, exif_gps_speed_ref);
    }

    /** Explicitly removes tags based on the RemoveDeviceExif option.
     *  Note that in theory this method is unnecessary: we implement the RemoveDeviceExif options
     *  (if not OFF) by resaving the JPEG via a bitmap, and then limiting what Exif tags are
     *  transferred across. This method is for extra paranoia: first to reduce the risk of future
     *  bugs, secondly just in case saving via a bitmap does ever add exif tags.
     */
    private void removeExifTags(ExifInterface exif_new, final Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "removeExifTags");

        if( request.remove_device_exif != Request.RemoveDeviceExif.OFF ) {
            if( MyDebug.LOG )
                Log.d(TAG, "remove exif tags");
            exif_new.setAttribute(ExifInterface.TAG_F_NUMBER, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, null);
            exif_new.setAttribute(ExifInterface.TAG_FLASH, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, null);
            //noinspection deprecation
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, null);
            exif_new.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, null);
            exif_new.setAttribute(ExifInterface.TAG_MAKE, null);
            exif_new.setAttribute(ExifInterface.TAG_MODEL, null);
            exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, null);
            exif_new.setAttribute(ExifInterface.TAG_APERTURE_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_CFA_PATTERN, null);
            exif_new.setAttribute(ExifInterface.TAG_COLOR_SPACE, null);
            exif_new.setAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION, null);
            exif_new.setAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, null);
            exif_new.setAttribute(ExifInterface.TAG_COMPRESSION, null);
            exif_new.setAttribute(ExifInterface.TAG_CONTRAST, null);
            exif_new.setAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, null);
            exif_new.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_INDEX, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, null);
            exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, null);
            exif_new.setAttribute(ExifInterface.TAG_FLASH_ENERGY, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, null);
            exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, null);
            exif_new.setAttribute(ExifInterface.TAG_GAIN_CONTROL, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_BEARING, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_BEARING_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_DISTANCE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_DISTANCE_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_DOP, null);
            if( !request.store_geo_direction ) {
                exif_new.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, null);
            }
            exif_new.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_MEASURE_MODE, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_SATELLITES, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_STATUS, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_TRACK, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_TRACK_REF, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_VERSION_ID, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, null);
            exif_new.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, null);
            exif_new.setAttribute(ExifInterface.TAG_INTEROPERABILITY_INDEX, null);
            exif_new.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, null);
            exif_new.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, null);
            exif_new.setAttribute(ExifInterface.TAG_MAKER_NOTE, null);
            exif_new.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_METERING_MODE, null);
            exif_new.setAttribute(ExifInterface.TAG_OECF, null);
            exif_new.setAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, null);
            exif_new.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, null);
            exif_new.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, null);
            exif_new.setAttribute(ExifInterface.TAG_PLANAR_CONFIGURATION, null);
            exif_new.setAttribute(ExifInterface.TAG_PRIMARY_CHROMATICITIES, null);
            exif_new.setAttribute(ExifInterface.TAG_REFERENCE_BLACK_WHITE, null);
            exif_new.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, null);
            exif_new.setAttribute(ExifInterface.TAG_ROWS_PER_STRIP, null);
            exif_new.setAttribute(ExifInterface.TAG_SAMPLES_PER_PIXEL, null);
            exif_new.setAttribute(ExifInterface.TAG_SATURATION, null);
            exif_new.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_SCENE_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_SENSING_METHOD, null);
            exif_new.setAttribute(ExifInterface.TAG_SHARPNESS, null);
            exif_new.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, null);
            exif_new.setAttribute(ExifInterface.TAG_SOFTWARE, null);
            exif_new.setAttribute(ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE, null);
            exif_new.setAttribute(ExifInterface.TAG_SPECTRAL_SENSITIVITY, null);
            exif_new.setAttribute(ExifInterface.TAG_STRIP_BYTE_COUNTS, null);
            exif_new.setAttribute(ExifInterface.TAG_STRIP_OFFSETS, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_AREA, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBJECT_LOCATION, null);
            exif_new.setAttribute(ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH, null);
            exif_new.setAttribute(ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_TRANSFER_FUNCTION, null);
            if( !request.store_ypr ) {
                exif_new.setAttribute(ExifInterface.TAG_USER_COMMENT, null);
            }
            exif_new.setAttribute(ExifInterface.TAG_WHITE_POINT, null);
            exif_new.setAttribute(ExifInterface.TAG_X_RESOLUTION, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_CB_CR_COEFFICIENTS, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_CB_CR_POSITIONING, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING, null);
            exif_new.setAttribute(ExifInterface.TAG_Y_RESOLUTION, null);
            if( !(request.custom_tag_artist != null && !request.custom_tag_artist.isEmpty() ) ) {
                exif_new.setAttribute(ExifInterface.TAG_ARTIST, null);
            }
            if( !(request.custom_tag_copyright != null && !request.custom_tag_copyright.isEmpty() ) ) {
                exif_new.setAttribute(ExifInterface.TAG_COPYRIGHT, null);
            }

            exif_new.setAttribute(ExifInterface.TAG_BITS_PER_SAMPLE, null);
            exif_new.setAttribute(ExifInterface.TAG_EXIF_VERSION, null);
            exif_new.setAttribute(ExifInterface.TAG_FLASHPIX_VERSION, null);
            exif_new.setAttribute(ExifInterface.TAG_GAMMA, null);
            exif_new.setAttribute(ExifInterface.TAG_RELATED_SOUND_FILE, null);
            exif_new.setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, null);
            exif_new.setAttribute(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX, null);
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED, null);
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY, null);
            exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ, null);
            exif_new.setAttribute(ExifInterface.TAG_FILE_SOURCE, null);
            exif_new.setAttribute(ExifInterface.TAG_CUSTOM_RENDERED, null);
            exif_new.setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, null);
            exif_new.setAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_SPECIFICATION, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_MAKE, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_MODEL, null);
            exif_new.setAttribute(ExifInterface.TAG_LENS_SERIAL_NUMBER, null);
            exif_new.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, null);
            exif_new.setAttribute(ExifInterface.TAG_DNG_VERSION, null);
            exif_new.setAttribute(ExifInterface.TAG_DEFAULT_CROP_SIZE, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_THUMBNAIL_IMAGE, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_PREVIEW_IMAGE_START, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH, null);
            exif_new.setAttribute(ExifInterface.TAG_ORF_ASPECT_FRAME, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_SENSOR_TOP_BORDER, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_ISO, null);
            exif_new.setAttribute(ExifInterface.TAG_RW2_JPG_FROM_RAW, null);
            exif_new.setAttribute(ExifInterface.TAG_XMP, null);
            exif_new.setAttribute(ExifInterface.TAG_NEW_SUBFILE_TYPE, null);
            exif_new.setAttribute(ExifInterface.TAG_SUBFILE_TYPE, null);

            if( request.remove_device_exif != Request.RemoveDeviceExif.KEEP_DATETIME ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "remove datetime tags");
                exif_new.setAttribute(ExifInterface.TAG_DATETIME, null);
                exif_new.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null);
                exif_new.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null);
                exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME, null);
                exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, null);
                exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, null);
                exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME, null);
                exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, null);
                exif_new.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, null);
            }

            if( !request.store_location ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "remove gps tags");
                exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED, null);
                exif_new.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, null);
            }
        }
    }

    /** Transfers exif tags from exif to exif_new, and then applies any extra Exif tags according to the preferences in the request.
     *  Note that we use several ExifInterface tags that are now deprecated in API level 23 and 24. These are replaced with new tags that have
     *  the same string value (e.g., TAG_APERTURE replaced with TAG_F_NUMBER, but both have value "FNumber"). We use the deprecated versions
     *  to avoid complicating the code (we'd still have to read the deprecated values for older devices).
     */
    private void setExif(final Request request, ExifInterface exif, ExifInterface exif_new) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "setExif");

        if( request.remove_device_exif == Request.RemoveDeviceExif.OFF ) {
            transferDeviceExif(exif, exif_new);
        }

        if( request.remove_device_exif == Request.RemoveDeviceExif.OFF || request.remove_device_exif == Request.RemoveDeviceExif.KEEP_DATETIME ) {
            transferDeviceExifDateTime(exif, exif_new);
        }

        if( request.remove_device_exif == Request.RemoveDeviceExif.OFF || request.store_location ) {
            // If geotagging is enabled, we explicitly override the remove_device_exif setting.
            // Arguably we don't need an if statement here at all - but if there was some device strangely
            // setting GPS tags even when we haven't set them, it's better to remove them if the user has not
            // requested RemoveDeviceExif.OFF.
            transferDeviceExifGPS(exif, exif_new);
        }

        modifyExif(exif_new, request.remove_device_exif, request.type == Request.Type.JPEG, request.using_camera2, request.using_camera_extensions, request.current_date, request.store_location, request.location, request.store_geo_direction, request.geo_direction, request.custom_tag_artist, request.custom_tag_copyright, request.level_angle, request.pitch_angle, request.store_ypr);

        removeExifTags(exif_new, request); // must be last, before saving attributes
        exif_new.saveAttributes();
    }

    /** May be run in saver thread or picture callback thread (depending on whether running in background).
     */
    private boolean saveImageNowRaw(Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveImageNowRaw");

        StorageUtils storageUtils = main_activity.getStorageUtils();
        boolean success = false;
        String savedFilePath = null; // Track file path for Nextcloud upload

        main_activity.savingImage(true);

        OutputStream output = null;
        RawImage raw_image = request.raw_image;
        try {
            File picFile = null;
            Uri saveUri = null;
            boolean use_media_store = false;
            ContentValues contentValues = null; // used if using scoped storage

            String suffix = "_";
            String filename_suffix = (request.force_suffix) ? suffix + (request.suffix_offset) : "";
            if( storageUtils.isUsingSAF() ) {
                saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, "dng", request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "saveUri: " + saveUri);
                // When using SAF, we don't save to a temp file first (unlike for JPEGs). Firstly we don't need to modify Exif, so don't
                // need a real file; secondly copying to a temp file is much slower for RAW.
            }
            else if( MainActivity.useScopedStorage() ) {
                use_media_store = true;
                Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                contentValues = new ContentValues();
                String picName = storageUtils.createMediaFilename(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, 0, ".dng", request.current_date);
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, picName);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/dng");
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, storageUtils.getSaveRelativeFolder());
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                // Note, we catch exceptions specific to insert() here and rethrow as IOException,
                // rather than catching below, to avoid catching things too broadly.
                // Catching too broadly could mean we miss genuine problems that should be fixed.
                try {
                    saveUri = main_activity.getContentResolver().insert(folder, contentValues);
                }
                catch(IllegalArgumentException e) {
                    // can happen for mediastore method if invalid ContentResolver.insert() call
                    MyDebug.logStackTrace(TAG, "IllegalArgumentException inserting to mediastore", e);
                    throw new IOException();
                }
                catch(IllegalStateException e) {
                    MyDebug.logStackTrace(TAG, "IllegalStateException inserting to mediastore", e);
                    throw new IOException();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "saveUri: " + saveUri);
                if( saveUri == null )
                    throw new IOException();
            }
            else {
                picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, "dng", request.current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + picFile.getAbsolutePath());
                if( picFile != null )
                    savedFilePath = picFile.getAbsolutePath();
            }

            if( picFile != null ) {
                output = new FileOutputStream(picFile);
            }
            else {
                output = main_activity.getContentResolver().openOutputStream(saveUri);
            }
            raw_image.writeImage(output);
            raw_image.close();
            raw_image = null;
            output.close();
            output = null;
            success = true;

            // set last image for share/trash options for pause preview
            // Must be done before broadcastFile() (because on Android 7+ with non-SAF, we update
            // the LastImage's uri from the MediaScannerConnection.scanFile() callback from
            // StorageUtils.broadcastFile(), which assumes the last image has already been set.
            MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
            boolean raw_only = applicationInterface.isRawOnly();
            if( MyDebug.LOG )
                Log.d(TAG, "raw_only: " + raw_only);
            if( saveUri == null ) {
                applicationInterface.addLastImage(picFile, raw_only);
            }
            else if( storageUtils.isUsingSAF() ){
                applicationInterface.addLastImageSAF(saveUri, raw_only);
            }
            else if( success && use_media_store ){
                applicationInterface.addLastImageMediaStore(saveUri, raw_only);
            }

            // if RAW only, need to update the cached uri
            if( raw_only ) {
                // clear just in case we're unable to update this - don't want an out of date cached uri
                storageUtils.clearLastMediaScanned();
            }

            // n.b., at time of writing, remove_device_exif will always be OFF for RAW, but have added the code for future proofing
            boolean hasnoexifdatetime = request.remove_device_exif != Request.RemoveDeviceExif.OFF && request.remove_device_exif != Request.RemoveDeviceExif.KEEP_DATETIME;

            if( saveUri == null ) {
                storageUtils.broadcastFile(picFile, true, false, raw_only, hasnoexifdatetime, null);
            }
            else if( use_media_store ) {
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                    contentValues.clear();
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                    main_activity.getContentResolver().update(saveUri, contentValues, null, null);

                    // Query the actual filename from MediaStore (includes ~2, ~3 suffixes if added)
                    android.database.Cursor cursor = null;
                    try {
                        cursor = main_activity.getContentResolver().query(
                            saveUri,
                            new String[]{MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH},
                            null, null, null
                        );
                        if( cursor != null && cursor.moveToFirst() ) {
                            int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                            int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                            if( nameIndex >= 0 && pathIndex >= 0 ) {
                                String actualName = cursor.getString(nameIndex);
                                String actualRelativePath = cursor.getString(pathIndex);
                                if( actualName != null && actualRelativePath != null ) {
                                    if( !actualRelativePath.endsWith("/") )
                                        actualRelativePath = actualRelativePath + "/";
                                    savedFilePath = "/storage/emulated/0/" + actualRelativePath + actualName;
                                    Log.d(TAG, "RAW savedFilePath from MediaStore: " + savedFilePath);
                                }
                            }
                        }
                    }
                    catch(Exception e) {
                        Log.e(TAG, "Error querying MediaStore for RAW filename: " + e.getMessage());
                    }
                    finally {
                        if( cursor != null )
                            cursor.close();
                    }
                }

                // no need to broadcast when using mediastore method

                // in theory this is pointless, as announceUri no longer does anything on Android 7+,
                // and mediastore method is only used on Android 10+, but keep this just in case
                // announceUri does something in future
                storageUtils.announceUri(saveUri, true, false);

                if( raw_only ) {
                    // we also want to save the uri - we can use the media uri directly, rather than having to scan it
                    storageUtils.setLastMediaScanned(saveUri, true, hasnoexifdatetime, saveUri);
                }
            }
            else {
                storageUtils.broadcastUri(saveUri, true, false, raw_only, hasnoexifdatetime, false);
            }
        }
        catch(FileNotFoundException e) {
            MyDebug.logStackTrace(TAG, "file not found", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo_raw);
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "ioexception writing raw image file", e);
            main_activity.getPreview().showToast(null, R.string.failed_to_save_photo_raw);
        }
        finally {
            if( output != null ) {
                try {
                    output.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "ioexception closing raw output", e);
                }
            }
            if( raw_image != null ) {
                raw_image.close();
            }
        }

        System.gc();

        main_activity.savingImage(false);

        // Trigger Nextcloud upload if enabled and save was successful
        if( success && savedFilePath != null ) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(main_activity);
            boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);

            Log.d(TAG, "Nextcloud: success=" + success + ", savedFilePath=" + savedFilePath + ", uploadEnabled=" + uploadEnabled);

            if( uploadEnabled ) {
                Log.d(TAG, "Triggering Nextcloud upload for: " + savedFilePath);
                NextcloudUploadService.startUpload(main_activity, savedFilePath);
            }
        }

        return success;
    }

    /** Rotates the supplied bitmap according to the orientation tag stored in the exif data. If no
     *  rotation is required, the input bitmap is returned. If rotation is required, the input
     *  bitmap is recycled.
     * @param data Jpeg data containing the Exif information to use.
     */
    private Bitmap rotateForExif(Bitmap bitmap, byte [] data) {
        if( MyDebug.LOG )
            Log.d(TAG, "rotateForExif");
        if( bitmap == null ) {
            // support thumbnail being null - as this can happen according to Google Play crashes, see comment in saveSingleImageNow()
            return null;
        }
        InputStream inputStream = null;
        try {
            ExifInterface exif;

            if( MyDebug.LOG )
                Log.d(TAG, "use data stream to read exif tags");
            inputStream = new ByteArrayInputStream(data);
            exif = new ExifInterface(inputStream);

            int exif_orientation_s = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            if( MyDebug.LOG )
                Log.d(TAG, "    exif orientation string: " + exif_orientation_s);
            boolean needs_tf = false;
            int exif_orientation = 0;
            // see http://jpegclub.org/exif_orientation.html
            // and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
            switch (exif_orientation_s) {
                case ExifInterface.ORIENTATION_UNDEFINED:
                case ExifInterface.ORIENTATION_NORMAL:
                    // leave unchanged
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    needs_tf = true;
                    exif_orientation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    needs_tf = true;
                    exif_orientation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    needs_tf = true;
                    exif_orientation = 270;
                    break;
                default:
                    // just leave unchanged for now
                    if (MyDebug.LOG)
                        Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
                    break;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "    exif orientation: " + exif_orientation);

            if( needs_tf ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "    need to rotate bitmap due to exif orientation tag");
                Matrix m = new Matrix();
                m.setRotate(exif_orientation, bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f);
                Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), m, true);
                if( rotated_bitmap != bitmap ) {
                    bitmap.recycle();
                    bitmap = rotated_bitmap;
                }
            }
        }
        catch(IOException e) {
            MyDebug.logStackTrace(TAG, "exif orientation ioexception", e);
        }
        catch(NoClassDefFoundError e) {
            // have had Google Play crashes from new ExifInterface() for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn)
            MyDebug.logStackTrace(TAG, "exif orientation NoClassDefFoundError", e);
        }
        finally {
            if( inputStream != null ) {
                try {
                    inputStream.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to close inputStream", e);
                }
            }
        }
        return bitmap;
    }

    /** Loads the bitmap from the supplied jpeg data, rotating if necessary according to the
     *  supplied EXIF orientation tag.
     * @param data The jpeg data.
     * @param mutable Whether to create a mutable bitmap.
     * @return A bitmap representing the correctly rotated jpeg.
     */
    private Bitmap loadBitmapWithRotation(byte [] data, boolean mutable) {
        Bitmap bitmap = loadBitmap(data, mutable, 1);
        if( bitmap != null ) {
            // rotate the bitmap if necessary for exif tags
            if( MyDebug.LOG )
                Log.d(TAG, "rotate bitmap for exif tags?");
            bitmap = rotateForExif(bitmap, data);
        }
        return bitmap;
    }

    /* In some cases we may create an ExifInterface with a FileDescriptor obtained from a
     * ParcelFileDescriptor (via getFileDescriptor()). It's important to keep a reference to the
     * ParcelFileDescriptor object for as long as the exif interface, otherwise there's a risk of
     * the ParcelFileDescriptor being garbage collected, invalidating the file descriptor still
     * being used by the ExifInterface!
     * This didn't cause any known bugs, but good practice to fix, similar to the issue reported in
     * https://sourceforge.net/p/opencamera/tickets/417/ .
     * Also important to call the close() method when done with it, to close the
     * ParcelFileDescriptor (if one was created).
     */
    private static class ExifInterfaceHolder {
        // see documentation above about keeping hold of pdf due to the garbage collector!
        private final ParcelFileDescriptor pfd;
        private final ExifInterface exif;

        ExifInterfaceHolder(ParcelFileDescriptor pfd, ExifInterface exif) {
            this.pfd = pfd;
            this.exif = exif;
        }

        ExifInterface getExif() {
            return this.exif;
        }

        void close()  {
            if( this.pfd != null ) {
                try {
                    this.pfd.close();
                }
                catch(IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to close parcelfiledescriptor", e);
                }
            }
        }
    }

    /** Creates a new exif interface for reading and writing.
     *  If picFile==null, then saveUri must be non-null, and will be used instead to write the exif
     *  tags too.
     *  The returned ExifInterfaceHolder will always be non-null, but the contained getExif() may
     *  return null if this method was unable to create the exif interface.
     *  The caller should call close() on the returned ExifInterfaceHolder when no longer required.
     */
    private ExifInterfaceHolder createExifInterface(File picFile, Uri saveUri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = null;
        ExifInterface exif = null;
        if( picFile != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "write to picFile: " + picFile);
            exif = new ExifInterface(picFile.getAbsolutePath());
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "write direct to saveUri: " + saveUri);
            parcelFileDescriptor = main_activity.getContentResolver().openFileDescriptor(saveUri, "rw");
            if( parcelFileDescriptor != null ) {
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                exif = new ExifInterface(fileDescriptor);
            }
            else {
                Log.e(TAG, "failed to create ParcelFileDescriptor for saveUri: " + saveUri);
            }
        }
        return new ExifInterfaceHolder(parcelFileDescriptor, exif);
    }

    /** Makes various modifications to the saved image file, according to the preferences in request.
     *  This method is used when saving directly from the JPEG data rather than a bitmap.
     *  If picFile==null, then saveUri must be non-null, and will be used instead to write the exif
     *  tags too.
     */
    private void updateExif(Request request, File picFile, Uri saveUri) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "updateExif: " + picFile);
        if( request.store_geo_direction || request.store_ypr || hasCustomExif(request.custom_tag_artist, request.custom_tag_copyright) ||
                request.using_camera_extensions || // when using camera extensions, we need to call modifyExif() to fix up various missing tags
                needGPSExifFix(request.type == Request.Type.JPEG, request.using_camera2, request.store_location) ) {
            long time_s = System.currentTimeMillis();
            if( MyDebug.LOG )
                Log.d(TAG, "add additional exif info");
            try {
                ExifInterfaceHolder exif_holder = createExifInterface(picFile, saveUri);
                if( MyDebug.LOG )
                    Log.d(TAG, "*** time after create exif: " + (System.currentTimeMillis() - time_s));
                try {
                    ExifInterface exif = exif_holder.getExif();
                    if( exif != null ) {
                        modifyExif(exif, request.remove_device_exif, request.type == Request.Type.JPEG, request.using_camera2, request.using_camera_extensions, request.current_date, request.store_location, request.location, request.store_geo_direction, request.geo_direction, request.custom_tag_artist, request.custom_tag_copyright, request.level_angle, request.pitch_angle, request.store_ypr);

                        if( MyDebug.LOG )
                            Log.d(TAG, "*** time after modifyExif: " + (System.currentTimeMillis() - time_s));
                        exif.saveAttributes();
                        if( MyDebug.LOG )
                            Log.d(TAG, "*** time after saveAttributes: " + (System.currentTimeMillis() - time_s));
                    }
                }
                finally {
                    exif_holder.close();
                }
            }
            catch(NoClassDefFoundError e) {
                // have had Google Play crashes from new ExifInterface() elsewhere for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn), so also catch here just in case
                MyDebug.logStackTrace(TAG, "exif orientation NoClassDefFoundError", e);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "*** time to add additional exif info: " + (System.currentTimeMillis() - time_s));
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "no exif data to update for: " + picFile);
        }
    }

    /** Makes various modifications to the exif data, if necessary.
     *  Any fix-ups should respect the setting of RemoveDeviceExif!
     */
    private void modifyExif(ExifInterface exif, Request.RemoveDeviceExif remove_device_exif, boolean is_jpeg, boolean using_camera2, boolean using_camera_extensions, Date current_date, boolean store_location, Location location, boolean store_geo_direction, double geo_direction, String custom_tag_artist, String custom_tag_copyright, double level_angle, double pitch_angle, boolean store_ypr) {
        if( MyDebug.LOG )
            Log.d(TAG, "modifyExif");
        setGPSDirectionExif(exif, store_geo_direction, geo_direction);
        if( store_ypr ) {
            float geo_angle = (float)Math.toDegrees(geo_direction);
            if( geo_angle < 0.0f ) {
                geo_angle += 360.0f;
            }
            String encoding = "ASCII\0\0\0";
            // fine to ignore request.remove_device_exif, as this is a separate user option
            //exif.setAttribute(ExifInterface.TAG_USER_COMMENT,"Yaw:" + geo_angle + ",Pitch:" + pitch_angle + ",Roll:" + level_angle);
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT,encoding + "Yaw:" + geo_angle + ",Pitch:" + pitch_angle + ",Roll:" + level_angle);
            if( MyDebug.LOG )
                Log.d(TAG, "UserComment: " + exif.getAttribute(ExifInterface.TAG_USER_COMMENT));
        }
        setCustomExif(exif, custom_tag_artist, custom_tag_copyright);

        boolean force_location = false; // whether we need to add location data
        if( store_location ) {
            // Normally if geotagging is enabled, location should have already been added via the Camera API.
            // But we need this when using camera extensions (since Camera API doesn't support location for camera extensions).
            // And some devices (e.g., Pixel 6 Pro with Camera2 API) seem to not store location data, so we always check if we need to add it.
            // Similarly Fairphone 5 always has longitude stored as 0.0.
            // fine to ignore request.remove_device_exif, as this is a separate user option
            if( !exif.hasAttribute(ExifInterface.TAG_GPS_LATITUDE) || !exif.hasAttribute(ExifInterface.TAG_GPS_LONGITUDE) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "force location as not present in exif");
                force_location = true;
            }
            else {
                double [] lat_long = exif.getLatLong();
                if( lat_long == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "force location as not present in exif");
                    force_location = true;
                }
                else if( lat_long[0] == 0.0 || lat_long[1] == 0.0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "force location as longitude or latitude is 0.0");
                    force_location = true;
                }
            }
        }
        if( force_location ) {
            if( MyDebug.LOG )
                Log.d(TAG, "force store location"); // don't log location for privacy reasons!
            exif.setGpsInfo(location);
        }

        if( using_camera_extensions ) {
            if( remove_device_exif == Request.RemoveDeviceExif.OFF || remove_device_exif == Request.RemoveDeviceExif.KEEP_DATETIME ) {
                addDateTimeExif(exif, current_date);
            }
        }
        else if( needGPSExifFix(is_jpeg, using_camera2, store_location) ) {
            // fine to ignore request.remove_device_exif, as this is a separate user option
            fixGPSTimestamp(exif, current_date);
        }
    }

    private void setGPSDirectionExif(ExifInterface exif, boolean store_geo_direction, double geo_direction) {
        if( MyDebug.LOG )
            Log.d(TAG, "setGPSDirectionExif");
        if( store_geo_direction ) {
            float geo_angle = (float)Math.toDegrees(geo_direction);
            if( geo_angle < 0.0f ) {
                geo_angle += 360.0f;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "save geo_angle: " + geo_angle);
            // see http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/GPS.html
            String GPSImgDirection_string = Math.round(geo_angle*100) + "/100";
            if( MyDebug.LOG )
                Log.d(TAG, "GPSImgDirection_string: " + GPSImgDirection_string);
            // fine to ignore request.remove_device_exif, as this is a separate user option
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, GPSImgDirection_string);
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M");
        }
    }

    /** Whether custom exif tags need to be applied to the image file.
     */
    private boolean hasCustomExif(String custom_tag_artist, String custom_tag_copyright) {
        if( custom_tag_artist != null && !custom_tag_artist.isEmpty() )
            return true;
        if( custom_tag_copyright != null && !custom_tag_copyright.isEmpty() )
            return true;
        return false;
    }

    /** Applies the custom exif tags to the ExifInterface.
     */
    private void setCustomExif(ExifInterface exif, String custom_tag_artist, String custom_tag_copyright) {
        if( MyDebug.LOG )
            Log.d(TAG, "setCustomExif");
        if( custom_tag_artist != null && !custom_tag_artist.isEmpty() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "apply TAG_ARTIST: " + custom_tag_artist);
            // fine to ignore request.remove_device_exif, as this is a separate user option
            exif.setAttribute(ExifInterface.TAG_ARTIST, custom_tag_artist);
        }
        if( custom_tag_copyright != null && !custom_tag_copyright.isEmpty())  {
            if( MyDebug.LOG )
                Log.d(TAG, "apply TAG_COPYRIGHT: " + custom_tag_copyright);
            // fine to ignore request.remove_device_exif, as this is a separate user option
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, custom_tag_copyright);
        }
    }

    /** Adds exif tags for datetime from the supplied date, if not present. Needed for camera vendor
     *  extensions which (at least on Galaxy S10e) don't seem to have these tags set at all!
     */
    private void addDateTimeExif(ExifInterface exif, Date current_date) {
        if( MyDebug.LOG )
            Log.d(TAG, "addDateTimeExif");
        String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        if( MyDebug.LOG )
            Log.d(TAG, "existing exif TAG_DATETIME: " + exif_datetime);
        if( exif_datetime == null ) {
            SimpleDateFormat date_fmt = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            date_fmt.setTimeZone(TimeZone.getDefault()); // need local timezone for TAG_DATETIME
            exif_datetime = date_fmt.format(current_date);
            if( MyDebug.LOG )
                Log.d(TAG, "new TAG_DATETIME: " + exif_datetime);

            exif.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
            // set these tags too (even if already present, overwrite to be consistent)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exif_datetime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exif_datetime);

            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                // XXX requires Android 7
                // needs to be -/+HH:mm format, which is given by XXX
                date_fmt = new SimpleDateFormat("XXX", Locale.US);
                date_fmt.setTimeZone(TimeZone.getDefault());
                String timezone = date_fmt.format(current_date);
                if( MyDebug.LOG )
                    Log.d(TAG, "timezone: " + timezone);
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, timezone);
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, timezone);
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, timezone);
            }
        }
    }

    private void fixGPSTimestamp(ExifInterface exif, Date current_date) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "fixGPSTimestamp");
            Log.d(TAG, "current datestamp: " + exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP));
            Log.d(TAG, "current timestamp: " + exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));
            Log.d(TAG, "current datetime: " + exif.getAttribute(ExifInterface.TAG_DATETIME));
        }
        // Hack: Problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP and TAG_GPS_TIMESTAMP (GPSDateStamp) set (date tends to be around 2038 - possibly a driver bug of casting long to int?).
        // This causes problems when viewing with Gallery apps (e.g., Gallery ICS; Google Photos seems fine however), as they show this incorrect date.
        // Update: Before v1.34 this was "fixed" by calling: exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, Long.toString(System.currentTimeMillis()));
        // However this stopped working on or before 20161006. This wasn't a change in Open Camera (whilst this was working fine in
        // 1.33 when I released it, the bug had come back when I retested that version) and I'm not sure how this ever worked, since
        // TAG_GPS_TIMESTAMP is meant to be a string such "21:45:23", and not the number of ms since 1970 - possibly it wasn't really
        // working , and was simply invalidating it such that Gallery then fell back to looking elsewhere for the datetime?
        // So now hopefully fixed properly...
        // Note, this problem also occurs on OnePlus 3T and Gallery ICS, if we don't have this function called
        SimpleDateFormat date_fmt = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
        date_fmt.setTimeZone(TimeZone.getTimeZone("UTC")); // needs to be UTC time for the GPS datetime tags
        String datestamp = date_fmt.format(current_date);

        SimpleDateFormat time_fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        time_fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = time_fmt.format(current_date);

        if( MyDebug.LOG ) {
            Log.d(TAG, "datestamp: " + datestamp);
            Log.d(TAG, "timestamp: " + timestamp);
        }
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, datestamp);
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, timestamp);

        if( MyDebug.LOG )
            Log.d(TAG, "fixGPSTimestamp exit");
    }

    /** Whether we need to fix up issues with location.
     *  See comments in fixGPSTimestamp(), where some devices with Camera2 need fixes for TAG_GPS_DATESTAMP and TAG_GPS_TIMESTAMP.
     *  Also some devices (e.g. Pixel 6 Pro) have problem that location is not stored in images with Camera2 API, so we need to
     *  enter modifyExif() to add it if not present; similarly Fairphone 5 needs correcting due to storing longitude as 0.0.
     */
    private boolean needGPSExifFix(boolean is_jpeg, boolean using_camera2, boolean store_location) {
        if( is_jpeg && using_camera2 ) {
            return store_location;
        }
        return false;
    }

    // for testing:

    HDRProcessor getHDRProcessor() {
        return hdrProcessor;
    }

    public PanoramaProcessor getPanoramaProcessor() {
        return panoramaProcessor;
    }
}
