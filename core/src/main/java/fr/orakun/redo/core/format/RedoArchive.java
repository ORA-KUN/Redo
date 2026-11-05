package fr.orakun.redo.core.format;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.track.EventTrack;
import fr.orakun.redo.core.track.TrackCodec;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A finished recording: a zip with meta.json, packets.bin and track.bin (both already
 * deflate-compressed, so stored as is). Edits live in a sidecar "name.redo.edit.json".
 */
public final class RedoArchive {
    public static final String EXTENSION = ".redo";
    public static final String META = "meta.json";
    public static final String PACKETS = "packets.bin";
    public static final String TRACK = "track.bin";
    public static final String RESOURCE_PACK = "resourcepack.zip";

    public final File file;
    public final RedoMeta meta;
    private EventTrack track;

    private RedoArchive(File file, RedoMeta meta) {
        this.file = file;
        this.meta = meta;
    }

    public static RedoArchive open(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            ZipEntry e = zip.getEntry(META);
            if (e == null) throw new IOException("Fichier .redo invalide (meta.json absent)");
            String json = readAll(zip.getInputStream(e));
            return new RedoArchive(file, RedoMeta.fromJson(Json.parseObject(json)));
        } finally {
            zip.close();
        }
    }

    public String displayName() {
        String n = file.getName();
        return n.endsWith(EXTENSION) ? n.substring(0, n.length() - EXTENSION.length()) : n;
    }

    public File editFile() {
        return new File(file.getParentFile(), file.getName() + ".edit.json");
    }

    public synchronized EventTrack track() throws IOException {
        if (track != null) return track;
        EventTrack t = new EventTrack();
        ZipFile zip = new ZipFile(file);
        try {
            ZipEntry e = zip.getEntry(TRACK);
            if (e != null) {
                DataInputStream in = new DataInputStream(new BufferedInputStream(
                        new InflaterInputStream(zip.getInputStream(e)), 1 << 16));
                try {
                    TrackCodec.readAll(in, t);
                } catch (IOException ex) {
                    RedoLog.warn("Track partly unreadable: " + ex);
                    t.finish();
                }
            } else {
                t.finish();
            }
        } finally {
            zip.close();
        }
        t.setDuration((int) meta.durationMs);
        track = t;
        return t;
    }

    /** Opens a fresh sequential reader on the packets (close it when done). */
    public PacketStreamReader openPackets() throws IOException {
        final ZipFile zip = new ZipFile(file);
        ZipEntry e = zip.getEntry(PACKETS);
        if (e == null) {
            zip.close();
            throw new IOException("packets.bin absent");
        }
        InputStream in = new java.io.FilterInputStream(zip.getInputStream(e)) {
            @Override
            public void close() throws IOException {
                super.close();
                zip.close();
            }
        };
        return new PacketStreamReader(in);
    }

    public boolean hasResourcePack() {
        try {
            ZipFile zip = new ZipFile(file);
            try {
                return zip.getEntry(RESOURCE_PACK) != null;
            } finally {
                zip.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** Copies the stored resource pack to {@code target}. */
    public boolean extractResourcePack(File target) {
        try {
            ZipFile zip = new ZipFile(file);
            try {
                ZipEntry e = zip.getEntry(RESOURCE_PACK);
                if (e == null) return false;
                InputStream in = zip.getInputStream(e);
                OutputStream out = new FileOutputStream(target);
                try {
                    copy(in, out);
                } finally {
                    in.close();
                    out.close();
                }
                return true;
            } finally {
                zip.close();
            }
        } catch (IOException e) {
            RedoLog.error("Could not extract resource pack", e);
            return false;
        }
    }

    // ---------------------------------------------------------------- packing

    /** Packs a finished temporary folder into {@code <dir>/<baseName>.redo}. */
    public static File pack(File tempDir, File dir, String baseName) throws IOException {
        String safe = sanitize(baseName);
        File out = new File(dir, safe + EXTENSION);
        int n = 2;
        while (out.exists()) out = new File(dir, safe + " (" + n++ + ")" + EXTENSION);
        File part = new File(dir, out.getName() + ".part");
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(part), 1 << 16));
        try {
            addDeflated(zip, new File(tempDir, META), META);
            addStored(zip, new File(tempDir, PACKETS), PACKETS);
            addStored(zip, new File(tempDir, TRACK), TRACK);
            File pack = new File(tempDir, RESOURCE_PACK);
            if (pack.isFile()) addStored(zip, pack, RESOURCE_PACK);
        } finally {
            zip.close();
        }
        if (!part.renameTo(out)) throw new IOException("Impossible de renommer " + part);
        return out;
    }

    private static void addDeflated(ZipOutputStream zip, File f, String name) throws IOException {
        if (!f.isFile()) return;
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.DEFLATED);
        zip.putNextEntry(e);
        InputStream in = new FileInputStream(f);
        try {
            copy(in, zip);
        } finally {
            in.close();
        }
        zip.closeEntry();
    }

    private static void addStored(ZipOutputStream zip, File f, String name) throws IOException {
        if (!f.isFile()) return;
        CRC32 crc = new CRC32();
        InputStream in = new BufferedInputStream(new FileInputStream(f), 1 << 16);
        byte[] buf = new byte[1 << 16];
        try {
            int r;
            while ((r = in.read(buf)) > 0) crc.update(buf, 0, r);
        } finally {
            in.close();
        }
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(f.length());
        e.setCompressedSize(f.length());
        e.setCrc(crc.getValue());
        zip.putNextEntry(e);
        in = new BufferedInputStream(new FileInputStream(f), 1 << 16);
        try {
            copy(in, zip);
        } finally {
            in.close();
        }
        zip.closeEntry();
    }

    /** Finishes temporary folders left by a crash. Returns the archives created. */
    public static List<File> recover(File dir) {
        List<File> out = new ArrayList<File>();
        File[] tmp = dir.listFiles();
        if (tmp == null) return out;
        for (File t : tmp) {
            if (!t.isDirectory() || !t.getName().startsWith(".tmp-")) continue;
            try {
                File packets = new File(t, PACKETS);
                if (packets.isFile() && packets.length() > 1024 && recordingRunning(t)) {
                    out.add(pack(t, dir, t.getName().substring(5) + " (récupéré)"));
                }
                deleteRecursively(t);
            } catch (IOException e) {
                RedoLog.error("Could not recover " + t, e);
            }
        }
        return out;
    }

    /** False for a background capture left by a crash while the user was not recording. */
    private static boolean recordingRunning(File tempDir) {
        File f = new File(tempDir, META);
        if (!f.isFile()) return true;
        try {
            InputStream in = new FileInputStream(f);
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                copy(in, out);
                return RedoMeta.fromJson(Json.parseObject(new String(out.toByteArray(), "UTF-8"))).clipStartMs >= 0;
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return true;
        }
    }

    /** Recordings in a folder, newest first. */
    public static List<File> list(File dir) {
        File[] files = dir.listFiles();
        List<File> out = new ArrayList<File>();
        if (files == null) return out;
        for (File f : files) if (f.isFile() && f.getName().endsWith(EXTENSION)) out.add(f);
        File[] arr = out.toArray(new File[0]);
        Arrays.sort(arr, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return new ArrayList<File>(Arrays.asList(arr));
    }

    public static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public static String sanitize(String s) {
        String r = s == null ? "replay" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (r.length() > 80) r = r.substring(0, 80);
        return r.isEmpty() ? "replay" : r;
    }

    static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            copy(in, bo);
            return new String(bo.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }
}
