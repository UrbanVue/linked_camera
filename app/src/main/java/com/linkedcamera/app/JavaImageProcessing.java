package com.linkedcamera.app;

import android.graphics.Bitmap;
import android.util.Log;

public class JavaImageProcessing {
    private static final String TAG = "JavaImageProcessing";

    public interface ApplyFunctionInterface {
        void init(int n_threads);
        void apply(CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height); // version with no input

        /**
         * @param pixels An array of pixels for the subset being operated on. I.e., pixels[0] represents the input pixel at (off_x, off_y), and
         *               the pixels array is of size this_width*this_height.
         */
        void apply(CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height);
        /**
         * @param pixels An array of pixels for the subset being operated on. I.e., pixels[0] represents the input pixel at (off_x, off_y), and
         *               the pixels array is of size 4*this_width*this_height.
         */
        void apply(CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height);
    }

    /** Encapsulates a Bitmap, but optimised for reading individual pixels.
     *  This differs to CachedBitmap in that FastAccessBitmap automatically decides which to cache,
     *  based on the requested pixels.
     */
    static class FastAccessBitmap {
        private final Bitmap bitmap;
        private final int bitmap_width;
        private final int cache_height;
        private final int [] cache_pixels_i;
        private int cache_y = -1;

        FastAccessBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.bitmap_width = bitmap.getWidth();
            this.cache_height = Math.min(128, bitmap.getHeight());
            this.cache_pixels_i = new int[bitmap_width*cache_height];
            // better for performance to initialise the cache, rather than having to keep checking if it's initialised
            cache(0);
        }

        private void cache(int y) {
            /*if( MyDebug.LOG )
                Log.d(TAG, ">>> cache: " + y + " [ " + this + " ]");*/
            y = Math.max(0, y-4);
            this.cache_y = Math.min(y, bitmap.getHeight()-cache_height);
            this.bitmap.getPixels(cache_pixels_i, 0, bitmap_width, 0, cache_y, bitmap_width, cache_height);
        }

        int getPixel(int x, int y) {
            if( y < cache_y || y >= cache_y+cache_height ) {
                // update cache
                cache(y);
            }
            // read from cache
            return cache_pixels_i[(y-cache_y)*bitmap_width+x];
        }

        void ensureCache(int sy, int ey) {
            if( ey - sy > cache_height ) {
                throw new RuntimeException("can't cache this many rows: " + sy + " to " + ey + " vs cache_height: " + cache_height);
            }
            if( sy < cache_y || ey >= cache_y+cache_height ) {
                cache(sy);
            }
        }

        int getCacheY() {
            return this.cache_y;
        }

        int [] getCachedPixelsI() {
            return this.cache_pixels_i;
        }
    }

    /** Encapsulates a Bitmap, together with caching of pixels.
     *  This differs to FastAccessBitmap in that CachedBitmap requires the caller to actually do the
     *  caching.
     */
    public static class CachedBitmap {
        private final Bitmap bitmap;
        private final int [] cache_pixels_i;
        private final byte [] cache_pixels_b;

        CachedBitmap(Bitmap bitmap, int cache_width, int cache_height) {
            this.bitmap = bitmap;
            this.cache_pixels_i = new int[cache_width*cache_height];
            this.cache_pixels_b = null;
        }

        int [] getCachedPixelsI() {
            return this.cache_pixels_i;
        }

        byte [] getCachedPixelsB() {
            return this.cache_pixels_b;
        }
    }

    /** Generic thread to apply a Java function to a bunch of pixels.
     */
    private static class ApplyFunctionThread extends Thread {
        private final int thread_index;
        private final ApplyFunctionInterface function;
        private final CachedBitmap input;
        private final int start_x, start_y, stop_x, stop_y;
        private int chunk_size; // number of lines to process at a time
        private CachedBitmap output; // optional
        private int output_start_x, output_start_y;

        private static int getChunkSize(int start_y, int stop_y) {
            int height = stop_y - start_y;
            //return height;
            //return (int)Math.ceil(height/4.0);
            //return Math.min(512, height);
            return Math.min(64, height);
            //return Math.min(32, height);
        }

        ApplyFunctionThread(int thread_index, ApplyFunctionInterface function, Bitmap bitmap, int start_x, int start_y, int stop_x, int stop_y) {
            super("ApplyFunctionThread");
            /*if( MyDebug.LOG ) {
                Log.d(TAG, "    thread_index: " + thread_index);
                Log.d(TAG, "    start_x: " + start_x);
                Log.d(TAG, "    start_y: " + start_y);
                Log.d(TAG, "    stop_x: " + stop_x);
                Log.d(TAG, "    stop_y: " + stop_y);
            }*/
            this.thread_index = thread_index;
            this.function = function;
            this.start_x = start_x;
            this.start_y = start_y;
            this.stop_x = stop_x;
            this.stop_y = stop_y;
            this.chunk_size = getChunkSize(start_y, stop_y);
            /*if( MyDebug.LOG )
                Log.d(TAG, "    chunk_size: " + chunk_size);*/
            if( bitmap != null )
                this.input = new CachedBitmap(bitmap, stop_x-start_x, chunk_size);
            else
                this.input = null;
        }

        void setOutput(Bitmap bitmap, int output_start_x, int output_start_y) {
            /*if( MyDebug.LOG ) {
                Log.d(TAG, "    output_start_x: " + output_start_x);
                Log.d(TAG, "    output_start_y: " + output_start_y);
            }*/
            this.output = new CachedBitmap(bitmap, stop_x-start_x, chunk_size);
            this.output_start_x = output_start_x;
            this.output_start_y = output_start_y;
        }

        public void run() {
            /*if( MyDebug.LOG )
                Log.d(TAG, "ApplyFunctionThread.run");*/
            int width = stop_x-start_x;
            int this_start_y = start_y;
            int output_shift_y = output_start_y - start_y;
            /*if( MyDebug.LOG ) {
                Log.d(TAG, "start_y: " + start_y);
                Log.d(TAG, "output_start_y: " + output_start_y);
                Log.d(TAG, "output_shift_y: " + output_shift_y);
            }*/
            if( input == null && output == null ) {
                this.chunk_size = stop_y-start_y;
                /*if( MyDebug.LOG )
                    Log.d(TAG, "reset chunk_size to: " + chunk_size);*/
            }

            final int chunk_size_f = chunk_size;
            while(this_start_y < stop_y) {
                int this_stop_y = Math.min(this_start_y+chunk_size_f, stop_y);
                int this_height = this_stop_y-this_start_y;
                //if( MyDebug.LOG )
                //    Log.d(TAG, "chunks from " + this_start_y + " to " + this_stop_y);

                //long time_s = System.currentTimeMillis();
                if( input == null ) {
                    // nothing to copy to cache
                    function.apply(output, thread_index, start_x, this_start_y, width, this_height);
                }
                else if( input.bitmap != null ) {
                    input.bitmap.getPixels(input.cache_pixels_i, 0, width, start_x, this_start_y, width, this_height);
                    /*if( MyDebug.LOG )
                        Log.d(TAG, "### ApplyFunctionThread: time after reading pixels: " + (System.currentTimeMillis() - time_s));*/
                    function.apply(output, thread_index, input.cache_pixels_i, start_x, this_start_y, width, this_height);
                }
                /*if( MyDebug.LOG )
                    Log.d(TAG, "### ApplyFunctionThread: time after apply: " + (System.currentTimeMillis() - time_s));*/

                if( output != null ) {
                    // write cached pixels back to output bitmap
                    if( output.bitmap != null ) {
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "this_start_y: " + this_start_y);
                            Log.d(TAG, "output_shift_y: " + output_shift_y);
                            Log.d(TAG, "this_height: " + this_height);
                            Log.d(TAG, "height: " + output.bitmap.getHeight());
                        }*/
                        output.bitmap.setPixels(output.cache_pixels_i, 0, width, output_start_x, this_start_y+output_shift_y, width, this_height);
                    }
                }

                this_start_y = this_stop_y;
            }
        }
    }

    /** Applies a function to the specified pixels of the supplied bitmap.
     */
    public static void applyFunction(ApplyFunctionInterface function, Bitmap bitmap, Bitmap output, int start_x, int start_y, int stop_x, int stop_y) {
        applyFunction(function, bitmap, output, start_x, start_y, stop_x, stop_y, start_x, start_y);
    }

    /** Applies a function to the specified pixels of the supplied bitmap.
     */
    static void applyFunction(ApplyFunctionInterface function, Bitmap bitmap, Bitmap output, int start_x, int start_y, int stop_x, int stop_y, int output_start_x, int output_start_y) {
        if( MyDebug.LOG )
            Log.d(TAG, "applyFunction [bitmap]");
        long time_s = System.currentTimeMillis();

        int height = stop_y-start_y;
        if( MyDebug.LOG )
            Log.d(TAG, "height: " + height);
        //final int n_threads = 1;
        final int n_threads = height >= 16 ? 4 : 1;
        //final int n_threads = height >= 16 ? 8 : 1;
        function.init(n_threads);
        ApplyFunctionThread [] threads = new ApplyFunctionThread[n_threads];
        int st_indx = 0;
        for(int i=0;i<n_threads;i++) {
            int nd_indx = (((i+1)*height)/n_threads);
            /*if( MyDebug.LOG )
                Log.d(TAG, "thread " + i + " from " + st_indx + " to " + nd_indx);*/
            threads[i] = new ApplyFunctionThread(i, function, bitmap, start_x, start_y+st_indx, stop_x, start_y+nd_indx);
            if( output != null )
                threads[i].setOutput(output, output_start_x, output_start_y+st_indx);
            st_indx = nd_indx;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "start threads");
        for(int i=0;i<n_threads;i++) {
            threads[i].start();
        }
        if( MyDebug.LOG )
            Log.d(TAG, "wait for threads to complete");
        try {
            for(int i=0;i<n_threads;i++) {
                threads[i].join();
            }
        }
        catch(InterruptedException e) {
            Log.e(TAG, "applyFunction threads interrupted");
            throw new RuntimeException(e);
        }

        //function.init(1);
        //ApplyFunctionThread thread = new ApplyFunctionThread(0, function, bitmap, start_x, start_y, stop_x, stop_y);
        //thread.run();

        if( MyDebug.LOG )
            Log.d(TAG, "applyFunction time: " + (System.currentTimeMillis() - time_s));
    }
}
