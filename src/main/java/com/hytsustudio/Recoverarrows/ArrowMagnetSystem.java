package com.hytsustudio.Recoverarrows;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class ArrowMagnetSystem extends EntityTickingSystem<EntityStore> {

    private static final String ALLOWED_ARROW_ITEM = "Weapon_Arrow_Crude";
    private static final double VELOCITY_EPSILON = 0.001;
    private static final int CLEANUP_INTERVAL_TICKS = 200; // ~10 seconds at 20 TPS

    private static final HytaleLogger LOG = HytaleLogger.get("ArrowPickup");

    private final Map<Ref<EntityStore>, Vector3d> lastPositions = new HashMap<>();
    private int tickCounter = 0;

    @Override
    public Query<EntityStore> getQuery() {
        return Projectile.getComponentType();
    }

    @Override
    public void tick(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commands
    ) {
        // Periodic cleanup of stale references
        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;
            cleanupStaleReferences(store);
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        TransformComponent tx =
                store.getComponent(ref, TransformComponent.getComponentType());
        Velocity vel =
                store.getComponent(ref, Velocity.getComponentType());
        ModelComponent model =
                store.getComponent(ref, ModelComponent.getComponentType());

        if (tx == null || vel == null || model == null || model.getModel() == null) {
            lastPositions.remove(ref);
            return;
        }

        Vector3d pos = tx.getPosition();
        Vector3d last = lastPositions.get(ref);

        // Only trigger once the arrow has stopped moving (with epsilon for float comparison)
        if (last == null || !last.equals(pos) || vel.getVelocity().length() > VELOCITY_EPSILON) {
            lastPositions.put(ref, pos.clone());
            return;
        }

        String modelAssetId = model.getModel().getModelAssetId();

        int idx = modelAssetId.lastIndexOf("Arrow_");
        if (idx == -1) {
            lastPositions.remove(ref);
            return;
        }

        String arrowSuffix = modelAssetId.substring(idx);
        String itemId = "Weapon_" + arrowSuffix;

        // HARD FILTER:
        // Only allow crude arrows for now
        if (!ALLOWED_ARROW_ITEM.equals(itemId)) {
            LOG.at(Level.FINE).log(
                    "Skipping non-supported arrow type: %s", itemId
            );
            lastPositions.remove(ref);
            return;
        }

        ItemStack arrow = new ItemStack(ALLOWED_ARROW_ITEM, 1);

        Holder<EntityStore> holder =
                ItemComponent.generateItemDrop(
                        commands,
                        arrow,
                        pos,
                        Vector3f.ZERO,
                        0f, 0f, 0f
                );

        if (holder == null) {
            LOG.at(Level.WARNING).log(
                    "Failed to spawn arrow pickup for %s", ALLOWED_ARROW_ITEM
            );
            lastPositions.remove(ref);
            return;
        }

        ItemComponent itemComponent =
                holder.getComponent(ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setPickupDelay(1.0F);
        }

        commands.addEntity(holder, AddReason.SPAWN);
        commands.removeEntity(ref, RemoveReason.REMOVE);

        lastPositions.remove(ref);

        LOG.at(Level.FINE).log("Recovered arrow at %s", pos);
    }

    /**
     * Removes entries from lastPositions for entities that no longer exist.
     * This prevents memory leaks when projectiles are destroyed by other means
     * (despawn, world unload, killed by damage, etc.)
     */
    private void cleanupStaleReferences(Store<EntityStore> store) {
        Iterator<Ref<EntityStore>> iterator = lastPositions.keySet().iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> ref = iterator.next();
            // Check if the entity still exists by attempting to get a component
            TransformComponent tx = store.getComponent(ref, TransformComponent.getComponentType());
            if (tx == null) {
                iterator.remove();
                LOG.at(Level.FINER).log("Cleaned up stale arrow reference");
            }
        }
    }
}