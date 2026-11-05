package fr.orakun.redo.core.track;

import fr.orakun.redo.core.io.VarIO;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;

/** Binary encoding of the event track ("track.bin"). */
public final class TrackCodec {
    public static final int ENTITY = 1;
    public static final int SAMPLE = 2;
    public static final int EVENT = 3;
    public static final int TERRAIN = 4;
    public static final int SELF = 5;
    public static final int SELF_EQUIP = 6;
    public static final int REMOVE = 7;

    private TrackCodec() {
    }

    public static void writeEntity(DataOutput out, EntityInfo e) throws IOException {
        out.writeByte(ENTITY);
        VarIO.writeSVarInt(out, e.id);
        VarIO.writeString(out, e.uuid);
        VarIO.writeString(out, e.name);
        VarIO.writeString(out, e.type);
        out.writeByte((e.player ? 1 : 0) | (e.self ? 2 : 0));
    }

    public static void writeSample(DataOutput out, int entityId, EntitySample s) throws IOException {
        out.writeByte(SAMPLE);
        VarIO.writeVarInt(out, s.timeMs);
        VarIO.writeSVarInt(out, entityId);
        out.writeFloat(s.x);
        out.writeFloat(s.y);
        out.writeFloat(s.z);
        out.writeFloat(s.yaw);
        out.writeFloat(s.pitch);
        out.writeByte(s.flags);
    }

    public static void writeEvent(DataOutput out, GameEvent e) throws IOException {
        out.writeByte(EVENT);
        VarIO.writeVarInt(out, e.timeMs);
        out.writeByte(e.type.ordinal());
        VarIO.writeSVarInt(out, e.entity);
        VarIO.writeSVarInt(out, e.other);
        out.writeFloat(e.x);
        out.writeFloat(e.y);
        out.writeFloat(e.z);
        VarIO.writeString(out, e.text);
    }

    public static void writeTerrain(DataOutput out, TerrainSample t) throws IOException {
        out.writeByte(TERRAIN);
        VarIO.writeVarInt(out, t.timeMs);
        out.writeInt(t.originX);
        out.writeInt(t.originZ);
        out.writeByte(t.step);
        out.writeByte(t.size);
        for (short h : t.heights) out.writeShort(h);
        out.write(t.surface);
        out.writeByte(t.palette.length);
        for (String p : t.palette) VarIO.writeString(out, p);
    }

    public static void writeSelf(DataOutput out, SelfSample s) throws IOException {
        out.writeByte(SELF);
        VarIO.writeVarInt(out, s.timeMs);
        out.writeDouble(s.x);
        out.writeDouble(s.y);
        out.writeDouble(s.z);
        out.writeFloat(s.yaw);
        out.writeFloat(s.pitch);
        out.writeFloat(s.headYaw);
        out.writeFloat(s.bodyYaw);
        out.writeByte(s.flags);
        out.writeByte(s.actions);
    }

    public static void writeEquipment(DataOutput out, SelfEquipment e) throws IOException {
        out.writeByte(SELF_EQUIP);
        VarIO.writeVarInt(out, e.timeMs);
        out.writeByte(e.slots.length);
        for (String s : e.slots) VarIO.writeString(out, s);
    }

    public static void writeRemove(DataOutput out, int timeMs, int entityId) throws IOException {
        out.writeByte(REMOVE);
        VarIO.writeVarInt(out, timeMs);
        VarIO.writeSVarInt(out, entityId);
    }

    /** Reads every record into {@code track}. A truncated tail (crash) is tolerated. */
    public static void readAll(DataInput in, EventTrack track) throws IOException {
        while (true) {
            int type;
            try {
                type = in.readUnsignedByte();
            } catch (EOFException eof) {
                break;
            }
            try {
                switch (type) {
                    case ENTITY: {
                        int id = VarIO.readSVarInt(in);
                        String uuid = VarIO.readString(in);
                        String name = VarIO.readString(in);
                        String etype = VarIO.readString(in);
                        int f = in.readUnsignedByte();
                        track.addEntity(new EntityInfo(id, uuid, name, etype, (f & 1) != 0, (f & 2) != 0));
                        break;
                    }
                    case SAMPLE: {
                        int t = VarIO.readVarInt(in);
                        int id = VarIO.readSVarInt(in);
                        EntitySample s = new EntitySample(t, in.readFloat(), in.readFloat(), in.readFloat(),
                                in.readFloat(), in.readFloat(), in.readUnsignedByte());
                        track.addSample(id, s);
                        break;
                    }
                    case EVENT: {
                        int t = VarIO.readVarInt(in);
                        GameEvent.Type et = GameEvent.Type.byId(in.readUnsignedByte());
                        int ent = VarIO.readSVarInt(in);
                        int other = VarIO.readSVarInt(in);
                        float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                        String text = VarIO.readString(in);
                        track.addEvent(new GameEvent(et, t, ent, other, x, y, z, text));
                        break;
                    }
                    case TERRAIN: {
                        int t = VarIO.readVarInt(in);
                        int ox = in.readInt();
                        int oz = in.readInt();
                        int step = in.readUnsignedByte();
                        int size = in.readUnsignedByte();
                        short[] h = new short[size * size];
                        for (int i = 0; i < h.length; i++) h[i] = in.readShort();
                        byte[] surf = new byte[size * size];
                        in.readFully(surf);
                        int pn = in.readUnsignedByte();
                        String[] pal = new String[pn];
                        for (int i = 0; i < pn; i++) pal[i] = VarIO.readString(in);
                        track.addTerrain(new TerrainSample(t, ox, oz, step, size, h, surf, pal));
                        break;
                    }
                    case SELF: {
                        int t = VarIO.readVarInt(in);
                        track.addSelf(new SelfSample(t, in.readDouble(), in.readDouble(), in.readDouble(),
                                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                                in.readUnsignedByte(), in.readUnsignedByte()));
                        break;
                    }
                    case SELF_EQUIP: {
                        int t = VarIO.readVarInt(in);
                        int n = in.readUnsignedByte();
                        String[] slots = new String[n];
                        for (int i = 0; i < n; i++) slots[i] = VarIO.readString(in);
                        track.addEquipment(new SelfEquipment(t, slots));
                        break;
                    }
                    case REMOVE: {
                        int t = VarIO.readVarInt(in);
                        int id = VarIO.readSVarInt(in);
                        track.addRemoval(id, t);
                        break;
                    }
                    default:
                        return; // unknown record: stop, the rest is unreadable
                }
            } catch (EOFException eof) {
                break;
            }
        }
        track.finish();
    }
}
