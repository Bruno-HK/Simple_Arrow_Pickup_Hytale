package com.hytsustudio.recoverarrows;

import com.hytale.server.plugins.Plugin;
import java.util.logging.Logger;

/**
 * RecoverArrows plugin entry point.
 * Infrastructure-only: no gameplay systems are registered here yet.
 */
public final class RecoverArrowsPlugin extends Plugin {
    private final Logger logger = Logger.getLogger(RecoverArrowsPlugin.class.getName());

    @Override
    public void onEnable() {
        logger.info("RecoverArrows enabled.");
    }

    @Override
    public void onDisable() {
        logger.info("RecoverArrows disabled.");
    }
}
