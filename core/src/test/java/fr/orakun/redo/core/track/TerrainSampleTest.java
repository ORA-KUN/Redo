package fr.orakun.redo.core.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainSampleTest {
    private static TerrainSample sample(byte... surface) {
        return new TerrainSample(0, 0, 0, 4, 2, new short[] {64, 64, 64, 64}, surface,
                new String[] {"minecraft:water", "minecraft:grass_block"});
    }

    @Test
    void unloadedCellsAreSkipped() {
        // -1 marks a column that was not loaded when the terrain was sampled
        TerrainSample t = sample((byte) 0, (byte) -1, (byte) 1, (byte) -1);
        assertEquals(0.5, t.waterRatio(), 1e-9);
        assertEquals(1, t.count("grass"));
        assertEquals("", t.surfaceAt(1, 0));
    }

    @Test
    void nothingLoadedMeansNoWater() {
        assertEquals(0, sample((byte) -1, (byte) -1, (byte) -1, (byte) -1).waterRatio(), 1e-9);
    }
}
