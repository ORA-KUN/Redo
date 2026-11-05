package fr.orakun.redo.core.format;

import fr.orakun.redo.core.track.EventTrack;
import fr.orakun.redo.core.track.SelfSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingTakesTest {
    @TempDir
    File dir;

    private static File save(RecordingSession s, String name) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<File> out = new AtomicReference<File>();
        s.saveTake(name, new RecordingSession.Done() {
            @Override
            public void finished(File archive, Exception error) {
                out.set(archive);
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "take saved in time");
        return out.get();
    }

    private static List<PacketRecord> packets(RedoArchive a) throws Exception {
        List<PacketRecord> l = new ArrayList<PacketRecord>();
        PacketStreamReader r = a.openPackets();
        try {
            for (PacketRecord p = r.next(); p != null; p = r.next()) l.add(p);
        } finally {
            r.close();
        }
        return l;
    }

    @Test
    void eachTakeKeepsThePacketsFromTheStartAndOnlyItsOwnTrack() throws Exception {
        RecordingSession s = new RecordingSession(dir, new RedoMeta());
        s.packet(PacketRecord.PHASE_PLAY, new byte[]{1, 2, 3});
        s.packet(PacketRecord.PHASE_PLAY, new byte[]{4});

        s.beginTake(100);
        s.self(new SelfSample(150, 1, 2, 3, 0, 0, 0, 0, 0, 0));
        s.packet(PacketRecord.PHASE_PLAY, new byte[]{5, 6});
        s.marker("first");
        File first = save(s, "first");

        s.packet(PacketRecord.PHASE_PLAY, new byte[]{7});
        s.beginTake(400);
        s.self(new SelfSample(450, 4, 5, 6, 0, 0, 0, 0, 0, 0));
        File second = save(s, "second");
        s.stop(false, "", null);

        assertNotNull(first);
        assertNotNull(second);
        RedoArchive a = RedoArchive.open(first);
        assertEquals(100, a.meta.clipStartMs);
        assertEquals(3, packets(a).size(), "packets from the start of the capture to the end of the take");
        assertEquals(1, a.meta.markers.size());
        EventTrack t = a.track();
        assertEquals(150, t.startMs());

        RedoArchive b = RedoArchive.open(second);
        assertEquals(400, b.meta.clipStartMs);
        assertEquals(4, packets(b).size());
        assertEquals(0, b.meta.markers.size(), "markers belong to their take");
        assertEquals(450, b.track().startMs(), "the second take has only its own track");
    }

    @Test
    void anIdleCaptureIsNotRecoveredAfterACrash() throws Exception {
        RecordingSession s = new RecordingSession(dir, new RedoMeta());
        byte[] big = new byte[4096];
        for (int i = 0; i < 10; i++) s.packet(PacketRecord.PHASE_PLAY, big);
        Thread.sleep(200);
        assertTrue(RedoArchive.recover(dir).isEmpty(), "nothing recorded by the user, nothing to recover");
        s.stop(false, "", null);
    }
}
