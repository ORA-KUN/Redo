package fr.orakun.redo.legacy.mixin;

import fr.orakun.redo.core.record.Recorder;
import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block changes sent by the server, to spot building and digging. */
@Mixin(WorldClient.class)
public abstract class WorldClientMixin {
    @Inject(method = "invalidateRegionAndSetBlock", at = @At("HEAD"))
    private void redo$block(BlockPos pos, IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!Recorder.isRecording()) return;
        IBlockState old = ((WorldClient) (Object) this).getBlockState(pos);
        LegacyClient.blockChanged(pos, old.getBlock().getMaterial() == Material.air, state.getBlock().getMaterial() == Material.air);
    }
}
