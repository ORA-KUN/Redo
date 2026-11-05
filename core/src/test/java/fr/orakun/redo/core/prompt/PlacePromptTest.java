package fr.orakun.redo.core.prompt;

import fr.orakun.redo.core.camera.CameraKeyframe;
import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.director.FakeWorld;
import fr.orakun.redo.core.director.Landmark;
import fr.orakun.redo.core.director.Scene;
import fr.orakun.redo.core.director.SceneScanner;
import fr.orakun.redo.core.director.SceneScannerTest;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.math.Vec3d;
import fr.orakun.redo.core.track.EntityInfo;
import fr.orakun.redo.core.track.EntitySample;
import fr.orakun.redo.core.track.EventTrack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacePromptTest {
    static final String JULES = "Fais un traveling long et lent autour de l'arbre présent au centre du hub";

    @Test
    void julesRequestIsUnderstoodWithoutClaude() {
        ShotRequest r = OfflinePromptParser.parse(JULES, Collections.singletonList("Jules"), "Jules");
        assertEquals(1, r.shots.size(), r.toString());
        ShotRequest.Spec s = r.shots.get(0);
        assertEquals(ShotType.ORBIT, s.type);
        assertEquals(14, s.durationSec, 0.01);
        assertEquals(0.55, s.motion, 0.01);
        assertNotNull(s.place);
        assertEquals(Collections.singleton(Landmark.Kind.TREE), s.place.kinds);
        assertEquals(Scene.Pick.CENTRAL, s.place.pick);
        assertNull(s.subject);
        assertNull(s.kind);
    }

    @Test
    void otherWaysToNameAPlace() {
        ShotRequest.Spec tower = OfflinePromptParser.parse("contre-plongée sur la plus grande tour", Collections.singletonList("Jules"), "Jules").shots.get(0);
        assertEquals(ShotType.LOW_ANGLE, tower.type);
        assertTrue(tower.place.kinds.contains(Landmark.Kind.TOWER));
        assertEquals(Scene.Pick.BIGGEST, tower.place.pick);

        ShotRequest.Spec fountain = OfflinePromptParser.parse("plan drone au-dessus de la fontaine au nord", Collections.singletonList("Jules"), "Jules").shots.get(0);
        assertEquals(ShotType.DRONE, fountain.type);
        assertTrue(fountain.place.kinds.contains(Landmark.Kind.FOUNTAIN));
        assertEquals("nord", fountain.place.direction);

        ShotRequest.Spec hub = OfflinePromptParser.parse("survol du hub", Collections.singletonList("Jules"), "Jules").shots.get(0);
        assertTrue(hub.place.wholePlace);
        assertEquals(ShotType.DRONE, hub.type);

        ShotRequest.Spec wall = OfflinePromptParser.parse("travelling latéral le long du mur", Collections.singletonList("Jules"), "Jules").shots.get(0);
        assertEquals(ShotType.TRUCK, wall.type);
        assertTrue(wall.place.kinds.contains(Landmark.Kind.WALL));

        // a player named: the shot follows the player, not the tree behind
        ShotRequest.Spec steve = OfflinePromptParser.parse("orbite autour de Steve", Arrays.asList("Steve", "Jules"), "Jules").shots.get(0);
        assertNull(steve.place);
        assertEquals("Steve", steve.subject);
    }

    static EventTrack track() {
        EventTrack t = new EventTrack();
        t.addEntity(new EntityInfo(7, "u", "Jules", "player", true, true));
        for (int ms = 0; ms <= 60000; ms += 50) t.addSample(7, new EntitySample(ms, 6.5f, 65f, 8.5f, 0, 0, 0));
        t.setDuration(60000);
        t.finish();
        return t;
    }

    static PromptService service(FakeWorld world) {
        EventTrack track = track();
        RedoMeta meta = new RedoMeta();
        meta.selfName = "Jules";
        Director d = new Director(track, meta, world);
        Vec3d self = new Vec3d(6.5, 65, 8.5);
        Scene scene = SceneScanner.scan(world, self, self);
        return new PromptService(d, meta).withScene(scene, 20000, self);
    }

    @Test
    void theShotCirclesSlowlyAroundTheTreeInTheMiddle() {
        PromptService ps = service(hubWorld());
        PromptService.Result r = ps.execute(OfflinePromptParser.parse(JULES, Collections.singletonList("Jules"), "Jules"), 3);
        assertEquals(1, r.shots.size(), r.message);
        ShotSuggestion s = r.shots.get(0);
        assertNotNull(s.place);
        assertEquals("Chêne", s.place.label);
        assertEquals(14000, s.clip.durationMs);
        assertEquals(ShotType.ORBIT.name(), s.clip.shotType);
        assertEquals(20000, s.clip.sourceStartMs, 1000, "filmed at the time the request was made");
        assertTrue(r.message.startsWith("Orbite") || r.message.contains("Chêne"), r.message);
        assertTrue(r.message.contains("Chêne"), r.message);

        Vec3d look = s.place.base.add(0, s.place.height * 0.55, 0);
        double turned = 0, prev = Double.NaN;
        for (CameraKeyframe k : s.clip.path.keys()) {
            CameraState c = k.state;
            double d = Math.hypot(c.x - s.place.base.x, c.z - s.place.base.z);
            assertTrue(d > 6 && d < 20, "keeps its distance: " + d);
            // always facing the tree
            Vec3d to = look.sub(c.pos());
            Vec3d fw = c.forward();
            double cos = (to.x * fw.x + to.y * fw.y + to.z * fw.z) / to.length();
            assertTrue(cos > 0.97, "looks at the tree at " + k.timeMs + " ms: " + cos);
            double a = Math.atan2(c.z - s.place.base.z, c.x - s.place.base.x);
            if (!Double.isNaN(prev)) {
                double da = a - prev;
                while (da > Math.PI) da -= 2 * Math.PI;
                while (da < -Math.PI) da += 2 * Math.PI;
                turned += da;
            }
            prev = a;
        }
        double deg = Math.abs(Math.toDegrees(turned));
        assertTrue(deg > 120 && deg < 220, "a long slow arc, not a spin: " + deg);
    }

    @Test
    void aMissingPlaceIsSaid() {
        FakeWorld bare = new FakeWorld();
        PromptService.Result r = service(bare).execute(OfflinePromptParser.parse("orbite autour de la fontaine", Collections.singletonList("Jules"), "Jules"), 1);
        assertTrue(r.shots.isEmpty());
        assertEquals("Je n'ai pas trouvé la fontaine dans le décor autour de toi.", r.message);
    }

    @Test
    void claudeCanNameAPlaceById() {
        PromptService ps = service(hubWorld());
        ShotRequest req = ShotRequest.fromJson(fr.orakun.redo.core.json.Json.parseObject(
                "{\"shots\":[{\"type\":\"DRONE\",\"place\":\"p2\",\"duration_seconds\":8}],\"reply\":\"Survol de la tour.\"}"));
        req.source = "claude";
        PromptService.Result r = ps.execute(req, 5);
        assertEquals(1, r.shots.size(), r.message);
        assertEquals(Landmark.Kind.TOWER, r.shots.get(0).place.kind);
        assertEquals("Survol de la tour.", r.message);
        assertFalse(ps.summary().isEmpty());
        assertTrue(ps.summary().contains("p2 | TOWER"), ps.summary());
    }

    private static FakeWorld hubWorld() {
        return SceneScannerTest.hub();
    }
}
