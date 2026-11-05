package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a replay, Redo decides how many ticks run and where frames sit between them. */
@Mixin(Timer.class)
public abstract class TimerMixin {
    @Inject(method = "updateTimer", at = @At("RETURN"))
    private void redo$replayClock(CallbackInfo ci) {
        LegacyClient.timerUpdated((Timer) (Object) this);
    }
}
