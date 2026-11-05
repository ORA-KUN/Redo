package fr.orakun.redo.legacy;

/** Extra state added to {@link net.minecraft.network.NetworkManager} by its mixin. */
public interface RedoNetworkManager {
    /** True for the in-memory connection that feeds a replay. */
    boolean redo$isReplay();

    void redo$setReplay(boolean replay);
}
