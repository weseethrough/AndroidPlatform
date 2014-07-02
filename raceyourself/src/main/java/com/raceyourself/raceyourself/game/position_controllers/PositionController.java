package com.raceyourself.raceyourself.game.position_controllers;

import com.raceyourself.platform.models.Position;
import com.raceyourself.raceyourself.game.GameStrategy;
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

    public abstract void start();
    public abstract void stop();
    public abstract void reset();

    public abstract double getRealDistance();
    public abstract long getElapsedTime();
    public abstract float getCurrentSpeed();
    public abstract float getAverageSpeed();

    public float getOnScreenDistance() {
        return placementStrategy.get1DPlacement(getRealDistance());
    }

    public float getProgressTowardsGoal(GameStrategy gs) {
        switch (gs.getGameType()) {
            case TIME_CHALLENGE: return (float) (gs.getTargetTime() == 0 ? 0 : (double)getElapsedTime() / gs.getTargetTime());
            case DISTANCE_CHALLENGE: return (float) (gs.getTargetDistance() == 0 ? 0 : (double)getRealDistance() / gs.getTargetDistance());
            default: return 0.0f;
        }
    }

    public double getRemainingDistance(GameStrategy gs) {
        if (gs.getGameType() == GameStrategy.GameType.DISTANCE_CHALLENGE) {
            return gs.getTargetDistance() - getRealDistance();
        } else {
            throw new RuntimeException("Remaining distance is not a valid concept unless GameType is DISTANCE_CHALLENGE");
        }
    }

    public long getRemainingTime(GameStrategy gs) {
        if (gs.getGameType() == GameStrategy.GameType.TIME_CHALLENGE) {
            return gs.getTargetTime() - getElapsedTime();
        } else {
            throw new RuntimeException("Remaining time is not a valid concept unless GameType is TIME_CHALLENGE");
        }
    }

}