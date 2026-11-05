package fr.orakun.redo.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.orakun.redo.core.editor.EditorHost;
import fr.orakun.redo.fabric.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Util;

import java.io.File;

/** What the editor needs from the game on 1.21.11. */
final class FabricEditorHost implements EditorHost {
    @Override
    public String clipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    @Override
    public void openFolder(File folder) {
        folder.mkdirs();
        Util.getPlatform().openFile(folder);
    }

    static boolean down(KeyMapping k) {
        InputConstants.Key key = ((KeyMappingAccessor) k).redo$key();
        if (key == null || key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    @Override
    public void movement(double[] out) {
        Options o = Minecraft.getInstance().options;
        out[0] = (down(o.keyUp) ? 1 : 0) - (down(o.keyDown) ? 1 : 0);
        out[1] = (down(o.keyRight) ? 1 : 0) - (down(o.keyLeft) ? 1 : 0);
        out[2] = (down(o.keyJump) ? 1 : 0) - (down(o.keyShift) ? 1 : 0);
        out[3] = down(o.keySprint) ? 1 : 0;
    }

    @Override
    public void exitReplay() {
        FabricReplay r = RedoClient.replay();
        if (r != null) r.exit();
    }

    @Override
    public int framebufferWidth() {
        RenderTarget t = Minecraft.getInstance().getMainRenderTarget();
        return t.width;
    }

    @Override
    public int framebufferHeight() {
        RenderTarget t = Minecraft.getInstance().getMainRenderTarget();
        return t.height;
    }

    @Override
    public boolean framesBottomUp() {
        // Screenshot.takeScreenshot already gives top-down rows
        return false;
    }
}
