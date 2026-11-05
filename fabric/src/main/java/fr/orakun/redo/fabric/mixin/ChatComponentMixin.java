package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
*///?} else {
import net.minecraft.client.GuiMessageTag;
//?}

/** Chat lines become events of the recording. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    //? if >=26.1 {
    /*@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"))
    private void redo$chat(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        RedoClient.chat(message.getString());
    }
    *///?} else {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void redo$chat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        RedoClient.chat(message.getString());
    }
    //?}
}
