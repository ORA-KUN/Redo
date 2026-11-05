package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a replay, Redo decides how many ticks run and where frames sit between them. */
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @Inject(method = "advanceTime", at = @At("RETURN"), cancellable = true)
    private void redo$ticks(long nowMs, boolean advanceGameTime, CallbackInfoReturnable<Integer> cir) {
        int ticks = RedoClient.replayTicks(nowMs);
        if (ticks >= 0) cir.setReturnValue(ticks);
    }

    @Inject(method = "getGameTimeDeltaPartialTick", at = @At("RETURN"), cancellable = true)
    private void redo$partial(boolean ignoreFrozenGame, CallbackInfoReturnable<Float> cir) {
        if (RedoClient.replayActive()) cir.setReturnValue(RedoClient.partial(cir.getReturnValueF()));
    }
}
