package com.hytsustudio.Recoverarrows;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;

/**
 * RecoverArrows – core system
 *
 * Arrow identity is captured EXACTLY like Night:
 * - Bow must be in hand
 * - Utility slot 0 holds the arrow item
 * - Projectile entities carry NO item identity
 */
public class ArrowMagnetSystem extends EntityTickingSystem<EntityStore> {

    /* =======================
       CONSTANTS
       ======================= */

    private static final double SEARCH_RADIUS = 12.0;
    private static final double PICKUP_RADIUS = 2.5;
    private static final double PICKUP_RADIUS_SQ = PICKUP_RADIUS * PICKUP_RADIUS;

    private static final int PICKUP_DELAY_TICKS = 10;
    private static final int REQUIRED_STATIONARY_TICKS = 5;
    private static final double STOP_SPEED_THRESHOLD = 0.05;

    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final boolean OWNER_ONLY_PICKUP = true;

    private static final String DEFAULT_ARROW_ID = "Weapon_Arrow_Crude";

    /* =======================
       STATE
       ======================= */

    private final Map<UUID, Tracker> trackers = new HashMap<>();
    private final Map<UUID, String> lastArrowEquipped = new HashMap<>();

    private int tickCounter = 0;

    private final Query<EntityStore> query = Query.and(Player.getComponentType());

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> cmd
    ) {
        tickCounter++;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null) return;

        Player player = (Player) EntityUtils.toHolder(index, chunk)
                .getComponent(Player.getComponentType());
        if (player == null) return;

        PlayerRef pref = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pref == null) return;

        UUID playerUuid = pref.getUuid();
        if (playerUuid == null) return;

        TransformComponent playerTransform =
                (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) return;

        Vector3d playerPos = playerTransform.getPosition();
        if (playerPos == null) return;

        // Capture arrow type Night-style
        detectBowAndArrow(player, playerUuid);

        // Throttle heavy scans
        if ((tickCounter % SCAN_INTERVAL_TICKS) != 0) {
            cleanupDeadTrackers(store);
            return;
        }

        SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                (SpatialResource<Ref<EntityStore>, EntityStore>)
                        store.getResource(EntityModule.get().getEntitySpatialResourceType());
        if (spatial == null) return;

        SpatialStructure<Ref<EntityStore>> structure = spatial.getSpatialStructure();
        if (structure == null) return;

        List<Ref<EntityStore>> nearby = new ArrayList<>();
        structure.collect(playerPos, SEARCH_RADIUS, nearby);

        for (Ref<EntityStore> ref : nearby) {
            if (ref == null || ref.equals(playerRef)) continue;

            Projectile projectile =
                    (Projectile) store.getComponent(ref, Projectile.getComponentType());
            if (projectile == null) continue;

            TransformComponent arrowTransform =
                    (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
            if (arrowTransform == null) {
                trackers.remove(ref);
                continue;
            }

            Velocity vel =
                    (Velocity) store.getComponent(ref, Velocity.getComponentType());
            if (vel == null) continue;

            Vector3d arrowPos = arrowTransform.getPosition();
            Vector3d v = vel.getVelocity();
            if (arrowPos == null || v == null) continue;

            double speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);

            Tracker tracker = trackers.get(ref);
            if (tracker == null) {
                tracker = new Tracker(ref, playerUuid, tickCounter);
                trackers.put(ref, tracker);
            }

            tracker.update(speed);

            if ((tickCounter - tracker.spawnTick) < PICKUP_DELAY_TICKS) continue;
            if (tracker.stationaryTicks < REQUIRED_STATIONARY_TICKS) continue;

            if (distanceSq(playerPos, arrowPos) > PICKUP_RADIUS_SQ) continue;

            if (OWNER_ONLY_PICKUP && !tracker.ownerUuid.equals(playerUuid)) continue;

            String arrowId = lastArrowEquipped.getOrDefault(playerUuid, DEFAULT_ARROW_ID);

            if (tryGiveArrow(player, arrowId)) {
                cmd.tryRemoveEntity(ref, RemoveReason.REMOVE);
                trackers.remove(ref);
                player.sendInventory();
                break;
            }
        }

        cleanupDeadTrackers(store);
    }

    /* =======================
       NIGHT-STYLE ARROW DETECTION
       ======================= */

    private void detectBowAndArrow(Player player, UUID playerUuid) {
        Inventory inventory = player.getInventory();
        if (inventory == null) return;

        ItemStack inHand = inventory.getItemInHand();
        if (inHand == null || inHand.isEmpty()) return;

        String handId = inHand.getItemId();
        if (handId == null || !handId.contains("Bow")) return;

        ItemContainer utility = inventory.getUtility();
        if (utility == null) return;

        ItemStack arrowStack = utility.getItemStack((short) 0);
        if (arrowStack == null || arrowStack.isEmpty()) return;

        String arrowId = arrowStack.getItemId();
        if (arrowId == null || !arrowId.contains("Arrow")) return;

        lastArrowEquipped.put(playerUuid, arrowId);
    }

    /* =======================
       HELPERS
       ======================= */

    private boolean tryGiveArrow(Player player, String arrowId) {
        Inventory inv = player.getInventory();
        if (inv == null) return false;

        ItemStackTransaction tx =
                inv.getCombinedHotbarFirst().addItemStack(new ItemStack(arrowId, 1));

        ItemStack remainder = tx.getRemainder();
        return remainder == null || remainder.isEmpty();
    }

    private void cleanupDeadTrackers(Store<EntityStore> store) {
        Iterator<Map.Entry<Ref<EntityStore>, Tracker>> it =
                trackers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Ref<EntityStore>, Tracker> entry = it.next();
            Ref<EntityStore> ref = entry.getKey();

            // 🔒 HARD SAFETY CHECKS
            if (ref == null || !ref.isValid()) {
                it.remove();
                continue;
            }

            if (!store.has(ref)) {
                it.remove();
            }
        }
    }

    private static double distanceSq(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /* =======================
       TRACKER
       ======================= */

    private static final class Tracker {
        final Ref<EntityStore> arrowRef;
        final UUID ownerUuid;
        final int spawnTick;

        int stationaryTicks = 0;

        Tracker(Ref<EntityStore> arrowRef, UUID ownerUuid, int spawnTick) {
            this.arrowRef = arrowRef;
            this.ownerUuid = ownerUuid;
            this.spawnTick = spawnTick;
        }

        void update(double speed) {
            if (speed < STOP_SPEED_THRESHOLD) stationaryTicks++;
            else stationaryTicks = 0;
        }
    }
}
