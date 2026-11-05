package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the world renderer, to know when a rebuilt replay world is drawn in full. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void redo$created(CallbackInfo ci) {
        RedoClient.levelRendererCreated((LevelRenderer) (Object) this);
    }
}
