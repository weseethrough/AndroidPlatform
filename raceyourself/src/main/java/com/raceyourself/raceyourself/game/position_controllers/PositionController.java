package com.raceyourself.raceyourself.game.position_controllers;

import com.raceyourself.raceyourself.game.GameConfiguration;
import com.raceyourself.raceyourself.game.placement_strategies.PlacementStrategy;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by benlister on 26/06/2014.
 */
public abstract class PositionController {

    @Getter
    @Setter
    private PlacementStrategy placementStrategy;

    @Getter
    @Setter
    private boolean localPlayer = false;

    public abstract void start();  // start updating position
    public abstract void stop();  // stop updating position
    public abstract void reset();  // set back to initial state
    public abstract void close();  // clean up, stop requesting GPS, should be ready for GC after this runs

    public abstract double getRealDistance();
    public abstract long getElapsedTime();
    public abstract float getCurrentSpeed();
    public abstract float getAverageSpeed();

    public float getProgressTowardsGoal(GameConfiguration gs) {
        switch (gs.getGameType()) {
            case TIME_CHALLENGE: return (float) (gs.getTargetTime() == 0 ? 0 : (double)getElapsedTime() / gs.getTargetTime());
            case DISTANCE_CHALLENGE: return (float) (gs.getTargetDistance() == 0 ? 0 : (double)getRealDistance() / gs.getTargetDistance());
            default: return 0.0f;
        }
    }

    public double getRemainingDistance(GameConfiguration gs) {
        if (gs.getGameType() == GameConfiguration.GameType.DISTANCE_CHALLENGE) {
            return gs.getTargetDistance() - getRealDistance();
        } else {
            throw new RuntimeException("Remaining distance is not a valid concept unless GameType is DISTANCE_CHALLENGE");
        }
    }

    public long getRemainingTime(GameConfiguration gs) {
        if (gs.getGameType() == GameConfiguration.GameType.TIME_CHALLENGE) {
            return gs.getTargetTime() - getElapsedTime();
        } else {
            throw new RuntimeException("Remaining time is not a valid concept unless GameType is TIME_CHALLENGE");
        }
    }

    // look forward to predict distance covered at a time in the future
    // TODO: update to support times in the past?
    public abstract double getExpectedDistanceAtTime(long elapsedMillis);

    public boolean isFinished(GameConfiguration gs) {
        switch (gs.getGameType()) {
            case TIME_CHALLENGE: return (getElapsedTime() >= gs.getTargetTime()) ? true : false;
            case DISTANCE_CHALLENGE: return (getRealDistance() >= gs.getTargetDistance()) ? true : false;
            default: return false;
        }
    }

}
