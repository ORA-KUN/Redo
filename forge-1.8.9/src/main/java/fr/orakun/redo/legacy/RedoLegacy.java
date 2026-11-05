package fr.orakun.redo.legacy;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.format.RedoArchive;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;

@Mod(modid = "redo", name = "Redo", version = Redo.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class RedoLegacy {
    public static final Logger LOG = LogManager.getLogger("Redo");

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RedoLog.setSink(new RedoLog.Sink() {
            @Override
            public void info(String msg) {
                LOG.info(msg);
            }

            @Override
            public void warn(String msg) {
                LOG.warn(msg);
            }

            @Override
            public void error(String msg, Throwable t) {
                LOG.error(msg, t);
            }
        });
        Redo.init(Minecraft.getMinecraft().mcDataDir, "1.8.9");
        LegacyEvents events = new LegacyEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
        Thread recover = new Thread(new Runnable() {
            @Override
            public void run() {
                List<File> files = RedoArchive.recover(Redo.recordingsDir());
                if (!files.isEmpty()) LOG.info("Recovered " + files.size() + " unfinished recording(s)");
            }
        }, "Redo recovery");
        recover.setDaemon(true);
        recover.start();
        LOG.info("Redo " + Redo.VERSION + " ready (Minecraft 1.8.9)");
    }
}
