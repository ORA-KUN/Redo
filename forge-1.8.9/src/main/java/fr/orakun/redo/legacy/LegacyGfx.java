package fr.orakun.redo.legacy;

import fr.orakun.redo.core.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

/** Redo's drawing interface on top of the 1.8.9 GUI helpers (scaled GUI coordinates). */
public final class LegacyGfx implements Gfx {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final FontRenderer font = mc.fontRendererObj;
    private final int w, h, scale;
    private final Deque<int[]> clips = new ArrayDeque<int[]>();

    public LegacyGfx() {
        ScaledResolution sr = new ScaledResolution(mc);
        w = sr.getScaledWidth();
        h = sr.getScaledHeight();
        scale = sr.getScaleFactor();
    }

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        Gui.drawRect(x0, y0, x1, y1, argb);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        if (s == null || s.isEmpty()) return;
        if ((argb >>> 24) == 0) argb |= 0xFF000000;
        GlStateManager.enableBlend();
        font.drawString(s, (float) x, (float) y, argb, shadow);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public int textWidth(String s) {
        return s == null ? 0 : font.getStringWidth(s);
    }

    @Override
    public int lineHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        int[] top = clips.peek();
        if (top != null) {
            x0 = Math.max(x0, top[0]);
            y0 = Math.max(y0, top[1]);
            x1 = Math.min(x1, top[2]);
            y1 = Math.min(y1, top[3]);
        }
        int[] c = {x0, y0, Math.max(x0, x1), Math.max(y0, y1)};
        clips.push(c);
        apply(c);
    }

    @Override
    public void popClip() {
        if (!clips.isEmpty()) clips.pop();
        int[] top = clips.peek();
        if (top == null) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        else apply(top);
    }

    private void apply(int[] c) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(c[0] * scale, mc.displayHeight - c[3] * scale, (c[2] - c[0]) * scale, (c[3] - c[1]) * scale);
    }
}
