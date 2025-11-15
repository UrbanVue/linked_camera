package com.linkedcamera.app;

import android.graphics.Bitmap;
//import android.util.Log;

import java.util.List;

public class JavaImageFunctions {
    private static final String TAG = "JavaImageFunctions";

    static class CreateMTBApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final boolean use_mtb;
        private final int median_value;

        CreateMTBApplyFunction(boolean use_mtb, int median_value) {
            this.use_mtb = use_mtb;
            this.median_value = median_value;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            if( use_mtb ) {
                for(int y=off_y,c=0;y<off_y+this_height;y++) {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;

                        int value = Math.max(r, g);
                        value = Math.max(value, b);

                        // ignore small differences to reduce effect of noise - this helps testHDR22
                        int diff;
                        if( value > median_value )
                            diff = value - median_value;
                        else
                            diff = median_value - value;

                        if( diff <= 4 ) // should be same value as min_diff_c in HDRProcessor.autoAlignment()
                            pixels_out[c] = 127 << 24;
                        else if( value <= median_value )
                            pixels_out[c] = 0;
                        else
                            pixels_out[c] = 255 << 24;
                    }
                }
            }
            else {
                for(int y=off_y,c=0;y<off_y+this_height;y++) {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;

                        int value = Math.max(r, g);
                        value = Math.max(value, b);

                        pixels_out[c] = value << 24;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class AlignMTBApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private int [][] errors = null;
        private final boolean use_mtb;
        private final Bitmap bitmap0;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap0;
        private final Bitmap bitmap1;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap1;
        private final int offset_x, offset_y;
        private final int step_size;

        AlignMTBApplyFunction(boolean use_mtb, Bitmap bitmap0, Bitmap bitmap1, int offset_x, int offset_y, int step_size) {
            this.use_mtb = use_mtb;
            this.bitmap0 = bitmap0;
            this.bitmap1 = bitmap1;
            this.offset_x = offset_x;
            this.offset_y = offset_y;
            this.step_size = step_size;
        }

        @Override
        public void init(int n_threads) {
            errors = new int[n_threads][];
            fast_bitmap0 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            fast_bitmap1 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            for(int i=0;i<n_threads;i++) {
                fast_bitmap0[i] = new JavaImageProcessing.FastAccessBitmap(bitmap0);
                fast_bitmap1[i] = new JavaImageProcessing.FastAccessBitmap(bitmap1);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            if( errors[thread_index] == null )
                errors[thread_index] = new int[9];

            /* We want to sample every step_size'th pixel. Because this is awkward to set up (and wasn't possible
               in renderscript version), instead we fake it by sampling over an input bitmap of size
               (width/step_size, height/step_size), and then scaling the coordinates by step_size.

               The reason we want to sample every step_size'th pixel is it's good enough for the algorithm to work,
               and is much faster.
               */
            int bitmap0_width = bitmap0.getWidth();
            int bitmap1_width = bitmap1.getWidth();
            int bitmap1_height = bitmap1.getHeight();
            if( use_mtb ) {
                int sy = off_y, ey = off_y+this_height;
                while( sy*step_size+offset_y < step_size )
                    sy++;
                while( (ey-1)*step_size+offset_y >= bitmap1_height-step_size )
                    ey--;
                for(int cy=sy;cy<ey;cy++) {
                    int y = cy*step_size;
                    int y_plus_offset = y+offset_y;

                    fast_bitmap0[thread_index].getPixel(0, y); // force cache to cover rows needed by this row
                    int bitmap0_cache_y = fast_bitmap0[thread_index].getCacheY();
                    int y_rel_bitmap0_cache = y-bitmap0_cache_y;
                    int [] bitmap0_cache_pixels = fast_bitmap0[thread_index].getCachedPixelsI();

                    fast_bitmap1[thread_index].ensureCache(y_plus_offset-step_size, y_plus_offset+step_size); // force cache to cover rows needed by this row
                    int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                    int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                    int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();
                    int y_rel_bitmap1_cache_plus_offset = y_rel_bitmap1_cache+offset_y;

                    int sx = off_x, ex = off_x+this_width;
                    while( sx*step_size+offset_x < step_size )
                        sx++;
                    while( (ex-1)*step_size+offset_x >= bitmap1_width-step_size )
                        ex--;
                    for(int cx=sx;cx<ex;cx++) {
                        int x = cx*step_size;
                        int x_plus_offset = x+offset_x;
                        //if( x_plus_offset >= step_size && x_plus_offset < bitmap1_width-step_size && y_plus_offset >= step_size && y_plus_offset < bitmap1_height-step_size )
                        {
                            //int pixel0 = fast_bitmap0[thread_index].getPixel(x, y) >>> 24;
                            int pixel0 = bitmap0_cache_pixels[y_rel_bitmap0_cache*bitmap0_width+x] >>> 24;

                            /*int c=0;
                            for(int dy=-1;dy<=1;dy++) {
                                for(int dx=-1;dx<=1;dx++) {
                                    int pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset+dx*step_size, y_plus_offset+dy*step_size) >>> 24;
                                    if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                        // check against 127 to ignore noise - see CreateMTBApplyFunction
                                        errors[thread_index][c]++;
                                    }
                                    c++;
                                }
                            }*/

                            // unroll loops
                            // check against 127 to ignore noise - see CreateMTBApplyFunction
                            int pixel1;

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset-step_size, y_plus_offset-step_size) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset-step_size)*bitmap1_width+(x_plus_offset-step_size)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][0]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset, y_plus_offset-step_size) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset-step_size)*bitmap1_width+(x_plus_offset)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][1]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset+step_size, y_plus_offset-step_size) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset-step_size)*bitmap1_width+(x_plus_offset+step_size)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][2]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset-step_size, y_plus_offset) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset)*bitmap1_width+(x_plus_offset-step_size)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][3]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset, y_plus_offset) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset)*bitmap1_width+(x_plus_offset)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][4]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset+step_size, y_plus_offset) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset)*bitmap1_width+(x_plus_offset+step_size)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][5]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset-step_size, y_plus_offset+step_size) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+step_size)*bitmap1_width+(x_plus_offset-step_size)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][6]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset, y_plus_offset+step_size) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+step_size)*bitmap1_width+(x_plus_offset)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][7]++;
                            }

                            //pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset+step_size, y_plus_offset+step_size) >>> 24;
                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+step_size)*bitmap1_width+(x_plus_offset+step_size)] >>> 24;
                            if( pixel0 != pixel1 && pixel0 != 127 && pixel1 != 127 ) {
                                errors[thread_index][8]++;
                            }
                        }
                    }
                }
            }
            else {
                int sy = off_y, ey = off_y+this_height;
                while( sy*step_size+offset_y < step_size )
                    sy++;
                while( (ey-1)*step_size+offset_y >= bitmap1_height-step_size )
                    ey--;
                for(int cy=sy;cy<ey;cy++) {
                //for(int cy=off_y;cy<off_y+this_height;cy++) {
                    int y = cy*step_size;
                    int y_plus_offset = y+offset_y;

                    fast_bitmap0[thread_index].getPixel(0, y); // force cache to cover rows needed by this row
                    int bitmap0_cache_y = fast_bitmap0[thread_index].getCacheY();
                    int y_rel_bitmap0_cache = y-bitmap0_cache_y;
                    int [] bitmap0_cache_pixels = fast_bitmap0[thread_index].getCachedPixelsI();

                    fast_bitmap1[thread_index].ensureCache(y_plus_offset-step_size, y_plus_offset+step_size); // force cache to cover rows needed by this row
                    int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                    int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                    int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();
                    int y_rel_bitmap1_cache_plus_offset = y_rel_bitmap1_cache+offset_y;

                    int sx = off_x, ex = off_x+this_width;
                    while( sx*step_size+offset_x < step_size )
                        sx++;
                    while( (ex-1)*step_size+offset_x >= bitmap1_width-step_size )
                        ex--;
                    for(int cx=sx;cx<ex;cx++) {
                    //for(int cx=off_x;cx<off_x+this_width;cx++) {
                        int x = cx*step_size;
                        int x_plus_offset = x+offset_x;
                        //if( x_plus_offset >= step_size && x_plus_offset < bitmap1_width-step_size && y_plus_offset >= step_size && y_plus_offset < bitmap1_height-step_size )
                        {
                            //int pixel0 = fast_bitmap0[thread_index].getPixel(x, y) >>> 24;
                            int pixel0 = bitmap0_cache_pixels[y_rel_bitmap0_cache*bitmap0_width+x] >>> 24;
                            /*if( MyDebug.LOG ) {
                                Log.d(TAG, "int = " + fast_bitmap0[thread_index].getPixel(x, y));
                                Log.d(TAG, "pixel0 = " + pixel0);
                            }*/

                            /*int c=0;
                            for(int dy=-1;dy<=1;dy++) {
                                for(int dx=-1;dx<=1;dx++) {
                                    //int pixel1 = fast_bitmap1[thread_index].getPixel(x_plus_offset+dx*step_size, y_plus_offset+dy*step_size) >>> 24;
                                    int pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+dy*step_size)*bitmap1_width+(x_plus_offset+dx*step_size)] >>> 24;
                                    int diff = pixel1 - pixel0;
                                    //if( Math.abs(diff) > 255 )
                                    //    throw new RuntimeException("diff too high: " + diff);
                                    int diff2 = diff*diff;
                                    //diff2 = pixel0;
                                    //if( MyDebug.LOG )
                                    //    Log.d(TAG, "diff = " + diff);
                                    if( errors[thread_index][c] < 2000000000 ) { // avoid risk of overflow
                                        errors[thread_index][c] += diff2;
                                    }
                                    c++;
                                }
                            }*/

                            // unroll loops
                            int pixel1;
                            int diff;
                            final int overflow_check_c = 2000000000;

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset-step_size)*bitmap1_width+(x_plus_offset-step_size)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][0] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][0] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset-step_size)*bitmap1_width+(x_plus_offset)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][1] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][1] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset-step_size)*bitmap1_width+(x_plus_offset+step_size)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][2] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][2] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset)*bitmap1_width+(x_plus_offset-step_size)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][3] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][3] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset)*bitmap1_width+(x_plus_offset)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][4] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][4] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset)*bitmap1_width+(x_plus_offset+step_size)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][5] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][5] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+step_size)*bitmap1_width+(x_plus_offset-step_size)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][6] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][6] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+step_size)*bitmap1_width+(x_plus_offset)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][7] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][7] += diff*diff;
                            }

                            pixel1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache_plus_offset+step_size)*bitmap1_width+(x_plus_offset+step_size)] >>> 24;
                            diff = pixel1 - pixel0;
                            //if( Math.abs(diff) > 255 )
                            //    throw new RuntimeException("diff too high: " + diff);
                            if( errors[thread_index][8] < overflow_check_c ) { // avoid risk of overflow
                                errors[thread_index][8] += diff*diff;
                            }

                        }
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        int [] getErrors() {
            int [] total_errors = new int[errors[0].length];
            // for each errors, add its entries to the total errors
            for(int [] error : errors) {
                for (int j=0;j<error.length;j++) {
                    total_errors[j] += error[j];
                }
            }
            return total_errors;
        }
    }

    /* Simplified brighten algorithm for gain/gamma only, used for DRO algorithm.
     */
    static class DROBrightenApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float gain_A, gain_B; // see comments below
        private final float gamma;
        private final float low_x;
        private final float mid_x;
        private final float max_x;
        private final float [] value_to_gamma_scale_lut = new float[256]; // look up table for performance

        DROBrightenApplyFunction(float gain, float gamma, float low_x, float mid_x, float max_x) {
            /* We want A and B s.t.:
                float alpha = (value-low_x)/(mid_x-low_x);
                float new_value = (1.0-alpha)*low_x + alpha*gain*mid_x;
                We should be able to write this as new_value = A * value + B
                alpha = value/(mid_x-low_x) - low_x/(mid_x-low_x)
                new_value = low_x - value*low_x/(mid_x-low_x) + low_x^2/(mid_x-low_x) +
                    value*gain*mid_x/(mid_x-low_x) - gain*mid_x*low_x/(mid_x-low_x)
                So A = (gain*mid_x - low_x)/(mid_x-low_x)
                B = low_x + low_x^2/(mid_x-low_x) - gain*mid_x*low_x/(mid_x-low_x)
                = (low_x*mid_x - low_x^2 + low_x^2 - gain*mid_x*low_x)/(mid_x-low_x)
                = (low_x*mid_x - gain*mid_x*low_x)/(mid_x-low_x)
                = low_x*mid_x*(1-gain)/(mid_x-low_x)
             */
            this.gamma = gamma;
            this.low_x = low_x;
            this.mid_x = mid_x;
            this.max_x = max_x;

            if( mid_x > low_x ) {
                this.gain_A = (gain * mid_x - low_x) / (mid_x - low_x);
                this.gain_B = low_x*mid_x*(1.0f-gain)/ (mid_x - low_x);
            }
            else {
                this.gain_A = 1.0f;
                this.gain_B = 0.0f;
            }

            for(int value=0;value<256;value++) {
                float new_value =  (float)Math.pow(value/max_x, gamma) * 255.0f;
                value_to_gamma_scale_lut[value] = new_value / value;
            }
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    float fr = r, fg = g, fb = b;
                    float value = Math.max(fr, fg);
                    value = Math.max(value, fb);

                    // apply piecewise function of gain vs gamma
                    if( value <= low_x ) {
                        // don't scale
                    }
                    else if( value <= mid_x ) {
                        //float alpha = (value-low_x)/(mid_x-low_x);
                        //float new_value = (1.0-alpha)*low_x + alpha*gain*mid_x;
                        // gain_A and gain_B should be set so that new_value meets the commented out code above
                        // This code is critical for performance!

                        fr *= (gain_A + gain_B/value);
                        fg *= (gain_A + gain_B/value);
                        fb *= (gain_A + gain_B/value);
                    }
                    else {
                        // use LUT for performance
                        /*float new_value =  (float)Math.pow(value/max_x, gamma) * 255.0f;
                        float gamma_scale = new_value / value;*/
                        float gamma_scale = value_to_gamma_scale_lut[(int)(value+0.5f)];

                        fr *= gamma_scale;
                        fg *= gamma_scale;
                        fb *= gamma_scale;

                    }

                    r = (int)(fr+0.5f);
                    g = (int)(fg+0.5f);
                    b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            byte [] pixels_out = output.getCachedPixelsB();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                    int r = pixels[c];
                    int g = pixels[c+1];
                    int b = pixels[c+2];
                    // bytes are signed!
                    if( r < 0 )
                        r += 256;
                    if( g < 0 )
                        g += 256;
                    if( b < 0 )
                        b += 256;

                    float fr = r, fg = g, fb = b;
                    float value = Math.max(fr, fg);
                    value = Math.max(value, fb);

                    // apply piecewise function of gain vs gamma
                    if( value <= low_x ) {
                        // don't scale
                    }
                    else if( value <= mid_x ) {
                        //float alpha = (value-low_x)/(mid_x-low_x);
                        //float new_value = (1.0-alpha)*low_x + alpha*gain*mid_x;
                        // gain_A and gain_B should be set so that new_value meets the commented out code above
                        // This code is critical for performance!

                        fr *= (gain_A + gain_B/value);
                        fg *= (gain_A + gain_B/value);
                        fb *= (gain_A + gain_B/value);
                    }
                    else {
                        float new_value =  (float)Math.pow(value/max_x, gamma) * 255.0f;

                        float gamma_scale = new_value / value;
                        fr *= gamma_scale;
                        fg *= gamma_scale;
                        fb *= gamma_scale;
                    }

                    r = (int)(fr+0.5f);
                    g = (int)(fg+0.5f);
                    b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    pixels_out[c] = (byte)r;
                    pixels_out[c+1] = (byte)g;
                    pixels_out[c+2] = (byte)b;
                    pixels_out[c+3] = (byte)255;
                }
            }
        }
    }

    /** Class to store floating point rgb values, along with luminance.
     */
    private static class RGBf_luminance {
        float fr, fg, fb;
        float lum;

        // set from RGB101010 format
        /*void setRGB101010(int rgb) {
            this.fr = (float)((rgb) & 0x3FF) / 4.0f;
            this.fg = (float)((rgb >> 10) & 0x3FF) / 4.0f;
            this.fb = (float)((rgb >> 20) & 0x3FF) / 4.0f;
            this.lum = Math.max(Math.max(fr, fg), fb);
        }*/

        void setRGB(float fr, float fg, float fb) {
            this.fr = fr;
            this.fg = fg;
            this.fb = fb;
            this.lum = Math.max(Math.max(fr, fg), fb);
        }

        void setRGB(final float [] pixels_in_rgbf, int x, int y, int width) {
            int indx = (y*width+x)*3;
            setRGB(pixels_in_rgbf[indx], pixels_in_rgbf[indx+1], pixels_in_rgbf[indx+2]);
        }
    }

    static class AvgApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf; // output
        private final Bitmap bitmap_new; // new bitmap being added to the input
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_new;
        private final Bitmap bitmap_orig; // original bitmap (first image)
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_orig;
        private final int width, height;
        private final int offset_x_new, offset_y_new;
        private final float avg_factor;
        private final float wiener_C;
        private final float wiener_C_cutoff;

        final int radius = 2; // must be less than the radius we actually read from below
        //final int n_pixels_c = 5; // number of pixels we read from
        //final int [] sample_x = new int[]{-2, 2, 0, -2, 2};
        //final int [] sample_y = new int[]{-2, -2, 0, 2, 2};

        /*final float [] pixels_avg_fr;
        final float [] pixels_avg_fg;
        final float [] pixels_avg_fb;*/

        AvgApplyFunction(float [] pixels_rgbf, Bitmap bitmap_new, Bitmap bitmap_orig, int width, int height, int offset_x_new, int offset_y_new, float avg_factor, float wiener_C, float wiener_C_cutoff) {
            this.pixels_rgbf = pixels_rgbf;
            this.bitmap_new = bitmap_new;
            this.bitmap_orig = bitmap_orig;
            this.width = width;
            this.height = height;
            this.offset_x_new = offset_x_new;
            this.offset_y_new = offset_y_new;
            this.avg_factor = avg_factor;
            this.wiener_C = wiener_C;
            this.wiener_C_cutoff = wiener_C_cutoff;
            /*this.pixels_avg_fr = new float[width];
            this.pixels_avg_fg = new float[width];
            this.pixels_avg_fb = new float[width];*/
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_new = new JavaImageProcessing.FastAccessBitmap[n_threads];
            fast_bitmap_orig = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_new[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_new);
                fast_bitmap_orig[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_orig);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            apply(output, thread_index, (int[]) null, off_x, off_y, this_width, this_height);
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            final float avg_factorp1 = avg_factor+1.0f;
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                /*if( MyDebug.LOG )
                    Log.d(TAG, "y = " + y);*/
                int pixels_rgbf_indx = 3*y*width;
                if( y+offset_y_new < 0 || y+offset_y_new >= height ) {
                    if( pixels != null ) {
                        for(int x=off_x;x<off_x+this_width;x++,c++,pixels_rgbf_indx+=3) {
                            // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                            int color = pixels[c];
                            /*this.pixels_rgbf[3*(y*width + x)] = (float)((color >> 16) & 0xFF);
                            this.pixels_rgbf[3*(y*width + x)+1] = (float)((color >> 8) & 0xFF);
                            this.pixels_rgbf[3*(y*width + x)+2] = (float)(color & 0xFF);*/
                            this.pixels_rgbf[pixels_rgbf_indx] = (float)((color >> 16) & 0xFF);
                            this.pixels_rgbf[pixels_rgbf_indx+1] = (float)((color >> 8) & 0xFF);
                            this.pixels_rgbf[pixels_rgbf_indx+2] = (float)(color & 0xFF);
                        }
                    }
                    // else leave pixels_rgbf unchanged for this row
                    continue;
                }

                fast_bitmap_orig[thread_index].getPixel(0, Math.min(y+2, height-1)); // force cache to cover rows needed by this row
                int bitmap_orig_cache_y = fast_bitmap_orig[thread_index].getCacheY();
                int y_rel_bitmap_orig_cache = y-bitmap_orig_cache_y;
                int [] bitmap_orig_cache_pixels = fast_bitmap_orig[thread_index].getCachedPixelsI();

                int y_new = y+offset_y_new;

                //fast_bitmap_new[thread_index].getPixel(0, y+offset_y_new); // force cache to cover row y
                fast_bitmap_new[thread_index].getPixel(0, Math.min(y_new+2, height-1)); // force cache to cover rows needed by this row
                int bitmap_new_cache_y = fast_bitmap_new[thread_index].getCacheY();
                int y_rel_bitmap_new_cache = y_new-bitmap_new_cache_y;
                int [] bitmap_new_cache_pixels = fast_bitmap_new[thread_index].getCachedPixelsI();

                /*int x = off_x;
                for(;x+offset_x_new < 0;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];
                    this.pixels_rgbf[3*(y*width + x)] = (float)((color >> 16) & 0xFF);
                    this.pixels_rgbf[3*(y*width + x)+1] = (float)((color >> 8) & 0xFF);
                    this.pixels_rgbf[3*(y*width + x)+2] = (float)(color & 0xFF);
                }
                for(;x<off_x+this_width;x++,c++) {*/

                /*int saved_c = c;
                int saved_pixels_rgbf_indx = pixels_rgbf_indx;
                if( pixels != null ) {
                    for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3) {
                        // read from integer format
                        int color = pixels[c++];
                        pixels_avg_fr[x] = (float)((color >> 16) & 0xFF);
                        pixels_avg_fg[x] = (float)((color >> 8) & 0xFF);
                        pixels_avg_fb[x] = (float)(color & 0xFF);
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3) {
                        // read from floating point format
                        pixels_avg_fr[x] = this.pixels_rgbf[pixels_rgbf_indx];
                        pixels_avg_fg[x] = this.pixels_rgbf[pixels_rgbf_indx+1];
                        pixels_avg_fb[x] = this.pixels_rgbf[pixels_rgbf_indx+2];
                    }
                }
                pixels_rgbf_indx = saved_pixels_rgbf_indx;
                c = saved_c;*/

                for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    /*int color = pixels[c];
                    float pixel_avg_fr = (float)((color >> 16) & 0xFF);
                    float pixel_avg_fg = (float)((color >> 8) & 0xFF);
                    float pixel_avg_fb = (float)(color & 0xFF);*/
                    float pixel_avg_fr, pixel_avg_fg, pixel_avg_fb;
                    if( pixels != null ) {
                        // read from integer format
                        int color = pixels[c++];
                        pixel_avg_fr = (float)((color >> 16) & 0xFF);
                        pixel_avg_fg = (float)((color >> 8) & 0xFF);
                        pixel_avg_fb = (float)(color & 0xFF);
                    }
                    else {
                        // read from floating point format
                        pixel_avg_fr = this.pixels_rgbf[pixels_rgbf_indx];
                        pixel_avg_fg = this.pixels_rgbf[pixels_rgbf_indx+1];
                        pixel_avg_fb = this.pixels_rgbf[pixels_rgbf_indx+2];
                    }
                    /*float pixel_avg_fr = pixels_avg_fr[x];
                    float pixel_avg_fg = pixels_avg_fg[x];
                    float pixel_avg_fb = pixels_avg_fb[x];*/

                    int x_new = x+offset_x_new;
                    if( x_new >= 0 && x_new < width ) {
                    //if( x_new < width ) {
                    //{
                        //int pixel_new = bitmap_new.getPixel(x+offset_x_new, y+offset_y_new);
                        //int pixel_new = fast_bitmap_new[thread_index].getPixel(x+offset_x_new, y+offset_y_new);
                        //int pixel_new = bitmap_new_cache_pixels[(y+offset_y_new-bitmap_new_cache_y)*width+(x+offset_x_new)];
                        int pixel_new = bitmap_new_cache_pixels[y_rel_bitmap_new_cache*width+x_new];

                        float pixel_new_fr = (float)((pixel_new >> 16) & 0xFF);
                        float pixel_new_fg = (float)((pixel_new >> 8) & 0xFF);
                        float pixel_new_fb = (float)(pixel_new & 0xFF);

                        // temporal merging
                        // smaller value of wiener_C means stronger filter (i.e., less averaging)

                        // diff based on rgb
                        //float diff_r = pixel_avg_fr - pixel_new_fr;
                        //float diff_g = pixel_avg_fg - pixel_new_fg;
                        //float diff_b = pixel_avg_fb - pixel_new_fb;
                        //float L = diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;

                        // diff based on neighbourhood [sampling a subset of pixels]
                        // this helps testAvg24, testAvg28, testAvg31, testAvg33, testAvg39
                        float L = 0.0f;
                        if( x-radius >= 0 && x+radius < width &&
                                y-radius >= 0 && y+radius < height &&
                                x_new-radius >= 0 && x_new+radius < width &&
                                y_new-radius >= 0 && y_new+radius < height ) {

                            final int n_pixels_c = 5; // number of pixels we read from

                            // average of diffs:
                            /*for(int i=0;i<n_pixels_c;i++) {
                                int sx = sample_x[i];
                                int sy = sample_y[i];

                                //int pixel_orig = bitmap_orig.getPixel(x+sx, y+sy);
                                //int pixel_orig = fast_bitmap_orig[thread_index].getPixel(x+sx, y+sy);
                                //int pixel_orig = bitmap_orig_cache_pixels[(y+sy-bitmap_orig_cache_y)*width+(x+sx)];
                                int pixel_orig = bitmap_orig_cache_pixels[(y_rel_bitmap_orig_cache+sy)*width+(x+sx)];
                                float pixel_orig_fr = (float)((pixel_orig >> 16) & 0xFF);
                                float pixel_orig_fg = (float)((pixel_orig >> 8) & 0xFF);
                                float pixel_orig_fb = (float)(pixel_orig & 0xFF);

                                float pixel_new_sample_fr, pixel_new_sample_fg, pixel_new_sample_fb;
                                if( sx == 0 && sy == 0 ) {
                                    pixel_new_sample_fr = pixel_new_fr;
                                    pixel_new_sample_fg = pixel_new_fg;
                                    pixel_new_sample_fb = pixel_new_fb;
                                }
                                else {
                                    //int pixel_new_sample = bitmap_new.getPixel(ox+sx, oy+sy);
                                    //int pixel_new_sample = fast_bitmap_new[thread_index].getPixel(ox+sx, oy+sy);
                                    //int pixel_new_sample = bitmap_new_cache_pixels[(oy+sy-bitmap_new_cache_y)*width+(ox+sx)];
                                    //int pixel_new_sample = bitmap_new_cache_pixels[(y_new+sy-bitmap_new_cache_y)*width+(x_new+sx)];
                                    int pixel_new_sample = bitmap_new_cache_pixels[(y_rel_bitmap_new_cache+sy)*width+(x_new+sx)];
                                    pixel_new_sample_fr = (float)((pixel_new_sample >> 16) & 0xFF);
                                    pixel_new_sample_fg = (float)((pixel_new_sample >> 8) & 0xFF);
                                    pixel_new_sample_fb = (float)(pixel_new_sample & 0xFF);
                                }

                                float diff_r = pixel_orig_fr - pixel_new_sample_fr;
                                float diff_g = pixel_orig_fg - pixel_new_sample_fg;
                                float diff_b = pixel_orig_fb - pixel_new_sample_fb;
                                L += diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;
                            }*/

                            // unroll loop for performance:

                            int pixel_orig;
                            float pixel_orig_fr, pixel_orig_fg, pixel_orig_fb;
                            int pixel_new_sample;
                            float pixel_new_sample_fr, pixel_new_sample_fg, pixel_new_sample_fb;
                            float diff_r, diff_g, diff_b;

                            pixel_orig = bitmap_orig_cache_pixels[(y_rel_bitmap_orig_cache-2)*width+(x-2)];
                            pixel_orig_fr = (float)((pixel_orig >> 16) & 0xFF);
                            pixel_orig_fg = (float)((pixel_orig >> 8) & 0xFF);
                            pixel_orig_fb = (float)(pixel_orig & 0xFF);
                            pixel_new_sample = bitmap_new_cache_pixels[(y_rel_bitmap_new_cache-2)*width+(x_new-2)];
                            pixel_new_sample_fr = (float)((pixel_new_sample >> 16) & 0xFF);
                            pixel_new_sample_fg = (float)((pixel_new_sample >> 8) & 0xFF);
                            pixel_new_sample_fb = (float)(pixel_new_sample & 0xFF);
                            diff_r = pixel_orig_fr - pixel_new_sample_fr;
                            diff_g = pixel_orig_fg - pixel_new_sample_fg;
                            diff_b = pixel_orig_fb - pixel_new_sample_fb;
                            L += diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;

                            pixel_orig = bitmap_orig_cache_pixels[(y_rel_bitmap_orig_cache-2)*width+(x+2)];
                            pixel_orig_fr = (float)((pixel_orig >> 16) & 0xFF);
                            pixel_orig_fg = (float)((pixel_orig >> 8) & 0xFF);
                            pixel_orig_fb = (float)(pixel_orig & 0xFF);
                            pixel_new_sample = bitmap_new_cache_pixels[(y_rel_bitmap_new_cache-2)*width+(x_new+2)];
                            pixel_new_sample_fr = (float)((pixel_new_sample >> 16) & 0xFF);
                            pixel_new_sample_fg = (float)((pixel_new_sample >> 8) & 0xFF);
                            pixel_new_sample_fb = (float)(pixel_new_sample & 0xFF);
                            diff_r = pixel_orig_fr - pixel_new_sample_fr;
                            diff_g = pixel_orig_fg - pixel_new_sample_fg;
                            diff_b = pixel_orig_fb - pixel_new_sample_fb;
                            L += diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;

                            pixel_orig = bitmap_orig_cache_pixels[(y_rel_bitmap_orig_cache)*width+(x)];
                            pixel_orig_fr = (float)((pixel_orig >> 16) & 0xFF);
                            pixel_orig_fg = (float)((pixel_orig >> 8) & 0xFF);
                            pixel_orig_fb = (float)(pixel_orig & 0xFF);
                            pixel_new_sample_fr = pixel_new_fr;
                            pixel_new_sample_fg = pixel_new_fg;
                            pixel_new_sample_fb = pixel_new_fb;
                            diff_r = pixel_orig_fr - pixel_new_sample_fr;
                            diff_g = pixel_orig_fg - pixel_new_sample_fg;
                            diff_b = pixel_orig_fb - pixel_new_sample_fb;
                            L += diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;

                            pixel_orig = bitmap_orig_cache_pixels[(y_rel_bitmap_orig_cache+2)*width+(x-2)];
                            pixel_orig_fr = (float)((pixel_orig >> 16) & 0xFF);
                            pixel_orig_fg = (float)((pixel_orig >> 8) & 0xFF);
                            pixel_orig_fb = (float)(pixel_orig & 0xFF);
                            pixel_new_sample = bitmap_new_cache_pixels[(y_rel_bitmap_new_cache+2)*width+(x_new-2)];
                            pixel_new_sample_fr = (float)((pixel_new_sample >> 16) & 0xFF);
                            pixel_new_sample_fg = (float)((pixel_new_sample >> 8) & 0xFF);
                            pixel_new_sample_fb = (float)(pixel_new_sample & 0xFF);
                            diff_r = pixel_orig_fr - pixel_new_sample_fr;
                            diff_g = pixel_orig_fg - pixel_new_sample_fg;
                            diff_b = pixel_orig_fb - pixel_new_sample_fb;
                            L += diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;

                            pixel_orig = bitmap_orig_cache_pixels[(y_rel_bitmap_orig_cache+2)*width+(x+2)];
                            pixel_orig_fr = (float)((pixel_orig >> 16) & 0xFF);
                            pixel_orig_fg = (float)((pixel_orig >> 8) & 0xFF);
                            pixel_orig_fb = (float)(pixel_orig & 0xFF);
                            pixel_new_sample = bitmap_new_cache_pixels[(y_rel_bitmap_new_cache+2)*width+(x_new+2)];
                            pixel_new_sample_fr = (float)((pixel_new_sample >> 16) & 0xFF);
                            pixel_new_sample_fg = (float)((pixel_new_sample >> 8) & 0xFF);
                            pixel_new_sample_fb = (float)(pixel_new_sample & 0xFF);
                            diff_r = pixel_orig_fr - pixel_new_sample_fr;
                            diff_g = pixel_orig_fg - pixel_new_sample_fg;
                            diff_b = pixel_orig_fb - pixel_new_sample_fb;
                            L += diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;

                            L /= n_pixels_c;
                        }
                        else {
                            float diff_r = pixel_avg_fr - pixel_new_fr;
                            float diff_g = pixel_avg_fg - pixel_new_fg;
                            float diff_b = pixel_avg_fb - pixel_new_fb;
                            L = diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;
                        }

                        // debug mode: only works if limited to 2 images being merged
                        /*L = sqrt(L);
                        L = fmin(L, 255.0f);
                        pixel_new_f.r = L;
                        pixel_new_f.g = L;
                        pixel_new_f.b = L;
                        return pixel_new_f;*/

                        // diff based on luminance
                        /*float value_avg = fmax(pixel_avg_f.r, pixel_avg_f.g);
                        value_avg = fmax(value_avg, pixel_avg_f.b);
                        float value_new = fmax(pixel_new_f.r, pixel_new_f.g);
                        value_new = fmax(value_new, pixel_new_f.b);
                        float diff = value_avg - value_new;
                        float L = 3.0f*diff*diff;*/
                        //L = 0.0f; // test no wiener filter

                        /*float value_avg = fmax(pixel_avg_f.r, pixel_avg_f.g);
                        value_avg = fmax(value_avg, pixel_avg_f.b);
                        float value_new = fmax(pixel_new_f.r, pixel_new_f.g);
                        value_new = fmax(value_new, pixel_new_f.b);
                        //float value = 0.5f*(value_avg + value_new)/127.5f;
                        float value = 0.5f*(value_avg + value_new);
                        value = fmax(value, 8.0f);
                        value = fmin(value, 32.0f);
                        value /= 32.0f;*/
                        //float value = 1.0f;

                        // relative scaling:
                        /*float value_avg = fmax(pixel_avg_f.r, pixel_avg_f.g);
                        value_avg = fmax(value_avg, pixel_avg_f.b);
                        float value_new = fmax(pixel_new_f.r, pixel_new_f.g);
                        value_new = fmax(value_new, pixel_new_f.b);
                        float value = 0.5*(value_avg + value_new);
                        //float value = fmax(value_avg, value_new);
                        value = fmax(value, 64.0f);
                        L *= 64.0f/value;
                        //float L_scale = 64.0f/value;
                        //L *= L_scale*L_scale;
                        */

                        //L = 0.0f; // test no deghosting
                        if( L > wiener_C_cutoff ) {
                            // error too large, so no contribution for new image pixel
                            // stick with pixel_avg
                            // reduces ghosting in: testAvg13, testAvg25, testAvg26, testAvg29, testAvg31
                        }
                        else {
                            float weight = L/(L+wiener_C); // lower weight means more averaging
                            float weight1 = 1.0f-weight;
                            pixel_new_fr = weight * pixel_avg_fr + weight1 * pixel_new_fr;
                            pixel_new_fg = weight * pixel_avg_fg + weight1 * pixel_new_fg;
                            pixel_new_fb = weight * pixel_avg_fb + weight1 * pixel_new_fb;

                            /*float weight = L/(L+wiener_C); // lower weight means more averaging
                            weight = fmin(weight, max_weight);
                            if( L > wiener_C_cutoff ) {
                                // error too large, so no contribution for new image pixel
                                // reduces ghosting in: testAvg13, testAvg25, testAvg26, testAvg29, testAvg31
                                weight = max_weight;
                            }
                            pixel_new_f = weight * pixel_avg_f + (1.0-weight) * pixel_new_f;*/

                            pixel_avg_fr = (avg_factor*pixel_avg_fr + pixel_new_fr)/avg_factorp1;
                            pixel_avg_fg = (avg_factor*pixel_avg_fg + pixel_new_fg)/avg_factorp1;
                            pixel_avg_fb = (avg_factor*pixel_avg_fb + pixel_new_fb)/avg_factorp1;
                        }
                    }

                    /*this.pixels_rgbf[3*(y*width + x)] = pixel_avg_fr;
                    this.pixels_rgbf[3*(y*width + x)+1] = pixel_avg_fg;
                    this.pixels_rgbf[3*(y*width + x)+2] = pixel_avg_fb;*/
                    this.pixels_rgbf[pixels_rgbf_indx] = pixel_avg_fr;
                    this.pixels_rgbf[pixels_rgbf_indx+1] = pixel_avg_fg;
                    this.pixels_rgbf[pixels_rgbf_indx+2] = pixel_avg_fb;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class AvgBrightenApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        //private final int [] pixels_in;
        private final float [] pixels_in_rgbf;
        private final int width, height;
        private final DROBrightenApplyFunction brighten;
        private final float median_filter_strength, black_level, white_level;
        private final float [] value_to_gamma_scale_lut = new float[256]; // look up table for performance

        AvgBrightenApplyFunction(/*int [] pixels_in,*/ float [] pixels_in_rgbf, int width, int height, float gain, float gamma, float low_x, float mid_x, float max_x, float median_filter_strength, float black_level) {
            //this.pixels_in = pixels_in;
            this.pixels_in_rgbf = pixels_in_rgbf;
            this.width = width;
            this.height = height;
            this.brighten = new DROBrightenApplyFunction(gain, gamma, low_x, mid_x, max_x);
            this.median_filter_strength = median_filter_strength;
            this.black_level = black_level;
            this.white_level = 255.0f / (255.0f - black_level);

            for(int value=0;value<256;value++) {
                float new_value =  (float)Math.pow(value/brighten.max_x, brighten.gamma) * 255.0f;
                value_to_gamma_scale_lut[value] = new_value / value;
            }
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            RGBf_luminance [] rgbf_luminances = new RGBf_luminance[5];
            for(int i=0;i<rgbf_luminances.length;i++) {
                rgbf_luminances[i] = new RGBf_luminance();
            }
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                //int indx = y*width+off_x;
                int indx = (y*width+off_x)*3;
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    /*int color = pixels_in[indx++];
                    float fr = (float)((color) & 0x3FF) / 4.0f;
                    float fg = (float)((color >> 10) & 0x3FF) / 4.0f;
                    float fb = (float)((color >> 20) & 0x3FF) / 4.0f;*/
                    float fr = pixels_in_rgbf[indx++];
                    float fg = pixels_in_rgbf[indx++];
                    float fb = pixels_in_rgbf[indx++];

                    /*int r = (int)(fr+0.5f);
                    int g = (int)(fg+0.5f);
                    int b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));*/

                    if( x > 0 && x < width-1 && y > 0 && y < height-1 )
                    {
                        // median filter for noise reduction
                        // performs better than spatial filter; reduces black/white speckles in: testAvg23,
                        // testAvg28, testAvg31, testAvg33
                        // note that one has to typically zoom to 400% to see the improvement

                        /*int color0 = pixels_in[(y-1)*width+(x)];
                        int color1 = pixels_in[(y)*width+(x-1)];
                        int color2 = color;
                        int color3 = pixels_in[(y)*width+(x+1)];
                        int color4 = pixels_in[(y+1)*width+(x)];

                        rgbf_luminances[0].setRGB101010(color0);
                        rgbf_luminances[1].setRGB101010(color1);
                        rgbf_luminances[2].setRGB101010(color2);
                        rgbf_luminances[3].setRGB101010(color3);
                        rgbf_luminances[4].setRGB101010(color4);*/

                        rgbf_luminances[0].setRGB(pixels_in_rgbf, x, y-1, width);
                        rgbf_luminances[1].setRGB(pixels_in_rgbf, x-1, y, width);
                        rgbf_luminances[2].setRGB(fr, fg, fb);
                        rgbf_luminances[3].setRGB(pixels_in_rgbf, x+1, y, width);
                        rgbf_luminances[4].setRGB(pixels_in_rgbf, x, y+1, width);

                        // if changing this code, see if the test code in UnitTest.findMedian() should be updated

                        // new faster version:
                        if( rgbf_luminances[0].lum > rgbf_luminances[1].lum ) {
                            RGBf_luminance temp_p = rgbf_luminances[0];
                            rgbf_luminances[0] = rgbf_luminances[1];
                            rgbf_luminances[1] = temp_p;
                        }
                        if( rgbf_luminances[3].lum > rgbf_luminances[4].lum ) {
                            RGBf_luminance temp_p = rgbf_luminances[3];
                            rgbf_luminances[3] = rgbf_luminances[4];
                            rgbf_luminances[4] = temp_p;
                        }
                        if( rgbf_luminances[0].lum > rgbf_luminances[3].lum ) {
                            RGBf_luminance temp_p = rgbf_luminances[0];
                            rgbf_luminances[0] = rgbf_luminances[3];
                            rgbf_luminances[3] = temp_p;

                            temp_p = rgbf_luminances[1];
                            rgbf_luminances[1] = rgbf_luminances[4];
                            rgbf_luminances[4] = temp_p;
                        }
                        if( rgbf_luminances[1].lum > rgbf_luminances[2].lum ) {
                            if( rgbf_luminances[2].lum > rgbf_luminances[3].lum ) {
                                if( rgbf_luminances[2].lum > rgbf_luminances[4].lum ) {
                                    RGBf_luminance temp_p = rgbf_luminances[2];
                                    rgbf_luminances[2] = rgbf_luminances[4];
                                    rgbf_luminances[4] = temp_p;
                                }
                                // else median is rgbf_luminances[2]
                            }
                            else {
                                if( rgbf_luminances[1].lum > rgbf_luminances[3].lum ) {
                                    RGBf_luminance temp_p = rgbf_luminances[2];
                                    rgbf_luminances[2] = rgbf_luminances[3];
                                    rgbf_luminances[3] = temp_p;
                                }
                                else {
                                    RGBf_luminance temp_p = rgbf_luminances[2];
                                    rgbf_luminances[2] = rgbf_luminances[1];
                                    rgbf_luminances[1] = temp_p;
                                }
                            }
                        }
                        else {
                            if( rgbf_luminances[1].lum > rgbf_luminances[3].lum ) {
                                if( rgbf_luminances[1].lum > rgbf_luminances[4].lum ) {
                                    RGBf_luminance temp_p = rgbf_luminances[2];
                                    rgbf_luminances[2] = rgbf_luminances[4];
                                    rgbf_luminances[4] = temp_p;
                                }
                                else {
                                    RGBf_luminance temp_p = rgbf_luminances[2];
                                    rgbf_luminances[2] = rgbf_luminances[1];
                                    rgbf_luminances[1] = temp_p;
                                }
                            }
                            else {
                                if( rgbf_luminances[2].lum > rgbf_luminances[3].lum ) {
                                    RGBf_luminance temp_p = rgbf_luminances[2];
                                    rgbf_luminances[2] = rgbf_luminances[3];
                                    rgbf_luminances[3] = temp_p;
                                }
                                // else median is rgbf_luminances[2]
                            }
                        }

                        // original slower version:
                        /*if( rgbf_luminances[0].lum > rgbf_luminances[1].lum ) {
                            RGBf_luminance temp = rgbf_luminances[0];
                            rgbf_luminances[0] = rgbf_luminances[1];
                            rgbf_luminances[1] = temp;
                        }
                        if( rgbf_luminances[0].lum > rgbf_luminances[2].lum ) {
                            RGBf_luminance temp = rgbf_luminances[0];
                            rgbf_luminances[0] = rgbf_luminances[2];
                            rgbf_luminances[2] = temp;
                        }
                        if( rgbf_luminances[0].lum > rgbf_luminances[3].lum ) {
                            RGBf_luminance temp = rgbf_luminances[0];
                            rgbf_luminances[0] = rgbf_luminances[3];
                            rgbf_luminances[3] = temp;
                        }
                        if( rgbf_luminances[0].lum > rgbf_luminances[4].lum ) {
                            RGBf_luminance temp = rgbf_luminances[0];
                            rgbf_luminances[0] = rgbf_luminances[4];
                            rgbf_luminances[4] = temp;
                        }
                        //
                        if( rgbf_luminances[1].lum > rgbf_luminances[2].lum ) {
                            RGBf_luminance temp = rgbf_luminances[1];
                            rgbf_luminances[1] = rgbf_luminances[2];
                            rgbf_luminances[2] = temp;
                        }
                        if( rgbf_luminances[1].lum > rgbf_luminances[3].lum ) {
                            RGBf_luminance temp = rgbf_luminances[1];
                            rgbf_luminances[1] = rgbf_luminances[3];
                            rgbf_luminances[3] = temp;
                        }
                        if( rgbf_luminances[1].lum > rgbf_luminances[4].lum ) {
                            RGBf_luminance temp = rgbf_luminances[1];
                            rgbf_luminances[1] = rgbf_luminances[4];
                            rgbf_luminances[4] = temp;
                        }
                        //
                        if( rgbf_luminances[2].lum > rgbf_luminances[3].lum ) {
                            RGBf_luminance temp = rgbf_luminances[2];
                            rgbf_luminances[2] = rgbf_luminances[3];
                            rgbf_luminances[3] = temp;
                        }
                        if( rgbf_luminances[2].lum > rgbf_luminances[4].lum ) {
                            RGBf_luminance temp = rgbf_luminances[2];
                            rgbf_luminances[2] = rgbf_luminances[4];
                            rgbf_luminances[4] = temp;
                        }
                        // don't care about sorting p3 and p4
                        */

                        fr = (1.0f - median_filter_strength) * fr + median_filter_strength * rgbf_luminances[2].fr;
                        fg = (1.0f - median_filter_strength) * fg + median_filter_strength * rgbf_luminances[2].fg;
                        fb = (1.0f - median_filter_strength) * fb + median_filter_strength * rgbf_luminances[2].fb;
                    }

                    {
                        // spatial noise reduction filter, colour only
                        // if making changes to this (especially radius, C), run AvgTests - in particular, pay close
                        // attention to:
                        // testAvg6: don't want to make the postcard too blurry
                        // testAvg8: zoom in to 600%, ensure still appears reasonably sharp
                        // testAvg23: ensure we do reduce the noise, e.g., view around "vicks", without making the
                        // text blurry
                        // testAvg24: want to reduce the colour noise near the wall, but don't blur out detail, e.g.
                        // at the flowers
                        // testAvg31
                        // Also need to be careful of performance.
                        //float old_value = Math.max(fr, fg);
                        //old_value = Math.max(old_value, fb);
                        float old_value = fg; // use only green component for performance
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;
                        //int radius = 3;
                        int radius = 2;
                        int count = 0;
                        int sx = (x >= radius) ? x-radius : 0;
                        int ex = (x < width-radius) ? x+radius : width-1;
                        int sy = (y >= radius) ? y-radius : 0;
                        int ey = (y < height-radius) ? y+radius : height-1;
                        for(int cy=sy;cy<=ey;cy++) {
                            int this_indx = (cy*width+sx)*3;
                            for(int cx=sx;cx<=ex;cx++) {
                                //if( cx >= 0 && cx < width && cy >= 0 && cy < height )
                                {
                                    /*int this_pixel = pixels_in[cy*width+cx];
                                    float this_fr = (float)((this_pixel) & 0x3FF) / 4.0f;
                                    float this_fg = (float)((this_pixel >> 10) & 0x3FF) / 4.0f;
                                    float this_fb = (float)((this_pixel >> 20) & 0x3FF) / 4.0f;*/
                                    float this_fr = pixels_in_rgbf[this_indx++];
                                    float this_fg = pixels_in_rgbf[this_indx++];
                                    float this_fb = pixels_in_rgbf[this_indx++];
                                    {
                                        //float this_value = Math.max(this_fr, this_fg);
                                        //this_value = Math.max(this_value, this_fb);
                                        float this_value = this_fg; // use only green component for performance
                                        if( this_value > 0.5f ) {
                                            float scale = old_value/this_value;
                                            this_fr *= scale;
                                            this_fg *= scale;
                                            this_fb *= scale;
                                        }
                                        /*if( this_fg > 0.5f ) {
                                            float scale = fg/this_fg;
                                            this_fr *= scale;
                                            this_fg *= scale;
                                            this_fb *= scale;
                                        }*/
                                        // use a wiener filter, so that more similar pixels have greater contribution
                                        // smaller value of C means stronger filter (i.e., less averaging)
                                        // for now set at same value as standard spatial filter above
                                        //final float C = 64.0f*64.0f/8.0f;
                                        //final float C = 512.0f;
                                        //final float C = 16.0f*16.0f/8.0f;
                                        final float C = 32.0f;

                                        float diff_r = fr - this_fr;
                                        float diff_g = fg - this_fg;
                                        float diff_b = fb - this_fb;

                                        float L = diff_r*diff_r + diff_g*diff_g + diff_b*diff_b;
                                        //L = 0.0f; // test no wiener filter
                                        float weight = L/(L+C);

                                        /*float weight1 = 1.0f-weight;
                                        this_fr = weight * fr + weight1 * this_fr;
                                        this_fg = weight * fg + weight1 * this_fg;
                                        this_fb = weight * fb + weight1 * this_fb;*/

                                        // faster version:
                                        this_fr = this_fr + weight * diff_r;
                                        this_fg = this_fg + weight * diff_g;
                                        this_fb = this_fb + weight * diff_b;
                                    }
                                    sum_fr += this_fr;
                                    sum_fg += this_fg;
                                    sum_fb += this_fb;
                                    count++;
                                }
                            }
                        }

                        fr = sum_fr / count;
                        fg = sum_fg / count;
                        fb = sum_fb / count;
                    }

                    {
                        // sharpen
                        // helps: testAvg12, testAvg16, testAvg23, testAvg30, testAvg32
                        if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                            /*int color00 = pixels_in[(y-1)*width+(x-1)];
                            int color10 = pixels_in[(y-1)*width+(x)];
                            int color20 = pixels_in[(y-1)*width+(x+1)];

                            int color01 = pixels_in[(y)*width+(x-1)];
                            int color21 = pixels_in[(y)*width+(x+1)];

                            int color02 = pixels_in[(y+1)*width+(x-1)];
                            int color12 = pixels_in[(y+1)*width+(x)];
                            int color22 = pixels_in[(y+1)*width+(x+1)];

                            float fr00 = (float)((color00) & 0x3FF) / 4.0f;
                            float fg00 = (float)((color00 >> 10) & 0x3FF) / 4.0f;
                            float fb00 = (float)((color00 >> 20) & 0x3FF) / 4.0f;
                            float fr10 = (float)((color10) & 0x3FF) / 4.0f;
                            float fg10 = (float)((color10 >> 10) & 0x3FF) / 4.0f;
                            float fb10 = (float)((color10 >> 20) & 0x3FF) / 4.0f;
                            float fr20 = (float)((color20) & 0x3FF) / 4.0f;
                            float fg20 = (float)((color20 >> 10) & 0x3FF) / 4.0f;
                            float fb20 = (float)((color20 >> 20) & 0x3FF) / 4.0f;

                            float fr01 = (float)((color01) & 0x3FF) / 4.0f;
                            float fg01 = (float)((color01 >> 10) & 0x3FF) / 4.0f;
                            float fb01 = (float)((color01 >> 20) & 0x3FF) / 4.0f;
                            float fr21 = (float)((color21) & 0x3FF) / 4.0f;
                            float fg21 = (float)((color21 >> 10) & 0x3FF) / 4.0f;
                            float fb21 = (float)((color21 >> 20) & 0x3FF) / 4.0f;

                            float fr02 = (float)((color02) & 0x3FF) / 4.0f;
                            float fg02 = (float)((color02 >> 10) & 0x3FF) / 4.0f;
                            float fb02 = (float)((color02 >> 20) & 0x3FF) / 4.0f;
                            float fr12 = (float)((color12) & 0x3FF) / 4.0f;
                            float fg12 = (float)((color12 >> 10) & 0x3FF) / 4.0f;
                            float fb12 = (float)((color12 >> 20) & 0x3FF) / 4.0f;
                            float fr22 = (float)((color22) & 0x3FF) / 4.0f;
                            float fg22 = (float)((color22 >> 10) & 0x3FF) / 4.0f;
                            float fb22 = (float)((color22 >> 20) & 0x3FF) / 4.0f;*/

                            int indx00 = ((y-1)*width+(x-1))*3;
                            int indx10 = ((y-1)*width+(x))*3;
                            int indx20 = ((y-1)*width+(x+1))*3;

                            int indx01 = ((y)*width+(x-1))*3;
                            int indx21 = ((y)*width+(x+1))*3;

                            int indx02 = ((y+1)*width+(x-1))*3;
                            int indx12 = ((y+1)*width+(x))*3;
                            int indx22 = ((y+1)*width+(x+1))*3;

                            float fr00 = pixels_in_rgbf[indx00];
                            float fg00 = pixels_in_rgbf[indx00+1];
                            float fb00 = pixels_in_rgbf[indx00+2];
                            float fr10 = pixels_in_rgbf[indx10];
                            float fg10 = pixels_in_rgbf[indx10+1];
                            float fb10 = pixels_in_rgbf[indx10+2];
                            float fr20 = pixels_in_rgbf[indx20];
                            float fg20 = pixels_in_rgbf[indx20+1];
                            float fb20 = pixels_in_rgbf[indx20+2];

                            float fr01 = pixels_in_rgbf[indx01];
                            float fg01 = pixels_in_rgbf[indx01+1];
                            float fb01 = pixels_in_rgbf[indx01+2];
                            float fr21 = pixels_in_rgbf[indx21];
                            float fg21 = pixels_in_rgbf[indx21+1];
                            float fb21 = pixels_in_rgbf[indx21+2];

                            float fr02 = pixels_in_rgbf[indx02];
                            float fg02 = pixels_in_rgbf[indx02+1];
                            float fb02 = pixels_in_rgbf[indx02+2];
                            float fr12 = pixels_in_rgbf[indx12];
                            float fg12 = pixels_in_rgbf[indx12+1];
                            float fb12 = pixels_in_rgbf[indx12+2];
                            float fr22 = pixels_in_rgbf[indx22];
                            float fg22 = pixels_in_rgbf[indx22+1];
                            float fb22 = pixels_in_rgbf[indx22+2];

                            float blurred_fr = (fr00 + fr10 + fr20 + fr01 + 8.0f*fr + fr21 + fr02 + fr12 + fr22)/16.0f;
                            float blurred_fg = (fg00 + fg10 + fg20 + fg01 + 8.0f*fg + fg21 + fg02 + fg12 + fg22)/16.0f;
                            float blurred_fb = (fb00 + fb10 + fb20 + fb01 + 8.0f*fb + fb21 + fb02 + fb12 + fb22)/16.0f;
                            float shift_fr = 1.5f * (fr-blurred_fr);
                            float shift_fg = 1.5f * (fg-blurred_fg);
                            float shift_fb = 1.5f * (fb-blurred_fb);
                            final float threshold2 = 8*8;
                            if( shift_fr*shift_fr + shift_fg*shift_fg + shift_fb*shift_fb > threshold2 )
                            {
                                fr += shift_fr;
                                fg += shift_fg;
                                fb += shift_fb;
                            }

                            fr = Math.max(0.0f, Math.min(255.0f, fr));
                            fg = Math.max(0.0f, Math.min(255.0f, fg));
                            fb = Math.max(0.0f, Math.min(255.0f, fb));
                        }
                    }

                    fr = fr - black_level;
                    fg = fg - black_level;
                    fb = fb - black_level;
                    fr *= white_level;
                    fg *= white_level;
                    fb *= white_level;
                    fr = Math.max(0.0f, Math.min(255.0f, fr));
                    fg = Math.max(0.0f, Math.min(255.0f, fg));
                    fb = Math.max(0.0f, Math.min(255.0f, fb));

                    float value = Math.max(fr, fg);
                    value = Math.max(value, fb);

                    // apply piecewise function of gain vs gamma
                    if( value <= brighten.low_x ) {
                        // don't scale
                    }
                    else if( value <= brighten.mid_x ) {
                        //float alpha = (value-low_x)/(mid_x-low_x);
                        //float new_value = (1.0-alpha)*low_x + alpha*gain*mid_x;
                        // gain_A and gain_B should be set so that new_value meets the commented out code above
                        // This code is critical for performance!

                        float scale = (brighten.gain_A + brighten.gain_B/value);
                        fr *= scale;
                        fg *= scale;
                        fb *= scale;
                    }
                    else {
                        // use LUT for performance
                        /*float new_value =  (float)Math.pow(value/brighten.max_x, brighten.gamma) * 255.0f;
                        float gamma_scale = new_value / value;*/
                        float gamma_scale = value_to_gamma_scale_lut[(int)(value+0.5f)];

                        fr *= gamma_scale;
                        fg *= gamma_scale;
                        fb *= gamma_scale;
                    }

                    int r = (int)(fr+0.5f);
                    int g = (int)(fg+0.5f);
                    int b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
            /*int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }*/
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class HDRApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final HDRProcessor.TonemappingAlgorithm tonemap_algorithm;
        private final float tonemap_scale; // for Reinhard
        private final float W; // for FU2
        private final float linear_scale;
        private final Bitmap bitmap0;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap0;
        private final Bitmap bitmap2;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap2;
        final int offset_x0;
        final int offset_y0;
        final int offset_x2;
        final int offset_y2;
        final int width;
        final int height;
        float [] parameter_A;
        float [] parameter_B;

        HDRApplyFunction(HDRProcessor.TonemappingAlgorithm tonemap_algorithm, float tonemap_scale, float W, float linear_scale, Bitmap bitmap0, Bitmap bitmap2, int offset_x0, int offset_y0, int offset_x2, int offset_y2, int width, int height, float [] parameter_A, float [] parameter_B) {
            this.tonemap_algorithm = tonemap_algorithm;
            this.tonemap_scale = tonemap_scale;
            this.W = W;
            this.linear_scale = linear_scale;
            this.bitmap0 = bitmap0;
            this.bitmap2 = bitmap2;
            this.offset_x0 = offset_x0;
            this.offset_y0 = offset_y0;
            this.offset_x2 = offset_x2;
            this.offset_y2 = offset_y2;
            this.width = width;
            this.height = height;

            if( parameter_A.length != parameter_B.length ) {
                throw new RuntimeException("unequal parameter lengths");
            }
            this.parameter_A = new float[parameter_A.length];
            System.arraycopy(parameter_A, 0, this.parameter_A, 0, parameter_A.length);
            this.parameter_B = new float[parameter_B.length];
            System.arraycopy(parameter_B, 0, this.parameter_B, 0, parameter_B.length);
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap0 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            if( bitmap2 != null )
                fast_bitmap2 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            for(int i=0;i<n_threads;i++) {
                fast_bitmap0[i] = new JavaImageProcessing.FastAccessBitmap(bitmap0);
                if( bitmap2 != null )
                    fast_bitmap2[i] = new JavaImageProcessing.FastAccessBitmap(bitmap2);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        private static float FU2Tonemap(float x) {
            final float A = 0.15f;
            final float B = 0.50f;
            final float C = 0.10f;
            final float D = 0.20f;
            final float E = 0.02f;
            final float F = 0.30f;
            return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
        }

        void tonemap(int[] out, float hdr_r, float hdr_g, float hdr_b) {
            // tonemap
            switch( tonemap_algorithm )
            {
                case TONEMAPALGORITHM_CLAMP:
                {
                    // Simple clamp
                    int r = (int)(hdr_r+0.5f);
                    int g = (int)(hdr_g+0.5f);
                    int b = (int)(hdr_b+0.5f);
                    r = Math.min(r, 255);
                    g = Math.min(g, 255);
                    b = Math.min(b, 255);
                    out[0] = r;
                    out[1] = g;
                    out[2] = b;
                    break;
                }
                case TONEMAPALGORITHM_EXPONENTIAL:
                {
                    // for Exponential; should match setting in HDRProcessor.java:
                    final float exposure = 1.2f;
                    float out_fr = (float)(linear_scale * 255.0f * (1.0 - Math.exp( - exposure * hdr_r / 255.0f )));
                    float out_fg = (float)(linear_scale * 255.0f * (1.0 - Math.exp( - exposure * hdr_g / 255.0f )));
                    float out_fb = (float)(linear_scale * 255.0f * (1.0 - Math.exp( - exposure * hdr_b / 255.0f )));
                    out[0] = (int)Math.max(Math.min(out_fr+0.5f, 255.0f), 0.0f);
                    out[1] = (int)Math.max(Math.min(out_fg+0.5f, 255.0f), 0.0f);
                    out[2] = (int)Math.max(Math.min(out_fb+0.5f, 255.0f), 0.0f);
                    break;
                }
                case TONEMAPALGORITHM_REINHARD:
                {
                    float value = Math.max(hdr_r, hdr_g);
                    value = Math.max(value, hdr_b);
                    float scale = 255.0f / ( tonemap_scale + value );
                    scale *= linear_scale;
                    // shouldn't need to clamp - linear_scale should be such that values don't map to more than 255
                    out[0] = (int)(scale * hdr_r + 0.5f);
                    out[1] = (int)(scale * hdr_g + 0.5f);
                    out[2] = (int)(scale * hdr_b + 0.5f);
                    /*float3 out_f = scale * hdr;
                    out.r = (int)clamp(out_f.r+0.5f, 0.0f, 255.0f);
                    out.g = (int)clamp(out_f.g+0.5f, 0.0f, 255.0f);
                    out.b = (int)clamp(out_f.b+0.5f, 0.0f, 255.0f);*/
                    /*int test_r = (int)(scale * hdr_r + 0.5f);
                    int test_g = (int)(scale * hdr_g + 0.5f);
                    int test_b = (int)(scale * hdr_b + 0.5f);
                    if( test_r > 255 || test_g > 255 || test_b > 255 ) {
                        out.r = 255;
                        out.g = 0;
                        out.b = 255;
                    }*/
                    break;
                }
                case TONEMAPALGORITHM_FU2:
                {
                    // FU2 (Filmic)
                    // for FU2; should match setting in HDRProcessor.java:
                    final float fu2_exposure_bias = 2.0f / 255.0f;
                    float white_scale = 255.0f / FU2Tonemap(W);
                    float curr_r = FU2Tonemap(fu2_exposure_bias * hdr_r);
                    float curr_g = FU2Tonemap(fu2_exposure_bias * hdr_g);
                    float curr_b = FU2Tonemap(fu2_exposure_bias * hdr_b);
                    curr_r *= white_scale;
                    curr_g *= white_scale;
                    curr_b *= white_scale;
                    out[0] = (int)Math.max(Math.min(curr_r+0.5f, 255.0f), 0.0f);
                    out[1] = (int)Math.max(Math.min(curr_g+0.5f, 255.0f), 0.0f);
                    out[2] = (int)Math.max(Math.min(curr_b+0.5f, 255.0f), 0.0f);
                    break;
                }
                case TONEMAPALGORITHM_ACES:
                {
                    // https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/ (released under public domain cc0)
                    final float a = 2.51f;
                    final float b = 0.03f;
                    final float c = 2.43f;
                    final float d = 0.59f;
                    final float e = 0.14f;
                    float xr = hdr_r/255.0f;
                    float xg = hdr_g/255.0f;
                    float xb = hdr_b/255.0f;
                    float out_fr = 255.0f * (xr*(a*xr+b))/(xr*(c*xr+d)+e);
                    float out_fg = 255.0f * (xg*(a*xg+b))/(xg*(c*xg+d)+e);
                    float out_fb = 255.0f * (xb*(a*xb+b))/(xb*(c*xb+d)+e);
                    out[0] = (int)Math.max(Math.min(out_fr+0.5f, 255.0f), 0.0f);
                    out[1] = (int)Math.max(Math.min(out_fg+0.5f, 255.0f), 0.0f);
                    out[2] = (int)Math.max(Math.min(out_fb+0.5f, 255.0f), 0.0f);
                    break;
                }
            }
            //return out;
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            // although we could move temp_rgb to a class member for performance, remember we'd have to have a version per-thread
            final int [] temp_rgb = new int[3];

            //final int max_bitmaps_c = 3;
            //int n_bitmaps = 3;
            //final int mid_indx = (n_bitmaps-1)/2;
            //int pixels_r[max_bitmaps_c];
            //int pixels_g[max_bitmaps_c];
            //int pixels_b[max_bitmaps_c];
            int pixel0_r, pixel0_g, pixel0_b;
            int pixel1_r, pixel1_g, pixel1_b;
            int pixel2_r, pixel2_g, pixel2_b;

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                fast_bitmap0[thread_index].ensureCache(y+offset_y0, y+offset_y0); // force cache to cover rows needed by this row
                int bitmap0_cache_y = fast_bitmap0[thread_index].getCacheY();
                int y_rel_bitmap0_cache = y-bitmap0_cache_y;
                int [] bitmap0_cache_pixels = fast_bitmap0[thread_index].getCachedPixelsI();

                fast_bitmap2[thread_index].ensureCache(y+offset_y2, y+offset_y2); // force cache to cover rows needed by this row
                int bitmap2_cache_y = fast_bitmap2[thread_index].getCacheY();
                int y_rel_bitmap2_cache = y-bitmap2_cache_y;
                int [] bitmap2_cache_pixels = fast_bitmap2[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    float this_parameter_A0 = parameter_A[0], this_parameter_B0 = parameter_B[0];
                    float this_parameter_A1 = parameter_A[1], this_parameter_B1 = parameter_B[1];
                    float this_parameter_A2 = parameter_A[2], this_parameter_B2 = parameter_B[2];

                    // middle image is not offset
                    int pixel1 = pixels[c];
                    pixel1_r = (pixel1 >> 16) & 0xFF;
                    pixel1_g = (pixel1 >> 8) & 0xFF;
                    pixel1_b = pixel1 & 0xFF;

                    if( x+offset_x0 >= 0 && y+offset_y0 >= 0 && x+offset_x0 < width && y+offset_y0 < height ) {
                        //int pixel0 = fast_bitmap0[thread_index].getPixel(x+offset_x0, y+offset_y0);
                        int pixel0 = bitmap0_cache_pixels[(y_rel_bitmap0_cache+offset_y0)*width+(x+offset_x0)];
                        pixel0_r = (pixel0 >> 16) & 0xFF;
                        pixel0_g = (pixel0 >> 8) & 0xFF;
                        pixel0_b = pixel0 & 0xFF;
                    }
                    else {
                        pixel0_r = pixel1_r;
                        pixel0_g = pixel1_g;
                        pixel0_b = pixel1_b;
                        this_parameter_A0 = this_parameter_A1;
                        this_parameter_B0 = this_parameter_B1;
                    }

                    if( x+offset_x2 >= 0 && y+offset_y2 >= 0 && x+offset_x2 < width && y+offset_y2 < height ) {
                        //int pixel2 = fast_bitmap2[thread_index].getPixel(x+offset_x2, y+offset_y2);
                        int pixel2 = bitmap2_cache_pixels[(y_rel_bitmap2_cache+offset_y2)*width+(x+offset_x2)];
                        pixel2_r = (pixel2 >> 16) & 0xFF;
                        pixel2_g = (pixel2 >> 8) & 0xFF;
                        pixel2_b = pixel2 & 0xFF;
                    }
                    else {
                        pixel2_r = pixel1_r;
                        pixel2_g = pixel1_g;
                        pixel2_b = pixel1_b;
                        this_parameter_A2 = this_parameter_A1;
                        this_parameter_B2 = this_parameter_B1;
                    }

                    float hdr_r = 0.0f;
                    float hdr_g = 0.0f;
                    float hdr_b = 0.0f;
                    float sum_weight = 0.0f;

                    // assumes 3 bitmaps, with middle bitmap being the "base" exposure, and first image being darker, third image being brighter
                    {
                        final float safe_range_c = 96.0f;
                        float rgb_r = pixel1_r;
                        float rgb_g = pixel1_g;
                        float rgb_b = pixel1_b;
                        float avg = (rgb_r+rgb_g+rgb_b) / 3.0f;
                        // avoid Math.abs as this line seems costly for performance:
                        //float diff = Math.abs( avg - 127.5f );
                        float weight = 1.0f;
                        if( avg <= 127.5f ) {
                            // We now intentionally have the weights be non-symmetric, and have the weight fall to 0
                            // faster for dark pixels than bright pixels. This fixes ghosting problems of testHDR62,
                            // where we have very dark regions where we get ghosting between the middle and bright
                            // images, and the image is too dark for the deghosting algorithm below to resolve this.
                            // We're better off using smaller weight, so that more of the pixel comes from the
                            // bright image.
                            // This also gives improved lighting/colour in: testHDR1, testHDR2, testHDR11,
                            // testHDR12, testHDR21, testHDR52.
                            final float range_low_c = 32.0f;
                            final float range_high_c = 48.0f;
                            if( avg <= range_low_c ) {
                                weight = 0.0f;
                            }
                            else if( avg <= range_high_c ) {
                                weight = (avg - range_low_c) / (range_high_c - range_low_c);
                            }
                        }
                        else if( (avg - 127.5f)  > safe_range_c ) {
                            // scaling chosen so that 0 and 255 map to a non-zero weight of 0.01
                            weight = 1.0f - 0.99f * ((avg - 127.5f)  - safe_range_c) / (127.5f - safe_range_c);
                        }

                        // response function
                        rgb_r = this_parameter_A1 * rgb_r + this_parameter_B1;
                        rgb_g = this_parameter_A1 * rgb_g + this_parameter_B1;
                        rgb_b = this_parameter_A1 * rgb_b + this_parameter_B1;

                        hdr_r += weight * rgb_r;
                        hdr_g += weight * rgb_g;
                        hdr_b += weight * rgb_b;
                        sum_weight += weight;

                        if( weight < 1.0 ) {
                            float base_rgb_r = rgb_r;
                            float base_rgb_g = rgb_g;
                            float base_rgb_b = rgb_b;

                            // now look at a neighbour image
                            weight = 1.0f - weight;

                            if( avg <= 127.5f ) {
                                rgb_r = pixel2_r;
                                rgb_g = pixel2_g;
                                rgb_b = pixel2_b;
                                /* In some cases it can be that even on the neighbour image, the brightness is too
                                   dark/bright - but it should still be a better choice than the base image.
                                   If we change this (including say for handling more than 3 images), need to be
                                   careful of unpredictable effects. In particular, consider a pixel that is brightness
                                   255 on the base image. As the brightness on the neighbour image increases, we
                                   should expect that the resultant image also increases (or at least, doesn't
                                   decrease). See testHDR36 for such an example.
                                   */
                                /*avg = (rgb.r+rgb.g+rgb.b) / 3.0f;
                                diff = fabs( avg - 127.5f );
                                if( diff > safe_range_c ) {
                                    // scaling chosen so that 0 and 255 map to a non-zero weight of 0.01
                                    weight *= 1.0f - 0.99f * (diff - safe_range_c) / (127.5f - safe_range_c);
                                }*/

                                rgb_r = this_parameter_A2 * rgb_r + this_parameter_B2;
                                rgb_g = this_parameter_A2 * rgb_g + this_parameter_B2;
                                rgb_b = this_parameter_A2 * rgb_b + this_parameter_B2;
                            }
                            else {
                                rgb_r = pixel0_r;
                                rgb_g = pixel0_g;
                                rgb_b = pixel0_b;
                                // see note above for why this is commented out
                                /*avg = (rgb.r+rgb.g+rgb.b) / 3.0f;
                                diff = fabs( avg - 127.5f );
                                if( diff > safe_range_c ) {
                                    // scaling chosen so that 0 and 255 map to a non-zero weight of 0.01
                                    weight *= 1.0f - 0.99f * (diff - safe_range_c) / (127.5f - safe_range_c);
                                }*/

                                rgb_r = this_parameter_A0 * rgb_r + this_parameter_B0;
                                rgb_g = this_parameter_A0 * rgb_g + this_parameter_B0;
                                rgb_b = this_parameter_A0 * rgb_b + this_parameter_B0;
                            }

                            float value = Math.max(rgb_r, rgb_g);
                            value = Math.max(value, rgb_b);
                            if( value <= 250.0f )
                            {
                                // deghosting
                                // for overexposed pixels, we don't have a reliable value for that pixel, so we can't distinguish between
                                // pixels that are overexposed, and those that need deghosting, so we limit to value <= 250.0f
                                // tests that benefit from deghosting for dark pixels: testHDR2, testHDR9, testHDR19, testHDR21, testHDR30,
                                // testHDR35, testHDR37, testHDR40, testHDR41, testHDR42, testHDR44
                                // tests that benefit from deghosting for bright pixels: testHDR2, testHDR41, testHDR42
                                // for 127.5-avg = 96.0, we want wiener_C = wiener_C_lo
                                // for 127.5-avg = 127.5f, we want wiener_C = wiener_C_hi
                                final float wiener_C_lo = 2000.0f;
                                final float wiener_C_hi = 8000.0f;
                                float wiener_C = wiener_C_lo; // higher value means more HDR but less ghosting
                                float xx = Math.abs( value - 127.5f ) - 96.0f;
                                if( xx > 0.0f ) {
                                    final float scale = (wiener_C_hi-wiener_C_lo)/(127.5f-96.0f);
                                    wiener_C = wiener_C_lo + xx*scale;
                                }
                                float diff_r = base_rgb_r - rgb_r;
                                float diff_g = base_rgb_g - rgb_g;
                                float diff_b = base_rgb_b - rgb_b;
                                float L = (diff_r*diff_r) + (diff_g*diff_g) + (diff_b*diff_b);
                                float ghost_weight = L/(L+wiener_C);
                                rgb_r = ghost_weight * base_rgb_r + (1.0f-ghost_weight) * rgb_r;
                                rgb_g = ghost_weight * base_rgb_g + (1.0f-ghost_weight) * rgb_g;
                                rgb_b = ghost_weight * base_rgb_b + (1.0f-ghost_weight) * rgb_b;
                            }

                            hdr_r += weight * rgb_r;
                            hdr_g += weight * rgb_g;
                            hdr_b += weight * rgb_b;
                            sum_weight += weight;

                            // testing: make all non-safe images purple:
                            //hdr_r = 255;
                            //hdr_g = 0;
                            //hdr_b = 255;
                        }
                    }

                    hdr_r /= sum_weight;
                    hdr_g /= sum_weight;
                    hdr_b /= sum_weight;

                    tonemap(temp_rgb, hdr_r, hdr_g, hdr_b);
                    /*{
                        float value = Math.max(hdr_r, hdr_g);
                        value = Math.max(value, hdr_b);
                        float scale = 255.0f / ( tonemap_scale + value );
                        scale *= linear_scale;
                        // shouldn't need to clamp - linear_scale should be such that values don't map to more than 255
                        temp_rgb[0] = (int)(scale * hdr_r + 0.5f);
                        temp_rgb[1] = (int)(scale * hdr_g + 0.5f);
                        temp_rgb[2] = (int)(scale * hdr_b + 0.5f);
                    }*/

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (temp_rgb[0] << 16) | (temp_rgb[1] << 8) | temp_rgb[2];
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class HDRNApplyFunction extends HDRApplyFunction {
        private final int n_bitmaps;
        private final Bitmap bitmap1;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap1;
        private final Bitmap bitmap3;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap3;
        private final Bitmap bitmap4;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap4;
        private final Bitmap bitmap5;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap5;
        private final Bitmap bitmap6;
        JavaImageProcessing.FastAccessBitmap [] fast_bitmap6;
        final int offset_x1;
        final int offset_y1;
        final int offset_x3;
        final int offset_y3;
        final int offset_x4;
        final int offset_y4;
        final int offset_x5;
        final int offset_y5;
        final int offset_x6;
        final int offset_y6;

        HDRNApplyFunction(HDRProcessor.TonemappingAlgorithm tonemap_algorithm, float tonemap_scale, float W, float linear_scale, List<Bitmap> bitmaps, int [] offsets_x, int [] offsets_y, int width, int height, float [] parameter_A, float [] parameter_B) {
            super(tonemap_algorithm, tonemap_scale, W, linear_scale, bitmaps.get(0), bitmaps.size() > 2 ? bitmaps.get(2) : null, offsets_x[0], offsets_y[0], offsets_x.length > 2 ? offsets_x[2] : 0, offsets_y.length > 2 ? offsets_y[2] : 0, width, height, parameter_A, parameter_B);

            this.n_bitmaps = bitmaps.size();
            if( n_bitmaps < 2 || n_bitmaps > 7 ) {
                throw new RuntimeException("n_bitmaps not supported: " + n_bitmaps);
            }
            else if( offsets_x.length != n_bitmaps ) {
                throw new RuntimeException("offsets_x unexpected length: " + offsets_x.length);
            }
            else if( offsets_y.length != n_bitmaps ) {
                throw new RuntimeException("offsets_y unexpected length: " + offsets_y.length);
            }

            this.bitmap1 = bitmaps.get(1);
            this.bitmap3 = n_bitmaps > 3 ? bitmaps.get(3) : null;
            this.bitmap4 = n_bitmaps > 4 ? bitmaps.get(4) : null;
            this.bitmap5 = n_bitmaps > 5 ? bitmaps.get(5) : null;
            this.bitmap6 = n_bitmaps > 6 ? bitmaps.get(6) : null;

            this.offset_x1 = offsets_x[1];
            this.offset_y1 = offsets_y[1];
            this.offset_x3 = n_bitmaps > 3 ? offsets_x[3] : 0;
            this.offset_y3 = n_bitmaps > 3 ? offsets_y[3] : 0;
            this.offset_x4 = n_bitmaps > 4 ? offsets_x[4] : 0;
            this.offset_y4 = n_bitmaps > 4 ? offsets_y[4] : 0;
            this.offset_x5 = n_bitmaps > 5 ? offsets_x[5] : 0;
            this.offset_y5 = n_bitmaps > 5 ? offsets_y[5] : 0;
            this.offset_x6 = n_bitmaps > 6 ? offsets_x[6] : 0;
            this.offset_y6 = n_bitmaps > 6 ? offsets_y[6] : 0;

            if( parameter_A.length != n_bitmaps || parameter_B.length != n_bitmaps ) {
                throw new RuntimeException("unexpected parameter lengths");
            }
        }

        @Override
        public void init(int n_threads) {
            super.init(n_threads);

            if( bitmap1 != null )
                fast_bitmap1 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            if( bitmap3 != null )
                fast_bitmap3 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            if( bitmap4 != null )
                fast_bitmap4 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            if( bitmap5 != null )
                fast_bitmap5 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            if( bitmap6 != null )
                fast_bitmap6 = new JavaImageProcessing.FastAccessBitmap[n_threads];
            for(int i=0;i<n_threads;i++) {
                if( bitmap1 != null )
                    fast_bitmap1[i] = new JavaImageProcessing.FastAccessBitmap(bitmap1);
                if( bitmap3 != null )
                    fast_bitmap3[i] = new JavaImageProcessing.FastAccessBitmap(bitmap3);
                if( bitmap4 != null )
                    fast_bitmap4[i] = new JavaImageProcessing.FastAccessBitmap(bitmap4);
                if( bitmap5 != null )
                    fast_bitmap5[i] = new JavaImageProcessing.FastAccessBitmap(bitmap5);
                if( bitmap6 != null )
                    fast_bitmap6[i] = new JavaImageProcessing.FastAccessBitmap(bitmap6);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            int mid_indx = (n_bitmaps-1)/2; // round down to dark image for even number of bitmaps
            boolean even = n_bitmaps % 2 == 0;

            // although we could move these allocations to class members for performance, remember we'd have to have versions per-thread
            int [] pixels_r = new int[n_bitmaps];
            int [] pixels_g = new int[n_bitmaps];
            int [] pixels_b = new int[n_bitmaps];
            float [] this_parameter_A = new float[n_bitmaps];
            float [] this_parameter_B = new float[n_bitmaps];
            final int [] temp_rgb = new int[3];

            int base_pixel_r, base_pixel_g, base_pixel_b;

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                fast_bitmap0[thread_index].ensureCache(y+offset_y0, y+offset_y0); // force cache to cover rows needed by this row
                int bitmap0_cache_y = fast_bitmap0[thread_index].getCacheY();
                int y_rel_bitmap0_cache = y-bitmap0_cache_y;
                int [] bitmap0_cache_pixels = fast_bitmap0[thread_index].getCachedPixelsI();

                fast_bitmap1[thread_index].ensureCache(y+offset_y1, y+offset_y1); // force cache to cover rows needed by this row
                int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();

                int y_rel_bitmap2_cache = 0;
                int y_rel_bitmap3_cache = 0;
                int y_rel_bitmap4_cache = 0;
                int y_rel_bitmap5_cache = 0;
                int y_rel_bitmap6_cache = 0;
                int [] bitmap2_cache_pixels = null;
                int [] bitmap3_cache_pixels = null;
                int [] bitmap4_cache_pixels = null;
                int [] bitmap5_cache_pixels = null;
                int [] bitmap6_cache_pixels = null;

                if( n_bitmaps > 2 ) {
                    fast_bitmap2[thread_index].ensureCache(y+offset_y2, y+offset_y2); // force cache to cover rows needed by this row
                    int bitmap2_cache_y = fast_bitmap2[thread_index].getCacheY();
                    y_rel_bitmap2_cache = y-bitmap2_cache_y;
                    bitmap2_cache_pixels = fast_bitmap2[thread_index].getCachedPixelsI();

                    if( n_bitmaps > 3 ) {
                        fast_bitmap3[thread_index].ensureCache(y+offset_y3, y+offset_y3); // force cache to cover rows needed by this row
                        int bitmap3_cache_y = fast_bitmap3[thread_index].getCacheY();
                        y_rel_bitmap3_cache = y-bitmap3_cache_y;
                        bitmap3_cache_pixels = fast_bitmap3[thread_index].getCachedPixelsI();

                        if( n_bitmaps > 4 ) {
                            fast_bitmap4[thread_index].ensureCache(y+offset_y4, y+offset_y4); // force cache to cover rows needed by this row
                            int bitmap4_cache_y = fast_bitmap4[thread_index].getCacheY();
                            y_rel_bitmap4_cache = y-bitmap4_cache_y;
                            bitmap4_cache_pixels = fast_bitmap4[thread_index].getCachedPixelsI();

                            if( n_bitmaps > 5 ) {
                                fast_bitmap5[thread_index].ensureCache(y+offset_y5, y+offset_y5); // force cache to cover rows needed by this row
                                int bitmap5_cache_y = fast_bitmap5[thread_index].getCacheY();
                                y_rel_bitmap5_cache = y-bitmap5_cache_y;
                                bitmap5_cache_pixels = fast_bitmap5[thread_index].getCachedPixelsI();

                                if( n_bitmaps > 6 ) {
                                    fast_bitmap6[thread_index].ensureCache(y+offset_y6, y+offset_y6); // force cache to cover rows needed by this row
                                    int bitmap6_cache_y = fast_bitmap6[thread_index].getCacheY();
                                    y_rel_bitmap6_cache = y-bitmap6_cache_y;
                                    bitmap6_cache_pixels = fast_bitmap6[thread_index].getCachedPixelsI();
                                }
                            }
                        }
                    }
                }

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    System.arraycopy(parameter_A, 0, this_parameter_A, 0, parameter_A.length);
                    System.arraycopy(parameter_B, 0, this_parameter_B, 0, parameter_B.length);

                    int base_pixel = pixels[c];
                    base_pixel_r = (base_pixel >> 16) & 0xFF;
                    base_pixel_g = (base_pixel >> 8) & 0xFF;
                    base_pixel_b = base_pixel & 0xFF;

                    if( x+offset_x0 >= 0 && y+offset_y0 >= 0 && x+offset_x0 < width && y+offset_y0 < height ) {
                        //int pixel = fast_bitmap0[thread_index].getPixel(x+offset_x0, y+offset_y0);
                        int pixel = bitmap0_cache_pixels[(y_rel_bitmap0_cache+offset_y0)*width+(x+offset_x0)];
                        pixels_r[0] = (pixel >> 16) & 0xFF;
                        pixels_g[0] = (pixel >> 8) & 0xFF;
                        pixels_b[0] = pixel & 0xFF;
                    }
                    else {
                        pixels_r[0] = base_pixel_r;
                        pixels_g[0] = base_pixel_g;
                        pixels_b[0] = base_pixel_b;
                        this_parameter_A[0] = this_parameter_A[mid_indx];
                        this_parameter_B[0] = this_parameter_B[mid_indx];
                    }

                    if( x+offset_x1 >= 0 && y+offset_y1 >= 0 && x+offset_x1 < width && y+offset_y1 < height ) {
                        //int pixel = fast_bitmap1[thread_index].getPixel(x+offset_x1, y+offset_y1);
                        int pixel = bitmap1_cache_pixels[(y_rel_bitmap1_cache+offset_y1)*width+(x+offset_x1)];
                        pixels_r[1] = (pixel >> 16) & 0xFF;
                        pixels_g[1] = (pixel >> 8) & 0xFF;
                        pixels_b[1] = pixel & 0xFF;
                    }
                    else {
                        pixels_r[1] = base_pixel_r;
                        pixels_g[1] = base_pixel_g;
                        pixels_b[1] = base_pixel_b;
                        this_parameter_A[1] = this_parameter_A[mid_indx];
                        this_parameter_B[1] = this_parameter_B[mid_indx];
                    }

                    if( n_bitmaps > 2 ) {
                        if( x+offset_x2 >= 0 && y+offset_y2 >= 0 && x+offset_x2 < width && y+offset_y2 < height ) {
                            //int pixel = fast_bitmap2[thread_index].getPixel(x+offset_x2, y+offset_y2);
                            int pixel = bitmap2_cache_pixels[(y_rel_bitmap2_cache+offset_y2)*width+(x+offset_x2)];
                            pixels_r[2] = (pixel >> 16) & 0xFF;
                            pixels_g[2] = (pixel >> 8) & 0xFF;
                            pixels_b[2] = pixel & 0xFF;
                        }
                        else {
                            pixels_r[2] = base_pixel_r;
                            pixels_g[2] = base_pixel_g;
                            pixels_b[2] = base_pixel_b;
                            this_parameter_A[2] = this_parameter_A[mid_indx];
                            this_parameter_B[2] = this_parameter_B[mid_indx];
                        }

                        if( n_bitmaps > 3 ) {
                            if( x+offset_x3 >= 0 && y+offset_y3 >= 0 && x+offset_x3 < width && y+offset_y3 < height ) {
                                //int pixel = fast_bitmap3[thread_index].getPixel(x+offset_x3, y+offset_y3);
                                int pixel = bitmap3_cache_pixels[(y_rel_bitmap3_cache+offset_y3)*width+(x+offset_x3)];
                                pixels_r[3] = (pixel >> 16) & 0xFF;
                                pixels_g[3] = (pixel >> 8) & 0xFF;
                                pixels_b[3] = pixel & 0xFF;
                            }
                            else {
                                pixels_r[3] = base_pixel_r;
                                pixels_g[3] = base_pixel_g;
                                pixels_b[3] = base_pixel_b;
                                this_parameter_A[3] = this_parameter_A[mid_indx];
                                this_parameter_B[3] = this_parameter_B[mid_indx];
                            }

                            if( n_bitmaps > 4 ) {
                                if( x+offset_x4 >= 0 && y+offset_y4 >= 0 && x+offset_x4 < width && y+offset_y4 < height ) {
                                    //int pixel = fast_bitmap4[thread_index].getPixel(x+offset_x4, y+offset_y4);
                                    int pixel = bitmap4_cache_pixels[(y_rel_bitmap4_cache+offset_y4)*width+(x+offset_x4)];
                                    pixels_r[4] = (pixel >> 16) & 0xFF;
                                    pixels_g[4] = (pixel >> 8) & 0xFF;
                                    pixels_b[4] = pixel & 0xFF;
                                }
                                else {
                                    pixels_r[4] = base_pixel_r;
                                    pixels_g[4] = base_pixel_g;
                                    pixels_b[4] = base_pixel_b;
                                    this_parameter_A[4] = this_parameter_A[mid_indx];
                                    this_parameter_B[4] = this_parameter_B[mid_indx];
                                }

                                if( n_bitmaps > 5 ) {
                                    if( x+offset_x5 >= 0 && y+offset_y5 >= 0 && x+offset_x5 < width && y+offset_y5 < height ) {
                                        //int pixel = fast_bitmap5[thread_index].getPixel(x+offset_x5, y+offset_y5);
                                        int pixel = bitmap5_cache_pixels[(y_rel_bitmap5_cache+offset_y5)*width+(x+offset_x5)];
                                        pixels_r[5] = (pixel >> 16) & 0xFF;
                                        pixels_g[5] = (pixel >> 8) & 0xFF;
                                        pixels_b[5] = pixel & 0xFF;
                                    }
                                    else {
                                        pixels_r[5] = base_pixel_r;
                                        pixels_g[5] = base_pixel_g;
                                        pixels_b[5] = base_pixel_b;
                                        this_parameter_A[5] = this_parameter_A[mid_indx];
                                        this_parameter_B[5] = this_parameter_B[mid_indx];
                                    }

                                    if( n_bitmaps > 6 ) {
                                        if( x+offset_x6 >= 0 && y+offset_y6 >= 0 && x+offset_x6 < width && y+offset_y6 < height ) {
                                            //int pixel = fast_bitmap6[thread_index].getPixel(x+offset_x6, y+offset_y6);
                                            int pixel = bitmap6_cache_pixels[(y_rel_bitmap6_cache+offset_y6)*width+(x+offset_x6)];
                                            pixels_r[6] = (pixel >> 16) & 0xFF;
                                            pixels_g[6] = (pixel >> 8) & 0xFF;
                                            pixels_b[6] = pixel & 0xFF;
                                        }
                                        else {
                                            pixels_r[6] = base_pixel_r;
                                            pixels_g[6] = base_pixel_g;
                                            pixels_b[6] = base_pixel_b;
                                            this_parameter_A[6] = this_parameter_A[mid_indx];
                                            this_parameter_B[6] = this_parameter_B[mid_indx];
                                        }
                                    }
                                }
                            }
                        }
                    }

                    float hdr_r = 0.0f;
                    float hdr_g = 0.0f;
                    float hdr_b = 0.0f;
                    float sum_weight = 0.0f;

                    // assumes from 2 to 7 bitmaps, with middle bitmap being the "base" exposure, and first images being darker, last images being brighter
                    {
                        final float safe_range_c = 96.0f;
                        float rgb_r = pixels_r[mid_indx];
                        float rgb_g = pixels_g[mid_indx];
                        float rgb_b = pixels_b[mid_indx];
                        float avg = (rgb_r+rgb_g+rgb_b) / 3.0f;
                        // avoid Math.abs as this line seems costly for performance:
                        //float diff = Math.abs( avg - 127.5f );
                        float weight = 1.0f;
                        if( avg <= 127.5f ) {
                            // see comment for corresponding code in HDRApplyFunction
                            final float range_low_c = 32.0f;
                            final float range_high_c = 48.0f;
                            if( avg <= range_low_c ) {
                                weight = 0.0f;
                            }
                            else if( avg <= range_high_c ) {
                                weight = (avg - range_low_c) / (range_high_c - range_low_c);
                            }
                        }
                        else if( (avg - 127.5f)  > safe_range_c ) {
                            // scaling chosen so that 0 and 255 map to a non-zero weight of 0.01
                            weight = 1.0f - 0.99f * ((avg - 127.5f)  - safe_range_c) / (127.5f - safe_range_c);
                        }

                        // response function
                        rgb_r = this_parameter_A[mid_indx] * rgb_r + this_parameter_B[mid_indx];
                        rgb_g = this_parameter_A[mid_indx] * rgb_g + this_parameter_B[mid_indx];
                        rgb_b = this_parameter_A[mid_indx] * rgb_b + this_parameter_B[mid_indx];

                        hdr_r += weight * rgb_r;
                        hdr_g += weight * rgb_g;
                        hdr_b += weight * rgb_b;
                        sum_weight += weight;

                        if( even ) {
                            float rgb1_r = pixels_r[mid_indx+1];
                            float rgb1_g = pixels_g[mid_indx+1];
                            float rgb1_b = pixels_b[mid_indx+1];
                            float avg1 = (rgb1_r+rgb1_g+rgb1_b) / 3.0f;
                            float diff1 = Math.abs( avg1 - 127.5f );
                            float weight1 = 1.0f;
                            if( diff1 > safe_range_c ) {
                                // scaling chosen so that 0 and 255 map to a non-zero weight of 0.01
                                weight1 = 1.0f - 0.99f * (diff1 - safe_range_c) / (127.5f - safe_range_c);
                            }
                            rgb1_r = this_parameter_A[mid_indx+1] * rgb1_r + this_parameter_B[mid_indx+1];
                            rgb1_g = this_parameter_A[mid_indx+1] * rgb1_g + this_parameter_B[mid_indx+1];
                            rgb1_b = this_parameter_A[mid_indx+1] * rgb1_b + this_parameter_B[mid_indx+1];

                            hdr_r += weight1 * rgb1_r;
                            hdr_g += weight1 * rgb1_g;
                            hdr_b += weight1 * rgb1_b;
                            sum_weight += weight1;

                            avg = (avg+avg1)/2.0f;
                            weight = (weight+weight1)/2.0f;
                        }

                        if( weight < 1.0 ) {
                            float base_rgb_r = rgb_r;
                            float base_rgb_g = rgb_g;
                            float base_rgb_b = rgb_b;

                            int adj_indx = mid_indx;
                            int step_dir = avg <= 127.5f ? 1 : -1;
                            if( even && step_dir == 1 ) {
                                adj_indx++; // so we move one beyond the middle pair of images (since mid_indx will be the darker of the pair)
                            }

                            float diff = 0.0f;
                            int n_adj = (n_bitmaps-1)/2;
                            for(int k=0;k<n_adj;k++) {

                                // now look at a neighbour image
                                weight = 1.0f - weight;
                                adj_indx += step_dir;

                                rgb_r = pixels_r[adj_indx];
                                rgb_g = pixels_g[adj_indx];
                                rgb_b = pixels_b[adj_indx];

                                if( k+1 < n_adj ) {
                                    // there will be at least one more adjacent image to look at
                                    avg = (rgb_r+rgb_g+rgb_b) / 3.0f;
                                    diff = Math.abs( avg - 127.5f );

                                    // n.b., we don't have the codepath here for "if( avg <= 127.5f )" - causes problems
                                    // for testHDR_exp5 (black blotches)
                                    if( diff > safe_range_c ) {
                                        // scaling chosen so that 0 and 255 map to a non-zero weight of 0.01
                                        weight *= 1.0f - 0.99f * (diff - safe_range_c) / (127.5f - safe_range_c);
                                    }
                                }

                                rgb_r = this_parameter_A[adj_indx] * rgb_r + this_parameter_B[adj_indx];
                                rgb_g = this_parameter_A[adj_indx] * rgb_g + this_parameter_B[adj_indx];
                                rgb_b = this_parameter_A[adj_indx] * rgb_b + this_parameter_B[adj_indx];

                                float value = Math.max(rgb_r, rgb_g);
                                value = Math.max(value, rgb_b);
                                if( value <= 250.0f )
                                {
                                    // deghosting
                                    // for overexposed pixels, we don't have a reliable value for that pixel, so we can't distinguish between
                                    // pixels that are overexposed, and those that need deghosting, so we limit to value <= 250.0f
                                    // tests that benefit from deghosting for dark pixels: testHDR2, testHDR9, testHDR19, testHDR21, testHDR30,
                                    // testHDR35, testHDR37, testHDR40, testHDR41, testHDR42, testHDR44
                                    // tests that benefit from deghosting for bright pixels: testHDR2, testHDR41, testHDR42
                                    // for 127.5-avg = 96.0, we want wiener_C = wiener_C_lo
                                    // for 127.5-avg = 127.5f, we want wiener_C = wiener_C_hi
                                    final float wiener_C_lo = 2000.0f;
                                    final float wiener_C_hi = 8000.0f;
                                    float wiener_C = wiener_C_lo; // higher value means more HDR but less ghosting
                                    float xx = Math.abs( value - 127.5f ) - 96.0f;
                                    if( xx > 0.0f ) {
                                        final float scale = (wiener_C_hi-wiener_C_lo)/(127.5f-96.0f);
                                        wiener_C = wiener_C_lo + xx*scale;
                                    }
                                    float diff_r = base_rgb_r - rgb_r;
                                    float diff_g = base_rgb_g - rgb_g;
                                    float diff_b = base_rgb_b - rgb_b;
                                    float L = (diff_r*diff_r) + (diff_g*diff_g) + (diff_b*diff_b);
                                    float ghost_weight = L/(L+wiener_C);
                                    rgb_r = ghost_weight * base_rgb_r + (1.0f-ghost_weight) * rgb_r;
                                    rgb_g = ghost_weight * base_rgb_g + (1.0f-ghost_weight) * rgb_g;
                                    rgb_b = ghost_weight * base_rgb_b + (1.0f-ghost_weight) * rgb_b;
                                }

                                hdr_r += weight * rgb_r;
                                hdr_g += weight * rgb_g;
                                hdr_b += weight * rgb_b;
                                sum_weight += weight;

                                if( diff <= safe_range_c ) {
                                    break;
                                }

                                // testing: make all non-safe images purple:
                                //hdr_r = 255;
                                //hdr_g = 0;
                                //hdr_b = 255;

                            }
                        }
                    }

                    hdr_r /= sum_weight;
                    hdr_g /= sum_weight;
                    hdr_b /= sum_weight;

                    tonemap(temp_rgb, hdr_r, hdr_g, hdr_b);

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (temp_rgb[0] << 16) | (temp_rgb[1] << 8) | temp_rgb[2];
                }
            }
        }
    }

    static class AdjustHistogramApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float hdr_alpha; // 0.0 means no change, 1.0 means fully equalise
        private final int n_tiles;
        private final int width;
        private final int height;
        private final int [] c_histogram;

        AdjustHistogramApplyFunction(float hdr_alpha, int n_tiles, int width, int height, int [] c_histogram) {
            this.hdr_alpha = hdr_alpha;
            this.n_tiles = n_tiles;
            this.width = width;
            this.height = height;
            this.c_histogram = c_histogram;
        }

        private int getEqualValue(int histogram_offset, int value) {
            int cdf_v = c_histogram[histogram_offset+value];
            int cdf_0 = c_histogram[histogram_offset];
            int n_pixels = c_histogram[histogram_offset+255];
            float num = (float)(cdf_v - cdf_0);
            float den = (float)(n_pixels - cdf_0);
            int equal_value = (int)( 255.0f * (num/den) ); // value that we should choose to fully equalise the histogram
            return equal_value;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    int value = Math.max(r, g);
                    value = Math.max(value, b);

                    float tx = ((float)x*n_tiles)/(float)width - 0.5f;
                    float ty = ((float)y*n_tiles)/(float)height - 0.5f;

                    // inline floor for performance
                    //int ix = (int)Math.floor(tx);
                    //int iy = (int)Math.floor(ty);
                    int ix = tx >= 0.0 ? (int)tx : (int)tx - 1;
                    int iy = ty >= 0.0 ? (int)ty : (int)ty - 1;
                    /*if( ix != (int)Math.floor(tx) || iy != (int)Math.floor(ty) ) {
                        throw new RuntimeException("floor error");
                    }*/
                    int equal_value;
                    if( ix >= 0 && ix < n_tiles-1 && iy >= 0 && iy < n_tiles-1 ) {
                        int histogram_offset00 = 256*(ix*n_tiles+iy);
                        int histogram_offset10 = 256*((ix+1)*n_tiles+iy);
                        int histogram_offset01 = 256*(ix*n_tiles+iy+1);
                        int histogram_offset11 = 256*((ix+1)*n_tiles+iy+1);
                        int equal_value00 = getEqualValue(histogram_offset00, value);
                        int equal_value10 = getEqualValue(histogram_offset10, value);
                        int equal_value01 = getEqualValue(histogram_offset01, value);
                        int equal_value11 = getEqualValue(histogram_offset11, value);
                        float alpha = tx - ix;
                        float beta = ty - iy;

                        float equal_value0 = (1.0f-alpha)*equal_value00 + alpha*equal_value10;
                        float equal_value1 = (1.0f-alpha)*equal_value01 + alpha*equal_value11;
                        equal_value = (int)((1.0f-beta)*equal_value0 + beta*equal_value1);
                    }
                    else if( ix >= 0 && ix < n_tiles-1 ) {
                        int this_y = (iy<0) ? iy+1 : iy;
                        int histogram_offset0 = 256*(ix*n_tiles+this_y);
                        int histogram_offset1 = 256*((ix+1)*n_tiles+this_y);
                        int equal_value0 = getEqualValue(histogram_offset0, value);
                        int equal_value1 = getEqualValue(histogram_offset1, value);
                        float alpha = tx - ix;
                        equal_value = (int)((1.0f-alpha)*equal_value0 + alpha*equal_value1);
                    }
                    else if( iy >= 0 && iy < n_tiles-1 ) {
                        int this_x = (ix<0) ? ix+1 : ix;
                        int histogram_offset0 = 256*(this_x*n_tiles+iy);
                        int histogram_offset1 = 256*(this_x*n_tiles+iy+1);
                        int equal_value0 = getEqualValue(histogram_offset0, value);
                        int equal_value1 = getEqualValue(histogram_offset1, value);
                        float beta = ty - iy;
                        equal_value = (int)((1.0f-beta)*equal_value0 + beta*equal_value1);
                    }
                    else {
                        int this_x = (ix<0) ? ix+1 : ix;
                        int this_y = (iy<0) ? iy+1 : iy;
                        int histogram_offset = 256*(this_x*n_tiles+this_y);
                        equal_value = getEqualValue(histogram_offset, value);
                    }

                    int new_value = (int)( (1.0f-hdr_alpha) * value + hdr_alpha * equal_value );

                    //float use_hdr_alpha = smart_contrast_enhancement ? hdr_alpha*((float)value/255.0f) : hdr_alpha;
                    //float use_hdr_alpha = smart_contrast_enhancement ? hdr_alpha*pow(((float)value/255.0f), 0.5f) : hdr_alpha;
                    //int new_value = (int)( (1.0f-use_hdr_alpha) * value + use_hdr_alpha * equal_value );

                    float scale = ((float)new_value) / (float)value;

                    // need to add +0.5 so that we round to nearest - particularly important as due to floating point rounding, we
                    // can end up with incorrect behaviour even when new_value==value!
                    r = Math.min(255, (int)(r * scale + 0.5f));
                    g = Math.min(255, (int)(g * scale + 0.5f));
                    b = Math.min(255, (int)(b * scale + 0.5f));
                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            byte [] pixels_out = output.getCachedPixelsB();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                    int r = pixels[c];
                    int g = pixels[c+1];
                    int b = pixels[c+2];
                    // bytes are signed!
                    if( r < 0 )
                        r += 256;
                    if( g < 0 )
                        g += 256;
                    if( b < 0 )
                        b += 256;

                    int value = Math.max(r, g);
                    value = Math.max(value, b);

                    float tx = ((float)x*n_tiles)/(float)width - 0.5f;
                    float ty = ((float)y*n_tiles)/(float)height - 0.5f;

                    // inline floor for performance
                    //int ix = (int)Math.floor(tx);
                    //int iy = (int)Math.floor(ty);
                    int ix = tx >= 0.0 ? (int)tx : (int)tx - 1;
                    int iy = ty >= 0.0 ? (int)ty : (int)ty - 1;
                    /*if( ix != (int)Math.floor(tx) || iy != (int)Math.floor(ty) ) {
                        throw new RuntimeException("floor error");
                    }*/
                    int equal_value;
                    if( ix >= 0 && ix < n_tiles-1 && iy >= 0 && iy < n_tiles-1 ) {
                        int histogram_offset00 = 256*(ix*n_tiles+iy);
                        int histogram_offset10 = 256*((ix+1)*n_tiles+iy);
                        int histogram_offset01 = 256*(ix*n_tiles+iy+1);
                        int histogram_offset11 = 256*((ix+1)*n_tiles+iy+1);
                        int equal_value00 = getEqualValue(histogram_offset00, value);
                        int equal_value10 = getEqualValue(histogram_offset10, value);
                        int equal_value01 = getEqualValue(histogram_offset01, value);
                        int equal_value11 = getEqualValue(histogram_offset11, value);
                        float alpha = tx - ix;
                        float beta = ty - iy;

                        float equal_value0 = (1.0f-alpha)*equal_value00 + alpha*equal_value10;
                        float equal_value1 = (1.0f-alpha)*equal_value01 + alpha*equal_value11;
                        equal_value = (int)((1.0f-beta)*equal_value0 + beta*equal_value1);
                    }
                    else if( ix >= 0 && ix < n_tiles-1 ) {
                        int this_y = (iy<0) ? iy+1 : iy;
                        int histogram_offset0 = 256*(ix*n_tiles+this_y);
                        int histogram_offset1 = 256*((ix+1)*n_tiles+this_y);
                        int equal_value0 = getEqualValue(histogram_offset0, value);
                        int equal_value1 = getEqualValue(histogram_offset1, value);
                        float alpha = tx - ix;
                        equal_value = (int)((1.0f-alpha)*equal_value0 + alpha*equal_value1);
                    }
                    else if( iy >= 0 && iy < n_tiles-1 ) {
                        int this_x = (ix<0) ? ix+1 : ix;
                        int histogram_offset0 = 256*(this_x*n_tiles+iy);
                        int histogram_offset1 = 256*(this_x*n_tiles+iy+1);
                        int equal_value0 = getEqualValue(histogram_offset0, value);
                        int equal_value1 = getEqualValue(histogram_offset1, value);
                        float beta = ty - iy;
                        equal_value = (int)((1.0f-beta)*equal_value0 + beta*equal_value1);
                    }
                    else {
                        int this_x = (ix<0) ? ix+1 : ix;
                        int this_y = (iy<0) ? iy+1 : iy;
                        int histogram_offset = 256*(this_x*n_tiles+this_y);
                        equal_value = getEqualValue(histogram_offset, value);
                    }

                    int new_value = (int)( (1.0f-hdr_alpha) * value + hdr_alpha * equal_value );

                    //float use_hdr_alpha = smart_contrast_enhancement ? hdr_alpha*((float)value/255.0f) : hdr_alpha;
                    //float use_hdr_alpha = smart_contrast_enhancement ? hdr_alpha*pow(((float)value/255.0f), 0.5f) : hdr_alpha;
                    //int new_value = (int)( (1.0f-use_hdr_alpha) * value + use_hdr_alpha * equal_value );

                    float scale = ((float)new_value) / (float)value;

                    // need to add +0.5 so that we round to nearest - particularly important as due to floating point rounding, we
                    // can end up with incorrect behaviour even when new_value==value!
                    pixels_out[c] = (byte)Math.min(255, (int)(r * scale + 0.5f));
                    pixels_out[c+1] = (byte)Math.min(255, (int)(g * scale + 0.5f));
                    pixels_out[c+2] = (byte)Math.min(255, (int)(b * scale + 0.5f));
                    pixels_out[c+3] = (byte)255;
                }
            }
        }
    }

    public static class ComputeHistogramApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private int [][] histograms = null;
        private float [] pixels_rgb_f;
        private int pixels_width;
        public enum Type {
            TYPE_RGB, // returns array of length 3*256, containing the red histogram, followed by green, then blue
            TYPE_LUMINANCE, // 0.299f*r + 0.587f*g + 0.114f*b
            TYPE_VALUE, // max(r,g,b)
            TYPE_INTENSITY, // mean(r, g, b)
            TYPE_LIGHTNESS // mean( min(r,g,b), max(r,g,b) )
        }
        private final Type type;

        public ComputeHistogramApplyFunction(Type type) {
            this.type = type;
        }

        /** For use when we want to operate over a full pixel array, instead of an input supplied to applyFunction().
         */
        void setPixelsRGBf(float [] pixels_rgb_f, int pixels_width) {
            this.pixels_rgb_f = pixels_rgb_f;
            this.pixels_width = pixels_width;
        }

        @Override
        public void init(int n_threads) {
            histograms = new int[n_threads][];
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // version for operating on the supplied floating point array in rgb format
            if( type != Type.TYPE_VALUE )
                throw new RuntimeException("type not supported: " + type);
            if( histograms[thread_index] == null )
                histograms[thread_index] = new int[256];
            for(int y=off_y;y<off_y+this_height;y++) {
                int indx = 3*(y*pixels_width+off_x);
                for(int x=off_x;x<off_x+this_width;x++) {
                    int r = (int)(pixels_rgb_f[indx++]+0.5f);
                    int g = (int)(pixels_rgb_f[indx++]+0.5f);
                    int b = (int)(pixels_rgb_f[indx++]+0.5f);
                    int value = Math.max(r, g);
                    value = Math.max(value, b);
                    value = Math.min(value, 255);
                    value = Math.max(value, 0);
                    histograms[thread_index][value]++;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            /*if( MyDebug.LOG )
                Log.d(TAG, "ComputeHistogramApplyFunction.apply [int array]");*/
            if( histograms[thread_index] == null )
                histograms[thread_index] = new int[type == Type.TYPE_RGB ? 3*256 : 256];

            switch( type ) {
                case TYPE_RGB:
                    for(int c=0;c<this_width*this_height;c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        histograms[thread_index][((color >> 16) & 0xFF)]++; // red
                        histograms[thread_index][256 + ((color >> 8) & 0xFF)]++; // green
                        histograms[thread_index][512 + (color & 0xFF)]++; // blue
                    }
                    break;
                case TYPE_LUMINANCE:
                    for(int c=0;c<this_width*this_height;c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        float fr = (float)((color >> 16) & 0xFF);
                        float fg = (float)((color >> 8) & 0xFF);
                        float fb = (float)(color & 0xFF);
                        float avg = (0.299f*fr + 0.587f*fg + 0.114f*fb);
                        int value = (int)(avg+0.5); // round to nearest
                        value = Math.min(value, 255); // just in case
                        histograms[thread_index][value]++;
                    }
                    break;
                case TYPE_VALUE:
                    for(int c=0;c<this_width*this_height;c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        int value = Math.max( (color >> 16) & 0xFF, (color >> 8) & 0xFF );
                        value = Math.max( value, color & 0xFF );
                        histograms[thread_index][value]++;
                    }
                    break;
                case TYPE_INTENSITY:
                    for(int c=0;c<this_width*this_height;c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        float fr = (float)((color >> 16) & 0xFF);
                        float fg = (float)((color >> 8) & 0xFF);
                        float fb = (float)(color & 0xFF);
                        float avg = (fr + fg + fb)/3.0f;
                        int value = (int)(avg+0.5); // round to nearest
                        value = Math.min(value, 255); // just in case
                        histograms[thread_index][value]++;
                    }
                    break;
                case TYPE_LIGHTNESS:
                    for(int c=0;c<this_width*this_height;c++) {
                        // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                        int color = pixels[c];
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;
                        int max_value = Math.max( r, g );
                        max_value = Math.max( max_value, b );
                        int min_value = Math.min( r, g );
                        min_value = Math.min( min_value, b );
                        float avg = (min_value + max_value)/2.0f;
                        int value = (int)(avg+0.5); // round to nearest
                        value = Math.min(value, 255); // just in case
                        histograms[thread_index][value]++;
                    }
                    break;
                default:
                    throw new RuntimeException("unknown: " + type);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            /*if( MyDebug.LOG )
                Log.d(TAG, "ComputeHistogramApplyFunction.apply [byte vector array]");*/
            if( histograms[thread_index] == null )
                histograms[thread_index] = new int[256];
            for(int c=0;c<4*this_width*this_height;) { // n.b., we increment c inside the loop
                int r = pixels[c++];
                int g = pixels[c++];
                int b = pixels[c++];
                // bytes are signed!
                if( r < 0 )
                    r += 256;
                if( g < 0 )
                    g += 256;
                if( b < 0 )
                    b += 256;
                c++; // skip padding
                int value = Math.max(r, g);
                value = Math.max(value, b);
                value = Math.min(value, 255);
                value = Math.max(value, 0);
                histograms[thread_index][value]++;
            }
        }

        public int [] getHistogram() {
            int [] total_histogram = new int[histograms[0].length];
            // for each histogram, add its entries to the total histogram
            for(int [] histogram : histograms) {
                for (int j=0;j<histogram.length;j++) {
                    total_histogram[j] += histogram[j];
                }
            }
            return total_histogram;
        }
    }

    public static class ZebraStripesApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final int zebra_stripes_threshold;
        private final int zebra_stripes_foreground;
        private final int zebra_stripes_background;
        private final int zebra_stripes_width;

        public ZebraStripesApplyFunction(int zebra_stripes_threshold, int zebra_stripes_foreground, int zebra_stripes_background, int zebra_stripes_width) {
            this.zebra_stripes_threshold = zebra_stripes_threshold;
            this.zebra_stripes_foreground = zebra_stripes_foreground;
            this.zebra_stripes_background = zebra_stripes_background;
            this.zebra_stripes_width = zebra_stripes_width;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];

                    int value = Math.max( (color >> 16) & 0xFF, (color >> 8) & 0xFF );
                    value = Math.max( value, color & 0xFF );

                    if( value >= zebra_stripes_threshold ) {
                        int stripe = (x+y)/zebra_stripes_width;
                        if( stripe % 2 == 0 ) {
                            pixels_out[c] = zebra_stripes_background;
                        }
                        else {
                            pixels_out[c] = zebra_stripes_foreground;
                        }
                    }
                    else {
                        pixels_out[c] = 0; // transparent (zero alpha)
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    public static class FocusPeakingApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap;

        public FocusPeakingApplyFunction(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
            this.height = bitmap.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap[i] = new JavaImageProcessing.FastAccessBitmap(bitmap);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    int strength = 0;
                    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                        fast_bitmap[thread_index].ensureCache(y-1, y+1); // force cache to cover rows needed by this row
                        int bitmap_cache_y = fast_bitmap[thread_index].getCacheY();
                        int y_rel_bitmap_cache = y-bitmap_cache_y;
                        int [] bitmap_cache_pixels = fast_bitmap[thread_index].getCachedPixelsI();

                        //int pixel0c = fast_bitmap[thread_index].getPixel(x-1, y-1);
                        int pixel0c = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x-1)];
                        int pixel1c = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x)];
                        int pixel2c = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x+1)];
                        int pixel3c = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x-1)];
                        int pixel4c = pixels[c];
                        /*if( pixels[c] != bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x)] ) {
                            throw new RuntimeException("pixel4c incorrect");
                        }*/
                        int pixel5c = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x+1)];
                        int pixel6c = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x-1)];
                        int pixel7c = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x)];
                        int pixel8c = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x+1)];

                        int pixel0r = (pixel0c >> 16) & 0xFF;
                        int pixel0g = (pixel0c >> 8) & 0xFF;
                        int pixel0b = pixel0c & 0xFF;

                        int pixel1r = (pixel1c >> 16) & 0xFF;
                        int pixel1g = (pixel1c >> 8) & 0xFF;
                        int pixel1b = pixel1c & 0xFF;

                        int pixel2r = (pixel2c >> 16) & 0xFF;
                        int pixel2g = (pixel2c >> 8) & 0xFF;
                        int pixel2b = pixel2c & 0xFF;

                        int pixel3r = (pixel3c >> 16) & 0xFF;
                        int pixel3g = (pixel3c >> 8) & 0xFF;
                        int pixel3b = pixel3c & 0xFF;

                        int pixel4r = (pixel4c >> 16) & 0xFF;
                        int pixel4g = (pixel4c >> 8) & 0xFF;
                        int pixel4b = pixel4c & 0xFF;

                        int pixel5r = (pixel5c >> 16) & 0xFF;
                        int pixel5g = (pixel5c >> 8) & 0xFF;
                        int pixel5b = pixel5c & 0xFF;

                        int pixel6r = (pixel6c >> 16) & 0xFF;
                        int pixel6g = (pixel6c >> 8) & 0xFF;
                        int pixel6b = pixel6c & 0xFF;

                        int pixel7r = (pixel7c >> 16) & 0xFF;
                        int pixel7g = (pixel7c >> 8) & 0xFF;
                        int pixel7b = pixel7c & 0xFF;

                        int pixel8r = (pixel8c >> 16) & 0xFF;
                        int pixel8g = (pixel8c >> 8) & 0xFF;
                        int pixel8b = pixel8c & 0xFF;

                        int value_r = ( 8*pixel4r - pixel0r - pixel1r - pixel2r - pixel3r - pixel5r - pixel6r - pixel7r - pixel8r );
                        int value_g = ( 8*pixel4g - pixel0g - pixel1g - pixel2g - pixel3g - pixel5g - pixel6g - pixel7g - pixel8g );
                        int value_b = ( 8*pixel4b - pixel0b - pixel1b - pixel2b - pixel3b - pixel5b - pixel6b - pixel7b - pixel8b );
                        strength = value_r*value_r + value_g*value_g + value_b*value_b;
                    }

                    if( strength > 256*256 ) {
                        pixels_out[c] = (255 << 24) | (255 << 16) | (255 << 8) | 255;
                    }
                    else {
                        pixels_out[c] = 0; // transparent (zero alpha)
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    public static class FocusPeakingFilteredApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap;

        public FocusPeakingFilteredApplyFunction(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
            this.height = bitmap.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap[i] = new JavaImageProcessing.FastAccessBitmap(bitmap);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    int count = 0;
                    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                        fast_bitmap[thread_index].ensureCache(y-1, y+1); // force cache to cover rows needed by this row
                        int bitmap_cache_y = fast_bitmap[thread_index].getCacheY();
                        int y_rel_bitmap_cache = y-bitmap_cache_y;
                        int [] bitmap_cache_pixels = fast_bitmap[thread_index].getCachedPixelsI();

                        // only need to read one component, as input image is now greyscale
                        int pixel1 = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x)] & 0xFF;
                        int pixel3 = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x-1)] & 0xFF;
                        int pixel4 = pixels[c] & 0xFF;
                        /*if( pixels[c] != bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x)] ) {
                            throw new RuntimeException("pixel4c incorrect");
                        }*/
                        int pixel5 = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x+1)] & 0xFF;
                        int pixel7 = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x)] & 0xFF;

                        if( pixel1 == 255 )
                            count++;
                        if( pixel3 == 255 )
                            count++;
                        if( pixel4 == 255 )
                            count++;
                        if( pixel5 == 255 )
                            count++;
                        if( pixel7 == 255 )
                            count++;

                    }

                    if( count >= 3 ) {
                        pixels_out[c] = (255 << 24) | (255 << 16) | (255 << 8) | 255;
                    }
                    else {
                        pixels_out[c] = 0; // transparent (zero alpha)
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ConvertToGreyscaleFunction implements JavaImageProcessing.ApplyFunctionInterface {

        ConvertToGreyscaleFunction() {
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    int value = (int)(0.3* (float) r + 0.59* (float) g + 0.11* (float) b);

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = value << 24;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ComputeDerivativesFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_Ix; // output for x derivatives
        private final Bitmap bitmap_Iy; // output for y derivatives
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ComputeDerivativesFunction(Bitmap bitmap_Ix, Bitmap bitmap_Iy, Bitmap bitmap_in) {
            this.bitmap_Ix = bitmap_Ix;
            this.bitmap_Iy = bitmap_Iy;
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // we could move these to class members for performance, remember we'd have to have a version per-thread
            int [] cache_bitmap_Ix = new int[this_width*this_height];
            int [] cache_bitmap_Iy = new int[this_width*this_height];

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                fast_bitmap_in[thread_index].ensureCache(y-1, y+1); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    int Ix = 0, Iy = 0;
                    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                        // use Sobel operator

                        //int pixel0 = fast_bitmap_in[thread_index].getPixel(x-1, y-1) >>> 24;
                        //int pixel0 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x-1)] >>> 24;
                        int pixel1 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)] >>> 24;
                        //int pixel2 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x+1)] >>> 24;
                        int pixel3 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x-1)] >>> 24;
                        int pixel5 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x+1)] >>> 24;
                        //int pixel6 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x-1)] >>> 24;
                        int pixel7 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)] >>> 24;
                        //int pixel8 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x+1)] >>> 24;

                        //int iIx = (pixel2 + 2*pixel5 + pixel8) - (pixel0 + 2*pixel3 + pixel6);
                        //int iIy = (pixel6 + 2*pixel7 + pixel8) - (pixel0 + 2*pixel1 + pixel2);
                        //iIx /= 8;
                        //iIy /= 8;
                        int iIx = (pixel5 - pixel3)/2;
                        int iIy = (pixel7 - pixel1)/2;

                        // convert so we can store in range 0-255

                        iIx = Math.max(iIx, -127);
                        iIx = Math.min(iIx, 128);
                        iIx += 127; // iIx now runs from 0 to 255

                        iIy = Math.max(iIy, -127);
                        iIy = Math.min(iIy, 128);
                        iIy += 127; // iIy now runs from 0 to 255

                        Ix = iIx;
                        Iy = iIy;
                    }

                    //bitmap_Ix.setPixel(x, y, Ix << 24);
                    //bitmap_Iy.setPixel(x, y, Iy << 24);
                    cache_bitmap_Ix[c] = Ix << 24;
                    cache_bitmap_Iy[c] = Iy << 24;
                }
            }

            bitmap_Ix.setPixels(cache_bitmap_Ix, 0, this_width, off_x, off_y, this_width, this_height);
            bitmap_Iy.setPixels(cache_bitmap_Iy, 0, this_width, off_x, off_y, this_width, this_height);
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class CornerDetectorFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_f; // output
        private final Bitmap bitmap_Ix;
        private final Bitmap bitmap_Iy;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_Ix;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_Iy;

        CornerDetectorFunction(float [] pixels_f, Bitmap bitmap_Ix, Bitmap bitmap_Iy) {
            this.pixels_f = pixels_f;
            this.bitmap_Ix = bitmap_Ix;
            this.bitmap_Iy = bitmap_Iy;
            this.width = bitmap_Ix.getWidth();
            this.height = bitmap_Ix.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_Ix = new JavaImageProcessing.FastAccessBitmap[n_threads];
            fast_bitmap_Iy = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_Ix[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_Ix);
                fast_bitmap_Iy[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_Iy);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            final int radius = 2; // radius for corner detector
            final float [] weights = new float[]{1, 4, 6, 4, 1};

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                fast_bitmap_Ix[thread_index].ensureCache(y-radius, y+radius); // force cache to cover rows needed by this row
                int bitmap_Ix_cache_y = fast_bitmap_Ix[thread_index].getCacheY();
                int y_rel_bitmap_Ix_cache = y-bitmap_Ix_cache_y;
                int [] bitmap_Ix_cache_pixels = fast_bitmap_Ix[thread_index].getCachedPixelsI();

                fast_bitmap_Iy[thread_index].ensureCache(y-radius, y+radius); // force cache to cover rows needed by this row
                int bitmap_Iy_cache_y = fast_bitmap_Iy[thread_index].getCacheY();
                int y_rel_bitmap_Iy_cache = y-bitmap_Iy_cache_y;
                int [] bitmap_Iy_cache_pixels = fast_bitmap_Iy[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    float out = 0;

                    // extra +1 as we won't have derivative info for the outermost pixels (see compute_derivatives)
                    if( x >= radius+1 && x < width-radius-1 && y >= radius+1 && y < height-radius-1 ) {
                        float h00 = 0.0f;
                        float h01 = 0.0f;
                        float h11 = 0.0f;
                        for(int cy=y-radius;cy<=y+radius;cy++) {
                            for(int cx=x-radius;cx<=x+radius;cx++) {
                                int dx = cx - x;
                                int dy = cy - y;

                                int Ix = bitmap_Ix_cache_pixels[(y_rel_bitmap_Ix_cache+dy)*width+(cx)] >>> 24;
                                int Iy = bitmap_Iy_cache_pixels[(y_rel_bitmap_Iy_cache+dy)*width+(cx)] >>> 24;

                                // convert from 0-255 to -127 - +128:
                                Ix -= 127;
                                Iy -= 127;

                                /*float dist2 = dx*dx + dy*dy;
                                const float sigma2 = 0.25f;
                                float weight = exp(-dist2/(2.0f*sigma2)) / (6.28318530718f*sigma2);
                                //float weight = 1.0;
                                weight /= 65025.0f; // scale from (0, 255) to (0, 1)
                                */
                                float weight = weights[2+dx] * weights[2+dy];
                                //weight = 36;

                                h00 += weight*Ix*Ix;
                                h01 += weight*Ix*Iy;
                                h11 += weight*Iy*Iy;
                            }
                        }

                        float det_H = h00*h11 - h01*h01;
                        float tr_H = h00 + h11;
                        //out = det_H - 0.1f*tr_H*tr_H;
                        out = det_H - 0.06f*tr_H*tr_H;
                    }

                    pixels_f[y*width+x] = out;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class LocalMaximumFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_f; // input
        private final byte [] bytes; // output
        private final int width, height;
        private final float corner_threshold;

        LocalMaximumFunction(float [] pixels_f, byte [] bytes, int width, int height, float corner_threshold) {
            this.pixels_f = pixels_f;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
            this.corner_threshold = corner_threshold;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    int out = 0;
                    float in = pixels_f[y*width+x];
                    bytes[y*width+x] = (byte)out;

                    if( in >= corner_threshold ) {
                        //out = 255;
                        // best of 3x3:
                        /*if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                            if( in > rsGetElementAt_float(bitmap, x-1, y-1) &&
                                in > rsGetElementAt_float(bitmap, x, y-1) &&
                                in > rsGetElementAt_float(bitmap, x+1, y-1) &&

                                in > rsGetElementAt_float(bitmap, x-1, y) &&
                                in > rsGetElementAt_float(bitmap, x+1, y) &&

                                in > rsGetElementAt_float(bitmap, x-1, y+1) &&
                                in > rsGetElementAt_float(bitmap, x, y+1) &&
                                in > rsGetElementAt_float(bitmap, x+1, y+1)
                                ) {
                                out = 255;
                            }
                        }*/
                        // best of 5x5:
                        if( x >= 2 && x < width-2 && y >= 2 && y < height-2 ) {
                            if( in > pixels_f[(y-2)*width+(x-2)] &&
                                    in > pixels_f[(y-2)*width+(x-1)] &&
                                    in > pixels_f[(y-2)*width+(x)] &&
                                    in > pixels_f[(y-2)*width+(x+1)] &&
                                    in > pixels_f[(y-2)*width+(x+2)] &&

                                    in > pixels_f[(y-1)*width+(x-2)] &&
                                    in > pixels_f[(y-1)*width+(x-1)] &&
                                    in > pixels_f[(y-1)*width+(x)] &&
                                    in > pixels_f[(y-1)*width+(x+1)] &&
                                    in > pixels_f[(y-1)*width+(x+2)] &&

                                    in > pixels_f[(y)*width+(x-2)] &&
                                    in > pixels_f[(y)*width+(x-1)] &&
                                    in > pixels_f[(y)*width+(x+1)] &&
                                    in > pixels_f[(y)*width+(x+2)] &&

                                    in > pixels_f[(y+1)*width+(x-2)] &&
                                    in > pixels_f[(y+1)*width+(x-1)] &&
                                    in > pixels_f[(y+1)*width+(x)] &&
                                    in > pixels_f[(y+1)*width+(x+1)] &&
                                    in > pixels_f[(y+1)*width+(x+2)] &&

                                    in > pixels_f[(y+2)*width+(x-2)] &&
                                    in > pixels_f[(y+2)*width+(x-1)] &&
                                    in > pixels_f[(y+2)*width+(x)] &&
                                    in > pixels_f[(y+2)*width+(x+1)] &&
                                    in > pixels_f[(y+2)*width+(x+2)]
                            ) {
                                out = 255;
                            }
                        }
                    }

                    bytes[y*width+x] = (byte)out;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    public static class PyramidBlendingComputeErrorFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private int [] errors; // error per thread
        private final Bitmap bitmap;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap;
        private final int width;

        public PyramidBlendingComputeErrorFunction(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
        }

        @Override
        public void init(int n_threads) {
            errors = new int[n_threads];
            fast_bitmap = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap[i] = new JavaImageProcessing.FastAccessBitmap(bitmap);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                fast_bitmap[thread_index].ensureCache(y, y); // force cache to cover rows needed by this row
                int bitmap_cache_y = fast_bitmap[thread_index].getCacheY();
                int y_rel_bitmap_cache = y-bitmap_cache_y;
                int [] bitmap_cache_pixels = fast_bitmap[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    int r0 = (color0 >> 16) & 0xFF;
                    int g0 = (color0 >> 8) & 0xFF;
                    int b0 = color0 & 0xFF;

                    int color1 = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x)];
                    int r1 = (color1 >> 16) & 0xFF;
                    int g1 = (color1 >> 8) & 0xFF;
                    int b1 = color1 & 0xFF;

                    int dr = r0 - r1;
                    int dg = g0 - g1;
                    int db = b0 - b1;
                    int diff2 = dr*dr + dg*dg + db*db;
                    if( errors[thread_index] < 2000000000 ) { // avoid risk of overflow
                        errors[thread_index] += diff2;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        public int getError() {
            int total_error = 0;
            for(int error : errors) {
                total_error += error;
            }
            return total_error;
        }
    }

    private static final float [] pyramid_blending_weights = new float[]{0.05f, 0.25f, 0.4f, 0.25f, 0.05f};

    static class ReduceBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ReduceBitmapFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                int sy = 2*y;

                fast_bitmap_in[thread_index].ensureCache(sy-2, sy+2); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = sy-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    int sx = 2*x;

                    if( sx >= 2 && sx < width-2 && sy >= 2 & sy < height-2 ) {

                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        for(int dy=-2;dy<=2;dy++) {
                            for(int dx=-2;dx<=2;dx++) {

                                int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(sx+dx)];
                                //int color = bitmap_in.getPixel(sx+dx, sy+dy);
                                int r = (color >> 16) & 0xFF;
                                int g = (color >> 8) & 0xFF;
                                int b = color & 0xFF;

                                // commented out version might be faster, but needs to be tested as gives slightly different results due to numerical wobble
                                /*float fr = r, fg = g, fb = b;
                                float weight = pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                fr *= weight;
                                fg *= weight;
                                fb *= weight;*/
                                float fr = ((float)r) * pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                float fg = ((float)g) * pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                float fb = ((float)b) * pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                sum_fr += fr;
                                sum_fg += fg;
                                sum_fb += fb;
                            }
                        }

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                    else {
                        int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx)];
                        //int color = bitmap_in.getPixel(sx, sy);
                        pixels_out[c] = color;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapXFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ReduceBitmapXFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                fast_bitmap_in[thread_index].ensureCache(y, y); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    int sx = 2*x;

                    if( sx >= 2 && sx < width-2 ) {

                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx+dx)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dx];
                        }*/

                        // unroll loops

                        int offset = (y_rel_bitmap_in_cache)*width+(sx);
                        int color;

                        color = bitmap_in_cache_pixels[offset-2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[offset-1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[offset];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[offset+1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[offset+2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                    else {
                        int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx)];
                        //int color = bitmap_in.getPixel(sx, y);
                        pixels_out[c] = color;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapYFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ReduceBitmapYFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                int sy = 2*y;

                fast_bitmap_in[thread_index].ensureCache(sy-2, sy+2); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = sy-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                if( sy >= 2 & sy < height-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dy];
                        }*/

                        // unroll loops

                        int color;

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                        //int color = bitmap_in.getPixel(x, sy);
                        pixels_out[c] = color;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapXFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width; // width of bitmap_out (bitmap_in should be twice the width)

        ReduceBitmapXFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array
                for(int x=off_x;x<off_x+this_width;x++,c+=4) {

                    int sx = 2*x;
                    int pixel_index = 4*((y)*(2*width)+(sx));

                    if( sx >= 2 && sx < (2*width)-2 ) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx+dx)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dx];
                        }*/

                        // unroll loops

                        int offset;

                        offset = pixel_index-8;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[0];

                        offset = pixel_index-4;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[1];

                        offset = pixel_index;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[2];

                        offset = pixel_index+4;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[3];

                        offset = pixel_index+8;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                    else {
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[pixel_index+1];
                        bitmap_out[c+2] = bitmap_in[pixel_index+2];
                        bitmap_out[c+3] = bitmap_in[pixel_index+3];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapYFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width; // width of bitmap_out (bitmap_in should be the same width)
        private final int height; // width of bitmap_out (bitmap_in should be twice the height)

        ReduceBitmapYFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array

                int sy = 2*y;

                if( sy >= 2 & sy < (2*height)-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dy];
                        }*/

                        // unroll loops

                        int offset;

                        offset = 4*((sy-2)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[0];

                        offset = 4*((sy-1)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[1];

                        offset = 4*((sy)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[2];

                        offset = 4*((sy+1)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[3];

                        offset = 4*((sy+2)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        int pixel_index = 4*((sy)*(width)+(x));
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[pixel_index+1];
                        bitmap_out[c+2] = bitmap_in[pixel_index+2];
                        bitmap_out[c+3] = bitmap_in[pixel_index+3];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ExpandBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ExpandBitmapFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                if( y % 2 == 0 ) {
                    int sy = y/2;

                    fast_bitmap_in[thread_index].ensureCache(sy, sy); // force cache to cover rows needed by this row
                    int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                    int y_rel_bitmap_in_cache = sy-bitmap_in_cache_y;
                    int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        if( x % 2 == 0 ) {
                            int sx = x/2;
                            pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx)];
                        }
                        else {
                            pixels_out[c] = (255 << 24);
                        }
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        pixels_out[c] = (255 << 24);
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), rather than being a general blur
     *  function.
     */
    static class Blur1dXFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        Blur1dXFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                if( y % 2 == 1 ) {
                    // can skip odd y lines, as will be all zeroes (due to the result of ExpandBitmapFunction)
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        pixels_out[c] = (255 << 24);
                    }
                    continue;
                }

                fast_bitmap_in[thread_index].ensureCache(y, y); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                int sx = Math.max(off_x, 2);
                int ex = Math.min(off_x+this_width, width-2);

                for(int x=off_x;x<sx;x++,c++) {
                    // x values < 2
                    pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                }

                //for(int x=off_x;x<off_x+this_width;x++,c++) {
                for(int x=sx;x<ex;x++,c++) {
                    //if( x >= 2 && x < width-2 )
                    {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x+dx)];
                            int r = (color >> 16) & 0xFF;
                            int g = (color >> 8) & 0xFF;
                            int b = color & 0xFF;

                            float fr = ((float)r) * pyramid_blending_weights[2+dx];
                            float fg = ((float)g) * pyramid_blending_weights[2+dx];
                            float fb = ((float)b) * pyramid_blending_weights[2+dx];
                            sum_fr += fr;
                            sum_fg += fg;
                            sum_fb += fb;
                        }*/

                        // unroll loop

                        int color;
                        int pixel_index = (y_rel_bitmap_in_cache)*width+x;

                        // when blending, we can take advantage of the fact that pixels will be 0 at odd x coordinates (due to the result of ExpandBitmapFunction)
                        if( x % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            color = bitmap_in_cache_pixels[pixel_index-1];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            color = bitmap_in_cache_pixels[pixel_index+1];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            color = bitmap_in_cache_pixels[pixel_index-2];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            color = bitmap_in_cache_pixels[pixel_index];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            color = bitmap_in_cache_pixels[pixel_index+2];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        }
                        /*
                        color = bitmap_in_cache_pixels[pixel_index-2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[pixel_index-1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[pixel_index];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[pixel_index+1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[pixel_index+2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        */

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                    /*else {
                        pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                    }*/
                }

                for(int x=ex;x<off_x+this_width;x++,c++) {
                    // x values >= width-2
                    pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), that was then processed with
     *  Blur1dXFunction, rather than being a general blur function.
     */
    static class Blur1dYFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        Blur1dYFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                fast_bitmap_in[thread_index].ensureCache(y-2, y+2); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                if( y >= 2 && y < height-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(x)];
                            int r = (color >> 16) & 0xFF;
                            int g = (color >> 8) & 0xFF;
                            int b = color & 0xFF;

                            float fr = ((float)r) * pyramid_blending_weights[2+dy];
                            float fg = ((float)g) * pyramid_blending_weights[2+dy];
                            float fb = ((float)b) * pyramid_blending_weights[2+dy];
                            sum_fr += fr;
                            sum_fg += fg;
                            sum_fb += fb;
                        }*/

                        // unroll loop:

                        int color;

                        // when blending, due to having blurred X the result of ExpandBitmapFunction, we will now have odd-y lines being zero, even-y lines being non-zero
                        if( y % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-2)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+2)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        }

                        /*
                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        */

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Alpha isn't written on result for performance.
     */
    static class ExpandBitmapFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width, /** @noinspection FieldCanBeLocal*/ height; // dimensions of bitmap_out (bitmap_in should be half the width and half the height)

        ExpandBitmapFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array

                if( y % 2 == 0 ) {
                    int sy = y/2;

                    /*for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        if( x % 2 == 0 ) {
                            int sx = x/2;
                            int sc = 4*(sy*(width/2)+sx); // index into bitmap_in array (n.b., width/2 as bitmap_in is half the size)
                            bitmap_out[c] = bitmap_in[sc];
                            bitmap_out[c+1] = bitmap_in[sc+1];
                            bitmap_out[c+2] = bitmap_in[sc+2];
                            bitmap_out[c+3] = bitmap_in[sc+3];
                        }
                        else {
                            bitmap_out[c] = (byte)255;
                        }
                    }*/
                    // copy even x (assumes off_x is even)
                    //int saved_c = c;
                    for(int sx=off_x/2;sx<(off_x+this_width)/2;sx++,c+=8) {
                        int sc = 4*(sy*(width/2)+sx); // index into bitmap_in array (n.b., width/2 as bitmap_in is half the size)
                        //bitmap_out[c] = bitmap_in[sc];
                        bitmap_out[c+1] = bitmap_in[sc+1];
                        bitmap_out[c+2] = bitmap_in[sc+2];
                        bitmap_out[c+3] = bitmap_in[sc+3];
                    }
                    // skip writing odd x
                    /*
                    // copy odd x
                    c = saved_c+4;
                    for(int x=off_x+1;x<off_x+this_width;x+=2,c+=8) {
                        bitmap_out[c] = (byte)255;
                    }
                   */
                }
                /*else {
                    // skip writing odd y
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        bitmap_out[c] = (byte)255;
                    }
                }*/
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), rather than being a general blur
     *  function.
     *  Alpha isn't written on result for performance.
     */
    static class Blur1dXFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width, /** @noinspection FieldCanBeLocal*/ height;

        Blur1dXFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array
                if( y % 2 == 1 ) {
                    // can skip odd y lines, as will be all zeroes (due to the result of ExpandBitmapFunction)
                    /*for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        bitmap_out[c] = (byte)255;
                    }*/
                    continue;
                }

                int sx = Math.max(off_x, 2);
                int ex = Math.min(off_x+this_width, width-2);

                for(int x=off_x;x<sx;x++,c+=4) {
                    // x values < 2
                    //bitmap_out[c] = bitmap_in[c];
                    bitmap_out[c+1] = bitmap_in[c+1];
                    bitmap_out[c+2] = bitmap_in[c+2];
                    bitmap_out[c+3] = bitmap_in[c+3];
                }

                //for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                for(int x=sx;x<ex;x++,c+=4) {
                    //if( x >= 2 && x < width-2 )
                    {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int index = 4*((y)*width+(x+dx));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2+dx];
                        }*/

                        // unroll loop

                        int pixel_index = 4*((y)*width+(x)), index;

                        // when blending, we can take advantage of the fact that pixels will be 0 at odd x coordinates (due to the result of ExpandBitmapFunction)
                        if( x % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            index = pixel_index-4;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            index = pixel_index+4;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            index = pixel_index-8;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            index = pixel_index;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            index = pixel_index+8;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[4];
                        }

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        //r = Math.max(0, Math.min(255, r));
                        //g = Math.max(0, Math.min(255, g));
                        //b = Math.max(0, Math.min(255, b));

                        //bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                    /*else {
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[c+1];
                        bitmap_out[c+2] = bitmap_in[c+2];
                        bitmap_out[c+3] = bitmap_in[c+3];
                    }*/
                }

                for(int x=ex;x<off_x+this_width;x++,c+=4) {
                    // x values >= width-2
                    //bitmap_out[c] = bitmap_in[c];
                    bitmap_out[c+1] = bitmap_in[c+1];
                    bitmap_out[c+2] = bitmap_in[c+2];
                    bitmap_out[c+3] = bitmap_in[c+3];
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), that was then processed with
     *  Blur1dXFunction, rather than being a general blur function.
     *  Alpha isn't written as 255, rather than being based on input alpha.
     */
    static class Blur1dYFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width, height;

        Blur1dYFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array
                if( y >= 2 && y < height-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int index = 4*((y+dy)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2+dy];
                        }*/

                        // unroll loop:

                        int index;

                        // when blending, due to having blurred X the result of ExpandBitmapFunction, we will now have odd-y lines being zero, even-y lines being non-zero
                        if( y % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            index = 4*((y-1)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            index = 4*((y+1)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            index = 4*((y-2)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            index = 4*((y)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            index = 4*((y+2)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[4];
                        }

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        //r = Math.max(0, Math.min(255, r));
                        //g = Math.max(0, Math.min(255, g));
                        //b = Math.max(0, Math.min(255, b));

                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[c+1];
                        bitmap_out[c+2] = bitmap_in[c+2];
                        bitmap_out[c+3] = bitmap_in[c+3];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class SubtractBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf; // output
        private final Bitmap bitmap1;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap1;
        private final int width;

        SubtractBitmapFunction(float [] pixels_rgbf, Bitmap bitmap1) {
            this.pixels_rgbf = pixels_rgbf;
            this.bitmap1 = bitmap1;
            this.width = bitmap1.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap1 = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap1[i] = new JavaImageProcessing.FastAccessBitmap(bitmap1);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                int pixels_rgbf_indx = 3*y*width;

                fast_bitmap1[thread_index].getPixel(0, y); // force cache to cover row y
                int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    float pixel0_fr = (float)((color0 >> 16) & 0xFF);
                    float pixel0_fg = (float)((color0 >> 8) & 0xFF);
                    float pixel0_fb = (float)(color0 & 0xFF);

                    //int color1 = fast_bitmap1[thread_index].getPixel(x, y);
                    int color1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache)*width+(x)];
                    float pixel1_fr = (float)((color1 >> 16) & 0xFF);
                    float pixel1_fg = (float)((color1 >> 8) & 0xFF);
                    float pixel1_fb = (float)(color1 & 0xFF);

                    float fr = pixel0_fr - pixel1_fr;
                    float fg = pixel0_fg - pixel1_fg;
                    float fb = pixel0_fb - pixel1_fb;

                    this.pixels_rgbf[pixels_rgbf_indx] = fr;
                    this.pixels_rgbf[pixels_rgbf_indx+1] = fg;
                    this.pixels_rgbf[pixels_rgbf_indx+2] = fb;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class MergefFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf0; // input
        private final float [] pixels_rgbf1; // input
        private final int width;
        private final int [] interpolated_best_path;
        private final int merge_blend_width;
        //private final int start_blend_x;

        MergefFunction(float [] pixels_rgbf0, float [] pixels_rgbf1, int blend_width, int width, int [] interpolated_best_path) {
            this.pixels_rgbf0 = pixels_rgbf0;
            this.pixels_rgbf1 = pixels_rgbf1;
            this.width = width;
            this.interpolated_best_path = interpolated_best_path;

            merge_blend_width = blend_width;
            //start_blend_x = (full_width - merge_blend_width)/2;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int pixels_rgbf_indx = 3*y*width;
                int mid_x = interpolated_best_path[y];

                for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3) {
                    float pixel0_fr = pixels_rgbf0[pixels_rgbf_indx];
                    float pixel0_fg = pixels_rgbf0[pixels_rgbf_indx+1];
                    float pixel0_fb = pixels_rgbf0[pixels_rgbf_indx+2];
                    float pixel1_fr = pixels_rgbf1[pixels_rgbf_indx];
                    float pixel1_fg = pixels_rgbf1[pixels_rgbf_indx+1];
                    float pixel1_fb = pixels_rgbf1[pixels_rgbf_indx+2];

                    float alpha = ((float)( x-(mid_x-merge_blend_width/2) )) / (float)merge_blend_width;
                    alpha = Math.max(alpha, 0.0f);
                    alpha = Math.min(alpha, 1.0f);

                    this.pixels_rgbf0[pixels_rgbf_indx] = (1.0f-alpha)*pixel0_fr + alpha*pixel1_fr;
                    this.pixels_rgbf0[pixels_rgbf_indx+1] = (1.0f-alpha)*pixel0_fg + alpha*pixel1_fg;
                    this.pixels_rgbf0[pixels_rgbf_indx+2] = (1.0f-alpha)*pixel0_fb + alpha*pixel1_fb;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class MergeFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap1;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap1;
        private final int [] interpolated_best_path;
        private final int merge_blend_width;
        //private final int start_blend_x;

        MergeFunction(Bitmap bitmap1, int blend_width, int [] interpolated_best_path) {
            this.bitmap1 = bitmap1;
            this.width = bitmap1.getWidth();
            this.interpolated_best_path = interpolated_best_path;

            merge_blend_width = blend_width;
            //start_blend_x = (full_width - merge_blend_width)/2;
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap1 = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap1[i] = new JavaImageProcessing.FastAccessBitmap(bitmap1);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                int mid_x = interpolated_best_path[y];

                fast_bitmap1[thread_index].getPixel(0, y); // force cache to cover row y
                int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    float pixel0_fr = (float)((color0 >> 16) & 0xFF);
                    float pixel0_fg = (float)((color0 >> 8) & 0xFF);
                    float pixel0_fb = (float)(color0 & 0xFF);

                    //int color1 = fast_bitmap1[thread_index].getPixel(x, y);
                    int color1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache)*width+(x)];
                    float pixel1_fr = (float)((color1 >> 16) & 0xFF);
                    float pixel1_fg = (float)((color1 >> 8) & 0xFF);
                    float pixel1_fb = (float)(color1 & 0xFF);

                    float alpha = ((float)( x-(mid_x-merge_blend_width/2) )) / (float)merge_blend_width;
                    alpha = Math.max(alpha, 0.0f);
                    alpha = Math.min(alpha, 1.0f);

                    float fr = (1.0f-alpha)*pixel0_fr + alpha*pixel1_fr;
                    float fg = (1.0f-alpha)*pixel0_fg + alpha*pixel1_fg;
                    float fb = (1.0f-alpha)*pixel0_fb + alpha*pixel1_fb;

                    int r = (int)(fr+0.5f);
                    int g = (int)(fg+0.5f);
                    int b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class AddBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf1;
        private final int width;

        AddBitmapFunction(float [] pixels_rgbf1, int width) {
            this.pixels_rgbf1 = pixels_rgbf1;
            this.width = width;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                int pixels_rgbf_indx = 3*y*width;

                for(int x=off_x;x<off_x+this_width;x++,c++,pixels_rgbf_indx+=3) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    float pixel0_fr = (float)((color0 >> 16) & 0xFF);
                    float pixel0_fg = (float)((color0 >> 8) & 0xFF);
                    float pixel0_fb = (float)(color0 & 0xFF);

                    float pixel1_fr = pixels_rgbf1[pixels_rgbf_indx];
                    float pixel1_fg = pixels_rgbf1[pixels_rgbf_indx+1];
                    float pixel1_fb = pixels_rgbf1[pixels_rgbf_indx+2];

                    float fr = pixel0_fr + pixel1_fr;
                    float fg = pixel0_fg + pixel1_fg;
                    float fb = pixel0_fb + pixel1_fb;

                    int r = (int)(fr+0.5f);
                    int g = (int)(fg+0.5f);
                    int b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }
}
