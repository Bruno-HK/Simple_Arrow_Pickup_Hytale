package com.hytsustudio;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;

import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.ItemUtils;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import com.hypixel.hytale.component.spatial.SpatialResource;

import com.hypixel.hytale.math.vector.Vector3d;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.objects.ObjectList;

public class ArrowPickupSystem extends EntityTickingSystem<EntityStore> {

    public Query<EntityStore> getQuery() {
        return Query.and(
                ProjectileComponent.getComponentType(),
                TransformComponent.getComponentType()
        );
    }

    public void tick(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> cmd
    ) {

        ProjectileComponent proj =
                chunk.getComponent(index, ProjectileComponent.getComponentType());

        if (proj == null) return;

        String id = proj.getProjectileAssetName();
        if (id == null || !id.contains("Arrow")) return;

        TransformComponent transform =
                chunk.getComponent(index, TransformComponent.getComponentType());

        Vector3d pos = transform.getPosition();

        SpatialResource<Ref<EntityStore>, EntityStore> players =
                cmd.getResource(EntityModule.get().getPlayerSpatialResourceType());

        ObjectList<Ref<EntityStore>> nearby =
                SpatialResource.getThreadLocalReferenceList();

        players.getSpatialStructure().collect(pos, 2.5, nearby);

        if (nearby.isEmpty()) return;

        Ref<EntityStore> playerRef = nearby.getFirst();

        ItemStack stack = new ItemStack(id, 1);

        ItemUtils.interactivelyPickupItem(
                playerRef,
                stack,
                pos,
                cmd
        );

        cmd.tryRemoveEntity(
                chunk.getReferenceTo(index),
                RemoveReason.REMOVE
        );
    }
}


