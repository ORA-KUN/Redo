package fr.orakun.redo.core.director;

import fr.orakun.redo.core.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SceneScannerTest {
    /** A hub: a stone square with an oak in the middle, a house, a tower, a fountain and a portal. */
    public static FakeWorld hub() {
        FakeWorld w = new FakeWorld();
        w.fill(-25, FakeWorld.GROUND, -25, 25, FakeWorld.GROUND, 25, "stone_bricks");
        w.tree(1, -1, 6, 3);
        w.tree(18, 15, 9, 4);
        w.tree(50, 50, 7, 3);
        w.fill(-20, 65, -20, -12, 70, -12, "oak_planks");
        w.fill(15, 65, -20, 17, 80, -18, "stone_bricks");
        w.fill(-18, 65, 12, -12, 65, 18, "stone_bricks").fill(-17, 65, 13, -13, 65, 17, "water");
        w.fill(0, 65, 20, 0, 69, 23, "obsidian").fill(0, 66, 21, 0, 68, 22, "nether_portal");
        return w;
    }

    static Scene scanHub() {
        return SceneScanner.scan(hub(), new Vec3d(6, 65, 8), new Vec3d(6, 65, 8));
    }

    @Test
    void theTreeInTheMiddleOfTheHubIsFound() {
        Scene s = scanHub();
        assertNotNull(s);
        assertTrue(s.centreFromBuildings, "the stone square makes the centre");
        assertTrue(Math.abs(s.centre.x) < 2 && Math.abs(s.centre.z) < 2, "centre " + s.centre);
        Landmark tree = s.find(Scene.kinds(Landmark.Kind.TREE), Scene.Pick.CENTRAL, null, null);
        assertNotNull(tree, s.summary());
        assertEquals(1.5, tree.base.x, 0.01);
        assertEquals(-0.5, tree.base.z, 0.01);
        assertEquals(65, tree.base.y, 0.01);
        assertEquals("Chêne", tree.label);
        assertTrue(tree.height >= 8 && tree.height <= 10, "height " + tree.height);
        assertTrue(tree.distanceToCentre < 4, s.summary());
        Landmark biggest = s.find(Scene.kinds(Landmark.Kind.TREE), Scene.Pick.BIGGEST, null, null);
        assertEquals(18.5, biggest.base.x, 0.01);
    }

    @Test
    void buildingsTowersFountainsAndPortalsAreTold() {
        Scene s = scanHub();
        String sum = s.summary();
        Landmark house = s.find(Scene.kinds(Landmark.Kind.BUILDING), Scene.Pick.BIGGEST, null, null);
        assertNotNull(house, sum);
        assertEquals("Bâtiment en bois", house.label);
        assertTrue(house.direction.equals("nord-ouest"), "the house stands north-west (north is -z): " + house.direction);
        Landmark tower = s.find(Scene.kinds(Landmark.Kind.TOWER), Scene.Pick.ANY, null, null);
        assertNotNull(tower, sum);
        assertEquals("Tour en briques de pierre", tower.label);
        assertEquals(17, tower.height, 0.01);
        Landmark fountain = s.find(Scene.kinds(Landmark.Kind.FOUNTAIN), Scene.Pick.ANY, null, null);
        assertNotNull(fountain, sum);
        Landmark portal = s.find(Scene.kinds(Landmark.Kind.PORTAL), Scene.Pick.ANY, null, null);
        assertNotNull(portal, sum);
        assertEquals("Portail du Nether", portal.label);
        for (Landmark l : s.landmarks) assertFalse("obsidienne".equals(l.material), "the portal frame is the portal: " + sum);
        assertNotNull(s.find(Scene.kinds(Landmark.Kind.TOWER), Scene.Pick.ANY, "nord", null), "the tower stands to the north-east");
        assertNull(s.find(Scene.kinds(Landmark.Kind.TOWER), Scene.Pick.ANY, "sud", null));
        assertTrue(sum.contains("Chêne") && sum.contains("p1 |"), sum);
    }

    @Test
    void oldBlockNamesAreReadToo() {
        // 1.8.9 servers: "log", "leaves", "stonebrick", "grass" for the grass block
        FakeWorld w = new FakeWorld("minecraft:grass");
        w.fill(-12, FakeWorld.GROUND, -12, 12, FakeWorld.GROUND, 12, "stonebrick");
        for (int y = 65; y <= 70; y++) w.set(0, y, 0, "log");
        w.fill(-2, 70, -2, 2, 72, 2, "leaves").set(0, 70, 0, "log");
        Scene s = SceneScanner.scan(w, new Vec3d(3, 65, 3), null);
        Landmark tree = s.find(Scene.kinds(Landmark.Kind.TREE), Scene.Pick.CENTRAL, null, null);
        assertNotNull(tree, s.summary());
        assertEquals("Arbre", tree.label);
    }

    @Test
    void woodenBeamsWithoutLeavesAreNoTree() {
        FakeWorld w = new FakeWorld();
        for (int y = 65; y <= 72; y++) w.set(0, y, 0, "oak_log");
        Scene s = SceneScanner.scan(w, new Vec3d(0, 65, 0), new Vec3d(0, 65, 0));
        assertNull(s.find(Scene.kinds(Landmark.Kind.TREE), Scene.Pick.ANY, null, null), s.summary());
        assertFalse(s.centreFromBuildings);
    }

    @Test
    void anUnloadedWorldGivesNoScene() {
        WorldQuery none = new FakeWorld() {
            @Override
            public int surfaceY(int x, int z) {
                return Integer.MIN_VALUE;
            }
        };
        assertNull(SceneScanner.scan(none, new Vec3d(0, 65, 0), null));
    }
}
