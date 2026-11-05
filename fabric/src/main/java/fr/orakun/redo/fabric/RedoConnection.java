package fr.orakun.redo.fabric;

import net.minecraft.network.ProtocolInfo;

/** Extra state added to {@link net.minecraft.network.Connection} by the connection mixin. */
public interface RedoConnection {
    /** True for the in-memory connection that feeds a replay. */
    boolean redo$isReplay();

    void redo$setReplay(boolean replay);

    /** The protocol of the packets currently received (tracked for in-memory connections). */
    ProtocolInfo<?> redo$inbound();
}
