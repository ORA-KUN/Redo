package fr.orakun.redo.legacy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "redo", name = "Redo", version = "0.1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class RedoLegacy {
    public static final Logger LOG = LogManager.getLogger("Redo");

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOG.info("Redo loaded");
    }
}
