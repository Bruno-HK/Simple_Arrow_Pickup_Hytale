package com.hytsustudio.Recoverarrows;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Working Arrow Magnet System based on decompiled working version
 */
public class ArrowMagnetSystem extends EntityTickingSystem<EntityStore> {

        @Nonnull
        private final Query<EntityStore> query = Query.and(Player.getComponentType());

        // SMALLER pickup area - more realistic
        private static final double HORIZONTAL_RANGE = 1.5;  // Reduced from 3.0
        private static final double RANGE_DOWN_FROM_FEET = 1.0;  // Reduced from 1.5
        private static final double RANGE_UP_FROM_FEET = 1.5;    // Reduced from 3.0
        private static final double SEARCH_RADIUS = 8.0;         // Reduced from 12.0

        // IMPORTANT: SLOWER speed threshold for pickup
        private static final double MIN_VELOCITY = 0.02;         // Reduced from 0.05
        private static final int MESSAGE_COOLDOWN = 20;
        private static final int MIN_ALIVE_TICKS = 10;           // Increased from 5

        // NEW: Prevent arrow despawn by tracking IMMEDIATELY
        private static final int ARROW_LIFETIME_TICKS = 20 * 300; // 5 minutes

        private final HashMap<UUID, Integer> lastMessageTick = new HashMap<>();
        private final HashMap<Integer, ArrowData> trackedArrows = new HashMap<>();
        private static final HashMap<UUID, String> lastArrowEquipped = new HashMap<>();
        private int currentTick = 0;

        @Override
        @Nonnull
        public Query<EntityStore> getQuery() {
            return this.query;
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            ++this.currentTick;

            Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
            Player player = (Player) EntityUtils.toHolder(index, archetypeChunk)
                    .getComponent(Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent == null) return;

            UUID playerUUID = playerRefComponent.getUuid();
            TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
            ModelComponent playerModel = store.getComponent(playerRef, ModelComponent.getComponentType());
            if (playerTransform == null || playerModel == null) return;

            detectBowAndArrow(player, playerUUID);

            Vector3d playerPos = playerTransform.getPosition().clone();
            double eyeHeight = playerModel.getModel().getEyeHeight();

            List<Ref<EntityStore>> arrowsToCollect = new ArrayList<>();
            List<Integer> currentArrowHashes = new ArrayList<>();
            List<Ref<EntityStore>> nearbyEntities = new ArrayList<>();

            try {
                SpatialResource<Ref<EntityStore>, EntityStore> entitySpatialResource =
                        store.getResource(EntityModule.get().getEntitySpatialResourceType());
                entitySpatialResource.getSpatialStructure().collect(playerPos, SEARCH_RADIUS, nearbyEntities);

                for (Ref<EntityStore> entityRef : nearbyEntities) {
                    if (entityRef.equals(playerRef)) continue;

                    Projectile projectile = store.getComponent(entityRef, Projectile.getComponentType());
                    if (projectile == null) continue;

                    int arrowHash = entityRef.hashCode();
                    currentArrowHashes.add(arrowHash);

                    TransformComponent entityTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
                    if (entityTransform == null) continue;

                    Vector3d arrowPos = entityTransform.getPosition();
                    Velocity velocity = store.getComponent(entityRef, Velocity.getComponentType());

                    // Track arrow immediately when found
                    ArrowData arrowData = trackedArrows.get(arrowHash);
                    if (arrowData == null) {
                        // NEW: Track arrow spawn time
                        arrowData = new ArrowData(arrowHash, currentTick, arrowPos, playerUUID);
                        trackedArrows.put(arrowHash, arrowData);
                    } else {
                        arrowData.updatePosition(arrowPos);
                    }

                    // Check lifetime - PREVENT DESPAWN
                    if (currentTick - arrowData.spawnTick > ARROW_LIFETIME_TICKS) {
                        commandBuffer.tryRemoveEntity(entityRef, RemoveReason.REMOVE);
                        trackedArrows.remove(arrowHash);
                        continue;
                    }

                    // Arrow must be alive for minimum time
                    if (currentTick - arrowData.spawnTick < MIN_ALIVE_TICKS) {
                        continue;
                    }

                    // Check if arrow is within smaller pickup box
                    if (!isWithinPickupRange(playerPos, eyeHeight, arrowPos)) {
                        continue;
                    }

                    // Arrow is ready for pickup - NO VELOCITY CHECK
                    if (isWithinPickupRange(playerPos, eyeHeight, arrowPos)) {
                        arrowsToCollect.add(entityRef);
                    }arrowsToCollect.add(entityRef);

                }
            } catch (Exception e) {
                // Ignore errors
            }

            // Clean up old trackers
            trackedArrows.keySet().removeIf(hash -> !currentArrowHashes.contains(hash));

            // Collect arrows
            if (!arrowsToCollect.isEmpty()) {
                int collected = 0;
                Inventory inventory = player.getInventory();
                String arrowId = lastArrowEquipped.getOrDefault(playerUUID, "Weapon_Arrow_Crude");

                for (Ref<EntityStore> arrowRef : arrowsToCollect) {
                    try {
                        if (inventory != null) {
                            ItemStack arrowStack = new ItemStack(arrowId, 1);
                            ItemStack remainder = addItemToInventory(inventory, arrowStack);

                            if (remainder == null || remainder.isEmpty()) {
                                commandBuffer.tryRemoveEntity(arrowRef, RemoveReason.REMOVE);
                                ++collected;
                                trackedArrows.remove(arrowRef.hashCode());
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors
                    }
                }

                if (collected > 0) {
                    if (!lastMessageTick.containsKey(playerUUID) ||
                            this.currentTick - lastMessageTick.get(playerUUID) > MESSAGE_COOLDOWN) {
                        lastMessageTick.put(playerUUID, this.currentTick);
                        player.sendInventory();
                    }
                }
            }
        }

        private boolean isWithinPickupRange(Vector3d playerPos, double eyeHeight, Vector3d arrowPos) {
            double playerFeetY = playerPos.y;

            // SMALLER pickup area
            double minX = playerPos.x - HORIZONTAL_RANGE;
            double maxX = playerPos.x + HORIZONTAL_RANGE;
            double minY = playerFeetY - RANGE_DOWN_FROM_FEET;
            double maxY = playerFeetY + eyeHeight + RANGE_UP_FROM_FEET;
            double minZ = playerPos.z - HORIZONTAL_RANGE;
            double maxZ = playerPos.z + HORIZONTAL_RANGE;

            return arrowPos.x >= minX && arrowPos.x <= maxX &&
                    arrowPos.y >= minY && arrowPos.y <= maxY &&
                    arrowPos.z >= minZ && arrowPos.z <= maxZ;
        }

        private void detectBowAndArrow(@Nonnull Player player, @Nonnull UUID playerUUID) {
            try {
                Inventory inventory = player.getInventory();
                if (inventory == null) return;

                ItemStack mainHandItem = inventory.getItemInHand();
                if (mainHandItem == null || mainHandItem.isEmpty()) return;

                String itemId = mainHandItem.getItemId();
                if (isBow(itemId)) {
                    ItemStack utilityArrow = inventory.getUtility().getItemStack((short) 0);
                    if (utilityArrow != null && !utilityArrow.isEmpty() && isArrow(utilityArrow.getItemId())) {
                        String arrowId = utilityArrow.getItemId();
                        lastArrowEquipped.put(playerUUID, arrowId);
                    }
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }

        private boolean isBow(@Nonnull String itemId) {
            return itemId.contains("Bow"); // Simpler check
        }

        private boolean isArrow(@Nonnull String itemId) {
            return itemId.contains("Arrow"); // Simpler check
        }

        private ItemStack addItemToInventory(@Nonnull Inventory inventory, @Nonnull ItemStack itemStack) {
            ItemStackTransaction transaction = inventory.getCombinedHotbarFirst().addItemStack(itemStack);
            return transaction.getRemainder();
        }

        // Arrow tracking data
        private static class ArrowData {
            final int arrowHash;
            final int spawnTick;
            final UUID ownerUUID;
            Vector3d lastPosition;
            int stationaryTicks;

            ArrowData(int arrowHash, int spawnTick, Vector3d position, UUID ownerUUID) {
                this.arrowHash = arrowHash;
                this.spawnTick = spawnTick;
                this.ownerUUID = ownerUUID;
                this.lastPosition = position.clone();
                this.stationaryTicks = 0;
            }

            void updatePosition(Vector3d newPosition) {
                if (lastPosition != null) {
                    double dx = newPosition.x - lastPosition.x;
                    double dy = newPosition.y - lastPosition.y;
                    double dz = newPosition.z - lastPosition.z;
                    double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

                    if (distance < 0.01) { // Almost stationary
                        stationaryTicks++;
                    } else {
                        stationaryTicks = 0;
                    }
                }
                lastPosition = newPosition.clone();
            }

            boolean isReadyForPickup() {
                return stationaryTicks >= 3; // Must be stationary for 3 ticks
            }
        }
    }