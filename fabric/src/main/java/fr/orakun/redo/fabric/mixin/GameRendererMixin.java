package fr.orakun.redo.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?} else {
import fr.orakun.redo.core.camera.CameraState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
//? if <26.3 {
import net.minecraft.client.DeltaTracker;
//?}

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    //? if >=26.1 {
    /*@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void redo$bobHurt(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        if (RedoClient.cameraOverride() != null) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void redo$bobView(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        if (RedoClient.cameraOverride() != null) ci.cancel();
    }
    *///?} else {
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
    //?}

    //? if >=26.3 {
    /*@Inject(method = "renderLevel", at = @At("TAIL"))
    private void redo$levelRendered(CallbackInfo ci) {
        RedoClient.levelRendered();
    }
    *///?} else {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void redo$levelRendered(DeltaTracker deltaTracker, CallbackInfo ci) {
        RedoClient.levelRendered();
    }
    //?}
}
