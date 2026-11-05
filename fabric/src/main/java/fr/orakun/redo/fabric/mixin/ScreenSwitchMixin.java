package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.2 {
/*import net.minecraft.client.gui.Gui;
*///?} else {
import net.minecraft.client.Minecraft;
//?}

/** Keeps the replay editor on screen while the replay world is rebuilt for a jump back. */
//? if >=26.2 {
/*@Mixin(Gui.class)
*///?} else {
@Mixin(Minecraft.class)
//?}
public abstract class ScreenSwitchMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void redo$keepEditor(Screen screen, CallbackInfo ci) {
        if (RedoClient.keepEditor(screen)) ci.cancel();
    }
}
