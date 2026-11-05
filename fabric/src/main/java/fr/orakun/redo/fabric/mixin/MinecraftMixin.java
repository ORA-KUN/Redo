package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void redo$frameStart(boolean renderLevel, CallbackInfo ci) {
        RedoClient.frameStart();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void redo$tickStart(CallbackInfo ci) {
        RedoClient.tickStart();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void redo$tickEnd(CallbackInfo ci) {
        RedoClient.tickEnd();
    }

    @Inject(method = {
            "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
            "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V"
    }, at = @At("HEAD"))
    private void redo$disconnect(CallbackInfo ci) {
        RedoClient.disconnecting();
    }
}
