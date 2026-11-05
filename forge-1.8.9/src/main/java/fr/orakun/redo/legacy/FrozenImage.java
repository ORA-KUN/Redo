package fr.orakun.redo.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.shader.Framebuffer;
import fr.orakun.redo.legacy.mixin.RenderGlobalAccessor;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The last image of the replay world, kept on screen while a jump rebuilds the world: the editor
 * shows it until the new world is drawn in full, instead of the world filling in before the eyes.
 */
final class FrozenImage {
    private static Framebuffer image;
    private static boolean unsupported, valid;

    private FrozenImage() {
    }

    /** True once a world image has been copied. */
    static boolean available() {
        return valid && image != null;
    }

    /** Copies the world drawn in the game's frame buffer (once per frame, a quick copy on the card). */
    static void capture() {
        if (unsupported) return;
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        if (!OpenGlHelper.isFramebufferEnabled() || main == null) return;
        ContextCapabilities caps = GLContext.getCapabilities();
        int kind = caps.OpenGL30 ? 1 : caps.GL_ARB_framebuffer_object ? 2 : caps.GL_EXT_framebuffer_blit ? 3 : 0;
        if (kind == 0) {
            unsupported = true;
            return;
        }
        int w = main.framebufferWidth, h = main.framebufferHeight;
        try {
            if (image == null || image.framebufferWidth != w || image.framebufferHeight != h) {
                if (image != null) image.deleteFramebuffer();
                valid = false;
                image = new Framebuffer(w, h, false);
            }
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (kind == 1) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.framebufferObject);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, image.framebufferObject);
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            } else if (kind == 2) {
                ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, main.framebufferObject);
                ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, image.framebufferObject);
                ARBFramebufferObject.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            } else {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, main.framebufferObject);
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, image.framebufferObject);
                EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            }
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            valid = true;
        } catch (RuntimeException e) {
            unsupported = true;
            valid = false;
        } finally {
            // creating the copy binds the window's frame buffer: draw in the game's one again
            main.bindFramebuffer(false);
        }
    }

    /** Draws the kept image over the whole screen (in the game's interface coordinates). */
    static void draw() {
        Framebuffer img = image;
        if (img == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        double w = sr.getScaledWidth_double(), h = sr.getScaledHeight_double();
        float u = img.framebufferWidth / (float) img.framebufferTextureWidth;
        float v = img.framebufferHeight / (float) img.framebufferTextureHeight;
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        img.bindFramebufferTexture();
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        // the frame buffer's rows go upwards
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, h, 0).tex(0, 0).endVertex();
        wr.pos(w, h, 0).tex(u, 0).endVertex();
        wr.pos(w, 0, 0).tex(u, v).endVertex();
        wr.pos(0, 0, 0).tex(0, v).endVertex();
        t.draw();
        img.unbindFramebufferTexture();
        GlStateManager.depthMask(true);
        if (alpha) GlStateManager.enableAlpha();
        if (blend) GlStateManager.enableBlend();
    }

    /** True once the terrain in view is built: nothing left that would pop in. */
    static boolean terrainReady() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal == null) return true;
        RenderGlobalAccessor rg = (RenderGlobalAccessor) mc.renderGlobal;
        Set<RenderChunk> pending = rg.redo$chunksToUpdate();
        if (pending != null && !pending.isEmpty()) return false;
        ChunkRenderDispatcher d = rg.redo$renderDispatcher();
        if (d == null) return true;
        // "pC: 012, pU: 3, aB: 1": chunks waiting to be built, then to be sent to the card
        Matcher m = QUEUES.matcher(d.getDebugInfo());
        return !m.find() || (Integer.parseInt(m.group(1)) == 0 && Integer.parseInt(m.group(2)) == 0);
    }

    private static final Pattern QUEUES = Pattern.compile("pC: *(\\d+), *pU: *(\\d+)");
}
