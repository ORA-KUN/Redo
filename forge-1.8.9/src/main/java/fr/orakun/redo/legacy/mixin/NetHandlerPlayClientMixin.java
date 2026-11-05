package fr.orakun.redo.legacy.mixin;

import com.mojang.authlib.GameProfile;
import fr.orakun.redo.legacy.LegacyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client enters the play state: a recording begins (before the first world packet). */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void redo$start(Minecraft mc, GuiScreen screen, NetworkManager manager, GameProfile profile, CallbackInfo ci) {
        LegacyClient.connectionStarting(manager);
    }
}
