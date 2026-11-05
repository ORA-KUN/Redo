package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.client.LoadingScreenRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No dirt loading screen over the editor while the replay world is rebuilt for a jump back. */
@Mixin(LoadingScreenRenderer.class)
public abstract class LoadingScreenRendererMixin {
    @Inject(method = "setLoadingProgress", at = @At("HEAD"), cancellable = true)
    private void redo$keepEditor(int progress, CallbackInfo ci) {
        if (LegacyClient.replayActive() && LegacyClient.editorShown()) ci.cancel();
    }
}
