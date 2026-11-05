package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.legacy.LegacyClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.util.MessageDeserializer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Copies every packet the client receives from a server, as raw bytes, before decoding it. */
@Mixin(MessageDeserializer.class)
public abstract class MessageDeserializerMixin {
    @Shadow @Final private EnumPacketDirection direction;

    @Inject(method = "decode", at = @At("HEAD"))
    private void redo$record(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        LegacyClient.networkPacket(direction, ctx, in);
    }
}
