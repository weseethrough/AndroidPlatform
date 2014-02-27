package com.glassfitgames.glassfitplatform.gpstracker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.glassfitgames.glassfitplatform.models.Position;
import com.glassfitgames.glassfitplatform.utils.ScreenLog;
import com.glassfitgames.glassfitplatform.utils.Stopwatch;
import com.lf.api.EquipmentManager;
import com.lf.api.EquipmentObserver;
import com.lf.api.License;
import com.lf.api.models.WorkoutPreset;
import com.lf.api.models.WorkoutResult;
import com.lf.api.models.WorkoutStream;

public class LifeFitnessTracker extends AbstractTracker implements EquipmentObserver {
    
    private static LifeFitnessTracker instance; // singleton instance
    private Context context; // application context to allow binding to services
    private ServiceConnection lifeFitnessServiceConnection;  // connection to the LF service
    
    private boolean bound = false;  // true when connected to the LF service (on the local device)
    private boolean connected = false;  // true when the LF service is connected to a LF machine (e.g. treadmill)
    private boolean tracking = false;  // true when recording / counting up distance, time etc
    
    // data from last sample (usually updated at 1Hz)
    private float speedAtLastSample = 0.0f;
    private float bearingAtLastSample = -999.0f;
    private double elapsedDistanceAtLastSample = 0;
    private long elapsedTimeAtLastSample = 0;
    
    private Stopwatch interpolationStopwatch = new Stopwatch();
    
    private ScreenLog screenLog = new ScreenLog("LifeFitnessTracker"); // description of current internal state
    
    // time in milliseconds over which current position will converge with the
    // more accurate but non-continuous extrapolated console position
    private static final long DISTANCE_CORRECTION_MILLISECONDS = 1500; 
    
    private LifeFitnessTracker() {} // private constructor enforces singleton
    
    /**
     * Returns the singleton instance of LifeFitnessTracker
     */
    public static LifeFitnessTracker getInstance() {
        if (instance == null) instance = new LifeFitnessTracker();
        return instance;
    }
    
    /**
     * Register with the Life Fitness service and start listening for incoming
     * data
     * 
     * @param ctx the current application context or foreground activity to
     *            allow us to bind to the LF service
     */
    public void init(Context ctx) {
        
        this.context = ctx;
        
        // Display current state
        screenLog.clear();
        screenLog.log("Initializing...");
        
        // Apply our LifeFitness license key
        screenLog.log("Checking LF license...");
        //License.getInstance().setEnvironmentToLive(ctx, true);  // only needed when launch on LF site. Breaks dev license keys.
        //License.getInstance().setLicense(ctx, "553-3729043115-87079");
        //License.getInstance().setLicense(ctx, "605-0139711041-59355");
        License.getInstance().setLicense(ctx, "testlicense");
        
        int tempvalue = -1;
        while (tempvalue != License.VALID) {
            tempvalue = License.getInstance().state;
        }

        if (License.getInstance().state == License.NOT_VALID) {
            screenLog.log("License invalid.");
        } else if (License.getInstance().state == License.VALID) {
            screenLog.log("License validated successfully.");
        }
        
        ctx.startService(new Intent(ctx, EquipmentManager.class));
        
        // set up a connection to the Life Fitness service, and register
        // LifeFitnessTracker as a listener for updates from the equipment
        lifeFitnessServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName pName) {
                screenLog.log("Unbound from Life Fitness service.");
                bound = false;
            }

            @Override
            public void onServiceConnected(ComponentName pName, IBinder pService) {
                screenLog.log("Bound to Life Fitness service.");
                // temp: set the LF library to debug mode for testing
                //EquipmentManager.DEBUG_MODE = true;
                EquipmentManager.getInstance().registerObserver(LifeFitnessTracker.this);
                EquipmentManager.getInstance().start();
                screenLog.log("Waiting for Life Fitness console");
                bound = true;
            }
        };
        
        // Bind to the Life Fitness service
        //connect();
        
        // Nasty workaround for invalid license - wait a bit, then unbind and rebind to LF service
        // Need to do this on a separate thread as the onServiceConnected seems
        // to be executed on the main thread, so never runs if we just sleep
        // waiting for it
//        new Thread() {
//            public void run() {
//                try {
//                    screenLog.log("Sleeping...");
//                    Log.d(this.getClass().getSimpleName(), "Sleeping...");
//                    while (!bound) {
//                        Thread.sleep(500);
//                    }
//                    connect();
//                } catch (InterruptedException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }
//        }.run();
    }
    
    public void connect() {
        screenLog.log("RaceYourself is binding to the LifeFitness service...");
        context.bindService(new Intent(context, EquipmentManager.class),
                lifeFitnessServiceConnection,
                Context.BIND_AUTO_CREATE);
    }
    
    public void disconnect() {
        screenLog.clear();
        screenLog.log("RaceYourself is unbinding from the LifeFitness service...");
        EquipmentManager.getInstance().stop();
        EquipmentManager.getInstance().unregisterObserver(instance);
        context.unbindService(lifeFitnessServiceConnection);
    }
    
    @Override
    public void setIndoorMode(boolean indoor) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isIndoorMode() {
        return false;
    }

    @Override
    public boolean hasPosition() {
        return connected;
    }

    @Override
    public void startTracking() {
        if (connected) {
            // Set the equipment to a difficulty level of 3 (just to demonstrate
            // we can do it)
            screenLog.log("Started tracking");
            EquipmentManager.getInstance().sendSetWorkoutLevel(3);
            interpolationStopwatch.start();
            tracking = true;
        }
    }

    @Override
    public void stopTracking() {
        // TODO Auto-generated method stub
        // TODO Send stop signal to LF machine
        screenLog.log("Stopped tracking");
        interpolationStopwatch.stop();
        tracking = false;
    }
    
    @Override
    public boolean isTracking() {
        return tracking;
    }

    @Override
    public void startNewTrack() {
        // TODO Auto-generated method stub
    }

    @Override
    public Position getCurrentPosition() {
        return null;  // most unity code handles null positions nicely
    }

    @Override
    public float getCurrentSpeed() {
        return speedAtLastSample;
    }
    
    @Override
    public float getSampleSpeed() {
        return speedAtLastSample;
    }

    @Override
    public float getCurrentBearing() {
        return bearingAtLastSample;
    }

    
    private double extrapolatedConsoleDistance = 0;
    private double extrapolatedDeviceDistance = 0;
    private long tickTime = 0;
    private long lastTickTime = 0;
    
    @Override
    public double getElapsedDistance() {
        
        if (!isTracking()) return extrapolatedDeviceDistance;

        // if we're just starting, catch up with the console immediately
        if (lastTickTime == 0) {
            interpolationStopwatch.reset();
            extrapolatedDeviceDistance = extrapolatedConsoleDistance;
            tickTime = System.currentTimeMillis();
        }
        lastTickTime = tickTime;
        tickTime = System.currentTimeMillis();

        // calculate the speed we need to move at to make our smooth internal
        // distance converge with the non-continuous external distance over
        // a period of DISTANCE_CORRECTION_MILLISECONDS
        double correctiveSpeed = getSampleSpeed()
                + (extrapolatedConsoleDistance - extrapolatedDeviceDistance) * 1000.0
                / DISTANCE_CORRECTION_MILLISECONDS;

        // extrapolate (estimate) external distance for next loop. Non-continuous when a new sample comes in.
        extrapolatedConsoleDistance = getSampleDistance() + getSampleSpeed()
                * (interpolationStopwatch.elapsedTimeMillis()) / 1000.0;
        
        // extrapolate smooth, continuous internal distance for next loop & return. Always continuous.
        extrapolatedDeviceDistance += correctiveSpeed * (tickTime - lastTickTime) / 1000.0;
        return extrapolatedDeviceDistance;
    }
    
    @Override
    public double getSampleDistance() {
        return elapsedDistanceAtLastSample;
    }

    @Override
    public long getElapsedTime() {
        // TODO: inconsistency before first sample of track arrives 
        return elapsedTimeAtLastSample + interpolationStopwatch.elapsedTimeMillis();
    }
    
    public String toString() {
        return "LifeFitnessTracker singleton object";
    }
    
    public String getScreenLog() {
        return screenLog.get();
    }
    
    public void setIncline(double incline) {
        if (connected) {
            EquipmentManager.getInstance().sendSetWorkoutIncline(incline);
        }
    }
    
    public void setDifficulty(int level) {
        if (connected) {
            EquipmentManager.getInstance().sendSetWorkoutLevel(level);
        }
    }
    
    public void setResistance(int watts) {
        if (connected) {
            EquipmentManager.getInstance().sendSetWorkoutWatts(watts);
        }
    }
    
    public void setTargetHeartRate(int bpm) {
        if (connected) {
            EquipmentManager.getInstance().sendSetWorkoutThr(bpm);
        }
    }
    
    public void setConsoleMessage(String message) {
        if (connected) {
            EquipmentManager.getInstance().sendShowConsoleMessage(message);
        }
    }

    
    // ------------------------------------------------------------
    // The following methods are callbacks from the LifeFitness API
    // When connected, these will be called up to once per second to provide
    // workout data from the Life Fitness console
    
    @Override
    public void onAutoLoginRequest() {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onAutoLoginRequest() called by LF API");
    }

    @Override
    public void onConnected() {
        Log.d(this.getClass().getSimpleName(), "onConnected() called by LF API");
        // display some useful info on the screen
        int deviceId = EquipmentManager.getInstance().getDeviceType();
        String consoleVersion = EquipmentManager.getInstance().getConsoleVersion(); 
        screenLog.log("Connected to type " + deviceId + " Life Fitness device.");
        screenLog.log("Console version is " + consoleVersion + ".");
        screenLog.log("Press go on the console to start");
        connected = true;
    }

    @Override
    public void onConnection() {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onConnection() called by LF API");
    }

    @Override
    public void onConsoleMaxInclineReceived(double arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onConsoleMaxInclineReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onConsoleMaxTimeReceived(int arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onConsoleMaxTimeReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onConsoleUnitsReceived(byte arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onConsoleUnitsReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onDisconnected() {
        screenLog.log("Disconnected from the Life Fitness device.");
        connected = false;
        if (isTracking()) stopTracking();
    }

    @Override
    public void onError(Exception arg0) {
        // TODO Auto-generated method stub
        Log.e(this.getClass().getSimpleName(), "onError(" + arg0.getMessage() + ") called by LF API");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        arg0.printStackTrace(pw);
        sw.toString();
        Log.e(this.getClass().getSimpleName(), sw.toString());
    }

    @Override
    public void onInit() {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onInit() called by LF API");
    }

    @Override
    public List<WorkoutPreset> onSendingWorkoutPreset() {
        // TODO This is what the console is set up to target (dist, time etc)
        // TODO Should store these locally to set in-game target distance/calories.
        // TODO May change mid-race! Need API for targets.
        Log.d(this.getClass().getSimpleName(), "onSendingWorkoutPreset() called by LF API");
        return null;
    }

    @Override
    public void onSetWorkoutInclineAckReceived(boolean arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onSetWorkoutInclineAckReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onSetWorkoutLevelAckReceived(boolean arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onSetWorkoutLevelAckReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onSetWorkoutThrAckReceived(boolean arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onSetWorkoutThrAckReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onSetWorkoutWattsAckReceived(boolean arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onSetWorkoutWattsAckReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onShowConsoleMessageAckReceived(boolean arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onShowConsoleMessageAckReceived(" + arg0 + ") called by LF API");
    }

    @Override
    public void onStreamReceived(WorkoutStream arg0) {
        Log.d(this.getClass().getSimpleName(), "onStreamReceived() called by LF API");
        speedAtLastSample = (float)arg0.getCurrentSpeed()*1000/3600;  // convert km/hr to m/s
        if (arg0.getAccumulatedDistance() == 0.0 && speedAtLastSample > 0.0f) {
            // DEBUG mode doesn't seem to report distances, so fudge them
            elapsedDistanceAtLastSample += speedAtLastSample * (arg0.getWorkoutElapseSeconds()*1000.0-elapsedTimeAtLastSample);
        } else {
            // real machine should report distance
            elapsedDistanceAtLastSample = arg0.getAccumulatedDistance()*1000;  // convert distance from km to m    
        }
        elapsedTimeAtLastSample = (long)(arg0.getWorkoutElapseSeconds()*1000.0);  // convert seconds to milliseconds
        interpolationStopwatch.reset();
        
        screenLog.clear();
        screenLog.log("Current speed is " + arg0.getCurrentSpeed() + "?");
        screenLog.log("Current heartrate is " + arg0.getCurrentHeartRate() + "bpm");
        screenLog.log("Total distance covered is " + arg0.getAccumulatedDistance() + "m.");
        screenLog.log("Total calories burned " + arg0.getAccumulatedCalories() + "kcal");
        screenLog.log("Total workout time is " + (int)(getElapsedTime()/1000.0) + "s.");
    }

    @Override
    public void onWorkoutPaused() {
        Log.d(this.getClass().getSimpleName(), "onWorkoutPaused() called by LF API");
        if (isTracking()) stopTracking();
    }

    @Override
    public void onWorkoutPresetSent() {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onWorkoutPresetSent() called by LF API");
    }

    @Override
    public void onWorkoutResultReceived(WorkoutResult arg0) {
        // TODO Auto-generated method stub
        Log.d(this.getClass().getSimpleName(), "onWorkoutResultReceived() called by LF API");
    }

    @Override
    public void onWorkoutResume() {
        Log.d(this.getClass().getSimpleName(), "onWorkoutResume() called by LF API");
        if (!isTracking()) startTracking();
    }

}
