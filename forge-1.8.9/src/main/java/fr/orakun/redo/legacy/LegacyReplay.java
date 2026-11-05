package fr.orakun.redo.legacy;

import com.mojang.authlib.GameProfile;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.director.WorldQuery;
import fr.orakun.redo.core.format.PacketRecord;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.replay.AbstractReplay;
import fr.orakun.redo.core.replay.ReplayController;
import fr.orakun.redo.core.track.EventTrack;
import fr.orakun.redo.core.track.SelfSample;
import fr.orakun.redo.core.ui.MessageView;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.MessageDeserializer;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

import java.io.IOException;
import java.util.UUID;

/**
 * A replay on 1.8.9: the recorded packets go through a real client connection whose channel lives
 * in memory, so the game rebuilds the world exactly as it was. What the client sends is dropped.
 */
public final class LegacyReplay extends AbstractReplay {
    /** Drops everything the client sends. */
    private static final class Sink extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet) {
                promise.setSuccess();
                return;
            }
            super.write(ctx, msg, promise);
        }
    }

    private NetworkManager manager;
    private EmbeddedChannel channel;
    private final LegacyWorldQuery world = new LegacyWorldQuery();
    volatile float livePartial;
    CameraState lastCamera;
    private boolean hideGuiBefore;
    private int thirdPersonBefore;
    private EventTrack track;
    private boolean trackFailed;

    public LegacyReplay(RedoArchive archive) {
        super(archive);
    }

    @Override
    public void start() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        hideGuiBefore = mc.gameSettings.hideGUI;
        thirdPersonBefore = mc.gameSettings.thirdPersonView;
        mc.gameSettings.hideGUI = true;
        super.start();
    }

    @Override
    protected void openWorld() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        manager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        ((RedoNetworkManager) manager).redo$setReplay(true);
        // netty 4.0 needs the handlers at construction: decoder, connection, then the sink
        channel = new EmbeddedChannel(new MessageDeserializer(EnumPacketDirection.CLIENTBOUND), manager, new Sink());
        manager.setConnectionState(EnumConnectionState.PLAY);
        channel.attr(NetworkDispatcher.FML_DISPATCHER).set(new NetworkDispatcher(manager));

        ServerData server = new ServerData("Redo", "redo.replay", false);
        server.setResourceMode(ServerData.ServerResourceMode.ENABLED);
        mc.setServerData(server);

        UUID id;
        try {
            id = UUID.fromString(archive.meta.selfUuid);
        } catch (IllegalArgumentException e) {
            id = UUID.nameUUIDFromBytes(("Redo:" + archive.meta.selfName).getBytes("UTF-8"));
        }
        String name = archive.meta.selfName == null || archive.meta.selfName.isEmpty() ? "Redo" : archive.meta.selfName;
        NetHandlerPlayClient handler = new NetHandlerPlayClient(mc, new GuiMainMenu(), manager, new GameProfile(id, name));
        manager.setNetHandler(handler);
    }

    @Override
    protected void closeWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        LegacyClient.busy++;
        try {
            // the editor stays open while the world is rebuilt (see LegacyClient.keepEditor)
            boolean editor = LegacyClient.editorShown();
            mc.loadWorld(null);
            if (!editor) mc.displayGuiScreen(new RedoGuiScreen(new MessageView("Redo", "Retour en arrière…", true, null), null));
        } finally {
            LegacyClient.busy--;
        }
        closeChannel();
    }

    private void closeChannel() {
        if (channel != null) channel.close();
        channel = null;
        manager = null;
    }

    @Override
    protected void handle(PacketRecord p) throws Exception {
        if (channel == null || !channel.isOpen()) return;
        channel.writeInbound(Unpooled.wrappedBuffer(p.data));
    }

    @Override
    protected void applyTiming(boolean frozen, double speed) {
        // the timer mixin asks LegacyClient.timerUpdated, which reads isFrozen() and speed()
    }

    @Override
    protected float livePartialTick() {
        return livePartial;
    }

    @Override
    public CameraState currentCamera() {
        return lastCamera;
    }

    @Override
    public WorldQuery world() {
        return world;
    }

    private void restoreSettings() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.hideGUI = hideGuiBefore;
        mc.gameSettings.thirdPersonView = thirdPersonBefore;
    }

    /** Leaves the replay and comes back to the recordings list. */
    @Override
    public void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        ReplayController c = ReplayController.current();
        if (c != null && c.replay == this) c.close();
        restoreSettings();
        LegacyClient.busy++;
        try {
            mc.loadWorld(null);
            mc.setServerData(null);
            mc.displayGuiScreen(LegacyClient.recordingsScreen(new GuiMainMenu()));
        } finally {
            LegacyClient.busy--;
        }
        closeChannel();
    }

    /** The game is leaving the replay world by itself: just let go. */
    void abandon() {
        ReplayController c = ReplayController.current();
        if (c != null && c.replay == this) c.close();
        restoreSettings();
        closeChannel();
    }

    boolean worldReady() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null && mc.thePlayer != null;
    }

    /** The recording player's state at replay time t, or null. */
    SelfSample selfAt(long t) {
        if (track == null && !trackFailed) {
            try {
                track = archive.track();
            } catch (IOException e) {
                trackFailed = true;
                RedoLog.error("Could not read the recording track", e);
            }
        }
        return track == null ? null : track.selfAt(t);
    }
}
