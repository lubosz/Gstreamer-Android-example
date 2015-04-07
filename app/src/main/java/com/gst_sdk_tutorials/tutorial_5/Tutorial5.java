package com.gst_sdk_tutorials.tutorial_5;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import org.freedesktop.gstreamer.GStreamer;

public class Tutorial5 extends Activity implements SurfaceHolder.Callback {
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private native void nativeReady();    // Set pipeline to PAUSED
    private native void nativeNull();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface); // A new surface is available
    private native void nativeSurfaceFinalize(); // Surface about to be destroyed
    private long native_custom_data;      // Native code will use this to keep private data

    private boolean is_playing_desired;   // Whether the user asked to go to PLAYING
    private int position;                 // Current position, reported by native code
    private int duration;                 // Current clip duration, reported by native code

    private PowerManager.WakeLock wake_lock;

    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.main);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wake_lock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "GStreamer tutorial 5");
        wake_lock.setReferenceCounted(false);

        ImageButton play = (ImageButton) this.findViewById(R.id.button_play);
        play.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = true;
                wake_lock.acquire();
                nativePlay();
            }
        });

        ImageButton pause = (ImageButton) this.findViewById(R.id.button_stop);
        pause.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = false;
                wake_lock.release();
                nativePause();
            }
        });

        ImageButton readyButton = (ImageButton) this.findViewById(R.id.button_ready);
        readyButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = false;
                wake_lock.release();
                Toast.makeText(Tutorial5.this, "ready!", Toast.LENGTH_LONG).show();
                nativeReady();
            }
        });

        ImageButton nullButton = (ImageButton) this.findViewById(R.id.button_null);
        nullButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = false;
                wake_lock.release();
                Toast.makeText(Tutorial5.this, "null!", Toast.LENGTH_LONG).show();
                nativeNull();
            }
        });

        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        // Retrieve our previous state, or initialize it to default values
        if (savedInstanceState != null) {
            is_playing_desired = savedInstanceState.getBoolean("playing");
            position = savedInstanceState.getInt("position");
            duration = savedInstanceState.getInt("duration");
            Log.i ("GStreamer", "Activity created with saved state:");
        } else {
            is_playing_desired = false;
            position = duration = 0;
            Log.i ("GStreamer", "Activity created with no saved state:");
        }
        Log.i ("GStreamer", "  playing:" + is_playing_desired + " position:" + position +
                " duration: " + duration);

        // Start with disabled buttons, until native code is initialized
        this.findViewById(R.id.button_play).setEnabled(false);
        this.findViewById(R.id.button_stop).setEnabled(false);

        nativeInit();
    }

    protected void onSaveInstanceState (Bundle outState) {
        Log.d ("GStreamer", "Saving state, playing:" + is_playing_desired + " position:" + position +
                " duration: " + duration);
        outState.putBoolean("playing", is_playing_desired);
        outState.putInt("position", position);
        outState.putInt("duration", duration);
    }

    protected void onDestroy() {
        nativeFinalize();
        if (wake_lock.isHeld())
            wake_lock.release();
        super.onDestroy();
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        runOnUiThread (new Runnable() {
            public void run() {
                tv.setText(message);
            }
        });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
        Log.i ("GStreamer", "GStreamer initialized:");
        Log.i ("GStreamer", "  playing:" + is_playing_desired + " position:" + position);

        // Restore previous playing state
        if (is_playing_desired) {
            nativePlay();
            wake_lock.acquire();
        } else {
            nativePause();
            wake_lock.release();
        }

        // Re-enable buttons, now that GStreamer is initialized
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            public void run() {
                activity.findViewById(R.id.button_play).setEnabled(true);
                activity.findViewById(R.id.button_stop).setEnabled(true);
            }
        });
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("tutorial-5");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }

    // Called from native code when the size of the media changes or is first detected.
    // Inform the video surface about the new size and recalculate the layout.
    private void onMediaSizeChanged (int width, int height) {
        Log.i ("GStreamer", "Media size changed to " + width + "x" + height);
        final GStreamerSurfaceView gsv = (GStreamerSurfaceView) this.findViewById(R.id.surface_video);
        gsv.media_width = width;
        gsv.media_height = height;
        runOnUiThread(new Runnable() {
            public void run() {
                gsv.requestLayout();
            }
        });
    }

    // The user started dragging the Seek Bar thumb
    public void onStartTrackingTouch(SeekBar sb) {
        nativePause();
    }

    // The user released the Seek Bar thumb
    public void onStopTrackingTouch(SeekBar sb) {
        // If this is a remote file, scrub seeking is probably not going to work smoothly enough.
        // Therefore, perform only the seek when the slider is released.
        if (is_playing_desired) nativePlay();
    }
}