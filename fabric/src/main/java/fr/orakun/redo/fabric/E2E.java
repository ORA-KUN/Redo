package fr.orakun.redo.fabric;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.editor.EditorSession;
import fr.orakun.redo.core.editor.EditorView;
import fr.orakun.redo.core.export.ExportJob;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.record.Recorder;
import fr.orakun.redo.core.replay.AbstractReplay;
import fr.orakun.redo.core.replay.ReplayController;
import fr.orakun.redo.core.ui.RecordingsView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;

import java.io.File;
import java.util.List;

/**
 * In-game end-to-end test, only active with -Dredo.e2e=true (CI). The client joins a local test
 * server, records a few seconds, leaves, opens the recording, plays it, jumps back, exports the
 * automatic trailer and comes back to the list, taking screenshots on the way. Prints
 * "REDO E2E OK" or "REDO E2E FAIL: ..." in the log, then quits.
 */
final class E2E {
    static final boolean ENABLED = Boolean.getBoolean("redo.e2e");

    private enum Step { JOIN, RECORD, LEAVE, OPEN, EDITOR, PLAY, SEEK, EXPORT, EXIT, DONE }

    private static Step step = Step.JOIN;
    private static int ticks;
    private static int total;
    private static int shots;
    private static long startedAt;
    private static long lastTickAt = -1;
    private static long seekStartedAt;
    private static long seekTo;
    private static File recording;

    private E2E() {
    }

    private static void log(String s) {
        RedoFabric.LOG.info("[Redo E2E] " + s);
    }

    private static void fail(String why) {
        RedoFabric.LOG.error("REDO E2E FAIL: " + why);
        step = Step.DONE;
        Minecraft.getInstance().stop();
    }

    private static void next(Step s) {
        log("step " + step + " done after " + ticks + " ticks -> " + s);
        step = s;
        ticks = 0;
    }

    /** Saves a screenshot and logs its mean brightness, so a black or empty frame shows in the log. */
    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        String file = String.format("e2e-%02d-%s.png", ++shots, name);
        Screenshot.takeScreenshot(Compat.mainTarget(), image -> {
            try {
                long sum = 0;
                int n = 0;
                for (int y = 0; y < image.getHeight(); y += 8) {
                    for (int x = 0; x < image.getWidth(); x += 8) {
                        int c = image.getPixel(x, y);
                        sum += ((c & 0xFF) + ((c >> 8) & 0xFF) + ((c >> 16) & 0xFF)) / 3;
                        n++;
                    }
                }
                File dir = new File(mc.gameDirectory, "screenshots");
                dir.mkdirs();
                image.writeToFile(new File(dir, file));
                log("screenshot " + file + " " + image.getWidth() + "x" + image.getHeight() + ", mean brightness "
                        + (n == 0 ? 0 : sum / n) + "/255");
            } catch (Exception e) {
                log("screenshot " + file + " failed: " + e);
            } finally {
                image.close();
            }
        });
    }

    /**
     * Called at the start of every frame. The test counts its own 20 Hz ticks from the wall
     * clock, because the game does not tick while a replay is paused.
     */
    static void frame() {
        if (step == Step.DONE) return;
        long now = System.currentTimeMillis();
        if (startedAt == 0) startedAt = now;
        if (lastTickAt >= 0 && now - lastTickAt < 50) return;
        lastTickAt = now;
        ticks++;
        total++;
        try {
            run();
        } catch (Throwable t) {
            RedoFabric.LOG.error("REDO E2E FAIL: exception", t);
            step = Step.DONE;
            Minecraft.getInstance().stop();
        }
    }

    private static void run() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (System.currentTimeMillis() - startedAt > 12 * 60 * 1000L) {
            fail("timeout in step " + step);
            return;
        }
        switch (step) {
            case JOIN:
                if (total == 1) {
                    Connection probe = new Connection(PacketFlow.CLIENTBOUND);
                    if (!(probe instanceof RedoConnection)) {
                        fail("connection mixin not applied");
                        return;
                    }
                }
                if (mc.level != null && mc.player != null) {
                    if (!Recorder.isRecording()) {
                        fail("joined the server but no recording started");
                        return;
                    }
                    next(Step.RECORD);
                } else if (ticks > 20 * 180 || Compat.screen() instanceof DisconnectedScreen) {
                    fail("could not join the test server: " + joinState());
                } else if (ticks % 200 == 0) {
                    log("joining: " + joinState());
                }
                break;
            case RECORD:
                if (mc.player != null) {
                    // turn around slowly so the recorded player moves
                    mc.player.setYRot(mc.player.getYRot() + 3.0F);
                    mc.player.setXRot((float) (20 * Math.sin(ticks / 20.0)));
                }
                if (ticks == 20 * 2 && mc.player != null) mc.player.connection.sendCommand("time set day");
                if (ticks == 20 * 6) shot("in-game");
                if (ticks == 20 * 7) {
                    Recorder.toggleHighlight();
                    Recorder.marker();
                }
                if (ticks == 20 * 12) Recorder.toggleHighlight();
                if (ticks >= 20 * 16) {
                    log("recorded " + (Recorder.session() == null ? -1 : Recorder.session().timeMs()) + " ms");
                    if (mc.level != null) mc.level.disconnect(Component.literal("Redo E2E"));
                    mc.disconnectWithProgressScreen();
                    next(Step.LEAVE);
                }
                break;
            case LEAVE:
                if (Recorder.isRecording()) {
                    if (ticks > 20 * 10) fail("recording still running after leaving");
                    return;
                }
                List<File> files = RedoArchive.list(Redo.recordingsDir());
                if (!files.isEmpty() && ticks > 20) {
                    recording = files.get(0);
                    log("recording saved: " + recording.getName() + " (" + recording.length() + " bytes)");
                    next(Step.OPEN);
                } else if (ticks > 20 * 60) {
                    fail("no recording file in " + Redo.recordingsDir());
                }
                break;
            case OPEN:
                if (mc.level != null) return;
                if (ticks == 20) {
                    Compat.setScreen(RedoClient.recordingsScreen(new TitleScreen()));
                }
                if (ticks == 40) {
                    if (!(Compat.screen() instanceof RedoScreen) || !(((RedoScreen) Compat.screen()).view instanceof RecordingsView)) {
                        fail("recordings screen did not open");
                        return;
                    }
                    shot("recordings");
                }
                if (ticks == 60) {
                    RedoArchive archive = RedoArchive.open(recording);
                    log("meta: " + archive.meta.minecraftVersion + " protocol " + archive.meta.protocol + ", "
                            + archive.meta.durationMs + " ms, " + archive.meta.packetCount + " packets, self "
                            + archive.meta.selfName);
                    if (archive.meta.packetCount < 10) {
                        fail("almost no packets were recorded");
                        return;
                    }
                    RedoClient.openReplay(archive, new TitleScreen());
                    if (ReplayController.current() == null) {
                        fail("replay did not start");
                        return;
                    }
                    next(Step.EDITOR);
                }
                break;
            case EDITOR: {
                ReplayController c = ReplayController.current();
                if (c == null) {
                    fail("replay closed while loading");
                    return;
                }
                boolean editor = Compat.screen() instanceof RedoScreen && ((RedoScreen) Compat.screen()).view instanceof EditorView;
                if (ticks % 100 == 0) log("waiting for the editor: " + state(c));
                if (editor && mc.level != null && mc.player != null && !c.replay.isSeeking() && ticks > 20 * 3) {
                    EditorSession s = c.session();
                    log("replay world ready: player at " + (int) mc.player.getX() + " " + (int) mc.player.getY() + " "
                            + (int) mc.player.getZ() + ", " + countEntities(mc) + " entities, sequence of "
                            + (s == null ? -1 : s.sequence.clips.size()) + " clips, " + (s == null ? -1 : s.edit.suggestions.size())
                            + " suggested shots");
                    shot("editor");
                    if (s == null || s.edit.suggestions.isEmpty() || s.sequence.clips.isEmpty()) {
                        fail("the automatic analysis proposed no shot or no trailer (see \"Analysis failed\" above)");
                        return;
                    }
                    next(Step.PLAY);
                } else if (ticks > 20 * 120) {
                    shot("editor-missing");
                    fail("the editor did not open: " + state(c));
                }
                break;
            }
            case PLAY: {
                ReplayController c = ReplayController.current();
                if (c == null) {
                    fail("replay closed");
                    return;
                }
                EditorSession s = c.session();
                if (ticks == 1) {
                    s.setPlayhead(0);
                    if (!s.playing) s.togglePlay();
                }
                if (ticks == 20 * 3) shot("playing");
                if (ticks == 20 * 5) {
                    log("replay time after 5 s of playback: " + (long) c.replay.timeMs() + " ms");
                    if (s.playing) s.togglePlay();
                    next(Step.SEEK);
                }
                break;
            }
            case SEEK: {
                ReplayController c = ReplayController.current();
                AbstractReplay r = c.replay;
                if (ticks == 1) {
                    r.setCamera(null);
                    c.session().setMode(EditorSession.Mode.FREE_CAMERA);
                    // one second after the world appears (the recording starts during the login)
                    seekTo = Math.min(r.startMs() + 1000, r.durationMs());
                    log("jumping back to " + seekTo + " ms (world from " + r.startMs() + " ms)");
                    r.seek(seekTo);
                    seekStartedAt = System.currentTimeMillis();
                }
                if (ticks > 5 && !r.isSeeking() && mc.level != null && mc.player != null && Compat.screen() instanceof RedoScreen
                        && ((RedoScreen) Compat.screen()).view instanceof EditorView && ticks > 40) {
                    log("jumped back to " + (long) r.timeMs() + " ms in " + (System.currentTimeMillis() - seekStartedAt) + " ms");
                    if (Math.abs(r.timeMs() - seekTo) > 200) {
                        fail("seek landed at " + r.timeMs());
                        return;
                    }
                    shot("free-camera");
                    c.session().setMode(EditorSession.Mode.MONTAGE);
                    next(Step.EXPORT);
                } else if (ticks > 20 * 60) {
                    fail("backward seek did not finish");
                }
                break;
            }
            case EXPORT: {
                ReplayController c = ReplayController.current();
                EditorSession s = c.session();
                if (ticks == 1) {
                    if (s.sequence.clips.isEmpty()) {
                        log("no automatic trailer: skipping the export");
                        next(Step.EXIT);
                        return;
                    }
                    s.sequence.export.fps = 30;
                    s.sequence.export.width = 1280;
                    s.sequence.export.height = 720;
                    if (!s.startExport(Compat.frameWidth(), Compat.frameHeight(), false)) {
                        fail("export did not start (ffmpeg: " + s.ffmpeg() + ")");
                        return;
                    }
                    log("exporting " + s.export.frameCount() + " frames to " + s.export.output);
                }
                ExportJob job = s.export;
                if (job == null) {
                    fail("export vanished");
                    return;
                }
                if (ticks % 200 == 0) log("export " + job.frame() + " / " + job.frameCount() + " (" + job.state() + ")");
                if (ticks == 60) shot("exporting");
                if (job.state() == ExportJob.State.DONE) {
                    log("export done: " + job.output.getName() + " (" + job.output.length() + " bytes)");
                    if (job.output.length() < 1000) {
                        fail("exported file is empty");
                        return;
                    }
                    next(Step.EXIT);
                } else if (job.state() == ExportJob.State.FAILED || job.state() == ExportJob.State.CANCELLED) {
                    fail("export " + job.state() + ": " + job.error());
                }
                break;
            }
            case EXIT:
                if (ticks == 1) {
                    FabricReplay r = RedoClient.replay();
                    if (r != null) r.exit();
                }
                if (ticks == 40) {
                    if (ReplayController.active() || mc.level != null) {
                        fail("replay did not close");
                        return;
                    }
                    shot("back-to-list");
                }
                if (ticks == 60) {
                    log("REDO E2E OK");
                    step = Step.DONE;
                    mc.stop();
                }
                break;
            default:
                break;
        }
    }

    /** What the client shows while it joins the server, for the log. */
    private static String joinState() {
        Screen screen = Compat.screen();
        StringBuilder s = new StringBuilder();
        if (screen == null) {
            s.append("no screen");
        } else {
            s.append(screen.getClass().getName()).append(" \"").append(screen.getTitle().getString()).append('"');
            for (Object child : screen.children()) {
                if (child instanceof AbstractWidget) s.append(" [").append(((AbstractWidget) child).getMessage().getString()).append(']');
            }
        }
        s.append(", recording ").append(Recorder.isRecording());
        if (Recorder.session() != null) s.append(" (").append(Recorder.session().packetBytes()).append(" bytes)");
        return s.toString();
    }

    /** What the replay is doing, for the log. */
    private static String state(ReplayController c) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = Compat.screen();
        String shown = screen == null ? "no screen" : screen instanceof RedoScreen
                ? "RedoScreen/" + ((RedoScreen) screen).view.getClass().getSimpleName() : screen.getClass().getSimpleName();
        String s = shown + ", level " + (mc.level != null) + ", player " + (mc.player != null);
        if (c != null) {
            s += ", time " + (long) c.replay.timeMs() + " ms, seeking " + c.replay.isSeeking() + ", paused " + c.replay.isPaused();
            if (c.replay instanceof FabricReplay) s += ", " + ((FabricReplay) c.replay).debugState();
        }
        return s;
    }

    private static int countEntities(Minecraft mc) {
        int n = 0;
        for (Object ignored : mc.level.entitiesForRendering()) n++;
        return n;
    }
}
