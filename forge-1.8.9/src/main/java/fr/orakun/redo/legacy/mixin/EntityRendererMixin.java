package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void redo$fov(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        if (!useFOVSetting) return;
        Float f = LegacyClient.fovOverride();
        if (f != null) cir.setReturnValue(f);
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void redo$worldRendered(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        LegacyClient.worldRendered();
    }

    /** Particles and name tags face the editor's camera, not the recorded player. */
    @ModifyArg(method = "renderWorldPass", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/player/EntityPlayer;Z)V"), index = 0)
    private EntityPlayer redo$viewer(EntityPlayer player) {
        EntityPlayer cam = LegacyClient.cameraEntity();
        return cam != null ? cam : player;
    }
}
