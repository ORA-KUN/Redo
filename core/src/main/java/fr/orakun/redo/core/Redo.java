package fr.orakun.redo.core;

import java.io.File;

/** Global state shared by the platform adapters: folders, settings, version. */
public final class Redo {
    public static final String VERSION = "0.1.0";
    public static final String NAME = "Redo";

    private static File gameDir = new File(".");
    private static RedoConfig config;
    private static String minecraftVersion = "";

    private Redo() {
    }

    public static void init(File gameDirectory, String mcVersion) {
        gameDir = gameDirectory;
        minecraftVersion = mcVersion;
        config = RedoConfig.load(new File(new File(gameDir, "config"), "redo.json"));
        recordingsDir().mkdirs();
        exportDir().mkdirs();
    }

    public static RedoConfig config() {
        if (config == null) config = new RedoConfig();
        return config;
    }

    public static String minecraftVersion() {
        return minecraftVersion;
    }

    public static File gameDir() {
        return gameDir;
    }

    /** .minecraft/redo, holds ffmpeg and temporary files. */
    public static File home() {
        return new File(gameDir, "redo");
    }

    public static File recordingsDir() {
        String d = config().recordingsDir;
        return d == null || d.trim().isEmpty() ? new File(home(), "recordings") : new File(d);
    }

    public static File exportDir() {
        String d = config().exportDir;
        return d == null || d.trim().isEmpty() ? new File(home(), "exports") : new File(d);
    }

    public static String userAgent() {
        return NAME + "/" + VERSION + " (Minecraft " + minecraftVersion + ")";
    }
}
