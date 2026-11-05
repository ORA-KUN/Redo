package fr.orakun.redo.legacy.mixin;

import com.mojang.authlib.GameProfile;
import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a replay the local player follows the recorded path instead of the keyboard. */
@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin extends AbstractClientPlayer {
    public EntityPlayerSPMixin(World world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true)
    private void redo$replay(CallbackInfo ci) {
        if (LegacyClient.replaySelf((EntityPlayerSP) (Object) this)) {
            updateArmSwingProgress();
            ci.cancel();
        }
    }
}
