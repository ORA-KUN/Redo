package fr.orakun.redo.legacy;

import fr.orakun.redo.legacy.mixin.RenderGlobalAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The last image of the replay world, kept on screen while a jump back rebuilds the world: the
 * editor shows it until the new world is drawn in full, instead of the world filling in before
 * the eyes.
 *
 * Plain OpenGL only, and every change is undone before returning: the game keeps its own record
 * of the OpenGL state (GlStateManager) and skips calls it thinks useless, so a change it does not
 * know about would leak into the world and the interface (black textures, wrong colours).
 */
final class FrozenImage {
    private static int texture, width, height;
    private static boolean unsupported, valid;

    private FrozenImage() {
    }

    /** True once a world image has been copied. */
    static boolean available() {
        return valid && texture != 0;
    }

    /**
     * Copies the world just drawn (called after the world, before the interface, while the game's
     * frame buffer is the one being drawn into): a copy on the card, from the frame buffer into a
     * texture of the same size.
     */
    static void capture() {
        if (unsupported) return;
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth, h = mc.displayHeight;
        if (w <= 0 || h <= 0) return;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (w != width || h != height) {
                valid = false;
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                width = w;
                height = h;
            }
            for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
                // an error left by something else: not the copy's
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            valid = GL11.glGetError() == GL11.GL_NO_ERROR;
        } catch (RuntimeException e) {
            unsupported = true;
            valid = false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    /** Draws the kept image over the whole window, under the editor's panels. */
    static void draw() {
        if (!available()) return;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 1, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            // the texture as it is, whatever colour or light the game left set
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
            GL11.glColor4f(1, 1, 1, 1);
            // the copy's rows go upwards: its last row is the top of the window
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(0, 1);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(1, 1);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(1, 0);
            GL11.glEnd();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
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
