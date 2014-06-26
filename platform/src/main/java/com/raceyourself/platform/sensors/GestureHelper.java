package com.raceyourself.platform.sensors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice.MotionRange;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.raceyourself.platform.gpstracker.Helper;
import com.raceyourself.platform.models.Device;
import com.raceyourself.platform.utils.UnityInterface;
import com.raceyourself.platform.utils.Utils;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.roscopeco.ormdroid.ORMDroidApplication;
import com.unity3d.player.UnityPlayerActivity;

public class GestureHelper extends UnityPlayerActivity {

    // Glass uses a different Gesture Detector to other devices
    private GestureDetector glassGestureDetector = null;
    private NormalGestureDetector normalGestureDetector = null;

    // current touch status, for polling from unity
    private float xPosition = 0;
    private float yPosition = 0;
    private long touchTime = 0;
    private int touchCount = 0;

    // Bluetooth
    private final static boolean REQUEST_BT = false;
    private BluetoothAdapter bt = null;
    public static enum BluetoothState {
        UNDEFINED,
        SERVER,
        CLIENT
    };
    private BluetoothState btState = BluetoothState.UNDEFINED;
    private Thread btInitThread = null; // Server accept thread or client connect thread
    private final String BT_NAME = "Glassfit";
    private final String BT_UUID = "cdc0b1dc-335a-4179-8aec-1dcd7ad2d832";
    private ConcurrentLinkedQueue<BluetoothThread> btThreads = new ConcurrentLinkedQueue<BluetoothThread>();

    // Intent results
    private final static int REQUEST_ENABLE_BT = 1;
    
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private final static String GCM_SENDER_ID = "892619514273";
    
    // accessors for polling from unity
    public float getTouchX() {
        return xPosition;
    }

    public float getTouchY() {
        return yPosition;
    }

    public long getTouchTime() {
        return touchTime;
    }
    
    public int getTouchCount() {
        return touchCount;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ORMDroidApplication.initialize(getApplicationContext());
        
        // Initialize a gesture detector
        // Glass uses a different Gesture Detector to other devices
        if (Helper.onGlass()) {
            glassGestureDetector = createGlassGestureDetector(this);
        } else {
            normalGestureDetector = new NormalGestureDetector();
        }
        
        if (checkPlayServices()) {
            Log.i("GestureHelper", "Play services available");        
            
            String regid = getRegistrationId(getApplicationContext());
            if (regid.isEmpty()) {
                registerInBackground();
            } else {
                Device self = Device.self();
                if (self != null) {
                    self.push_id = regid;
                    self.save();
                }
            }
        }
        
        Intent intent = getIntent();
        if (intent != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                UnityInterface.unitySendMessage("Platform", "OnActionIntent", intent.getData().toString());
            }
        }
        
        bt = BluetoothAdapter.getDefaultAdapter();
        if (bt != null) {
            if (REQUEST_BT && !bt.isEnabled()) {
                Log.i("GestureHelper", "Bluetooth not enabled. Enabling..");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);                
            } 
            if (bt.isEnabled()) {
                bluetoothStartup();                
            }
        }        
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                UnityInterface.unitySendMessage("Platform", "OnActionIntent", intent.getData().toString());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();        
        checkPlayServices();
    }
    
    /**
     * Gets the current registration ID for application on GCM service.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    public String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(Utils.GCM_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i("GestureHelper", "Registration not found.");
            return "";
        }
        return registrationId;
    }
    
    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Utils.GCM_REG_ID, regId);
        editor.commit();
        // Store in device pushId
        Device self = Device.self();
        if (self != null) {
            self.push_id = regId;
            self.save();
        }
    }
    
    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(this.getClass().getSimpleName(),
                Context.MODE_PRIVATE);
    }
    
    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<String, String, Boolean>() {
            @Override
            protected Boolean doInBackground(final String... params) {
                try {
                    Context context = getApplicationContext();
                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                    String regid = gcm.register(GCM_SENDER_ID);
                    Log.i("GestureHelper", "Device registered with GCM, id=" + regid);

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (final IOException ex) {
                    Log.e("GestureHelper", "Failed to register device with GCM", ex);
                    return false;
                }
                return true;
            }

        }.execute(null, null, null);
    }
    
    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        if (Helper.onGlass()) return false; // TODO: Fix resources so this special case isn't needed.
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.w("GestureHelper", "This device is not supported by Play Services");
            return false;
        }
        return true;
    }    
    
    /**
     * Activity result handler.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_ENABLE_BT == requestCode && bt != null) {
            bluetoothStartup();
        }
    }
    
    /**
     * Start Bluetooth server
     */
    public void startBluetoothServer() {
        btState = BluetoothState.SERVER;
        Log.i("GestureHelper", "Starting Bluetooth server..");
        bluetoothStartup();
    }

    /**
     * Start Bluetooth client
     */
    public void startBluetoothClient() {
        btState = BluetoothState.CLIENT;
        Log.i("GestureHelper", "Starting Bluetooth client..");
        bluetoothStartup();
    }
    
    /**
     * Common (delayable) startup method for Bluetooth
     */
    public void bluetoothStartup() {
        if (bt != null && bt.getName() != null && bt.getName().contains("Display")) Helper.setRemoteDisplay(true); // Act as a remote display
        if (bt == null || !bt.isEnabled()) return; // Delayed
        if (btInitThread != null) return; // Done
        Log.i("GestureHelper", "Bluetooth enabled: " + bt.isEnabled());
        if (BluetoothState.SERVER.equals(btState)) {
            btInitThread = new AcceptThread();
            btInitThread.start();
            Log.i("GestureHelper", "Bluetooth server started");            
        }
        if (BluetoothState.CLIENT.equals(btState)) {
            btInitThread = new ConnectThread();
            btInitThread.start();
            Log.i("GestureHelper", "Bluetooth client started");
        }
    }
    
    public void broadcast(String data) {
        broadcast(data.getBytes());
    }
    
    public void broadcast(byte[] data) {
        for (BluetoothThread btThread : btThreads) {
            btThread.send(data);
        }        
    }
    
    public String[] getBluetoothPeers() {
        List<String> peers = new LinkedList<String>();
        for (BluetoothThread thread : btThreads) {
            thread.keepalive();
        }
        for (BluetoothThread thread : btThreads) {
            peers.add(thread.getDevice().getName());
        }
        return peers.toArray(new String[peers.size()]);
    }
    
    /**
     * Whenever the content changes, we need to tell the new view to keep the
     * screen on and make sure all child components listen for touch input
     */
    @Override
    public void onContentChanged() {
        // Log.v("GestureHelper", "Content changed");
        super.onContentChanged();
        View view = this.findViewById(android.R.id.content);
        view.setKeepScreenOn(true);
        recurseViews(view);
    }

    /**
     * Loop through all child views and tell them to listen for touch input
     * 
     * @param view
     */
    private void recurseViews(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); ++i) {
                View foundView = vg.getChildAt(i);
                if (foundView != null) {
                    foundView.setFocusableInTouchMode(true);
                    recurseViews(foundView);
                }
            }
        }
    }

    /**
     * Listen for (and respond to) all track-pad events. This enables navigation
     * on glass.
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Log.v("GestureHelper", "Trackpad event detected");
        processEvent(event);
//        Log.d("GestureHelper", "Trackpad event:  x:" + xPosition + " y:" + yPosition + " downtime:"
//                + touchTime);
        // return true to prevent event passing onto parent
        return true;
    }

    /**
     * Listen for (and respond to) all touch-screen events. This enables
     * navigation on phones and tablets.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Log.v("GestureHelper", "Touchscreen event detected");
        processEvent(event);
//        Log.d("GestureHelper", "Touch-screen event:  x:" + xPosition + " y:" + yPosition
//                + " downtime:" + touchTime);
        // return true to prevent event passing onto parent
        return true;
    }
    
    /** 
     * Detect back button presses and send to unity.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
        	Log.d("GestureHelper", "Actual back button pressed!");
            UnityInterface.unitySendMessage("Scriptholder", "BackButton","");
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Stores the event's (x,y) co-ordinates (so the game can poll them) and
     * checks for gestures (which are sent ot the game via a UnityMessage)
     * 
     * @param event
     *            the raw event from the input device
     * @return true if the event has been consumed
     */
    private boolean processEvent(MotionEvent event) {
        // record the co-ordinates
        if (event.getAction() == event.ACTION_CANCEL || event.getAction() == event.ACTION_UP) {
            // no touch, so set position and downtime to null
            //touchCount -= event.getPointerCount();
        } else {
            // new touch or continuation of existing touch: update X, Y and
            // downtime
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                //touchCount += event.getPointerCount();
                // replace by finger listener
            }
            // need to scale raw x/y values to the range 0-1
            float xCoord = event.getX();
            float yCoord = event.getY();

            MotionRange xRange = event.getDevice().getMotionRange(MotionEvent.AXIS_X,
                    event.getSource());
            MotionRange yRange = event.getDevice().getMotionRange(MotionEvent.AXIS_Y,
                    event.getSource());

            xPosition = (xCoord - xRange.getMin()) / (xRange.getMax() - xRange.getMin());
            yPosition = (yCoord - yRange.getMin()) / (yRange.getMax() - yRange.getMin());
            touchTime = event.getEventTime() - event.getDownTime();
            Log.d("GestureHelper", "x:" + xPosition + ", y:" + yPosition + ", count:" + touchCount);
        }

        // check for gestures (may send unity message)
        if (glassGestureDetector != null) {
            glassGestureDetector.onMotionEvent(event);
        } else if (normalGestureDetector != null) {
            normalGestureDetector.onMotionEvent(event);
        }

        // return true to prevent event passing onto parent
        return true;
    }

    /**
     * Helper method to build a new Glass GestureDetector that sends messages to
     * unity when a touchpad gesture is recognised. Note - throws a runtime
     * exception on non-glass devices.
     * 
     * @param context
     * @return the newly created Gesture Detector
     */
    private GestureDetector createGlassGestureDetector(Context context) {

        GestureDetector gestureDetector = new GestureDetector(context);

        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {

            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    UnityInterface.unitySendMessage("Scriptholder", "IsTap", "");
                    return true;
                } else if (gesture == Gesture.SWIPE_LEFT) {
                    UnityInterface.unitySendMessage("Scriptholder", "FlingLeft", "");
                    return true;
                } else if (gesture == Gesture.SWIPE_RIGHT) {
                    UnityInterface.unitySendMessage("Scriptholder", "FlingRight", "");
                    return true;
                } else if (gesture == Gesture.SWIPE_UP) {
                    UnityInterface.unitySendMessage("Scriptholder", "SwipeUp", "");
                    return true;
                } else if (gesture == Gesture.TWO_SWIPE_LEFT) {
                    UnityInterface.unitySendMessage("Scriptholder", "TwoSwipeLeft", "");
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                    UnityInterface.unitySendMessage("Scriptholder", "TwoTap", "");
                    return true;
                } else if (gesture == Gesture.THREE_TAP) {
                    UnityInterface.unitySendMessage("Scriptholder", "ThreeTap", "");
                } else if(gesture == Gesture.SWIPE_DOWN) {
                	Log.d("GestureHelper", "Back button is swipe down gesture!");
                	UnityInterface.unitySendMessage("Scriptholder", "FlingDown", "");
                }
                
                return false;
            }
        });
        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
            @Override
            public void onFingerCountChanged(int previousCount, int currentCount) {
                touchCount = currentCount;
            }
        });
        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta, float velocity) {
                // do something on scrolling
                return true;
            }
        });
        return gestureDetector;
    }
    
    /**
     * Helper method to build a new Glass GestureDetector that sends messages to
     * unity when a touchpad gesture is recognised. Note - throws a runtime
     * exception on non-glass devices.
     * 
     * @param context
     * @return the newly created Gesture Detector
     */
    private class NormalGestureDetector {
        
        int lastDownPointerID = MotionEvent.INVALID_POINTER_ID;
        float lastDownX = 0;
        float lastDownY = 0;
        
        float swipeDistanceX = 0.0f;
        float swipeDistanceY = 0.0f;
        float swipeSpeedX = 0.0f;
        float swipeSpeedY = 0.0f;
        
        public void onMotionEvent(MotionEvent event) {
            
            if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                
                // TODO: check for event.ACTION_POINTER_UP to detect a release of a 2nd or subsequent finger
                
                // end of an event - trigger action!
                long pressTime = event.getEventTime() - event.getDownTime(); // in milliseconds
                int numberOfFingers = event.getPointerCount();
                
                // calculate gesture distance and speed in both X and Y directions
                if (event.getPointerId(0) == lastDownPointerID) {
                    // need to scale raw x/y values to the range 0-1
                    MotionRange xRange = event.getDevice().getMotionRange(MotionEvent.AXIS_X,
                            event.getSource());
                    MotionRange yRange = event.getDevice().getMotionRange(MotionEvent.AXIS_Y,
                            event.getSource());

                    swipeDistanceX = (event.getX() - lastDownX) / (xRange.getMax() - xRange.getMin()); // 0-1, 1 means full length of pad
                    swipeDistanceY = (event.getY() - lastDownY) / (yRange.getMax() - yRange.getMin()); // 0-1, 1 means full length of pad
                    swipeSpeedX = swipeDistanceX / pressTime * 1000; // pads per second
                    swipeSpeedY = swipeDistanceY / pressTime * 1000; // pads per second
                } else {
                    swipeDistanceX = 0;
                    swipeDistanceY = 0;
                    swipeSpeedX = 0;
                    swipeSpeedY = 0;
                }
                
                // send any interesting gestures to unity 
                if (Math.abs(swipeDistanceX) < 0.05f && Math.abs(swipeDistanceY) < 0.05f && pressTime < 400) {
                    // we have some kind of tap
                    if (numberOfFingers == 1) {
                        UnityInterface.unitySendMessage("Scriptholder", "IsTap", "");
                    } else if (numberOfFingers == 2) {
                        UnityInterface.unitySendMessage("Scriptholder", "TwoTap", "");
                    } else if (numberOfFingers == 3) {
                        UnityInterface.unitySendMessage("Scriptholder", "ThreeTap", "");
                    }
                } else if (Math.abs(swipeDistanceX) > 0.3f && Math.abs(swipeSpeedX) > 1.5f) {
                    // we have a horizontal swipe
                    if (swipeDistanceX < 0.0f) {
                        UnityInterface.unitySendMessage("Scriptholder", "FlingLeft", "");
                    } else {
                        UnityInterface.unitySendMessage("Scriptholder", "FlingRight", "");
                    }
                } else if (Math.abs(swipeDistanceY) > 0.3f && Math.abs(swipeSpeedY) > 1.5f) {
                    // we have a vertical swipe
                    if (swipeDistanceX < 0.0f) {
                        UnityInterface.unitySendMessage("Scriptholder", "SwipeUp", "");
                    } else {
                        UnityInterface.unitySendMessage("Scriptholder", "SwipeDown", "");
                    }
                }
                
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {

                if (event.getPointerCount() == 1) {
                    // first finger of new touch - store until it is released
                    lastDownPointerID = event.getPointerId(0);
                    lastDownX = event.getX();
                    lastDownY = event.getY();
                }
                
            }
        }

    }

    /**
     * Bluetooth server thread
     */
    private class AcceptThread extends Thread {
        private boolean done = false;
        private final BluetoothServerSocket mmServerSocket;
     
        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            Log.i("AcceptThread", "Creating server socket..");
            try {
                tmp = bt.listenUsingRfcommWithServiceRecord(BT_NAME, UUID.fromString(BT_UUID));
            } catch (IOException e) { 
                Log.e("AcceptThread", "Error creating server socket", e);                
            }
            mmServerSocket = tmp;
            Log.i("AcceptThread", "Created server socket");
        }
     
        public void run() {
            // Keep listening until exception occurs 
            while (!done) {
                try {
                    // Block until we get a socket, pass it to the bluetooth thread.
                    manageConnectedSocket(mmServerSocket.accept());
                } catch (IOException e) {
                    Log.e("AcceptThread", "Error accepting socket", e);
                    break;
                }
            }
        }
     
        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            done = true;
            try {
                Log.i("AcceptThread", "Closing server socket..");
                mmServerSocket.close();
            } catch (IOException e) { 
                Log.e("AcceptThread", "Error closing server socket", e);
            }
        }
    }
   
    /**
     * Bluetooth client thread
     */
    private class ConnectThread extends Thread {
        private boolean done = false;
        private Set<BluetoothDevice> connectedDevices = new HashSet<BluetoothDevice>();        
        private UUID uuid = UUID.fromString(BT_UUID);

        public ConnectThread() {
            Log.i("ConnectThread", "Creating client sockets..");    
        }
        
        public void run() {
            while (!done) {
                Set<BluetoothDevice> devices = new HashSet<BluetoothDevice>(bt.getBondedDevices());
                devices.removeAll(connectedDevices);
                for (BluetoothDevice device : devices) {
                    if (done) break;
                    try {
                        Log.i("ConnectThread", "Creating client socket for " + device.getName() + "/" + device.getAddress());            
                        BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                        socket.connect();
                        manageConnectedSocket(socket);
                        connectedDevices.add(device);
                    } catch (IOException e) {
                        Log.e("ConnectThread", "Error creating client socket", e);
                    }
                }
                synchronized(this) {
                    try {
                        this.wait(5000);
                    } catch (InterruptedException e) {
                        Log.e("ConnectThread", "Interrupted while waiting", e);
                    }
                }
            }
        }
        
        public void reconnect(BluetoothDevice device) {
            connectedDevices.remove(device);
            synchronized(this) {
                this.notify();
            }
        }
     
        public void cancel() {
            done = true;
            synchronized(this) {
                this.notify();
            }
        }
    }
   
    public void manageConnectedSocket(BluetoothSocket socket) {
        BluetoothThread btThread = new BluetoothThread(socket);
        btThread.start();
        btThreads.add(btThread);
    }
    
    /** 
     * Common Bluetooth thread
     */
    private class BluetoothThread extends Thread {
        private boolean done = false;
        private final BluetoothSocket socket;
        private InputStream is = null;
        private OutputStream os = null;
        private ConcurrentLinkedQueue<byte[]> msgQueue = new ConcurrentLinkedQueue<byte[]>();
        private long alive = 0L;
        private long keepalive = 0L;

        public BluetoothThread(BluetoothSocket socket) {
            this.socket = socket;
            Log.i("BluetoothThread", "Connected to " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress());
            Helper.message("OnBluetoothConnect", "Connected to " + socket.getRemoteDevice().getName());
        }
        
        public void send(byte[] data) {
            msgQueue.add(data);
            Log.i("BluetoothThread", "Queue size: " + msgQueue.size());
            synchronized(this) {
                this.notify();
            }
        }
        
        @Override
        public void run() {
            byte[] buffer = new byte[1024]; 
            int bufferOffset = 0;
            int packetLength = -1;
            ByteBuffer header = ByteBuffer.allocate(8);
            header.order(ByteOrder.BIG_ENDIAN); // Network byte order
            try {
                is = socket.getInputStream();
                os = socket.getOutputStream();
                while (!done) {
                    byte[] data = msgQueue.poll();
                    boolean busy = false;
                    /// Write queued packets
                    if (data != null) {
                        /// Packetize using a simple header
                        header.clear();
                        // Marker
                        header.putInt(0xd34db33f);
                        // Length
                        header.putInt(data.length);
                        header.flip();
                        os.write(header.array(), header.arrayOffset(), header.limit());
                        os.write(data);
                        Log.i("BluetoothThread", "Sent " + data.length + "B to "  + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress());
                        busy = true;
                        alive = System.currentTimeMillis();
                    }
                    /// Read incoming packets
                    if (is.available() > 0) {
                        // New packet
                        if (packetLength < 0) {
                            /// Depacketize                           
                            // Verify marker
                            if (is.read() != 0xd3 || is.read() != 0x4d || is.read() != 0xb3 || is.read() != 0x3f) { 
                                Log.e("BluetoothThread", "Received invalid header from " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress());
                                cancel(); 
                                if (btInitThread instanceof ConnectThread) {
                                    ((ConnectThread)btInitThread).reconnect(socket.getRemoteDevice());
                                }
                                return; 
                            }  
                            // 32-bit length
                            header.clear();
                            header.put((byte)is.read());
                            header.put((byte)is.read());
                            header.put((byte)is.read());
                            header.put((byte)is.read());
                            header.flip();
                            packetLength = header.getInt();
                            if (packetLength < 0) {
                                Log.e("BluetoothThread", "Received invalid packet length " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress());
                                cancel(); 
                                if (btInitThread instanceof ConnectThread) {
                                    ((ConnectThread)btInitThread).reconnect(socket.getRemoteDevice());
                                }
                                return;                                 
                            }
                            if (packetLength > buffer.length) {
                                // Resize buffer
                                buffer = new byte[packetLength];
                            }
                            bufferOffset = 0;
                        }
                        // Packet payload
                        int read = is.read(buffer, bufferOffset, packetLength - bufferOffset);
                        if (read < 0) {
                            Log.e("BluetoothThread", "Received EOF from " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress());
                            cancel();
                            if (btInitThread instanceof ConnectThread) {
                                ((ConnectThread)btInitThread).reconnect(socket.getRemoteDevice());
                            }
                            return;
                        }
                        bufferOffset += read;
                        Log.i("BluetoothThread", "Received " + read + "B of " + packetLength + "B packet, " + (packetLength - bufferOffset) + "B left, from " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress() );
                        if (packetLength == bufferOffset) {
                            if (packetLength > 0) {
                                String message = new String(buffer, 0, packetLength);
                                Helper.message("OnBluetoothMessage", message);
                            }
                            packetLength = -1;
                        }
                        busy = true;
                        alive = System.currentTimeMillis();
                    }
                    if (!busy) {
                        try {
                            synchronized(this) {
                                this.wait(100);
                            }
                        } catch (InterruptedException e) {
                            Log.e("BluetoothThread", "InterruptedException for " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress(), e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("BluetoothThread", "IOException for " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress(), e);
                if (!done) {
                    if (btInitThread instanceof ConnectThread) {
                        ((ConnectThread)btInitThread).reconnect(socket.getRemoteDevice());
                    }                    
                }
            } finally {
                cancel();
            }
            btThreads.remove(this);
        }
        
        public void keepalive() {
            // Send ping to check if connection is still up if unused
            if (System.currentTimeMillis() - alive > 5000 && System.currentTimeMillis() - keepalive > 5000) {
                keepalive = System.currentTimeMillis();
                if (msgQueue.isEmpty()) send(new byte[0]);
            }
        }
        
        public BluetoothDevice getDevice() {
            return socket.getRemoteDevice();
        }
        
        public void cancel() {
            Helper.message("OnBluetoothConnect", "Disconnected from " + socket.getRemoteDevice().getName());
            done = true;
            try {
                if (is != null) is.close();
                if (os != null) os.close();
                socket.close();
            } catch (IOException e) {
                Log.e("BluetoothThread", "IOException for " + socket.getRemoteDevice().getName() + "/" + socket.getRemoteDevice().getAddress(), e);
            }
        }
    }
    
}