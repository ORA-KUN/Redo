package fr.orakun.redo.legacy;

import fr.orakun.redo.core.editor.EditorHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.Locale;

/** What the editor needs from the game on 1.8.9. */
final class LegacyEditorHost implements EditorHost {
    @Override
    public String clipboard() {
        return GuiScreen.getClipboardString();
    }

    @Override
    public void openFolder(File folder) {
        LegacyEditorHost.open(folder);
    }

    static void open(File folder) {
        folder.mkdirs();
        String path = folder.getAbsolutePath();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) new ProcessBuilder("explorer", path).start();
            else if (os.contains("mac")) new ProcessBuilder("open", path).start();
            else new ProcessBuilder("xdg-open", path).start();
        } catch (Exception e) {
            RedoLegacy.LOG.warn("Could not open " + path + ": " + e);
        }
    }

    static boolean down(KeyBinding k) {
        int code = k.getKeyCode();
        if (code > 0 && code < 256) return Keyboard.isKeyDown(code);
        if (code < 0 && code + 100 >= 0) return Mouse.isButtonDown(code + 100);
        return false;
    }

    @Override
    public void movement(double[] out) {
        GameSettings s = Minecraft.getMinecraft().gameSettings;
        out[0] = (down(s.keyBindForward) ? 1 : 0) - (down(s.keyBindBack) ? 1 : 0);
        out[1] = (down(s.keyBindRight) ? 1 : 0) - (down(s.keyBindLeft) ? 1 : 0);
        out[2] = (down(s.keyBindJump) ? 1 : 0) - (down(s.keyBindSneak) ? 1 : 0);
        out[3] = down(s.keyBindSprint) ? 1 : 0;
    }

    @Override
    public void exitReplay() {
        LegacyReplay r = LegacyClient.replay();
        if (r != null) r.exit();
    }

    @Override
    public int framebufferWidth() {
        return Minecraft.getMinecraft().displayWidth;
    }

    @Override
    public int framebufferHeight() {
        return Minecraft.getMinecraft().displayHeight;
    }

    @Override
    public boolean framesBottomUp() {
        // glReadPixels gives the bottom row first
        return true;
    }
}
