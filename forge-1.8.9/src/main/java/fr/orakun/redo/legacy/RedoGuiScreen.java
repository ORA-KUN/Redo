package fr.orakun.redo.legacy;

import fr.orakun.redo.core.ui.Keys;
import fr.orakun.redo.core.ui.UiScale;
import fr.orakun.redo.core.ui.View;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** The game screen that shows a Redo view (editor or recordings list) and forwards input to it. */
public final class RedoGuiScreen extends GuiScreen {
    public final View view;
    private final Runnable onEscape;
    private long lastFrame = System.nanoTime();
    private int pressed = -1;
    private double lastX, lastY;

    public RedoGuiScreen(View view, Runnable onEscape) {
        this.view = view;
        this.onEscape = onEscape;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        view.closed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Screen pixels per Redo interface pixel (see {@link UiScale}). */
    private int pixels() {
        return UiScale.pixels(mc.displayWidth, mc.displayHeight);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        double dt = (now - lastFrame) / 1000000.0;
        lastFrame = now;
        // Redo screens have their own size, whatever the game's interface size
        int px = pixels();
        float k = px / (float) new ScaledResolution(mc).getScaleFactor();
        int mx = Mouse.getX() / px;
        int my = (mc.displayHeight - Mouse.getY() - 1) / px;
        GlStateManager.pushMatrix();
        GlStateManager.scale(k, k, 1.0F);
        try {
            view.render(new LegacyGfx(px), mx, my, dt);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int px = pixels();
        double x = Mouse.getEventX() / (double) px;
        double y = (mc.displayHeight - Mouse.getEventY() - 1) / (double) px;
        int button = Mouse.getEventButton();
        view.setModifiers(LegacyClient.modifiers());
        if (button >= 0) {
            if (Mouse.getEventButtonState()) {
                view.mouseDown(x, y, button);
                pressed = button;
                lastX = x;
                lastY = y;
            } else {
                view.mouseUp(x, y, button);
                if (button == pressed) pressed = -1;
            }
        } else if (pressed >= 0 && (x != lastX || y != lastY)) {
            view.mouseDrag(x, y, pressed, x - lastX, y - lastY);
            lastX = x;
            lastY = y;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) view.scroll(x, y, wheel > 0 ? 1 : -1);
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        int k = Keyboard.getEventKey();
        char c = Keyboard.getEventCharacter();
        if (!Keyboard.getEventKeyState() && !(k == 0 && c >= 32)) return;
        boolean used = false;
        if (k != 0) used = view.key(Keys.fromLwjgl2(k), LegacyClient.modifiers());
        if (c >= 32 && c != 127) used |= view.chr(c);
        if (!used && k == Keyboard.KEY_ESCAPE && onEscape != null) onEscape.run();
    }
}
