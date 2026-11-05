package fr.orakun.redo.legacy;

import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.record.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Forge events used by Redo on 1.8.9. */
public final class LegacyEvents {
    private static final int BUTTON_ID = 0x7ED0;
    private static final int RECORD_ID = 0x7ED1;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) LegacyClient.tickStart();
        else LegacyClient.tickEnd();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) LegacyClient.renderStart();
    }

    /** 1.8.9 draws the game interface under any open screen, even hidden: not over the replay. */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL && LegacyClient.replayActive()) {
            // the game sets up its 2D projection inside the overlay, and the Redo screen drawn just
            // after it needs that projection: without it the editor is drawn in the 3D view, unseen
            Minecraft.getMinecraft().entityRenderer.setupOverlayRendering();
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (LegacyClient.keepEditor(e.gui)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL) LegacyClient.renderHud();
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post e) {
        if (e.gui instanceof GuiMainMenu) e.buttonList.add(new GuiButton(BUTTON_ID, e.gui.width - 58, 6, 52, 20, "Redo"));
        // "Enregistrer / Arrêter" in the top right corner of the pause menu
        if (e.gui instanceof GuiIngameMenu && Recorder.canRecord()) {
            e.buttonList.add(new GuiButton(RECORD_ID, e.gui.width - 136, 6, 130, 20, Recorder.buttonLabel()));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Post e) {
        if (e.gui instanceof GuiMainMenu && e.button.id == BUTTON_ID) LegacyClient.openRecordings(e.gui);
        if (e.gui instanceof GuiIngameMenu && e.button.id == RECORD_ID) {
            boolean starting = !Recorder.isRecording();
            Recorder.toggleTake();
            if (starting) {
                // back to the game when a recording starts; when it stops, stay (to leave the server, say)
                Minecraft mc = Minecraft.getMinecraft();
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
            } else {
                e.button.displayString = Recorder.buttonLabel();
            }
        }
    }

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        CameraState c = LegacyClient.cameraOverride();
        if (c != null) e.roll = (float) c.roll;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (e.message != null) LegacyClient.chat(e.message.getUnformattedText());
    }
}
