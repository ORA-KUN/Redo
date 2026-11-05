package fr.orakun.redo.core.record;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.format.RecordingSession;
import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.track.EntityInfo;
import fr.orakun.redo.core.track.EntitySample;
import fr.orakun.redo.core.track.GameEvent;
import fr.orakun.redo.core.track.SelfEquipment;
import fr.orakun.redo.core.track.SelfSample;
import fr.orakun.redo.core.track.TerrainSample;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives a recording from the platform hooks: the network code hands over packets, the client
 * tick hands over the world through a {@link TrackSource}. Platform independent, main thread
 * except {@link #packet}.
 */
public final class Recorder {
    private static final int TERRAIN_EVERY_TICKS = 100;
    private static final int TERRAIN_SIZE = 33;
    private static final int TERRAIN_STEP = 4;

    private static final class Known {
        EntitySample last;
        boolean hurt, dead, swinging;
        String type;
        boolean player;
        int lastSeenTick;
        double x, y, z;
        int swingTick = -1000;
    }

    private static volatile RecordingSession session;
    private static volatile boolean replaying;
    private static final Map<Integer, Known> known = new HashMap<Integer, Known>();
    private static final Set<Integer> seen = new HashSet<Integer>();
    private static final TrackSource.EntityState selfState = new TrackSource.EntityState();
    private static boolean selfAnnounced;
    private static int selfId = Integer.MIN_VALUE;
    private static SelfSample lastSelf;
    private static String[] lastEquipment;
    private static int tick;
    private static boolean selfWasHurt, selfWasSwinging;
    private static String message = "";
    private static long messageAt;

    private Recorder() {
    }

    /** True while a replay is open: nothing is recorded. */
    public static void setReplaying(boolean r) {
        replaying = r;
        if (r) stop();
    }

    public static boolean isReplaying() {
        return replaying;
    }

    public static boolean isRecording() {
        RecordingSession s = session;
        return s != null && !s.isStopped();
    }

    public static RecordingSession session() {
        return session;
    }

    /**
     * Starts a recording when the client begins to join a server (before the first packet).
     * Does nothing if auto recording is off or a replay is open.
     */
    public static void start(RedoMeta meta) {
        if (replaying || !Redo.config().autoRecord) return;
        stop();
        try {
            meta.redoVersion = Redo.VERSION;
            meta.startedAt = System.currentTimeMillis();
            session = new RecordingSession(Redo.recordingsDir(), meta);
            known.clear();
            seen.clear();
            selfAnnounced = false;
            selfId = Integer.MIN_VALUE;
            lastSelf = null;
            lastEquipment = null;
            tick = 0;
            RedoLog.info("Recording started (" + meta.serverName + ")");
        } catch (Exception e) {
            session = null;
            RedoLog.error("Could not start recording", e);
            say("Redo : impossible de démarrer l'enregistrement (" + e.getMessage() + ")");
        }
    }

    /** Raw bytes of one clientbound packet (id + payload, uncompressed). Any thread. */
    public static void packet(int phase, byte[] data) {
        RecordingSession s = session;
        if (s != null && !s.isStopped()) s.packet(phase, data);
    }

    /** Ends the recording (on disconnect) and saves it in the background. */
    public static void stop() {
        final RecordingSession s = session;
        session = null;
        if (s == null || s.isStopped()) return;
        int seconds = s.timeMs() / 1000;
        boolean keep = seconds >= 5 || s.wasUsed();
        String base = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(s.meta.startedAt))
                + (s.meta.serverName.isEmpty() ? "" : "_" + s.meta.serverName);
        if (keep) say("Redo : sauvegarde de l'enregistrement…");
        s.stop(keep, base, new RecordingSession.Done() {
            @Override
            public void finished(File archive, Exception error) {
                if (error != null) say("Redo : l'enregistrement n'a pas pu être sauvegardé (" + error.getMessage() + ")");
                else if (archive != null) say("Redo : enregistrement sauvegardé (" + archive.getName() + ")");
            }
        });
    }

    /** Opens or closes a "passage à garder". */
    public static void toggleHighlight() {
        RecordingSession s = session;
        if (s == null) return;
        say(s.toggleHighlight() ? "Redo : début du passage à garder" : "Redo : fin du passage à garder");
    }

    public static void marker() {
        RecordingSession s = session;
        if (s == null) return;
        s.marker("Repère");
        say("Redo : repère ajouté");
    }

    /** A block changed in the world (from the server). Used to spot building. */
    public static void blockChanged(int x, int y, int z, boolean wasAir, boolean isAir) {
        RecordingSession s = session;
        if (s == null || wasAir == isAir) return;
        int actor = nearestPlayer(x + 0.5, y + 0.5, z + 0.5, 6);
        s.event(new GameEvent(isAir ? GameEvent.Type.BLOCK_BREAK : GameEvent.Type.BLOCK_PLACE, s.timeMs(), actor,
                GameEvent.NONE, x + 0.5f, y + 0.5f, z + 0.5f, ""));
    }

    /** A chat or system message was shown. */
    public static void chat(String text) {
        RecordingSession s = session;
        if (s == null || text == null || text.isEmpty()) return;
        s.event(new GameEvent(GameEvent.Type.CHAT, s.timeMs(), GameEvent.NONE, GameEvent.NONE, 0, 0, 0,
                text.length() > 200 ? text.substring(0, 200) : text));
    }

    /** Called once per client tick. */
    public static void tick(TrackSource src) {
        RecordingSession s = session;
        if (s == null || s.isStopped() || !src.inWorld()) return;
        tick++;
        int t = s.timeMs();
        selfState.reset();
        if (!src.self(selfState)) return;
        if (!selfAnnounced || selfState.id != selfId) {
            selfId = selfState.id;
            selfAnnounced = true;
            s.meta.selfEntityId = selfId;
            s.meta.selfUuid = selfState.uuid;
            s.meta.selfName = selfState.name;
            s.entity(new EntityInfo(selfId, selfState.uuid, selfState.name, "minecraft:player", true, true));
        }
        recordSelf(s, t);
        recordEntities(s, src, t);
        recordEquipment(s, src, t);
        if (tick % TERRAIN_EVERY_TICKS == 1) recordTerrain(s, src, t);
    }

    private static void recordSelf(RecordingSession s, int t) {
        TrackSource.EntityState e = selfState;
        int actions = (e.swinging ? SelfSample.SWING : 0) | (e.hurt ? SelfSample.HURT : 0) | (e.usingItem ? SelfSample.USING_ITEM : 0);
        SelfSample cur = new SelfSample(t, e.x, e.y, e.z, e.yaw, e.pitch, e.headYaw, e.bodyYaw, e.flags, actions);
        s.self(cur);
        lastSelf = cur;
        if (e.hurt && !selfWasHurt) {
            int attacker = attackerNear(e.x, e.y, e.z, selfId);
            s.event(new GameEvent(GameEvent.Type.DAMAGE, t, selfId, attacker, (float) e.x, (float) e.y, (float) e.z, ""));
        }
        if (e.swinging && !selfWasSwinging) {
            s.event(new GameEvent(GameEvent.Type.SWING, t, selfId, GameEvent.NONE, (float) e.x, (float) e.y, (float) e.z, ""));
        }
        selfWasHurt = e.hurt;
        selfWasSwinging = e.swinging;
    }

    private static void recordEntities(final RecordingSession s, TrackSource src, final int t) {
        seen.clear();
        src.entities(new TrackSource.Visitor() {
            @Override
            public void visit(TrackSource.EntityState e) {
                seen.add(e.id);
                Known k = known.get(e.id);
                if (k == null) {
                    k = new Known();
                    k.type = e.type;
                    k.player = e.player;
                    known.put(e.id, k);
                    s.entity(new EntityInfo(e.id, e.uuid, e.name, e.type, e.player, false));
                }
                k.lastSeenTick = tick;
                k.x = e.x;
                k.y = e.y;
                k.z = e.z;
                EntitySample last = k.last;
                boolean changed = last == null
                        || Math.abs(last.x - e.x) > 0.01 || Math.abs(last.y - e.y) > 0.01 || Math.abs(last.z - e.z) > 0.01
                        || Math.abs(last.yaw - e.yaw) > 0.5f || Math.abs(last.pitch - e.pitch) > 0.5f
                        || last.flags != e.flags || t - last.timeMs > 2000;
                if (changed) {
                    EntitySample sample = new EntitySample(t, (float) e.x, (float) e.y, (float) e.z, e.yaw, e.pitch, e.flags);
                    s.sample(e.id, sample);
                    k.last = sample;
                }
                if (e.swinging && !k.swinging) {
                    k.swingTick = tick;
                    s.event(new GameEvent(GameEvent.Type.SWING, t, e.id, GameEvent.NONE, (float) e.x, (float) e.y, (float) e.z, ""));
                }
                if (e.hurt && !k.hurt) {
                    int attacker = attackerNear(e.x, e.y, e.z, e.id);
                    s.event(new GameEvent(GameEvent.Type.DAMAGE, t, e.id, attacker, (float) e.x, (float) e.y, (float) e.z, ""));
                }
                if (e.dead && !k.dead) {
                    int killer = attackerNear(e.x, e.y, e.z, e.id);
                    s.event(new GameEvent(GameEvent.Type.DEATH, t, e.id, killer, (float) e.x, (float) e.y, (float) e.z, e.name));
                }
                k.hurt = e.hurt;
                k.dead = e.dead;
                k.swinging = e.swinging;
            }
        });
        List<Integer> gone = null;
        for (Iterator<Map.Entry<Integer, Known>> it = known.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Known> en = it.next();
            if (seen.contains(en.getKey())) continue;
            Known k = en.getValue();
            if (gone == null) gone = new ArrayList<Integer>();
            gone.add(en.getKey());
            String type = k.type == null ? "" : k.type;
            if (!k.dead && (type.contains("tnt") || type.contains("creeper") || type.contains("end_crystal"))) {
                s.event(new GameEvent(GameEvent.Type.EXPLOSION, t, en.getKey(), GameEvent.NONE, (float) k.x, (float) k.y, (float) k.z, type));
            }
            it.remove();
        }
        if (gone != null) for (Integer id : gone) s.removed(id);
    }

    private static void recordEquipment(RecordingSession s, TrackSource src, int t) {
        if (tick % 5 != 0) return;
        String[] eq = src.equipment();
        if (eq == null) return;
        if (lastEquipment == null || !java.util.Arrays.equals(lastEquipment, eq)) {
            s.equipment(new SelfEquipment(t, eq.clone()));
            lastEquipment = eq.clone();
        }
    }

    private static void recordTerrain(RecordingSession s, TrackSource src, int t) {
        SelfSample me = lastSelf;
        if (me == null) return;
        int half = TERRAIN_SIZE / 2;
        int ox = ((int) Math.floor(me.x) - half * TERRAIN_STEP);
        int oz = ((int) Math.floor(me.z) - half * TERRAIN_STEP);
        short[] heights = new short[TERRAIN_SIZE * TERRAIN_SIZE];
        byte[] surface = new byte[TERRAIN_SIZE * TERRAIN_SIZE];
        List<String> palette = new ArrayList<String>();
        Map<String, Integer> index = new HashMap<String, Integer>();
        int loaded = 0;
        for (int iz = 0; iz < TERRAIN_SIZE; iz++) {
            for (int ix = 0; ix < TERRAIN_SIZE; ix++) {
                int x = ox + ix * TERRAIN_STEP, z = oz + iz * TERRAIN_STEP;
                int h = src.height(x, z);
                int i = iz * TERRAIN_SIZE + ix;
                if (h == Integer.MIN_VALUE) {
                    heights[i] = Short.MIN_VALUE;
                    surface[i] = -1;
                    continue;
                }
                loaded++;
                heights[i] = (short) h;
                String b = src.block(x, h, z);
                Integer p = index.get(b);
                if (p == null) {
                    if (palette.size() >= 127) {
                        surface[i] = -1;
                        continue;
                    }
                    p = palette.size();
                    palette.add(b);
                    index.put(b, p);
                }
                surface[i] = (byte) (int) p;
            }
        }
        if (loaded == 0) return;
        s.terrain(new TerrainSample(t, ox, oz, TERRAIN_STEP, TERRAIN_SIZE, heights, surface, palette.toArray(new String[0])));
    }

    /** Most likely attacker: the closest player (or mob) near the victim that swung recently. */
    private static int attackerNear(double x, double y, double z, int victim) {
        int best = GameEvent.NONE;
        double bestD = 6 * 6;
        for (Map.Entry<Integer, Known> en : known.entrySet()) {
            if (en.getKey() == victim) continue;
            Known k = en.getValue();
            double d = (k.x - x) * (k.x - x) + (k.y - y) * (k.y - y) + (k.z - z) * (k.z - z);
            boolean swung = tick - k.swingTick < 8;
            if (d < bestD && (swung || k.player)) {
                bestD = swung ? d * 0.25 : d;
                best = en.getKey();
            }
        }
        if (victim != selfId && lastSelf != null) {
            double d = (lastSelf.x - x) * (lastSelf.x - x) + (lastSelf.y - y) * (lastSelf.y - y) + (lastSelf.z - z) * (lastSelf.z - z);
            if (d < 6 * 6 && (selfWasSwinging || d < bestD)) best = selfId;
        }
        return best;
    }

    private static int nearestPlayer(double x, double y, double z, double radius) {
        int best = GameEvent.NONE;
        double bestD = radius * radius;
        if (lastSelf != null) {
            double d = (lastSelf.x - x) * (lastSelf.x - x) + (lastSelf.y - y) * (lastSelf.y - y) + (lastSelf.z - z) * (lastSelf.z - z);
            if (d < bestD) {
                bestD = d;
                best = selfId;
            }
        }
        for (Map.Entry<Integer, Known> en : known.entrySet()) {
            Known k = en.getValue();
            if (!k.player) continue;
            double d = (k.x - x) * (k.x - x) + (k.y - y) * (k.y - y) + (k.z - z) * (k.z - z);
            if (d < bestD) {
                bestD = d;
                best = en.getKey();
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ messages for the HUD

    static synchronized void say(String m) {
        message = m;
        messageAt = System.currentTimeMillis();
        RedoLog.info(m);
    }

    /** Last status message if it is recent, else null. */
    public static synchronized String recentMessage() {
        return System.currentTimeMillis() - messageAt < 4000 ? message : null;
    }
}
