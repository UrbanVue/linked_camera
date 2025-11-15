package com.linkedcamera.app;

/*import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;*/
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
//import android.media.MediaScannerConnection;
//import android.os.Environment;

import androidx.annotation.NonNull;
import android.util.Log;

public class HDRProcessor {
    private static final String TAG = "HDRProcessor";

    private final Context context;
    private final boolean is_test;

    // public for access by testing
    public int [] offsets_x = null;
    public int [] offsets_y = null;
    @SuppressWarnings("CanBeFinal")
    public int sharp_index = 0;

    private enum HDRAlgorithm {
        HDRALGORITHM_STANDARD,
        HDRALGORITHM_SINGLE_IMAGE
    }

    public enum TonemappingAlgorithm {
        TONEMAPALGORITHM_CLAMP,
        TONEMAPALGORITHM_EXPONENTIAL,
        TONEMAPALGORITHM_REINHARD,
        TONEMAPALGORITHM_FU2,
        TONEMAPALGORITHM_ACES
    }
    public static final TonemappingAlgorithm default_tonemapping_algorithm_c = TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD;
    public enum DROTonemappingAlgorithm {
        DROALGORITHM_NONE,
        DROALGORITHM_GAINGAMMA
    }

    public HDRProcessor(Context context, boolean is_test) {
        this.context = context;
        this.is_test = is_test;
    }

    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
    }

    /** Given a set of data Xi and Yi, this function estimates a relation between X and Y
     *  using linear least squares.
     *  We use it to modify the pixels of images taken at the brighter or darker exposure
     *  levels, to estimate what the pixel should be at the "base" exposure.
     *  We estimate as y = parameter_A * x + parameter_B.
     */
    private static class ResponseFunction {
        float parameter_A;
        float parameter_B;

        private ResponseFunction(float parameter_A, float parameter_B) {
            this.parameter_A = parameter_A;
            this.parameter_B = parameter_B;
        }

        static ResponseFunction createIdentity() {
            return new ResponseFunction(1.0f, 0.0f);
        }

        /** Computes the response function.
         * We pass the context, so this inner class can be made static.
         * @param x_samples List of Xi samples. Must be at least 3 samples.
         * @param y_samples List of Yi samples. Must be same length as x_samples.
         * @param weights List of weights. Must be same length as x_samples.
         */
        ResponseFunction(Context context, int id, List<Double> x_samples, List<Double> y_samples, List<Double> weights) {
            if( MyDebug.LOG )
                Log.d(TAG, "ResponseFunction");

            if( x_samples.size() != y_samples.size() ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "unequal number of samples");
                // throw RuntimeException, as this is a programming error
                throw new RuntimeException();
            }
            else if( x_samples.size() != weights.size() ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "unequal number of samples");
                // throw RuntimeException, as this is a programming error
                throw new RuntimeException();
            }
            else if( x_samples.size() <= 3 ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "not enough samples");
                // throw RuntimeException, as this is a programming error
                throw new RuntimeException();
            }

            // linear Y = AX + B
            boolean done = false;
            double sum_wx = 0.0;
            double sum_wx2 = 0.0;
            double sum_wxy = 0.0;
            double sum_wy = 0.0;
            double sum_w = 0.0;
            for(int i=0;i<x_samples.size();i++) {
                double x = x_samples.get(i);
                double y = y_samples.get(i);
                double w = weights.get(i);
                sum_wx += w * x;
                sum_wx2 += w * x * x;
                sum_wxy += w * x * y;
                sum_wy += w * y;
                sum_w += w;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "sum_wx = " + sum_wx);
                Log.d(TAG, "sum_wx2 = " + sum_wx2);
                Log.d(TAG, "sum_wxy = " + sum_wxy);
                Log.d(TAG, "sum_wy = " + sum_wy);
                Log.d(TAG, "sum_w = " + sum_w);
            }
            // need to solve:
            // A . sum_wx + B . sum_w - sum_wy = 0
            // A . sum_wx2 + B . sum_wx - sum_wxy = 0
            // =>
            // A . sum_wx^2 + B . sum_w . sum_wx - sum_wy . sum_wx = 0
            // A . sum_w . sum_wx2 + B . sum_w . sum_wx - sum_w . sum_wxy = 0
            // A ( sum_wx^2 - sum_w . sum_wx2 ) = sum_wy . sum_wx - sum_w . sum_wxy
            // then plug A into:
            // B . sum_w = sum_wy - A . sum_wx
            double A_numer = sum_wy * sum_wx - sum_w * sum_wxy;
            double A_denom = sum_wx * sum_wx - sum_w * sum_wx2;
            if( MyDebug.LOG ) {
                Log.d(TAG, "A_numer = " + A_numer);
                Log.d(TAG, "A_denom = " + A_denom);
            }
            if( Math.abs(A_denom) < 1.0e-5 ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "denom too small");
                // will fall back to linear Y = AX
            }
            else {
                parameter_A = (float)(A_numer / A_denom);
                parameter_B = (float)((sum_wy - parameter_A * sum_wx) / sum_w);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "parameter_A = " + parameter_A);
                    Log.d(TAG, "parameter_B = " + parameter_B);
                }
                // we don't want a function that is not monotonic, or can be negative!
                if( parameter_A < 1.0e-5 ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "parameter A too small or negative: " + parameter_A);
                }
                else if( parameter_B < 1.0e-5 ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "parameter B too small or negative: " + parameter_B);
                }
                else {
                    done = true;
                }
            }

            if( !done ) {
                if( MyDebug.LOG )
                    Log.e(TAG, "falling back to linear Y = AX");
                // linear Y = AX
                double numer = 0.0;
                double denom = 0.0;
                for(int i=0;i<x_samples.size();i++) {
                    double x = x_samples.get(i);
                    double y = y_samples.get(i);
                    double w = weights.get(i);
                    numer += w*x*y;
                    denom += w*x*x;
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "numer = " + numer);
                    Log.d(TAG, "denom = " + denom);
                }

                if( denom < 1.0e-5 ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "denom too small");
                    parameter_A = 1.0f;
                }
                else {
                    parameter_A = (float)(numer / denom);
                    // we don't want a function that is not monotonic!
                    if( parameter_A < 1.0e-5 ) {
                        if( MyDebug.LOG )
                            Log.e(TAG, "parameter A too small or negative: " + parameter_A);
                        parameter_A = 1.0e-5f;
                    }
                }
                parameter_B = 0.0f;
            }

            if( MyDebug.LOG ) {
                Log.d(TAG, "parameter_A = " + parameter_A);
                Log.d(TAG, "parameter_B = " + parameter_B);
            }

            /*if( MyDebug.LOG ) {
                // log samples to a CSV file
                File file = new File(context.getExternalFilesDir(null).getPath() + "/net.sourceforge.opencamera.hdr_samples_" + id + ".csv");
                if( file.exists() ) {
                    if( !file.delete() ) {
                        // keep FindBugs happy by checking return argument
                        Log.e(TAG, "failed to delete csv file");
                    }
                }
                FileWriter writer = null;
                try {
                    writer = new FileWriter(file);
                    //writer.append("Parameter," + parameter + "\n");
                    writer.append("Parameters,").append(String.valueOf(parameter_A)).append(",").append(String.valueOf(parameter_B)).append("\n");
                    writer.append("X,Y,Weight\n");
                    for(int i=0;i<x_samples.size();i++) {
                        //Log.d(TAG, "log: " + i + " / " + x_samples.size());
                        double x = x_samples.get(i);
                        double y = y_samples.get(i);
                        double w = weights.get(i);
                        writer.append(String.valueOf(x)).append(",").append(String.valueOf(y)).append(",").append(String.valueOf(w)).append("\n");
                    }
                }
                catch (IOException e) {
                    MyDebug.logStackTrace(TAG, "failed to open csv file", e);
                }
                finally {
                    try {
                        if( writer != null )
                            writer.close();
                    }
                    catch (IOException e) {
                        MyDebug.logStackTrace(TAG, "failed to close csv file", e);
                    }
                }
                MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, null);
            }*/
        }
    }

    @SuppressWarnings("WeakerAccess")
    public interface SortCallback {
        /** This is called when the sort order for the input bitmaps is known, from darkest to brightest.
         * @param sort_order A list of length equal to the supplied bitmaps.size(). sort_order.get(i)
         *                   returns the index in the bitmaps array of the i-th image after sorting,
         *                   where i==0 represents the darkest image, and i==bitmaps.size()-1 is the
         *                   brightest.
         */
        void sortOrder(List<Integer> sort_order);
    }

    /** Converts a list of bitmaps into a HDR image, which is then tonemapped to a final RGB image.
     * @param bitmaps The list of bitmaps, which should be in order of increasing brightness (exposure).
     *                Currently only supports a list of either 1 image, or 3 images (the 2nd should be
     *                at the desired exposure level for the resultant image).
     *                The bitmaps must all be the same resolution.
     * @param release_bitmaps If true, the resultant image will be stored in one of the input bitmaps.
     *                        The bitmaps array will be updated so that the first entry will contain
     *                        the output bitmap. If assume_sorted is true, this will be equal to the
     *                        input bitmaps.get( (bitmaps.size()-1) / 2). The remainder bitmaps will have
     *                        recycle() called on them.
     *                        If false, the resultant image is copied to output_bitmap.
     * @param output_bitmap If release_bitmaps is false, the resultant image is stored in this bitmap.
     *                      If release_bitmaps is true, this parameter is ignored.
     * @param assume_sorted If true, the input bitmaps should be sorted in order from darkest to brightest
     *                      exposure. If false, the function will automatically resort.
     * @param sort_cb       If assume_sorted is false and this is non-null, sort_cb.sortOrder() will be
     *                      called to indicate the sort order when this is known.
     * @param hdr_alpha     A value from 0.0f to 1.0f indicating the "strength" of the HDR effect. Specifically,
     *                      this controls the level of the local contrast enhancement done in adjustHistogram().
     * @param n_tiles       A value of 1 or greater indicating how local the contrast enhancement algorithm should be.
     * @param ce_preserve_blacks
     *                      If true (recommended), then we apply a modification to the contrast enhancement algorithm to avoid
     *                      making darker pixels too dark. A value of false gives more contrast on the darker regions of the
     *                      resultant image.
     * @param tonemapping_algorithm
     *                      Algorithm to use for tonemapping (if multiple images are received).
     * @param dro_tonemapping_algorithm
     *                      Algorithm to use for tonemapping (if single image is received).
     */
    public void processHDR(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, boolean assume_sorted, SortCallback sort_cb, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, TonemappingAlgorithm tonemapping_algorithm, DROTonemappingAlgorithm dro_tonemapping_algorithm) throws HDRProcessorException {
        if( MyDebug.LOG )
            Log.d(TAG, "processHDR");
        if( !assume_sorted && !release_bitmaps ) {
            if( MyDebug.LOG )
                Log.d(TAG, "take a copy of bitmaps array");
            // if !release_bitmaps, then we shouldn't be modifying the input bitmaps array - but if !assume_sorted, we need to sort them
            // so make sure we take a copy
            bitmaps = new ArrayList<>(bitmaps);
        }
        int n_bitmaps = bitmaps.size();
        //if( n_bitmaps != 1 && n_bitmaps != 3 && n_bitmaps != 5 && n_bitmaps != 7 ) {
        if( n_bitmaps < 1 || n_bitmaps > 7 ) {
            if( MyDebug.LOG )
                Log.e(TAG, "n_bitmaps not supported: " + n_bitmaps);
            throw new HDRProcessorException(HDRProcessorException.INVALID_N_IMAGES);
        }
        for(int i=1;i<n_bitmaps;i++) {
            if( bitmaps.get(i).getWidth() != bitmaps.get(0).getWidth() ||
                    bitmaps.get(i).getHeight() != bitmaps.get(0).getHeight() ) {
                if( MyDebug.LOG ) {
                    Log.e(TAG, "bitmaps not of same resolution");
                    for(int j=0;j<n_bitmaps;j++) {
                        Log.e(TAG, "bitmaps " + j + " : " + bitmaps.get(j).getWidth() + " x " + bitmaps.get(j).getHeight());
                    }
                }
                throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
            }
        }

        final HDRAlgorithm algorithm = n_bitmaps == 1 ? HDRAlgorithm.HDRALGORITHM_SINGLE_IMAGE : HDRAlgorithm.HDRALGORITHM_STANDARD;

        switch( algorithm ) {
            case HDRALGORITHM_SINGLE_IMAGE:
                if( !assume_sorted && sort_cb != null ) {
                    List<Integer> sort_order = new ArrayList<>();
                    sort_order.add(0);
                    sort_cb.sortOrder(sort_order);
                }
                processSingleImage(bitmaps, release_bitmaps, output_bitmap, hdr_alpha, n_tiles, ce_preserve_blacks, dro_tonemapping_algorithm);
                break;
            case HDRALGORITHM_STANDARD:
                processHDRCore(bitmaps, release_bitmaps, output_bitmap, assume_sorted, sort_cb, hdr_alpha, n_tiles, ce_preserve_blacks, tonemapping_algorithm);
                break;
            default:
                if( MyDebug.LOG )
                    Log.e(TAG, "unknown algorithm " + algorithm);
                // throw RuntimeException, as this is a programming error
                throw new RuntimeException();
        }
    }

    /** Creates a ResponseFunction to estimate how pixels from the in_bitmap should be adjusted to
     *  match the exposure level of out_bitmap.
     *  The supplied offsets offset_x, offset_y give the offset for in_bitmap as computed by
     *  autoAlignment().
     */
    private ResponseFunction createFunctionFromBitmaps(int id, Bitmap in_bitmap, Bitmap out_bitmap, int offset_x, int offset_y) {
        if( MyDebug.LOG )
            Log.d(TAG, "createFunctionFromBitmaps");
        List<Double> x_samples = new ArrayList<>();
        List<Double> y_samples = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        final int n_samples_c = 100;
        final int n_w_samples = (int)Math.sqrt(n_samples_c);
        final int n_h_samples = n_samples_c/n_w_samples;

        double avg_in = 0.0;
        double avg_out = 0.0;
        for(int y=0;y<n_h_samples;y++) {
            double alpha = ((double)y+1.0) / ((double)n_h_samples+1.0);
            int y_coord = (int)(alpha * in_bitmap.getHeight());
            for(int x=0;x<n_w_samples;x++) {
                double beta = ((double)x+1.0) / ((double)n_w_samples+1.0);
                int x_coord = (int)(beta * in_bitmap.getWidth());
                /*if( MyDebug.LOG )
                    Log.d(TAG, "sample response from " + x_coord + " , " + y_coord);*/
                if( x_coord + offset_x < 0 || x_coord + offset_x >= in_bitmap.getWidth() || y_coord + offset_y < 0 || y_coord + offset_y >= in_bitmap.getHeight() ) {
                    continue;
                }
                int in_col = in_bitmap.getPixel(x_coord + offset_x, y_coord + offset_y);
                int out_col = out_bitmap.getPixel(x_coord, y_coord);
                double in_value = averageRGB(in_col);
                double out_value = averageRGB(out_col);
                avg_in += in_value;
                avg_out += out_value;
                x_samples.add(in_value);
                y_samples.add(out_value);
            }
        }
        if( x_samples.isEmpty() )  {
            Log.e(TAG, "no samples for response function!");
            // shouldn't happen, but could do with a very large offset - just make up a dummy sample
            double in_value = 255.0;
            double out_value = 255.0;
            avg_in += in_value;
            avg_out += out_value;
            x_samples.add(in_value);
            y_samples.add(out_value);
        }
        avg_in /= x_samples.size();
        avg_out /= x_samples.size();
        boolean is_dark_exposure = avg_in < avg_out;
        if( MyDebug.LOG ) {
            Log.d(TAG, "avg_in: " + avg_in);
            Log.d(TAG, "avg_out: " + avg_out);
            Log.d(TAG, "is_dark_exposure: " + is_dark_exposure);
        }
        {
            // calculate weights
            double min_value = x_samples.get(0);
            double max_value = x_samples.get(0);
            for(int i=1;i<x_samples.size();i++) {
                double value = x_samples.get(i);
                if( value < min_value )
                    min_value = value;
                if( value > max_value )
                    max_value = value;
            }
            double med_value = 0.5*(min_value + max_value);
            if( MyDebug.LOG ) {
                Log.d(TAG, "min_value: " + min_value);
                Log.d(TAG, "max_value: " + max_value);
                Log.d(TAG, "med_value: " + med_value);
            }
            double min_value_y = y_samples.get(0);
            double max_value_y = y_samples.get(0);
            for(int i=1;i<y_samples.size();i++) {
                double value = y_samples.get(i);
                if( value < min_value_y )
                    min_value_y = value;
                if( value > max_value_y )
                    max_value_y = value;
            }
            double med_value_y = 0.5*(min_value_y + max_value_y);
            if( MyDebug.LOG ) {
                Log.d(TAG, "min_value_y: " + min_value_y);
                Log.d(TAG, "max_value_y: " + max_value_y);
                Log.d(TAG, "med_value_y: " + med_value_y);
            }
            for(int i=0;i<x_samples.size();i++) {
                double value = x_samples.get(i);
                double value_y = y_samples.get(i);
                double weight = (value <= med_value) ? value - min_value : max_value - value;
                if( is_dark_exposure ) {
                    // for dark exposure, also need to worry about the y values (which will be brighter than x) being overexposed
                    double weight_y = (value_y <= med_value_y) ? value_y - min_value_y : max_value_y - value_y;
                    if( weight_y < weight )
                        weight = weight_y;
                }
                weights.add(weight);
            }
        }

        return new ResponseFunction(context, id, x_samples, y_samples, weights);
    }

    /** Calculates average of RGB values for the supplied color.
     */
    private double averageRGB(int color) {
        int r = (color & 0xFF0000) >> 16;
        int g = (color & 0xFF00) >> 8;
        int b = (color & 0xFF);
        return (r + g + b)/3.0;
        //return 0.27*r + 0.67*g + 0.06*b;
    }

    /** Core implementation of HDR algorithm.
     */
    private void processHDRCore(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, boolean assume_sorted, SortCallback sort_cb, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, TonemappingAlgorithm tonemapping_algorithm) {
        if( MyDebug.LOG )
            Log.d(TAG, "processHDRCore");

        long time_s = System.currentTimeMillis();

        int n_bitmaps = bitmaps.size();
        int width = bitmaps.get(0).getWidth();
        int height = bitmaps.get(0).getHeight();
        ResponseFunction [] response_functions = new ResponseFunction[n_bitmaps]; // ResponseFunction for each image (the ResponseFunction entry can be left null to indicate the Identity)
        offsets_x = new int[n_bitmaps];
        offsets_y = new int[n_bitmaps];
        /*int [][] buffers = new int[n_bitmaps][];
        for(int i=0;i<n_bitmaps;i++) {
            buffers[i] = new int[bm.getWidth()];
        }*/
        //float [] hdr = new float[3];
        //int [] rgb = new int[3];

        //final boolean use_hdr_n = true; // test always using hdr_n
        final boolean use_hdr_n = n_bitmaps != 3;

        //final int base_bitmap = (n_bitmaps - 1) / 2; // index of the bitmap with the base exposure and offsets
        final int base_bitmap = n_bitmaps % 2 == 0 ? n_bitmaps/2 : (n_bitmaps - 1) / 2; // index of the bitmap with the base exposure and offsets
        // for even number of images, round up to brighter image

        // perform auto-alignment
        // if assume_sorted if false, this function will also sort the bitmaps from darkest to brightest.
        BrightnessDetails brightnessDetails = autoAlignment(offsets_x, offsets_y, width, height, bitmaps, base_bitmap, assume_sorted, sort_cb, true, 1, true, 1, width, height, time_s);
        int median_brightness = brightnessDetails.median_brightness;
        if( MyDebug.LOG ) {
            Log.d(TAG, "### time after autoAlignment: " + (System.currentTimeMillis() - time_s));
            Log.d(TAG, "median_brightness: " + median_brightness);
        }

        // compute response_functions
        for(int i=0;i<n_bitmaps;i++) {
            ResponseFunction function = null;
            if( i != base_bitmap ) {
                function = createFunctionFromBitmaps(i, bitmaps.get(i), bitmaps.get(base_bitmap), offsets_x[i], offsets_y[i]);
            }
            else if( use_hdr_n ) {
                // for hdr_n, need to still create the identity response function
                function = ResponseFunction.createIdentity();
            }
            response_functions[i] = function;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "### time after creating response functions: " + (System.currentTimeMillis() - time_s));

        if( n_bitmaps % 2 == 0 ) {
            // need to remap so that we aim for a brightness between the middle two images
            float a = (float)Math.sqrt(response_functions[base_bitmap-1].parameter_A);
            float b = response_functions[base_bitmap-1].parameter_B / (a+1.0f);
            if( MyDebug.LOG ) {
                Log.d(TAG, "remap for even number of images");
                Log.d(TAG, "    a: " + a);
                Log.d(TAG, "    b: " + b);
            }
            if( a < 1.0e-5f ) {
                // avoid risk of division by 0
                a = 1.0e-5f;
                if( MyDebug.LOG )
                    Log.e(TAG, "    clamp a to: " + a);
            }
            for(int i=0;i<n_bitmaps;i++) {
                float this_A = response_functions[i].parameter_A;
                float this_B = response_functions[i].parameter_B;
                response_functions[i].parameter_A = this_A / a;
                response_functions[i].parameter_B = this_B - this_A * b / a;
                if( response_functions[i].parameter_B < 1.0e-5f ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "remapped parameter B too small or negative: " + response_functions[i].parameter_B);
                    response_functions[i].parameter_B = 1.0e-5f;
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "remapped: " + i);
                    Log.d(TAG, "    A: " + this_A + " -> " + response_functions[i].parameter_A);
                    Log.d(TAG, "    B: " + this_B + " -> " + response_functions[i].parameter_B);
                }
            }
        }

        /*
        // calculate average luminance by sampling
        final int n_samples_c = 100;
        final int n_w_samples = (int)Math.sqrt(n_samples_c);
        final int n_h_samples = n_samples_c/n_w_samples;

        double sum_log_luminance = 0.0;
        int count = 0;
        for(int y=0;y<n_h_samples;y++) {
            double alpha = ((double)y+1.0) / ((double)n_h_samples+1.0);
            int y_coord = (int)(alpha * bm.getHeight());
            for(int i=0;i<n_bitmaps;i++) {
                bitmaps.get(i).getPixels(buffers[i], 0, bm.getWidth(), 0, y_coord, bm.getWidth(), 1);
            }
            for(int x=0;x<n_w_samples;x++) {
                double beta = ((double)x+1.0) / ((double)n_w_samples+1.0);
                int x_coord = (int)(beta * bm.getWidth());
                if( MyDebug.LOG )
                    Log.d(TAG, "sample luminance from " + x_coord + " , " + y_coord);
                calculateHDR(hdr, n_bitmaps, buffers, x_coord, response_functions);
                double luminance = calculateLuminance(hdr[0], hdr[1], hdr[2]) + 1.0; // add 1 so we don't take log of 0..;
                sum_log_luminance += Math.log(luminance);
                count++;
            }
        }
        float avg_luminance = (float)(Math.exp( sum_log_luminance / count ));
        if( MyDebug.LOG )
            Log.d(TAG, "avg_luminance: " + avg_luminance);
        if( MyDebug.LOG )
            Log.d(TAG, "time after calculating average luminance: " + (System.currentTimeMillis() - time_s));
            */

        // write new hdr image

        float max_possible_value = response_functions[0].parameter_A * 255 + response_functions[0].parameter_B;
        //float max_possible_value = response_functions[base_bitmap - 1].parameter_A * 255 + response_functions[base_bitmap - 1].parameter_B;
        if( MyDebug.LOG )
            Log.d(TAG, "max_possible_value: " + max_possible_value);
        if( max_possible_value < 255.0f ) {
            max_possible_value = 255.0f; // don't make dark images too bright, see below about linear_scale for more details
            if( MyDebug.LOG )
                Log.d(TAG, "clamp max_possible_value to: " + max_possible_value);
        }

        //hdr_alpha = 0.0f; // test
        //final float tonemap_scale_c = avg_luminance / 0.8f; // lower values tend to result in too dark pictures; higher values risk over exposed bright areas
        //final float tonemap_scale_c = 255.0f;
        //final float tonemap_scale_c = 255.0f - median_brightness;
        float tonemap_scale_c = 255.0f;

        int median_target = getBrightnessTarget(median_brightness, 2, 119);

        if( MyDebug.LOG ) {
            Log.d(TAG, "median_target: " + median_target);
            Log.d(TAG, "compare: " + 255.0f / max_possible_value);
            Log.d(TAG, "to: " + (((float)median_target)/(float)median_brightness + median_target / 255.0f - 1.0f));
        }
        if( 255.0f / max_possible_value < ((float)median_target)/(float)median_brightness + median_target / 255.0f - 1.0f ) {
            // For Reinhard tonemapping:
            // As noted below, we have f(V) = V.S / (V+C), where V is the HDR value, C is tonemap_scale_c
            // and S = (Vmax + C)/Vmax (see below)
            // Ideally we try to choose C such that we map median value M to target T:
            // f(M) = T
            // => T = M . (Vmax + C) / (Vmax . (M + C))
            // => (T/M).(M + C) = (Vmax + C) / Vmax = 1 + C/Vmax
            // => C . ( T/M - 1/Vmax ) = 1 - T
            // => C = (1-T) / (T/M - 1/Vmax)
            // Since we want C <= 1, we must have:
            // 1-T <= T/M - 1/Vmax
            // => 1/Vmax <= T/M + T - 1
            // If this isn't the case, we set C to 1 (to preserve the median as close as possible).
            // Note that if we weren't doing the linear scaling below, this would reduce to choosing
            // C = M(1-T)/T. We also tend to that as max_possible_value tends to infinity. So even though
            // we only sometimes enter this case, it's important for cases where max_possible_value
            // might be estimated too large (also consider that if we ever support more than 3 images,
            // we'd risk having too large values).
            // If T=M, then this simplifies to C = 1-M.
            // I've tested that using "C = 1-M" always (and no linear scaling) also gives good results:
            // much better compared to Open Camera 1.39, though not quite as good as doing both this
            // and linear scaling (testHDR18, testHDR26, testHDR32 look too grey and/or bright).
            final float tonemap_denom = ((float)median_target)/(float)median_brightness - (255.0f / max_possible_value);
            if( MyDebug.LOG )
                Log.d(TAG, "tonemap_denom: " + tonemap_denom);
            if( tonemap_denom != 0.0f ) { // just in case
                tonemap_scale_c = (255.0f - median_target) / tonemap_denom;
                if( MyDebug.LOG )
                    Log.d(TAG, "tonemap_scale_c (before setting min): " + tonemap_scale_c);
                /*if( tonemap_scale_c < 0.5f*255.0f ) {
                    throw new RuntimeException("tonemap_scale_c: " + tonemap_scale_c);
                }*/
                // important to set a min value, see testHDR58, testHDR59, testHDR60 - at least 0.25, but 0.5 works better:
                //tonemap_scale_c = Math.max(tonemap_scale_c, 0.25f*255.0f);
                tonemap_scale_c = Math.max(tonemap_scale_c, 0.5f*255.0f);
            }
            //throw new RuntimeException(); // test
        }
        // Higher tonemap_scale_c values means darker results from the Reinhard tonemapping.
        // Colours brighter than 255-tonemap_scale_c will be made darker, colours darker than 255-tonemap_scale_c will be made brighter
        // (tonemap_scale_c==255 means therefore that colours will only be made darker).
        if( MyDebug.LOG )
            Log.d(TAG, "tonemap_scale_c: " + tonemap_scale_c);

        // algorithm specific parameters
        float linear_scale = 0.0f;
        float W = 0.0f;
        switch( tonemapping_algorithm ) {
            case TONEMAPALGORITHM_EXPONENTIAL:
            {
                // The basic algorithm is f(V) = 1 - exp( - E * V ), where V is the HDR value, E is a
                // constant. This maps [0, infinity] to [0, 1]. However we have an estimate of the maximum
                // possible value, Vmax, so we can set a linear scaling S so that [0, Vmax] maps to [0, 1]
                // f(V) = S . (1 - exp( - E * V ))
                // so 1 = S . (1 - exp( - E * Vmax ))
                // => S = 1 / (1 - exp( - E * Vmax ))
                // Note that Vmax should be set to a minimum of 255, else we'll make darker images brighter.
                final float exposure = 1.2f; // should match setting in process_hdr.rs
                linear_scale = (float)(1.0 / (1.0 - Math.exp(-exposure * max_possible_value / 255.0)));
                if( MyDebug.LOG )
                    Log.d(TAG, "linear_scale: " + linear_scale);
                break;
            }
            case TONEMAPALGORITHM_REINHARD: {
                // The basic algorithm is f(V) = V / (V+C), where V is the HDR value, C is tonemap_scale_c
                // This was used until Open Camera 1.39, but has the problem of making images too dark: it
                // maps [0, infinity] to [0, 1], but since in practice we never have very large V values, we
                // won't use the full [0, 1] range. So we apply a linear scale S:
                // f(V) = V.S / (V+C)
                // S is chosen such that the maximum possible value, Vmax, maps to 1. So:
                // 1 = Vmax . S / (Vmax + C)
                // => S = (Vmax + C)/Vmax
                // Note that we don't actually know the maximum HDR value, but instead we estimate it with
                // max_possible_value, which gives the maximum value we'd have if even the darkest image was
                // 255.0.
                // Note that if max_possible_value was less than 255, we'd end up scaling a max value less than
                // 1, to [0, 1], i.e., making dark images brighter, which we don't want, which is why above we
                // set max_possible_value to a minimum of 255. In practice, this is unlikely to ever happen
                // since max_possible_value is calculated as a maximum possible based on the response functions
                // (as opposed to the real brightest HDR value), so even for dark photos we'd expect to have
                // max_possible_value >= 255.
                // Note that the original Reinhard tonemapping paper describes a non-linear scaling by (1 + CV/Vmax^2),
                // though this is poorer performance (in terms of calculation time).
                linear_scale = (max_possible_value + tonemap_scale_c) / max_possible_value;
                if( MyDebug.LOG )
                    Log.d(TAG, "linear_scale: " + linear_scale);
                break;
            }
            case TONEMAPALGORITHM_FU2:
            {
                // For FU2, we have f(V) = U(EV) / U(W), where V is the HDR value, U is a function.
                // We want f(Vmax) = 1, so EVmax = W
                final float fu2_exposure_bias = 2.0f / 255.0f; // should match setting in process_hdr.rs
                W = fu2_exposure_bias * max_possible_value;
                if( MyDebug.LOG )
                    Log.d(TAG, "fu2 W: " + W);
                break;
            }
        }

        {
            if( release_bitmaps ) {
                output_bitmap = bitmaps.get(base_bitmap);
            }

            float [] parameters_A = new float[response_functions.length];
            float [] parameters_B = new float[response_functions.length];
            for(int i=0;i<response_functions.length;i++) {
                if( response_functions[i] != null ) {
                    parameters_A[i] = response_functions[i].parameter_A;
                    parameters_B[i] = response_functions[i].parameter_B;
                }
                else {
                    parameters_A[i] = 1.0f;
                    parameters_B[i] = 0.0f;
                }
            }
            if( MyDebug.LOG )
                Log.d(TAG, "### time before HDRApplyFunction: " + (System.currentTimeMillis() - time_s));

            JavaImageFunctions.HDRApplyFunction function;
            if( use_hdr_n ) {
                function = new JavaImageFunctions.HDRNApplyFunction(tonemapping_algorithm, tonemap_scale_c, W, linear_scale, bitmaps, offsets_x, offsets_y, width, height, parameters_A, parameters_B);
            }
            else {
                function = new JavaImageFunctions.HDRApplyFunction(tonemapping_algorithm, tonemap_scale_c, W, linear_scale, bitmaps.get(0), bitmaps.get(2), offsets_x[0], offsets_y[0], offsets_x[2], offsets_y[2], width, height, parameters_A, parameters_B);
            }

            JavaImageProcessing.applyFunction(function, bitmaps.get(base_bitmap), output_bitmap, 0, 0, width, height);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after HDRApplyFunction: " + (System.currentTimeMillis() - time_s));

            if( hdr_alpha != 0.0f ) {
                adjustHistogram(output_bitmap, output_bitmap, width, height, hdr_alpha, n_tiles, ce_preserve_blacks, time_s);
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after adjustHistogram: " + (System.currentTimeMillis() - time_s));
            }
        }

        if( release_bitmaps ) {
            // make it so that we store the output bitmap as first in the list
            bitmaps.set(0, output_bitmap);
            for(int i=1;i<bitmaps.size();i++) {
                bitmaps.set(i, null);
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processHDRCore: " + (System.currentTimeMillis() - time_s));
    }

    private void processSingleImage(List<Bitmap> bitmaps, boolean release_bitmaps, Bitmap output_bitmap, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, DROTonemappingAlgorithm dro_tonemapping_algorithm) {
        if( MyDebug.LOG )
            Log.d(TAG, "processSingleImage");

        long time_s = System.currentTimeMillis();

        int width = bitmaps.get(0).getWidth();
        int height = bitmaps.get(0).getHeight();

        Bitmap input_bitmap = bitmaps.get(0);

        if( release_bitmaps ) {
            output_bitmap = input_bitmap;
        }

        if( dro_tonemapping_algorithm == DROTonemappingAlgorithm.DROALGORITHM_GAINGAMMA ) {
            // brighten?
            int [] histo = computeHistogram(input_bitmap, HDRProcessor.HistogramType.HISTOGRAM_TYPE_VALUE);
            HistogramInfo histogramInfo = getHistogramInfo(histo);
            int brightness = histogramInfo.median_brightness;
            int max_brightness = histogramInfo.max_brightness;
            if( MyDebug.LOG )
                Log.d(TAG, "### processSingleImage: time after computeHistogram: " + (System.currentTimeMillis() - time_s));
            if( MyDebug.LOG ) {
                Log.d(TAG, "median brightness: " + brightness);
                Log.d(TAG, "max brightness: " + max_brightness);
            }
            BrightenFactors brighten_factors = computeBrightenFactors(false, 0, 0, brightness, max_brightness);
            float gain = brighten_factors.gain;
            float gamma = brighten_factors.gamma;
            float low_x = brighten_factors.low_x;
            float mid_x = brighten_factors.mid_x;
            if( MyDebug.LOG ) {
                Log.d(TAG, "gain: " + gain);
                Log.d(TAG, "gamma: " + gamma);
                Log.d(TAG, "low_x: " + low_x);
                Log.d(TAG, "mid_x: " + mid_x);
            }

            if( Math.abs(gain - 1.0) > 1.0e-5 || max_brightness != 255 || Math.abs(gamma - 1.0) > 1.0e-5 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "apply gain/gamma");
                //if( true )
                //  throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES); // test

                JavaImageFunctions.DROBrightenApplyFunction function = new JavaImageFunctions.DROBrightenApplyFunction(gain, gamma, low_x, mid_x, max_brightness);
                JavaImageProcessing.applyFunction(function, input_bitmap, output_bitmap, 0, 0, width, height);

                // output is now the input for subsequent operations
                input_bitmap = output_bitmap;
                if( MyDebug.LOG )
                    Log.d(TAG, "### processSingleImage: time after dro_brighten: " + (System.currentTimeMillis() - time_s));
            }
        }

        adjustHistogram(input_bitmap, output_bitmap, width, height, hdr_alpha, n_tiles, ce_preserve_blacks, time_s);

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processSingleImage: " + (System.currentTimeMillis() - time_s));
    }

    void brightenImage(Bitmap bitmap, int brightness, int max_brightness, int brightness_target) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "brightenImage");
            Log.d(TAG, "brightness: " + brightness);
            Log.d(TAG, "max_brightness: " + max_brightness);
            Log.d(TAG, "brightness_target: " + brightness_target);
        }
        BrightenFactors brighten_factors = computeBrightenFactors(false, 0, 0, brightness, max_brightness, brightness_target, false);
        float gain = brighten_factors.gain;
        float gamma = brighten_factors.gamma;
        float low_x = brighten_factors.low_x;
        float mid_x = brighten_factors.mid_x;
        if( MyDebug.LOG ) {
            Log.d(TAG, "gain: " + gain);
            Log.d(TAG, "gamma: " + gamma);
            Log.d(TAG, "low_x: " + low_x);
            Log.d(TAG, "mid_x: " + mid_x);
        }

        if( Math.abs(gain - 1.0) > 1.0e-5 || max_brightness != 255 || Math.abs(gamma - 1.0) > 1.0e-5 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "apply gain/gamma");

            JavaImageFunctions.DROBrightenApplyFunction function = new JavaImageFunctions.DROBrightenApplyFunction(gain, gamma, low_x, mid_x, max_brightness);
            JavaImageProcessing.applyFunction(function, bitmap, bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight());
        }
    }

    public static boolean sceneIsLowLight(int iso, long exposure_time) {
        final int ISO_FOR_DARK = 1100;
        // For Nexus 6, max reported ISO is 1196, so the limit for dark scenes shouldn't be more than this
        // Nokia 8's max reported ISO is 1551
        // Note that OnePlus 3T has max reported ISO of 800, but this is a device bug
        // The addition of the iso*exposure_time helps behaviour on Galaxy S10e which uses ISO >= 1600
        // far more often, even for non-dark scenes. Potentially we could drop the requirement for
        // "iso >= ISO_FOR_DARK" and instead have iso*exposure_time >= 91 to 115, but we need the
        // dedicated iso check for Nexus 6 (iso 1196 exposure time 1/12s should be dark) and
        // Nokia 8 testAvg23 (iso 1044 exposure time 0.1s shouldn't be dark).
        // We also assume dark for long exposure times (which in practice is probably set in
        // manual mode) - since long exposure times will give lower ISOs (e.g., on Galaxy S10e)
        // (also useful for cameras where max ISO isn't as high as ISO_FOR_DARK)
        //return iso >= ISO_FOR_DARK;
        return ( iso >= ISO_FOR_DARK && iso*exposure_time >= 69*1000000000L ) || exposure_time >= (1000000000L/5-10000L);
    }

    private int cached_avg_sample_size = 1;

    /** As part of the noise reduction process, the caller should scale the input images down by the factor returned
     *  by this method. This both provides a spatial smoothing, as well as improving performance and memory usage.
     */
    public int getAvgSampleSize(int capture_result_iso, long capture_result_exposure_tim) {
        // If changing this, may also want to change the radius of the spatial filter in avg_brighten.rs ?
        //this.cached_avg_sample_size = (n_images>=8) ? 2 : 1;
        this.cached_avg_sample_size = sceneIsLowLight(capture_result_iso, capture_result_exposure_tim) ? 2 : 1;
        //this.cached_avg_sample_size = 1;
        //this.cached_avg_sample_size = 2;
        if( MyDebug.LOG )
            Log.d(TAG, "getAvgSampleSize: " + cached_avg_sample_size);
        return cached_avg_sample_size;
    }

    public int getAvgSampleSize() {
        return cached_avg_sample_size;
    }

    public static class AvgData {
        float [] pixels_rgbf_out;
        Bitmap bitmap_avg_align;
        Bitmap bitmap_orig; // first bitmap, need to keep until all images are processed

        AvgData(float [] pixels_rgbf_out, Bitmap bitmap_avg_align, Bitmap bitmap_orig) {
            this.pixels_rgbf_out = pixels_rgbf_out;
            this.bitmap_avg_align = bitmap_avg_align;
            this.bitmap_orig = bitmap_orig;
        }

        public void destroy() {
            if( MyDebug.LOG )
                Log.d(TAG, "AvgData.destroy()");
            pixels_rgbf_out = null;
            if( bitmap_avg_align != null ) {
                bitmap_avg_align.recycle();
                bitmap_avg_align = null;
            }
            if( bitmap_orig != null ) {
                bitmap_orig.recycle();
                bitmap_orig = null;
            }
        }
    }

    /** Combines two images by averaging them. Each pixel of bitmap_avg is modified to contain:
     *      (avg_factor * bitmap_avg + bitmap_new)/(avg_factor+1)
     *  A simple average is therefore obtained by calling this function with avg_factor = 1.0f.
     *  For averaging multiple images, first call this function with avg_factor 1.0 for the first
     *  two images, then call updateAvg() for subsequent images, increasing avg_factor by 1.0 each
     *  time.
     *  The reason we do it this way (rather than just receiving a list of bitmaps) is so that we
     *  can average multiple images without having to keep them all in memory simultaneously.
     * @param bitmap_avg     One of the input images. The bitmap is recycled.
     * @param bitmap_new     The other input image. The bitmap is recycled.
     * @param avg_factor     The weighting factor for bitmap_avg.
     * @param iso            The ISO used to take the photos.
     * @param exposure_time  The exposure time used to take the photos.
     * @param zoom_factor    The digital zoom factor used to take the photos.
     */
    public AvgData processAvg(Bitmap bitmap_avg, Bitmap bitmap_new, float avg_factor, int iso, long exposure_time, float zoom_factor) throws HDRProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "processAvg");
            Log.d(TAG, "avg_factor: " + avg_factor);
        }
        if( bitmap_avg.getWidth() != bitmap_new.getWidth() ||
                bitmap_avg.getHeight() != bitmap_new.getHeight() ) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "bitmaps not of same resolution");
            }
            throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
        }

        long time_s = System.currentTimeMillis();

        int width = bitmap_avg.getWidth();
        int height = bitmap_avg.getHeight();

        /*final boolean use_sharpness_test = false; // disabled for now - takes about 1s extra, and no evidence this helps quality
        if( use_sharpness_test ) {
            float sharpness_avg = computeSharpness(allocation_avg, width, time_s);
            float sharpness_new = computeSharpness(allocation_new, width, time_s);
            if( sharpness_new > sharpness_avg ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "use new image as reference");
                Allocation dummy_allocation = allocation_avg;
                allocation_avg = allocation_new;
                allocation_new = dummy_allocation;
                Bitmap dummy_bitmap = bitmap_avg;
                bitmap_avg = bitmap_new;
                bitmap_new = dummy_bitmap;
                sharp_index = 1;
            }
            else {
                sharp_index = 0;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "sharp_index: " + sharp_index);
        }*/

        /*LuminanceInfo luminanceInfo = computeMedianLuminance(bitmap_avg, 0, 0, width, height);
        if( MyDebug.LOG )
            Log.d(TAG, "median: " + luminanceInfo.median_value);*/

        AvgData avg_data = processAvgCore(null, bitmap_avg, bitmap_new, width, height, avg_factor, iso, exposure_time, zoom_factor, time_s);

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processAvg: " + (System.currentTimeMillis() - time_s));

        return avg_data;
    }

    /** Combines multiple images by averaging them. See processAvg() for more details.
     * @param avg_data       The argument returned by processAvg().
     * @param width          The width of the images.
     * @param height         The height of the images.
     * @param bitmap_new     The new input image. The bitmap is recycled.
     * @param avg_factor     The weighting factor for bitmap_avg.
     * @param iso            The ISO used to take the photos.
     * @param exposure_time  The exposure time used to take the photos.
     * @param zoom_factor    The digital zoom factor used to take the photos.
     */
    public void updateAvg(AvgData avg_data, int width, int height, Bitmap bitmap_new, float avg_factor, int iso, long exposure_time, float zoom_factor) throws HDRProcessorException {
        if( MyDebug.LOG ) {
            Log.d(TAG, "updateAvg");
            Log.d(TAG, "avg_factor: " + avg_factor);
        }
        if( width != bitmap_new.getWidth() ||
                height != bitmap_new.getHeight() ) {
            if( MyDebug.LOG ) {
                Log.e(TAG, "bitmaps not of same resolution");
            }
            throw new HDRProcessorException(HDRProcessorException.UNEQUAL_SIZES);
        }

        long time_s = System.currentTimeMillis();

        processAvgCore(avg_data, null, bitmap_new, width, height, avg_factor, iso, exposure_time, zoom_factor, time_s);

        if( MyDebug.LOG )
            Log.d(TAG, "### time for updateAvg: " + (System.currentTimeMillis() - time_s));
    }

    /** Core algorithm for Noise Reduction algorithm.
     * @param avg_data       Should be null for first call, and non-null for subsequent calls. This should
     *                       be the AvgData returned by the first call.
     * @param bitmap_avg     If non-null, the first bitmap (which will be recycled when the returned
     *                       AvgData is destroyed). If null, an avg_data should be supplied.
     * @param bitmap_new     The new bitmap to combined. The bitmap will be recycled.
     * @param width          The width of the bitmaps.
     * @param height         The height of the bitmaps.
     * @param avg_factor     The averaging factor.
     * @param iso            The ISO used for the photos.
     * @param zoom_factor    The digital zoom factor used to take the photos.
     * @param time_s         Time, for debugging.
     */
    private AvgData processAvgCore(AvgData avg_data, Bitmap bitmap_avg, Bitmap bitmap_new, int width, int height, float avg_factor, int iso, long exposure_time, float zoom_factor, long time_s) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "processAvgCore");
            Log.d(TAG, "iso: " + iso);
            Log.d(TAG, "zoom_factor: " + zoom_factor);
        }

        float [] pixels_rgbf_out = null;
        Bitmap bitmap_avg_align = null; // if non-null, use this bitmap for alignment for averaged image.
        Bitmap bitmap_orig = null; // if non-null, this is a bitmap representing the first image.
        if( avg_data != null ) {
            pixels_rgbf_out = avg_data.pixels_rgbf_out;
            bitmap_avg_align = avg_data.bitmap_avg_align;
            bitmap_orig = avg_data.bitmap_orig;
        }

        offsets_x = new int[2];
        offsets_y = new int[2];
        boolean floating_point;
        if( bitmap_avg != null && pixels_rgbf_out == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "process first bitmap");
            floating_point = false;
        }
        else if( bitmap_avg == null && pixels_rgbf_out != null ) {
            floating_point = true;
            if( MyDebug.LOG )
                Log.d(TAG, "processing existing result");
        }
        else {
            throw new RuntimeException("only one of bitmap_avg or pixels_rgbf_out should be supplied");
        }

        {
            // perform auto-alignment
            List<Bitmap> align_bitmaps = new ArrayList<>();
            Bitmap bitmap_new_align;
            int alignment_width;
            int alignment_height;
            int full_alignment_width = width;
            int full_alignment_height = height;

            //final int scale_align_size = 2;
            //final int scale_align_size = 4;
            //final int scale_align_size = Math.max(4 / this.cached_avg_sample_size, 1);
            final int scale_align_size = (zoom_factor > 3.9f) ?
                    1 :
                    Math.max(4 / this.getAvgSampleSize(iso, exposure_time), 1);
            if( MyDebug.LOG )
                Log.d(TAG, "scale_align_size: " + scale_align_size);
            boolean crop_to_centre = true;
            {
                // use scaled down and/or cropped bitmaps for alignment
                if( MyDebug.LOG )
                    Log.d(TAG, "### time before creating bitmaps for autoalignment: " + (System.currentTimeMillis() - time_s));
                Matrix align_scale_matrix = new Matrix();
                align_scale_matrix.postScale(1.0f/scale_align_size, 1.0f/scale_align_size);
                full_alignment_width /= scale_align_size;
                full_alignment_height /= scale_align_size;

                final boolean full_align = false; // whether alignment images should be created as being cropped to the centre
                //final boolean full_align = true; // whether alignment images should be created as being cropped to the centre
                int align_width = width;
                int align_height = height;
                int align_x = 0;
                int align_y = 0;
                if( !full_align ) {
                    // need to use /2 rather than /4 to prevent misalignment in testAvg26
                    //align_width = width/4;
                    //align_height = height/4;
                    align_width = width/2;
                    align_height = height/2;
                    align_x = (width - align_width)/2;
                    align_y = (height - align_height)/2;
                    crop_to_centre = false; // no need to crop in autoAlignment, as we're cropping here
                }

                final boolean filter_align = false;
                //final boolean filter_align = true;
                if( bitmap_avg_align == null ) {
                    bitmap_avg_align = Bitmap.createBitmap(bitmap_avg, align_x, align_y, align_width, align_height, align_scale_matrix, filter_align);
                    if( MyDebug.LOG )
                        Log.d(TAG, "### time after creating avg bitmap for autoalignment: " + (System.currentTimeMillis() - time_s));
                }
                bitmap_new_align = Bitmap.createBitmap(bitmap_new, align_x, align_y, align_width, align_height, align_scale_matrix, filter_align);

                alignment_width = bitmap_new_align.getWidth();
                alignment_height = bitmap_new_align.getHeight();

                align_bitmaps.add(bitmap_avg_align);
                align_bitmaps.add(bitmap_new_align);
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after creating bitmaps for autoalignment: " + (System.currentTimeMillis() - time_s));
            }

            // misalignment more likely in "dark" images with more images and/or longer exposures
            // using max_align_scale=2 needed to prevent misalignment in testAvg51; also helps testAvg14
            boolean wider = sceneIsLowLight(iso, exposure_time);
            autoAlignment(offsets_x, offsets_y, alignment_width, alignment_height, align_bitmaps, 0, true, null, false, 1, crop_to_centre, wider ? 2 : 1, full_alignment_width, full_alignment_height, time_s);

            {
                for(int i=0;i<offsets_x.length;i++) {
                    offsets_x[i] *= scale_align_size;
                }
                for(int i=0;i<offsets_y.length;i++) {
                    offsets_y[i] *= scale_align_size;
                }
            }

            if( bitmap_new_align != null ) {
                bitmap_new_align.recycle();
                //noinspection UnusedAssignment
                bitmap_new_align = null;
            }

            if( MyDebug.LOG ) {
                Log.d(TAG, "### time after autoAlignment: " + (System.currentTimeMillis() - time_s));
            }
        }

        // write new avg image

        // higher wiener_C (and higher wiener_cutoff_factor) means more averaging (but more risk of ghosting)
        // if changing this, pay close attention to tests testAvg6, testAvg8, testAvg17, testAvg23
        float limited_iso = Math.min(iso, 400);
        float wiener_cutoff_factor = 1.0f;
        if( iso >= 700 ) {
            // helps reduce speckles in testAvg17, testAvg23, testAvg33, testAvg36, testAvg38
            // using this level for testAvg31 (ISO 609) would increase ghosting
            //limited_iso = 500;
            limited_iso = 800;
            if( iso >= 1100 ) {
                // helps further reduce speckles in testAvg17, testAvg38
                // but don't do for iso >= 700 as makes "vicks" text in testAvg23 slightly more blurred
                wiener_cutoff_factor = 8.0f;
            }
        }
        limited_iso = Math.max(limited_iso, 100);
        float wiener_C = 10.0f * limited_iso;
        //float wiener_C = 1000.0f;
        //float wiener_C = 4000.0f;

        // Tapering the wiener scale means that we do more averaging for earlier images in the stack, the
        // logic being we'll have more chance of ghosting or misalignment with later images.
        // This helps: testAvg31, testAvg33.
        // Also slightly helps testAvg17, testAvg23 (slightly less white speckle on tv), testAvg28
        // (one less white speckle on face).
        // Note that too much tapering risks increasing ghosting in testAvg26, testAvg39.
        float tapered_wiener_scale = 1.0f - (float)Math.pow(0.5, avg_factor);
        if( MyDebug.LOG ) {
            Log.d(TAG, "avg_factor: " + avg_factor);
            Log.d(TAG, "tapered_wiener_scale: " + tapered_wiener_scale);
        }
        wiener_C /= tapered_wiener_scale;

        float wiener_C_cutoff = wiener_cutoff_factor * wiener_C;
        if( MyDebug.LOG ) {
            Log.d(TAG, "wiener_C: " + wiener_C);
            Log.d(TAG, "wiener_cutoff_factor: " + wiener_cutoff_factor);
        }

        if( bitmap_orig == null ) {
            // bitmap_orig should only be null for the first pair of images, which should be when we
            // have the avg image not in floating point format
            if( floating_point ) {
                throw new RuntimeException("is in floating point mode, but no bitmap_orig supplied");
            }
            bitmap_orig = bitmap_avg;
        }

        /*if( !floating_point )*/ {
            if( pixels_rgbf_out == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "need to create pixels_rgbf_out");
                pixels_rgbf_out = new float[3*width*height];
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after create pixels_rgbf_out: " + (System.currentTimeMillis() - time_s));
            }
            JavaImageFunctions.AvgApplyFunction function = new JavaImageFunctions.AvgApplyFunction(pixels_rgbf_out, bitmap_new, bitmap_orig, width, height, offsets_x[1], offsets_y[1], avg_factor, wiener_C, wiener_C_cutoff);
            JavaImageProcessing.applyFunction(function, bitmap_avg, null, 0, 0, width, height);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after AvgApplyFunction: " + (System.currentTimeMillis() - time_s));
        }

        // N.B., we don't recycle bitmap_avg (if non-null), we need to use when processing later iterations.
        // Instead the first bitmap is recycled in
        // AvgData.destroy(). Also note that if we did recycle bitmap_avg, we get a native crash in
        // process_avg.rs when reading from bitmap_orig, but only on some devices (crash can be
        // reproduced on Android 11 with Android emulator, e.g., running testTakePhotoNR).
        /*if( bitmap_avg != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "release bitmap_avg");
            bitmap_avg.recycle();
            bitmap_avg = null;
        }*/
        if( bitmap_new != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "release bitmap_new");
            bitmap_new.recycle();
            //noinspection UnusedAssignment
            bitmap_new = null;
        }

        if( MyDebug.LOG )
            Log.d(TAG, "### time for processAvgCore: " + (System.currentTimeMillis() - time_s));
        return new AvgData(pixels_rgbf_out, bitmap_avg_align, bitmap_avg);
    }

    void autoAlignment(int[] offsets_x, int[] offsets_y, int width, int height, List<Bitmap> bitmaps, int base_bitmap, boolean use_mtb, int max_align_scale) {
        if( MyDebug.LOG )
            Log.d(TAG, "autoAlignment");
        autoAlignment(offsets_x, offsets_y, width, height, bitmaps, base_bitmap, true, null, use_mtb, 1, false, max_align_scale, width, height, 0);
    }

    static class BrightnessDetails {
        final int median_brightness; // median brightness value of the median image

        BrightnessDetails(int median_brightness) {
            this.median_brightness = median_brightness;
        }
    }

    /**
     *
     * @param bitmaps       Bitmaps to align.
     * @param base_bitmap   Index of bitmap in bitmaps that should be kept fixed; the other bitmaps
     *                      will be aligned relative to this.
     * @param assume_sorted If assume_sorted if false, and use_mtb is true, this function will also
     *                      sort the bitmaps from darkest to brightest.
     * @param use_mtb       Whether to align based on the median threshold bitmaps or not.
     * @param max_align_scale If larger than 1, start from a larger start area.
     */
    private BrightnessDetails autoAlignment(int [] offsets_x, int [] offsets_y, int width, int height, List<Bitmap> bitmaps, int base_bitmap, boolean assume_sorted, SortCallback sort_cb, boolean use_mtb, int min_step_size, boolean crop_to_centre, int max_align_scale, int full_width, int full_height, long time_s) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "autoAlignment");
            Log.d(TAG, "width: " + width);
            Log.d(TAG, "height: " + height);
            Log.d(TAG, "use_mtb: " + use_mtb);
            Log.d(TAG, "max_align_scale: " + max_align_scale);
            Log.d(TAG, "bitmaps: " + bitmaps.size());
        }

        int n_images = bitmaps.size();
        if( bitmaps.size() != offsets_x.length ) {
            throw new RuntimeException("unequal bitmaps and offsets_x lengths");
        }
        else if( bitmaps.size() != offsets_y.length ) {
            throw new RuntimeException("unequal bitmaps and offsets_y lengths");
        }

        // initialise
        for(int i=0;i<n_images;i++) {
            offsets_x[i] = 0;
            offsets_y[i] = 0;
        }

        // Testing shows that in practice we get good results by only aligning the centre quarter of the images. This gives better
        // performance, and uses less memory.
        // If copy_to_centre is false, this has already been done by the caller.
        int mtb_width = width;
        int mtb_height = height;
        int mtb_x = 0;
        int mtb_y = 0;
        if( crop_to_centre ) {
            mtb_width = width/2;
            mtb_height = height/2;
            mtb_x = mtb_width/2;
            mtb_y = mtb_height/2;
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "mtb_x: " + mtb_x);
            Log.d(TAG, "mtb_y: " + mtb_y);
            Log.d(TAG, "mtb_width: " + mtb_width);
            Log.d(TAG, "mtb_height: " + mtb_height);
        }

        LuminanceInfo [] luminanceInfos = null;
        if( use_mtb ) {
            luminanceInfos = new LuminanceInfo[n_images];
            for(int i = 0; i < n_images; i++) {
                luminanceInfos[i] = computeMedianLuminance(bitmaps.get(i), mtb_x, mtb_y, mtb_width, mtb_height);
                if( MyDebug.LOG )
                    Log.d(TAG, i + ": median_value: " + luminanceInfos[i].median_value);
            }
            if( MyDebug.LOG )
                Log.d(TAG, "time after computeMedianLuminance: " + (System.currentTimeMillis() - time_s));
        }

        if( !assume_sorted && use_mtb ) {
            if( MyDebug.LOG )
                Log.d(TAG, "sort bitmaps");
            class BitmapInfo {
                final LuminanceInfo luminanceInfo;
                final Bitmap bitmap;
                final int index;

                BitmapInfo(LuminanceInfo luminanceInfo, Bitmap bitmap, int index) {
                    this.luminanceInfo = luminanceInfo;
                    this.bitmap = bitmap;
                    this.index = index;
                }

            }

            List<BitmapInfo> bitmapInfos = new ArrayList<>(bitmaps.size());
            for(int i=0;i<bitmaps.size();i++) {
                BitmapInfo bitmapInfo = new BitmapInfo(luminanceInfos[i], bitmaps.get(i), i);
                bitmapInfos.add(bitmapInfo);
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "before sorting:");
                for(int i=0;i<n_images;i++) {
                    Log.d(TAG, "    " + i + ": " + luminanceInfos[i]);
                }
            }
            Collections.sort(bitmapInfos, new Comparator<>() {
                @Override
                public int compare(BitmapInfo o1, BitmapInfo o2) {
                    // important to use the code in LuminanceInfo.compareTo(), as that's also tested via the unit test
                    // sortLuminanceInfo()
                    return o1.luminanceInfo.compareTo(o2.luminanceInfo);
                }
            });
            bitmaps.clear();
            for(int i=0;i<bitmapInfos.size();i++) {
                bitmaps.add(bitmapInfos.get(i).bitmap);
                luminanceInfos[i] = bitmapInfos.get(i).luminanceInfo;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "after sorting:");
                for(int i=0;i<n_images;i++) {
                    Log.d(TAG, "    " + i + ": " + luminanceInfos[i]);
                }
            }
            if( sort_cb != null ) {
                List<Integer> sort_order = new ArrayList<>();
                for(int i=0;i<bitmapInfos.size();i++) {
                    sort_order.add( bitmapInfos.get(i).index);
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "sort_order: " + sort_order);
                sort_cb.sortOrder(sort_order);
            }
        }
        /*{
            // test
            for(int i = 0; i < luminanceInfos.length-1; i++) {
                if( luminanceInfos[i].compareTo(luminanceInfos[i+1]) == 0 ) {
                    throw new RuntimeException("this: " + luminanceInfos[i] + " , that: " + luminanceInfos[i+1]);
                }
            }
        }*/

        int median_brightness = -1;
        if( use_mtb ) {
            median_brightness = luminanceInfos[base_bitmap].median_value;
            if( MyDebug.LOG )
                Log.d(TAG, "median_brightness: " + median_brightness);
        }

        Bitmap [] mtb_bitmaps = new Bitmap[n_images];

        for(int i=0;i<n_images;i++) {
            int median_value = -1;
            if( use_mtb ) {
                median_value = luminanceInfos[i].median_value;
                if( MyDebug.LOG )
                    Log.d(TAG, i + ": median_value: " + median_value);

                /*if( median_value < 16 ) {
                    // needed for testHDR2, testHDR28
                    if( MyDebug.LOG )
                        Log.d(TAG, "image too dark to do alignment");
                    if( mtb_bitmaps != null )
                        mtb_bitmaps[i] = null;

                    continue;
                }*/
            }

            if( use_mtb && luminanceInfos[i].noisy ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "unable to compute median luminance safely");
                if( mtb_bitmaps != null )
                    mtb_bitmaps[i] = null;
                continue;
            }

            // avoid too low/high median_values, otherwise we'll detect dark or light pixels as "noisy" - needed for testHDR61
            final int min_diff_c = 4; // should be same value as in create_mtb.rs/create_mtb()
            /*if( median_value < min_diff_c+1 || median_value > 255-(min_diff_c+1) ) {
                throw new RuntimeException("image " + i + " has median_value: " + median_value); // test
            }*/
            median_value = Math.max(median_value, min_diff_c+1);
            median_value = Math.min(median_value, 255-(min_diff_c+1));
            if( MyDebug.LOG )
                Log.d(TAG, i + ": median_value is now: " + median_value);

            {
                Bitmap output_mtb_bitmap = Bitmap.createBitmap(mtb_width, mtb_height, Bitmap.Config.ALPHA_8);
                JavaImageFunctions.CreateMTBApplyFunction function = new JavaImageFunctions.CreateMTBApplyFunction(use_mtb, median_value);
                JavaImageProcessing.applyFunction(function, bitmaps.get(i), output_mtb_bitmap, mtb_x, mtb_y, mtb_x+mtb_width, mtb_y+mtb_height, 0, 0);
                if( MyDebug.LOG )
                    Log.d(TAG, "### time after CreateMTBApplyFunction: " + (System.currentTimeMillis() - time_s));
                mtb_bitmaps[i] = output_mtb_bitmap;
            }
        }

        // The initial step_size N should be a power of 2; the maximum offset we can achieve by the algorithm is N-1.
        // For pictures resolution 4160x3120, this gives max_ideal_size 27, and initial_step_size 32.
        // On tests testHDR1 to testHDR35, the max required offset was 24 pixels (for testHDR33) even when using
        // inital_step_size of 64.
        // Note for old renderscript codepath: there isn't really a performance cost in allowing higher initial step sizes (as larger sizes have less
        // sampling - since we sample every step_size pixels - though there might be some overhead for every extra call
        // to renderscript that we do). But high step sizes have a risk of producing really bad results if we were
        // to misidentify cases as needing a large offset.
        int max_dim = Math.max(full_width, full_height); // n.b., use the full width and height here, not the mtb_width, height
        //int max_ideal_size = max_dim / (wider ? 75 : 150);
        int max_ideal_size = (max_align_scale * max_dim) / 150;
        int initial_step_size = 1;
        while( initial_step_size < max_ideal_size ) {
            initial_step_size *= 2;
        }
        //initial_step_size = 64;
        if( MyDebug.LOG ) {
            Log.d(TAG, "max_dim: " + max_dim);
            Log.d(TAG, "max_ideal_size: " + max_ideal_size);
            Log.d(TAG, "initial_step_size: " + initial_step_size);
        }

        if( mtb_bitmaps != null && mtb_bitmaps[base_bitmap] == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "base image not suitable for image alignment");
            for(int i=0;i<mtb_bitmaps.length;i++) {
                if( mtb_bitmaps[i] != null ) {
                    mtb_bitmaps[i].recycle();
                    mtb_bitmaps[i] = null;
                }
            }
            return new BrightnessDetails(median_brightness);
        }

        for(int i=0;i<n_images;i++)  {
            if( i == base_bitmap ) {
                // don't need to align the "base" reference image
                continue;
            }
            if( mtb_bitmaps != null && mtb_bitmaps[i] == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "image " + i + " not suitable for image alignment");
                continue;
            }

            //final int pixel_step = use_mtb ? 1 : 4;
            final int pixel_step = 1;
            int step_size = initial_step_size;
            while( step_size > min_step_size ) {
                step_size /= 2;
                int pixel_step_size = step_size * pixel_step;
                if( pixel_step_size > mtb_width || pixel_step_size > mtb_height )
                    pixel_step_size = step_size;

                if( MyDebug.LOG ) {
                    Log.d(TAG, "call align for image: " + i);
                    Log.d(TAG, "    versus base image: " + base_bitmap);
                    Log.d(TAG, "step_size: " + step_size);
                    Log.d(TAG, "pixel_step_size: " + pixel_step_size);
                }

                final boolean use_pyramid = false;
                //final boolean use_pyramid = true;

                int stop_x, stop_y;
                if( use_pyramid ) {
                    stop_x = mtb_width;
                    stop_y = mtb_height;
                }
                else {
                    // see note inside align_mtb.rs/align_mtb() for why we sample over a subset of the image
                    stop_x = mtb_width/pixel_step_size;
                    stop_y = mtb_height/pixel_step_size;
                }
                if( MyDebug.LOG ) {
                    Log.d(TAG, "stop_x: " + stop_x);
                    Log.d(TAG, "stop_y: " + stop_y);
                }

                int [] errors;

                {
                    JavaImageFunctions.AlignMTBApplyFunction function = new JavaImageFunctions.AlignMTBApplyFunction(use_mtb, mtb_bitmaps[base_bitmap], mtb_bitmaps[i], offsets_x[i], offsets_y[i], pixel_step_size);
                    JavaImageProcessing.applyFunction(function, null, null, 0, 0, stop_x, stop_y);
                    if( MyDebug.LOG )
                        Log.d(TAG, "### time after AlignMTBApplyFunction: " + (System.currentTimeMillis() - time_s));
                    errors = function.getErrors();
                }

                int best_error = -1;
                int best_id = -1;
                for(int j=0;j<9;j++) {
                    int this_error = errors[j];
                    if( MyDebug.LOG )
                        Log.d(TAG, "    errors[" + j + "]: " + this_error);
                    if( best_id==-1 || this_error < best_error ) {
                        best_error = this_error;
                        best_id = j;
                    }
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "    best_id " + best_id + " error: " + best_error);
                if( best_error >= 2000000000 ) {
                    Log.e(TAG, "    auto-alignment failed due to overflow");
                    // hitting overflow means behaviour will be unstable under SMP, and auto-alignment won't be reliable anyway
                    best_id = 4; // default to centre
                    if( is_test ) {
                        throw new RuntimeException();
                    }
                }
                /*if( best_id != 4 ) {
                    int this_off_x = best_id % 3;
                    int this_off_y = best_id/3;
                    this_off_x--;
                    this_off_y--;
                    for(int j=0;j<9;j++) {
                        int that_off_x = j % 3;
                        int that_off_y = j/3;
                        that_off_x--;
                        that_off_y--;
                        if( this_off_x * that_off_x == -1 || this_off_y * that_off_y == -1 ) {
                            float diff = ((float)(best_error - errors[j]))/(float)errors[j];
                            if( MyDebug.LOG )
                                Log.d(TAG, "    opposite errors[" + j + "] diff: " + diff);
                            if( Math.abs(diff) <= 0.02f ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "    reject auto-alignment");
                                best_id = 4;
                                break;
                            }
                        }
                    }
                }*/
                if( best_id != -1 ) {
                    int this_off_x = best_id % 3;
                    int this_off_y = best_id/3;
                    this_off_x--;
                    this_off_y--;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "this_off_x: " + this_off_x);
                        Log.d(TAG, "this_off_y: " + this_off_y);
                    }
                    offsets_x[i] += this_off_x * step_size;
                    offsets_y[i] += this_off_y * step_size;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "offsets_x is now: " + offsets_x[i]);
                        Log.d(TAG, "offsets_y is now: " + offsets_y[i]);
                    }
                    /*if( wider && step_size == initial_step_size/2 && (this_off_x != 0 || this_off_y != 0 ) ) {
                        throw new RuntimeException(); // test
                    }*/
                }
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "resultant offsets for image: " + i);
                Log.d(TAG, "resultant offsets_x: " + offsets_x[i]);
                Log.d(TAG, "resultant offsets_y: " + offsets_y[i]);
            }
        }

        /*for(int i=0;i<n_images;i++) {
            offsets_x[i] = 0;
            offsets_y[i] = 0;
        }*/
        for(int i=0;mtb_bitmaps != null && i<mtb_bitmaps.length;i++) {
            if( mtb_bitmaps[i] != null ) {
                mtb_bitmaps[i].recycle();
                mtb_bitmaps[i] = null;
            }
        }
        return new BrightnessDetails(median_brightness);
    }

    public static class LuminanceInfo implements Comparable<LuminanceInfo> {
        final int min_value;
        final int median_value;
        final int hi_value;
        final boolean noisy;

        public LuminanceInfo(int min_value, int median_value, int hi_value, boolean noisy) {
            this.min_value = min_value;
            this.median_value = median_value;
            this.hi_value = hi_value;
            this.noisy = noisy;
        }

        @Override
        @NonNull
        public String toString() {
            return "min: " + min_value + " , median: " + median_value + " , hi: " + hi_value + " , noisy: " + noisy;
        }

        @Override
        public int compareTo(LuminanceInfo o) {
            int value = this.median_value - o.median_value;
            if( value == 0 ) {
                // fall back to using min_value
                value = this.min_value - o.min_value;
            }
            if( value == 0 ) {
                // fall back to using hi_value
                value = this.hi_value - o.hi_value;
            }
            return value;
        }
    }

    private LuminanceInfo computeMedianLuminance(Bitmap bitmap, int mtb_x, int mtb_y, int mtb_width, int mtb_height) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computeMedianLuminance");
            Log.d(TAG, "mtb_x: " + mtb_x);
            Log.d(TAG, "mtb_y: " + mtb_y);
            Log.d(TAG, "mtb_width: " + mtb_width);
            Log.d(TAG, "mtb_height: " + mtb_height);
        }
        final int n_samples_c = 100;
        final int n_w_samples = (int)Math.sqrt(n_samples_c);
        final int n_h_samples = n_samples_c/n_w_samples;

        int [] histo = new int[256];
        for(int i=0;i<256;i++)
            histo[i] = 0;
        int total = 0;
        //double sum_log_luminance = 0.0;
        for(int y=0;y<n_h_samples;y++) {
            double alpha = ((double) y + 1.0) / ((double) n_h_samples + 1.0);
            //int y_coord = (int) (alpha * bitmap.getHeight());
            int y_coord = mtb_y + (int) (alpha * mtb_height);
            for(int x=0;x<n_w_samples;x++) {
                double beta = ((double) x + 1.0) / ((double) n_w_samples + 1.0);
                //int x_coord = (int) (beta * bitmap.getWidth());
                int x_coord = mtb_x + (int) (beta * mtb_width);
                /*if( MyDebug.LOG )
                    Log.d(TAG, "sample value from " + x_coord + " , " + y_coord);*/
                int color = bitmap.getPixel(x_coord, y_coord);
                int r = (color & 0xFF0000) >> 16;
                int g = (color & 0xFF00) >> 8;
                int b = (color & 0xFF);
                int luminance = Math.max(r, g);
                luminance = Math.max(luminance, b);
                histo[luminance]++;
                //sum_log_luminance += Math.log(luminance+1.0); // add 1 so we don't take log of 0...;
                total++;
            }
        }
        /*float avg_luminance = (float)(Math.exp( sum_log_luminance / total ));
        if( MyDebug.LOG )
            Log.d(TAG, "avg_luminance: " + avg_luminance);*/
        int middle = total/2;
        int count = 0;
        boolean noisy = false;
        int min_value = -1, hi_value = -1;
        // first count backwards to get hi_value
        for(int i=255;i>=0;i--) {
            /*if( histo[i] > 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "max luminance " + i);
                max_value = i;
                break;
            }*/
            count += histo[i];
            if( count >= total/10 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "hi luminance " + i);
                hi_value = i;
                break;
            }
        }

        // then count forwards to get min and median values
        count = 0;
        for(int i=0;i<256;i++) {
            count += histo[i];
            if( min_value == -1 && histo[i] > 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "min luminance " + i);
                min_value = i;
            }
            if( count >= middle ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "median luminance " + i);
                final int noise_threshold = 4;
                int n_below = 0, n_above = 0;
                for(int j=0;j<=i-noise_threshold;j++) {
                    n_below += histo[j];
                }
                for(int j=0;j<=i+noise_threshold && j<256;j++) {
                    n_above += histo[j];
                }
                double frac_below = n_below / (double)total;
                if( MyDebug.LOG ) {
                    double frac_above = 1.0 - n_above / (double)total;
                    Log.d(TAG, "count: " + count);
                    Log.d(TAG, "n_below: " + n_below);
                    Log.d(TAG, "n_above: " + n_above);
                    Log.d(TAG, "frac_below: " + frac_below);
                    Log.d(TAG, "frac_above: " + frac_above);
                }
                if( frac_below < 0.2 ) {
                    // needed for testHDR2, testHDR28
                    // note that we don't exclude cases where frac_above is too small, as this could be an overexposed image - see testHDR31
                    if( MyDebug.LOG )
                        Log.d(TAG, "too dark/noisy");
                    noisy = true;
                }
                return new LuminanceInfo(min_value, i, hi_value, noisy);
            }
        }
        Log.e(TAG, "computeMedianLuminance failed");
        return new LuminanceInfo(min_value, 127, hi_value, true);
    }

    /** Clips the histogram, for Contrast Limited AHE algorithm.
     * @param histogram Histogram to modify (length 256).
     * @param temp_c_histogram Temporary workspace (length 256).
     * @param sub_width Width of the region being processed.
     * @param sub_height Height of the region being processed.
     */
    void clipHistogram(int [] histogram, int [] temp_c_histogram, int sub_width, int sub_height, boolean ce_preserve_blacks) {
        int n_pixels = sub_width * sub_height;
        int clip_limit = (5 * n_pixels) / 256;
                    /*if( MyDebug.LOG ) {
                        Log.d(TAG, "clip_limit: " + clip_limit);
                        Log.d(TAG, "    relative clip limit: " + clip_limit*256.0f/n_pixels);
                    }*/
        {
            // find real clip limit
            int bottom = 0, top = clip_limit;
            while( top - bottom > 1 ) {
                int middle = (top + bottom)/2;
                int sum = 0;
                for(int x=0;x<256;x++) {
                    if( histogram[x] > middle ) {
                        sum += (histogram[x] - clip_limit);
                    }
                }
                if( sum > (clip_limit - middle) * 256 )
                    top = middle;
                else
                    bottom = middle;
            }
            clip_limit = (top + bottom)/2;
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "updated clip_limit: " + clip_limit);
                            Log.d(TAG, "    relative updated clip limit: " + clip_limit*256.0f/n_pixels);
                        }*/
        }
        int n_clipped = 0;
        for(int x=0;x<256;x++) {
            if( histogram[x] > clip_limit ) {
                            /*if( MyDebug.LOG ) {
                                Log.d(TAG, "    " + x + " : " + histogram[x] + " : " + (histogram[x]*256.0f/n_pixels));
                            }*/
                n_clipped += (histogram[x] - clip_limit);
                histogram[x] = clip_limit;
            }
        }
        int n_clipped_per_bucket = n_clipped / 256;
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "n_clipped: " + n_clipped);
                            Log.d(TAG, "n_clipped_per_bucket: " + n_clipped_per_bucket);
                        }*/
        for(int x=0;x<256;x++) {
            histogram[x] += n_clipped_per_bucket;
        }

        if( ce_preserve_blacks ) {
            // This helps tests such as testHDR52, testHDR57, testAvg26, testAvg30
            // The basic idea is that we want to avoid making darker pixels darker (by too
            // much). We do this by adjusting the histogram:
            // * We can set a minimum value of each histogram value. E.g., if we set all
            //   pixels up to a certain brightness to a value equal to n_pixels/256, then
            //   we prevent those pixels from being made darker. In practice, we choose
            //   a tapered minimum, starting at (n_pixels/256) for black pixels, linearly
            //   interpolating to no minimum at brightness 128 (dark_threshold_c).
            // * For any adjusted value of the histogram, we redistribute, by reducing
            //   the histogram values of brighter pixels with values larger than (n_pixels/256),
            //   reducing them to a minimum of (n_pixels/256).
            // * Lastly, we only modify a given histogram value if pixels of that brightness
            //   would be made darker by the CLAHE algorithm. We can do this by looking at
            //   the cumulative histogram (as computed before modifying any values).
                        /*if( MyDebug.LOG ) {
                            for(int x=0;x<256;x++) {
                                Log.d(TAG, "pre-brighten histogram[" + x + "] = " + histogram[x]);
                            }
                        }*/

            temp_c_histogram[0] = histogram[0];
            for(int x=1;x<256;x++) {
                temp_c_histogram[x] = temp_c_histogram[x-1] + histogram[x];
            }

            // avoid making pixels too dark
            int equal_limit = n_pixels / 256;
            if( MyDebug.LOG )
                Log.d(TAG, "equal_limit: " + equal_limit);
            //final int dark_threshold_c = 64;
            final int dark_threshold_c = 128;
            //final int dark_threshold_c = 256;
            for(int x=0;x<dark_threshold_c;x++) {
                int c_equal_limit = equal_limit * (x+1);
                if( temp_c_histogram[x] >= c_equal_limit ) {
                    continue;
                }
                float alpha = 1.0f - ((float)x)/((float)dark_threshold_c);
                //float alpha = 1.0f - ((float)x)/256.0f;
                int limit = (int)(alpha * equal_limit);
                //int limit = equal_limit;
                /*if( MyDebug.LOG )
                    Log.d(TAG, "x: " + x + " ; limit: " + limit);*/
                            /*histogram[x] = Math.max(histogram[x], limit);
                            if( MyDebug.LOG )
                                Log.d(TAG, "    histogram pulled up to: "  + histogram[x]);*/
                if( histogram[x] < limit ) {
                    // top up by redistributing later values
                    for(int y=x+1;y<256 && histogram[x] < limit;y++) {
                        if( histogram[y] > equal_limit ) {
                            int move = histogram[y] - equal_limit;
                            move = Math.min(move, limit - histogram[x]);
                            histogram[x] += move;
                            histogram[y] -= move;
                        }
                    }
                    /*if( MyDebug.LOG )
                        Log.d(TAG, "    histogram pulled up to: "  + histogram[x]);*/
                                /*if( temp_c_histogram[x] >= c_equal_limit )
                                    throw new RuntimeException(); // test*/
                }
            }
        }
    }

    void adjustHistogram(Bitmap bitmap_in, Bitmap bitmap_out, int width, int height, float hdr_alpha, int n_tiles, boolean ce_preserve_blacks, long time_s) {
        if( MyDebug.LOG )
            Log.d(TAG, "adjustHistogram [bitmap]");

        //final boolean adjust_histogram_local = false;
        final boolean adjust_histogram_local = true;

        if( adjust_histogram_local ) {
            // Contrast Limited Adaptive Histogram Equalisation (CLAHE)
            // Note we don't fully equalise the histogram, rather the resultant image is the mid-point of the non-equalised and fully-equalised images
            // See https://en.wikipedia.org/wiki/Adaptive_histogram_equalization#Contrast_Limited_AHE
            // Also see "Adaptive Histogram Equalization and its Variations" ( http://www.cs.unc.edu/Research/MIDAG/pubs/papers/Adaptive%20Histogram%20Equalization%20and%20Its%20Variations.pdf ),
            // Pizer, Amburn, Austin, Cromartie, Geselowitz, Greer, ter Haar Romeny, Zimmerman, Zuiderveld (1987).
            // Also note that if ce_preserve_blacks is true, we apply a modification to this algorithm, see below.

            if( MyDebug.LOG )
                Log.d(TAG, "time before creating histograms: " + (System.currentTimeMillis() - time_s));
            // create histograms

            //final int n_tiles_c = 8;
            //final int n_tiles_c = 4;
            //final int n_tiles_c = 1;
            int [] c_histogram = new int[n_tiles*n_tiles*256];
            int [] temp_c_histogram = new int[256];
            for(int i=0;i<n_tiles;i++) {
                double a0 = ((double)i)/(double)n_tiles;
                double a1 = ((double)i+1.0)/(double)n_tiles;
                int start_x = (int)(a0 * width);
                int stop_x = (int)(a1 * width);
                if( stop_x == start_x )
                    continue;
                for(int j=0;j<n_tiles;j++) {
                    double b0 = ((double)j)/(double)n_tiles;
                    double b1 = ((double)j+1.0)/(double)n_tiles;
                    int start_y = (int)(b0 * height);
                    int stop_y = (int)(b1 * height);
                    if( stop_y == start_y )
                        continue;
                        /*if( MyDebug.LOG )
                            Log.d(TAG, i + " , " + j + " : " + start_x + " , " + start_y + " to " + stop_x + " , " + stop_y);*/

                    // We compute a histogram based on the max RGB value, so this matches with the scaling we do in histogram_adjust.rs.
                    // This improves the look of the grass in testHDR24, testHDR27.
                    /*int [] pixels = new int[(stop_x-start_x)*(stop_y-start_y)];
                    bitmap_in.getPixels(pixels, 0, stop_x-start_x, start_x, start_y, stop_x-start_x, stop_y-start_y);
                    int [] histogram = computeHistogram(pixels);*/
                    JavaImageFunctions.ComputeHistogramApplyFunction function = new JavaImageFunctions.ComputeHistogramApplyFunction(JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_VALUE);
                    JavaImageProcessing.applyFunction(function, bitmap_in, null, start_x, start_y, stop_x, stop_y);
                    int [] histogram = function.getHistogram();

                    clipHistogram(histogram, temp_c_histogram, (stop_x - start_x), (stop_y - start_y), ce_preserve_blacks);

                    // compute cumulative histogram
                    int histogram_offset = 256*(i*n_tiles+j);
                    c_histogram[histogram_offset] = histogram[0];
                    for(int x=1;x<256;x++) {
                        c_histogram[histogram_offset+x] = c_histogram[histogram_offset+x-1] + histogram[x];
                    }
                    /*if( MyDebug.LOG ) {
                        for(int x=0;x<256;x++) {
                            Log.d(TAG, "histogram[" + x + "] = " + histogram[x] + " cumulative: " + c_histogram[histogram_offset+x]);
                        }
                    }*/
                }
            }

            if( MyDebug.LOG )
                Log.d(TAG, "adjustHistogram: time after creating histograms: " + (System.currentTimeMillis() - time_s));

            JavaImageFunctions.AdjustHistogramApplyFunction function = new JavaImageFunctions.AdjustHistogramApplyFunction(hdr_alpha, n_tiles, width, height, c_histogram);
            JavaImageProcessing.applyFunction(function, bitmap_in, bitmap_out, 0, 0, width, height);
            if( MyDebug.LOG )
                Log.d(TAG, "time after adjusting histogram: " + (System.currentTimeMillis() - time_s));
        }
    }

    public enum HistogramType {
        HISTOGRAM_TYPE_RGB,
        HISTOGRAM_TYPE_LUMINANCE,
        HISTOGRAM_TYPE_VALUE,
        HISTOGRAM_TYPE_INTENSITY,
        HISTOGRAM_TYPE_LIGHTNESS
    }

    /**
     * @param type The type of histogram to compute.
     */
    public int [] computeHistogram(Bitmap bitmap, HistogramType type) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "computeHistogram [bitmap]");
            Log.d(TAG, "type: " + type);
        }
        long time_s = System.currentTimeMillis();

        /*final int n_pixels = bitmap.getWidth()*bitmap.getHeight();
        int [] pixels = new int[n_pixels];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int [] histogram = computeHistogram(pixels);*/

        JavaImageFunctions.ComputeHistogramApplyFunction.Type java_type;
        switch( type ) {
            case HISTOGRAM_TYPE_RGB:
                java_type = JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_RGB;
                break;
            case HISTOGRAM_TYPE_LUMINANCE:
                java_type = JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_LUMINANCE;
                break;
            case HISTOGRAM_TYPE_VALUE:
                java_type = JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_VALUE;
                break;
            case HISTOGRAM_TYPE_INTENSITY:
                java_type = JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_INTENSITY;
                break;
            case HISTOGRAM_TYPE_LIGHTNESS:
                java_type = JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_LIGHTNESS;
                break;
            default:
                throw new RuntimeException("unknown histogram type: " + type);
        }
        JavaImageFunctions.ComputeHistogramApplyFunction function = new JavaImageFunctions.ComputeHistogramApplyFunction(java_type);
        JavaImageProcessing.applyFunction(function, bitmap, null, 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int [] histogram = function.getHistogram();

        if( MyDebug.LOG ) {
            Log.d(TAG, "image size: " + bitmap.getWidth() + " x " + bitmap.getHeight());
            Log.d(TAG, "### time to compute histogram: " + (System.currentTimeMillis() - time_s));
        }
        return histogram;
    }

    /**
     * @param pixels Pixels in floating point RGB format. Length of array should be 3*width*height.
     */
    private int [] computeHistogram(float [] pixels, int width, int height, boolean avg) {
        if( avg ) {
            throw new RuntimeException("not implemented");
        }

        long time_s = System.currentTimeMillis();

        JavaImageFunctions.ComputeHistogramApplyFunction function = new JavaImageFunctions.ComputeHistogramApplyFunction(JavaImageFunctions.ComputeHistogramApplyFunction.Type.TYPE_VALUE);
        function.setPixelsRGBf(pixels, width);
        JavaImageProcessing.applyFunction(function, null, null, 0, 0, width, height);
        int [] histogram = function.getHistogram();

        if( MyDebug.LOG ) {
            Log.d(TAG, "image size: " + width + " x " + height);
            Log.d(TAG, "### time to compute histogram: " + (System.currentTimeMillis() - time_s));
        }
        return histogram;
    }

    static class HistogramInfo {
        final int total;
        final int mean_brightness;
        final int median_brightness;
        final int max_brightness;

        HistogramInfo(int total, int mean_brightness, int median_brightness, int max_brightness) {
            this.total = total;
            this.mean_brightness = mean_brightness;
            this.median_brightness = median_brightness;
            this.max_brightness = max_brightness;
        }
    }

    HistogramInfo getHistogramInfo(int[] histo) {
        int total = 0;
        for(int value : histo)
            total += value;
        int middle = total / 2;
        int count = 0;
        double sum_brightness = 0.0f;
        int median_brightness = -1;
        int max_brightness = 0;
        for(int i = 0; i < histo.length; i++) {
            count += histo[i];
            sum_brightness += (histo[i] * i);
            if( count >= middle && median_brightness == -1 ) {
                median_brightness = i;
            }
            if( histo[i] > 0 ) {
                max_brightness = i;
            }
        }
        int mean_brightness = (int)(sum_brightness/count + 0.1);

        return new HistogramInfo(total, mean_brightness, median_brightness, max_brightness);
    }

    private static int getBrightnessTarget(int brightness, float max_gain_factor, int ideal_brightness) {
        if( brightness > 0 ) {
            // At least try to achieve a minimum brightness.
            // Increasing max_gain_factor helps the following tests significantly: testAvg12, testAvg14, testAvg15,
            // testAvg28, testAvg31, testAvg32.
            // Other tests also helped to a lesser degree are: testAvg1, testAvg5, testAvg6, testAvg40, testAvg41,
            // testAvg42, testHDR1, testHDR1_exp5, testHDR11 (DRO example), testHDR20 (DRO example), testHDR28 (DRO example),
            // testHDR48, testHDR49, testHDR49_exp5, testHDR53.
            // We need to be careful of increasing max_gain_factor too high in some cases - for AvgTests, see comment in
            // computeBrightenFactors() for examples of tests that would be affected.

            final float min_brightness_c = 42.0f;
            float min_max_gain_factor = min_brightness_c / brightness;
            max_gain_factor = Math.max(max_gain_factor, min_max_gain_factor);

            // still set some maximum max_gain_factor - highest max_gain_factor in tests is
            // testAvg14 with max_gain_factor=14.0, which benefits from this, but some parts starting
            // to look overblown
            max_gain_factor = Math.min(max_gain_factor, 15.0f);
        }

        if( brightness <= 0 )
            brightness = 1;
        if( MyDebug.LOG ) {
            Log.d(TAG, "brightness: " + brightness);
            Log.d(TAG, "max_gain_factor: " + max_gain_factor);
            Log.d(TAG, "ideal_brightness: " + ideal_brightness);
        }
        int median_target = Math.min(ideal_brightness, (int)(max_gain_factor*brightness));
        return Math.max(brightness, median_target); // don't make darker
    }

    public static class BrightenFactors {
        public final float gain;
        public final float low_x;
        public final float mid_x;
        public final float gamma;

        BrightenFactors(float gain, float low_x, float mid_x, float gamma) {
            this.gain = gain;
            this.low_x = low_x;
            this.mid_x = mid_x;
            this.gamma = gamma;
        }
    }

    /** Computes various factors used in the avg_brighten.rs script.
     */
    public static BrightenFactors computeBrightenFactors(boolean has_iso_exposure, int iso, long exposure_time, int brightness, int max_brightness) {
        // For outdoor/bright images, don't want max_gain_factor 4, otherwise we lose variation in grass colour in testAvg42
        // and having max_gain_factor at 1.5 prevents testAvg43, testAvg44 being too bright and oversaturated
        // for other images, we also don't want max_gain_factor 4, as makes cases too bright and overblown if it would
        // take the max_possible_value over 255. Especially testAvg46, but also testAvg25, testAvg31, testAvg38,
        // testAvg39.
        // Note however that we now do allow increasing the max_gain_factor in getBrightnessTarget(), depending on
        // brightness levels.
        float max_gain_factor = 1.5f;
        int ideal_brightness = 119;
        if( has_iso_exposure && iso < 1100 && exposure_time < 1000000000L/59 ) {
            // this helps: testAvg12, testAvg21, testAvg35
            // but note we don't want to treat the following as "bright": testAvg17, testAvg23, testAvg36, testAvg37, testAvg50
            ideal_brightness = 199;
        }
        int brightness_target = getBrightnessTarget(brightness, max_gain_factor, ideal_brightness);
        //int max_target = Math.min(255, (int)((max_brightness*brightness_target)/(float)brightness + 0.5f) );
        if( MyDebug.LOG ) {
            Log.d(TAG, "brightness: " + brightness);
            Log.d(TAG, "max_brightness: " + max_brightness);
            Log.d(TAG, "ideal_brightness: " + ideal_brightness);
            Log.d(TAG, "brightness target: " + brightness_target);
            //Log.d(TAG, "max target: " + max_target);
        }

        return computeBrightenFactors(has_iso_exposure, iso, exposure_time, brightness, max_brightness, brightness_target, true);
    }

    /** Computes various factors used in the avg_brighten.rs script.
     */
    private static BrightenFactors computeBrightenFactors(boolean has_iso_exposure, int iso, long exposure_time, int brightness, int max_brightness, int brightness_target, boolean brighten_only) {
        /* We use a combination of gain and gamma to brighten images if required. Gain works best for
         * dark images (e.g., see testAvg8), gamma works better for bright images (e.g., testAvg12).
         */
        if( brightness <= 0 )
            brightness = 1;
        float gain = brightness_target / (float)brightness;
        if( MyDebug.LOG )
            Log.d(TAG, "gain " + gain);
        if( gain < 1.0f && brighten_only ) {
            gain = 1.0f;
            if( MyDebug.LOG ) {
                Log.d(TAG, "clamped gain to: " + gain);
            }
        }
        float gamma = 1.0f;
        float max_possible_value = gain*max_brightness;
        if( MyDebug.LOG )
            Log.d(TAG, "max_possible_value: " + max_possible_value);

        /*if( max_possible_value > 255.0f ) {
            gain = 255.0f / max_brightness;
            if( MyDebug.LOG )
                Log.d(TAG, "limit gain to: " + gain);
            // use gamma correction for the remainder
            if( brightness_target > gain * brightness ) {
                gamma = (float) (Math.log(brightness_target / 255.0f) / Math.log(gain * brightness / 255.0f));
            }
        }

        //float gamma = (float)(Math.log(brightness_target/255.0f) / Math.log(brightness/255.0f));
        if( MyDebug.LOG )
            Log.d(TAG, "gamma " + gamma);
        final float min_gamma_non_bright_c = 0.75f;
        //final float min_gamma_non_bright_c = 0.5f;
        if( gamma > 1.0f ) {
            gamma = 1.0f;
            if( MyDebug.LOG ) {
                Log.d(TAG, "clamped gamma to : " + gamma);
            }
        }
        else if( has_iso_exposure && iso > 150 && gamma < min_gamma_non_bright_c ) {
            // too small gamma on non-bright reduces contrast too much (e.g., see testAvg9)
            // however we can't clamp too much, see testAvg28, testAvg32
            gamma = min_gamma_non_bright_c;
            if( MyDebug.LOG ) {
                Log.d(TAG, "clamped gamma to : " + gamma);
            }
        }*/

        float mid_x = 255.5f;
        if( max_possible_value > 255.0f ) {
            if( MyDebug.LOG )
                Log.d(TAG, "use piecewise gain/gamma");
            // use piecewise function with gain and gamma
            // changed from 0.5 to 0.6 to help grass colour variation in testAvg42; also helps testAvg6; using 0.8 helps testAvg46 and testAvg50 further
            //float mid_y = ( has_iso_exposure && iso <= 150 ) ? 0.6f*255.0f : 0.8f*255.0f;
            float mid_y = ( has_iso_exposure && iso < 1100 && exposure_time < 1000000000L/59 ) ? 0.6f*255.0f : 0.8f*255.0f;
            mid_x = mid_y / gain;
            gamma = (float)(Math.log(mid_y/255.0f) / Math.log(mid_x/max_brightness));
        }
        else if( brighten_only && max_possible_value < 255.0f && max_brightness > 0 ) {
            // slightly brightens testAvg17; also brightens testAvg8 to be clearer
            float alt_gain = 255.0f / max_brightness;
            // okay to allow higher max than max_gain_factor, when it isn't going to take us over 255
            alt_gain = Math.min(alt_gain, 4.0f);
            if( MyDebug.LOG )
                Log.d(TAG, "alt_gain: " + alt_gain);
            if( alt_gain > gain ) {
                gain = alt_gain;
                if( MyDebug.LOG )
                    Log.d(TAG, "increased gain to: " + gain);
            }
        }
        float low_x = 0.0f;
        if( has_iso_exposure && iso >= 400 ) {
            // this helps: testAvg10, testAvg28, testAvg31, testAvg33
            //low_x = Math.min(8.0f, 0.125f*mid_x);
            // don't use mid_x directly, otherwise we get unstable behaviour depending on whether we
            // entered "use piecewise gain/gamma" above or not
            // see unit test testBrightenFactors().
            float piecewise_mid_y = 0.5f*255.0f;
            float piecewise_mid_x = piecewise_mid_y / gain;
            low_x = Math.min(8.0f, 0.125f*piecewise_mid_x);
        }
        if( MyDebug.LOG ) {
            Log.d(TAG, "low_x " + low_x);
            Log.d(TAG, "mid_x " + mid_x);
            Log.d(TAG, "gamma " + gamma);
        }

        return new BrightenFactors(gain, low_x, mid_x, gamma);
    }

    private float computeBlackLevel(HistogramInfo histogramInfo, int [] histo, int iso) {
        float black_level = 0.0f;
        {
            // quick and dirty dehaze algorithm
            // helps (among others): testAvg1 to testAvg10, testAvg27, testAvg30, testAvg31, testAvg39, testAvg40
            int total = histogramInfo.total;
            int percentile = (int)(total*0.001f);
            int count = 0;
            int darkest_brightness = -1;
            for(int i = 0; i < histo.length; i++) {
                count += histo[i];
                if( count >= percentile && darkest_brightness == -1 ) {
                    darkest_brightness = i;
                }
            }
            black_level = Math.max(black_level, darkest_brightness);
            // don't allow black_level too high for "dark" images, as this can cause problems due to exaggerating noise (e.g.,
            // see testAvg38)
            black_level = Math.min(black_level, iso <= 700 ? 18 : 4);
            if( MyDebug.LOG ) {
                Log.d(TAG, "percentile: " + percentile);
                Log.d(TAG, "darkest_brightness: " + darkest_brightness);
                Log.d(TAG, "black_level is now: " + black_level);
            }
        }
        return black_level;
    }

    /** Final stage of the noise reduction algorithm.
     * @param pixels_in_rgbf The pixels in floating point RGB format.
     * @param width          Width of the input.
     * @param height         Height of the input.
     * @param iso            ISO used for the original images.
     * @param exposure_time  Exposure time used for the original images.
     * @return               Resultant bitmap.
     */
    private Bitmap avgBrightenRGBf(float [] pixels_in_rgbf, int width, int height, int iso, long exposure_time) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "avgBrightenRGBf");
            Log.d(TAG, "iso: " + iso);
            Log.d(TAG, "exposure_time: " + exposure_time);
        }

        long time_s = System.currentTimeMillis();

        int [] histo = computeHistogram(pixels_in_rgbf, width, height, false);

        HistogramInfo histogramInfo = getHistogramInfo(histo);
        int brightness = histogramInfo.median_brightness;
        int max_brightness = histogramInfo.max_brightness;
        if( MyDebug.LOG )
            Log.d(TAG, "### time after computeHistogram: " + (System.currentTimeMillis() - time_s));

        if( MyDebug.LOG ) {
            Log.d(TAG, "median brightness: " + histogramInfo.median_brightness);
            Log.d(TAG, "mean brightness: " + histogramInfo.mean_brightness);
            Log.d(TAG, "max brightness: " + max_brightness);
            /*for(int i=0;i<256;i++) {
                Log.d(TAG, "histogram[" + i + "]: " + histo[i]);
            }*/
        }

        BrightenFactors brighten_factors = computeBrightenFactors(true, iso, exposure_time, brightness, max_brightness);
        float gain = brighten_factors.gain;
        float low_x = brighten_factors.low_x;
        float mid_x = brighten_factors.mid_x;
        float gamma = brighten_factors.gamma;

        //float gain = brightness_target / (float)brightness;
        /*float gamma = (float)(Math.log(max_target/(float)brightness_target) / Math.log(max_brightness/(float)brightness));
        float gain = brightness_target / ((float)Math.pow(brightness/255.0f, gamma) * 255.0f);
        if( MyDebug.LOG ) {
            Log.d(TAG, "gamma " + gamma);
            Log.d(TAG, "gain " + gain);
            Log.d(TAG, "gain2 " + max_target / ((float)Math.pow(max_brightness/255.0f, gamma) * 255.0f));
        }*/
        /*float gain = brightness_target / (float)brightness;
        if( MyDebug.LOG ) {
            Log.d(TAG, "gain: " + gain);
        }
        if( gain < 1.0f ) {
            gain = 1.0f;
            if( MyDebug.LOG ) {
                Log.d(TAG, "clamped gain to : " + gain);
            }
        }*/

        float black_level = computeBlackLevel(histogramInfo, histo, iso);

        // use a lower medial filter strength for pixel binned images, so that we don't blur testAvg46 so much (especially sign text)
        float median_filter_strength = (cached_avg_sample_size >= 2) ? 0.5f : 1.0f;
        if( MyDebug.LOG )
            Log.d(TAG, "median_filter_strength: " + median_filter_strength);

        Bitmap output_bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        JavaImageFunctions.AvgBrightenApplyFunction function = new JavaImageFunctions.AvgBrightenApplyFunction(pixels_in_rgbf, width, height, gain, gamma, low_x, mid_x, max_brightness, median_filter_strength, black_level);
        //JavaImageProcessing.applyFunction(function, input_bitmap, output_bitmap, 0, 0, width, height);
        JavaImageProcessing.applyFunction(function, null, output_bitmap, 0, 0, width, height);
        if( MyDebug.LOG )
            Log.d(TAG, "### time after AvgBrightenApplyFunction: " + (System.currentTimeMillis() - time_s));

        //if( iso <= 150 ) {
        if( iso < 1100 && exposure_time < 1000000000L/59 ) {
            // for bright scenes, contrast enhancement helps improve the quality of images (especially where we may have both
            // dark and bright regions, e.g., testAvg12); but for dark scenes, it just blows up the noise too much
            // keep n_tiles==1 - get too much contrast enhancement with n_tiles==4 e.g. for testAvg34
            // tests that are better at 25% (median brightness in brackets): testAvg16 (90), testAvg26 (117), testAvg30 (79),
            //     testAvg43 (55), testAvg44 (82)
            // tests that are better at 50%: testAvg12 (8), testAvg13 (38), testAvg15 (10), testAvg18 (39), testAvg19 (37)
            // other tests improved by doing contrast enhancement: testAvg32, testAvg40
            final int median_lo = 60, median_hi = 35;
            float alpha = (histogramInfo.median_brightness - median_lo) / (float)(median_hi - median_lo);
            alpha = Math.max(alpha, 0.0f);
            alpha = Math.min(alpha, 1.0f);
            float amount = (1.0f-alpha) * 0.25f + alpha * 0.5f;
            if( MyDebug.LOG ) {
                Log.d(TAG, "dro alpha: " + alpha);
                Log.d(TAG, "dro amount: " + amount);
            }
            adjustHistogram(output_bitmap, output_bitmap, width, height, amount, 1, true, time_s);
            if( MyDebug.LOG )
                Log.d(TAG, "### time after adjustHistogram: " + (System.currentTimeMillis() - time_s));
        }

        if( MyDebug.LOG )
            Log.d(TAG, "### total time for avgBrighten: " + (System.currentTimeMillis() - time_s));
        return output_bitmap;
    }

    /** Final stage of the noise reduction algorithm.
     * @param avg_data       AvgData returned from call to processAvg().
     * @param width          Width of the input.
     * @param height         Height of the input.
     * @param iso            ISO used for the original images.
     * @param exposure_time  Exposure time used for the original images.
     * @return               Resultant bitmap.
     */
    public Bitmap avgBrighten(AvgData avg_data, int width, int height, int iso, long exposure_time) {
        return avgBrightenRGBf(avg_data.pixels_rgbf_out, width, height, iso, exposure_time);
    }
}
