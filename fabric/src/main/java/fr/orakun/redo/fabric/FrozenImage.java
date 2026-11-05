package fr.orakun.redo.fabric;

import fr.orakun.redo.core.RedoLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * The last image of the replay world, kept on screen while a jump rebuilds the world: the editor
 * shows it until the new world is drawn in full, instead of the world filling in before the eyes.
 * The image is read like a screenshot when the jump starts and arrives a frame or two later; the
 * jump waits for it (see AbstractReplay.frame).
 */
final class FrozenImage {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("redo", "frozen_world");
    /** The game's world renderer, kept when it is created (its field differs between versions). */
    static volatile LevelRenderer levelRenderer;
    private static boolean pending, ready;
    private static int width, height, asked;

    private FrozenImage() {
    }

    /** Starts copying the world now drawn. False if it cannot be read. */
    static boolean request() {
        if (pending) return true;
        pending = true;
        ready = false;
        final int ticket = ++asked;
        try {
            Screenshot.takeScreenshot(Compat.mainTarget(), image -> {
                pending = false;
                if (ticket != asked) {
                    image.close();
                    return;
                }
                try {
                    Minecraft mc = Minecraft.getInstance();
                    // nothing draws the previous image any more: it is only drawn while ready
                    mc.getTextureManager().release(ID);
                    mc.getTextureManager().register(ID, new DynamicTexture(() -> "Redo frozen world", image));
                    width = image.getWidth();
                    height = image.getHeight();
                    ready = true;
                } catch (RuntimeException e) {
                    image.close();
                    RedoLog.error("Could not keep the image of the replay world", e);
                }
            });
            return true;
        } catch (RuntimeException e) {
            pending = false;
            RedoLog.error("Could not read the image of the replay world", e);
            return false;
        }
    }

    static boolean ready() {
        return ready;
    }

    /** Draws the kept image over the whole screen, under the editor's panels. */
    //? if >=26.1 {
    /*static void draw(GuiGraphicsExtractor g) {
    *///?} else {
    static void draw(GuiGraphics g) {
    //?}
        if (!ready || width <= 0 || height <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0f, 0f, g.guiWidth(), g.guiHeight(), width, height, width, height);
    }

    /** True if no terrain is waiting to be built (or the world renderer is unknown). */
    static boolean terrainQueueEmpty() {
        LevelRenderer lr = levelRenderer;
        return lr == null || lr.hasRenderedAllSections();
    }
}
