package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.core.record.Recorder;
import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block changes sent by the server, to spot building and digging. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void redo$block(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (!Recorder.isRecording()) return;
        BlockState old = ((ClientLevel) (Object) this).getBlockState(pos);
        RedoClient.blockChanged(pos, old.isAir(), state.isAir());
    }
}
