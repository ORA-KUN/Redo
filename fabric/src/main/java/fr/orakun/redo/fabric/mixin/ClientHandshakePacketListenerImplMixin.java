package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Duration;
import java.util.function.Consumer;

/** The client starts logging in to a server (or its own world): a recording begins. */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void redo$start(Connection connection, Minecraft minecraft, ServerData serverData, Screen parent,
                            boolean newWorld, Duration worldLoadDuration, Consumer<Component> updateStatus,
                            LevelLoadTracker levelLoadTracker, TransferState transferState, CallbackInfo ci) {
        RedoClient.connectionStarting(connection, serverData);
    }
}
