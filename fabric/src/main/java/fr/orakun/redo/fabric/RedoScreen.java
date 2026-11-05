package fr.orakun.redo.fabric;

import fr.orakun.redo.core.ui.View;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The game screen that shows a Redo view (editor or recordings list) and forwards input to it. */
public final class RedoScreen extends Screen {
    public final View view;
    private final Runnable onEscape;
    private long lastFrame = System.nanoTime();
    private double lastX = Double.NaN, lastY = Double.NaN;

    public RedoScreen(View view, Runnable onEscape) {
        super(Component.literal("Redo"));
        this.view = view;
        this.onEscape = onEscape;
    }

    @Override
    //? if >=26.1 {
    /*public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    *///?} else {
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    //?}
        long now = System.nanoTime();
        double dt = (now - lastFrame) / 1_000_000.0;
        lastFrame = now;
        view.render(new FabricGfx(g), mouseX, mouseY, dt);
    }

    @Override
    //? if >=26.1 {
    /*public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    *///?} else {
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    //?}
        // the replay world stays visible behind the editor; the list paints its own background
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        view.closed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        view.setModifiers(e.modifiers());
        lastX = e.x();
        lastY = e.y();
        return view.mouseDown(e.x(), e.y(), e.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        view.setModifiers(e.modifiers());
        view.mouseUp(e.x(), e.y(), e.button());
        lastX = lastY = Double.NaN;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        view.setModifiers(e.modifiers());
        view.mouseDrag(e.x(), e.y(), e.button(), dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        view.setModifiers(RedoClient.modifiers());
        return view.scroll(x, y, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean used = view.key(e.key(), e.modifiers());
        if (!used && e.key() == 256 && onEscape != null) {
            onEscape.run();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (!Character.isBmpCodePoint(cp)) return false;
        return view.chr((char) cp);
    }
}
