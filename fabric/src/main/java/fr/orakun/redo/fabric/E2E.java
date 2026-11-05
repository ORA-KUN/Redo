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

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        String file = String.format("e2e-%02d-%s.png", ++shots, name);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1, c -> log("screenshot " + file));
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
                } else if (ticks > 20 * 180) {
                    fail("could not join the test server");
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
                    mc.disconnectFromWorld(Component.literal("Redo E2E"));
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
                    mc.setScreen(RedoClient.recordingsScreen(new TitleScreen()));
                }
                if (ticks == 40) {
                    if (!(mc.screen instanceof RedoScreen) || !(((RedoScreen) mc.screen).view instanceof RecordingsView)) {
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
                boolean editor = mc.screen instanceof RedoScreen && ((RedoScreen) mc.screen).view instanceof EditorView;
                if (editor && mc.level != null && mc.player != null && !c.replay.isSeeking() && ticks > 20 * 3) {
                    EditorSession s = c.session();
                    log("replay world ready: player at " + (int) mc.player.getX() + " " + (int) mc.player.getY() + " "
                            + (int) mc.player.getZ() + ", " + countEntities(mc) + " entities, sequence of "
                            + (s == null ? -1 : s.sequence.clips.size()) + " clips, " + (s == null ? -1 : s.edit.suggestions.size())
                            + " suggested shots");
                    shot("editor");
                    next(Step.PLAY);
                } else if (ticks > 20 * 120) {
                    fail("the editor did not open (screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + ")");
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
                    r.seek(1000);
                    seekStartedAt = System.currentTimeMillis();
                }
                if (ticks > 5 && !r.isSeeking() && mc.level != null && mc.player != null && mc.screen instanceof RedoScreen
                        && ((RedoScreen) mc.screen).view instanceof EditorView && ticks > 40) {
                    log("jumped back to " + (long) r.timeMs() + " ms in " + (System.currentTimeMillis() - seekStartedAt) + " ms");
                    if (Math.abs(r.timeMs() - 1000) > 200) {
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
                    if (!s.startExport(mc.getMainRenderTarget().width, mc.getMainRenderTarget().height, false)) {
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

    private static int countEntities(Minecraft mc) {
        int n = 0;
        for (Object ignored : mc.level.entitiesForRendering()) n++;
        return n;
    }
}
