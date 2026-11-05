package fr.orakun.redo.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.editor.EditorView;
import fr.orakun.redo.core.export.ExportDriver;
import fr.orakun.redo.core.export.ExportJob;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.record.Recorder;
import fr.orakun.redo.core.record.RecorderHud;
import fr.orakun.redo.core.replay.AbstractReplay;
import fr.orakun.redo.core.replay.ReplayController;
import fr.orakun.redo.core.track.EntitySample;
import fr.orakun.redo.core.track.SelfSample;
import fr.orakun.redo.core.ui.Keys;
import fr.orakun.redo.core.ui.MessageView;
import fr.orakun.redo.core.ui.RecordingsView;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Everything the mixins call into on 1.21.11. Holds the little state that ties the game to the
 * shared core: the connection being recorded, the replay's own tick clock and re-entrancy guard.
 */
public final class RedoClient {
    private static final FabricEditorHost HOST = new FabricEditorHost();
    /** The connection being recorded (network or singleplayer), or null. */
    private static volatile Connection recorded;
    /**
     * Depth of Redo work that may make the game re-enter its own loop (leaving a world runs a
     * forced frame). While above zero, the hooks leave the game alone.
     */
    static int busy;
    private static long lastTimerMs = -1;
    private static double tickAcc;
    private static float lastFov = (float) CameraState.DEFAULT_FOV;
    private static boolean highlightWasDown, markerWasDown;
    private static final Set<String> encodeFailures = new HashSet<String>();
    private static int replayErrors;

    private RedoClient() {
    }

    // ------------------------------------------------------------------ state

    public static FabricReplay replay() {
        ReplayController c = ReplayController.current();
        return c != null && c.replay instanceof FabricReplay ? (FabricReplay) c.replay : null;
    }

    public static boolean replayActive() {
        return replay() != null;
    }

    /** True while the replay editor is the open screen. */
    public static boolean editorShown() {
        Screen s = Compat.screen();
        return s instanceof RedoScreen && ((RedoScreen) s).view instanceof EditorView;
    }

    /**
     * While the replay editor is open, the game's own screens (level loading when the world is
     * rebuilt for a jump back, death screen...) do not replace it: no flash, the mouse stays free.
     */
    public static boolean keepEditor(Screen next) {
        return replayActive() && editorShown() && (next == Compat.screen() || !(next instanceof RedoScreen));
    }

    private static boolean keyDown(int key) {
        return key >= 0 && Compat.keyDown(key);
    }

    /** Current modifier keys as {@link Keys} flags. */
    public static int modifiers() {
        Window w = Minecraft.getInstance().getWindow();
        int m = 0;
        if (Compat.keyDown(Keys.LEFT_SHIFT) || Compat.keyDown(Keys.RIGHT_SHIFT)) m |= Keys.MOD_SHIFT;
        if (Compat.keyDown(Keys.LEFT_CONTROL) || Compat.keyDown(Keys.RIGHT_CONTROL)) m |= Keys.MOD_CONTROL;
        if (Compat.keyDown(Keys.LEFT_ALT) || Compat.keyDown(Keys.RIGHT_ALT)) m |= Keys.MOD_ALT;
        return m;
    }

    // ------------------------------------------------------------------ screens

    /** The list of recordings, coming back to {@code parent} when closed. */
    public static Screen recordingsScreen(final Screen parent) {
        RecordingsView view = new RecordingsView(new RecordingsView.Host() {
            @Override
            public void openReplay(RedoArchive archive) {
                RedoClient.openReplay(archive, parent);
            }

            @Override
            public void openFolder(File folder) {
                HOST.openFolder(folder);
            }

            @Override
            public void close() {
                Compat.setScreen(parent);
            }

            @Override
            public String clipboard() {
                return HOST.clipboard();
            }
        });
        return new RedoScreen(view, new Runnable() {
            @Override
            public void run() {
                Compat.setScreen(parent);
            }
        });
    }

    public static void openRecordings(Screen parent) {
        Compat.setScreen(recordingsScreen(parent));
    }

    static Screen message(String text, final Screen back) {
        return new RedoScreen(new MessageView("Redo", text, true, back == null ? null : new Runnable() {
            @Override
            public void run() {
                Compat.setScreen(back);
            }
        }), null);
    }

    /** Why this recording cannot be played here, or null. */
    static String incompatibility(RedoMeta meta) {
        boolean same = "modern".equals(meta.family) && meta.protocol == SharedConstants.getProtocolVersion();
        if (same) return null;
        String v = meta.minecraftVersion == null || meta.minecraftVersion.isEmpty() ? "une autre version" : meta.minecraftVersion;
        return "Cet enregistrement a été fait en " + v + ". Ouvre-le avec Redo pour " + v
                + " (tu es en " + SharedConstants.getCurrentVersion().name() + ").";
    }

    static void openReplay(RedoArchive archive, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        final Screen list = recordingsScreen(parent);
        String problem = incompatibility(archive.meta);
        if (problem != null) {
            Compat.setScreen(message(problem, list));
            return;
        }
        if (mc.level != null) {
            Compat.setScreen(message("Quitte d'abord le monde pour ouvrir un enregistrement.", list));
            return;
        }
        Compat.setScreen(new RedoScreen(new MessageView("Redo", "Chargement de l'enregistrement…", true, new Runnable() {
            @Override
            public void run() {
                FabricReplay r = replay();
                if (r != null) r.exit();
            }
        }), null));
        replayErrors = 0;
        lastTimerMs = -1;
        tickAcc = 0;
        if (ReplayController.open(new FabricReplay(archive)) == null) {
            Compat.setScreen(message("Impossible d'ouvrir cet enregistrement (voir le journal du jeu).", list));
        }
    }

    /** Leaves the replay and shows what went wrong. */
    static void fail(String text) {
        FabricReplay r = replay();
        if (r != null) r.exit();
        Compat.setScreen(message(text, recordingsScreen(new TitleScreen())));
    }

    // ------------------------------------------------------------------ game loop

    /** Start of a frame, before the tick timer runs. */
    public static void frameStart() {
        if (E2E.ENABLED && busy == 0) E2E.frame();
        if (busy > 0) return;
        FabricReplay r = replay();
        if (r == null) return;
        ReplayController c = ReplayController.current();
        Minecraft mc = Minecraft.getInstance();
        busy++;
        try {
            if (r.failed()) {
                fail("La lecture de l'enregistrement a échoué (fichier abîmé ?).");
                return;
            }
            c.frameStart();
            if (ReplayController.current() != c) return;
            if (r.worldReady()) {
                Screen s = Compat.screen();
                if (!(s instanceof RedoScreen) || !(((RedoScreen) s).view instanceof EditorView)) {
                    EditorView v = c.editor(HOST);
                    if (v == null) {
                        fail(c.error() == null ? "Impossible d'ouvrir l'éditeur." : c.error());
                        return;
                    }
                    Compat.setScreen(new RedoScreen(v, null));
                }
            }
        } catch (RuntimeException e) {
            RedoLog.error("Replay frame failed", e);
            fail("Erreur pendant la lecture : " + e);
        } finally {
            busy--;
        }
    }

    /** Start of a client tick: plays the next 50 ms of the recording. */
    public static void tickStart() {
        if (busy > 0) return;
        FabricReplay r = replay();
        if (r == null) return;
        busy++;
        try {
            r.tick();
        } finally {
            busy--;
        }
    }

    /** End of a client tick: records the world and reads the recording keys. */
    public static void tickEnd() {
        if (busy > 0 || replayActive()) return;
        Recorder.tick(FabricTrackSource.INSTANCE);
        Minecraft mc = Minecraft.getInstance();
        boolean free = Compat.screen() == null && Recorder.isRecording();
        boolean h = free && keyDown(Redo.config().keyHighlight);
        if (h && !highlightWasDown) Recorder.toggleHighlight();
        highlightWasDown = h;
        boolean m = free && keyDown(Redo.config().keyMarker);
        if (m && !markerWasDown) Recorder.marker();
        markerWasDown = m;
    }

    /**
     * How many ticks the game runs this frame during a replay, or -1 to keep the game's own
     * count. The replay owns its clock: speed and pause never depend on the server tick rate.
     */
    public static int replayTicks(long nowMs) {
        FabricReplay r = replay();
        if (r == null || busy > 0) {
            lastTimerMs = -1;
            return -1;
        }
        long dt = lastTimerMs < 0 ? 0 : Math.max(0, nowMs - lastTimerMs);
        lastTimerMs = nowMs;
        ExportDriver d = ReplayController.current().export();
        if (d != null && d.isActive()) {
            r.livePartial = d.forcedPartial();
            return d.forcedTicks();
        }
        if (r.isFrozen()) {
            tickAcc = r.renderPartial();
            r.livePartial = (float) tickAcc;
            return 0;
        }
        tickAcc += dt * r.speed() / AbstractReplay.TICK_MS;
        int n = (int) Math.floor(tickAcc);
        tickAcc -= n;
        if (n > 10) n = 10;
        r.livePartial = (float) tickAcc;
        return n;
    }

    /** Interpolation between ticks used to draw the frame. */
    public static float partial(float vanilla) {
        FabricReplay r = replay();
        if (r == null || busy > 0) return vanilla;
        return r.renderPartial();
    }

    // ------------------------------------------------------------------ camera and rendering

    /** The camera forced by the editor, or null. */
    public static CameraState cameraOverride() {
        FabricReplay r = replay();
        return r == null ? null : r.camera();
    }

    public static void fovComputed(float fov) {
        lastFov = fov;
    }

    public static void cameraRendered(double x, double y, double z, float yaw, float pitch) {
        FabricReplay r = replay();
        if (r == null) return;
        CameraState o = r.camera();
        r.lastCamera = new CameraState(x, y, z, yaw, pitch, o == null ? 0 : o.roll, lastFov);
    }

    /** Called once the world is drawn (before the interface): reads the frame for an export. */
    public static void levelRendered() {
        ReplayController c = ReplayController.current();
        if (c == null) return;
        ExportDriver d = c.export();
        if (d == null || !d.captureThisFrame()) return;
        final ExportJob job = d.job;
        d.captured();
        Screenshot.takeScreenshot(Compat.mainTarget(), image -> {
            try {
                submitFrame(job, image);
            } catch (RuntimeException e) {
                RedoLog.error("Could not read an export frame", e);
            } finally {
                image.close();
            }
        });
    }

    private static void submitFrame(ExportJob job, NativeImage img) {
        if (!job.isActive()) return;
        int w = job.inputWidth(), h = job.inputHeight();
        byte[] out = job.borrowBuffer();
        if (out == null || out.length < w * h * 4) out = new byte[w * h * 4];
        int iw = img.getWidth(), ih = img.getHeight();
        int[] px = img.getPixelsABGR();
        int cw = Math.min(w, iw), ch = Math.min(h, ih);
        if (cw != w || ch != h) java.util.Arrays.fill(out, (byte) 0);
        for (int y = 0; y < ch; y++) {
            int src = y * iw, dst = y * w * 4;
            for (int x = 0; x < cw; x++) {
                int p = px[src + x];
                out[dst++] = (byte) p;
                out[dst++] = (byte) (p >> 8);
                out[dst++] = (byte) (p >> 16);
                out[dst++] = (byte) 0xFF;
            }
        }
        job.submit(out);
    }

    /** Draws the recording indicator on the game's interface. */
    public static void renderHud(FabricGfx g) {
        if (replayActive()) return;
        if (Compat.hideGui()) return;
        RecorderHud.render(g);
    }

    /** Replaces the local player's movement during a replay. Returns true if handled. */
    public static boolean replaySelf(LocalPlayer p) {
        FabricReplay r = replay();
        if (r == null) return false;
        SelfSample s = r.selfAt(r.tickTime());
        if (s != null) {
            p.setPos(s.x, s.y, s.z);
            p.setYRot(s.yaw);
            p.setXRot(s.pitch);
            p.setYHeadRot(s.headYaw);
            p.setYBodyRot(s.bodyYaw);
            p.setShiftKeyDown((s.flags & EntitySample.SNEAK) != 0);
            p.setSprinting((s.flags & EntitySample.SPRINT) != 0);
            if ((s.actions & SelfSample.SWING) != 0 && !Compat.swinging(p)) Compat.swing(p);
        }
        p.calculateEntityAnimation(false);
        return true;
    }

    // ------------------------------------------------------------------ recording

    static int phase(ConnectionProtocol p) {
        if (p == ConnectionProtocol.CONFIGURATION) return 1;
        if (p == ConnectionProtocol.PLAY) return 2;
        return 0;
    }

    /** A client connection is about to log in: starts the capture (the recording starts from the pause menu). */
    public static void connectionStarting(Connection c, ServerData server) {
        if (((RedoConnection) c).redo$isReplay() || replayActive()) return;
        RedoMeta m = new RedoMeta();
        m.minecraftVersion = SharedConstants.getCurrentVersion().name();
        m.family = "modern";
        m.protocol = SharedConstants.getProtocolVersion();
        if (server != null) {
            m.serverName = server.name == null ? "" : server.name;
            m.serverAddress = server.ip == null ? "" : server.ip;
        } else {
            m.serverName = "Solo";
        }
        recorded = c;
        encodeFailures.clear();
        Recorder.start(m);
    }

    /** A connection is gone. Ends the recording if it was the recorded one. */
    public static void connectionClosed(Connection c) {
        if (c != null && c == recorded) {
            recorded = null;
            Recorder.stop();
        }
    }

    /** The game leaves its world. */
    public static void disconnecting() {
        if (busy > 0) return;
        FabricReplay r = replay();
        if (r != null) {
            r.abandon();
            return;
        }
        recorded = null;
        Recorder.stop();
    }

    /** Raw bytes of a packet read from the network (any thread). */
    public static void networkPacket(ProtocolInfo<?> info, ByteBuf in) {
        if (info == null || info.flow() != PacketFlow.CLIENTBOUND || !Recorder.isCapturing()) return;
        int phase = phase(info.id());
        int n = in.readableBytes();
        if (phase == 0 || n <= 0) return;
        byte[] data = new byte[n];
        in.getBytes(in.readerIndex(), data);
        Recorder.packet(phase, data);
    }

    /** A packet received by an in-memory (singleplayer) connection: encoded to be recorded. */
    public static void memoryPacket(Connection c, Packet<?> packet) {
        if (c != recorded || !c.isMemoryConnection() || !Recorder.isCapturing()) return;
        ProtocolInfo<?> info = ((RedoConnection) c).redo$inbound();
        if (info == null) return;
        int phase = phase(info.id());
        if (phase != 0) encode(info, packet, phase);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void encode(ProtocolInfo<?> info, Packet<?> packet, int phase) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> sub : bundle.subPackets()) encode(info, sub, phase);
            return;
        }
        ByteBuf buf = Unpooled.buffer(256);
        try {
            StreamCodec codec = info.codec();
            codec.encode(buf, packet);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            Recorder.packet(phase, data);
        } catch (Throwable t) {
            String type = packet.getClass().getSimpleName();
            synchronized (encodeFailures) {
                if (encodeFailures.add(type)) RedoLog.warn("Could not record packet " + type + ": " + t);
            }
        } finally {
            buf.release();
        }
    }

    public static void blockChanged(BlockPos pos, boolean wasAir, boolean isAir) {
        if (replayActive()) return;
        Recorder.blockChanged(pos.getX(), pos.getY(), pos.getZ(), wasAir, isAir);
    }

    public static void chat(String text) {
        if (replayActive()) return;
        Recorder.chat(text);
    }

    /** A replay connection hit an error: logged, the replay goes on. */
    public static void replayError(Throwable t) {
        if (replayErrors++ < 20) RedoLog.error("Replay packet error", t);
    }

    static void openFolder(File f) {
        Compat.openFile(f);
    }
}
