package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void redo$frameStart(CallbackInfo ci) {
        LegacyClient.frameStart();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void redo$loadWorld(WorldClient world, String message, CallbackInfo ci) {
        if (world == null) LegacyClient.disconnecting();
    }
}
