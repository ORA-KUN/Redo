package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.legacy.LegacyClient;
import fr.orakun.redo.legacy.RedoNetworkManager;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin implements RedoNetworkManager {
    @Unique private volatile boolean redo$replay;

    @Override
    public boolean redo$isReplay() {
        return redo$replay;
    }

    @Override
    public void redo$setReplay(boolean replay) {
        redo$replay = replay;
    }

    /** Singleplayer packets never go through the decoder: they are encoded here to be recorded. */
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
    private void redo$memoryPacket(ChannelHandlerContext ctx, Packet packet, CallbackInfo ci) {
        LegacyClient.memoryPacket((NetworkManager) (Object) this, packet);
    }

    /** A bad packet must not end a replay: it is logged and skipped. */
    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void redo$replayError(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
        if (redo$replay) {
            LegacyClient.replayError(cause);
            ci.cancel();
        }
    }

    /** A recorded kick must not close the replay: Redo closes its channel itself. */
    @Inject(method = "closeChannel", at = @At("HEAD"), cancellable = true)
    private void redo$keepReplay(IChatComponent message, CallbackInfo ci) {
        if (redo$replay) ci.cancel();
    }

    /** The replay stream is already uncompressed. */
    @Inject(method = "setCompressionTreshold", at = @At("HEAD"), cancellable = true)
    private void redo$noCompression(int threshold, CallbackInfo ci) {
        if (redo$replay) ci.cancel();
    }

    @Inject(method = "checkDisconnected", at = @At("HEAD"), cancellable = true)
    private void redo$disconnected(CallbackInfo ci) {
        if (redo$replay) {
            ci.cancel();
            return;
        }
        NetworkManager self = (NetworkManager) (Object) this;
        if (!self.hasNoChannel() && !self.isChannelOpen()) LegacyClient.connectionClosed(self);
    }
}
