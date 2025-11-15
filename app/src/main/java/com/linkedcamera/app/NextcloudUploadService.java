package com.linkedcamera.app;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Background service for uploading photos to Nextcloud via WebDAV
 */
public class NextcloudUploadService extends IntentService {
    private static final String TAG = "NextcloudUploadService";
    private static final String ACTION_UPLOAD = "net.sourceforge.opencamera.action.UPLOAD";
    private static final String ACTION_PROCESS_QUEUE = "net.sourceforge.opencamera.action.PROCESS_QUEUE";
    private static final String EXTRA_FILE_PATH = "net.sourceforge.opencamera.extra.FILE_PATH";
    private static final String PREF_UPLOAD_QUEUE = "nextcloud_upload_queue";
    private static final String CHANNEL_ID = "nextcloud_upload_channel";
    private static final int NOTIFICATION_ID = 1001;

    public NextcloudUploadService() {
        super("NextcloudUploadService");
    }

    /**
     * Start upload for a specific file
     */
    public static void startUpload(Context context, String filePath) {
        Log.d(TAG, "startUpload called with filePath: " + filePath);
        Intent intent = new Intent(context, NextcloudUploadService.class);
        intent.setAction(ACTION_UPLOAD);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        context.startService(intent);
        Log.d(TAG, "startService called");
    }

    /**
     * Process all queued uploads
     */
    public static void processUploadQueue(Context context) {
        Intent intent = new Intent(context, NextcloudUploadService.class);
        intent.setAction(ACTION_PROCESS_QUEUE);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent called");
        if (intent != null) {
            final String action = intent.getAction();
            Log.d(TAG, "Action: " + action);
            if (ACTION_UPLOAD.equals(action)) {
                final String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
                Log.d(TAG, "Handling upload for: " + filePath);
                handleUpload(filePath);
            } else if (ACTION_PROCESS_QUEUE.equals(action)) {
                Log.d(TAG, "Processing upload queue");
                handleProcessQueue();
            }
        }
    }

    /**
     * Handle upload of a single file
     */
    private void handleUpload(String filePath) {
        Log.d(TAG, "handleUpload called with: " + filePath);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if upload is enabled
        boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);
        Log.d(TAG, "Upload enabled: " + uploadEnabled);
        if (!uploadEnabled) {
            Log.d(TAG, "Upload not enabled");
            return;
        }

        // Get Nextcloud URL
        String nextcloudUrl = prefs.getString(PreferenceKeys.NextcloudUrlPreferenceKey, "").trim();
        Log.d(TAG, "Nextcloud URL configured: " + (!nextcloudUrl.isEmpty()));
        if (nextcloudUrl.isEmpty()) {
            Log.e(TAG, "Nextcloud URL not configured");
            showNotification(getString(R.string.nextcloud_upload_failed), "URL not configured");
            return;
        }

        // Check WiFi if required
        boolean wifiOnly = prefs.getBoolean(PreferenceKeys.NextcloudWifiOnlyPreferenceKey, true);
        boolean isWifi = isWifiConnected();
        Log.d(TAG, "WiFi only: " + wifiOnly + ", WiFi connected: " + isWifi);

        if (wifiOnly && !isWifi) {
            Log.d(TAG, "WiFi only enabled but not connected, queueing");
            addToUploadQueue(filePath);
            showNotification(getString(R.string.nextcloud_upload_waiting_wifi), new File(filePath).getName());
            return;
        }

        // Perform upload
        File file = new File(filePath);

        // Only upload image files (jpg, jpeg), skip videos and other files
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
            Log.d(TAG, "Skipping non-JPEG file: " + fileName);
            removeFromUploadQueue(filePath);
            return;
        }

        Log.d(TAG, "File exists: " + file.exists() + ", path: " + filePath);
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + filePath);
            removeFromUploadQueue(filePath);
            return;
        }
        Log.d(TAG, "Starting upload for file: " + file.getName() + ", size: " + file.length());

        boolean success = uploadToNextcloud(file, nextcloudUrl);

        if (success) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload successful");
            removeFromUploadQueue(filePath);
            showNotification(getString(R.string.nextcloud_upload_success), file.getName());

            // Auto-delete if enabled
            boolean autoDelete = prefs.getBoolean(PreferenceKeys.NextcloudAutoDeletePreferenceKey, false);
            if (autoDelete) {
                if (file.delete()) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "File deleted after upload");
                    showNotification(getString(R.string.nextcloud_photo_deleted), file.getName());
                } else {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Failed to delete file after upload");
                }
            }
        } else {
            if (MyDebug.LOG)
                Log.e(TAG, "Upload failed");
            addToUploadQueue(filePath);
            showNotification(getString(R.string.nextcloud_upload_failed), file.getName());
        }
    }

    /**
     * Process all files in the upload queue
     */
    private void handleProcessQueue() {
        Log.d(TAG, "handleProcessQueue called");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> queue = getUploadQueue();

        Log.d(TAG, "Processing upload queue with " + queue.size() + " items");

        if (queue.isEmpty()) {
            Log.d(TAG, "Upload queue is empty");
            return;
        }

        // Check if upload is enabled
        boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);
        if (!uploadEnabled) {
            Log.d(TAG, "Upload not enabled, skipping queue processing");
            return;
        }

        // Pre-check WiFi condition before processing queue
        // This prevents unnecessary iteration through all files when WiFi isn't available
        boolean wifiOnly = prefs.getBoolean(PreferenceKeys.NextcloudWifiOnlyPreferenceKey, true);

        if (wifiOnly) {
            boolean isWifi = isWifiConnected();
            if (!isWifi) {
                Log.d(TAG, "WiFi-only enabled but not connected, skipping queue processing");
                return;
            }
            Log.d(TAG, "WiFi condition met");
        }

        // Process each file in queue
        Log.d(TAG, "Starting to process " + queue.size() + " files in upload queue");
        for (String filePath : queue) {
            handleUpload(filePath);
        }
        Log.d(TAG, "Finished processing upload queue");
    }

    /**
     * Upload file to Nextcloud via WebDAV
     */
    private boolean uploadToNextcloud(File file, String shareUrl) {
        HttpURLConnection connection = null;
        FileInputStream fileInputStream = null;
        DataOutputStream outputStream = null;

        Log.d(TAG, "uploadToNextcloud: Starting upload with WebDAV");
        Log.d(TAG, "File: " + file.getAbsolutePath() + ", size: " + file.length());
        Log.d(TAG, "Share URL: " + shareUrl);

        try {
            // Parse share URL
            // Format: https://cloud.example.com/index.php/s/TOKEN
            int indexPos = shareUrl.indexOf("/index.php/s/");
            if (indexPos == -1) {
                Log.e(TAG, "Invalid share URL format: " + shareUrl);
                return false;
            }

            String baseUrl = shareUrl.substring(0, indexPos);
            String token = shareUrl.substring(indexPos + "/index.php/s/".length());

            Log.d(TAG, "Extracted base URL: " + baseUrl);
            Log.d(TAG, "Extracted token (before cleanup): " + token);

            // Clean token (remove trailing slashes, query params)
            token = token.split("[/?]")[0];

            Log.d(TAG, "Final token: " + token);

            // Construct WebDAV upload URL
            String uploadUrl = baseUrl + "/public.php/webdav/" + file.getName();

            Log.d(TAG, "Upload URL (WebDAV): " + uploadUrl);

            // Create HTTP connection
            URL url = new URL(uploadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(30000);

            Log.d(TAG, "Connection created, method: PUT");

            // Get password from preferences (optional, for password-protected shares)
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String password = prefs.getString(PreferenceKeys.NextcloudPasswordPreferenceKey, "").trim();

            // Set authorization (token as username, password if provided)
            String credentials;
            if (password.isEmpty()) {
                credentials = token + ":";
                Log.d(TAG, "Auth: using token as username, empty password (public share)");
            } else {
                credentials = token + ":" + password;
                Log.d(TAG, "Auth: using token as username with password (password-protected share)");
            }

            String basicAuth = "Basic " + Base64.encodeToString(
                credentials.getBytes(), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("Content-Length", String.valueOf(file.length()));

            Log.d(TAG, "Headers set, starting file upload");

            // Upload file data
            outputStream = new DataOutputStream(connection.getOutputStream());
            fileInputStream = new FileInputStream(file);

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            outputStream.flush();

            Log.d(TAG, "Upload completed, " + totalBytes + " bytes sent");

            // Check response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            Log.d(TAG, "Response code: " + responseCode + ", message: " + responseMessage);

            boolean success = responseCode >= 200 && responseCode < 300;
            Log.d(TAG, "Upload success: " + success);

            return success;

        } catch (IOException e) {
            Log.e(TAG, "Upload error: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                if (fileInputStream != null) fileInputStream.close();
                if (outputStream != null) outputStream.close();
                if (connection != null) connection.disconnect();
                Log.d(TAG, "Cleanup completed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Check if WiFi is connected
     */
    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifiInfo != null && wifiInfo.isConnected();
        }

        return false;
    }

    /**
     * Add file to upload queue
     */
    private void addToUploadQueue(String filePath) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> queue = new HashSet<>(getUploadQueue());
        boolean wasAdded = queue.add(filePath);

        if (wasAdded) {
            prefs.edit().putStringSet(PREF_UPLOAD_QUEUE, queue).apply();
            if (MyDebug.LOG)
                Log.d(TAG, "Added to queue: " + filePath);
        } else {
            if (MyDebug.LOG)
                Log.d(TAG, "File already in queue: " + filePath);
        }
    }

    /**
     * Remove file from upload queue
     */
    private void removeFromUploadQueue(String filePath) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> queue = new HashSet<>(getUploadQueue());
        queue.remove(filePath);
        prefs.edit().putStringSet(PREF_UPLOAD_QUEUE, queue).apply();

        if (MyDebug.LOG)
            Log.d(TAG, "Removed from queue: " + filePath);
    }

    /**
     * Get current upload queue
     */
    private Set<String> getUploadQueue() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return new HashSet<>(prefs.getStringSet(PREF_UPLOAD_QUEUE, new HashSet<String>()));
    }

    /**
     * Show notification
     */
    private void showNotification(String title, String message) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nextcloud Upload",
                NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
