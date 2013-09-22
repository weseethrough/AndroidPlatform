
package com.glassfitgames.glassfitplatform.gpstracker;

import java.util.ArrayDeque;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import com.glassfitgames.glassfitplatform.gpstracker.TargetTracker.TargetSpeed;
import com.glassfitgames.glassfitplatform.models.Position;
import com.glassfitgames.glassfitplatform.models.Track;
import com.roscopeco.ormdroid.ORMDroidApplication;
import com.unity3d.player.UnityPlayer;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class GPSTracker implements LocationListener {

    private final Context mContext;
    
    // ordered list of recent positions, last = most recent
    private ArrayDeque<Position> recentPositions = new ArrayDeque<Position>(10);
    private BearingCalculationAlgorithm bearingAlgorithm = new BearingCalculationAlgorithm(recentPositions);
    // flag for GPS status
    private boolean isGPSEnabled = false;

    // flag for whether we're actively tracking
    private boolean isTracking = false;

    private boolean indoorMode = false; // if true, we generate fake GPS updates

    private float indoorSpeed = TargetSpeed.WALKING.speed(); // speed for fake GPS updates

    private int trackId; // ID of the current track

    private long elapsedDistance; // distance so far in metres

    private long startTime; // time so far in milliseconds

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000; // 1 second

    private static final float MAX_TOLERATED_POSITION_ERROR = 21; // 21 metres

    // Declaring a Location Manager
    protected LocationManager locationManager;

    private Timer timer;

    private GpsTask task;

    public GPSTracker(Context context) {
        this.mContext = context;

        ORMDroidApplication.initialize(context);
        Log.i("ORMDroid", "Initalized");

        Track track = new Track("Test");
        track.save();
        trackId = track.getId();
        Log.i("GlassFitPlatform", "New track created with ID " + trackId);

        elapsedDistance = 0;
        startTime = 0;

        initGps();

    }

    public Position getCurrentPosition() {
        if (recentPositions.size() > 0) {
            return recentPositions.getLast();
        } else {
            return null;
        }
    }

    private void initGps() {

        locationManager = (LocationManager)mContext.getSystemService(Service.LOCATION_SERVICE);

        // getting GPS status
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!isGPSEnabled /* && !isNetworkEnabled */) {
            // no network provider is enabled
            showSettingsAlert();
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, (LocationListener)this);
            Log.d("GlassFitPlatform", "GPS location updates requested");
        }

    }

    public void startTracking() {
        
        Log.d("GlassFitPlatform", "startTracking() called");
        isTracking = true;

        if (indoorMode == true) {
            // start TimerTask to generate fake position data once per second
            timer = new Timer();
            task = new GpsTask();
            timer.scheduleAtFixedRate(task, 0, 1000);
        }

    }

    public void stopTracking() {
        Log.d("GlassFitPlatform", "stopTracking() called");
        isTracking = false;
        if (task != null) task.cancel();
    }
    
    public boolean isIndoorMode() {
        return indoorMode;
    }

    public void setIndoorMode(boolean indoorMode) {
        this.indoorMode = indoorMode;
    }

    /**
     * Sets the speed for indoor mode from the TargetSpeed enum
     * of reference speeds.
     * @param TargetSpeed indoorSpeed
     */
    public void setIndoorSpeed(TargetSpeed indoorSpeed) {
        this.indoorSpeed = indoorSpeed.speed();
    }
    
    /**
     * Sets the speed for indoor mode to the supplied float value,
     * measured in m/s.
     * @param float indoorSpeed
     */
    public void setIndoorSpeed(float indoorSpeed) {
        this.indoorSpeed = indoorSpeed;
    }    


    /**
     * Task to regularly generate fake position data when in indoor mode. startLogging() triggers
     * starts the task, stopLogging() ends it.
     */
    private class GpsTask extends TimerTask {
        private double drift = 0f;

        public void run() {
                // Fake location
                Location location = new Location("");
                location.setTime(System.currentTimeMillis());
                location.setLatitude(location.getLatitude() + drift);
                location.setSpeed(indoorSpeed);

                // Fake movement due north at indoorSpeed (converted to degrees)
                drift += indoorSpeed / 111229d;
                
                // Broadcast the fake location the local listener only (otherwise risk
                // confusing other apps!)
                onLocationChanged(location);
            }
    }

    /**
     * Stop using GPS listener Calling this function will stop using GPS in your app
     */
    private void stopUsingGPS() {
        if (locationManager != null) {
            locationManager.removeUpdates(GPSTracker.this);
        }
    }

    /**
     * Function to check GPS enabled
     * 
     * @return boolean
     * @deprecated
     */
    public boolean canGetPosition() {
        return recentPositions.size() > 0;
    }
    
    /**
     * Do we have a GPS fix yet? If false, wait until it is true before expecting 
     * the other functions in this class to work.
     * 
     * @return true if we have a position fix
     */
    public boolean hasPosition() {
        return recentPositions.size() > 0;
    }

    /**
     * Function to show settings alert dialog On pressing Settings button will launch Settings
     * Options
     */
    private void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

        // Setting Dialog Title
        alertDialog.setTitle("GPS is setAbstractTrackertings");

        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mContext.startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    
    @Override
    public void onLocationChanged(Location location) {

        // save every position update for analysis
        Position gpsPosition = new Position(trackId, location);
        
        // stop here if we're not tracking
        if (!isTracking) return;
        
        // save all positions for later analysis, even if we don't use the
        gpsPosition.save();
        
        // if the latest gpsPosition doesn't meets our accuracy criteria, throw it away
        if (gpsPosition.getEpe() > MAX_TOLERATED_POSITION_ERROR) {
            Log.d("GPSTracker", "Throwing away position with error " + gpsPosition.getEpe());
        }
        
        // otherwise, add to the buffer for later use
        Log.d("GPSTracker", "Using position with error " + gpsPosition.getEpe());
        if (recentPositions.isEmpty()) {
            // if we only have one position, this must be the first in the route
            startTime = gpsPosition.getDeviceTimestamp();
            recentPositions.addLast(gpsPosition); //recentPositions.getLast() now points at gpsPosition.
            return;
        }
        if (recentPositions.size() >= 10) {
            // if the buffer is full, discard the oldest element
            recentPositions.removeFirst();
        }
        Position lastPosition = recentPositions.getLast(); // remember previous for distance calc
        recentPositions.addLast(gpsPosition); //recentPositions.getLast() now points at gpsPosition.
        
        // calculate distance between lastPosition and currentPosition; add to elapsedDistance 
        elapsedDistance += Position.distanceBetween(lastPosition, gpsPosition);
        
        // calculate corrected bearing
        // this is more accurate than the raw GPS bearing as it averages several recent positions
        correctBearing(gpsPosition);
        
        gpsPosition.save();

        // Broadcast new state to unity3D and to the log
        broadcastToUnity();
        logPosition();

    }
    
    // calculate corrected bearing
    // this is more accurate than the raw GPS bearing as it averages several recent positions
    private void correctBearing(Position gpsPosition) {
        float[] correctedBearing = bearingAlgorithm.calculateCurrentBearing();
        if (correctedBearing != null) {
          gpsPosition.setCorrectedBearing(correctedBearing[0]);
          gpsPosition.setCorrectedBearingR(correctedBearing[1]);
          gpsPosition.setCorrectedBearingSignificance(correctedBearing[2]);
        }    
    }
    
    private void broadcastToUnity() {
        JSONObject data = new JSONObject();
        try {
            data.put("speed", getCurrentPace());
            data.put("bearing", getCurrentBearing());
            data.put("elapsedDistance", getElapsedDistance());
        } catch (JSONException e) {
            Log.e("GPSTracker", e.getMessage());
        }
        // Sending the message needs the unity native library installed:
        try {
            UnityPlayer.UnitySendMessage("script holder", "NewGPSPosition", data.toString());
        } catch (UnsatisfiedLinkError e) {
            Log.i("GlassFitPlatform","Failed to send unity message, probably because Unity native libraries aren't available (e.g. you are not running this from Unity");
            Log.i("GlassFitPlatform",e.getMessage());
        }
    }
    
    private void logPosition() {
        Log.d("GPSTracker", "New elapsed distance is: " + getElapsedDistance());
        Log.d("GPSTracker", "Current speed estimate is: " + getCurrentPace());
        Log.d("GPSTracker", "Current bearing estimate is: " + getCurrentBearing());
        Log.d("GPSTracker", "New elapsed time is: " + getElapsedTime());
        Log.d("GPSTracker", "\n");  
    }

    public boolean hasBearing() {
        return recentPositions.getLast().getCorrectedBearing() != null;
    }
    
    public Float getCurrentBearing() {
        return recentPositions.getLast().getCorrectedBearing();
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    // @Override
    // public IBinder onBind(Intent arg0) {
    // return null;
    // }

    public float getCurrentPace() {
        if (recentPositions.size() > 0) {
            return recentPositions.getLast().getSpeed();
        } else {
            return 0;
        }
    }

    public long getElapsedDistance() {
        return elapsedDistance;
    }

    public long getElapsedTime() {
        if (recentPositions.size() > 0) {
            // use system time rather than timestamp of last position
            // because last fix may be several seconds ago
            return System.currentTimeMillis() - startTime;
        } else {
            return 0;
        }
    }

}
