package fr.orakun.redo.core.replay;

import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.core.director.WorldQuery;
import fr.orakun.redo.core.format.PacketRecord;
import fr.orakun.redo.core.format.PacketStreamWriter;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.format.RedoMeta;
import fr.orakun.redo.core.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A jump keeps the last image of the world on screen until the new world is drawn. */
class HeldImageTest {
    @TempDir
    File dir;

    /** A replay without a game: counts what the platform would do. */
    private static final class FakeReplay extends AbstractReplay {
        ImageCapture capture = ImageCapture.READY;
        boolean imageArrived;
        boolean terrain = true;
        int worldsOpened, worldsClosed;
        final List<Integer> applied = new ArrayList<Integer>();

        FakeReplay(RedoArchive a) {
            super(a);
        }

        @Override
        protected void openWorld() {
            worldsOpened++;
        }

        @Override
        protected void closeWorld() {
            worldsClosed++;
            applied.clear();
        }

        @Override
        protected void handle(PacketRecord p) {
            applied.add(p.timeMs);
        }

        @Override
        protected void applyTiming(boolean frozen, double speed) {
        }

        @Override
        protected float livePartialTick() {
            return 1;
        }

        @Override
        protected ImageCapture captureImage() {
            return capture;
        }

        @Override
        protected boolean imageReady() {
            return imageArrived;
        }

        @Override
        protected boolean terrainReady() {
            return terrain;
        }

        @Override
        public CameraState currentCamera() {
            return null;
        }

        @Override
        public WorldQuery world() {
            return null;
        }

        @Override
        public void exit() {
        }
    }

    private RedoArchive archive() throws Exception {
        File tmp = new File(dir, "tmp");
        assertTrue(tmp.mkdirs());
        PacketStreamWriter w = new PacketStreamWriter(new File(tmp, RedoArchive.PACKETS));
        for (int t = 0; t <= 20000; t += 100) w.write(t, PacketRecord.PHASE_PLAY, new byte[]{1}, 0, 1);
        w.close();
        RedoMeta m = new RedoMeta();
        m.durationMs = 20000;
        OutputStream out = new FileOutputStream(new File(tmp, RedoArchive.META));
        out.write(Json.write(m.toJson()).getBytes("UTF-8"));
        out.close();
        return RedoArchive.open(RedoArchive.pack(tmp, dir, "test"));
    }

    private static FakeReplay startedAt(FakeReplay r, long t) throws Exception {
        r.start();
        r.seek(t);
        for (int i = 0; i < 100 && (r.isSeeking() || r.isHoldingImage()); i++) r.frame();
        assertFalse(r.isSeeking());
        return r;
    }

    @Test
    void aJumpBackKeepsTheImageUntilTheTerrainIsDrawn() throws Exception {
        FakeReplay r = startedAt(new FakeReplay(archive()), 10000);
        assertFalse(r.isHoldingImage());
        r.terrain = false;
        r.seek(3000);
        assertTrue(r.isHoldingImage(), "the image is kept as soon as the jump starts");
        assertEquals(0, r.worldsClosed, "never rebuilt inside seek: the editor asks for jumps while it draws");
        r.frame();
        assertEquals(1, r.worldsClosed, "rebuilt at the start of the next frame");
        for (int i = 0; i < 10; i++) r.frame();
        assertFalse(r.isSeeking());
        assertTrue(r.isHoldingImage(), "still kept while the terrain is being built");
        r.terrain = true;
        for (int i = 0; i < 5; i++) r.frame();
        assertFalse(r.isHoldingImage(), "let go once the terrain is drawn");
        assertEquals(3000, (long) r.timeMs());
    }

    @Test
    void aJumpWaitsForAnImageThatIsStillBeingCopied() throws Exception {
        FakeReplay r = startedAt(new FakeReplay(archive()), 10000);
        r.capture = AbstractReplay.ImageCapture.PENDING;
        r.imageArrived = false;
        r.seek(3000);
        assertTrue(r.isSeeking());
        assertEquals(0, r.worldsClosed, "the world is not touched before the image is copied");
        r.frame();
        assertEquals(0, r.worldsClosed);
        r.seek(2000); // the user moves the playhead again meanwhile
        r.imageArrived = true;
        r.frame();
        assertEquals(1, r.worldsClosed, "rebuilt once the image is there");
        for (int i = 0; i < 100 && (r.isSeeking() || r.isHoldingImage()); i++) r.frame();
        assertEquals(2000, (long) r.timeMs());
        assertFalse(r.isHoldingImage());
    }

    @Test
    void draggingForwardShowsTheWorldLive() throws Exception {
        FakeReplay r = startedAt(new FakeReplay(archive()), 10000);
        // the playhead dragged forward: a seek every few frames, the world follows on screen
        for (long t = 10300; t <= 16000; t += 300) {
            r.seek(t);
            assertFalse(r.isHoldingImage(), "no frozen image while dragging forward");
            r.frame();
            r.frame();
            assertEquals(t, (long) r.timeMs());
        }
        assertEquals(1, r.worldsOpened, "forward jumps never rebuild the world");
    }

    @Test
    void jumpsBackAskedWhileHoldingWaitForTheNextFrame() throws Exception {
        FakeReplay r = startedAt(new FakeReplay(archive()), 10000);
        r.terrain = false;
        r.seek(6000);
        r.frame();
        assertEquals(1, r.worldsClosed);
        for (int i = 0; i < 20; i++) r.frame();
        assertTrue(r.isHoldingImage());
        // released again further back while the first image is still kept
        r.seek(2000);
        assertEquals(1, r.worldsClosed, "not rebuilt inside seek, even while an image is kept");
        r.seek(2500);
        r.frame();
        assertEquals(2, r.worldsClosed, "once, at the start of the frame, for the last aim");
        r.terrain = true;
        for (int i = 0; i < 100 && (r.isSeeking() || r.isHoldingImage()); i++) r.frame();
        assertEquals(2500, (long) r.timeMs());
    }

    @Test
    void theImageIsCopiedOnlyFromASettledWorld() throws Exception {
        FakeReplay r = startedAt(new FakeReplay(archive()), 10000);
        for (int i = 0; i < 10; i++) r.frame();
        assertTrue(r.worldSettled());
        r.seek(4000);
        assertFalse(r.worldSettled());
        for (int i = 0; i < 100 && (r.isSeeking() || r.isHoldingImage()); i++) r.frame();
        assertFalse(r.worldSettled(), "a few frames after the jump first");
        for (int i = 0; i < 10; i++) r.frame();
        assertTrue(r.worldSettled());
    }

    @Test
    void withoutAnImageTheJumpIsSeenAsBefore() throws Exception {
        FakeReplay r = startedAt(new FakeReplay(archive()), 10000);
        r.capture = AbstractReplay.ImageCapture.NONE;
        r.seek(3000);
        assertFalse(r.isHoldingImage());
        r.frame();
        assertEquals(1, r.worldsClosed);
    }
}
