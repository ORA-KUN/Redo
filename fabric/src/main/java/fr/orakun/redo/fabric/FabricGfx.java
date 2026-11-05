package fr.orakun.redo.fabric;

import fr.orakun.redo.core.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Redo's drawing interface on top of the game's GUI renderer. */
public final class FabricGfx implements Gfx {
    private final GuiGraphics g;
    private final Font font;

    public FabricGfx(GuiGraphics g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    @Override
    public int width() {
        return g.guiWidth();
    }

    @Override
    public int height() {
        return g.guiHeight();
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        if (s == null || s.isEmpty()) return;
        if ((argb >>> 24) == 0) argb |= 0xFF000000;
        g.drawString(font, s, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String s) {
        return s == null ? 0 : font.width(s);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    @Override
    public void popClip() {
        g.disableScissor();
    }
}
