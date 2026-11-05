package fr.orakun.redo.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedoFabric implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Redo");

    @Override
    public void onInitializeClient() {
        LOG.info("Redo loaded");
    }
}
