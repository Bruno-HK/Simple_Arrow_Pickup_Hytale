package com.hytsustudio.Recoverarrows;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class ArrowMagnetSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOG = HytaleLogger.get("ArrowRecover");

    private static final double STOP_EPSILON_SQ = 0.0001;
    private static final double STOP_VELOCITY = 0.01;

    private final Map<Ref<EntityStore>, Vector3d> lastPositions = new HashMap<>();

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
        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        TransformComponent tx =
                store.getComponent(ref, TransformComponent.getComponentType());
        Velocity vel =
                store.getComponent(ref, Velocity.getComponentType());
        ModelComponent model =
                store.getComponent(ref, ModelComponent.getComponentType());

        if (tx == null || vel == null || model == null || model.getModel() == null) {
            return;
        }

        Vector3d pos = tx.getPosition();
        Vector3d last = lastPositions.get(ref);

        if (last != null) {
            double dx = last.getX() - pos.getX();
            double dy = last.getY() - pos.getY();
            double dz = last.getZ() - pos.getZ();

            boolean stopped =
                    (dx * dx + dy * dy + dz * dz) < STOP_EPSILON_SQ
                            && vel.getVelocity().length() < STOP_VELOCITY;

            if (stopped) {
                // ----- build correct item id from model -----
                String modelAssetId = model.getModel().getModelAssetId();
                int idx = modelAssetId.lastIndexOf("Arrow_");
                if (idx == -1) {
                    return;
                }

                String itemId = "Weapon_" + modelAssetId.substring(idx);

                ItemStack arrowItem = new ItemStack(itemId, 1);

                Holder<EntityStore> holder =
                        ItemComponent.generateItemDrop(
                                commands,
                                arrowItem,
                                last.clone(), // FINAL arrow position
                                Vector3f.ZERO,
                                0f, 0f, 0f
                        );

                if (holder == null) {
                    LOG.at(Level.WARNING).log(
                            "Failed to drop arrow item %s (invalid item id?)",
                            itemId
                    );
                    return;
                }

                ItemComponent itemComp =
                        holder.getComponent(ItemComponent.getComponentType());
                if (itemComp != null) {
                    itemComp.setPickupDelay(1.0f);
                }

                commands.addEntity(holder, AddReason.SPAWN);
                commands.removeEntity(ref, RemoveReason.REMOVE);

                lastPositions.remove(ref);
                return;
            }
        }

        // cache position for next tick
        lastPositions.put(ref, pos.clone());
    }
}
