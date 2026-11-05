package fr.orakun.redo.fabric;

import com.mojang.authlib.GameProfile;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.director.WorldQuery;
import fr.orakun.redo.core.format.PacketRecord;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.replay.AbstractReplay;
import fr.orakun.redo.core.replay.ReplayController;
import fr.orakun.redo.core.track.EventTrack;
import fr.orakun.redo.core.track.SelfSample;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.ui.MessageView;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;

import java.io.IOException;
import java.util.UUID;

/**
 * A replay on Minecraft 1.21.11: the recorded packets are fed into a real client connection whose
 * channel lives in memory, so the game builds the world exactly as it did during the game.
 * Everything the client tries to send back is dropped.
 */
public final class FabricReplay extends AbstractReplay {
    /** Drops what the client sends; lets the pipeline's own reconfiguration tasks through. */
    private static final class Sink extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet || msg instanceof UnconfiguredPipelineHandler.OutboundConfigurationTask) {
                promise.setSuccess();
                return;
            }
            super.write(ctx, msg, promise);
        }
    }

    private Connection connection;
    private EmbeddedChannel channel;
    private final FabricWorldQuery world = new FabricWorldQuery();
    volatile float livePartial;
    CameraState lastCamera;
    private boolean hideGuiBefore;
    private int fed;
    private EventTrack track;
    private boolean trackFailed;

    public FabricReplay(RedoArchive archive) {
        super(archive);
    }

    public Connection connection() {
        return connection;
    }

    @Override
    public void start() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        hideGuiBefore = mc.options.hideGui;
        mc.options.hideGui = true;
        super.start();
    }

    @Override
    protected void openWorld() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        connection = new Connection(PacketFlow.CLIENTBOUND);
        ((RedoConnection) connection).redo$setReplay(true);
        channel = new EmbeddedChannel();
        ChannelPipeline p = channel.pipeline();
        Connection.configureSerialization(p, PacketFlow.CLIENTBOUND, true, null);
        connection.configurePacketHandler(p);
        p.addLast("redo_sink", new Sink());
        p.fireChannelActive();

        ServerData server = new ServerData("Redo", "redo.replay", ServerData.Type.OTHER);
        server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ClientHandshakePacketListenerImpl login = new ClientHandshakePacketListenerImpl(connection, mc, server,
                new TitleScreen(), false, null, c -> { }, new LevelLoadTracker(), null);
        connection.setupInboundProtocol(LoginProtocols.CLIENTBOUND, login);
        UUID id;
        try {
            id = UUID.fromString(archive.meta.selfUuid);
        } catch (IllegalArgumentException e) {
            id = UUID.nameUUIDFromBytes(("Redo:" + archive.meta.selfName).getBytes("UTF-8"));
        }
        String name = archive.meta.selfName == null || archive.meta.selfName.isEmpty() ? "Redo" : archive.meta.selfName;
        login.handleLoginFinished(new ClientboundLoginFinishedPacket(new GameProfile(id, name)));
        fed = 0;
    }

    @Override
    protected void closeWorld() {
        Minecraft mc = Minecraft.getInstance();
        RedoClient.busy++;
        try {
            // keeps the server resource packs: the world is rebuilt right away
            mc.disconnect(new RedoScreen(new MessageView("Redo", "Retour en arrière…", true, null), null), true);
        } finally {
            RedoClient.busy--;
        }
        closeChannel();
    }

    private void closeChannel() {
        if (channel != null) channel.close();
        channel = null;
        connection = null;
    }

    @Override
    protected void handle(PacketRecord p) throws Exception {
        if (channel == null || !channel.isOpen()) return;
        channel.writeInbound(Unpooled.wrappedBuffer(p.data));
        if (++fed % 512 == 0) channel.releaseOutbound();
    }

    @Override
    protected void applyTiming(boolean frozen, double speed) {
        // the tick timer mixin asks RedoClient.replayTicks, which reads isFrozen() and speed()
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

    /** Leaves the replay and comes back to the recordings list. */
    @Override
    public void exit() {
        Minecraft mc = Minecraft.getInstance();
        ReplayController c = ReplayController.current();
        if (c != null && c.replay == this) c.close();
        mc.options.hideGui = hideGuiBefore;
        RedoClient.busy++;
        try {
            mc.disconnect(RedoClient.recordingsScreen(new TitleScreen()), false);
        } finally {
            RedoClient.busy--;
        }
        closeChannel();
    }

    /** The game is leaving the replay world by itself (window closed...): just let go. */
    void abandon() {
        ReplayController c = ReplayController.current();
        if (c != null && c.replay == this) c.close();
        Minecraft.getInstance().options.hideGui = hideGuiBefore;
        closeChannel();
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

    boolean worldReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    static Component title() {
        return Component.literal("Redo");
    }
}
