package net.sourceforge.opencamera;

import static org.junit.Assert.*;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import androidx.exifinterface.media.ExifInterface;

import net.sourceforge.opencamera.preview.Preview;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Helper class for testing. This method should not include any code specific to any test framework
 *  (e.g., shouldn't be specific to ActivityInstrumentationTestCase2).
 */
public class TestUtils {
    private static final String TAG = "TestUtils";

    public static final boolean test_camera2 = false;
    //public static final boolean test_camera2 = true;

    final private static String images_base_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
    final public static String hdr_images_path = images_base_path + "/testOpenCamera/testdata/hdrsamples/";
    final public static String avg_images_path = images_base_path + "/testOpenCamera/testdata/avgsamples/";
    final public static String logprofile_images_path = images_base_path + "/testOpenCamera/testdata/logprofilesamples/";
    final public static String panorama_images_path = images_base_path + "/testOpenCamera/testdata/panoramasamples/";

    public static void setDefaultIntent(Intent intent) {
        intent.putExtra("test_project", true);
    }

    /** Code to call before running each test.
     */
    public static void initTest(Context context) {
        Log.d(TAG, "initTest: " + test_camera2);
        // initialise test statics (to avoid the persisting between tests in a test suite run!)
        MainActivity.test_preview_want_no_limits = false;
        MainActivity.test_preview_want_no_limits_value = false;
        ImageSaver.test_small_queue_size = false;

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        if( test_camera2 ) {
            MainActivity.test_force_supports_camera2 = true;
            //editor.putBoolean(PreferenceKeys.UseCamera2PreferenceKey, true);
            editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
        }
        editor.apply();

        Log.d(TAG, "initTest: done");
    }

    public static boolean isEmulator() {
        return Build.MODEL.contains("Android SDK built for x86");
    }

    /** Converts a path to a Uri for com.android.providers.media.documents.
     */
    private static Uri getDocumentUri(String filename) throws FileNotFoundException {
        Log.d(TAG, "getDocumentUri: " + filename);

        // convert from File path format to Storage Access Framework form
        Uri treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FtestOpenCamera");
        Log.d(TAG, "treeUri: " + treeUri);
        if( !filename.startsWith(images_base_path) ) {
            Log.e(TAG, "unknown base for: " + filename);
            throw new FileNotFoundException();
        }
        String stem = filename.substring(images_base_path.length());
        Uri stemUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADCIM" + stem.replace("/", "%2F"));
        Log.d(TAG, "stem: " + stem);
        Log.d(TAG, "stemUri: " + stemUri);
        //String docID = "primary:DCIM" + stem;
        String docID = DocumentsContract.getTreeDocumentId(stemUri);
        Log.d(TAG, "docID: " + docID);
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docID);

        if( uri == null ) {
            throw new FileNotFoundException();
        }
        return uri;
    }

    public static Bitmap getBitmapFromFile(MainActivity activity, String filename) {
        return getBitmapFromFile(activity, filename, 1);
    }

    public static Bitmap getBitmapFromFile(MainActivity activity, String filename, int inSampleSize) {
        try {
            return getBitmapFromFileCore(activity, filename, inSampleSize);
        }
        catch(FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException loading: " + filename, e);
            fail("FileNotFoundException loading: " + filename);
            return null;
        }
    }

    /** Loads bitmap from supplied filename.
     *  Note that on Android 10+ (with scoped storage), this uses Storage Access Framework, which
     *  means Open Camera must have SAF permission to the folder DCIM/testOpenCamera.
     */
    private static Bitmap getBitmapFromFileCore(MainActivity activity, String filename, int inSampleSize) throws FileNotFoundException {
        Log.d(TAG, "getBitmapFromFileCore: " + filename);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        //options.inSampleSize = inSampleSize;
        if( inSampleSize > 1 ) {
            // use inDensity for better quality, as inSampleSize uses nearest neighbour
            // see same code in ImageSaver.setBitmapOptionsSampleSize()
            options.inDensity = inSampleSize;
            options.inTargetDensity = 1;
        }

        Uri uri = null;
        Bitmap bitmap;

        if( MainActivity.useScopedStorage() ) {
            uri = getDocumentUri(filename);
            Log.d(TAG, "uri: " + uri);
            InputStream is = activity.getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(is, null, options);
            try {
                is.close();
            }
            catch(IOException e) {
                Log.e(TAG, "failed to close input stream", e);
            }
        }
        else {
            bitmap = BitmapFactory.decodeFile(filename, options);
        }
        if( bitmap == null )
            throw new FileNotFoundException();
        Log.d(TAG, "    done: " + bitmap);

        // now need to take exif orientation into account, as some devices or camera apps store the orientation in the exif tag,
        // which getBitmap() doesn't account for
        ParcelFileDescriptor parcelFileDescriptor = null;
        FileDescriptor fileDescriptor;
        try {
            ExifInterface exif = null;
            if( uri != null ) {
                parcelFileDescriptor = activity.getContentResolver().openFileDescriptor(uri, "r");
                if( parcelFileDescriptor != null ) {
                    fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                    exif = new ExifInterface(fileDescriptor);
                }
            }
            else {
                exif = new ExifInterface(filename);
            }
            if( exif != null ) {
                int exif_orientation_s = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                boolean needs_tf = false;
                int exif_orientation = 0;
                // from http://jpegclub.org/exif_orientation.html
                // and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
                if( exif_orientation_s == ExifInterface.ORIENTATION_UNDEFINED || exif_orientation_s == ExifInterface.ORIENTATION_NORMAL ) {
                    // leave unchanged
                }
                else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_180 ) {
                    needs_tf = true;
                    exif_orientation = 180;
                }
                else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_90 ) {
                    needs_tf = true;
                    exif_orientation = 90;
                }
                else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_270 ) {
                    needs_tf = true;
                    exif_orientation = 270;
                }
                else {
                    // just leave unchanged for now
                    Log.e(TAG, "    unsupported exif orientation: " + exif_orientation_s);
                }
                Log.d(TAG, "    exif orientation: " + exif_orientation);

                if( needs_tf ) {
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
        }
        catch(IOException e) {
            Log.e(TAG, "failed to load bitmap", e);
        }
        finally {
            if( parcelFileDescriptor != null ) {
                try {
                    parcelFileDescriptor.close();
                }
                catch(IOException e) {
                    Log.e(TAG, "failed to close parcelFileDescriptor", e);
                }
            }
        }
        /*{
            for(int y=0;y<bitmap.getHeight();y++) {
                for(int x=0;x<bitmap.getWidth();x++) {
                    int color = bitmap.getPixel(x, y);
                    Log.d(TAG, x + "," + y + ": " + Color.red(color) + "," + Color.green(color) + "," + Color.blue(color));
                }
            }
        }*/
        return bitmap;
    }

    /** Returns the mediastore Uri for the supplied filename inside the supplied baseUri, or null
     *  if an entry can't be found.
     */
    private static Uri getUriFromName(MainActivity activity, Uri baseUri, String name) {
        Uri uri = null;
        String [] projection = new String[]{MediaStore.Images.ImageColumns._ID};
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(baseUri, projection, MediaStore.Images.ImageColumns.DISPLAY_NAME + " LIKE ?", new String[]{name}, null);
            if( cursor != null && cursor.moveToFirst() ) {
                Log.d(TAG, "found: " + cursor.getCount());
                long id = cursor.getLong(0);
                uri = ContentUris.withAppendedId(baseUri, id);
                Log.d(TAG, "id: " + id);
                Log.d(TAG, "uri: " + uri);
            }
        }
        catch(Exception e) {
            Log.e(TAG, "exception trying to find uri from filename", e);
        }
        finally {
            if( cursor != null ) {
                cursor.close();
            }
        }
        return uri;
    }

    public static void saveBitmap(MainActivity activity, Bitmap bitmap, String name) {
        try {
            saveBitmapCore(activity, bitmap, name);
        }
        catch(IOException e) {
            Log.e(TAG, "IOException saving: " + name, e);
            fail("IOException saving: " + name);
        }
    }

    private static void saveBitmapCore(MainActivity activity, Bitmap bitmap, String name) throws IOException {
        Log.d(TAG, "saveBitmapCore: " + name);

        File file = null;
        ContentValues contentValues = null;
        Uri uri = null;
        OutputStream outputStream;
        if( MainActivity.useScopedStorage() ) {
            Uri folder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

            // first try to delete pre-existing image
            Uri old_uri = getUriFromName(activity, folder, name);
            if( old_uri != null ) {
                Log.d(TAG, "delete: " + old_uri);
                activity.getContentResolver().delete(old_uri, null, null);
            }

            contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            String extension = name.substring(name.lastIndexOf("."));
            String mime_type = activity.getStorageUtils().getImageMimeType(extension);
            Log.d(TAG, "mime_type: " + mime_type);
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, mime_type);
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                String relative_path = Environment.DIRECTORY_DCIM + File.separator;
                Log.d(TAG, "relative_path: " + relative_path);
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, relative_path);
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            uri = activity.getContentResolver().insert(folder, contentValues);
            Log.d(TAG, "saveUri: " + uri);
            if( uri == null ) {
                throw new IOException();
            }
            outputStream = activity.getContentResolver().openOutputStream(uri);
        }
        else {
            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + name);
            outputStream = new FileOutputStream(file);
        }

        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        outputStream.close();

        if( MainActivity.useScopedStorage() ) {
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                contentValues.clear();
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                activity.getContentResolver().update(uri, contentValues, null, null);
            }
        }
        else {
            activity.getStorageUtils().broadcastFile(file, true, false, true, false, null);
        }
    }

    public static class HistogramDetails {
        public final int min_value;
        public final int median_value;
        public final int max_value;

        HistogramDetails(int min_value, int median_value, int max_value) {
            this.min_value = min_value;
            this.median_value = median_value;
            this.max_value = max_value;
        }
    }

    /** Checks for the resultant histogram.
     *  We check that we have a single range of non-zero values.
     * @param bitmap The bitmap to compute and check a histogram for.
     */
    public static HistogramDetails checkHistogram(MainActivity activity, Bitmap bitmap) {
        int [] histogram = activity.getApplicationInterface().getHDRProcessor().computeHistogram(bitmap, HDRProcessor.HistogramType.HISTOGRAM_TYPE_INTENSITY);
        assertEquals(256, histogram.length);
        int total = 0;
        for(int i=0;i<histogram.length;i++) {
            Log.d(TAG, "histogram[" + i + "]: " + histogram[i]);
            total += histogram[i];
        }
        Log.d(TAG, "total: " + total);
        boolean started = false;
        int min_value = -1, median_value = -1, max_value = -1;
        int count = 0;
        int middle = total/2;
        for(int i=0;i<histogram.length;i++) {
            int value = histogram[i];
            if( !started ) {
                started = value != 0;
            }
            if( value != 0 ) {
                if( min_value == -1 )
                    min_value = i;
                max_value = i;
                count += value;
                if( count >= middle && median_value == -1 )
                    median_value = i;
            }
        }
        Log.d(TAG, "min_value: " + min_value);
        Log.d(TAG, "median_value: " + median_value);
        Log.d(TAG, "max_value: " + max_value);
        return new HistogramDetails(min_value, median_value, max_value);
    }

    public static HistogramDetails subTestHDR(MainActivity activity, List<Bitmap> inputs, String output_name, boolean test_dro, int iso, long exposure_time) {
        return subTestHDR(activity, inputs, output_name, test_dro, iso, exposure_time, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD);
    }

    /** The testHDRX tests test the HDR algorithm on a given set of input images.
     *  By testing on a fixed sample, this makes it easier to finetune the HDR algorithm for quality and performance.
     *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
     *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
     *  time to transfer to the device everytime we run the tests.
     * @param iso The ISO of the middle image (for testing Open Camera's "smart" contrast enhancement). If set to -1, then use "always" contrast enhancement.
     * @param exposure_time The exposure time of the middle image (for testing Open Camera's "smart" contrast enhancement)
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static HistogramDetails subTestHDR(MainActivity activity, List<Bitmap> inputs, String output_name, boolean test_dro, int iso, long exposure_time, HDRProcessor.TonemappingAlgorithm tonemapping_algorithm/*, HDRTestCallback test_callback*/) {
        Log.d(TAG, "subTestHDR");

        try {
            Thread.sleep(1000); // wait for camera to open
        }
        catch(InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }

        Bitmap dro_bitmap_in = null;
        if( test_dro ) {
            // save copy of input bitmap to also test DRO (since the HDR routine will free the inputs)
            int mid = (inputs.size()-1)/2;
            dro_bitmap_in = inputs.get(mid);
            dro_bitmap_in = dro_bitmap_in.copy(dro_bitmap_in.getConfig(), true);
        }

        HistogramDetails hdrHistogramDetails = null;
        if( inputs.size() > 1 ) {
            String preference_hdr_contrast_enhancement = (iso==-1) ? "preference_hdr_contrast_enhancement_always" : "preference_hdr_contrast_enhancement_smart";
            float hdr_alpha = ImageSaver.getHDRAlpha(preference_hdr_contrast_enhancement, exposure_time, inputs.size());
            long time_s = System.currentTimeMillis();
            try {
                activity.getApplicationInterface().getHDRProcessor().processHDR(inputs, true, null, true, null, hdr_alpha, 4, true, tonemapping_algorithm, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA);
                //test_callback.doHDR(inputs, tonemapping_algorithm, hdr_alpha);
            }
            catch(HDRProcessorException e) {
                Log.e(TAG, "processHDR failed", e);
                throw new RuntimeException();
            }
            Log.d(TAG, "HDR time: " + (System.currentTimeMillis() - time_s));

            saveBitmap(activity, inputs.get(0), output_name);
            hdrHistogramDetails = checkHistogram(activity, inputs.get(0));
        }
        inputs.get(0).recycle();
        inputs.clear();

        if( test_dro ) {
            inputs.add(dro_bitmap_in);
            long time_s = System.currentTimeMillis();
            try {
                activity.getApplicationInterface().getHDRProcessor().processHDR(inputs, true, null, true, null, 0.5f, 4, true, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD, HDRProcessor.DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA);
                //test_callback.doHDR(inputs, HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD, 0.5f);
            }
            catch(HDRProcessorException e) {
                Log.e(TAG, "processHDR failed", e);
                throw new RuntimeException();
            }
            Log.d(TAG, "DRO time: " + (System.currentTimeMillis() - time_s));

            saveBitmap(activity, inputs.get(0), "dro" + output_name);
            checkHistogram(activity, inputs.get(0));
            inputs.get(0).recycle();
            inputs.clear();
        }
        try {
            Thread.sleep(500);
        }
        catch(InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }

        return hdrHistogramDetails;
    }

    public static void checkHDROffsets(MainActivity activity, int [] exp_offsets_x, int [] exp_offsets_y) {
        checkHDROffsets(activity, exp_offsets_x, exp_offsets_y, 1);
    }

    /** Checks that the HDR offsets used for auto-alignment are as expected.
     */
    public static void checkHDROffsets(MainActivity activity, int [] exp_offsets_x, int [] exp_offsets_y, int scale) {
        int [] offsets_x = activity.getApplicationInterface().getHDRProcessor().offsets_x;
        int [] offsets_y = activity.getApplicationInterface().getHDRProcessor().offsets_y;
        for(int i=0;i<offsets_x.length;i++) {
            Log.d(TAG, "offsets " + i + " ( " + offsets_x[i]*scale + " , " + offsets_y[i]*scale + " ), expected ( " + exp_offsets_x[i] + " , " + exp_offsets_y[i] + " )");
            // we allow some tolerance as different devices can produce different results (e.g., Nexus 6 vs OnePlus 3T; see testHDR5 on Nexus 6)
            assertTrue(Math.abs(offsets_x[i]*scale - exp_offsets_x[i]) <= 1);
            assertTrue(Math.abs(offsets_y[i]*scale - exp_offsets_y[i]) <= 1);
        }
    }

    public static void checkHistogramDetails(HistogramDetails hdrHistogramDetails, int exp_min_value, int exp_median_value, int exp_max_value) {
        Log.d(TAG, "checkHistogramDetails");
        Log.d(TAG, "compare min value " + hdrHistogramDetails.min_value + " to expected " + exp_min_value);
        Log.d(TAG, "compare median value " + hdrHistogramDetails.median_value + " to expected " + exp_median_value);
        Log.d(TAG, "compare max value " + hdrHistogramDetails.max_value + " to expected " + exp_max_value);
        // we allow some tolerance as different devices can produce different results (e.g., Nexus 6 vs OnePlus 3T; see testHDR18 on Nexus 6 which needs a tolerance of 2)
        // interestingly it's testHDR18 that also needs a higher tolerance for Nokia 8 vs Galaxy S10e
        assertTrue(Math.abs(exp_min_value - hdrHistogramDetails.min_value) <= 3);
        assertTrue(Math.abs(exp_median_value - hdrHistogramDetails.median_value) <= 3);
        assertTrue(Math.abs(exp_max_value - hdrHistogramDetails.max_value) <= 3);
    }

    public interface TestAvgCallback {
        void doneProcessAvg(int index); // called after every call to HDRProcessor.processAvg()
    }

    /** The following testAvgX tests test the Avg noise reduction algorithm on a given set of input images.
     *  By testing on a fixed sample, this makes it easier to finetune the algorithm for quality and performance.
     *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
     *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
     *  time to transfer to the device everytime we run the tests.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static HistogramDetails subTestAvg(MainActivity activity, List<String> inputs, String output_name, int iso, long exposure_time, float zoom_factor, TestAvgCallback cb) {
        Log.d(TAG, "subTestAvg");

        try {
            Thread.sleep(1000); // wait for camera to open
        }
        catch(InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }

        /*Bitmap nr_bitmap = getBitmapFromFile(activity, inputs.get(0));
        long time_s = System.currentTimeMillis();
        try {
            for(int i=1;i<inputs.size();i++) {
                Log.d(TAG, "processAvg for image: " + i);
                Bitmap new_bitmap = getBitmapFromFile(activity, inputs.get(i));
                float avg_factor = (float)i;
                activity.getApplicationInterface().getHDRProcessor().processAvg(nr_bitmap, new_bitmap, avg_factor, true);
                // processAvg recycles new_bitmap
                if( cb != null ) {
                    cb.doneProcessAvg(i);
                }
                //break; // test
            }
            //activity.getApplicationInterface().getHDRProcessor().processAvgMulti(inputs, hdr_strength, 4);
        }
        catch(HDRProcessorException e) {
            Log.e(TAG, "processAvg failed", e);
            throw new RuntimeException();
        }
        Log.d(TAG, "Avg time: " + (System.currentTimeMillis() - time_s));

        {
            activity.getApplicationInterface().getHDRProcessor().avgBrighten(nr_bitmap);
            Log.d(TAG, "time after brighten: " + (System.currentTimeMillis() - time_s));
        }*/

        Bitmap nr_bitmap;
        try {
            HDRProcessor hdrProcessor = activity.getApplicationInterface().getHDRProcessor();
            // initialise allocation from first two bitmaps
            //int inSampleSize = hdrProcessor.getAvgSampleSize(inputs.size());
            int inSampleSize = hdrProcessor.getAvgSampleSize(iso, exposure_time);
            Bitmap bitmap0 = getBitmapFromFile(activity, inputs.get(0), inSampleSize);
            Bitmap bitmap1 = getBitmapFromFile(activity, inputs.get(1), inSampleSize);
            int width = bitmap0.getWidth();
            int height = bitmap0.getHeight();

            float avg_factor = 1.0f;
            List<Long> times = new ArrayList<>();
            long time_s = System.currentTimeMillis();
            HDRProcessor.AvgData avg_data = hdrProcessor.processAvg(bitmap0, bitmap1, avg_factor, iso, exposure_time, zoom_factor);
            times.add(System.currentTimeMillis() - time_s);
            // processAvg recycles both bitmaps
            if( cb != null ) {
                cb.doneProcessAvg(1);
            }

            for(int i=2;i<inputs.size();i++) {
                Log.d(TAG, "processAvg for image: " + i);

                Bitmap new_bitmap = getBitmapFromFile(activity, inputs.get(i), inSampleSize);
                avg_factor = (float)i;
                time_s = System.currentTimeMillis();
                hdrProcessor.updateAvg(avg_data, width, height, new_bitmap, avg_factor, iso, exposure_time, zoom_factor);
                times.add(System.currentTimeMillis() - time_s);
                // updateAvg recycles new_bitmap
                if( cb != null ) {
                    cb.doneProcessAvg(i);
                }
            }

            time_s = System.currentTimeMillis();
            nr_bitmap = hdrProcessor.avgBrighten(avg_data, width, height, iso, exposure_time);
            avg_data.destroy();
            //noinspection UnusedAssignment
            avg_data = null;
            times.add(System.currentTimeMillis() - time_s);

            long total_time = 0;
            Log.d(TAG, "*** times are:");
            for(long time : times) {
                total_time += time;
                Log.d(TAG, "    " + time);
            }
            Log.d(TAG, "    total: " + total_time);
        }
        catch(HDRProcessorException e) {
            Log.e(TAG, "HDRProcessorException", e);
            throw new RuntimeException();
        }

        saveBitmap(activity, nr_bitmap, output_name);
        HistogramDetails hdrHistogramDetails = checkHistogram(activity, nr_bitmap);
        nr_bitmap.recycle();
        System.gc();
        inputs.clear();

        try {
            Thread.sleep(500);
        }
        catch(InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }

        return hdrHistogramDetails;
    }

    /**
     * @param panorama_pics_per_screen The value of panorama_pics_per_screen used when taking the input photos.
     * @param camera_angle_x The value of preview.getViewAngleX(for_preview=false) (in degrees) when taking the input photos (on the device used).
     * @param camera_angle_y The value of preview.getViewAngleY(for_preview=false) (in degrees) when taking the input photos (on the device used).
     */
    public static void subTestPanorama(MainActivity activity, List<String> inputs, String output_name, String gyro_debug_info_filename, float panorama_pics_per_screen, float camera_angle_x, float camera_angle_y, float gyro_tol_degrees) {
        Log.d(TAG, "subTestPanorama");

        // we set panorama_pics_per_screen in the test rather than using MyApplicationInterface.panorama_pics_per_screen,
        // in case the latter value is changed

        boolean first = true;
        Matrix scale_matrix = null;
        int bitmap_width = 0;
        int bitmap_height = 0;
        List<Bitmap> bitmaps = new ArrayList<>();
        for(String input : inputs) {
            Bitmap bitmap = getBitmapFromFile(activity, input);

            if( first ) {
                bitmap_width = bitmap.getWidth();
                bitmap_height = bitmap.getHeight();
                Log.d(TAG, "bitmap_width: " + bitmap_width);
                Log.d(TAG, "bitmap_height: " + bitmap_height);

                final int max_height = 2080;
                //final int max_height = 2079; // test non power of 2
                if( bitmap_height > max_height ) {
                    float scale = ((float)max_height) / ((float)bitmap_height);
                    Log.d(TAG, "scale: " + scale);
                    scale_matrix = new Matrix();
                    scale_matrix.postScale(scale, scale);
                }

                first = false;
            }

            // downscale
            if( scale_matrix != null ) {
                Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap_width, bitmap_height, scale_matrix, true);
                bitmap.recycle();
                bitmap = new_bitmap;
            }

            bitmaps.add(bitmap);
        }

        bitmap_width = bitmaps.get(0).getWidth();
        bitmap_height = bitmaps.get(0).getHeight();
        Log.d(TAG, "bitmap_width is now: " + bitmap_width);
        Log.d(TAG, "bitmap_height is now: " + bitmap_height);


        /*ImageSaver.GyroDebugInfo gyro_debug_info = null;
        if( gyro_debug_info_filename != null ) {
            InputStream inputStream;
            try {
                inputStream = new FileInputStream(gyro_debug_info_filename);
            }
            catch(FileNotFoundException e) {
                Log.e(TAG, "failed to load gyro debug info file: " + gyro_debug_info_filename, e);
                throw new RuntimeException();
            }

            gyro_debug_info = new ImageSaver.GyroDebugInfo();
            if( !ImageSaver.readGyroDebugXml(inputStream, gyro_debug_info) ) {
                Log.e(TAG, "failed to read gyro debug xml");
                throw new RuntimeException();
            }
            else if( gyro_debug_info.image_info.size() != bitmaps.size() ) {
                Log.e(TAG, "gyro debug xml has unexpected number of images: " + gyro_debug_info.image_info.size());
                throw new RuntimeException();
            }
        }*/
        //bitmaps.subList(2,bitmaps.size()).clear(); // test

        Bitmap panorama = null;
        try {
            final boolean crop = true;
            //final boolean crop = false; // test
            panorama = activity.getApplicationInterface().getPanoramaProcessor().panorama(bitmaps, panorama_pics_per_screen, camera_angle_y, crop);
        }
        catch(PanoramaProcessorException e) {
            Log.e(TAG, "panorama failed", e);
            fail();
        }

        saveBitmap(activity, panorama, output_name);
        try {
            Thread.sleep(500);
        }
        catch(InterruptedException e) {
            Log.e(TAG, "InterruptedException from sleep", e);
        }

        // check we've cropped correctly:
        final float black_factor = 0.9f;
        // top:
        int n_black = 0;
        for(int i=0;i<panorama.getWidth();i++) {
            int color = panorama.getPixel(i, 0);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getWidth()*black_factor ) {
            Log.e(TAG, "too many black pixels on top border: " + n_black);
            fail();
        }
        // bottom:
        n_black = 0;
        for(int i=0;i<panorama.getWidth();i++) {
            int color = panorama.getPixel(i, panorama.getHeight()-1);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getWidth()*black_factor ) {
            Log.e(TAG, "too many black pixels on bottom border: " + n_black);
            fail();
        }
        // left:
        n_black = 0;
        for(int i=0;i<panorama.getHeight();i++) {
            int color = panorama.getPixel(0, i);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getHeight()*black_factor ) {
            Log.e(TAG, "too many black pixels on left border: " + n_black);
            fail();
        }
        // right:
        n_black = 0;
        for(int i=0;i<panorama.getHeight();i++) {
            int color = panorama.getPixel(panorama.getWidth()-1, i);
            if( ((color >> 16) & 0xff) == 0 && ((color >> 8) & 0xff) == 0 && ((color) & 0xff) == 0 ) {
                n_black++;
            }
        }
        if( n_black >= panorama.getHeight()*black_factor ) {
            Log.e(TAG, "too many black pixels on right border: " + n_black);
            fail();
        }
    }

    public static void waitForTakePhotoChecks(MainActivity activity, long time_s) {
        Preview preview = activity.getPreview();
        View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchMultiCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_multi_camera);
        View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = activity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = activity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = activity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = activity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = activity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = activity.findViewById(net.sourceforge.opencamera.R.id.share);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean is_focus_bracketing = activity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_panorama = activity.supportsPanorama() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_panorama");

        // make sure the test fails rather than hanging, if for some reason we get stuck (note that testTakePhotoManualISOExposure takes over 10s on Nexus 6)
        // also see note at end of setToDefault for Nokia 8, need to sleep briefly to avoid hanging here
        if( !is_focus_bracketing ) {
            assertTrue(System.currentTimeMillis() - time_s < (is_panorama ? 50000 : 20000)); // need longer for panorama on Nexus 7 for testTakePhotoPanoramaMax
        }
        assertTrue(!preview.isTakingPhoto() || switchCameraButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || switchMultiCameraButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || switchVideoButton.getVisibility() == View.GONE);
        //assertTrue(!preview.isTakingPhoto() || flashButton.getVisibility() == View.GONE);
        //assertTrue(!preview.isTakingPhoto() || focusButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || exposureButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || exposureLockButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || audioControlButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || popupButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || trashButton.getVisibility() == View.GONE);
        assertTrue(!preview.isTakingPhoto() || shareButton.getVisibility() == View.GONE);
    }

    private static void checkFocusInitial(MainActivity activity, final String focus_value, final String focus_value_ui) {
        String new_focus_value_ui = activity.getPreview().getCurrentFocusValue();
        //noinspection StringEquality
        assertTrue(new_focus_value_ui == focus_value_ui || new_focus_value_ui.equals(focus_value_ui)); // also need to do == check, as strings may be null if focus not supported
        assertEquals(activity.getPreview().getCameraController().getFocusValue(), focus_value);
    }

    public static void checkFocusAfterTakePhoto(MainActivity activity, final String focus_value, final String focus_value_ui) {
        // focus should be back to normal now:
        String new_focus_value_ui = activity.getPreview().getCurrentFocusValue();
        Log.d(TAG, "focus_value_ui: " + focus_value_ui);
        Log.d(TAG, "new new_focus_value_ui: " + new_focus_value_ui);
        //noinspection StringEquality
        assertTrue(new_focus_value_ui == focus_value_ui || new_focus_value_ui.equals(focus_value_ui)); // also need to do == check, as strings may be null if focus not supported
        String new_focus_value = activity.getPreview().getCameraController().getFocusValue();
        Log.d(TAG, "focus_value: " + focus_value);
        Log.d(TAG, "new focus_value: " + new_focus_value);
        if( new_focus_value_ui != null && new_focus_value_ui.equals("focus_mode_continuous_picture") && focus_value.equals("focus_mode_auto") && new_focus_value.equals("focus_mode_continuous_picture") ) {
            // this is fine, it just means we were temporarily in touch-to-focus mode
        }
        else {
            assertEquals(new_focus_value, focus_value);
        }
    }

    public static void checkFocusAfterTakePhoto2(MainActivity activity, final boolean touch_to_focus, final boolean single_tap_photo, final boolean double_tap_photo, final boolean test_wait_capture_result, final boolean locked_focus, final boolean can_auto_focus, final boolean can_focus_area, final int saved_count) {
        Preview preview = activity.getPreview();
        // in locked focus mode, taking photo should never redo an auto-focus
        // if photo mode, we may do a refocus if the previous auto-focus failed, but not if it succeeded
        Log.d(TAG, "2 count_cameraAutoFocus: " + preview.count_cameraAutoFocus);
        if( locked_focus ) {
            assertEquals(preview.count_cameraAutoFocus, (can_auto_focus ? saved_count + 1 : saved_count));
        }
        if( test_wait_capture_result ) {
            // if test_wait_capture_result, then we'll have waited too long, so focus settings may have changed
        }
        else if( touch_to_focus ) {
            Log.d(TAG, "can_focus_area?: " + can_focus_area);
            Log.d(TAG, "hasFocusArea?: " + preview.hasFocusArea());
            if( single_tap_photo || double_tap_photo ) {
                assertFalse(preview.hasFocusArea());
                assertNull(preview.getCameraController().getFocusAreas());
                assertNull(preview.getCameraController().getMeteringAreas());
            }
            else if( can_focus_area ) {
                assertTrue(preview.hasFocusArea());
                assertNotNull(preview.getCameraController().getFocusAreas());
                assertEquals(1, preview.getCameraController().getFocusAreas().size());
                assertNotNull(preview.getCameraController().getMeteringAreas());
                assertEquals(1, preview.getCameraController().getMeteringAreas().size());
            }
            else {
                assertFalse(preview.hasFocusArea());
                assertNull(preview.getCameraController().getFocusAreas());

                if( preview.getCameraController().supportsMetering() ) {
                    // we still set metering areas
                    assertNotNull(preview.getCameraController().getMeteringAreas());
                    assertEquals(1, preview.getCameraController().getMeteringAreas().size());
                }
                else {
                    assertNull(preview.getCameraController().getMeteringAreas());
                }
            }
        }
        else {
            assertFalse(preview.hasFocusArea());
            assertNull(preview.getCameraController().getFocusAreas());
            assertNull(preview.getCameraController().getMeteringAreas());
        }
    }

    private static int getExpNNewFiles(MainActivity activity, final boolean is_raw) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean hdr_save_expo =  sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false);
        boolean is_hdr = activity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        boolean is_expo = activity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");
        boolean is_focus_bracketing = activity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_fast_burst = activity.supportsFastBurst() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_fast_burst");
        String n_expo_images_s = sharedPreferences.getString(PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "3");
        int n_expo_images = Integer.parseInt(n_expo_images_s);
        String n_focus_bracketing_images_s = sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3");
        int n_focus_bracketing_images = Integer.parseInt(n_focus_bracketing_images_s);
        String n_fast_burst_images_s = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
        int n_fast_burst_images = Integer.parseInt(n_fast_burst_images_s);
        boolean is_preshot = activity.getApplicationInterface().getPreShotsPref(activity.getApplicationInterface().getPhotoMode());

        int exp_n_new_files;
        if( is_hdr && hdr_save_expo ) {
            exp_n_new_files = 4;
            if( is_raw && !activity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files += 3;
            }
        }
        else if( is_expo ) {
            exp_n_new_files = n_expo_images;
            if( is_raw && !activity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files *= 2;
            }
        }
        else if( is_focus_bracketing ) {
            exp_n_new_files = n_focus_bracketing_images;
            if( is_raw && !activity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files *= 2;
            }
        }
        else if( is_fast_burst )
            exp_n_new_files = n_fast_burst_images;
        else {
            exp_n_new_files = 1;
            if( is_raw && !activity.getApplicationInterface().isRawOnly() ) {
                exp_n_new_files *= 2;
            }
        }

        if( is_preshot )
            exp_n_new_files++;

        Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
        return exp_n_new_files;
    }

    private static void checkFilenames(MainActivity activity, final boolean is_raw, final String [] files, final String [] files2) {
        Log.d(TAG, "checkFilenames");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean hdr_save_expo =  sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false);
        boolean is_hdr = activity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        boolean is_fast_burst = activity.supportsFastBurst() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_fast_burst");
        boolean is_expo = activity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");
        boolean is_focus_bracketing = activity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_preshot = activity.getApplicationInterface().getPreShotsPref(activity.getApplicationInterface().getPhotoMode());

        // check files have names as expected
        String filename_jpeg = null;
        String filename_dng = null;
        String filename_preshot_video =  null;
        int n_files = files == null ? 0 : files.length;
        for(String file : files2) {
            Log.d(TAG, "check file: " + file);
            boolean is_new = true;
            for(int j=0;j<n_files && is_new;j++) {
                if( file.equals( files[j] ) ) {
                    is_new = false;
                    break;
                }
            }
            if( is_new ) {
                Log.d(TAG, "file is new");
                //String filename = file.getName();
                //noinspection UnnecessaryLocalVariable
                String filename = file;
                assertTrue(filename.startsWith("IMG_"));
                if( filename.endsWith(".jpg") ) {
                    assertTrue(hdr_save_expo || is_expo || is_focus_bracketing || is_fast_burst || filename_jpeg == null);
                    if( is_hdr && hdr_save_expo ) {
                        // only look for the "_HDR" image
                        if( filename.contains("_HDR") )
                            filename_jpeg = filename;
                    }
                    else if( is_expo || is_focus_bracketing ) {
                        if( filename_jpeg != null ) {
                            // check same root
                            int last_underscore_jpeg = filename_jpeg.lastIndexOf('_');
                            assertTrue(last_underscore_jpeg != -1);
                            String filename_base_jpeg = filename_jpeg.substring(0, last_underscore_jpeg+1);

                            int last_underscore = filename.lastIndexOf('_');
                            assertTrue(last_underscore != -1);
                            String filename_base = filename.substring(0, last_underscore+1);
                            Log.d(TAG, "filename_base: " + filename_base);

                            assertEquals(filename_base_jpeg, filename_base);
                        }
                        filename_jpeg = filename; // store the last name, to match activity.test_last_saved_image
                    }
                    else {
                        filename_jpeg = filename;
                    }
                }
                else if( filename.endsWith(".dng") ) {
                    assertTrue(is_raw);
                    assertTrue(hdr_save_expo || is_expo || is_focus_bracketing || filename_dng == null);
                    filename_dng = filename;
                }
                else if( filename.endsWith(".mp4") ) {
                    assertTrue(is_preshot);
                    assertNull(filename_preshot_video);
                    filename_preshot_video = filename;
                }
                else {
                    fail();
                }
            }
        }
        assertEquals((filename_jpeg == null), (is_raw && activity.getApplicationInterface().isRawOnly() && !is_hdr));
        assertEquals((filename_dng != null), is_raw);
        assertEquals((filename_preshot_video != null), is_preshot);

        if( is_raw && !activity.getApplicationInterface().isRawOnly() ) {
            // check we have same filenames (ignoring extensions)
            // if HDR, then we should exclude the "_HDR" vs "_x" of the base filenames
            // if expo, then exclude the "_x" as values may be different due to different order of JPEG vs DNG files in the files2 array (at least on Galaxy S10e)
            String filename_base_jpeg;
            String filename_base_dng;
            if( is_hdr ) {
                filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-7);
                //noinspection DuplicateExpressions
                filename_base_dng = filename_dng.substring(0, filename_dng.length()-5);
            }
            else if( is_expo ) {
                filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-5);
                //noinspection DuplicateExpressions
                filename_base_dng = filename_dng.substring(0, filename_dng.length()-5);
            }
            else {
                //noinspection DuplicateExpressions
                filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-4);
                filename_base_dng = filename_dng.substring(0, filename_dng.length()-4);
            }
            Log.d(TAG, "filename_base_jpeg: " + filename_base_jpeg);
            Log.d(TAG, "filename_base_dng: " + filename_base_dng);
            assertEquals(filename_base_jpeg, filename_base_dng);
        }

        if( is_preshot ) {
            // check we have same filenames (ignoring extensions)
            //noinspection DuplicateExpressions
            String filename_base_jpeg = filename_jpeg.substring(0, filename_jpeg.length()-4);
            String filename_base_preshot_video = filename_preshot_video.substring(0, filename_jpeg.length()-4);
            Log.d(TAG, "filename_base_jpeg: " + filename_base_jpeg);
            Log.d(TAG, "filename_base_preshot_video: " + filename_base_preshot_video);
            assertEquals(filename_base_jpeg, filename_base_preshot_video);
        }
    }

    private enum UriType {
        MEDIASTORE_IMAGES,
        MEDIASTORE_VIDEOS,
        STORAGE_ACCESS_FRAMEWORK
    }

    /** Returns an array of filenames (not including full path) of images or videos in the current
     *  save folder, by querying the media store; or by using storage access framework on the
     *  supplied uri.
     */
    private static List<String> mediaFilesinSaveFolder(MainActivity activity, Uri baseUri, String bucket_id, UriType uri_type) {
        List<String> files = new ArrayList<>();
        final int column_name_c = 0; // filename (without path), including extension

        String [] projection;
        switch( uri_type ) {
            case MEDIASTORE_IMAGES:
                projection = new String[] {MediaStore.Images.ImageColumns.DISPLAY_NAME};
                break;
            case MEDIASTORE_VIDEOS:
                //noinspection DuplicateBranchesInSwitch
                projection = new String[] {MediaStore.Video.VideoColumns.DISPLAY_NAME};
                break;
            case STORAGE_ACCESS_FRAMEWORK:
                projection = new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
                break;
            default:
                throw new RuntimeException("unknown uri_type: " + uri_type);
        }

        String selection = "";
        switch( uri_type ) {
            case MEDIASTORE_IMAGES:
                selection = MediaStore.Images.ImageColumns.BUCKET_ID + " = " + bucket_id;
                break;
            case MEDIASTORE_VIDEOS:
                //noinspection DuplicateBranchesInSwitch
                selection = MediaStore.Video.VideoColumns.BUCKET_ID + " = " + bucket_id;
                break;
            case STORAGE_ACCESS_FRAMEWORK:
                break;
            default:
                throw new RuntimeException("unknown uri_type: " + uri_type);
        }
        Log.d(TAG, "selection: " + selection);

        Cursor cursor = activity.getContentResolver().query(baseUri, projection, selection, null, null);
        if( cursor != null && cursor.moveToFirst() ) {
            Log.d(TAG, "found: " + cursor.getCount());

            do {
                String name = cursor.getString(column_name_c);
                files.add(name);
            }
            while( cursor.moveToNext() );
        }

        if( cursor != null ) {
            cursor.close();
        }

        return files;
    }

    /** Returns an array of filenames (not including full path) in the current save folder.
     */
    public static String [] filesInSaveFolder(MainActivity activity) {
        Log.d(TAG, "filesInSaveFolder");
        if( MainActivity.useScopedStorage() ) {
            List<String> files = new ArrayList<>();
            if( activity.getStorageUtils().isUsingSAF() ) {
                // See documentation for StorageUtils.getLatestMediaSAF() - for some reason with scoped storage when not having READ_EXTERNAL_STORAGE,
                // we can't query the mediastore for files saved via SAF!
                Uri treeUri = activity.getStorageUtils().getTreeUriSAF();
                Uri baseUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                files.addAll( mediaFilesinSaveFolder(activity, baseUri, null, UriType.STORAGE_ACCESS_FRAMEWORK) );
            }
            else {
                String save_folder = activity.getStorageUtils().getImageFolderPath();
                String bucket_id = String.valueOf(save_folder.toLowerCase().hashCode());
                files.addAll( mediaFilesinSaveFolder(activity, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, bucket_id, UriType.MEDIASTORE_IMAGES) );
                files.addAll( mediaFilesinSaveFolder(activity, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, bucket_id, UriType.MEDIASTORE_VIDEOS) );
            }

            if( files.isEmpty() ) {
                return null;
            }
            else {
                return files.toArray(new String[0]);
            }
        }
        else {
            File folder = activity.getImageFolder();
            File [] files = folder.listFiles();
            if( files == null )
                return null;
            String [] filenames = new String[files.length];
            for(int i=0;i<files.length;i++) {
                filenames[i] = files[i].getName();
            }
            return filenames;
        }
    }

    public static void checkFilesAfterTakePhoto(MainActivity activity, final boolean is_raw, final boolean test_wait_capture_result, final String [] files) throws InterruptedException {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean is_dro = activity.supportsDRO() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_dro");
        boolean is_hdr = activity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        boolean is_nr = activity.supportsNoiseReduction() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_noise_reduction");
        boolean is_expo = activity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");
        boolean is_focus_bracketing = activity.supportsFocusBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_focus_bracketing");
        boolean is_fast_burst = activity.supportsFastBurst() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_fast_burst");
        boolean is_x_night = activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT) && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_x_night");
        String n_expo_images_s = sharedPreferences.getString(PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "3");
        int n_expo_images = Integer.parseInt(n_expo_images_s);
        String n_focus_bracketing_images_s = sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3");
        int n_focus_bracketing_images = Integer.parseInt(n_focus_bracketing_images_s);
        String n_fast_burst_images_s = sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5");
        int n_fast_burst_images = Integer.parseInt(n_fast_burst_images_s);

        Date date = new Date();
        String suffix = "";
        int max_time_s = 3;
        if( is_dro ) {
            suffix = "_DRO";
        }
        else if( is_hdr ) {
            suffix = "_HDR";
        }
        else if( is_nr ) {
            suffix = "_NR";
            if( activity.getApplicationInterface().getNRModePref() == MyApplicationInterface.NRModePref.NRMODE_LOW_LIGHT )
                max_time_s += 11; // takes longer to save low light photo
            else
                max_time_s += 10;
        }
        else if( is_expo ) {
            suffix = "_" + (n_expo_images-1);
        }
        else if( is_focus_bracketing ) {
            suffix = "_" + (n_focus_bracketing_images-1); // when focus bracketing starts from _0
            //suffix = "_" + (n_focus_bracketing_images); // when focus bracketing starts from _1
            max_time_s = 60; // can take much longer to save in focus bracketing mode!
        }
        else if( is_fast_burst ) {
            suffix = "_" + (n_fast_burst_images-1); // when burst numbering starts from _0
            //suffix = "_" + (n_fast_burst_images); // when burst numbering starts from _1
            max_time_s = 6; // takes longer to save 20 images!
        }
        else if( is_x_night ) {
            suffix = "_Night";
        }

        if( is_raw ) {
            max_time_s += 6; // extra time needed for Nexus 6 at least
        }

        boolean pause_preview = sharedPreferences.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
        if( pause_preview ) {
            max_time_s += 3; // need to allow longer for testTakePhotoRawWaitCaptureResult with Nexus 6 at least
        }

        int n_files = files == null ? 0 : files.length;
        String [] files2 = filesInSaveFolder(activity);
        int n_new_files = (files2 == null ? 0 : files2.length) - n_files;
        Log.d(TAG, "n_new_files: " + n_new_files);
        int exp_n_new_files = getExpNNewFiles(activity, is_raw);
        assertEquals(n_new_files, exp_n_new_files);
        checkFilenames(activity, is_raw, files, files2);
        Thread.sleep(1500); // wait until we've scanned
        if( test_wait_capture_result ) {
            // if test_wait_capture_result, then it may take longer before we've scanned
        }
        else {
            Log.d(TAG, "failed to scan: " + activity.getStorageUtils().failed_to_scan);
            assertFalse(activity.getStorageUtils().failed_to_scan);
        }

        if( !activity.getApplicationInterface().isRawOnly() ) {
            String saved_image_filename;
            if( MainActivity.useScopedStorage() || activity.getStorageUtils().isUsingSAF() ) {
                /*assertNotNull(activity.test_last_saved_imagename);
                saved_image_filename = activity.test_last_saved_imagename;*/
                assertNotNull(activity.test_last_saved_imageuri);
                saved_image_filename = activity.getStorageUtils().getFileName(activity.test_last_saved_imageuri);
            }
            else {
                assertNotNull(activity.test_last_saved_image);
                File saved_image_file = new File(activity.test_last_saved_image);
                saved_image_filename = saved_image_file.getName();
            }
            Log.d(TAG, "saved name: " + saved_image_filename);
            /*Log.d(TAG, "expected name: " + expected_filename);
            Log.d(TAG, "expected name1: " + expected_filename1);
            assertTrue(expected_filename.equals(saved_image_file.getName()) || expected_filename1.equals(saved_image_file.getName()));*/
            // allow for possibility that the time has passed since taking the photo
            // but start from -1, as time may have passed in the Test code since saving the file
            boolean matched = false;
            for(int i=-1;i<=max_time_s && !matched;i++) {
                Date test_date = new Date(date.getTime() - 1000L *i);
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(test_date);
                String expected_filename = "IMG_" + timeStamp + suffix + ".jpg";
                Log.d(TAG, "expected name: " + expected_filename);
                if( expected_filename.equals(saved_image_filename) )
                    matched = true;
            }
            assertTrue(matched);
        }
    }

    public static void postTakePhotoChecks(MainActivity activity, final boolean immersive_mode, final int exposureVisibility, final int exposureLockVisibility) {
        Preview preview = activity.getPreview();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchMultiCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_multi_camera);
        View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = activity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = activity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = activity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = activity.findViewById(net.sourceforge.opencamera.R.id.share);

        // trash/share only shown when preview is paused after taking a photo
        boolean pause_preview =  sharedPreferences.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
        if( pause_preview ) {
            assertFalse(preview.isPreviewStarted());
            assertEquals(switchCameraButton.getVisibility(), View.GONE);
            assertEquals(switchMultiCameraButton.getVisibility(), View.GONE);
            assertEquals(switchVideoButton.getVisibility(), View.GONE);
            assertEquals(exposureButton.getVisibility(), View.GONE);
            assertEquals(exposureLockButton.getVisibility(), View.GONE);
            assertEquals(audioControlButton.getVisibility(), View.GONE);
            assertEquals(popupButton.getVisibility(), View.GONE);
            assertEquals(trashButton.getVisibility(), View.VISIBLE);
            assertEquals(shareButton.getVisibility(), View.VISIBLE);
        }
        else {
            assertTrue(preview.isPreviewStarted()); // check preview restarted
            assertEquals(switchCameraButton.getVisibility(), (preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE));
            assertEquals(switchMultiCameraButton.getVisibility(), (activity.showSwitchMultiCamIcon() ? View.VISIBLE : View.GONE));
            assertEquals(switchVideoButton.getVisibility(), View.VISIBLE);
            if( !immersive_mode ) {
                assertEquals(exposureButton.getVisibility(), exposureVisibility);
                assertEquals(exposureLockButton.getVisibility(), exposureLockVisibility);
            }
            assertEquals(audioControlButton.getVisibility(), (has_audio_control_button ? View.VISIBLE : View.GONE));
            assertEquals(popupButton.getVisibility(), View.VISIBLE);
            assertEquals(trashButton.getVisibility(), View.GONE);
            assertEquals(shareButton.getVisibility(), View.GONE);
        }
    }

    public static class SubTestTakePhotoInfo {
        public boolean has_thumbnail_anim;
        public boolean is_hdr;
        public boolean is_nr;
        public boolean is_expo;
        public int exposureVisibility;
        public int exposureLockVisibility;
        public String focus_value;
        public String focus_value_ui;
        public boolean can_auto_focus;
        public boolean manual_can_auto_focus;
        public boolean can_focus_area;
    }

    public static SubTestTakePhotoInfo getSubTestTakePhotoInfo(MainActivity activity, boolean immersive_mode, boolean single_tap_photo, boolean double_tap_photo) {
        assertTrue(activity.getPreview().isPreviewStarted());
        assertFalse(activity.getApplicationInterface().getImageSaver().test_queue_blocked);

        SubTestTakePhotoInfo info = new SubTestTakePhotoInfo();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);

        info.has_thumbnail_anim = sharedPreferences.getBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, true);
        info.is_hdr = activity.supportsHDR() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_hdr");
        info.is_nr = activity.supportsNoiseReduction() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_noise_reduction");
        info.is_expo = activity.supportsExpoBracketing() && sharedPreferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std").equals("preference_photo_mode_expo_bracketing");

        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchMultiCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_multi_camera);
        View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        //View flashButton = activity.findViewById(net.sourceforge.opencamera.R.id.flash);
        //View focusButton = activity.findViewById(net.sourceforge.opencamera.R.id.focus_mode);
        View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = activity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = activity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = activity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = activity.findViewById(net.sourceforge.opencamera.R.id.share);
        assertEquals(switchCameraButton.getVisibility(), (immersive_mode ? View.GONE : (activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE)));
        assertEquals(switchMultiCameraButton.getVisibility(), (immersive_mode ? View.GONE : (activity.showSwitchMultiCamIcon() ? View.VISIBLE : View.GONE)));
        assertEquals(switchVideoButton.getVisibility(), (immersive_mode ? View.GONE : View.VISIBLE));
        info.exposureVisibility = exposureButton.getVisibility();
        info.exposureLockVisibility = exposureLockButton.getVisibility();
        assertEquals(audioControlButton.getVisibility(), ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        assertEquals(popupButton.getVisibility(), (immersive_mode ? View.GONE : View.VISIBLE));
        assertEquals(trashButton.getVisibility(), View.GONE);
        assertEquals(shareButton.getVisibility(), View.GONE);

        info.focus_value = activity.getPreview().getCameraController().getFocusValue();
        info.focus_value_ui = activity.getPreview().getCurrentFocusValue();
        info.can_auto_focus = false;
        info.manual_can_auto_focus = false;
        info.can_focus_area = false;
        if( info.focus_value.equals("focus_mode_auto") || info.focus_value.equals("focus_mode_macro") ) {
            info.can_auto_focus = true;
        }

        if( info.focus_value.equals("focus_mode_auto") || info.focus_value.equals("focus_mode_macro") ) {
            info.manual_can_auto_focus = true;
        }
        else if( info.focus_value.equals("focus_mode_continuous_picture") && !single_tap_photo && !double_tap_photo ) {
            // if single_tap_photo or double_tap_photo, and continuous mode, we go straight to taking a photo rather than doing a touch to focus
            info.manual_can_auto_focus = true;
        }

        if( activity.getPreview().getMaxNumFocusAreas() != 0 && ( info.focus_value.equals("focus_mode_auto") || info.focus_value.equals("focus_mode_macro") || info.focus_value.equals("focus_mode_continuous_picture") || info.focus_value.equals("focus_mode_continuous_video") || info.focus_value.equals("focus_mode_manual2") ) ) {
            info.can_focus_area = true;
        }
        Log.d(TAG, "focus_value? " + info.focus_value);
        Log.d(TAG, "can_auto_focus? " + info.can_auto_focus);
        Log.d(TAG, "manual_can_auto_focus? " + info.manual_can_auto_focus);
        Log.d(TAG, "can_focus_area? " + info.can_focus_area);

        checkFocusInitial(activity, info.focus_value, info.focus_value_ui);

        return info;
    }

    public static void touchToFocusChecks(MainActivity activity, final boolean single_tap_photo, final boolean double_tap_photo, final boolean manual_can_auto_focus, final boolean can_focus_area, final String focus_value, final String focus_value_ui, int saved_count) {
        Preview preview = activity.getPreview();
        Log.d(TAG, "1 count_cameraAutoFocus: " + preview.count_cameraAutoFocus);
        assertEquals((manual_can_auto_focus ? saved_count + 1 : saved_count), preview.count_cameraAutoFocus);
        Log.d(TAG, "has focus area?: " + preview.hasFocusArea());
        if( single_tap_photo || double_tap_photo ) {
            assertFalse(preview.hasFocusArea());
            assertNull(preview.getCameraController().getFocusAreas());
            assertNull(preview.getCameraController().getMeteringAreas());
        }
        else if( can_focus_area ) {
            assertTrue(preview.hasFocusArea());
            assertNotNull(preview.getCameraController().getFocusAreas());
            assertEquals(1, preview.getCameraController().getFocusAreas().size());
            assertNotNull(preview.getCameraController().getMeteringAreas());
            assertEquals(1, preview.getCameraController().getMeteringAreas().size());
        }
        else {
            assertFalse(preview.hasFocusArea());
            assertNull(preview.getCameraController().getFocusAreas());
            if( preview.getCameraController().supportsMetering() ) {
                // we still set metering areas
                assertNotNull(preview.getCameraController().getMeteringAreas());
                assertEquals(1, preview.getCameraController().getMeteringAreas().size());
            }
            else {
                assertNull(preview.getCameraController().getMeteringAreas());
            }
        }
        String new_focus_value_ui = preview.getCurrentFocusValue();
        //noinspection StringEquality
        assertTrue(new_focus_value_ui == focus_value_ui || new_focus_value_ui.equals(focus_value_ui)); // also need to do == check, as strings may be null if focus not supported
        if( focus_value.equals("focus_mode_continuous_picture") && !single_tap_photo && !double_tap_photo && preview.supportsFocus() && preview.getSupportedFocusValues().contains("focus_mode_auto") )
            assertEquals("focus_mode_auto", preview.getCameraController().getFocusValue()); // continuous focus mode switches to auto focus on touch (unless single_tap_photo, or auto focus not supported)
        else
            assertEquals(preview.getCameraController().getFocusValue(), focus_value);
    }

    private static boolean gpsIsNull(String gps_string) {
        return gps_string == null || gps_string.equals("0/1,0/1,0/100000");
    }

    /** Tests the Exif tags in the resultant file. If the file is null, the uri will be
     *  used instead to read the Exif tags.
     */
    public static void testExif(MainActivity activity, String file, Uri uri, boolean expect_device_tags, boolean expect_datetime, boolean expect_gps) throws IOException {
        //final String TAG_GPS_IMG_DIRECTION = "GPSImgDirection";
        //final String TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef";
        InputStream inputStream = null;
        ExifInterface exif;
        if( file != null ) {
            assertNull(uri); // should only supply one of file or uri
            exif = new ExifInterface(file);
        }
        else {
            assertNotNull(uri);
            inputStream = activity.getContentResolver().openInputStream(uri);
            exif = new ExifInterface(inputStream);
        }

        assertNotNull(exif.getAttribute(ExifInterface.TAG_ORIENTATION));
        if( !( isEmulator() && Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1 ) ) {
            // older Android emulator versions don't store exif info in photos
            if( expect_device_tags ) {
                assertNotNull(exif.getAttribute(ExifInterface.TAG_MAKE));
                assertNotNull(exif.getAttribute(ExifInterface.TAG_MODEL));
            }
            else {
                assertNull(exif.getAttribute(ExifInterface.TAG_MAKE));
                assertNull(exif.getAttribute(ExifInterface.TAG_MODEL));

                assertNull(exif.getAttribute(ExifInterface.TAG_F_NUMBER));
                assertNull(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME));
                assertNull(exif.getAttribute(ExifInterface.TAG_FLASH));
                assertNull(exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH));
                assertNull(exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION));
                assertNull(exif.getAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID));
                assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT));
                assertNull(exif.getAttribute(ExifInterface.TAG_ARTIST));
                assertNull(exif.getAttribute(ExifInterface.TAG_COPYRIGHT));
            }

            if( expect_datetime ) {
                assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME));
                assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
                assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED));
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
                    // not available on Galaxy Nexus Android 4.3 at least
                    assertNotNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME));
                    assertNotNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL));
                    assertNotNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED));
                    // TAG_OFFSET_TIME at least no longer saved on Pixel 6 Pro
                    //assertNotNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME));
                    //assertNotNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL));
                    //assertNotNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED));
                }
            }
            else {
                assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME));
                assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
                assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED));
                assertNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME));
                assertNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL));
                assertNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED));
                assertNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME));
                assertNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL));
                assertNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED));
            }

            if( expect_gps ) {
                assertFalse(gpsIsNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)));
                assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF));
                assertFalse(gpsIsNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)));
                assertNotNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF));
                // can't read custom tags, even though we can write them?!
                //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION) != null);
                //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION_REF) != null);
            }
            else {
                assertTrue(gpsIsNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)));
                assertTrue(gpsIsNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)));
                // TAG_GPS_LATITUDE_REF, TAG_GPS_LONGITUDE_REF are still non-null on Samsung Galaxy S24+ with Camera2 API
                //assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF));
                //assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF));
                // can't read custom tags, even though we can write them?!
                //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION) == null);
                //assertTrue(exif.getAttribute(TAG_GPS_IMG_DIRECTION_REF) == null);
            }
        }

        if( inputStream != null ) {
            inputStream.close();
        }
    }

    public static void preTakeVideoChecks(MainActivity activity, boolean immersive_mode) {
        Preview preview = activity.getPreview();

        assertTrue(preview.isPreviewStarted());
        if( preview.usingCamera2API() ) {
            assertEquals(preview.getCurrentPreviewSize().width, preview.getCameraController().test_texture_view_buffer_w);
            assertEquals(preview.getCurrentPreviewSize().height, preview.getCameraController().test_texture_view_buffer_h);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View pauseVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchMultiCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_multi_camera);
        View audioControlButton = activity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = activity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = activity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = activity.findViewById(net.sourceforge.opencamera.R.id.share);

        if( preview.isVideo() ) {
            assertEquals((int) (Integer) takePhotoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_video_selector);
            assertEquals((int) (Integer) switchVideoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_photo);
            assertEquals(takePhotoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.start_video));
            assertEquals(pauseVideoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video));
            assertEquals(switchVideoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_photo));
        }
        else {
            assertEquals((int) (Integer) takePhotoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_photo_selector);
            assertEquals((int) (Integer) switchVideoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_video);
            assertEquals(takePhotoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.take_photo));
            assertEquals(pauseVideoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video));
            assertEquals(switchVideoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.switch_to_video));
        }
        assertEquals(pauseVideoButton.getVisibility(), View.GONE);
        assertEquals(takePhotoVideoButton.getVisibility(), View.GONE);

        assertEquals(switchCameraButton.getVisibility(), (immersive_mode ? View.GONE : (preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE)));
        assertEquals(switchMultiCameraButton.getVisibility(), (immersive_mode ? View.GONE : (activity.showSwitchMultiCamIcon() ? View.VISIBLE : View.GONE)));
        assertEquals(switchVideoButton.getVisibility(), (immersive_mode ? View.GONE : View.VISIBLE));
        assertEquals(audioControlButton.getVisibility(), ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        assertEquals(popupButton.getVisibility(), (immersive_mode ? View.GONE : View.VISIBLE));
        assertEquals(trashButton.getVisibility(), View.GONE);
        assertEquals(shareButton.getVisibility(), View.GONE);
    }

    public static void takeVideoRecordingChecks(MainActivity activity, boolean immersive_mode, int exposureVisibility, int exposureLockVisibility) {
        Preview preview = activity.getPreview();

        View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View pauseVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchMultiCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_multi_camera);
        View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = activity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = activity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = activity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = activity.findViewById(net.sourceforge.opencamera.R.id.share);

        assertEquals((int) (Integer) takePhotoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_video_recording);
        assertEquals((int) (Integer) switchVideoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_photo);
        assertEquals(takePhotoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.stop_video));
        assertEquals(pauseVideoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video));
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N )
            assertEquals(pauseVideoButton.getVisibility(), View.VISIBLE);
        else
            assertEquals(pauseVideoButton.getVisibility(), View.GONE);
        if( preview.supportsPhotoVideoRecording() )
            assertEquals(takePhotoVideoButton.getVisibility(), View.VISIBLE);
        else
            assertEquals(takePhotoVideoButton.getVisibility(), View.GONE);
        assertEquals(switchCameraButton.getVisibility(), View.GONE);
        assertEquals(switchMultiCameraButton.getVisibility(), View.GONE);
        //assertTrue(switchVideoButton.getVisibility() == (immersive_mode ? View.GONE : View.VISIBLE));
        assertEquals(switchVideoButton.getVisibility(), View.GONE);
        assertEquals(audioControlButton.getVisibility(), View.GONE);
        assertEquals(popupButton.getVisibility(), (!immersive_mode && preview.supportsFlash() ? View.VISIBLE : View.GONE)); // popup button only visible when recording video if flash supported
        assertEquals(exposureButton.getVisibility(), exposureVisibility);
        assertEquals(exposureLockButton.getVisibility(), exposureLockVisibility);
        assertEquals(trashButton.getVisibility(), View.GONE);
        assertEquals(shareButton.getVisibility(), View.GONE);
    }

    public static void checkFilesAfterTakeVideo(MainActivity activity, boolean allow_failure, boolean has_cb, long time_ms, int n_non_video_files, boolean failed_to_start, int exp_n_new_files, int n_new_files) {
        if( !has_cb ) {
            if( time_ms <= 500 ) {
                // if quick, should have deleted corrupt video - but may be device dependent, sometimes we manage to record a video anyway!
                assertTrue(n_new_files == 0 || n_new_files == 1);
            }
            else if( failed_to_start ) {
                // if video recording failed to start, we should have deleted any file created!
                assertEquals(0, n_new_files);
            }
            else {
                assertEquals(n_non_video_files+1, n_new_files);
            }
        }
        else {
            Log.d(TAG, "exp_n_new_files: " + exp_n_new_files);
            if( exp_n_new_files >= 0 ) {
                assertEquals(exp_n_new_files, n_new_files);
            }
        }

        Log.d(TAG, "test_n_videos_scanned: " + activity.getApplicationInterface().test_n_videos_scanned);
        if( !allow_failure ) {
            assertEquals(n_new_files-n_non_video_files, activity.getApplicationInterface().test_n_videos_scanned);
        }
    }

    public static void postTakeVideoChecks(MainActivity activity, boolean immersive_mode, boolean max_filesize, int exposureVisibility, int exposureLockVisibility) {
        Preview preview = activity.getPreview();

        assertTrue(preview.isPreviewStarted()); // check preview restarted

        if( preview.usingCamera2API() ) {
            assertNotNull(preview.getCameraController());
            assertEquals(preview.getCurrentPreviewSize().width, preview.getCameraController().test_texture_view_buffer_w);
            assertEquals(preview.getCurrentPreviewSize().height, preview.getCameraController().test_texture_view_buffer_h);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean has_audio_control_button = !sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none").equals("none");

        View takePhotoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo);
        View pauseVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.pause_video);
        View takePhotoVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.take_photo_when_video_recording);
        View switchVideoButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_video);
        View switchCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_camera);
        View switchMultiCameraButton = activity.findViewById(net.sourceforge.opencamera.R.id.switch_multi_camera);
        View exposureButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure);
        View exposureLockButton = activity.findViewById(net.sourceforge.opencamera.R.id.exposure_lock);
        View audioControlButton = activity.findViewById(net.sourceforge.opencamera.R.id.audio_control);
        View popupButton = activity.findViewById(net.sourceforge.opencamera.R.id.popup);
        View trashButton = activity.findViewById(net.sourceforge.opencamera.R.id.trash);
        View shareButton = activity.findViewById(net.sourceforge.opencamera.R.id.share);

        if( !max_filesize ) {
            // if doing restart on max filesize, we may have already restarted by now (on Camera2 API at least)
            Log.d(TAG, "switchCameraButton.getVisibility(): " + switchCameraButton.getVisibility());
            assertEquals(switchCameraButton.getVisibility(), (immersive_mode ? View.GONE : (preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE)));
            assertEquals(switchMultiCameraButton.getVisibility(), (immersive_mode ? View.GONE : (activity.showSwitchMultiCamIcon() ? View.VISIBLE : View.GONE)));
            assertEquals(audioControlButton.getVisibility(), ((has_audio_control_button && !immersive_mode) ? View.VISIBLE : View.GONE));
        }
        assertEquals(switchVideoButton.getVisibility(), (immersive_mode ? View.GONE : View.VISIBLE));
        assertEquals(exposureButton.getVisibility(), exposureVisibility);
        assertEquals(exposureLockButton.getVisibility(), exposureLockVisibility);
        assertEquals(popupButton.getVisibility(), (immersive_mode ? View.GONE : View.VISIBLE));
        // trash/share only shown when preview is paused after taking a photo
        assertEquals(trashButton.getVisibility(), View.GONE);
        assertEquals(shareButton.getVisibility(), View.GONE);

        assertFalse( preview.isVideoRecording() );
        assertEquals((int) (Integer) takePhotoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_video_selector);
        assertEquals((int) (Integer) switchVideoButton.getTag(), net.sourceforge.opencamera.R.drawable.take_photo);
        assertEquals( takePhotoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.start_video) );
        assertEquals(pauseVideoButton.getContentDescription(), activity.getResources().getString(net.sourceforge.opencamera.R.string.pause_video));
        Log.d(TAG, "pauseVideoButton.getVisibility(): " + pauseVideoButton.getVisibility());
        assertEquals(pauseVideoButton.getVisibility(), View.GONE);
        assertEquals(takePhotoVideoButton.getVisibility(), View.GONE);

        assertTrue( preview.getCameraController() == null || preview.getCameraController().count_camera_parameters_exception == 0 );
    }

    public interface VideoTestCallback {
        int doTest(); // return expected number of new files (or -1 to indicate not to check this)
    }
}
