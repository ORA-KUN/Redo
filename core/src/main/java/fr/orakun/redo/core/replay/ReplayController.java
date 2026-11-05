package fr.orakun.redo.core.replay;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.editor.EditorHost;
import fr.orakun.redo.core.editor.EditorSession;
import fr.orakun.redo.core.editor.EditorView;
import fr.orakun.redo.core.export.ExportDriver;
import fr.orakun.redo.core.export.ExportJob;

/**
 * Ties together the running replay, its editor and an export in progress. There is at most one
 * at a time; the platform hooks ask {@link #current()} whether a replay is open.
 */
public final class ReplayController {
    private static ReplayController current;

    public final AbstractReplay replay;
    private EditorSession session;
    private EditorView view;
    private ExportDriver driver;
    private String error;

    private ReplayController(AbstractReplay replay) {
        this.replay = replay;
    }

    public static ReplayController current() {
        return current;
    }

    public static boolean active() {
        return current != null;
    }

    /** Starts a replay. Returns null (and logs) if it cannot start. */
    public static ReplayController open(AbstractReplay replay) {
        if (current != null) current.close();
        ReplayController c = new ReplayController(replay);
        current = c;
        try {
            replay.start();
            return c;
        } catch (Exception e) {
            RedoLog.error("Could not start the replay", e);
            c.close();
            return null;
        }
    }

    public EditorSession session() {
        return session;
    }

    /** The editor, created the first time it is needed (when the replay world is loaded). */
    public EditorView editor(EditorHost host) {
        if (view == null) {
            try {
                session = new EditorSession(replay.archive, replay.archive.track(), replay);
                session.analyseIfNeeded();
            } catch (Exception e) {
                RedoLog.error("Could not open the editor", e);
                error = "Impossible de lire la piste de l'enregistrement : " + e.getMessage();
                return null;
            }
            view = new EditorView(session, host);
        }
        return view;
    }

    public String error() {
        return error;
    }

    /** Called by the platform at the start of each frame, before the tick timer. */
    public void frameStart() {
        replay.frame();
        ExportJob job = session == null ? null : session.export;
        if (job != null && job.isActive()) {
            if (driver == null || driver.job != job) driver = new ExportDriver(job, replay);
        } else if (driver != null) {
            driver.stop();
            driver = null;
        }
        if (driver != null) driver.beginFrame();
    }

    /** The export being rendered, or null. */
    public ExportDriver export() {
        return driver;
    }

    /** Leaves the replay (the platform has disconnected the fake world or is about to). */
    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (RuntimeException e) {
                RedoLog.error("Could not close the editor", e);
            }
        }
        if (driver != null) driver.stop();
        replay.dispose();
        if (current == this) current = null;
    }
}
