package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Copies every packet the client receives from a server, as raw bytes, before decoding it. */
@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {
    @Shadow @Final private ProtocolInfo<?> protocolInfo;

    @Inject(method = "decode", at = @At("HEAD"))
    private void redo$record(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        RedoClient.networkPacket(protocolInfo, in);
    }
}
