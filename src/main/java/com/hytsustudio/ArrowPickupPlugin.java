package com.hytsustudio;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytsustudio.Recoverarrows.ArrowMagnetSystem;

import javax.annotation.Nonnull;

public class ArrowPickupPlugin extends JavaPlugin {

    public ArrowPickupPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        EntityStore.REGISTRY.registerSystem(new ArrowMagnetSystem());
    }
}
