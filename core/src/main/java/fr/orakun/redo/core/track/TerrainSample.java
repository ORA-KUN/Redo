package fr.orakun.redo.core.track;

/**
 * Coarse grid of surface heights around the player, sampled periodically. It lets the director
 * judge relief (cliffs, mountains, water) and keep cameras above ground without the world.
 */
public final class TerrainSample {
    public final int timeMs;
    public final int originX, originZ;
    public final int step;
    public final int size;
    /** Surface height per cell, row-major (z then x). */
    public final short[] heights;
    /** Index into {@link #palette} per cell. */
    public final byte[] surface;
    public final String[] palette;

    public TerrainSample(int timeMs, int originX, int originZ, int step, int size, short[] heights, byte[] surface,
                         String[] palette) {
        this.timeMs = timeMs;
        this.originX = originX;
        this.originZ = originZ;
        this.step = step;
        this.size = size;
        this.heights = heights;
        this.surface = surface;
        this.palette = palette;
    }

    public boolean contains(double x, double z) {
        return x >= originX && z >= originZ && x < originX + step * (size - 1) && z < originZ + step * (size - 1);
    }

    /** Bilinear height at a world position (only meaningful when {@link #contains}). */
    public double heightAt(double x, double z) {
        double gx = (x - originX) / step;
        double gz = (z - originZ) / step;
        int ix = (int) Math.floor(gx);
        int iz = (int) Math.floor(gz);
        ix = Math.max(0, Math.min(size - 2, ix));
        iz = Math.max(0, Math.min(size - 2, iz));
        double fx = Math.max(0, Math.min(1, gx - ix));
        double fz = Math.max(0, Math.min(1, gz - iz));
        double h00 = heights[iz * size + ix];
        double h10 = heights[iz * size + ix + 1];
        double h01 = heights[(iz + 1) * size + ix];
        double h11 = heights[(iz + 1) * size + ix + 1];
        return (h00 * (1 - fx) + h10 * fx) * (1 - fz) + (h01 * (1 - fx) + h11 * fx) * fz;
    }

    public String surfaceAt(int ix, int iz) {
        int idx = surface[iz * size + ix] & 0xFF;
        return idx < palette.length ? palette[idx] : "";
    }

    public double maxHeight() {
        int m = Short.MIN_VALUE;
        for (short h : heights) m = Math.max(m, h);
        return m;
    }

    public double minHeight() {
        int m = Short.MAX_VALUE;
        for (short h : heights) m = Math.min(m, h);
        return m;
    }

    /** Standard deviation of heights: a good proxy for how dramatic the relief is. */
    public double relief() {
        double sum = 0, sq = 0;
        for (short h : heights) {
            sum += h;
            sq += h * (double) h;
        }
        double n = heights.length;
        double mean = sum / n;
        return Math.sqrt(Math.max(0, sq / n - mean * mean));
    }

    /** Share of cells whose surface is water. */
    public double waterRatio() {
        int w = 0;
        for (int i = 0; i < surface.length; i++) {
            String s = palette[surface[i] & 0xFF];
            if (s.contains("water") || s.contains("ice")) w++;
        }
        return w / (double) surface.length;
    }

    public int count(String needle) {
        int c = 0;
        for (int i = 0; i < surface.length; i++) {
            if (palette[surface[i] & 0xFF].contains(needle)) c++;
        }
        return c;
    }
}
