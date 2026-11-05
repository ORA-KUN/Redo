package fr.orakun.redo.core.export;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Finds an ffmpeg executable: configured path, Redo's own folder, PATH, then usual install places. */
public final class FfmpegLocator {
    private FfmpegLocator() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    public static String exeName() {
        return isWindows() ? "ffmpeg.exe" : "ffmpeg";
    }

    public static File find(String configured, File redoDir) {
        List<File> candidates = new ArrayList<File>();
        if (configured != null && !configured.trim().isEmpty()) candidates.add(new File(configured.trim()));
        if (redoDir != null) candidates.add(new File(new File(redoDir, "ffmpeg"), exeName()));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isEmpty()) candidates.add(new File(dir, exeName()));
            }
        }
        if (isWindows()) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) candidates.add(new File(local, "Microsoft\\WinGet\\Links\\ffmpeg.exe"));
            candidates.add(new File("C:\\ffmpeg\\bin\\ffmpeg.exe"));
            candidates.add(new File("C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe"));
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) candidates.add(new File(userProfile, "scoop\\shims\\ffmpeg.exe"));
            candidates.add(new File("C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe"));
        } else if (isMac()) {
            candidates.add(new File("/opt/homebrew/bin/ffmpeg"));
            candidates.add(new File("/usr/local/bin/ffmpeg"));
        } else {
            candidates.add(new File("/usr/bin/ffmpeg"));
            candidates.add(new File("/usr/local/bin/ffmpeg"));
        }
        for (File f : candidates) {
            if (f.isFile() && f.canExecute()) return f;
        }
        return null;
    }
}
