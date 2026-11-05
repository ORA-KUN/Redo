package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.core.record.Recorder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A "Enregistrer / Arrêter" button in the top right corner of the pause menu. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void redo$recordButton(CallbackInfo ci) {
        // no button when the menu is hidden (F3 + Esc) or when nothing can be recorded
        if (children().isEmpty() || !Recorder.canRecord()) return;
        addRenderableWidget(Button.builder(Component.literal(Recorder.buttonLabel()), b -> {
            boolean starting = !Recorder.isRecording();
            Recorder.toggleTake();
            // back to the game when a recording starts; when it stops, stay (to leave the server, say)
            if (starting) onClose();
            else b.setMessage(Component.literal(Recorder.buttonLabel()));
        }).bounds(width - 136, 6, 130, 20).build());
    }
}
