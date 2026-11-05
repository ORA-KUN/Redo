package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** A small world for tests: grass at y 64 over stone, plus the blocks a test sets. */
public class FakeWorld implements WorldQuery {
    public static final int GROUND = 64;
    private final Map<Long, String> blocks = new HashMap<Long, String>();
    private final Map<Long, Integer> tops = new HashMap<Long, Integer>();
    private final String ground;

    public FakeWorld() {
        this("minecraft:grass_block");
    }

    public FakeWorld(String ground) {
        this.ground = ground;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (z & 0xFFFFF) << 20) | (y & 0xFFFFF);
    }

    private static long col(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public FakeWorld set(int x, int y, int z, String name) {
        if (!name.contains(":")) name = "minecraft:" + name;
        blocks.put(key(x, y, z), name);
        Integer t = tops.get(col(x, z));
        if (!name.equals("minecraft:air") && (t == null || y > t)) tops.put(col(x, z), y);
        return this;
    }

    public FakeWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, String name) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, name);
        return this;
    }

    /** An oak: a trunk and a ball of leaves on top. */
    public FakeWorld tree(int x, int z, int trunk, int crown) {
        for (int y = GROUND + 1; y <= GROUND + trunk; y++) set(x, y, z, "oak_log");
        int cy = GROUND + trunk;
        for (int dx = -crown; dx <= crown; dx++)
            for (int dz = -crown; dz <= crown; dz++)
                for (int dy = -1; dy <= crown; dy++) {
                    if (dx * dx + dz * dz + dy * dy > crown * crown + 1) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    set(x + dx, cy + dy, z + dz, "oak_leaves");
                }
        return this;
    }

    @Override
    public String blockName(int x, int y, int z) {
        String b = blocks.get(key(x, y, z));
        if (b != null) return b;
        if (y > GROUND) return "minecraft:air";
        if (y == GROUND) return ground;
        return y > GROUND - 4 ? "minecraft:dirt" : "minecraft:stone";
    }

    @Override
    public int surfaceY(int x, int z) {
        Integer t = tops.get(col(x, z));
        return t == null ? GROUND : Math.max(GROUND, t);
    }

    @Override
    public int minY() {
        return 0;
    }

    @Override
    public boolean isSolid(double x, double y, double z) {
        String b = blockName((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !b.equals("minecraft:air") && !b.endsWith("leaves");
    }

    @Override
    public boolean isBlocked(Vec3d from, Vec3d to) {
        Vec3d d = to.sub(from);
        double len = d.length();
        for (double t = 0.5; t < len - 0.5; t += 0.5) {
            Vec3d p = from.add(d.mul(t / len));
            if (isSolid(p.x, p.y, p.z)) return true;
        }
        return false;
    }

    @Override
    public boolean isLoaded(double x, double z) {
        return Math.abs(x) < 400 && Math.abs(z) < 400;
    }
}
