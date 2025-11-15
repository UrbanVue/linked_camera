package com.linkedcamera.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;
import android.util.Log;

/** Provides service for quick settings tile.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class MyTileService extends TileService {
    private static final String TAG = "MyTileService";
    public static final String TILE_ID = "net.sourceforge.opencamera.TILE_CAMERA";

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        if( MyDebug.LOG )
            Log.d(TAG, "onClick");
        super.onClick();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(TILE_ID);
        // use startActivityAndCollapse() instead of startActivity() so that the notification panel doesn't remain pulled down
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
            // startActivityAndCollapse(Intent) throws UnsupportedOperationException on Android 14+
            // FLAG_IMMUTABLE needed for PendingIntents on Android 12+
            PendingIntent pending_intent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending_intent);
        }
        else {
            // still get warning for startActivityAndCollapse being deprecated, but startActivityAndCollapse(PendingIntent) requires Android 14+
            // and only seems possible to disable the warning for the function, not this statement
            startActivityAndCollapse(intent);
        }
    }
}
