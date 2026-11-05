package fr.orakun.redo.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void redo$fov(Camera camera, float partial, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (!useFovSetting) return;
        CameraState c = RedoClient.cameraOverride();
        if (c != null) cir.setReturnValue((float) c.fov);
        RedoClient.fovComputed(cir.getReturnValueF());
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void redo$bobHurt(PoseStack pose, float partial, CallbackInfo ci) {
        if (RedoClient.cameraOverride() != null) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void redo$bobView(PoseStack pose, float partial, CallbackInfo ci) {
        if (RedoClient.cameraOverride() != null) ci.cancel();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void redo$levelRendered(DeltaTracker deltaTracker, CallbackInfo ci) {
        RedoClient.levelRendered();
    }
}
