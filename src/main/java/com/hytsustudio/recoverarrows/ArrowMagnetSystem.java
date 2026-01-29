package com.hytsustudio.recoverarrows;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Arrow pickup system that keeps arrows in-world until they settle and a nearby player can collect them.
 */
public class ArrowMagnetSystem extends EntityTickingSystem<EntityStore> {

    private static final Logger LOGGER = Logger.getLogger(ArrowMagnetSystem.class.getName());

    private static final double PICKUP_RADIUS = 2.5;
    private static final double PICKUP_RADIUS_SQUARED = PICKUP_RADIUS * PICKUP_RADIUS;
    private static final int PICKUP_DELAY_TICKS = 20;
    private static final int REQUIRED_STATIONARY_TICKS = 10;
    private static final boolean OWNER_ONLY_PICKUP = true;
    private static final int SCAN_INTERVAL_TICKS = 5;

    private final Map<Ref<EntityStore>, ArrowTracker> trackers = new HashMap<>();
    private long lastCleanupTick = Long.MIN_VALUE;

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                ProjectileComponent.getComponentType(),
                TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> cmd
    ) {
        ProjectileComponent projectile =
                chunk.getComponent(index, ProjectileComponent.getComponentType());
        if (projectile == null) {
            return;
        }

        String assetName = projectile.getProjectileAssetName();
        if (assetName == null || !assetName.contains("Arrow")) {
            return;
        }

        TransformComponent transform =
                chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d position = transform.getPosition();
        if (position == null) {
            return;
        }
        Ref<EntityStore> arrowRef = chunk.getReferenceTo(index);
        if (!store.isEntityAlive(arrowRef)) {
            trackers.remove(arrowRef);
            return;
        }
        long currentTick = store.getTick();

        ArrowTracker tracker = trackers.get(arrowRef);
        if (tracker == null) {
            Ref<EntityStore> ownerRef = resolveShooterRef(projectile);
            tracker = new ArrowTracker(
                    arrowRef,
                    currentTick,
                    position,
                    REQUIRED_STATIONARY_TICKS,
                    PICKUP_DELAY_TICKS,
                    ownerRef
            );
            trackers.put(arrowRef, tracker);
        } else {
            tracker.update(position, currentTick);
        }

        cleanupTrackersIfNeeded(store, currentTick);

        boolean shouldScan = shouldScan(currentTick);
        if (!tracker.isPickupReady(currentTick) || !shouldScan) {
            return;
        }

        SpatialResource<Ref<EntityStore>, EntityStore> players =
                cmd.getResource(EntityModule.get().getPlayerSpatialResourceType());
        ObjectList<Ref<EntityStore>> nearby =
                SpatialResource.getThreadLocalReferenceList();
        nearby.clear();

        players.getSpatialStructure().collect(position, PICKUP_RADIUS, nearby);

        if (nearby.isEmpty()) {
            return;
        }

        for (Ref<EntityStore> playerRef : nearby) {
            if (playerRef == null) {
                continue;
            }

            if (!store.isEntityAlive(playerRef)) {
                continue;
            }

            TransformComponent playerTransform =
                    store.getComponent(playerRef, TransformComponent.getComponentType());
            if (playerTransform == null) {
                continue;
            }

            Vector3d playerPosition = playerTransform.getPosition();
            if (distanceSquared(position, playerPosition) > PICKUP_RADIUS_SQUARED) {
                continue;
            }

            if (!isPickupAllowed(playerRef, tracker)) {
                continue;
            }

            ItemStack stack = new ItemStack(assetName, 1);
            boolean inserted = ItemUtils.interactivelyPickupItem(
                    playerRef,
                    stack,
                    position,
                    cmd
            );

            if (inserted) {
                cmd.tryRemoveEntity(arrowRef, RemoveReason.REMOVE);
                trackers.remove(arrowRef);
            }
            return;
        }
    }

    private static boolean shouldScan(long tick) {
        return tick % SCAN_INTERVAL_TICKS == 0;
    }

    private void cleanupTrackersIfNeeded(Store<EntityStore> store, long tick) {
        if (lastCleanupTick == tick) {
            return;
        }
        lastCleanupTick = tick;

        if (trackers.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Ref<EntityStore>, ArrowTracker>> iterator = trackers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Ref<EntityStore>, ArrowTracker> entry = iterator.next();
            Ref<EntityStore> ref = entry.getKey();
            if (!store.isEntityAlive(ref)) {
                iterator.remove();
            }
        }
    }

    private static boolean isPickupAllowed(Ref<EntityStore> playerRef, ArrowTracker tracker) {
        if (!OWNER_ONLY_PICKUP) {
            return true;
        }
        Ref<EntityStore> ownerRef = tracker.getOwnerRef();
        if (ownerRef == null) {
            return true;
        }
        return ownerRef.equals(playerRef);
    }

    private static Ref<EntityStore> resolveShooterRef(ProjectileComponent projectile) {
        Ref<EntityStore> ownerRef = tryResolveRef(projectile, "getOwner");
        if (ownerRef != null) {
            return ownerRef;
        }
        ownerRef = tryResolveRef(projectile, "getShooter");
        if (ownerRef != null) {
            return ownerRef;
        }
        ownerRef = tryResolveRef(projectile, "getOwnerRef");
        if (ownerRef != null) {
            return ownerRef;
        }
        return tryResolveRef(projectile, "getShooterRef");
    }

    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> tryResolveRef(ProjectileComponent projectile, String methodName) {
        try {
            Object value = projectile.getClass().getMethod(methodName).invoke(projectile);
            if (value instanceof Ref) {
                return (Ref<EntityStore>) value;
            }
            return null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException ex) {
            LOGGER.log(Level.WARNING, "ArrowMagnetSystem: failed to resolve shooter reference", ex);
            return null;
        }
    }

    private static double distanceSquared(Vector3d first, Vector3d second) {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
