package fr.orakun.redo.core;

import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.prompt.ClaudeClient;
import fr.orakun.redo.core.ui.Keys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** User settings, stored in config/redo.json. */
public final class RedoConfig {
    /** Record every multiplayer session from the moment you join. */
    public boolean autoRecord = true;
    /** Keep recordings without asking when leaving a server. */
    public boolean autoSave = true;
    public int keyHighlight = Keys.F9;
    public int keyMarker = Keys.M;
    /** Folder of recordings; empty = .minecraft/redo/recordings. */
    public String recordingsDir = "";
    /** Folder of exported videos; empty = .minecraft/redo/exports. */
    public String exportDir = "";
    public String ffmpegPath = "";
    public String claudeApiKey = "";
    public String claudeModel = ClaudeClient.DEFAULT_MODEL;
    /** Free camera speed in blocks per second. */
    public double flySpeed = 10;
    /** Shows the small red dot while recording. */
    public boolean showRecordingIndicator = true;

    private transient File file;

    public static RedoConfig load(File file) {
        RedoConfig c = new RedoConfig();
        c.file = file;
        if (!file.isFile()) {
            c.save();
            return c;
        }
        try {
            InputStream in = new FileInputStream(file);
            byte[] data;
            try {
                data = readAll(in);
            } finally {
                in.close();
            }
            Map<String, Object> m = Json.parseObject(new String(data, "UTF-8"));
            c.autoRecord = Json.getBool(m, "autoRecord", c.autoRecord);
            c.autoSave = Json.getBool(m, "autoSave", c.autoSave);
            c.keyHighlight = Json.getInt(m, "keyHighlight", c.keyHighlight);
            c.keyMarker = Json.getInt(m, "keyMarker", c.keyMarker);
            c.recordingsDir = Json.getString(m, "recordingsDir", c.recordingsDir);
            c.exportDir = Json.getString(m, "exportDir", c.exportDir);
            c.ffmpegPath = Json.getString(m, "ffmpegPath", c.ffmpegPath);
            c.claudeApiKey = Json.getString(m, "claudeApiKey", c.claudeApiKey);
            c.claudeModel = Json.getString(m, "claudeModel", c.claudeModel);
            c.flySpeed = Json.getDouble(m, "flySpeed", c.flySpeed);
            c.showRecordingIndicator = Json.getBool(m, "showRecordingIndicator", c.showRecordingIndicator);
        } catch (Exception e) {
            RedoLog.warn("Invalid Redo config, using defaults: " + e);
        }
        return c;
    }

    public void save() {
        if (file == null) return;
        Map<String, Object> m = Json.obj();
        m.put("autoRecord", autoRecord);
        m.put("autoSave", autoSave);
        m.put("keyHighlight", keyHighlight);
        m.put("keyMarker", keyMarker);
        m.put("recordingsDir", recordingsDir);
        m.put("exportDir", exportDir);
        m.put("ffmpegPath", ffmpegPath);
        m.put("claudeApiKey", claudeApiKey);
        m.put("claudeModel", claudeModel);
        m.put("flySpeed", flySpeed);
        m.put("showRecordingIndicator", showRecordingIndicator);
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            OutputStream out = new FileOutputStream(file);
            try {
                out.write(Json.pretty(m).getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            RedoLog.warn("Could not save Redo config: " + e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
