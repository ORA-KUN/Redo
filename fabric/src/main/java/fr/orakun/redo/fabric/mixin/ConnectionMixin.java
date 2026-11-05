package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import fr.orakun.redo.fabric.RedoConnection;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements RedoConnection {
    @Unique private volatile boolean redo$replay;
    @Unique private volatile ProtocolInfo<?> redo$inbound;

    @Override
    public boolean redo$isReplay() {
        return redo$replay;
    }

    @Override
    public void redo$setReplay(boolean replay) {
        redo$replay = replay;
    }

    @Override
    public ProtocolInfo<?> redo$inbound() {
        return redo$inbound;
    }

    @Inject(method = "setupInboundProtocol", at = @At("HEAD"))
    private void redo$protocol(ProtocolInfo<?> info, PacketListener listener, CallbackInfo ci) {
        redo$inbound = info;
    }

    /** Singleplayer packets never go through the decoder: they are encoded here to be recorded. */
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void redo$memoryPacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        RedoClient.memoryPacket((Connection) (Object) this, packet);
    }

    /** A bad packet must not end a replay: it is logged and skipped. */
    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void redo$replayError(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
        if (redo$replay) {
            RedoClient.replayError(cause);
            ci.cancel();
        }
    }

    /** A recorded kick or disconnect must not close the replay: Redo closes it itself. */
    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"), cancellable = true)
    private void redo$keepReplay(DisconnectionDetails details, CallbackInfo ci) {
        if (redo$replay) ci.cancel();
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"), cancellable = true)
    private void redo$disconnected(CallbackInfo ci) {
        if (redo$replay) {
            ci.cancel();
            return;
        }
        RedoClient.connectionClosed((Connection) (Object) this);
    }
}
