package fr.orakun.redo.legacy;

import fr.orakun.redo.core.camera.CameraState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Forge events used by Redo on 1.8.9. */
public final class LegacyEvents {
    private static final int BUTTON_ID = 0x7ED0;

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
        if (e.type == RenderGameOverlayEvent.ElementType.ALL && LegacyClient.replayActive()) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL) LegacyClient.renderHud();
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post e) {
        if (e.gui instanceof GuiMainMenu) e.buttonList.add(new GuiButton(BUTTON_ID, e.gui.width - 58, 6, 52, 20, "Redo"));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Post e) {
        if (e.gui instanceof GuiMainMenu && e.button.id == BUTTON_ID) LegacyClient.openRecordings(e.gui);
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
