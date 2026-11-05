package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the blocks around a point and lists what a camera could film there: trees, buildings,
 * towers, fountains, water, portals... with their size and where they stand from the centre of
 * the place (the middle of what is built around, a hub's square for instance).
 */
public final class SceneScanner {
    public static final int RADIUS = 72;
    /** How deep a column is read under its highest block (tree trunks, under roofs...). */
    private static final int DEPTH = 40;
    private static final int MAX_LANDMARKS = 40;

    private final WorldQuery world;
    private final int cx, cz, r, n;
    private final int[] surface;
    private final BlockKinds.Kind[] top;
    private final String[] builtName;
    private final Map<Long, String> logs = new HashMap<Long, String>();
    private final List<Object[]> notables = new ArrayList<Object[]>();
    private int[] floor;

    private SceneScanner(WorldQuery world, int cx, int cz, int r) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.r = r;
        this.n = 2 * r + 1;
        this.surface = new int[n * n];
        this.top = new BlockKinds.Kind[n * n];
        this.builtName = new String[n * n];
    }

    /**
     * Analyses the scenery within {@link #RADIUS} blocks of {@code around}. {@code spawn} (or null)
     * is where the recording player appeared, the centre when nothing is built around.
     * Returns null when the world cannot be read there.
     */
    public static Scene scan(WorldQuery world, Vec3d around, Vec3d spawn) {
        return scan(world, around, spawn, RADIUS);
    }

    public static Scene scan(WorldQuery world, Vec3d around, Vec3d spawn, int radius) {
        if (world == null || around == null) return null;
        SceneScanner s = new SceneScanner(world, (int) Math.floor(around.x), (int) Math.floor(around.z), radius);
        if (!s.read()) return null;
        return s.analyse(around, spawn);
    }

    private int index(int x, int z) {
        int dx = x - cx + r, dz = z - cz + r;
        if (dx < 0 || dz < 0 || dx >= n || dz >= n) return -1;
        return dz * n + dx;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int ux(long p) {
        return (int) (p >> 38) << 6 >> 6;
    }

    private static int uz(long p) {
        return (int) ((p >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    private static int uy(long p) {
        return (int) (p & 0xFFF) << 20 >> 20;
    }

    // ---------------------------------------------------------------- reading

    private boolean read() {
        int loaded = 0;
        int minWorld = world.minY();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, z = cz + dz, i = index(x, z);
                surface[i] = Integer.MIN_VALUE;
                int sy = world.surfaceY(x, z);
                if (sy == Integer.MIN_VALUE) continue;
                loaded++;
                int bottom = Math.max(minWorld, sy - DEPTH);
                for (int y = sy; y >= bottom; y--) {
                    String name = world.blockName(x, y, z);
                    if (name == null) break;
                    BlockKinds.Kind k = BlockKinds.of(name);
                    if (k == BlockKinds.Kind.AIR || k == BlockKinds.Kind.PLANT) continue;
                    if (surface[i] == Integer.MIN_VALUE) {
                        surface[i] = y;
                        top[i] = k;
                    }
                    if (k == BlockKinds.Kind.LOG) logs.put(pack(x, y, z), name);
                    else if (k == BlockKinds.Kind.BUILT && builtName[i] == null) builtName[i] = name;
                    else if (k == BlockKinds.Kind.NOTABLE) notables.add(new Object[]{name, x, y, z});
                    else if (k == BlockKinds.Kind.NATURAL) break;
                }
            }
        }
        return loaded > n * n / 4;
    }

    // ---------------------------------------------------------------- analysis

    private Scene analyse(Vec3d around, Vec3d spawn) {
        floor = estimateFloor();
        List<Landmark> found = new ArrayList<Landmark>();
        found.addAll(trees());
        Set<Integer> structureCols = new HashSet<Integer>();
        found.addAll(structures(structureCols));
        found.addAll(liquids(structureCols));
        List<Landmark> blocks = notableBlocks();
        found.addAll(blocks);
        // a portal's obsidian frame is the portal, not a monument of its own
        for (Landmark p : blocks) {
            if (p.kind != Landmark.Kind.PORTAL) continue;
            for (java.util.Iterator<Landmark> it = found.iterator(); it.hasNext(); ) {
                Landmark l = it.next();
                if (l == p || !"obsidienne".equals(l.material)) continue;
                if (Math.hypot(l.base.x - p.base.x, l.base.z - p.base.z) <= l.radius + p.radius + 1) it.remove();
            }
        }

        // the centre: the middle of what is built (a hub's square), else where the player appeared
        long sx = 0, sz = 0;
        int built = 0;
        for (int i = 0; i < surface.length; i++) {
            if (top[i] != BlockKinds.Kind.BUILT) continue;
            sx += i % n;
            sz += i / n;
            built++;
        }
        Vec3d centre;
        boolean fromBuildings = built >= 100;
        if (fromBuildings) {
            double x = cx - r + sx / (double) built + 0.5, z = cz - r + sz / (double) built + 0.5;
            centre = new Vec3d(x, groundAt((int) Math.floor(x), (int) Math.floor(z), around.y), z);
        } else if (spawn != null && index((int) Math.floor(spawn.x), (int) Math.floor(spawn.z)) >= 0) {
            centre = spawn;
        } else {
            centre = around;
        }

        for (Landmark l : found) {
            double dx = l.base.x - centre.x, dz = l.base.z - centre.z;
            l.distanceToCentre = Math.sqrt(dx * dx + dz * dz);
            l.direction = compass(dx, dz);
        }
        // ranks by size within each kind
        Map<Landmark.Kind, List<Landmark>> byKind = new HashMap<Landmark.Kind, List<Landmark>>();
        for (Landmark l : found) {
            List<Landmark> list = byKind.get(l.kind);
            if (list == null) byKind.put(l.kind, list = new ArrayList<Landmark>());
            list.add(l);
        }
        for (List<Landmark> list : byKind.values()) {
            Collections.sort(list, new Comparator<Landmark>() {
                @Override
                public int compare(Landmark a, Landmark b) {
                    return Double.compare(b.height * (b.radius + 1), a.height * (a.radius + 1));
                }
            });
            for (int i = 0; i < list.size(); i++) list.get(i).rank = i + 1;
        }
        // keep the most telling ones: the biggest of each kind and what stands near the centre
        Collections.sort(found, new Comparator<Landmark>() {
            @Override
            public int compare(Landmark a, Landmark b) {
                return Double.compare(importance(b), importance(a));
            }
        });
        List<Landmark> kept = new ArrayList<Landmark>();
        for (Landmark l : found) {
            if (kept.size() >= MAX_LANDMARKS) break;
            kept.add(l);
        }
        for (int i = 0; i < kept.size(); i++) kept.get(i).id = "p" + (i + 1);
        return new Scene(centre, fromBuildings, kept, around, r);
    }

    private static double importance(Landmark l) {
        double size = Math.log(1 + l.height * (l.radius + 1));
        double near = 1 - Math.min(l.distanceToCentre, 80) / 80.0;
        double top = l.rank == 1 ? 1.5 : l.rank <= 3 ? 0.6 : 0;
        return size + near * 2.5 + top;
    }

    static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        if (a < 0) a += 360;
        String[] names = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest"};
        return names[(int) Math.round(a / 45) % 8];
    }

    private double groundAt(int x, int z, double fallback) {
        int i = index(x, z);
        if (i < 0 || floor[i] == Integer.MIN_VALUE) return fallback;
        return floor[i] + 1;
    }

    /** The walkable level around each column: low surfaces of the neighbourhood (8 x 8 cells). */
    private int[] estimateFloor() {
        int cells = (n + 7) / 8;
        int[] cell = new int[cells * cells];
        int[] buf = new int[64];
        for (int cz2 = 0; cz2 < cells; cz2++) {
            for (int cx2 = 0; cx2 < cells; cx2++) {
                int k = 0;
                for (int z = cz2 * 8; z < Math.min(n, cz2 * 8 + 8); z++) {
                    for (int x = cx2 * 8; x < Math.min(n, cx2 * 8 + 8); x++) {
                        int s = surface[z * n + x];
                        if (s != Integer.MIN_VALUE) buf[k++] = s;
                    }
                }
                if (k == 0) {
                    cell[cz2 * cells + cx2] = Integer.MIN_VALUE;
                } else {
                    Arrays.sort(buf, 0, k);
                    cell[cz2 * cells + cx2] = buf[k / 4];
                }
            }
        }
        int[] f = new int[n * n];
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int best = Integer.MAX_VALUE;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int qx = x / 8 + dx, qz = z / 8 + dz;
                        if (qx < 0 || qz < 0 || qx >= cells || qz >= cells) continue;
                        int v = cell[qz * cells + qx];
                        if (v != Integer.MIN_VALUE && v < best) best = v;
                    }
                }
                f[z * n + x] = best == Integer.MAX_VALUE ? Integer.MIN_VALUE : best;
            }
        }
        return f;
    }

    private int raise(int i) {
        if (surface[i] == Integer.MIN_VALUE || floor[i] == Integer.MIN_VALUE) return 0;
        return surface[i] - floor[i];
    }

    // ---------------------------------------------------------------- trees

    private List<Landmark> trees() {
        List<Landmark> out = new ArrayList<Landmark>();
        Set<Long> seen = new HashSet<Long>();
        List<long[]> trunks = new ArrayList<long[]>();
        List<String> species = new ArrayList<String>();
        for (Long start : logs.keySet()) {
            if (seen.contains(start)) continue;
            List<Long> comp = new ArrayList<Long>();
            ArrayDeque<Long> q = new ArrayDeque<Long>();
            q.add(start);
            seen.add(start);
            while (!q.isEmpty() && comp.size() < 4000) {
                long p = q.poll();
                comp.add(p);
                int x = ux(p), y = uy(p), z = uz(p);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            long o = pack(x + dx, y + dy, z + dz);
                            if (logs.containsKey(o) && seen.add(o)) q.add(o);
                        }
                    }
                }
            }
            if (comp.size() < 2) continue;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (long p : comp) {
                minY = Math.min(minY, uy(p));
                maxY = Math.max(maxY, uy(p));
            }
            double ax = 0, az = 0;
            int k = 0;
            Map<String, Integer> names = new HashMap<String, Integer>();
            for (long p : comp) {
                if (uy(p) <= minY + 1) {
                    ax += ux(p) + 0.5;
                    az += uz(p) + 0.5;
                    k++;
                }
                String nm = logs.get(p);
                Integer c = names.get(nm);
                names.put(nm, c == null ? 1 : c + 1);
            }
            trunks.add(new long[]{Math.round(ax / k * 16), Math.round(az / k * 16), minY, maxY, comp.size()});
            species.add(mostCommon(names));
        }
        // leaves on top of the columns belong to the nearest trunk
        int t = trunks.size();
        double[] reach = new double[t];
        int[] canopy = new int[t], topY = new int[t];
        for (int i = 0; i < t; i++) topY[i] = (int) trunks.get(i)[3];
        for (int i = 0; i < surface.length; i++) {
            if (top[i] != BlockKinds.Kind.LEAVES) continue;
            double x = cx - r + i % n + 0.5, z = cz - r + i / n + 0.5;
            int best = -1;
            double bestD = 7.5;
            for (int j = 0; j < t; j++) {
                long[] tr = trunks.get(j);
                if (surface[i] < tr[3] - 4 || surface[i] > tr[3] + 12) continue;
                double dx = x - tr[0] / 16.0, dz = z - tr[1] / 16.0;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < bestD) {
                    bestD = d;
                    best = j;
                }
            }
            if (best < 0) continue;
            canopy[best]++;
            reach[best] = Math.max(reach[best], bestD);
            topY[best] = Math.max(topY[best], surface[i]);
        }
        for (int j = 0; j < t; j++) {
            long[] tr = trunks.get(j);
            // logs without leaves are part of a building, not a tree
            if (canopy[j] < 3) continue;
            double height = topY[j] - tr[2] + 1;
            if (height < 4) continue;
            String sp = species.get(j) == null ? null : BlockKinds.species(species.get(j));
            String size = height >= 20 ? "Immense " : height >= 11 ? "Grand " : height <= 5 ? "Petit " : "";
            String label = sp == null ? (size.isEmpty() ? "Arbre" : size + "arbre") : capitalize(size + sp);
            Landmark l = new Landmark(Landmark.Kind.TREE, label, new Vec3d(tr[0] / 16.0, tr[2], tr[1] / 16.0),
                    Math.max(1.5, reach[j]), height, (int) tr[4] + canopy[j]);
            l.material = sp;
            out.add(l);
        }
        return out;
    }

    // ---------------------------------------------------------------- buildings

    private List<Landmark> structures(Set<Integer> taken) {
        List<Landmark> out = new ArrayList<Landmark>();
        boolean[] raised = new boolean[n * n];
        for (int i = 0; i < raised.length; i++) {
            raised[i] = (top[i] == BlockKinds.Kind.BUILT || top[i] == BlockKinds.Kind.NOTABLE) && raise(i) >= 3;
        }
        boolean[] seen = new boolean[n * n];
        for (int s = 0; s < raised.length; s++) {
            if (!raised[s] || seen[s]) continue;
            List<Integer> comp = flood(s, raised, seen);
            if (comp.size() < 4) continue;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            int height = 0;
            List<Integer> floors = new ArrayList<Integer>();
            Map<String, Integer> names = new HashMap<String, Integer>();
            for (int i : comp) {
                int x = i % n, z = i / n;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                height = Math.max(height, raise(i));
                floors.add(floor[i]);
                if (builtName[i] != null) {
                    Integer c = names.get(builtName[i]);
                    names.put(builtName[i], c == null ? 1 : c + 1);
                }
            }
            taken.addAll(comp);
            Collections.sort(floors);
            int baseY = floors.get(floors.size() / 2) + 1;
            int w = maxX - minX + 1, d = maxZ - minZ + 1;
            String mat = mostCommon(names);
            String material = mat == null ? null : BlockKinds.material(mat);
            Landmark.Kind kind;
            String what;
            if (comp.size() <= 16 && height >= 4 && height < 10) {
                kind = Landmark.Kind.MONUMENT;
                what = "Monument";
            } else if (height >= 10 && height >= 1.6 * Math.max(w, d)) {
                kind = Landmark.Kind.TOWER;
                what = "Tour";
            } else if (Math.min(w, d) <= 2 && Math.max(w, d) >= 8) {
                kind = Landmark.Kind.WALL;
                what = "Mur";
            } else {
                kind = Landmark.Kind.BUILDING;
                what = height >= 15 && comp.size() > 150 ? "Grand bâtiment" : "Bâtiment";
            }
            Landmark l = new Landmark(kind, material == null ? what : what + " en " + material,
                    new Vec3d(cx - r + (minX + maxX + 1) / 2.0, baseY, cz - r + (minZ + maxZ + 1) / 2.0),
                    Math.max(w, d) / 2.0, height + 1, comp.size());
            l.material = material;
            out.add(l);
        }
        return out;
    }

    private List<Integer> flood(int start, boolean[] in, boolean[] seen) {
        List<Integer> comp = new ArrayList<Integer>();
        ArrayDeque<Integer> q = new ArrayDeque<Integer>();
        q.add(start);
        seen[start] = true;
        while (!q.isEmpty()) {
            int i = q.poll();
            comp.add(i);
            int x = i % n, z = i / n;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int qx = x + dx, qz = z + dz;
                    if (qx < 0 || qz < 0 || qx >= n || qz >= n) continue;
                    int j = qz * n + qx;
                    if (in[j] && !seen[j]) {
                        seen[j] = true;
                        q.add(j);
                    }
                }
            }
        }
        return comp;
    }

    // ---------------------------------------------------------------- water, lava

    private List<Landmark> liquids(Set<Integer> structureCols) {
        List<Landmark> out = new ArrayList<Landmark>();
        for (BlockKinds.Kind liquid : new BlockKinds.Kind[]{BlockKinds.Kind.WATER, BlockKinds.Kind.LAVA}) {
            boolean[] in = new boolean[n * n];
            for (int i = 0; i < in.length; i++) in[i] = top[i] == liquid;
            boolean[] seen = new boolean[n * n];
            for (int s = 0; s < in.length; s++) {
                if (!in[s] || seen[s]) continue;
                List<Integer> comp = flood(s, in, seen);
                if (comp.size() < 4) continue;
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                int y = Integer.MIN_VALUE;
                Set<Integer> set = new HashSet<Integer>(comp);
                int ring = 0, builtRing = 0;
                for (int i : comp) {
                    int x = i % n, z = i / n;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                    y = Math.max(y, surface[i]);
                    for (int d = 0; d < 4; d++) {
                        int qx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), qz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                        if (qx < 0 || qz < 0 || qx >= n || qz >= n) continue;
                        int j = qz * n + qx;
                        if (set.contains(j)) continue;
                        ring++;
                        if (top[j] == BlockKinds.Kind.BUILT || structureCols.contains(j)) builtRing++;
                    }
                }
                int w = maxX - minX + 1, d = maxZ - minZ + 1;
                boolean framed = ring > 0 && builtRing >= ring * 0.6;
                Landmark.Kind kind;
                String label;
                if (liquid == BlockKinds.Kind.LAVA) {
                    kind = Landmark.Kind.LAVA;
                    label = comp.size() > 200 ? "Lac de lave" : "Bassin de lave";
                } else if (framed && comp.size() <= 120) {
                    kind = Landmark.Kind.FOUNTAIN;
                    label = "Fontaine";
                } else {
                    kind = Landmark.Kind.WATER;
                    label = framed ? "Bassin" : comp.size() > 400 ? "Lac" : comp.size() > 40 ? "Étang" : "Mare";
                }
                out.add(new Landmark(kind, label, new Vec3d(cx - r + (minX + maxX + 1) / 2.0, y, cz - r + (minZ + maxZ + 1) / 2.0),
                        Math.max(w, d) / 2.0, kind == Landmark.Kind.FOUNTAIN ? 3 : 1, comp.size()));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- remarkable blocks

    private List<Landmark> notableBlocks() {
        List<Landmark> out = new ArrayList<Landmark>();
        boolean[] used = new boolean[notables.size()];
        for (int a = 0; a < notables.size(); a++) {
            if (used[a]) continue;
            Object[] first = notables.get(a);
            String name = (String) first[0];
            String label = BlockKinds.notableLabel(name);
            int minX = (Integer) first[1], maxX = minX, minY = (Integer) first[2], maxY = minY, minZ = (Integer) first[3], maxZ = minZ;
            int count = 0;
            for (int b = a; b < notables.size(); b++) {
                Object[] o = notables.get(b);
                if (used[b] || !BlockKinds.notableLabel((String) o[0]).equals(label)) continue;
                int x = (Integer) o[1], y = (Integer) o[2], z = (Integer) o[3];
                if (x < minX - 4 || x > maxX + 4 || z < minZ - 4 || z > maxZ + 4 || y < minY - 6 || y > maxY + 6) continue;
                used[b] = true;
                count++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            boolean portal = label.startsWith("Portail");
            out.add(new Landmark(portal ? Landmark.Kind.PORTAL : Landmark.Kind.BLOCK, label,
                    new Vec3d((minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0),
                    Math.max(0.5, Math.max(maxX - minX + 1, maxZ - minZ + 1) / 2.0), maxY - minY + 1, count));
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private static String mostCommon(Map<String, Integer> counts) {
        String best = null;
        int n = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > n) {
                n = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
