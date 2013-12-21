package com.glassfitgames.glassfitplatform.models;

import com.javadocmd.simplelatlng.LatLng;
import com.javadocmd.simplelatlng.LatLngTool;

// Utilities for bearing calculation
public class Bearing {

	public static float calcBearing(Position from, Position to) {
        LatLng fromL = new LatLng(from.getLatx(), from.getLngx());
        LatLng toL = new LatLng(to.getLatx(), to.getLngx());        
        return (float)LatLngTool.initialBearing(fromL, toL);
    }

    public static float calcBearingInRadians(Position from, Position to) {
        double lat1R = Math.toRadians(from.getLatx());
        double lat2R = Math.toRadians(to.getLatx());
        double dLngR = Math.toRadians(to.getLngx() - from.getLngx());
        double a = Math.sin(dLngR) * Math.cos(lat2R);
        double b = Math.cos(lat1R) * Math.sin(lat2R) - Math.sin(lat1R) * Math.cos(lat2R)
                                * Math.cos(dLngR);
        return (float)Math.atan2(a, b);
     }
    
    public static float normalizeBearing(float bearing) {
    	return (float) LatLngTool.normalizeBearing(bearing);
    }
    
    // Calculate minimal angle difference (in degrees) between two 
    public static float bearingDiffDegrees(float bearing1, float bearing2) {
    	float diff = bearing1 - bearing2;
    	diff  += (diff>180) ? -360 : (diff<-180) ? 360 : 0;
    	return diff;
    }
}
