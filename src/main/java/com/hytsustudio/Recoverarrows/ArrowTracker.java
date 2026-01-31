package com.hytsustudio.Recoverarrows;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks projectile arrows and decides when they are "embedded" (stopped)
 * and when they become pickup-ready.
 *
 * This class does NOT touch inventory, sounds, or entity removal.
 */
public final class ArrowTracker {

    // If an arrow position barely changes for a few ticks, treat as "stopped"
    private static final double POSITION_EPSILON = 0.001;
    private static final double POSITION_EPSILON_SQUARED = POSITION_EPSILON * POSITION_EPSILON;

    public static final class TrackedArrow {
        public final Ref<?> arrowRef;

        public Vector3d lastPos;
        public int stationaryTicks;
        public long stoppedTick;

        public TrackedArrow(Ref<?> arrowRef, Vector3d initialPos) {
            this.arrowRef = arrowRef;
            this.lastPos = copy(initialPos);
            this.stationaryTicks = 0;
            this.stoppedTick = -1;
        }
    }

    private final Map<Ref<?>, TrackedArrow> tracked = new HashMap<>();

    private final int requiredStationaryTicks;
    private final int pickupDelayTicks;

    public ArrowTracker(int requiredStationaryTicks, int pickupDelayTicks) {
        this.requiredStationaryTicks = requiredStationaryTicks;
        this.pickupDelayTicks = pickupDelayTicks;
    }

    public void trackIfMissing(Ref<?> arrowRef, Vector3d currentPos) {
        tracked.computeIfAbsent(arrowRef, r -> new TrackedArrow(r, currentPos));
    }

    public void untrack(Ref<?> arrowRef) {
        tracked.remove(arrowRef);
    }

    public TrackedArrow get(Ref<?> arrowRef) {
        return tracked.get(arrowRef);
    }

    public boolean isStopped(TrackedArrow a) {
        return a != null && a.stationaryTicks >= requiredStationaryTicks;
    }

    public boolean isPickupReady(TrackedArrow a, long tick) {
        if (a == null) return false;
        if (!isStopped(a)) return false;
        if (a.stoppedTick < 0) return false;
        return (tick - a.stoppedTick) >= pickupDelayTicks;
    }

    public void update(TrackedArrow a, Vector3d currentPos, long tick) {
        if (a == null) return;

        if (currentPos == null || a.lastPos == null) {
            a.lastPos = copy(currentPos);
            a.stationaryTicks = 0;
            a.stoppedTick = -1;
            return;
        }

        double d2 = dist2(currentPos, a.lastPos);

        if (d2 <= POSITION_EPSILON_SQUARED) {
            a.stationaryTicks++;
            if (a.stationaryTicks >= requiredStationaryTicks && a.stoppedTick < 0) {
                a.stoppedTick = tick;
            }
        } else {
            a.stationaryTicks = 0;
            a.stoppedTick = -1;
        }

        a.lastPos = copy(currentPos);
    }

    private static double dist2(Vector3d a, Vector3d b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vector3d copy(Vector3d p) {
        if (p == null) return null;
        return new Vector3d(p.getX(), p.getY(), p.getZ());
    }
}
