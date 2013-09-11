package com.glassfitgames.glassfitplatform.gpstracker;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import com.glassfitgames.glassfitplatform.R;

public class GPSActivity extends Activity {
	GPSTracker gps;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_authentication);
		gps = new GPSTracker(GPSActivity.this);

		// check if GPS enabled
		if (gps.canGetLocation()) {

			double latitude = gps.getLatitude();
			double longitude = gps.getLongitude();

			// \n is for new line
			Toast.makeText(
					getApplicationContext(),
					"Your Location is - \nLat: " + latitude + "\nLong: "
							+ longitude, Toast.LENGTH_LONG).show();
		} else {
			// can't get location
			// GPS or Network is not enabled
			// Ask user to enable GPS/network in settings
			gps.showSettingsAlert();
		}
	}
}
