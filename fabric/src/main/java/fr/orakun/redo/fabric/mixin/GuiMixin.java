package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.FabricGfx;
import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.2 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
*///?} else if >=26.1 {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
//?}

/** Draws the recording indicator over the game's interface. */
//? if >=26.2 {
/*@Mixin(Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
public abstract class GuiMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void redo$hud(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        RedoClient.renderHud(new FabricGfx(g));
    }
    *///?} else {
    @Inject(method = "render", at = @At("TAIL"))
    private void redo$hud(GuiGraphics g, DeltaTracker deltaTracker, CallbackInfo ci) {
        RedoClient.renderHud(new FabricGfx(g));
    }
    //?}
}
