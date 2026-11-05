package fr.orakun.redo.fabric;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.format.RedoArchive;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class RedoFabric implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Redo");

    @Override
    public void onInitializeClient() {
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
        Redo.init(FabricLoader.getInstance().getGameDir().toFile(), SharedConstants.getCurrentVersion().name());
        Thread recover = new Thread(new Runnable() {
            @Override
            public void run() {
                List<File> files = RedoArchive.recover(Redo.recordingsDir());
                if (!files.isEmpty()) LOG.info("Recovered {} unfinished recording(s)", files.size());
            }
        }, "Redo recovery");
        recover.setDaemon(true);
        recover.start();
        LOG.info("Redo {} ready (Minecraft {})", Redo.VERSION, Redo.minecraftVersion());
    }
}
