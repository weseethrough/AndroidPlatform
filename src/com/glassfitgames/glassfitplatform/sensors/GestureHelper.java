package com.glassfitgames.glassfitplatform.sensors;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.glassfitgames.glassfitplatform.R;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.qualcomm.QCARUnityPlayer.QCARPlayerActivity;
import com.unity3d.player.UnityPlayer;

public class GestureHelper extends QCARPlayerActivity {
	
	private UnityPlayer mUnityPlayer;
	
	GestureDetector mGestureDetector;
    	
	@Override
	 public void onCreate(Bundle savedInstanceState) {
		 super.onCreate(savedInstanceState);
		 
		 // Create the UnityPlayer
	        mUnityPlayer = super.getUnityPlayer();
	        int glesMode = mUnityPlayer.getSettings().getInt("gles_mode", 1);
	        boolean trueColor8888 = false;
	        mUnityPlayer.init(glesMode, trueColor8888);
		 
		 //gmListener = new GenericMotionListener();
		 mGestureDetector = createGestureDetector(this);
		 
		 setContentView(R.layout.test_layout);
		 
		 // Add the Unity view
	        FrameLayout layout = (FrameLayout) findViewById(R.id.unityLayout);
	        if(layout == null) {
	        	Log.e("GestureHelper", "layout null");
	        }
	        
	        if(mUnityPlayer.getView() == null) {
	        	Log.e("GestureHelper", "Unity view null");
	        }
	        LayoutParams lp = new LayoutParams (LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
	        layout.addView(mUnityPlayer.getView(), 0, lp);

			// Set the focus
	        FrameLayout mainLayout = (FrameLayout) findViewById(R.id.mainLayout);
			mainLayout.setFocusableInTouchMode(true);
	 }
	
	private GestureDetector createGestureDetector(Context context) {
		GestureDetector gestureDetector = new GestureDetector(context);
		
		gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
			
			@Override
			public boolean onGesture(Gesture gesture) {
				if(gesture == Gesture.TAP) {
					Log.e("GestureHelper", "Tap Function On");
					try {
						UnityPlayer.UnitySendMessage("Scriptholder", "isTap", "");
						Log.i("GestureHelper", "Message Sent: Tap");
					} catch (UnsatisfiedLinkError er) {
						Log.i("GestureHelper",
								"Failed to send unity message, probably because Unity native libraries aren't available (e.g. you are not running this from Unity");
						Log.i("GestureHelper", er.getMessage());
					}
					return true;
				} else if(gesture == Gesture.SWIPE_DOWN) {
					Log.e("GestureHelper", "Down swipe");
					return true;
				} else if(gesture == Gesture.SWIPE_UP) {
					Log.e("GestureHelper", "Up swipe");
					return true;
				}
				return false;
			}
		});
		gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
            @Override
            public void onFingerCountChanged(int previousCount, int currentCount) {
              // do something on finger count changes
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
	
	@Override
    public boolean onGenericMotionEvent(MotionEvent event) {
		Log.e("GestureHelper", "Gesture processing");
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		Log.e("GestureHelper", "On touch called");
		super.onTouchEvent(event);
		return true;
	}

	@Override
    protected void onResume() {
        super.onResume();
    }
	
	@Override
    protected void onPause() {
        super.onPause();
 
	}
	
	@Override
	protected void onDestroy() {
		Log.e("GestureHelper", "On destroy has been called");
		super.onDestroy();
	}
	
	@Override
	protected void onStop() {
		Log.e("GestureHelper", "On stop called");
		super.onStop();
	}

	
}
