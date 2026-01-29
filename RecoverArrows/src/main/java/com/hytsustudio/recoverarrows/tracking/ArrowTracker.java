package com.hytsustudio.recoverarrows.tracking;

import com.hytale.server.entity.Ref;
import java.util.Objects;
import org.joml.Vector3d;

/**
 * Pure logic for tracking arrow movement and pickup readiness.
 * This class intentionally has no ECS access.
 */
public final class ArrowTracker {
    private static final double STATIONARY_DISTANCE_SQUARED = 0.0001d;

    private final Ref<?> arrowRef;
    private final long spawnTick;
    private Vector3d lastPosition;
    private int stationaryTicks;
    private long lastUpdateTick = Long.MIN_VALUE;

    public ArrowTracker(Ref<?> arrowRef, long spawnTick, Vector3d initialPosition) {
        this.arrowRef = Objects.requireNonNull(arrowRef, "arrowRef");
        this.spawnTick = spawnTick;
        this.lastPosition = new Vector3d(Objects.requireNonNull(initialPosition, "initialPosition"));
    }

    public Ref<?> getArrowRef() {
        return arrowRef;
    }

    public long getSpawnTick() {
        return spawnTick;
    }

    public Vector3d getLastPosition() {
        return lastPosition;
    }

    public int getStationaryTicks() {
        return stationaryTicks;
    }

    /**
     * Updates the tracker with the arrow's current position.
     */
    public void update(Vector3d currentPosition, long currentTick) {
        Objects.requireNonNull(currentPosition, "currentPosition");

        if (currentTick == lastUpdateTick) {
            return;
        }
        lastUpdateTick = currentTick;

        double distanceSquared = distanceSquared(lastPosition, currentPosition);
        if (distanceSquared <= STATIONARY_DISTANCE_SQUARED) {
            stationaryTicks++;
        } else {
            stationaryTicks = 0;
        }

        lastPosition.set(currentPosition);
    }

    public boolean isStopped(int requiredStationaryTicks) {
        return stationaryTicks >= requiredStationaryTicks;
    }

    public boolean isPickupReady(long currentTick, int requiredStationaryTicks, long pickupDelayTicks) {
        return isStopped(requiredStationaryTicks)
                && currentTick - spawnTick >= pickupDelayTicks;
    }

    private static double distanceSquared(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
