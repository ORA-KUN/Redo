package fr.orakun.redo.core.export;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads a static ffmpeg build into {@code <redoDir>/ffmpeg/} (Windows and macOS).
 * Runs on a background thread; progress is exposed as a fraction.
 */
public final class FfmpegDownloader implements Runnable {
    public static final String WINDOWS_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
    public static final String MAC_URL = "https://evermeet.cx/ffmpeg/getrelease/zip";

    private final File targetDir;
    private volatile double progress;
    private volatile String error;
    private volatile File result;
    private volatile boolean done;

    public FfmpegDownloader(File redoDir) {
        this.targetDir = new File(redoDir, "ffmpeg");
    }

    public static boolean supported() {
        return FfmpegLocator.isWindows() || FfmpegLocator.isMac();
    }

    public double progress() {
        return progress;
    }

    public String error() {
        return error;
    }

    public File result() {
        return result;
    }

    public boolean isDone() {
        return done;
    }

    public Thread start() {
        Thread t = new Thread(this, "Redo ffmpeg download");
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Override
    public void run() {
        try {
            if (!supported()) throw new IOException("Téléchargement automatique indisponible sur ce système : installe ffmpeg avec ton gestionnaire de paquets.");
            String url = FfmpegLocator.isWindows() ? WINDOWS_URL : MAC_URL;
            if (!targetDir.isDirectory() && !targetDir.mkdirs()) throw new IOException("Impossible de créer " + targetDir);
            HttpURLConnection con = open(url);
            long total = con.getContentLengthLong();
            File out = new File(targetDir, FfmpegLocator.exeName());
            InputStream in = new CountingStream(new BufferedInputStream(con.getInputStream()), total);
            ZipInputStream zip = new ZipInputStream(in);
            try {
                ZipEntry e;
                boolean found = false;
                while ((e = zip.getNextEntry()) != null) {
                    String name = e.getName().replace('\\', '/');
                    String base = name.substring(name.lastIndexOf('/') + 1);
                    if (!e.isDirectory() && base.equals(FfmpegLocator.exeName())) {
                        OutputStream os = new FileOutputStream(out);
                        try {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = zip.read(buf)) > 0) os.write(buf, 0, r);
                        } finally {
                            os.close();
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) throw new IOException("ffmpeg introuvable dans l'archive téléchargée");
            } finally {
                zip.close();
            }
            //noinspection ResultOfMethodCallIgnored
            out.setExecutable(true);
            progress = 1;
            result = out;
        } catch (Exception ex) {
            error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        } finally {
            done = true;
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "Redo-Minecraft-Mod");
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = con.getHeaderField("Location");
                if (loc == null) throw new IOException("Redirection sans destination");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) throw new IOException("Téléchargement refusé (HTTP " + code + ")");
            return con;
        }
        throw new IOException("Trop de redirections");
    }

    private final class CountingStream extends InputStream {
        private final InputStream in;
        private final long total;
        private long read;

        CountingStream(InputStream in, long total) {
            this.in = in;
            this.total = total;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = in.read(b, off, len);
            if (r > 0) count(r);
            return r;
        }

        private void count(int n) {
            read += n;
            if (total > 0) progress = Math.min(0.99, read / (double) total);
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
