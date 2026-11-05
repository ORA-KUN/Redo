package fr.orakun.redo.core.format;

/** A raw clientbound packet (id + payload, uncompressed) with its arrival time. */
public final class PacketRecord {
    public static final int PHASE_LOGIN = 0;
    public static final int PHASE_CONFIGURATION = 1;
    public static final int PHASE_PLAY = 2;

    public final int timeMs;
    public final int phase;
    public final byte[] data;

    public PacketRecord(int timeMs, int phase, byte[] data) {
        this.timeMs = timeMs;
        this.phase = phase;
        this.data = data;
    }
}
