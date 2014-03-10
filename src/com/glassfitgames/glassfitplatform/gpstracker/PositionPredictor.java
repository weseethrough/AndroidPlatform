package com.glassfitgames.glassfitplatform.gpstracker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import android.os.Environment;
import au.com.bytecode.opencsv.CSVWriter;

import com.glassfitgames.glassfitplatform.gpstracker.kml.GFKml;
import com.glassfitgames.glassfitplatform.models.Bearing;
import com.glassfitgames.glassfitplatform.models.EnhancedPosition;
import com.glassfitgames.glassfitplatform.models.Position;

import com.javadocmd.simplelatlng.*;
import com.javadocmd.simplelatlng.util.*;

// The class keeps track of few last (predicted) position and may predict the future position and its bearing :)
// updatePosition() method should be used to update predictor state with the last GPS position.
// After that, predictPosition() and/or predictBearing() can be used to get up-to-date prediction
public class PositionPredictor {
	private boolean LOG_KML = false;
	private GFKml kml = new GFKml();
    // Constant used to optimize calculations
    private double INV_DELTA_TIME_MS = CardinalSpline.getNumberPoints() / 1000.0; // delta time between predictions
    // Positions below this speed threshold will be discarded in bearing computation
    private float SPEED_THRESHOLD_MS = 1.25f;
    // Maximal number of predicted positions used for spline interpolation
    private int MAX_PREDICTED_POSITIONS = 3;
    private int MAX_EXT_PREDICTED_POSITIONS = 5;
    
    // Last predicted positions - used for spline
    private ArrayDeque<EnhancedPosition> recentPredictedPositions = new ArrayDeque<EnhancedPosition>();

    private ArrayDeque<EnhancedPosition> recentGpsPositions = new ArrayDeque<EnhancedPosition>();
    
    private BearingCalculator bearingCalculator = new BearingCalculator(recentGpsPositions);
    // Interpolated positions between recent predicted position
    private Position[] interpPath = new Position[MAX_PREDICTED_POSITIONS * CardinalSpline.getNumberPoints()];
    // Last GPS position
    private Position lastGpsPosition = new Position();
    private Position lastPredictedPos = new Position();
    // Accumulated GPS distance
    private double gpsTraveledDistance = 0;
    // Accumulated predicted distance
    private double predictedTraveledDistance = 0;
    
    private int numStaticPos = 0;
    
    private int MAX_NUM_STATIC_POS = 2;
        
    public PositionPredictor() {
    }
    
    public BearingCalculator getBearingCalculator() {
    	return bearingCalculator; 
    }
    // Update prediction with new GPS position. 
    // Input: recent GPS positon, output: correspondent predicted position 
    public Position updatePosition(EnhancedPosition aLastGpsPos) {
    	//System.out.printf("\n------ %d ------\n", ++i);
        if (aLastGpsPos == null || aLastGpsPos.getBearing() == null) {
            return null;
        }
        if(LOG_KML) kml.addPosition(GFKml.PathType.GPS, aLastGpsPos);
        // Need at least 3 positions
        if (recentPredictedPositions.size() < 2) {
            recentPredictedPositions.addLast(aLastGpsPos);
            recentGpsPositions.addLast(aLastGpsPos);
            return aLastGpsPos;
        } else if (recentPredictedPositions.size() == 2) {
            recentPredictedPositions.addLast(extrapolatePosition(recentPredictedPositions.getLast(), 1));
        }
        // Update traveled distance
        updateDistance(aLastGpsPos);
        
        bearingCalculator.updatePosition(recentPredictedPositions.getLast());
        
        // correct last (predicted) position with last GPS position
        correctLastPredictedPosition(aLastGpsPos);
        
        // predict next user position (in 1 sec) based on current speed and bearing
        EnhancedPosition nextPos = extrapolatePosition(recentPredictedPositions.getLast(), 1);
        if(LOG_KML) kml.addPosition(GFKml.PathType.EXTRAPOLATED, nextPos);

        // Update number static positions
        numStaticPos = (aLastGpsPos.getSpeed() < SPEED_THRESHOLD_MS) ? numStaticPos+1 : 0;
        // Throw away static positions and flush predicted path/traveled distance
        if (nextPos == null || numStaticPos > MAX_NUM_STATIC_POS 
        		|| aLastGpsPos.getSpeed() == 0.0) { // standing still
        	recentPredictedPositions.clear();
        	recentGpsPositions.clear();
        	predictedTraveledDistance = gpsTraveledDistance;
        	numStaticPos = 0;
            return null;
        }
        recentGpsPositions.add(aLastGpsPos);
        if (recentGpsPositions.size() > MAX_EXT_PREDICTED_POSITIONS) {
        	recentGpsPositions.removeFirst();
        }
        
        
        // Add predicted position for the next round
        recentPredictedPositions.addLast(nextPos);
        // Keep queue within maximal size limit
        Position firstToRemove = null;
        if (recentPredictedPositions.size() > MAX_PREDICTED_POSITIONS) {
        	firstToRemove = recentPredictedPositions.removeFirst(); 
        }
        // Fill input for interpolation
        Position[] points = new Position[recentPredictedPositions.size()];
        
        int i = 0;
        for (Position p : recentPredictedPositions) {
            points[i++] = p;
            System.out.printf("CTL POINTS: points[%d], %.15f,,%.15f, bearing: %f\n",	i, p.getLngx(), p.getLatx(), p.getBearing());
        }
        // interpolate points using spline
        interpPath = interpolatePositions(points);
        
        lastGpsPosition = aLastGpsPos;
        return recentPredictedPositions.getLast();
    }
    
    // Returns predicted position at a given timestamp
    public Position predictPosition(long aDeviceTimestampMilliseconds) {
        if (interpPath == null || recentPredictedPositions.size() < 3)
        {
            return null;
        }
        // Find closest point (according to device timestamp) in interpolated path
        long firstPredictedPositionTs = recentPredictedPositions.getFirst().getDeviceTimestamp();
        int index = (int) ((aDeviceTimestampMilliseconds - firstPredictedPositionTs) * INV_DELTA_TIME_MS);
        // Predicting only within current path
        //System.out.printf("BearingAlgo::predictPosition: ts: %d, index: %d, path length: %d\n", aDeviceTimestampMilliseconds
        //                   ,index, interpPath.length);   
        if (index < 0 || index >= interpPath.length) {
            return null;
        }
        if(LOG_KML) kml.addPosition(GFKml.PathType.PREDICTION, interpPath[index]);

        return interpPath[index];
    }

    public void stopTracking() {
    	// Dump KML
        if(LOG_KML) {
            String fileName = Environment.getExternalStorageDirectory().getPath()+"/Downloads/track_" + 
            		RandomStringUtils.randomAlphanumeric(16) + ".kml";
            System.out.println("Dumping KML: " + fileName); 
            FileOutputStream out;
			try {
				out = new FileOutputStream(fileName);
	            kml.write(out);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        bearingCalculator.reset();
    }
    
    // Returns bearing of the predicted position at given time
    public Float predictBearing(long aDeviceTimestampMilliseconds) {
        Position pos = predictPosition(aDeviceTimestampMilliseconds);
        if (pos == null)
        {
            return null;
        }
        return pos.getBearing();
    }
    
    // Extrapolate (predict) position based on last positions given time ahead
    private EnhancedPosition extrapolatePosition(Position aLastPos, long timeSec) {
    	// Simple method - calculate based on speed and bearing of last position
    	return new EnhancedPosition(Position.predictPosition(aLastPos, timeSec*1000));
    }
    
    private Position[] interpolatePositions(Position[] ctrlPoints) {
    	if (!constrainControlPoints(ctrlPoints)) {
            // TODO: avoid conversion to array
    		return CardinalSpline.create(ctrlPoints).toArray(interpPath);
    	} else {
    		return ConstrainedCubicSpline.create(ctrlPoints);
    	}
    }
    
    private boolean constrainControlPoints(Position[] pts) {
    	float prevDistance = (float) Position.distanceBetween(pts[0], pts[1]);;
    	if (prevDistance == 0) {
    		return false;
    	}
    	for (int i = 1; i < pts.length; ++i) {
    		float distance = (float) Position.distanceBetween(pts[i], pts[i-1]);
    		float ratio = distance/prevDistance;
    		System.out.printf("constrainControlPoints i = %d, ratio: %f\n", i, ratio);
    		if (ratio >= 8.0 || ratio <= 0.125) {
    			return true;
    		}
    		prevDistance = distance;
    	}
    	return false;
    }

    
    // Update calculations for predicted and real traveled distances
    // TODO: unify distance calculations with GpsTracker distance calculations
    private void updateDistance(Position aLastPos) {
        Iterator<EnhancedPosition> reverseIterator = recentPredictedPositions.descendingIterator();
        reverseIterator.next();
        Position prevPredictedPos = reverseIterator.next();        
        double distancePredicted = Position.distanceBetween(prevPredictedPos, recentPredictedPositions.getLast());
        predictedTraveledDistance += distancePredicted;	
        
        double distanceReal = Position.distanceBetween(lastGpsPosition, aLastPos);
        gpsTraveledDistance += distanceReal;
    }
    
    private void correctLastPredictedPosition(Position aLastGpsPos) {
        // correct last (predicted) position with last GPS position
        lastPredictedPos = recentPredictedPositions.getLast();
        lastPredictedPos.setBearing(bearingCalculator.calcBearing(aLastGpsPos));
        lastPredictedPos.setDeviceTimestamp(aLastGpsPos.getDeviceTimestamp());
        lastPredictedPos.setGpsTimestamp(aLastGpsPos.getGpsTimestamp());        
        lastPredictedPos.setSpeed(calcCorrectedSpeed(aLastGpsPos));
    }
    

    private float calcCorrectedSpeed(Position aLastPos) {
    	// Do not correct position below threshold
    	if (aLastPos.getSpeed() < SPEED_THRESHOLD_MS) {
    		return aLastPos.getSpeed();
    	}
    	double offset = (gpsTraveledDistance - predictedTraveledDistance);
    	/*System.out.printf("GPS DIST: %f, EST DIST: %f, OFFSET: %f\n" , 
    			gpsTraveledDistance,predictedTraveledDistance, offset);
		*/
        double coeff = (offset > 0 ) ? 0.3 : -0.3;        
        coeff = Math.abs(offset) <= aLastPos.getSpeed() ? offset/aLastPos.getSpeed() : coeff;

        double correctedSpeed = aLastPos.getSpeed()*(1 + coeff);
        
        // System.out.printf("SPEED: %f, CORRECTED SPEED: %f, DISTANCE COEFF: %f\n",aLastPos.getSpeed(), correctedSpeed, coeff);
        return (float) correctedSpeed;
    	
    }
   
 }
