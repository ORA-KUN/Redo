package fr.orakun.redo.legacy;

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
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Timer;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/** Everything the 1.8.9 mixins and events call into. */
public final class LegacyClient {
    public static final int PROTOCOL = 47;
    private static final LegacyEditorHost HOST = new LegacyEditorHost();
    /** The connection being recorded (network or singleplayer), or null. */
    private static volatile NetworkManager recorded;
    /** Depth of Redo work that may re-enter the game loop; the hooks stay out of it meanwhile. */
    static int busy;
    private static long lastTimerMs = -1;
    private static double tickAcc;
    private static boolean highlightWasDown, markerWasDown;
    private static CameraEntity camera;
    private static ByteBuffer readBuffer;
    private static final Set<String> encodeFailures = new HashSet<String>();
    private static int replayErrors;

    private LegacyClient() {
    }

    // ------------------------------------------------------------------ state

    public static LegacyReplay replay() {
        ReplayController c = ReplayController.current();
        return c != null && c.replay instanceof LegacyReplay ? (LegacyReplay) c.replay : null;
    }

    public static boolean replayActive() {
        return replay() != null;
    }

    public static int modifiers() {
        int m = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) m |= Keys.MOD_SHIFT;
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) m |= Keys.MOD_CONTROL;
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) m |= Keys.MOD_ALT;
        return m;
    }

    private static boolean keyDown(int glfwKey) {
        int k = Keys.toLwjgl2(glfwKey);
        return k > 0 && Keyboard.isKeyDown(k);
    }

    // ------------------------------------------------------------------ screens

    public static GuiScreen recordingsScreen(final GuiScreen parent) {
        RecordingsView view = new RecordingsView(new RecordingsView.Host() {
            @Override
            public void openReplay(RedoArchive archive) {
                LegacyClient.openReplay(archive, parent);
            }

            @Override
            public void openFolder(File folder) {
                LegacyEditorHost.open(folder);
            }

            @Override
            public void close() {
                Minecraft.getMinecraft().displayGuiScreen(parent);
            }

            @Override
            public String clipboard() {
                return GuiScreen.getClipboardString();
            }
        });
        return new RedoGuiScreen(view, new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().displayGuiScreen(parent);
            }
        });
    }

    public static void openRecordings(GuiScreen parent) {
        Minecraft.getMinecraft().displayGuiScreen(recordingsScreen(parent));
    }

    static GuiScreen message(String text, final GuiScreen back) {
        return new RedoGuiScreen(new MessageView("Redo", text, true, back == null ? null : new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().displayGuiScreen(back);
            }
        }), null);
    }

    static String incompatibility(RedoMeta meta) {
        if ("legacy".equals(meta.family) && meta.protocol == PROTOCOL) return null;
        String v = meta.minecraftVersion == null || meta.minecraftVersion.isEmpty() ? "une autre version" : meta.minecraftVersion;
        return "Cet enregistrement a été fait en " + v + ". Ouvre-le avec Redo pour " + v + " (tu es en 1.8.9).";
    }

    static void openReplay(RedoArchive archive, GuiScreen parent) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen list = recordingsScreen(parent);
        String problem = incompatibility(archive.meta);
        if (problem != null) {
            mc.displayGuiScreen(message(problem, list));
            return;
        }
        if (mc.theWorld != null) {
            mc.displayGuiScreen(message("Quitte d'abord le monde pour ouvrir un enregistrement.", list));
            return;
        }
        mc.displayGuiScreen(new RedoGuiScreen(new MessageView("Redo", "Chargement de l'enregistrement…", true, new Runnable() {
            @Override
            public void run() {
                LegacyReplay r = replay();
                if (r != null) r.exit();
            }
        }), null));
        replayErrors = 0;
        lastTimerMs = -1;
        tickAcc = 0;
        camera = null;
        if (ReplayController.open(new LegacyReplay(archive)) == null) {
            mc.displayGuiScreen(message("Impossible d'ouvrir cet enregistrement (voir le journal du jeu).", list));
        }
    }

    static void fail(String text) {
        LegacyReplay r = replay();
        if (r != null) r.exit();
        Minecraft.getMinecraft().displayGuiScreen(message(text, recordingsScreen(new GuiMainMenu())));
    }

    // ------------------------------------------------------------------ game loop

    /** Start of a frame, before the timer. */
    public static void frameStart() {
        if (busy > 0) return;
        LegacyReplay r = replay();
        if (r == null) return;
        ReplayController c = ReplayController.current();
        Minecraft mc = Minecraft.getMinecraft();
        busy++;
        try {
            if (r.failed()) {
                fail("La lecture de l'enregistrement a échoué (fichier abîmé ?).");
                return;
            }
            c.frameStart();
            if (ReplayController.current() != c) return;
            if (r.worldReady()) {
                GuiScreen s = mc.currentScreen;
                if (!(s instanceof RedoGuiScreen) || !(((RedoGuiScreen) s).view instanceof EditorView)) {
                    EditorView v = c.editor(HOST);
                    if (v == null) {
                        fail(c.error() == null ? "Impossible d'ouvrir l'éditeur." : c.error());
                        return;
                    }
                    mc.displayGuiScreen(new RedoGuiScreen(v, null));
                }
            }
        } catch (RuntimeException e) {
            RedoLog.error("Replay frame failed", e);
            fail("Erreur pendant la lecture : " + e);
        } finally {
            busy--;
        }
    }

    /** The game timer was updated: during a replay Redo decides the ticks and the interpolation. */
    public static void timerUpdated(Timer t) {
        LegacyReplay r = replay();
        if (r == null || busy > 0) {
            lastTimerMs = -1;
            return;
        }
        long now = Minecraft.getSystemTime();
        long dt = lastTimerMs < 0 ? 0 : Math.max(0, now - lastTimerMs);
        lastTimerMs = now;
        ExportDriver d = ReplayController.current().export();
        int n;
        if (d != null && d.isActive()) {
            r.livePartial = d.forcedPartial();
            n = d.forcedTicks();
        } else if (r.isFrozen()) {
            tickAcc = r.renderPartial();
            r.livePartial = (float) tickAcc;
            n = 0;
        } else {
            tickAcc += dt * r.speed() / AbstractReplay.TICK_MS;
            n = (int) Math.floor(tickAcc);
            tickAcc -= n;
            if (n > 10) n = 10;
            r.livePartial = (float) tickAcc;
        }
        t.elapsedTicks = n;
        t.renderPartialTicks = r.renderPartial();
    }

    public static void tickStart() {
        if (busy > 0) return;
        LegacyReplay r = replay();
        if (r == null) return;
        busy++;
        try {
            r.tick();
        } finally {
            busy--;
        }
    }

    public static void tickEnd() {
        if (busy > 0 || replayActive()) return;
        Recorder.tick(LegacyTrackSource.INSTANCE);
        Minecraft mc = Minecraft.getMinecraft();
        boolean free = mc.currentScreen == null && Recorder.isRecording();
        boolean h = free && keyDown(Redo.config().keyHighlight);
        if (h && !highlightWasDown) Recorder.toggleHighlight();
        highlightWasDown = h;
        boolean m = free && keyDown(Redo.config().keyMarker);
        if (m && !markerWasDown) Recorder.marker();
        markerWasDown = m;
    }

    // ------------------------------------------------------------------ camera and rendering

    public static CameraState cameraOverride() {
        LegacyReplay r = replay();
        return r == null ? null : r.camera();
    }

    /** The camera entity while the editor drives the view, or null. */
    public static EntityPlayer cameraEntity() {
        Minecraft mc = Minecraft.getMinecraft();
        return camera != null && mc.getRenderViewEntity() == camera ? camera : null;
    }

    public static Float fovOverride() {
        CameraState c = cameraOverride();
        return c == null ? null : (float) c.fov;
    }

    /** Before the frame is drawn: puts the view where the editor wants it. */
    public static void renderStart() {
        LegacyReplay r = replay();
        Minecraft mc = Minecraft.getMinecraft();
        if (r == null || mc.theWorld == null || mc.thePlayer == null) return;
        CameraState c = r.camera();
        if (c != null) {
            if (camera == null || camera.worldObj != mc.theWorld) camera = new CameraEntity(mc.theWorld);
            camera.place(c);
            if (mc.getRenderViewEntity() != camera) mc.setRenderViewEntity(camera);
            mc.gameSettings.thirdPersonView = 0;
        } else if (mc.getRenderViewEntity() == null || mc.getRenderViewEntity() == camera) {
            mc.setRenderViewEntity(mc.thePlayer);
        }
        Entity v = mc.getRenderViewEntity();
        if (v == null) return;
        float pt = r.renderPartial();
        double x = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
        double y = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt + v.getEyeHeight();
        double z = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        float yaw = v.prevRotationYaw + (v.rotationYaw - v.prevRotationYaw) * pt;
        float pitch = v.prevRotationPitch + (v.rotationPitch - v.prevRotationPitch) * pt;
        r.lastCamera = new CameraState(x, y, z, yaw, pitch, c == null ? 0 : c.roll,
                c == null ? mc.gameSettings.fovSetting : c.fov);
    }

    /** The world is drawn (before the interface): reads the frame for an export. */
    public static void worldRendered() {
        ReplayController c = ReplayController.current();
        if (c == null) return;
        ExportDriver d = c.export();
        if (d == null || !d.captureThisFrame()) return;
        ExportJob job = d.job;
        d.captured();
        Minecraft mc = Minecraft.getMinecraft();
        int fw = mc.displayWidth, fh = mc.displayHeight;
        int w = job.inputWidth(), h = job.inputHeight();
        if (readBuffer == null || readBuffer.capacity() < fw * fh * 4) readBuffer = BufferUtils.createByteBuffer(fw * fh * 4);
        readBuffer.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, fw, fh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuffer);
        byte[] out = job.borrowBuffer();
        if (out == null || out.length < w * h * 4) out = new byte[w * h * 4];
        int cw = Math.min(w, fw), ch = Math.min(h, fh);
        if (cw != w || ch != h) java.util.Arrays.fill(out, (byte) 0);
        for (int row = 0; row < ch; row++) {
            readBuffer.position(row * fw * 4);
            readBuffer.get(out, row * w * 4, cw * 4);
        }
        for (int i = 3; i < out.length; i += 4) out[i] = (byte) 0xFF;
        job.submit(out);
    }

    public static void renderHud() {
        if (replayActive() || !Recorder.isRecording()) return;
        if (Minecraft.getMinecraft().gameSettings.hideGUI) return;
        RecorderHud.render(new LegacyGfx());
    }

    /** Replaces the local player's movement during a replay. Returns true if handled. */
    public static boolean replaySelf(EntityPlayerSP p) {
        LegacyReplay r = replay();
        if (r == null) return false;
        SelfSample s = r.selfAt(r.tickTime());
        if (s != null) {
            p.setPosition(s.x, s.y, s.z);
            p.rotationYaw = s.yaw;
            p.rotationPitch = s.pitch;
            p.rotationYawHead = s.headYaw;
            p.renderYawOffset = s.bodyYaw;
            if (p.movementInput != null) p.movementInput.sneak = (s.flags & EntitySample.SNEAK) != 0;
            p.setSprinting((s.flags & EntitySample.SPRINT) != 0);
            if ((s.actions & SelfSample.SWING) != 0 && !p.isSwingInProgress) p.swingItem();
        }
        p.prevLimbSwingAmount = p.limbSwingAmount;
        double dx = p.posX - p.prevPosX, dz = p.posZ - p.prevPosZ;
        float f = MathHelper.sqrt_double(dx * dx + dz * dz) * 4.0F;
        if (f > 1.0F) f = 1.0F;
        p.limbSwingAmount += (f - p.limbSwingAmount) * 0.4F;
        p.limbSwing += p.limbSwingAmount;
        return true;
    }

    // ------------------------------------------------------------------ recording

    /** The client switched to the play state with this connection: a recording begins. */
    public static void connectionStarting(NetworkManager nm) {
        if (nm == null || ((RedoNetworkManager) nm).redo$isReplay() || replayActive()) return;
        Minecraft mc = Minecraft.getMinecraft();
        RedoMeta m = new RedoMeta();
        m.minecraftVersion = "1.8.9";
        m.family = "legacy";
        m.protocol = PROTOCOL;
        ServerData server = mc.getCurrentServerData();
        if (server != null && !nm.isLocalChannel()) {
            m.serverName = server.serverName == null ? "" : server.serverName;
            m.serverAddress = server.serverIP == null ? "" : server.serverIP;
        } else {
            m.serverName = "Solo";
        }
        recorded = nm;
        encodeFailures.clear();
        Recorder.start(m);
    }

    public static void connectionClosed(NetworkManager nm) {
        if (nm != null && nm == recorded) {
            recorded = null;
            Recorder.stop();
        }
    }

    /** The game leaves its world. */
    public static void disconnecting() {
        if (busy > 0) return;
        LegacyReplay r = replay();
        if (r != null) {
            r.abandon();
            return;
        }
        recorded = null;
        Recorder.stop();
    }

    /** Raw bytes of a packet read from the network, before decoding (netty thread). */
    public static void networkPacket(EnumPacketDirection direction, ChannelHandlerContext ctx, ByteBuf in) {
        if (direction != EnumPacketDirection.CLIENTBOUND || !Recorder.isRecording()) return;
        if (ctx.channel().attr(NetworkManager.attrKeyConnectionState).get() != EnumConnectionState.PLAY) return;
        int n = in.readableBytes();
        if (n <= 0) return;
        byte[] data = new byte[n];
        in.getBytes(in.readerIndex(), data);
        Recorder.packet(2, data);
    }

    /** A packet received by the singleplayer connection: encoded to be recorded. */
    public static void memoryPacket(NetworkManager nm, Packet packet) {
        if (nm != recorded || !nm.isLocalChannel() || !Recorder.isRecording()) return;
        ByteBuf buf = Unpooled.buffer(256);
        try {
            Integer id = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, packet);
            if (id == null) return;
            PacketBuffer pb = new PacketBuffer(buf);
            pb.writeVarIntToBuffer(id);
            packet.writePacketData(pb);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            Recorder.packet(2, data);
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

    public static void replayError(Throwable t) {
        if (replayErrors++ < 20) RedoLog.error("Replay packet error", t);
    }
}
