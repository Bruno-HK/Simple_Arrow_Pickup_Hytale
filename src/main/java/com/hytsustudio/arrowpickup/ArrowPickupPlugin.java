package com.hytsustudio.arrowpickup;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class ArrowPickupPlugin extends JavaPlugin {

    private static ArrowPickupPlugin instance;

    public ArrowPickupPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        getLogger().at(Level.INFO).log("[ArrowPickup] Plugin loaded");
    }

    public static ArrowPickupPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[ArrowPickup] Plugin setup");
        // later: register events here
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("[ArrowPickup] Plugin enabled");
    }

    @Override
    public void shutdown() {
        getLogger().at(Level.INFO).log("[ArrowPickup] Plugin disabled");
    }
}
