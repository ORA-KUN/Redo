package fr.orakun.redo.legacy;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.director.Landmark;
import fr.orakun.redo.core.director.Scene;
import fr.orakun.redo.core.director.SceneScanner;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.editor.EditorSession;
import fr.orakun.redo.core.editor.EditorView;
import fr.orakun.redo.core.export.ExportJob;
import fr.orakun.redo.core.format.RedoArchive;
import fr.orakun.redo.core.record.Recorder;
import fr.orakun.redo.core.replay.AbstractReplay;
import fr.orakun.redo.core.replay.ReplayController;
import fr.orakun.redo.core.ui.RecordingsView;
import fr.orakun.redo.core.ui.Theme;
import fr.orakun.redo.core.ui.UiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ScreenShotHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * In-game end-to-end test, only active with -Dredo.e2e=true (CI). Same scenario as the Fabric
 * one: join a local test server, record, leave, open the recording, play it, jump back, export
 * the automatic trailer and come back to the list. Prints "REDO E2E OK" or "REDO E2E FAIL: ...".
 */
final class LegacyE2E {
    static final boolean ENABLED = Boolean.getBoolean("redo.e2e");

    private enum Step { JOIN, RECORD, LEAVE, OPEN, EDITOR, PLAY, SEEK, SCRUB, EXPORT, SCENERY, EXIT, DONE }

    private static Step step = Step.JOIN;
    private static int ticks;
    private static int shots;
    private static long startedAt;
    private static long lastTickAt = -1;
    private static long seekStartedAt;
    /** Ticks of the jump back during which another screen replaced the editor. */
    private static int editorLostTicks;
    /** Frames during the jump back that kept the last image, and how much the picture moved. */
    private static int heldFrames;
    private static BufferedImage beforeJump;
    private static int jumpChange = -1;
    private static String editorLostTo = "";
    private static long seekTo;
    private static boolean promptSent;
    private static int scrubFrom = -1, scrubHeld;
    private static int promptTicks;
    private static File recording;

    private LegacyE2E() {
    }

    static final String TREE_PROMPT = "Fais un traveling long et lent autour de l'arbre présent au centre du hub";

    /** Where the recording player stands at the current replay time. */
    private static fr.orakun.redo.core.math.Vec3d selfAt(EditorSession s) {
        fr.orakun.redo.core.math.Vec3d p = s.track.positionAt(s.track.selfId(), s.backend.timeMs());
        if (p != null) return p;
        fr.orakun.redo.core.camera.CameraState cam = s.backend.currentCamera();
        return cam == null ? null : new fr.orakun.redo.core.math.Vec3d(cam.x, cam.y, cam.z);
    }

    private static void log(String s) {
        RedoLegacy.LOG.info("[Redo E2E] " + s);
    }

    private static void fail(String why) {
        RedoLegacy.LOG.error("REDO E2E FAIL: " + why);
        step = Step.DONE;
        Minecraft.getMinecraft().shutdown();
    }

    private static void next(Step s) {
        log("step " + step + " done after " + ticks + " ticks -> " + s);
        step = s;
        ticks = 0;
    }

    private static BufferedImage shot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        String file = String.format("e2e-%02d-%s.png", ++shots, name);
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        // mean brightness, so a black or empty frame shows in the log
        try {
            BufferedImage img = ImageIO.read(new File(new File(mc.mcDataDir, "screenshots"), file));
            long sum = 0;
            int n = 0;
            for (int y = 0; y < img.getHeight(); y += 8) {
                for (int x = 0; x < img.getWidth(); x += 8) {
                    int c = img.getRGB(x, y);
                    sum += ((c & 0xFF) + ((c >> 8) & 0xFF) + ((c >> 16) & 0xFF)) / 3;
                    n++;
                }
            }
            log("screenshot " + file + " " + img.getWidth() + "x" + img.getHeight() + ", mean brightness " + (n == 0 ? 0 : sum / n) + "/255");
            return img;
        } catch (Exception e) {
            log("screenshot " + file + " not readable: " + e);
            return null;
        }
    }

    /**
     * True if the editor's top bar shows in the screenshot (its color at the top left), so an
     * editor that is open but drawn nowhere fails the test.
     */
    private static boolean editorDrawn(BufferedImage img) {
        if (img == null) return true;
        int px = UiScale.pixels(img.getWidth(), img.getHeight());
        int found = 0;
        // the right end too: an editor drawn shrunk in a corner leaves it to the world
        int[] xs = {2, 60, 110, img.getWidth() / px - 4};
        for (int ux : xs) {
            int c = img.getRGB(Math.min(img.getWidth() - 1, ux * px), px);
            if (close(c, Theme.HEADER)) found++;
        }
        log("editor top bar found at " + found + " of " + xs.length + " points");
        return found >= 3;
    }

    /** Mean difference of the middle of two screenshots (0 = same picture), or -1. */
    private static int change(BufferedImage a, BufferedImage b) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return -1;
        long sum = 0;
        int n = 0;
        for (int y = a.getHeight() * 3 / 10; y < a.getHeight() * 7 / 10; y += 6) {
            for (int x = a.getWidth() * 3 / 10; x < a.getWidth() * 7 / 10; x += 6) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                for (int sh = 0; sh <= 16; sh += 8) sum += Math.abs(((p >> sh) & 0xFF) - ((q >> sh) & 0xFF));
                n += 3;
            }
        }
        return n == 0 ? -1 : (int) (sum / n);
    }

    private static boolean close(int a, int b) {
        for (int sh = 0; sh <= 16; sh += 8) {
            if (Math.abs(((a >> sh) & 0xFF) - ((b >> sh) & 0xFF)) > 12) return false;
        }
        return true;
    }

    /** What the replay is doing, for the log. */
    private static String state(ReplayController c) {
        Minecraft mc = Minecraft.getMinecraft();
        String shown = mc.currentScreen == null ? "no screen" : mc.currentScreen instanceof RedoGuiScreen
                ? "RedoGuiScreen/" + ((RedoGuiScreen) mc.currentScreen).view.getClass().getSimpleName()
                : mc.currentScreen.getClass().getSimpleName();
        String s = shown + ", world " + (mc.theWorld != null) + ", player " + (mc.thePlayer != null);
        if (c != null) s += ", time " + (long) c.replay.timeMs() + " ms, seeking " + c.replay.isSeeking() + ", paused " + c.replay.isPaused();
        return s;
    }

    /** Called at the start of every frame; counts its own 20 Hz ticks from the wall clock. */
    static void frame() {
        if (step == Step.DONE) return;
        long now = System.currentTimeMillis();
        if (startedAt == 0) startedAt = now;
        if (lastTickAt >= 0 && now - lastTickAt < 50) return;
        lastTickAt = now;
        ticks++;
        try {
            run();
        } catch (Throwable t) {
            RedoLegacy.LOG.error("REDO E2E FAIL: exception", t);
            step = Step.DONE;
            Minecraft.getMinecraft().shutdown();
        }
    }

    private static void run() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        if (System.currentTimeMillis() - startedAt > 12 * 60 * 1000L) {
            fail("timeout in step " + step);
            return;
        }
        switch (step) {
            case JOIN:
                if (ticks == 1 && !(new NetworkManager(EnumPacketDirection.CLIENTBOUND) instanceof RedoNetworkManager)) {
                    fail("network manager mixin not applied");
                    return;
                }
                if (mc.theWorld != null && mc.thePlayer != null) {
                    if (Recorder.isRecording()) {
                        fail("a recording started without the pause menu button");
                        return;
                    }
                    if (!Recorder.canRecord()) {
                        fail("joined the server but nothing is captured");
                        return;
                    }
                    next(Step.RECORD);
                } else if (ticks > 20 * 180) {
                    fail("could not join the test server");
                }
                break;
            case RECORD:
                if (ticks == 10) mc.displayGuiScreen(new GuiIngameMenu());
                if (ticks == 20) {
                    // the "Enregistrer" button of the pause menu starts the recording and closes the menu
                    GuiButton button = null;
                    if (mc.currentScreen instanceof GuiIngameMenu) {
                        java.lang.reflect.Field f = GuiScreen.class.getDeclaredField("buttonList");
                        f.setAccessible(true);
                        for (Object b : (List<?>) f.get(mc.currentScreen)) {
                            if (b instanceof GuiButton && ((GuiButton) b).displayString.contains("Enregistrer")) button = (GuiButton) b;
                        }
                    }
                    if (button == null) {
                        fail("no record button in the pause menu (" + mc.currentScreen + ")");
                        return;
                    }
                    Recorder.toggleTake();
                    mc.displayGuiScreen(null);
                    if (!Recorder.isRecording()) {
                        fail("the record button did not start a recording");
                        return;
                    }
                    log("recording started from the pause menu");
                }
                if (mc.thePlayer != null) {
                    mc.thePlayer.rotationYaw += 3.0F;
                    mc.thePlayer.rotationPitch = (float) (20 * Math.sin(ticks / 20.0));
                }
                if (ticks == 20 * 2 && mc.thePlayer != null) mc.thePlayer.sendChatMessage("/time set day");
                if (ticks == 20 * 6) shot("in-game");
                if (ticks == 20 * 7) {
                    Recorder.toggleHighlight();
                    Recorder.marker();
                }
                if (ticks == 20 * 12) Recorder.toggleHighlight();
                if (ticks == 20 * 15) {
                    // "Arrêter": the recording is saved, the capture goes on until we leave
                    log("recorded " + Recorder.recordingTimeMs() + " ms");
                    Recorder.toggleTake();
                    if (Recorder.isRecording()) {
                        fail("the record button did not stop the recording");
                        return;
                    }
                }
                if (ticks >= 20 * 16) {
                    if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
                    mc.loadWorld((WorldClient) null);
                    mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
                    next(Step.LEAVE);
                }
                break;
            case LEAVE:
                if (Recorder.isRecording()) {
                    if (ticks > 20 * 10) fail("recording still running after leaving");
                    return;
                }
                List<File> files = RedoArchive.list(Redo.recordingsDir());
                if (files.size() > 1) {
                    fail("leaving the server saved a second recording: " + files);
                    return;
                }
                if (!files.isEmpty() && ticks > 20 * 3) {
                    recording = files.get(0);
                    log("recording saved: " + recording.getName() + " (" + recording.length() + " bytes)");
                    next(Step.OPEN);
                } else if (ticks > 20 * 60) {
                    fail("no recording file in " + Redo.recordingsDir());
                }
                break;
            case OPEN:
                if (mc.theWorld != null) return;
                if (ticks == 20) mc.displayGuiScreen(LegacyClient.recordingsScreen(new GuiMainMenu()));
                if (ticks == 40) {
                    if (!(mc.currentScreen instanceof RedoGuiScreen) || !(((RedoGuiScreen) mc.currentScreen).view instanceof RecordingsView)) {
                        fail("recordings screen did not open");
                        return;
                    }
                    shot("recordings");
                }
                if (ticks == 60) {
                    RedoArchive archive = RedoArchive.open(recording);
                    log("meta: " + archive.meta.minecraftVersion + " protocol " + archive.meta.protocol + ", "
                            + archive.meta.durationMs + " ms from " + archive.meta.clipStartMs + " ms, "
                            + archive.meta.packetCount + " packets, self " + archive.meta.selfName);
                    if (archive.meta.clipStartMs <= 0) {
                        fail("the recording does not start where the button was pressed");
                        return;
                    }
                    if (archive.meta.packetCount < 10) {
                        fail("almost no packets were recorded");
                        return;
                    }
                    LegacyClient.openReplay(archive, new GuiMainMenu());
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
                boolean editor = mc.currentScreen instanceof RedoGuiScreen && ((RedoGuiScreen) mc.currentScreen).view instanceof EditorView;
                if (ticks % 100 == 0) log("waiting for the editor: " + state(c));
                if (editor && mc.theWorld != null && mc.thePlayer != null && !c.replay.isSeeking() && ticks > 20 * 3) {
                    EditorSession s = c.session();
                    log("replay world ready: player at " + (int) mc.thePlayer.posX + " " + (int) mc.thePlayer.posY + " "
                            + (int) mc.thePlayer.posZ + ", " + mc.theWorld.loadedEntityList.size() + " entities, sequence of "
                            + (s == null ? -1 : s.sequence.clips.size()) + " clips, " + (s == null ? -1 : s.edit.suggestions.size())
                            + " suggested shots");
                    if (!editorDrawn(shot("editor"))) {
                        fail("the editor is open but its interface is not drawn");
                        return;
                    }
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
                    log("replay time after 5 s of playback: " + (long) c.replay.timeMs() + " ms, playhead " + (long) s.playheadMs + " ms, clip at playhead "
                            + (s.sequence.clipAt(s.playheadMs) == null ? "none" : s.sequence.clipAt(s.playheadMs).shotType + " from " + s.sequence.clipAt(s.playheadMs).sourceStartMs + " ms"));
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
                }
                if (ticks == 3) {
                    beforeJump = shot("before-jump");
                    // one second after the world appears (the recording starts during the login)
                    seekTo = Math.min(r.startMs() + 1000, r.durationMs());
                    log("jumping back to " + seekTo + " ms (world from " + r.startMs() + " ms)");
                    r.seek(seekTo);
                    seekStartedAt = System.currentTimeMillis();
                }
                if (ticks > 3 && r.isHoldingImage()) heldFrames++;
                // in the middle of the jump, the picture is still the one from before it
                if (ticks == 8 && r.isHoldingImage()) jumpChange = change(beforeJump, shot("during-jump"));
                // the editor stays on screen while the world is rebuilt (no loading screens)
                if (ticks > 1 && !(mc.currentScreen instanceof RedoGuiScreen && ((RedoGuiScreen) mc.currentScreen).view instanceof EditorView)) {
                    editorLostTicks++;
                    editorLostTo = mc.currentScreen == null ? "no screen" : mc.currentScreen.getClass().getName();
                }
                if (ticks > 40 && !r.isSeeking() && !r.isHoldingImage() && mc.theWorld != null && mc.thePlayer != null
                        && mc.currentScreen instanceof RedoGuiScreen && ((RedoGuiScreen) mc.currentScreen).view instanceof EditorView) {
                    log("jumped back to " + (long) r.timeMs() + " ms in " + (System.currentTimeMillis() - seekStartedAt) + " ms; last image kept for "
                            + heldFrames + " frames, picture change in the middle of the jump " + jumpChange + "/255");
                    if (heldFrames == 0) {
                        fail("the last image was not kept during the jump back (the world was seen being rebuilt)");
                        return;
                    }
                    if (jumpChange > 6) {
                        fail("the picture changed during the jump back (" + jumpChange + "/255)");
                        return;
                    }
                    if (editorLostTicks > 0) {
                        fail("the editor left the screen during the jump back (" + editorLostTicks + " ticks, " + editorLostTo + ")");
                        return;
                    }
                    if (Math.abs(r.timeMs() - seekTo) > 200) {
                        fail("seek landed at " + r.timeMs());
                        return;
                    }
                    shot("free-camera");
                    c.session().setMode(EditorSession.Mode.MONTAGE);
                    next(Step.SCRUB);
                } else if (ticks > 20 * 60) {
                    fail("backward seek did not finish");
                }
                break;
            }
            case SCRUB: {
                // the playhead dragged along the timeline in the Montage view, then dropped back near
                // the start: the world follows live while dragging forward, and a jump back keeps
                // the last image (never a frame of the editor drawn shrunk in a corner)
                ReplayController c = ReplayController.current();
                AbstractReplay r = c.replay;
                EditorSession s = c.session();
                if (ticks == 1) {
                    s.setMode(EditorSession.Mode.MONTAGE);
                    if (s.playing) s.togglePlay();
                    s.setPlayhead(0);
                    scrubFrom = -1;
                }
                if (scrubFrom < 0) {
                    if (ticks > 10 && !r.isSeeking() && !r.isHoldingImage()) scrubFrom = ticks;
                    else if (ticks > 20 * 60) fail("the replay did not settle before dragging the playhead");
                    return;
                }
                int k = ticks - scrubFrom;
                if (k < 24) {
                    s.scrubbing = true;
                    s.setPlayhead(Math.min(s.playheadMs + 150, Math.max(0, s.sequence.duration() - 1)));
                    if (r.isHoldingImage()) scrubHeld++;
                    return;
                }
                if (k == 24) {
                    log("dragged the playhead to " + (long) s.playheadMs + " ms (replay at " + (long) r.timeMs() + " ms), picture frozen "
                            + scrubHeld + " times while dragging");
                    s.scrubbing = false;
                    s.setPlayhead(100);
                    return;
                }
                if (k > 34 && !r.isSeeking() && !r.isHoldingImage() && r.worldSettled()) {
                    if (scrubHeld > 0) {
                        fail("the picture froze while the playhead was dragged forward (" + scrubHeld + " times)");
                        return;
                    }
                    if (!editorDrawn(shot("after-scrub"))) {
                        fail("the editor is not drawn whole after dragging the playhead");
                        return;
                    }
                    next(Step.EXPORT);
                } else if (k > 20 * 60) {
                    fail("the replay did not settle after dragging the playhead");
                }
                break;
            }
            case EXPORT: {
                EditorSession s = ReplayController.current().session();
                if (ticks == 1) {
                    if (s.sequence.clips.isEmpty()) {
                        log("no automatic trailer: skipping the export");
                        next(Step.SCENERY);
                        return;
                    }
                    s.sequence.export.fps = 30;
                    s.sequence.export.width = 1280;
                    s.sequence.export.height = 720;
                    if (!s.startExport(mc.displayWidth, mc.displayHeight, true)) {
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
                    next(Step.SCENERY);
                } else if (job.state() == ExportJob.State.FAILED || job.state() == ExportJob.State.CANCELLED) {
                    fail("export " + job.state() + ": " + job.error());
                }
                break;
            }
            case SCENERY: {
                // the prompt reads the scenery: the test server planted an oak next to the player
                ReplayController c = ReplayController.current();
                EditorSession s = c.session();
                if (ticks == 1) {
                    s.stopPreview();
                    c.replay.seek(Math.max(c.replay.startMs(), c.replay.durationMs() - 1500));
                }
                if (!promptSent) {
                    if (ticks > 20 && !c.replay.isSeeking() && !c.replay.isHoldingImage()) {
                        Scene scene = SceneScanner.scan(s.backend.world(), selfAt(s), selfAt(s));
                        log("scenery: " + (scene == null ? "not readable" : scene.summary().replace('\n', ' ')));
                        s.runPrompt(TREE_PROMPT);
                        promptSent = true;
                        promptTicks = ticks;
                    } else if (ticks > 20 * 60) {
                        fail("the replay did not settle before the scenery test");
                    }
                    return;
                }
                if (s.promptBusy) {
                    if (ticks - promptTicks > 20 * 30) fail("the prompt did not answer");
                    return;
                }
                ShotSuggestion tree = null;
                for (ShotSuggestion x : s.edit.suggestions) if (x.place != null && tree == null) tree = x;
                log("prompt \"" + TREE_PROMPT + "\": " + s.promptMessage);
                if (tree == null || tree.place.kind != Landmark.Kind.TREE) {
                    fail("the prompt did not find the tree of the scenery: " + s.promptMessage);
                    return;
                }
                if (tree.clip.durationMs != 14000 || !"ORBIT".equals(tree.clip.shotType)) {
                    fail("the shot around the tree is not a long slow orbit: " + tree.clip.shotType + " " + tree.clip.durationMs + " ms");
                    return;
                }
                shot("prompt-tree");
                next(Step.EXIT);
                break;
            }
            case EXIT:
                if (ticks == 1) {
                    LegacyReplay r = LegacyClient.replay();
                    if (r != null) r.exit();
                }
                if (ticks == 40) {
                    if (ReplayController.active() || mc.theWorld != null) {
                        fail("replay did not close");
                        return;
                    }
                    shot("back-to-list");
                }
                if (ticks == 60) {
                    log("REDO E2E OK");
                    step = Step.DONE;
                    mc.shutdown();
                }
                break;
            default:
                break;
        }
    }
}
