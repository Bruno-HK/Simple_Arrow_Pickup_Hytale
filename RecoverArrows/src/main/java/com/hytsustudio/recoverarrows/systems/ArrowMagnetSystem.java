package com.hytsustudio.recoverarrows.systems;

import com.hytsustudio.recoverarrows.tracking.ArrowTracker;
import com.hytale.server.entity.Ref;
import com.hytale.server.entity.components.ProjectileComponent;
import com.hytale.server.entity.components.TransformComponent;
import com.hytale.server.entity.components.VelocityComponent;
import com.hytale.server.entity.components.inventory.InventoryComponent;
import com.hytale.server.entity.components.player.PlayerComponent;
import com.hytale.server.entity.systems.CommandBuffer;
import com.hytale.server.entity.systems.SystemContext;
import com.hytale.server.entity.systems.TickingSystem;
import com.hytale.server.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Vector3d;

/**
 * ECS system that tracks grounded arrows and lets nearby players recover them.
 */
public final class ArrowMagnetSystem implements TickingSystem {
    private static final Logger LOGGER = Logger.getLogger(ArrowMagnetSystem.class.getName());

    private static final double PICKUP_RADIUS = 3.0d;
    private static final double PICKUP_RADIUS_SQUARED = PICKUP_RADIUS * PICKUP_RADIUS;
    private static final long PICKUP_DELAY_TICKS = 20L;
    private static final int REQUIRED_STATIONARY_TICKS = 10;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final double STATIONARY_VELOCITY_SQUARED = 0.0001d;
    private static final String ARROW_PROJECTILE_ID = "hytale:arrow";
    private static final String ARROW_ITEM_ID = "hytale:arrow";

    private final Map<Ref<?>, ArrowTracker> trackedArrows = new HashMap<>();
    private long lastScanTick = Long.MIN_VALUE;

    @Override
    public void tick(SystemContext context) {
        try {
            long currentTick = context.getTick();
            CommandBuffer commandBuffer = context.getCommandBuffer();

            updateTrackedArrows(context, currentTick);

            if (currentTick - lastScanTick < SCAN_INTERVAL_TICKS) {
                return;
            }
            lastScanTick = currentTick;

            scanForNewArrows(context, currentTick);

            List<Vector3d> playerPositions = new ArrayList<>();
            List<InventoryComponent> playerInventories = new ArrayList<>();
            collectPlayers(context, playerPositions, playerInventories);

            if (playerPositions.isEmpty()) {
                return;
            }

            attemptPickups(currentTick, playerPositions, playerInventories, commandBuffer);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Unexpected error in ArrowMagnetSystem tick.", ex);
            throw ex;
        }
    }

    private void updateTrackedArrows(SystemContext context, long currentTick) {
        Iterator<Map.Entry<Ref<?>, ArrowTracker>> iterator = trackedArrows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Ref<?>, ArrowTracker> entry = iterator.next();
            Ref<?> arrowRef = entry.getKey();

            if (!context.getWorld().isAlive(arrowRef)) {
                iterator.remove();
                continue;
            }

            TransformComponent transform = context.getWorld().getComponent(arrowRef, TransformComponent.class);
            if (transform == null) {
                iterator.remove();
                continue;
            }

            Vector3d position = new Vector3d(transform.getPosition());
            entry.getValue().update(position, currentTick);
        }
    }

    private void scanForNewArrows(SystemContext context, long currentTick) {
        context.getWorld()
                .getEntityManager()
                .query(ProjectileComponent.class, TransformComponent.class, VelocityComponent.class)
                .forEach((ref, projectile, transform, velocity) -> {
                    if (trackedArrows.containsKey(ref)) {
                        return;
                    }

                    if (!ARROW_PROJECTILE_ID.equals(projectile.getProjectileId())) {
                        return;
                    }

                    Vector3d velocityVector = velocity.getVelocity();
                    if (lengthSquared(velocityVector) > STATIONARY_VELOCITY_SQUARED) {
                        return;
                    }

                    Vector3d position = new Vector3d(transform.getPosition());
                    trackedArrows.put(ref, new ArrowTracker(ref, currentTick, position));
                });
    }

    private void collectPlayers(
            SystemContext context,
            List<Vector3d> playerPositions,
            List<InventoryComponent> playerInventories
    ) {
        context.getWorld()
                .getEntityManager()
                .query(PlayerComponent.class, TransformComponent.class, InventoryComponent.class)
                .forEach((ref, player, transform, inventory) -> {
                    playerPositions.add(new Vector3d(transform.getPosition()));
                    playerInventories.add(inventory);
                });
    }

    private void attemptPickups(
            long currentTick,
            List<Vector3d> playerPositions,
            List<InventoryComponent> playerInventories,
            CommandBuffer commandBuffer
    ) {
        Iterator<Map.Entry<Ref<?>, ArrowTracker>> iterator = trackedArrows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Ref<?>, ArrowTracker> entry = iterator.next();
            ArrowTracker tracker = entry.getValue();

            if (!tracker.isPickupReady(currentTick, REQUIRED_STATIONARY_TICKS, PICKUP_DELAY_TICKS)) {
                continue;
            }

            Vector3d arrowPosition = tracker.getLastPosition();
            if (arrowPosition == null) {
                continue;
            }

            boolean pickedUp = false;
            for (int i = 0; i < playerPositions.size(); i++) {
                Vector3d playerPosition = playerPositions.get(i);
                double distanceSquared = distanceSquared(arrowPosition, playerPosition);
                if (distanceSquared > PICKUP_RADIUS_SQUARED) {
                    continue;
                }

                InventoryComponent inventory = playerInventories.get(i);
                if (tryGiveArrow(inventory)) {
                    commandBuffer.removeEntity(entry.getKey());
                    iterator.remove();
                    pickedUp = true;
                    break;
                }
            }

            if (pickedUp) {
                continue;
            }
        }
    }

    private boolean tryGiveArrow(InventoryComponent inventory) {
        ItemStack arrowStack = ItemStack.of(ARROW_ITEM_ID, 1);
        return inventory.tryAddItem(arrowStack);
    }

    private static double distanceSquared(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double lengthSquared(Vector3d vector) {
        return vector.x * vector.x + vector.y * vector.y + vector.z * vector.z;
    }
}
