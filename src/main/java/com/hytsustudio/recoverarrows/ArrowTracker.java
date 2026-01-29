package com.hytsustudio.recoverarrows;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Pure logic tracker for arrow movement and pickup readiness.
 */
public final class ArrowTracker {

    private static final double POSITION_EPSILON = 0.001;
    private static final double POSITION_EPSILON_SQUARED = POSITION_EPSILON * POSITION_EPSILON;

    private final Ref<EntityStore> arrowRef;
    private final long spawnTick;
    private final int requiredStationaryTicks;
    private final int pickupDelayTicks;

    private Vector3d lastPosition;
    private int stationaryTicks;
    private long stoppedTick;

    public ArrowTracker(
            Ref<EntityStore> arrowRef,
            long spawnTick,
            Vector3d initialPosition,
            int requiredStationaryTicks,
            int pickupDelayTicks
    ) {
        this.arrowRef = arrowRef;
        this.spawnTick = spawnTick;
        this.requiredStationaryTicks = requiredStationaryTicks;
        this.pickupDelayTicks = pickupDelayTicks;
        this.lastPosition = copyPosition(initialPosition);
        this.stationaryTicks = 0;
        this.stoppedTick = -1;
    }

    public Ref<EntityStore> getArrowRef() {
        return arrowRef;
    }

    public long getSpawnTick() {
        return spawnTick;
    }

    public void update(Vector3d position, long tick) {
        if (position == null || lastPosition == null) {
            lastPosition = copyPosition(position);
            stationaryTicks = 0;
            stoppedTick = -1;
            return;
        }

        double distanceSquared = distanceSquared(position, lastPosition);
        if (distanceSquared <= POSITION_EPSILON_SQUARED) {
            stationaryTicks++;
            if (stationaryTicks >= requiredStationaryTicks && stoppedTick < 0) {
                stoppedTick = tick;
            }
        } else {
            stationaryTicks = 0;
            stoppedTick = -1;
        }

        lastPosition = copyPosition(position);
    }

    public boolean isStopped() {
        return stationaryTicks >= requiredStationaryTicks;
    }

    public boolean isPickupReady(long tick) {
        if (!isStopped() || stoppedTick < 0) {
            return false;
        }
        return (tick - stoppedTick) >= pickupDelayTicks;
    }

    private static double distanceSquared(Vector3d first, Vector3d second) {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private static Vector3d copyPosition(Vector3d position) {
        if (position == null) {
            return null;
        }
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }
}
