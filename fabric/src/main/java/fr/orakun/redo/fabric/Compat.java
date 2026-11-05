package fr.orakun.redo.fabric;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.util.UUID;

/**
 * The Minecraft calls that differ between the supported versions, in one place (Stonecutter
 * comments pick the right line for each version). Everything else in the adapter is shared.
 */
final class Compat {
    private Compat() {
    }

    static Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    static void setScreen(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    static RenderTarget mainTarget() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        *///?} else {
        return Minecraft.getInstance().getMainRenderTarget();
        //?}
    }

    /** Size of the game's frame in pixels. */
    static int frameWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    static int frameHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    static boolean hideGui() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.hud.isHidden();
        *///?} else {
        return Minecraft.getInstance().options.hideGui;
        //?}
    }

    static void setHideGui(boolean hide) {
        //? if >=26.2 {
        /*if (Minecraft.getInstance().gui.hud.isHidden() != hide) Minecraft.getInstance().gui.hud.toggle();
        *///?} else {
        Minecraft.getInstance().options.hideGui = hide;
        //?}
    }

    /** Whether a key (GLFW code) is held down. */
    static boolean keyDown(int key) {
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(key);
        *///?} else {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        //?}
    }

    static boolean swinging(LivingEntity e) {
        //? if >=26.3 {
        /*return e.isSwinging();
        *///?} else {
        return e.swinging;
        //?}
    }

    static void swing(LivingEntity e) {
        //? if >=26.3 {
        /*// 26.3 asks for the item's swing animation; the replayed arm swing is left out there
        *///?} else {
        e.swing(InteractionHand.MAIN_HAND);
        //?}
    }

    static boolean blocksMotion(BlockState s) {
        //? if >=26.3 {
        /*return s.isSolid();
        *///?} else {
        return s.blocksMotion();
        //?}
    }

    static void openFile(File f) {
        //? if >=26.3 {
        /*String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[] {"explorer", f.getAbsolutePath()}
                : os.contains("mac") ? new String[] {"open", f.getAbsolutePath()}
                : new String[] {"xdg-open", f.getAbsolutePath()};
        try {
            new ProcessBuilder(cmd).start();
        } catch (java.io.IOException e) {
            fr.orakun.redo.core.RedoLog.warn("Could not open " + f + ": " + e);
        }
        *///?} else {
        Util.getPlatform().openFile(f);
        //?}
    }

    static ClientboundLoginFinishedPacket loginFinished(GameProfile profile) {
        //? if >=26.2 {
        /*return new ClientboundLoginFinishedPacket(profile, UUID.randomUUID());
        *///?} else {
        return new ClientboundLoginFinishedPacket(profile);
        //?}
    }
}
