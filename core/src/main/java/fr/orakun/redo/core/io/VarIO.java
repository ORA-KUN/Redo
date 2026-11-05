package fr.orakun.redo.core.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;

/** Variable-length integers and length-prefixed UTF-8 strings. */
public final class VarIO {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private VarIO() {
    }

    public static void writeVarInt(DataOutput out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    public static int readVarInt(DataInput in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
            if (shift > 35) throw new IOException("VarInt too big");
        }
    }

    public static void writeVarLong(DataOutput out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((int) value);
    }

    public static long readVarLong(DataInput in) throws IOException {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
            if (shift > 70) throw new IOException("VarLong too big");
        }
    }

    /** ZigZag encoding for signed varints. */
    public static void writeSVarInt(DataOutput out, int value) throws IOException {
        writeVarInt(out, (value << 1) ^ (value >> 31));
    }

    public static int readSVarInt(DataInput in) throws IOException {
        int v = readVarInt(in);
        return (v >>> 1) ^ -(v & 1);
    }

    public static void writeString(DataOutput out, String s) throws IOException {
        if (s == null) s = "";
        byte[] b = s.getBytes(UTF8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    public static String readString(DataInput in) throws IOException {
        int len = readVarInt(in);
        if (len < 0 || len > 1 << 20) throw new IOException("String too long: " + len);
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, UTF8);
    }

    public static int varIntSize(int value) {
        int n = 1;
        while ((value & ~0x7F) != 0) {
            n++;
            value >>>= 7;
        }
        return n;
    }

    public static boolean isEof(Throwable t) {
        return t instanceof EOFException;
    }
}
